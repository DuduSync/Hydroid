package gg.hydroid.app.data.log

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// log em arquivo para diagnostico: filesDir/logs/hydroid.log
// rotaciona em 2MB (mantem .1) e pode ser compartilhado pelo app
object AppLog {
    private const val MAX_BYTES = 2_000_000L
    private var logDir: File? = null
    private var logFile: File? = null
    private val lock = Any()

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        logDir = dir
        logFile = File(dir, "hydroid.log")
        rotateIfNeeded()
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        i("AppLog", "=== Hydroid v$version iniciado | Android ${Build.VERSION.RELEASE} " +
            "(sdk ${Build.VERSION.SDK_INT}) | ${Build.MANUFACTURER} ${Build.MODEL} ===")
    }

    fun i(tag: String, msg: String) = write("I", tag, msg)
    fun d(tag: String, msg: String) = write("D", tag, msg)
    fun w(tag: String, msg: String) = write("W", tag, msg)

    fun e(tag: String, msg: String, tr: Throwable? = null) =
        write("E", tag, msg + (tr?.let { "\n" + it.stackTraceToString() } ?: ""))

    private fun write(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        val line = "${stamp()} $level/$tag: $msg\n"
        synchronized(lock) {
            runCatching {
                file.appendText(line)
                if (file.length() > MAX_BYTES) rotate()
            }
        }
        when (level) {
            "E" -> android.util.Log.e(tag, msg)
            "W" -> android.util.Log.w(tag, msg)
            "D" -> android.util.Log.d(tag, msg)
            else -> android.util.Log.i(tag, msg)
        }
    }

    private fun rotate() {
        val dir = logDir ?: return
        val current = logFile ?: return
        val old = File(dir, "hydroid.log.1")
        if (old.exists()) old.delete()
        current.renameTo(old)
        logFile = File(dir, "hydroid.log")
    }

    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_BYTES) rotate()
    }

    fun file(): File? = logFile

    fun sizeBytes(): Long = logFile?.length() ?: 0L

    fun clear() {
        synchronized(lock) {
            runCatching { logFile?.writeText("") }
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
