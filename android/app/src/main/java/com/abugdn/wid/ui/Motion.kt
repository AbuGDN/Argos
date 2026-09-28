package com.abugdn.wid.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Movimento reduzido
// ---------------------------------------------------------------------------

/** "Remover animações" ligado no Android (escala de animação 0): tudo aparece sem movimento. */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun Context.animationsOff(): Boolean =
    runCatching { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)

// ---------------------------------------------------------------------------
// Notícia que "abre" do cartão (elemento compartilhado entre a lista e a notícia)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Liga o elemento ao de mesma [key] na outra tela; sem transição disponível, não faz nada. */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedKey(key: String): Modifier = composed {
    val shared = LocalSharedScope.current
    val anim = LocalAnimScope.current
    if (shared == null || anim == null || LocalReduceMotion.current) {
        this
    } else {
        with(shared) {
            this@composed.sharedBounds(
                rememberSharedContentState(key),
                animatedVisibilityScope = anim,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Números que contam
// ---------------------------------------------------------------------------

/**
 * Número que sobe de 0 até o valor ao aparecer e, quando muda depois, rola como placar
 * (o novo entra por baixo). [format] monta o texto a partir do número.
 */
@Composable
fun CountUpText(
    value: Long,
    style: TextStyle,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
    format: (Long) -> String = { it.toString() },
) {
    val reduce = LocalReduceMotion.current
    val start = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { start.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
        },
        label = "placar",
        modifier = modifier,
    ) { v ->
        val shown = if (start.value < 1f) (v * start.value).toLong() else v
        Text(format(shown), style = style, color = color, fontWeight = fontWeight)
    }
}

// ---------------------------------------------------------------------------
// Borda que respira (alertas ativos)
// ---------------------------------------------------------------------------

/** Borda vermelha com pulso lento de brilho enquanto [active]. */
fun Modifier.breathingBorder(active: Boolean, shape: Shape = RoundedCornerShape(12.dp), color: Color = Alert): Modifier = composed {
    if (!active) return@composed this
    if (LocalReduceMotion.current) return@composed this.border(1.5.dp, color, shape)
    val t = rememberInfiniteTransition(label = "respira")
    val a by t.animateFloat(0.25f, 0.95f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "alfa")
    this.border(1.5.dp, color.copy(alpha = a), shape)
}

/** Brilho dourado que some em ~1,5 s (história que acabou de chegar). */
fun Modifier.freshGlow(active: Boolean): Modifier = composed {
    if (!active || LocalReduceMotion.current) return@composed this
    val glow = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { glow.animateTo(0f, tween(1500)) }
    this.drawBehind {
        if (glow.value > 0f) {
            drawRect(Accent.copy(alpha = glow.value * 0.18f))
            drawRect(Accent.copy(alpha = glow.value), size = size.copy(width = 3.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------
// Abertura: o triângulo se desenha e o olho abre
// ---------------------------------------------------------------------------

@Composable
fun ArgosSplash(onDone: () -> Unit) {
    val draw = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        open.animateTo(1f, tween(320))
        kotlinx.coroutines.delay(180)
        fade.animateTo(0f, tween(300))
        onDone()
    }
    Box(Modifier.fillMaxSize().alpha(fade.value).background(Ink), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(140.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tri = Path().apply {
                moveTo(w / 2, h * 0.06f)
                lineTo(w * 0.96f, h * 0.9f)
                lineTo(w * 0.04f, h * 0.9f)
                close()
            }
            // Só o pedaço já "desenhado" do contorno.
            val measure = PathMeasure().apply { setPath(tri, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * draw.value, partial, true)
            drawPath(partial, Accent, style = stroke)
            if (open.value > 0f) {
                val ey = h * 0.6f
                val ew = w * 0.24f
                val eh = ew * 0.85f * open.value
                val eye = Path().apply {
                    moveTo(w / 2 - ew, ey)
                    quadraticBezierTo(w / 2, ey - eh, w / 2 + ew, ey)
                    quadraticBezierTo(w / 2, ey + eh, w / 2 - ew, ey)
                    close()
                }
                drawPath(eye, Accent, style = Stroke(width = w * 0.035f))
                // A íris "foca": começa grande e fecha no tamanho certo.
                val r = ew * 0.36f * (1.6f - 0.6f * open.value)
                drawCircle(Accent, radius = r * open.value, center = Offset(w / 2, ey))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Varredura do Radar
// ---------------------------------------------------------------------------

/** Ícone de radar com um ponteiro girando; com [alert], um ponto vermelho acende quando a varredura passa. */
@Composable
fun RadarSweepIcon(alert: Boolean, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "varredura")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "angulo")
    val a = if (reduce) 45f else angle
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2 * 0.92f
        val ring = Stroke(width = 1.5.dp.toPx())
        drawCircle(Accent.copy(alpha = 0.5f), radius = r, center = c, style = ring)
        drawCircle(Accent.copy(alpha = 0.35f), radius = r * 0.55f, center = c, style = ring)
        // Rastro da varredura: alguns traços que vão apagando.
        for (i in 0 until 8) {
            val ang = Math.toRadians((a - i * 6).toDouble())
            val end = Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat())
            drawLine(Accent.copy(alpha = 0.9f - i * 0.11f), c, end, strokeWidth = 2.dp.toPx())
        }
        if (alert) {
            // Ponto fixo a 300°: brilha forte logo depois que a varredura passa por ele.
            val blipAngle = 300f
            val since = ((a - blipAngle) % 360f + 360f) % 360f
            val glow = if (reduce) 1f else (1f - since / 360f).coerceIn(0.25f, 1f)
            val ang = Math.toRadians(blipAngle.toDouble())
            val p = Offset(c.x + (r * 0.7f * cos(ang)).toFloat(), c.y + (r * 0.7f * sin(ang)).toFloat())
            drawCircle(Alert.copy(alpha = glow), radius = 3.dp.toPx(), center = p)
        }
    }
}

// ---------------------------------------------------------------------------
// Puxar para atualizar: o olho abre com o dedo e a íris gira ao carregar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyePullIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val fraction = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
    if (fraction <= 0f) return
    val t = rememberInfiniteTransition(label = "iris")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "giro")
    Box(
        modifier.padding(top = (16 * fraction).dp).size(44.dp).alpha(fraction)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp).rotate(if (refreshing && !LocalReduceMotion.current) spin else 0f)) {
            val w = size.width
            val h = size.height
            val ey = h / 2
            val ew = w * 0.42f
            val eh = ew * 0.8f * fraction
            val eye = Path().apply {
                moveTo(w / 2 - ew, ey)
                quadraticBezierTo(w / 2, ey - eh, w / 2 + ew, ey)
                quadraticBezierTo(w / 2, ey + eh, w / 2 - ew, ey)
                close()
            }
            drawPath(eye, Accent, style = Stroke(width = w * 0.07f))
            if (fraction > 0.4f) {
                drawCircle(Accent, radius = ew * 0.36f, center = Offset(w / 2, ey))
                // Brilho fora do centro: mostra a íris girando.
                drawCircle(Ink, radius = ew * 0.1f, center = Offset(w / 2 + ew * 0.14f, ey - ew * 0.12f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carimbo ARQUIVADO (salvar / seguir)
// ---------------------------------------------------------------------------

/** Carimbo que bate e some. Mude [trigger] (ex.: um contador) para disparar de novo. */
@Composable
fun StampFlash(trigger: Int, text: String, modifier: Modifier = Modifier) {
    if (trigger == 0 || LocalReduceMotion.current) return
    val scale = remember(trigger) { Animatable(1.8f) }
    val alpha = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        alpha.snapTo(1f)
        scale.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(650)
        alpha.animateTo(0f, tween(350))
    }
    if (alpha.value <= 0f) return
    Box(
        modifier.scale(scale.value).rotate(-12f).alpha(alpha.value)
            .border(3.dp, Alert, RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, color = Alert, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 3.sp)
    }
}
