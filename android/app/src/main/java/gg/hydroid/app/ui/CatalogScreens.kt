package gg.hydroid.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import gg.hydroid.app.data.api.RealDebridApi
import gg.hydroid.app.data.api.SourceFetcher
import gg.hydroid.app.data.api.SourceRepack
import gg.hydroid.app.data.api.SteamApi
import gg.hydroid.app.data.model.*
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.download.DownloadEngine
import kotlinx.coroutines.launch

// ---------- VM ----------

class CatalogViewModel : ViewModel() {
    var results by mutableStateOf<List<SteamSearchItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
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

// ---------- Catalogo (busca) ----------

@Composable
fun CatalogScreen(vm: CatalogViewModel = viewModel()) {
    val selected = vm.selectedGame
    if (selected != null) {
        GameDetailScreen(vm)
        return
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Buscar jogos...") },
            trailingIcon = {
                IconButton(onClick = { vm.search() }) { Icon(Icons.Filled.Search, "Buscar") }
            },
            singleLine = true
        )
        Button(
            onClick = { vm.search() },
            modifier = Modifier.padding(horizontal = 12.dp)
        ) { Text("Buscar") }

        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
        }
        if (vm.results.isEmpty() && !vm.loading) {
            Text(
                "Busque pelo nome de um jogo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.results) { item ->
                Card(onClick = { vm.openGame(item) }) {
                    Column {
                        AsyncImage(
                            model = item.tiny_image.replace("capsule_231x87", "header"),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(90.dp)
                        )
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------- Detalhe do jogo + download ----------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(vm: CatalogViewModel) {
    val game = vm.selectedGame ?: return
    val snackbar = remember { SnackbarHostState() }
    var magnetInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val inLibrary = remember(game.id) { AppStore.isInLibrary(game.id) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(game.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { vm.closeGame() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            if (vm.detailsLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val details = vm.details
            if (details != null) {
                AsyncImage(
                    model = details.header_image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                )
                Text(
                    details.name ?: game.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                if (details.genres.isNotEmpty()) {
                    Text(
                        details.genres.joinToString(" · ") { it.description },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        if (inLibrary) {
                            AppStore.removeFromLibrary(game.id)
                            scope.launch { snackbar.showSnackbar("Removido da biblioteca") }
                        } else {
                            AppStore.addToLibrary(
                                LibraryGame(
                                    appId = game.id,
                                    name = details.name ?: game.name,
                                    headerImage = details.header_image ?: ""
                                )
                            )
                            scope.launch { snackbar.showSnackbar("Adicionado à biblioteca") }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(if (AppStore.isInLibrary(game.id)) "✓ Na biblioteca (tocar p/ remover)" else "+ Adicionar à biblioteca")
                }
                if (details.detailed_description != null) {
                    Text(
                        "Sobre o jogo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        details.detailed_description.replace(Regex("<[^>]*>"), " ").trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ---- Fontes / Download ----
            Text(
                "Downloads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            SourceRepackList(gameName = vm.details?.name ?: game.name, appId = game.id)

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ---- Download manual ----
            Text(
                "Download manual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            OutlinedTextField(
                value = magnetInput,
                onValueChange = { magnetInput = it },
                label = { Text("Link ou magnet (Real-Debrid)") },
                placeholder = { Text("magnet:?xt=... ou link direto da RD") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minLines = 2
            )
            Button(
                onClick = {
                    val uri = magnetInput.trim()
                    if (uri.isBlank()) {
                        scope.launch { snackbar.showSnackbar("Cole um magnet ou link") }
                        return@Button
                    }
                    DownloadEngine.resolveAndDownload(
                        AppStore.appContext, id = "rd-${game.id}-${System.currentTimeMillis()}",
                        title = vm.details?.name ?: game.name, uri = uri
                    )
                    magnetInput = ""
                    scope.launch { snackbar.showSnackbar("Download iniciado — veja em Downloads") }
                },
                modifier = Modifier.padding(16.dp)
            ) { Text("Baixar via Real-Debrid") }

            Text(
                "Dica: magnet de torrent ou link de hoster ativado na RD. O download acontece no cloud da RD e depois no seu aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun normalizeTitle(t: String) =
    t.lowercase().replace(Regex("[^a-z0-9]"), "").trim()

@Composable
private fun SourceRepackList(gameName: String, appId: Long) {
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
            val target = normalizeTitle(gameName)

            // 1) API do Hydra (repacks mapeados por shop/objectId no servidor deles)
            if (hydraIds.isNotEmpty()) {
                gg.hydroid.app.data.api.HydraCloudApi
                    .repacks("steam", appId.toString(), hydraIds)
                    .forEach { found += it to it.downloadSourceName }
            }
            // 2) fallback: fontes locais raw (github etc) com match por titulo
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

    if (sources.isEmpty()) {
        Text(
            "Nenhuma fonte configurada — adicione em Ajustes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        return
    }
    if (loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Text(
            "Consultando ${sources.size} fonte(s)...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        return
    }
    if (repacks.isEmpty()) {
        Text(
            "Sem repacks nas suas fontes para \"$gameName\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        return
    }
    repacks.forEach { (repack, sourceName) ->
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(repack.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(repack.fileSize, repack.uploadDate, sourceName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val uri = repack.uris.firstOrNull() ?: return@Button
                            DownloadEngine.resolveAndDownload(
                                AppStore.appContext,
                                id = "rp-${repack.id}-${System.currentTimeMillis()}",
                                title = gameName,
                                uri = uri
                            )
                        }
                    ) { Text("Baixar") }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
