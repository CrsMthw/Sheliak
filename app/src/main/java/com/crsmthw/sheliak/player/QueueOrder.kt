package com.crsmthw.sheliak.player

/**
 * The queue as the UI sees it: the player's timeline (window) indices in PLAY order. With shuffle off that is
 * 0, 1, 2…; with shuffle on it is the timeline's shuffle order, which the session sends to the controller with the
 * timeline. Pure; tested in QueueOrderTest.
 */
object QueueOrder {

    /** Media3's `C.INDEX_UNSET`. */
    const val INDEX_UNSET: Int = -1

    /**
     * Walks the order from [firstIndex] through [next] (which returns [INDEX_UNSET] at the end). Never longer than
     * [windowCount]; a malformed order (a cycle, an index out of range) falls back to the timeline order so the
     * queue always lists every window exactly once.
     */
    fun playOrder(windowCount: Int, firstIndex: Int, next: (Int) -> Int): IntArray {
        if (windowCount <= 0) return IntArray(0)
        val order = IntArray(windowCount)
        val seen = BooleanArray(windowCount)
        var index = firstIndex
        var count = 0
        while (index != INDEX_UNSET && count < windowCount) {
            if (index !in 0 until windowCount || seen[index]) return identity(windowCount)
            seen[index] = true
            order[count++] = index
            index = next(index)
        }
        return if (count == windowCount) order else identity(windowCount)
    }

    fun identity(windowCount: Int): IntArray = IntArray(windowCount.coerceAtLeast(0)) { it }

    /** The play-order position of [windowIndex], or [INDEX_UNSET]. */
    fun positionOf(order: IntArray, windowIndex: Int): Int = order.indexOf(windowIndex)
}
