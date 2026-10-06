package com.crsmthw.sheliak.service

import kotlin.random.Random

/**
 * The array arithmetic behind [SheliakShuffleOrder]. A shuffle order is an array `order` where `order[p]` is the
 * timeline (window) index played at play position `p`. Pure; tested in ShuffleArraysTest.
 */
object ShuffleArrays {

    /** A fresh random permutation of `0 until length`. */
    fun permutation(length: Int, random: Random): IntArray {
        val order = IntArray(length) { it }
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        return order
    }

    /** [first] plays first, the rest in a fresh random order. */
    fun startingWith(length: Int, first: Int, random: Random): IntArray {
        if (first !in 0 until length) return permutation(length, random)
        val rest = permutation(length - 1, random).map { if (it >= first) it + 1 else it }
        return IntArray(length) { if (it == 0) first else rest[it - 1] }
    }

    /**
     * [count] windows inserted into the timeline at [at]. Indices at or after [at] shift up by [count]; the new
     * block keeps its own order and is placed:
     * - at the END of the play order when appended to the timeline (`at == order.size`) — "add to queue";
     * - right AFTER window `at - 1` in play order otherwise — "play next" inserts after the current window;
     * - at the FRONT when inserted at timeline index 0.
     *
     * Into an empty order (a new queue) the block is shuffled instead.
     */
    fun insert(order: IntArray, at: Int, count: Int, random: Random): IntArray {
        if (count <= 0) return order
        if (order.isEmpty()) return permutation(count, random).map { it + at }.toIntArray()
        val shifted = IntArray(order.size) { if (order[it] >= at) order[it] + count else order[it] }
        val position = when {
            at >= order.size -> shifted.size
            at <= 0          -> 0
            else             -> shifted.indexOf(at - 1) + 1
        }
        val result = IntArray(order.size + count)
        shifted.copyInto(result, 0, 0, position)
        for (i in 0 until count) result[position + i] = at + i
        shifted.copyInto(result, position + count, position, shifted.size)
        return result
    }

    /** Windows `from until toExclusive` removed from the timeline: dropped from the order, later ones shift down. */
    fun remove(order: IntArray, from: Int, toExclusive: Int): IntArray {
        val removed = toExclusive - from
        if (removed <= 0) return order
        return order.filter { it !in from until toExclusive }
            .map { if (it >= toExclusive) it - removed else it }
            .toIntArray()
    }

    /**
     * Windows `from until toExclusive` moved to start at [newFrom] in the timeline. Every item keeps its PLAY
     * position; only the window indices are renamed (a reorder with shuffle off must not reshuffle the play order).
     */
    fun move(order: IntArray, from: Int, toExclusive: Int, newFrom: Int): IntArray {
        val count = toExclusive - from
        val size = order.size
        if (count <= 0 || from !in 0 until size || toExclusive > size) return order
        val target = newFrom.coerceIn(0, size - count)
        if (from == target) return order
        // Old window index → new window index, as Media3 moves a range (remove it, insert it at the target).
        val timeline = (0 until size).toMutableList()
        val block = timeline.subList(from, toExclusive).toList()
        repeat(count) { timeline.removeAt(from) }
        timeline.addAll(target, block)
        val newIndexOf = IntArray(size)
        timeline.forEachIndexed { newIndex, oldIndex -> newIndexOf[oldIndex] = newIndex }
        return IntArray(size) { newIndexOf[order[it]] }
    }

    /** The item at play position [from] moved to play position [to]; the timeline is untouched. */
    fun moveInPlayOrder(order: IntArray, from: Int, to: Int): IntArray {
        if (from !in order.indices || to !in order.indices || from == to) return order
        val list = order.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list.toIntArray()
    }
}
