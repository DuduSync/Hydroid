package gg.hydroid.app.data.store

import android.content.Context
import coil.imageLoader
import gg.hydroid.app.data.log.AppLog

// limpeza de cache: imagens (coil) + arquivos temporarios do cacheDir.
// NAO toca em downloads, torrents, logs nem configuracoes (esses ficam no filesDir).
object CacheCleaner {
    fun sizeBytes(context: Context): Long =
        context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clear(context: Context) {
        val before = sizeBytes(context)
        runCatching { context.imageLoader.memoryCache?.clear() }
        runCatching { context.imageLoader.diskCache?.clear() }
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        val after = sizeBytes(context)
        AppLog.i("Cache", "limpou cache: ${before / 1024} KB -> ${after / 1024} KB")
    }
}
