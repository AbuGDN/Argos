package com.abugdn.wid.data

import java.time.Instant
import kotlinx.serialization.Serializable

/** Frase entre aspas atribuída a uma pessoa-chave numa manchete ou resumo (já em português). */
@Serializable
data class QuoteEntry(
    val person: String,
    val text: String,
    val source: String,
    val clusterId: String,
    val time: Long,
)

const val QUOTES_MAX = 400

private val QUOTED = Regex("[“\"«]([^”\"»]{15,220})[”\"»]")
private val SPEECH = Regex(
    "\\b(diz|disse|dizem|afirma|afirmou|declara|declarou|alerta|alertou|promete|prometeu|ameaça|ameaçou|" +
        "avisa|avisou|garante|garantiu|acusa|acusou|pede|pediu|chama|chamou|defende|defendeu|nega|negou|segundo)\\b",
    RegexOption.IGNORE_CASE,
)
private val SENTENCE = Regex("(?<=[.!?])\\s+(?=[A-ZÁÉÍÓÚÂÊÔÃÕ“\"«])")

private fun namesIn(text: String): List<Actor> {
    val norm = normalize(text)
    return PEOPLE.filter { p -> p.terms.any { wordRegex(normalize(it)).containsMatchIn(norm) } }
}

object Quotes {
    /**
     * Citações do texto em português [text]. Só atribui quando a frase tem um verbo de fala
     * ("diz", "afirmou"...) e exatamente uma pessoa-chave citada (na frase ou, se não houver, no texto).
     */
    fun extract(text: String): List<Pair<String, String>> {
        if (!text.contains('“') && !text.contains('"') && !text.contains('«')) return emptyList()
        val everyone = namesIn(text)
        return SENTENCE.split(text).flatMap { sentence ->
            val quotes = QUOTED.findAll(sentence).map { it.groupValues[1].trim() }.toList()
            if (quotes.isEmpty() || !SPEECH.containsMatchIn(sentence)) return@flatMap emptyList()
            val here = namesIn(sentence)
            val who = when {
                here.size == 1 -> here.first()
                here.isEmpty() && everyone.size == 1 -> everyone.first()
                else -> return@flatMap emptyList()
            }
            quotes.map { who.key to it }
        }
    }

    /** Citações das histórias do feed; [ptText] devolve o texto em português (original ou traduzido). */
    fun fromFeed(clusters: List<Cluster>, ptText: (String, String) -> String): List<QuoteEntry> = buildList {
        for (c in clusters) {
            for (a in c.articles) {
                val time = runCatching { Instant.parse(a.published).toEpochMilli() }.getOrDefault(0L)
                val text = ptText(a.title, a.lang) + ". " + ptText(a.summary, a.lang)
                extract(text).forEach { (person, quote) -> add(QuoteEntry(person, quote, a.source, c.id, time)) }
            }
        }
    }

    /** Junta ao registro sem repetir a mesma frase da mesma pessoa; mais recentes primeiro. */
    fun merge(log: List<QuoteEntry>, found: List<QuoteEntry>): List<QuoteEntry> {
        fun key(q: QuoteEntry) = q.person + "|" + normalize(q.text)
        val known = log.mapTo(HashSet(), ::key)
        val fresh = found.filter { key(it) !in known }.distinctBy(::key)
        if (fresh.isEmpty()) return log
        return (fresh + log).sortedByDescending { it.time }.take(QUOTES_MAX)
    }
}
