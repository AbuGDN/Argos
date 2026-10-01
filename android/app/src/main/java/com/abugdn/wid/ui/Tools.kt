package com.abugdn.wid.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.TRUCES
import com.abugdn.wid.data.normalize
import com.abugdn.wid.data.upcomingAgenda
import com.abugdn.wid.repository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Tudo o que o Argos faz, num lugar só. [route] é o mesmo endereço usado pelos atalhos do ícone:
 * "clock", "story", "vigil", "bulletin", "tools", "settings", "radar:N", "map", "map:trend",
 * "map:frontline", "map:carriers", "map:military", "map:ranges", "library:N" e "region:TAG".
 */
data class Tool(val route: String, val icon: String, val name: String, val desc: String, val keywords: String = "")

val TOOL_GROUPS: List<Pair<String, List<Tool>>> = listOf(
    "Agora" to listOf(
        Tool("clock", "👁", "Relógio do Argos", "Tensão global e a região que mais puxa", "tensao indice global"),
        Tool("radar:0", "📡", "Sensores do Radar", "Internet, espaço aéreo, focos de calor, navios, aviões militares, sirenes e sismos", "apagao internet aviao navio fogo terremoto sismo nuclear"),
        Tool("vigil", "📜", "Vigília", "Registro dos alertas com data e hora", "alertas registro historico"),
        Tool("sirens", "🚨", "Sirenes em Israel", "Alertas de foguete e drone ao vivo, no mapa", "sirene alerta foguete tzeva adom"),
        Tool("deadlines", "⏳", "Ultimatos e prazos", "Contagem regressiva dos prazos dados nas manchetes", "ultimato prazo contagem"),
        Tool("diplomacy", "🌡", "Termômetro diplomático", "Embaixadores, sanções, fronteiras e negociações de cada região em 7 dias", "diplomacia embaixador sancoes negociacao acordo"),
        Tool("contradictions", "⚖", "Contradições ao vivo", "Números, palavras e versões que não batem entre os veículos", "contradicao divergente numeros versoes conflito"),
    ),
    "Resumos" to listOf(
        Tool("story", "▶", "O dia em 1 minuto", "As 5 principais em tela cheia", "resumo stories"),
        Tool("bulletin", "🗞", "Boletim semanal", "As principais da semana em imagem para compartilhar", "semana imagem"),
        Tool("monthly", "🗓", "Boletim mensal", "Os últimos 30 dias em imagem: Relógio, principais e regiões mais tensas", "mes mensal imagem resumo"),
        Tool("library:3", "📅", "Arquivo e Sua semana", "A principal de cada dia, suas leituras e quem noticia primeiro", "historico dias ranking primeiro"),
    ),
    "Mapas" to listOf(
        Tool("map", "🗺", "Mapa", "Histórias das últimas 48 h por região e por cidade", "cidades regioes"),
        Tool("map:trend", "📊", "Tendência", "Histórias por dia em cada região, 14 dias", "grafico evolucao"),
        Tool("map:frontline", "🇺🇦", "Linha de frente", "Área ocupada na Ucrânia, desenhada no mapa", "ucrania russia frente deepstate"),
        Tool("map:carriers", "⚓", "Porta-aviões", "Onde está cada porta-aviões americano", "frota marinha eua navios"),
        Tool("map:military", "✈", "Aviões militares", "Reabastecedores, aviões-radar e drones no ar", "militar avioes adsb"),
        Tool("map:ranges", "🎯", "Alcance de mísseis", "Até onde chegam mísseis e defesas", "misseis defesa alcance armas"),
        Tool("map:bases", "🪖", "Bases militares", "Bases estrangeiras de EUA, Rússia, China, França, Reino Unido e Turquia", "base militar eua russia china tropas exterior"),
        Tool("satellite", "🛰", "Antes e depois por satélite", "Imagens da NASA de uma cidade em duas datas, com controle deslizante", "satelite imagem nasa antes depois destruicao comparar"),
        Tool("compare", "⚖", "Comparar", "Duas regiões lado a lado, ou a mesma região em duas datas", "comparar regioes datas antes depois"),
        Tool("scale", "📏", "E se fosse no Brasil?", "Gaza, a frente na Ucrânia ou um míssil em cima da sua cidade", "escala tamanho comparar cidade"),
    ),
    "Radar" to listOf(
        Tool("radar:1", "🛢", "Mercados", "Petróleo, ouro, moedas e apostas sobre as guerras", "cotacao petroleo brent ouro polymarket"),
        Tool("radar:2", "🩸", "Números", "Gaza, deslocados, reféns e perdas russas", "mortos refens humanitario perdas"),
        Tool("radar:2", "🇺🇦", "Placar aéreo da Ucrânia", "Drones e mísseis lançados por noite e quantos foram abatidos", "drones misseis ucrania abatidos noite"),
        Tool("radar:2", "🌾", "Fome nas zonas de guerra", "Pessoas em crise alimentar (IPC) e preço dos alimentos", "fome ipc comida alimentos precos crise"),
        Tool("radar:2", "🏚", "Deslocados e refugiados", "Quantos fugiram de cada país e para onde foram (ACNUR)", "refugiados deslocados acnur fuga migracao"),
        Tool("attention:0", "👀", "Atenção do mundo", "Quantas pessoas leem sobre cada guerra na Wikipédia, em 8 idiomas", "atencao wikipedia leitura interesse visitas audiencia"),
        Tool("attention:1", "🕯", "Guerras esquecidas", "As guerras que mais matam e menos aparecem: mortes × atenção", "esquecidas esquecida mortes atencao sudao congo sahel mianmar ucdp"),
        Tool("radar:2", "📰", "Imprensa sob fogo", "Jornalistas mortos neste ano, por país (CPJ)", "jornalistas mortos imprensa cpj"),
        Tool("radar:1", "⛽", "Gás na Europa", "Estoques de gás da UE e o gás russo que ainda chega", "gas energia europa estoque russia turkstream"),
        Tool("radar:3", "🇺🇳", "Conselho de Segurança da ONU", "Reuniões, resoluções aprovadas, vetos e votações previstas", "onu veto resolucao conselho seguranca"),
        Tool("radar:0", "🌦", "Tempo nas zonas de conflito", "Vento, chuva, neve e tempestade de areia no front", "clima tempo chuva vento areia neve"),
        Tool("radar:3", "🏛", "Vozes", "O que governos dizem nos próprios canais e sanções", "oficial governo sancoes"),
        Tool("radar:3", "🧠", "Análise", "CrisisWatch, checagens e institutos de análise", "crisiswatch checagem isw fatos"),
        Tool("radar:2", "📅", "Contexto", "Alertas de viagem, agenda, neste dia, hora nas capitais e quem manda", "agenda datas capitais horario lideres viagem turismo"),
    ),
    "Aprender" to listOf(
        Tool("course", "🎓", "Curso rápido", "Lições curtas para entender cada guerra", "aprender curso licao entender historia"),
        Tool("arms", "🔫", "Quem arma quem", "Quem vende e quem compra armas, e de onde vêm as armas das guerras (SIPRI)", "armas exportacao importacao sipri fornecedor"),
        Tool("milex", "💰", "Gastos militares", "Quanto cada país gasta com as forças armadas, em dólares e em % do PIB (SIPRI)", "gastos militares orcamento defesa pib dinheiro sipri"),
        Tool("nuclear", "☢", "Arsenais nucleares", "Quantas ogivas cada país tem, quantas estão instaladas e quantas guardadas (FAS)", "nuclear ogivas bomba atomica arsenal armas nucleares"),
        Tool("alliances", "🕸", "Quem apoia quem", "Rede de alianças, apoios e rivalidades entre países e grupos", "aliancas aliados apoio rivais rede grupos"),
        Tool("method", "🔬", "Como sabemos?", "Como o Argos junta notícias, mede confiança e calcula a tensão", "metodologia confianca calculo transparencia fontes"),
    ),
    "Checar" to listOf(
        Tool("verify", "🔍", "Verificar imagem", "Busca reversa e metadados: a foto é mesmo de hoje?", "foto imagem falsa antiga checar busca reversa lens"),
        Tool("sanctions", "🚫", "Quem está sancionado?", "Busque pessoa, empresa ou navio na lista de sanções do mundo", "sancoes sancionado ofac lista navio empresa"),
    ),
    "Seus" to listOf(
        Tool("library:0", "★", "Salvos", "Notícias guardadas, com pasta e nota", "favoritos estrela"),
        Tool("library:1", "🗂", "Dossiês", "Linha do tempo automática de um assunto", "acompanhar assunto"),
        Tool("library:2", "🎯", "Minhas previsões", "Seus palpites e o placar", "palpite aposta"),
        Tool("library:4", "👁", "Lidas", "O que você leu nos últimos 30 dias", "historico leituras"),
        Tool("rules", "🔔", "Regras de alerta", "Avisos do seu jeito: se isso e aquilo, me avise", "regra alerta aviso condicao notificacao"),
        Tool("sources", "🟢", "Status das fontes", "Quais veículos e sensores estão funcionando agora", "status fontes funcionando fora do ar erro"),
        Tool("settings", "⚙", "Ajustes", "Notificações, tela Hoje, leitura, aparência", "configuracoes preferencias"),
    ),
)

