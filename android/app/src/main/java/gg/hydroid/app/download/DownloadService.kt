package gg.hydroid.app.download

import gg.hydroid.app.data.i18n.tr


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import gg.hydroid.app.data.log.AppLog

// foreground service: mantem o processo vivo durante downloads e mostra o progresso
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 42
        @Volatile private var running = false

        fun ensureChannel(context: Context) {
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

        fun update(context: Context, title: String, text: String, percent: Int, indeterminate: Boolean) {
            ensureChannel(context)
            AppLog.i("Service", "notificacao: $title | $text | $percent%${if (indeterminate) " (indeterminada)" else ""}")
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("title", title)
                putExtra("text", text)
                putExtra("percent", percent)
                putExtra("indeterminate", indeterminate)
            }
            ContextCompat.startForegroundService(context, intent)
            running = true
        }

        fun stop(context: Context) {
            val wasRunning = running
            running = false
            if (wasRunning) AppLog.i("Service", "parando notificacao (sem downloads ativos)")
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "Hydroid"
        val text = intent?.getStringExtra("text") ?: ""
        val percent = intent?.getIntExtra("percent", 0) ?: 0
        val indeterminate = intent?.getBooleanExtra("indeterminate", true) ?: true

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (indeterminate) setProgress(0, 0, true)
                else setProgress(100, percent.coerceIn(0, 100), false)
            }
            .build()

        startForeground(NOTIF_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
