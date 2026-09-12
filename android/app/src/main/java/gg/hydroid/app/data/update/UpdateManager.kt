package gg.hydroid.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import gg.hydroid.app.data.api.HttpClient
import gg.hydroid.app.data.api.JsonCfg
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.File

// verifica o release mais recente do GitHub, baixa o APK e abre o instalador
object UpdateManager {
    private const val REPO = "DuduSync/Hydroid"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    data class GhAsset(val name: String = "", val browser_download_url: String = "")

    @Serializable
    data class GhRelease(
        val tag_name: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList()
    )

    sealed class State {
        object Idle : State()
        data class Available(val version: String) : State()
        data class Downloading(val progress: Float) : State()
        data class Ready(val file: File) : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var apkUrl: String = ""

    fun check() {
        scope.launch {
            runCatching {
                val rq = Request.Builder()
                    .url("https://api.github.com/repos/$REPO/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                HttpClient.client.newCall(rq).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code}" }
                    val rel = JsonCfg.json.decodeFromString<GhRelease>(resp.body?.string().orEmpty())
                    if (rel.draft || rel.prerelease) return@launch
                    val remote = rel.tag_name.removePrefix("v").trim()
                    val apk = rel.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@launch
                    if (isNewer(remote, currentVersion())) {
                        apkUrl = apk.browser_download_url
                        _state.value = State.Available(remote)
                        AppLog.i("Update", "nova versao disponivel: v$remote")
                    }
                }
            }.onFailure { AppLog.w("Update", "check falhou: ${it.message}") }
        }
    }

    fun dismiss() {
        if (_state.value !is State.Downloading) _state.value = State.Idle
    }

    fun downloadAndInstall(context: Context) {
        val s = _state.value
        if (s !is State.Available && s !is State.Failed) return
        if (apkUrl.isBlank()) return
        scope.launch {
            runCatching {
                _state.value = State.Downloading(0f)
                val dir = File(context.cacheDir, "update").apply { mkdirs() }
                val out = File(dir, "Hydroid-update.apk")
                if (out.exists()) out.delete()
                val rq = Request.Builder().url(apkUrl).build()
                HttpClient.client.newCall(rq).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code}" }
                    val body = resp.body ?: error("Corpo vazio")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) _state.value = State.Downloading(done.toFloat() / total)
                            }
                        }
                    }
                }
                _state.value = State.Ready(out)
                AppLog.i("Update", "APK baixado: ${out.length()} bytes")
                install(context, out)
            }.onFailure {
                AppLog.e("Update", "download falhou", it)
                _state.value = State.Failed(it.message ?: "falha ao baixar a atualização")
            }
        }
    }

    fun install(context: Context, apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val i = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(i) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "gg.hydroid.app.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(i) }
            .onFailure { _state.value = State.Failed("Não foi possível abrir o instalador") }
    }

    private fun currentVersion(): String = runCatching {
        AppStore.appContext.packageManager
            .getPackageInfo(AppStore.appContext.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').mapNotNull { it.toIntOrNull() }
        val c = current.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
