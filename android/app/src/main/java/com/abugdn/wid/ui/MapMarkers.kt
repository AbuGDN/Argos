package com.abugdn.wid.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ícones do mapa desenhados no próprio aparelho (sem imagens no APK): avião virado para o rumo,
 * porta-aviões visto de cima, navio dos estreitos, escudo das bases, ondas dos sismos, alfinete
 * das cidades e o disco das regiões com o anel da tensão. Cada desenho é feito uma vez e guardado
 * (o rumo do avião vai de 15 em 15 graus), porque o mapa refaz as camadas a cada mudança.
 */
internal object MapGlyphs {
    private const val INK = 0xFF050505.toInt()
    private const val GOLD = 0xFFC9A227.toInt()
    private const val BONE = 0xFFE8E2D0.toInt()
    private const val ASH = 0xFF8A8578.toInt()
    const val RED = 0xFFB3122E.toInt()
    const val QUAKE = 0xFFE0521B.toInt()

    private val cache = HashMap<String, Bitmap>()

    private fun cached(key: String, make: () -> Bitmap): Bitmap = synchronized(cache) { cache.getOrPut(key, make) }

    fun drawable(context: Context, bitmap: Bitmap): Drawable = BitmapDrawable(context.resources, bitmap)

    private fun density(context: Context) = context.resources.displayMetrics.density

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /** Caminho SVG dos Material Symbols (caixa 0 -960 960 960); nulo se o texto não abrir. */
    private fun symbol(emoji: String): Path? =
        EMOJI_ICON_PATHS[emoji]?.let { runCatching { androidx.core.graphics.PathParser.createPathFromPathData(it) }.getOrNull() }

    private val planePath by lazy { symbol("✈") }
    private val shipPath by lazy { symbol("🚢") }

    // --- Aviões militares -----------------------------------------------------------------

    fun aircraftColor(category: String): Int = when (category) {
        "reabastecedor" -> GOLD
        "radar" -> 0xFF4FC3F7.toInt()
        "espionagem" -> 0xFFB388FF.toInt()
        "bombardeiro" -> RED
        "caça" -> 0xFFC8662B.toInt()
        "transporte" -> BONE
        else -> ASH
    }

    private fun aircraftSizeDp(category: String): Int = when (category) {
        "bombardeiro", "reabastecedor", "radar", "transporte" -> 28
        "caça" -> 21
        else -> 24
    }

    /** Silhueta do avião vista de cima, apontando para o rumo (graus a partir do norte, horário). */
    fun aircraft(context: Context, category: String, track: Double?, grounded: Boolean): Bitmap {
        val d = density(context)
        val step = track?.let { ((it / 15.0).roundToInt() * 15) % 360 } ?: 0
        return cached("ac:$category:$step:$grounded:$d") {
            val s = (aircraftSizeDp(category) * d).toInt().coerceAtLeast(8)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val color = aircraftColor(category)
            val path = planePath
            if (path == null) {
                c.drawCircle(s / 2f, s / 2f, s / 3f, fill(color))
                c.drawCircle(s / 2f, s / 2f, s / 3f, stroke(INK, 1.5f * d))
                return@cached bmp
            }
            // O desenho ocupa um círculo de raio ~410 em volta de (480, -480).
            val scale = (s / 2f - 2f * d) / 410f
            c.translate(s / 2f, s / 2f)
            c.rotate(step.toFloat())
            c.scale(scale, scale)
            c.translate(-480f, 480f)
            c.drawPath(path, stroke(INK, 3.2f * d / scale).apply { alpha = 235 })
            c.drawPath(path, fill(color).apply { if (grounded) alpha = 150 })
            if (category == "radar") {
                // Disco do radar (rotodome) em cima da fuselagem, como no AWACS.
                c.drawCircle(480f, -520f, 120f, fill(INK))
                c.drawCircle(480f, -520f, 120f, stroke(color, 2f * d / scale))
            }
            bmp
        }
    }

    // --- Porta-aviões ---------------------------------------------------------------------

