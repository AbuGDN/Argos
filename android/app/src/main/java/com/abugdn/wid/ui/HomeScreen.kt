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
    // Atalho "Buscar" do ícone do app.
    LaunchedEffect(searchRequest) { if (searchRequest > 0) searchOpen = true }
    val saved by repo.saved.collectAsStateWithLifecycle()
    val readIds by repo.read.collectAsStateWithLifecycle()
    val settings by repo.settings.state.collectAsStateWithLifecycle()

    fun refresh() = scope.launch {
        refreshing = true
        error = repo.refresh().exceptionOrNull()?.let { "Sem conexão — mostrando o que está salvo" }
        refreshing = false
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
                            placeholder = { Text("Buscar nas últimas 48 h e nos salvos") },
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
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refresh() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val data = feed
            val searching = searchOpen && query.isNotBlank()
            // Notícias já abertas vão para a aba "Lidas".
            val inFilter = data?.clusters.orEmpty().filter { tag == null || tag in it.tags }
            val unreadList = inFilter.filter { it.id !in readIds }
            val readList = inFilter.filter { it.id in readIds }
            val clusters = when {
                searching -> search((data?.clusters.orEmpty() + saved).distinctBy { it.id }, query, repo.translator::cached)
                showRead -> readList
                else -> unreadList
            }
            val tagsPresent = if (searching) emptyList() else TAG_LABELS.keys.filter { key -> data?.clusters.orEmpty().any { key in it.tags } }
            val top = data?.topOfDay?.takeIf { tag == null && !searching && !showRead && it.id !in readIds }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (settings.showTicker && !searching) {
                    val headlines = tickerHeadlines(data?.clusters.orEmpty())
                    if (headlines.isNotEmpty()) {
                        item { HeadlineTicker(headlines, { repo.translator.display(it.title, it.lang) }, onOpen) }
                    }
                }
                error?.let { item { Text(it, color = Accent, modifier = Modifier.padding(16.dp, 8.dp)) } }
                item { UpdateBanner(Modifier.padding(16.dp, 8.dp)) }
                if (data == null) {
                    item {
                        Text(
                            if (refreshing) "Carregando…" else "Puxe para baixo para carregar as notícias.",
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                if (tagsPresent.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        }
                    }
                }
                if (tag != null && !searching) {
                    item {
                        androidx.compose.material3.TextButton(
                            onClick = { onRegion(tag) },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("🌍 Página de ${TAG_LABELS[tag] ?: tag}: contexto, tendência e 30 dias") }
                    }
                }
                if (!searching && data != null) {
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
                if (searching) {
                    item {
                        Text(
                            if (clusters.isEmpty()) "Nada encontrado para “$query”." else "${clusters.size} resultado(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp, 8.dp),
                        )
                    }
                }
                if (showWidgetHint && !searching) {
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
                val clock = feed?.global
                if (clock != null && !searching && tag == null) {
                    item { ArgosClock(clock, Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), onClick = onClock) }
                }
                if (top != null) {
                    item {
                        FilledTonalButton(onClick = onStory, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("O dia em 1 minuto")
                        }
                    }
                }
                top?.let { item { TopCard(it, onOpen) } }
                items(clusters.filter { it.id != top?.id }, key = { it.id }) { c ->
                    ClusterRow(c, onOpen)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
        NewsImage(c, Modifier.fillMaxWidth().aspectRatio(16f / 9f), revealable = false)
        Column(Modifier.padding(16.dp)) {
            Text("PRINCIPAL DO DIA", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(translator.display(c.title, c.lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                "✏ MANCHETE ALTERADA".takeIf { c.articles.any { it.edits.isNotEmpty() } },
            )
            if (badges.isNotEmpty()) {
                Text(badges.joinToString(" · "), color = if (c.urgent) Alert else Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                translator.display(c.title, c.lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Meta(c)
        }
        NewsImage(c, Modifier.width(88.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)), revealable = false)
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

/** Busca sem acento no título, resumo, tradução e títulos de todos os veículos do grupo. */
fun search(clusters: List<Cluster>, query: String, translated: (String) -> String): List<Cluster> {
    val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return emptyList()
    return clusters.filter { c ->
        val text = normalize(
            buildString {
                append(c.title).append(' ').append(translated(c.title)).append(' ')
                append(c.summary).append(' ').append(translated(c.summary)).append(' ')
                c.articles.forEach { append(it.title).append(' ').append(translated(it.title)).append(' ') }
            }
        )
        terms.all { it in text }
    }.sortedByDescending { it.updated }
}

/** Quantos veículos entraram na história depois da última vez que ela foi lida. */
fun newSinceRead(c: Cluster, snapshot: Set<String>?): Int =
    if (snapshot == null) 0 else c.articles.map { it.source to it.id }.filter { it.second !in snapshot }.map { it.first }.distinct().size
