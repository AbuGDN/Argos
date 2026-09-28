package com.abugdn.wid.data

/**
 * "O que mudou desde a última vez" no Radar: um retrato resumido de cada seção (só o que
 * importa: status, alerta, contagem) é guardado quando a pessoa sai da aba e comparado na volta.
 */
fun radarFingerprint(r: RadarData): Map<String, String> = buildMap {
    r.internet?.countries.orEmpty().filter { it.status != "sem_dados" }.forEach { put("net|${it.name}", it.status) }
    r.airspace?.zones.orEmpty().filter { it.status == "normal" || it.status == "reduzido" || it.status == "fechado" }
        .forEach { put("air|${it.name}", it.status) }
    r.fires?.zones.orEmpty().filter { !it.error }.forEach { z ->
        val high = z.baseline != null && z.baseline >= 3 && z.count >= 2 * z.baseline
        put("fire|${z.name}", if (high) "alto" else "normal")
    }
    r.straits?.items.orEmpty().filter { it.avg7 != null }.forEach { s ->
        val low = s.avg90 != null && s.avg90 > 5 && s.avg7!! < s.avg90 * 0.6
        put("strait|${s.name}", if (low) "queda" else "normal")
    }
    r.military?.zones.orEmpty().forEach { put("mil|${it.name}", if (it.unusual) "incomum" else "normal") }
    r.carriers?.let { c -> put("carriers", c.ships.count { it.lat in 10.0..40.0 && it.lon in 25.0..65.0 }.toString()) }
    r.frontline?.let { if (it.occupiedKm2 > 0) put("front", it.occupiedKm2.toString()) }
    r.crisiswatch?.let { if (it.month.isNotBlank()) put("cw", it.month) }
    FEED_SECTIONS.forEach { (key, _) ->
        feedOf(r, key)?.items.orEmpty().maxOfOrNull { it.published }?.takeIf { it.isNotBlank() }?.let { put("feed|$key", it) }
    }
}

private val FEED_SECTIONS = listOf(
    "official" to "🏛 Publicações oficiais novas: %d",
    "sanctions" to "⛔ Anúncios de sanções novos: %d",
    "analysis" to "🧠 Análises novas: %d",
    "factcheck" to "✅ Checagens novas: %d",
)

private fun feedOf(r: RadarData, key: String): FeedSection? = when (key) {
    "official" -> r.official
    "sanctions" -> r.sanctions
    "analysis" -> r.analysis
    else -> r.factcheck
}

/** Mudança para mostrar: texto e se é para alarmar (vermelho). */
data class RadarChange(val text: String, val alert: Boolean)

/** Diferenças entre o Radar de agora e o retrato [before]. Retrato vazio (primeira visita) = nada. */
fun radarChanges(r: RadarData, before: Map<String, String>): List<RadarChange> {
    if (before.isEmpty()) return emptyList()
    val now = radarFingerprint(r)
    val out = mutableListOf<RadarChange>()
    for ((key, value) in now) {
        val old = before[key] ?: continue
        if (old == value) continue
        val kind = key.substringBefore('|')
        val name = key.substringAfter('|')
        when (kind) {
            "net" -> out += RadarChange(
                "🌐 Internet · $name: ${INTERNET_STATUS[old] ?: old} → ${INTERNET_STATUS[value] ?: value}",
                value == "apagao" || value == "queda",
            )
            "air" -> out += RadarChange(
                "✈ Espaço aéreo · $name: ${AIRSPACE_STATUS[old] ?: old} → ${AIRSPACE_STATUS[value] ?: value}",
                value == "fechado" || value == "reduzido",
            )
            "fire" -> out += RadarChange(
                if (value == "alto") "🔥 Focos de calor acima do normal: $name" else "🔥 Focos de calor voltaram ao normal: $name",
                value == "alto",
            )
            "strait" -> out += RadarChange(
                if (value == "queda") "🚢 Tráfego de navios em queda: $name" else "🚢 Tráfego de navios normalizou: $name",
                value == "queda",
            )
            "mil" -> out += RadarChange(
                if (value == "incomum") "✈ Aviões militares acima do normal: $name" else "✈ Aviões militares voltaram ao normal: $name",
                value == "incomum",
            )
            "carriers" -> out += RadarChange(
                "⚓ Porta-aviões perto do Oriente Médio: $old → $value",
                (value.toIntOrNull() ?: 0) > (old.toIntOrNull() ?: 0),
            )
            "front" -> {
                val d = (value.toLongOrNull() ?: 0) - (old.toLongOrNull() ?: 0)
                if (d != 0L) out += RadarChange(
                    if (d > 0) "🗺 Frente na Ucrânia: Rússia avançou ${km2(d)}" else "🗺 Frente na Ucrânia: Ucrânia retomou ${km2(-d)}",
                    d > 0,
                )
            }
            "cw" -> out += RadarChange("📉 CrisisWatch de $value saiu", false)
            "feed" -> {
                val template = FEED_SECTIONS.firstOrNull { it.first == name }?.second ?: continue
                val n = feedOf(r, name)?.items.orEmpty().count { it.published > old }
                if (n > 0) out += RadarChange(template.format(n), name == "factcheck")
            }
        }
    }
    return out.sortedByDescending { it.alert }
}

private fun km2(n: Long) = java.text.NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR")).format(n) + " km²"
