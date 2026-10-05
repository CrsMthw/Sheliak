package com.crsmthw.sheliak.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuiteLayoutTest {

    // ── windowWidthOf ───────────────────────────────────────────────────────

    @Test
    fun `widths below 600dp are compact`() {
        assertEquals(WindowWidth.Compact, windowWidthOf(0))
        assertEquals(WindowWidth.Compact, windowWidthOf(411))
        assertEquals(WindowWidth.Compact, windowWidthOf(599))
    }

    @Test
    fun `600dp up to 839dp is medium`() {
        assertEquals(WindowWidth.Medium, windowWidthOf(600))
        assertEquals(WindowWidth.Medium, windowWidthOf(839))
    }

    @Test
    fun `840dp and up is expanded`() {
        assertEquals(WindowWidth.Expanded, windowWidthOf(840))
        assertEquals(WindowWidth.Expanded, windowWidthOf(1200))
    }

    // ── suiteLayoutFor ──────────────────────────────────────────────────────

    @Test
    fun `destinations get the bar on compact`() {
        TopLevelKeys.forEach { assertEquals(SuiteLayout.Bar, suiteLayoutFor(WindowWidth.Compact, it)) }
    }

    @Test
    fun `destinations get the collapsed rail on medium`() {
        TopLevelKeys.forEach { assertEquals(SuiteLayout.CollapsedRail, suiteLayoutFor(WindowWidth.Medium, it)) }
    }

    @Test
    fun `destinations get the expanded rail on expanded`() {
        TopLevelKeys.forEach { assertEquals(SuiteLayout.ExpandedRail, suiteLayoutFor(WindowWidth.Expanded, it)) }
    }

    @Test
    fun `every other screen hides the suite at every width`() {
        val others = listOf(Intro, Search, Settings, Player, Queue, null)
        WindowWidth.entries.forEach { width ->
            others.forEach { key -> assertEquals(SuiteLayout.Hidden, suiteLayoutFor(width, key), "$width $key") }
        }
    }

    // ── searchMorphEnabled ──────────────────────────────────────────────────

    @Test
    fun `the search morph runs on compact only`() {
        assertTrue(searchMorphEnabled(WindowWidth.Compact))
        assertFalse(searchMorphEnabled(WindowWidth.Medium))
        assertFalse(searchMorphEnabled(WindowWidth.Expanded))
    }
}
