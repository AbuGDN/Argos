package com.abugdn.wid.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espelha o radar.json gerado por backend/wid/radar.py: dados de fora da imprensa.
 * Toda seção é opcional (a coleta daquela seção pode ter falhado ou ainda não ter rodado).
 */
@Serializable
data class RadarData(
    @SerialName("generated_at") val generatedAt: String = "",
    val internet: InternetSection? = null,
    val airspace: AirspaceSection? = null,
    val fires: FiresSection? = null,
    val straits: StraitsSection? = null,
    val markets: MarketsSection? = null,
    val humanitarian: HumanitarianSection? = null,
    val losses: LossesSection? = null,
    val official: FeedSection? = null,
    val sanctions: FeedSection? = null,
    val analysis: FeedSection? = null,
    val crisiswatch: CrisisWatchSection? = null,
    val factcheck: FeedSection? = null,
    val predictions: PredictionsSection? = null,
    val military: MilitarySection? = null,
    val carriers: CarriersSection? = null,
    val frontline: FrontlineSection? = null,
    val status: Map<String, SectionStatus> = emptyMap(),
)

/** Aviões militares com transponder ligado (adsb.lol), por zona. */
@Serializable
data class MilitarySection(
    val updated: String = "",
    val zones: List<MilitaryZone> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)

@Serializable
data class MilitaryZone(
    val id: String,
    val name: String,
    val tag: String = "",
    val count: Int = 0,
    /** Reabastecedores, aviões-radar, espionagem e bombardeiros: os que mais dizem algo. */
    val key: Int = 0,
    val baseline: Double? = null,
    val unusual: Boolean = false,
    val counts: Map<String, Int> = emptyMap(),
    val aircraft: List<MilitaryAircraft> = emptyList(),
)

@Serializable
data class MilitaryAircraft(
    val hex: String = "",
    val callsign: String = "",
    val type: String = "",
    val reg: String = "",
    val category: String = "outro",
    val lat: Double,
    val lon: Double,
    val alt: Double = 0.0,
    val track: Double? = null,
)

/** Porta-aviões dos EUA (USNI News Fleet Tracker, semanal). */
@Serializable
data class CarriersSection(
    val updated: String = "",
    val title: String = "",
    val url: String = "",
    val ships: List<Carrier> = emptyList(),
)

@Serializable
data class Carrier(
    val hull: String,
    val name: String,
    val place: String = "",
    val lat: Double,
    val lon: Double,
    val status: String = "",
    val text: String = "",
)

/** Linha de frente na Ucrânia (DeepStateMap): números; os polígonos vêm de frontline.json. */
@Serializable
data class FrontlineSection(
    val updated: String = "",
    @SerialName("occupied_km2") val occupiedKm2: Long = 0,
    @SerialName("grey_km2") val greyKm2: Long = 0,
    @SerialName("change_7d_km2") val change7dKm2: Long? = null,
    val history: List<List<kotlinx.serialization.json.JsonElement>> = emptyList(),
    val changes: List<FrontlineChange> = emptyList(),
)

@Serializable
data class FrontlineChange(val text: String, val at: String = "")

/** frontline.json: polígonos simplificados, cada um uma lista de anéis de [lat, lon]. */
@Serializable
data class FrontlineShapes(
    val occupied: List<List<List<List<Double>>>> = emptyList(),
    val grey: List<List<List<List<Double>>>> = emptyList(),
    val updated: String = "",
)

val MILITARY_ICONS = mapOf(
    "reabastecedor" to "⛽", "radar" to "📡", "espionagem" to "🛰", "bombardeiro" to "💣",
    "caça" to "✈", "transporte" to "📦", "outro" to "•",
)

@Serializable
data class SectionStatus(val ok: Boolean = false, val checked: String = "", val error: String = "")

@Serializable
data class InternetSection(val updated: String = "", val countries: List<InternetCountry> = emptyList())

@Serializable
data class InternetCountry(
    val code: String,
    val name: String,
    val tag: String = "",
    /** "normal", "queda", "apagao" ou "sem_dados". */
    val status: String = "sem_dados",
    /** Sinal da última hora dividido pelo normal das 24 h anteriores. */
    val ratio: Double = 1.0,
    val since: String = "",
    /** 24 pontos (um por hora), 1.0 = normal. */
    val spark: List<Double?> = emptyList(),
)

@Serializable
data class AirspaceSection(val updated: String = "", val zones: List<AirZone> = emptyList())

@Serializable
data class AirZone(
    val id: String,
    val name: String,
    val tag: String = "",
    val flights: Int = 0,
    val baseline: Double? = null,
    /** "normal", "reduzido", "fechado", "coletando", "pouco_trafego" ou "sem_dados". */
    val status: String = "sem_dados",
    val since: String = "",
)

@Serializable
data class FiresSection(
    val updated: String = "",
    @SerialName("missing_key") val missingKey: Boolean = false,
    val zones: List<FireZone> = emptyList(),
)

@Serializable
data class FireZone(
    val id: String,
    val name: String,
    val tag: String = "",
    val count: Int = 0,
    val baseline: Double? = null,
    /** [lat, lon, potência radiativa em MW]. */
    val points: List<List<Double>> = emptyList(),
    val error: Boolean = false,
)

