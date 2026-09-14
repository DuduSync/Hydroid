package gg.hydroid.app.data.store

import android.content.Context
import android.os.Environment
import gg.hydroid.app.data.log.AppLog
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

    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language

    // link da loja de fontes: quem informa e o usuario (o app nao carrega link de loja)
    private val _storeUrl = MutableStateFlow("")
    val storeUrl: StateFlow<String> = _storeUrl

    // auto | light | dark | amoled | glass
    private val _theme = MutableStateFlow("auto")
    val theme: StateFlow<String> = _theme

    // aba que abre com o app (indice das tabs)
    private val _startTab = MutableStateFlow(1)
    val startTab: StateFlow<Int> = _startTab

    private val _collections = MutableStateFlow<List<GameCollection>>(emptyList())
    val collections: StateFlow<List<GameCollection>> = _collections

    // jogo pedido por atalho da tela inicial
    private val _pendingOpenGame = MutableStateFlow<Long?>(null)
    val pendingOpenGame: StateFlow<Long?> = _pendingOpenGame

    // navegador interno (hosters com espera/login): url a abrir + titulo do download
    data class BrowserRequest(val url: String, val title: String)

    private val _browser = MutableStateFlow<BrowserRequest?>(null)
    val browser: StateFlow<BrowserRequest?> = _browser

    fun openBrowser(url: String, title: String) {
        AppLog.i("UI", "navegador: abrindo ${url.take(90)}")
        _browser.value = BrowserRequest(url, title)
    }

    fun closeBrowser() { _browser.value = null }

    private val _hydraAuth = MutableStateFlow<HydraAuth?>(null)
    val hydraAuth: StateFlow<HydraAuth?> = _hydraAuth

    private val _hydraUser = MutableStateFlow<HydraUser?>(null)
    val hydraUser: StateFlow<HydraUser?> = _hydraUser

    fun init(context: Context) {
        dir = File(context.filesDir, "hydroid").apply { mkdirs() }
        appContext = context.applicationContext
        _sources.value = load<List<DownloadSource>>("sources.json") ?: emptyList()
        _library.value = (load<List<LibraryGame>>("library.json") ?: emptyList()).distinctBy { it.appId }
        _collections.value = load<List<GameCollection>>("collections.json") ?: emptyList()
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
        _language.value = prefs.language.ifBlank {
            if (java.util.Locale.getDefault().language == "en") "en" else "pt"
        }
        _theme.value = prefs.theme
        _startTab.value = prefs.startTab
        _storeUrl.value = prefs.storeUrl
    }

    @kotlinx.serialization.Serializable
    data class Prefs(
        val setupDone: Boolean = false,
        val autoExtract: Boolean = false,
        val deleteArchive: Boolean = false,
        val downloadDir: String = "",
        val maxConcurrent: Int = 2,
        val wifiOnly: Boolean = false,
        val speedLimitKbps: Int = 0,
        val language: String = "",
        val theme: String = "auto",
        val startTab: Int = 1,
        val storeUrl: String = ""
    )

    private fun savePrefs() = save(
        "prefs.json",
        Prefs(
            _setupDone.value, _autoExtract.value, _deleteArchive.value, _downloadDir.value,
            _maxConcurrent.value, _wifiOnly.value, _speedLimitKbps.value, _language.value,
            _theme.value, _startTab.value, _storeUrl.value
        )
    )

    // link da loja de fontes (colado pelo usuario)
    fun setStoreUrl(url: String) {
        AppLog.i("Store", "link da loja de fontes=" + if (url.isBlank()) "(vazio)" else url.take(90))
        _storeUrl.value = url.trim()
        savePrefs()
    }

    fun setSetupDone(done: Boolean) { AppLog.i("Store", "setup concluido=$done"); _setupDone.value = done; savePrefs() }

    // apagar arquivo so faz sentido junto de extrair automaticamente
    fun setAutoExtract(v: Boolean) {
        AppLog.i("Store", "extrair automaticamente=$v")
        _autoExtract.value = v
        if (!v) _deleteArchive.value = false
        savePrefs()
    }

    fun setDeleteArchive(v: Boolean) {
        AppLog.i("Store", "apagar arquivo apos extrair=$v")
        _deleteArchive.value = v && _autoExtract.value
        savePrefs()
    }
    fun setDownloadDir(path: String) { AppLog.i("Store", "pasta de downloads=$path"); _downloadDir.value = path; savePrefs() }

    // 0 = sem limite de downloads simultaneos
    fun setMaxConcurrent(v: Int) { AppLog.i("Store", "max simultaneos=$v"); _maxConcurrent.value = v.coerceIn(0, 5); savePrefs() }

    fun setWifiOnly(v: Boolean) { AppLog.i("Store", "so Wi-Fi=$v"); _wifiOnly.value = v; savePrefs() }

    // 0 = sem limite de velocidade
    fun setSpeedLimitKbps(v: Int) { AppLog.i("Store", "limite de velocidade=${v}KB/s"); _speedLimitKbps.value = v.coerceAtLeast(0); savePrefs() }

    fun setLanguage(code: String) { AppLog.i("Store", "idioma=$code"); _language.value = code; savePrefs() }

    fun setTheme(code: String) { AppLog.i("Store", "tema=$code"); _theme.value = code; savePrefs() }

    fun setStartTab(index: Int) { AppLog.i("Store", "aba inicial=$index"); _startTab.value = index.coerceIn(0, 3); savePrefs() }

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
    }.onFailure { AppLog.e("Store", "load $name falhou", it) }.getOrNull()

    private inline fun <reified T> save(name: String, value: T) = runCatching {
        File(dir, name).writeText(gg.hydroid.app.data.api.JsonCfg.json.encodeToString(value))
    }.onFailure { AppLog.e("Store", "save $name falhou", it) }.let { }

    private inline fun <reified T> commit(name: String, value: List<T>, flow: MutableStateFlow<List<T>>) {
        flow.value = value
        save(name, value)
    }

    fun addSource(source: DownloadSource) {
        AppLog.i("Store", "fonte adicionada: ${source.name} (${source.url.take(90)})")
        commit("sources.json", (_sources.value + source).distinctBy { it.url }, _sources)
        // contagens voltam a ser checadas sem precisar reiniciar o app
        gg.hydroid.app.data.api.RepackFinder.refreshSoon()
    }

    fun removeSource(id: String) {
        AppLog.i("Store", "fonte removida: $id")
        commit("sources.json", _sources.value.filter { it.id != id }, _sources)
        gg.hydroid.app.data.api.RepackFinder.refreshSoon()
    }

    fun addToLibrary(game: LibraryGame) {
        AppLog.i("Store", "biblioteca: +${game.name} (${game.appId})")
        commit("library.json", (_library.value + game).distinctBy { it.appId }, _library)
    }

    fun removeFromLibrary(appId: Long) {
        AppLog.i("Store", "biblioteca: -$appId")
        commit("library.json", _library.value.filter { it.appId != appId }, _library)
    }

    fun isInLibrary(appId: Long) = _library.value.any { it.appId == appId }

    fun toggleFavorite(appId: Long) {
        val favorito = _library.value.firstOrNull { it.appId == appId }?.favorite == true
        AppLog.i("Store", "favorito ${if (favorito) "desmarcado" else "marcado"}: $appId")
        commit(
            "library.json",
            _library.value.map { if (it.appId == appId) it.copy(favorite = !it.favorite) else it },
            _library
        )
    }

    fun setGameCollections(appId: Long, collectionIds: List<String>) {
        AppLog.i("Store", "colecoes de $appId: ${collectionIds.size}")
        commit(
            "library.json",
            _library.value.map { if (it.appId == appId) it.copy(collectionIds = collectionIds) else it },
            _library
        )
    }

    fun addCollection(name: String): GameCollection {
        val col = GameCollection(id = java.util.UUID.randomUUID().toString(), name = name.trim())
        AppLog.i("Store", "colecao criada: ${col.name}")
        commit("collections.json", _collections.value + col, _collections)
        return col
    }

    fun renameCollection(id: String, name: String) {
        AppLog.i("Store", "colecao renomeada: $id -> ${name.trim()}")
        commit(
            "collections.json",
            _collections.value.map { if (it.id == id) it.copy(name = name.trim()) else it },
            _collections
        )
    }

    fun removeCollection(id: String) {
        AppLog.i("Store", "colecao removida: $id")
        commit("collections.json", _collections.value.filter { it.id != id }, _collections)
        commit(
            "library.json",
            _library.value.map { it.copy(collectionIds = it.collectionIds - id) },
            _library
        )
    }

    fun openGameFromShortcut(appId: Long) {
        AppLog.i("Store", "atalho: abrir jogo $appId")
        _pendingOpenGame.value = appId
    }

    fun consumePendingOpenGame() { _pendingOpenGame.value = null }

    fun setRdKey(key: String) {
        AppLog.i("Store", "Real-Debrid: ${if (key.isBlank()) "chave removida" else "chave definida (${key.trim().length} chars)"}")
        _rdApiKey.value = key.trim()
        save("rdkey.json", key.trim())
        // recarrega a lista de hosts do RD (tag Recomendado no sheet)
        gg.hydroid.app.data.api.RdHosts.invalidate()
    }

    fun setPremiumizeKey(key: String) {
        AppLog.i("Store", "Premiumize: ${if (key.isBlank()) "chave removida" else "chave definida (${key.trim().length} chars)"}")
        _premiumizeKey.value = key.trim()
        save("premiumize.json", key.trim())
    }

    fun setAlldebridKey(key: String) {
        AppLog.i("Store", "AllDebrid: ${if (key.isBlank()) "chave removida" else "chave definida (${key.trim().length} chars)"}")
        _alldebridKey.value = key.trim()
        save("alldebrid.json", key.trim())
    }

    fun setTorboxKey(key: String) {
        AppLog.i("Store", "TorBox: ${if (key.isBlank()) "chave removida" else "chave definida (${key.trim().length} chars)"}")
        _torboxKey.value = key.trim()
        save("torbox.json", key.trim())
    }

    fun saveHydraAuth(auth: HydraAuth) {
        AppLog.i("Store", "conta Hydra: tokens salvos")
        _hydraAuth.value = auth
        save("hydraauth.json", auth)
    }

    fun saveHydraUser(user: HydraUser) {
        AppLog.i("Store", "conta Hydra: ${user.email ?: user.displayName ?: "usuario"}")
        _hydraUser.value = user
        save("hydrauser.json", user)
    }

    fun clearHydraAuth() {
        AppLog.i("Store", "conta Hydra: desconectada")
        _hydraAuth.value = null
        _hydraUser.value = null
        File(dir, "hydraauth.json").delete()
        File(dir, "hydrauser.json").delete()
    }

    fun upsertDownload(dl: ActiveDownload) {
        // preserva a uri original (magnet/hoster) entre posts de progresso
        val current = _downloads.value
        val prev = current.firstOrNull { it.id == dl.id }
        // posts de progresso vem sem uri/headers: preserva do card anterior
        val merged = dl.copy(
            uri = dl.uri ?: prev?.uri,
            headers = dl.headers ?: prev?.headers
        )
        fun isActive(d: ActiveDownload) = d.stage !in setOf("concluido", "erro")
        val idx = current.indexOfFirst { it.id == merged.id }
        val updated: List<ActiveDownload> = when {
            idx < 0 -> listOf(merged) + current                    // novo: entra no topo
            isActive(merged) == (prev != null && isActive(prev)) ->
                // mudou so o progresso: fica NA MESMA posicao (nada de card pulando)
                current.toMutableList().also { it[idx] = merged }
            isActive(merged) -> listOf(merged) + current.filter { it.id != merged.id }  // retomou
            else -> current.filter { it.id != merged.id } + merged                      // terminou
        }
        commit("downloads.json", updated.take(30), _downloads)
    }

    fun removeDownload(id: String) {
        val dl = _downloads.value.firstOrNull { it.id == id }
        AppLog.i("Store", "download removido: ${dl?.title ?: id} [${dl?.stage ?: "?"}]")
        commit("downloads.json", _downloads.value.filter { it.id != id }, _downloads)
    }

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
