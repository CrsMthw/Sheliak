package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/*
 * The pure rules behind the art carousel (AlbumArtCarousel) and the wavy seek bar: which carousel item is the
 * playing row, how the carousel follows the player, what a settled swipe does, the hero geometry (the carousel's
 * size and the large item Material must lay out for the queue's length), and where a touch on the seek bar
 * lands. No Compose runtime — unit-tested in ArtCarouselRulesTest.
 *
 * One carousel item per queue row, in PLAY order (`PlayerState.queue`), so item i IS queue row i.
 */

/** The carousel item that shows queue row [queueIndex]; null for an empty queue (no carousel at all). */
fun carouselItemFor(queueIndex: Int, queueSize: Int): Int? =
    if (queueSize <= 0) null else queueIndex.coerceIn(0, queueSize - 1)

/** A programmatic move of the carousel. */
sealed interface CarouselMove {
    /** Already there, or the carousel must not move now. */
    data object Stay : CarouselMove

    /** Glide to [item] on the finite spec. */
    data class Animate(val item: Int) : CarouselMove

    /** Jump to [item] without motion. */
    data class Snap(val item: Int) : CarouselMove
}

/**
 * How the carousel follows the player when the playing row changes (not during a swipe — the caller asks only
 * while no user scroll is in progress).
 *
 * - The TRACK changed to a neighbour (next, previous, auto-advance): glide one item on the finite spec.
 * - The track changed further away (a repeat-all wrap, a far jump): snap — gliding across the queue is noise.
 * - The SAME track moved to another row (shuffle on puts it first, a row above it was removed, the controller's
 *   brief masking timeline): snap — it is the same art, and a glide would show a different track passing by.
 * - A swipe's skip is still on its way ([pendingSkip] set and not yet reported): stay, so the carousel is not
 *   pulled back to the row the player is leaving.
 *
 * @param currentItem the carousel's current (centre) item.
 * @param trackChanged the playing track's key differs from the one the carousel last followed.
 */
fun carouselFollow(
    currentItem : Int,
    queueIndex  : Int,
    queueSize   : Int,
    trackChanged: Boolean,
    pendingSkip : Int?,
): CarouselMove {
    val target = carouselItemFor(queueIndex, queueSize) ?: return CarouselMove.Stay
    if (pendingSkip != null && pendingSkip != target) return CarouselMove.Stay
    if (currentItem == target) return CarouselMove.Stay
    return if (trackChanged && abs(target - currentItem) == 1) CarouselMove.Animate(target) else CarouselMove.Snap(target)
}

/** What a swipe that came to rest does. */
sealed interface CarouselSettle {
    /** Nothing: it rests on the row that plays (or is about to). */
    data object Stay : CarouselSettle

    /**
     * The user picked another row: move the player to [item] (`seekToQueueIndex` — play / pause unchanged; never
     * previous / next, so no 3-second rule).
     */
    data class Skip(val item: Int) : CarouselSettle

    /** The user picked nothing (released where the swipe began) while the player moved on: go back to it. */
    data class Return(val move: CarouselMove) : CarouselSettle
}

/**
 * The carousel came to rest on [settledItem] after a scroll that began on [startItem].
 *
 * The row it is expected to rest on is the pending skip if one is on its way, else the playing row. Resting
 * there changes nothing; resting where the swipe began while the player moved on under the finger returns to
 * the playing row; resting anywhere else moves the player to that row. A programmatic follow always lands on the
 * expected row, so it never skips.
 */
fun carouselSettled(
    settledItem: Int,
    startItem  : Int,
    queueIndex : Int,
    queueSize  : Int,
    pendingSkip: Int?,
): CarouselSettle {
    val playing = carouselItemFor(queueIndex, queueSize) ?: return CarouselSettle.Stay
    if (settledItem !in 0 until queueSize) return CarouselSettle.Stay
    val expected = pendingSkip?.takeIf { it in 0 until queueSize } ?: playing
    return when (settledItem) {
        expected  -> CarouselSettle.Stay
        startItem -> CarouselSettle.Return(
            if (abs(expected - settledItem) == 1) CarouselMove.Animate(expected) else CarouselMove.Snap(expected),
        )
        else      -> CarouselSettle.Skip(settledItem)
    }
}

