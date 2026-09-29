package com.abugdn.wid.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.time.Instant

// --- Sirenes em Israel (radar.json -> sirens; ao vivo pela API do Tzeva Adom) ------------

@Serializable
data class SirensSection(
    val updated: String = "",
    @SerialName("count_24h") val count24h: Int = 0,
    val events: List<SirenEvent> = emptyList(),
    val last: String? = null,
    val days: List<SirenDay> = emptyList(),
    /** Locais com sirene por hora do dia (horário de Israel), 7 dias. */
    val hours: List<Int> = emptyList(),
)

@Serializable
data class SirenEvent(val time: String, val threat: String = "", val cities: List<SirenCity> = emptyList())

@Serializable
data class SirenCity(val name: String, val lat: Double? = null, val lon: Double? = null)

@Serializable
data class SirenDay(val date: String, val count: Int = 0)

/** Mesmos códigos de ameaça do backend (radar.py SIREN_THREATS). */
val SIREN_THREATS = mapOf(
    0 to "foguetes e mísseis", 1 to "vazamento de material perigoso", 2 to "infiltração de terroristas", 3 to "terremoto",
    4 to "tsunami", 5 to "aeronave hostil (drone)", 6 to "evento radiológico", 7 to "arma não convencional", 8 to "alerta geral",
)

const val LIVE_SIRENS_URL = "https://api.tzevaadom.co.il/notifications"

/**
 * Alertas ativos agora (resposta de [LIVE_SIRENS_URL]), com os nomes traduzidos pelo
 * dicionário de cidades (sirens_cities.json: hebraico -> [inglês, lat, lon]).
 */
