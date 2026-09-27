package com.abugdn.wid.data

import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentTest {
    /** O OkHttp derruba o app se um cabeçalho tiver caractere fora do ASCII. */
    @Test
    fun wikiUserAgentIsAscii() {
        assertTrue(WIKI_USER_AGENT.all { it.code in 0x20..0x7e })
    }
}
