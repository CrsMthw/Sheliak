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

    // ── list-detail ─────────────────────────────────────────────────────────

    @Test
    fun `list-detail starts at the medium width`() {
        assertFalse(listDetailEnabled(WindowWidth.Compact))
        assertTrue(listDetailEnabled(WindowWidth.Medium))
        assertTrue(listDetailEnabled(WindowWidth.Expanded))
    }

    @Test
    fun `Tracks is single pane at every width`() {
        WindowWidth.entries.forEach { width -> assertFalse(libraryTabIsTwoPane(LibraryTab.TRACKS, width), "$width") }
    }

    @Test
    fun `albums, artists and playlists are two-pane from 600dp`() {
        listOf(LibraryTab.ALBUMS, LibraryTab.ARTISTS, LibraryTab.PLAYLISTS).forEach { tab ->
            assertFalse(libraryTabIsTwoPane(tab, WindowWidth.Compact), "$tab")
            assertTrue(libraryTabIsTwoPane(tab, WindowWidth.Medium), "$tab")
            assertTrue(libraryTabIsTwoPane(tab, WindowWidth.Expanded), "$tab")
        }
    }

    // ── suiteChromeExtent ───────────────────────────────────────────────────

    @Test
    fun `the bar reports the height the content lost`() {
        assertEquals(
            SuiteChromeExtent(bottomBarPx = 210, startRailPx = 0),
            suiteChromeExtent(SuiteLayout.Bar, totalWidth = 1080, totalHeight = 2400, contentWidth = 1080, contentHeight = 2190),
        )
    }

    @Test
    fun `a rail reports the width the content lost`() {
        listOf(SuiteLayout.CollapsedRail, SuiteLayout.ExpandedRail).forEach { layout ->
            assertEquals(
                SuiteChromeExtent(bottomBarPx = 0, startRailPx = 252),
                suiteChromeExtent(layout, totalWidth = 2160, totalHeight = 1800, contentWidth = 1908, contentHeight = 1800),
                "$layout",
            )
        }
    }

    @Test
    fun `a half-measured suite never reports a negative extent`() {
        assertEquals(SuiteChromeExtent.None, suiteChromeExtent(SuiteLayout.Bar, 1080, 0, 1080, 2190))
        assertEquals(SuiteChromeExtent.None, suiteChromeExtent(SuiteLayout.CollapsedRail, 0, 1800, 1908, 1800))
    }
}
