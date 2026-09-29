package com.abugdn.wid.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.StatsDay
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.data.Translator
import com.abugdn.wid.data.weekTop
import com.abugdn.wid.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Boletim mensal: os últimos 30 dias em uma imagem. */
data class MonthlyBulletin(
    val from: LocalDate,
    val to: LocalDate,
    val top: List<Cluster>,
    /** (data, pico do Relógio do Argos no dia). */
    val clock: List<Pair<String, Int>>,
    val tensest: List<Pair<String, Int>>,
    val stories: Int,
    val alerts: Int,
)

private const val MW = 1080
private const val MH = 1350
private const val MM = 60f
private val M_GOLD = 0xFFC9A227.toInt()
private val M_BLOOD = 0xFFB3122E.toInt()
private val M_BONE = 0xFFE8E2D0.toInt()
private val M_ASH = 0xFF8A8578.toInt()
private val M_INK = 0xFF050505.toInt()
private val monthName = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR"))
private val dm = DateTimeFormatter.ofPattern("dd/MM")

private fun mLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.08f)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

fun buildMonthly(days: List<com.abugdn.wid.data.HistoryDay>, stats: List<StatsDay>, vigilTimes: List<Long>, today: LocalDate): MonthlyBulletin {
    val from = today.minusDays(29)
    val inRange = { d: String -> runCatching { !LocalDate.parse(d).isBefore(from) }.getOrDefault(false) }
    val month = stats.filter { inRange(it.date) }
    val tensest = month.flatMap { it.tension.entries }.groupBy({ it.key }, { it.value })
        .mapValues { (_, v) -> v.max() }.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
    val zone = ZoneId.systemDefault()
    return MonthlyBulletin(
        from = from,
        to = today,
        top = weekTop(days.filter { inRange(it.date) }).map { it.top },
        clock = month.map { it.date to it.global },
        tensest = tensest,
        stories = month.sumOf { it.total },
        alerts = vigilTimes.count { !Instant.ofEpochMilli(it).atZone(zone).toLocalDate().isBefore(from) },
    )
}

