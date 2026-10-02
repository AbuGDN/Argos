package com.abugdn.wid.data

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private val HEBREW = Regex("[\\u05d0-\\u05ea]")
private val ARABIC = Regex("[\\u0621-\\u064a]")

/** Idioma de origem pelo alfabeto: hebraico, árabe ou (o resto) inglês. */
internal fun sourceLanguage(text: String): String = when {
    HEBREW.containsMatchIn(text) -> TranslateLanguage.HEBREW
    ARABIC.containsMatchIn(text) -> TranslateLanguage.ARABIC
    else -> TranslateLanguage.ENGLISH
}

/**
 * Tradução para português no próprio aparelho (ML Kit), de inglês, hebraico e árabe.
 * Cada modelo (~30 MB) é baixado uma vez, só quando aparece um texto naquele idioma;
 * depois funciona offline e sem limite.
 */
class Translator(private val storage: Storage) {
    private val clients = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()
    private val ready = mutableSetOf<String>()
    private val mutex = Mutex()

    /** Com a economia de dados ligada, os modelos só baixam no Wi-Fi. */
    @Volatile var wifiOnly = false

    /** Texto traduzido se já estiver em cache; senão, o original. */
    fun cached(text: String): String = storage.translations[text] ?: text

    fun display(text: String, lang: String): String = if (lang == "pt") text else cached(text)

    private fun client(lang: String) = clients.getOrPut(lang) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(lang)
                .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                .build()
        )
    }

    /** Chamado dentro do mutex. */
    private suspend fun ensureModel(lang: String): Boolean {
        if (lang in ready) return true
        val ok = runCatching {
            val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
            client(lang).downloadModelIfNeeded(conditions).await()
        }.isSuccess
        if (ok) ready += lang
        return ok
    }

    /**
     * ML Kit com o glossário de guerra em volta (siglas e nomes do inglês; ajustes do português em
     * todos) e, no inglês, nomes de pessoas e siglas protegidos ([NameGuard]).
     */
    private suspend fun translateOne(text: String, lang: String, names: Set<String>): String {
        if (lang != TranslateLanguage.ENGLISH) return TranslationGlossary.postprocess(client(lang).translate(text).await())
        val guarded = NameGuard.protect(TranslationGlossary.preprocess(text), names)
        val out = client(lang).translate(guarded.text).await()
        return TranslationGlossary.postprocess(NameGuard.restore(out, guarded))
    }

    /** Traduz e guarda no cache o que ainda não foi traduzido. */
    suspend fun translateAll(texts: Collection<String>): Boolean {
        val missing = texts.filter { it.isNotBlank() && it !in storage.translations }.distinct()
        if (missing.isEmpty()) return true
        // Os resumos ensinam os nomes que as manchetes Em Maiúsculas não deixam ver.
        val names = NameGuard.learn(texts)
        var allOk = true
        mutex.withLock {
            for ((lang, group) in missing.groupBy(::sourceLanguage)) {
                if (!ensureModel(lang)) {
                    allOk = false
                    continue
                }
                for (text in group) {
                    runCatching { translateOne(text, lang, names) }
                        .onSuccess { storage.translations[text] = it }
                }
            }
        }
        return allOk
    }

    /** Traduz uma lista mantendo a ordem (usado no texto completo, não vai para o cache). */
    suspend fun translateList(texts: List<String>): List<String>? {
        val lang = sourceLanguage(texts.joinToString(" ").take(2000))
        return mutex.withLock {
            if (!ensureModel(lang)) return@withLock null
            val names = NameGuard.learn(texts)
            texts.map { runCatching { translateOne(it, lang, names) }.getOrDefault(it) }
        }
    }
}
