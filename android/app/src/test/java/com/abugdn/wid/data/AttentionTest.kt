package com.abugdn.wid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionTest {
    /** Mesmo formato que backend/wid/radar.py (collect_attention) grava no radar.json. */
    private val radarJson = """
        {"attention": {"updated": "2026-10-01T12:00:00Z", "start": "2026-08-31", "end": "2026-09-29", "langs": ["en", "pt"],
         "conflicts": [
           {"id": "ucrania", "name": "Ucrânia", "tag": "ucrania_russia", "views_7d": 900, "views_prev_7d": 1000,
            "views_total": 9000, "series": [["2026-09-28", 100], ["2026-09-29", 120]], "by_lang": {"en": 800, "pt": 100}},
           {"id": "sudao", "name": "Sudão", "tag": "sudao", "views_7d": 10, "views_prev_7d": 0, "views_total": 100,
            "series": [], "by_lang": {"en": 10}}
         ]}}
    """

    @Test
    fun parsesAttentionFromRadarJson() {
        val a = json.decodeFromString<RadarData>(radarJson).attention!!
        assertEquals(2, a.conflicts.size)
        assertEquals(-10.0, a.conflicts[0].change!!, 0.001)
        assertNull(a.conflicts[1].change) // sem semana anterior não há variação
        assertEquals(listOf(100.0, 120.0), seriesValues(a.conflicts[0].series))
    }

    @Test
    fun everyAttentionConflictHasDeaths() {
        // Os ids do radar.yaml (attention.conflicts) e do WarDeaths.kt gerado têm de ser os mesmos.
        val ids = WAR_DEATHS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(WAR_DEATHS.all { it.deaths.size == WAR_DEATHS_YEARS.size })
    }

    @Test
    fun forgottenPutsLittleAttentionPerDeathFirst() {
        val a = json.decodeFromString<RadarData>(radarJson).attention!!
        val rows = forgottenRows(a)
        assertEquals(WAR_DEATHS.size, rows.size)
        // Sudão: muitas mortes e 100 de 9.100 visitas -> mais esquecida que a Ucrânia.
        val sudan = rows.indexOfFirst { it.war.id == "sudao" }
        val ukraine = rows.indexOfFirst { it.war.id == "ucrania" }
        assertTrue(sudan < ukraine)
        // Guerras sem visitas no radar vão para o fim, sem parte de atenção.
        assertNull(rows.last().attentionShare)
        assertEquals(100.0, rows.sumOf { it.deathShare }, 0.001)
    }

    @Test
    fun forgottenWorksWithoutAttention() {
        val rows = forgottenRows(null)
        assertEquals(WAR_DEATHS.size, rows.size)
        assertTrue(rows.all { it.attentionShare == null })
    }
}
