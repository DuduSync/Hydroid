package gg.hydroid.app.data.store

import android.content.Context
import android.os.Environment
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

    private val _premiumizeKey = MutableStateFlow("")
    val premiumizeKey: StateFlow<String> = _premiumizeKey

    private val _alldebridKey = MutableStateFlow("")
    val alldebridKey: StateFlow<String> = _alldebridKey

    private val _torboxKey = MutableStateFlow("")
    val torboxKey: StateFlow<String> = _torboxKey

    private val _setupDone = MutableStateFlow(false)
    val setupDone: StateFlow<Boolean> = _setupDone

    private val _autoExtract = MutableStateFlow(false)
    val autoExtract: StateFlow<Boolean> = _autoExtract

    private val _deleteArchive = MutableStateFlow(false)
    val deleteArchive: StateFlow<Boolean> = _deleteArchive

    private val _downloadDir = MutableStateFlow("")
    val downloadDir: StateFlow<String> = _downloadDir

    private val _maxConcurrent = MutableStateFlow(2)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    private val _speedLimitKbps = MutableStateFlow(0)
    val speedLimitKbps: StateFlow<Int> = _speedLimitKbps

    private val _hydraAuth = MutableStateFlow<HydraAuth?>(null)
    val hydraAuth: StateFlow<HydraAuth?> = _hydraAuth

    private val _hydraUser = MutableStateFlow<HydraUser?>(null)
    val hydraUser: StateFlow<HydraUser?> = _hydraUser

    fun init(context: Context) {
        dir = File(context.filesDir, "hydroid").apply { mkdirs() }
        appContext = context.applicationContext
        _sources.value = load<List<DownloadSource>>("sources.json") ?: emptyList()
        _library.value = load<List<LibraryGame>>("library.json") ?: emptyList()
        // terminais e pausados sobrevivem a restart (pausado pode continuar depois)
        val terminal = setOf("concluido", "erro", "pausado")
        val cleaned = (load<List<ActiveDownload>>("downloads.json") ?: emptyList())
            .filter { it.stage in terminal }
        commit("downloads.json", cleaned, _downloads)
        _rdApiKey.value = load<String>("rdkey.json") ?: ""
        _premiumizeKey.value = load<String>("premiumize.json") ?: ""
        _alldebridKey.value = load<String>("alldebrid.json") ?: ""
        _torboxKey.value = load<String>("torbox.json") ?: ""
        _hydraAuth.value = load<HydraAuth>("hydraauth.json")
        _hydraUser.value = load<HydraUser>("hydrauser.json")
        val prefs = load<Prefs>("prefs.json") ?: Prefs()
        _setupDone.value = prefs.setupDone
        _autoExtract.value = prefs.autoExtract
        _deleteArchive.value = prefs.deleteArchive
        _downloadDir.value = prefs.downloadDir
        _maxConcurrent.value = prefs.maxConcurrent
        _wifiOnly.value = prefs.wifiOnly
        _speedLimitKbps.value = prefs.speedLimitKbps
    }

    @kotlinx.serialization.Serializable
    data class Prefs(
        val setupDone: Boolean = false,
        val autoExtract: Boolean = false,
        val deleteArchive: Boolean = false,
        val downloadDir: String = "",
        val maxConcurrent: Int = 2,
        val wifiOnly: Boolean = false,
        val speedLimitKbps: Int = 0
    )

    private fun savePrefs() = save(
        "prefs.json",
        Prefs(
            _setupDone.value, _autoExtract.value, _deleteArchive.value, _downloadDir.value,
            _maxConcurrent.value, _wifiOnly.value, _speedLimitKbps.value
        )
    )

    fun setSetupDone(done: Boolean) { _setupDone.value = done; savePrefs() }

    // apagar arquivo so faz sentido junto de extrair automaticamente
    fun setAutoExtract(v: Boolean) {
        _autoExtract.value = v
        if (!v) _deleteArchive.value = false
        savePrefs()
    }

    fun setDeleteArchive(v: Boolean) {
        _deleteArchive.value = v && _autoExtract.value
        savePrefs()
    }
    fun setDownloadDir(path: String) { _downloadDir.value = path; savePrefs() }

    // 0 = sem limite de downloads simultaneos
    fun setMaxConcurrent(v: Int) { _maxConcurrent.value = v.coerceIn(0, 5); savePrefs() }

    fun setWifiOnly(v: Boolean) { _wifiOnly.value = v; savePrefs() }

    // 0 = sem limite de velocidade
    fun setSpeedLimitKbps(v: Int) { _speedLimitKbps.value = v.coerceAtLeast(0); savePrefs() }

    // destino dos downloads: pasta escolhida pelo usuario ou padrao do app
    fun targetDir(context: Context): File {
        val custom = _downloadDir.value
        if (custom.isNotBlank()) {
            val f = File(custom)
            if (f.isDirectory || f.mkdirs()) return f
        }
        return downloadsDir(context)
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

    fun setPremiumizeKey(key: String) {
        _premiumizeKey.value = key.trim()
        save("premiumize.json", key.trim())
    }

    fun setAlldebridKey(key: String) {
        _alldebridKey.value = key.trim()
        save("alldebrid.json", key.trim())
    }

    fun setTorboxKey(key: String) {
        _torboxKey.value = key.trim()
        save("torbox.json", key.trim())
    }

    fun saveHydraAuth(auth: HydraAuth) {
        _hydraAuth.value = auth
        save("hydraauth.json", auth)
    }

    fun saveHydraUser(user: HydraUser) {
        _hydraUser.value = user
        save("hydrauser.json", user)
    }

    fun clearHydraAuth() {
        _hydraAuth.value = null
        _hydraUser.value = null
        File(dir, "hydraauth.json").delete()
        File(dir, "hydrauser.json").delete()
    }

    fun upsertDownload(dl: ActiveDownload) {
        // preserva a uri original (magnet/hoster) entre posts de progresso
        val prev = _downloads.value.firstOrNull { it.id == dl.id }
        val merged = if (dl.uri == null && prev?.uri != null) dl.copy(uri = prev.uri) else dl
        val updated = _downloads.value
            .filter { it.id != merged.id }
            .sortedByDescending { it.progress < 1f } // ativos primeiro
            .let { listOf(merged) + it.filter { x -> x.id != merged.id } }
        commit("downloads.json", updated.take(30), _downloads)
    }

    fun removeDownload(id: String) = commit(
        "downloads.json", _downloads.value.filter { it.id != id }, _downloads
    )

    // padrao: pasta Download do Android + subpasta HYDROID (precisa do acesso a arquivos)
    fun downloadsDir(context: Context): File {
        val public = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "HYDROID"
        )
        if (public.isDirectory || public.mkdirs()) return public
        return File(context.getExternalFilesDir(null), "Downloads").apply { mkdirs() }
    }

    // torrents vao para armazenamento INTERNO: evita FUSE/vold do Android 14
    // (vold mata o processo que manipula symlinks em /storage/emulated)
    fun torrentsDir(context: Context): File =
        File(context.filesDir, "torrents").apply { mkdirs() }
}
