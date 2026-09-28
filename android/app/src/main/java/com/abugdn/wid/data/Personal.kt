package com.abugdn.wid.data

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Dossiê: um assunto que a pessoa acompanha. Toda sincronização junta as notícias novas que
 * batem com os termos, montando uma linha do tempo que fica guardada no aparelho.
 */
@Serializable
data class Dossier(
    val id: String,
    val title: String,
    /** Termos que puxam notícias (qualquer um basta). */
    val terms: List<String>,
    val notes: String = "",
    val created: Long = System.currentTimeMillis(),
    val entries: List<DossierEntry> = emptyList(),
)

@Serializable
data class DossierEntry(
    val clusterId: String,
    val title: String,
    val lang: String = "pt",
    val source: String = "",
    val url: String = "",
    val time: Long,
)

const val DOSSIER_MAX_ENTRIES = 300

object Dossiers {
    /** Notícias do feed que citam algum termo do dossiê (título/resumo, original ou traduzido). */
    fun matches(d: Dossier, clusters: List<Cluster>, translated: (String) -> String): List<DossierEntry> {
        val terms = d.terms.map { normalize(it.trim()) }.filter { it.length >= 2 }
        if (terms.isEmpty()) return emptyList()
        return clusters.mapNotNull { c ->
            val text = normalize("${c.title}\n${c.summary}\n${translated(c.title)}\n${translated(c.summary)}")
            if (terms.none { wordRegex(it, open = true).containsMatchIn(text) }) return@mapNotNull null
            val time = runCatching { Instant.parse(c.published).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            DossierEntry(c.id, c.title, c.lang, c.source, c.url, time)
        }
    }

    /** Junta as novas à linha do tempo (sem repetir), mais recentes primeiro. */
    fun merge(d: Dossier, found: List<DossierEntry>): Dossier {
        val known = d.entries.mapTo(HashSet()) { it.clusterId }
        val fresh = found.filter { it.clusterId !in known }
        if (fresh.isEmpty()) return d
        return d.copy(entries = (fresh + d.entries).sortedByDescending { it.time }.take(DOSSIER_MAX_ENTRIES))
    }
}

/** Palpite da pessoa, conferido na data marcada. [hit] = null enquanto não foi conferido. */
@Serializable
data class Prediction(
    val id: String,
    val text: String,
    /** aaaa-mm-dd */
    val deadline: String,
    /** 50–100: quanta certeza a pessoa tinha. */
    val confidence: Int = 70,
    val created: Long = System.currentTimeMillis(),
    val hit: Boolean? = null,
    val notified: Boolean = false,
) {
    fun due(today: LocalDate = LocalDate.now()): Boolean =
        hit == null && runCatching { !LocalDate.parse(deadline).isAfter(today) }.getOrDefault(false)
}

/** Placar: acertos entre os conferidos. */
data class PredictionScore(val hits: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else hits * 100 / total
}

fun score(list: List<Prediction>): PredictionScore {
    val done = list.filter { it.hit != null }
    return PredictionScore(done.count { it.hit == true }, done.size)
}
