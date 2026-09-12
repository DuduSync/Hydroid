package gg.hydroid.app.data.api

import gg.hydroid.app.data.model.GameRepack
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// repacks de um jogo (Hydra Cloud + fontes locais) e cache da contagem por appId
// usado pela home do catalogo, pagina do jogo e biblioteca
object RepackFinder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _counts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val counts: StateFlow<Map<Long, Int>> = _counts
    private val checked = mutableSetOf<Long>()
    private var warmedUp = false

    suspend fun find(gameName: String, appId: Long): List<Pair<GameRepack, String>> {
        val sources = AppStore.sources.value
        if (sources.isEmpty() || gameName.isBlank()) return emptyList()
        val hydraIds = sources.filter { !it.id.startsWith("local-") }.map { it.id }
        val localSources = sources.filter { it.id.startsWith("local-") }
        val found = mutableListOf<Pair<GameRepack, String>>()

        if (hydraIds.isNotEmpty()) {
            HydraCloudApi.repacks("steam", appId.toString(), hydraIds)
                .forEach { found += it to it.downloadSourceName }
        }
        val target = normalizeTitle(gameName)
        for (source in localSources.take(3)) {
            runCatching {
                val catalog = SourceFetcher.fetch(source.url, AppStore.appContext)
                catalog?.downloads?.forEach { repack ->
                    val rp = normalizeTitle(repack.title)
                    if (rp.contains(target) || target.contains(rp.take(20))) {
                        found += GameRepack(
                            id = "local-${repack.title.hashCode()}",
                            title = repack.title,
                            fileSize = repack.fileSize,
                            uris = repack.uris,
                            uploadDate = repack.uploadDate
                        ) to source.name
                    }
                }
            }
        }
        return found.distinctBy { it.first.id + it.second }
    }

    // uso na UI: dispara a checagem de um card (dedup por appId dentro de runCheck)
    fun check(appId: Long, name: String) {
        scope.launch { runCheck(appId, name) }
    }

    // boot: monta a fila (biblioteca + todos os jogos da home) e checa em sequencia, em background
    fun warmUp() {
        synchronized(this) {
            if (warmedUp) return
            warmedUp = true
        }
        scope.launch {
            val queue = LinkedHashMap<Long, String>()
            AppStore.library.value.forEach { queue[it.appId] = it.name }
            FeaturedCache.await().values.flatten().distinctBy { it.id }
                .forEach { queue[it.id] = it.name }
            queue.forEach { (id, name) -> runCheck(id, name) }
        }
    }

    private suspend fun runCheck(appId: Long, name: String) {
        synchronized(checked) {
            if (!checked.add(appId)) return
        }
        runCatching { find(name, appId).size }
            .onSuccess { _counts.value = _counts.value + (appId to it) }
            .onFailure { synchronized(checked) { checked.remove(appId) } }
    }

    private fun normalizeTitle(t: String) =
        t.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
}