/**
 * The hero carousel's size: a square art of [itemSide], [width] in all (room for a peek and a gap on each side),
 * and [largeItemWidth] — the width Material's large (focal) item must take for the queue's length, passed as
 * `preferredItemWidth` (see [heroCarouselGeometry]). [largeItemWidth] is never less than [itemSide]; where it is
 * more, the art stays an [itemSide] square centred in the item.
 */
data class HeroCarouselGeometry(val itemSide: Dp, val width: Dp, val largeItemWidth: Dp)

/**
 * Fits the centred hero carousel into [availableWidth] × [availableHeight] for a queue of [queueSize] tracks. The
 * art is square, as tall as the room allows and narrow enough to leave a [peek]-wide neighbour and a [spacing]
 * gap on both sides; the carousel is that wide whatever the queue's length, so the art keeps its size when tracks
 * are added or removed.
 *
 * [HeroCarouselGeometry.largeItemWidth] is what makes Material lay out ONE large item. Its hero keylines
 * (material3 1.5.0-beta01 `heroKeylineList` + `Arrangement.findLowestCostArrangement`, read from the bytecode) keep
 * two small slots only for three or more items (centred), one for two (start-aligned), none for one. They then try
 * large-item counts from `ceil(width / preferred)` down, fit each to the width with the small items pinned to
 * [peek] (passed as both the minimum and the maximum), and keep the fit with the lowest
 * `|preferred − fitted large| × priority` (priority 1, 2, … in that order; a cost of 0 wins at once). With the
 * 40dp peek and 8dp gap, passing [HeroCarouselGeometry.itemSide] for every queue gave a two-track queue's spare
 * peek + gap to the large item: ONE large item of `itemSide + 48dp` cost 48 × 2 and lost to TWO of
 * `(itemSide + 40dp) / 2` (cost `(itemSide − 40dp) / 2`) whenever the art was under 232dp — the pop-out card's two
 * portrait crops (fix round 2); a one-track queue lost the same way under 472dp. So the preferred width is exactly
 * what one large item fills — the width less one peek + gap per small slot Material keeps — and that fit costs 0:
 * [HeroCarouselGeometry.itemSide] from three tracks on (unchanged), `itemSide + peek + spacing` for two (what the
 * full player already landed on), the whole width for one.
 */
fun heroCarouselGeometry(
    availableWidth : Dp,
    availableHeight: Dp,
    queueSize      : Int,
    peek           : Dp,
    spacing        : Dp,
): HeroCarouselGeometry {
    val neighbour = peek + spacing
    val side = minOf(availableHeight, availableWidth - neighbour * 2).coerceAtLeast(0.dp)
    val width = side + neighbour * 2
    return HeroCarouselGeometry(
        itemSide       = side,
        width          = width,
        largeItemWidth = width - neighbour * heroSmallSlots(queueSize),
    )
}

/**
 * How many small (peek) slots Material's hero keylines keep for [queueSize] items: two from three items on (one
 * each side, centred), one for two (start-aligned), none for one.
 */
private fun heroSmallSlots(queueSize: Int): Int = (queueSize - 1).coerceIn(0, 2)

/** Where a touch at [x] (px) on a seek bar [widthPx] wide lands, as a 0..1 share of the track. */
fun seekFractionAt(x: Float, widthPx: Float): Float =
    if (widthPx <= 0f) 0f else (x / widthPx).coerceIn(0f, 1f)

/** The seek bar's haptic notch for [fraction]: one tick per [notch] of the track while dragging (Lyra's 5 %). */
fun seekNotch(fraction: Float, notch: Float): Int = (fraction.coerceIn(0f, 1f) / notch).toInt()
