package com.crsmthw.sheliak.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lyra's `"album-art#<generation>"` re-key rule (DESIGN §5.1), as ported in fix round 1 (items 53 / 59). */
class ArtKeyGenerationTest {

    @Test
    fun `the key carries the generation`() {
        assertEquals("album-art#0", albumArtKey(0))
        assertEquals("album-art#3", albumArtKey(3))
    }

    @Test
    fun `quiet means settled, no gesture of either kind and no shared transition running`() {
        assertTrue(morphQuiet(surfaceSettled = true, panelGestureIdle = true, routeGestureIdle = true, sharedTransitionActive = false))
        assertFalse(morphQuiet(surfaceSettled = false, panelGestureIdle = true, routeGestureIdle = true, sharedTransitionActive = false))
        assertFalse(morphQuiet(surfaceSettled = true, panelGestureIdle = false, routeGestureIdle = true, sharedTransitionActive = false))
        assertFalse(morphQuiet(surfaceSettled = true, panelGestureIdle = true, routeGestureIdle = false, sharedTransitionActive = false))
        assertFalse(morphQuiet(surfaceSettled = true, panelGestureIdle = true, routeGestureIdle = true, sharedTransitionActive = true))
    }

    @Test
    fun `an ordinary settle never re-keys`() {
        val keys = ArtKeyGeneration()
        // Committed opens, closes, pushes, pops, folds, swaps: quiet moments with nothing abandoned.
        repeat(5) { assertFalse(keys.onQuiet(true)) }
        assertEquals(0, keys.generation)
    }

    @Test
    fun `an abandoned seek re-keys once, at the next quiet moment`() {
        val keys = ArtKeyGeneration()
        keys.onSeekAbandoned()
        assertTrue(keys.pending)
        assertTrue(keys.onQuiet(true))
        assertEquals(1, keys.generation)
        assertFalse(keys.pending)
        // The next quiet moment keeps the new key.
        assertFalse(keys.onQuiet(true))
        assertEquals(1, keys.generation)
    }

    @Test
    fun `the request waits while the morph is not quiet`() {
        val keys = ArtKeyGeneration()
        keys.onSeekAbandoned()
        // The unwind is still running, the navigation transition still unwinding, a new gesture started…
        repeat(3) { assertFalse(keys.onQuiet(false)) }
        assertEquals(0, keys.generation)
        assertTrue(keys.pending)
        assertTrue(keys.onQuiet(true))
        assertEquals(1, keys.generation)
    }

    @Test
    fun `two abandoned seeks before a quiet moment cost one generation`() {
        val keys = ArtKeyGeneration()
        keys.onSeekAbandoned()
        keys.onSeekAbandoned()
        assertTrue(keys.onQuiet(true))
        assertFalse(keys.onQuiet(true))
        assertEquals(1, keys.generation)
    }

    @Test
    fun `cancel then cancel again re-keys after each`() {
        // Cris's sequence: cancel a back, back again (cancel again), each followed by a quiet moment.
        val keys = ArtKeyGeneration()
        keys.onSeekAbandoned()
        keys.onQuiet(true)
        keys.onSeekAbandoned()
        keys.onQuiet(true)
        assertEquals(2, keys.generation)
        assertEquals("album-art#2", albumArtKey(keys.generation))
    }
}
