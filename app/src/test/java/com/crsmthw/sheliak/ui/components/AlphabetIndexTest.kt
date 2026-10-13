package com.crsmthw.sheliak.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlphabetIndexTest {

    // ── Key → section ───────────────────────────────────────────────────────

    @Test
    fun `a Latin letter is its upper-case section`() {
        assertEquals("A", alphabetSectionKey("abbey road"))
        assertEquals("Z", alphabetSectionKey("zooropa"))
    }

    @Test
    fun `digits, symbols and an empty key fall under the other section`() {
        assertEquals(ALPHABET_OTHER_SECTION, alphabetSectionKey("1999"))
        assertEquals(ALPHABET_OTHER_SECTION, alphabetSectionKey("!!!"))
        assertEquals(ALPHABET_OTHER_SECTION, alphabetSectionKey(""))
    }

    @Test
    fun `letters of other cased scripts are their own upper-case sections`() {
        assertEquals("Ж", alphabetSectionKey("жизнь"))
        assertEquals("Ω", alphabetSectionKey("ωμέγα"))
        assertEquals("Ø", alphabetSectionKey("øresund"))
    }

    @Test
    fun `a Latin letter with no single-letter upper case is still its own section`() {
        assertEquals("ß", alphabetSectionKey("ßtraße"))
    }

    @Test
    fun `a caseless script is one section, whatever its first character`() {
        assertEquals(alphabetSectionKey("さくら"), alphabetSectionKey("ひかり"))
        assertEquals(alphabetSectionKey("東京"), alphabetSectionKey("大阪"))
        assertTrue(alphabetSectionKey("さくら") != alphabetSectionKey("東京"))
    }

    // ── Building the index ──────────────────────────────────────────────────

    @Test
    fun `sections in list order, each with its first row`() {
        val index = alphabetIndexOf(listOf("10 years", "abba", "acdc", "beatles", "blur", "coldplay"))
        assertEquals(listOf("#", "A", "B", "C"), index.labels)
        assertEquals(listOf(0, 1, 3, 5), index.starts)
        assertEquals(6, index.rowCount)
    }

    @Test
    fun `a section that comes back later keeps its first run`() {
        // Playlists are not sorted by title: Liked Songs ("") first, then by their own order.
        val index = alphabetIndexOf(listOf("", "rock", "chill", "running", "chill 2"))
        assertEquals(listOf("#", "R", "C"), index.labels)
        assertEquals(listOf(0, 1, 2), index.starts)
        assertEquals(1, index.firstRowOf(1))
    }

    @Test
    fun `a caseless script section is labelled by its first character`() {
        val index = alphabetIndexOf(listOf("abba", "さくら", "ひかり"))
        assertEquals(listOf("A", "さ"), index.labels)
        assertEquals(listOf(0, 1), index.starts)
    }

    @Test
    fun `no rows, no sections`() {
        assertEquals(AlphabetIndex.Empty, alphabetIndexOf(emptyList()))
        assertEquals(0, AlphabetIndex.Empty.sectionCount)
        assertEquals(0f, AlphabetIndex.Empty.fractionOfRow(3f))
        assertEquals(0, AlphabetIndex.Empty.sectionAt(0.5f))
        assertEquals(0, AlphabetIndex.Empty.firstRowOf(2))
    }

    @Test
    fun `an unchanged re-read is equal`() {
        assertEquals(alphabetIndexOf(listOf("a", "b")), alphabetIndexOf(listOf("a", "b")))
    }

    // ── Rows ↔ sections ─────────────────────────────────────────────────────

    private val index = alphabetIndexOf(
        // A: rows 0–3 (4), B: row 4 (1), C: rows 5–9 (5)
        listOf("a1", "a2", "a3", "a4", "b1", "c1", "c2", "c3", "c4", "c5"),
    )

    @Test
    fun `a row's section`() {
        assertEquals(0, index.sectionOfRow(0))
        assertEquals(0, index.sectionOfRow(3))
        assertEquals(1, index.sectionOfRow(4))
        assertEquals(2, index.sectionOfRow(5))
        assertEquals(2, index.sectionOfRow(9))
        assertEquals(2, index.sectionOfRow(42))
        assertEquals(0, index.sectionOfRow(-1))
    }

    @Test
    fun `every section gets an equal slot of the track`() {
        assertEquals(0, index.sectionAt(0f))
        assertEquals(0, index.sectionAt(0.33f))
        assertEquals(1, index.sectionAt(0.34f))
        assertEquals(1, index.sectionAt(0.66f))
        assertEquals(2, index.sectionAt(0.67f))
        assertEquals(2, index.sectionAt(1f))
        assertEquals(0, index.sectionAt(-0.5f))
        assertEquals(2, index.sectionAt(1.5f))
    }

    @Test
    fun `a row sits in its section's slot, by its place inside the section`() {
        assertEquals(0f, index.fractionOfRow(0f))
        assertEquals((0f + 2f / 4f) / 3f, index.fractionOfRow(2f), 1e-6f)
        assertEquals(1f / 3f, index.fractionOfRow(4f), 1e-6f)
        assertEquals(2f / 3f, index.fractionOfRow(5f), 1e-6f)
        assertEquals(1f, index.fractionOfRow(10f))
        assertEquals(1f, index.fractionOfRow(99f))
    }

    @Test
    fun `the thumb and the finger agree on the letter`() {
        for (row in 0 until index.rowCount) {
            val fraction = index.fractionOfRow(row.toFloat())
            assertEquals(index.sectionOfRow(row), index.sectionAt(fraction), "row $row")
        }
    }

    // ── Scroll position → row ───────────────────────────────────────────────

    @Test
    fun `the top of the list is row 0 and the furthest scroll is the row count`() {
        assertEquals(0f, alphabetScrollRow(firstVisibleRow = 0, offsetFraction = 0f, visibleRows = 10, rowCount = 100))
        assertEquals(100f, alphabetScrollRow(firstVisibleRow = 90, offsetFraction = 0f, visibleRows = 10, rowCount = 100))
        assertEquals(50f, alphabetScrollRow(firstVisibleRow = 45, offsetFraction = 0f, visibleRows = 10, rowCount = 100))
    }

    @Test
    fun `items above the rows still on screen are the top`() {
        assertEquals(0f, alphabetScrollRow(firstVisibleRow = -2, offsetFraction = 0.5f, visibleRows = 6, rowCount = 100))
    }

    @Test
    fun `a list that fits, or has no rows, stays at the top`() {
        assertEquals(0f, alphabetScrollRow(firstVisibleRow = 0, offsetFraction = 0f, visibleRows = 12, rowCount = 10))
        assertEquals(0f, alphabetScrollRow(firstVisibleRow = 3, offsetFraction = 0f, visibleRows = 5, rowCount = 0))
    }

    @Test
    fun `the partly scrolled-off first row moves the position smoothly`() {
        val row = alphabetScrollRow(firstVisibleRow = 45, offsetFraction = 0.5f, visibleRows = 10, rowCount = 100)
        assertEquals(45.5f / 90f * 100f, row, 1e-4f)
    }
}
