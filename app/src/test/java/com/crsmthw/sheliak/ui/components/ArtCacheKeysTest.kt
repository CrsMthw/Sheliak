package com.crsmthw.sheliak.ui.components

import com.crsmthw.sheliak.domain.ArtRef
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtCacheKeysTest {

    private val art = ArtRef("plex:abc", "/library/metadata/1/thumb/2")

    @BeforeTest
    fun reset() = ArtCacheKeys.clear()

    @Test
    fun `key is provider, path and size, not a URL`() {
        assertEquals("art:plex:abc:/library/metadata/1/thumb/2@168", ArtCacheKeys.key(art, 168).key)
    }

    @Test
    fun `nothing loaded means no placeholder`() {
        assertNull(ArtCacheKeys.nearestLoaded(art, 660))
    }

    @Test
    fun `the largest loaded size below the request wins`() {
        ArtCacheKeys.loaded(art, 144)
        ArtCacheKeys.loaded(art, 168)
        ArtCacheKeys.loaded(art, 1024)
        assertEquals(ArtCacheKeys.key(art, 168), ArtCacheKeys.nearestLoaded(art, 660))
    }

    @Test
    fun `with nothing below, the smallest size above is used`() {
        ArtCacheKeys.loaded(art, 1024)
        ArtCacheKeys.loaded(art, 660)
        assertEquals(ArtCacheKeys.key(art, 660), ArtCacheKeys.nearestLoaded(art, 144))
    }

    @Test
    fun `an exact size is its own key`() {
        ArtCacheKeys.loaded(art, 660)
        assertEquals(ArtCacheKeys.key(art, 660), ArtCacheKeys.nearestLoaded(art, 660))
    }

    @Test
    fun `covers do not share sizes`() {
        ArtCacheKeys.loaded(ArtRef("plex:abc", "/other"), 168)
        assertNull(ArtCacheKeys.nearestLoaded(art, 660))
    }
}
