package gg.hydroid.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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

private fun formatBytes(b: Long): String = when {
    b >= 1_073_741_824 -> "%.2f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

// ---------- Biblioteca ----------

@Composable
fun LibraryScreen() {
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
            LibraryCard(game)
        }
    }
}

@Composable
private fun LibraryCard(game: LibraryGame) {
    Card(
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
    val badge = downloadMethodBadge(dl.method)
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
                    "concluido" -> "Concluído"
                    "erro" -> dl.error ?: "Erro desconhecido"
                    "baixando" -> "${formatBytes(dl.speedBps)}/s  ·  ${formatBytes(dl.bytesDownloaded)} de ${formatBytes(dl.totalBytes)}"
                    else -> dl.stage.replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (dl.stage == "erro") MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dl.stage != "erro") {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { dl.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dl.stage == "concluido" && dl.savePath != null) {
                    Icon(
                        Icons.Filled.Folder, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        dl.savePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = { DownloadEngine.cancel(dl.id) }) {
                    Text(if (dl.stage == "concluido") "Limpar" else "Cancelar")
                }
            }
        }
    }
}

// ---------- Ajustes ----------

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    val sources by AppStore.sources.collectAsState()
    val rdKey by AppStore.rdApiKey.collectAsState()
    var keyInput by remember(rdKey) { mutableStateOf(rdKey) }
    var sourceUrl by remember { mutableStateOf("") }
    var rdStatus by remember { mutableStateOf<String?>(null) }
    var rdOk by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var addingSource by remember { mutableStateOf(false) }
    var sourceMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Real-Debrid ----
        item {
            SettingsSection(
                icon = Icons.Filled.Key,
                title = "Real-Debrid",
                subtitle = "Downloads de torrent e hosters via cloud"
            ) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Chave da API") },
                    placeholder = { Text("real-debrid.com/apitoken") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (keyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = if (keyVisible) "Ocultar chave" else "Mostrar chave",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = keyInput.isNotBlank() && !checking,
                        onClick = {
                            checking = true; rdStatus = null
                            scope.launch {
                                runCatching { RealDebridApi(keyInput.trim()).user() }
                                    .onSuccess { user ->
                                        AppStore.setRdKey(keyInput.trim())
                                        rdOk = true
                                        val premium = if (user.premium > 0)
                                            "premium (${user.premium / 86400} dias)" else "sem premium"
                                        rdStatus = "Conectado como ${user.username} — $premium"
                                    }
                                    .onFailure {
                                        rdOk = false
                                        rdStatus = "Falha: ${it.message}"
                                    }
                                checking = false
                            }
                        }
                    ) { Text(if (checking) "Verificando..." else "Validar e salvar") }
                    if (rdKey.isNotBlank()) {
                        TextButton(onClick = {
                            AppStore.setRdKey("")
                            keyInput = ""
                            rdStatus = "Chave removida"
                            rdOk = false
                        }) { Text("Remover") }
                    }
                }
                rdStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rdOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ---- Fontes ----
        item {
            SettingsSection(
                icon = Icons.Filled.Link,
                title = "Fontes de download",
                subtitle = "Registradas via servidor Hydra Cloud"
            ) {
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text("URL da fonte (.json)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    enabled = sourceUrl.isNotBlank() && !addingSource,
                    onClick = {
                        addingSource = true
                        val url = sourceUrl.trim()
                        scope.launch {
                            val registered = HydraCloudApi.addRemoteSource(url)
                            AppStore.addSource(
                                registered ?: DownloadSource(
                                    id = "local-${url.hashCode()}",
                                    name = url.substringAfter("//").substringBefore('/'),
                                    url = url,
                                    createdAt = java.time.Instant.now().toString()
                                )
                            )
                            sourceMsg = if (registered != null)
                                "Fonte \"${registered.name}\" registrada"
                            else "API do Hydra indisponível — salva localmente"
                            sourceUrl = ""
                            addingSource = false
                        }
                    }
                ) { Text(if (addingSource) "Registrando..." else "Adicionar fonte") }
                sourceMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ---- lista de fontes ----
        if (sources.isNotEmpty()) {
            items(sources, key = { it.id }) { source ->
                SourceRow(source)
            }
        }

        // ---- Créditos ----
        item {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            SettingsSection(
                icon = Icons.Filled.Favorite,
                title = "Créditos",
                subtitle = "Hydroid 0.3 · fork de estudo do Hydra Launcher (MIT)"
            ) {
                Text(
                    "Port Android não-oficial. Downloads acontecem via suas fontes configuradas " +
                        "e Real-Debrid. Este app não hospeda nem distribui conteúdo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Desenvolvido por",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "DuduSync",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/DuduSync")
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { uriHandler.openUri("https://github.com/DuduSync/Hydroid") }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Code, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Repositório",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { if (DONATION_URL.isNotBlank()) uriHandler.openUri(DONATION_URL) },
                    enabled = DONATION_URL.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (DONATION_URL.isNotBlank()) "Apoiar com Pix" else "Doações em breve")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SourceRow(source: DownloadSource) {
    val isRemote = !source.id.startsWith("local-")
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    source.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    source.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isRemote) {
                Box(
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Hydra",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { AppStore.removeSource(source.id) }) {
                Icon(
                    Icons.Filled.Delete, "Remover",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
