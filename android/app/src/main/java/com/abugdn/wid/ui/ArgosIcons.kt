package com.abugdn.wid.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

// Emoji usado como ícone (no começo de título de cartão, botão, chip ou ferramenta) vira um ícone de
// traço fino na cor do texto: emoji muda de desenho de um celular para outro e brigava com a paleta.
// Os desenhos ficam em ArgosIconPaths.kt (gerado). Emoji sem desenho continua aparecendo como emoji.

private val iconCache = HashMap<String, ImageVector>()

/** Ícone do emoji (sem o seletor de variação U+FE0F), ou null se não houver desenho. */
fun emojiIcon(emoji: String): ImageVector? {
    val key = emoji.replace("️", "")
    iconCache[key]?.let { return it }
    val d = EMOJI_ICON_PATHS[key] ?: return null
    return ImageVector.Builder(key, 24.dp, 24.dp, 960f, 960f)
        // Os caminhos do Material Symbols ficam na caixa 0 -960 960 960: desce 960 para caber no quadro.
        .addGroup(translationY = 960f)
        .addPath(addPathNodes(d), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
        .also { iconCache[key] = it }
}

/** Separa o emoji do começo do texto, se for um que tem ícone: ("⚠", "NÚMEROS DIVERGENTES"). */
fun splitIcon(text: String): Pair<ImageVector, String>? {
    val trimmed = text.trimStart()
    for (key in EMOJI_ICON_PATHS.keys) {
        if (!trimmed.startsWith(key)) continue
        val rest = trimmed.removePrefix(key).removePrefix("️")
        // Só vale como ícone se vier separado do texto por espaço (ou se for o texto inteiro).
        if (rest.isNotEmpty() && !rest.first().isWhitespace()) return null
        return emojiIcon(key)?.let { it to rest.trimStart() }
    }
    return null
}

/**
 * Text que troca o emoji do começo por um ícone da mesma cor e tamanho da letra. Sem emoji mapeado,
 * é um Text comum.
 */
@Composable
fun IconText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val split = splitIcon(text)
    if (split == null) {
        Text(text, modifier, color = color, style = style, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow)
        return
    }
    val (icon, rest) = split
    val tint = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    val size = with(LocalDensity.current) { (if (style.fontSize.isSpecified) style.fontSize else LocalTextStyle.current.fontSize).toDp() * 1.25f }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        if (rest.isNotEmpty()) {
            Text(
                rest,
                Modifier.padding(start = 6.dp),
                color = color,
                style = style,
                fontWeight = fontWeight,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}
