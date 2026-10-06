package com.crsmthw.sheliak.data.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtsQueryTest {

    @Test
    fun `every word becomes a quoted prefix term`() {
        assertEquals("\"beat*\" \"it*\"", FtsQuery.build("beat it"))
        assertEquals("\"Björk*\"", FtsQuery.build("Björk"))
    }

    @Test
    fun `blank or punctuation-only input builds nothing`() {
        listOf("", "   ", "\"", "?!", "* - ( )").forEach { assertNull(FtsQuery.build(it), "<$it>") }
    }

    @Test
    fun `quotes can never break out of a phrase`() {
        val q = FtsQuery.build("say \"hello\" world\"")!!
        assertEquals("\"say*\" \"hello*\" \"world*\"", q)
        assertEquals(0, q.count { it == '"' } % 2)
    }

    @Test
    fun `FTS operators are inert words inside quotes`() {
        assertEquals("\"rock*\" \"OR*\" \"roll*\"", FtsQuery.build("rock OR roll"))
        assertEquals("\"AC*\" \"DC*\"", FtsQuery.build("AC/DC"))
        assertEquals("\"a*\" \"b*\"", FtsQuery.build("-a* (b)"))
    }

    @Test
    fun `words split where the unicode61 tokenizer splits them`() {
        assertEquals(listOf("Don", "t", "stop"), FtsQuery.tokens("Don't stop"))
        assertEquals(listOf("坂本龍一"), FtsQuery.tokens("坂本龍一"))
        assertEquals(listOf("Beyoncé"), FtsQuery.tokens("Beyoncé"))   // NFC first, so the accent stays inside the word
    }

    @Test
    fun `at most MAX_TOKENS words are used`() {
        val q = FtsQuery.build((1..40).joinToString(" ") { "w$it" })!!
        assertEquals(FtsQuery.MAX_TOKENS, q.split(' ').size)
        assertTrue(q.endsWith("\"w${FtsQuery.MAX_TOKENS}*\""))
    }

    @Test
    fun `like patterns escape wildcards and the escape character`() {
        assertEquals("%50\\% off\\_now\\\\%", FtsQuery.likeContains(" 50% off_now\\ "))
        assertEquals("%chill%", FtsQuery.likeContains("chill"))
        assertNull(FtsQuery.likeContains("  "))
    }
}
