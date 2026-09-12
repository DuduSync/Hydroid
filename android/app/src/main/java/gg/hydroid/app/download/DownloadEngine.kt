package gg.hydroid.app.download

import android.content.Context
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

    fun start(context: Context, id: String, title: String, uri: String, method: DownloadMethod) {
        // evita cards duplicados do mesmo titulo
        AppStore.downloads.value
            .filter { it.title == title && it.stage !in setOf("concluido", "erro") }
            .forEach { AppStore.removeDownload(it.id) }
        if (method == DownloadMethod.TORRENT) {
            if (!uri.startsWith("magnet:")) {
                post(ActiveDownload(id, title, stage = "erro", method = "torrent",
                    error = "Torrent precisa de um magnet"))
                return
            }
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
                error = "Chave ${labelOf(method)} não configurada (Ajustes, Integrações)"))
            return
        }
        if (method == DownloadMethod.DIRETO && uri.startsWith("magnet:")) {
            post(ActiveDownload(id, title, stage = "erro", method = "direto",
                error = "Magnet precisa de torrent local ou serviço debrid"))
            return
        }
        val methodTag = tagOf(method)
        post(ActiveDownload(id, title, stage = "resolvendo", method = methodTag, uri = uri))
        AppLog.i("Download", "start: $title [$methodTag] uri=${uri.take(80)}")
        jobs[id] = scope.launch {
            try {
                val directUrl = when (method) {
                    DownloadMethod.RD -> resolveViaRd(id, title, uri)
                    DownloadMethod.PREMIUMIZE -> DebridClouds.premiumize(debridKey!!, uri)
                    DownloadMethod.ALLDEBRID -> DebridClouds.alldebrid(debridKey!!, uri)
                    DownloadMethod.TORBOX -> DebridClouds.torbox(debridKey!!, uri)
                    else -> uri
                }
                downloadFile(context, id, title, directUrl, methodTag)
            } catch (e: CancellationException) {
                AppLog.i("Download", if (paused.contains(id)) "pausado: $title"
                    else "cancelado pelo usuário: $title")
                throw e
            } catch (e: Exception) {
                AppLog.e("Download", "falhou: $title", e)
                post(ActiveDownload(id, title, stage = "erro", method = methodTag,
                    error = e.message ?: e.toString()))
            }
        }
    }

    fun cancel(id: String) {
        val dl = AppStore.downloads.value.firstOrNull { it.id == id }
        cancelled = cancelled + id
        jobs.remove(id)?.cancel()
        TorrentEngine.cancel(id)
        // descarta o arquivo parcial do download HTTP
        if (dl != null && dl.method != "torrent") {
            runCatching {
                File(AppStore.targetDir(AppStore.appContext), "${safeName(dl.title)}.bin").delete()
            }
        }
        AppStore.downloads.value.filter { it.id == "cloud-$id" }.forEach { AppStore.removeDownload(it.id) }
        AppStore.removeDownload(id)
    }

    fun pause(id: String) {
        val dl = AppStore.downloads.value.firstOrNull { it.id == id } ?: return
        paused = paused + id
        jobs.remove(id)?.cancel()
        AppStore.downloads.value.filter { it.id == "cloud-$id" }.forEach { AppStore.removeDownload(it.id) }
        TorrentEngine.pause(id)
        post(dl.copy(stage = "pausado", speedBps = 0))
    }

    fun resume(id: String) {
        val dl = AppStore.downloads.value.firstOrNull { it.id == id } ?: return
        val uri = dl.uri
        if (uri.isNullOrBlank()) {
            post(dl.copy(stage = "erro", error = "Sem link salvo para retomar, baixe de novo"))
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
        scope.launch {
            var current = File(savePath)

            if (doExtract) {
                val archive = findArchive(current)
                if (archive != null) {
                    post(ActiveDownload(id, title, stage = "extraindo", method = methodTag,
                        progress = 1f, savePath = savePath))
                    val result = runCatching {
                        ArchiveExtractor.extract(archive, archive.parentFile!!)
                    }
                    if (result.isFailure) {
                        post(ActiveDownload(id, title, stage = "concluido", method = methodTag,
                            progress = 1f, savePath = archive.absolutePath,
                            error = "Extração falhou: ${result.exceptionOrNull()?.message}"))
                        return@launch
                    }
                    if (AppStore.deleteArchive.value) archive.delete()
                    current = archive.parentFile ?: current
                }
            }

            if (moveToTarget && AppStore.downloadDir.value.isNotBlank()) {
                val target = AppStore.targetDir(context)
                if (current.canonicalPath != target.canonicalPath) {
                    val dest = uniqueDest(File(target, current.name))
                    runCatching { moveRecursive(current, dest) }
                        .onSuccess { current = dest }
                        .onFailure {
                            android.util.Log.e("HydroidMove", "mover falhou: ${it.message}", it)
                        }
                }
            }

            post(ActiveDownload(id, title, stage = "concluido", method = methodTag,
                progress = 1f, savePath = current.absolutePath))
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
        scope.launch(Dispatchers.Main) {
            AppStore.upsertDownload(dl)
            notifyService(dl)
        }
    }

    // notificacao: mostra o download ativo; para quando nao ha mais nenhum
    fun notifyService(dl: ActiveDownload) {
        val context = AppStore.appContext
        val active = AppStore.downloads.value.filter {
            it.stage !in listOf("concluido", "erro")
        }
        if (active.isNotEmpty()) {
            val first = active.first()
            val text = when (first.stage) {
                "baixando" -> "${formatSpeed(first.speedBps)} · ${formatBytes(first.bytesDownloaded)} de ${formatBytes(first.totalBytes)}"
                else -> first.stage.replaceFirstChar { it.uppercase() }
            }
            DownloadService.update(context, first.title, text, (first.progress * 100).toInt(),
                first.stage != "baixando")
        } else {
            DownloadService.stop(context)
        }
        if (dl.stage == "extraindo") {
            DownloadService.update(context, dl.title, "Extraindo...", 100, true)
        }
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

    private suspend fun downloadFile(
        context: Context, id: String, title: String, url: String, methodTag: String
    ) {
        val safe = safeName(title)
        val outFile = File(AppStore.targetDir(context), "$safe.bin")
        withContext(Dispatchers.IO) {
            fun open(startAt: Long): okhttp3.Response {
                val rq = Request.Builder().url(url)
                if (startAt > 0) rq.header("Range", "bytes=$startAt-")
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
                        "O link retornou uma página web, não um arquivo. Use Real-Debrid para este provedor."
                    }
                }
                val body = r.body ?: error("Corpo vazio")
                val len = body.contentLength()
                val total = if (len > 0) len + startAt else -1L
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var done = startAt
                var lastMs = System.currentTimeMillis()
                var lastBytes = startAt
                java.io.FileOutputStream(outFile, true).use { out ->
                    while (true) {
                        if (cancelled.contains(id) || paused.contains(id)) return@withContext
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastMs >= 800) {
                            val speed = (done - lastBytes) * 1000 / (now - lastMs)
                            post(ActiveDownload(
                                id, title, stage = "baixando", method = methodTag,
                                progress = if (total > 0) done.toFloat() / total else 0f,
                                bytesDownloaded = done, totalBytes = total, speedBps = speed
                            ))
                            lastMs = now; lastBytes = done
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

    private fun formatBytes(b: Long): String = when {
        b >= 1_073_741_824 -> "%.2f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }

    private fun formatSpeed(bps: Long) = "${formatBytes(bps)}/s"
}