    /** Distintivo redondo com o porta-aviões visto de cima e a sigla do casco embaixo. */
    fun carrier(context: Context, hull: String): Bitmap {
        val d = density(context)
        return cached("cvn:$hull:$d") {
            val badge = 32f * d
            labeled(context, badge, hull, GOLD) { c, cx, cy ->
                c.drawCircle(cx, cy, badge / 2f - 1.5f * d, fill(INK).apply { alpha = 235 })
                c.drawCircle(cx, cy, badge / 2f - 1.5f * d, stroke(GOLD, 1.8f * d))
                // Convés em caixa 0..100, proa para cima, pista em ângulo à esquerda.
                val k = badge * 0.0068f
                c.save()
                c.translate(cx - 50f * k, cy - 52f * k)
                c.scale(k, k)
                val deck = Path().apply {
                    moveTo(46f, 4f); lineTo(56f, 4f); lineTo(63f, 20f); lineTo(63f, 92f)
                    lineTo(40f, 98f); lineTo(37f, 64f); lineTo(27f, 52f); lineTo(29f, 40f)
                    lineTo(38f, 32f); lineTo(39f, 18f); close()
                }
                c.drawPath(deck, fill(GOLD))
                // Ilha (torre) à direita e as linhas da pista.
                c.drawRect(64f, 56f, 71f, 72f, fill(GOLD))
                c.drawLine(50f, 92f, 33f, 47f, stroke(INK, 3.5f))
                c.drawLine(51f, 10f, 51f, 40f, stroke(INK, 3f))
                c.restore()
            }
        }
    }

    // --- Navios nos estreitos -------------------------------------------------------------

    /** Navio num distintivo; anel vermelho quando o tráfego caiu bem abaixo do normal. */
    fun strait(context: Context, label: String, dropped: Boolean): Bitmap {
        val d = density(context)
        return cached("strait:$label:$dropped:$d") {
            val badge = 30f * d
            val ring = if (dropped) RED else BONE
            labeled(context, badge, label, ring) { c, cx, cy ->
                c.drawCircle(cx, cy, badge / 2f - 1.5f * d, fill(INK).apply { alpha = 235 })
                c.drawCircle(cx, cy, badge / 2f - 1.5f * d, stroke(ring, 1.8f * d))
                val path = shipPath
                if (path != null) {
                    val scale = badge * 0.58f / 960f
                    c.save()
                    c.translate(cx - 480f * scale, cy + 480f * scale)
                    c.scale(scale, scale)
                    c.drawPath(path, fill(BONE))
                    c.restore()
                } else {
                    c.drawCircle(cx, cy, badge / 5f, fill(BONE))
                }
            }
        }
    }

    // --- Bases -----------------------------------------------------------------------------

    /** Escudo na cor do país dono da base, com uma estrela. */
    fun base(context: Context, color: Int): Bitmap {
        val d = density(context)
        return cached("base:$color:$d") {
            val s = (19 * d).toInt().coerceAtLeast(8)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val k = s / 100f
            c.scale(k, k)
            val shield = Path().apply {
                moveTo(50f, 6f); lineTo(90f, 18f); lineTo(87f, 56f)
                quadTo(80f, 82f, 50f, 96f); quadTo(20f, 82f, 13f, 56f); lineTo(10f, 18f); close()
            }
            c.drawPath(shield, fill(color))
            c.drawPath(shield, stroke(INK, 8f))
            c.drawPath(star(50f, 48f, 22f, 9f), fill(INK))
            bmp
        }
    }

    private fun star(cx: Float, cy: Float, outer: Float, inner: Float): Path = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    // --- Sismos ----------------------------------------------------------------------------

    /** Ondas concêntricas; o tamanho cresce com a magnitude. Explosão sai em vermelho. */
    fun quake(context: Context, magnitude: Double?, explosion: Boolean): Bitmap {
        val d = density(context)
        val m = ((magnitude ?: 3.0) * 2).roundToInt() / 2.0
        return cached("quake:$m:$explosion:$d") {
            val sizeDp = (10 + (m.coerceIn(2.0, 7.0) - 2.0) * 5).toFloat()
            val s = (sizeDp * d).toInt().coerceAtLeast(8)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val color = if (explosion) RED else QUAKE
            val r = s / 2f
            c.drawCircle(r, r, r - 1.2f * d, stroke(color, 1.4f * d).apply { alpha = 120 })
            c.drawCircle(r, r, r * 0.62f, stroke(color, 1.6f * d).apply { alpha = 200 })
            c.drawCircle(r, r, (r * 0.26f).coerceAtLeast(2.5f * d), fill(color))
            c.drawCircle(r, r, (r * 0.26f).coerceAtLeast(2.5f * d), stroke(INK, 1f * d))
            bmp
        }
    }

    // --- Desastres (GDACS) -------------------------------------------------------------------

    fun disasterColor(level: String): Int = when (level) {
        "red" -> RED
        "orange" -> 0xFFC8662B.toInt()
        else -> 0xFF6E8B6A.toInt()
    }

    private val flamePath by lazy { symbol("🔥") }

