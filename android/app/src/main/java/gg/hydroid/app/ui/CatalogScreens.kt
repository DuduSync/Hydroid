package gg.hydroid.app.ui

import gg.hydroid.app.ui.theme.glassAwareElevation

import gg.hydroid.app.data.i18n.tr
import gg.hydroid.app.data.i18n.tf

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import gg.hydroid.app.data.api.HydraCloudApi
import gg.hydroid.app.data.api.ProtonDbApi
import gg.hydroid.app.data.api.SourceFetcher
import gg.hydroid.app.data.api.SteamApi
import gg.hydroid.app.data.model.*
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.download.DownloadEngine
import gg.hydroid.app.download.DownloadMethod
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

    fun openFromLibrary(lib: LibraryGame) {
        openGame(SteamSearchItem(name = lib.name, id = lib.appId))
    }

    fun closeGame() { selectedGame = null }
}

private fun steamHeader(appId: Long) =
    "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"

// ---------- Catálogo ----------

@Composable
fun CatalogScreen(vm: CatalogViewModel = viewModel()) {
    val selected = vm.selectedGame
    BackHandler(enabled = selected != null) { vm.closeGame() }
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(180)))
            }
        },
        label = "catalog"
    ) { game ->
        if (game != null) GameDetailScreen(vm) else CatalogSearchScreen(vm)
    }
}

