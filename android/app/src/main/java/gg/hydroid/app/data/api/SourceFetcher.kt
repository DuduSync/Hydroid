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

    suspend fun fetch(url: String, appContext: android.content.Context): SourceCatalog? {
        val raw = runCatching { httpGetJson(url) }
            .onFailure { android.util.Log.e("HydroidSource", "fetch $url: ${it.message}") }
            .getOrNull() ?: return null
        return runCatching { Json.decodeFromString<SourceCatalog>(raw) }
            .onFailure { android.util.Log.e("HydroidSource", "parse $url: ${it.message}") }
            .onSuccess { android.util.Log.i("HydroidSource", "${it.name}: ${it.downloads.size} repacks") }
            .getOrNull()
    }
}
