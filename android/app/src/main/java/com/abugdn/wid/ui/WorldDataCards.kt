package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.RadarData
import com.abugdn.wid.data.countryPt
import com.abugdn.wid.data.seriesValues
import java.text.NumberFormat
import java.util.Locale

private val ptBR = Locale("pt", "BR")
private val intFmt = NumberFormat.getIntegerInstance(ptBR)

/** 5.224.131 -> "5,2 milhões"; 874.853 -> "875 mil". */
fun bigNumber(n: Long): String = when {
    n >= 1_000_000 -> "%.1f milhões".format(ptBR, n / 1_000_000.0).replace(",0 ", " ")
    n >= 10_000 -> "${intFmt.format((n + 500) / 1000)} mil"
    else -> intFmt.format(n)
}

private fun monthYear(date: String): String {
    val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    val y = date.take(4)
    val m = date.drop(5).take(2).toIntOrNull() ?: return y
    return "${months[(m - 1).coerceIn(0, 11)]}/$y"
}

/** Deslocados e refugiados por país de origem (ACNUR). */
@Composable
fun RefugeesCard(radar: RadarData, onRegion: (String) -> Unit) {
    val section = radar.refugees ?: return
    val list = section.countries.filter { it.latest != null }
        .sortedByDescending { c -> c.latest!!.let { c.abroad(it) + it.idps } }
    if (list.isEmpty()) return
    ArgosCard(
        "🏚 DESLOCADOS E REFUGIADOS",
        source = "ACNUR",
        updated = section.updated,
        status = radar.status["refugees"],
        info = "Dados anuais do ACNUR (a agência da ONU para refugiados). \"Fora do país\" soma refugiados e solicitantes de " +
            "refúgio; \"deslocados\" são os que fugiram mas continuam dentro do país. O ano mais recente costuma ser parcial " +
            "(meio do ano). Palestinos ficam sob outra agência (UNRWA) e não entram aqui.",
        collapsedSummary = list.take(3).joinToString(" · ") { c -> "${c.name} ${bigNumber(c.latest!!.let { c.abroad(it) + it.idps })}" },
        startExpanded = false,
    ) {
        list.forEach { c ->
            val y = c.latest!!
            val abroad = c.abroad(y)
            val prev = c.years.dropLast(1).lastOrNull()
            Column(Modifier.fillMaxWidth().clickable(enabled = c.tag != null) { c.tag?.let(onRegion) }.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.name} · ${y.year}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    prev?.let { p ->
                        val before = c.abroad(p) + p.idps
                        val now = abroad + y.idps
                        if (before > 0) {
                            val pct = (now - before) * 100.0 / before
                            Text(
                                (if (pct >= 0) "▲ " else "▼ ") + "%.0f%% vs ${p.year}".format(ptBR, kotlin.math.abs(pct)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pct > 0) Alert else Ash,
                            )
                        }
                    }
                }
                Text(
                    listOfNotNull(
                        abroad.takeIf { it > 0 }?.let { "${bigNumber(it)} fora do país" },
                        y.idps.takeIf { it > 0 }?.let { "${bigNumber(it)} deslocados dentro" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (c.hosts.isNotEmpty()) {
                    Text(
                        "Para onde foram: " + c.hosts.take(4).joinToString(", ") { "${countryPt(it.name)} (${bigNumber(it.people)})" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val series = c.years.map { (c.abroad(it) + it.idps).toDouble() }
                if (series.size >= 3) Sparkline(series, Accent, Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

/** Fome (fases do IPC) e preço dos alimentos nas zonas de conflito (HDX HAPI). */
@Composable
fun HungerCard(radar: RadarData, onRegion: (String) -> Unit) {
    val section = radar.hunger ?: return
    val list = section.countries.sortedByDescending { it.ipc?.phase3plus ?: 0 }
    if (list.isEmpty()) return
    val famine = list.filter { (it.ipc?.phase5 ?: 0) > 0 }
    ArgosCard(
        "🌾 FOME NAS ZONAS DE GUERRA",
        alert = famine.isNotEmpty(),
        source = "IPC / PMA (HDX HAPI)",
        updated = section.updated,
        status = radar.status["hunger"],
        info = "Classificação IPC, usada pela ONU: fase 3 = crise, 4 = emergência, 5 = catástrofe/fome. Os números são " +
            "estimativas de cada análise, que cobre um período (mostrado embaixo). Os preços são a mediana dos mercados " +
            "monitorados pelo Programa Mundial de Alimentos, em moeda local; a variação inclui a inflação.",
        collapsedSummary = if (famine.isNotEmpty()) "fase 5 (catástrofe) em " + famine.joinToString(", ") { it.name }
        else list.take(2).joinToString(" · ") { "${it.name}: ${bigNumber(it.ipc?.phase3plus ?: 0)} em crise" },
        startExpanded = famine.isNotEmpty(),
    ) {
        list.forEach { c ->
            Column(Modifier.fillMaxWidth().clickable(enabled = c.tag != null) { c.tag?.let(onRegion) }.padding(vertical = 6.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                c.ipc?.let { ipc ->
                    Text(
                        buildString {
                            append("${bigNumber(ipc.phase3plus)} em crise ou pior")
                            ipc.fraction?.let { append(" (%.0f%% da população analisada)".format(ptBR, it * 100)) }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ipc.phase5 > 0) {
                        IconText("⚠ ${bigNumber(ipc.phase5)} em catástrofe (fase 5)", style = MaterialTheme.typography.bodySmall, color = Alert, fontWeight = FontWeight.Bold)
                    } else if (ipc.phase4 > 0) {
                        Text("${bigNumber(ipc.phase4)} em emergência (fase 4)", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Análise de ${monthYear(ipc.start)} a ${monthYear(ipc.end)}" +
                            (ipc.prev?.takeIf { it.phase3plus > 0 }?.let { " · antes: ${bigNumber(it.phase3plus)} (${monthYear(it.start)})" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                c.prices.forEach { p ->
                    Row(Modifier.fillMaxWidth()) {
                        IconText("🛒 ${p.name}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text(
                            (if (p.change >= 0) "+" else "") + "%.0f%%".format(ptBR, p.change) + " desde ${monthYear(p.from)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (p.change >= 25) Alert else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Gás na Europa: estoques (AGSI+) e o gás russo que ainda entra pelo TurkStream (ENTSOG). */
@Composable
fun GasCard(radar: RadarData) {
    val section = radar.gas ?: return
    val st = section.storage
    val ru = section.russia
    if (st == null && ru == null) return
    ArgosCard(
        "⛽ GÁS NA EUROPA",
        source = "GIE AGSI+ · ENTSOG",
        updated = section.updated,
        status = radar.status["gas"],
        info = "A guerra na Ucrânia pela conta de luz. Estoque: quanto dos reservatórios de gás da União Europeia está cheio " +
            "(a meta é chegar ao inverno perto de 90%). Gás russo: o que entra na UE pelo TurkStream, a única rota russa por " +
            "gasoduto que sobrou depois que o trânsito pela Ucrânia acabou, em 1º de janeiro de 2025. A Europa ainda compra GNL russo, " +
            "que chega de navio e não aparece aqui.",
        collapsedSummary = listOfNotNull(
            st?.let { "estoque %.1f%%".format(ptBR, it.full) },
            ru?.let { "TurkStream %.0f GWh/dia".format(ptBR, it.gwh) },
        ).joinToString(" · "),
        startExpanded = false,
    ) {
        st?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Estoques da UE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "%.0f TWh em ${dayLabel(it.date)}".format(ptBR, it.twh) +
                            (it.lastYear?.let { ly -> " · há um ano: %.1f%%".format(ptBR, ly) } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("%.1f%%".format(ptBR, it.full), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Accent)
            }
            Sparkline(seriesValues(it.series), Accent, Modifier.fillMaxWidth().padding(vertical = 4.dp), baseline = it.lastYear)
            Text(
                if (it.trend >= 0) "Enchendo: +%.2f ponto por dia".format(ptBR, it.trend) else "Esvaziando: %.2f ponto por dia".format(ptBR, it.trend),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ru?.let {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gás russo por gasoduto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${it.point} · média de 30 dias: %.0f GWh/dia".format(ptBR, it.avg30),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("%.0f".format(ptBR, it.gwh), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Sparkline(seriesValues(it.series), Ash, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Text(
                "GWh por dia em ${dayLabel(it.date)} (≈ %.0f milhões de m³)".format(ptBR, it.gwh / 10.55),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Jornalistas mortos neste ano e no anterior (CPJ). */
@Composable
fun PressCard(radar: RadarData, onRegion: (String) -> Unit) {
    val section = radar.press ?: return
    if (section.recent.isEmpty()) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    ArgosCard(
        "📰 IMPRENSA SOB FOGO",
        source = "CPJ",
        updated = section.updated,
        status = radar.status["press"],
        info = "Jornalistas e profissionais de imprensa mortos, segundo o Comitê para a Proteção dos Jornalistas (CPJ), " +
            "que só inclui um caso depois de apurar. Os números podem subir com o tempo, conforme casos são confirmados.",
        collapsedSummary = "${section.killedThisYear} mortos em ${section.year} · ${section.killedLastYear} em ${section.year - 1}",
        startExpanded = false,
    ) {
        Text(
            "${section.killedThisYear} mortos em ${section.year} · ${section.killedLastYear} em ${section.year - 1}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        section.countries.take(6).forEach { c ->
            Row(Modifier.fillMaxWidth().clickable(enabled = c.tag != null) { c.tag?.let(onRegion) }.padding(vertical = 2.dp)) {
                Text(countryPt(c.country), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${c.thisYear} · ${c.lastYear}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "Neste ano · no ano passado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Mais recentes", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        section.recent.take(if (showAll) 15 else 5).forEach { v ->
            Column(Modifier.padding(vertical = 3.dp)) {
                Text(v.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    listOf(v.outlet, v.place.ifBlank { countryPt(v.country) }, v.date.takeIf { it.isNotBlank() }?.let { dayLabel(it) } ?: "")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (section.recent.size > 5) {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Mostrar menos" else "Mostrar mais") }
        }
    }
}
