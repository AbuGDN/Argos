package com.abugdn.wid.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.repository
import com.abugdn.wid.sync.SyncWorker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

const val EXTRA_CLUSTER_ID = "cluster_id"

/** Atalhos do ícone do app (res/xml/shortcuts.xml) e de notificações: "story", "search", "saved", "bulletin", "vigil", "clock", "radar". */
const val EXTRA_SHORTCUT = "shortcut"

/** Abre direto a página de uma região (notificação de alta incomum). */
const val EXTRA_REGION = "region"

/** As telas internas não aplicam insets do sistema: a barra de baixo é do Scaffold externo. */
val NoInsets = WindowInsets(0, 0, 0, 0)

/** Ícone da aba Radar: arcos concêntricos com um ponteiro (não existe no material-icons-core). */
private val RadarIcon: ImageVector = ImageVector.Builder("Radar", 24.dp, 24.dp, 24f, 24f).apply {
    val stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)
    path(stroke = stroke, strokeLineWidth = 2f) {
        moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
    }
    path(stroke = stroke, strokeLineWidth = 2f) {
        moveTo(12f, 7.5f); arcTo(4.5f, 4.5f, 0f, true, true, 11.99f, 7.5f)
    }
    path(stroke = stroke, strokeLineWidth = 2f) {
        moveTo(12f, 12f); lineTo(18.4f, 5.6f)
    }
    path(fill = stroke) {
        moveTo(12f, 10.5f); arcTo(1.5f, 1.5f, 0f, true, true, 11.99f, 10.5f); close()
    }
}.build()

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Hoje", Icons.Filled.Home),
    MAP("Mapa", Icons.Filled.Place),
    RADAR("Radar", RadarIcon),
    LIBRARY("Biblioteca", Icons.Filled.Star),
}

