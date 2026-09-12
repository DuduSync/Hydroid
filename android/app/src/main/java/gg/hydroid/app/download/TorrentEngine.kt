package gg.hydroid.app.download

import android.content.Context
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.model.ActiveDownload
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.remove_flags_t
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

// engine de torrent local (jlibtorrent) — alternativa a Real-Debrid.
// ATENCAO: TorrentHandle vindo de alert so vale DENTRO do callback; guardamos
// apenas o infoHash (valor) e reencontramos o handle com session.find().
object TorrentEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var manager: SessionManager? = null
    private var appContext: Context? = null

    private val pollers = mutableMapOf<String, Job>()
    private val hashes = mutableMapOf<String, Sha1Hash>()
    @Volatile private var pausedIds = setOf<String>()
    private var pendingId: String? = null
    private var pendingTitle: String? = null

    fun start(context: Context, id: String, title: String, magnet: String) {
        appContext = context.applicationContext
        val mgr = ensureSession() ?: run {
            post(ActiveDownload(id, title, stage = "erro", method = "torrent",
                error = "Falha ao iniciar a engine de torrent"))
            return
        }
        // evita cards duplicados do mesmo titulo
        AppStore.downloads.value
            .filter { it.title == title && it.stage !in setOf("concluido", "erro") }
            .forEach { AppStore.removeDownload(it.id) }
        pollers[id]?.cancel()
        pendingId = id
        pendingTitle = title
        post(ActiveDownload(id, title, stage = "conectando ao swarm", method = "torrent"))
        try {
            mgr.download(magnet, AppStore.torrentsDir(context), torrent_flags_t())
        } catch (e: Exception) {
            post(ActiveDownload(id, title, stage = "erro", method = "torrent",
                error = "Magnet inválido: ${e.message}"))
        }
    }

    fun cancel(id: String) {
        pollers.remove(id)?.cancel()
        pausedIds = pausedIds - id
        pendingId = pendingId.takeIf { it != id }
        val hash = hashes.remove(id)
        if (hash != null) {
            runCatching {
                manager?.find(hash)?.let { handle ->
                    // remove_flags_t.from_int(1) = delete_files
                    manager?.remove(handle, remove_flags_t.from_int(1))
                }
            }
        }
    }

    fun pause(id: String) {
        pausedIds = pausedIds + id
        val hash = hashes[id] ?: return
        runCatching { manager?.find(hash)?.pause() }
    }

    fun resume(context: Context, id: String, title: String, magnet: String) {
        pausedIds = pausedIds - id
        val hash = hashes[id]
        val handle = hash?.let { runCatching { manager?.find(it) }.getOrNull() }
        if (handle != null && handle.isValid()) {
            runCatching { handle.resume() }
        } else {
            // sessão perdida (app reiniciou): re-adiciona o magnet, os dados ficam no diretório
            hashes.remove(id)
            start(context, id, title, magnet)
        }
    }

    private fun ensureSession(): SessionManager? {
        manager?.let { return it }
        return try {
            val mgr = SessionManager()
            mgr.addListener(object : AlertListener {
                override fun types(): IntArray = intArrayOf(
                    AlertType.ADD_TORRENT.swig(),
                    AlertType.TORRENT_ERROR.swig()
                )

                override fun alert(alert: Alert<*>) {
                    when (alert) {
                        is AddTorrentAlert -> {
                            val id = pendingId
                            val title = pendingTitle
                            // captura o hash AQUI (dentro do callback) — o handle expira ao sair
                            val hash: Sha1Hash? =
                                runCatching { alert.handle().infoHash() }.getOrNull()
                            if (id != null && title != null && hash != null) {
                                pendingId = null
                                pendingTitle = null
                                hashes[id] = hash
                                poll(id, title, hash)
                            }
                        }
                        is TorrentErrorAlert -> {
                            val id = pendingId ?: return
                            pendingId = null
                            post(ActiveDownload(id, pendingTitle ?: "Torrent", stage = "erro",
                                method = "torrent", error = "Erro no torrent: ${alert.message()}"))
                        }
                    }
                }
            })
            mgr.start()
            manager = mgr
            AppLog.i("Torrent", "sessão jlibtorrent iniciada")
            mgr
        } catch (e: Throwable) {
            AppLog.e("Torrent", "falha ao iniciar sessão", e)
            null
        }
    }

    private fun poll(id: String, title: String, hash: Sha1Hash) {
        pollers[id] = scope.launch {
            // da tempo da sessao registrar o torrent
            delay(1500)
            var lastLog = 0L
            while (isActive) {
                val mgr = manager
                if (mgr == null) { delay(1500); continue }
                // handle duravel (nao vem do alert)
                val handle = runCatching { mgr.find(hash) }.getOrNull()
                if (handle == null || !handle.isValid()) {
                    delay(1500)
                    continue
                }
                val st = runCatching { handle.status() }.getOrNull()
                if (st == null) {
                    delay(1500)
                    continue
                }
                if (pausedIds.contains(id)) {
                    delay(1000)
                    continue
                }
                val done = st.totalWantedDone()
                val total = st.totalWanted()
                post(ActiveDownload(
                    id, title,
                    stage = "baixando",
                    method = "torrent",
                    progress = st.progress(),
                    bytesDownloaded = done,
                    totalBytes = total,
                    speedBps = st.downloadPayloadRate().toLong()
                ))
                if (System.currentTimeMillis() - lastLog > 15000) {
                    lastLog = System.currentTimeMillis()
                    AppLog.i("Torrent",
                        "poll $title: ${st.progress()} peers=${st.numPeers()} seeds=${st.numSeeds()} rate=${st.downloadPayloadRate()}")
                }
                if (st.isFinished) {
                    runCatching { handle.pause() }
                    val name = runCatching { st.name() }.getOrNull() ?: ""
                    val savePath = File(AppStore.torrentsDir(context()), name).absolutePath
                    AppLog.i("Torrent", "concluído: $title -> $savePath")
                    post(ActiveDownload(id, title, stage = "concluido", method = "torrent",
                        progress = 1f, savePath = savePath))
                    DownloadEngine.afterDownload(context(), id, title, savePath, "torrent", moveToTarget = true)
                    break
                }
                delay(1000)
            }
        }
    }

    private fun context(): Context = appContext
        ?: error("TorrentEngine sem contexto")

    private fun post(dl: ActiveDownload) {
        scope.launch(Dispatchers.Main) {
            AppStore.upsertDownload(dl)
            DownloadEngine.notifyService(dl)
        }
    }
}
