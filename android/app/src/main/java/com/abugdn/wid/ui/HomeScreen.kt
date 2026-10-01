package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import com.abugdn.wid.data.normalize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.TOPIC_LABELS
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.repository
import com.abugdn.wid.widget.TopWidget
import com.abugdn.wid.widget.TopWidgetReceiver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tag: String?,
    onTag: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
    searchRequest: Int = 0,
    onRegion: (String) -> Unit = {},
    onClock: () -> Unit = {},
    onRoute: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = context.repository
    val feed by repo.feed.collectAsStateWithLifecycle()
    val widgets = remember { GlanceAppWidgetManager(context) }
    var showWidgetHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showWidgetHint = runCatching { widgets.getGlanceIds(TopWidget::class.java).isEmpty() }.getOrDefault(false)
    }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showRead by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var topicsOpen by rememberSaveable { mutableStateOf(false) }
    // Atalho "Buscar" do ícone do app.
    LaunchedEffect(searchRequest) { if (searchRequest > 0) searchOpen = true }
    val saved by repo.saved.collectAsStateWithLifecycle()
    val readIds by repo.read.collectAsStateWithLifecycle()
    val settings by repo.settings.state.collectAsStateWithLifecycle()

    // Ids que chegaram na última atualização do feed (comparando com o feed anterior).
    var knownIds by remember { mutableStateOf<Set<String>?>(null) }
    var fresh by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(feed?.generatedAt) {
        val ids = feed?.clusters.orEmpty().map { it.id }.toSet()
        val before = knownIds
        fresh = if (before == null) emptySet() else ids - before
        knownIds = ids
    }

    fun refresh() = scope.launch {
        refreshing = true
        error = repo.refresh().exceptionOrNull()?.let { "Sem conexão — mostrando o que está salvo" }
        refreshing = false
    }
    // Primeira abertura (nada salvo ainda): carrega sozinho, em vez de pedir para puxar a tela.
    LaunchedEffect(Unit) { if (repo.feed.value == null) refresh() }
    if (topicsOpen) {
        val topicsPresent = TOPIC_LABELS.keys.filter { key -> feed?.clusters.orEmpty().any { key in it.topics } }
        TopicsSheet(topicsPresent, onDismiss = { topicsOpen = false }) { key ->
            topicsOpen = false
            onRoute("topic:$key")
        }
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { searchOpen = false; query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar busca")
                        }
                    },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar notícias e ferramentas") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            } else {
                TopAppBar(actions = {
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, contentDescription = "Buscar") }
                    IconButton(onClick = { onRoute("tools") }) { Icon(Icons.Filled.Build, contentDescription = "Ferramentas") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") }
                }, title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    ArgosEyeIcon(feed?.global?.level, Modifier.padding(end = 10.dp).clickable(onClick = onClock))
                    Column {
                        Text("ARGOS", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 4.sp, color = Accent)
                        feed?.let { f ->
                            val fresh = f.clusters.count { it.id !in readIds && isNewSinceLastVisit(it, repo.previousVisit) }
                            Text(
                                "Atualizado ${relativeTime(f.generatedAt)}" + if (fresh > 0) " · $fresh novas" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    }
                })
            }
        },
    ) { padding ->
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refresh() },
            modifier = Modifier.padding(padding).fillMaxSize(),
            state = pullState,
            // O olho do Argos abre conforme o dedo desce e a íris gira enquanto carrega.
            indicator = { EyePullIndicator(pullState, refreshing, Modifier.align(androidx.compose.ui.Alignment.TopCenter)) },
        ) {
            val data = feed
            val searching = searchOpen && query.isNotBlank()
            // A busca também olha o Arquivo (a principal de cada dia), baixado na primeira busca.
            val archiveState by repo.archive.collectAsStateWithLifecycle()
            LaunchedEffect(searching) { if (searching && repo.archive.value == null) runCatching { repo.loadArchive() } }
            val archiveTops = archiveState.orEmpty().map { it.top }
            // Notícias já abertas vão para a aba "Lidas".
            val inFilter = data?.clusters.orEmpty().filter { tag == null || tag in it.tags }
            val unreadList = inFilter.filter { it.id !in readIds }
            val readList = inFilter.filter { it.id in readIds }
            val clusters = when {
                searching -> search(
                    (data?.clusters.orEmpty() + saved + archiveTops).distinctBy { it.id }, query, repo.translator::cached,
                )
                showRead -> readList
                else -> unreadList
            }
            val tagsPresent = if (searching) emptyList() else TAG_LABELS.keys.filter { key -> data?.clusters.orEmpty().any { key in it.tags } }
            val top = data?.topOfDay?.takeIf {
                tag == null && !searching && !showRead && it.id !in readIds && "top" !in settings.homeHidden
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                error?.let { item { Text(it, color = Accent, modifier = Modifier.padding(16.dp, 8.dp)) } }
                item { UpdateBanner(Modifier.padding(16.dp, 8.dp)) }
                if (data == null) {
                    if (refreshing || error == null) {
                        item { LoadingCards("Baixando as notícias pela primeira vez…") }
                    } else {
                        item { Text("Puxe para baixo para tentar de novo.", modifier = Modifier.padding(24.dp)) }
                    }
                }
                if (searching) {
                    // A busca também acha telas do app ("mapa", "petróleo", "reféns"...).
                    val tools = toolsMatching(query)
                    if (tools.isNotEmpty()) {
                        item {
                            Text(
                                "FERRAMENTAS",
                                style = MaterialTheme.typography.labelMedium,
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp),
                            )
                        }
                        items(tools, key = { "tool-" + it.route + "|" + it.name }) { ToolRow(it, onRoute) }
                    }
                    item {
                        Text(
                            "Filtros: região:irã · fonte:g1 · lado:árabe · depois:01/09 · antes:15/09 · tipo:urgente, conflito, confirmado, umlado, alterada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 0.dp),
                        )
                    }
                    item {
                        Text(
                            if (clusters.isEmpty()) "Nenhuma notícia encontrada para “$query”." else "${clusters.size} notícia(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp, 8.dp),
                        )
                    }
                } else {
                    // Blocos na ordem escolhida em Ajustes → Tela Hoje.
                    settings.homeOrder.filter { it !in settings.homeHidden }.forEach { block ->
                        when (block) {
                            "ticker" -> {
                                val headlines = tickerHeadlines(data?.clusters.orEmpty())
                                if (headlines.isNotEmpty()) {
                                    item(key = "ticker") { HeadlineTicker(headlines, { repo.translator.display(it.title, it.lang) }, onOpen) }
                                }
                            }
                            "panel" -> if (tag == null && data != null) {
                                item(key = "panel") { HomePanel(settings.panelOrder, settings.panelHidden, onRoute) }
                            }
                            "filters" -> if (tagsPresent.isNotEmpty()) {
                                // Uma fileira só: regiões (filtram a lista) e, no fim, "Temas", que abre a
                                // lista de temas (cada um tem página própria). Eram duas fileiras roláveis.
                                val topicsPresent = TOPIC_LABELS.keys.filter { key -> data?.clusters.orEmpty().any { key in it.topics } }
                                item(key = "filters") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) {
                                        item {
                                            FilterChip(selected = tag == null, onClick = { onTag(null) }, label = { Text("Tudo") })
                                        }
                                        items(tagsPresent) { key ->
                                            FilterChip(
                                                selected = tag == key,
                                                onClick = { onTag(if (tag == key) null else key) },
                                                label = { Text(TAG_LABELS.getValue(key)) },
                                            )
                                        }
                                        if (topicsPresent.isNotEmpty()) {
                                            item {
                                                androidx.compose.material3.AssistChip(
                                                    onClick = { topicsOpen = true },
                                                    label = { IconText("🧩 Temas · ${topicsPresent.size}") },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (tag != null) {
                                    item(key = "region-link") {
                                        androidx.compose.material3.TextButton(
                                            onClick = { onRegion(tag) },
                                            modifier = Modifier.padding(start = 8.dp),
                                        ) { IconText("🌍 Tudo sobre ${TAG_LABELS[tag] ?: tag}: tensão, Radar, mapa e 30 dias") }
                                    }
                                }
                            }
                            "top" -> top?.let { item(key = "top") { TopCard(it, onOpen) } }
                        }
                    }
                    if (showWidgetHint) {
                        item {
                            WidgetHint(onAdd = {
                                scope.launch {
                                    // Pede ao launcher para fixar o widget; funciona mesmo quando
                                    // o widget não aparece na lista de widgets do launcher.
                                    val ok = runCatching {
                                        widgets.requestPinGlanceAppWidget(TopWidgetReceiver::class.java)
                                    }.getOrDefault(false)
                                    if (ok) showWidgetHint = false
                                    else error = "Seu launcher não aceita adicionar widget pelo app. Use a lista de widgets da tela inicial."
                                }
                            }, onDismiss = { showWidgetHint = false })
                        }
                    }
                    if (data != null) {
                        item {
                            TabRow(selectedTabIndex = if (showRead) 1 else 0, modifier = Modifier.padding(top = 8.dp)) {
                                Tab(selected = !showRead, onClick = { showRead = false }, text = { Text("Não lidas (${unreadList.size})") })
                                Tab(selected = showRead, onClick = { showRead = true }, text = { Text("Lidas (${readList.size})") })
                            }
                        }
                        if (clusters.isEmpty()) {
                            item {
                                Text(
                                    if (showRead) "Nenhuma notícia lida aqui ainda." else "Você leu tudo por aqui. As abertas estão na aba Lidas.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
                items(clusters.filter { it.id != top?.id }, key = { it.id }) { c ->
                    // Histórias que chegaram nesta sincronização entram deslizando, com brilho dourado.
                    Column(Modifier.animateItem().freshGlow(c.id in fresh)) {
                        ClusterRow(c, onOpen)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetHint(onAdd: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.padding(16.dp, 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Widget da principal do dia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Coloque a notícia mais importante do dia na sua tela inicial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAdd) { Text("Adicionar widget") }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Agora não") }
            }
        }
    }
}

@Composable
private fun TopCard(c: Cluster, onOpen: (String) -> Unit) {
    val translator = LocalContext.current.repository.translator
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth().clickable { onOpen(c.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        NewsImage(c, Modifier.sharedKey("img-${c.id}").fillMaxWidth().aspectRatio(16f / 9f), revealable = false)
        Column(Modifier.padding(16.dp)) {
            Text("PRINCIPAL DO DIA", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(translator.display(c.title, c.lang), modifier = Modifier.sharedKey("title-${c.id}"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (c.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    translator.display(c.summary, c.lang),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Meta(c)
        }
    }
}

@Composable
fun ClusterRow(c: Cluster, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val translator = repo.translator
    val readIds by repo.read.collectAsStateWithLifecycle()
    val isRead = c.id in readIds
    val isNew = !isRead && isNewSinceLastVisit(c, repo.previousVisit)
    val snapshots by repo.snapshots.collectAsStateWithLifecycle()
    val addedSinceRead = if (isRead) newSinceRead(c, snapshots[c.id]) else 0
    val settings by repo.settings.state.collectAsStateWithLifecycle()
    // Modo manchetes: só o título (e o selo de urgente), para passar rápido pelo dia.
    if (settings.headlinesOnly) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(16.dp, 7.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            Text(
                if (c.urgent) "● " else "· ",
                color = if (c.urgent) Alert else Accent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                translator.display(c.title, c.lang) + if (c.sourcesCount > 1) "  (${c.sourcesCount})" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(16.dp, 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val badges = listOfNotNull(
                "URGENTE".takeIf { c.urgent },
                "NOVA".takeIf { isNew },
                "+$addedSinceRead DESDE SUA LEITURA".takeIf { addedSinceRead > 0 },
                sidesBadge(c),
                confidenceBadge(c),
                "✏ MANCHETE ALTERADA".takeIf { c.articles.any { it.edits.isNotEmpty() } },
            )
            if (badges.isNotEmpty()) {
                Text(badges.joinToString(" · "), color = if (c.urgent) Alert else Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                translator.display(c.title, c.lang),
                modifier = Modifier.sharedKey("title-${c.id}"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Meta(c)
        }
        NewsImage(c, Modifier.sharedKey("img-${c.id}").width(88.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)), revealable = false)
    }
}

@Composable
fun Meta(c: Cluster) {
    val count = if (c.sourcesCount > 1) " · ${c.sourcesCount} veículos" else ""
    val tags = c.tags.mapNotNull { TAG_LABELS[it] }.take(2).joinToString(" · ")
    Text(
        listOf("${c.source}$count", relativeTime(c.updated), tags).filter { it.isNotBlank() }.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** História que começou depois da última vez que o app foi aberto. */
fun isNewSinceLastVisit(c: Cluster, previousVisit: Long): Boolean {
    if (previousVisit <= 0) return false
    val start = runCatching { java.time.Instant.parse(c.published).toEpochMilli() }.getOrDefault(0)
    return start > previousVisit
}

/** Busca com filtros: região:irã, fonte:g1, lado:árabe, depois:01/09, antes:15/09, tipo:urgente. */
data class SearchQuery(
    val terms: List<String> = emptyList(),
    val regions: Set<String> = emptySet(),
    val sources: List<String> = emptyList(),
    val sides: Set<String> = emptySet(),
    val after: java.time.LocalDate? = null,
    val before: java.time.LocalDate? = null,
    val types: Set<String> = emptySet(),
) {
    val isEmpty get() = terms.isEmpty() && regions.isEmpty() && sources.isEmpty() && sides.isEmpty() && after == null && before == null && types.isEmpty()
}

private val SIDE_ALIASES = mapOf(
    "israel" to "israel", "israelense" to "israel", "arabe" to "arabe", "arabes" to "arabe", "eua" to "eua", "americana" to "eua",
    "americano" to "eua", "brasil" to "brasil", "brasileira" to "brasil", "internacional" to "internacional", "mundo" to "internacional",
)
val SEARCH_TYPES = mapOf(
    "urgente" to "urgentes", "conflito" to "informações conflitantes", "confirmado" to "várias fontes independentes",
    "umlado" to "só um lado noticiou", "alterada" to "manchete alterada", "tregua" to "violação de trégua",
)

/** Data "15/09", "15/09/2026" ou "2026-09-15"; sem ano, o mais recente que não está no futuro. */
fun parseSearchDate(raw: String, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate? {
    runCatching { return java.time.LocalDate.parse(raw) }
    val parts = raw.split('/', '-', '.').mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return null
    return runCatching {
        if (parts.size >= 3) {
            val y = if (parts[2] < 100) 2000 + parts[2] else parts[2]
            java.time.LocalDate.of(y, parts[1], parts[0])
        } else {
            val d = java.time.LocalDate.of(today.year, parts[1], parts[0])
            if (d.isAfter(today)) d.minusYears(1) else d
        }
    }.getOrNull()
}

fun parseSearch(query: String): SearchQuery {
    var q = SearchQuery()
    val free = mutableListOf<String>()
    for (raw in query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }) {
        val key = normalize(raw.substringBefore(':', ""))
        val value = normalize(raw.substringAfter(':', "")).trim()
        if (value.isEmpty()) { free += normalize(raw); continue }
        when (key) {
            "regiao", "region", "r" -> {
                // Nome exato primeiro ("irã" não pega "Iraque"); se não houver, o começo do nome.
                val words = { label: String -> normalize(label).split(' ', '/', '(', ')').filter { it.isNotBlank() } }
                val exact = TAG_LABELS.filter { (k, label) -> k == value || normalize(label) == value || value in words(label) }.keys
                val tags = exact.ifEmpty {
                    TAG_LABELS.filter { (k, label) -> k.startsWith(value) || words(label).any { w -> w.startsWith(value) } }.keys
                }
                q = q.copy(regions = q.regions + tags.ifEmpty { setOf(value) })
            }
            "fonte", "source", "f", "veiculo" -> q = q.copy(sources = q.sources + value)
            "lado", "origem", "imprensa" -> q = q.copy(sides = q.sides + (SIDE_ALIASES[value] ?: value))
            "depois", "desde", "after", "de" -> q = q.copy(after = parseSearchDate(value))
            "antes", "ate", "before" -> q = q.copy(before = parseSearchDate(value))
            "tipo", "type" -> q = q.copy(types = q.types + value.replace("-", "").replace("_", ""))
            else -> free += normalize(raw)
        }
    }
    return q.copy(terms = free)
}

private fun Cluster.matchesType(type: String): Boolean = when (type) {
    "urgente" -> urgent
    "conflito" -> confidence?.level == "conflito" || figures.values.any { it.divergent }
    "confirmado" -> confidence?.level == "alta"
    "umlado" -> sides == "um_lado"
    "alterada" -> articles.any { it.edits.isNotEmpty() }
    "tregua" -> truceViolation
    else -> true
}

/** Busca sem acento no título, resumo, tradução e títulos de todos os veículos do grupo, com os filtros. */
fun search(clusters: List<Cluster>, query: String, translated: (String) -> String): List<Cluster> {
    val q = parseSearch(query)
    if (q.isEmpty) return emptyList()
    val zone = java.time.ZoneId.systemDefault()
    return clusters.filter { c ->
        if (q.regions.isNotEmpty() && c.tags.none { it in q.regions }) return@filter false
        if (q.sources.isNotEmpty() && q.sources.none { s -> c.articles.any { normalize(it.source).contains(s) } || normalize(c.source).contains(s) }) return@filter false
        if (q.sides.isNotEmpty() && c.articles.none { it.origin in q.sides }) return@filter false
        if (q.after != null || q.before != null) {
            val day = runCatching { java.time.Instant.parse(c.published).atZone(zone).toLocalDate() }.getOrNull() ?: return@filter false
            if (q.after != null && day.isBefore(q.after)) return@filter false
            if (q.before != null && day.isAfter(q.before)) return@filter false
        }
        if (!q.types.all { c.matchesType(it) }) return@filter false
        if (q.terms.isEmpty()) return@filter true
        val text = normalize(
            buildString {
                append(c.title).append(' ').append(translated(c.title)).append(' ')
                append(c.summary).append(' ').append(translated(c.summary)).append(' ')
                c.articles.forEach { append(it.title).append(' ').append(translated(it.title)).append(' ') }
            }
        )
        q.terms.all { it in text }
    }.sortedByDescending { it.updated }
}

/** Quantos veículos entraram na história depois da última vez que ela foi lida. */
fun newSinceRead(c: Cluster, snapshot: Set<String>?): Int =
    if (snapshot == null) 0 else c.articles.map { it.source to it.id }.filter { it.second !in snapshot }.map { it.first }.distinct().size

/** Lista de temas presentes nas notícias de agora; cada um abre a página do tema. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicsSheet(topics: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "TEMAS",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        topics.forEach { key ->
            IconText(
                TOPIC_LABELS.getValue(key),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable { onPick(key) }.padding(24.dp, 14.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
    }
}
