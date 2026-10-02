package com.abugdn.wid

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abugdn.wid.data.NameGuard
import com.abugdn.wid.data.TranslationGlossary
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.junit.Test
import org.junit.runner.RunWith

/** Medição temporária (branch de teste): antes x depois do NameGuard, com o código real. Não vai para a base. */
@RunWith(AndroidJUnit4::class)
class ProbeTest {
    // Manchetes e resumos reais (feed de 01/10/2026); os resumos ensinam os nomes, como no app.
    private val texts = listOf(
        "Family of Renee Good Sues ICE Agent Who Shot Her, Top Trump Officials",
        "Family of Renee Good sues ICE agent who shot her and top Trump officials",
        "The family of Renee Good files lawsuits over her death during Minneapolis ICE raids",
        "Renee Good's family sues Trump admin over fatal ICE shooting",
        "The family of Renee Good, the woman killed by immigration agents in January in Minneapolis, filed two federal lawsuits against the agent who shot her.",
        "Wife and brother of Minneapolis woman killed in immigration crackdown allege wrongful death. Becca Good said the agent never warned her.",
        "Submitted by Jonathan Cook on Tuesday. Trump needs a scapegoat, and the Israeli prime minister best fits the bill.",
        "Installing tech billionaires Elon Musk and Palmer Luckey to Pentagon defense board raises conflict of interest concerns",
        "Secretary of Defense Pete Hegseth told hundreds of junior military officers that the Trump administration has achieved a cultural shift.",
        "Nurse charged with helping man in hospital escape from ICE custody",
        "Ethiopia fighting escalates in Tigray killing 52 civilians, medic tells the BBC",
        "Former President George Bush met Condoleezza Rice and Mike Pence at the White House",
        "Senator Rand Paul and Rep. Mark Green criticized the CIA and FBI over the strike",
        "NATO Secretary-General Mark Rutte said allies would boost air defenses for Ukraine.",
        "British police arrested a man near the RAF Fairford base in southwest England.",
        "Admiral Brad Cooper, head of US Central Command, visited the region on Monday.",
        "Israel launches airstrikes on Beirut as Hezbollah fires rockets",
    )

    @Test fun probe() {
        val client = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.PORTUGUESE).build())
        Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
        val names = NameGuard.learn(texts)
        Log.i("PROBE", "NOMES|$names")
        for ((n, text) in texts.withIndex()) {
            val before = TranslationGlossary.postprocess(Tasks.await(client.translate(TranslationGlossary.preprocess(text))))
            // Mesmo caminho do Translator.translateOne.
            val guarded = NameGuard.protect(TranslationGlossary.preprocess(text), names)
            val raw = Tasks.await(client.translate(guarded.text))
            val after = TranslationGlossary.postprocess(NameGuard.restore(raw, guarded))
            Log.i("PROBE", "$n|$text|$before|$after|${guarded.text}|$raw")
        }
    }
}
