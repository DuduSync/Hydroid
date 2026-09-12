package gg.hydroid.app.data.api

import gg.hydroid.app.data.api.JsonCfg
import kotlinx.serialization.json.Json

// fonte de downloads no formato Hydra: {name, downloads:[{title, uris, fileSize, uploadDate}]}
@kotlinx.serialization.Serializable
data class SourceCatalog(
    val name: String = "",
    val downloads: List<SourceRepack> = emptyList()
)

@kotlinx.serialization.Serializable
data class SourceRepack(
    val title: String,
    val uris: List<String> = emptyList(),
    val fileSize: String? = null,
    val uploadDate: String? = null
)

// ponytail: so fontes servidas como JSON puro (github raw etc). Sites atras de
// Cloudflare (hydralinks.cloud) exigem browser real — o Hydra de verdade resolve
// isso no servidor deles; a versao mobile usa fontes raw/mirrors por enquanto.
object SourceFetcher {

    // cache em memoria (TTL 10 min): evita baixar o mesmo JSON de fonte a cada jogo checado
    private const val TTL_MS = 10 * 60 * 1000L
    private val cache = mutableMapOf<String, Pair<Long, SourceCatalog>>()

    suspend fun fetch(url: String, appContext: android.content.Context): SourceCatalog? {
        synchronized(cache) {
            cache[url]?.let { (at, cat) ->
                if (System.currentTimeMillis() - at < TTL_MS) return cat
            }
        }
        val raw = runCatching { httpGetJson(url) }
            .onFailure { android.util.Log.e("HydroidSource", "fetch $url: ${it.message}") }
            .getOrNull() ?: return null
        val catalog = runCatching { Json.decodeFromString<SourceCatalog>(raw) }
            .onFailure { android.util.Log.e("HydroidSource", "parse $url: ${it.message}") }
            .onSuccess { android.util.Log.i("HydroidSource", "${it.name}: ${it.downloads.size} repacks") }
            .getOrNull() ?: return null
        synchronized(cache) { cache[url] = System.currentTimeMillis() to catalog }
        return catalog
    }
}
