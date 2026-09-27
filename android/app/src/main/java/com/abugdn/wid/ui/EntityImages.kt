package com.abugdn.wid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abugdn.wid.data.REGION_FLAGS
import com.abugdn.wid.data.flagUrl
import com.abugdn.wid.repository

/**
 * Foto do artigo da Wikipédia [title]. [person] = recorte redondo pelo rosto; senão (grupos,
 * armas) a imagem inteira em cantos arredondados, sem cortar logos. Some com a economia de dados.
 */
@Composable
fun WikiImage(title: String?, person: Boolean, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    if (title == null || LocalDataSaver.current) return
    val repo = LocalContext.current.repository
    val url by produceState<String?>(null, title) { value = repo.wikiImage(title) }
    val src = url ?: return
    AsyncImage(
        model = src,
        contentDescription = null,
        contentScale = if (person) ContentScale.Crop else ContentScale.Fit,
        alignment = if (person) Alignment.TopCenter else Alignment.Center,
        modifier = modifier
            .let { if (person) it.size(size) else it.width(size * 1.6f).height(size) }
            .clip(if (person) CircleShape else RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Bandeira(s) de uma região (Ucrânia/Rússia tem duas). */
@Composable
fun RegionFlags(tag: String, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val files = REGION_FLAGS[tag] ?: return
    if (LocalDataSaver.current) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        files.forEach { file ->
            AsyncImage(
                model = flagUrl(file),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(height).width(height * 1.6f).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}
