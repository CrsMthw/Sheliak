package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.IndexPlaylistEntry
import com.crsmthw.sheliak.data.provider.plex.PlexFixtures.decode
import com.crsmthw.sheliak.domain.AudioFormatInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlexMappingTest {

    private val listed = decode(PlexResponse.serializer(), PlexFixtures.TRACKS_PAGE_1).mediaContainer!!.metadata!!
    private val detailed = decode(PlexResponse.serializer(), PlexFixtures.METADATA_BATCH).mediaContainer!!.metadata!!

    @Test
    fun `a listed track maps ids, numbers and times in milliseconds`() {
        val t = PlexMapping.track(listed[0])!!
        assertEquals("1001", t.itemId)
        assertEquals("Ágætis byrjun", t.title)
        assertEquals("Agaetis byrjun", t.titleSort)
        assertEquals("901", t.albumItemId)
        assertEquals("Ágætis byrjun", t.albumTitle)
        assertEquals("801", t.artistItemId)
        assertEquals("Sigur Rós", t.artistName)
        assertNull(t.albumArtistName)
        assertEquals(1, t.discNo)
        assertEquals(5, t.trackNo)
        assertEquals(475_000L, t.durationMs)
        assertEquals(1999, t.year)
        assertEquals(1_600_000_000_000L, t.addedAt)
        assertEquals(1_700_000_100_000L, t.updatedAt)
        assertEquals(7, t.playCount)
        assertEquals(1_690_000_000_000L, t.lastPlayedAt)
        assertEquals("/library/parts/3001/1600000000/file.flac", t.streamRef)
        assertEquals("/library/metadata/901/thumb/1700000100", t.art)
        assertEquals(172_187_500L, t.fileSize)
    }

    @Test
    fun `a listing gives codec and bit rate, not sample rate or bit depth`() {
        val f = PlexMapping.track(listed[0])!!.format!!
        assertEquals(AudioFormatInfo("flac", "flac", 2900, null, null, 2, lossless = true), f)
        assertEquals(false, PlexMapping.hasStreamDetails(listed[0]))
    }

    @Test
    fun `full metadata adds sample rate, bit depth and loudness gains`() {
        val t = PlexMapping.track(detailed[0])!!
        assertEquals(AudioFormatInfo("flac", "flac", 2900, 96_000, 24, 2, lossless = true), t.format)
        assertEquals(-8.53f, t.replayGainTrackDb)
        assertEquals(-7.21f, t.replayGainAlbumDb)
        assertEquals(true, PlexMapping.hasStreamDetails(detailed[0]))
        val mp3 = PlexMapping.track(detailed[1])!!.format!!
        assertEquals(AudioFormatInfo("mp3", "mp3", 320, 44_100, null, 2, lossless = false), mp3)
    }

    @Test
    fun `the track's own artist wins and the album artist is kept apart`() {
        val t = PlexMapping.track(listed[1])!!
        assertEquals("Guest Artist", t.artistName)
        assertEquals("Various Artists", t.albumArtistName)
        assertEquals("802", t.artistItemId)
        assertEquals(2004, t.year)
    }

    @Test
    fun `disc number falls back from parentIndex to absoluteIndex`() {
        assertEquals(1, PlexMapping.track(listed[0])!!.discNo)
        assertEquals(2, PlexMapping.track(listed[1])!!.discNo)
        assertEquals(3, PlexMapping.track(PlexMetadata(ratingKey = "x", parentIndex = 3, absoluteIndex = 9))!!.discNo)
        assertNull(PlexMapping.track(PlexMetadata(ratingKey = "x"))!!.discNo)
    }

    @Test
    fun `an item without a ratingKey is dropped, a missing title is empty`() {
        assertNull(PlexMapping.track(PlexMetadata(title = "No key")))
        assertNull(PlexMapping.album(PlexMetadata(ratingKey = " "), 0))
        assertEquals("", PlexMapping.track(PlexMetadata(ratingKey = "7"))!!.title)
        assertNull(PlexMapping.track(PlexMetadata(ratingKey = "7"))!!.format)
    }

    @Test
    fun `art falls back from thumb to the album's and the artist's`() {
        assertEquals("/p", PlexMapping.track(PlexMetadata(ratingKey = "1", parentThumb = "/p", grandparentThumb = "/g"))!!.art)
        assertEquals("/g", PlexMapping.track(PlexMetadata(ratingKey = "1", grandparentThumb = "/g"))!!.art)
    }

    @Test
    fun `albums and artists take their derived numbers from the sync`() {
        val album = PlexMapping.album(
            PlexMetadata(ratingKey = "901", title = "Ágætis byrjun", parentRatingKey = "801", parentTitle = "Sigur Rós",
                year = 1999, leafCount = 10, thumb = "/t", addedAt = 10, updatedAt = 20),
            durationMs = 4_000_000,
        )!!
        assertEquals("801", album.artistItemId)
        assertEquals("Sigur Rós", album.artistName)
        assertEquals(10, album.trackCount)
        assertEquals(4_000_000L, album.durationMs)
        assertEquals(10_000L, album.addedAt)
        assertEquals(20_000L, album.updatedAt)
        val artist = PlexMapping.artist(PlexMetadata(ratingKey = "801", title = "Sigur Rós", titleSort = "Sigur Ros"), 3)!!
        assertEquals(3, artist.albumCount)
        assertEquals("Sigur Ros", artist.nameSort)
    }

    @Test
    fun `playlist entries keep order and playlistItemID`() {
        val items = decode(PlexResponse.serializer(), PlexFixtures.PLAYLIST_ITEMS).mediaContainer!!.metadata!!
        val entries = items.mapNotNull(PlexMapping::playlistEntry)
        assertEquals(listOf(IndexPlaylistEntry("1002", "7002"), IndexPlaylistEntry("1001", "7001")), entries)
        val lists = decode(PlexResponse.serializer(), PlexFixtures.PLAYLISTS).mediaContainer!!.metadata!!
        val dumb = PlexMapping.playlist(lists[0], entries)!!
        assertEquals("Road trip", dumb.title)
        assertEquals("/playlists/5001/composite/1700000200", dumb.art)
        assertEquals(2, dumb.trackCount)
        assertEquals(690_000L, dumb.durationMs)
        assertEquals(1_700_000_200_000L, dumb.updatedAt)
        val smart = PlexMapping.playlist(lists[1], emptyList())!!
        assertEquals(5, smart.trackCount)
        assertNull(smart.art)
    }
}
