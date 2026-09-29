package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.CONTEXT_DISCLAIMER
import com.abugdn.wid.data.MILESTONES
import com.abugdn.wid.data.REGION_CONTEXT
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.repository
import java.time.LocalDate

/**
 * Página de uma região: o lugar central de tudo sobre ela. Tensão, trégua, Radar, mapa, quem
 * manda, reféns, agenda, apostas, vozes oficiais, contexto, notícias atuais, 30 dias e marcos.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RegionScreen(tag: String, onBack: () -> Unit, onOpen: (String) -> Unit, onRoute: (String) -> Unit = {}) {
    val repo = LocalContext.current.repository
    val feed by repo.feed.collectAsStateWithLifecycle()
    val archive by repo.archive.collectAsStateWithLifecycle()
    val stats by repo.stats.collectAsStateWithLifecycle()
    LaunchedEffect(tag) {
        if (repo.archive.value == null) repo.loadArchive()
        if (repo.stats.value == null) repo.loadStats()
    }
    val label = TAG_LABELS[tag] ?: tag
    val current = feed?.clusters.orEmpty().filter { tag in it.tags }
    val cutoff = LocalDate.now().minusDays(30).toString()
    val pastTops = archive.orEmpty().filter { tag in it.top.tags && it.date >= cutoff && current.none { c -> c.id == it.top.id } }
    val trend = stats?.days.orEmpty().takeLast(14).map { it.counts[tag] ?: 0 }
    val tensionDays = stats?.days.orEmpty().takeLast(30).mapNotNull { d -> d.tension[tag]?.let { d.date to it } }
    val vigil by repo.vigil.collectAsStateWithLifecycle()
    val peaks = vigil.filter { it.region == tag && (it.kind == "tension" || it.kind == "spike") }.take(5)

    val stat = feed?.regions?.get(tag)
    val radar by repo.radar.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(label, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
                    RegionFlags(tag, Modifier.padding(bottom = 10.dp), height = 32.dp)
                    ConflictCounter(tag, Modifier.padding(bottom = 12.dp))
                    stat?.let { TensionGauge(it) }
                    SinceLastVisitCard(tag, current, stat, onOpen)
                    stat?.let { TensionBreakdown(it) }
                    Text(
                        "TENSÃO · 30 DIAS",
                        style = MaterialTheme.typography.labelMedium,
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    )
                    TensionHistoryChart(tensionDays)
                    peaks.forEach { e ->
                        Text(
                            "⚑ ${dayClock(java.time.Instant.ofEpochMilli(e.time).toString())} · ${e.title} · ${e.detail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Alert,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    // Atalhos para as outras telas, já no lugar desta região.
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.AssistChip(onClick = { onRoute("map:at:$tag") }, label = { Text("🗺 Ver no mapa") })
                        androidx.compose.material3.AssistChip(onClick = { onRoute("radar:0") }, label = { Text("📡 Radar") })
                        if (tag == "ucrania_russia") {
                            androidx.compose.material3.AssistChip(onClick = { onRoute("map:frontline") }, label = { Text("🇺🇦 Linha de frente") })
                        }
                        androidx.compose.material3.AssistChip(onClick = { onRoute("map:trend") }, label = { Text("📊 Tendência") })
                    }
                    TruceCards(tag)
                    RegionRadarCard(tag)
                    if (com.abugdn.wid.data.POWER.any { it.tag == tag }) PowerCards(only = tag)
                    if (tag == "israel" || tag == "gaza") HostagesCard(onOpen)
                    val agenda = remember { com.abugdn.wid.data.upcomingAgenda().filter { it.tag == tag }.take(3) }
                    if (agenda.isNotEmpty()) {
                        ArgosCard("📅 NA AGENDA", info = "Datas que costumam mexer com esta região. A agenda completa fica em Radar → Contexto.") {
                            agenda.forEach { e ->
                                Text(
                                    "${e.date.dayOfMonth}/${e.date.monthValue} · ${e.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    val bets = radar?.predictions?.events.orEmpty().filter { tag in it.tags }.take(3)
                    if (bets.isNotEmpty()) {
                        ArgosCard("🎲 O QUE OS APOSTADORES ACHAM", source = "Polymarket", info = "Apostas com dinheiro real; não são previsões oficiais e podem ser manipuladas.") {
                            bets.forEach { e ->
                                val m = e.markets.firstOrNull()
                                Text(
                                    repo.translator.cached(e.title) + (m?.let { " · ${(it.prob * 100).toInt()}%" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable { openUrl(context, e.url) }.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    val voices = listOfNotNull(radar?.official, radar?.analysis, radar?.factcheck)
                        .flatMap { it.items }.filter { tag in it.tags }.sortedByDescending { it.published }.take(4)
                    if (voices.isNotEmpty()) {
                        ArgosCard("🏛 VOZES E ANÁLISES", info = "Publicações de governos, institutos de análise e agências de checagem que citam esta região. Mais na aba Radar.") {
                            voices.forEach { v ->
                                Column(Modifier.fillMaxWidth().clickable { openUrl(context, v.url) }.padding(top = 6.dp)) {
                                    Text("${v.source} · ${relativeTime(v.published)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(repo.translator.display(v.title, v.lang), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
            REGION_CONTEXT[tag]?.let { text ->
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            CONTEXT_DISCLAIMER,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            if (trend.any { it > 0 }) {
                item {
                    TrendRow("Histórias por dia (14 dias)", trend, highlight = true, onClick = null)
                    HorizontalDivider()
                }
            }
            item { Header("Agora · últimas 48 h · ${current.size}") }
            if (current.isEmpty()) {
                item { Text("Nenhuma história desta região no momento.", modifier = Modifier.padding(16.dp)) }
            }
            items(current, key = { "now-" + it.id }) { c ->
                ClusterRow(c, onOpen)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (pastTops.isNotEmpty()) {
                item { Header("Principais dos últimos 30 dias") }
                items(pastTops, key = { "day-" + it.date }) { day ->
                    Text(
                        dayLabel(day.date).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                    ClusterRow(day.top, onOpen)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            MILESTONES[tag]?.let { list ->
                item { Header("Marcos") }
                items(list, key = { "m-" + it.date + it.text.hashCode() }) { m ->
                    Row(Modifier.padding(16.dp, 6.dp)) {
                        Text(m.date, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Accent,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
