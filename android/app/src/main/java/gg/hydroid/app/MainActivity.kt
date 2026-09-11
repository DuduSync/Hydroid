package gg.hydroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.ui.*

@androidx.compose.material3.ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { HydroidRoot() }
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun HydroidAppTopBar() = CenterAlignedTopAppBar(
    title = { Text("Hydroid", fontWeight = FontWeight.Bold) },
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
)

private val tabs = listOf(
    Tab("Biblioteca", Icons.Filled.Gamepad),
    Tab("Catálogo", Icons.Filled.Search),
    Tab("Downloads", Icons.Filled.Download),
    Tab("Ajustes", Icons.Filled.Settings),
)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun HydroidRoot() {
    var selected by remember { mutableIntStateOf(1) } // comeca no catalogo
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = { HydroidAppTopBar() },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                when (selected) {
                    0 -> LibraryScreen { }
                    1 -> CatalogScreen()
                    2 -> DownloadsScreen()
                    3 -> SettingsScreen()
                }
            }
        }
    }
}
