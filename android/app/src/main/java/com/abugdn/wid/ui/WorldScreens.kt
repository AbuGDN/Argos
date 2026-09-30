package com.abugdn.wid.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.abugdn.wid.data.ARMS_BUYERS
import com.abugdn.wid.data.ARMS_EXPORTERS
import com.abugdn.wid.data.ARMS_IMPORTERS
import com.abugdn.wid.data.ARMS_PERIOD
import com.abugdn.wid.data.ARMS_REPORTED
import com.abugdn.wid.data.ArmsShare
import com.abugdn.wid.data.CITIES
import com.abugdn.wid.data.SanctionEntry
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.normalize
import com.abugdn.wid.repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Quem está sancionado?
// ---------------------------------------------------------------------------------------------

@Composable
fun SanctionsScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    var attempt by remember { mutableIntStateOf(0) }
    // Carrega a lista (e já deixa os nomes normalizados) fora da thread principal.
    val loaded by produceState<Result<List<Pair<String, String>>>?>(null, attempt) {
        value = null
        value = repo.loadSanctions(force = attempt > 0).map { lines ->
            withContext(Dispatchers.Default) { lines.map { it to normalize(it.substringBefore('\t') + " " + it.substringAfterLast('\t')) } }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val results by produceState<List<SanctionEntry>>(emptyList(), loaded, query) {
        val list = loaded?.getOrNull()
        val terms = normalize(query).split(Regex("\\s+")).filter { it.length >= 2 }
        value = if (list == null || terms.isEmpty() || query.trim().length < 3) emptyList() else withContext(Dispatchers.Default) {
            list.asSequence().filter { (_, n) -> terms.all { it in n } }.take(80).mapNotNull { SanctionEntry.parse(it.first) }.toList()
        }
    }
    SimpleScaffold("Quem está sancionado?", onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Text(
                    "Digite o nome de uma pessoa, empresa, navio ou avião. A busca é feita no celular, na lista consolidada " +
                        "de sanções de EUA, União Europeia, Reino Unido, ONU e dezenas de outros governos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Nome (ex.: Rosneft, Petrov, Shahed)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                when {
                    loaded == null -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        Text("  Baixando a lista (uns 3 MB, só na primeira vez do dia)…", style = MaterialTheme.typography.bodySmall)
                    }
                    loaded?.isFailure == true -> Column(Modifier.padding(top = 16.dp)) {
                        Text("Não consegui baixar a lista de sanções agora.", color = Alert)
                        TextButton(onClick = { attempt++ }) { Text("Tentar de novo") }
                    }
                    else -> {
                        val total = loaded?.getOrNull()?.size ?: 0
                        Text(
                            "$total nomes na lista" + (radar?.sanctionlist?.sourceUpdated?.takeIf { it.isNotBlank() }?.let { " · atualizada em ${dayLabel(it.take(10))}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (query.trim().length >= 3 && results.isEmpty()) {
                            Text("Nenhum nome encontrado. Tente só o sobrenome ou a grafia em inglês.", modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
            }
            items(results) { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(e.kind, e.countries.uppercase().replace(",", ", ")).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (e.authorities.isNotBlank()) Text("Sancionado por: ${e.authorities}", style = MaterialTheme.typography.bodySmall, color = Accent)
                    if (e.since.isNotBlank()) Text("Na lista desde ${dayLabel(e.since)}", style = MaterialTheme.typography.labelSmall)
                    if (e.aliases.isNotBlank()) {
                        Text("Também: ${e.aliases}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    "Fonte: OpenSanctions (dados abertos, uso não comercial), atualizada todo dia. Nome igual não quer dizer " +
                        "a mesma pessoa: confira país, data e apelidos. \"Na lista desde\" é quando o OpenSanctions viu o nome pela primeira vez.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Quem arma quem
// ---------------------------------------------------------------------------------------------

@Composable
private fun ShareBar(s: ArmsShare, max: Double) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text("${s.flag} ${s.country}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("%.1f%%".format(java.util.Locale("pt", "BR"), s.share).replace(",0%", "%"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth((s.share / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(Accent))
        }
    }
}

@Composable
fun ArmsScreen(onBack: () -> Unit, onRegion: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SimpleScaffold("Quem arma quem", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Quem vende") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Quem compra") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("De quem") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
                when (tab) {
                    0 -> {
                        item { Text("Parte de cada país nas exportações mundiais de armas pesadas, $ARMS_PERIOD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(ARMS_EXPORTERS, key = { "e-" + it.country }) { ShareBar(it, ARMS_EXPORTERS.first().share) }
                        item { Text("Os 10 maiores somam mais de 90% do total. A fatia russa caiu pela metade em relação a 2015–2019.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                    }
                    1 -> {
                        item { Text("Parte de cada país nas importações mundiais de armas pesadas, $ARMS_PERIOD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(ARMS_IMPORTERS, key = { "i-" + it.country }) { ShareBar(it, ARMS_IMPORTERS.first().share) }
                    }
                    else -> {
                        item { Text("De onde vêm as armas de países em conflito ($ARMS_PERIOD, parte de cada fornecedor)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(ARMS_BUYERS, key = { "b-" + it.country }) { b ->
                            ArgosCard("${b.flag} ${b.country.uppercase()}", onClick = b.tag?.let { t -> { onRegion(t) } }) {
                                b.suppliers.forEach { ShareBar(it, 100.0) }
                                Text(b.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        item {
                            ArgosCard("📦 FORNECIMENTOS RELATADOS (FORA DO SIPRI)") {
                                ARMS_REPORTED.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp)) }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Fonte: SIPRI (Instituto Internacional de Pesquisa para a Paz de Estocolmo), fact sheet de março de 2025. " +
                            "Mede armas pesadas (aviões, navios, tanques, mísseis, artilharia) por um valor de tendência, não em dólares; " +
                            "munição leve e doações emergenciais aparecem só em parte. Números até 2024.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Verificar imagem
// ---------------------------------------------------------------------------------------------

private data class ExifInfo(val date: String?, val device: String?, val software: String?, val gps: Pair<Double, Double>?)

@Composable
fun VerifyImageScreen(initial: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Imagem compartilhada (content://) ou endereço de uma imagem na internet.
    var input by rememberSaveable { mutableStateOf(initial) }
    var urlField by rememberSaveable { mutableStateOf(if (initial.startsWith("http")) initial else "") }
    val isLocal = input.startsWith("content:") || input.startsWith("file:")
    val isWeb = input.startsWith("http")
    val exif by produceState<ExifInfo?>(null, input) {
        value = null
        if (!isLocal) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(input))?.use { stream ->
                    val e = android.media.ExifInterface(stream)
                    val ll = FloatArray(2)
                    ExifInfo(
                        date = e.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(android.media.ExifInterface.TAG_DATETIME),
                        device = listOfNotNull(e.getAttribute(android.media.ExifInterface.TAG_MAKE), e.getAttribute(android.media.ExifInterface.TAG_MODEL))
                            .joinToString(" ").ifBlank { null },
                        software = e.getAttribute(android.media.ExifInterface.TAG_SOFTWARE),
                        gps = if (e.getLatLong(ll)) ll[0].toDouble() to ll[1].toDouble() else null,
                    )
                }
            }.getOrNull()
        }
    }

    fun openLensWithImage() {
        val uri = Uri.parse(input)
        val send = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val lens = Intent(send).setPackage("com.google.ar.lens")
        val google = Intent(send).setPackage("com.google.android.googlequicksearchbox")
        for (i in listOf(lens, google)) {
            try {
                context.startActivity(i)
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Buscar esta imagem com…")) }
    }

    SimpleScaffold("Verificar imagem", onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                Text(
                    "Foto antiga circulando como se fosse de hoje é o golpe mais comum em guerra. Compartilhe uma foto de " +
                        "outro app para o Argos (menu Compartilhar) ou cole o endereço de uma imagem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = urlField,
                    onValueChange = { urlField = it.trim() },
                    label = { Text("Endereço da imagem (https://…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                TextButton(onClick = { if (urlField.startsWith("http")) input = urlField }, enabled = urlField.startsWith("http")) { Text("Verificar este endereço") }
            }
            if (input.isNotBlank()) {
                item {
                    AsyncImage(
                        model = input,
                        contentDescription = "Imagem a verificar",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                item {
                    ArgosCard("🔍 BUSCA REVERSA", info = "Procura a mesma foto na internet. A publicação mais antiga costuma revelar a origem real.") {
                        if (isWeb) {
                            val enc = Uri.encode(input)
                            listOf(
                                "Google Lens" to "https://lens.google.com/uploadbyurl?url=$enc",
                                "Yandex (bom para Rússia e Ucrânia)" to "https://yandex.com/images/search?rpt=imageview&url=$enc",
                                "TinEye (mostra a mais antiga)" to "https://tineye.com/search?url=$enc",
                                "Bing" to "https://www.bing.com/images/search?view=detailv2&iss=sbi&q=imgurl:$enc",
                            ).forEach { (label, url) ->
                                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Abrir no $label") }
                            }
                        } else {
                            OutlinedButton(onClick = { openLensWithImage() }, modifier = Modifier.fillMaxWidth()) { Text("Buscar no Google Lens") }
                            Text(
                                "Yandex e TinEye pedem que você envie o arquivo pelo site:",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Row {
                                TextButton(onClick = { runCatching { uriHandler.openUri("https://yandex.com/images/") } }) { Text("Yandex") }
                                TextButton(onClick = { runCatching { uriHandler.openUri("https://tineye.com/") } }) { Text("TinEye") }
                            }
                        }
                    }
                }
                if (isLocal) item {
                    ArgosCard("🧾 DADOS DO ARQUIVO", info = "Metadados (EXIF) gravados pela câmera. WhatsApp, Telegram e redes sociais apagam quase tudo; ausência de dados não prova nada.") {
                        val e = exif
                        if (e == null || (e.date == null && e.device == null && e.gps == null && e.software == null)) {
                            Text("Sem metadados: a imagem provavelmente passou por um app de mensagem ou rede social.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            e.date?.let { Text("📅 Tirada em: ${it.replaceFirst(':', '/').replaceFirst(':', '/')}", style = MaterialTheme.typography.bodyMedium) }
                            e.device?.let { Text("📱 Aparelho: $it", style = MaterialTheme.typography.bodyMedium) }
                            e.software?.let { Text("🖥 Editada com: $it", style = MaterialTheme.typography.bodyMedium) }
                            e.gps?.let { (lat, lon) ->
                                TextButton(onClick = { runCatching { uriHandler.openUri("https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=14/$lat/$lon") } }) {
                                    Text("📍 Local gravado: %.4f, %.4f (ver no mapa)".format(java.util.Locale.US, lat, lon))
                                }
                            }
                        }
                    }
                }
            }
            item {
                ArgosCard("✅ CHECKLIST RÁPIDO") {
                    listOf(
                        "A busca reversa achou a foto em data anterior ao fato? Então é antiga.",
                        "Clima, estação do ano e sombras batem com o dia e o lugar?",
                        "Placas, idioma, uniformes e carros são daquele país?",
                        "Quem publicou primeiro? Perfil novo ou anônimo pede cuidado.",
                        "Veículos de checagem já falaram disso? (Radar → Análise → Checagens)",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Antes e depois por satélite (NASA GIBS: HLS 30 m e VIIRS diário)
// ---------------------------------------------------------------------------------------------

private enum class SatLayer(val label: String, val layer: String, val note: String) {
    SENTINEL("Sentinel-2 (30 m)", "HLS_S30_Nadir_BRDF_Adjusted_Reflectance", "passa a cada 2–5 dias"),
    LANDSAT("Landsat (30 m)", "HLS_L30_Nadir_BRDF_Adjusted_Reflectance", "passa a cada 8 dias"),
    VIIRS("VIIRS (diário, 375 m)", "VIIRS_NOAA20_CorrectedReflectance_TrueColor", "todo dia, pouco detalhe"),
}

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun gibsUrl(layer: SatLayer, lat: Double, lon: Double, km: Double, date: LocalDate): String {
    val dLat = km / 2 / 111.0
    val dLon = km / 2 / (111.0 * kotlin.math.cos(Math.toRadians(lat)))
    val bbox = "%.4f,%.4f,%.4f,%.4f".format(java.util.Locale.US, lat - dLat, lon - dLon, lat + dLat, lon + dLon)
    return "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi?SERVICE=WMS&REQUEST=GetMap&VERSION=1.3.0" +
        "&LAYERS=${layer.layer}&CRS=EPSG:4326&BBOX=$bbox&WIDTH=900&HEIGHT=900&FORMAT=image/jpeg&TIME=$date"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteScreen(initialCity: String, onBack: () -> Unit) {
    var cityName by rememberSaveable { mutableStateOf(CITIES.firstOrNull { it.name == initialCity }?.name ?: "Cidade de Gaza") }
    val city = CITIES.firstOrNull { it.name == cityName } ?: CITIES.first()
    var km by rememberSaveable { mutableStateOf(20.0) }
    var layerIdx by rememberSaveable { mutableIntStateOf(0) }
    val layer = SatLayer.values()[layerIdx]
    val today = remember { LocalDate.now() }
    var after by rememberSaveable { mutableStateOf(today.minusDays(3).toString()) }
    var before by rememberSaveable { mutableStateOf(today.minusDays(33).toString()) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var picking by remember { mutableStateOf(false) }
    val a = LocalDate.parse(after)
    val b = LocalDate.parse(before)

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Fechar") } },
            title = { Text("Escolha o lugar") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    CITIES.groupBy { it.tag }.forEach { (tag, list) ->
                        item(key = "h-$tag") {
                            Text(TAG_LABELS[tag] ?: tag, style = MaterialTheme.typography.labelMedium, color = Accent, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(list) { c ->
                            Text(c.name, modifier = Modifier.fillMaxWidth().clickable { cityName = c.name; picking = false }.padding(vertical = 8.dp))
                        }
                    }
                }
            },
        )
    }

    SimpleScaffold("Antes e depois por satélite", onBack) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("📍 ${city.name} · trocar lugar") }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8.0 to "8 km", 20.0 to "20 km", 60.0 to "60 km", 250.0 to "250 km").forEach { (v, l) ->
                        FilterChip(selected = km == v, onClick = { km = v; if (v >= 250 && layerIdx != 2) layerIdx = 2 }, label = { Text(l) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SatLayer.values().forEachIndexed { i, l -> FilterChip(selected = layerIdx == i, onClick = { layerIdx = i }, label = { Text(l.label) }) }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                    AsyncImage(
                        model = gibsUrl(layer, city.lat, city.lon, km, b),
                        contentDescription = "Antes",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // "Depois" por cima, recortado até a posição do controle.
                    AsyncImage(
                        model = gibsUrl(layer, city.lat, city.lon, km, a),
                        contentDescription = "Depois",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().drawWithContent {
                            clipRect(left = size.width * split) { this@drawWithContent.drawContent() }
                        },
                    )
                    Box(Modifier.fillMaxSize().drawBehind {
                        val x = size.width * split
                        drawLine(Accent, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                    })
                    Text(
                        "ANTES ${b.format(dayFmt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Bone,
                        modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.6f)).padding(6.dp, 3.dp),
                    )
                    Text(
                        "DEPOIS ${a.format(dayFmt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent,
                        modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f)).padding(6.dp, 3.dp),
                    )
                }
                Slider(value = split, onValueChange = { split = it }, modifier = Modifier.fillMaxWidth())
                Text("Arraste: à esquerda da linha, antes; à direita, depois.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                DateStepper("Antes", b, onChange = { before = it.toString() }, max = a.minusDays(1))
                DateStepper("Depois", a, onChange = { after = it.toString() }, max = today)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7L to "1 semana", 30L to "1 mês", 90L to "3 meses", 365L to "1 ano").forEach { (d, l) ->
                        FilterChip(selected = b == a.minusDays(d), onClick = { before = a.minusDays(d).toString() }, label = { Text("Antes: $l antes") })
                    }
                }
            }
            item {
                Text(
                    "Imagens: NASA GIBS (${layer.label}, ${layer.note}). Se sair preto, vazio ou coberto de nuvem, o satélite não " +
                        "passou ou o tempo estava fechado naquele dia: mude um ou dois dias. As imagens de 30 m mostram bairros e " +
                        "grandes estragos, não prédios isolados; fumaça e queimadas aparecem melhor no VIIRS.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DateStepper(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, max: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        TextButton(onClick = { onChange(date.minusDays(7)) }) { Text("−7") }
        TextButton(onClick = { onChange(date.minusDays(1)) }) { Text("−1") }
        Text(date.format(dayFmt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (date.plusDays(1) <= max) onChange(date.plusDays(1)) }, enabled = date.plusDays(1) <= max) { Text("+1") }
        TextButton(onClick = { onChange(minOf(date.plusDays(7), max)) }, enabled = date < max) { Text("+7") }
    }
}
