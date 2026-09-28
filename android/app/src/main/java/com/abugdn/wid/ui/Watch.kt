package com.abugdn.wid.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abugdn.wid.data.Cluster
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

private val EyeCopper = Color(0xFFC8662B)

/**
 * O olho do Argos reage à tensão global: meio fechado e cinza quando está calmo, dourado e
 * aberto na moderada, cobre na alta, vermelho e pulsando na crítica. Pisca de vez em quando.
 */
@Composable
fun ArgosEyeIcon(level: String?, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    val color = when (level) {
        "crítica" -> Alert
        "alta" -> EyeCopper
        "moderada" -> Accent
        else -> Ash
    }
    val open = when (level) {
        "crítica", "alta" -> 1f
        "moderada" -> 0.8f
        else -> 0.5f
    }
    val blink = remember { Animatable(1f) }
    LaunchedEffect(level) {
        while (true) {
            delay(if (level == "crítica") 2_500 else 6_500)
            blink.animateTo(0.05f, tween(90))
            blink.animateTo(1f, tween(140))
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulso")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (level == "crítica") 0.35f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alfa",
    )
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = color.copy(alpha = alpha)
        val stroke = Stroke(width = w * 0.07f, join = StrokeJoin.Round)
        val tri = Path().apply {
            moveTo(w / 2, h * 0.06f)
            lineTo(w * 0.96f, h * 0.9f)
            lineTo(w * 0.04f, h * 0.9f)
            close()
        }
        drawPath(tri, c, style = stroke)
        val ey = h * 0.6f
        val ew = w * 0.24f
        val eh = ew * 0.85f * open * blink.value
        val eye = Path().apply {
            moveTo(w / 2 - ew, ey)
            quadraticBezierTo(w / 2, ey - eh, w / 2 + ew, ey)
            quadraticBezierTo(w / 2, ey + eh, w / 2 - ew, ey)
            close()
        }
        drawPath(eye, c, style = Stroke(width = w * 0.055f))
        if (open * blink.value > 0.3f) drawCircle(c, radius = ew * 0.36f * minOf(1f, open * blink.value + 0.2f), center = Offset(w / 2, ey))
    }
}

/** Manchetes recentes (urgentes primeiro) para a faixa rolante. */
fun tickerHeadlines(clusters: List<Cluster>, now: Instant = Instant.now(), max: Int = 8): List<Cluster> =
    clusters.filter { c ->
        runCatching { Duration.between(Instant.parse(c.updated), now).toHours() < 6 }.getOrDefault(false)
    }.sortedWith(compareByDescending<Cluster> { it.urgent }.thenByDescending { it.updated }).take(max)

/** Faixa de manchetes rolando, como nos canais de notícia. Tocar abre a primeira. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeadlineTicker(items: List<Cluster>, title: (Cluster) -> String, onOpen: (String) -> Unit) {
    if (items.isEmpty()) return
    val text = items.joinToString("      ◆      ") { (if (it.urgent) "URGENTE · " else "") + title(it) }
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen(items.first().id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "  ◉ AO VIVO  ",
            style = MaterialTheme.typography.labelSmall,
            color = if (items.any { it.urgent }) Alert else Accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp),
        )
    }
}
