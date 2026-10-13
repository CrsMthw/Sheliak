package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The art carousel's index ↔ queue mapping and the wavy seek bar's geometry (M1 fix round 1, items 50 / 52). */
class ArtCarouselRulesTest {

    // ── carouselItemFor ─────────────────────────────────────────────────────

    @Test
    fun `item i is queue row i`() {
        assertEquals(0, carouselItemFor(queueIndex = 0, queueSize = 10))
        assertEquals(7, carouselItemFor(queueIndex = 7, queueSize = 10))
    }

    @Test
    fun `an empty queue has no carousel item`() {
        assertNull(carouselItemFor(queueIndex = -1, queueSize = 0))
        assertNull(carouselItemFor(queueIndex = 0, queueSize = 0))
    }

    @Test
    fun `an index outside the queue is clamped into it`() {
        assertEquals(0, carouselItemFor(queueIndex = -1, queueSize = 3))
        assertEquals(2, carouselItemFor(queueIndex = 5, queueSize = 3))
    }

    // ── carouselFollow (the player moves) ───────────────────────────────────

    @Test
    fun `next or previous by the player glides one item`() {
        assertEquals(CarouselMove.Animate(5), carouselFollow(currentItem = 4, queueIndex = 5, queueSize = 10, trackChanged = true, pendingSkip = null))
        assertEquals(CarouselMove.Animate(3), carouselFollow(currentItem = 4, queueIndex = 3, queueSize = 10, trackChanged = true, pendingSkip = null))
    }

    @Test
    fun `a far jump of the track snaps`() {
        // Repeat all wrapping from the last row to the first.
        assertEquals(CarouselMove.Snap(0), carouselFollow(currentItem = 9, queueIndex = 0, queueSize = 10, trackChanged = true, pendingSkip = null))
    }

    @Test
    fun `the same track moving to another row snaps and never glides`() {
        // Shuffle on puts the playing track first; a removal above it shifts it up by one.
        assertEquals(CarouselMove.Snap(0), carouselFollow(currentItem = 6, queueIndex = 0, queueSize = 10, trackChanged = false, pendingSkip = null))
        assertEquals(CarouselMove.Snap(3), carouselFollow(currentItem = 4, queueIndex = 3, queueSize = 9, trackChanged = false, pendingSkip = null))
    }

    @Test
    fun `already there stays`() {
        assertEquals(CarouselMove.Stay, carouselFollow(currentItem = 4, queueIndex = 4, queueSize = 10, trackChanged = true, pendingSkip = null))
    }

    @Test
    fun `while a swipe's skip is on its way the carousel is not pulled back`() {
        // Swiped 4 -> 5; the player still reports 4 (or a stale report arrives): stay on 5.
        assertEquals(CarouselMove.Stay, carouselFollow(currentItem = 5, queueIndex = 4, queueSize = 10, trackChanged = false, pendingSkip = 5))
        assertEquals(CarouselMove.Stay, carouselFollow(currentItem = 4, queueIndex = 5, queueSize = 10, trackChanged = true, pendingSkip = 4))
    }

    @Test
    fun `an empty queue never moves the carousel`() {
        assertEquals(CarouselMove.Stay, carouselFollow(currentItem = 0, queueIndex = -1, queueSize = 0, trackChanged = true, pendingSkip = null))
    }

    // ── carouselSettled (the user swiped) ───────────────────────────────────

    @Test
    fun `a swipe that rests on another row plays that row, previous included`() {
        assertEquals(CarouselSettle.Skip(5), carouselSettled(settledItem = 5, startItem = 4, queueIndex = 4, queueSize = 10, pendingSkip = null))
        // Back ALWAYS goes to the previous track — a skip to its row, never the 3-second restart.
        assertEquals(CarouselSettle.Skip(3), carouselSettled(settledItem = 3, startItem = 4, queueIndex = 4, queueSize = 10, pendingSkip = null))
    }

