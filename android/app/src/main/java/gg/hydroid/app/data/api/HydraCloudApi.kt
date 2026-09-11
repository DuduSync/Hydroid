package gg.hydroid.app.data.api

import gg.hydroid.app.data.model.DownloadSource
import gg.hydroid.app.data.model.GameRepack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request

// API oficial do Hydra (mesma usada pelo launcher desktop, MIT igual ao fork)
// Endpoint de repacks aceita arrays como downloadSourceIds[]=id (serializacao axios)
object HydraCloudApi {
    private const val BASE = "https://hydra-api-us-east-1.losbroxas.org"
    private const val UA = "Hydra Launcher v4.1.3"

    suspend fun registerSource(url: String): DownloadSource? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JsonCfg.json.encodeToString(mapOf("url" to url))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$BASE/download-sources")
                .header("User-Agent", UA)
                .post(body)
                .build()
            HttpClient.client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: error("HTTP ${resp.code}")
                if (!resp.isSuccessful) error("register source HTTP ${resp.code}: $raw")
                JsonCfg.json.decodeFromString<DownloadSource>(raw)
            }
        }.onFailure { android.util.Log.e("HydroidApi", "registerSource $url: ${it.message}") }
            .getOrNull()
    }

    suspend fun repacks(shop: String, objectId: String, sourceIds: List<String>): List<GameRepack> =
        withContext(Dispatchers.IO) {
            if (sourceIds.isEmpty()) return@withContext emptyList()
            val qs = buildString {
                append("?take=100&skip=0")
                sourceIds.forEach { id ->
                    append("&downloadSourceIds%5B%5D=")
                    append(java.net.URLEncoder.encode(id, "UTF-8"))
                }
            }
            val req = Request.Builder()
                .url("$BASE/games/$shop/$objectId/download-sources$qs")
                .header("User-Agent", UA)
                .build()
            runCatching {
                HttpClient.client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: error("HTTP ${resp.code}")
                    JsonCfg.json.decodeFromString<List<GameRepack>>(raw)
                }
            }.onFailure { android.util.Log.e("HydroidApi", "repacks: ${it.message}") }
                .getOrDefault(emptyList())
        }

    // registra fonte remota e devolve DownloadSource com hydraId preenchido
    suspend fun addRemoteSource(url: String): DownloadSource? {
        val registered = registerSource(url) ?: return null
        return registered
    }
}
