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

    // ── surfaceSwapFor (crossing the pop-out line) ──────────────────────────

    @Test
    fun `the window becoming able to host the card swaps an open full player for it`() {
        assertEquals(
            SurfaceSwap.PlayerToCard,
            surfaceSwapFor(canShowPanelBefore = false, canShowPanelNow = true, playerOnTop = true, hasTrack = true, cardOverScreen = false),
        )
    }

    @Test
    fun `the window losing the card swaps an open card for the full player`() {
        assertEquals(
            SurfaceSwap.CardToPlayer,
            surfaceSwapFor(canShowPanelBefore = true, canShowPanelNow = false, playerOnTop = false, hasTrack = true, cardOverScreen = true),
        )
    }

    @Test
    fun `no change of the pop-out rule swaps nothing, whatever is open`() {
        assertEquals(
            SurfaceSwap.None,
            surfaceSwapFor(canShowPanelBefore = true, canShowPanelNow = true, playerOnTop = true, hasTrack = true, cardOverScreen = false),
        )
        assertEquals(
            SurfaceSwap.None,
            surfaceSwapFor(canShowPanelBefore = false, canShowPanelNow = false, playerOnTop = false, hasTrack = true, cardOverScreen = true),
        )
    }

    @Test
    fun `gaining the card with no full player on top swaps nothing`() {
        assertEquals(
            SurfaceSwap.None,
            surfaceSwapFor(canShowPanelBefore = false, canShowPanelNow = true, playerOnTop = false, hasTrack = true, cardOverScreen = false),
        )
    }

    @Test
    fun `an empty player is never swapped for a card`() {
        assertEquals(
            SurfaceSwap.None,
            surfaceSwapFor(canShowPanelBefore = false, canShowPanelNow = true, playerOnTop = true, hasTrack = false, cardOverScreen = false),
        )
    }

    @Test
    fun `losing the card while the full player covers it opens nothing more`() {
        // The card was hidden under a full player opened from it: the player is already there.
        assertEquals(
            SurfaceSwap.None,
            surfaceSwapFor(canShowPanelBefore = true, canShowPanelNow = false, playerOnTop = true, hasTrack = true, cardOverScreen = false),
        )
    }

    @Test
    fun `the swap follows the pop-out rule, not the width class - folded landscape is wide but cannot host the card`() {
        // Folded portrait (compact) -> folded landscape (medium width, short): the pop-out rule stays false.
        val before = popOutAllowed(WindowWidth.Compact, 900f)
        val now = popOutAllowed(WindowWidth.Medium, 380f)
        assertEquals(
            SurfaceSwap.None,
            surfaceSwapFor(canShowPanelBefore = before, canShowPanelNow = now, playerOnTop = true, hasTrack = true, cardOverScreen = false),
        )
    }

    // ── "album-art" match gating ────────────────────────────────────────────

    @Test
    fun `a full player counts while on top and while it is still leaving`() {
        assertTrue(fullPlayerPresent(topIsPlayer = true, composedFullPlayers = 0))
        assertTrue(fullPlayerPresent(topIsPlayer = false, composedFullPlayers = 1))
        assertFalse(fullPlayerPresent(topIsPlayer = false, composedFullPlayers = 0))
    }

    @Test
    fun `a floating surface's art may match only while a partner exists`() {
        assertTrue(floatingArtMayMatch(fullPlayerPresent = true, otherSurfaceInTransition = false))
        assertTrue(floatingArtMayMatch(fullPlayerPresent = false, otherSurfaceInTransition = true))
        assertFalse(floatingArtMayMatch(fullPlayerPresent = false, otherSurfaceInTransition = false))
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

    // ── panelScrimShare / PanelScrimSegment (fix round 2, item 41) ──────────
    // Every progress and share here is a short binary fraction, so the expected floats are exact.

    @Test
    fun `at rest the scrim is full on the card and gone everywhere else, whatever came before`() {
        assertEquals(1f, panelScrimShare(PlayerSurface.Panel, PlayerSurface.Panel, progress = 0f, startShare = 0f))
        assertEquals(0f, panelScrimShare(PlayerSurface.None, PlayerSurface.None, progress = 0f, startShare = 1f))
        assertEquals(0f, panelScrimShare(PlayerSurface.Bar, PlayerSurface.Bar, progress = 0.5f, startShare = 1f))
    }

    @Test
    fun `into the card the scrim fades in with the progress`() {
        assertEquals(0.25f, panelScrimShare(PlayerSurface.Bar, PlayerSurface.Panel, progress = 0.25f, startShare = 0f))
        // A back gesture off the full player seeking the card in.
        assertEquals(0.5f, panelScrimShare(PlayerSurface.None, PlayerSurface.Panel, progress = 0.5f, startShare = 0f))
    }

    @Test
    fun `out of the card the scrim fades out with the progress`() {
        assertEquals(0.75f, panelScrimShare(PlayerSurface.Panel, PlayerSurface.Bar, progress = 0.25f, startShare = 1f))
        assertEquals(0f, panelScrimShare(PlayerSurface.Panel, PlayerSurface.None, progress = 1f, startShare = 1f))
    }

    @Test
    fun `a moving segment starts from the share it is given`() {
        assertEquals(0.75f, panelScrimShare(PlayerSurface.Panel, PlayerSurface.Bar, progress = 0f, startShare = 0.75f))
        assertEquals(0.375f, panelScrimShare(PlayerSurface.Panel, PlayerSurface.Bar, progress = 0.5f, startShare = 0.75f))
        assertEquals(0.625f, panelScrimShare(PlayerSurface.Bar, PlayerSurface.Panel, progress = 0.5f, startShare = 0.25f))
    }

    @Test
    fun `the progress is clamped`() {
        assertEquals(1f, panelScrimShare(PlayerSurface.Bar, PlayerSurface.Panel, progress = 1.5f, startShare = 0f))
        assertEquals(0f, panelScrimShare(PlayerSurface.Bar, PlayerSurface.Panel, progress = -0.25f, startShare = 0f))
    }

    @Test
    fun `item 41 - after a fold snaps the card away, no scrim is left anywhere`() {
        val scrim = PanelScrimSegment()
        assertEquals(1f, scrim.share(PlayerSurface.Panel, PlayerSurface.Panel, 0f))  // unfolded, card open
        assertEquals(0f, scrim.share(PlayerSurface.None, PlayerSurface.None, 0f))    // fold: snapped, full player
        assertEquals(0f, scrim.share(PlayerSurface.None, PlayerSurface.Bar, 0.5f))   // back gesture brings the bar in
        assertEquals(0f, scrim.share(PlayerSurface.None, PlayerSurface.Bar, 1f))     // committed
        assertEquals(0f, scrim.share(PlayerSurface.Bar, PlayerSurface.Bar, 0f))      // the bar at rest; unfold
        assertEquals(0.5f, scrim.share(PlayerSurface.Bar, PlayerSurface.Panel, 0.5f)) // tap: the card opens over it
        assertEquals(1f, scrim.share(PlayerSurface.Panel, PlayerSurface.Panel, 0f))
        assertEquals(0.5f, scrim.share(PlayerSurface.Panel, PlayerSurface.Bar, 0.5f)) // close
        assertEquals(0f, scrim.share(PlayerSurface.Bar, PlayerSurface.Bar, 0f))
    }

    @Test
    fun `unfolding with the full player open shows the card over its scrim at once`() {
        val scrim = PanelScrimSegment()
        assertEquals(0f, scrim.share(PlayerSurface.None, PlayerSurface.None, 0f))
        assertEquals(1f, scrim.share(PlayerSurface.Panel, PlayerSurface.Panel, 0f))  // the swap's snap to the card
    }

    @Test
    fun `a retarget mid-flight continues from the share on screen instead of jumping`() {
        val scrim = PanelScrimSegment()
        assertEquals(0f, scrim.share(PlayerSurface.None, PlayerSurface.None, 0f))       // the full player on top
        assertEquals(0.75f, scrim.share(PlayerSurface.None, PlayerSurface.Panel, 0.75f)) // its back arrow: card rising
        // A back swipe on the card still rising from the full player (opening from None): the state restarts at
        // fraction 0 with the OLD target (the card) as its current state. (Opening from the bar it is no retarget:
        // the current state is still the bar, and the host does not seek.)
        assertEquals(0.75f, scrim.share(PlayerSurface.Panel, PlayerSurface.Bar, 0f))
        assertEquals(0.375f, scrim.share(PlayerSurface.Panel, PlayerSurface.Bar, 0.5f))
        // Committed: the close plays out from there.
        assertEquals(0f, scrim.share(PlayerSurface.Panel, PlayerSurface.Bar, 1f))
        assertEquals(0f, scrim.share(PlayerSurface.Bar, PlayerSurface.Bar, 0f))
    }

    @Test
    fun `the full-screen button on a moving card fades the scrim out from where it is`() {
        val scrim = PanelScrimSegment()
        assertEquals(0f, scrim.share(PlayerSurface.Bar, PlayerSurface.Bar, 0f))
        assertEquals(0.5f, scrim.share(PlayerSurface.Bar, PlayerSurface.Panel, 0.5f))   // opening
        assertEquals(0.5f, scrim.share(PlayerSurface.Panel, PlayerSurface.None, 0f))    // retarget to None
        assertEquals(0.25f, scrim.share(PlayerSurface.Panel, PlayerSurface.None, 0.5f))
        assertEquals(0f, scrim.share(PlayerSurface.None, PlayerSurface.None, 0f))
    }

    @Test
    fun `a back swipe on the card released early winds the scrim back to full`() {
        val scrim = PanelScrimSegment()
        assertEquals(1f, scrim.share(PlayerSurface.Panel, PlayerSurface.Panel, 0f))
        assertEquals(0.75f, scrim.share(PlayerSurface.Panel, PlayerSurface.Bar, 0.25f)) // the finger
        assertEquals(0.875f, scrim.share(PlayerSurface.Panel, PlayerSurface.Bar, 0.125f)) // the unwind
        assertEquals(1f, scrim.share(PlayerSurface.Panel, PlayerSurface.Panel, 0f))     // snapped back
    }

    @Test
    fun `the first state seen shows at its rest share`() {
        assertEquals(1f, PanelScrimSegment().share(PlayerSurface.Panel, PlayerSurface.Panel, 0f)) // restored card
        assertEquals(0f, PanelScrimSegment().share(PlayerSurface.Bar, PlayerSurface.Bar, 0f))
    }
}
