package com.abugdn.wid.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.isSensitive
import com.abugdn.wid.repository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val W = 1080
private const val H = 1350
private const val PHOTO_H = 640

/** Gera um cartão 1080×1350 (formato de post/story) da notícia e abre o compartilhar do Android. */
suspend fun shareAsImage(context: Context, cluster: Cluster) {
    val repo = context.repository
    val settings = repo.settings.value
    val title = repo.translator.display(cluster.title, cluster.lang)
    // Foto só quando não for sensível e a economia de dados estiver desligada.
    val photo = if (cluster.image != null && !settings.dataSaver && !cluster.isSensitive(repo.translator::cached)) {
        runCatching {
            val request = ImageRequest.Builder(context).data(cluster.image).allowHardware(false).size(W, PHOTO_H).build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    } else {
        null
    }
    val bitmap = withContext(Dispatchers.Default) { drawCard(cluster, title, photo) }
    shareBitmap(context, bitmap, "wid-${cluster.id}", "$title\n${cluster.url}")
}

/** Salva a imagem no cache e abre o compartilhar do Android. */
suspend fun shareBitmap(context: Context, bitmap: Bitmap, name: String, text: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "$name.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, "Compartilhar imagem"))
}

private fun drawCard(cluster: Cluster, title: String, photo: Bitmap?): Bitmap {
    val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFF050505.toInt())
    var y = 110f

    if (photo != null) {
        // Recorte central da foto na faixa de cima, com degradê para o fundo.
        val scale = maxOf(W.toFloat() / photo.width, PHOTO_H.toFloat() / photo.height)
        val srcW = (W / scale).toInt()
        val srcH = (PHOTO_H / scale).toInt()
        val left = (photo.width - srcW) / 2
        val top = (photo.height - srcH) / 2
        canvas.drawBitmap(photo, Rect(left, top, left + srcW, top + srcH), Rect(0, 0, W, PHOTO_H), Paint(Paint.FILTER_BITMAP_FLAG))
        val fade = Paint().apply {
            shader = LinearGradient(0f, PHOTO_H * 0.45f, 0f, PHOTO_H.toFloat(), 0x00050505, 0xFF050505.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), PHOTO_H.toFloat(), fade)
        y = PHOTO_H + 30f
    }

    val gold = 0xFFC9A227.toInt()
    val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; textSize = 34f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f }
    canvas.drawText("ARGOS", 60f, y, label)
    // Número do dossiê em "máquina de escrever", ao lado do nome.
    val typewriter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A8578.toInt(); textSize = 28f; typeface = Typeface.MONOSPACE; letterSpacing = 0.05f
    }
    canvas.drawText("DOSSIÊ Nº ${cluster.id.take(6).uppercase()}", 230f, y, typewriter)
    y += 40f

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8E2D0.toInt(); textSize = if (photo != null) 62f else 76f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val maxLines = if (photo != null) 6 else 9
    val layout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, W - 120)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.1f)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    canvas.save()
    canvas.translate(60f, y)
    layout.draw(canvas)
    canvas.restore()
    y += layout.height + 60f

    val date = runCatching {
        DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(Instant.parse(cluster.updated))
    }.getOrDefault("")
    val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A8578.toInt(); textSize = 32f; typeface = Typeface.MONOSPACE }
    canvas.drawText(
        "${cluster.source} · ${cluster.sourcesCount} ${if (cluster.sourcesCount == 1) "veículo" else "veículos"} · $date",
        60f, y, meta,
    )

    // Rodapé: o olho no triângulo como selo e o carimbo com a data.
    drawArgosEye(canvas, 100f, H - 96f, 80f, gold)
    drawStamp(canvas, "CONFIDENCIAL", date, W - 250f, H - 120f, -12f)
    return bitmap
}

/** Carimbo vermelho-sangue inclinado, com moldura dupla, estilo documento secreto. */
fun drawStamp(canvas: Canvas, title: String, subtitle: String, cx: Float, cy: Float, angle: Float) {
    val red = 0xCCB3122E.toInt()
    val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = red; textSize = 46f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.12f; textAlign = Paint.Align.CENTER
    }
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = red; textSize = 26f; typeface = Typeface.MONOSPACE; letterSpacing = 0.1f; textAlign = Paint.Align.CENTER
    }
    val w = maxOf(big.measureText(title), small.measureText(subtitle)) / 2 + 28f
    val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red; style = Paint.Style.STROKE; strokeWidth = 5f }
    canvas.save()
    canvas.rotate(angle, cx, cy)
    canvas.drawRect(cx - w, cy - 52f, cx + w, cy + (if (subtitle.isBlank()) 22f else 52f), box)
    canvas.drawRect(cx - w + 9f, cy - 43f, cx + w - 9f, cy + (if (subtitle.isBlank()) 13f else 43f), box.apply { strokeWidth = 2f })
    canvas.drawText(title, cx, cy + 4f, big)
    if (subtitle.isNotBlank()) canvas.drawText(subtitle, cx, cy + 36f, small)
    canvas.restore()
}

/** Logo Argos: triângulo com o olho no centro, desenhado centrado em (cx, cy) com lado [size]. */
fun drawArgosEye(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.06f; strokeJoin = Paint.Join.ROUND
    }
    val h = size * 0.866f
    val triangle = android.graphics.Path().apply {
        moveTo(cx, cy - h * 0.62f)
        lineTo(cx + size / 2, cy + h * 0.38f)
        lineTo(cx - size / 2, cy + h * 0.38f)
        close()
    }
    canvas.drawPath(triangle, stroke)
    // Olho amendoado no centro de massa do triângulo.
    val ey = cy + h * 0.05f
    val w = size * 0.28f
    val eye = android.graphics.Path().apply {
        moveTo(cx - w, ey)
        quadTo(cx, ey - w * 0.9f, cx + w, ey)
        quadTo(cx, ey + w * 0.9f, cx - w, ey)
        close()
    }
    canvas.drawPath(eye, stroke.apply { strokeWidth = size * 0.045f })
    canvas.drawCircle(cx, ey, w * 0.34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}
