package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.QUAKE_TYPES
import com.abugdn.wid.data.RadarData
import com.abugdn.wid.data.TRAVEL_LEVELS
import com.abugdn.wid.data.TravelSection
import com.abugdn.wid.repository
import java.time.Duration
import java.time.Instant

/** Abre uma rota do app ("sirens", "deadlines", "radar:0"...), fornecida pelo MainActivity. */
val LocalGo = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Sirene nas últimas 3 horas. */
fun sirensRecent(radar: RadarData): Boolean {
    val last = radar.sirens?.last ?: return false
    val t = runCatching { Instant.parse(last) }.getOrNull() ?: return false
    return Duration.between(t, Instant.now()).toHours() < 3
}

fun LazyListScope.sirensItem(radar: RadarData) {
    val section = radar.sirens ?: return
    item {
        val go = LocalGo.current
        val recent = sirensRecent(radar)
        ArgosCard(
            "🚨 SIRENES EM ISRAEL · 24 H",
            alert = recent,
            source = "Tzeva Adom",
            updated = section.updated,
            status = radar.status["sirens"],
            info = "Alertas de foguete, míssil, drone e infiltração do Comando da Frente Interna de Israel, pelo Tzeva Adom. " +
                "Toque em “Ao vivo” para ver o mapa e os alertas em tempo real.",
            collapsedSummary = if (section.count24h == 0) "nenhuma sirene nas últimas 24 h" else "${section.count24h} locais com sirene nas últimas 24 h",
            startExpanded = recent,
        ) {
            section.events.take(3).forEach { e ->
                Text(
                    relativeTime(e.time) + " · " + e.threat,
                    style = MaterialTheme.typography.labelSmall,
                    color = Alert,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(e.cities.take(6).joinToString(", ") { it.name } + if (e.cities.size > 6) "…" else "", style = MaterialTheme.typography.bodyMedium)
            }
            if (section.events.isEmpty()) Text("Nenhuma sirene nas últimas 24 h.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = { go("sirens") }) { Text("Ao vivo e mapa →") }
        }
    }
}

fun LazyListScope.quakesItem(radar: RadarData, onRegion: (String) -> Unit) {
    val section = radar.quakes ?: return
    item {
        val repo = LocalContext.current.repository
        val uri = LocalUriHandler.current
        val alerts = section.items.filter { it.alert }
        ArgosCard(
            "🌋 SISMÓGRAFO · 7 DIAS",
            alert = alerts.isNotEmpty(),
            source = "USGS",
            updated = section.updated,
            status = radar.status["quakes"],
            info = "Tremores de magnitude 2,5 ou mais nas zonas de conflito (serviço geológico dos EUA). Testes nucleares e grandes " +
                "explosões aparecem como sismos rasos. O alerta sobe quando o USGS classifica como explosão ou quando o tremor é raso " +
                "(até 5 km) perto de um local nuclear. Terremoto comum é o normal: Irã e Turquia tremem com frequência.",
            collapsedSummary = when {
                alerts.isNotEmpty() -> "${alerts.size} suspeito(s)"
                section.items.isEmpty() -> "nenhum tremor nas zonas vigiadas"
                else -> "${section.items.size} tremores, nenhum suspeito"
            },
            startExpanded = alerts.isNotEmpty(),
        ) {
            if (section.items.isEmpty()) Text("Nenhum tremor nas zonas vigiadas nesta semana.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            section.items.take(12).forEach { q ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(enabled = q.tag != null) { q.tag?.let(onRegion) }) {
                        val kind = QUAKE_TYPES[q.type] ?: q.type
                        Text(
                            "M ${q.mag ?: "?"} · $kind · ${q.zone}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (q.alert) Alert else Color.Unspecified,
                            fontWeight = if (q.alert) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            buildString {
                                append(relativeTime(q.time))
                                q.depth?.let { append(" · ${it.toInt()} km de profundidade") }
                                q.site?.let { append(" · a ${it.km} km de ${it.name}") }
                                if (q.place.isNotBlank()) append(" · ${repo.translator.cached(q.place)}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (q.url.isNotBlank()) TextButton(onClick = { runCatching { uri.openUri(q.url) } }) { Text("USGS") }
                }
            }
        }
    }
}

@Composable
fun TravelCard(section: TravelSection, radar: RadarData, onRegion: (String) -> Unit) {
    val fresh = section.changes.filter {
        runCatching { Duration.between(Instant.parse(it.date), Instant.now()).toDays() < 7 }.getOrDefault(false)
    }
    ArgosCard(
        "✈ ALERTAS DE VIAGEM · EUA",
        alert = fresh.any { it.to > it.from && it.to >= 3 },
        source = "Departamento de Estado",
        updated = section.updated,
        status = radar.status["travel"],
        info = "Nível de risco que o governo dos EUA dá a cada país: 1 (precauções normais), 2 (mais cautela), 3 (reconsidere a viagem) " +
            "e 4 (não viaje). Quando um país sobe de nível, costuma ser sinal de piora na segurança. ${section.level4} países estão no nível 4.",
        collapsedSummary = if (fresh.isEmpty()) "sem mudanças nesta semana" else fresh.joinToString(" · ") { "${it.name}: ${it.from} → ${it.to}" },
    ) {
        if (section.changes.isNotEmpty()) {
            Text("MUDANÇAS RECENTES", style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            section.changes.take(6).forEach { c ->
                val up = c.to > c.from
                Text(
                    "${if (up) "▲" else "▼"} ${c.name}: nível ${c.from} → ${c.to} (${TRAVEL_LEVELS[c.to] ?: ""}) · ${relativeTime(c.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (up) Alert else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp).clickable(enabled = c.tag != null) { c.tag?.let(onRegion) },
                )
            }
        }
        section.items.forEach { t ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = t.tag != null) { t.tag?.let(onRegion) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${t.level} · ${t.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = when (t.level) {
                        4 -> Alert
                        3 -> Color(0xFFC8662B)
                        2 -> Accent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (t.level >= 3) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
