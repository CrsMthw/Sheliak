package com.crsmthw.sheliak.service

import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheliakShuffleOrderTest {

    /** The windows in play order, walked through the ShuffleOrder interface as ExoPlayer walks it. */
    private fun ShuffleOrder.playOrder(): List<Int> {
        val order = ArrayList<Int>(length)
        var index = firstIndex
        while (index != -1) {
            order += index
            index = getNextIndex(index)
        }
        return order
    }

    @Test
    fun `a new queue is a fresh shuffle of every window`() {
        val order = SheliakShuffleOrder(seed = 7).cloneAndInsert(0, 10) as SheliakShuffleOrder
        assertTrue(order.fresh)
        assertEquals((0 until 10).toList(), order.playOrder().sorted())
        assertEquals(order.playOrder().last(), order.lastIndex)
    }

    @Test
    fun `starting with the current window makes it first and the order no longer fresh`() {
        val fresh = SheliakShuffleOrder(seed = 7).cloneAndInsert(0, 10) as SheliakShuffleOrder
        val started = fresh.startingWith(4)
        assertFalse(started.fresh)
        assertEquals(4, started.firstIndex)
        assertEquals((0 until 10).toList(), started.playOrder().sorted())
    }

    @Test
    fun `the same parent derives the same order on every thread`() {
        val parent = SheliakShuffleOrder(seed = 99).cloneAndInsert(0, 25)
        assertEquals(parent.cloneAndInsert(25, 3).playOrder(), parent.cloneAndInsert(25, 3).playOrder())
        assertEquals(
            (parent as SheliakShuffleOrder).startingWith(5).playOrder(),
            parent.startingWith(5).playOrder(),
        )
    }

    @Test
    fun `next and previous walk the play order both ways`() {
        val order = (SheliakShuffleOrder(seed = 1).cloneAndInsert(0, 6) as SheliakShuffleOrder).startingWith(2)
        val forward = order.playOrder()
        val backward = ArrayList<Int>()
        var index = order.lastIndex
        while (index != -1) {
            backward += index
            index = order.getPreviousIndex(index)
        }
        assertEquals(forward.reversed(), backward)
    }

    @Test
    fun `play next follows the current window and add to queue goes last`() {
        val order = (SheliakShuffleOrder(seed = 5).cloneAndInsert(0, 5) as SheliakShuffleOrder).startingWith(3)
        val withNext = order.cloneAndInsert(4, 1) // after window 3
        assertEquals(4, withNext.getNextIndex(3))
        val withLast = withNext.cloneAndInsert(6, 2)
        assertEquals(listOf(6, 7), withLast.playOrder().takeLast(2))
        assertFalse((withLast as SheliakShuffleOrder).fresh)
    }

    @Test
    fun `clearing empties the order`() {
        val cleared = SheliakShuffleOrder(seed = 5).cloneAndInsert(0, 5).cloneAndClear()
        assertEquals(0, cleared.length)
        assertEquals(-1, cleared.firstIndex)
        assertEquals(-1, cleared.lastIndex)
    }
}