class MainActivity : ComponentActivity() {
    private var openCluster by mutableStateOf<String?>(null)
    private var shortcut by mutableStateOf<String?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCluster = intent.getStringExtra(EXTRA_CLUSTER_ID)
        shortcut = intent.getStringExtra(EXTRA_SHORTCUT) ?: intent.getStringExtra(EXTRA_REGION)?.let { "region:$it" }
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) SyncWorker.runNow(this)

        setContent {
            val settings by repository.settings.state.collectAsStateWithLifecycle()
            WidTheme(settings.theme, settings.textScale) {
                val systemOff = remember { animationsOff() }
                val reduceMotion = systemOff || settings.reduceMotion
                CompositionLocalProvider(LocalDataSaver provides settings.dataSaver, LocalReduceMotion provides reduceMotion) {
                    App(
                        openCluster = openCluster,
                        onOpenCluster = { openCluster = it },
                        shortcut = shortcut,
                        onShortcutHandled = { shortcut = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        repository.startVisit()
        // Procura versão nova no GitHub toda vez que o app é aberto ou volta para a frente.
        lifecycleScope.launch { repository.updater.check(force = true) }
    }

    override fun onStop() {
        super.onStop()
        repository.endVisit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CLUSTER_ID)?.let { openCluster = it }
        intent.getStringExtra(EXTRA_SHORTCUT)?.let { openCluster = null; shortcut = it }
        intent.getStringExtra(EXTRA_REGION)?.let { openCluster = null; shortcut = "region:$it" }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun App(
    openCluster: String?,
    onOpenCluster: (String?) -> Unit,
    shortcut: String?,
    onShortcutHandled: () -> Unit,
) {
    val repo = LocalContext.current.repository
    // Coletados para que a busca da notícia aberta se refaça quando os dados chegarem.
    val feed by repo.feed.collectAsStateWithLifecycle()
    val saved by repo.saved.collectAsStateWithLifecycle()
    val archive by repo.archive.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var storyOpen by rememberSaveable { mutableStateOf(false) }
    var searchRequest by remember { mutableIntStateOf(0) }
    var regionOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var vigilOpen by rememberSaveable { mutableStateOf(false) }
    var bulletinOpen by rememberSaveable { mutableStateOf(false) }
    var clockOpen by rememberSaveable { mutableStateOf(false) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    // Telas avulsas: "sirens", "deadlines", "scale", "course".
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var librarySub by rememberSaveable { mutableIntStateOf(0) }
    var radarTab by rememberSaveable { mutableIntStateOf(0) }
    // Mais de 24 h sem abrir: mostra o que a pessoa perdeu (uma vez por abertura).
    val missedSince = remember { repo.previousVisit }
    var missedOpen by rememberSaveable {
        mutableStateOf(missedSince > 0 && System.currentTimeMillis() - missedSince > 24 * 3600 * 1000L)
    }

    fun closeAll() {
        onOpenCluster(null)
        settingsOpen = false; storyOpen = false; regionOpen = null; missedOpen = false
        vigilOpen = false; bulletinOpen = false; clockOpen = false; toolsOpen = false; page = null
    }

    /**
     * Um endereço para cada lugar do app, usado pelas Ferramentas, pela busca, pelo painel da
     * tela Hoje e pelos atalhos do ícone/notificações (ver [Tool]).
     */
    fun go(route: String) {
        val arg = route.substringAfter(':', "")
        when (route.substringBefore(':')) {
            "story" -> { closeAll(); storyOpen = true }
            "search" -> { closeAll(); tab = Tab.HOME; searchRequest++ }
            "saved" -> { closeAll(); librarySub = 0; tab = Tab.LIBRARY }
            "predictions" -> { closeAll(); librarySub = 2; tab = Tab.LIBRARY }
            "library" -> { closeAll(); librarySub = arg.toIntOrNull() ?: 0; tab = Tab.LIBRARY }
            "bulletin" -> { closeAll(); bulletinOpen = true }
            "vigil" -> { closeAll(); vigilOpen = true }
            "clock" -> { closeAll(); clockOpen = true }
            "tools" -> { closeAll(); toolsOpen = true }
            "sirens", "deadlines", "scale", "course", "contradictions", "method" -> { closeAll(); page = route.substringBefore(':') }
            "settings" -> { closeAll(); settingsOpen = true }
            "radar" -> { closeAll(); radarTab = arg.toIntOrNull() ?: 0; tab = Tab.RADAR }
            "region" -> { closeAll(); regionOpen = arg }
            "map" -> {
                closeAll()
                repo.mapFocus.value = when (arg) {
                    "" -> null
                    "military" -> "military:all"
                    else -> arg
                }
                tab = Tab.MAP
            }
        }
    }
    LaunchedEffect(shortcut) {
        val request = shortcut ?: return@LaunchedEffect
        go(request)
        onShortcutHandled()
    }
    // O app fechou sozinho da última vez: mostra o erro para a pessoa poder mandar.
    val context = LocalContext.current
    var crash by remember { mutableStateOf(com.abugdn.wid.CrashLog.pending(context)) }
    crash?.let { text ->
        CrashDialog(text) {
            com.abugdn.wid.CrashLog.clear(context)
            crash = null
        }
    }
    // Aparece a cada abertura até a pessoa marcar "não mostrar de novo".
    val changelog = remember { repo.pendingChangelog() }
    var whatsNewOpen by rememberSaveable { mutableStateOf(changelog.isNotEmpty()) }
    if (whatsNewOpen) {
        WhatsNewDialog(changelog, repo.updater.installedName) { dontShowAgain ->
            if (dontShowAgain) repo.dismissWhatsNew()
            whatsNewOpen = false
        }
    }

    // Abertura com o olho, só quando o app começa do zero (não ao girar a tela).
    val reduceMotion = LocalReduceMotion.current
    var splash by rememberSaveable { mutableStateOf(!reduceMotion) }

    Box {
    Scaffold(
        bottomBar = {
            val overlay = settingsOpen || storyOpen || regionOpen != null || missedOpen || vigilOpen || bulletinOpen || clockOpen || toolsOpen || page != null
            if (openCluster == null && !overlay) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Ficha de armamento → "ver alcance no mapa": fecha o que estiver aberto e vai para o Mapa.
        val openMap: (String) -> Unit = { id ->
            repo.mapFocus.value = id
            onOpenCluster(null)
            settingsOpen = false; storyOpen = false; regionOpen = null; missedOpen = false
            vigilOpen = false; bulletinOpen = false; clockOpen = false; toolsOpen = false; page = null
            tab = Tab.MAP
        }
        // O "dia em 1 minuto" ocupa a tela inteira, inclusive atrás da barra de navegação.
        CompositionLocalProvider(LocalOpenMap provides openMap, LocalGo provides { r: String -> go(r) }) {
        Box(Modifier.padding(bottom = if (storyOpen) 0.dp else padding.calculateBottomPadding())) {
        // A notícia "abre" do cartão da lista: título e foto viajam até a tela da notícia.
        SharedTransitionLayout {
        AnimatedContent(
            targetState = openCluster,
            transitionSpec = {
                if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween(260)) togetherWith fadeOut(tween(200))
            },
            label = "noticia",
        ) { openId ->
        CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalAnimScope provides this@AnimatedContent) {
            val detail = remember(openId, feed, saved, archive) { openId?.let(repo::cluster) }
            when {
                openId != null -> {
                    BackHandler { onOpenCluster(null) }
                    if (detail != null) {
                        DetailScreen(
                            cluster = detail,
                            onBack = { onOpenCluster(null) },
                            onOpen = { onOpenCluster(it) },
                            onRegion = { onOpenCluster(null); regionOpen = it },
                        )
                    } else {
                        MissingScreen(onBack = { onOpenCluster(null) })
                    }
                }
                missedOpen -> {
                    BackHandler { missedOpen = false }
                    MissedScreen(since = missedSince, onClose = { missedOpen = false }, onOpen = { onOpenCluster(it) })
                }
                page != null -> {
                    BackHandler { page = null }
                    when (page) {
                        "sirens" -> SirensScreen(onBack = { page = null })
                        "deadlines" -> DeadlinesScreen(onBack = { page = null }, onOpen = { onOpenCluster(it) })
                        "scale" -> ScaleScreen(onBack = { page = null })
                        "contradictions" -> ContradictionsScreen(onBack = { page = null }, onOpen = { onOpenCluster(it) })
                        "method" -> MethodScreen(onBack = { page = null })
                        else -> CourseScreen(onBack = { page = null }, onRegion = { page = null; regionOpen = it })
                    }
                }
                regionOpen != null -> {
                    BackHandler { regionOpen = null }
                    RegionScreen(tag = regionOpen!!, onBack = { regionOpen = null }, onOpen = { onOpenCluster(it) }, onRoute = { go(it) })
                }
                vigilOpen -> {
                    BackHandler { vigilOpen = false }
                    VigilScreen(onBack = { vigilOpen = false }, onOpen = { onOpenCluster(it) }, onRegion = { regionOpen = it })
                }
                clockOpen -> {
                    BackHandler { clockOpen = false }
                    ClockScreen(onBack = { clockOpen = false }, onRegion = { regionOpen = it })
                }
                bulletinOpen -> {
                    BackHandler { bulletinOpen = false }
                    BulletinScreen(onBack = { bulletinOpen = false })
                }
                storyOpen -> {
                    BackHandler { storyOpen = false }
                    StoryScreen(onClose = { storyOpen = false }, onOpen = { storyOpen = false; onOpenCluster(it) })
                }
                toolsOpen -> {
                    BackHandler { toolsOpen = false }
                    ToolsScreen(onBack = { toolsOpen = false }, onRoute = { go(it) })
                }
                settingsOpen -> {
                    BackHandler { settingsOpen = false }
                    SettingsScreen(onBack = { settingsOpen = false })
                }
                else -> {
                    if (tab != Tab.HOME) BackHandler { tab = Tab.HOME }
                    // Troca de aba desliza para o lado da aba escolhida.
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (slideInHorizontally(tween(240)) { w -> dir * w / 6 } + fadeIn(tween(240))) togetherWith
                                    (slideOutHorizontally(tween(200)) { w -> -dir * w / 6 } + fadeOut(tween(160)))
                            }
                        },
                        label = "aba",
                    ) { current ->
                    when (current) {
                        Tab.HOME -> HomeScreen(
                            tag = tag,
                            onTag = { tag = it },
                            onOpen = { onOpenCluster(it) },
                            onSettings = { settingsOpen = true },
                            onStory = { storyOpen = true },
                            searchRequest = searchRequest,
                            onRegion = { regionOpen = it },
                            onClock = { clockOpen = true },
                            onRoute = { go(it) },
                        )
                        Tab.MAP -> MapScreen(onRegion = { regionOpen = it }, onOpen = { onOpenCluster(it) })
                        Tab.RADAR -> RadarScreen(
                            onOpen = { onOpenCluster(it) },
                            onRegion = { regionOpen = it },
                            tab = radarTab,
                            onTab = { radarTab = it },
                        )
                        Tab.LIBRARY -> LibraryScreen(onOpen = { onOpenCluster(it) }, sub = librarySub, onSub = { librarySub = it })
                    }
                    }
                }
            }
        }
        }
        }
        }
        }
    }
    if (splash) ArgosSplash(onDone = { splash = false })
    }
}

/** Erro do último fechamento inesperado, com botão para compartilhar o texto. */
@Composable
private fun CrashDialog(text: String, onClose: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("O Argos fechou sozinho") },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text("Da última vez o app parou por um erro. Compartilhe o texto abaixo com quem cuida do app para corrigir.")
                Text(
                    text,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Compartilhar erro"))
                onClose()
            }) { Text("Compartilhar") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Fechar") } },
    )
}
