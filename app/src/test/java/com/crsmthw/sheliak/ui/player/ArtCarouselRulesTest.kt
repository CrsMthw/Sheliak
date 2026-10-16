package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The art carousel's index ↔ queue mapping, its hero geometry (fix round 2: short queues) and the wavy seek bar's
 * geometry (M1 fix round 1, items 50 / 52).
 */
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
        val geometry = heroCarouselGeometry(availableWidth = 400.dp, availableHeight = 600.dp, queueSize = 10, peek = 40.dp, spacing = 8.dp)
        assertEquals(304.dp, geometry.itemSide)
        assertEquals(400.dp, geometry.width)
    }

    @Test
    fun `a short area makes the art as tall as the room and the carousel narrower`() {
        val geometry = heroCarouselGeometry(availableWidth = 600.dp, availableHeight = 300.dp, queueSize = 10, peek = 40.dp, spacing = 8.dp)
        assertEquals(300.dp, geometry.itemSide)
        assertEquals(396.dp, geometry.width)
    }

    @Test
    fun `no room gives no art rather than a negative size`() {
        val geometry = heroCarouselGeometry(availableWidth = 60.dp, availableHeight = 300.dp, queueSize = 10, peek = 40.dp, spacing = 8.dp)
        assertEquals(0.dp, geometry.itemSide)
    }

    @Test
    fun `from three tracks on the large item is the art`() {
        for (queueSize in listOf(3, 4, 50)) {
            val geometry = heroCarouselGeometry(availableWidth = 390.dp, availableHeight = 135.dp, queueSize = queueSize, peek = 40.dp, spacing = 8.dp)
            assertEquals(135.dp, geometry.largeItemWidth)
            // Exactly one large item between two peeks fills the carousel.
            assertEquals(geometry.width, geometry.largeItemWidth + (40.dp + 8.dp) * 2)
        }
    }

    @Test
    fun `a two-track queue's large item fills the carousel beside ONE peek`() {
        // The pop-out card of Cris's report: an art area 390 x 135dp.
        val geometry = heroCarouselGeometry(availableWidth = 390.dp, availableHeight = 135.dp, queueSize = 2, peek = 40.dp, spacing = 8.dp)
        assertEquals(135.dp, geometry.itemSide)
        assertEquals(231.dp, geometry.width)
        assertEquals(183.dp, geometry.largeItemWidth)
        assertEquals(geometry.width, geometry.largeItemWidth + 40.dp + 8.dp)
    }

    @Test
    fun `a one-track queue's large item is the whole carousel`() {
        val geometry = heroCarouselGeometry(availableWidth = 390.dp, availableHeight = 135.dp, queueSize = 1, peek = 40.dp, spacing = 8.dp)
        assertEquals(135.dp, geometry.itemSide)
        assertEquals(geometry.width, geometry.largeItemWidth)
    }

    @Test
    fun `the art and the carousel keep their size whatever the queue's length`() {
        val sizes = (1..5).map { heroCarouselGeometry(availableWidth = 600.dp, availableHeight = 300.dp, queueSize = it, peek = 40.dp, spacing = 8.dp) }
        assertEquals(setOf(300.dp), sizes.map { it.itemSide }.toSet())
        assertEquals(setOf(396.dp), sizes.map { it.width }.toSet())
        sizes.forEach { assertTrue(it.largeItemWidth >= it.itemSide) }
    }

    // ── heroCarouselGeometry under Material's hero keylines ─────────────────

    @Test
    fun `the port reproduces the reported layouts with the art as the preferred width`() {
        // The card (art 135dp, carousel 231dp), two tracks: two equal large items of 87.5dp — Cris's two portrait
        // crops; one track: one item of 111.5dp, also a crop.
        assertEquals(HeroFit(largeCount = 2, largeSize = 87.5f), materialHeroFit(width = 231f, preferred = 135f, spacing = 8f, itemCount = 2, peek = 40f))
        assertEquals(HeroFit(largeCount = 2, largeSize = 111.5f), materialHeroFit(width = 231f, preferred = 135f, spacing = 8f, itemCount = 1, peek = 40f))
        // The folded full player (art 315dp), two tracks: one large item of 363dp beside the peek — the layout Cris
        // called right — and the same with the new preferred width.
        assertEquals(HeroFit(largeCount = 1, largeSize = 363f), materialHeroFit(width = 411f, preferred = 315f, spacing = 8f, itemCount = 2, peek = 40f))
        val folded = heroCarouselGeometry(availableWidth = 411.dp, availableHeight = 600.dp, queueSize = 2, peek = 40.dp, spacing = 8.dp)
        assertEquals(363.dp, folded.largeItemWidth)
    }

    @Test
    fun `Material lays out ONE large item of the preferred width at every size and queue length`() {
        for (density in listOf(1f, 2f, 2.625f, 2.8f, 3f, 3.5f)) {
            for (artArea in 60..900) {
                for (queueSize in 1..6) {
                    val geometry = heroCarouselGeometry(availableWidth = 2000.dp, availableHeight = artArea.dp, queueSize = queueSize, peek = 40.dp, spacing = 8.dp)
                    // In pixels as on the device: the pager hands Material the carousel's width and the gap in whole
                    // pixels; the preferred width and the small size reach it unrounded.
                    val fit = materialHeroFit(
                        width     = (geometry.width.value * density).roundToInt().toFloat(),
                        preferred = geometry.largeItemWidth.value * density,
                        spacing   = (8f * density).roundToInt().toFloat(),
                        itemCount = queueSize,
                        peek      = 40f * density,
                    )
                    val case = "density $density, art $artArea dp, $queueSize tracks"
                    assertEquals(1, fit.largeCount, case)
                    // Within the rounding: half a pixel of width and of each of up to two gaps.
                    assertTrue(abs(fit.largeSize - geometry.largeItemWidth.value * density) <= 2f, case)
                    assertTrue(geometry.largeItemWidth >= geometry.itemSide, case)
                }
            }
        }
    }

    /** Which large items Material's hero arrangement search lays out: how many, and how wide. */
    private data class HeroFit(val largeCount: Int, val largeSize: Float)

    /**
     * Material 3 1.5.0-beta01's hero arrangement search — `heroKeylineList` → `Arrangement.findLowestCostArrangement`
     * / `fit` / `calculateLargeSize`, ported from the bytecode for these tests only: the hero list has no medium
     * items, and the small items are pinned to [peek] (passed as Material's minimum AND maximum, as
     * AlbumArtCarousel does). Small slots: none for one item, one for two (start-aligned), two from three on
     * (centred), none if the carousel is too narrow for them; large counts tried from `ceil(width / target)` down,
     * cost `|target − fitted large| × priority`, the first lowest kept, a cost of 0 at once. Re-read it on a
     * Material bump (docs/PLAYER.md, "Player UI, fix round 2 (lane G)").
     */
    private fun materialHeroFit(width: Float, preferred: Float, spacing: Float, itemCount: Int, peek: Float): HeroFit {
        val target = minOf(preferred, width)
        var smallCount = when {
            itemCount <= 1 -> 0
            itemCount >= 3 -> 2
            else           -> 1
        }
        if (width < peek * smallCount + peek * 1.25f) smallCount = 0
        val minLarge = maxOf(1, floor((width - peek * smallCount) / target).toInt())
        val maxLarge = ceil(width / target).toInt()
        var best: HeroFit? = null
        var bestCost = Float.MAX_VALUE
        for ((index, largeCount) in (maxLarge downTo minLarge).withIndex()) {
            val large = (width - (largeCount + smallCount - 1) * spacing - smallCount * peek) / largeCount
            val valid = smallCount == 0 || large > peek
            val cost = if (valid) abs(target - large) * (index + 1) else Float.MAX_VALUE
            if (best == null || cost < bestCost) {
                best = HeroFit(largeCount, large)
                bestCost = cost
                if (cost == 0f) break
            }
        }
        return checkNotNull(best)
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
    fun `the haptic notch ticks every five percent`() {
        assertEquals(0, seekNotch(0.04f, 0.05f))
        assertEquals(1, seekNotch(0.05f, 0.05f))
        assertEquals(19, seekNotch(0.99f, 0.05f))
        assertEquals(20, seekNotch(1f, 0.05f))
    }
}
