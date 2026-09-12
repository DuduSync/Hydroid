package gg.hydroid.app.download

import gg.hydroid.app.data.i18n.tr

import gg.hydroid.app.data.i18n.tf


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
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.remove_flags_t
import org.libtorrent4j.swig.settings_pack
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
    @Volatile private var cancelledIds = setOf<String>()
    private var pendingId: String? = null
    private var pendingTitle: String? = null

    // trackers publicos injetados nos magnets (DHT sozinho rende pouco no Android)
    private val PUBLIC_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://open.demonii.com:1337/announce"
    )

    private fun withTrackers(magnet: String): String {
        if (!magnet.startsWith("magnet:")) return magnet
        val extra = PUBLIC_TRACKERS
            .filter { !magnet.contains(it) && !magnet.contains(java.net.URLEncoder.encode(it, "UTF-8")) }
            .joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }
        return magnet + extra
    }

    // limite de download do libtorrent em KB/s (0 = sem limite)
    fun applySpeedLimit() {
        val mgr = manager ?: return
        runCatching {
            val kbps = AppStore.speedLimitKbps.value
            val pack = SettingsPack()
            pack.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), if (kbps > 0) kbps * 1024 else 0)
            mgr.applySettings(pack)
        }.onFailure { AppLog.w("Torrent", "limite de velocidade falhou: ${it.message}") }
    }

    fun start(context: Context, id: String, title: String, magnet: String) {
        appContext = context.applicationContext
        cancelledIds = cancelledIds - id
        val mgr = ensureSession() ?: run {
            post(ActiveDownload(id, title, stage = "erro", method = "torrent",
                error = tr("Falha ao iniciar a engine de torrent")))
            return
        }
        // evita cards duplicados do mesmo titulo: mata o engine anterior SEM apagar os
        // arquivos (sao os mesmos do torrent novo — apagar jogaria o progresso fora)
        AppStore.downloads.value
            .filter { it.title == title && it.id != id && it.stage !in setOf("concluido", "erro") }
            .forEach {
                AppLog.i("Torrent", "titulo repetido: matando download anterior ${it.id} (${it.stage})")
                kill(it.id, deleteFiles = false)
            }
        pollers.remove(id)?.cancel()
        pendingId = id
        pendingTitle = title
        post(ActiveDownload(id, title, stage = "conectando ao swarm", method = "torrent"))
        AppLog.i("Torrent", "start: $title id=$id magnet=${magnet.take(100)}")
        try {
            mgr.download(withTrackers(magnet), AppStore.torrentsDir(context), torrent_flags_t())
        } catch (e: Exception) {
            AppLog.e("Torrent", "magnet inválido: $title", e)
            post(ActiveDownload(id, title, stage = "erro", method = "torrent",
                error = tf("Magnet inválido: %s", e.message)))
        }
    }

    fun cancel(id: String) {
        AppLog.i("Torrent", "cancel: id=$id")
        kill(id, deleteFiles = true)
    }

    // remove o download da sessao; deleteFiles=false mantem os dados (titulo repetido)
    private fun kill(id: String, deleteFiles: Boolean) {
        cancelledIds = cancelledIds + id
        pollers.remove(id)?.cancel()
        pausedIds = pausedIds - id
        if (pendingId == id) {
            pendingId = null
            pendingTitle = null
        }
        val hash = hashes.remove(id)
        if (hash != null) {
            runCatching {
                manager?.find(hash)?.let { handle ->
                    // remove_flags_t.from_int(1) = delete_files
                    manager?.remove(handle, remove_flags_t.from_int(if (deleteFiles) 1 else 0))
                }
            }.onFailure { AppLog.w("Torrent", "remove falhou: ${it.message}") }
        }
        AppLog.i("Torrent", "kill: id=$id arquivos=${if (deleteFiles) "apagados" else "mantidos"}")
    }

    fun pause(id: String) {
        pausedIds = pausedIds + id
        val hash = hashes[id] ?: return
        AppLog.i("Torrent", "pause: id=$id")
        runCatching { manager?.find(hash)?.pause() }
    }

    fun resume(context: Context, id: String, title: String, magnet: String) {
        pausedIds = pausedIds - id
        cancelledIds = cancelledIds - id
        AppLog.i("Torrent", "resume: $title id=$id")
        val hash = hashes[id]
        val handle = hash?.let { runCatching { manager?.find(it) }.getOrNull() }
        if (handle != null && handle.isValid()) {
            runCatching { handle.resume() }
        } else {
            // sessão perdida (app reiniciou): re-adiciona o magnet, os dados ficam no diretório
            AppLog.i("Torrent", "sessao perdida pra id=$id, re-adicionando magnet")
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
                            AppLog.i("Torrent",
                                "alert ADD_TORRENT id=${id ?: "-"} hash=${hash ?: "-"}")
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
                            AppLog.w("Torrent", "alert ERROR id=$id: ${alert.message()}")
                            post(ActiveDownload(id, pendingTitle ?: "Torrent", stage = "erro",
                                method = "torrent", error = tf("Erro no torrent: %s", alert.message())))
                            DownloadEngine.onEngineDone(id)
                        }
                    }
                }
            })
            mgr.start()
            manager = mgr
            applySpeedLimit()
            AppLog.i("Torrent", "sessão jlibtorrent iniciada")
            mgr
        } catch (e: Throwable) {
            AppLog.e("Torrent", "falha ao iniciar sessão", e)
            null
        }
    }

    private fun poll(id: String, title: String, hash: Sha1Hash) {
        // nunca deixa um poller antigo rodando (virava zumbi e ressuscitava card/notificacao)
        pollers.remove(id)?.cancel()
        AppLog.i("Torrent", "poll iniciado: $title id=$id hash=$hash")
        pollers[id] = scope.launch {
            // da tempo da sessao registrar o torrent
            delay(1500)
            var lastLog = 0L
            while (isActive) {
                if (cancelledIds.contains(id)) {
                    AppLog.i("Torrent", "poll encerrado (cancelado): $title id=$id")
                    break
                }
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
                    speedBps = st.downloadPayloadRate().toLong(),
                    peers = st.numPeers(),
                    seeds = st.numSeeds()
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
                    DownloadEngine.onEngineDone(id)
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
        // cancelado nao pode voltar pra tela (o poll ressuscitava o card a cada tick)
        if (cancelledIds.contains(dl.id) && dl.stage != "erro") return
        scope.launch(Dispatchers.Main) {
            if (cancelledIds.contains(dl.id) && dl.stage != "erro") return@launch
            AppStore.upsertDownload(dl)
            DownloadEngine.notifyService(dl)
        }
    }
}