@Serializable
data class StraitsSection(val updated: String = "", val items: List<Strait> = emptyList())

@Serializable
data class Strait(
    val id: String,
    val name: String,
    val tag: String = "",
    val date: String = "",
    val avg7: Double? = null,
    val avg90: Double? = null,
    @SerialName("year_ago") val yearAgo: Double? = null,
    val spark: List<Double> = emptyList(),
    val error: Boolean = false,
)

@Serializable
data class MarketsSection(val updated: String = "", val items: List<Quote> = emptyList())

@Serializable
data class Quote(
    val id: String,
    val name: String,
    val unit: String = "",
    val digits: Int = 2,
    val price: Double? = null,
    @SerialName("change_pct") val changePct: Double? = null,
    @SerialName("change_week_pct") val changeWeekPct: Double? = null,
    val time: String = "",
    val spark: List<Double> = emptyList(),
    val error: Boolean = false,
)

@Serializable
data class HumanitarianSection(
    val updated: String = "",
    val palestine: List<HumanFigure> = emptyList(),
    val idps: List<IdpFigure> = emptyList(),
)

@Serializable
data class HumanFigure(val region: String, val label: String, val value: Long, val date: String = "")

@Serializable
data class IdpFigure(val location: String, val name: String, val tag: String = "", val value: Long, val date: String = "")

@Serializable
data class LossesSection(val updated: String = "", val date: String = "", val day: Int? = null, val items: List<LossItem> = emptyList())

@Serializable
data class LossItem(val key: String, val label: String, val total: Long, val increase: Long = 0)

/** Itens de RSS: fontes oficiais, sanções, análises e checagens. */
@Serializable
data class FeedSection(
    val updated: String = "",
    val items: List<RadarItem> = emptyList(),
    val sources: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class RadarItem(
    val id: String,
    val title: String,
    val summary: String = "",
    val url: String,
    val source: String,
    val lang: String = "en",
    val published: String = "",
    val tags: List<String> = emptyList(),
    /** Só nas checagens: histórias do feed sobre o mesmo assunto. */
    val clusters: List<String> = emptyList(),
)

@Serializable
data class CrisisWatchSection(
    val updated: String = "",
    val month: String = "",
    val url: String = "",
    /** Quando a página bloqueia, vem do RSS: título e resumo da edição do mês (em inglês). */
    val title: String = "",
    val summary: String = "",
    val deteriorated: List<CwCountry> = emptyList(),
    val improved: List<CwCountry> = emptyList(),
    val risk: List<CwCountry> = emptyList(),
    val resolution: List<CwCountry> = emptyList(),
)

@Serializable
data class CwCountry(val name: String, val tag: String = "")

@Serializable
data class PredictionsSection(val updated: String = "", val events: List<PredictionEvent> = emptyList())

@Serializable
data class PredictionEvent(
    val id: String,
    val title: String,
    val url: String,
    val volume: Double = 0.0,
    val volume24: Double = 0.0,
    val end: String = "",
    val tags: List<String> = emptyList(),
    val markets: List<PredictionMarket> = emptyList(),
)

@Serializable
data class PredictionMarket(val label: String, val prob: Double, val change: Double? = null)

/** Sinal do Radar que subiu a tensão de uma região (feed.json → regions → signals). */
@Serializable
data class RegionSignal(val kind: String, val status: String, val name: String = "")

val INTERNET_STATUS = mapOf(
    "normal" to "normal",
    "queda" to "queda",
    "apagao" to "apagão",
    "sem_dados" to "sem dados",
)

val AIRSPACE_STATUS = mapOf(
    "normal" to "normal",
    "reduzido" to "reduzido",
    "fechado" to "fechado",
    "coletando" to "aprendendo o normal",
    "pouco_trafego" to "pouco tráfego",
    "sem_dados" to "sem dados",
)

fun RegionSignal.describe(): String = when (kind) {
    "internet" -> (if (status == "apagao") "🌐 Apagão de internet" else "🌐 Queda de internet") + " · $name"
    "airspace" -> (if (status == "fechado") "✈ Espaço aéreo fechado" else "✈ Espaço aéreo reduzido") + " · $name"
    else -> "$kind: $status"
}

/** Textos em outro idioma que o app traduz (títulos e resumos). */
fun RadarData.foreignTexts(): Set<String> = buildSet {
    listOfNotNull(official, sanctions, analysis, factcheck).forEach { s ->
        s.items.filter { it.lang != "pt" }.forEach { i ->
            add(i.title)
            if (i.summary.isNotBlank()) add(i.summary)
        }
    }
    crisiswatch?.let { cw -> listOf(cw.title, cw.summary).filter { it.isNotBlank() }.forEach(::add) }
    frontline?.changes?.forEach { add(it.text) }
    carriers?.ships?.forEach { if (it.text.isNotBlank()) add(it.text) }
    predictions?.events?.forEach { e ->
        add(e.title)
        e.markets.forEach { m -> if (m.label != e.title) add(m.label) }
    }
}
