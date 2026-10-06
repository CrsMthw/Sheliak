package com.crsmthw.sheliak.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackKeyTest {

    @Test
    fun `mediaId joins the provider and the item with a bar`() {
        assertEquals("plex:abc123|4567", TrackKey("plex:abc123", "4567").mediaId)
    }

    @Test
    fun `parseMediaId is the inverse of mediaId`() {
        listOf(
            TrackKey("plex:abc123", "4567"),
            TrackKey("local", "content://media/external/audio/media/12"),
            TrackKey("smb:share-1", "/Music/AC|DC/Back in Black.flac"),   // an item id may contain the separator
            TrackKey("jellyfin:9f8e", "a1b2c3d4"),
        ).forEach { key ->
            assertEquals(key, TrackKey.parseMediaId(key.mediaId), key.mediaId)
        }
    }

    @Test
    fun `parseMediaId splits on the first separator only`() {
        assertEquals(TrackKey("p", "a|b|c"), TrackKey.parseMediaId("p|a|b|c"))
    }

    @Test
    fun `ids that are not ours parse to null`() {
        listOf("", "|", "noseparator", "|item", "provider|", "root").forEach {
            assertNull(TrackKey.parseMediaId(it), it)
        }
    }
}
