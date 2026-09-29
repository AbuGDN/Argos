package com.abugdn.wid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.AIRSPACE_STATUS
import com.abugdn.wid.data.Actor
import com.abugdn.wid.data.CAPITALS
import com.abugdn.wid.data.CONTEXT_DISCLAIMER
import com.abugdn.wid.data.CrisisWatchSection
import com.abugdn.wid.data.CwCountry
import com.abugdn.wid.data.FeedSection
import com.abugdn.wid.data.HOSTAGES_DISCLAIMER
import com.abugdn.wid.data.HOSTAGES_TAKEN
import com.abugdn.wid.data.HOSTAGE_TERMS
import com.abugdn.wid.data.HOSTAGE_TIMELINE
import com.abugdn.wid.data.INTERNET_STATUS
import com.abugdn.wid.data.MILITARY_ICONS
import com.abugdn.wid.data.PEOPLE
import com.abugdn.wid.data.POWER
import com.abugdn.wid.data.POWER_DISCLAIMER
import com.abugdn.wid.data.PredictionEvent
import com.abugdn.wid.data.Quote
import com.abugdn.wid.data.RadarData
import com.abugdn.wid.data.RadarItem
import com.abugdn.wid.data.SectionStatus
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.onThisDay
import com.abugdn.wid.data.wikiTitleForName
import com.abugdn.wid.data.sunTimes
import com.abugdn.wid.data.upcomingAgenda
import com.abugdn.wid.data.normalize
import com.abugdn.wid.repository
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ptBR = Locale("pt", "BR")
private val intFmt: NumberFormat = NumberFormat.getIntegerInstance(ptBR)
private fun fmt(n: Number) = intFmt.format(n)

/** Categorias da aba Radar. */
private val RADAR_TABS = listOf("Sensores", "Mercados", "Números", "Vozes", "Análise", "Contexto")

