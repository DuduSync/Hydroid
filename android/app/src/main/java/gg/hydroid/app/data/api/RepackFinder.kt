package gg.hydroid.app.data.api

import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.model.GameRepack
import gg.hydroid.app.data.store.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private var refreshJob: Job? = null

    // a lista de fontes mudou (adicionou/removeu/registrou): esquece as contagens e
    // re-checa tudo sozinho (debounce: varias mudancas seguidas viram uma so rodada)
    fun refreshSoon() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(1500)
            synchronized(checked) { checked.clear() }
            _counts.value = emptyMap()
            synchronized(this@RepackFinder) { warmedUp = false }
            AppLog.i("Repacks", "fontes mudaram: re-checando contagens")
            warmUp()
        }
    }

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
        for (source in localSources.take(3)) {
            runCatching {
                val catalog = SourceFetcher.fetch(source.url, AppStore.appContext)
                catalog?.downloads?.forEach { repack ->
                    if (matches(gameName, repack.title)) {
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
            AppLog.i("Repacks", "warmUp: ${queue.size} jogos pra checar em background")
            queue.forEach { (id, name) -> runCheck(id, name) }
            AppLog.i("Repacks", "warmUp concluido: ${_counts.value.size} contagens em cache")
        }
    }

    private suspend fun runCheck(appId: Long, name: String) {
        synchronized(checked) {
            if (!checked.add(appId)) return
        }
        runCatching { find(name, appId).size }
            .onSuccess {
                AppLog.i("Repacks", "$name ($appId): $it downloads disponiveis")
                _counts.value = _counts.value + (appId to it)
            }
            .onFailure {
                AppLog.w("Repacks", "checagem falhou $name ($appId): ${it.message}")
                synchronized(checked) { checked.remove(appId) }
            }
    }

    // ---- casamento de titulo (fontes locais) ----    // "Watch Dogs" NAO pode casar com "Watch Dogs 2"/"Legion"; "The Witcher 3" casa com
    // "The Witcher 3: Wild Hunt"; "Watch Dogs" casa com "Watch Dogs Complete Edition"

    private val EDITION_WORDS = setOf(
        "complete", "completa", "definitive", "definitiva", "deluxe", "ultimate", "gold", "goty",
        "game", "of", "the", "year", "edition", "edicao", "enhanced", "remastered", "remaster",
        "collection", "anthology", "trilogy", "bundle", "premium", "legendary", "anniversary",
        "final", "cut", "standard", "legacy", "classic", "classics", "plus", "and", "e"
    )
    private val ROMAN = setOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")

    private fun tokens(s: String): List<String> =
        s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(" ").filter { it.isNotBlank() }

    // nome do jogo = parte antes do primeiro "(" ou "[" - o resto e versao/repack
    private fun namePart(title: String): String =
        title.substringBefore('(').substringBefore('[').trim()

    private fun matches(game: String, repackTitle: String): Boolean {
        val g = tokens(namePart(game).ifBlank { game })
        val r = tokens(namePart(repackTitle).ifBlank { repackTitle })
        if (g.isEmpty() || r.isEmpty()) return false
        val common = minOf(g.size, r.size)
        if (g.take(common) != r.take(common)) return false
        // extras do jogo (subtitulo) ok; numero/romano = jogo diferente
        if (g.drop(common).any { it.toIntOrNull() != null || it in ROMAN }) return false
        // extras do repack: so palavras de edicao (2, Legion, etc. barram aqui)
        return r.drop(common).all { it in EDITION_WORDS }
    }
}
