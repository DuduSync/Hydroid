package gg.hydroid.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import gg.hydroid.app.data.api.HydraCloudApi
import gg.hydroid.app.data.api.RealDebridApi
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.model.DownloadSource
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.data.store.StorageUtil
import kotlinx.coroutines.launch

// ===== DOACOES: link usado no botao "Apoiar com Pix" dos creditos =====
private const val DONATION_URL = "https://nubank.com.br/cobrar/7rfap/6aa35e97-c27c-479b-b74b-dbd122db9877"

private enum class SettingsPage { INTEGRACOES, FONTES, CONFIG, LOGS, CREDITOS }

@Composable
fun SettingsScreen() {
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    Crossfade(targetState = page, animationSpec = tween(180), label = "settings") { current ->
        when (current) {
            null -> SettingsHome { page = it }
            SettingsPage.INTEGRACOES -> IntegracoesPage { page = null }
            SettingsPage.FONTES -> FontesPage { page = null }
            SettingsPage.CONFIG -> AppConfigPage { page = null }
            SettingsPage.LOGS -> LogsPage { page = null }
            SettingsPage.CREDITOS -> CreditosPage { page = null }
        }
    }
}

// ---------- Home ----------

@Composable
private fun SettingsHome(onOpen: (SettingsPage) -> Unit) {
    val sources by AppStore.sources.collectAsState()
    val rdKey by AppStore.rdApiKey.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            NavRow(
                Icons.Filled.Key, "Integrações",
                if (rdKey.isBlank()) "Real-Debrid não configurado" else "Real-Debrid conectado"
            ) { onOpen(SettingsPage.INTEGRACOES) }
        }
        item {
            NavRow(
                Icons.Filled.Link, "Fontes de download",
                "${sources.size} fonte(s) configurada(s)"
            ) { onOpen(SettingsPage.FONTES) }
        }
        item {
            NavRow(
                Icons.Filled.Settings, "Configurações do app",
                "Pasta de downloads, extração automática"
            ) { onOpen(SettingsPage.CONFIG) }
        }
        item {
            NavRow(
                Icons.Filled.Description, "Logs e diagnóstico",
                "Exportar histórico técnico"
            ) { onOpen(SettingsPage.LOGS) }
        }
        item {
            NavRow(
                Icons.Filled.Favorite, "Créditos",
                "Hydroid 0.4 · fork de estudo do Hydra (MIT)"
            ) { onOpen(SettingsPage.CREDITOS) }
        }
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Pagina base ----------

