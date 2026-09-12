package gg.hydroid.app.ui

import gg.hydroid.app.data.i18n.tr
import gg.hydroid.app.data.i18n.tf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import gg.hydroid.app.data.api.HydraCloudApi
import gg.hydroid.app.data.api.RealDebridApi
import gg.hydroid.app.data.model.ActiveDownload
import gg.hydroid.app.data.model.DownloadSource
import gg.hydroid.app.data.model.LibraryGame
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.download.DownloadEngine
import kotlinx.coroutines.launch

// ===== DOACOES: quando o usuario mandar o link, colar aqui (ex.: ko-fi, pix, github sponsors) =====
private const val DONATION_URL = "https://nubank.com.br/cobrar/7rfap/6aa35e97-c27c-479b-b74b-dbd122db9877"

internal fun formatBytes(b: Long): String = when {
    b >= 1_073_741_824 -> "%.2f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

// ---------- Biblioteca ----------

private fun normalize(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

private const val FILTER_ALL = "__all__"
private const val FILTER_FAV = "__fav__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenGame: (LibraryGame) -> Unit = {}) {
    val games by AppStore.library.collectAsState()
    val collections by AppStore.collections.collectAsState()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FILTER_ALL) }
    var sort by remember { mutableStateOf("recent") }
    var actionsFor by remember { mutableStateOf<LibraryGame?>(null) }
    var collectionsFor by remember { mutableStateOf<LibraryGame?>(null) }
    var manageOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    if (games.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.SportsEsports, null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            Text(tr("Sua biblioteca está vazia"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Adicione jogos pela aba Catálogo"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val shown = games
        .filter { g -> query.isBlank() || normalize(g.name).contains(normalize(query)) }
        .filter { g ->
            when (filter) {
                FILTER_ALL -> true
                FILTER_FAV -> g.favorite
                else -> g.collectionIds.contains(filter)
            }
        }
        .let { list ->
            when (sort) {
                "name" -> list.sortedBy { normalize(it.name) }
                "fav" -> list.sortedWith(
                    compareByDescending<LibraryGame> { it.favorite }.thenBy { normalize(it.name) }
                )
                else -> list.sortedByDescending { it.addedAt }
            }
        }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(tr("Buscar na biblioteca...")) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    FilterChip(
                        selected = filter == FILTER_ALL,
                        onClick = { filter = FILTER_ALL },
                        label = { Text(tr("Todas")) }
                    )
                }
                item {
                    FilterChip(
                        selected = filter == FILTER_FAV,
                        onClick = { filter = FILTER_FAV },
                        label = { Text(tr("Favoritas")) }
                    )
                }
                items(collections, key = { it.id }) { col ->
                    FilterChip(
                        selected = filter == col.id,
                        onClick = { filter = col.id },
                        label = { Text(col.name) }
                    )
                }
            }
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, tr("Ordenar"))
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Adicionados recentemente")) },
                        onClick = { sort = "recent"; sortOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Nome (A-Z)")) },
                        onClick = { sort = "name"; sortOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Favoritos primeiro")) },
                        onClick = { sort = "fav"; sortOpen = false }
                    )
                }
            }
            IconButton(onClick = { manageOpen = true }) {
                Icon(Icons.Filled.CreateNewFolder, tr("Gerenciar coleções"))
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("Nada encontrado"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shown, key = { it.appId }) { game: LibraryGame ->
                    LibraryCard(
                        game = game,
                        onClick = { onOpenGame(game) },
                        onLongClick = { actionsFor = game },
                        onToggleFavorite = { AppStore.toggleFavorite(game.appId) }
                    )
                }
            }
        }
    }

    actionsFor?.let { game ->
        GameActionsDialog(
            game = game,
            onDismiss = { actionsFor = null },
            onOpen = { actionsFor = null; onOpenGame(game) },
            onAddToCollection = { actionsFor = null; collectionsFor = game },
            onShortcut = { actionsFor = null; pinGameShortcut(context, game) },
            onRemove = { actionsFor = null; AppStore.removeFromLibrary(game.appId) }
        )
    }

    collectionsFor?.let { game ->
        CollectionPickerDialog(game = game, onDismiss = { collectionsFor = null })
    }

    if (manageOpen) {
        ManageCollectionsDialog(onDismiss = { manageOpen = false })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    game: LibraryGame,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
            Box(Modifier.fillMaxWidth().aspectRatio(460f / 215f)) {
                AsyncImage(
                    model = game.headerImage.ifBlank {
                        "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appId}/header.jpg"
                    },
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        null,
                        tint = if (game.favorite) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Text(
                game.name,
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
private fun GameActionsDialog(
    game: LibraryGame,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAddToCollection: () -> Unit,
    onShortcut: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(game.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                ActionRow(tr("Abrir página"), Icons.Filled.PlayArrow, onClick = onOpen)
                ActionRow(
                    if (game.favorite) tr("Remover dos favoritos") else tr("Favoritar"),
                    Icons.Filled.Favorite,
                    onClick = { AppStore.toggleFavorite(game.appId); onDismiss() }
                )
                ActionRow(
                    tr("Adicionar à coleção..."),
                    Icons.Filled.CreateNewFolder,
                    onClick = onAddToCollection
                )
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    ActionRow(
                        tr("Criar atalho na tela inicial"),
                        Icons.Filled.Add,
                        onClick = onShortcut
                    )
                }
                ActionRow(
                    tr("Remover da biblioteca"),
                    Icons.Filled.Delete,
                    danger = true,
                    onClick = onRemove
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Fechar")) } }
    )
}

@Composable
private fun ActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = if (danger) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CollectionPickerDialog(game: LibraryGame, onDismiss: () -> Unit) {
    val collections by AppStore.collections.collectAsState()
    var selected by remember { mutableStateOf(game.collectionIds) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Adicionar à coleção")) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text(
                        tr("Nenhuma coleção ainda"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    collections.forEach { col ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (selected.contains(col.id)) selected - col.id
                                    else selected + col.id
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(col.id),
                                onCheckedChange = { on ->
                                    selected = if (on) selected + col.id else selected - col.id
                                }
                            )
                            Text(col.name)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(tr("Nova coleção")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val col = AppStore.addCollection(newName)
                            selected = selected + col.id
                            newName = ""
                        }
                    ) { Text(tr("Criar")) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AppStore.setGameCollections(game.appId, selected)
                onDismiss()
            }) { Text(tr("Salvar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } }
    )
}

@Composable
private fun ManageCollectionsDialog(onDismiss: () -> Unit) {
    val collections by AppStore.collections.collectAsState()
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<gg.hydroid.app.data.model.GameCollection?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Coleções")) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text(
                        tr("Nenhuma coleção ainda"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                collections.forEach { col ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(col.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renaming = col }) {
                            Icon(
                                Icons.Filled.Edit, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { AppStore.removeCollection(col.id) }) {
                            Icon(
                                Icons.Filled.Delete, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(tr("Nova coleção")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = { AppStore.addCollection(newName); newName = "" }
                    ) { Text(tr("Criar")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Fechar")) } }
    )
    renaming?.let { col ->
        var text by remember(col.id) { mutableStateOf(col.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr("Renomear coleção")) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { AppStore.renameCollection(col.id, text); renaming = null }
                ) { Text(tr("Salvar")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(tr("Cancelar")) } }
        )
    }
}

// atalho do jogo na tela inicial (usa a capa como icone)
private fun pinGameShortcut(context: Context, game: LibraryGame) {
    CoroutineScope(Dispatchers.IO).launch {
        val url = game.headerImage.ifBlank {
            "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appId}/header.jpg"
        }
        val bitmap = runCatching {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).build()
            )
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        }.getOrNull()
        val icon = if (bitmap != null) IconCompat.createWithBitmap(bitmap)
        else IconCompat.createWithResource(context, gg.hydroid.app.R.mipmap.ic_launcher)
        val intent = Intent(context, gg.hydroid.app.MainActivity::class.java).apply {
            action = "gg.hydroid.app.OPEN_GAME"
            putExtra("appId", game.appId)
            putExtra("gameName", game.name)
        }
        val info = ShortcutInfoCompat.Builder(context, "game-${game.appId}")
            .setShortLabel(game.name.take(24))
            .setLongLabel(game.name)
            .setIcon(icon)
            .setIntent(intent)
            .build()
        withContext(Dispatchers.Main) {
            runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }
                .onFailure {
                    Toast.makeText(
                        context,
                        tr("Não foi possível criar o atalho"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}

// ---------- Downloads ----------

private data class MethodBadge(val label: String, val color: Color)

@Composable
private fun downloadMethodBadge(method: String): MethodBadge = when (method) {
    "direto" -> MethodBadge(tr("Direto"), MaterialTheme.colorScheme.secondary)
    "torrent" -> MethodBadge(tr("Torrent"), MaterialTheme.colorScheme.tertiary)
    "premiumize" -> MethodBadge(tr("Premiumize"), MaterialTheme.colorScheme.tertiary)
    "alldebrid" -> MethodBadge(tr("AllDebrid"), MaterialTheme.colorScheme.tertiary)
    "torbox" -> MethodBadge(tr("TorBox"), MaterialTheme.colorScheme.tertiary)
    else -> MethodBadge(tr("Real-Debrid"), MaterialTheme.colorScheme.primary)
}

@Composable
fun DownloadsScreen() {
    val downloads by AppStore.downloads.collectAsState()
    if (downloads.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Download, null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            Text(tr("Nenhum download"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Inicie um download pela página de um jogo"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(downloads, key = { it.id }) { dl: ActiveDownload -> DownloadCard(dl) }
    }
}

@Composable
private fun DownloadCard(dl: ActiveDownload) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val badge = downloadMethodBadge(dl.method)
    val path = dl.savePath
    val isArchive = path != null &&
        (path.lowercase().endsWith(".zip") || path.lowercase().endsWith(".rar"))
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteSize by remember { mutableStateOf<String?>(null) }

    if (confirmDelete && path != null) {
        val targets = dl.savedPaths.ifEmpty { listOf(path) }
        LaunchedEffect(path, dl.savedPaths) {
            deleteSize = withContext(Dispatchers.IO) {
                runCatching { formatBytes(targets.sumOf { folderSize(File(it)) }) }.getOrNull()
            }
        }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Apagar ${dl.title}?") },
            text = {
                Text(
                    tf(
                        "Os arquivos baixados%s serão removidos do aparelho. Não dá para desfazer.",
                        deleteSize?.let { " ($it)" } ?: ""
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch(Dispatchers.IO) {
                        targets.forEach { runCatching { File(it).deleteRecursively() } }
                        withContext(Dispatchers.Main) {
                            DownloadEngine.cancel(dl.id)
                            Toast.makeText(context, tr("Arquivos apagados"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(tr("Apagar")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar")) }
            }
        )
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (dl.stage == "erro") MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(badge.color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        badge.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badge.color
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    dl.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (dl.stage) {
                    "concluido" -> dl.error ?: tr("Concluído")
                    "erro" -> dl.error ?: tr("Erro desconhecido")
                    "baixando" -> buildString {
                        append("${formatBytes(dl.speedBps)}/s  ·  ")
                        append("${formatBytes(dl.bytesDownloaded)} de ${formatBytes(dl.totalBytes)}")
                        formatEta(dl)?.let { append("  ·  $it") }
                        if (dl.method == "torrent" && (dl.peers > 0 || dl.seeds > 0)) {
                            append("  ·  ${dl.seeds} seeds / ${dl.peers} peers")
                        }
                    }
                    else -> dl.stage.replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (dl.stage == "erro") MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dl.stage != "erro" && dl.stage != "aguardando Wi-Fi") {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { dl.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            if (dl.stage == "concluido" && path != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Folder, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        path.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    dl.stage == "pausado" -> TextButton(onClick = { DownloadEngine.resume(dl.id) }) {
                        Text(tr("Continuar"))
                    }
                    dl.stage == "erro" && dl.uri != null -> TextButton(onClick = { DownloadEngine.resume(dl.id) }) {
                        Text(tr("Tentar de novo"))
                    }
                    dl.stage == "baixando" || dl.stage == "resolvendo" ||
                        dl.stage == "conectando ao swarm" || dl.stage.startsWith("cloud") ->
                        TextButton(onClick = { DownloadEngine.pause(dl.id) }) { Text(tr("Pausar")) }
                }
                if (dl.stage == "concluido" && path != null && File(path).exists()) {
                    TextButton(onClick = { openFolder(context, path) }) { Text(tr("Abrir pasta")) }
                }
                if (dl.stage == "concluido" && isArchive) {
                    TextButton(onClick = { DownloadEngine.extractNow(dl.id) }) { Text(tr("Extrair")) }
                }
                if (dl.stage == "concluido" && path != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text(tr("Apagar")) }
                }
                TextButton(onClick = { DownloadEngine.cancel(dl.id) }) {
                    Text(if (dl.stage == "concluido") tr("Limpar") else tr("Cancelar"))
                }
            }
        }
    }
}

// tempo restante estimado a partir da velocidade atual
private fun formatEta(dl: ActiveDownload): String? {
    if (dl.speedBps <= 0 || dl.totalBytes <= 0) return null
    val remaining = dl.totalBytes - dl.bytesDownloaded
    if (remaining <= 0) return null
    val secs = remaining / dl.speedBps
    return "~" + when {
        secs < 60 -> "${secs}s"
        secs < 3600 -> "${secs / 60} min"
        secs < 86400 -> "${secs / 3600}h${(secs % 3600) / 60}min"
        else -> "${secs / 86400}d"
    }
}

private fun folderSize(f: File): Long =
    if (f.isFile) f.length() else f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

// abre a pasta do download no gerenciador de arquivos (DocumentsUI)
private fun openFolder(context: Context, savePath: String) {
    val f = File(savePath)
    val target = if (f.isFile) f.parentFile ?: f else f
    val base = tr("/storage/emulated/0/")
    if (!target.absolutePath.startsWith(base)) {
        Toast.makeText(context, tr("Pasta fora do armazenamento principal"), Toast.LENGTH_SHORT).show()
        return
    }
    val rel = "primary:" + target.absolutePath.removePrefix(base)
    val uri = Uri.parse(
        "content://com.android.externalstorage.documents/document/" + Uri.encode(rel)
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "vnd.android.document/directory")
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            android.util.Log.e("HydroidFolder", "abrir pasta falhou: ${uri}", it)
            Toast.makeText(context, tr("Nenhum app de arquivos encontrado"), Toast.LENGTH_SHORT).show()
        }
}

// ---------- Ajustes ----------

