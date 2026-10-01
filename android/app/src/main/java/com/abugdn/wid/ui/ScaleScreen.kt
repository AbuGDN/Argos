package com.abugdn.wid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abugdn.wid.data.RANGES
import com.abugdn.wid.repository
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Cidade brasileira de referência (área do município em km², IBGE, arredondada). */
private data class BrCity(val name: String, val lat: Double, val lon: Double, val km2: Int)

private val BR_CITIES = listOf(
    BrCity("São Paulo", -23.55, -46.63, 1521), BrCity("Rio de Janeiro", -22.91, -43.20, 1200),
    BrCity("Brasília (DF)", -15.79, -47.88, 5760), BrCity("Belo Horizonte", -19.92, -43.94, 331),
    BrCity("Salvador", -12.97, -38.50, 693), BrCity("Fortaleza", -3.73, -38.52, 313),
    BrCity("Recife", -8.05, -34.88, 218), BrCity("Porto Alegre", -30.03, -51.23, 495),
    BrCity("Curitiba", -25.43, -49.27, 435), BrCity("Manaus", -3.12, -60.02, 11401),
    BrCity("Belém", -1.46, -48.49, 1059), BrCity("Goiânia", -16.69, -49.26, 729),
    BrCity("Florianópolis", -27.60, -48.55, 674), BrCity("Vitória", -20.32, -40.34, 97),
    BrCity("Natal", -5.79, -35.21, 167), BrCity("João Pessoa", -7.12, -34.86, 210),
    BrCity("Maceió", -9.67, -35.74, 509), BrCity("Aracaju", -10.91, -37.07, 182),
    BrCity("Teresina", -5.09, -42.80, 1391), BrCity("São Luís", -2.53, -44.30, 583),
    BrCity("Cuiabá", -15.60, -56.10, 4327), BrCity("Campo Grande", -20.47, -54.62, 8082),
    BrCity("Porto Velho", -8.76, -63.90, 34091), BrCity("Rio Branco", -9.97, -67.81, 8835),
    BrCity("Macapá", 0.03, -51.07, 6563), BrCity("Boa Vista", 2.82, -60.67, 5687),
    BrCity("Palmas", -10.18, -48.33, 2219),
)

/** Contorno aproximado da Faixa de Gaza (lat, lon), para desenhar com o formato real. */
private val GAZA_OUTLINE = listOf(
    31.594 to 34.491, 31.500 to 34.400, 31.400 to 34.300, 31.320 to 34.218, 31.220 to 34.267,
    31.280 to 34.350, 31.380 to 34.430, 31.470 to 34.510, 31.530 to 34.560, 31.585 to 34.550,
)

/** O que comparar: área (desenhada como círculo de mesma área, exceto Gaza) ou alcance (raio). */
private data class ScaleShape(val id: String, val label: String, val km2: Double? = null, val radiusKm: Double? = null, val note: String = "")

private val fmt = NumberFormat.getIntegerInstance(Locale("pt", "BR"))
private val dec = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }

