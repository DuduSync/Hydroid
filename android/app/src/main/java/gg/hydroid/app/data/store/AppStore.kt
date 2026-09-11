package gg.hydroid.app.data.store

import android.content.Context
import gg.hydroid.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import java.io.File

object AppStore {
    private lateinit var dir: File
    lateinit var appContext: Context

    // state observavel
    private val _sources = MutableStateFlow<List<DownloadSource>>(emptyList())
    val sources: StateFlow<List<DownloadSource>> = _sources

    private val _library = MutableStateFlow<List<LibraryGame>>(emptyList())
    val library: StateFlow<List<LibraryGame>> = _library

    private val _downloads = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val downloads: StateFlow<List<ActiveDownload>> = _downloads

    private val _rdApiKey = MutableStateFlow("")
    val rdApiKey: StateFlow<String> = _rdApiKey

    fun init(context: Context) {
        dir = File(context.filesDir, "hydroid").apply { mkdirs() }
        appContext = context.applicationContext
        _sources.value = load<List<DownloadSource>>("sources.json") ?: emptyList()
        _library.value = load<List<LibraryGame>>("library.json") ?: emptyList()
        _downloads.value = load<List<ActiveDownload>>("downloads.json") ?: emptyList()
        _rdApiKey.value = load<String>("rdkey.json") ?: ""
    }

    private inline fun <reified T> load(name: String): T? = runCatching {
        val f = File(dir, name)
        if (!f.exists()) return null
        gg.hydroid.app.data.api.JsonCfg.json.decodeFromString<T>(f.readText())
    }.onFailure { android.util.Log.e("HydroidStore", "load $name", it) }.getOrNull()

    private inline fun <reified T> save(name: String, value: T) = runCatching {
        File(dir, name).writeText(gg.hydroid.app.data.api.JsonCfg.json.encodeToString(value))
    }.onFailure { android.util.Log.e("HydroidStore", "save $name", it) }.let { }

    private inline fun <reified T> commit(name: String, value: List<T>, flow: MutableStateFlow<List<T>>) {
        flow.value = value
        save(name, value)
    }

    fun addSource(source: DownloadSource) = commit(
        "sources.json", (_sources.value + source).distinctBy { it.url }, _sources
    )

    fun removeSource(id: String) = commit(
        "sources.json", _sources.value.filter { it.id != id }, _sources
    )

    fun addToLibrary(game: LibraryGame) = commit(
        "library.json", (_library.value + game).distinctBy { it.appId }, _library
    )

    fun removeFromLibrary(appId: Long) = commit(
        "library.json", _library.value.filter { it.appId != appId }, _library
    )

    fun isInLibrary(appId: Long) = _library.value.any { it.appId == appId }

    fun setRdKey(key: String) {
        _rdApiKey.value = key.trim()
        save("rdkey.json", key.trim())
    }

    fun upsertDownload(dl: ActiveDownload) {
        val updated = _downloads.value
            .filter { it.id != dl.id }
            .sortedByDescending { it.progress < 1f } // ativos primeiro
            .let { listOf(dl) + it.filter { x -> x.id != dl.id } }
        commit("downloads.json", updated.take(30), _downloads)
    }

    fun removeDownload(id: String) = commit(
        "downloads.json", _downloads.value.filter { it.id != id }, _downloads
    )

    fun downloadsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "Downloads").apply { mkdirs() }
}
