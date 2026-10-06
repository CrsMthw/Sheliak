package com.crsmthw.sheliak.service

import com.crsmthw.sheliak.domain.TrackKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaIdsTest {

    private val awkwardKeys = listOf(
        TrackKey("plex:abc123", "4567"),
        TrackKey("local", "content://media/external/audio/media/12"),
        TrackKey("smb:share-1", "/Music/AC|DC/Back in Black.flac"),
        TrackKey("jellyfin:9f8e", "a b?c=d&e#f%20g+h"),
        TrackKey("plex:x", "Sigur Rós – ( )"),
        TrackKey("plex:x", "track.m3u8"),
    )

    @Test
    fun `a stream uri names its track and round-trips`() {
        awkwardKeys.forEach { key ->
            val uri = StreamUri.of(key)
            assertTrue(uri.startsWith("sheliak://track?id="), uri)
            assertEquals(key, StreamUri.keyOf(uri), uri)
        }
    }

    @Test
    fun `a stream uri keeps the id out of the path so no extension is ever inferred`() {
        val uri = StreamUri.of(TrackKey("plex:x", "track.m3u8"))
        assertTrue(!uri.substringBefore('?').contains(".m3u8"), uri)
    }

    @Test
    fun `uris that are not ours are passed through`() {
        listOf(
            "content://media/external/audio/media/12",
            "file:///sdcard/Music/a.flac",
            "https://server:32400/library/parts/1/file.flac",
            "sheliak://track",
            "sheliak://track?id=",
            "sheliak://album?id=plex%3Ax%7C1",
        ).forEach { assertNull(StreamUri.keyOf(it), it) }
    }

    @Test
    fun `every browse node round-trips through its id`() {
        val album = TrackKey("plex:abc", "100")
        val nodes = listOf(
            BrowseNode.Root, BrowseNode.Tracks, BrowseNode.Albums, BrowseNode.Artists, BrowseNode.Playlists,
            BrowseNode.Recent,
            BrowseNode.Album(album),
            BrowseNode.Artist(TrackKey("smb:s", "/Music/AC|DC")),
            BrowseNode.Playlist(42),
        )
        val inContext = nodes.filter { it != BrowseNode.Root }.flatMap { parent ->
            awkwardKeys.map { BrowseNode.InContext(parent, it) }
        }
        (nodes + inContext).forEach { node ->
            val id = BrowseIds.id(node)
            assertTrue(id.startsWith(BrowseIds.PREFIX), id)
            assertEquals(node, BrowseIds.parse(id), id)
        }
    }

    @Test
    fun `a track media id is never a browse node`() {
        awkwardKeys.forEach { key ->
            assertNull(BrowseIds.parse(key.mediaId), key.mediaId)
            assertEquals(key, BrowseIds.trackOf(key.mediaId), key.mediaId)
        }
    }

    @Test
    fun `the track of a row in a list is its own key`() {
        val key = TrackKey("plex:abc", "7")
        val id = BrowseIds.id(BrowseNode.InContext(BrowseNode.Album(TrackKey("plex:abc", "100")), key))
        assertEquals(key, BrowseIds.trackOf(id))
        assertNull(BrowseIds.trackOf(BrowseIds.id(BrowseNode.Albums)))
    }

    @Test
    fun `malformed node ids parse to null`() {
        listOf(
            "@", "@nope", "@album:", "@album:noseparator", "@playlist:x", "@playlist:",
            "@in:", "@in:x:", "@in:999:@tracksplex:a|1", "@in:7:@tracksplex:a", "@in:5:@rootplex:a|1",
            "@in:0:plex:a|1",
        ).forEach { assertNull(BrowseIds.parse(it), it) }
    }
}