/** Imagem 1080×1350 do boletim mensal: Relógio do Argos em 30 dias, principais e regiões mais tensas. */
fun drawMonthly(b: MonthlyBulletin, translator: Translator): Bitmap {
    val bitmap = Bitmap.createBitmap(MW, MH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(M_INK)
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = M_GOLD; style = Paint.Style.STROKE; strokeWidth = 3f }
    canvas.drawRect(24f, 24f, MW - 24f, MH - 24f, frame)
    drawArgosEye(canvas, 118f, 118f, 100f, M_GOLD)
    val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = M_GOLD; textSize = 58f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); letterSpacing = 0.25f
    }
    canvas.drawText("ARGOS", 196f, 112f, brand)
    val kicker = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = M_BONE; textSize = 30f; typeface = Typeface.MONOSPACE; letterSpacing = 0.12f }
    canvas.drawText("BOLETIM MENSAL · ${dm.format(b.from)} A ${dm.format(b.to)}", 198f, 158f, kicker)
    drawStamp(canvas, "CONFIDENCIAL", "", MW - 190f, 96f, -10f)
    val rule = Paint().apply { color = M_GOLD; strokeWidth = 2f }
    canvas.drawLine(MM, 210f, MW - MM, 210f, rule)

    val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = M_GOLD; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = M_BONE; textSize = 32f; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = M_ASH; textSize = 26f }

    // Relógio do Argos em 30 dias.
    var y = 262f
    canvas.drawText("RELÓGIO DO ARGOS · PICO DE CADA DIA", MM, y, label)
    val chartTop = y + 24f
    val chartH = 220f
    val values = b.clock.map { it.second }
    val axis = Paint().apply { color = 0xFF3A362C.toInt(); strokeWidth = 2f }
    canvas.drawLine(MM, chartTop + chartH, MW - MM, chartTop + chartH, axis)
    listOf(25, 50, 75).forEach { v ->
        val gy = chartTop + chartH - chartH * v / 100f
        canvas.drawLine(MM, gy, MW - MM, gy, axis.apply { strokeWidth = 1f })
    }
    if (values.size >= 2) {
        val step = (MW - MM * 2) / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = MM + i * step
            val py = chartTop + chartH - chartH * v.coerceIn(0, 100) / 100f
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = M_GOLD; style = Paint.Style.STROKE; strokeWidth = 5f })
        val peak = values.indices.maxByOrNull { values[it] } ?: 0
        val px = MM + peak * step
        val py = chartTop + chartH - chartH * values[peak].coerceIn(0, 100) / 100f
        canvas.drawCircle(px, py, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = M_BLOOD })
        val tag = "pico ${values[peak]} · ${runCatching { dm.format(LocalDate.parse(b.clock[peak].first)) }.getOrDefault("")}"
        canvas.drawText(tag, (px - 90f).coerceIn(MM, MW - MM - 260f), (py - 18f).coerceAtLeast(chartTop + 20f), small.apply { color = M_BLOOD })
        small.color = M_ASH
    } else {
        canvas.drawText("Ainda sem dias suficientes de histórico.", MM, chartTop + chartH / 2, small)
    }
    y = chartTop + chartH + 70f

    canvas.drawText("AS PRINCIPAIS DO MÊS", MM, y, label)
    y += 20f
    val number = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = M_GOLD; textSize = 40f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    b.top.take(5).forEachIndexed { i, c ->
        if (y > MH - 330f) return@forEachIndexed
        val l = mLayout(translator.display(c.title, c.lang), body, (MW - MM * 2 - 70).toInt(), 2)
        canvas.drawText("${i + 1}", MM, y + 40f, number)
        canvas.save(); canvas.translate(MM + 70, y + 6f); l.draw(canvas); canvas.restore()
        y += l.height + 16f
    }

    y = maxOf(y + 20f, MH - 300f)
    canvas.drawLine(MM, y, MW - MM, y, axis.apply { strokeWidth = 2f })
    y += 46f
    canvas.drawText("REGIÕES MAIS TENSAS (PICO)", MM, y, label)
    y += 46f
    val tense = b.tensest.joinToString("   ·   ") { (tag, v) -> "${TAG_LABELS[tag] ?: tag} $v" }
    canvas.drawText(TextUtils.ellipsize(tense.ifBlank { "sem registro" }, body, MW - MM * 2, TextUtils.TruncateAt.END).toString(), MM, y, body)

    val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = M_ASH; textSize = 28f; textAlign = Paint.Align.CENTER }
    canvas.drawText("${b.stories} histórias · ${b.alerts} alertas registrados em 30 dias", MW / 2f, MH - 62f, footer)
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.repository
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var top by remember { mutableStateOf<List<Cluster>>(emptyList()) }
    LaunchedEffect(Unit) {
        val days = repo.loadArchive(days = 31, publish = false).getOrNull() ?: repo.archive.value.orEmpty()
        val stats = repo.stats.value ?: repo.loadStats().getOrNull()
        val m = buildMonthly(days, stats?.days.orEmpty(), repo.vigil.value.map { it.time }, LocalDate.now())
        top = m.top
        bitmap = withContext(Dispatchers.Default) { drawMonthly(m, repo.translator) }
    }
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Boletim mensal", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = {
                    val image = bitmap
                    if (image != null) {
                        IconButton(onClick = {
                            scope.launch { shareBitmap(context, image, "argos-mes-${LocalDate.now()}", "Boletim mensal do Argos") }
                        }) { Icon(Icons.Filled.Share, contentDescription = "Compartilhar") }
                    }
                },
            )
        },
    ) { padding ->
        val image = bitmap
        if (image == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Image(image.asImageBitmap(), contentDescription = "Boletim mensal", modifier = Modifier.fillMaxWidth().aspectRatio(MW.toFloat() / MH))
                Text(
                    "Gerado com os últimos 30 dias do arquivo, das estatísticas e do seu registro de vigília (${monthName.format(LocalDate.now())}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                top.forEach { c -> TextButton(onClick = { onOpen(c.id) }) { Text("• " + repo.translator.display(c.title, c.lang)) } }
            }
        }
    }
}
