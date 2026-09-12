package gg.hydroid.app.data.api

import android.util.Base64
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.model.DownloadSource
import gg.hydroid.app.data.model.HydraAuth
import gg.hydroid.app.data.model.HydraRemoteGame
import gg.hydroid.app.data.model.HydraRemoteSource
import gg.hydroid.app.data.model.HydraUser
import gg.hydroid.app.data.model.LibraryGame
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object HydraAccountApi {
    const val AUTH_PAGE = "https://auth.hydra.losbroxas.org/?lng=pt-BR"
    private const val API = "https://hydra-api-us-east-1.losbroxas.org"
    private const val UA = "Hydra Launcher v4.1.3"
    private const val EXPIRATION_MARGIN_MS = 5 * 60 * 1000L

    private val json = JsonCfg.json

    // login nativo: POST /auth/signin {login, password}
    suspend fun signIn(login: String, password: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("login", login.trim())
                put("password", password)
                put("isLernaAuth", false)
            }.toString().toRequestBody(JsonCfg.mediaType)
            val req = Request.Builder().url("$API/auth/signin").post(body)
                .header("User-Agent", UA).build()
            HttpClient.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content
                    }.getOrNull()
                    return@withContext when (msg) {
                        "IncorrectUserOrPassword" -> "Login ou senha incorretos"
                        "OAuthAccountMismatch" -> "Conta vinculada a outro provedor"
                        else -> "Falha no login (${resp.code})"
                    }
                }
                val obj = json.parseToJsonElement(text).jsonObject
                val access = obj["accessToken"]?.jsonPrimitive?.content ?: ""
                val refresh = obj["refreshToken"]?.jsonPrimitive?.content ?: ""
                val expiresIn = obj["expiresIn"]?.jsonPrimitive?.longOrNull ?: 0L
                if (access.isBlank() || refresh.isBlank()) return@withContext "Resposta de login inválida"
                AppStore.saveHydraAuth(
                    HydraAuth(
                        accessToken = access,
                        refreshToken = refresh,
                        expiration = System.currentTimeMillis() + expiresIn * 1000L - EXPIRATION_MARGIN_MS
                    )
                )
                AppLog.i("Hydra", "login nativo ok")
                null
            }
        }.getOrElse { "Erro de conexão: ${it.message}" }
    }

    private suspend fun authedPatch(path: String, body: String): Boolean = withContext(Dispatchers.IO) {
        refreshIfNeeded()
        val token = AppStore.hydraAuth.value?.accessToken ?: return@withContext false
        runCatching {
            val req = Request.Builder().url("$API$path")
                .patch(body.toRequestBody(JsonCfg.mediaType))
                .header("User-Agent", UA)
                .header("Authorization", "Bearer $token")
                .build()
            HttpClient.client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    // resolve links de hosters no servidor do Hydra (datanodes, vikingfile...) - precisa de conta
    suspend fun unlockHoster(path: String, url: String): String? = withContext(Dispatchers.IO) {
        refreshIfNeeded()
        val token = AppStore.hydraAuth.value?.accessToken ?: return@withContext null
        runCatching {
            val body = buildJsonObject { put("url", url) }.toString().toRequestBody(JsonCfg.mediaType)
            val req = Request.Builder().url("$API$path").post(body)
                .header("User-Agent", UA)
                .header("Authorization", "Bearer $token")
                .build()
            HttpClient.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                AppLog.i("Hoster", "unlock $path -> ${resp.code}")
                if (!resp.isSuccessful) return@runCatching null
                json.parseToJsonElement(text).jsonObject["link"]?.jsonPrimitive?.content
            }
        }.onFailure { AppLog.w("Hoster", "unlock $path falhou: ${it.message}") }.getOrNull()
    }

    suspend fun setVisibility(profile: String? = null, souvenirs: String? = null): Boolean {
        val body = buildJsonObject {
            profile?.let { put("profileVisibility", it) }
            souvenirs?.let { put("souvenirsVisibility", it) }
        }.toString()
        val ok = authedPatch("/profile", body)
        AppLog.i("Hydra", "visibilidade: perfil=${profile ?: "-"} souvenirs=${souvenirs ?: "-"} ok=$ok")
        return ok
    }

    suspend fun setAllowCloudGifts(value: Boolean): Boolean {
        val ok = authedPatch("/profile", buildJsonObject { put("allowCloudGifts", value) }.toString())
        AppLog.i("Hydra", "permitir presentes=$value ok=$ok")
        return ok
    }

    suspend fun blocksCount(): Int {
        val body = authedGet("/profile/blocks") ?: return 0
        return runCatching {
            json.parseToJsonElement(body).jsonObject["totalBlocks"]?.jsonPrimitive?.intOrNull ?: 0
        }.getOrDefault(0)
    }

    // token de pagamento para abrir o checkout no navegador
    suspend fun checkoutUrl(): String? = withContext(Dispatchers.IO) {
        refreshIfNeeded()
        val refresh = AppStore.hydraAuth.value?.refreshToken ?: return@withContext null
        runCatching {            val body = buildJsonObject { put("refreshToken", refresh) }
                .toString().toRequestBody(JsonCfg.mediaType)
            val req = Request.Builder().url("$API/auth/payment").post(body)
                .header("User-Agent", UA).build()
            HttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val obj = json.parseToJsonElement(resp.body!!.string()).jsonObject
                val token = obj["accessToken"]?.jsonPrimitive?.content ?: return@runCatching null
                "https://checkout.hydralauncher.gg?token=$token"
            }
        }.getOrNull().also { AppLog.i("Hydra", "checkout: ${if (it != null) "url gerada" else "falhou"}") }
    }

    // callback do site de auth: hydralauncher://auth?payload=base64(json)
    suspend fun handleAuthUri(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = android.net.Uri.parse(uri).getQueryParameter("payload")
                ?: return@runCatching false
            val decoded = String(Base64.decode(payload, Base64.DEFAULT))
            val obj = json.parseToJsonElement(decoded).jsonObject
            val accessToken = obj["accessToken"]?.jsonPrimitive?.content ?: ""
            val refreshToken = obj["refreshToken"]?.jsonPrimitive?.content ?: ""
            val expiresIn = obj["expiresIn"]?.jsonPrimitive?.longOrNull ?: 0L
            if (accessToken.isBlank() || refreshToken.isBlank()) return@runCatching false
            AppStore.saveHydraAuth(
                HydraAuth(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiration = System.currentTimeMillis() + expiresIn * 1000L - EXPIRATION_MARGIN_MS
                )
            )
            AppLog.i("Hydra", "login recebido via auth window")
            true
        }.getOrDefault(false)
    }

    private fun refreshIfNeeded() {
        val auth = AppStore.hydraAuth.value ?: return
        if (auth.expiration > System.currentTimeMillis()) return
        runCatching {
            val body = """{"refreshToken":"${auth.refreshToken}"}""".toRequestBody(JsonCfg.mediaType)
            val req = Request.Builder().url("$API/auth/refresh").post(body)
                .header("User-Agent", UA).build()
            HttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.w("Hydra", "refresh falhou: ${resp.code}")
                    return@runCatching
                }
                val obj = json.parseToJsonElement(resp.body!!.string()).jsonObject
                val newToken = obj["accessToken"]?.jsonPrimitive?.content ?: return@runCatching
                val expiresIn = obj["expiresIn"]?.jsonPrimitive?.longOrNull ?: 0L
                AppStore.saveHydraAuth(
                    auth.copy(
                        accessToken = newToken,
                        expiration = System.currentTimeMillis() + expiresIn * 1000L - EXPIRATION_MARGIN_MS
                    )
                )
                AppLog.i("Hydra", "token renovado")
            }
        }.onFailure { AppLog.e("Hydra", "erro renovando token", it) }
    }

    private suspend fun authedGet(path: String): String? = withContext(Dispatchers.IO) {
        refreshIfNeeded()
        val token = AppStore.hydraAuth.value?.accessToken ?: return@withContext null
        runCatching {
            val req = Request.Builder().url("$API$path")
                .header("User-Agent", UA)
                .header("Authorization", "Bearer $token")
                .build()
            HttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.w("Hydra", "GET $path -> ${resp.code}")
                    return@runCatching null
                }
                resp.body?.string()
            }
        }.onFailure { AppLog.e("Hydra", "GET $path falhou", it) }.getOrNull()
    }

    suspend fun profile(): HydraUser? {
        val body = authedGet("/profile/me") ?: return null
        val user = runCatching { json.decodeFromString<HydraUser>(body) }.getOrNull() ?: return null
        AppStore.saveHydraUser(user)
        AppLog.i("Hydra", "perfil carregado: ${user.email ?: user.displayName ?: "?"} (visibilidade=${user.profileVisibility ?: "?"})")
        return user
    }

    suspend fun remoteGames(): List<HydraRemoteGame> {
        val all = mutableListOf<HydraRemoteGame>()
        var skip = 0
        while (true) {
            val page = authedGet("/profile/games?take=100&skip=$skip") ?: break
            val items = runCatching {
                json.decodeFromString<List<HydraRemoteGame>>(page)
            }.getOrNull() ?: break
            all += items
            if (items.size < 100) break
            skip += 100
        }
        return all
    }

    suspend fun remoteSources(): List<HydraRemoteSource> {
        val body = authedGet("/profile/download-sources") ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<HydraRemoteSource>>(body)
        }.getOrDefault(emptyList())
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            runCatching {
                val token = AppStore.hydraAuth.value?.accessToken ?: return@runCatching
                val req = Request.Builder().url("$API/auth/logout")
                    .post("{}".toRequestBody(JsonCfg.mediaType))
                    .header("User-Agent", UA)
                    .header("Authorization", "Bearer $token")
                    .build()
                HttpClient.client.newCall(req).execute().close()
            }
        }
        AppStore.clearHydraAuth()
        AppLog.i("Hydra", "logout")
    }

    // sincroniza a biblioteca da conta para o aparelho (steam)
    suspend fun syncLibrary(): Int {
        var added = 0
        for (g in remoteGames()) {
            if (g.shop.isNotBlank() && g.shop != "steam") continue
            val appId = g.objectId.toLongOrNull() ?: continue
            if (AppStore.isInLibrary(appId)) continue
            val image = g.coverImageUrl ?: g.libraryImageUrl
                ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
            AppStore.addToLibrary(
                LibraryGame(appId = appId, name = g.title.ifBlank { "App $appId" }, headerImage = image)
            )
            added++
        }
        if (added > 0) AppLog.i("Hydra", "biblioteca sincronizada: +$added")
        return added
    }

    // sincroniza as fontes de download vinculadas a conta; -1 = recurso requer Hydra Cloud ativo
    suspend fun syncSources(): Int {
        val body = authedGet("/profile/download-sources") ?: return 0
        if (!body.trimStart().startsWith("[")) return -1
        val list = runCatching {
            json.decodeFromString<List<HydraRemoteSource>>(body)
        }.getOrDefault(emptyList())
        var added = 0
        for (s in list) {
            if (s.url.isBlank()) continue
            if (AppStore.sources.value.any { it.url == s.url }) continue
            AppStore.addSource(
                DownloadSource(
                    id = s.id.ifBlank { s.url.hashCode().toString() },
                    name = s.name?.takeIf { it.isNotBlank() } ?: "Fonte da conta",
                    url = s.url
                )
            )
            added++
        }
        if (added > 0) AppLog.i("Hydra", "fontes sincronizadas: +$added")
        return added
    }
}