@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
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
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- Integrações ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntegracoesPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rdKey by AppStore.rdApiKey.collectAsState()
    var keyInput by remember(rdKey) { mutableStateOf(rdKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var rdStatus by remember { mutableStateOf<String?>(null) }
    var rdOk by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    SettingsPageScaffold("Integrações", onBack) {
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
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (keyVisible) "Ocultar chave" else "Mostrar chave",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                enabled = keyInput.isNotBlank() && !checking,
                modifier = Modifier.fillMaxWidth(),
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
                                AppLog.i("RD", "chave validada: ${user.username} ($premium)")
                            }
                            .onFailure {
                                rdOk = false
                                rdStatus = "Falha: ${it.message}"
                                AppLog.w("RD", "validação falhou: ${it.message}")
                            }
                        checking = false
                    }
                }
            ) { Text(if (checking) "Verificando..." else "Validar e salvar") }
            if (rdKey.isNotBlank()) {
                TextButton(
                    onClick = {
                        AppStore.setRdKey("")
                        keyInput = ""
                        rdStatus = "Chave removida"
                        rdOk = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Remover chave") }
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
}

// ---------- Fontes ----------

@Composable
private fun FontesPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sources by AppStore.sources.collectAsState()
    var sourceUrl by remember { mutableStateOf("") }
    var addingSource by remember { mutableStateOf(false) }
    var sourceMsg by remember { mutableStateOf<String?>(null) }

    SettingsPageScaffold("Fontes de download", onBack) {
        SettingsSection(
            icon = Icons.Filled.Link,
            title = "Adicionar fonte",
            subtitle = "Registrada via servidor Hydra Cloud"
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
                modifier = Modifier.fillMaxWidth(),
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
                        AppLog.i("Fontes", if (registered != null)
                            "registrada via Hydra: ${registered.name}" else "salva local: $url")
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

        sources.forEach { source ->
            SourceRow(source)
        }
        if (sources.isEmpty()) {
            Text(
                "Nenhuma fonte configurada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
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
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    source.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
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
                Icon(Icons.Filled.Delete, "Remover", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Config do app ----------

@Composable
private fun AppConfigPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val downloadDir by AppStore.downloadDir.collectAsState()
    val autoExtract by AppStore.autoExtract.collectAsState()
    val deleteArchive by AppStore.deleteArchive.collectAsState()
    var folderMsg by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            StorageUtil.persistPermission(context, uri)
            val path = StorageUtil.resolveTreePath(uri)
            if (path != null) {
                AppStore.setDownloadDir(path)
                folderMsg = "Pasta definida: $path"
            } else {
                folderMsg = "Pasta não suportada — escolha no armazenamento do aparelho"
            }
        }
    }

    SettingsPageScaffold("Configurações do app", onBack) {
        SettingsSection(
            icon = Icons.Filled.Folder,
            title = "Pasta de downloads",
            subtitle = "Onde os jogos serão salvos"
        ) {
            Text(
                if (downloadDir.isBlank())
                    "Padrão: /storage/emulated/0/Download/HYDROID"
                else downloadDir,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Escolher pasta")
            }
            if (downloadDir.isNotBlank()) {
                TextButton(
                    onClick = {
                        AppStore.setDownloadDir("")
                        folderMsg = "Pasta padrão restaurada"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Usar padrão") }
            }
            folderMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        SettingsSection(
            icon = Icons.Filled.Settings,
            title = "Pós-download",
            subtitle = "Ações automáticas ao terminar"
        ) {
            SwitchRow(
                "Extrair automaticamente",
                "Descompacta .zip e .rar quando o download terminar",
                autoExtract
            ) { AppStore.setAutoExtract(it) }
            SwitchRow(
                "Apagar arquivo após extrair",
                "Remove o .zip/.rar para liberar o espaço",
                deleteArchive
            ) { AppStore.setDeleteArchive(it) }
        }
    }
}

// ---------- Logs ----------

@Composable
private fun LogsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var logSize by remember { mutableStateOf(AppLog.sizeBytes()) }
    var logMsg by remember { mutableStateOf<String?>(null) }

    SettingsPageScaffold("Logs e diagnóstico", onBack) {
        SettingsSection(
            icon = Icons.Filled.Description,
            title = "Arquivo de log",
            subtitle = "Histórico técnico para reportar problemas"
        ) {
            Text(
                "Tamanho: ${formatBytes(logSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    val file = AppLog.file()
                    if (file == null || !file.exists()) {
                        logMsg = "Nenhum log ainda"
                        return@FilledTonalButton
                    }
                    runCatching {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "gg.hydroid.app.fileprovider", file
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Compartilhar logs")
                        )
                    }.onFailure { logMsg = "Falha ao compartilhar: ${it.message}" }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartilhar logs")
            }
            TextButton(
                onClick = {
                    AppLog.clear()
                    logSize = AppLog.sizeBytes()
                    logMsg = "Logs apagados"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Limpar logs") }
            logMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------- Créditos ----------

@Composable
private fun CreditosPage(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SettingsPageScaffold("Créditos", onBack) {
        SettingsSection(
            icon = Icons.Filled.Favorite,
            title = "Hydroid 0.4",
            subtitle = "fork de estudo do Hydra Launcher (MIT)"
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
                    modifier = Modifier.clickable { uriHandler.openUri("https://github.com/DuduSync") }
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .clickable { uriHandler.openUri("https://github.com/DuduSync/Hydroid") }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Repositório",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
