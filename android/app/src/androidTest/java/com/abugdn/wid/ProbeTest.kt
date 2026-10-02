package com.abugdn.wid

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abugdn.wid.data.TranslationGlossary
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.android.gms.tasks.Tasks
import org.junit.Test
import org.junit.runner.RunWith

/** Medição temporária (branch de teste): como o ML Kit trata nomes protegidos. Não vai para a main. */
@RunWith(AndroidJUnit4::class)
class ProbeTest {
    private class Case(val text: String, val protect: List<String>, val sentence: String? = null)

    private val cases = listOf(
        Case("Family of Renee Good Sues ICE Agent Who Shot Her, Top Trump Officials", listOf("Renee Good", "ICE", "Trump"),
            "Family of Renee Good sues ICE agent who shot her, top Trump officials"),
        Case("Family of Renee Good sues ICE agent who shot her and top Trump officials", listOf("Renee Good", "ICE", "Trump")),
        Case("The family of Renee Good files lawsuits over her death during Minneapolis ICE raids", listOf("Renee Good", "ICE")),
        Case("Renee Good's family sues Trump admin over fatal ICE shooting", listOf("Renee Good", "Trump", "ICE")),
        Case("Wife and brother of Minneapolis woman killed in immigration crackdown allege wrongful death. Becca Good said the agent never warned her.", listOf("Becca Good")),
        Case("Submitted by Jonathan Cook on Tuesday. Trump needs a scapegoat, and the Israeli prime minister best fits the bill.", listOf("Jonathan Cook", "Trump")),
        Case("Installing tech billionaires Elon Musk and Palmer Luckey to Pentagon defense board raises conflict of interest concerns", listOf("Elon Musk", "Palmer Luckey")),
        Case("'Rumors are slightly exaggerated' — Fire Point co-owner speaks out after reported assassination attempt", listOf("Fire Point")),
        Case("Secretary of Defense Pete Hegseth told hundreds of junior military officers that the Trump administration has achieved a cultural shift.", listOf("Pete Hegseth", "Trump")),
        Case("Trump criticized the renovation amid attempts to pressure then Fed chair Jerome Powell into lowering interest rates", listOf("Trump", "Jerome Powell")),
        Case("In an interview with Piers Morgan, Qatari Prime Minister Sheikh Mohammed bin Abdulrahman says Israeli officials rejected the idea", listOf("Piers Morgan")),
        Case("Nurse charged with helping man in hospital escape from ICE custody", listOf("ICE")),
        Case("Ethiopia fighting escalates in Tigray killing 52 civilians, medic tells the BBC", listOf("BBC")),
        Case("Former President George Bush met Condoleezza Rice and Mike Pence at the White House", listOf("George Bush", "Condoleezza Rice", "Mike Pence")),
        Case("Senator Rand Paul and Rep. Mark Green criticized the CIA and FBI over the strike", listOf("Rand Paul", "Mark Green", "CIA", "FBI")),
        Case("Investigators Seek Motive for FlyDubai Cockpit Attack", listOf("FlyDubai"),
            "Investigators seek motive for FlyDubai cockpit attack"),
        Case("Russia to Sharply Increase War Spending and Cut Social Programs", listOf(),
            "Russia to sharply increase war spending and cut social programs"),
        Case("Plane to Israel Narrowly Averts Disaster After Pilot Stabbing", listOf(),
            "Plane to Israel narrowly averts disaster after pilot stabbing"),
        Case("Malaysia Begins Repatriation of Migrants to War-Torn Myanmar Despite Critics’ Warnings", listOf(),
            "Malaysia begins repatriation of migrants to war-torn Myanmar despite critics’ warnings"),
    )

    private val dummies = listOf("Kowalski", "Nakamura", "Okafor", "Lindqvist")
    private fun sub(text: String, protect: List<String>, token: (Int) -> String): String =
        protect.foldIndexed(text) { i, acc, p -> Regex("""(?<![\p{L}\d])""" + Regex.escape(p) + """(?![\p{L}\d])""").replace(acc, token(i)) }

    @Test fun probe() {
        val client = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.PORTUGUESE).build())
        Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
        fun tr(s: String) = TranslationGlossary.postprocess(Tasks.await(client.translate(TranslationGlossary.preprocess(s))))
        for ((n, c) in cases.withIndex()) {
            val variants = buildList {
                add("hoje" to c.text)
                add("ficticio" to sub(c.text, c.protect) { dummies[it % dummies.size] })
                add("codigo" to sub(c.text, c.protect) { "ZQX" + ('A' + it) })
                add("numero" to sub(c.text, c.protect) { "X${it + 1}" })
                add("aspas" to sub(c.text, c.protect) { "\"${c.protect[it]}\"" })
                c.sentence?.let {
                    add("frase" to it)
                    add("frase+ficticio" to sub(it, c.protect) { dummies[it % dummies.size] })
                }
            }
            for ((label, input) in variants) Log.i("PROBE", "$n|$label|$input|${tr(input)}")
        }
    }
}
