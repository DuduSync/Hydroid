package gg.hydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import gg.hydroid.app.data.api.HydraCloudApi
import gg.hydroid.app.data.api.SourceFetcher
import gg.hydroid.app.data.api.SteamApi
import gg.hydroid.app.data.model.*
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.download.DownloadEngine
import gg.hydroid.app.download.DownloadMethod
import kotlinx.coroutines.launch

// ---------- VM ----------

class CatalogViewModel : ViewModel() {
    var results by mutableStateOf<List<SteamSearchItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var selectedGame by mutableStateOf<SteamSearchItem?>(null)
    var details by mutableStateOf<SteamAppDetails?>(null)
    var detailsLoading by mutableStateOf(false)
        private set

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        loading = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            results = SteamApi.search(q)
            searched = true
            loading = false
        }
    }

    fun openGame(item: SteamSearchItem) {
        selectedGame = item
        details = null
        detailsLoading = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            details = SteamApi.appDetails(item.id)
            detailsLoading = false
        }
    }

    fun closeGame() { selectedGame = null }
}

private fun steamHeader(appId: Long) =
    "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"

// ---------- Catálogo ----------

@Composable
fun CatalogScreen(vm: CatalogViewModel = viewModel()) {
    val selected = vm.selectedGame
    if (selected != null) {
        GameDetailScreen(vm)
        return
    }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Buscar jogos na Steam...") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (vm.query.isNotBlank()) {
                    FilledTonalIconButton(onClick = { vm.search() }) {
                        Icon(Icons.Filled.Search, "Buscar")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.search()
            }),
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !vm.searched -> EmptyState(
                icon = Icons.Filled.Search,
                title = "Explore o catálogo",
                subtitle = "Busque qualquer jogo da Steam para ver opções de download"
            )
            vm.results.isEmpty() -> EmptyState(
                icon = Icons.Filled.Search,
                title = "Nada encontrado",
                subtitle = "Tente outro nome"
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.results) { item -> GameCard(item) { vm.openGame(item) } }
            }
        }
    }
}

