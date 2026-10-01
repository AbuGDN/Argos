package com.abugdn.wid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.AttentionConflict
import com.abugdn.wid.data.ForgottenRow
import com.abugdn.wid.data.MILEX_FOCUS
import com.abugdn.wid.data.MILEX_FROM
import com.abugdn.wid.data.MILEX_GDP_TOP
import com.abugdn.wid.data.MILEX_TOP
import com.abugdn.wid.data.MILEX_WORLD_BN
import com.abugdn.wid.data.MILEX_YEAR
import com.abugdn.wid.data.MilexCountry
import com.abugdn.wid.data.NUCLEAR_AS_OF
import com.abugdn.wid.data.NUCLEAR_FORCES
import com.abugdn.wid.data.NuclearForce
import com.abugdn.wid.data.UCDP_VERSION
import com.abugdn.wid.data.WAR_DEATHS_YEARS
import com.abugdn.wid.data.WIKI_LANG_PT
import com.abugdn.wid.data.forgottenRows
import com.abugdn.wid.data.seriesValues
import com.abugdn.wid.repository
import java.util.Locale

// Telas de dados sobre as guerras: atenção do mundo e guerras esquecidas (Wikipédia + UCDP),
// gastos militares (SIPRI) e arsenais nucleares (FAS). Os dados fixos vêm de arquivos gerados por
// backend/tools/gerar_dados_fixos.py (WarDeaths.kt, MilitarySpending.kt, NuclearForces.kt).

private val ptBR = Locale("pt", "BR")

private fun dec(v: Double, digits: Int = 1): String = "%.${digits}f".format(ptBR, v).replace(Regex(",0+$"), "")

private fun signedPct(v: Double): String = (if (v > 0) "+" else "") + dec(v, 0) + "%"

/** Barra horizontal fina, proporcional a [fraction] (0..1). */
@Composable
private fun Bar(fraction: Double, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(color))
    }
}

@Composable
private fun Note(text: String, top: Int = 16) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top.dp),
    )
}

@Composable
private fun Intro(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
}

// ---------------------------------------------------------------------------------------------
// Atenção do mundo e guerras esquecidas
// ---------------------------------------------------------------------------------------------

