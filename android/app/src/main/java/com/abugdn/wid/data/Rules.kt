package com.abugdn.wid.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Duration
import java.time.Instant

/**
 * Regras de alerta da pessoa: "SE condição E condição → avisar". A regra avisa quando passa a
 * valer (e não de novo enquanto continuar valendo); volta a poder avisar depois que deixa de valer.
 */
@Serializable
data class AlertRule(
    val id: String,
    val name: String,
    val conditions: List<RuleCondition> = emptyList(),
    /** Se valia na última checagem (para avisar só na virada). */
    val active: Boolean = false,
)

@Serializable
data class RuleCondition(
    /** "tensao", "radar", "mercado" ou "palavra". */
    val kind: String,
    val region: String? = null,
    val market: String? = null,
    /** "acima" ou "abaixo" (mercado). */
    val op: String = "acima",
    val value: Double = 0.0,
    val word: String? = null,
)

val RULE_KINDS = linkedMapOf(
    "tensao" to "Tensão da região",
    "radar" to "Radar em alerta na região",
    "mercado" to "Mercado",
    "palavra" to "Palavra numa manchete (6 h)",
)

fun RuleCondition.describe(radar: RadarData?): String = when (kind) {
    "tensao" -> "tensão de ${TAG_LABELS[region] ?: region} ≥ ${value.toInt()}"
    "radar" -> "Radar em alerta em ${TAG_LABELS[region] ?: region}"
    "mercado" -> {
        val name = radar?.markets?.items?.firstOrNull { it.id == market }?.name ?: market
        "$name $op de ${"%.2f".format(java.util.Locale("pt", "BR"), value)}"
    }
    "palavra" -> "manchete com “$word”"
    else -> kind
}

fun AlertRule.describe(radar: RadarData?): String = conditions.joinToString(" E ") { it.describe(radar) }

/** Regiões com algum sinal do Radar em alerta agora. */
fun radarAlertTags(radar: RadarData?, now: Instant = Instant.now()): Set<String> {
    if (radar == null) return emptySet()
    return buildSet {
        radar.internet?.countries.orEmpty().filter { it.status == "apagao" || it.status == "queda" }.forEach { add(it.tag) }
        radar.airspace?.zones.orEmpty().filter { it.status == "fechado" || it.status == "reduzido" }.forEach { add(it.tag) }
        radar.fires?.zones.orEmpty().filter { z -> z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline }.forEach { add(it.tag) }
        radar.straits?.items.orEmpty().filter { s -> s.avg7 != null && s.avg90 != null && s.avg90 > 5 && s.avg7 < s.avg90 * 0.6 }.forEach { add(it.tag) }
        radar.military?.zones.orEmpty().filter { it.unusual }.forEach { add(it.tag) }
        radar.quakes?.items.orEmpty().filter { it.alert }.forEach { q -> q.tag?.let { add(it) } }
        radar.incidents.forEach { add(it.tag) }
        val last = radar.sirens?.last?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (last != null && Duration.between(last, now).toHours() < 3) add("israel")
    }.filter { it.isNotBlank() }.toSet()
}

/** A condição vale agora? */
fun RuleCondition.holds(feed: Feed?, radar: RadarData?, translate: (String) -> String, now: Instant = Instant.now()): Boolean = when (kind) {
    "tensao" -> (feed?.regions?.get(region)?.tension ?: 0) >= value
    "radar" -> region != null && region in radarAlertTags(radar, now)
    "mercado" -> radar?.markets?.items?.firstOrNull { it.id == market }?.price?.let { p -> if (op == "abaixo") p <= value else p >= value } ?: false
    "palavra" -> {
        val w = word?.trim().orEmpty()
        w.isNotEmpty() && feed?.clusters.orEmpty().any { c ->
            val recent = runCatching { Duration.between(Instant.parse(c.published), now).toHours() < 6 }.getOrDefault(false)
            recent && wordRegex(normalize(w)).containsMatchIn(normalize(c.title + " " + translate(c.title)))
        }
    }
    else -> false
}

fun AlertRule.holds(feed: Feed?, radar: RadarData?, translate: (String) -> String): Boolean =
    conditions.isNotEmpty() && conditions.all { it.holds(feed, radar, translate) }

val RULES_SERIALIZER = ListSerializer(AlertRule.serializer())
