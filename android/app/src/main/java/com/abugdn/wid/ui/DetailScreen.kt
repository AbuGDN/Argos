package com.abugdn.wid.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.abugdn.wid.data.ORIGIN_LABELS
import com.abugdn.wid.data.REGION_CONTEXT
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.actors
import com.abugdn.wid.data.isPerson
import com.abugdn.wid.data.SavedMeta
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.abugdn.wid.data.ArticleRef
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.FullText
import com.abugdn.wid.repository

private sealed interface TextState {
    data object Loading : TextState
    data class Ready(val text: FullText) : TextState
    data object Failed : TextState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(cluster: Cluster, onBack: () -> Unit, onOpen: (String) -> Unit, onRegion: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.repository
    val saved by repo.saved.collectAsStateWithLifecycle()
    val isSaved = saved.any { it.id == cluster.id }
    val followed by repo.followed.collectAsStateWithLifecycle()
    val isFollowed = cluster.id in followed
    val translator = repo.translator
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    // Carimbo que bate ao salvar ou seguir.
    var stamp by remember { mutableStateOf(0 to "") }

    val textState by produceState<TextState>(TextState.Loading, cluster.id) {
        value = repo.fullText(cluster).fold({ TextState.Ready(it) }, { TextState.Failed })
    }
    val title = translator.display(cluster.title, cluster.lang)
    // Veículos que a história tinha na leitura anterior (antes de marcar a leitura de agora).
    val previousSnapshot = remember(cluster.id) { repo.snapshots.value[cluster.id] }
    val newIds = previousSnapshot?.let { snap -> cluster.articles.map { it.id }.filter { it !in snap }.toSet() }.orEmpty()
    val newSources = cluster.articles.filter { it.id in newIds }.map { it.source }.distinct()
    LaunchedEffect(cluster.id) { repo.markRead(cluster) }
    var profileOf by remember { mutableStateOf<String?>(null) }
    profileOf?.let { SourceProfileDialog(it) { profileOf = null } }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(cluster.source, modifier = Modifier.clickable { profileOf = cluster.source }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!isFollowed) stamp = (stamp.first + 1) to "SEGUINDO"
                        repo.toggleFollow(cluster)
                    }) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = if (isFollowed) "Deixar de seguir" else "Seguir história",
                            tint = if (isFollowed) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        if (!isSaved) stamp = (stamp.first + 1) to "ARQUIVADO"
                        repo.toggleSaved(cluster)
                    }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = if (isSaved) "Remover dos salvos" else "Salvar",
                            tint = if (isSaved) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        var shareMenu by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        IconButton(onClick = { shareMenu = true }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
                        }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Compartilhar link") },
                                onClick = { shareMenu = false; share(context, title, cluster.url) },
                            )
                            DropdownMenuItem(
                                text = { Text("Compartilhar como imagem") },
                                onClick = { shareMenu = false; scope.launch { shareAsImage(context, cluster) } },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            NewsImage(cluster, Modifier.sharedKey("img-${cluster.id}").fillMaxWidth().aspectRatio(16f / 9f))
            val image = cluster.image
            if (image != null && !LocalDataSaver.current) {
                // Busca reversa: mostra se a foto já circulou antes (outra data, outro conflito).
                TextButton(
                    onClick = { openUrl(context, "https://lens.google.com/uploadbyurl?url=" + java.net.URLEncoder.encode(image, "UTF-8")) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("🔍 Checar esta imagem (busca reversa)", style = MaterialTheme.typography.labelMedium) }
            }
            Column(Modifier.padding(16.dp)) {
                if (cluster.urgent) {
                    Text("URGENTE", color = Alert, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(title, modifier = Modifier.sharedKey("title-${cluster.id}"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Meta(cluster)
                if (isFollowed) {
                    Text(
                        "Seguindo: você será avisado quando outros veículos noticiarem.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (newSources.isNotEmpty()) {
                    Text(
                        "+${newSources.size} ${if (newSources.size == 1) "veículo" else "veículos"} desde que você leu: " +
                            newSources.joinToString(", "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                // A notícia em três partes: o texto, como foi coberta e o contexto em volta.
                val coverageCount = listOf(
                    cluster.sides != null, cluster.figures.isNotEmpty(), cluster.framing.isNotEmpty(),
                    cluster.articles.any { it.edits.isNotEmpty() },
                ).count { it }
                var section by rememberSaveable(cluster.id) { mutableStateOf(0) }
                androidx.compose.material3.TabRow(selectedTabIndex = section, modifier = Modifier.padding(top = 12.dp)) {
                    androidx.compose.material3.Tab(selected = section == 0, onClick = { section = 0 }, text = { Text("Texto") })
                    androidx.compose.material3.Tab(selected = section == 1, onClick = { section = 1 }, text = {
                        Text("Cobertura · ${cluster.sourcesCount}" + if (coverageCount > 0) " ●" else "")
                    })
                    androidx.compose.material3.Tab(selected = section == 2, onClick = { section = 2 }, text = { Text("Contexto") })
                }
                // A checagem aparece em qualquer aba: é um aviso sobre a história inteira.
                FactcheckCard(cluster)
                when (section) {
                    1 -> {
                        SidesCard(cluster)
                        FiguresCard(cluster)
                        EditsCard(cluster)
                        FramingCard(cluster)
                        Perspectives(cluster, newIds) { profileOf = it }
                        if (cluster.articles.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("Linha do tempo · ${cluster.sourcesCount} veículos", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            val ordered = cluster.articles.sortedBy { it.published }
                            ordered.forEachIndexed { i, a ->
                                TimelineItem(a, first = i == 0, last = i == ordered.lastIndex, isNew = a.id in newIds) { openUrl(context, a.url) }
                            }
                        }
                    }
                    2 -> {
                        ContextChips(cluster, onOpen, onRegion)
                        Text(
                            "Toque numa pessoa, grupo, arma ou região para abrir o cartão.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        SagaCard(cluster, onOpen)
                        cluster.tags.firstOrNull { it in com.abugdn.wid.data.TAG_LABELS }?.let { tag ->
                            RegionRadarCard(tag)
                            TextButton(onClick = { onRegion(tag) }) {
                                Text("🌍 Tudo sobre ${com.abugdn.wid.data.TAG_LABELS[tag]}")
                            }
                        }
                    }
                    else -> {
                        if (isSaved) SavedMetaSection(cluster.id)
                        Spacer(Modifier.height(16.dp))
                        ArticleText(cluster, textState, showOriginal) { showOriginal = !showOriginal }
                        OutlinedButton(onClick = { openUrl(context, cluster.url) }, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text("Abrir no site (${cluster.source})")
                        }
                        if (coverageCount > 0 || cluster.sourcesCount > 1) {
                            TextButton(onClick = { section = 1 }) {
                                Text("Ver como ${cluster.sourcesCount} veículos cobriram →")
                            }
                        }
                    }
                }
            }
        }
    }
    StampFlash(stamp.first, stamp.second, Modifier.align(androidx.compose.ui.Alignment.Center))
    }
}

/** Texto completo (traduzido) ou o resumo enquanto baixa. */
@Composable
private fun ArticleText(cluster: Cluster, state: TextState, showOriginal: Boolean, onToggleOriginal: () -> Unit) {
    val repo = LocalContext.current.repository
    when (state) {
        TextState.Loading -> {
            Summary(cluster)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("Baixando e traduzindo o texto completo…")
            }
        }
        TextState.Failed -> {
            Summary(cluster)
            Text(
                "Não foi possível baixar o texto completo (sem conexão ou paywall).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is TextState.Ready -> {
            val text = state.text
            val translated = text.translated
            val paragraphs = if (translated != null && !showOriginal) translated else text.paragraphs
            // ~200 palavras por minuto.
            val minutes = (paragraphs.sumOf { p -> p.split(' ').size } / 200).coerceAtLeast(1)
            Text(
                "Texto: ${text.source} · ~$minutes min de leitura" +
                    if (translated != null) " · traduzido automaticamente" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (translated != null) {
                    TextButton(onClick = onToggleOriginal) {
                        Text(if (showOriginal) "Ver tradução" else "Ver original")
                    }
                }
                ReaderMenu()
            }
            val settings by repo.settings.state.collectAsStateWithLifecycle()
            val base = MaterialTheme.typography.bodyLarge
            val readerStyle = base.copy(
                fontFamily = if (settings.readerSerif) FontFamily.Serif else base.fontFamily,
                lineHeight = if (settings.readerWide) base.fontSize * 1.75f else base.lineHeight,
            )
            paragraphs.forEach {
                Text(
                    it,
                    style = readerStyle,
                    modifier = Modifier.padding(bottom = if (settings.readerWide) 18.dp else 12.dp),
                )
            }
        }
    }
}

private fun actorIcon(a: com.abugdn.wid.data.Actor): String = when {
    isPerson(a) -> "👤 "
    a.key in com.abugdn.wid.data.WEAPON_SHEETS -> "🔫 "
    else -> "ⓘ "
}

/** Chips "ⓘ Hamas", "🔫 Shahed", "👤 Netanyahu", "🌍 Gaza"… que abrem um cartão de contexto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextChips(cluster: Cluster, onOpen: (String) -> Unit, onRegion: (String) -> Unit) {
    val translator = LocalContext.current.repository.translator
    val actors = remember(cluster.id) { cluster.actors(translator::cached) }
    val regions = cluster.tags.filter { it in REGION_CONTEXT }
    if (actors.isEmpty() && regions.isEmpty()) return
    var openActor by remember { mutableStateOf<com.abugdn.wid.data.Actor?>(null) }
    var openRegion by remember { mutableStateOf<String?>(null) }

    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actors.forEach { a ->
            val person = com.abugdn.wid.data.isPerson(a)
            AssistChip(
                onClick = { openActor = a },
                label = { Text(if (person) a.name else actorIcon(a) + a.name) },
                leadingIcon = if (person) {
                    { WikiImage(com.abugdn.wid.data.WIKI_TITLES[a.key], person = true, size = 22.dp) }
                } else null,
            )
        }
        regions.forEach { tag ->
            AssistChip(
                onClick = { onRegion(tag) },
                label = { Text(TAG_LABELS[tag] ?: tag) },
                leadingIcon = { RegionFlags(tag, height = 14.dp) },
            )
        }
    }
    openActor?.let { a -> ActorContextDialog(a, onOpen, exclude = cluster.id) { openActor = null } }
    openRegion?.let { tag -> RegionContextDialog(tag, onOpen, exclude = cluster.id) { openRegion = null } }
}

/** "Como cada lado noticiou": títulos agrupados pela origem do veículo. */
@Composable
private fun Perspectives(cluster: Cluster, newIds: Set<String>, onProfile: (String) -> Unit) {
    val translator = LocalContext.current.repository.translator
    val context = LocalContext.current
    val groups = ORIGIN_LABELS.keys.associateWith { origin -> cluster.articles.filter { it.origin == origin } }
        .filterValues { it.isNotEmpty() }
    if (groups.size < 2) return

    Spacer(Modifier.height(16.dp))
    Text("Como cada lado noticiou", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    groups.forEach { (origin, articles) ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(ORIGIN_LABELS.getValue(origin).uppercase(), style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.Bold)
                articles.distinctBy { it.source }.forEach { a ->
                    Column(Modifier.fillMaxWidth().clickable { openUrl(context, a.url) }.padding(vertical = 6.dp)) {
                        Text(translator.display(a.title, a.lang), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "ⓘ ${a.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onProfile(a.source) }.padding(vertical = 4.dp),
                            )
                            if (a.id in newIds) {
                                Text("  NOVO", style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Botão "Aa": fonte serifada e espaçamento amplo no texto completo. */
@Composable
private fun ReaderMenu() {
    val store = LocalContext.current.repository.settings
    val s by store.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Aa", fontWeight = FontWeight.Bold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text((if (s.readerSerif) "✓ " else "") + "Fonte serifada") },
                onClick = { store.update { it.copy(readerSerif = !it.readerSerif) } },
            )
            DropdownMenuItem(
                text = { Text((if (s.readerWide) "✓ " else "") + "Espaçamento amplo") },
                onClick = { store.update { it.copy(readerWide = !it.readerWide) } },
            )
        }
    }
}

/** Pasta e nota pessoal de uma notícia salva, com edição num diálogo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedMetaSection(id: String) {
    val repo = LocalContext.current.repository
    val allMeta by repo.savedMeta.collectAsStateWithLifecycle()
    val meta = allMeta[id] ?: SavedMeta()
    var editing by remember { mutableStateOf(false) }

    ArgosCard(
        "★ SALVA" + (meta.folder?.let { " · PASTA: ${it.uppercase()}" } ?: ""),
        onClick = { editing = true },
    ) {
        run {
            Text(
                meta.note ?: "Toque para escolher uma pasta ou escrever uma nota.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (meta.note == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (editing) {
        var folder by remember { mutableStateOf(meta.folder.orEmpty()) }
        var note by remember { mutableStateOf(meta.note.orEmpty()) }
        val folders = remember { repo.folders() }
        AlertDialog(
            onDismissRequest = { editing = false },
            confirmButton = {
                TextButton(onClick = { repo.updateSavedMeta(id, folder, note); editing = false }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
            title = { Text("Pasta e nota") },
            text = {
                Column {
                    if (folders.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            folders.forEach { f ->
                                FilterChip(selected = folder == f, onClick = { folder = if (folder == f) "" else f }, label = { Text(f) })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("Pasta (ex.: Irã nuclear)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota pessoal") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

@Composable
private fun Summary(cluster: Cluster) {
    if (cluster.summary.isBlank()) return
    Text(
        LocalContext.current.repository.translator.display(cluster.summary, cluster.lang),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(12.dp))
}

/** Um ponto na linha do tempo: horário, veículo e título, ligados por uma linha vertical. */
@Composable
private fun TimelineItem(a: ArticleRef, first: Boolean, last: Boolean, isNew: Boolean, onClick: () -> Unit) {
    val translator = LocalContext.current.repository.translator
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick)) {
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                Box(Modifier.width(2.dp).height(14.dp).background(if (first) MaterialTheme.colorScheme.surface else line))
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (first) Accent else line))
                Box(Modifier.width(2.dp).weight(1f).background(if (last) MaterialTheme.colorScheme.surface else line))
            }
        }
        Column(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Text(
                "${dayClock(a.published)} · ${a.source}" + (if (first) " · primeiro a noticiar" else "") +
                    if (isNew) " · NOVO desde sua leitura" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (first || isNew) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(translator.display(a.title, a.lang), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Argos") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        Text("Carregando… (se não aparecer, a notícia saiu do feed)", modifier = Modifier.padding(padding).padding(24.dp))
    }
}

fun openUrl(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}

private fun share(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    context.startActivity(Intent.createChooser(send, "Compartilhar notícia"))
}
