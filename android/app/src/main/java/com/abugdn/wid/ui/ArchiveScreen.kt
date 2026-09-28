package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.weekTop
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.repository
import kotlinx.coroutines.launch

/** Sub-aba Arquivo da Biblioteca: a principal de cada dia, do mais recente para o mais antigo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveTab(onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val archive by repo.archive.collectAsStateWithLifecycle()
    val translator = repo.translator
    val first by repo.first.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (repo.first.value == null) repo.loadFirst() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun load() = scope.launch {
        loading = true
        error = repo.loadArchive().isFailure
        loading = false
    }
    LaunchedEffect(Unit) { if (archive == null) load() }

    run {
        PullToRefreshBox(isRefreshing = loading, onRefresh = { load() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (error) item { Text("Sem conexão.", color = Accent, modifier = Modifier.padding(16.dp)) }
                if (archive.isNullOrEmpty() && !loading) {
                    item { Text("Nada no arquivo ainda. O servidor guarda um dia por vez a partir de 24/09/2026.", modifier = Modifier.padding(24.dp)) }
                }
                item { YourWeekCard(onOpen) }
                item { first?.let { FirstRankingCard(it, Modifier.padding(horizontal = 16.dp)) } }
                val week = weekTop(archive.orEmpty())
                if (week.size >= 2) {
                    item {
                        ArgosCard(
                            "🗓 RESUMO DA SEMANA",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            info = "A principal de cada um dos últimos 7 dias, escolhida pelo servidor (mais veículos e mais peso).",
                        ) {
                            run {
                                week.forEachIndexed { i, day ->
                                    Column(Modifier.fillMaxWidth().clickable { onOpen(day.top.id) }.padding(vertical = 8.dp)) {
                                        Text(
                                            "${i + 1}. " + translator.display(day.top.title, day.top.lang),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "${dayLabel(day.date)} · ${day.top.source} · ${day.top.sourcesCount} veículos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                items(archive.orEmpty(), key = { it.date }) { day ->
                    Column {
                        Text(
                            dayLabel(day.date).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                        )
                        ClusterRow(day.top, onOpen)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

/** "Sua semana": leituras dos últimos 7 dias, regiões mais lidas e histórias seguidas ativas. */
@Composable
private fun YourWeekCard(onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val log by repo.readLog.collectAsStateWithLifecycle()
    val followed by repo.followed.collectAsStateWithLifecycle()
    val feed by repo.feed.collectAsStateWithLifecycle()
    val since = java.time.LocalDate.now().toEpochDay() - 6
    val week = log.filter { it.day >= since }
    val stories = week.map { it.id }.distinct().size
    val topRegions = week.distinctBy { it.id }.flatMap { it.tags }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
    val activeFollowed = feed?.clusters.orEmpty().filter { it.id in followed }

    ArgosCard(
        "👤 SUA SEMANA",
        modifier = Modifier.padding(horizontal = 16.dp),
        info = "Contado só no seu celular, com as notícias que você abriu nos últimos 7 dias.",
    ) {
        run {
            Text(
                when (stories) {
                    0 -> "Você ainda não leu nenhuma notícia nos últimos 7 dias."
                    1 -> "Você leu 1 história nos últimos 7 dias."
                    else -> "Você leu $stories histórias nos últimos 7 dias."
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (topRegions.isNotEmpty()) {
                Text(
                    "Mais acompanhadas: " + topRegions.joinToString(", ") { (tag, n) -> "${TAG_LABELS[tag] ?: tag} ($n)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (activeFollowed.isNotEmpty()) {
                Text(
                    "Seguindo (${activeFollowed.size} ativas):",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                activeFollowed.take(3).forEach { c ->
                    Text(
                        "• " + repo.translator.display(c.title, c.lang) + " (${c.sourcesCount} veículos)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
