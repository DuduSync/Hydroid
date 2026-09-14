package gg.hydroid.app.data.api

import android.content.Context
import gg.hydroid.app.data.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

// loja de fontes: catalogo que O USUARIO COLA (link da loja). O app nao carrega nem
// divulga link nenhum de loja; quem informa e o usuario. Cache local da ultima lista.
// ATENCAO: a API da Hydra Library responde 500 com User-Agent de navegador, usamos o padrao
object SourceStore {
    // link colado pelo usuario; sem query, assume a paginacao da Hydra Library (compativel)
    private fun storeUrl(raw: String): String {
        val u = raw.trim()
        return if (u.contains("?")) u else "$u?page=1&limit=100"
    }

    @Serializable
    data class Stats(
        val installs: Long? = null,
        val copies: Long? = null,
        val recentActivity: Long? = null
    )

    @Serializable
    data class Rating(val avg: Double? = null, val total: Int? = null)

    @Serializable
    data class DownloadOption(val name: String? = null, val count: Int? = null)

    @Serializable
    data class StoreSource(
        val id: Int = 0,
        val title: String? = null,
        val description: String? = null,
        val url: String? = null,
        val gamesCount: Int = 0,
        val status: List<String>? = null,
        val addedDate: String? = null,
        val stats: Stats? = null,
        val rating: Rating? = null,
        val topDownloadOption: List<DownloadOption>? = null
    )

    @Serializable
    data class Response(val total: Int = 0, val sources: List<StoreSource> = emptyList())

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "hydroid/sources-store.json").apply { parentFile?.mkdirs() }

    // ultima copia baixada (abre offline)
    fun cached(context: Context): List<StoreSource> = runCatching {
        val f = cacheFile(context)
        if (!f.exists()) return emptyList()
        JsonCfg.json.decodeFromString<Response>(f.readText()).sources
    }.getOrDefault(emptyList())

    // fontes com jogos pre-instalados (sem instalador: extrai e joga) sao as ideais
    // pro Hydroid; a propria descricao da fonte diz isso ("pre-installed", "uncompressed"...)
    fun isHydroidRecommended(s: StoreSource): Boolean {
        val text = ((s.title ?: "") + " " + (s.description ?: "")).lowercase()
        return Regex("pre[- ]?install|preinstal|portable|uncompressed|no install|descompactad")
            .containsMatchIn(text)
    }

    // baixa a lista do link do usuario e atualiza o cache
    suspend fun fetch(context: Context, url: String): List<StoreSource>? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = httpGetJson(storeUrl(url))
            val resp = JsonCfg.json.decodeFromString<Response>(raw)
            runCatching { cacheFile(context).writeText(raw) }
            AppLog.i("SourceStore", "loja sincronizada: ${resp.sources.size} fontes")
            resp.sources
        }.onFailure { AppLog.w("SourceStore", "sync da loja falhou: ${it.message}") }.getOrNull()
    }
}