@Composable
private fun CatalogSearchScreen(vm: CatalogViewModel) {
    val focus = LocalFocusManager.current
    val glassTheme = AppStore.theme.collectAsState().value == "glass"
    Box(Modifier.fillMaxSize()) {
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !vm.searched -> EmptyState(
                icon = Icons.Filled.Search,
                title = tr("Explore o catálogo"),
                subtitle = tr("Busque qualquer jogo da Steam para ver opções de download")
            )
            vm.results.isEmpty() -> EmptyState(
                icon = Icons.Filled.Search,
                title = tr("Nada encontrado"),
                subtitle = tr("Tente outro nome")
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .topFadeMask(170.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 92.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.results) { item -> GameCard(item) { vm.openGame(item) } }
            }
        }

        // busca: fundo solido nos outros temas; no glass o fade do conteudo resolve
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (glassTheme) Color.Transparent else MaterialTheme.colorScheme.background)
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(tr("Buscar jogos na Steam...")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotBlank()) {
                        FilledTonalIconButton(onClick = { vm.search() }) {
                            Icon(Icons.Filled.Search, tr("Buscar"))
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
    val premiumizeKey by AppStore.premiumizeKey.collectAsState()
    val alldebridKey by AppStore.alldebridKey.collectAsState()
    val torboxKey by AppStore.torboxKey.collectAsState()
    var optionsFor by remember { mutableStateOf<DownloadSheet?>(null) }
    var sourcesOpen by remember { mutableStateOf(false) }

    // dados extras da pagina do jogo (HLTB e ProtonDB)
    var hltb by remember(game.id) { mutableStateOf<List<HltbEntry>?>(null) }
    var proton by remember(game.id) { mutableStateOf<ProtonTier?>(null) }
    LaunchedEffect(game.id) {
        hltb = HydraCloudApi.howLongToBeat(game.id)
        proton = ProtonDbApi.tier(game.id)
    }

    fun start(uri: String, method: DownloadMethod, title: String) {
        DownloadEngine.start(
            AppStore.appContext,
            id = "dl-${System.currentTimeMillis()}",
            title = title,
            uri = uri,
            method = method
        )
        scope.launch { snackbar.showSnackbar(tr("Download iniciado — acompanhe em Downloads")) }
    }

    fun startChecked(uri: String, method: DownloadMethod, title: String) {
        if (method == DownloadMethod.RD && rdKey.isBlank()) {
            scope.launch { snackbar.showSnackbar(tr("Configure a chave Real-Debrid em Ajustes")) }
            return
        }
        start(uri, method, title)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

            // Hero full-bleed no aspect ratio natural da arte
            val heroUrl = details?.header_image ?: steamHeader(game.id)
            val heroPainter = rememberAsyncImagePainter(model = heroUrl)
            val intrinsic = heroPainter.intrinsicSize
            val heroRatio =
                if (intrinsic.isSpecified && intrinsic.height > 0f) intrinsic.width / intrinsic.height
                else 460f / 215f
            Box(Modifier.fillMaxWidth().aspectRatio(heroRatio)) {
                if (vm.detailsLoading && details?.header_image == null) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                Image(
                    painter = heroPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { vm.closeGame() },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, tr("Voltar"),
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
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            details.genres.take(4).forEach { g ->
                                Box(
                                    Modifier
                                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        g.description,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
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
                        scope.launch { snackbar.showSnackbar(tr("Removido da biblioteca")) }
                    } else {
                        AppStore.addToLibrary(
                            LibraryGame(
                                appId = game.id,
                                name = details?.name ?: game.name,
                                headerImage = details?.header_image ?: steamHeader(game.id)
                            )
                        )
                        inLibrary = true
                        scope.launch { snackbar.showSnackbar(tr("Adicionado à biblioteca")) }
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Icon(
                    if (inLibrary) Icons.Filled.Check else Icons.Filled.Add, null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (inLibrary) tr("Na biblioteca") else tr("Adicionar à biblioteca"))
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
                    Text(if (descExpanded) tr("Ver menos") else tr("Ver mais"))
                }
            }

            // Botao unico de download: abre o popup com todas as fontes
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { sourcesOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("Baixar"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Download manual
            Spacer(Modifier.height(16.dp))
            Text(
                tr("Link manual"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ManualDownloadCard(
                title = details?.name ?: game.name,
                onShowOptions = { uri ->
                    optionsFor = DownloadSheet(
                        title = details?.name ?: game.name,
                        fileSize = null,
                        source = tr("Link manual"),
                        uris = listOf(uri)
                    )
                }
            )

            // Sobre (metadados)
            details?.let { d ->
                val meta = buildList {
                    d.developers.takeIf { it.isNotEmpty() }
                        ?.let { add(tr("Desenvolvedora") to it.joinToString(", ")) }
                    d.publishers.takeIf { it.isNotEmpty() }
                        ?.let { add(tr("Publicadora") to it.joinToString(", ")) }
                    d.release_date?.date?.takeIf { it.isNotBlank() }
                        ?.let { add(tr("Lançamento") to it) }
                }
                val hasMeta = meta.isNotEmpty() ||
                    (d.metacritic?.score ?: 0) > 0 ||
                    (d.recommendations?.total ?: 0) > 0
                if (hasMeta) {
                    GameInfoSection(tr("Sobre")) {
                        meta.forEach { (k, v) -> InfoRow(k, v) }
                        d.metacritic?.takeIf { it.score > 0 }?.let { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Metacritic",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(110.dp)
                                )
                                Box(
                                    Modifier
                                        .background(metacriticColor(m.score), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        m.score.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                        (d.recommendations?.total ?: 0).takeIf { it > 0 }?.let { total ->
                            InfoRow(tr("Recomendações"), "%,d".format(total))
                        }
                    }
                }
            }

            // How long to beat
            hltb?.takeIf { it.isNotEmpty() }?.let { list ->
                GameInfoSection(tr("How long to beat")) {
                    list.forEach { e -> InfoRow(hltbTitle(e.title), e.duration) }
                }
            }

            // Galeria
            details?.screenshots?.takeIf { it.isNotEmpty() }?.let { shots ->
                var fullShot by remember { mutableStateOf<String?>(null) }
                GameInfoSection(tr("Galeria")) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shots.size) { i ->
                            val s = shots[i]
                            AsyncImage(
                                model = s.path_thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 160.dp, height = 90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullShot = s.path_full }
                            )
                        }
                    }
                }
                fullShot?.let { url ->
                    AlertDialog(
                        onDismissRequest = { fullShot = null },
                        confirmButton = {},
                        text = {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { fullShot = null }
                            )
                        }
                    )
                }
            }

            // Requisitos (colapsavel)
            details?.pc_requirements?.let { req ->
                val reqs = parsePcRequirements(req)
                if (reqs != null && (reqs.first != null || reqs.second != null)) {
                    var reqExpanded by remember { mutableStateOf(false) }
                    GameInfoSection(tr("Requisitos")) {
                        TextButton(onClick = { reqExpanded = !reqExpanded }) {
                            Text(if (reqExpanded) tr("Ocultar requisitos") else tr("Mostrar requisitos"))
                        }
                        if (reqExpanded) {
                            reqs.first?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    tr("Mínimos"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stripHtml(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            reqs.second?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    tr("Recomendados"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stripHtml(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ProtonDB
            proton?.let { p ->
                val uriHandler = LocalUriHandler.current
                GameInfoSection(tr("ProtonDB")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .background(protonColor(p.tier), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                p.tier.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tf("%s relatos", p.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { uriHandler.openUri("https://www.protondb.com/app/${game.id}") }) {
                        Text(tr("Ver no ProtonDB"))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                tr("Fonte dos downloads: suas fontes em Ajustes · Hydra Cloud. ") +
                    tr("Baixe apenas conteúdo que você tem direito."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(32.dp))
        }

        optionsFor?.let { sheet ->
            ModalBottomSheet(
                onDismissRequest = { optionsFor = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                DownloadOptionsSheet(
                    sheet = sheet,
                    rdAvailable = rdKey.isNotBlank(),
                    premiumizeAvailable = premiumizeKey.isNotBlank(),
                    alldebridAvailable = alldebridKey.isNotBlank(),
                    torboxAvailable = torboxKey.isNotBlank(),
                    onPick = { uri, method ->
                        optionsFor = null
                        startChecked(uri, method, sheet.title)
                    }
                )
            }
        }

        if (sourcesOpen) {
            ModalBottomSheet(
                onDismissRequest = { sourcesOpen = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        tr("Fontes de download"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    SourceRepackList(
                        gameName = details?.name ?: game.name,
                        appId = game.id
                    ) { repack, sourceName ->
                        sourcesOpen = false
                        optionsFor = DownloadSheet(
                            title = repack.title,
                            fileSize = repack.fileSize,
                            source = sourceName,
                            uris = repack.uris
                        )
                    }
                }
            }
        }
    }
}

private data class DownloadSheet(
    val title: String,
    val fileSize: String?,
    val source: String?,
    val uris: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(
    sheet: DownloadSheet,
    rdAvailable: Boolean,
    premiumizeAvailable: Boolean,
    alldebridAvailable: Boolean,
    torboxAvailable: Boolean,
    onPick: (String, DownloadMethod) -> Unit
) {
    val hasMagnet = sheet.uris.any { it.startsWith("magnet:") }
    val hasHttp = sheet.uris.any { it.startsWith("http") }
    // uri preferida para servicos debrid: magnet > http > primeira
    val debridUri = sheet.uris.firstOrNull { it.startsWith("magnet:") }
        ?: sheet.uris.firstOrNull { it.startsWith("http") }
        ?: sheet.uris.firstOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
    ) {
        Text(
            tr("Baixar"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(sheet.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val meta = listOfNotNull(sheet.fileSize, sheet.source).joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))

        if (rdAvailable && debridUri != null) {
            DownloadMethodRow(
                icon = Icons.Filled.CloudDownload,
                title = tr("Real-Debrid"),
                subtitle = tr("Processa no cloud e baixa em alta velocidade")
            ) { onPick(debridUri, DownloadMethod.RD) }
        }
        if (premiumizeAvailable && debridUri != null) {
            DownloadMethodRow(
                icon = Icons.Filled.CloudDownload,
                title = tr("Premiumize"),
                subtitle = tr("Processa no cloud e baixa em alta velocidade"),
                beta = true
            ) { onPick(debridUri, DownloadMethod.PREMIUMIZE) }
        }
        if (alldebridAvailable && debridUri != null) {
            DownloadMethodRow(
                icon = Icons.Filled.CloudDownload,
                title = tr("AllDebrid"),
                subtitle = tr("Processa no cloud e baixa em alta velocidade"),
                beta = true
            ) { onPick(debridUri, DownloadMethod.ALLDEBRID) }
        }
        if (torboxAvailable && debridUri != null) {
            DownloadMethodRow(
                icon = Icons.Filled.CloudDownload,
                title = tr("TorBox"),
                subtitle = tr("Processa no cloud e baixa em alta velocidade"),
                beta = true
            ) { onPick(debridUri, DownloadMethod.TORBOX) }
        }

        if (hasMagnet) {
            DownloadMethodRow(
                icon = Icons.Filled.SportsEsports,
                title = tr("Torrent"),
                subtitle = tr("Baixa direto do swarm no aparelho")
            ) { onPick(sheet.uris.first { it.startsWith("magnet:") }, DownloadMethod.TORRENT) }
        }
        if (hasHttp) {
            DownloadMethodRow(
                icon = Icons.Filled.Download,
                title = tr("Direto"),
                subtitle = tr("Download HTTP sem precisar de conta")
            ) { onPick(sheet.uris.first { it.startsWith("http") }, DownloadMethod.DIRETO) }
        }
    }
}

@Composable
private fun DownloadMethodRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    beta: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (beta) {
                BetaInfoButton()
            }
        }
    }
}

@Composable
private fun ManualDownloadCard(
    title: String,
    onShowOptions: (String) -> Unit
) {
    var uri by remember { mutableStateOf("") }
    val rdKey by AppStore.rdApiKey.collectAsState()

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = glassAwareElevation()),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = uri,
                onValueChange = { uri = it },
                label = { Text(tr("Magnet ou URL direta")) },
                placeholder = { Text("magnet:?xt=... ou https://...") },
                leadingIcon = { Icon(Icons.Filled.Link, null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = uri.isNotBlank(),
                onClick = { onShowOptions(uri.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Baixar"))
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
    onShowOptions: (GameRepack, String) -> Unit
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
                tr("Nenhuma fonte configurada. Adicione em Ajustes para ver opções."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("Consultando fontes..."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        repacks.isEmpty() -> Box(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                tr("Sem repacks disponíveis nas suas fontes para este jogo."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repacks.forEach { (repack, sourceName) ->
                RepackCard(repack, sourceName, onShowOptions)
            }
        }
    }
}

@Composable
private fun RepackCard(
    repack: GameRepack,
    sourceName: String,
    onShowOptions: (GameRepack, String) -> Unit
) {

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = glassAwareElevation()),
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
            Button(
                onClick = { onShowOptions(repack, sourceName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Baixar"))
            }
        }
    }
}

// ---------- secoes extras da pagina do jogo ----------

@Composable
private fun GameInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
    Spacer(Modifier.height(4.dp))
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = glassAwareElevation()),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun hltbTitle(title: String): String = when (title) {
    "Main Story" -> tr("História principal")
    "Main + Sides" -> tr("História + extras")
    "Completionist" -> tr("Completista")
    else -> title
}

private fun metacriticColor(score: Int) = when {
    score >= 75 -> Color(0xFF66CC33)
    score >= 50 -> Color(0xFFFFCC33)
    else -> Color(0xFFFF687D)
}

private fun protonColor(tier: String) = when (tier.lowercase()) {
    "platinum" -> Color(0xFFB4C7DC)
    "gold" -> Color(0xFFF4C430)
    "silver" -> Color(0xFFA6A6A6)
    "bronze" -> Color(0xFFCD7F32)
    "borked" -> Color(0xFFE06666)
    else -> Color(0xFFCCCCCC)
}

private fun stripHtml(s: String): String = s
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")
    .trim()

private fun parsePcRequirements(el: kotlinx.serialization.json.JsonElement): Pair<String?, String?>? {
    val obj = el as? JsonObject ?: return null
    val min = (obj["minimum"] as? JsonPrimitive)?.contentOrNull
    val rec = (obj["recommended"] as? JsonPrimitive)?.contentOrNull
    if (min.isNullOrBlank() && rec.isNullOrBlank()) return null
    return min to rec
}
