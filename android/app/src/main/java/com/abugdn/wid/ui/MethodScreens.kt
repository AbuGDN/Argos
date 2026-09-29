package com.abugdn.wid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.ORIGIN_LABELS
import com.abugdn.wid.repository

private val FIGURE_NAMES = mapOf("killed" to "Mortos", "injured" to "Feridos")

/** Contradições ao vivo: números que não batem, palavras diferentes e histórias de um lado só. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContradictionsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val feed by repo.feed.collectAsStateWithLifecycle()
    val clusters = feed?.clusters.orEmpty()
    val numbers = clusters.filter { c -> c.figures.values.any { it.divergent } }
    val words = clusters.filter { it.framing.isNotEmpty() && it !in numbers }
    val oneSide = clusters.filter { it.sides == "um_lado" && it !in numbers && it !in words }
    val sameWire = clusters.filter { c ->
        val w = c.wires
        w != null && c.sourcesCount >= 3 && w.independent == 1 && c !in numbers && c !in words && c !in oneSide
    }
    val translator = repo.translator

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Contradições ao vivo", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Text(
                    "Onde as versões não batem nas histórias das últimas 48 h. Divergência não quer dizer mentira: " +
                        "balanços sobem com o tempo e cada lado escolhe palavras. Confira a hora e a fonte de cada um.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (numbers.isEmpty() && words.isEmpty() && oneSide.isEmpty() && sameWire.isEmpty()) {
                item { Text("Nenhuma contradição nas histórias de agora.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (numbers.isNotEmpty()) item { Label("⚖ NÚMEROS QUE NÃO BATEM · ${numbers.size}") }
            items(numbers, key = { "n" + it.id }) { c ->
                ArgosCard(regionsOf(c), alert = true, onClick = { onOpen(c.id) }) {
                    Text(translator.display(c.title, c.lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    c.figures.filterValues { it.divergent }.forEach { (kind, info) ->
                        Text(
                            "${FIGURE_NAMES[kind] ?: kind}: " + info.bySource.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.value} (${it.key})" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Alert,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (words.isNotEmpty()) item { Label("🗣 MESMO FATO, PALAVRAS DIFERENTES · ${words.size}") }
            items(words, key = { "w" + it.id }) { c ->
                ArgosCard(regionsOf(c), onClick = { onOpen(c.id) }) {
                    Text(translator.display(c.title, c.lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    c.framing.forEach { g ->
                        g.byOrigin.forEach { (origin, labels) ->
                            Text(
                                "${ORIGIN_LABELS[origin] ?: origin}: ${labels.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            if (oneSide.isNotEmpty()) item { Label("⚠ SÓ UM LADO NOTICIOU · ${oneSide.size}") }
            items(oneSide, key = { "s" + it.id }) { c ->
                ArgosCard(regionsOf(c), onClick = { onOpen(c.id) }) {
                    Text(translator.display(c.title, c.lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    val origin = c.articles.firstOrNull()?.origin
                    Text(
                        "${c.sourcesCount} veículos, todos da ${(ORIGIN_LABELS[origin] ?: "mesma origem").lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (sameWire.isNotEmpty()) item { Label("🔁 MUITOS VEÍCULOS, UMA FONTE SÓ · ${sameWire.size}") }
            items(sameWire, key = { "r" + it.id }) { c ->
                ArgosCard(regionsOf(c), onClick = { onOpen(c.id) }) {
                    Text(translator.display(c.title, c.lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    c.wires?.agencies?.forEach { (agency, outlets) ->
                        Text("$agency ← ${outlets.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

private fun regionsOf(c: Cluster): String =
    c.tags.mapNotNull { com.abugdn.wid.data.TAG_LABELS[it] }.take(2).joinToString(" · ").uppercase().ifEmpty { "HISTÓRIA" } +
        " · ${c.sourcesCount} ${if (c.sourcesCount == 1) "VEÍCULO" else "VEÍCULOS"}"

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
}

/** Uma seção de "Como sabemos?". */
private data class MethodSection(val icon: String, val title: String, val body: List<String>)

