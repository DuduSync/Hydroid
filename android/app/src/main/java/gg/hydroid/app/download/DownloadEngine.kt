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

enum class DownloadMethod { RD, DIRETO, TORRENT }

object DownloadEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    @Volatile private var cancelled = setOf<String>()

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
        if (method == DownloadMethod.RD && AppStore.rdApiKey.value.isBlank()) {
            AppLog.w("Download", "RD sem chave configurada — bloqueado: $title")
            post(ActiveDownload(id, title, stage = "erro", method = "rd",
                error = "Chave Real-Debrid não configurada (Ajustes)"))
            return
        }
        if (method == DownloadMethod.DIRETO && uri.startsWith("magnet:")) {
            post(ActiveDownload(id, title, stage = "erro", method = "direto",
                error = "Magnet precisa de torrent local ou Real-Debrid"))
            return
        }
        val methodTag = if (method == DownloadMethod.RD) "rd" else "direto"
        post(ActiveDownload(id, title, stage = "resolvendo", method = methodTag))
        AppLog.i("Download", "start: $title [$methodTag] uri=${uri.take(80)}")
        jobs[id] = scope.launch {
            try {
                val directUrl = when {
                    method == DownloadMethod.RD -> resolveViaRd(id, title, uri)
                    else -> uri
                }
                downloadFile(context, id, title, directUrl, methodTag)
            } catch (e: CancellationException) {
                AppLog.i("Download", "cancelado pelo usuário: $title")
                throw e
            } catch (e: Exception) {
                AppLog.e("Download", "falhou: $title", e)
                post(ActiveDownload(id, title, stage = "erro", method = methodTag,
                    error = e.message ?: e.toString()))
            }
        }
    }

    fun cancel(id: String) {
        cancelled = cancelled + id
        jobs.remove(id)?.cancel()
        TorrentEngine.cancel(id)
        AppStore.removeDownload(id)
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
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
        val outFile = File(AppStore.targetDir(context), "$safe.bin")
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            HttpClient.client.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) { "Download HTTP ${resp.code}" }
                val contentType = resp.header("Content-Type") ?: ""
                check(!contentType.contains("text/html")) {
                    "O link retornou uma página web, não um arquivo. Use Real-Debrid para este provedor."
                }
                val body = resp.body ?: error("Corpo vazio")
                val total = body.contentLength()
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastMs = System.currentTimeMillis()
                var lastBytes = 0L
                outFile.outputStream().use { out ->
                    while (true) {
                        if (cancelled.contains(id)) { outFile.delete(); return@withContext }
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                        done += r
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
        if (cancelled.contains(id)) { outFile.delete(); return }
        val guess = url.substringBefore('?').substringAfterLast('/')
        val ext = guess.substringAfterLast('.', "").takeIf { it.length in 2..4 } ?: "zip"
        val finalName = File(outFile.parent, "$safe.$ext")
        outFile.renameTo(finalName)
        AppLog.i("Download", "concluído: $title -> ${finalName.absolutePath}")
        post(ActiveDownload(id, title, stage = "concluido", method = methodTag,
            progress = 1f, savePath = finalName.absolutePath))
        afterDownload(context, id, title, finalName.absolutePath, methodTag)
    }

    private fun formatBytes(b: Long): String = when {
        b >= 1_073_741_824 -> "%.2f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }

    private fun formatSpeed(bps: Long) = "${formatBytes(bps)}/s"
}
