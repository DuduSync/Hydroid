package gg.hydroid.app

import gg.hydroid.app.data.i18n.tr

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.ui.CatalogScreen
import gg.hydroid.app.ui.CatalogViewModel
import gg.hydroid.app.ui.DownloadsScreen
import gg.hydroid.app.ui.LibraryScreen
import gg.hydroid.app.ui.SettingsScreen
import gg.hydroid.app.ui.SetupScreen
import gg.hydroid.app.ui.theme.HydroidTheme

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(gg.hydroid.app.data.i18n.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HydroidTheme {
                LaunchedEffect(Unit) { gg.hydroid.app.data.update.UpdateManager.check() }
                gg.hydroid.app.ui.UpdateDialog()
                val setupDone by AppStore.setupDone.collectAsState()
                if (!setupDone) SetupScreen { AppStore.setSetupDone(true) }
                else HydroidRoot()
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

// getter (nao val fixo): reavalia o idioma a cada uso
private val tabs: List<Tab>
    get() = listOf(
        Tab(tr("Biblioteca"), Icons.Filled.SportsEsports),
        Tab(tr("Catálogo"), Icons.Filled.Search),
        Tab(tr("Downloads"), Icons.Filled.Download),
        Tab(tr("Ajustes"), Icons.Filled.Settings),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HydroidRoot() {
    var selected by rememberSaveable { mutableIntStateOf(1) }
    val catalogVm: CatalogViewModel = viewModel()
    val detailOpen = catalogVm.selectedGame != null
    val context = LocalContext.current
    var lastBackMs by remember { mutableLongStateOf(0L) }
    var originTab by remember { mutableIntStateOf(-1) }

    // ao fechar a pagina do jogo, volta para a aba de origem (ex.: Biblioteca)
    LaunchedEffect(detailOpen) {
        if (!detailOpen && originTab >= 0) {
            selected = originTab
            originTab = -1
        }
    }

    // fecha so com dois toques no voltar (detalhe/subpaginas tem prioridade via LIFO)
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackMs < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackMs = now
            Toast.makeText(context, tr("Toque em voltar de novo para sair"), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (detailOpen) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!detailOpen) {
                Text(
                    tr("Hydroid"),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, top = 6.dp, bottom = 6.dp)
                )
            }
        },
        bottomBar = {
            if (!detailOpen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selected,
            animationSpec = tween(200),
            label = "tabs"
        ) { tab ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when (tab) {
                    0 -> LibraryScreen(onOpenGame = { game ->
                        originTab = 0
                        catalogVm.openFromLibrary(game)
                        selected = 1
                    })
                    1 -> CatalogScreen()
                    2 -> DownloadsScreen()
                    3 -> SettingsScreen()
                }
            }
        }
    }
}