private val Moss = Color(0xFF6E8B6A)
private val Copper = Color(0xFFC8662B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(onOpen: (String) -> Unit, onRegion: (String) -> Unit, tab: Int = 0, onTab: (Int) -> Unit = {}) {
    val repo = LocalContext.current.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    // Retrato da última visita, lido uma vez ao abrir; ao sair da aba vira o retrato de agora.
    val seen = remember { repo.radarSeen() }
    var changesDismissed by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { repo.markRadarSeen() } }
    val changes = remember(radar) { radar?.let { com.abugdn.wid.data.radarChanges(it, seen.first) }.orEmpty() }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadarSweepIcon(alert = radar?.let { radarAlerts(it).isNotEmpty() } == true, modifier = Modifier.padding(end = 10.dp))
                        Text("Radar", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            loading = true
                            scope.launch { repo.refresh(); loading = false }
                        }) { Icon(Icons.Filled.Refresh, contentDescription = "Atualizar") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (changes.isNotEmpty() && !changesDismissed) {
                RadarChangesCard(changes, seen.second) { changesDismissed = true; repo.markRadarSeen() }
            }
            val sensorsAlert = radar?.let { radarAlerts(it).isNotEmpty() } == true
            val analysisAlert = radar?.crisiswatch?.let { it.deteriorated.isNotEmpty() || it.risk.isNotEmpty() } == true
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                RADAR_TABS.forEachIndexed { i, label ->
                    // Bolinha vermelha na categoria que tem alerta agora.
                    val dot = (i == 0 && sensorsAlert) || (i == 4 && analysisAlert)
                    Tab(selected = tab == i, onClick = { onTab(i) }, text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label)
                            if (dot) Text(" ●", color = Alert, style = MaterialTheme.typography.labelSmall)
                        }
                    })
                }
            }
            val data = radar
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
                if (data == null && tab < 5) {
                    item {
                        Text(
                            "O Radar ainda não foi baixado. Puxe o feed na tela Hoje ou toque em atualizar; " +
                                "os dados chegam junto com as notícias.",
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                when (tab) {
                    0 -> data?.let { sensors(it, onRegion) }
                    1 -> data?.let { markets(it) }
                    2 -> numbers(data, onOpen, onRegion)
                    3 -> data?.let { voices(it, onRegion) }
                    4 -> data?.let { analysis(it, onOpen, onRegion) }
                    else -> contextTab(data, onRegion)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Blocos comuns
// ---------------------------------------------------------------------------

/** "O que mudou desde a última vez" que a pessoa abriu o Radar. */
@Composable
private fun RadarChangesCard(changes: List<com.abugdn.wid.data.RadarChange>, since: Long, onDismiss: () -> Unit) {
    val shown = changes.take(6)
    Column(Modifier.padding(horizontal = 16.dp)) {
        ArgosCard(
            "🆕 O QUE MUDOU DESDE A ÚLTIMA VISITA",
            alert = changes.any { it.alert },
            source = if (since > 0) "última visita ${relativeTime(Instant.ofEpochMilli(since).toString())}" else null,
            info = "Compara o Radar de agora com o da última vez que você abriu esta aba: status de internet e espaço aéreo, " +
                "alertas de focos de calor, navios e aviões militares, porta-aviões, linha de frente e publicações novas.",
            collapsedSummary = "${changes.size} mudança(s)",
        ) {
            shown.forEach { c ->
                Text(
                    c.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (c.alert) Alert else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (c.alert) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (changes.size > shown.size) Note("e mais ${changes.size - shown.size}")
            TextButton(onClick = onDismiss) { Text("Visto") }
        }
    }
}

/** Título da seção com "atualizado há…" e aviso se a última coleta falhou. */
@Composable
private fun SectionTitle(title: String, updated: String? = null, status: SectionStatus? = null) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold)
    val parts = buildList {
        if (!updated.isNullOrBlank()) add("atualizado ${relativeTime(updated)}")
        if (status != null && !status.ok) add("⚠ a última coleta falhou; mostrando o dado anterior")
    }
    if (parts.isNotEmpty()) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = if (status != null && !status.ok) Alert else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Unavailable(what: String, status: SectionStatus?) {
    ArgosCard(what.uppercase(), titleColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(
            if (status == null) "Ainda não coletado. Aparece nas próximas rodadas do servidor."
            else "Fonte indisponível no momento; o servidor tenta de novo em até 1 hora.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Linha fina com os valores (nulos viram buraco). [baseline] desenha uma linha pontilhada de referência. */
@Composable
private fun Sparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier, baseline: Double? = null) {
    val present = values.filterNotNull()
    if (present.size < 2) return
    val min = minOf(present.min(), baseline ?: present.min())
    val max = maxOf(present.max(), baseline ?: present.max())
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    // A linha se traça da esquerda para a direita ao aparecer.
    val grow = rememberGrow(durationMs = 800)
    Canvas(modifier.height(28.dp)) {
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Double) = (size.height - (v - min) / range * size.height).toFloat()
        baseline?.let {
            drawLine(Ash.copy(alpha = 0.5f), Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1.dp.toPx())
        }
        val path = Path()
        var open = false
        val visible = (values.size * grow).toInt().coerceAtLeast(2)
        values.forEachIndexed { i, v ->
            if (i >= visible) return@forEachIndexed
            if (v == null) {
                open = false
                return@forEachIndexed
            }
            if (open) path.lineTo(i * step, y(v)) else path.moveTo(i * step, y(v))
            open = true
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

private fun regionLabel(tag: String) = TAG_LABELS[tag] ?: tag

/** Notícia de fonte externa (oficial, sanção, análise, checagem): abre no navegador. */
@Composable
private fun ItemRow(item: RadarItem, extra: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val tr = context.repository.translator
    Column(
        Modifier.fillMaxWidth().clickable { openUrl(context, item.url) }.padding(vertical = 10.dp),
    ) {
        Text(
            buildString {
                append(item.source)
                if (item.published.isNotBlank()) append(" · ").append(relativeTime(item.published))
                if (item.tags.isNotEmpty()) append(" · ").append(item.tags.joinToString(", ") { regionLabel(it) })
            },
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            fontWeight = FontWeight.Bold,
        )
        Text(tr.display(item.title, item.lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (item.summary.isNotBlank()) {
            Text(
                tr.display(item.summary, item.lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        extra?.invoke()
    }
    HorizontalDivider()
}

private fun LazyListScope.feedSection(title: String, section: FeedSection?, status: SectionStatus?, note: String, extra: @Composable (RadarItem) -> Unit = {}) {
    item {
        if (section == null) {
            Unavailable(title, status)
        } else {
            ArgosCard(title.uppercase(), updated = section.updated, status = status, info = note) {
                val down = section.sources.filterValues { !it }.keys
                Text(
                    if (section.items.isEmpty()) "Nada recente." else "${section.items.size} publicações recentes · toque para abrir no site",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (down.isNotEmpty()) Note("Fora do ar nesta rodada: ${down.joinToString(", ")}")
            }
        }
    }
    section?.items?.let { list ->
        items(list.size) { i -> ItemRow(list[i]) { extra(list[i]) } }
    }
}

// ---------------------------------------------------------------------------
// Sensores
// ---------------------------------------------------------------------------

/** Sinais de alerta ativos agora (usados no topo dos sensores). */
fun radarAlerts(radar: RadarData): List<String> = buildList {
    radar.internet?.countries.orEmpty().filter { it.status == "apagao" || it.status == "queda" }.forEach {
        add("🌐 ${if (it.status == "apagao") "Apagão" else "Queda"} de internet: ${it.name} (${(it.ratio * 100).toInt()}% do normal)")
    }
    radar.airspace?.zones.orEmpty().filter { it.status == "fechado" || it.status == "reduzido" }.forEach {
        add("✈ Espaço aéreo ${it.status}: ${it.name}")
    }
    radar.fires?.zones.orEmpty().filter { z -> z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline }.forEach {
        add("🔥 Focos de calor acima do normal: ${it.name} (${it.count} em 24 h)")
    }
    radar.straits?.items.orEmpty().filter { s -> s.avg7 != null && s.avg90 != null && s.avg90 > 5 && s.avg7 < s.avg90 * 0.6 }.forEach {
        add("🚢 Tráfego em queda: ${it.name}")
    }
    radar.incidents.forEach { inc ->
        add("🧩 Sinais coincidentes em ${TAG_LABELS[inc.tag] ?: inc.tag}: " + inc.signals.joinToString(" + ") { it.kind.let { k -> INCIDENT_KINDS[k] ?: k } })
    }
    if (sirensRecent(radar)) radar.sirens?.let { add("🚨 Sirenes em Israel: ${it.count24h} locais em 24 h") }
    radar.quakes?.items.orEmpty().filter { it.alert }.forEach { q ->
        add("🌋 Sismo suspeito: M ${q.mag ?: "?"} em ${q.zone}" + (q.site?.let { " (perto de ${it.name})" } ?: ""))
    }
    radar.military?.zones.orEmpty().filter { it.unusual }.forEach {
        add("✈ Aviões militares acima do normal: ${it.name} (${it.key} reabastecedores/radar/espionagem)")
    }
}

private fun internetColor(status: String) = when (status) {
    "apagao" -> Alert
    "queda" -> Copper
    "normal" -> Moss
    else -> Ash
}

private fun airColor(status: String) = when (status) {
    "fechado" -> Alert
    "reduzido" -> Copper
    "normal" -> Moss
    else -> Ash
}

private fun LazyListScope.sensors(radar: RadarData, onRegion: (String) -> Unit) {
    if (radar.incidents.isNotEmpty()) item { IncidentsCard(radar, onRegion) }
    item {
        val alerts = radarAlerts(radar)
        ArgosCard(
            if (alerts.isEmpty()) "👁 NENHUM SINAL DE ALERTA AGORA" else "👁 SINAIS DE ALERTA AGORA",
            alert = alerts.isNotEmpty(),
            updated = radar.generatedAt,
            info = "Dados de sensores, que costumam mostrar um ataque antes da primeira manchete. " +
                "Apagões e espaço aéreo fechado também somam na tensão da região. " +
                "Os cartões com alerta sobem para o topo; os calmos ficam recolhidos (toque para abrir).",
        ) {
            alerts.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Alert, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            if (alerts.isEmpty()) {
                Text("Internet, espaço aéreo, focos de calor, navios e aviões militares dentro do normal.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    // Quem está em alerta sobe; o resto vem recolhido, na ordem de sempre.
    val order = listOf(
        "internet" to radar.internet?.countries.orEmpty().any { it.status == "apagao" || it.status == "queda" },
        "airspace" to radar.airspace?.zones.orEmpty().any { it.status == "fechado" || it.status == "reduzido" },
        "fires" to radar.fires?.zones.orEmpty().any { z -> z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline },
        "straits" to radar.straits?.items.orEmpty().any { s -> s.avg7 != null && s.avg90 != null && s.avg90 > 5 && s.avg7 < s.avg90 * 0.6 },
        "military" to radar.military?.zones.orEmpty().any { it.unusual },
        "carriers" to (radar.carriers?.ships.orEmpty().count { it.lat in 10.0..40.0 && it.lon in 25.0..65.0 } >= 2),
        "frontline" to ((radar.frontline?.change7dKm2 ?: 0L) > 50L),
        "sirens" to sirensRecent(radar),
        "quakes" to radar.quakes?.items.orEmpty().any { it.alert },
    ).sortedByDescending { it.second }
    order.forEach { (key, _) ->
        when (key) {
            "internet" -> internetItem(radar, onRegion)
            "airspace" -> airspaceItem(radar, onRegion)
            "fires" -> firesItem(radar, onRegion)
            "straits" -> straitsItem(radar, onRegion)
            "military" -> militaryItem(radar)
            "carriers" -> carriersItem(radar)
            "sirens" -> sirensItem(radar)
            "quakes" -> quakesItem(radar, onRegion)
            else -> frontlineItem(radar)
        }
    }
    item { WeatherCard(radar, onRegion) }
}

private fun LazyListScope.internetItem(radar: RadarData, onRegion: (String) -> Unit) {
    // Internet
    item {
        val section = radar.internet
        val status = radar.status["internet"]
        if (section == null) {
            Unavailable("🌐 Internet", status)
            return@item
        }
        val bad = section.countries.filter { it.status == "apagao" || it.status == "queda" }
        ArgosCard(
            "🌐 INTERNET",
            alert = bad.isNotEmpty(),
            source = "IODA",
            updated = section.updated,
            status = status,
            info = "Fonte: IODA (Georgia Tech). Compara a conectividade da última hora com as 24 h anteriores. Abaixo de 85% é queda; abaixo de 50%, apagão. Linha cinza = normal.",
            collapsedSummary = if (bad.isEmpty()) "${section.countries.size} países · todos normais" else bad.joinToString(" · ") { "${it.name}: ${INTERNET_STATUS[it.status] ?: it.status}" },
            startExpanded = bad.isNotEmpty(),
        ) {
            section.countries.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = c.tag.isNotBlank()) { onRegion(c.tag) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (INTERNET_STATUS[c.status] ?: c.status) +
                                (if (c.status != "sem_dados") " · ${(c.ratio * 100).toInt()}% do normal" else "") +
                                (if ((c.status == "apagao" || c.status == "queda") && c.since.isNotBlank()) " · desde ${dayClock(c.since)}" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = internetColor(c.status),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Sparkline(c.spark, internetColor(c.status), Modifier.width(96.dp), baseline = 1.0)
                }
            }
        }
    }
}

private fun LazyListScope.airspaceItem(radar: RadarData, onRegion: (String) -> Unit) {
    // Espaço aéreo
    item {
        val section = radar.airspace
        val status = radar.status["airspace"]
        if (section == null) {
            Unavailable("✈ Espaço aéreo", status)
            return@item
        }
        val shut = section.zones.filter { it.status == "fechado" || it.status == "reduzido" }
        ArgosCard(
            "✈ ESPAÇO AÉREO",
            alert = shut.isNotEmpty(),
            source = "OpenSky",
            updated = section.updated,
            status = status,
            info = "Fonte: OpenSky Network. Conta os aviões no ar sobre cada país e compara com o mesmo horário nos últimos 14 dias (nos primeiros 3 dias, ainda está aprendendo o normal). Menos de 25% do normal = fechado.",
            collapsedSummary = if (shut.isEmpty()) "${section.zones.size} zonas · nenhuma fechada" else shut.joinToString(" · ") { "${it.name}: ${it.status}" },
            startExpanded = shut.isNotEmpty(),
        ) {
            section.zones.forEach { z ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = z.tag.isNotBlank()) { onRegion(z.tag) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(z.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            AIRSPACE_STATUS[z.status] ?: z.status,
                            style = MaterialTheme.typography.labelMedium,
                            color = airColor(z.status),
                            fontWeight = FontWeight.Bold,
                        )
                        if (z.status != "sem_dados") {
                            Text(
                                "${z.flights} no ar" + (z.baseline?.let { " · normal ${it.toInt()}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.firesItem(radar: RadarData, onRegion: (String) -> Unit) {
    // Focos de calor
    item {
        val section = radar.fires
        val status = radar.status["fires"]
        if (section == null) {
            Unavailable("🔥 Focos de calor", status)
            return@item
        }
        val hot = section.zones.filter { z -> z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline }
        ArgosCard(
            "🔥 FOCOS DE CALOR · 24 H",
            alert = hot.isNotEmpty(),
            source = "NASA FIRMS",
            updated = section.updated,
            status = status,
            info = "Fonte: NASA FIRMS (satélites VIIRS). Mostra calor intenso: explosões e incêndios, mas também queimadas agrícolas, fábricas e chamas de gás. Um salto repentino numa área de combate é o que interessa.",
            collapsedSummary = when {
                section.missingKey -> "falta a chave da NASA"
                hot.isEmpty() -> "${fmt(section.zones.sumOf { it.count })} focos · dentro do normal"
                else -> hot.joinToString(" · ") { "${it.name}: ${fmt(it.count)} (acima do normal)" }
            },
            startExpanded = hot.isNotEmpty() || section.missingKey,
        ) {
            if (section.missingKey) {
                Text(
                    "Falta a chave grátis da NASA (FIRMS_MAP_KEY) nos Secrets do repositório. " +
                        "Crie em firms.modaps.eosdis.nasa.gov/api/map_key e adicione em Settings → Secrets and variables → Actions.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                return@ArgosCard
            }
            val openMap = LocalOpenMap.current
            section.zones.forEach { z ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(enabled = z.tag.isNotBlank()) { onRegion(z.tag) }) {
                        Text(z.name, style = MaterialTheme.typography.bodyMedium)
                        val high = z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline
                        Text(
                            if (z.error) "sem dados nesta rodada"
                            else "${fmt(z.count)} focos" + (z.baseline?.let { " · média ${it.toInt()}/dia" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (high) Alert else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (high) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    if (z.points.isNotEmpty()) TextButton(onClick = { openMap("fires:${z.id}") }) { Text("Mapa") }
                }
            }
        }
    }
}

private fun LazyListScope.straitsItem(radar: RadarData, onRegion: (String) -> Unit) {
    // Estreitos
    item {
        val section = radar.straits
        val status = radar.status["straits"]
        if (section == null) {
            Unavailable("🚢 Estreitos", status)
            return@item
        }
        val low = section.items.filter { s -> s.avg7 != null && s.avg90 != null && s.avg90 > 5 && s.avg7 < s.avg90 * 0.6 }
        ArgosCard(
            "🚢 NAVIOS NOS ESTREITOS",
            alert = low.isNotEmpty(),
            source = "IMF PortWatch",
            updated = section.updated,
            status = status,
            info = "Fonte: IMF PortWatch (FMI), com dados de satélite dos navios (AIS). Média de passagens por dia na última semana; o FMI atualiza uma vez por semana, com alguns dias de atraso.",
            collapsedSummary = if (low.isEmpty()) section.items.filter { it.avg7 != null }.joinToString(" · ") { "${it.name} ${it.avg7!!.toInt()}/dia" }
            else low.joinToString(" · ") { "${it.name}: tráfego em queda" },
            startExpanded = low.isNotEmpty(),
        ) {
            section.items.forEach { s ->
                Column(Modifier.fillMaxWidth().clickable(enabled = s.tag.isNotBlank()) { onRegion(s.tag) }.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (s.avg7 != null) {
                            Text("${s.avg7.toInt()} por dia", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (s.error) {
                        Text("sem dados nesta rodada", style = MaterialTheme.typography.labelSmall, color = Ash)
                    } else {
                        val parts = buildList {
                            s.avg90?.let { add("90 dias antes: ${it.toInt()}${pct(s.avg7, it)}") }
                            s.yearAgo?.let { add("há um ano: ${it.toInt()}${pct(s.avg7, it)}") }
                            if (s.date.isNotBlank()) add("dados até ${dayLabel(s.date)}")
                        }
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Sparkline(s.spark, Accent, Modifier.fillMaxWidth().padding(top = 4.dp), baseline = s.avg90)
                    }
                }
            }
        }
    }
}


private fun pct(now: Double?, before: Double): String {
    if (now == null || before <= 0) return ""
    val p = ((now - before) / before * 100).toInt()
    return " (${if (p >= 0) "+" else ""}$p%)"
}

// ---------------------------------------------------------------------------
// Mercados
// ---------------------------------------------------------------------------

private fun LazyListScope.markets(radar: RadarData) {
    item {
        val section = radar.markets
        val status = radar.status["markets"]
        if (section == null) {
            Unavailable("🛢 Cotações", status)
            return@item
        }
        ArgosCard(
            "🛢 TERMÔMETRO DO MERCADO",
            source = "EIA, Stooq, Yahoo e câmbio aberto",
            updated = section.updated,
            status = status,
            info = "Fechamento do dia anterior ou cotação com atraso. Petróleo e ouro costumam subir com medo de guerra; shekel, rublo e hryvnia mostram como o mercado vê Israel, Rússia e Ucrânia.",
        ) {
            section.items.forEach { QuoteRow(it) }
        }
    }
    item {
        val section = radar.predictions
        val status = radar.status["predictions"]
        if (section == null) {
            Unavailable("🎲 Mercados de previsão", status)
            return@item
        }
        ArgosCard(
            "🎲 O QUE OS APOSTADORES ACHAM",
            source = "Polymarket",
            updated = section.updated,
            status = status,
            info = "Probabilidades do Polymarket: apostas com dinheiro real sobre as guerras. Não são previsões oficiais nem análises; mudam com boatos e podem ser manipuladas.",
        ) {
            Text("${section.events.size} apostas abertas sobre os conflitos acompanhados.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
    radar.predictions?.events?.let { events ->
        items(events.size) { i -> PredictionCard(events[i]) }
    }
}

@Composable
private fun QuoteRow(q: Quote) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(q.name, style = MaterialTheme.typography.bodyMedium)
            val change = q.changePct
            val price = q.price
            if (q.error || price == null) {
                Text("sem dados nesta rodada", style = MaterialTheme.typography.labelSmall, color = Ash)
            } else {
                Text(
                    buildString {
                        append(String.format(ptBR, "%,.${q.digits}f", price))
                        if (q.unit.isNotBlank()) append(" ").append(q.unit)
                        if (change != null) append(String.format(ptBR, "  %s %.2f%% hoje", if (change >= 0) "▲" else "▼", kotlin.math.abs(change)))
                        q.changeWeekPct?.let { append(String.format(ptBR, " · %+.1f%% em 7 dias", it)) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if ((change ?: 0.0) >= 0) Accent else Ash,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Sparkline(q.spark, if ((q.changePct ?: 0.0) >= 0) Accent else Ash, Modifier.width(96.dp))
    }
}

@Composable
private fun PredictionCard(e: PredictionEvent) {
    val context = LocalContext.current
    val tr = context.repository.translator
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { openUrl(context, e.url) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(tr.cached(e.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            e.markets.forEach { m ->
                val label = if (m.label == e.title) "Sim" else tr.cached(m.label)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${(m.prob * 100).toInt()}%" + (m.change?.takeIf { kotlin.math.abs(it) >= 0.01 }?.let {
                            String.format(ptBR, " (%+d em 24 h)", (it * 100).toInt())
                        } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { m.prob.toFloat().coerceIn(0f, 1f) },
                    color = Accent,
                    trackColor = Accent.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(4.dp),
                )
            }
            Text(
                buildString {
                    append("Volume US$ ").append(fmt(e.volume.toLong()))
                    if (e.end.isNotBlank()) append(" · encerra ").append(dayLabel(e.end))
                    if (e.tags.isNotEmpty()) append(" · ").append(e.tags.joinToString(", ") { regionLabel(it) })
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Números
// ---------------------------------------------------------------------------

private fun LazyListScope.numbers(radar: RadarData?, onOpen: (String) -> Unit, onRegion: (String) -> Unit) {
    item { AirwarCard(onOpen) }
    val human = radar?.humanitarian
    item {
        if (human == null) {
            if (radar != null) Unavailable("🩸 Painel humanitário", radar.status["humanitarian"])
            return@item
        }
        if (human.palestine.isNotEmpty()) {
            ArgosCard(
                "🩸 GAZA E CISJORDÂNIA",
                source = "Tech for Palestine",
                updated = human.updated,
                status = radar?.status?.get("humanitarian"),
                info = "Números do Ministério da Saúde de Gaza (ligado ao Hamas) e da ONU (OCHA), compilados pelo projeto Tech for Palestine. Não separam civis de combatentes; Israel contesta parte deles.",
            ) {
                human.palestine.groupBy { it.region }.forEach { (region, figures) ->
                    Text(
                        regionLabel(region) + (figures.firstOrNull()?.date?.takeIf { it.isNotBlank() }?.let { " · até ${dayLabel(it)}" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp).clickable { onRegion(region) },
                    )
                    figures.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(f.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(fmt(f.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (human.idps.isNotEmpty()) {
            ArgosCard(
                "🏚 DESLOCADOS DENTRO DO PRÓPRIO PAÍS",
                source = "ONU (HDX HAPI)",
                updated = human.updated,
                info = "Fonte: ONU (HDX HAPI, com dados da OIM e do ACNUR). Levantamentos periódicos; a data de cada país aparece embaixo do nome.",
            ) {
                human.idps.sortedByDescending { it.value }.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = d.tag.isNotBlank()) { onRegion(d.tag) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.bodyMedium)
                            if (d.date.isNotBlank()) {
                                Text("dado de ${dayLabel(d.date)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(fmt(d.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    item { HostagesCard(onOpen) }
    item {
        val losses = radar?.losses
        if (losses == null) {
            if (radar != null) Unavailable("🇺🇦 Perdas declaradas", radar.status["losses"])
            return@item
        }
        ArgosCard(
            "🇺🇦 PERDAS RUSSAS SEGUNDO A UCRÂNIA" + (losses.day?.let { " · DIA ${fmt(it)}" } ?: ""),
            source = "Estado-Maior da Ucrânia" + if (losses.date.isNotBlank()) " · ${dayLabel(losses.date)}" else "",
            updated = losses.updated,
            status = radar?.status?.get("losses"),
            info = "Números do Estado-Maior da Ucrânia, divulgados todo dia. É a versão de um dos lados, sem verificação independente; a Rússia não divulga as próprias perdas.",
        ) {
            losses.items.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(l.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(fmt(l.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (l.increase > 0) " +${fmt(l.increase)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Alert,
                        modifier = Modifier.width(64.dp).padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun HostagesCard(onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val feed by repo.feed.collectAsStateWithLifecycle()
    val terms = remember { HOSTAGE_TERMS.map(::normalize) }
    val news = remember(feed) {
        feed?.clusters.orEmpty().filter { c ->
            val text = normalize(repo.translator.display(c.title, c.lang) + " " + c.title)
            terms.any { it in text }
        }.take(5)
    }
    ArgosCard("🎗 REFÉNS DO 7 DE OUTUBRO", info = HOSTAGES_DISCLAIMER) {
        Text("$HOSTAGES_TAKEN levados para Gaza", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        HOSTAGE_TIMELINE.forEach { step ->
            Row(Modifier.padding(top = 6.dp)) {
                Text(step.date, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                Text(step.text, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (news.isNotEmpty()) {
            Text("NAS NOTÍCIAS AGORA", style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            news.forEach { c ->
                Text(
                    "• " + repo.translator.display(c.title, c.lang),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { onOpen(c.id) }.padding(vertical = 3.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vozes
// ---------------------------------------------------------------------------

private fun LazyListScope.voices(radar: RadarData, onRegion: (String) -> Unit) {
    item { UnscCard(radar, onRegion) }
    feedSection(
        "🏛 Fontes oficiais", radar.official, radar.status["official"],
        "O que governos, Forças Armadas e a ONU dizem nos próprios canais, sem intermediário. É a versão de quem publica: compare com a imprensa.",
    )
    feedSection(
        "⛔ Sanções", radar.sanctions, radar.status["sanctions"],
        "Anúncios de sanções dos EUA (Tesouro/OFAC) e da União Europeia.",
    )
}

// ---------------------------------------------------------------------------
// Análise
// ---------------------------------------------------------------------------

private fun LazyListScope.analysis(radar: RadarData, onOpen: (String) -> Unit, onRegion: (String) -> Unit) {
    item {
        val cw = radar.crisiswatch
        if (cw == null) {
            Unavailable("📉 CrisisWatch", radar.status["crisiswatch"])
        } else {
            CrisisWatchCard(cw, radar.status["crisiswatch"], onRegion)
        }
    }
    feedSection(
        "✅ Checagens", radar.factcheck, radar.status["factcheck"],
        "Desmentidos e checagens sobre as guerras (Aos Fatos, Lupa, AFP, Misbar, Snopes, Full Fact). Quando uma checagem fala de uma história do feed, ela aparece também na notícia.",
    ) { item -> FactcheckLinks(item, onOpen) }
    feedSection(
        "🧠 Análises", radar.analysis, radar.status["analysis"],
        "Relatórios de institutos de análise: ISW e Critical Threats (americanos, diários, sobre Ucrânia e Irã), Crisis Group e War on the Rocks. Têm linha própria; leia como análise, não como notícia.",
    )
}

@Composable
private fun FactcheckLinks(item: RadarItem, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    item.clusters.mapNotNull(repo::cluster).take(2).forEach { c ->
        Text(
            "⚠ Sobre: " + repo.translator.display(c.title, c.lang),
            style = MaterialTheme.typography.labelSmall,
            color = Alert,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onOpen(c.id) }.padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrisisWatchCard(cw: CrisisWatchSection, status: SectionStatus?, onRegion: (String) -> Unit) {
    val context = LocalContext.current
    ArgosCard(
        "📉 CRISISWATCH" + if (cw.month.isNotBlank()) " · ${cw.month.uppercase()}" else "",
        alert = cw.deteriorated.isNotEmpty() || cw.risk.isNotEmpty(),
        source = "International Crisis Group",
        updated = cw.updated,
        status = status,
        info = "Avaliação mensal do International Crisis Group sobre ~70 conflitos. Leitura automática da página; confira no site.",
    ) {
        @Composable
        fun group(label: String, list: List<CwCountry>, color: Color) {
            if (list.isEmpty()) return
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { c ->
                    Text(
                        TAG_LABELS[c.tag]?.takeIf { c.name != "Russia" && c.name != "Red Sea" && c.name != "West Bank" } ?: c.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(enabled = c.tag.isNotBlank()) { onRegion(c.tag) }.padding(vertical = 2.dp),
                    )
                }
            }
        }
        group("▼ Pioraram", cw.deteriorated, Alert)
        group("▲ Melhoraram", cw.improved, Moss)
        group("⚠ Risco de conflito", cw.risk, Copper)
        group("🕊 Chance de solução", cw.resolution, Accent)
        if (listOf(cw.deteriorated, cw.improved, cw.risk, cw.resolution).all { it.isEmpty() }) {
            if (cw.summary.isNotBlank()) {
                val tr = context.repository.translator
                if (cw.title.isNotBlank()) Text(tr.cached(cw.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Text(tr.cached(cw.summary), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text("Nenhum dos conflitos acompanhados pelo Argos mudou de tendência neste mês.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (cw.url.isNotBlank()) TextButton(onClick = { openUrl(context, cw.url) }) { Text("Abrir o CrisisWatch") }
    }
}

// ---------------------------------------------------------------------------
// Contexto (fixo, sem servidor)
// ---------------------------------------------------------------------------

private val agendaDay = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", ptBR)

private fun LazyListScope.contextTab(radar: RadarData?, onRegion: (String) -> Unit) {
    val travel = radar?.travel
    if (radar != null && travel != null) item { TravelCard(travel, radar, onRegion) }
    item {
        val today = LocalDate.now()
        val past = onThisDay(today)
        if (past.isNotEmpty()) {
            ArgosCard("📜 NESTE DIA") {
                past.forEach { (tag, m) ->
                    Text(
                        "${m.date} · ${regionLabel(tag)}: ${m.text}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onRegion(tag) }.padding(top = 6.dp),
                    )
                }
            }
        }
        ArgosCard(
            "📅 AGENDA · PRÓXIMOS 4 MESES",
            info = "Datas que costumam mexer com as guerras: aniversários de ataques, feriados religiosos, eleições. Feriados islâmicos dependem da lua e podem variar um dia.",
        ) {
            upcomingAgenda(today).forEach { e ->
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = e.tag != null) { e.tag?.let(onRegion) }.padding(vertical = 6.dp),
                ) {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, e.date)
                    Text(
                        agendaDay.format(e.date) + when (days) {
                            0L -> " · hoje"
                            1L -> " · amanhã"
                            else -> " · em $days dias"
                        } + (if (e.approx) " · data aproximada" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (days <= 7) Alert else Accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(e.text + (e.tag?.let { " (${regionLabel(it)})" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    item { CapitalsCard() }
    item { PowerCards() }
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun CapitalsCard() {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    ArgosCard("🕰 HORA NAS CAPITAIS", info = "Muitos ataques aéreos e com drones acontecem de madrugada no horário local.") {
        CAPITALS.forEach { c ->
            val zone = runCatching { ZoneId.of(c.zone) }.getOrDefault(ZoneId.of("UTC"))
            val local = now.atZone(zone)
            val sun = sunTimes(local.toLocalDate(), c.lat, c.lon)
            val day = sun != null && now.isAfter(sun.first) && now.isBefore(sun.second)
            val next = when {
                sun == null -> ""
                now.isBefore(sun.first) -> "nascer do sol ${hhmm.format(sun.first.atZone(zone))}"
                now.isBefore(sun.second) -> "pôr do sol ${hhmm.format(sun.second.atZone(zone))}"
                else -> "noite"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (day) "☀" else "☾", color = if (day) Accent else Ash, modifier = Modifier.width(24.dp))
                Text(c.city, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(next, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                Text(hhmm.format(local), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PowerCards(only: String? = null) {
    var person by remember { mutableStateOf<Actor?>(null) }
    person?.let { ActorContextDialog(it, onOpen = null) { person = null } }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    ArgosCard("🧭 QUEM MANDA" + if (only == null) " EM CADA LADO" else "", info = "$POWER_DISCLAIMER $CONTEXT_DISCLAIMER") {
        POWER.filter { only == null || it.tag == only }.forEach { side ->
            val open = expanded == side.title || only != null
            Row(
                Modifier.fillMaxWidth().clickable { expanded = if (open) null else side.title }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (open) "▾ " else "▸ ") + side.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                RegionFlags(side.tag, height = 16.dp)
            }
            if (open) {
                side.roles.forEach { r ->
                    val actor = r.person?.let { key -> PEOPLE.firstOrNull { it.key == key } }
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = actor != null) { person = actor }.padding(start = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WikiImage(wikiTitleForName(r.name, r.person), person = true, size = 40.dp, modifier = Modifier.padding(end = 10.dp))
                        Column {
                            Text(r.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                r.name + if (actor != null) "  ⓘ" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (actor != null) Accent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}


// ---------------------------------------------------------------------------
// Olhos militares: aviões, porta-aviões e linha de frente
// ---------------------------------------------------------------------------

private fun LazyListScope.militaryItem(radar: RadarData) {
    item {
        val section = radar.military
        val status = radar.status["military"]
        if (section == null) {
            Unavailable("✈ Aviões militares", status)
            return@item
        }
        val openMap = LocalOpenMap.current
        val unusual = section.zones.filter { it.unusual }
        ArgosCard(
            "✈ AVIÕES MILITARES NO AR",
            alert = unusual.isNotEmpty(),
            source = "adsb.lol",
            updated = section.updated,
            status = status,
            info = "Fonte: adsb.lol (receptores de rádio voluntários). \"Que importam\" = reabastecedores, aviões-radar, espionagem/drones e bombardeiros: quando se juntam acima do normal, costuma vir operação. Muitos voam com transponder desligado; o número é um mínimo.",
            collapsedSummary = if (unusual.isEmpty()) section.zones.joinToString(" · ") { "${it.name}: ${it.count}" } + " · normal"
            else unusual.joinToString(" · ") { "${it.name}: acima do normal" },
            startExpanded = unusual.isNotEmpty(),
        ) {
            section.zones.forEach { z ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(z.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${z.count} no ar · ${z.key} que importam" + (z.baseline?.let { " · normal ${it.toInt()}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (z.unusual) Alert else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (z.unusual) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (z.counts.isNotEmpty()) {
                            Text(
                                z.counts.entries.sortedByDescending { it.value }.joinToString(" · ") { (k, v) ->
                                    "${MILITARY_ICONS[k] ?: "•"} ${section.labels[k] ?: k} $v"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (z.aircraft.isNotEmpty()) TextButton(onClick = { openMap("military:${z.id}") }) { Text("Mapa") }
                }
            }
        }
    }
}

private fun LazyListScope.carriersItem(radar: RadarData) {
    item {
        val section = radar.carriers
        val status = radar.status["carriers"]
        if (section == null) {
            Unavailable("⚓ Porta-aviões", status)
            return@item
        }
        val context = LocalContext.current
        val openMap = LocalOpenMap.current
        val nearby = section.ships.count { it.lat in 10.0..40.0 && it.lon in 25.0..65.0 }
        ArgosCard(
            "⚓ PORTA-AVIÕES DOS EUA",
            source = "USNI News",
            updated = section.updated,
            status = status,
            info = "Posição aproximada pelo texto do acompanhamento semanal da frota (USNI News): região, não coordenada exata. Pode estar alguns dias atrasada.",
            collapsedSummary = "$nearby no Oriente Médio e arredores · ${section.ships.size} no total",
            startExpanded = nearby >= 2,
        ) {
            Text(
                "$nearby no Oriente Médio e arredores",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (nearby >= 2) Alert else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
            section.ships.forEach { ship ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(ship.name.removePrefix("USS "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${ship.place} · ${ship.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                TextButton(onClick = { openMap("carriers") }) { Text("Ver no mapa") }
                if (section.url.isNotBlank()) TextButton(onClick = { openUrl(context, section.url) }) { Text("Relatório da semana") }
            }
        }
    }
}

private fun LazyListScope.frontlineItem(radar: RadarData) {
    item {
        val section = radar.frontline
        val status = radar.status["frontline"]
        if (section == null) {
            Unavailable("🗺 Linha de frente", status)
            return@item
        }
        val repo = LocalContext.current.repository
        val openMap = LocalOpenMap.current
        val weekChange = section.change7dKm2
        ArgosCard(
            "🗺 LINHA DE FRENTE NA UCRÂNIA",
            source = "DeepStateMap",
            updated = section.updated,
            status = status,
            info = "Fonte: DeepStateMap, mapa ucraniano independente atualizado todo dia a partir de fotos e vídeos verificados. A área é calculada pelo Argos a partir do desenho.",
            collapsedSummary = "Ocupado: ${formatKm2(section.occupiedKm2)}" + when {
                weekChange == null -> ""
                weekChange > 0 -> " · Rússia +${formatKm2(weekChange)} em 7 dias"
                weekChange < 0 -> " · Ucrânia retomou ${formatKm2(-weekChange)}"
                else -> " · sem mudança em 7 dias"
            },
            startExpanded = false,
        ) {
            CountUpText(
                section.occupiedKm2,
                MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
                format = { "Ocupado pela Rússia: ${formatKm2(it)}" },
            )
            section.change7dKm2?.let { d ->
                Text(
                    when {
                        d > 0 -> "▲ Rússia avançou ${formatKm2(d)} em 7 dias"
                        d < 0 -> "▼ Ucrânia retomou ${formatKm2(-d)} em 7 dias"
                        else -> "Sem mudança em 7 dias"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (d > 0) Alert else Moss,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (section.greyKm2 > 0) {
                Text("Zona cinzenta (incerta): ${formatKm2(section.greyKm2)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val history = section.history.mapNotNull { row -> row.getOrNull(1)?.toString()?.toDoubleOrNull() }
            Sparkline(history, Alert, Modifier.fillMaxWidth().padding(top = 6.dp))
            section.changes.take(3).forEach { ch ->
                Text(
                    "• " + repo.translator.cached(ch.text) + (if (ch.at.isNotBlank()) " (${dayClock(ch.at)})" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = { openMap("frontline") }) { Text("Ver a frente no mapa") }
        }
    }
}
