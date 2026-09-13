package gg.hydroid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gg.hydroid.app.data.i18n.tf
import gg.hydroid.app.data.i18n.tr
import gg.hydroid.app.data.update.UpdateManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.download.DownloadEngine
import gg.hydroid.app.download.DownloadMethod

// mascara de fade no topo: o conteudo dissolve ate sumir (usado sob cabecalhos)
fun Modifier.topFadeMask(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black,
                startY = 0f,
                endY = height.toPx()
            ),
            blendMode = BlendMode.DstIn
        )
    }

// getter (nao const): reavalia o idioma a cada uso
val BETA_MESSAGE: String
    get() = tr(
        "Essa função está em beta. Se você realmente for utilizar, entre em contato com o " +
            "desenvolvedor para ter um suporte melhor e ajudar o projeto a crescer."
    )

// botao universal de aviso: mostra o popup de funcao em beta
@Composable
fun BetaInfoButton(modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = {
        gg.hydroid.app.data.log.AppLog.i("UI", "beta: info aberta")
        show = true
    }, modifier = modifier.size(28.dp)) {
        Icon(
            Icons.Filled.Info,
            tr("Função em beta"),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text(tr("Entendi")) }
            },
            icon = {
                Icon(
                    Icons.Filled.Info, null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { Text(tr("Função em beta")) },
            text = { Text(BETA_MESSAGE) }
        )
    }
}

// popup de atualizacao: aparece quando o GitHub tem uma versao mais nova
@Composable
fun UpdateDialog() {
    val state by UpdateManager.state.collectAsState()
    val context = LocalContext.current
    val s = state
    if (s is UpdateManager.State.Idle) return
    AlertDialog(
        onDismissRequest = { UpdateManager.dismiss() },
        icon = {
            Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            when (s) {
                is UpdateManager.State.Available -> Text(tr("Nova versão disponível"))
                is UpdateManager.State.Downloading -> Text(tr("Baixando atualização"))
                is UpdateManager.State.Ready -> Text(tr("Atualização pronta"))
                is UpdateManager.State.Failed -> Text(tr("Falha na atualização"))
                else -> Text(tr("Atualização"))
            }
        },
        text = {
            when (s) {
                is UpdateManager.State.Available -> Text(
                    tf("O Hydroid v%s já saiu. Quer baixar e instalar agora?", s.version)
                )
                is UpdateManager.State.Downloading -> Column {
                    Text(tr("Baixando o novo APK..."))
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { s.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UpdateManager.State.Ready -> Text(
                    tr(
                        "O APK foi baixado. Se o instalador não abriu, toque em \"Instalar\" " +
                            "e permita instalar apps desconhecidos quando o Android pedir."
                    )
                )
                is UpdateManager.State.Failed -> Text(s.message)
                else -> {}
            }
        },
        confirmButton = {
            when (s) {
                is UpdateManager.State.Available -> TextButton(
                    onClick = { UpdateManager.downloadAndInstall(context) }
                ) { Text(tr("Baixar e instalar")) }
                is UpdateManager.State.Ready -> TextButton(
                    onClick = { UpdateManager.install(context, s.file) }
                ) { Text(tr("Instalar")) }
                is UpdateManager.State.Failed -> TextButton(
                    onClick = { UpdateManager.downloadAndInstall(context) }
                ) { Text(tr("Tentar de novo")) }
                else -> {}
            }
        },
        dismissButton = {
            if (s !is UpdateManager.State.Downloading) {
                TextButton(onClick = { UpdateManager.dismiss() }) { Text(tr("Depois")) }
            }
        }
    )
}


// navegador interno para hosters com espera/login (1fichier etc).
// o usuario baixa pela pagina; o download disparado e capturado (url + cookies) e o app assume
@Composable
fun HostBrowser(request: AppStore.BrowserRequest) {
    BackHandler { AppStore.closeBrowser() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { AppStore.closeBrowser() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Voltar"))
            }
            Text(
                request.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            tr("Toque no botão de download do site; o Hydroid assume o download quando ele começar."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    webViewClient = android.webkit.WebViewClient()
                    setDownloadListener { dlUrl, ua, _, _, _ ->
                        val cookie = android.webkit.CookieManager.getInstance().getCookie(dlUrl).orEmpty()
                        AppStore.closeBrowser()
                        AppLog.i("Hoster",
                            "navegador capturou: ${dlUrl.take(100)} (cookie=${if (cookie.isBlank()) "nao" else "sim"})")
                        val headers = buildMap {
                            if (cookie.isNotBlank()) put("Cookie", cookie)
                            if (!ua.isNullOrBlank()) put("User-Agent", ua)
                        }
                        DownloadEngine.start(
                            AppStore.appContext,
                            id = "dl-${System.currentTimeMillis()}",
                            title = request.title,
                            uri = dlUrl,
                            method = DownloadMethod.DIRETO,
                            headers = headers
                        )
                        android.widget.Toast.makeText(
                            ctx, tr("Download capturado - iniciando"), android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    loadUrl(request.url)
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
            modifier = Modifier.fillMaxSize()
        )
    }
}
