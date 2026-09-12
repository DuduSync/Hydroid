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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gg.hydroid.app.data.update.UpdateManager

const val BETA_MESSAGE =
    "Essa função está em beta. Se você realmente for utilizar, entre em contato com o " +
        "desenvolvedor para ter um suporte melhor e ajudar o projeto a crescer."

// botao universal de aviso: mostra o popup de funcao em beta
@Composable
fun BetaInfoButton(modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }, modifier = modifier.size(28.dp)) {
        Icon(
            Icons.Filled.Info,
            "Função em beta",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text("Entendi") }
            },
            icon = {
                Icon(
                    Icons.Filled.Info, null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { Text("Função em beta") },
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
                is UpdateManager.State.Available -> Text("Nova versão disponível")
                is UpdateManager.State.Downloading -> Text("Baixando atualização")
                is UpdateManager.State.Ready -> Text("Atualização pronta")
                is UpdateManager.State.Failed -> Text("Falha na atualização")
                else -> Text("Atualização")
            }
        },
        text = {
            when (s) {
                is UpdateManager.State.Available -> Text(
                    "O Hydroid v${s.version} já saiu. Quer baixar e instalar agora?"
                )
                is UpdateManager.State.Downloading -> Column {
                    Text("Baixando o novo APK...")
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { s.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UpdateManager.State.Ready -> Text(
                    "O APK foi baixado. Se o instalador não abriu, toque em \"Instalar\" " +
                        "e permita instalar apps desconhecidos quando o Android pedir."
                )
                is UpdateManager.State.Failed -> Text(s.message)
                else -> {}
            }
        },
        confirmButton = {
            when (s) {
                is UpdateManager.State.Available -> TextButton(
                    onClick = { UpdateManager.downloadAndInstall(context) }
                ) { Text("Baixar e instalar") }
                is UpdateManager.State.Ready -> TextButton(
                    onClick = { UpdateManager.install(context, s.file) }
                ) { Text("Instalar") }
                is UpdateManager.State.Failed -> TextButton(
                    onClick = { UpdateManager.downloadAndInstall(context) }
                ) { Text("Tentar de novo") }
                else -> {}
            }
        },
        dismissButton = {
            if (s !is UpdateManager.State.Downloading) {
                TextButton(onClick = { UpdateManager.dismiss() }) { Text("Depois") }
            }
        }
    )
}