    @Test
    fun `a swipe that rests where the player is changes nothing`() {
        assertEquals(CarouselSettle.Stay, carouselSettled(settledItem = 4, startItem = 4, queueIndex = 4, queueSize = 10, pendingSkip = null))
        // A programmatic follow lands on the playing row.
        assertEquals(CarouselSettle.Stay, carouselSettled(settledItem = 5, startItem = 4, queueIndex = 5, queueSize = 10, pendingSkip = null))
    }

    @Test
    fun `released where it began while the track advanced under the finger, it returns to the playing row`() {
        assertEquals(
            CarouselSettle.Return(CarouselMove.Animate(5)),
            carouselSettled(settledItem = 4, startItem = 4, queueIndex = 5, queueSize = 10, pendingSkip = null),
        )
    }

    @Test
    fun `a quick swipe back before the first skip landed plays the row it rests on`() {
        // Swiped 4 -> 5 (skip 5 pending), then straight back to 4.
        assertEquals(CarouselSettle.Skip(4), carouselSettled(settledItem = 4, startItem = 5, queueIndex = 4, queueSize = 10, pendingSkip = 5))
    }

    @Test
    fun `resting on the pending row changes nothing`() {
        assertEquals(CarouselSettle.Stay, carouselSettled(settledItem = 5, startItem = 5, queueIndex = 4, queueSize = 10, pendingSkip = 5))
    }

    @Test
    fun `a settle outside the queue or on an empty queue does nothing`() {
        assertEquals(CarouselSettle.Stay, carouselSettled(settledItem = 12, startItem = 4, queueIndex = 4, queueSize = 10, pendingSkip = null))
        assertEquals(CarouselSettle.Stay, carouselSettled(settledItem = 0, startItem = 0, queueIndex = -1, queueSize = 0, pendingSkip = null))
    }

    // ── heroCarouselGeometry ────────────────────────────────────────────────

    @Test
    fun `the art is square and leaves a peek and a gap on both sides`() {
        val geometry = heroCarouselGeometry(availableWidth = 400.dp, availableHeight = 600.dp, peek = 40.dp, spacing = 8.dp)
        assertEquals(304.dp, geometry.itemSide)
        assertEquals(400.dp, geometry.width)
    }

    @Test
    fun `a short area makes the art as tall as the room and the carousel narrower`() {
        val geometry = heroCarouselGeometry(availableWidth = 600.dp, availableHeight = 300.dp, peek = 40.dp, spacing = 8.dp)
        assertEquals(300.dp, geometry.itemSide)
        assertEquals(396.dp, geometry.width)
    }

    @Test
    fun `no room gives no art rather than a negative size`() {
        val geometry = heroCarouselGeometry(availableWidth = 60.dp, availableHeight = 300.dp, peek = 40.dp, spacing = 8.dp)
        assertEquals(0.dp, geometry.itemSide)
    }

    // ── The wavy seek bar ───────────────────────────────────────────────────

    @Test
    fun `a touch maps to its share of the track, clamped`() {
        assertEquals(0.25f, seekFractionAt(x = 100f, widthPx = 400f))
        assertEquals(0f, seekFractionAt(x = -20f, widthPx = 400f))
        assertEquals(1f, seekFractionAt(x = 450f, widthPx = 400f))
        assertEquals(0f, seekFractionAt(x = 10f, widthPx = 0f))
    }

    @Test
    fun `the thumb is centred on the progress point and stays inside the track`() {
        assertEquals(198, seekThumbOffset(fraction = 0.5f, widthPx = 400, thumbWidthPx = 4))
        assertEquals(0, seekThumbOffset(fraction = 0f, widthPx = 400, thumbWidthPx = 4))
        assertEquals(396, seekThumbOffset(fraction = 1f, widthPx = 400, thumbWidthPx = 4))
        assertEquals(0, seekThumbOffset(fraction = 0.5f, widthPx = 0, thumbWidthPx = 4))
    }

    @Test
    fun `the haptic notch ticks every five percent`() {
        assertEquals(0, seekNotch(0.04f, 0.05f))
        assertEquals(1, seekNotch(0.05f, 0.05f))
        assertEquals(19, seekNotch(0.99f, 0.05f))
        assertEquals(20, seekNotch(1f, 0.05f))
    }
}
