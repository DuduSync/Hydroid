package gg.hydroid.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
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

@Composable
fun LibraryScreen(onOpenGame: (LibraryGame) -> Unit = {}) {
    val games by AppStore.library.collectAsState()
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
            Text("Sua biblioteca está vazia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Adicione jogos pela aba Catálogo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(games, key = { it.appId }) { game: LibraryGame ->
            LibraryCard(game) { onOpenGame(game) }
        }
    }
}

@Composable
private fun LibraryCard(game: LibraryGame, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(460f / 215f)) {
                AsyncImage(
                    model = game.headerImage.ifBlank {
                        "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appId}/header.jpg"
                    },
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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

// ---------- Downloads ----------

private data class MethodBadge(val label: String, val color: Color)

@Composable
private fun downloadMethodBadge(method: String): MethodBadge = when (method) {
    "direto" -> MethodBadge("Direto", MaterialTheme.colorScheme.secondary)
    "torrent" -> MethodBadge("Torrent", MaterialTheme.colorScheme.tertiary)
    "premiumize" -> MethodBadge("Premiumize", MaterialTheme.colorScheme.tertiary)
    "alldebrid" -> MethodBadge("AllDebrid", MaterialTheme.colorScheme.tertiary)
    "torbox" -> MethodBadge("TorBox", MaterialTheme.colorScheme.tertiary)
    else -> MethodBadge("Real-Debrid", MaterialTheme.colorScheme.primary)
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
            Text("Nenhum download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Inicie um download pela página de um jogo",
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
                    "Os arquivos baixados${deleteSize?.let { " ($it)" } ?: ""} serão removidos " +
                        "do aparelho. Não dá para desfazer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch(Dispatchers.IO) {
                        targets.forEach { runCatching { File(it).deleteRecursively() } }
                        withContext(Dispatchers.Main) {
                            DownloadEngine.cancel(dl.id)
                            Toast.makeText(context, "Arquivos apagados", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
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
                    "concluido" -> dl.error ?: "Concluído"
                    "erro" -> dl.error ?: "Erro desconhecido"
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
                        Text("Continuar")
                    }
                    dl.stage == "erro" && dl.uri != null -> TextButton(onClick = { DownloadEngine.resume(dl.id) }) {
                        Text("Tentar de novo")
                    }
                    dl.stage == "baixando" || dl.stage == "resolvendo" ||
                        dl.stage == "conectando ao swarm" || dl.stage.startsWith("cloud") ->
                        TextButton(onClick = { DownloadEngine.pause(dl.id) }) { Text("Pausar") }
                }
                if (dl.stage == "concluido" && path != null && File(path).exists()) {
                    TextButton(onClick = { openFolder(context, path) }) { Text("Abrir pasta") }
                }
                if (dl.stage == "concluido" && isArchive) {
                    TextButton(onClick = { DownloadEngine.extractNow(dl.id) }) { Text("Extrair") }
                }
                if (dl.stage == "concluido" && path != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Apagar") }
                }
                TextButton(onClick = { DownloadEngine.cancel(dl.id) }) {
                    Text(if (dl.stage == "concluido") "Limpar" else "Cancelar")
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
    val base = "/storage/emulated/0/"
    if (!target.absolutePath.startsWith(base)) {
        Toast.makeText(context, "Pasta fora do armazenamento principal", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Nenhum app de arquivos encontrado", Toast.LENGTH_SHORT).show()
        }
}

// ---------- Ajustes ----------

