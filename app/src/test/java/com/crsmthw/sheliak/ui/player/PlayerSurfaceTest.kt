package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.ui.navigation.WindowWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSurfaceTest {

    // ── playerSurfaceFor ────────────────────────────────────────────────────

    @Test
    fun `nothing loaded shows no surface anywhere`() {
        assertEquals(PlayerSurface.None, playerSurfaceFor(hasTrack = false, routeAllowsSurface = true, panelWanted = false, canShowPanel = true))
        assertEquals(PlayerSurface.None, playerSurfaceFor(hasTrack = false, routeAllowsSurface = true, panelWanted = true, canShowPanel = true))
    }

    @Test
    fun `a screen without the surface shows none even with a track and an open panel`() {
        assertEquals(PlayerSurface.None, playerSurfaceFor(hasTrack = true, routeAllowsSurface = false, panelWanted = false, canShowPanel = true))
        assertEquals(PlayerSurface.None, playerSurfaceFor(hasTrack = true, routeAllowsSurface = false, panelWanted = true, canShowPanel = true))
    }

    @Test
    fun `a track on an allowed screen shows the bar`() {
        assertEquals(PlayerSurface.Bar, playerSurfaceFor(hasTrack = true, routeAllowsSurface = true, panelWanted = false, canShowPanel = false))
        assertEquals(PlayerSurface.Bar, playerSurfaceFor(hasTrack = true, routeAllowsSurface = true, panelWanted = false, canShowPanel = true))
    }

    @Test
    fun `the panel replaces the bar where it can exist`() {
        assertEquals(PlayerSurface.Panel, playerSurfaceFor(hasTrack = true, routeAllowsSurface = true, panelWanted = true, canShowPanel = true))
    }

    @Test
    fun `a wanted panel on a window that cannot host it falls back to the bar`() {
        assertEquals(PlayerSurface.Bar, playerSurfaceFor(hasTrack = true, routeAllowsSurface = true, panelWanted = true, canShowPanel = false))
    }

    // ── popOutAllowed ───────────────────────────────────────────────────────

    @Test
    fun `compact windows never get the pop-out`() {
        assertFalse(popOutAllowed(WindowWidth.Compact, 900f))
    }

    @Test
    fun `medium and expanded windows get the pop-out when at least 500dp tall`() {
        assertTrue(popOutAllowed(WindowWidth.Medium, 500f))
        assertTrue(popOutAllowed(WindowWidth.Expanded, 1000f))
    }

    @Test
    fun `short windows get the full player instead`() {
        assertFalse(popOutAllowed(WindowWidth.Medium, 499f))
        assertFalse(popOutAllowed(WindowWidth.Expanded, 380f))
    }

    // ── miniBarPlacement ────────────────────────────────────────────────────

    @Test
    fun `on the compact library the bar sits on the library's bottom bar`() {
        val placement = miniBarPlacement(
            onLibrary = true, wide = false, libraryBottomBar = 104.dp, libraryStartRail = 0.dp,
            libraryTwoPane = false, navigationBarBottom = 24.dp,
        )
        assertEquals(MiniBarPlacement(bottom = 104.dp, start = 0.dp, widthFraction = 1f), placement)
    }

    @Test
    fun `on a pushed screen the bar sits on the navigation bar, whatever the library still publishes`() {
        val placement = miniBarPlacement(
            onLibrary = false, wide = true, libraryBottomBar = 104.dp, libraryStartRail = 96.dp,
            libraryTwoPane = true, navigationBarBottom = 24.dp,
        )
        assertEquals(MiniBarPlacement(bottom = 24.dp, start = 0.dp, widthFraction = 1f), placement)
    }

    @Test
    fun `on the rail layouts the bar starts after the rail, on the navigation bar`() {
        val placement = miniBarPlacement(
            onLibrary = true, wide = true, libraryBottomBar = 0.dp, libraryStartRail = 96.dp,
            libraryTwoPane = false, navigationBarBottom = 24.dp,
        )
        assertEquals(MiniBarPlacement(bottom = 24.dp, start = 96.dp, widthFraction = 1f), placement)
    }

    @Test
    fun `over a two-pane library the bar spans the right pane`() {
        val placement = miniBarPlacement(
            onLibrary = true, wide = true, libraryBottomBar = 0.dp, libraryStartRail = 96.dp,
            libraryTwoPane = true, navigationBarBottom = 0.dp,
        )
        assertEquals(TWO_PANE_BAR_WIDTH_FRACTION, placement.widthFraction)
        assertEquals(0.58f, TWO_PANE_BAR_WIDTH_FRACTION)
    }

    @Test
    fun `a compact window never narrows the bar`() {
        val placement = miniBarPlacement(
            onLibrary = true, wide = false, libraryBottomBar = 104.dp, libraryStartRail = 0.dp,
            libraryTwoPane = true, navigationBarBottom = 24.dp,
        )
        assertEquals(1f, placement.widthFraction)
    }

    // ── unwindMillis ────────────────────────────────────────────────────────

    @Test
    fun `an abandoned seek unwinds over the share it covered`() {
        assertEquals(150, unwindMillis(0.5f, 300_000_000L, floorMillis = 300))
        assertEquals(15, unwindMillis(0.05f, 300_000_000L, floorMillis = 300))
        assertEquals(0, unwindMillis(0f, 300_000_000L, floorMillis = 300))
    }

    @Test
    fun `a transition reporting no duration unwinds over the floor`() {
        assertEquals(150, unwindMillis(0.5f, 0L, floorMillis = 300))
    }

    @Test
    fun `out-of-range fractions are clamped`() {
        assertEquals(300, unwindMillis(1.5f, 300_000_000L, floorMillis = 300))
        assertEquals(0, unwindMillis(-0.2f, 300_000_000L, floorMillis = 300))
    }
}
