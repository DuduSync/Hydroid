package gg.hydroid.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import gg.hydroid.app.data.api.DebridApis
import gg.hydroid.app.data.api.HydraAccountApi
import gg.hydroid.app.data.api.HydraCloudApi
import gg.hydroid.app.data.api.RealDebridApi
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.model.DownloadSource
import gg.hydroid.app.data.model.HydraUser
import gg.hydroid.app.data.i18n.tr
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.data.store.CacheCleaner
import gg.hydroid.app.data.store.StorageUtil
import gg.hydroid.app.download.DownloadEngine
import gg.hydroid.app.download.TorrentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== DOACOES: link usado no botao "Apoiar com Pix" dos creditos =====
private const val DONATION_URL = "https://nubank.com.br/cobrar/7rfap/6aa35e97-c27c-479b-b74b-dbd122db9877"

private enum class SettingsPage { CONTA, INTEGRACOES, FONTES, CONFIG, LOGS, CREDITOS }

@Composable
fun SettingsScreen() {
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    Crossfade(targetState = page, animationSpec = tween(180), label = "settings") { current ->
        when (current) {
            null -> SettingsHome { page = it }
            SettingsPage.CONTA -> AccountPage { page = null }
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
            val auth by AppStore.hydraAuth.collectAsState()
            val user by AppStore.hydraUser.collectAsState()
            AccountNavRow(loggedIn = auth != null, user = user) { onOpen(SettingsPage.CONTA) }
        }
        item {
            NavRow(
                Icons.Filled.Key, tr("Integrações"),
                if (rdKey.isBlank()) tr("Real-Debrid não configurado") else tr("Real-Debrid conectado")
            ) { onOpen(SettingsPage.INTEGRACOES) }
        }
        item {
            NavRow(
                Icons.Filled.Link, tr("Fontes de download"),
                "${sources.size} fonte(s) configurada(s)"
            ) { onOpen(SettingsPage.FONTES) }
        }
        item {
            NavRow(
                Icons.Filled.Settings, tr("Configurações do app"),
                tr("Pasta de downloads, extração automática")
            ) { onOpen(SettingsPage.CONFIG) }
        }
        item {
            NavRow(
                Icons.Filled.Description, tr("Logs e diagnóstico"),
                tr("Exportar histórico técnico")
            ) { onOpen(SettingsPage.LOGS) }
        }
        item {
            NavRow(
                Icons.Filled.Favorite, tr("Créditos"),
                tr("Hydroid 0.8 · fork de estudo do Hydra (MIT)")
            ) { onOpen(SettingsPage.CREDITOS) }
        }
    }
}

