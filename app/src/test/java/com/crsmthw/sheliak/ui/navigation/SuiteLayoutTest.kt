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
    fun `compact gets the bar`() {
        assertEquals(SuiteLayout.Bar, suiteLayoutFor(WindowWidth.Compact))
    }

    @Test
    fun `medium gets the collapsed rail`() {
        assertEquals(SuiteLayout.CollapsedRail, suiteLayoutFor(WindowWidth.Medium))
    }

    @Test
    fun `expanded gets the expanded rail`() {
        assertEquals(SuiteLayout.ExpandedRail, suiteLayoutFor(WindowWidth.Expanded))
    }

    @Test
    fun `measured widths map end to end`() {
        assertEquals(SuiteLayout.Bar, suiteLayoutFor(windowWidthOf(411)))
        assertEquals(SuiteLayout.CollapsedRail, suiteLayoutFor(windowWidthOf(673)))
        assertEquals(SuiteLayout.ExpandedRail, suiteLayoutFor(windowWidthOf(882)))
    }

    // ── searchMorphEnabled ──────────────────────────────────────────────────

    @Test
    fun `the search morph runs on compact only`() {
        assertTrue(searchMorphEnabled(WindowWidth.Compact))
        assertFalse(searchMorphEnabled(WindowWidth.Medium))
        assertFalse(searchMorphEnabled(WindowWidth.Expanded))
    }
}
