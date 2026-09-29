package com.abugdn.wid.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Espelha o feed.json gerado por backend/wid/build.py. */
@Serializable
data class Feed(
    val version: Int = 1,
    @SerialName("generated_at") val generatedAt: String = "",
    @SerialName("top_of_day") val topOfDay: Cluster? = null,
    /** Índice de tensão e alerta de anomalia por região (tag). */
    val regions: Map<String, RegionStat> = emptyMap(),
    val global: GlobalClock? = null,
    val clusters: List<Cluster> = emptyList(),
)

@Serializable
data class RegionStat(
    val tension: Int = 0,
    val level: String = "baixa",
    val last24: Int = 0,
    val baseline: Double = 0.0,
    val spike: Boolean = false,
    @SerialName("spike_ratio") val spikeRatio: Double = 0.0,
    /** Sinais do Radar (apagão de internet, espaço aéreo fechado) que somaram na tensão. */
    val signals: List<RegionSignal> = emptyList(),
)

/** Números de mortos/feridos citados por veículo. */
@Serializable
data class FigureInfo(
    @SerialName("by_source") val bySource: Map<String, Int> = emptyMap(),
    val divergent: Boolean = false,
)

/** Termos que cada origem usa para a mesma coisa. */
@Serializable
data class FramingGroup(
    val group: String,
    @SerialName("by_origin") val byOrigin: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class SagaRef(
    val id: String,
    val title: String,
    val lang: String = "en",
    val chapter: Int = 1,
    val total: Int = 0,
    val chapters: List<SagaChapter> = emptyList(),
)

@Serializable
data class SagaChapter(
    @SerialName("cluster_id") val clusterId: String,
    val title: String,
    val lang: String = "en",
    val source: String = "",
    val url: String = "",
    val published: String = "",
    @SerialName("sources_count") val sourcesCount: Int = 1,
)

/** stats/first.json: quem publicou primeiro nas histórias grandes (30 dias). */
@Serializable
data class FirstStats(
    @SerialName("big_stories") val bigStories: Int = 0,
    val ranking: List<FirstRank> = emptyList(),
)

@Serializable
data class FirstRank(
    val source: String,
    val firsts: Int = 0,
    val stories: Int = 0,
    @SerialName("lead_min") val leadMin: Int = 0,
    val rate: Double = 0.0,
)

@Serializable
data class Cluster(
    val id: String,
    val title: String,
    val summary: String = "",
    val url: String,
    val source: String,
    val lang: String = "en",
    val image: String? = null,
    val published: String,
    val updated: String = published,
    val tags: List<String> = emptyList(),
    @SerialName("sources_count") val sourcesCount: Int = 1,
    val score: Double = 0.0,
    @SerialName("day_score") val dayScore: Double = 0.0,
    val urgent: Boolean = false,
    val articles: List<ArticleRef> = emptyList(),
    val figures: Map<String, FigureInfo> = emptyMap(),
    val framing: List<FramingGroup> = emptyList(),
    val saga: SagaRef? = null,
    /** "opostos" (imprensa árabe e israelense/americana contam a mesma coisa) ou "um_lado". */
    val sides: String? = null,
    /** Algum veículo relata violação de cessar-fogo/trégua. */
    @SerialName("truce_violation") val truceViolation: Boolean = false,
)

@Serializable
data class ArticleRef(
    val id: String,
    val title: String,
    val summary: String = "",
    val url: String,
    val source: String,
    val lang: String = "en",
    val published: String,
    val image: String? = null,
    val origin: String = "internacional",
    /** Manchetes anteriores do mesmo link (o veículo trocou o título depois de publicar). */
    val edits: List<TitleEdit> = emptyList(),
)

@Serializable
data class TitleEdit(val title: String, val at: String = "")

/** Relógio do Argos: tensão global 0–100. */
@Serializable
data class GlobalClock(val index: Int = 0, val level: String = "baixa", val leader: String = "")

/** Uma leitura: dia (epochDay), história e regiões dela. */
@Serializable
data class ReadEvent(val day: Long, val id: String, val tags: List<String> = emptyList())

/** Pasta e nota pessoal de uma notícia salva. */
@Serializable
data class SavedMeta(val folder: String? = null, val note: String? = null)

/** stats/daily.json: histórias iniciadas por dia e por região. */
@Serializable
data class DailyStats(val days: List<StatsDay> = emptyList())

@Serializable
data class StatsDay(
    val date: String,
    val total: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    /** Maior índice de tensão do dia por região, e o maior do relógio global. */
    val tension: Map<String, Int> = emptyMap(),
    val global: Int = 0,
)

val ORIGIN_LABELS = linkedMapOf(
    "israel" to "Imprensa israelense",
    "arabe" to "Imprensa árabe",
    "eua" to "Imprensa americana",
    "internacional" to "Internacional",
    "brasil" to "Imprensa brasileira",
)

/** As 5 principais dos últimos 7 dias (uma por dia, as de maior peso). */
fun weekTop(days: List<HistoryDay>): List<HistoryDay> =
    days.sortedByDescending { it.date }.take(7).sortedByDescending { it.top.dayScore }.take(5)

/** history/AAAA-MM-DD.json: a principal de um dia. */
@Serializable
data class HistoryDay(val date: String, val top: Cluster)

@Serializable
data class HistoryIndex(val days: List<String> = emptyList())

/** Texto completo baixado e guardado no aparelho. */
@Serializable
data class FullText(
    val url: String,
    val source: String,
    val lang: String,
    val paragraphs: List<String>,
    val translated: List<String>? = null,
    /** Versão do glossário usada na tradução; menor que a atual = retraduzir. */
    val glossary: Int = 0,
    val fetchedAt: Long = System.currentTimeMillis(),
)

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

val TAG_LABELS = linkedMapOf(
    "israel" to "Israel",
    "gaza" to "Gaza",
    "cisjordania" to "Cisjordânia",
    "libano" to "Líbano",
    "ira" to "Irã",
    "iemen" to "Iêmen",
    "siria" to "Síria",
    "iraque" to "Iraque",
    "eua" to "EUA",
    "ucrania_russia" to "Ucrânia/Rússia",
    "sudao" to "Sudão",
    "egito" to "Egito",
    "jordania" to "Jordânia",
    "arabia" to "Arábia Saudita",
    "emirados" to "Emirados Árabes",
    "golfo" to "Catar e Golfo",
    "turquia" to "Turquia",
    "somalia" to "Somália",
    "mediterraneo" to "Mediterrâneo",
    "asia" to "Ásia",
    "africa" to "África",
    "otan" to "OTAN",
    "ice" to "ICE (imigração EUA)",
    "brasil" to "Brasil",
)