@Composable
private fun AccountNavRow(
    loggedIn: Boolean,
    user: HydraUser?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loggedIn && !user?.profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user?.profileImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (loggedIn) (user?.displayName?.ifBlank { tr("Conta Hydra") } ?: tr("Conta Hydra"))
                    else tr("Entrar na conta Hydra"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
                Text(
                    if (loggedIn) (user?.email ?: tr("Conta conectada"))
                    else tr("Sincronize biblioteca e fontes"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun syncAccount(): String {
    HydraAccountApi.profile()
    val games = HydraAccountApi.syncLibrary()
    val sources = HydraAccountApi.syncSources()
    return if (sources < 0) {
        "Sincronizado: +$games jogos. Fontes exigem Hydra Cloud ativo."
    } else {
        "Sincronizado: +$games jogos, +$sources fontes"
    }
}

private fun visibilityLabel(value: String) = when (value) {
    "PRIVATE" -> tr("Privado")
    "FRIENDS" -> tr("Amigos")
    else -> tr("Público")
}

@Composable
private fun VisibilityRow(
    label: String,
    value: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(visibilityLabel(value))
                Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("PUBLIC" to tr("Público"), "FRIENDS" to tr("Amigos"), "PRIVATE" to tr("Privado"))
                    .forEach { (v, l) ->
                        DropdownMenuItem(
                            text = { Text(l) },
                            onClick = {
                                expanded = false
                                if (v != value) onSelect(v)
                            }
                        )
                    }
            }
        }
    }
}

@Composable
private fun AccountPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val auth by AppStore.hydraAuth.collectAsState()
    val user by AppStore.hydraUser.collectAsState()
    val loggedIn = auth != null
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var blocks by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(auth?.accessToken) {
        if (auth != null) {
            runCatching { HydraAccountApi.profile() }
            blocks = HydraAccountApi.blocksCount()
        }
    }

    fun doSync() {
        scope.launch {
            syncing = true
            message = null
            message = syncAccount()
            syncing = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Voltar")) }
            Text(tr("Conta Hydra"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (!loggedIn) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    tr("Entre com a conta Hydra para sincronizar a biblioteca e os recursos vinculados a ela."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text(tr("Email ou usuário")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(tr("Senha")) },
                    singleLine = true,
                    visualTransformation = if (passVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(
                                if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                tr("Mostrar senha")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            val err = HydraAccountApi.signIn(login, password)
                            loading = false
                            if (err != null) error = err else {
                                password = ""
                                message = null
                                doSync()
                            }
                        }
                    },
                    enabled = !loading && login.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Login, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) tr("Entrando...") else tr("Entrar"))
                }
            }
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSection(Icons.Filled.Person, tr("Perfil"), tr("Sua conta Hydra")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!user?.profileImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = user?.profileImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Person, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                user?.displayName?.ifBlank { tr("Conta Hydra") } ?: tr("Conta Hydra"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "@${user?.username ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                user?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsSection(Icons.Filled.Visibility, tr("Conta & Privacidade"), tr("Quem pode ver seu perfil")) {
                    VisibilityRow(tr("Visibilidade do perfil"), user?.profileVisibility ?: "PUBLIC") { v ->
                        scope.launch {
                            if (HydraAccountApi.setVisibility(profile = v)) HydraAccountApi.profile()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    VisibilityRow(tr("Visibilidade das lembranças"), user?.souvenirsVisibility ?: "PUBLIC") { v ->
                        scope.launch {
                            if (HydraAccountApi.setVisibility(souvenirs = v)) HydraAccountApi.profile()
                        }
                    }
                }

                SettingsSection(Icons.Filled.Cloud, tr("Hydra Cloud"), tr("Assinatura")) {
                    val sub = user?.subscription
                    val active = runCatching {
                        sub?.expiresAt?.let { java.time.Instant.parse(it) > java.time.Instant.now() } ?: false
                    }.getOrDefault(false)
                    val date = sub?.expiresAt?.take(10)?.let {
                        it.substring(8, 10) + "/" + it.substring(5, 7) + "/" + it.substring(0, 4)
                    }
                    Text(
                        when {
                            sub == null -> tr("Sem assinatura")
                            active -> "Ativa até $date"
                            else -> "Expirada em $date"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val url = HydraAccountApi.checkoutUrl()
                                if (url != null) uri.openUri(url)
                                else message = tr("Não foi possível abrir o checkout")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr("Renovar Hydra Cloud")) }
                }

                SettingsSection(Icons.Filled.CardGiftcard, tr("Presentes Hydra Cloud"), tr("Receber presentes")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("Permitir que outros usuários me presenteiem"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = user?.allowCloudGifts ?: false,
                            onCheckedChange = { v ->
                                scope.launch {
                                    if (HydraAccountApi.setAllowCloudGifts(v)) HydraAccountApi.profile()
                                }
                            }
                        )
                    }
                }

                SettingsSection(Icons.Filled.Block, tr("Usuários bloqueados"), tr("Bloqueios da conta")) {
                    Text(
                        when (blocks) {
                            null -> tr("Carregando...")
                            0 -> tr("Você não bloqueou nenhum usuário")
                            1 -> tr("1 usuário bloqueado")
                            else -> "$blocks usuários bloqueados"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SettingsSection(Icons.Filled.Lock, tr("Segurança"), tr("Email e senha")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                auth?.accessToken?.let {
                                    uri.openUri("https://auth.hydra.losbroxas.org/update-email?token=$it")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Email, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Atualizar email"), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                auth?.accessToken?.let {
                                    uri.openUri("https://auth.hydra.losbroxas.org/update-password?token=$it")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Atualizar senha"), maxLines = 1)
                        }
                    }
                }

                SettingsSection(Icons.Filled.Refresh, tr("Sincronização"), tr("Biblioteca e fontes da conta")) {
                    Text(
                        tr("Baixa a biblioteca e as fontes de download vinculadas a sua conta Hydra."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { doSync() },
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (syncing) tr("Sincronizando...") else tr("Sincronizar agora"))
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            HydraAccountApi.logout()
                            message = null
                            blocks = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Sair da conta")) }
            }
        }
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    beta: Boolean = false,
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
            if (beta) {
                BetaInfoButton()
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Voltar"))
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
        ),
        modifier = Modifier.fillMaxWidth()
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
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

// linha com valor a direita que abre um dialogo de opcoes
@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
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
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Integrações ----------

@OptIn(ExperimentalMaterial3Api::class)
private enum class DebridService(val id: String, val label: String, val subtitle: String) {
    REAL_DEBRID("rd", tr("Real-Debrid"), tr("Torrents e hosters processados no cloud")),
    PREMIUMIZE("premiumize", tr("Premiumize"), tr("Cloud downloads com torrent e usenet")),
    ALLDEBRID("alldebrid", tr("AllDebrid"), tr("Downloads de torrents e hosters")),
    TORBOX("torbox", tr("TorBox"), tr("Downloads de torrents e hosters"))
}

@Composable
private fun IntegracoesPage(onBack: () -> Unit) {
    var service by remember { mutableStateOf<DebridService?>(null) }
    BackHandler(enabled = service != null) { service = null }
    val current = service
    if (current != null) {
        DebridServicePage(current) { service = null }
        return
    }
    val rdKey by AppStore.rdApiKey.collectAsState()
    val pmKey by AppStore.premiumizeKey.collectAsState()
    val adKey by AppStore.alldebridKey.collectAsState()
    val tbKey by AppStore.torboxKey.collectAsState()
    SettingsPageScaffold(tr("Integrações"), onBack) {
        Text(
            tr("Serviços Debrid são downloaders premium de internet."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        NavRow(
            Icons.Filled.CloudDownload, tr("Real-Debrid"),
            if (rdKey.isBlank()) tr("Não configurado") else tr("Conectado")
        ) { service = DebridService.REAL_DEBRID }
        NavRow(
            Icons.Filled.Cloud, tr("Premiumize"),
            if (pmKey.isBlank()) tr("Não configurado") else tr("Conectado"),
            beta = true
        ) { service = DebridService.PREMIUMIZE }
        NavRow(
            Icons.Filled.Cloud, tr("AllDebrid"),
            if (adKey.isBlank()) tr("Não configurado") else tr("Conectado"),
            beta = true
        ) { service = DebridService.ALLDEBRID }
        NavRow(
            Icons.Filled.Cloud, tr("TorBox"),
            if (tbKey.isBlank()) tr("Não configurado") else tr("Conectado"),
            beta = true
        ) { service = DebridService.TORBOX }
    }
}

@Composable
private fun DebridServicePage(service: DebridService, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyFlow = when (service) {
        DebridService.REAL_DEBRID -> AppStore.rdApiKey
        DebridService.PREMIUMIZE -> AppStore.premiumizeKey
        DebridService.ALLDEBRID -> AppStore.alldebridKey
        DebridService.TORBOX -> AppStore.torboxKey
    }
    val savedKey by keyFlow.collectAsState()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    fun save(k: String) = when (service) {
        DebridService.REAL_DEBRID -> AppStore.setRdKey(k)
        DebridService.PREMIUMIZE -> AppStore.setPremiumizeKey(k)
        DebridService.ALLDEBRID -> AppStore.setAlldebridKey(k)
        DebridService.TORBOX -> AppStore.setTorboxKey(k)
    }

    val placeholder = when (service) {
        DebridService.REAL_DEBRID -> tr("real-debrid.com/apitoken")
        DebridService.PREMIUMIZE -> tr("premiumize.me/account")
        DebridService.ALLDEBRID -> tr("alldebrid.com/apikeys")
        DebridService.TORBOX -> tr("torbox.app/settings")
    }

    SettingsPageScaffold(service.label, onBack) {
        SettingsSection(
            icon = Icons.Filled.Key,
            title = service.label,
            subtitle = service.subtitle
        ) {
            if (service != DebridService.REAL_DEBRID) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BetaInfoButton()
                    Text(
                        tr("Função em beta, toque no ícone para saber mais"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(tr("Chave da API")) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (keyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (keyVisible) tr("Ocultar chave") else tr("Mostrar chave"),
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
                    checking = true
                    status = null
                    scope.launch {
                        runCatching { DebridApis.validate(service.id, keyInput.trim()) }
                            .onSuccess {
                                save(keyInput.trim())
                                ok = true
                                status = it
                                AppLog.i("Debrid", "${service.id} validado: $it")
                            }
                            .onFailure {
                                ok = false
                                status = "Falha: ${it.message}"
                                AppLog.w("Debrid", "${service.id} falhou: ${it.message}")
                            }
                        checking = false
                    }
                }
            ) { Text(if (checking) tr("Verificando...") else tr("Validar e salvar")) }
            if (savedKey.isNotBlank()) {
                TextButton(
                    onClick = {
                        save("")
                        keyInput = ""
                        status = tr("Chave removida")
                        ok = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Remover chave")) }
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
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

    SettingsPageScaffold(tr("Fontes de download"), onBack) {
        SettingsSection(
            icon = Icons.Filled.Link,
            title = tr("Adicionar fonte"),
            subtitle = tr("Registrada via servidor Hydra Cloud")
        ) {
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it },
                label = { Text(tr("URL da fonte (.json)")) },
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
                        else tr("API do Hydra indisponível — salva localmente")
                        sourceUrl = ""
                        addingSource = false
                    }
                }
            ) { Text(if (addingSource) tr("Registrando...") else tr("Adicionar fonte")) }
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
                tr("Nenhuma fonte configurada"),
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
                        tr("Hydra"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { AppStore.removeSource(source.id) }) {
                Icon(Icons.Filled.Delete, tr("Remover"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val wifiOnly by AppStore.wifiOnly.collectAsState()
    val maxConcurrent by AppStore.maxConcurrent.collectAsState()
    val speedLimitKbps by AppStore.speedLimitKbps.collectAsState()
    val language by AppStore.language.collectAsState()
    var folderMsg by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { CacheCleaner.sizeBytes(context) }
    }
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
                folderMsg = tr("Pasta não suportada — escolha no armazenamento do aparelho")
            }
        }
    }

    SettingsPageScaffold(tr("Configurações do app"), onBack) {
        SettingsSection(
            icon = Icons.Filled.Language,
            title = tr("Idioma"),
            subtitle = tr("Idioma do aplicativo")
        ) {
            PickerRow(
                tr("Idioma"),
                tr("Português e inglês"),
                if (language == "en") tr("English") else tr("Português")
            ) { picker = "lang" }
        }

        SettingsSection(
            icon = Icons.Filled.CleaningServices,
            title = tr("Armazenamento"),
            subtitle = tr("Cache de imagens e arquivos temporários")
        ) {
            Text(
                tr("Cache do app") + ": " +
                    (cacheBytes?.let { formatBytes(it) } ?: tr("calculando...")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { CacheCleaner.clear(context) }
                        cacheBytes = withContext(Dispatchers.IO) { CacheCleaner.sizeBytes(context) }
                        Toast.makeText(context, tr("Cache limpo"), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CleaningServices, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Limpar cache"))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Não apaga seus downloads nem as configurações."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSection(
            icon = Icons.Filled.Folder,
            title = tr("Pasta de downloads"),
            subtitle = tr("Onde os jogos serão salvos")
        ) {
            Text(
                if (downloadDir.isBlank())
                    tr("Padrão: /storage/emulated/0/Download/HYDROID")
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
                Text(tr("Escolher pasta"))
            }
            if (downloadDir.isNotBlank()) {
                TextButton(
                    onClick = {
                        AppStore.setDownloadDir("")
                        folderMsg = tr("Pasta padrão restaurada")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Usar padrão")) }
            }
            folderMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        SettingsSection(
            icon = Icons.Filled.Settings,
            title = tr("Pós-download"),
            subtitle = tr("Ações automáticas ao terminar")
        ) {
            SwitchRow(
                tr("Extrair automaticamente"),
                tr("Descompacta .zip e .rar quando o download terminar"),
                autoExtract
            ) { AppStore.setAutoExtract(it) }
            SwitchRow(
                tr("Apagar arquivo após extrair"),
                if (autoExtract) tr("Remove o .zip/.rar para liberar o espaço")
                else tr("Ative a extração automática para usar"),
                deleteArchive,
                enabled = autoExtract
            ) { AppStore.setDeleteArchive(it) }
        }

        SettingsSection(
            icon = Icons.Filled.Download,
            title = tr("Downloads"),
            subtitle = tr("Fila, rede e velocidade")
        ) {
            SwitchRow(
                tr("Só baixar no Wi-Fi"),
                tr("Downloads ficam aguardando até conectar numa rede Wi-Fi"),
                wifiOnly
            ) {
                AppStore.setWifiOnly(it)
                DownloadEngine.kickQueue()
            }
            PickerRow(
                tr("Downloads simultâneos"),
                tr("Quantos downloads rodam ao mesmo tempo"),
                if (maxConcurrent == 0) tr("Sem limite") else maxConcurrent.toString()
            ) { picker = "concurrent" }
            PickerRow(
                tr("Limite de velocidade"),
                tr("Velocidade máxima por download"),
                speedLimitKbpsLabel(speedLimitKbps)
            ) { picker = "speed" }
        }
    }

    if (picker == "concurrent") {
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(tr("Downloads simultâneos")) },
            text = {
                Column {
                    listOf(1, 2, 3, 0).forEach { n ->
                        Text(
                            if (n == 0) tr("Sem limite") else n.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AppStore.setMaxConcurrent(n)
                                    picker = null
                                    DownloadEngine.kickQueue()
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (maxConcurrent == n) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text(tr("Fechar")) } }
        )
    }

    if (picker == "speed") {
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(tr("Limite de velocidade")) },
            text = {
                Column {
                    listOf(0, 512, 1024, 2048, 5120, 10240).forEach { v ->
                        Text(
                            speedLimitKbpsLabel(v),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AppStore.setSpeedLimitKbps(v)
                                    TorrentEngine.applySpeedLimit()
                                    picker = null
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (speedLimitKbps == v) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text(tr("Fechar")) } }
        )
    }

    if (picker == "lang") {
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(tr("Idioma")) },
            text = {
                Column {
                    listOf("pt" to tr("Português (Brasil)"), "en" to tr("English (US)")).forEach { (code, label) ->
                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picker = null
                                    if (AppStore.language.value != code) {
                                        AppStore.setLanguage(code)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (language == code) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text(tr("Fechar")) } }
        )
    }
}

private fun speedLimitKbpsLabel(kbps: Int): String = when {
    kbps <= 0 -> tr("Sem limite")
    kbps < 1024 -> "$kbps KB/s"
    else -> "${kbps / 1024} MB/s"
}

// ---------- Logs ----------

@Composable
private fun LogsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var logSize by remember { mutableStateOf(AppLog.sizeBytes()) }
    var logMsg by remember { mutableStateOf<String?>(null) }

    SettingsPageScaffold(tr("Logs e diagnóstico"), onBack) {
        SettingsSection(
            icon = Icons.Filled.Description,
            title = tr("Arquivo de log"),
            subtitle = tr("Histórico técnico para reportar problemas")
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
                        logMsg = tr("Nenhum log ainda")
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
                            android.content.Intent.createChooser(intent, tr("Compartilhar logs"))
                        )
                    }.onFailure { logMsg = "Falha ao compartilhar: ${it.message}" }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Compartilhar logs"))
            }
            TextButton(
                onClick = {
                    AppLog.clear()
                    logSize = AppLog.sizeBytes()
                    logMsg = tr("Logs apagados")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Limpar logs")) }
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
    SettingsPageScaffold(tr("Créditos"), onBack) {
        SettingsSection(
            icon = Icons.Filled.Favorite,
            title = tr("Hydroid 0.8"),
            subtitle = tr("fork de estudo do Hydra Launcher (MIT)")
        ) {
            Text(
                tr("Port Android não-oficial. Downloads acontecem via suas fontes configuradas ") +
                    tr("e Real-Debrid. Este app não hospeda nem distribui conteúdo."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Desenvolvido por"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    tr("DuduSync"),
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
                        tr("Repositório"),
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
                Text(if (DONATION_URL.isNotBlank()) tr("Apoiar com Pix") else tr("Doações em breve"))
            }
        }
    }
}