fun parseLiveSirens(root: JsonElement, cities: Map<String, List<JsonElement>>, now: Instant = Instant.now()): List<SirenEvent> =
    (root as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        if (o["isDrill"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
        val names = (o["cities"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        if (names.isEmpty()) return@mapNotNull null
        val time = o["time"]?.jsonPrimitive?.longOrNull?.let { Instant.ofEpochSecond(it) } ?: now
        SirenEvent(
            time = time.toString(),
            threat = SIREN_THREATS[o["threat"]?.jsonPrimitive?.intOrNull ?: 0] ?: "alerta",
            cities = names.map { he ->
                val c = cities[he]
                SirenCity(
                    name = c?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: he,
                    lat = c?.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                    lon = c?.getOrNull(2)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                )
            },
        )
    }

/** sirens_cities.json -> mapa hebraico -> [inglês, lat, lon]. */
fun parseSirenCities(root: JsonElement): Map<String, List<JsonElement>> =
    ((root as? JsonObject)?.get("cities") as? JsonObject).orEmpty().mapValues { (_, v) -> runCatching { v.jsonArray.toList() }.getOrDefault(emptyList()) }

// --- Sismógrafo (radar.json -> quakes) ---------------------------------------------------

@Serializable
data class QuakesSection(val updated: String = "", val items: List<Quake> = emptyList(), val alerts: Int = 0)

@Serializable
data class Quake(
    val id: String? = null,
    val mag: Double? = null,
    val place: String = "",
    val time: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val depth: Double? = null,
    val type: String = "earthquake",
    val zone: String = "",
    val tag: String? = null,
    val shallow: Boolean = false,
    val site: QuakeSite? = null,
    val alert: Boolean = false,
    val url: String = "",
)

@Serializable
data class QuakeSite(val name: String, val km: Int = 0)

val QUAKE_TYPES = mapOf(
    "earthquake" to "terremoto", "explosion" to "explosão", "nuclear explosion" to "explosão nuclear",
    "quarry blast" to "explosão em pedreira", "mining explosion" to "explosão em mina", "rock burst" to "ruptura de rocha",
)

// --- Alertas de viagem (radar.json -> travel) --------------------------------------------

@Serializable
data class TravelSection(
    val updated: String = "",
    val items: List<TravelItem> = emptyList(),
    val changes: List<TravelChange> = emptyList(),
    val level4: Int = 0,
)

@Serializable
data class TravelItem(val country: String, val name: String, val tag: String? = null, val level: Int, val label: String = "")

@Serializable
data class TravelChange(
    val country: String,
    val name: String = country,
    val tag: String? = null,
    val from: Int,
    val to: Int,
    val date: String,
)

val TRAVEL_LEVELS = mapOf(1 to "precauções normais", 2 to "mais cautela", 3 to "reconsidere a viagem", 4 to "não viaje")

// --- Ultimatos e prazos (deadlines.json) --------------------------------------------------

@Serializable
data class DeadlinesFile(val deadlines: List<Deadline> = emptyList())

@Serializable
data class Deadline(
    val id: String,
    @SerialName("cluster_id") val clusterId: String,
    val title: String,
    val lang: String = "pt",
    val quote: String = "",
    val span: String = "",
    val start: String,
    val due: String,
    val tags: List<String> = emptyList(),
    val after: DeadlineAfter? = null,
) {
    fun dueInstant(): Instant? = runCatching { Instant.parse(due) }.getOrNull()
    fun startInstant(): Instant? = runCatching { Instant.parse(start) }.getOrNull()
    fun expired(now: Instant = Instant.now()) = dueInstant()?.isBefore(now) ?: false

    /** Fração do prazo que já passou (0..1), para a barra. */
    fun progress(now: Instant = Instant.now()): Float {
        val s = startInstant() ?: return 0f
        val d = dueInstant() ?: return 0f
        val total = Duration.between(s, d).seconds.coerceAtLeast(1)
        return (Duration.between(s, now).seconds.toFloat() / total).coerceIn(0f, 1f)
    }
}

@Serializable
data class DeadlineAfter(
    @SerialName("cluster_id") val clusterId: String,
    val title: String,
    val lang: String = "pt",
    val published: String = "",
)

/** "faltam 1 d 4 h", "faltam 35 min", "venceu há 2 d". */
fun countdown(due: Instant, now: Instant = Instant.now()): String {
    val left = Duration.between(now, due)
    val mins = left.abs().toMinutes()
    val text = when {
        mins >= 1440 -> "${mins / 1440} d ${(mins % 1440) / 60} h"
        mins >= 60 -> "${mins / 60} h ${mins % 60} min"
        else -> "${mins.coerceAtLeast(1)} min"
    }
    return if (left.isNegative) "venceu há $text" else "faltam $text"
}

// --- Termômetro diplomático (diplomacy.json) e status das fontes (sources_status.json) -----

@Serializable
data class DiplomacyFile(val labels: Map<String, String> = emptyMap(), val regions: Map<String, DiplomacyRegion> = emptyMap())

@Serializable
data class DiplomacyRegion(
    /** 0 = só cooperação, 50 = equilíbrio, 100 = só hostilidade. */
    val index: Int = 50,
    val label: String = "",
    val hostile: Int = 0,
    val coop: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    val events: List<DiplomacyEvent> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class DiplomacyEvent(
    @SerialName("cluster_id") val clusterId: String,
    val title: String,
    val lang: String = "en",
    val published: String = "",
    val tags: List<String> = emptyList(),
    val cats: List<String> = emptyList(),
)

/** Categorias que esquentam (true) ou esfriam (false). */
val DIPLOMACY_HOSTILE = mapOf(
    "embaixador" to true, "ruptura" to true, "sancoes" to true, "fronteira" to true, "emergencia" to true, "ameaca" to true,
    "negociacao" to false, "acordo" to false, "encontro" to false, "reaproximacao" to false,
)

@Serializable
data class SourcesStatus(@SerialName("generated_at") val generatedAt: String = "", val sources: Map<String, SourceStatus> = emptyMap())

@Serializable
data class SourceStatus(val ok: Boolean = false, val items: Int = 0, val error: String? = null)

// --- Conselho de Segurança da ONU (radar.json -> unsc) -----------------------------------

@Serializable
data class UnscSection(val updated: String = "", val items: List<UnscItem> = emptyList(), val counts: Map<String, Int> = emptyMap())

@Serializable
data class UnscItem(
    val title: String,
    val url: String = "",
    val date: String = "",
    /** "veto", "aprovada", "votacao", "declaracao" ou "reuniao". */
    val kind: String = "reuniao",
    val source: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("veto_by") val vetoBy: List<String> = emptyList(),
    val summary: String = "",
)

val UNSC_KINDS = linkedMapOf(
    "veto" to "🚫 Veto ou rejeitada", "aprovada" to "✅ Resolução aprovada", "votacao" to "🗳 Votação prevista",
    "declaracao" to "📜 Declaração", "reuniao" to "🏛 Reunião",
)

// --- Tempo nas zonas de conflito (radar.json -> weather) ------------------------------------

@Serializable
data class WeatherSection(val updated: String = "", val places: List<WeatherPlace> = emptyList())

@Serializable
data class WeatherPlace(
    val name: String,
    val tag: String? = null,
    val temp: Double? = null,
    val wind: Double? = null,
    val gusts: Double? = null,
    val code: Int = 0,
    val desc: String = "",
    val rain: Double? = null,
    val visibility: Double? = null,
    val cloud: Double? = null,
    val dust: Double? = null,
    val flags: List<String> = emptyList(),
)

// --- Placar aéreo da Ucrânia (airwar.json) -------------------------------------------------

@Serializable
data class AirwarFile(val days: List<AirwarDay> = emptyList())

@Serializable
data class AirwarDay(
    val date: String,
    val drones: Int = 0,
    val missiles: Int = 0,
    val downed: Int? = null,
    @SerialName("cluster_id") val clusterId: String? = null,
    val title: String = "",
    val lang: String = "en",
)
