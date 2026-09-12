package gg.hydroid.app.data.api

import gg.hydroid.app.data.model.SteamFeaturedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// home do catalogo (Steam featuredcategories) com cache no processo: pré-carregada no boot
object FeaturedCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _data = MutableStateFlow<Map<String, List<SteamFeaturedItem>>>(emptyMap())
    val data: StateFlow<Map<String, List<SteamFeaturedItem>>> = _data
    private val _loaded = MutableStateFlow(false)
    private var started = false

    fun ensureLoaded() {
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            val f = runCatching { SteamApi.featured() }.getOrDefault(emptyMap())
            if (f.isNotEmpty()) _data.value = f
            _loaded.value = true
        }
    }

    suspend fun await(): Map<String, List<SteamFeaturedItem>> {
        _loaded.first { it }
        return _data.value
    }
}
