package com.crsmthw.sheliak.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistExpansionTest {

    @Test
    fun `one item per request keeps the start index`() {
        assertEquals(3, PlaylistExpansion.remapStartIndex(listOf(1, 1, 1, 1, 1), 3))
    }

    @Test
    fun `an album before the start shifts it by its tracks`() {
        // [track, album of 10, track ← start]
        assertEquals(11, PlaylistExpansion.remapStartIndex(listOf(1, 10, 1), 2))
    }

    @Test
    fun `a start that expanded to nothing moves to the next item`() {
        assertEquals(1, PlaylistExpansion.remapStartIndex(listOf(1, 0, 3), 1))
        // Nothing follows it: the last item.
        assertEquals(2, PlaylistExpansion.remapStartIndex(listOf(1, 2, 0), 2))
    }

    @Test
    fun `an unset start stays unset and an empty result starts at zero`() {
        assertEquals(PlaylistExpansion.INDEX_UNSET, PlaylistExpansion.remapStartIndex(listOf(3), PlaylistExpansion.INDEX_UNSET))
        assertEquals(0, PlaylistExpansion.remapStartIndex(listOf(0, 0), 1))
    }

    @Test
    fun `pages slice the list and stop at its end`() {
        assertEquals(0 until 10, PlaylistExpansion.pageRange(25, 0, 10))
        assertEquals(20 until 25, PlaylistExpansion.pageRange(25, 2, 10))
        assertNull(PlaylistExpansion.pageRange(25, 3, 10))
        assertNull(PlaylistExpansion.pageRange(25, -1, 10))
        assertNull(PlaylistExpansion.pageRange(25, 0, 0))
    }

    @Test
    fun `an unbounded page size does not overflow`() {
        assertEquals(0 until 25, PlaylistExpansion.pageRange(25, 0, Int.MAX_VALUE))
        assertNull(PlaylistExpansion.pageRange(25, 2, Int.MAX_VALUE))
    }
}
