package com.abugdn.wid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTest {
    private fun cluster(id: String, title: String) =
        Cluster(id = id, title = title, url = "https://x/$id", source = "X", published = "2026-09-27T10:00:00Z")

    @Test
    fun relatedFindsOnlyThePersonAndStopsAtFive() {
        val netanyahu = PEOPLE.first { it.key == "netanyahu" }
        val clusters = (1..20).map { cluster("n$it", "Netanyahu says war will continue $it") } +
            cluster("x", "Zelensky visits Kyiv front line")
        val related = netanyahu.related(clusters, { it })
        assertEquals(5, related.size)
        assertTrue(related.all { it.id.startsWith("n") })
        assertTrue(netanyahu.related(clusters, { it }, exclude = "n1").none { it.id == "n1" })
    }

    @Test
    fun actorsMatchWholeWordsAndPlural() {
        val c = cluster("h", "Houthis launch missile; Hezbollah's leader speaks")
        val keys = c.actors { it }.map { it.key }
        assertTrue("houthis" in keys)
        assertTrue("hezbollah" in keys)
        val none = cluster("m", "Mira Hamasaki wins award").actors { it }.map { it.key }
        assertTrue("hamas" !in none)
    }

    @Test
    fun wordRegexIsCached() {
        assertTrue(wordRegex("gaza") === wordRegex("gaza"))
    }
}
