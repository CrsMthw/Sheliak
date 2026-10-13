package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.ui.navigation.WindowWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mini bar's placement across a fold (M1 fix round 1, item 63): the Library's chrome is trusted only once it
 * was published for the current width class, it is remembered per width class, and a bar moving away from a
 * fold or a provisional placement snaps instead of gliding.
 */
class PlacementSeedingTest {

    private val compactChrome   = LibraryChrome(bottomBar = 104.dp, startRail = 0.dp, twoPane = false)
    private val collapsedRail   = LibraryChrome(bottomBar = 0.dp, startRail = 96.dp, twoPane = true)
    private val expandedRail    = LibraryChrome(bottomBar = 0.dp, startRail = 220.dp, twoPane = true)
    private val navigationBar   = 24.dp

    // ── chromeFits ──────────────────────────────────────────────────────────

    @Test
    fun `compact chrome is a bottom bar and no rail`() {
        assertTrue(chromeFits(wide = false, chrome = compactChrome))
        assertFalse(chromeFits(wide = false, chrome = collapsedRail))
        assertFalse(chromeFits(wide = false, chrome = LibraryChrome.None))
    }

    @Test
    fun `wide chrome is a rail and no bottom bar`() {
        assertTrue(chromeFits(wide = true, chrome = collapsedRail))
        assertFalse(chromeFits(wide = true, chrome = compactChrome))
        assertFalse(chromeFits(wide = true, chrome = LibraryChrome.None))
    }

    // ── freshChrome ─────────────────────────────────────────────────────────

    @Test
    fun `fitting chrome with no width change pending is fresh`() {
        assertEquals(compactChrome, freshChrome(wide = false, live = compactChrome, staleAtWidthChange = null))
    }

    @Test
    fun `chrome still laid out for the previous width class is not taken`() {
        // Unfold: the frame the width class changes, the suite still publishes the bottom bar.
        assertNull(freshChrome(wide = true, live = compactChrome, staleAtWidthChange = compactChrome))
    }

    @Test
    fun `a stale rail that happens to fit the new rail width class is not taken`() {
        // Medium -> expanded keeps a rail on screen: the collapsed rail's width fits "wide" but is the old layout.
        assertNull(freshChrome(wide = true, live = collapsedRail, staleAtWidthChange = collapsedRail))
    }

    @Test
    fun `the first publication after a width change is fresh`() {
        assertEquals(expandedRail, freshChrome(wide = true, live = expandedRail, staleAtWidthChange = collapsedRail))
        assertEquals(collapsedRail, freshChrome(wide = true, live = collapsedRail, staleAtWidthChange = LibraryChrome.None))
    }

    // ── miniBarTarget ───────────────────────────────────────────────────────

    @Test
    fun `over any screen but the Library the bar sits on the navigation bar at full width, chrome or not`() {
        assertEquals(
            MiniBarPlacement(bottom = navigationBar, start = 0.dp, widthFraction = 1f),
            miniBarTarget(onLibrary = false, wide = true, libraryChrome = null, navigationBarBottom = navigationBar),
        )
    }

    @Test
    fun `over the Library with no chrome for this width class the placement is not known yet`() {
        assertNull(miniBarTarget(onLibrary = true, wide = true, libraryChrome = null, navigationBarBottom = navigationBar))
    }

    @Test
    fun `over the Library the placement comes from the chrome`() {
        assertEquals(
            MiniBarPlacement(bottom = navigationBar, start = 96.dp, widthFraction = TWO_PANE_BAR_WIDTH_FRACTION),
            miniBarTarget(onLibrary = true, wide = true, libraryChrome = collapsedRail, navigationBarBottom = navigationBar),
        )
        assertEquals(
            MiniBarPlacement(bottom = 104.dp, start = 0.dp, widthFraction = 1f),
            miniBarTarget(onLibrary = true, wide = false, libraryChrome = compactChrome, navigationBarBottom = navigationBar),
        )
    }

