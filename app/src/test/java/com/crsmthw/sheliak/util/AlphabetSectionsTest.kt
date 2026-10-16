package com.crsmthw.sheliak.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlphabetSectionsTest {

    // ── Sort key → section ──────────────────────────────────────────────────

    @Test
    fun `a Latin letter is its upper-case section`() {
        assertEquals("A", AlphabetSections.keyOf("abbey road"))
        assertEquals("S", AlphabetSections.keyOf("sigur ros"))
        assertEquals("Z", AlphabetSections.keyOf("zooropa"))
    }

    @Test
    fun `digits, symbols and an empty key fall under the other section`() {
        assertEquals(AlphabetSections.OTHER, AlphabetSections.keyOf("1999"))
        assertEquals(AlphabetSections.OTHER, AlphabetSections.keyOf("!!!"))
        assertEquals(AlphabetSections.OTHER, AlphabetSections.keyOf(""))
        assertEquals("#", AlphabetSections.OTHER)
    }

    @Test
    fun `letters of other cased scripts are their own upper-case sections`() {
        assertEquals("Ж", AlphabetSections.keyOf("жизнь"))
        assertEquals("Ω", AlphabetSections.keyOf("ωμέγα"))
        assertEquals("Ø", AlphabetSections.keyOf("øresund"))
        assertEquals("ß", AlphabetSections.keyOf("ßtraße"))
    }

    @Test
    fun `a caseless script is one section, whatever its first character`() {
        assertEquals(AlphabetSections.keyOf("さくら"), AlphabetSections.keyOf("ひかり"))
        assertEquals(AlphabetSections.keyOf("東京"), AlphabetSections.keyOf("大阪"))
        assertTrue(AlphabetSections.keyOf("さくら") != AlphabetSections.keyOf("東京"))
    }

    // ── Section → label ─────────────────────────────────────────────────────

    @Test
    fun `a letter section is labelled by its key`() {
        assertEquals("B", AlphabetSections.labelOf("B", "beatles"))
        assertEquals("Ж", AlphabetSections.labelOf("Ж", "жизнь"))
        assertEquals(AlphabetSections.OTHER, AlphabetSections.labelOf(AlphabetSections.OTHER, "1999"))
    }

    @Test
    fun `a script section is labelled by its first row's first character`() {
        assertEquals("さ", AlphabetSections.labelOf(AlphabetSections.keyOf("さくら"), "さくら"))
        assertEquals("ひ", AlphabetSections.labelOf(AlphabetSections.keyOf("さくら"), "ひかり"))
        assertEquals("東", AlphabetSections.labelOf(AlphabetSections.keyOf("東京"), "東京"))
    }

    @Test
    fun `a script section with no first row to read keeps its key`() {
        val key = AlphabetSections.keyOf("さくら")
        assertEquals(key, AlphabetSections.labelOf(key, ""))
    }
}
