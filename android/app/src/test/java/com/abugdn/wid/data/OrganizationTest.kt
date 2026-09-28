package com.abugdn.wid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationTest {
    private fun radar(internet: String, km2: Long, analysisAt: String) = RadarData(
        internet = InternetSection(countries = listOf(InternetCountry(code = "IR", name = "Irã", status = internet))),
        frontline = FrontlineSection(occupiedKm2 = km2),
        analysis = FeedSection(items = listOf(RadarItem(id = "a", title = "ISW", url = "https://x", source = "ISW", published = analysisAt))),
    )

    @Test
    fun firstVisitShowsNoChanges() {
        assertTrue(radarChanges(radar("queda", 100, "2026-09-28T10:00:00Z"), emptyMap()).isEmpty())
    }

    @Test
    fun changesSinceLastVisitAlertsFirst() {
        val before = radarFingerprint(radar("normal", 100, "2026-09-28T10:00:00Z"))
        val now = radar("apagao", 130, "2026-09-28T12:00:00Z")
        val changes = radarChanges(now, before)
        assertEquals(3, changes.size)
        assertTrue(changes.first().alert)
        assertTrue(changes.any { it.text.contains("normal → apagão") })
        assertTrue(changes.any { it.text.contains("avançou 30 km²") })
        assertTrue(changes.any { it.text.contains("Análises novas: 1") })
        assertTrue(radarChanges(now, radarFingerprint(now)).isEmpty())
    }

    @Test
    fun moveBlocks() {
        val order = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), order.moved("b", -1))
        assertEquals(listOf("a", "c", "b"), order.moved("b", 1))
        assertEquals(order, order.moved("a", -1))
        assertEquals(order, order.moved("c", 1))
    }
}
