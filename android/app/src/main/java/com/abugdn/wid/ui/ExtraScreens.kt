package com.abugdn.wid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.ALLIANCE_EDGES
import com.abugdn.wid.data.ALLIANCE_NODES
import com.abugdn.wid.data.ALLIANCE_TYPES
import com.abugdn.wid.data.DIPLOMACY_HOSTILE
import com.abugdn.wid.data.DiplomacyRegion
import com.abugdn.wid.data.RadarData
import com.abugdn.wid.data.SourcesStatus
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.repository
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val ORANGE = Color(0xFFC8662B)
private val GREEN = Color(0xFF6E8B6A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun RegionChips(selected: String, onSelect: (String) -> Unit, only: Collection<String> = TAG_LABELS.keys) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(TAG_LABELS.keys.filter { it in only }) { t -> FilterChip(selected = t == selected, onClick = { onSelect(t) }, label = { Text(TAG_LABELS[t] ?: t) }) }
    }
}

// ---------------------------------------------------------------------------
// Comparar: duas regiões lado a lado, ou a mesma região em duas datas
// ---------------------------------------------------------------------------

@Composable
fun CompareScreen(onBack: () -> Unit, onRegion: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val feed by repo.feed.collectAsStateWithLifecycle()
    val stats by repo.stats.collectAsStateWithLifecycle()
    val archive by repo.archive.collectAsStateWithLifecycle()
    val radar by repo.radar.collectAsStateWithLifecycle()
    val diplomacy by repo.diplomacy.collectAsStateWithLifecycle()
    val vigil by repo.vigil.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (repo.stats.value == null) repo.loadStats()
        if (repo.archive.value == null) repo.loadArchive()
    }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var a by rememberSaveable { mutableStateOf("israel") }
    var b by rememberSaveable { mutableStateOf("ira") }
    var daysAgo by rememberSaveable { mutableIntStateOf(7) }
    val alertTags = com.abugdn.wid.data.radarAlertTags(radar)

    androidx.compose.material3.Scaffold(contentWindowInsets = NoInsets, topBar = { BackBar("Comparar", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                TabRow(selectedTabIndex = mode) {
                    Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("Duas regiões") })
                    Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("Duas datas") })
                }
            }
            if (mode == 0) {
                item {
                    Text("REGIÃO A", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    RegionChips(a, { a = it })
                    Text("REGIÃO B", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    RegionChips(b, { b = it })
                }
                item {
                    val sa = feed?.regions?.get(a)
                    val sb = feed?.regions?.get(b)
                    val days = stats?.days.orEmpty().takeLast(14)
                    val rows = listOf(
                        "Tensão agora" to (sa?.tension?.toString() ?: "–") to (sb?.tension?.toString() ?: "–"),
                        "Nível" to (sa?.level ?: "–") to (sb?.level ?: "–"),
                        "Histórias em 24 h" to "${sa?.last24 ?: 0}" to "${sb?.last24 ?: 0}",
                        "Normal por dia" to "${sa?.baseline ?: 0.0}" to "${sb?.baseline ?: 0.0}",
                        "Alta incomum" to (if (sa?.spike == true) "sim" else "não") to (if (sb?.spike == true) "sim" else "não"),
                        "Radar em alerta" to (if (a in alertTags) "sim" else "não") to (if (b in alertTags) "sim" else "não"),
                        "Histórias em 14 dias" to "${days.sumOf { it.counts[a] ?: 0 }}" to "${days.sumOf { it.counts[b] ?: 0 }}",
                        "Pico de tensão em 14 dias" to "${days.maxOfOrNull { it.tension[a] ?: 0 } ?: 0}" to "${days.maxOfOrNull { it.tension[b] ?: 0 } ?: 0}",
                        "Diplomacia (7 dias)" to (diplomacy?.regions?.get(a)?.label ?: "–") to (diplomacy?.regions?.get(b)?.label ?: "–"),
                        "Dia da guerra" to (com.abugdn.wid.data.CONFLICTS[a]?.day()?.toString() ?: "–") to (com.abugdn.wid.data.CONFLICTS[b]?.day()?.toString() ?: "–"),
                    )
                    ArgosCard("⚖ ${TAG_LABELS[a]?.uppercase()} × ${TAG_LABELS[b]?.uppercase()}") {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("", modifier = Modifier.weight(1.4f))
                            Text(TAG_LABELS[a] ?: a, style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.weight(1f).clickable { onRegion(a) })
                            Text(TAG_LABELS[b] ?: b, style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.weight(1f).clickable { onRegion(b) })
                        }
                        rows.forEach { (pair, vb) ->
                            val (label, va) = pair
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                                Text(va, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(vb, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    val ta = days.map { d -> d.date to (d.tension[a] ?: 0) }
                    val tb = days.map { d -> d.date to (d.tension[b] ?: 0) }
                    if (ta.isNotEmpty()) {
                        ArgosCard("TENSÃO · 14 DIAS") {
                            Text(TAG_LABELS[a] ?: a, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                            TensionHistoryChart(ta, compact = true)
                            Text(TAG_LABELS[b] ?: b, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                            TensionHistoryChart(tb, compact = true)
                        }
                    }
                }
            } else {
                item {
                    Hint("Como estava a região numa data e como está hoje. Só a tensão e as notícias têm histórico; a maioria dos sensores do Radar mostra só o agora.")
                    RegionChips(a, { a = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(1, 7, 14, 30).forEach { d ->
                            FilterChip(selected = daysAgo == d, onClick = { daysAgo = d }, label = { Text(if (d == 1) "ontem" else "há $d dias") })
                        }
                    }
                }
                item {
                    val today = LocalDate.now()
                    val then = today.minusDays(daysAgo.toLong())
                    val dayThen = stats?.days.orEmpty().firstOrNull { it.date == then.toString() }
                    val dayNow = stats?.days.orEmpty().firstOrNull { it.date == today.toString() }
                    val topThen = archive.orEmpty().firstOrNull { it.date == then.toString() }?.top
                    val zone = java.time.ZoneId.systemDefault()
                    fun alertsOn(d: LocalDate) = vigil.count { e ->
                        e.region == a && java.time.Instant.ofEpochMilli(e.time).atZone(zone).toLocalDate() == d
                    }
                    val rows = listOf(
                        Triple("Pico de tensão do dia", dayThen?.tension?.get(a)?.toString() ?: "–", (dayNow?.tension?.get(a) ?: feed?.regions?.get(a)?.tension)?.toString() ?: "–"),
                        Triple("Histórias que começaram", "${dayThen?.counts?.get(a) ?: 0}", "${dayNow?.counts?.get(a) ?: 0}"),
                        Triple("Alertas na vigília", "${alertsOn(then)}", "${alertsOn(today)}"),
                        Triple("Relógio do Argos (pico)", dayThen?.global?.takeIf { it > 0 }?.toString() ?: "–", (dayNow?.global?.takeIf { it > 0 } ?: feed?.global?.index)?.toString() ?: "–"),
                    )
                    ArgosCard("🕰 ${TAG_LABELS[a]?.uppercase()} · ${then.dayOfMonth}/${then.monthValue} × HOJE") {
                        rows.forEach { (label, x, y) ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                                Text(x, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(y, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                        }
                        if (topThen != null) {
                            Text("Principal do Argos naquele dia:", style = MaterialTheme.typography.labelSmall, color = Accent, modifier = Modifier.padding(top = 10.dp))
                            Text(repo.translator.display(topThen.title, topThen.lang), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (dayThen == null) Hint("Sem estatística guardada para essa data.")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status das fontes
// ---------------------------------------------------------------------------

private val RADAR_NAMES = mapOf(
    "internet" to "Internet (IODA)", "airspace" to "Espaço aéreo (OpenSky)", "fires" to "Focos de calor (NASA)", "straits" to "Navios (PortWatch)",
    "markets" to "Mercados", "humanitarian" to "Números humanitários", "losses" to "Perdas russas", "official" to "Fontes oficiais",
    "sanctions" to "Sanções", "analysis" to "Análises", "crisiswatch" to "CrisisWatch", "factcheck" to "Checagens", "predictions" to "Polymarket",
    "military" to "Aviões militares (adsb.lol)", "carriers" to "Porta-aviões (USNI)", "frontline" to "Linha de frente (DeepStateMap)",
    "sirens" to "Sirenes (Tzeva Adom)", "quakes" to "Sismos (USGS)", "travel" to "Alertas de viagem (EUA)",
)

@Composable
fun SourcesStatusScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    val status by produceState<SourcesStatus?>(null) { value = repo.loadSourcesStatus().getOrNull() }
    androidx.compose.material3.Scaffold(contentWindowInsets = NoInsets, topBar = { BackBar("Status das fontes", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item { Hint("Se a última coleta de cada veículo e sensor deu certo. Uma fonte fora do ar não derruba as outras: o Argos mantém o último dado bom. Também em abugdn.github.io/Argos/status.html") }
            val sources = status?.sources.orEmpty().entries.sortedWith(compareBy<Map.Entry<String, com.abugdn.wid.data.SourceStatus>>({ it.value.ok }, { it.key }))
            item {
                ArgosCard(
                    "📰 VEÍCULOS · ${sources.count { it.value.ok }}/${sources.size} FUNCIONANDO",
                    alert = sources.any { !it.value.ok },
                    updated = status?.generatedAt,
                ) {
                    if (status == null) Text("Carregando…", style = MaterialTheme.typography.bodySmall)
                    sources.forEach { (name, s) -> StatusRow(s.ok, name, if (s.ok) "${s.items} itens" else "falhou", if (s.ok) null else s.error) }
                }
            }
            val sections = radar?.status.orEmpty().entries.sortedBy { it.value.ok }
            item {
                ArgosCard("📡 RADAR · ${sections.count { it.value.ok }}/${sections.size} FUNCIONANDO", alert = sections.any { !it.value.ok }) {
                    sections.forEach { (key, s) -> StatusRow(s.ok, RADAR_NAMES[key] ?: key, relativeTime(s.checked), if (s.ok) null else s.error) }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, name: String, detail: String, error: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp, end = 8.dp).width(10.dp).height(10.dp).background(if (ok) GREEN else Alert, RoundedCornerShape(5.dp)))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (!error.isNullOrBlank()) Text(error.take(160), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Quem apoia quem
// ---------------------------------------------------------------------------

private fun edgeColor(type: String): Color = when (type) {
    "guerra" -> Alert
    "rival" -> ORANGE
    "apoio" -> Accent
    "aliado" -> GREEN
    else -> Ash
}

@Composable
fun AlliancesScreen(onBack: () -> Unit, onRegion: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("ira") }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.Scaffold(contentWindowInsets = NoInsets, topBar = { BackBar("Quem apoia quem", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Hint("Rede simplificada dos principais países e grupos nas guerras que o Argos acompanha, até 2025. Toque num nome para ver as ligações. Acusações contestadas dizem quem acusa e quem nega.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    ALLIANCE_TYPES.keys.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(12.dp).height(3.dp).background(edgeColor(t)))
                            Text(" $t", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                val n = ALLIANCE_NODES.size
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.width * 0.36f
                            val hit = ALLIANCE_NODES.indices.minByOrNull { i ->
                                val ang = 2 * PI * i / n - PI / 2
                                hypot(tap.x - (c.x + r * cos(ang).toFloat()), tap.y - (c.y + r * sin(ang).toFloat()))
                            }
                            if (hit != null) selected = ALLIANCE_NODES[hit].key
                        }
                    },
                ) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.width * 0.36f
                    val pos = ALLIANCE_NODES.mapIndexed { i, node ->
                        val ang = 2 * PI * i / n - PI / 2
                        node.key to Offset(c.x + r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat())
                    }.toMap()
                    ALLIANCE_EDGES.forEach { e ->
                        val p1 = pos[e.from] ?: return@forEach
                        val p2 = pos[e.to] ?: return@forEach
                        val focus = e.from == selected || e.to == selected
                        drawLine(
                            edgeColor(e.type).copy(alpha = if (focus) 1f else 0.14f), p1, p2,
                            strokeWidth = if (focus) 3.dp.toPx() else 1.dp.toPx(),
                        )
                    }
                    ALLIANCE_NODES.forEach { node ->
                        val p = pos.getValue(node.key)
                        val isSel = node.key == selected
                        drawCircle(if (isSel) Accent else Ash, radius = if (isSel) 7.dp.toPx() else 4.5.dp.toPx(), center = p)
                        val layout = measurer.measure(
                            node.name,
                            TextStyle(fontSize = 10.sp, color = if (isSel) Accent else labelColor, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal),
                        )
                        // Nome para fora do círculo.
                        val dx = p.x - c.x
                        val dy = p.y - c.y
                        val tx = if (dx >= 0) p.x + 9.dp.toPx() else p.x - 9.dp.toPx() - layout.size.width
                        val ty = p.y - layout.size.height / 2f + (if (dy < 0) -4.dp.toPx() else 4.dp.toPx())
                        drawText(layout, topLeft = Offset(tx.coerceIn(0f, size.width - layout.size.width), ty))
                    }
                }
            }
            item {
                val node = ALLIANCE_NODES.first { it.key == selected }
                val edges = ALLIANCE_EDGES.filter { it.from == selected || it.to == selected }
                ArgosCard("${node.icon} ${node.name.uppercase()} · ${edges.size} LIGAÇÕES", onClick = { node.tag?.let(onRegion) }) {
                    ALLIANCE_TYPES.forEach { (type, label) ->
                        val list = edges.filter { it.type == type }
                        if (list.isEmpty()) return@forEach
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = edgeColor(type), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        list.forEach { e ->
                            val other = ALLIANCE_NODES.first { it.key == (if (e.from == selected) e.to else e.from) }
                            val arrow = when {
                                type != "apoio" -> "↔"
                                e.from == selected -> "→"
                                else -> "←"
                            }
                            Text(
                                "$arrow ${other.icon} ${other.name}: ${e.note}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 3.dp).clickable { selected = other.key },
                            )
                        }
                    }
                    Text(CONTEXT_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

private const val CONTEXT_NOTE = "Texto fixo até 2025, escrito à mão; alianças mudam. Toque no cartão para abrir a região."

// ---------------------------------------------------------------------------
// Termômetro diplomático
// ---------------------------------------------------------------------------

private fun diplomacyColor(r: DiplomacyRegion): Color = when (r.label) {
    "hostil" -> Alert
    "cooperativo" -> GREEN
    "misto" -> Accent
    else -> Ash
}

@Composable
fun DiplomacyScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onRegion: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val data by repo.diplomacy.collectAsStateWithLifecycle()
    val regions = data?.regions.orEmpty().entries.sortedByDescending { it.value.total }
    androidx.compose.material3.Scaffold(contentWindowInsets = NoInsets, topBar = { BackBar("Termômetro diplomático", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Hint("Eventos diplomáticos nas manchetes dos últimos 7 dias. Esquentam: embaixador expulso ou convocado, rompimento, sanções, fronteira fechada, reunião de emergência, ameaças. Esfriam: negociação, acordo, encontros, reaproximação. É a conta desses eventos por regras automáticas, não uma opinião, e erra às vezes.")
            }
            if (regions.isEmpty()) item { Text("Ainda sem dados. Chegam com a próxima coleta.", style = MaterialTheme.typography.bodyMedium) }
            items(regions, key = { it.key }) { (tag, r) ->
                DiplomacyContent(tag, r, data?.labels.orEmpty(), onOpen, onRegion, compact = true)
            }
        }
    }
}

/** Cartão da região (página da região) ou linha da tela do termômetro. */
@Composable
fun DiplomacyCard(tag: String, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val data by repo.diplomacy.collectAsStateWithLifecycle()
    val r = data?.regions?.get(tag) ?: return
    DiplomacyContent(tag, r, data?.labels.orEmpty(), onOpen, null, compact = false)
}

@Composable
private fun DiplomacyContent(tag: String, r: DiplomacyRegion, labels: Map<String, String>, onOpen: (String) -> Unit, onRegion: ((String) -> Unit)?, compact: Boolean) {
    val repo = LocalContext.current.repository
    val go = LocalGo.current
    ArgosCard(
        "🌡 DIPLOMACIA" + (if (compact) " · ${(TAG_LABELS[tag] ?: tag).uppercase()}" else " · 7 DIAS") + " · ${r.label.uppercase()}",
        titleColor = diplomacyColor(r),
        info = "Conta eventos diplomáticos nas manchetes dos últimos 7 dias (regras automáticas). 0 = só cooperação, 100 = só hostilidade.",
        collapsedSummary = if (compact) "${r.total} eventos · índice ${r.index}" else null,
        startExpanded = !compact,
        onClick = if (compact) onRegion?.let { f -> { f(tag) } } else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("coopera", style = MaterialTheme.typography.labelSmall, color = GREEN)
            Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(8.dp).background(Ash.copy(alpha = 0.3f), RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth((r.index / 100f).coerceIn(0.02f, 1f)).height(8.dp).background(diplomacyColor(r), RoundedCornerShape(4.dp)))
            }
            Text("hostil", style = MaterialTheme.typography.labelSmall, color = Alert)
        }
        Text(
            r.counts.entries.sortedByDescending { it.value }.joinToString(" · ") { (k, v) ->
                (if (DIPLOMACY_HOSTILE[k] == true) "▲ " else "▼ ") + "${labels[k] ?: k}: $v"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        r.events.take(if (compact) 4 else 6).forEach { e ->
            Text(
                "• " + repo.translator.display(e.title, e.lang),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onOpen(e.clusterId) }.padding(top = 4.dp),
            )
        }
        if (!compact) androidx.compose.material3.TextButton(onClick = { go("diplomacy") }) { Text("Todas as regiões →") }
    }
}

// ---------------------------------------------------------------------------
// E o Brasil?
// ---------------------------------------------------------------------------

private data class BrazilLink(val icon: String, val title: String, val text: String, val market: String? = null)

private val BR_OIL = BrazilLink(
    "🛢", "Combustível",
    "O preço do petróleo (Brent) pesa na gasolina e no diesel no Brasil, porque a Petrobras acompanha o mercado internacional, com atraso e amortecimento.",
    "brent",
)
private val BR_DOLLAR = BrazilLink(
    "💵", "Dólar",
    "Em crises, investidores correm para o dólar e o real costuma perder valor, o que encarece importados e viagens.",
    "real",
)
private val BR_FERT = BrazilLink(
    "🌾", "Fertilizantes",
    "O Brasil importa cerca de 85% dos fertilizantes que usa. A Rússia é a maior fornecedora e o Golfo vende muita ureia: alta ou falta encarece a safra.",
)
private val BR_DIESEL = BrazilLink(
    "⛽", "Diesel russo",
    "Desde 2023 a Rússia está entre as maiores fornecedoras do diesel que o Brasil importa; sanções e ataques a refinarias mexem nesse preço.",
)
private val BR_ROUTES = BrazilLink(
    "🚢", "Frete",
    "Navios que desviam do Mar Vermelho dão a volta na África: o frete e o seguro de cargas entre Ásia, Europa e Brasil ficam mais caros.",
)
private val BR_PEOPLE = BrazilLink(
    "🧳", "Brasileiros na região",
    "Há comunidades brasileiras grandes em Israel e no Líbano. Em crises, o Itamaraty organiza repatriações com aviões da FAB, como em 2023 e 2024.",
)
private val BR_EXPORTS = BrazilLink(
    "🐔", "Exportações",
    "O Oriente Médio compra muito frango, carne bovina, açúcar, milho e soja do Brasil; guerra e rotas fechadas podem atrasar ou mudar esses pedidos.",
)
private val BR_FOOD = BrazilLink(
    "🍞", "Grãos",
    "Rússia e Ucrânia estão entre os maiores exportadores de trigo e milho do mundo; a guerra mexe nos preços mundiais de alimentos.",
)

private val BRAZIL_LINKS = mapOf(
    "ira" to listOf(BR_OIL, BR_DOLLAR, BR_EXPORTS, BR_FERT),
    "iemen" to listOf(BR_ROUTES, BR_OIL),
    "egito" to listOf(BR_ROUTES, BR_EXPORTS),
    "golfo" to listOf(BR_OIL, BR_FERT, BR_EXPORTS),
    "arabia" to listOf(BR_OIL, BR_EXPORTS),
    "emirados" to listOf(BR_OIL, BR_EXPORTS),
    "iraque" to listOf(BR_OIL),
    "israel" to listOf(BR_PEOPLE, BR_DOLLAR, BR_OIL),
    "gaza" to listOf(BR_PEOPLE),
    "cisjordania" to listOf(BR_PEOPLE),
    "libano" to listOf(BR_PEOPLE),
    "ucrania_russia" to listOf(BR_FERT, BR_DIESEL, BR_FOOD, BR_DOLLAR),
    "eua" to listOf(BR_DOLLAR, BR_OIL),
    "mediterraneo" to listOf(BR_ROUTES),
    "turquia" to listOf(BR_ROUTES),
)

/** Possíveis ligações entre a região e o Brasil, com as cotações do Radar quando há. */
@Composable
fun BrazilImpactCard(tag: String, radar: RadarData?) {
    val links = BRAZIL_LINKS[tag] ?: return
    ArgosCard(
        "🇧🇷 E O BRASIL?",
        info = "Canais por onde uma crise nesta região costuma chegar ao Brasil. São possíveis ligações, não previsões: o efeito depende de quanto dura e de muitos outros fatores.",
        collapsedSummary = links.joinToString(" · ") { it.title },
        startExpanded = false,
    ) {
        links.forEach { l ->
            val q = l.market?.let { m -> radar?.markets?.items?.firstOrNull { it.id == m } }
            Text("${l.icon} ${l.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(l.text, style = MaterialTheme.typography.bodySmall)
            if (q?.price != null) {
                val week = q.changeWeekPct?.let { " · semana ${if (it >= 0) "+" else ""}${"%.1f".format(java.util.Locale("pt", "BR"), it)}%" } ?: ""
                Text(
                    "${q.name}: ${"%.${q.digits}f".format(java.util.Locale("pt", "BR"), q.price)} ${q.unit}$week",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
