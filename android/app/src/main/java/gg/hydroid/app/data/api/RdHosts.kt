package gg.hydroid.app.data.api

import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

// lista oficial de hosts suportados pelo Real-Debrid (endpoint /hosts/domains com a chave
// do usuario): usada pra marcar "Recomendado" no metodo certo do sheet de downloads
object RdHosts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _domains = MutableStateFlow<Set<String>>(emptySet())
    val domains: StateFlow<Set<String>> = _domains
    private var loaded = false
    private var loading = false

    // pre-carrega no boot (assim a tag "Recomendado" nasce junto com o botao, sem pular)
    fun warmUp() {
        scope.launch { ensureLoaded() }
    }

    // chave trocada/removida: recarrega do zero
    fun invalidate() {
        loaded = false
        _domains.value = emptySet()
        warmUp()
    }

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        val key = AppStore.rdApiKey.value
        if (loaded || loading || key.isBlank()) return@withContext
        loading = true
        runCatching {
            val req = Request.Builder()
                .url("https://api.real-debrid.com/rest/1.0/hosts/domains")
                .header("Authorization", "Bearer ${key.trim()}")
                .build()
            HttpClient.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val list = JsonCfg.json.decodeFromString<List<String>>(resp.body?.string().orEmpty())
                    _domains.value = list.map { it.lowercase() }.toSet()
                    loaded = true
                    AppLog.i("Debrid", "RD: ${list.size} hosts suportados em cache")
                } else {
                    AppLog.w("Debrid", "lista de hosts do RD: HTTP ${resp.code}")
                }
            }
        }.onFailure { AppLog.w("Debrid", "lista de hosts do RD falhou: ${it.message}") }
        loading = false
    }

    fun supports(host: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        if (h.isBlank()) return false
        val list = _domains.value
        if (list.isEmpty()) return false
        return list.any { it == h || h.endsWith(".$it") }
    }
}
