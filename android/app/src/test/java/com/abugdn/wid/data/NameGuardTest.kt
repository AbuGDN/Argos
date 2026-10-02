package com.abugdn.wid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameGuardTest {
    private val m = NameGuard.MARKERS

    // Textos reais do feed de 01/10/2026.
    private val summaries = listOf(
        "The family of Renee Good, the woman killed by immigration agents in January in Minneapolis, filed two federal lawsuits.",
        "Installing tech billionaires Elon Musk and Palmer Luckey to advisory roles in a Pentagon project.",
        "Secretary of Defense Pete Hegseth told hundreds of junior military officers on Wednesday.",
        "Israeli Prime Minister Benjamin Netanyahu walks away after addressing the United Nations General Assembly.",
        "The Red Cross said the Israeli Army and the Supreme Court were involved.",
        "Submitted by Jonathan Cook on Tue, 09/29/2026 - 14:28",
        "Former President George Bush met Condoleezza Rice at the White House.",
        "It was a good day for the cook.",
    )

    @Test fun learnsPeopleWhoseNameIsAnEnglishWord() {
        val names = NameGuard.learn(summaries)
        for (n in listOf("Renee Good", "Jonathan Cook", "George Bush", "Condoleezza Rice")) assertTrue("$n em $names", n in names)
    }

    @Test fun leavesAloneNamesTheTranslatorAlreadyKeeps() {
        // Medido no emulador: esses já saíam certos.
        val names = NameGuard.learn(summaries)
        for (n in listOf("Elon Musk", "Palmer Luckey", "Pete Hegseth", "Benjamin Netanyahu")) assertFalse("$n em $names", n in names)
    }

    @Test fun skipsInstitutionsDemonymsAndTitles() {
        val names = NameGuard.learn(summaries)
        assertFalse(names.toString(), names.any { "Cross" in it || "Army" in it || "Court" in it || "House" in it })
        assertFalse(names.toString(), names.any { it.startsWith("Secretary") || it.startsWith("Former") || it.startsWith("Israeli") })
    }

    @Test fun titleCaseIsNotEvidence() {
        assertTrue(NameGuard.learn(listOf("Russia to Sharply Increase War Spending and Cut Social Programs", "the war")).isEmpty())
        assertTrue(NameGuard.isTitleCase("Family of Renee Good Sues ICE Agent Who Shot Her"))
        assertFalse(NameGuard.isTitleCase("Family of Renee Good sues ICE agent who shot her"))
        assertFalse(NameGuard.isTitleCase("Submitted by Jonathan Cook on Tue, 09/29/2026 - 14:28"))
    }

    @Test fun hebrewAndArabicAreLeftAlone() {
        assertTrue(NameGuard.learn(listOf("הפיגוע סוכל בידי Renee Good אתמול good")).isEmpty())
    }

    @Test fun protectsTheCaseFromTheScreenshot() {
        // Saída real do ML Kit no emulador para o texto protegido.
        val g = NameGuard.protect("Family of Renee Good Sues ICE Agent Who Shot Her, Top Trump Officials", setOf("Renee Good"))
        assertEquals("Family of ${m[0]} Sues ${m[2]} Agent Who Shot Her, Top ${m[1]} Officials", g.text)
        assertEquals("Família de Renee Good processa agente ICE que atirou nela, as principais autoridades Trump",
            NameGuard.restore("Família de ${m[0]} processa agente ${m[2]} que atirou nela, as principais autoridades ${m[1]}", g))
    }

    @Test fun nameAtSentenceStartIsLearned() {
        // Medido no emulador: "Becca Good said…" saía "Becca Bom disse…".
        val names = NameGuard.learn(listOf("Woman killed in crackdown allege wrongful death. Becca Good said the agent never warned her."))
        assertTrue(names.toString(), "Becca Good" in names)
        assertTrue(NameGuard.learn(listOf("The Red Cross said it was a good day.")).isEmpty())
        // Vistos no feed de 01/10/2026 quando o começo da frase passou a contar.
        val noise = NameGuard.learn(listOf(
            "Most Americans say it is good. Several Palestinians were killed. Some Iraqis left.",
            "Spokesperson Hossein Mohebbi said the most good was done.",
        ))
        assertTrue(noise.toString(), noise.none { "Americans" in it || "Palestinians" in it || "Iraqis" in it || "Spokesperson" in it })
    }

    @Test fun trumpIsAlwaysProtected() {
        assertEquals("the ${m[0]} administration", NameGuard.protect("the Trump administration", emptySet()).text)
        assertEquals("a trump card", NameGuard.protect("a trump card", emptySet()).text)
    }

    @Test fun rafStays() {
        assertEquals("near the RAF Fairford base", NameGuard.protect("near the RAF Fairford base", emptySet()).text)
    }

    @Test fun restoreIgnoresCase() {
        // O ML Kit às vezes devolve o marcador em minúsculas ("zqxa").
        val g = NameGuard.protect("Fire Point co-owner speaks out", setOf("Fire Point"))
        assertEquals("o co-proprietário Fire Point fala", NameGuard.restore("o co-proprietário ${m[0].lowercase()} fala", g))
    }

    @Test fun knownAcronymsStay() {
        val g = NameGuard.protect("medic tells the BBC, CIA and FBI", emptySet())
        assertEquals("medic tells the BBC, CIA and FBI", g.text)
        assertTrue(g.originals.isEmpty())
    }

    @Test fun sameAcronymTwiceUsesOneMarker() {
        val g = NameGuard.protect("ICE agents and ICE officers", emptySet())
        assertEquals(listOf("ICE"), g.originals)
        assertEquals("agentes do ICE e oficiais do ICE", NameGuard.restore("agentes do ${m[0]} e oficiais do ${m[0]}", g))
    }

    @Test fun longerNameWinsAndPossessiveStays() {
        val g = NameGuard.protect("Renee Good's family", setOf("Renee Good", "Good"))
        assertEquals("${m[0]}'s family", g.text)
    }

    @Test fun allCapsTextKeepsItsWords() {
        val g = NameGuard.protect("BREAKING NEWS FROM GAZA", emptySet())
        assertEquals("BREAKING NEWS FROM GAZA", g.text)
        assertTrue(g.originals.isEmpty())
    }

    @Test fun nothingToProtectChangesNothing() {
        val text = "Israel launches air attacks on Beirut"
        assertEquals(text, NameGuard.protect(text, setOf("Renee Good")).text)
    }
}
