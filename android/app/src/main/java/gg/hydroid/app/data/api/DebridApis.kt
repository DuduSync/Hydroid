package gg.hydroid.app.data.api

import gg.hydroid.app.data.i18n.tr


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request

// validacao das chaves dos servicos debrid suportados
object DebridApis {
    suspend fun validate(service: String, key: String): String = withContext(Dispatchers.IO) {
        when (service) {
            "rd" -> {
                val user = RealDebridApi(key).user()
                val premium = if (user.premium > 0) "premium (${user.premium / 86400} dias)" else "sem premium"
                "Conectado como ${user.username} - $premium"
            }
            "premiumize" -> premiumize(key)
            "alldebrid" -> alldebrid(key)
            "torbox" -> torbox(key)
            else -> error(tr("Serviço desconhecido"))
        }
    }

    private val json = JsonCfg.json

    private fun get(url: String, bearer: String? = null): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Hydroid v0.4")
            .apply { bearer?.let { header("Authorization", "Bearer $it") } }
            .build()
        HttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun premiumize(key: String): String {
        val obj = json.parseToJsonElement(
            get("https://www.premiumize.me/api/account/info?apikey=$key")
        ).jsonObject
        if (obj["status"]?.jsonPrimitive?.content != "success") {
            error(obj["message"]?.jsonPrimitive?.content ?: "chave inválida")
        }
        val until = obj["premium_until"]?.jsonPrimitive?.longOrNull ?: 0L
        val days = (until - System.currentTimeMillis() / 1000) / 86400
        return if (days > 0) "Conectado - premium ($days dias)" else "Conectado - sem premium"
    }

    private fun alldebrid(key: String): String {
        val obj = json.parseToJsonElement(
            get("https://api.alldebrid.com/v4/user?agent=Hydroid&apikey=$key")
        ).jsonObject
        if (obj["status"]?.jsonPrimitive?.content != "success") {
            val msg = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
            error(msg ?: "chave inválida")
        }
        val user = obj["data"]?.jsonObject?.get("user")?.jsonObject
        val name = user?.get("username")?.jsonPrimitive?.content ?: "usuário"
        val premium = user?.get("isPremium")?.jsonPrimitive?.content == "true"
        return "Conectado como $name - ${if (premium) "premium" else "sem premium"}"
    }

    private fun torbox(key: String): String {
        val obj = json.parseToJsonElement(
            get("https://api.torbox.app/v1/api/user/me", bearer = key)
        ).jsonObject
        if (obj["success"]?.jsonPrimitive?.content != "true") {
            error(obj["detail"]?.jsonPrimitive?.content ?: "chave inválida")
        }
        val data = obj["data"]?.jsonObject
        val email = data?.get("email")?.jsonPrimitive?.content ?: "usuário"
        val plan = data?.get("plan")?.jsonPrimitive?.intOrNull ?: 0
        val planName = when (plan) {
            1 -> "Essential"
            2 -> "Pro"
            3 -> "Standard"
            else -> "Free"
        }
        return "Conectado como $email - plano $planName"
    }
}
