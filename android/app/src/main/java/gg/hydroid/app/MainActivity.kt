package gg.hydroid.app

import gg.hydroid.app.data.i18n.tr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(gg.hydroid.app.data.i18n.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOpenIntent(intent)
        enableEdgeToEdge()
        setContent {
            HydroidTheme {
                val theme by AppStore.theme.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    if (theme == "glass") gg.hydroid.app.ui.theme.GlassBackground()
                    LaunchedEffect(Unit) { gg.hydroid.app.data.update.UpdateManager.check() }
                    gg.hydroid.app.ui.UpdateDialog()
                    val setupDone by AppStore.setupDone.collectAsState()
                    if (!setupDone) SetupScreen { AppStore.setSetupDone(true) }
                    else HydroidRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    // atalho da tela inicial: gg.hydroid.app.OPEN_GAME com appId do jogo
    private fun handleOpenIntent(intent: Intent?) {
        val appId = intent?.getLongExtra("appId", -1L) ?: -1L
        if (appId > 0) AppStore.openGameFromShortcut(appId)
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
    val catalogVm: CatalogViewModel = viewModel()
    val context = LocalContext.current
    val theme by AppStore.theme.collectAsState()
    val glass = theme == "glass"
    val hazeState = remember { HazeState() }
    val scope = rememberCoroutineScope()

    // paginas deslizaveis: o pager e a UNICA fonte da verdade.
    // clicar na dock anima direto pro destino (ultimo clique vence, sem loop de estados)
    val pagerState = rememberPagerState(initialPage = AppStore.startTab.value) { tabs.size }
    val selected = pagerState.currentPage
    val detailOpen = catalogVm.selectedGame != null
    var lastBackMs by remember { mutableLongStateOf(0L) }
    // clique na dock: cancela a animacao anterior e anima ate a aba (ultimo clique vence)
    var pagerJob by remember { mutableStateOf<Job?>(null) }
    var originTab by remember { mutableIntStateOf(-1) }

    // abre o jogo pedido por atalho da tela inicial
    val pendingGame by AppStore.pendingOpenGame.collectAsState()
    LaunchedEffect(pendingGame) {
        val id = pendingGame ?: return@LaunchedEffect
        AppStore.consumePendingOpenGame()
        val game = AppStore.library.value.firstOrNull { it.appId == id }
        catalogVm.openGame(
            gg.hydroid.app.data.model.SteamSearchItem(name = game?.name ?: "", id = id)
        )
        pagerState.animateScrollToPage(1)
    }

    // ao fechar a pagina do jogo, volta para a aba de origem (ex.: Biblioteca)
    LaunchedEffect(detailOpen) {
        if (!detailOpen && originTab >= 0) {
            pagerState.animateScrollToPage(originTab)
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glass) Modifier.haze(hazeState) else Modifier),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
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
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !detailOpen,
                modifier = Modifier.fillMaxSize(),
                key = { it }
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (page) {
                        0 -> LibraryScreen(onOpenGame = { game ->
                            originTab = 0
                            catalogVm.openFromLibrary(game)
                            scope.launch { pagerState.animateScrollToPage(1) }
                        })
                        1 -> CatalogScreen()
                        2 -> DownloadsScreen()
                        3 -> SettingsScreen()
                    }
                }
            }
        }

        // dock flutuante (GNOME style) por cima do conteudo
        AnimatedVisibility(
            visible = !detailOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingDock(
                selected = selected,
                onSelect = { index ->
                    pagerJob?.cancel()
                    pagerJob = scope.launch { pagerState.animateScrollToPage(index) }
                },
                hazeState = hazeState,
                glass = glass,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

// dock flutuante com pilula que desliza entre os icones
@Composable
private fun FloatingDock(
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    glass: Boolean,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(30.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .then(
                if (glass) Modifier
                    // vidro: desfoca o que passa por tras, SEM tinta (nao muda a cor) e mascarado no formato da dock
                    .hazeChild(
                        state = hazeState,
                        shape = dockShape,
                        style = HazeStyle(
                            backgroundColor = Color.Transparent,
                            tints = emptyList(),
                            blurRadius = 32.dp,
                            noiseFactor = 0f
                        )
                    )
                    // bisel: borda com brilho no topo (luz batendo no vidro)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f))
                        ),
                        shape = dockShape
                    )
                else Modifier
            ),
        shape = dockShape,
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (glass) 0.dp else 6.dp,
        shadowElevation = if (glass) 0.dp else 10.dp,
        border = if (glass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    // gradiente de luz sutil no vidro (topo mais claro)
                    if (glass) Modifier.background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.03f))
                        )
                    ) else Modifier
                )
        ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = maxWidth / tabs.size
            val pillOffset by animateDpAsState(
                targetValue = itemWidth * selected,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                label = "dockPill"
            )
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                Box(
                    Modifier
                        .offset(x = pillOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(percent = 50)
                        )
                )
                Row(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, tab ->
                        val isSel = index == selected
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
