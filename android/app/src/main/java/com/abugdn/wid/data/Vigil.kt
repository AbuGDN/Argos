package com.abugdn.wid.data

import java.time.Instant
import kotlinx.serialization.Serializable

/** Um alerta guardado no registro de vigília. [key] evita registrar o mesmo alerta duas vezes. */
@Serializable
data class VigilEvent(
    val key: String,
    val time: Long,
    /** "urgent", "spike", "figures", "tension", "truce", "clock", "internet" ou "airspace". */
    val kind: String,
    val title: String,
    val lang: String = "pt",
    val detail: String = "",
    val clusterId: String? = null,
    val region: String? = null,
    /** Intensidade: ritmo da alta incomum (×) ou índice de tensão. */
    val value: Double = 0.0,
)

val VIGIL_KINDS = linkedMapOf(
    "urgent" to "🚨 Urgente",
    "spike" to "📈 Alta incomum",
    "figures" to "⚠ Números divergentes",
    "tension" to "🔥 Tensão crítica",
    "truce" to "🕊 Violação de trégua",
    "clock" to "👁 Relógio do Argos",
    "internet" to "🌐 Queda de internet",
    "airspace" to "✈ Espaço aéreo fechado",
)

const val VIGIL_MAX = 500

object Vigil {
    /** Alertas presentes no feed agora. [day] (aaaa-mm-dd local) limita alertas de região a um por dia. */
    fun detect(feed: Feed, now: Long, day: String): List<VigilEvent> = buildList {
        for (c in feed.clusters) {
            val start = runCatching { Instant.parse(c.published).toEpochMilli() }.getOrDefault(now)
            if (c.urgent) {
                add(VigilEvent("urgent:${c.id}", start, "urgent", c.title, c.lang, "${c.sourcesCount} veículos", clusterId = c.id))
            }
            if (c.truceViolation) {
                val region = c.tags.firstOrNull { tag -> TRUCES.any { tag in it.tags } } ?: c.tags.firstOrNull()
                add(VigilEvent("truce:${c.id}", start, "truce", c.title, c.lang, "violação de cessar-fogo relatada", clusterId = c.id, region = region))
            }
            val divergent = c.figures.filterValues { it.divergent }
            if (divergent.isNotEmpty()) {
                val detail = divergent.entries.joinToString(" · ") { (kind, info) ->
                    val label = if (kind == "killed") "mortos" else "feridos"
                    "$label: ${info.bySource.values.min()} a ${info.bySource.values.max()}"
                }
                add(VigilEvent("figures:${c.id}", now, "figures", c.title, c.lang, detail, clusterId = c.id))
            }
        }
        feed.global?.takeIf { it.level == "alta" || it.level == "crítica" }?.let { g ->
            add(
                VigilEvent(
                    "clock:${g.level}:$day", now, "clock", "Relógio do Argos em ${g.level}",
                    detail = "índice ${g.index} de 100 · puxado por ${TAG_LABELS[g.leader] ?: g.leader}",
                    region = g.leader.ifBlank { null }, value = g.index.toDouble(),
                )
            )
        }
        for ((tag, r) in feed.regions) {
            val name = TAG_LABELS[tag] ?: tag
            if (r.spike) {
                add(
                    VigilEvent(
                        "spike:$tag:$day", now, "spike", "Alta incomum: $name",
                        detail = "ritmo ${"%.1f".format(r.spikeRatio)}× o normal nas últimas 6 h",
                        region = tag, value = r.spikeRatio,
                    )
                )
            }
            if (r.level == "crítica") {
                add(
                    VigilEvent(
                        "tension:$tag:$day", now, "tension", "Tensão crítica: $name",
                        detail = "índice ${r.tension} de 100", region = tag, value = r.tension.toDouble(),
                    )
                )
            }
        }
    }

    /** Sinais do Radar: apagão/queda de internet e espaço aéreo fechado (um registro por episódio). */
    fun detectRadar(radar: RadarData?, now: Long, day: String): List<VigilEvent> = buildList {
        if (radar == null) return@buildList
        fun start(since: String) = runCatching { Instant.parse(since).toEpochMilli() }.getOrDefault(now)
        radar.internet?.countries.orEmpty().filter { it.status == "apagao" || it.status == "queda" }.forEach { c ->
            val label = if (c.status == "apagao") "Apagão de internet" else "Queda de internet"
            add(
                VigilEvent(
                    "internet:${c.code}:${c.since.ifBlank { day }}", start(c.since), "internet", "$label: ${c.name}",
                    detail = "conectividade em ${(c.ratio * 100).toInt()}% do normal (IODA)",
                    region = c.tag.ifBlank { null }, value = c.ratio,
                )
            )
        }
        radar.airspace?.zones.orEmpty().filter { it.status == "fechado" }.forEach { z ->
            add(
                VigilEvent(
                    "airspace:${z.id}:${z.since.ifBlank { day }}", start(z.since), "airspace", "Espaço aéreo fechado: ${z.name}",
                    detail = "${z.flights} aviões no ar; o normal nesse horário é ${z.baseline?.toInt() ?: "?"} (OpenSky)",
                    region = z.tag.ifBlank { null }, value = z.flights.toDouble(),
                )
            )
        }
    }

    /** Junta os alertas novos ao registro (mais recentes primeiro), sem repetir chaves. */
    fun merge(log: List<VigilEvent>, found: List<VigilEvent>): List<VigilEvent> {
        val known = log.mapTo(HashSet()) { it.key }
        val fresh = found.filter { it.key !in known }.distinctBy { it.key }
        if (fresh.isEmpty()) return log
        return (fresh + log).sortedByDescending { it.time }.take(VIGIL_MAX)
    }
}
