package com.abugdn.wid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertsTest {
    @Test
    fun liveSirensTranslateCitiesAndSkipDrills() {
        val cities = parseSirenCities(json.parseToJsonElement("""{"cities":{"נחל עוז":["Nahal Oz",31.47,34.49]}}"""))
        val live = parseLiveSirens(
            json.parseToJsonElement(
                """[{"time":1790706920,"threat":5,"isDrill":false,"cities":["נחל עוז","מקום"]},
                    {"time":1790706920,"threat":0,"isDrill":true,"cities":["נחל עוז"]}]""",
            ),
            cities,
        )
        assertEquals(1, live.size)
        assertEquals("aeronave hostil (drone)", live[0].threat)
        assertEquals("Nahal Oz", live[0].cities[0].name)
        assertEquals(31.47, live[0].cities[0].lat!!, 0.001)
        assertEquals("מקום", live[0].cities[1].name)
        assertEquals(null, live[0].cities[1].lat)
    }

    @Test
    fun countdownTextAndProgress() {
        val now = Instant.parse("2026-09-29T12:00:00Z")
        assertEquals("faltam 1 d 4 h", countdown(Instant.parse("2026-09-30T16:00:00Z"), now))
        assertEquals("faltam 2 h 30 min", countdown(Instant.parse("2026-09-29T14:30:00Z"), now))
        assertEquals("venceu há 3 d 0 h", countdown(Instant.parse("2026-09-26T12:00:00Z"), now))
        val d = Deadline("x", "c", "t", start = "2026-09-29T00:00:00Z", due = "2026-09-30T00:00:00Z")
        assertEquals(0.5f, d.progress(now), 0.001f)
        assertTrue(!d.expired(now))
    }

    @Test
    fun courseLessonsHaveContent() {
        assertTrue(COURSE.size >= 6)
        assertEquals(COURSE.size, COURSE.map { it.id }.toSet().size)
        COURSE.forEach { l ->
            assertTrue(l.title, l.pages.isNotEmpty() && l.pages.all { it.length in 80..900 })
            l.tags.forEach { assertTrue("${l.id}: $it", it in TAG_LABELS) }
        }
    }
}
