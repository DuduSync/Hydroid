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

// ponytail: um download por vez, sem fila/prioridade; trocar por fila quando rd cloud ficar lento
object DownloadEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    @Volatile private var cancelled = setOf<String>()

    fun resolveAndDownload(context: Context, id: String, title: String, uri: String) {
        val apiKey = AppStore.rdApiKey.value
        if (apiKey.isBlank()) {
            post(ActiveDownload(id, title, stage = "erro", error = "Configure a chave Real-Debrid em Ajustes"))
            return
        }
        post(ActiveDownload(id, title, stage = "resolvendo"))
        jobs[id] = scope.launch {
            try {
                val rd = RealDebridApi(apiKey)
                val directUrl = resolveUri(rd, id, title, uri)
                downloadFile(context, id, title, directUrl)
            } catch (e: Exception) {
                post(ActiveDownload(id, title, stage = "erro", error = e.message ?: e.toString()))
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

    private suspend fun resolveUri(rd: RealDebridApi, deviceId: String, title: String, uri: String): String {
        if (uri.startsWith("magnet:")) {
            val cloudId = "cloud-$deviceId"
            val added = rd.addMagnet(uri)
            rd.selectFiles(added.id)
            // polling do cloud da RD ate ficar pronto; card temporario "cloud-*" sai ao finalizar
            repeat(120) {
                val info = rd.torrentInfo(added.id)
                post(ActiveDownload(cloudId, title, stage = "cloud: ${info.status}", progress = info.progress * 0.5f))
                if (info.status == "downloaded") {
                    val links = info.links
                    if (links.isEmpty()) error("Torrent sem links disponiveis na RD")
                    AppStore.removeDownload(cloudId)
                    return rd.unrestrictLink(links.first()).download
                }
                if (info.status in listOf("error", "dead", "magnet_error")) {
                    AppStore.removeDownload(cloudId)
                    error("RD nao conseguiu processar o torrent: ${info.status}")
                }
                delay(5000)
            }
            AppStore.removeDownload(cloudId)
            error("Timeout aguardando cloud da Real-Debrid")
        } else {
            return rd.unrestrictLink(uri).download
        }
    }

    private suspend fun downloadFile(context: Context, id: String, title: String, url: String) {
        val outFile = File(AppStore.downloadsDir(context), "$title.bin".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            HttpClient.client.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) { "Download HTTP ${resp.code}" }
                val body = resp.body ?: error("Corpo vazio")
                val total = body.contentLength()
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastMs = System.currentTimeMillis()
                var lastBytes = 0L
                outFile.outputStream().use { out ->
                    while (true) {
                        if (cancelled.contains(id)) {
                            outFile.delete()
                            return@withContext
                        }
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                        done += r
                        val now = System.currentTimeMillis()
                        if (now - lastMs >= 800) {
                            val speed = (done - lastBytes) * 1000 / (now - lastMs)
                            post(
                                ActiveDownload(
                                    id, title, stage = "baixando",
                                    progress = if (total > 0) done.toFloat() / total else 0f,
                                    bytesDownloaded = done, totalBytes = total, speedBps = speed
                                )
                            )
                            lastMs = now; lastBytes = done
                        }
                    }
                }
            }
        }
        if (cancelled.contains(id)) { outFile.delete(); return }
        val finalName = File(outFile.parent, outFile.name.removeSuffix(".bin") + ".zip")
        outFile.renameTo(finalName)
        post(ActiveDownload(id, title, stage = "concluido", progress = 1f, savePath = finalName.absolutePath))
    }
}
