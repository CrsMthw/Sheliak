package com.crsmthw.sheliak.service

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ShuffleArraysTest {

    private fun assertPermutation(order: IntArray, length: Int) {
        assertEquals((0 until length).toList(), order.sorted(), order.toList().toString())
    }

    @Test
    fun `a permutation holds every index once`() {
        repeat(20) { seed ->
            assertPermutation(ShuffleArrays.permutation(37, Random(seed)), 37)
        }
        assertEquals(0, ShuffleArrays.permutation(0, Random(1)).size)
    }

    @Test
    fun `starting with a window puts it first and keeps a permutation`() {
        repeat(20) { seed ->
            val order = ShuffleArrays.startingWith(10, 6, Random(seed))
            assertEquals(6, order[0])
            assertPermutation(order, 10)
        }
    }

    @Test
    fun `inserting into an empty order shuffles the new queue`() {
        val order = ShuffleArrays.insert(IntArray(0), 0, 12, Random(3))
        assertPermutation(order, 12)
    }

    @Test
    fun `appending lands at the end of the play order, in order`() {
        val order = intArrayOf(2, 0, 3, 1)
        assertContentEquals(intArrayOf(2, 0, 3, 1, 4, 5), ShuffleArrays.insert(order, 4, 2, Random(1)))
    }

    @Test
    fun `play next lands right after the current window in play order`() {
        // Window 1 is playing (play position 3); "play next" inserts two items at window index 2.
        val order = intArrayOf(2, 0, 3, 1)
        val next = ShuffleArrays.insert(order, 2, 2, Random(1))
        // Old windows 2 and 3 shift to 4 and 5; the new 2 and 3 follow window 1.
        assertContentEquals(intArrayOf(4, 0, 5, 1, 2, 3), next)
    }

    @Test
    fun `inserting at the front of the timeline plays first`() {
        assertContentEquals(intArrayOf(0, 3, 1, 2), ShuffleArrays.insert(intArrayOf(2, 0, 1), 0, 1, Random(1)))
    }

    @Test
    fun `removing drops the windows and renumbers the rest`() {
        val order = intArrayOf(4, 0, 5, 1, 2, 3)
        assertContentEquals(intArrayOf(2, 0, 3, 1), ShuffleArrays.remove(order, 2, 4))
        assertSame(order, ShuffleArrays.remove(order, 3, 3))
    }

    @Test
    fun `a timeline move keeps every item's play position`() {
        // Items A..E at windows 0..4; play order C, A, E, B, D.
        val items = listOf("A", "B", "C", "D", "E")
        val order = intArrayOf(2, 0, 4, 1, 3)
        val played = order.map { items[it] }

        // Move window 1 (B) to window 3: the timeline becomes A, C, D, B, E.
        val moved = ShuffleArrays.move(order, 1, 2, 3)
        val timeline = listOf("A", "C", "D", "B", "E")
        assertEquals(played, moved.map { timeline[it] })
        assertPermutation(moved, 5)
    }

    @Test
    fun `a range move keeps play positions too`() {
        val items = listOf("A", "B", "C", "D", "E")
        val order = intArrayOf(4, 3, 2, 1, 0)
        val played = order.map { items[it] }
        val moved = ShuffleArrays.move(order, 0, 2, 3) // A, B → after E
        val timeline = listOf("C", "D", "E", "A", "B")
        assertEquals(played, moved.map { timeline[it] })
    }

    @Test
    fun `a play-order move reorders play positions only`() {
        val order = intArrayOf(2, 0, 4, 1, 3)
        assertContentEquals(intArrayOf(2, 4, 1, 0, 3), ShuffleArrays.moveInPlayOrder(order, 1, 3))
        assertContentEquals(intArrayOf(3, 2, 0, 4, 1), ShuffleArrays.moveInPlayOrder(order, 4, 0))
        assertSame(order, ShuffleArrays.moveInPlayOrder(order, 2, 2))
        assertSame(order, ShuffleArrays.moveInPlayOrder(order, 2, 9))
    }
}
