package com.abugdn.wid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.AirwarDay
import com.abugdn.wid.data.RadarData
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.UNSC_KINDS
import com.abugdn.wid.repository
import java.text.NumberFormat
import java.util.Locale

private val nf = NumberFormat.getIntegerInstance(Locale("pt", "BR"))

/** Conselho de Segurança da ONU: reuniões, resoluções, vetos e votações previstas (30 dias). */
@Composable
fun UnscCard(radar: RadarData, onRegion: (String) -> Unit) {
    val section = radar.unsc ?: return
    val repo = LocalContext.current.repository
    val uri = LocalUriHandler.current
    val vetoes = section.items.filter { it.kind == "veto" }
    ArgosCard(
        "🇺🇳 CONSELHO DE SEGURANÇA DA ONU · 30 DIAS",
        alert = vetoes.isNotEmpty(),
        source = "ONU e Security Council Report",
        updated = section.updated,
        status = radar.status["unsc"],
        info = "Reuniões, resoluções aprovadas, vetos e votações previstas do Conselho de Segurança, pelo serviço de imprensa da ONU " +
            "e pelo Security Council Report. Cinco membros permanentes (EUA, Rússia, China, França e Reino Unido) podem vetar qualquer resolução.",
        collapsedSummary = "${section.counts["aprovada"] ?: 0} aprovadas · ${section.counts["veto"] ?: 0} vetadas ou rejeitadas · ${section.counts["reuniao"] ?: 0} reuniões",
        startExpanded = vetoes.isNotEmpty(),
    ) {
        Text(
            "${section.counts["aprovada"] ?: 0} resoluções aprovadas · ${section.counts["veto"] ?: 0} vetadas ou rejeitadas · ${section.counts["reuniao"] ?: 0} reuniões",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        section.items.sortedBy { if (it.kind == "veto" || it.kind == "votacao") 0 else 1 }.take(10).forEach { i ->
            Column(Modifier.fillMaxWidth().clickable(enabled = i.url.isNotBlank()) { runCatching { uri.openUri(i.url) } }.padding(vertical = 5.dp)) {
                Text(
                    (UNSC_KINDS[i.kind] ?: i.kind) + " · " + dayClock(i.date) +
                        (if (i.vetoBy.isNotEmpty()) " · veto: ${i.vetoBy.joinToString(", ")}" else "") +
                        i.tags.mapNotNull { TAG_LABELS[it] }.take(2).joinToString("") { " · $it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i.kind == "veto") Alert else Accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(repo.translator.cached(i.title), style = MaterialTheme.typography.bodySmall)
            }
        }
        val regions = section.items.flatMap { it.tags }.distinct().take(4)
        if (regions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                regions.forEach { t -> TextButton(onClick = { onRegion(t) }) { Text(TAG_LABELS[t] ?: t) } }
            }
        }
    }
}

/** Tempo agora nas cidades e pontos das guerras (Open-Meteo). */
@Composable
fun WeatherCard(radar: RadarData, onRegion: (String) -> Unit) {
    val section = radar.weather ?: return
    val flagged = section.places.filter { it.flags.isNotEmpty() }
    ArgosCard(
        "🌦 TEMPO NAS ZONAS DE CONFLITO",
        source = "Open-Meteo",
        updated = section.updated,
        status = radar.status["weather"],
        info = "Tempo agora nas cidades e pontos das guerras. Vento forte, chuva, neve, neblina e tempestades de areia atrapalham " +
            "voos, drones e operações em terra, e pioram a situação de quem está desabrigado.",
        collapsedSummary = if (flagged.isEmpty()) "sem tempo severo agora" else flagged.joinToString(" · ") { "${it.name}: ${it.flags.first()}" },
        startExpanded = false,
    ) {
        section.places.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = p.tag != null) { p.tag?.let(onRegion) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(
                            p.desc.takeIf { it.isNotBlank() },
                            p.wind?.let { "vento ${it.toInt()} km/h" + (p.gusts?.takeIf { g -> g >= 30 }?.let { g -> " (rajadas ${g.toInt()})" } ?: "") },
                            p.visibility?.takeIf { it < 10_000 }?.let { "visibilidade ${"%.1f".format(Locale("pt", "BR"), it / 1000)} km" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p.flags.isNotEmpty()) {
                        Text("⚠ " + p.flags.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Alert, fontWeight = FontWeight.Bold)
                    }
                }
                Text(p.temp?.let { "${it.toInt()}°" } ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Placar aéreo da Ucrânia: drones e mísseis lançados pela Rússia por noite e quantos foram abatidos. */
@Composable
fun AirwarCard(onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val days by repo.airwar.collectAsStateWithLifecycle()
    if (days.isEmpty()) return
    val last = days.last()
    val recent = days.takeLast(30)
    val max = recent.maxOf { it.drones + it.missiles }.coerceAtLeast(1)
    val week = days.takeLast(7)
    ArgosCard(
        "🇺🇦 PLACAR AÉREO DA UCRÂNIA",
        source = "manchetes (Força Aérea da Ucrânia)",
        info = "Drones e mísseis lançados pela Rússia contra a Ucrânia por noite e quantos a Ucrânia diz ter abatido, lidos " +
            "automaticamente das manchetes que citam o balanço da Força Aérea ucraniana. Os números são da Ucrânia; dias sem manchete ficam de fora.",
        onClick = { last.clusterId?.let(onOpen) },
    ) {
        Text(
            "${dayLabel(last.date)}: ${nf.format(last.drones)} drones" + (if (last.missiles > 0) " e ${nf.format(last.missiles)} mísseis" else "") +
                (last.downed?.let { " · ${nf.format(it)} abatidos" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Últimos 7 dias com balanço: ${nf.format(week.sumOf { it.drones })} drones e ${nf.format(week.sumOf { it.missiles })} mísseis",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            recent.forEach { d -> AirwarBar(d, max, Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(dayLabel(recent.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("■ drones  ", style = MaterialTheme.typography.labelSmall, color = Accent)
            Text("■ mísseis", style = MaterialTheme.typography.labelSmall, color = Alert)
        }
    }
}

@Composable
private fun AirwarBar(d: AirwarDay, max: Int, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
        if (d.missiles > 0) Box(Modifier.fillMaxWidth().height((60f * d.missiles / max).coerceAtLeast(1f).dp).background(Alert))
        Box(Modifier.fillMaxWidth().height((60f * d.drones / max).coerceAtLeast(1f).dp).background(Accent, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
    }
}

/** Sirenes por hora do dia (horário de Israel), 7 dias. */
@Composable
fun SirenHoursCard(hours: List<Int>) {
    if (hours.size != 24 || hours.sum() == 0) return
    val max = hours.max().coerceAtLeast(1)
    val peak = hours.indexOf(hours.max())
    ArgosCard(
        "⏰ SIRENES POR HORA DO DIA · 7 DIAS",
        source = "Tzeva Adom",
        info = "Em que horários (de Israel) as sirenes mais tocaram na última semana, somando os locais com alerta.",
    ) {
        Text("Pico: entre ${peak}h e ${(peak + 1) % 24}h (horário de Israel)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            hours.forEachIndexed { h, n ->
                Box(
                    Modifier.weight(1f).height((56f * n / max).coerceAtLeast(1f).dp)
                        .background(if (h == peak) Alert else Accent.copy(alpha = 0.7f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("0h", "6h", "12h", "18h", "23h").forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

