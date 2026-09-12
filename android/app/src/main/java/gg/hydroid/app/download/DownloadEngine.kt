package gg.hydroid.app.download

import gg.hydroid.app.data.i18n.tr

import gg.hydroid.app.data.i18n.tf


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import gg.hydroid.app.data.api.HttpClient
import gg.hydroid.app.data.api.RealDebridApi
import gg.hydroid.app.data.model.ActiveDownload
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.data.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

enum class DownloadMethod { RD, PREMIUMIZE, ALLDEBRID, TORBOX, DIRETO, TORRENT }

object DownloadEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    @Volatile private var cancelled = setOf<String>()
    @Volatile private var paused = setOf<String>()

    private data class Queued(val id: String, val title: String, val uri: String, val method: DownloadMethod)
    private val queue = mutableListOf<Queued>()
    private val busyIds = mutableSetOf<String>()
    @Volatile private var pumping = false

    fun start(context: Context, id: String, title: String, uri: String, method: DownloadMethod) {
        // link limpo/validado (campo manual as vezes chega com espacos ou texto emendado)
        val cleanUri = normalizeUri(uri)
        if (cleanUri == null) {
            AppLog.w("Download", "link invalido recusado: ${uri.take(120)}")
            post(ActiveDownload(id, title, stage = "erro", method = tagOf(method),
                error = tr("Link inválido")))
            return
        }
        // mesmo titulo: substitui o download anterior (os dois escreveriam no MESMO arquivo)
        // id != id: retomar um download pausado nao pode cancelar ele mesmo
        AppStore.downloads.value
            .filter { it.title == title && it.id != id && it.stage !in setOf("concluido", "erro") }
            .forEach {
                AppLog.i("Download", "titulo repetido: cancelando anterior ${it.id} (${it.stage})")
                cancel(it.id)
            }
        synchronized(this) {
            queue.removeAll { it.id == id }
            queue.add(Queued(id, title, cleanUri, method))
        }
        AppLog.i("Download", "enfileirado: $title [${tagOf(method)}] fila=${queue.size}")
        post(ActiveDownload(id, title, stage = "na fila", method = tagOf(method), uri = cleanUri))
        pump()
    }

    // trim, sem espacos/quebras, corrige "https//" e aceita magnet/http(s) sem esquema
    fun normalizeUri(raw: String): String? {
        var u = raw.trim().replace(Regex("\\s+"), "")
        u = u.replace(Regex("^https//", RegexOption.IGNORE_CASE), "https://")
            .replace(Regex("^http//", RegexOption.IGNORE_CASE), "http://")
        return when {
            u.startsWith("magnet:", ignoreCase = true) -> u
            u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true) -> u
            Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$").matches(u) -> "https://$u"
            else -> null
        }
    }

    // chamado quando a rede muda (ex.: Wi-Fi voltou); os posts tambem chamam
    fun onConnectivityChanged() = pump()

    // usado quando o usuario muda as regras da fila (ex.: limite de simultaneos)
    fun kickQueue() = pump()

    // processa a fila respeitando o limite de simultaneos e o "so Wi-Fi"
    private fun pump() {
        synchronized(this) {
            if (pumping) return
            pumping = true
            try {
                if (AppStore.wifiOnly.value && !isUnmetered()) {
                    queue.forEach { q ->
                        val cur = AppStore.downloads.value.firstOrNull { it.id == q.id }
                        if (cur != null && cur.stage != "aguardando Wi-Fi") {
                            AppLog.i("Download", "fila: ${q.title} aguardando Wi-Fi (rede movel)")
                            post(cur.copy(stage = "aguardando Wi-Fi", speedBps = 0))
                        }
                    }
                    return
                }
                val max = AppStore.maxConcurrent.value
                var busy = busyIds.size
                while (queue.isNotEmpty() && (max <= 0 || busy < max)) {
                    val next = queue.removeAt(0)
                    AppLog.i("Download", "iniciando da fila: ${next.title} [${tagOf(next.method)}] (ativos=$busy, max=${if (max <= 0) "sem limite" else max})")
                    launchDownload(next)
                    busy++
                }
            } finally {
                pumping = false
            }
        }
    }

    // libera uma vaga da fila quando a engine (ex.: torrent) termina
    fun onEngineDone(id: String) {
        synchronized(this) { busyIds.remove(id) }
        pump()
    }

    private fun isUnmetered(): Boolean = runCatching {
        val cm = AppStore.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    private fun launchDownload(q: Queued) {
        val context = AppStore.appContext
        val (id, title, uri, method) = q
        if (method == DownloadMethod.TORRENT) {
            if (!uri.startsWith("magnet:")) {
                post(ActiveDownload(id, title, stage = "erro", method = "torrent",
                    error = tr("Torrent precisa de um magnet")))
                return
            }
            synchronized(this) { busyIds.add(id) }
            TorrentEngine.start(context, id, title, uri)
            return
        }
        val debridKey = when (method) {
            DownloadMethod.RD -> AppStore.rdApiKey.value
            DownloadMethod.PREMIUMIZE -> AppStore.premiumizeKey.value
            DownloadMethod.ALLDEBRID -> AppStore.alldebridKey.value
            DownloadMethod.TORBOX -> AppStore.torboxKey.value
            else -> null
        }
        if (debridKey != null && debridKey.isBlank()) {
            AppLog.w("Download", "${method.name} sem chave configurada: $title")
            post(ActiveDownload(id, title, stage = "erro", method = tagOf(method),
                error = tf("Chave %s não configurada (Ajustes, Integrações)", labelOf(method))))
            return
        }
        if (method == DownloadMethod.DIRETO && uri.startsWith("magnet:")) {
            post(ActiveDownload(id, title, stage = "erro", method = "direto",
                error = tr("Magnet precisa de torrent local ou serviço debrid")))
            return
        }
        val methodTag = tagOf(method)
        synchronized(this) { busyIds.add(id) }
        post(ActiveDownload(id, title, stage = "resolvendo", method = methodTag, uri = uri))
        AppLog.i("Download", "start: $title [$methodTag] uri=${uri.take(80)}")
        jobs[id] = scope.launch {
            try {
                val directUrl = when (method) {
                    DownloadMethod.RD -> resolveViaRd(id, title, uri)
                    DownloadMethod.PREMIUMIZE -> DebridClouds.premiumize(debridKey!!, uri)
                    DownloadMethod.ALLDEBRID -> DebridClouds.alldebrid(debridKey!!, uri)
                    DownloadMethod.TORBOX -> DebridClouds.torbox(debridKey!!, uri)
                    DownloadMethod.DIRETO -> resolveDirect(uri)
                    else -> uri
                }
                downloadFile(context, id, title, directUrl, methodTag)
            } catch (e: CancellationException) {
                AppLog.i("Download", if (paused.contains(id)) "pausado: $title"
                    else "cancelado pelo usuário: $title")
                throw e
            } catch (e: Exception) {
                // cancelamento embrulhado (ex.: dentro de runCatching) nao vira card de erro
                if (isCancellation(e)) {
                    AppLog.i("Download", "cancelado durante o download: $title")
                    throw CancellationException("cancelado").also { it.initCause(e) }
                }
                AppLog.e("Download", "falhou: $title", e)
                post(ActiveDownload(id, title, stage = "erro", method = methodTag,
                    error = e.message ?: e.toString()))
            }
        }
    }

    fun cancel(id: String) {
        val dl = AppStore.downloads.value.firstOrNull { it.id == id }
        AppLog.i("Download", "cancel: ${dl?.title ?: id} [${dl?.stage ?: "?"}]")
        cancelled = cancelled + id
        synchronized(this) { queue.removeAll { it.id == id } }
        jobs.remove(id)?.cancel()
        TorrentEngine.cancel(id)
        // descarta o arquivo parcial do download HTTP
        if (dl != null && dl.method != "torrent") {
            runCatching {
                File(AppStore.targetDir(AppStore.appContext), "${safeName(dl.title)}.bin").delete()
            }
        }
        synchronized(this) { busyIds.remove(id) }
        AppStore.downloads.value.filter { it.id == "cloud-$id" }.forEach { AppStore.removeDownload(it.id) }
        AppStore.removeDownload(id)
        // cancela tambem derruba/atualiza a notificacao (antes ficava presa em "Pausado")
        refreshNotification()
        pump()
    }

    fun pause(id: String) {
        val dl = AppStore.downloads.value.firstOrNull { it.id == id } ?: return
        AppLog.i("Download", "pause: ${dl.title} [${dl.stage}]")
        paused = paused + id
        synchronized(this) { queue.removeAll { it.id == id } }
        synchronized(this) { busyIds.remove(id) }
        jobs.remove(id)?.cancel()
        AppStore.downloads.value.filter { it.id == "cloud-$id" }.forEach { AppStore.removeDownload(it.id) }
        TorrentEngine.pause(id)
        post(dl.copy(stage = "pausado", speedBps = 0))
    }

    fun resume(id: String) {
        val dl = AppStore.downloads.value.firstOrNull { it.id == id } ?: return
        val uri = dl.uri
        if (uri.isNullOrBlank()) {
            post(dl.copy(stage = "erro", error = tr("Sem link salvo para retomar, baixe de novo")))
            return
        }
        paused = paused - id
        cancelled = cancelled - id
        AppLog.i("Download", "retomando: ${dl.title} [${dl.method}]")
        if (dl.method == "torrent") {
            TorrentEngine.resume(AppStore.appContext, id, dl.title, uri)
        } else {
            start(AppStore.appContext, id, dl.title, uri, methodFromTag(dl.method))
        }
    }

    // extrai manualmente um arquivo ja baixado (.zip/.rar) do card concluido
    fun extractNow(id: String) {
        val dl = AppStore.downloads.value.firstOrNull { it.id == id } ?: return
        val path = dl.savePath ?: return
        val archive = File(path)
        if (!archive.isFile) return
        scope.launch {
            post(ActiveDownload(id, dl.title, stage = "extraindo", method = dl.method,
                progress = 1f, savePath = path, uri = dl.uri))
            val parent = archive.parentFile!!
            val result = runCatching { ArchiveExtractor.extract(archive, parent) }
            if (result.isFailure) {
                AppLog.e("Download", "extração manual falhou: ${archive.absolutePath}", result.exceptionOrNull())
                post(ActiveDownload(id, dl.title, stage = "concluido", method = dl.method,
                    progress = 1f, savePath = path, uri = dl.uri,
                    error = tf("Extração falhou: %s", result.exceptionOrNull()?.message)))
                return@launch
            }
            val created = ArchiveExtractor.topLevelEntries(archive)
                .map { File(parent, it) }
                .filter { it.exists() }
            if (AppStore.deleteArchive.value) archive.delete()
            val leftover = if (archive.exists()) listOf(archive) else emptyList()
            val current = if (created.size == 1) created.first() else parent
            post(ActiveDownload(id, dl.title, stage = "concluido", method = dl.method,
                progress = 1f, savePath = current.absolutePath, uri = dl.uri,
                savedPaths = (leftover + created).map { it.absolutePath }))
        }
    }

    // chamado pelos engines ao concluir: extracao automatica e/ou mover para a pasta escolhida
    fun afterDownload(
        context: Context,
        id: String,
        title: String,
        savePath: String,
        methodTag: String,
        moveToTarget: Boolean = false
    ) {
        val doExtract = AppStore.autoExtract.value
        if (!doExtract && !moveToTarget) return
        AppLog.i("Download", "pos-download: $title extracao=$doExtract mover=$moveToTarget em $savePath")
        scope.launch {
            var current = File(savePath)
            var saved = listOf(current)

            if (doExtract) {
                val archive = findArchive(current)
                if (archive != null) {
                    AppLog.i("Download", "extraindo: ${archive.name}")
                    post(ActiveDownload(id, title, stage = "extraindo", method = methodTag,
                        progress = 1f, savePath = savePath))
                    val parent = archive.parentFile!!
                    val result = runCatching {
                        ArchiveExtractor.extract(archive, parent)
                    }
                    if (result.isFailure) {
                        AppLog.e("Download", "extração falhou: ${archive.absolutePath}", result.exceptionOrNull())
                        post(ActiveDownload(id, title, stage = "concluido", method = methodTag,
                            progress = 1f, savePath = archive.absolutePath,
                            error = tf("Extração falhou: %s", result.exceptionOrNull()?.message)))
                        return@launch
                    }
                    // guarda SO o que este download criou (o Apagar nao pode levar a pasta toda)
                    val created = ArchiveExtractor.topLevelEntries(archive)
                        .map { File(parent, it) }
                        .filter { it.exists() }
                    AppLog.i("Download", "extraido: ${created.size} item(s) de ${archive.name} em ${parent.name}")
                    if (AppStore.deleteArchive.value) archive.delete()
                    val leftover = if (archive.exists()) listOf(archive) else emptyList()
                    if (created.isNotEmpty()) {
                        saved = leftover + created
                        current = if (created.size == 1) created.first() else parent
                    } else {
                        current = parent
                    }
                }
            }

            if (moveToTarget && AppStore.downloadDir.value.isNotBlank()) {
                val target = AppStore.targetDir(context)
                if (current.canonicalPath != target.canonicalPath) {
                    val dest = uniqueDest(File(target, current.name))
                    runCatching { moveRecursive(current, dest) }
                        .onSuccess {
                            AppLog.i("Download", "movido: ${current.absolutePath} -> ${dest.absolutePath}")
                            current = dest
                            saved = listOf(dest)
                        }
                        .onFailure {
                            AppLog.e("Download", "mover falhou: ${it.message}", it)
                        }
                }
            }

            post(ActiveDownload(id, title, stage = "concluido", method = methodTag,
                progress = 1f, savePath = current.absolutePath,
                savedPaths = saved.map { it.absolutePath }))
        }
    }

    private fun uniqueDest(dest: File): File {
        if (!dest.exists()) return dest
        val base = dest.nameWithoutExtension
        val ext = dest.extension
        val suffix = if (ext.isNotBlank()) ".$ext" else ""
        return File(dest.parentFile, "$base-${System.currentTimeMillis()}$suffix")
    }

    private fun moveRecursive(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { child -> moveRecursive(child, File(dst, child.name)) }
            src.delete()
        } else {
            dst.parentFile?.mkdirs()
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            src.delete()
        }
    }

    private fun findArchive(path: File): File? {
        if (path.isFile) {
            return path.takeIf { it.extension.lowercase() in listOf("zip", "rar") }
        }
        if (!path.exists()) return null
        return path.walkTopDown()
            .maxDepth(2)
            .firstOrNull { it.isFile && it.extension.lowercase() in listOf("zip", "rar") }
    }

    private fun post(dl: ActiveDownload) {
        // nao deixa progresso atrasado sobrescrever pausado/cancelado
        if (paused.contains(dl.id) && dl.stage != "pausado") return
        if (cancelled.contains(dl.id) && dl.stage != "erro") return
        if (dl.stage == "concluido" || dl.stage == "erro" || dl.stage == "pausado") {
            synchronized(this) { busyIds.remove(dl.id) }
        }
        scope.launch(Dispatchers.Main) {
            AppStore.upsertDownload(dl)
            notifyService(dl)
            pump()
        }
    }

    // notificacao: quem desenha e o DownloadService (observa a lista); aqui so garante que ele roda
    fun notifyService(dl: ActiveDownload) {
        refreshNotification()
    }

    // sobe/derruba o servico conforme existem downloads ativos
    fun refreshNotification() {
        val context = AppStore.appContext
        val hasActive = AppStore.downloads.value.any { it.stage !in listOf("concluido", "erro") }
        if (hasActive) DownloadService.ensureRunning(context) else DownloadService.stop(context)
    }

    private fun removeCloudCard(id: String) {
        scope.launch(Dispatchers.Main) { AppStore.removeDownload(id) }
    }

    private suspend fun resolveViaRd(deviceId: String, title: String, uri: String): String {
        val rd = RealDebridApi(AppStore.rdApiKey.value)
        if (uri.startsWith("magnet:")) {
            val cloudId = "cloud-$deviceId"
            val added = rd.addMagnet(uri)
            rd.selectFiles(added.id)
            repeat(120) {
                val info = rd.torrentInfo(added.id)
                post(ActiveDownload(cloudId, title, stage = "cloud: ${info.status}", method = "rd",
                    progress = info.progress * 0.5f))
                if (info.status == "downloaded") {
                    val links = info.links
                    if (links.isEmpty()) error("Torrent sem links disponíveis na RD")
                    removeCloudCard(cloudId)
                    return rd.unrestrictLink(links.first()).download
                }
                if (info.status in listOf("error", "dead", "magnet_error")) {
                    removeCloudCard(cloudId)
                    error("RD não conseguiu processar o torrent: ${info.status}")
                }
                delay(5000)
            }
            removeCloudCard(cloudId)
            error("Timeout aguardando cloud da Real-Debrid")
        }
        return rd.unrestrictLink(uri).download
    }

    // links de hosters (gofile, pixeldrain, mediafire...) viram um link direto de arquivo
    private suspend fun resolveDirect(uri: String): String {
        AppLog.i("Hoster", "resolvendo direto: ${uri.take(100)}")
        val start = System.currentTimeMillis()
        return when (val r = HostResolver.resolve(uri)) {
            is HostResolver.Result.Ok -> {
                if (r.url != uri) {
                    AppLog.i("Hoster",
                        "${r.host}: resolvido em ${System.currentTimeMillis() - start}ms -> ${r.url.take(100)}")
                }
                r.url
            }
            is HostResolver.Result.Fail -> {
                AppLog.w("Hoster", "${r.host}: ${r.message}")
                error(r.message)
            }
        }
    }

    private suspend fun downloadFile(
        context: Context, id: String, title: String, url: String, methodTag: String
    ) {
        val safe = safeName(title)
        val outFile = File(AppStore.targetDir(context), "$safe.bin")
        withContext(Dispatchers.IO) {
            fun open(startAt: Long): okhttp3.Response {
                val rq = Request.Builder().url(url)
                if (startAt > 0) rq.header("Range", "bytes=$startAt-")
                rq.header("User-Agent", HostResolver.BROWSER_UA)
                return HttpClient.client.newCall(rq.build()).execute()
            }
            var startAt = if (outFile.exists()) outFile.length() else 0L
            var resp = open(startAt)
            if (startAt > 0 && resp.code != 206) {
                // servidor não suporta retomar: recomeça do zero
                resp.close()
                outFile.delete()
                startAt = 0L
                resp = open(0L)
            }
            resp.use { r ->
                check(r.isSuccessful) { "Download HTTP ${r.code}" }
                if (startAt == 0L) {
                    val contentType = r.header("Content-Type") ?: ""
                    check(!contentType.contains("text/html")) {
                        tr("O link retornou uma página web, não um arquivo. Use Real-Debrid para este provedor.")
                    }
                }
                val body = r.body ?: error(tr("Corpo vazio"))
                val len = body.contentLength()
                val total = if (len > 0) len + startAt else -1L
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var done = startAt
                var lastMs = System.currentTimeMillis()
                var lastBytes = startAt
                var lastLogMs = 0L
                AppLog.i("Download", "HTTP conectado: $title -> ${url.take(100)} (code=${r.code}${if (startAt > 0) ", retomando de ${formatBytes(startAt)}" else ""})")
                var tokens = 0.0
                var lastRefill = System.currentTimeMillis()
                java.io.FileOutputStream(outFile, true).use { out ->
                    while (true) {
                        if (cancelled.contains(id) || paused.contains(id)) return@withContext
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        done += n
                        // limite de velocidade (KB/s) por download: token bucket de ~1s
                        val limit = AppStore.speedLimitKbps.value
                        if (limit > 0) {
                            val rate = limit * 1024.0
                            val nowMs = System.currentTimeMillis()
                            tokens = minOf(rate, tokens + (nowMs - lastRefill) * rate / 1000.0)
                            lastRefill = nowMs
                            if (tokens < n) {
                                val needMs = ((n - tokens) * 1000.0 / rate).toLong()
                                try {
                                    Thread.sleep(needMs)
                                } catch (_: InterruptedException) {}
                                tokens = 0.0
                                lastRefill = System.currentTimeMillis()
                            } else {
                                tokens -= n
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastMs >= 800) {
                            val speed = (done - lastBytes) * 1000 / (now - lastMs)
                            post(ActiveDownload(
                                id, title, stage = "baixando", method = methodTag,
                                progress = if (total > 0) done.toFloat() / total else 0f,
                                bytesDownloaded = done, totalBytes = total, speedBps = speed
                            ))
                            lastMs = now; lastBytes = done
                            if (now - lastLogMs > 15000) {
                                lastLogMs = now
                                AppLog.i("Download",
                                    "progresso $title: ${"%.1f".format(if (total > 0) 100.0 * done / total else 0.0)}% ${formatSpeed(speed)} (${formatBytes(done)}${if (total > 0) " de " + formatBytes(total) else ""})")
                            }
                        }
                    }
                }
            }
        }
        if (cancelled.contains(id) || paused.contains(id)) return
        val guess = url.substringBefore('?').substringAfterLast('/')
        val ext = guess.substringAfterLast('.', "").takeIf { it.length in 2..4 } ?: "zip"
        val finalName = File(outFile.parent, "$safe.$ext")
        outFile.renameTo(finalName)
        AppLog.i("Download", "concluído: $title -> ${finalName.absolutePath}")
        post(ActiveDownload(id, title, stage = "concluido", method = methodTag,
            progress = 1f, savePath = finalName.absolutePath))
        afterDownload(context, id, title, finalName.absolutePath, methodTag)
    }

    // cancelamento pode vir embrulhado em outra excecao (runCatching, okhttp etc.)
    private fun isCancellation(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is CancellationException) return true
            t = t.cause
        }
        return false
    }

    private fun safeName(title: String) =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)

    private fun methodFromTag(tag: String) = when (tag) {
        "rd" -> DownloadMethod.RD
        "premiumize" -> DownloadMethod.PREMIUMIZE
        "alldebrid" -> DownloadMethod.ALLDEBRID
        "torbox" -> DownloadMethod.TORBOX
        "torrent" -> DownloadMethod.TORRENT
        else -> DownloadMethod.DIRETO
    }

    private fun tagOf(method: DownloadMethod) = when (method) {
        DownloadMethod.RD -> "rd"
        DownloadMethod.PREMIUMIZE -> "premiumize"
        DownloadMethod.ALLDEBRID -> "alldebrid"
        DownloadMethod.TORBOX -> "torbox"
        DownloadMethod.DIRETO -> "direto"
        DownloadMethod.TORRENT -> "torrent"
    }

    private fun labelOf(method: DownloadMethod) = when (method) {
        DownloadMethod.RD -> "Real-Debrid"
        DownloadMethod.PREMIUMIZE -> "Premiumize"
        DownloadMethod.ALLDEBRID -> "AllDebrid"
        DownloadMethod.TORBOX -> "TorBox"
        else -> "serviço debrid"
    }

    internal fun formatBytes(b: Long): String = when {
        b >= 1_073_741_824 -> "%.2f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }

    internal fun formatSpeed(bps: Long) = "${formatBytes(bps)}/s"
}
