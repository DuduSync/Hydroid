package gg.hydroid.app.download

import gg.hydroid.app.data.api.HydraAccountApi
import gg.hydroid.app.data.api.HttpClient
import gg.hydroid.app.data.api.JsonCfg
import gg.hydroid.app.data.i18n.tf
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

// resolve links de hosters para o metodo "Direto" (sem debrid).
// portado do Hydra desktop (src/main/services/hosters/*.ts).
object HostResolver {
    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"

    sealed class Result {
        data class Ok(val url: String, val host: String) : Result()
        data class Fail(val host: String, val message: String) : Result()
    }

    // hosts que exigem fluxo proprietario: melhor erro claro do que baixar HTML
    private val blocked = listOf(
        "1fichier.com" to tf("1fichier: use Abrir no navegador ou Real-Debrid/TorBox"),
        "mega.nz" to tf("MEGA não é suportado no direto (use Real-Debrid/TorBox)"),
        "mega.io" to tf("MEGA não é suportado no direto (use Real-Debrid/TorBox)")
    )

    suspend fun resolve(uri: String): Result {
        val host = runCatching { java.net.URI(uri).host?.lowercase() ?: "" }.getOrDefault("")
        return try {
            when {
                host.endsWith("gofile.io") || host.endsWith("gofile.com") -> gofile(uri)
                host.endsWith("pixeldrain.com") -> pixeldrain(uri)
                host.endsWith("mediafire.com") -> mediafire(uri)
                host.endsWith("fuckingfast.co") -> fuckingfast(uri)
                host.endsWith("rootz.so") -> rootz(uri)
                host.endsWith("datanodes.to") -> hydraUnlock("datanodes.to", "/hosters/datanodes/unlock", uri)
                host.endsWith("vikingfile.com") -> hydraUnlock("vikingfile.com", "/hosters/vikingfile/unlock", uri)
                blocked.any { host.endsWith(it.first) } -> {
                    val msg = blocked.first { host.endsWith(it.first) }.second
                    Result.Fail(host, msg)
                }
                else -> Result.Ok(uri, "")   // host desconhecido: baixa direto (comportamento antigo)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // cancelamento nao vira erro de hoster
        } catch (e: Exception) {
            AppLog.e("Hoster", "$host: falha resolvendo ${uri.take(90)}", e)
            Result.Fail(host, e.message ?: tf("%s: não consegui resolver o link", host))
        }
    }

    // ---- gofile: CDN alternativa serve o arquivo sem o token JS do site ----
    private suspend fun gofile(uri: String): Result {
        val id = when {
            uri.contains("/d/") -> idAfter(uri, "/d/")
            else -> uri.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
        } ?: return Result.Fail("gofile.io", tf("%s: link inválido", "Gofile"))
        val cdn = "https://gofilecdn.eu.cc/$id"
        return if (probe(cdn)) {
            AppLog.i("Hoster", "gofile $id -> CDN alternativa")
            Result.Ok(cdn, "gofile.io")
        } else {
            Result.Fail("gofile.io", tf("%s: acesso recusado (conteúdo privado/expirado?) - tente Real-Debrid", "Gofile"))
        }
    }

    // ---- pixeldrain: API publica (com CDN alternativa quando disponivel) ----
    private suspend fun pixeldrain(uri: String): Result {
        val id = idAfter(uri, "/u/")
            ?: uri.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
            ?: return Result.Fail("pixeldrain.com", tf("%s: link inválido", "PixelDrain"))
        val cdn = "https://cdn.pixeldrain.eu.cc/$id"
        if (probe(cdn)) {
            AppLog.i("Hoster", "pixeldrain $id -> CDN alternativa")
            return Result.Ok(cdn, "pixeldrain.com")
        }
        AppLog.i("Hoster", "pixeldrain $id -> API oficial")
        return Result.Ok("https://pixeldrain.com/api/file/$id?download", "pixeldrain.com")
    }

    // ---- mediafire: link direto escondido no HTML da pagina ----
    private suspend fun mediafire(uri: String): Result {
        val page = get(uri)
        Regex("""(?<=['"])https?://download\d+\.mediafire\.com/[^'"]+(?=['"])""")
            .find(page)?.value?.let { return Result.Ok(it, "mediafire.com") }
        val pre = Regex("""(?<=['"])(https?:)?(//)?(www\.)?mediafire\.com/(file|view|download)/[^'"?]+\?dkey=[^'"]+(?=['"])""")
            .find(page)?.value?.let { if (it.startsWith("//")) "https:$it" else it }
        return pre?.let { Result.Ok(it, "mediafire.com") }
            ?: Result.Fail("mediafire.com", tf("%s: link direto não encontrado", "MediaFire"))
    }

    // ---- fuckingfast: link no window.open da pagina ----
    private suspend fun fuckingfast(uri: String): Result {
        val html = get(uri)
        if (html.contains("rate limit", ignoreCase = true)) {
            return Result.Fail("fuckingfast.co", tf("%s: limite de requisições, espere alguns minutos", "FuckingFast"))
        }
        if (html.contains("File Not Found Or Deleted")) {
            return Result.Fail("fuckingfast.co", tf("%s: arquivo não encontrado ou apagado", "FuckingFast"))
        }
        val m = Regex("""window\.open\("(https://fuckingfast\.co/dl/[^"]*)""").find(html)
        return m?.groupValues?.get(1)?.let { Result.Ok(it, "fuckingfast.co") }
            ?: Result.Fail("fuckingfast.co", tf("%s: link direto não encontrado", "FuckingFast"))
    }

    // ---- rootz: token da pagina + API de download ----
    private suspend fun rootz(uri: String): Result {
        val id = idAfter(uri, "/d/")
            ?: return Result.Fail("rootz.so", tf("%s: link inválido", "Rootz"))
        val pageUrl = "https://www.rootz.so/d/$id"
        val page = get(pageUrl, mapOf("Accept" to "text/html"))
        val token = Regex("""\\?"pageToken\\?"\s*:\s*\\?"([^"\\]+)""")
            .find(page)?.groupValues?.get(1)
            ?: return Result.Fail("rootz.so", tf("%s: link direto não encontrado", "Rootz"))
        val json = get(
            "https://www.rootz.so/api/files/download-by-short?shortId=$id",
            mapOf(
                "Accept" to "application/json",
                "Referer" to pageUrl,
                "X-Page-Token" to token
            )
        )
        val obj = JsonCfg.json.parseToJsonElement(json).jsonObject
        val link = obj["data"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        return link?.let { Result.Ok(it, "rootz.so") }
            ?: Result.Fail(
                "rootz.so",
                obj["error"]?.jsonPrimitive?.contentOrNull
                    ?: tf("%s: link direto não encontrado", "Rootz")
            )
    }

    // ---- datanodes / vikingfile: desbloqueio no servidor do Hydra (precisa de conta) ----
    private suspend fun hydraUnlock(host: String, path: String, uri: String): Result {
        if (AppStore.hydraAuth.value == null) {
            return Result.Fail(host, tf("%s: precisa da conta Hydra (Ajustes > Conta) ou Real-Debrid", host))
        }
        val link = HydraAccountApi.unlockHoster(path, uri)
        return link?.let { Result.Ok(it, host) }
            ?: Result.Fail(host, tf("%s: não consegui desbloquear (link expirado ou recurso da conta)", host))
    }

    // ---- helpers ----

    private fun idAfter(uri: String, marker: String): String? =
        uri.substringAfter(marker, "").substringBefore('/').substringBefore('?')
            .takeIf { it.isNotBlank() }

    private suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
            headers.forEach { (k, v) -> b.header(k, v) }
            HttpClient.client.newCall(b.build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        }

    // GET de 1 byte (alguns CDNs nao respondem HEAD); html = link invalido
    private suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val rq = Request.Builder().url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Range", "bytes=0-0")
                .build()
            HttpClient.client.newCall(rq).execute().use { r ->
                val type = r.header("Content-Type") ?: ""
                (r.isSuccessful || r.isRedirect) && !type.contains("text/html")
            }
        }.getOrDefault(false)
    }
}