@Composable
fun AttentionScreen(initialTab: Int, onBack: () -> Unit, onRegion: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    val attention = radar?.attention
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val forgotten = remember(attention) { forgottenRows(attention) }
    SimpleScaffold("Atenção do mundo", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("O que o mundo lê") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Guerras esquecidas") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
                if (tab == 0) {
                    item {
                        Intro(
                            if (attention == null) "Ainda sem dados: a primeira coleta da Wikipédia roda em até 12 horas."
                            else "Visitas de pessoas aos artigos da Wikipédia sobre cada guerra nos últimos 7 dias, somando " +
                                "${attention.langs.size} idiomas. A linha mostra os 30 dias de ${dayLabel(attention.start)} a ${dayLabel(attention.end)}.",
                        )
                    }
                    val list = attention?.conflicts.orEmpty()
                    val max = list.maxOfOrNull { it.views7d }?.coerceAtLeast(1) ?: 1
                    items(list, key = { "a-" + it.id }) { AttentionRow(it, max, onRegion) }
                    if (attention != null) {
                        item {
                            Note(
                                "Fonte: Wikimedia (visitas de pessoas, sem robôs), idiomas: " +
                                    attention.langs.joinToString(", ") { WIKI_LANG_PT[it] ?: it } + ". " +
                                    "Atenção não é importância: um artigo pode ser lido por curiosidade, estudo ou propaganda. " +
                                    "Atualizado ${dayLabel(attention.updated.take(10))}.",
                            )
                        }
                    }
                } else {
                    item {
                        Intro(
                            "Cada guerra com a sua parte das mortes em combate de ${WAR_DEATHS_YEARS.last()} e a sua parte da " +
                                "atenção (visitas na Wikipédia nos últimos 30 dias), entre as ${forgotten.size} guerras acompanhadas. " +
                                "No topo, as que matam muito e quase ninguém lê.",
                        )
                    }
                    items(forgotten, key = { "f-" + it.war.id }) { ForgottenItem(it, onRegion) }
                    item {
                        Note(
                            "Mortes: UCDP (Programa de Dados de Conflitos de Uppsala), versão $UCDP_VERSION, estimativa central de " +
                                "mortes em combate entre forças armadas (governos e grupos). Não inclui civis mortos fora de combate " +
                                "nem mortes por fome e doença, por isso fica abaixo de outras contagens (como a do Ministério da Saúde de Gaza).",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(c: AttentionConflict, max: Long, onRegion: (String) -> Unit) {
    val values = remember(c.series) { seriesValues(c.series) }
    Column(
        Modifier.fillMaxWidth().let { m -> c.tag?.let { t -> m.clickable { onRegion(t) } } ?: m }.padding(vertical = 8.dp),
    ) {
        Row {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(bigNumber(c.views7d), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Bar(c.views7d.toDouble() / max, Accent, Modifier.padding(vertical = 4.dp))
        Row {
            val change = c.change
            Text(
                if (change == null) "visitas em 7 dias" else "visitas em 7 dias · ${signedPct(change)} sobre a semana anterior",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Sparkline(values, Accent, Modifier.fillMaxWidth().padding(top = 4.dp))
        val total = c.byLang.values.sum().coerceAtLeast(1)
        Text(
            c.byLang.entries.take(3).joinToString(" · ") { (lang, v) -> "${WIKI_LANG_PT[lang] ?: lang} ${v * 100 / total}%" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ForgottenItem(r: ForgottenRow, onRegion: (String) -> Unit) {
    val w = r.war
    Column(Modifier.fillMaxWidth().let { m -> w.tag?.let { t -> m.clickable { onRegion(t) } } ?: m }.padding(vertical = 8.dp)) {
        Text(w.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            "Mortes em combate em ${WAR_DEATHS_YEARS.last()}: ${bigNumber(w.latest.toLong())} (${dec(r.deathShare)}% do total)",
            style = MaterialTheme.typography.labelSmall,
        )
        Bar(r.deathShare / 100, Ash, Modifier.padding(vertical = 3.dp))
        val att = r.attentionShare
        Text(
            if (att == null) "Atenção: sem dados da Wikipédia ainda"
            else "Atenção: ${bigNumber(r.views ?: 0)} visitas em 30 dias (${dec(att)}% do total)",
            style = MaterialTheme.typography.labelSmall,
        )
        if (att != null) Bar(att / 100, Accent, Modifier.padding(vertical = 3.dp))
        if (att != null && r.deathShare > 0) {
            val ratio = att / r.deathShare
            Text(
                when {
                    ratio < 0.5 -> "Recebe ${dec(1 / ratio)}× menos atenção do que a sua parte das mortes."
                    ratio > 2 -> "Recebe ${dec(ratio)}× mais atenção do que a sua parte das mortes."
                    else -> "Atenção parecida com a sua parte das mortes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ratio < 0.5) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Mortes por ano: " + WAR_DEATHS_YEARS.zip(w.deaths).joinToString(" · ") { (y, d) -> "$y: ${bigNumber(d.toLong())}" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

// ---------------------------------------------------------------------------------------------
// Gastos militares
// ---------------------------------------------------------------------------------------------

private fun usd(bn: Double?): String = when {
    bn == null -> "sem dado"
    bn >= 1 -> "US$ ${dec(bn)} bi"
    else -> "US$ ${dec(bn * 1000, 0)} mi"
}

@Composable
fun MilexScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SimpleScaffold("Gastos militares", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Quem mais gasta") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("% do PIB") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Guerras e Brasil") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
                when (tab) {
                    0 -> {
                        item { Intro("Os 15 países que mais gastaram com as forças armadas em $MILEX_YEAR. O mundo somou cerca de ${usd(MILEX_WORLD_BN)}.") }
                        val max = MILEX_TOP.firstOrNull()?.usdBn ?: 1.0
                        items(MILEX_TOP, key = { "t-" + it.country }) { MilexRow(it, (it.usdBn ?: 0.0) / max, usd(it.usdBn)) }
                    }
                    1 -> {
                        item { Intro("Os 15 países que mais gastaram com as forças armadas em relação ao tamanho da economia, em $MILEX_YEAR.") }
                        val max = MILEX_GDP_TOP.firstOrNull()?.gdpPct ?: 1.0
                        items(MILEX_GDP_TOP, key = { "g-" + it.country }) { MilexRow(it, (it.gdpPct ?: 0.0) / max, (it.gdpPct?.let { g -> dec(g) + "% do PIB" } ?: "sem dado")) }
                    }
                    else -> {
                        item { Intro("Países em guerra ou na vizinhança delas, e o Brasil. A linha mostra o gasto de $MILEX_FROM a $MILEX_YEAR, já descontada a inflação.") }
                        items(MILEX_FOCUS, key = { "f-" + it.country }) { c ->
                            MilexRow(c, null, usd(c.usdBn))
                            Sparkline(c.series, Accent, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                        }
                    }
                }
                item {
                    Note(
                        "Fonte: SIPRI (Instituto Internacional de Pesquisa para a Paz de Estocolmo), base de gastos militares de 1949 a $MILEX_YEAR. " +
                            "Valores em dólares do próprio ano; a variação em 10 anos desconta a inflação (dólares de 2024). Parte dos números " +
                            "é estimativa do SIPRI, sobretudo de países que não publicam o orçamento militar.",
                    )
                }
            }
        }
    }
}

@Composable
private fun MilexRow(c: MilexCountry, fraction: Double?, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text("${c.flag} ${c.country}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        if (fraction != null) Bar(fraction, Accent, Modifier.padding(vertical = 3.dp))
        Text(
            listOfNotNull(
                c.gdpPct?.let { "${dec(it)}% do PIB" },
                c.usdBn?.let { usd(it) },
                c.change10y?.let { "${signedPct(it)} em 10 anos" },
            ).distinct().joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Arsenais nucleares
// ---------------------------------------------------------------------------------------------

@Composable
fun NuclearScreen(onBack: () -> Unit) {
    val total = NUCLEAR_FORCES.sumOf { it.total ?: 0 }
    val max = NUCLEAR_FORCES.maxOfOrNull { it.total ?: 0 }?.coerceAtLeast(1) ?: 1
    SimpleScaffold("Arsenais nucleares", onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
            item {
                Intro(
                    "${NUCLEAR_FORCES.size} países têm cerca de ${bigNumber(total.toLong())} ogivas nucleares ($NUCLEAR_AS_OF). " +
                        "Instaladas = prontas em mísseis e bases; estoque = as que podem ser usadas; o resto são aposentadas à espera de desmonte.",
                )
            }
            items(NUCLEAR_FORCES, key = { "n-" + it.country }) { NuclearRow(it, max) }
            item {
                Note(
                    "Fonte: Federation of American Scientists (FAS), Status of World Nuclear Forces, $NUCLEAR_AS_OF. São estimativas: " +
                        "só EUA, Reino Unido e França publicam parte dos números, e Israel nunca confirmou ter a bomba.",
                )
            }
        }
    }
}

@Composable
private fun NuclearRow(n: NuclearForce, max: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row {
            Text("${n.flag} ${n.country}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(n.total?.let { bigNumber(it.toLong()) } ?: "?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Bar((n.total ?: 0).toDouble() / max, Accent, Modifier.padding(vertical = 4.dp))
        val deployed = listOfNotNull(n.deployedStrategic, n.deployedNonstrategic).sum()
        val retired = if (n.total != null && n.stockpile != null) n.total - n.stockpile else null
        Text(
            listOfNotNull(
                "instaladas ${bigNumber(deployed.toLong())}",
                n.stockpile?.let { "estoque ${bigNumber(it.toLong())}" },
                retired?.takeIf { it > 0 }?.let { "aposentadas ${bigNumber(it.toLong())}" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}
