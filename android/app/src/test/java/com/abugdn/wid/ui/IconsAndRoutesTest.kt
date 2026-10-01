package com.abugdn.wid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconsAndRoutesTest {
    @Test
    fun oldRadarRoutesStillLand() {
        // Atalhos e ferramentas antigos usavam 6 abas; Análise (4) foi para Vozes e Contexto (5) para Números.
        assertEquals(0, radarTabFor(0))
        assertEquals(2, radarTabFor(2))
        assertEquals(3, radarTabFor(3))
        assertEquals(3, radarTabFor(4))
        assertEquals(2, radarTabFor(5))
        assertEquals(0, radarTabFor(9))
    }

    @Test
    fun everyToolHasADrawnIcon() {
        // Bandeiras (🇺🇦, 🇺🇳) ficam como emoji de propósito: identificam o país.
        val isFlag = { s: String -> s.codePoints().allMatch { it in 0x1F1E6..0x1F1FF } }
        val missing = TOOL_GROUPS.flatMap { it.second }.map { it.icon }
            .filter { !isFlag(it) && it.replace("\uFE0F", "") !in EMOJI_ICON_PATHS }
        assertTrue("ferramentas sem ícone desenhado: $missing (rode backend/tools/gerar_icones.py)", missing.isEmpty())
    }

    @Test
    fun toolRoutesAreUnique() {
        // Chave repetida na lista derrubava o app ao rolar (1.0.42).
        val keys = TOOL_GROUPS.flatMap { it.second }.map { it.route + "|" + it.name }
        assertEquals(keys.size, keys.toSet().size)
    }
}