    // ── placementMotion ─────────────────────────────────────────────────────

    @Test
    fun `a bar on screen glides between two screens of the same width class`() {
        assertEquals(PlacementMotion.Animate, placementMotion(barShown = true, widthClassChanged = false, fromProvisional = false))
    }

    @Test
    fun `a hidden bar snaps into place before it is shown`() {
        assertEquals(PlacementMotion.Snap, placementMotion(barShown = false, widthClassChanged = false, fromProvisional = false))
    }

    @Test
    fun `a fold snaps the bar, never a visible glide to the rail`() {
        assertEquals(PlacementMotion.Snap, placementMotion(barShown = true, widthClassChanged = true, fromProvisional = false))
    }

    @Test
    fun `leaving a provisional placement snaps`() {
        assertEquals(PlacementMotion.Snap, placementMotion(barShown = true, widthClassChanged = false, fromProvisional = true))
    }

    // ── The whole seeding, frame by frame (the host's composition logic) ───

    /** The host's seeding as a fold sequence: one call per composition, like PlayerSurfaceHost. */
    private class Seeder(var width: WindowWidth) {
        var stale: LibraryChrome? = null
        val lastFresh = mutableMapOf<WindowWidth, LibraryChrome>()

        fun compose(width: WindowWidth, live: LibraryChrome, onLibrary: Boolean): MiniBarPlacement? {
            val wide = width != WindowWidth.Compact
            if (this.width != width) {
                this.width = width
                stale = live
            }
            val fresh = freshChrome(wide, live, stale)
            if (fresh != null) {
                stale = null
                lastFresh[width] = fresh
            }
            return miniBarTarget(onLibrary, wide, fresh ?: lastFresh[width], navigationBarBottom = 24.dp)
        }
    }

    @Test
    fun `unfolding with the full player on top places the bar by the rail it had before, not full width`() {
        val seeder = Seeder(WindowWidth.Medium)
        // Unfolded Library, rail published.
        assertEquals(96.dp, seeder.compose(WindowWidth.Medium, collapsedRail, onLibrary = true)?.start)
        // Fold, then open the full player: the Library leaves composition and its chrome resets.
        seeder.compose(WindowWidth.Compact, collapsedRail, onLibrary = true)
        assertEquals(104.dp, seeder.compose(WindowWidth.Compact, compactChrome, onLibrary = true)?.bottom)
        seeder.compose(WindowWidth.Compact, LibraryChrome.None, onLibrary = true)
        // Unfold with the player on top: nothing is published, the remembered rail places the bar.
        val unfolded = seeder.compose(WindowWidth.Medium, LibraryChrome.None, onLibrary = true)
        assertEquals(MiniBarPlacement(bottom = 24.dp, start = 96.dp, widthFraction = TWO_PANE_BAR_WIDTH_FRACTION), unfolded)
    }

    @Test
    fun `the frame a fold happens the old chrome is ignored, and the next publication is taken`() {
        val seeder = Seeder(WindowWidth.Compact)
        seeder.compose(WindowWidth.Compact, compactChrome, onLibrary = true)
        // Unfold, first time wide in this session: the suite still reports the bottom bar -> not known.
        assertNull(seeder.compose(WindowWidth.Medium, compactChrome, onLibrary = true))
        // The rail measures: known, from the first fresh chrome.
        assertEquals(96.dp, seeder.compose(WindowWidth.Medium, collapsedRail, onLibrary = true)?.start)
    }

    @Test
    fun `medium to expanded never takes the collapsed rail for the expanded one`() {
        val seeder = Seeder(WindowWidth.Medium)
        seeder.compose(WindowWidth.Medium, collapsedRail, onLibrary = true)
        assertNull(seeder.compose(WindowWidth.Expanded, collapsedRail, onLibrary = true))
        assertEquals(220.dp, seeder.compose(WindowWidth.Expanded, expandedRail, onLibrary = true)?.start)
    }
}