/** "E se fosse no Brasil?": áreas de guerra e alcances de mísseis por cima de uma cidade brasileira. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    val radar by repo.radar.collectAsStateWithLifecycle()
    val occupied = radar?.frontline?.occupiedKm2?.takeIf { it > 0 }?.toDouble() ?: 114_000.0
    val shapes = buildList {
        add(ScaleShape("gaza", "Faixa de Gaza", km2 = 365.0, note = "Desenhada com o formato real."))
        add(ScaleShape("cisjordania", "Cisjordânia", km2 = 5_655.0))
        add(ScaleShape("libano", "Líbano", km2 = 10_452.0))
        add(ScaleShape("israel", "Israel", km2 = 22_145.0))
        add(ScaleShape("ocupada", "Área ocupada na Ucrânia", km2 = occupied, note = "Pelo DeepStateMap, atualizado no Radar."))
        add(ScaleShape("ucrania", "Ucrânia inteira", km2 = 603_550.0))
        RANGES.filter { !it.defense }.forEach { add(ScaleShape("r:${it.id}", it.label, radiusKm = it.km.toDouble())) }
        RANGES.filter { it.defense }.forEach { add(ScaleShape("r:${it.id}", it.label, radiusKm = it.km.toDouble())) }
    }
    var cityIndex by rememberSaveable { mutableStateOf(0) }
    var shapeId by rememberSaveable { mutableStateOf("gaza") }
    var custom by rememberSaveable { mutableStateOf<Pair<Double, Double>?>(null) }
    val city = BR_CITIES[cityIndex]
    val shape = shapes.firstOrNull { it.id == shapeId } ?: shapes.first()
    val center = custom?.let { GeoPoint(it.first, it.second) } ?: GeoPoint(city.lat, city.lon)
    val map = rememberSmallMap(center, 9.0, minZoom = 3.0)
    LaunchedEffect(cityIndex, shapeId) {
        if (custom == null) map.controller.animateTo(GeoPoint(city.lat, city.lon), zoomFor(shape), 500L)
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("E se fosse no Brasil?", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text("CIDADE", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BR_CITIES.indices.toList()) { i ->
                        FilterChip(
                            selected = custom == null && i == cityIndex,
                            onClick = { custom = null; cityIndex = i },
                            label = { Text(BR_CITIES[i].name) },
                        )
                    }
                }
                Text("COMPARAR COM", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shapes, key = { it.id }) { s ->
                        FilterChip(selected = s.id == shape.id, onClick = { shapeId = s.id }, label = { Text(s.label) })
                    }
                }
            }
            item {
                SmallMap(map, Modifier.fillMaxWidth().height(380.dp).padding(top = 12.dp)) { m ->
                    m.overlays.removeAll { it is Polygon }
                    m.overlays.add(scalePolygon(m, shape, center))
                }
            }
            item {
                Column(Modifier.padding(16.dp, 8.dp)) {
                    OutlinedButton(onClick = {
                        val c = map.mapCenter
                        custom = c.latitude to c.longitude
                    }) { IconText("📍 Centralizar no meio do mapa") }
                    Text(
                        "Arraste o mapa até o lugar que quiser (sua cidade, seu bairro) e toque no botão.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val area = shape.km2
                    val text = if (area != null) {
                        val times = area / city.km2
                        "${shape.label}: ${fmt.format(area.toLong())} km². " +
                            (if (custom == null) {
                                if (times >= 1) "É ${dec.format(times)} vezes a área do município de ${city.name} (${fmt.format(city.km2)} km²)."
                                else "Cabe ${dec.format(1 / times)} vezes no município de ${city.name} (${fmt.format(city.km2)} km²)."
                            } else "") +
                            " Fora Gaza, o desenho é um círculo com a mesma área (o formato real é outro). " + shape.note
                    } else {
                        val r = shape.radiusKm ?: 0.0
                        "${shape.label}: alcance de ${fmt.format(r.toLong())} km em volta do ponto. " +
                            "É como se o lançador estivesse no centro de ${if (custom == null) city.name else "o ponto escolhido"}."
                    }
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

private fun zoomFor(s: ScaleShape): Double {
    val km = s.radiusKm ?: sqrt((s.km2 ?: 100.0) / PI)
    return when {
        km >= 1500 -> 3.4
        km >= 600 -> 4.4
        km >= 250 -> 5.4
        km >= 100 -> 6.4
        km >= 40 -> 7.6
        else -> 9.0
    }
}

private fun scalePolygon(m: org.osmdroid.views.MapView, s: ScaleShape, center: GeoPoint): Polygon = Polygon(m).apply {
    val points = if (s.id == "gaza") {
        val cLat = GAZA_OUTLINE.map { it.first }.average()
        val cLon = GAZA_OUTLINE.map { it.second }.average()
        // Mantém a forma: a diferença de longitude é corrigida pela latitude de destino.
        val k = cos(cLat * PI / 180) / cos(center.latitude * PI / 180)
        GAZA_OUTLINE.map { (lat, lon) -> GeoPoint(center.latitude + (lat - cLat), center.longitude + (lon - cLon) * k) }
    } else {
        val radiusKm = s.radiusKm ?: sqrt((s.km2 ?: 0.0) / PI)
        Polygon.pointsAsCircle(center, radiusKm * 1000.0)
    }
    setPoints(points)
    val red = 0xFFB3122E.toInt()
    outlinePaint.color = red
    outlinePaint.strokeWidth = 3f * m.context.resources.displayMetrics.density
    fillPaint.color = 0x44B3122E
    setOnClickListener { _, _, _ -> false }
}
