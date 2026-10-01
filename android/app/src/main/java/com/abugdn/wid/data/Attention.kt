package com.abugdn.wid.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Atenção do mundo (radar.json -> attention): visitas diárias de pessoas aos artigos da Wikipédia de
 * cada guerra, somando os idiomas de [langs]. O [id] de cada guerra é o mesmo de [WAR_DEATHS].
 */
@Serializable
data class AttentionSection(
    val updated: String = "",
    val start: String = "",
    val end: String = "",
    val langs: List<String> = emptyList(),
    val conflicts: List<AttentionConflict> = emptyList(),
)

@Serializable
data class AttentionConflict(
    val id: String = "",
    val name: String = "",
    val tag: String? = null,
    @SerialName("views_7d") val views7d: Long = 0,
    @SerialName("views_prev_7d") val viewsPrev7d: Long = 0,
    @SerialName("views_total") val viewsTotal: Long = 0,
    val series: List<List<JsonElement>> = emptyList(),
    @SerialName("by_lang") val byLang: Map<String, Long> = emptyMap(),
) {
    /** Variação da última semana sobre a anterior, em %; null sem base de comparação. */
    val change: Double? get() = if (viewsPrev7d > 0) (views7d - viewsPrev7d) * 100.0 / viewsPrev7d else null
}

/** Nome em português dos idiomas da Wikipédia usados na atenção. */
val WIKI_LANG_PT = mapOf(
    "en" to "inglês", "es" to "espanhol", "fr" to "francês", "de" to "alemão", "pt" to "português",
    "ar" to "árabe", "ru" to "russo", "ja" to "japonês",
)

/** Uma guerra na tela "Guerras esquecidas": mortes do UCDP e visitas da Wikipédia (se houver). */
data class ForgottenRow(val war: WarDeaths, val views: Long?, val deathShare: Double, val attentionShare: Double?)

/**
 * Parte de cada guerra nas mortes (último ano do UCDP) e na atenção (visitas no período), entre as
 * guerras acompanhadas. Ordena da mais esquecida (muita morte, pouca atenção) para a menos.
 */
fun forgottenRows(attention: AttentionSection?): List<ForgottenRow> {
    val views = attention?.conflicts?.associate { it.id to it.viewsTotal }.orEmpty()
    val totalDeaths = WAR_DEATHS.sumOf { it.latest }.coerceAtLeast(1)
    val withViews = WAR_DEATHS.filter { it.id in views }
    val totalViews = withViews.sumOf { views[it.id] ?: 0L }.coerceAtLeast(1)
    return WAR_DEATHS.map { w ->
        val v = views[w.id]
        ForgottenRow(w, v, w.latest * 100.0 / totalDeaths, v?.let { it * 100.0 / totalViews })
    }.sortedBy { r -> r.attentionShare?.let { it / r.deathShare.coerceAtLeast(0.01) } ?: Double.MAX_VALUE }
}
