package gg.hydroid.app.ui

import gg.hydroid.app.ui.theme.glassAwareElevation

import gg.hydroid.app.data.i18n.tr
import gg.hydroid.app.data.log.AppLog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import gg.hydroid.app.R
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.data.store.StorageUtil

private fun hasNotifPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true

private fun isIgnoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

private fun hasFileAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var notifGranted by remember { mutableStateOf(hasNotifPermission(context)) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(context)) }
    var fileAccess by remember { mutableStateOf(hasFileAccess()) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifGranted = hasNotifPermission(context)
        AppLog.i("Setup", "notificacoes: ${if (notifGranted) "permitidas" else "negadas"}")
    }

    // re-checa ao voltar de uma tela de sistema (ex.: bateria)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = hasNotifPermission(context)
                batteryOk = isIgnoringBattery(context)
                fileAccess = hasFileAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            tr("Bem-vindo ao Hydroid"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Antes de começar, quatro ajustes importantes para os downloads funcionarem bem:"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(28.dp))

        SetupStep(
            icon = Icons.Filled.Notifications,
            title = tr("Permitir notificações"),
            subtitle = tr("Mostra o progresso do download e avisa quando terminar"),
            done = notifGranted,
            actionLabel = tr("Permitir"),
            onAction = {
                AppLog.i("Setup", "pedindo permissao de notificacao")
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else notifGranted = true
            }
        )
        Spacer(Modifier.height(14.dp))
        SetupStep(
            icon = Icons.Filled.BatteryAlert,
            title = tr("Desativar otimização de energia"),
            subtitle = tr("Sem isso o Android pode matar o download em segundo plano"),
            done = batteryOk,
            actionLabel = tr("Abrir ajustes"),
            onAction = {
                AppLog.i("Setup", "abrindo ajustes de bateria")
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
        )
        Spacer(Modifier.height(14.dp))
        SetupStep(
            icon = Icons.Filled.Folder,
            title = tr("Acesso a arquivos"),
            subtitle = tr("Necessário para salvar, extrair e gerenciar os jogos baixados"),
            done = fileAccess,
            actionLabel = tr("Permitir"),
            onAction = {
                AppLog.i("Setup", "pedindo acesso a arquivos")
                if (Build.VERSION.SDK_INT >= 30) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                )
                            }
                        }
                } else fileAccess = true
            }
        )

        // passo 4: pasta de downloads escolhida pelo usuario
        val downloadDir by AppStore.downloadDir.collectAsState()
        val folderPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                StorageUtil.persistPermission(context, uri)
                StorageUtil.resolveTreePath(uri)?.let { AppStore.setDownloadDir(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SetupStep(
            icon = Icons.Filled.Download,
            title = tr("Escolha onde baixar"),
            subtitle = if (fileAccess) tr("Os jogos serão salvos na pasta que você escolher")
            else tr("Conclua o acesso a arquivos primeiro"),
            done = downloadDir.isNotBlank(),
            actionLabel = tr("Escolher pasta"),
            onAction = {
                AppLog.i("Setup", "escolher pasta de downloads (acesso a arquivos=$fileAccess)")
                if (fileAccess) folderPicker.launch(null)
            }
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = {
                AppLog.i("Setup", "concluindo setup (pasta=${downloadDir})")
                onDone()
            },
            enabled = fileAccess && downloadDir.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.SportsEsports, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("Começar a usar"), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            tr("Você pode mudar isso depois nas configurações do Android"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SetupStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = glassAwareElevation()),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (done) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (done) Icons.Filled.Check else icon, null,
                    tint = if (done) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!done) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