@Composable
private fun GameCard(item: SteamSearchItem, onClick: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(460f / 215f)) {
                if (!loaded) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
                AsyncImage(
                    model = steamHeader(item.id),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    onSuccess = { loaded = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 12.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Detalhe + downloads ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(vm: CatalogViewModel) {
    val game = vm.selectedGame ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val details = vm.details
    var descExpanded by remember { mutableStateOf(false) }
    var inLibrary by remember(game.id) { mutableStateOf(AppStore.isInLibrary(game.id)) }
    val rdKey by AppStore.rdApiKey.collectAsState()

    fun start(uri: String, method: DownloadMethod, title: String) {
        DownloadEngine.start(
            AppStore.appContext,
            id = "dl-${System.currentTimeMillis()}",
            title = title,
            uri = uri,
            method = method
        )
        scope.launch { snackbar.showSnackbar("Download iniciado — acompanhe em Downloads") }
    }

    fun startChecked(uri: String, method: DownloadMethod, title: String) {
        if (method == DownloadMethod.RD && rdKey.isBlank()) {
            scope.launch { snackbar.showSnackbar("Configure a chave Real-Debrid em Ajustes") }
            return
        }
        start(uri, method, title)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

            // Hero
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                if (vm.detailsLoading || details?.header_image == null) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                AsyncImage(
                    model = details?.header_image ?: steamHeader(game.id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // scrim inferior -> fundo
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
                )
                // scrim superior -> botoes
                Box(
                    Modifier.fillMaxWidth().height(96.dp).background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Transparent
                        )
                    )
                )
                IconButton(
                    onClick = { vm.closeGame() },
                    modifier = Modifier.padding(8.dp).size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Voltar",
                        tint = Color.White
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(20.dp)
                ) {
                    Text(
                        details?.name ?: game.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (details?.genres?.isNotEmpty() == true) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            details.genres.joinToString("  ·  ") { it.description },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Ações
            FilledTonalButton(
                onClick = {
                    if (inLibrary) {
                        AppStore.removeFromLibrary(game.id)
                        inLibrary = false
                        scope.launch { snackbar.showSnackbar("Removido da biblioteca") }
                    } else {
                        AppStore.addToLibrary(
                            LibraryGame(
                                appId = game.id,
                                name = details?.name ?: game.name,
                                headerImage = details?.header_image ?: steamHeader(game.id)
                            )
                        )
                        inLibrary = true
                        scope.launch { snackbar.showSnackbar("Adicionado à biblioteca") }
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Icon(
                    if (inLibrary) Icons.Filled.Check else Icons.Filled.Add, null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (inLibrary) "Na biblioteca" else "Adicionar à biblioteca")
            }

            // Descrição
            details?.detailed_description?.takeIf { it.isNotBlank() }?.let { html ->
                val desc = html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                Spacer(Modifier.height(20.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                TextButton(onClick = { descExpanded = !descExpanded }) {
                    Text(if (descExpanded) "Ver menos" else "Ver mais")
                }
            }

            // Downloads disponíveis
            Spacer(Modifier.height(8.dp))
            Text(
                "Downloads disponíveis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            SourceRepackList(gameName = details?.name ?: game.name, appId = game.id) { uri, method, title ->
                startChecked(uri, method, title)
            }

            // Download manual
            Spacer(Modifier.height(16.dp))
            Text(
                "Link manual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ManualDownloadCard(
                title = details?.name ?: game.name,
                onRd = { uri, title -> startChecked(uri, DownloadMethod.RD, title) },
                onDirect = { uri, title -> startChecked(uri, DownloadMethod.DIRETO, title) }
            )

            Spacer(Modifier.height(32.dp))
            Text(
                "Fonte dos downloads: suas fontes em Ajustes · Hydra Cloud. " +
                    "Baixe apenas conteúdo que você tem direito.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ManualDownloadCard(
    title: String,
    onRd: (String, String) -> Unit,
    onDirect: (String, String) -> Unit
) {
    var uri by remember { mutableStateOf("") }
    var rdKeyMissing by remember { mutableStateOf(false) }
    val rdKey by AppStore.rdApiKey.collectAsState()
    rdKeyMissing = rdKey.isBlank()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = uri,
                onValueChange = { uri = it },
                label = { Text("Magnet ou URL direta") },
                placeholder = { Text("magnet:?xt=... ou https://...") },
                leadingIcon = { Icon(Icons.Filled.Link, null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    enabled = uri.isNotBlank(),
                    onClick = {
                        if (rdKeyMissing) return@FilledTonalButton
                        onRd(uri.trim(), title)
                    }
                ) {
                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Real-Debrid")
                }
                OutlinedButton(
                    enabled = uri.isNotBlank() && !uri.trim().startsWith("magnet:"),
                    onClick = { onDirect(uri.trim(), title) }
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Direto")
                }
            }
            if (rdKeyMissing) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Real-Debrid desativado: configure a chave em Ajustes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun normalizeTitle(t: String) =
    t.lowercase().replace(Regex("[^a-z0-9]"), "").trim()

@Composable
private fun SourceRepackList(
    gameName: String,
    appId: Long,
    onStart: (String, DownloadMethod, String) -> Unit
) {
    val sources by AppStore.sources.collectAsState()
    val scope = rememberCoroutineScope()
    var repacks by remember { mutableStateOf<List<Pair<GameRepack, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(gameName) {
        if (sources.isEmpty() || gameName.isBlank()) return@LaunchedEffect
        loading = true
        scope.launch {
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
            repacks = found.distinctBy { it.first.id + it.second }
            loading = false
        }
    }

    when {
        sources.isEmpty() -> Box(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "Nenhuma fonte configurada. Adicione em Ajustes para ver opções.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Consultando fontes...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        repacks.isEmpty() -> Box(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "Sem repacks disponíveis nas suas fontes para este jogo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repacks.forEach { (repack, sourceName) ->
                RepackCard(repack, sourceName, onStart)
            }
        }
    }
}

@Composable
private fun RepackCard(
    repack: GameRepack,
    sourceName: String,
    onStart: (String, DownloadMethod, String) -> Unit
) {
    val hasMagnet = repack.uris.any { it.startsWith("magnet:") }
    val hasHttp = repack.uris.any { it.startsWith("http") }
    val rdKey by AppStore.rdApiKey.collectAsState()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                repack.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repack.fileSize?.let { size ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(size) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                if (sourceName.isNotBlank()) {
                    Text(
                        sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Real-Debrid pega qualquer uri (magnet via cloud, http via unrestrict)
                FilledTonalButton(
                    enabled = rdKey.isNotBlank(),
                    onClick = { onStart(repack.uris.first(), DownloadMethod.RD, repack.title) }
                ) {
                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Real-Debrid")
                }
                if (hasHttp) {
                    OutlinedButton(
                        onClick = {
                            val http = repack.uris.first { it.startsWith("http") }
                            onStart(http, DownloadMethod.DIRETO, repack.title)
                        }
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Direto")
                    }
                }
                if (hasMagnet) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Icon(Icons.Filled.SportsEsports, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Torrent", maxLines = 1)
                    }
                }
            }
            if (hasMagnet && !hasHttp && rdKey.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Configure a chave Real-Debrid em Ajustes para baixar este magnet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
