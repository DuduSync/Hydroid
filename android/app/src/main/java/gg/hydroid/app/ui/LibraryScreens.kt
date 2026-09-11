package gg.hydroid.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gg.hydroid.app.data.model.ActiveDownload
import gg.hydroid.app.data.model.DownloadSource
import gg.hydroid.app.data.model.LibraryGame
import gg.hydroid.app.data.api.RealDebridApi
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.download.DownloadEngine
import kotlinx.coroutines.launch

private fun formatBytes(b: Long): String = when {
    b >= 1_073_741_824 -> "%.2f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    else -> "%.0f KB".format(b / 1024.0)
}

// ---------- Biblioteca ----------

@Composable
fun LibraryScreen(onOpenGame: (Long) -> Unit) {
    val games by AppStore.library.collectAsState()
    if (games.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sua biblioteca está vazia", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Adicione jogos pela aba Catálogo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(games) { game: LibraryGame ->
            Card(onClick = { onOpenGame(game.appId) }) {
                Row {
                    AsyncImage(
                        model = game.headerImage,
                        contentDescription = game.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(120.dp).height(56.dp)
                    )
                    Text(
                        game.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp).align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

// ---------- Downloads ----------

@Composable
fun DownloadsScreen() {
    val downloads by AppStore.downloads.collectAsState()
    if (downloads.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nenhum download", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Inicie um download pela página de um jogo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(downloads, key = { it.id }) { dl: ActiveDownload ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(dl.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (dl.stage) {
                            "concluido" -> "Concluído ✓"
                            "erro" -> "Erro: ${dl.error ?: "?"}"
                            "baixando" -> "${formatBytes(dl.speedBps)}/s — ${formatBytes(dl.bytesDownloaded)} de ${formatBytes(dl.totalBytes)}"
                            else -> dl.stage
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (dl.stage != "erro") {
                        LinearProgressIndicator(
                            progress = { dl.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { DownloadEngine.cancel(dl.id) }) { Text("Remover") }
                        dl.savePath?.let {
                            Text(
                                formatBytes(java.io.File(it).length()),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
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
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

        // Real-Debrid
        Text("Real-Debrid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Pegue sua chave em real-debrid.com/apitoken",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it },
            label = { Text("Chave da API") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = keyInput.isNotBlank() && !checking,
                onClick = {
                    checking = true; statusMsg = null
                    scope.launch {
                        try {
                            val user = RealDebridApi(keyInput.trim()).user()
                            AppStore.setRdKey(keyInput.trim())
                            statusMsg = "✓ ${user.username} — premium: ${user.premium == 1}"
                        } catch (e: Exception) {
                            statusMsg = "Chave inválida: ${e.message}"
                        }
                        checking = false
                    }
                }
            ) { Text(if (checking) "Verificando..." else "Validar e salvar") }
            if (rdKey.isNotBlank()) {
                TextButton(onClick = {
                    AppStore.setRdKey("")
                    keyInput = ""
                    statusMsg = "Chave removida"
                }) { Text("Remover") }
            }
        }
        statusMsg?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Fontes de download
        Text("Fontes de download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "URLs de catálogos de download (formato Hydra)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sourceUrl, onValueChange = { sourceUrl = it },
            label = { Text("URL da fonte") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = sourceUrl.isNotBlank() && !checking,
            onClick = {
                checking = true
                val url = sourceUrl.trim()
                scope.launch {
                    // registra na API do Hydra (fetch server-side, passa Cloudflare);
                    // se falhar, cai pra fonte local (só raw URLs sem CF)
                    val registered = gg.hydroid.app.data.api.HydraCloudApi.addRemoteSource(url)
                    AppStore.addSource(
                        registered ?: DownloadSource(
                            id = "local-${url.hashCode()}",
                            name = url.substringAfter("//").substringBefore('/'),
                            url = url,
                            createdAt = java.time.Instant.now().toString()
                        )
                    )
                    statusMsg = if (registered != null)
                        "✓ Fonte \"${registered.name}\" registrada (via servidor Hydra)"
                    else "⚠ API do Hydra indisponível — fonte salva localmente"
                    sourceUrl = ""
                    checking = false
                }
            },
            modifier = Modifier.padding(vertical = 8.dp)
        ) { Icon(Icons.Filled.Add, null); Text(if (checking) " Registrando..." else " Adicionar fonte") }

        Spacer(Modifier.height(8.dp))
        if (sources.isEmpty()) {
            Text(
                "Nenhuma fonte adicionada",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        sources.forEach { source ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(source.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                IconButton(onClick = { AppStore.removeSource(source.id) }) {
                    Icon(Icons.Filled.Delete, "Remover")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Hydroid v0.2 — fork de estudo do Hydra Launcher (MIT). Esta versão roda downloads via Real-Debrid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
