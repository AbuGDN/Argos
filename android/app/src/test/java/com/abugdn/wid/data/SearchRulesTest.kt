package com.abugdn.wid.data

import com.abugdn.wid.ui.parseSearch
import com.abugdn.wid.ui.parseSearchDate
import com.abugdn.wid.ui.search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchRulesTest {
    private fun cluster(id: String, title: String, tags: List<String>, source: String = "G1", published: String = "2026-09-20T10:00:00Z", origin: String = "brasil") =
        Cluster(
            id = id, title = title, url = "https://x/$id", source = source, published = published, tags = tags,
            articles = listOf(ArticleRef(id = id, title = title, url = "https://x/$id", source = source, published = published, origin = origin)),
        )

    @Test
    fun parsesCommandsAndFreeText() {
        val q = parseSearch("região:irã fonte:g1 lado:árabe depois:01/09/2026 antes:15/09/2026 tipo:urgente míssil")
        assertEquals(setOf("ira"), q.regions)
        assertEquals(listOf("g1"), q.sources)
        assertEquals(setOf("arabe"), q.sides)
        assertEquals(LocalDate.of(2026, 9, 1), q.after)
        assertEquals(LocalDate.of(2026, 9, 15), q.before)
        assertEquals(setOf("urgente"), q.types)
        assertEquals(listOf("missil"), q.terms)
    }

    @Test
    fun dateWithoutYearIsTheLatestPast() {
        val today = LocalDate.of(2026, 1, 10)
        assertEquals(LocalDate.of(2025, 12, 20), parseSearchDate("20/12", today))
        assertEquals(LocalDate.of(2026, 1, 5), parseSearchDate("05/01", today))
        assertNull(parseSearchDate("amanhã", today))
    }

    @Test
    fun searchFiltersByRegionSourceAndDate() {
        val list = listOf(
            cluster("a", "Irã lança mísseis", listOf("ira"), source = "Al Jazeera", origin = "arabe"),
            cluster("b", "Irã negocia", listOf("ira"), published = "2026-08-01T10:00:00Z"),
            cluster("c", "Gaza sob ataque", listOf("gaza")),
        )
        assertEquals(listOf("a", "b"), search(list, "região:irã", { it }).map { it.id }.sorted())
        assertEquals(listOf("a"), search(list, "região:irã lado:árabe", { it }).map { it.id })
        assertEquals(listOf("a", "c"), search(list, "depois:01/09/2026", { it }).map { it.id }.sorted())
        assertEquals(listOf("c"), search(list, "fonte:g1 ataque", { it }).map { it.id })
    }

    @Test
    fun brierScore() {
        val preds = listOf(
            Prediction("1", "a", "2026-01-01", confidence = 90, hit = true),
            Prediction("2", "b", "2026-01-01", confidence = 60, hit = false),
            Prediction("3", "c", "2026-01-01", confidence = 70),
        )
        assertEquals((0.01 + 0.36) / 2, brier(preds)!!, 1e-9)
        assertNull(brier(listOf(preds[2])))
        assertEquals(2, calibration(preds).sumOf { it.third })
    }

    @Test
    fun rulesNeedAllConditions() {
        val feed = Feed(regions = mapOf("ira" to RegionStat(tension = 70)), clusters = listOf(cluster("x", "Ormuz fechado", listOf("ira"), published = java.time.Instant.now().toString())))
        val radar = RadarData(markets = MarketsSection(items = listOf(Quote("brent", "Petróleo Brent", price = 95.0))))
        val rule = AlertRule(
            "r", "teste",
            listOf(RuleCondition("tensao", region = "ira", value = 60.0), RuleCondition("mercado", market = "brent", op = "acima", value = 90.0)),
        )
        assertTrue(rule.holds(feed, radar) { it })
        assertFalse(rule.copy(conditions = rule.conditions + RuleCondition("radar", region = "ira")).holds(feed, radar) { it })
        assertTrue(RuleCondition("palavra", word = "ormuz").holds(feed, radar, { it }))
    }
}
