package com.abugdn.wid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.Deadline
import com.abugdn.wid.data.SirenEvent
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.countdown
import com.abugdn.wid.repository
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hms = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val hm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dayHm = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

private fun instantOf(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()

/**
 * Sirenes em Israel: alertas ativos agora (consultados a cada 5 s enquanto a tela está aberta),
 * mapa e lista das últimas 24 h e barras dos últimos 7 dias.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SirensScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    val section = radar?.sirens
    var live by remember { mutableStateOf<List<SirenEvent>>(emptyList()) }
    var checked by remember { mutableStateOf<Instant?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Só consulta com o app na frente (em segundo plano não gasta dados).
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(resumed) {
        while (resumed) {
            repo.liveSirens()
                .onSuccess { live = it; failed = false; checked = Instant.now() }
                .onFailure { failed = true }
            delay(5_000)
        }
    }
    val map = rememberSmallMap(GeoPoint(31.6, 34.9), 7.0, minZoom = 5.5)
    val recent = section?.events.orEmpty()

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Sirenes em Israel", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                ArgosCard(
                    if (live.isNotEmpty()) "🚨 SIRENES TOCANDO AGORA" else "AGORA",
                    alert = live.isNotEmpty(),
                    source = "Tzeva Adom",
                    info = "Alertas do Comando da Frente Interna de Israel, pelo Tzeva Adom (espelho público dos alertas oficiais). " +
                        "Com esta tela aberta, o Argos confere a cada 5 segundos. Sirene não quer dizer que algo caiu no lugar: " +
                        "ela avisa a área onde um foguete, míssil ou drone pode cair, e muitos são interceptados.",
                ) {
                    when {
                        live.isNotEmpty() -> live.forEach { e ->
                            Text(e.threat.replaceFirstChar { it.uppercase() }, color = Alert, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                            Text(e.cities.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                        }
                        failed && checked == null -> Text("Sem conexão com o serviço de alertas agora.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        else -> Text(
                            "Nenhuma sirene tocando" + (checked?.let { " · conferido às ${hms.format(it)}" } ?: " · conferindo…"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                SmallMap(map, Modifier.fillMaxWidth().height(320.dp).padding(top = 12.dp)) { m ->
                    m.overlays.removeAll { it is Polygon }
                    val density = m.context.resources.displayMetrics.density
                    fun dot(lat: Double, lon: Double, now: Boolean) = Polygon(m).apply {
                        setPoints(Polygon.pointsAsCircle(GeoPoint(lat, lon), if (now) 2500.0 else 1500.0))
                        fillPaint.color = if (now) 0xCCB3122E.toInt() else 0x66C9A227
                        outlinePaint.color = if (now) 0xFFB3122E.toInt() else 0xFFC9A227.toInt()
                        outlinePaint.strokeWidth = 1.5f * density
                        setOnClickListener { _, _, _ -> false }
                    }
                    recent.flatMap { it.cities }.forEach { c -> if (c.lat != null && c.lon != null) m.overlays.add(dot(c.lat, c.lon, false)) }
                    live.flatMap { it.cities }.forEach { c -> if (c.lat != null && c.lon != null) m.overlays.add(dot(c.lat, c.lon, true)) }
                }
            }
            if (section != null) item { SirenHoursCard(section.hours) }
            if (section != null && section.days.isNotEmpty()) {
                item {
                    ArgosCard("📊 LOCAIS COM SIRENE POR DIA · 7 DIAS", source = "Tzeva Adom", updated = section.updated) {
                        val max = section.days.maxOf { it.count }.coerceAtLeast(1)
                        Row(Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                            section.days.forEach { d ->
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${d.count}", style = MaterialTheme.typography.labelSmall)
                                    Box(
                                        Modifier.fillMaxWidth().height((60f * d.count / max).coerceAtLeast(2f).dp)
                                            .background(if (d.count > 0) Alert else Ash, RoundedCornerShape(3.dp)),
                                    )
                                    Text(d.date.takeLast(5).split("-").reversed().joinToString("/"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    if (recent.isEmpty()) "Nenhuma sirene nas últimas 24 h." else "ÚLTIMAS 24 H · ${section?.count24h ?: 0} locais",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(recent) { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        (instantOf(e.time)?.let { hm.format(it) } ?: "") + " · " + e.threat,
                        style = MaterialTheme.typography.labelSmall,
                        color = Alert,
                        fontWeight = FontWeight.Bold,
                    )
                    val names = e.cities.map { it.name }
                    Text(
                        names.take(10).joinToString(", ") + if (names.size > 10) " e mais ${names.size - 10}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Ultimatos e prazos achados nas manchetes, com contagem regressiva e "o que aconteceu depois". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val list by repo.deadlines.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    val (open, done) = list.partition { !it.expired(now) }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Ultimatos e prazos", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Text(
                    "Quando alguém dá um prazo numa manchete (“48 horas para…”, “duas semanas”), o Argos começa uma contagem. " +
                        "Quando vence, mostra a principal notícia da mesma região logo depois, para você ver o que aconteceu. " +
                        "O prazo conta a partir da primeira notícia, então pode ter algumas horas de diferença.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (list.isEmpty()) {
                item { Text("Nenhum ultimato nas manchetes das últimas semanas.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)) }
            }
            if (open.isNotEmpty()) item { SectionLabel("⏳ CORRENDO") }
            items(open, key = { it.id }) { d -> DeadlineCard(d, now, onOpen) }
            if (done.isNotEmpty()) item { SectionLabel("⌛ VENCIDOS") }
            items(done.sortedByDescending { it.due }, key = { it.id }) { d -> DeadlineCard(d, now, onOpen) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun DeadlineCard(d: Deadline, now: Instant, onOpen: (String) -> Unit) {
    val repo = LocalContext.current.repository
    val due = d.dueInstant()
    val expired = d.expired(now)
    val regions = d.tags.mapNotNull { TAG_LABELS[it] }.take(2).joinToString(" · ")
    ArgosCard(
        (if (expired) "VENCEU" else "PRAZO") + " · ${d.span.uppercase()}" + if (regions.isNotEmpty()) " · $regions" else "",
        alert = !expired && due != null && java.time.Duration.between(now, due).toHours() < 6,
        onClick = { onOpen(d.clusterId) },
    ) {
        Text(repo.translator.cached(d.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        if (due != null) {
            Text(
                countdown(due, now) + " · vence em " + dayHm.format(due),
                style = MaterialTheme.typography.labelMedium,
                color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else Alert,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            LinearProgressIndicator(
                progress = { d.progress(now) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = if (expired) Ash else Alert,
            )
        }
        d.after?.let { a ->
            Text("O que aconteceu depois:", style = MaterialTheme.typography.labelSmall, color = Accent, modifier = Modifier.padding(top = 10.dp))
            Text(
                repo.translator.cached(a.title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onOpen(a.clusterId) }.padding(vertical = 2.dp),
            )
        } ?: run {
            if (expired) {
                Text(
                    "Ainda sem notícia da região logo depois do prazo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
