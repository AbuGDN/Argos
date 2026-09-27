package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.CONFLICTS
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.FirstStats
import com.abugdn.wid.data.ORIGIN_LABELS
import com.abugdn.wid.data.RegionStat
import com.abugdn.wid.data.describe
import com.abugdn.wid.repository
import java.text.NumberFormat
import java.util.Locale

private val FIGURE_LABELS = mapOf("killed" to "Mortos", "injured" to "Feridos")

/** Escala da paleta Argos: verde-musgo, ouro, cobre e vermelho-sangue. */
fun tensionColor(level: String): Color = when (level) {
    "crítica" -> Alert
    "alta" -> Color(0xFFC8662B)
    "moderada" -> Accent
    else -> Color(0xFF6E8B6A)
}

@Composable
private fun InsightCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun CardTitle(text: String, color: Color = Accent) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
}

/** Medidor 0–100 de tensão da região, com o alerta de anomalia. */
@Composable
fun TensionGauge(stat: RegionStat, modifier: Modifier = Modifier) {
    val color = tensionColor(stat.level)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tensão ${stat.tension}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(" · ${stat.level}", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { stat.tension / 100f },
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            "${stat.last24} histórias nas últimas 24 h · média ${"%.0f".format(stat.baseline)} por dia",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (stat.spike) {
            Text(
                "⚠ Alta incomum: ritmo ${"%.1f".format(stat.spikeRatio)}× o normal nas últimas 6 h",
                style = MaterialTheme.typography.labelMedium,
                color = Alert,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        stat.signals.forEach { signal ->
            Text(
                "📡 " + signal.describe(),
                style = MaterialTheme.typography.labelMedium,
                color = Alert,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "Combina volume, palavras de escalada (míssil, invasão, nuclear), urgência e cobertura, comparado com a média da própria região. " +
                "Apagão de internet e espaço aéreo fechado (Radar) somam pontos.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "Guerra em Gaza · dia 1.084". */
@Composable
fun ConflictCounter(tag: String, modifier: Modifier = Modifier) {
    val conflict = CONFLICTS[tag] ?: return
    val day = NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(conflict.day())
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("DIA $day", style = MaterialTheme.typography.headlineSmall, color = Accent, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(8.dp))
        Text(
            "${conflict.label} (desde ${conflict.start.dayOfMonth}/${conflict.start.monthValue}/${conflict.start.year})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/** Números de mortos/feridos por veículo; destaca quando eles não batem. */
@Composable
fun FiguresCard(cluster: Cluster) {
    if (cluster.figures.isEmpty()) return
    val divergent = cluster.figures.values.any { it.divergent }
    InsightCard {
        CardTitle(if (divergent) "⚠ NÚMEROS DIVERGENTES" else "NÚMEROS CITADOS", if (divergent) Alert else Accent)
        cluster.figures.forEach { (kind, info) ->
            Text(
                FIGURE_LABELS[kind] ?: kind,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            info.bySource.entries.sortedByDescending { it.value }.forEach { (source, n) ->
                Text("$n · $source", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (divergent) {
            Text(
                "Os veículos citam números diferentes. Balanços costumam subir com o tempo; confira a hora de cada um.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Selo curto para a lista de notícias. */
fun sidesBadge(c: Cluster): String? = when (c.sides) {
    "opostos" -> "🤝 LADOS OPOSTOS"
    "um_lado" -> "⚠ SÓ UM LADO"
    else -> null
}

/** Confirmação cruzada: lados rivais contando a mesma história, ou só um deles. */
@Composable
fun SidesCard(cluster: Cluster) {
    val side = cluster.sides ?: return
    val byOrigin = cluster.articles.groupBy { it.origin }.mapValues { (_, arts) -> arts.map { it.source }.distinct() }
    InsightCard {
        if (side == "opostos") {
            CardTitle("🤝 CONFIRMADO POR LADOS OPOSTOS")
            Text(
                "Veículos de lados rivais contam esta história. Fatos que os dois lados relatam costumam ser mais sólidos; a interpretação ainda pode mudar.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            listOf("israel", "eua", "arabe").forEach { origin ->
                val sources = byOrigin[origin] ?: return@forEach
                Text(
                    "${ORIGIN_LABELS[origin] ?: origin}: ${sources.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            val origin = byOrigin.keys.firstOrNull()
            CardTitle("⚠ SÓ UM LADO NOTICIOU", Alert)
            Text(
                "Até agora só a ${(ORIGIN_LABELS[origin] ?: "mesma origem").lowercase()} publicou esta história " +
                    "(${byOrigin[origin].orEmpty().joinToString(", ")}). Vale esperar confirmação de outras fontes.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Palavras que cada origem usou para a mesma coisa. */
@Composable
fun FramingCard(cluster: Cluster) {
    if (cluster.framing.isEmpty()) return
    InsightCard {
        CardTitle("PALAVRAS DE CADA LADO")
        cluster.framing.forEach { g ->
            Text(g.group, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            g.byOrigin.forEach { (origin, terms) ->
                Text(
                    "${ORIGIN_LABELS[origin] ?: origin}: " + terms.joinToString(", ") { "“$it”" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** "Capítulo 3 de 5" da saga, com a lista de capítulos. */
@Composable
fun SagaCard(cluster: Cluster, onOpen: (String) -> Unit) {
    val saga = cluster.saga ?: return
    val context = LocalContext.current
    val repo = context.repository
    var expanded by remember(cluster.id) { mutableStateOf(false) }
    InsightCard {
        CardTitle("📚 CAPÍTULO ${saga.chapter} DE ${maxOf(saga.total, saga.chapters.size)} DESTA SAGA")
        Text(
            repo.translator.display(saga.title, saga.lang),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (expanded) {
            saga.chapters.forEachIndexed { i, ch ->
                val current = ch.clusterId == cluster.id
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = !current) {
                            if (repo.cluster(ch.clusterId) != null) onOpen(ch.clusterId) else openUrl(context, ch.url)
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        "${i + 1}. ${dayClock(ch.published)} · ${ch.source}" + if (current) " · você está aqui" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (current) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        repo.translator.display(ch.title, ch.lang),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Esconder capítulos" else "Ver os ${saga.chapters.size} capítulos")
        }
    }
}

/** Ranking de quem publica primeiro nas histórias grandes. */
@Composable
fun FirstRankingCard(stats: FirstStats, modifier: Modifier = Modifier) {
    if (stats.ranking.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            CardTitle("QUEM NOTICIA PRIMEIRO · 30 DIAS")
            Text(
                "Em ${stats.bigStories} histórias grandes (3+ veículos)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stats.ranking.filter { it.firsts > 0 }.take(6).forEachIndexed { i, r ->
                Text(
                    "${i + 1}. ${r.source} — primeiro em ${r.firsts} de ${r.stories}" +
                        if (r.leadMin > 0) " · ~${r.leadMin} min antes" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Veículos que trocaram a manchete do mesmo link depois de publicar: antes → agora. */
@Composable
fun EditsCard(cluster: Cluster) {
    val edited = cluster.articles.filter { it.edits.isNotEmpty() }
    if (edited.isEmpty()) return
    val translator = LocalContext.current.repository.translator
    InsightCard {
        CardTitle("✏ MANCHETE ALTERADA")
        Text(
            "O veículo mudou o título depois de publicar. Mudanças de palavra (\"ataque\" → \"suposto ataque\") dizem muito.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        edited.forEach { a ->
            Text(a.source, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            a.edits.forEach { e ->
                Text(
                    "Antes" + (if (e.at.isNotBlank()) " (até ${dayClock(e.at)})" else "") + ": " + translator.display(e.title, a.lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
            }
            Text("Agora: " + translator.display(a.title, a.lang), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Barras dos últimos dias de um índice 0–100 (tensão de uma região ou relógio global).
 * Picos críticos (75+) em vermelho, com o valor escrito em cima.
 */
@Composable
fun TensionHistoryChart(values: List<Pair<String, Int>>, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (values.isEmpty()) {
        Text(
            "O histórico de tensão começa a ser gravado com esta versão; o gráfico enche com os dias.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val levels = values.map { (_, v) -> tensionColor(levelOf(v)) }
    val peak = values.maxOf { it.second }
    Column(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(if (compact) 32.dp else 90.dp)) {
            val slot = size.width / values.size.coerceAtLeast(if (compact) 1 else 7)
            val bar = slot * 0.7f
            values.forEachIndexed { i, (_, v) ->
                val h = (size.height * v / 100f).coerceAtLeast(2.dp.toPx())
                drawRect(
                    color = levels[i],
                    topLeft = androidx.compose.ui.geometry.Offset(i * slot + (slot - bar) / 2, size.height - h),
                    size = androidx.compose.ui.geometry.Size(bar, h),
                )
            }
            // Linha dos 75 (crítica).
            val y = size.height * 0.25f
            drawLine(Alert.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (!compact) Row(Modifier.fillMaxWidth()) {
            Text(dayLabel(values.first().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                "pico ${peak} · linha vermelha = crítica (75)",
                style = MaterialTheme.typography.labelSmall,
                color = if (peak >= 75) Alert else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun levelOf(score: Int): String = when {
    score >= 75 -> "crítica"
    score >= 50 -> "alta"
    score >= 25 -> "moderada"
    else -> "baixa"
}

/** Relógio do Argos: índice global com a região que mais puxa. */
@Composable
fun ArgosClock(clock: com.abugdn.wid.data.GlobalClock, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val color = tensionColor(clock.level)
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${clock.index}",
                style = MaterialTheme.typography.displaySmall,
                color = color,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("👁 RELÓGIO DO ARGOS · ${clock.level.uppercase()}", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { clock.index / 100f },
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp),
                )
                Text(
                    "Tensão global · puxado por ${com.abugdn.wid.data.TAG_LABELS[clock.leader] ?: clock.leader}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Contador de trégua de uma região: dias desde o início e última violação relatada (da vigília). */
@Composable
fun TruceCards(tag: String) {
    val repo = LocalContext.current.repository
    val ended by repo.endedTruces.collectAsStateWithLifecycle()
    val vigil by repo.vigil.collectAsStateWithLifecycle()
    com.abugdn.wid.data.TRUCES.filter { tag in it.tags }.forEach { truce ->
        TruceCard(truce, truce.key in ended, vigil) { repo.setTruceEnded(truce.key, it) }
    }
}

@Composable
fun TruceCard(
    truce: com.abugdn.wid.data.Truce,
    ended: Boolean,
    vigil: List<com.abugdn.wid.data.VigilEvent>,
    onEnded: (Boolean) -> Unit,
) {
    val zone = java.time.ZoneId.systemDefault()
    val startMs = truce.start.atStartOfDay(zone).toInstant().toEpochMilli()
    val violations = vigil.filter { it.kind == "truce" && it.region in truce.tags && it.time >= startMs }
    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
    val recent = violations.count { it.time >= weekAgo }
    InsightCard {
        if (ended) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🕊 ${truce.label}: marcada como encerrada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEnded(false) }) { Text("Reativar") }
            }
            return@InsightCard
        }
        val days = java.time.temporal.ChronoUnit.DAYS.between(truce.start, java.time.LocalDate.now(zone)) + 1
        val day = NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(days)
        CardTitle("🕊 ${truce.label.uppercase()} · DIA $day")
        Text(
            "desde ${truce.start.dayOfMonth}/${truce.start.monthValue}/${truce.start.year}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val last = violations.maxByOrNull { it.time }
        Text(
            if (last == null) "Nenhuma violação relatada desde que o Argos começou a vigiar."
            else "⚠ Última violação relatada: ${dayClock(java.time.Instant.ofEpochMilli(last.time).toString())}" +
                if (recent > 0) " · $recent relato(s) em 7 dias" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (recent > 0) Alert else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (recent > 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            com.abugdn.wid.data.TRUCE_DISCLAIMER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = { onEnded(true) }) { Text("Trégua encerrada") }
    }
}

/** Checagens do Radar que falam desta história (Aos Fatos, Lupa, AFP...). */
@Composable
fun FactcheckCard(cluster: Cluster) {
    val context = LocalContext.current
    val repo = context.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    val checks = remember(radar, cluster.id) { repo.factchecksFor(cluster.id) }
    if (checks.isEmpty()) return
    InsightCard {
        CardTitle("⚠ CHECAGEM SOBRE ESTE ASSUNTO", Alert)
        checks.take(3).forEach { fc ->
            Column(Modifier.fillMaxWidth().clickable { openUrl(context, fc.url) }.padding(top = 8.dp)) {
                Text("${fc.source} · ${relativeTime(fc.published)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(repo.translator.display(fc.title, fc.lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Uma agência de checagem publicou algo sobre um assunto parecido. Pode ser um boato desmentido ligado a esta história; toque para ler.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** O que o Radar diz desta região: internet, espaço aéreo, focos de calor, estreitos e CrisisWatch. */
@Composable
fun RegionRadarCard(tag: String) {
    val repo = LocalContext.current.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    val r = radar ?: return
    val lines = buildList {
        r.internet?.countries.orEmpty().filter { it.tag == tag && it.status != "sem_dados" }.forEach { c ->
            val label = com.abugdn.wid.data.INTERNET_STATUS[c.status] ?: c.status
            add("🌐 Internet (${c.name}): $label · ${(c.ratio * 100).toInt()}% do normal" to (c.status != "normal"))
        }
        r.airspace?.zones.orEmpty().filter { it.tag == tag && it.status != "sem_dados" }.forEach { z ->
            val label = com.abugdn.wid.data.AIRSPACE_STATUS[z.status] ?: z.status
            add("✈ Espaço aéreo (${z.name}): $label · ${z.flights} aviões no ar" to (z.status == "fechado" || z.status == "reduzido"))
        }
        r.fires?.zones.orEmpty().filter { it.tag == tag && !it.error }.forEach { z ->
            val high = z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline
            add("🔥 Focos de calor (${z.name}): ${z.count} em 24 h" + (z.baseline?.let { " · média ${it.toInt()}" } ?: "") to high)
        }
        r.straits?.items.orEmpty().filter { it.tag == tag && it.avg7 != null }.forEach { s ->
            val low = s.avg90 != null && s.avg90 > 5 && s.avg7!! < s.avg90 * 0.6
            add("🚢 ${s.name}: ${s.avg7!!.toInt()} navios por dia" + (s.avg90?.let { " · antes ${it.toInt()}" } ?: "") to low)
        }
        r.crisiswatch?.let { cw ->
            val month = cw.month.ifBlank { "este mês" }
            if (cw.deteriorated.any { it.tag == tag }) add("📉 CrisisWatch: piorou em $month" to true)
            if (cw.improved.any { it.tag == tag }) add("📈 CrisisWatch: melhorou em $month" to false)
            if (cw.risk.any { it.tag == tag }) add("⚠ CrisisWatch: alerta de risco de conflito ($month)" to true)
        }
    }
    if (lines.isEmpty()) return
    InsightCard {
        CardTitle("📡 RADAR")
        lines.forEach { (text, alert) ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (alert) Alert else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (alert) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "Sensores e fontes fora da imprensa. Mais detalhes na aba Radar.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
