package gg.hydroid.app.download

import android.content.Context
import gg.hydroid.app.data.api.HttpClient
import gg.hydroid.app.data.api.RealDebridApi
import gg.hydroid.app.data.model.ActiveDownload
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

enum class DownloadMethod { RD, DIRETO }

// ponytail: um download por vez, sem fila/prioridade; trocar por fila quando precisar
object DownloadEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    @Volatile private var cancelled = setOf<String>()

    fun start(
        context: Context,
        id: String,
        title: String,
        uri: String,
        method: DownloadMethod
    ) {
        if (method == DownloadMethod.RD && AppStore.rdApiKey.value.isBlank()) {
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
        jobs[id] = scope.launch {
            try {
                val directUrl = when {
                    method == DownloadMethod.RD -> resolveViaRd(id, title, uri)
                    else -> uri // direto: usa a propria url
                }
                downloadFile(context, id, title, directUrl, methodTag)
            } catch (e: Exception) {
                post(ActiveDownload(id, title, stage = "erro", method = methodTag,
                    error = e.message ?: e.toString()))
            }
        }
    }

    fun cancel(id: String) {
        cancelled = cancelled + id
        jobs.remove(id)?.cancel()
        AppStore.removeDownload(id)
    }

    private fun post(dl: ActiveDownload) {
        scope.launch(Dispatchers.Main) { AppStore.upsertDownload(dl) }
    }

    // remocao enfileirada no Main DEPOIS dos posts pendentes (evita race que re-adiciona o card)
    private fun removeCloudCard(id: String) {
        scope.launch(Dispatchers.Main) { AppStore.removeDownload(id) }
    }

    // RD: magnet no cloud deles (polling) ou link http via unrestrict
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
        context: Context,
        id: String,
        title: String,
        url: String,
        methodTag: String
    ) {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
        val outFile = File(AppStore.downloadsDir(context), "$safe.bin")
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            HttpClient.client.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) { "Download HTTP ${resp.code}" }
                val contentType = resp.header("Content-Type") ?: ""
                check(!contentType.contains("text/html")) {
                    "O link retornou uma página web, não um arquivo. " +
                        "Use Real-Debrid para este provedor."
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
        post(ActiveDownload(id, title, stage = "concluido", method = methodTag,
            progress = 1f, savePath = finalName.absolutePath))
    }
}
