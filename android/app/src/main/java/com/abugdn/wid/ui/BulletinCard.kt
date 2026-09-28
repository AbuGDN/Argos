package com.abugdn.wid.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.abugdn.wid.data.Bulletin
import com.abugdn.wid.data.Translator
import java.time.format.DateTimeFormatter

private const val W = 1080
private const val H = 1350
private const val MARGIN = 60f
private val GOLD = 0xFFC9A227.toInt()
private val BLOOD = 0xFFB3122E.toInt()
private val BONE = 0xFFE8E2D0.toInt()
private val ASH = 0xFF8A8578.toInt()
private val INK = 0xFF050505.toInt()

private val short = DateTimeFormatter.ofPattern("dd/MM")
private val full = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.08f)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

private fun Canvas.drawLayout(l: StaticLayout, x: Float, y: Float) {
    save()
    translate(x, y)
    l.draw(this)
    restore()
}

/** Imagem 1080×1350 do boletim semanal, em preto e ouro. */
fun drawBulletin(b: Bulletin, translator: Translator): Bitmap {
    val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(INK)

    // Moldura fina dourada.
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.STROKE; strokeWidth = 3f }
    canvas.drawRect(24f, 24f, W - 24f, H - 24f, frame)

    // Cabeçalho: olho, nome e semana.
    drawArgosEye(canvas, 118f, 118f, 100f, GOLD)
    val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD; textSize = 58f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); letterSpacing = 0.25f
    }
    canvas.drawText("ARGOS", 196f, 112f, brand)
    val kicker = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BONE; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f
    }
    canvas.drawText("BOLETIM SEMANAL · ${short.format(b.from)} A ${full.format(b.to)}", 198f, 158f, kicker.apply { typeface = Typeface.MONOSPACE })
    drawStamp(canvas, "CONFIDENCIAL", "", W - 190f, 96f, -10f)
    val rule = Paint().apply { color = GOLD; strokeWidth = 2f }
    canvas.drawLine(MARGIN, 210f, W - MARGIN, 210f, rule)

    val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
    }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BONE; textSize = 33f; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }
    val number = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD; textSize = 44f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ASH; textSize = 27f }

    var y = 268f
    canvas.drawText("AS ${b.top.size.coerceAtLeast(1)} PRINCIPAIS DA SEMANA", MARGIN, y, label)
    y += 26f
    if (b.top.isEmpty()) {
        y += 40f
        canvas.drawText("O arquivo ainda não tem dias suficientes.", MARGIN, y, small)
        y += 20f
    }
    b.top.forEachIndexed { i, c ->
        val l = layout(translator.display(c.title, c.lang), body, (W - MARGIN * 2 - 70).toInt(), 2)
        canvas.drawText("${i + 1}", MARGIN, y + 42f, number)
        canvas.drawLayout(l, MARGIN + 70, y + 6f)
        y += l.height + 20f
    }

    // Três quadros de destaque.
    fun stat(title: String, main: String, detail: String?, alert: Boolean = false) {
        // Sem espaço para mais um quadro (títulos longos): para antes do rodapé.
        if (y > H - 250f) return
        y += 18f
        canvas.drawLine(MARGIN, y, W - MARGIN, y, rule.apply { color = 0xFF3A362C.toInt() })
        y += 44f
        canvas.drawText(title, MARGIN, y, label.apply { color = if (alert) BLOOD else GOLD })
        val l = layout(main, body.apply { textSize = 38f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }, (W - MARGIN * 2).toInt(), 1)
        canvas.drawLayout(l, MARGIN, y + 10f)
        y += l.height + 14f
        if (detail != null) {
            y += 26f
            canvas.drawText(TextUtils.ellipsize(detail, small, W - MARGIN * 2, TextUtils.TruncateAt.END).toString(), MARGIN, y, small)
        }
    }

    b.tenseRegion?.let { (name, tension) ->
        stat("REGIÃO MAIS TENSA", "$name · tensão $tension de 100", null)
    }
    b.biggestAlert?.let { e ->
        val date = java.time.Instant.ofEpochMilli(e.time).atZone(java.time.ZoneId.systemDefault()).format(short)
        stat("MAIOR PICO DE ALERTA", translator.display(e.title, e.lang), listOf(e.detail, date).filter { it.isNotBlank() }.joinToString(" · "), alert = true)
    }
    b.first?.let { r ->
        stat("QUEM NOTICIOU PRIMEIRO", r.source, "primeiro em ${r.firsts} de ${r.stories} histórias grandes (30 dias)")
    }

    // Rodapé.
    val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ASH; textSize = 28f; textAlign = Paint.Align.CENTER }
    val alerts = if (b.alerts == 1) "1 alerta registrado" else "${b.alerts} alertas registrados"
    canvas.drawText("$alerts na semana", W / 2f, H - 62f, footer)
    return bitmap
}