/** Ferramentas cujo nome, descrição ou palavras-chave contêm todos os termos da busca. */
fun toolsMatching(query: String): List<Tool> {
    val terms = normalize(query).split(Regex("\\s+")).filter { it.length >= 3 }
    if (terms.isEmpty()) return emptyList()
    return TOOL_GROUPS.flatMap { it.second }.filter { t ->
        val text = normalize("${t.name} ${t.desc} ${t.keywords}")
        terms.all { it in text }
    }
}

@Composable
fun ToolRow(tool: Tool, onRoute: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onRoute(tool.route) }.padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = emojiIcon(tool.icon)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.width(40.dp).padding(end = 16.dp))
        } else {
            Text(tool.icon, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tool.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(tool.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(onBack: () -> Unit, onRoute: (String) -> Unit) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Ferramentas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        val context = LocalContext.current
        val usage = remember { ToolUsage(context) }
        var query by rememberSaveable { mutableStateOf("") }
        var collapsed by remember { mutableStateOf(usage.collapsed()) }
        val favorites = remember { usage.mostUsed() }
        val open: (Tool) -> Unit = { t -> usage.count(t); onRoute(t.route) }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar ferramenta (ex.: sirene, nuclear, mapa)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpar busca") } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 8.dp),
                )
            }
            if (query.isNotBlank()) {
                val found = toolsMatching(query)
                if (found.isEmpty()) {
                    item { Text("Nenhuma ferramenta com esse nome.", modifier = Modifier.padding(16.dp)) }
                }
                items(found, key = { "q|" + it.route + "|" + it.name }) { ToolRow(it) { _ -> open(it) } }
                return@LazyColumn
            }
            if (favorites.isNotEmpty()) {
                item { GroupHeader("Mais usadas por você") }
                items(favorites, key = { "fav|" + it.route + "|" + it.name }) { ToolRow(it) { _ -> open(it) } }
            }
            TOOL_GROUPS.forEach { (group, tools) ->
                val isCollapsed = group in collapsed
                item(key = "grupo|$group") {
                    GroupHeader(group, count = tools.size, collapsed = isCollapsed) {
                        collapsed = if (isCollapsed) collapsed - group else collapsed + group
                        usage.setCollapsed(collapsed)
                    }
                }
                if (!isCollapsed) items(tools, key = { it.route + "|" + it.name }) { ToolRow(it) { _ -> open(it) } }
            }
            item { GroupHeader("Regiões") }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAG_LABELS.forEach { (tag, label) ->
                        AssistChip(
                            onClick = { onRoute("region:$tag") },
                            label = { Text(label) },
                            leadingIcon = { RegionFlags(tag, height = 14.dp) },
                        )
                    }
                }
            }
        }
    }
}

