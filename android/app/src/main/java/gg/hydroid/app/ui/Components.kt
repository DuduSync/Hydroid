package gg.hydroid.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