private val METHOD = listOf(
    MethodSection(
        "📥", "De onde vêm as notícias",
        listOf(
            "A cada 30 minutos, o Argos lê os feeds de mais de 30 veículos: imprensa israelense (inclusive em hebraico), árabe " +
                "(inclusive em árabe), americana, internacional e brasileira. O que não está em português é traduzido no seu celular.",
            "Entra no feed o que tem pelo menos um termo de guerra (ataque, míssil, cessar-fogo...) e uma região, ou dois termos " +
                "de guerra diferentes. ICE entra mesmo sem termo de guerra; Brasil só entra junto de outra região.",
        ),
    ),
    MethodSection(
        "🧲", "Como as notícias viram uma história",
        listOf(
            "Notícias de veículos diferentes publicadas em até 18 horas e com palavras suficientes em comum viram uma história só. " +
                "Um dicionário traduz os termos principais do português, hebraico e árabe para comparar entre idiomas.",
            "O título da história é o do veículo de maior peso, de preferência em português. Histórias de dias diferentes sobre o " +
                "mesmo assunto viram capítulos de uma saga.",
        ),
    ),
    MethodSection(
        "🔎", "Nível de confiança",
        listOf(
            "Não diz se a notícia é verdadeira: diz quão apoiada ela está.",
            "Fontes independentes: veículos que citam a mesma agência (Reuters, AP, AFP, WAFA, IRNA, TASS...) contam como uma só. " +
                "É a árvore de fontes. A agência é achada no título e no resumo, então pode faltar quando o veículo não diz.",
            "Alta: 3 ou mais fontes independentes. Média: 2, ou várias de um lado só, ou apoiadas na versão oficial de uma das " +
                "partes (“segundo o exército”, “Hamas says”). Baixa: uma fonte. Conflitante: os números de mortos " +
                "ou feridos não batem entre os veículos.",
        ),
    ),
    MethodSection(
        "🤝", "Lados e palavras",
        listOf(
            "Cada veículo tem uma origem (israelense, árabe, americana, internacional, brasileira). Quando a imprensa árabe e a " +
                "israelense ou americana contam a mesma história, ela ganha o selo de lados opostos. Quando uma história grande " +
                "(3 ou mais veículos) só saiu de um lado, ganha o aviso de um lado só.",
            "Palavras de cada lado compara termos que costumam mudar conforme quem fala (terroristas × combatentes, " +
                "operação × ataque). Números divergentes lê mortos e feridos nos títulos e resumos; idades, anos e porcentagens " +
                "são ignorados.",
        ),
    ),
    MethodSection(
        "🌡", "Índice de tensão (0 a 100)",
        listOf(
            "Soma de cinco partes: volume de histórias nas últimas 24 h comparado com a média dos 14 dias anteriores da própria " +
                "região (até 40 pontos; o máximo vem com 3× o normal), palavras de escalada como mortos, míssil e invasão (até 25), " +
                "histórias urgentes (até 20), veículos por história (até 15) e sinais do Radar (apagão de internet +10, queda +5, " +
                "espaço aéreo fechado +10, reduzido +5).",
            "Níveis: baixa até 24, moderada até 49, alta até 74, crítica a partir de 75. Na página da região, “De onde vem a " +
                "tensão” mostra cada parte.",
            "Alta incomum: o ritmo das últimas 6 h, projetado para 24 h, passa de 3× o normal (com pelo menos 3 histórias).",
            "Relógio do Argos: 60% da região mais tensa mais 40% da média das 3 mais tensas. ICE e Brasil ficam de fora.",
        ),
    ),
    MethodSection(
        "📡", "Radar e sinais coincidentes",
        listOf(
            "Os sensores vêm de fontes abertas: IODA (internet), OpenSky (aviões), NASA FIRMS (focos de calor), IMF PortWatch " +
                "(navios), adsb.lol (aviões militares), USNI (porta-aviões), DeepStateMap (frente), Tzeva Adom (sirenes), USGS " +
                "(sismos) e Departamento de Estado dos EUA (viagem). Cada um compara com o próprio normal e guarda o último dado bom.",
            "Sinais coincidentes: quando dois tipos diferentes de sinal aparecem ao mesmo tempo na mesma região (por exemplo, " +
                "sirenes e espaço aéreo fechado), o Argos junta num incidente só.",
        ),
    ),
    MethodSection(
        "⚠", "Limites",
        listOf(
            "Tudo é feito por regras automáticas, sem inteligência artificial nem pessoas revisando. As regras erram: uma agência " +
                "pode não ser citada no resumo, um número pode ser de outra coisa, uma história pode juntar dois fatos parecidos.",
            "O Argos só vê os veículos que acompanha e só o título e o resumo de cada notícia. Na dúvida, abra as fontes na aba " +
                "Cobertura e leia o original.",
        ),
    ),
)

/** "Como sabemos?": a metodologia inteira do Argos em linguagem simples. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Como sabemos?", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Text(
                    "Como o Argos junta as notícias, calcula a tensão e decide o que é alerta. Sem caixa-preta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(METHOD) { s ->
                ArgosCard("${s.icon} ${s.title.uppercase()}", collapsedSummary = s.body.first().take(90) + "…", startExpanded = s == METHOD.first()) {
                    s.body.forEach { p -> Text(p, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
    }
}
