package com.crsmthw.sheliak.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchRequestTest {

    @Test
    fun `one item with an empty media id is the controller's placeholder for a search`() {
        assertTrue(SearchRequest.isPlaceholder(itemCount = 1, firstMediaId = ""))
    }

    @Test
    fun `a resolved queue is not a placeholder`() {
        assertFalse(SearchRequest.isPlaceholder(itemCount = 1, firstMediaId = "plex:abc|1001"))
        assertFalse(SearchRequest.isPlaceholder(itemCount = 2, firstMediaId = ""))
        assertFalse(SearchRequest.isPlaceholder(itemCount = 0, firstMediaId = null))
        assertFalse(SearchRequest.isPlaceholder(itemCount = 1, firstMediaId = null))
    }
}