    /**
     * Disco na cor do alerta (vermelho, laranja, verde) com o desenho do tipo: ondas de sismo,
     * espiral de ciclone, ondas de enchente, vulcão, sol da seca ou chama do incêndio.
     * Vermelho é maior; evento que já acabou fica translúcido.
     */
    fun disaster(context: Context, type: String, level: String, active: Boolean): Bitmap {
        val d = density(context)
        return cached("dis:$type:$level:$active:$d") {
            val sizeDp = when (level) { "red" -> 30; "orange" -> 26; else -> 21 }
            val s = (sizeDp * d).toInt().coerceAtLeast(8)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = s / 2f
            val fade = if (active) 255 else 150
            c.drawCircle(r, r, r - 1f * d, fill(disasterColor(level)).apply { this.alpha = fade })
            c.drawCircle(r, r, r - 1f * d, stroke(INK, 1.6f * d))
            val g = stroke(BONE, (1.7f * d).coerceAtLeast(1.5f)).apply { this.alpha = fade }
            val gf = fill(BONE).apply { this.alpha = fade }
            val u = r * 0.52f // meia largura do desenho
            when (type) {
                "EQ" -> c.drawPath(Path().apply {
                    moveTo(r - u, r); lineTo(r - u * 0.55f, r); lineTo(r - u * 0.3f, r - u * 0.8f)
                    lineTo(r + u * 0.05f, r + u * 0.8f); lineTo(r + u * 0.35f, r - u * 0.45f)
                    lineTo(r + u * 0.55f, r); lineTo(r + u, r)
                }, g)
                "TC" -> {
                    c.drawCircle(r, r, u * 0.28f, gf)
                    val box = RectF(r - u, r - u, r + u, r + u)
                    c.drawArc(box, 200f, 110f, false, g)
                    c.drawArc(box, 20f, 110f, false, g)
                    val inner = RectF(r - u * 0.62f, r - u * 0.62f, r + u * 0.62f, r + u * 0.62f)
                    c.drawArc(inner, 290f, 90f, false, g)
                    c.drawArc(inner, 110f, 90f, false, g)
                }
                "FL" -> for (i in -1..1) {
                    val y = r + i * u * 0.6f
                    c.drawPath(Path().apply {
                        moveTo(r - u, y)
                        quadTo(r - u * 0.5f, y - u * 0.3f, r, y)
                        quadTo(r + u * 0.5f, y + u * 0.3f, r + u, y)
                    }, g)
                }
                "VO" -> {
                    c.drawPath(Path().apply {
                        moveTo(r - u, r + u * 0.75f); lineTo(r - u * 0.25f, r - u * 0.2f); lineTo(r + u * 0.25f, r - u * 0.2f)
                        lineTo(r + u, r + u * 0.75f); close()
                    }, gf)
                    c.drawCircle(r - u * 0.15f, r - u * 0.6f, u * 0.17f, gf)
                    c.drawCircle(r + u * 0.25f, r - u * 0.85f, u * 0.13f, gf)
                }
                "DR" -> {
                    c.drawCircle(r, r, u * 0.42f, gf)
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val x1 = r + (u * 0.62f) * cos(a).toFloat(); val y1 = r + (u * 0.62f) * sin(a).toFloat()
                        val x2 = r + u * cos(a).toFloat(); val y2 = r + u * sin(a).toFloat()
                        c.drawLine(x1, y1, x2, y2, g)
                    }
                }
                "" -> Unit // só a cor (legenda dos níveis)
                else -> {
                    val path = flamePath
                    if (path != null) {
                        val scale = u * 2.1f / 960f
                        c.save()
                        c.translate(r - 480f * scale, r + 480f * scale)
                        c.scale(scale, scale)
                        c.drawPath(path, gf)
                        c.restore()
                    } else {
                        c.drawCircle(r, r, u * 0.5f, gf)
                    }
                }
            }
            bmp
        }
    }

    // --- Focos de calor (só para a legenda; no mapa são pontos rápidos) --------------------

    fun fire(context: Context): Bitmap {
        val d = density(context)
        return cached("fire:$d") {
            val s = (16 * d).toInt().coerceAtLeast(8)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawCircle(s / 2f, s / 2f, s / 2f - 1f, fill(0x40E0521B))
            c.drawCircle(s / 2f, s / 2f, 3.5f * d, fill(0xFFFF7A2E.toInt()))
            bmp
        }
    }

    // --- Regiões e cidades -----------------------------------------------------------------

    fun tensionColor(level: String?): Int = when (level) {
        "crítica" -> RED
        "alta" -> 0xFFC8662B.toInt()
        "moderada" -> GOLD
        "baixa" -> 0xFF6E8B6A.toInt()
        else -> 0
    }

    /** Disco dourado com o número de histórias e, em volta, o anel na cor da tensão da região. */
    fun region(context: Context, count: Int, level: String?, spike: Boolean): Bitmap {
        val d = density(context)
        return cached("region:$count:$level:$spike:$d") {
            val ringColor = if (spike) RED else tensionColor(level)
            val core = (if (count >= 100) 38 else 32) * d
            val ring = if (ringColor != 0) 4f * d else 0f
            val s = (core + 2 * ring + 2 * d).toInt()
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = s / 2f
            if (ringColor != 0) {
                c.drawCircle(r, r, r - 1f * d, fill(INK).apply { alpha = 200 })
                c.drawCircle(r, r, r - ring / 2f - 1f * d, stroke(ringColor, ring - 1f * d))
            }
            val inner = core / 2f
            c.drawCircle(r, r, inner, fill(GOLD))
            c.drawCircle(r, r, inner, stroke(INK, 2f * d))
            if (count >= 0) drawCount(c, count, r, r, 13 * d)
            bmp
        }
    }

    /** Alfinete dourado com o número de histórias que citam a cidade; a ponta marca o lugar. */
    fun cityPin(context: Context, count: Int): Bitmap {
        val d = density(context)
        return cached("city:$count:$d") {
            val w = (24 * d).toInt()
            val h = (32 * d).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = w / 2f - 1.5f * d
            val cx = w / 2f
            val cy = r + 1.5f * d
            val pin = Path().apply {
                addCircle(cx, cy, r, Path.Direction.CW)
                moveTo(cx - r * 0.62f, cy + r * 0.78f)
                lineTo(cx, h - 1.5f * d)
                lineTo(cx + r * 0.62f, cy + r * 0.78f)
                close()
            }
            c.drawPath(pin, fill(GOLD))
            c.drawPath(pin, stroke(INK, 1.8f * d))
            drawCount(c, count, cx, cy, 10.5f * d)
            bmp
        }
    }

    private fun drawCount(c: Canvas, count: Int, x: Float, y: Float, size: Float) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textAlign = Paint.Align.CENTER
            textSize = if (count >= 100) size * 0.85f else size
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(count.toString(), x, y - (text.descent() + text.ascent()) / 2, text)
    }

    /**
     * Distintivo redondo de [badge] px com uma etiqueta embaixo. O centro do distintivo fica no
     * ponto do mapa: use [labeledAnchorV] como âncora vertical.
     */
    private fun labeled(context: Context, badge: Float, label: String, ring: Int, draw: (Canvas, Float, Float) -> Unit): Bitmap {
        val d = density(context)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BONE
            textAlign = Paint.Align.CENTER
            textSize = 9.5f * d
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textW = if (label.isBlank()) 0f else text.measureText(label) + 8f * d
        val labelH = if (label.isBlank()) 0f else 14f * d
        val w = maxOf(badge, textW).toInt() + 2
        val h = (badge + labelH + 2f * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        draw(c, cx, badge / 2f)
        if (label.isNotBlank()) {
            val top = badge + 1f * d
            val rect = RectF(cx - textW / 2f, top, cx + textW / 2f, top + labelH)
            c.drawRoundRect(rect, 4f * d, 4f * d, fill(INK).apply { alpha = 220 })
            c.drawRoundRect(rect, 4f * d, 4f * d, stroke(ring, 1f * d).apply { alpha = 180 })
            c.drawText(label, cx, rect.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
        return bmp
    }

    /** Âncora vertical (0..1) que põe o centro do distintivo de [labeled] no ponto. */
    fun labeledAnchorV(context: Context, bitmap: Bitmap, badgeDp: Float): Float =
        (badgeDp * density(context) / 2f) / bitmap.height

    const val CARRIER_BADGE_DP = 32f
    const val STRAIT_BADGE_DP = 30f
}

/** Posição de cada estreito do PortWatch no mapa (radar.yaml → straits). */
internal val STRAIT_POINTS = mapOf(
    "bab" to (12.6 to 43.35),
    "suez" to (30.55 to 32.33),
    "hormuz" to (26.55 to 56.35),
    "bosporus" to (41.12 to 29.07),
)

/** Tráfego da semana bem abaixo do normal (menos de 75% da média de 90 dias). */
internal fun com.abugdn.wid.data.Strait.dropped(): Boolean {
    val week = avg7 ?: return false
    val normal = avg90 ?: return false
    return normal > 0 && week < normal * 0.75
}
