package gg.hydroid.app.data.store

import android.content.Context
import coil.imageLoader

// limpeza de cache: imagens (coil) + arquivos temporarios do cacheDir.
// NAO toca em downloads, torrents, logs nem configuracoes (esses ficam no filesDir).
object CacheCleaner {
    fun sizeBytes(context: Context): Long =
        context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clear(context: Context) {
        runCatching { context.imageLoader.memoryCache?.clear() }
        runCatching { context.imageLoader.diskCache?.clear() }
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
    }
}
