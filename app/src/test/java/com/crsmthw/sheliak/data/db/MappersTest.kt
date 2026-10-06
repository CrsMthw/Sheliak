package com.crsmthw.sheliak.data.db

import com.crsmthw.sheliak.data.db.entity.ProviderEntity
import com.crsmthw.sheliak.data.provider.IndexAlbum
import com.crsmthw.sheliak.data.provider.IndexArtist
import com.crsmthw.sheliak.data.provider.IndexPlaylist
import com.crsmthw.sheliak.data.provider.IndexPlaylistEntry
import com.crsmthw.sheliak.data.provider.IndexTrack
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.PlaylistKind
import com.crsmthw.sheliak.domain.TrackKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MappersTest {

    private val pid = "plex:aaa"
    private val flac = AudioFormatInfo("flac", "flac", 1411, 96_000, 24, 2, lossless = true)

    private val track = IndexTrack(
        itemId = "t1", title = "Hyperballad", titleSort = null, albumItemId = "al1", albumTitle = "Post",
        artistItemId = "ar1", artistName = "Björk", albumArtistName = "Björk", discNo = 1, trackNo = 4,
        durationMs = 321_000, year = 1995, genre = "Electronic", format = flac, fileSize = 55_000_000,
        art = "/library/metadata/al1/thumb/1", streamRef = "/library/parts/9/file.flac", hasEmbeddedLyrics = true,
        replayGainTrackDb = -6.5f, replayGainAlbumDb = -7f, addedAt = 10, updatedAt = 20, playCount = 3,
        lastPlayedAt = 30,
    )

    @Test
    fun `a sink track becomes a row with keys, sort string and provider-qualified links`() {
        val e = track.toEntity(pid, syncRun = 99)
        assertEquals(pid, e.providerId)
        assertEquals("hyperballad", e.titleSort)
        assertEquals(NormalizedKeys.trackKey("Björk", "Post", 1, 4, "Hyperballad"), e.trackKey)
        assertEquals(pid, e.albumPid)
        assertEquals("al1", e.albumIid)
        assertEquals(pid, e.artistPid)
        assertEquals("flac", e.codec)
        assertEquals(24, e.bitDepth)
        assertEquals(true, e.lossless)
        assertEquals(99, e.syncRun)
    }

    @Test
    fun `the server's sort title wins over the title`() {
        assertEquals("beatles, the", IndexArtist(itemId = "1", name = "The Beatles", nameSort = "Beatles, The")
            .toEntity(pid, 1).nameSort)
        assertEquals("the beatles", IndexArtist(itemId = "1", name = "The Beatles", nameSort = " ")
            .toEntity(pid, 1).nameSort)
    }

    @Test
    fun `a row maps to the domain track`() {
        val t = track.toEntity(pid, 1).toDomain()
        assertEquals(TrackKey(pid, "t1"), t.key)
        assertEquals(TrackKey(pid, "al1"), t.albumKey)
        assertEquals(TrackKey(pid, "ar1"), t.artistKey)
        assertEquals("Post", t.albumTitle)
        assertEquals(flac, t.format)
        assertEquals(ArtRef(pid, "/library/metadata/al1/thumb/1"), t.art)
        assertEquals(321_000, t.durationMs)
        assertEquals(3, t.playCount)
        assertEquals(30L, t.lastPlayedAt)
    }

    @Test
    fun `missing links, format and art map to null`() {
        val bare = IndexTrack(itemId = "t2", title = "Untitled", artistName = "Unknown", art = "")
            .toEntity(pid, 1)
        val t = bare.toDomain()
        assertNull(t.albumKey)
        assertNull(t.artistKey)
        assertNull(t.format)
        assertNull(t.art)
        assertFalse(bare.lossless)
    }

    @Test
    fun `a row reads back as the sink track the provider wrote`() {
        val back = track.toEntity(pid, 1).toIndexTrack()
        assertEquals(track.copy(titleSort = "hyperballad"), back)
    }

    @Test
    fun `albums and artists map both ways`() {
        val album = IndexAlbum(itemId = "al1", title = "Post", artistItemId = "ar1", artistName = "Björk", year = 1995,
            trackCount = 11, durationMs = 2_760_000, art = "/thumb").toEntity(pid, 1).toDomain()
        assertEquals(TrackKey(pid, "al1"), album.key)
        assertEquals(TrackKey(pid, "ar1"), album.artistKey)
        assertEquals(11, album.trackCount)
        assertEquals(ArtRef(pid, "/thumb"), album.art)

        val noArtist = IndexAlbum(itemId = "al2", title = "X", artistItemId = null, artistName = "Various")
            .toEntity(pid, 1)
        assertNull(noArtist.artistPid)
        assertNull(noArtist.toDomain().artistKey)

        val artist = IndexArtist(itemId = "ar1", name = "Björk", albumCount = 9).toEntity(pid, 1).toDomain()
        assertEquals(TrackKey(pid, "ar1"), artist.key)
        assertEquals(9, artist.albumCount)
        assertNull(artist.art)
    }

    @Test
    fun `a server playlist maps with its provider key`() {
        val p = IndexPlaylist(itemId = "pl9", title = "Road", trackCount = 12, durationMs = 3_600_000,
            entries = listOf(IndexPlaylistEntry("t1"))).toEntity(pid, syncRun = 5, id = 3, sortIndex = 0).toDomain()
        assertEquals(3, p.id)
        assertEquals(PlaylistKind.SERVER, p.kind)
        assertEquals(TrackKey(pid, "pl9"), p.providerKey)
        assertEquals(12, p.trackCount)
        assertFalse(p.isLikedSongs)
    }

    @Test
    fun `a provider row maps to its instance`() {
        val row = ProviderEntity(
            id = pid, type = "plex", displayName = "Home", baseUrl = "https://x.plex.direct:32400", serverId = "aaa",
            config = "{}", sort = 0, addedAt = 1, lastSyncAt = null, syncCursor = null, enabled = true,
        )
        val i = row.toInstance()
        assertEquals(pid, i.id)
        assertEquals("plex", i.type)
        assertEquals("Home", i.displayName)
        assertEquals("https://x.plex.direct:32400", i.baseUrl)
        assertEquals("{}", i.config)
    }
}
