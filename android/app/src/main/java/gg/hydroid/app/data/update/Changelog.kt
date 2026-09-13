package gg.hydroid.app.data.update

import android.content.Context
import gg.hydroid.app.data.api.JsonCfg
import gg.hydroid.app.data.api.httpGetJson
import gg.hydroid.app.data.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

// changelog publicado no repositorio (CHANGELOG.json) com cache local:
// a tela abre na hora com a ultima copia e depois sincroniza do GitHub
object Changelog {
    private const val URL =
        "https://raw.githubusercontent.com/DuduSync/Hydroid/main/CHANGELOG.json"

    @Serializable
    data class Entry(
        val version: String = "",
        val date: String = "",
        val changes: List<String> = emptyList()
    )

    @Serializable
    data class Data(val versions: List<Entry> = emptyList())

    // cache-busting: o CDN do raw.githubusercontent segura a copia antiga por alguns
    // minutos depois de um commit; um parametro muda a chave de cache e vem sempre fresco
    private fun url(): String = "$URL?t=${System.currentTimeMillis() / 600_000}"

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "hydroid/changelog.json").apply { parentFile?.mkdirs() }

    // ultima copia baixada (funciona offline)
    fun cached(context: Context): Data? = runCatching {
        val f = cacheFile(context)
        if (!f.exists()) return null
        JsonCfg.json.decodeFromString<Data>(f.readText())
    }.getOrNull()

    // baixa do GitHub e atualiza o cache
    suspend fun fetch(context: Context): Data? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = httpGetJson(url())
            val data = JsonCfg.json.decodeFromString<Data>(raw)
            runCatching { cacheFile(context).writeText(raw) }
            AppLog.i("Changelog", "sincronizada: ${data.versions.size} versoes")
            data
        }.onFailure { AppLog.w("Changelog", "sync falhou: ${it.message}") }.getOrNull()
    }
}
