package gg.hydroid.app.download

import gg.hydroid.app.data.i18n.tr


import gg.hydroid.app.data.api.HttpClient
import gg.hydroid.app.data.api.JsonCfg
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

// resolucao de links pelos servicos debrid alternativos (beta)
object DebridClouds {
    private val json = JsonCfg.json

    private fun http(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: RequestBody? = null
    ): String {
        val b = Request.Builder().url(url).header("User-Agent", "Hydroid v0.4")
        headers.forEach { (k, v) -> b.header(k, v) }
        when (method) {
            "POST" -> b.post(body ?: "".toRequestBody())
            "PATCH" -> b.patch(body ?: "".toRequestBody())
        }
        HttpClient.client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return text
        }
    }

    private fun formBody(vararg pairs: Pair<String, String>) =
        FormBody.Builder().apply { pairs.forEach { add(it.first, it.second) } }.build()

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // ---------- Premiumize ----------
    suspend fun premiumize(key: String, uri: String): String {
        val api = "https://www.premiumize.me/api"
        if (uri.startsWith("magnet:")) {
            val created = json.parseToJsonElement(
                http("$api/transfer/create", "POST", body = formBody("apikey" to key, "src" to uri))
            ).jsonObject
            if (created["status"]?.jsonPrimitive?.content != "success") {
                error(created["message"]?.jsonPrimitive?.content ?: "Falha ao enviar o magnet")
            }
            val transferId = created["id"]?.jsonPrimitive?.content
            repeat(180) {
                val list = json.parseToJsonElement(http("$api/transfer/list?apikey=$key")).jsonObject
                val transfers = list["transfers"]?.jsonArray ?: JsonArray(emptyList())
                val t = transfers.map { it.jsonObject }.firstOrNull {
                    it["id"]?.jsonPrimitive?.content == transferId
                }
                when (t?.get("status")?.jsonPrimitive?.content) {
                    "finished" -> {
                        val dl = json.parseToJsonElement(
                            http("$api/transfer/directdl", "POST", body = formBody("apikey" to key, "src" to uri))
                        ).jsonObject
                        return pickLargest(dl)
                    }
                    "error" -> error(tr("Premiumize não conseguiu processar o torrent"))
                }
                delay(5000)
            }
            error(tr("Timeout aguardando o Premiumize"))
        }
        val dl = json.parseToJsonElement(
            http("$api/transfer/directdl", "POST", body = formBody("apikey" to key, "src" to uri))
        ).jsonObject
        if (dl["status"]?.jsonPrimitive?.content != "success") {
            error(dl["message"]?.jsonPrimitive?.content ?: "Premiumize não aceitou o link")
        }
        return pickLargest(dl)
    }

    private fun pickLargest(dl: JsonObject): String {
        val content = dl["content"]?.jsonArray ?: JsonArray(emptyList())
        val files = content.map { it.jsonObject }
        val best = files.maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
            ?: error(tr("Premiumize não retornou arquivos"))
        return best["link"]?.jsonPrimitive?.content ?: error(tr("Premiumize sem link direto"))
    }

    // ---------- AllDebrid ----------
    suspend fun alldebrid(key: String, uri: String): String {
        val api = "https://api.alldebrid.com/v4"
        if (uri.startsWith("magnet:")) {
            val up = json.parseToJsonElement(
                http("$api/magnet/upload?agent=Hydroid&apikey=$key&magnets[]=${encode(uri)}")
            ).jsonObject
            if (up["status"]?.jsonPrimitive?.content != "success") {
                error(up["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "AllDebrid recusou o magnet")
            }
            val id = up["data"]?.jsonObject?.get("magnets")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("id")?.jsonPrimitive?.content ?: error(tr("AllDebrid sem id do magnet"))
            repeat(180) {
                val st = json.parseToJsonElement(
                    http("$api/magnet/status?agent=Hydroid&apikey=$key&id=$id")
                ).jsonObject
                if (st["status"]?.jsonPrimitive?.content != "success") {
                    error(st["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "AllDebrid recusou")
                }
                val magnet = st["data"]?.jsonObject?.get("magnets")?.jsonObject
                when (magnet?.get("status")?.jsonPrimitive?.content) {
                    "Ready" -> {
                        val links = magnet["links"]?.jsonPrimitive?.content ?: error(tr("Sem links"))
                        val first = links.split("\n").firstOrNull { it.startsWith("http") }
                            ?: error(tr("Sem links válidos"))
                        return unlock(key, first)
                    }
                    "Error" -> error(tr("AllDebrid não conseguiu processar o torrent"))
                }
                delay(5000)
            }
            error(tr("Timeout aguardando o AllDebrid"))
        }
        return unlock(key, uri)
    }

    private fun unlock(key: String, link: String): String {
        val res = json.parseToJsonElement(
            http("https://api.alldebrid.com/v4/link/unlock?agent=Hydroid&apikey=$key&link=${encode(link)}")
        ).jsonObject
        if (res["status"]?.jsonPrimitive?.content != "success") {
            error(res["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "AllDebrid não liberou o link")
        }
        return res["data"]?.jsonObject?.get("link")?.jsonPrimitive?.content
            ?: error(tr("AllDebrid sem link direto"))
    }

    // ---------- TorBox ----------
    suspend fun torbox(key: String, uri: String): String {
        val api = "https://api.torbox.app/v1/api"
        val auth = mapOf("Authorization" to "Bearer $key")
        if (uri.startsWith("magnet:")) {
            val created = json.parseToJsonElement(
                http(
                    "$api/torrents/createtorrent", "POST", auth,
                    body = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("magnet", uri).build()
                )
            ).jsonObject
            if (created["success"]?.jsonPrimitive?.content != "true") {
                error(created["detail"]?.jsonPrimitive?.content ?: "TorBox recusou o magnet")
            }
            val torrentId = created["data"]?.jsonObject?.get("torrent_id")?.jsonPrimitive?.intOrNull
                ?: error(tr("TorBox sem torrent_id"))
            repeat(180) {
                val list = json.parseToJsonElement(
                    http("$api/torrents/mylist?bypass_cache=true&id=$torrentId", headers = auth)
                ).jsonObject
                val item = list["data"]?.jsonArray?.firstOrNull()?.jsonObject
                when (item?.get("download_state")?.jsonPrimitive?.content) {
                    "completed", "uploading", "cached" -> {
                        val fileId = biggestFileId(item)
                        return requestDl("$api/torrents/requestdl?token=$key&torrent_id=$torrentId&file_id=$fileId")
                    }
                    "paused", "error", "stalled" -> error(tr("TorBox não conseguiu processar o torrent"))
                }
                delay(5000)
            }
            error(tr("Timeout aguardando o TorBox"))
        }
        val created = json.parseToJsonElement(
            http(
                "$api/webdl/createwebdownload", "POST", auth,
                body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("link", uri).build()
            )
        ).jsonObject
        if (created["success"]?.jsonPrimitive?.content != "true") {
            error(created["detail"]?.jsonPrimitive?.content ?: "TorBox recusou o link")
        }
        val webId = created["data"]?.jsonObject?.get("webdownload_id")?.jsonPrimitive?.intOrNull
            ?: error(tr("TorBox sem webdownload_id"))
        repeat(180) {
            val list = json.parseToJsonElement(
                http("$api/webdl/mylist?bypass_cache=true&id=$webId", headers = auth)
            ).jsonObject
            val item = list["data"]?.jsonArray?.firstOrNull()?.jsonObject
            when (item?.get("download_state")?.jsonPrimitive?.content) {
                "completed", "uploading", "cached" -> {
                    val fileId = biggestFileId(item)
                    return requestDl("$api/webdl/requestdl?token=$key&web_id=$webId&file_id=$fileId")
                }
                "paused", "error", "stalled" -> error(tr("TorBox não conseguiu processar o link"))
            }
            delay(5000)
        }
        error(tr("Timeout aguardando o TorBox"))
    }

    private fun biggestFileId(item: JsonObject): Int = item["files"]?.jsonArray
        ?.map { it.jsonObject }
        ?.maxByOrNull { it["size"]?.jsonPrimitive?.longOrNull ?: 0L }
        ?.get("id")?.jsonPrimitive?.intOrNull ?: 0

    private fun requestDl(url: String): String {
        val res = json.parseToJsonElement(http(url)).jsonObject
        if (res["success"]?.jsonPrimitive?.content != "true") {
            error(res["detail"]?.jsonPrimitive?.content ?: "TorBox sem link direto")
        }
        return res["data"]?.jsonPrimitive?.content ?: error(tr("TorBox sem link direto"))
    }
}
