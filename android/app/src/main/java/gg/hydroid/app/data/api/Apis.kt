package gg.hydroid.app.data.api

import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object JsonCfg {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val mediaType = "application/json".toMediaType()
}

object HttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLogger)
        .build()

    fun get(url: String, builder: Request.Builder.() -> Unit = {}): Request =
        Request.Builder().url(url).apply(builder).build()
}

// loga TODA requisicao HTTP (metodo, host+caminho, codigo, tempo, bytes) — query so com os nomes das chaves
private object HttpLogger : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val rq = chain.request()
        val started = System.currentTimeMillis()
        val resp = try {
            chain.proceed(rq)
        } catch (e: Exception) {
            AppLog.w("HTTP", "${rq.method} ${safeUrl(rq.url.toString())} falhou: ${e.message}")
            throw e
        }
        val ms = System.currentTimeMillis() - started
        val bytes = resp.body?.contentLength() ?: -1L
        AppLog.i("HTTP", "${rq.method} ${safeUrl(rq.url.toString())} -> ${resp.code} ($bytes bytes, ${ms}ms)")
        return resp
    }

    // mantem host+caminho; na query loga so os nomes das chaves (valores podem ter chave de API)
    private fun safeUrl(url: String): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        val keys = url.substring(q + 1).split('&').joinToString(",") { it.substringBefore('=') }
        return url.substring(0, q) + "?$keys"
    }
}

suspend fun httpGetJson(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val request = HttpClient.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }
        HttpClient.client.newCall(request).execute().use { resp ->
            resp.body?.string() ?: error("HTTP ${resp.code}: corpo vazio")
        }
    }

object SteamApi {
    suspend fun search(term: String): List<SteamSearchItem> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        val url = "https://store.steampowered.com/api/storesearch/" +
            "?term=${java.net.URLEncoder.encode(term, "UTF-8")}&cc=BR&l=en"
        runCatching { JsonCfg.json.decodeFromString<SteamSearchResponse>(httpGetJson(url)).items }
            .getOrDefault(emptyList())
    }

    // jogos em alta (populares / lancamentos / promocoes / em breve) — home do catalogo
    suspend fun featured(): Map<String, List<SteamFeaturedItem>> = withContext(Dispatchers.IO) {
        val url = "https://store.steampowered.com/api/featuredcategories?cc=BR&l=portuguese"
        val raw = runCatching { httpGetJson(url) }.getOrNull() ?: return@withContext emptyMap()
        runCatching {
            val r = JsonCfg.json.decodeFromString<SteamFeaturedResponse>(raw)
            mapOf(
                "top_sellers" to r.top_sellers?.items.orEmpty(),
                "new_releases" to r.new_releases?.items.orEmpty(),
                "specials" to r.specials?.items.orEmpty(),
                "coming_soon" to r.coming_soon?.items.orEmpty()
            ).mapValues { (_, v) -> v.filter { it.id > 0 && it.name.isNotBlank() }.take(20) }
        }.getOrDefault(emptyMap())
    }

    suspend fun appDetails(appId: Long): SteamAppDetails? = withContext(Dispatchers.IO) {
        val url = "https://store.steampowered.com/api/appdetails?appids=$appId&l=brazilian"
        val raw = runCatching { httpGetJson(url) }.getOrNull() ?: return@withContext null
        val wrapper = runCatching {
            JsonCfg.json.decodeFromString<Map<String, SteamAppDetailsWrapper>>(raw)
        }.getOrNull() ?: return@withContext null
        wrapper[appId.toString()]?.data
    }

    @kotlinx.serialization.Serializable
    data class SteamAppDetailsWrapper(val success: Boolean = false, val data: SteamAppDetails? = null)
}

object ProtonDbApi {
    // tier de compatibilidade (platinum/gold/silver/bronze) — endpoint publico do site
    suspend fun tier(appId: Long): ProtonTier? = withContext(Dispatchers.IO) {
        runCatching {
            val req = HttpClient.get("https://www.protondb.com/api/v1/reports/summaries/$appId.json") {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            }
            HttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                JsonCfg.json.decodeFromString<ProtonTier>(resp.body?.string().orEmpty())
            }
        }.onFailure { android.util.Log.e("HydroidApi", "protondb: ${it.message}") }.getOrNull()
    }
}

class RealDebridApi(private var apiKey: String) {
    private val base = "https://api.real-debrid.com/rest/1.0"

    private fun authHeaders() = mapOf("Authorization" to "Bearer $apiKey")

    suspend fun user(): RdUser {
        val raw = httpGetJson("$base/user", authHeaders())
        return JsonCfg.json.decodeFromString(raw)
    }

    suspend fun addMagnet(magnet: String): RdAddMagnetResponse = withContext(Dispatchers.IO) {
        val body = "magnet=${java.net.URLEncoder.encode(magnet, "UTF-8")}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = HttpClient.get("$base/torrents/addMagnet") {
            header("Authorization", "Bearer $apiKey")
            post(body)
        }
        HttpClient.client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string() ?: error("HTTP ${resp.code}")
            JsonCfg.json.decodeFromString(raw)
        }
    }

    suspend fun selectFiles(id: String): Boolean = withContext(Dispatchers.IO) {
        val body = "files=all".toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = HttpClient.get("$base/torrents/selectFiles/$id") {
            header("Authorization", "Bearer $apiKey")
            post(body)
        }
        HttpClient.client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun torrentInfo(id: String): RdTorrentInfo = withContext(Dispatchers.IO) {
        val raw = httpGetJson("$base/torrents/info/$id", authHeaders())
        JsonCfg.json.decodeFromString(raw)
    }

    suspend fun unrestrictLink(link: String): RdUnrestrictLink = withContext(Dispatchers.IO) {
        val body = "link=${java.net.URLEncoder.encode(link, "UTF-8")}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = HttpClient.get("$base/unrestrict/link") {
            header("Authorization", "Bearer $apiKey")
            post(body)
        }
        HttpClient.client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string() ?: error("HTTP ${resp.code}")
            if (!resp.isSuccessful) {
                val friendly = when {
                    raw.contains("hoster_unsupported") ->
                        "Hoster não suportado pela Real-Debrid — tente o método Direto ou outro repack"
                    raw.contains("bad_token") || raw.contains("bad_credentials") ->
                        "Chave Real-Debrid inválida — reconfigure em Ajustes"
                    raw.contains("unavailable_file") ->
                        "Arquivo indisponível no provedor"
                    raw.contains("permission_denied") || raw.contains("account_locked") ->
                        "Conta Real-Debrid sem permissão (premium?)"
                    else -> "Real-Debrid: $raw"
                }
                error(friendly)
            }
            JsonCfg.json.decodeFromString(raw)
        }
    }
}
