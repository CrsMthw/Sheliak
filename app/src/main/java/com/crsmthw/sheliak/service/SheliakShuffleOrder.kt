@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * The player's shuffle order, so shuffle behaves the way a queue screen promises (DESIGN §3 keeps shuffle as the
 * player's own mode; Media3's default order inserts added items at RANDOM play positions and keeps the order
 * unchanged across a timeline move):
 * - "Play next" lands right after the current track and "Add to queue" at the end, in play order too
 *   ([ShuffleArrays.insert]);
 * - a reorder with shuffle off keeps every item's play position ([ShuffleArrays.move]);
 * - a new queue, or switching shuffle on, starts from the current track ([startingWith], applied by
 *   `PlaybackService` when it sees a [fresh] order or the shuffle switch);
 * - a reorder of the queue screen with shuffle on is a play-order move ([movedInPlayOrder], run by the
 *   service for the `MOVE_IN_PLAY_ORDER` session command).
 *
 * Immutable and deterministic: every derived order seeds its own Random from [seed], so the app thread and the
 * playback thread derive identical orders from the same parent (as Media3's own DefaultShuffleOrder does).
 */
class SheliakShuffleOrder private constructor(
    private val order: IntArray,
    private val seed: Long,
    /** True when this order was made by inserting into an empty one: a new queue, not yet started from its current. */
    val fresh: Boolean,
) : ShuffleOrder {

    constructor(seed: Long) : this(IntArray(0), seed, fresh = false)

    private val positionOf = IntArray(order.size).also { positions -> order.forEachIndexed { p, w -> positions[w] = p } }

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        val next = positionOf[index] + 1
        return if (next < order.size) order[next] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val previous = positionOf[index] - 1
        return if (previous >= 0) order[previous] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order[order.size - 1]

    override fun getFirstIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order[0]

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder =
        SheliakShuffleOrder(
            order = ShuffleArrays.insert(order, insertionIndex, insertionCount, Random(seed)),
            seed  = childSeed(),
            fresh = order.isEmpty() && insertionCount > 0,
        )

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder =
        SheliakShuffleOrder(ShuffleArrays.remove(order, indexFrom, indexToExclusive), childSeed(), fresh = false)

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder =
        SheliakShuffleOrder(ShuffleArrays.move(order, indexFrom, indexToExclusive, newIndexFrom), childSeed(), fresh)

    override fun cloneAndClear(): ShuffleOrder = SheliakShuffleOrder(childSeed())

    /** The same length, [windowIndex] first and the rest freshly shuffled. */
    fun startingWith(windowIndex: Int): SheliakShuffleOrder =
        SheliakShuffleOrder(ShuffleArrays.startingWith(order.size, windowIndex, Random(seed)), childSeed(), fresh = false)

    /** The item at play position [from] moved to play position [to]. */
    fun movedInPlayOrder(from: Int, to: Int): SheliakShuffleOrder =
        SheliakShuffleOrder(ShuffleArrays.moveInPlayOrder(order, from, to), childSeed(), fresh = false)

    private fun childSeed(): Long = Random(seed).nextLong()
}
