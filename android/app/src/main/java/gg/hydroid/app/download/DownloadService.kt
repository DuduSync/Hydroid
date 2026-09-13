package gg.hydroid.app.download

import gg.hydroid.app.data.i18n.tr
import gg.hydroid.app.data.i18n.tf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import gg.hydroid.app.MainActivity
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.model.ActiveDownload
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// foreground service: mantem o processo vivo durante downloads.
// notificacao resumo (foreground) + UMA notificacao por download ativo (2+ simultaneos)
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val posted = mutableSetOf<Int>()

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val SUMMARY_ID = 42
        @Volatile private var running = false      // start requisitado
        @Volatile private var started = false      // onStartCommand ja rodou
        @Volatile private var stopPending = false  // pediu parar antes de subir

        fun ensureChannel(context: Context) {
            runCatching {
                val mgr = context.getSystemService(NotificationManager::class.java)
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    mgr.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Downloads",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            description = tr("Progresso dos downloads")
                            setShowBadge(false)
                        }
                    )
                }
            }
        }

        // sobe o servico (idempotente); o conteudo das notificacoes vem do proprio service
        fun ensureRunning(context: Context) {
            if (running) return
            running = true
            stopPending = false
            AppLog.i("Service", "iniciando foreground service de downloads")
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            }.onFailure {
                running = false
                AppLog.w("Service", "nao consegui iniciar o servico: ${it.message}")
            }
        }

        fun stop(context: Context) {
            val wasRunning = running
            running = false
            // servico ainda subindo: deixa o onStartCommand se encerrar sozinho (a corrida
            // start->stop derruba o processo em algumas versoes do Android)
            if (!started) {
                stopPending = true
                if (wasRunning) AppLog.i("Service", "parada adiada (servico ainda iniciando)")
                return
            }
            if (wasRunning) AppLog.i("Service", "parando notificacao (sem downloads ativos)")
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }

        private fun notifId(dlId: String) = 1000 + (dlId.hashCode() and 0x7fffffff) % 1_000_000
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        started = true
        // startForeground nunca pode derrubar o app (ex.: notificacao bloqueada/limite do sistema)
        runCatching {
            ensureChannel(this)
            startForeground(SUMMARY_ID, buildSummary(emptyList()))
        }.onFailure { AppLog.w("Service", "startForeground falhou: ${it.message}") }
        if (stopPending) {
            stopPending = false
            AppLog.i("Service", "parada adiada executada")
            clearIndividual()
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            AppStore.downloads.collect { list ->
                runCatching {
                    val active = list.filter { it.stage !in listOf("concluido", "erro") }
                    if (active.isEmpty()) {
                        clearIndividual()
                        stopSelf()
                    } else {
                        render(active)
                    }
                }.onFailure { AppLog.w("Service", "atualizar notificacoes falhou: ${it.message}") }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        started = false
        scope.cancel()
        runCatching { clearIndividual() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // resumo sempre; notificacao individual so quando tem 2+ (com 1 seria duplicado)
    private fun render(active: List<ActiveDownload>) {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        runCatching { mgr.notify(SUMMARY_ID, buildSummary(active)) }
            .onFailure { AppLog.w("Service", "notificar resumo falhou: ${it.message}") }
        if (active.size < 2) {
            clearIndividual()
            return
        }
        val current = active.map { notifId(it.id) }.toSet()
        runCatching {
            posted.filter { it !in current }.forEach { mgr.cancel(it) }
            posted.retainAll(current)
            active.forEach { dl ->
                val id = notifId(dl.id)
                posted.add(id)
                mgr.notify(id, buildDownload(dl))
                AppLog.i("Service", "notificacao [${dl.id}]: ${dl.title} | ${textOf(dl)} | ${(dl.progress * 100).toInt()}%")
            }
        }.onFailure { AppLog.w("Service", "notificar download falhou: ${it.message}") }
    }

    private fun clearIndividual() {
        val mgr = getSystemService(NotificationManager::class.java)
        runCatching { posted.forEach { mgr.cancel(it) } }
        posted.clear()
    }

    private fun textOf(dl: ActiveDownload): String = when (dl.stage) {
        "baixando" -> "${DownloadEngine.formatSpeed(dl.speedBps)} · " +
            "${DownloadEngine.formatBytes(dl.bytesDownloaded)} de ${DownloadEngine.formatBytes(dl.totalBytes)}"
        else -> dl.stage.replaceFirstChar { it.uppercase() }
    }

    private fun openAppIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun baseBuilder(): NotificationCompat.Builder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentIntent(openAppIntent())
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setSilent(true)

    private fun buildSummary(active: List<ActiveDownload>): Notification {
        val one = active.firstOrNull()
        val text = when {
            one == null -> tr("Preparando...")
            active.size == 1 -> textOf(one)
            else -> tf("%d downloads em andamento", active.size)
        }
        val builder = baseBuilder()
            .setContentTitle(if (active.size > 1) tr("Downloads") else one?.title ?: "Hydroid")
            .setContentText(text)
        if (one != null && active.size == 1 && one.stage == "baixando") {
            builder.setProgress(100, (one.progress * 100).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun buildDownload(dl: ActiveDownload): Notification {
        val builder = baseBuilder()
            .setContentTitle(dl.title)
            .setContentText(textOf(dl))
        if (dl.stage == "baixando") {
            builder.setProgress(100, (dl.progress * 100).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }
}
