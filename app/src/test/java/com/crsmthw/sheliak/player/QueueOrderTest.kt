package com.crsmthw.sheliak.player

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class QueueOrderTest {

    /** A shuffle order given as the window indices in play order, walked as a Timeline walks it. */
    private fun walk(playOrder: IntArray): IntArray {
        val next = IntArray(playOrder.size) { QueueOrder.INDEX_UNSET }
        for (p in 0 until playOrder.size - 1) next[playOrder[p]] = playOrder[p + 1]
        return QueueOrder.playOrder(playOrder.size, playOrder.firstOrNull() ?: QueueOrder.INDEX_UNSET) { next[it] }
    }

    @Test
    fun `the play order follows the timeline's next index`() {
        assertContentEquals(intArrayOf(3, 0, 4, 1, 2), walk(intArrayOf(3, 0, 4, 1, 2)))
        assertContentEquals(intArrayOf(0, 1, 2), QueueOrder.playOrder(3, 0) { if (it < 2) it + 1 else QueueOrder.INDEX_UNSET })
    }

    @Test
    fun `an empty timeline has an empty order`() {
        assertEquals(0, QueueOrder.playOrder(0, QueueOrder.INDEX_UNSET) { QueueOrder.INDEX_UNSET }.size)
    }

    @Test
    fun `a malformed order falls back to the timeline order`() {
        // A cycle: 0 → 1 → 0.
        assertContentEquals(intArrayOf(0, 1, 2), QueueOrder.playOrder(3, 0) { if (it == 0) 1 else 0 })
        // Ends early.
        assertContentEquals(intArrayOf(0, 1, 2), QueueOrder.playOrder(3, 2) { QueueOrder.INDEX_UNSET })
        // Out of range.
        assertContentEquals(intArrayOf(0, 1), QueueOrder.playOrder(2, 5) { QueueOrder.INDEX_UNSET })
    }

    @Test
    fun `a window's queue position is its play position`() {
        val order = intArrayOf(3, 0, 4, 1, 2)
        assertEquals(2, QueueOrder.positionOf(order, 4))
        assertEquals(QueueOrder.INDEX_UNSET, QueueOrder.positionOf(order, 9))
    }
}
