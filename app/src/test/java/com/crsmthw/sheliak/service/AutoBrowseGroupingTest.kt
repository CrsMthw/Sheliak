package com.crsmthw.sheliak.service

import com.crsmthw.sheliak.ui.components.alphabetIndexOf
import com.crsmthw.sheliak.util.AlphabetSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoBrowseGroupingTest {

    // ── Flat or grouped ─────────────────────────────────────────────────────

    @Test
    fun `a list of up to a hundred items stays flat, a longer one is grouped`() {
        assertFalse(AutoBrowseGrouping.isGrouped(0))
        assertFalse(AutoBrowseGrouping.isGrouped(1))
        assertFalse(AutoBrowseGrouping.isGrouped(AutoBrowseGrouping.AUTO_GROUP_MIN))
        assertTrue(AutoBrowseGrouping.isGrouped(AutoBrowseGrouping.AUTO_GROUP_MIN + 1))
        assertTrue(AutoBrowseGrouping.isGrouped(3369))
        assertEquals(100, AutoBrowseGrouping.AUTO_GROUP_MIN)
    }

    // ── Sections ────────────────────────────────────────────────────────────

    private val sorted = listOf("10 years", "abba", "acdc", "beatles", "blur", "coldplay")

    @Test
    fun `sections in list order, each with every one of its rows`() {
        val sections = AutoBrowseGrouping.sectionsOf(sorted)
        assertEquals(listOf(AlphabetSections.OTHER, "A", "B", "C"), sections.map { it.key })
        assertEquals(listOf("#", "A", "B", "C"), sections.map { it.label })
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), sections.map { it.members })
    }

    @Test
    fun `a section that comes back later keeps all of its rows`() {
        // Playlists are not sorted by title: Liked Songs ("") first, then by their own order.
        val sections = AutoBrowseGrouping.sectionsOf(listOf("", "rock", "chill", "running", "chill 2"))
        assertEquals(listOf("#", "R", "C"), sections.map { it.key })
        assertEquals(listOf(listOf(0), listOf(1, 3), listOf(2, 4)), sections.map { it.members })
    }

    @Test
    fun `every row is in exactly one section`() {
        val keys = listOf("zz top", "abba", "", "ωμέγα", "さくら", "abba", "1999", "東京", "ひかり", "ßtraße", "øresund")
        val rows = AutoBrowseGrouping.sectionsOf(keys).flatMap { it.members }
        assertEquals(keys.indices.toList(), rows.sorted())
    }

    @Test
    fun `a caseless script is one section, labelled by its first row`() {
        val sections = AutoBrowseGrouping.sectionsOf(listOf("abba", "さくら", "ひかり"))
        assertEquals(listOf("A", "さ"), sections.map { it.label })
        assertEquals(AlphabetSections.keyOf("さくら"), sections[1].key)
        assertEquals(listOf(1, 2), sections[1].members)
    }

    @Test
    fun `the section comes from the sort key, not the display title`() {
        // "The Beatles", whose provider sent the sort title "Beatles": the index's sort key is "beatles", so B.
        assertEquals("B", AutoBrowseGrouping.sectionsOf(listOf("beatles")).single().key)
    }

    @Test
    fun `the sections are the app's fast-scroller sections`() {
        listOf(
            sorted,
            listOf("", "rock", "chill", "running", "chill 2"),
            listOf("10cc", "abba", "ßtraße", "øresund", "жизнь", "ωμέγα", "さくら", "ひかり", "東京", "!!!"),
        ).forEach { keys ->
            val app = alphabetIndexOf(keys)
            val auto = AutoBrowseGrouping.sectionsOf(keys)
            assertEquals(app.labels, auto.map { it.label }, "$keys")
            // The scroller jumps to a section's first row: Auto's folder starts with that same row.
            assertEquals(app.starts, auto.map { it.members.first() }, "$keys")
        }
    }

    @Test
    fun `no rows, no sections`() {
        assertTrue(AutoBrowseGrouping.sectionsOf(emptyList()).isEmpty())
    }

    // ── One section's rows ──────────────────────────────────────────────────

    @Test
    fun `a section's rows, in list order`() {
        assertEquals(listOf(3, 4), AutoBrowseGrouping.membersOf(sorted, "B"))
        assertEquals(listOf(0), AutoBrowseGrouping.membersOf(sorted, AlphabetSections.OTHER))
        assertEquals(emptyList<Int>(), AutoBrowseGrouping.membersOf(sorted, "Q"))
    }

    @Test
    fun `one section's rows agree with the full grouping`() {
        val keys = listOf("", "rock", "chill", "さくら", "running", "ひかり", "chill 2", "1999")
        AutoBrowseGrouping.sectionsOf(keys).forEach { section ->
            assertEquals(section.members, AutoBrowseGrouping.membersOf(keys, section.key), section.key)
        }
    }
}