/** Título de grupo; com [onToggle], toca para recolher/abrir e mostra quantas ferramentas tem. */
@Composable
private fun GroupHeader(text: String, count: Int? = null, collapsed: Boolean = false, onToggle: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().let { if (onToggle != null) it.clickable(onClick = onToggle) else it }) {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (onToggle != null) {
                Text(
                    (if (collapsed) "${count ?: ""} ▸" else "▾").trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * O que a pessoa abre nas Ferramentas (para "Mais usadas por você") e os grupos que ela recolheu.
 * Fica só no celular, em SharedPreferences.
 */
private class ToolUsage(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("argos_tools", android.content.Context.MODE_PRIVATE)
    private fun id(t: Tool) = t.route + "|" + t.name

    fun count(t: Tool) {
        prefs.edit().putInt("uso_" + id(t), prefs.getInt("uso_" + id(t), 0) + 1).apply()
    }

    /** Até 4 ferramentas abertas pelo menos 2 vezes, da mais usada para a menos. */
    fun mostUsed(): List<Tool> = TOOL_GROUPS.flatMap { it.second }.distinctBy { id(it) }
        .map { it to prefs.getInt("uso_" + id(it), 0) }
        .filter { it.second >= 2 }
        .sortedByDescending { it.second }
        .take(4)
        .map { it.first }

    fun collapsed(): Set<String> = prefs.getStringSet("recolhidos", emptySet()).orEmpty()

    fun setCollapsed(groups: Set<String>) {
        prefs.edit().putStringSet("recolhidos", groups).apply()
    }
}

// ---------------------------------------------------------------------------
// Painel da tela Hoje: mini-cartões que rolam de lado
// ---------------------------------------------------------------------------

@Composable
fun HomePanel(order: List<String>, hidden: Set<String>, onRoute: (String) -> Unit, modifier: Modifier = Modifier) {
    val repo = LocalContext.current.repository
    val feed by repo.feed.collectAsStateWithLifecycle()
    val radar by repo.radar.collectAsStateWithLifecycle()
    val vigil by repo.vigil.collectAsStateWithLifecycle()
    val ended by repo.endedTruces.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val nextDate = remember(today) { upcomingAgenda(today, days = 60).firstOrNull() }
    val ids = order.filter { it !in hidden }
    if (ids.isEmpty()) return
    LazyRow(
        modifier.padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ids, key = { it }) { id ->
            when (id) {
                "clock" -> feed?.global?.let { clock ->
                    MiniCard(
                        "👁 RELÓGIO",
                        sub = "${clock.level} · ${TAG_LABELS[clock.leader] ?: clock.leader}",
                        alert = clock.level == "crítica",
                        onClick = { onRoute("clock") },
                    ) {
                        CountUpText(clock.index.toLong(), MaterialTheme.typography.headlineSmall, color = tensionColor(clock.level), fontWeight = FontWeight.Black)
                    }
                }
                "radar" -> {
                    val alerts = radar?.let(::radarAlerts).orEmpty()
                    MiniCard(
                        "📡 RADAR",
                        value = if (alerts.isEmpty()) "Calmo" else "${alerts.size} alerta" + if (alerts.size > 1) "s" else "",
                        sub = alerts.firstOrNull() ?: "sensores no normal",
                        alert = alerts.isNotEmpty(),
                        valueColor = if (alerts.isEmpty()) null else Alert,
                        onClick = { onRoute("radar:0") },
                    )
                }
                "story" -> MiniCard("▶ O DIA EM 1 MIN", value = "5 principais", sub = "em tela cheia", onClick = { onRoute("story") })
                "truce" -> TRUCES.firstOrNull { it.key !in ended }?.let { truce ->
                    val days = ChronoUnit.DAYS.between(truce.start, today) + 1
                    val recent = vigil.count { it.kind == "truce" && it.region in truce.tags && it.time >= System.currentTimeMillis() - 7 * 86_400_000L }
                    MiniCard(
                        "🕊 TRÉGUA",
                        value = "Dia $days",
                        sub = truce.label + if (recent > 0) " · $recent violação(ões) em 7 dias" else "",
                        alert = recent > 0,
                        onClick = { onRoute("region:${truce.tags.first()}") },
                    )
                }
                "agenda" -> nextDate?.let { e ->
                    val days = ChronoUnit.DAYS.between(today, e.date)
                    MiniCard(
                        "📅 AGENDA",
                        value = when (days) {
                            0L -> "Hoje"
                            1L -> "Amanhã"
                            else -> "Em $days dias"
                        },
                        sub = e.text,
                        valueColor = if (days <= 2) Alert else null,
                        onClick = { onRoute("radar:2") },
                    )
                }
                "vigil" -> {
                    val last = vigil.maxByOrNull { it.time }
                    MiniCard(
                        "📜 VIGÍLIA",
                        value = last?.let { relativeTime(java.time.Instant.ofEpochMilli(it.time).toString()) } ?: "—",
                        sub = last?.title ?: "nenhum alerta registrado ainda",
                        onClick = { onRoute("vigil") },
                    )
                }
                "tools" -> MiniCard("🧰 FERRAMENTAS", value = "Tudo do Argos", sub = "mapas, radar, resumos e regiões", onClick = { onRoute("tools") })
            }
        }
    }
}

@Composable
private fun MiniCard(
    label: String,
    value: String? = null,
    sub: String,
    alert: Boolean = false,
    valueColor: Color? = null,
    onClick: () -> Unit,
    valueContent: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.width(150.dp).height(104.dp).breathingBorder(alert).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (alert) Alert else Accent, fontWeight = FontWeight.Bold, maxLines = 1)
            if (valueContent != null) {
                valueContent()
            } else if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
