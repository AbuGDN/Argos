package com.abugdn.wid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.RadarData
import com.abugdn.wid.data.RegionStat
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.repository
import java.time.Duration
import java.time.Instant

/** Selo de confiança para a lista (só quando diz algo: várias fontes, conflito ou mesma agência). */
fun confidenceBadge(c: Cluster): String? {
    val conf = c.confidence ?: return null
    return when {
        conf.level == "conflito" -> "⚖ INFORMAÇÕES CONFLITANTES"
        c.sourcesCount >= 2 && conf.label == "Todos repetem a mesma fonte" -> "🔁 MESMA FONTE"
        conf.level == "alta" -> "✔ VÁRIAS FONTES"
        else -> null
    }
}

private fun confidenceColor(level: String): Color = when (level) {
    "alta" -> Color(0xFF6E8B6A)
    "media" -> Accent
    "conflito" -> Alert
    else -> Ash
}

/** Nível de confiança, motivos e árvore de fontes (quem só repete uma agência). */
@Composable
fun ConfidenceCard(cluster: Cluster) {
    val conf = cluster.confidence ?: return
    val go = LocalGo.current
    ArgosCard(
        "🔎 CONFIANÇA: ${conf.label.uppercase()}",
        titleColor = confidenceColor(conf.level),
        alert = conf.level == "conflito",
        info = "Não diz se a notícia é verdadeira: diz quão apoiada ela está. Conta as fontes independentes " +
            "(veículos que só repetem a mesma agência contam como uma), se os dois lados noticiaram, se os números " +
            "batem e se a história se apoia na versão oficial de uma das partes. Detalhes em Ferramentas → Como sabemos?",
    ) {
        conf.reasons.forEach { r ->
            Text("• $r", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        val tree = cluster.wires
        if (tree != null) {
            Text(
                "ÁRVORE DE FONTES · ${tree.independent} ${if (tree.independent == 1) "fonte independente" else "fontes independentes"} em ${tree.total} ${if (tree.total == 1) "veículo" else "veículos"}",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
            tree.agencies.forEach { (agency, outlets) ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(agency, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                    Column(Modifier.weight(1f)) {
                        outlets.forEach { o -> Text("└ $o", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            val cited = tree.agencies.values.flatten().toSet()
            val own = cluster.articles.map { it.source }.distinct().filter { it !in cited }
            if (own.isNotEmpty()) {
                Text(
                    "Sem agência citada (apuração própria ou não informada): " + own.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        TextButton(onClick = { go("method") }) { Text("Como o Argos calcula →") }
    }
}

private val LANG_NAMES = mapOf("pt" to "português", "en" to "inglês", "he" to "hebraico", "ar" to "árabe")

/** Em que imprensa a história apareceu primeiro, e quanto tempo depois nas outras. */
@Composable
fun SpreadCard(cluster: Cluster) {
    if (cluster.spread.size < 2) return
    ArgosCard(
        "🌐 COMO A NOTÍCIA SE ESPALHOU",
        info = "Primeira publicação da história em cada imprensa (israelense, árabe, americana, internacional, brasileira), " +
            "pela hora que cada veículo informa. Só conta os veículos que o Argos acompanha.",
    ) {
        cluster.spread.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (i == 0) "1º" else "+${formatMinutes(s.afterMin)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == 0) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(72.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${s.source}" + (LANG_NAMES[s.lang]?.let { " · em $it" } ?: "") + " · ${clockTime(s.time)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m min"
    m < 1440 -> "${m / 60} h" + if (m % 60 > 0) " ${m % 60} min" else ""
    else -> "${m / 1440} d"
}

private val PART_LABELS = linkedMapOf(
    "volume" to ("Volume de notícias" to 40.0),
    "escalada" to ("Palavras de escalada" to 25.0),
    "urgencia" to ("Urgentes" to 20.0),
    "cobertura" to ("Cobertura (veículos por história)" to 15.0),
    "sensores" to ("Sensores do Radar" to 20.0),
)

/** De onde vem a tensão: cada parte do índice, com os números por trás. */
@Composable
fun TensionBreakdown(stat: RegionStat, modifier: Modifier = Modifier) {
    if (stat.parts.isEmpty()) return
    val go = LocalGo.current
    ArgosCard(
        "🧮 DE ONDE VEM A TENSÃO",
        modifier = modifier,
        info = "O índice (0 a 100) soma: volume de notícias comparado com o normal da região (até 40), palavras de " +
            "escalada como mortos, míssil e invasão (até 25), histórias urgentes (até 20), quantos veículos cobrem cada " +
            "história (até 15) e sinais do Radar, como apagão e espaço aéreo fechado (até +20).",
        collapsedSummary = PART_LABELS.keys.filter { (stat.parts[it] ?: 0.0) > 0 }
            .joinToString(" · ") { "${PART_LABELS[it]!!.first.substringBefore(" (")} ${stat.parts[it]!!.toInt()}" },
        startExpanded = false,
    ) {
        PART_LABELS.forEach { (key, pair) ->
            val (label, max) = pair
            val v = stat.parts[key] ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${v.toInt()} de ${max.toInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(6.dp).background(Ash.copy(alpha = 0.3f), RoundedCornerShape(3.dp))) {
                Box(
                    Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0f, 1f)).height(6.dp)
                        .background(if (v / max >= 0.75) Alert else Accent, RoundedCornerShape(3.dp)),
                )
            }
        }
        if (stat.why.isNotEmpty()) {
            Text("OS NÚMEROS", style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            stat.why.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
        }
        stat.signals.forEach { s ->
            Text("• Radar: ${s.name} (${s.status})", style = MaterialTheme.typography.bodySmall, color = Alert, modifier = Modifier.padding(top = 2.dp))
        }
        if (stat.spike) {
            Text(
                "⚠ Alta incomum: ${stat.last6} histórias nas últimas 6 h, ritmo de ${stat.spikeRatio}× o normal (o alerta dispara a partir de 3×).",
                style = MaterialTheme.typography.bodySmall,
                color = Alert,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = { go("method") }) { Text("Como o Argos calcula →") }
    }
}

/**
 * Resumo incremental: o que mudou na região desde a última vez que a pessoa abriu esta página
 * (tensão, histórias novas e alertas). A visita é gravada ao sair da página.
 */
@Composable
fun SinceLastVisitCard(tag: String, current: List<Cluster>, stat: RegionStat?, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val prefs = repo.storage.prefs
    val key = "visit_$tag"
    val previous = remember(tag) {
        prefs.getString(key, null)?.split('|')?.let { p -> p.getOrNull(0)?.toLongOrNull()?.let { it to p.getOrNull(1)?.toIntOrNull() } }
    }
    val latestTension by rememberUpdatedState(stat?.tension)
    DisposableEffect(tag) {
        onDispose { prefs.edit().putString(key, "${System.currentTimeMillis()}|${latestTension ?: ""}").apply() }
    }
    val (since, oldTension) = previous ?: return
    val gone = Duration.ofMillis(System.currentTimeMillis() - since)
    if (gone.toMinutes() < 60) return
    val sinceInstant = Instant.ofEpochMilli(since)
    val fresh = current.filter { c -> runCatching { Instant.parse(c.published).isAfter(sinceInstant) }.getOrDefault(false) }
        .sortedByDescending { it.score }
    val alerts = repo.vigil.value.filter { it.region == tag && it.time > since }
    val translator = repo.translator
    ArgosCard(
        "🕘 DESDE SUA ÚLTIMA VISITA · " + relativeTime(sinceInstant.toString()).let { if (it.startsWith("há")) it.uppercase() else "EM $it" },
        info = "O que mudou em ${TAG_LABELS[tag] ?: tag} desde a última vez que você abriu esta página: tensão, histórias novas e alertas.",
    ) {
        val now = stat?.tension
        if (oldTension != null && now != null) {
            val d = now - oldTension
            Text(
                "Tensão: $oldTension → $now" + when {
                    d > 0 -> " (▲ $d)"
                    d < 0 -> " (▼ ${-d})"
                    else -> " (igual)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (d >= 10) Alert else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        alerts.take(3).forEach { e ->
            Text("⚑ ${e.title}", style = MaterialTheme.typography.bodySmall, color = Alert, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            if (fresh.isEmpty()) "Nenhuma história nova." else "${fresh.size} ${if (fresh.size == 1) "história nova" else "histórias novas"}:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        fresh.take(5).forEach { c ->
            Text(
                "• " + translator.display(c.title, c.lang),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onOpen(c.id) }.padding(vertical = 3.dp),
            )
        }
    }
}

/** Nome curto de cada tipo de sinal num incidente. */
val INCIDENT_KINDS = mapOf(
    "internet" to "internet", "airspace" to "espaço aéreo", "news" to "disparo de notícias", "sirens" to "sirenes",
    "military" to "aviões militares", "fires" to "focos de calor", "quake" to "sismo",
)

/** Correlação do Radar: sinais de tipos diferentes na mesma região ao mesmo tempo. */
@Composable
fun IncidentsCard(radar: RadarData, onRegion: (String) -> Unit) {
    if (radar.incidents.isEmpty()) return
    ArgosCard(
        "🧩 SINAIS COINCIDENTES",
        alert = true,
        updated = radar.generatedAt,
        info = "Quando sensores de tipos diferentes mudam ao mesmo tempo na mesma região (sirenes, apagão de internet, " +
            "espaço aéreo, aviões militares, focos de calor, sismo suspeito, disparo de notícias), o Argos junta tudo num " +
            "incidente só. Um sinal sozinho pode ser ruído; vários juntos merecem atenção.",
    ) {
        radar.incidents.forEach { inc ->
            Text(
                "${TAG_LABELS[inc.tag] ?: inc.tag} · ${inc.signals.size} sinais",
                style = MaterialTheme.typography.bodyLarge,
                color = Alert,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { onRegion(inc.tag) },
            )
            inc.signals.forEach { s -> Text("• ${s.text}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/** "Entenda o contexto": lições do Curso e marcos históricos das regiões da notícia. */
@Composable
fun UnderstandCard(cluster: Cluster, onRegion: (String) -> Unit) {
    val go = LocalGo.current
    val lessons = com.abugdn.wid.data.COURSE.filter { l -> l.tags.any { it in cluster.tags } }.take(2)
    val milestones = cluster.tags.mapNotNull { t -> com.abugdn.wid.data.MILESTONES[t]?.takeLast(2)?.let { t to it } }.take(2)
    if (lessons.isEmpty() && milestones.isEmpty()) return
    ArgosCard(
        "🎓 ENTENDA O CONTEXTO",
        info = "Atalhos para entender a história desde o começo: as lições do Curso rápido e os marcos mais recentes das regiões da notícia.",
    ) {
        lessons.forEach { l ->
            TextButton(onClick = { go("course:${l.id}") }) { Text("${l.icon} ${l.title} · ${l.minutes} min →") }
        }
        milestones.forEach { (tag, list) ->
            Text(
                (TAG_LABELS[tag] ?: tag).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp).clickable { onRegion(tag) },
            )
            list.forEach { m -> Text("${m.date} · ${m.text}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}
