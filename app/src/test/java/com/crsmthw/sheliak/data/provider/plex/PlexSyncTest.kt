package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.IndexAlbum
import com.crsmthw.sheliak.data.provider.IndexArtist
import com.crsmthw.sheliak.data.provider.IndexKind
import com.crsmthw.sheliak.data.provider.IndexPlaylist
import com.crsmthw.sheliak.data.provider.IndexSink
import com.crsmthw.sheliak.data.provider.IndexTrack
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.provider.SyncProgress
import com.crsmthw.sheliak.data.provider.plex.PlexFixtures.decode
import com.crsmthw.sheliak.domain.AudioFormatInfo
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlexSyncTest {

    // ── Cursor, filter, paging ────────────────────────────────────────────────

    @Test
    fun `the cursor round-trips and writes its version`() {
        val cursor = PlexSyncCursor(updatedSince = 1_700_000_000, lastFullAt = 1_759_000_000_000, sections = listOf("1", "5"))
        val text = cursor.encode()
        assertTrue(text.contains("\"v\":1"))
        assertEquals(cursor, PlexSyncCursor.decode(text))
    }

    @Test
    fun `an absent, unreadable or foreign cursor means a full sync`() {
        assertNull(PlexSyncCursor.decode(null))
        assertNull(PlexSyncCursor.decode(""))
        assertNull(PlexSyncCursor.decode("not json"))
        assertNull(PlexSyncCursor.decode("""{"v":99,"updatedSince":5}"""))
        assertEquals(PlexSyncCursor(updatedSince = 5), PlexSyncCursor.decode("""{"v":1,"updatedSince":5,"extra":true}"""))
    }

    @Test
    fun `the incremental filter is updatedAt greater-greater-equals epoch seconds`() {
        val filter = PlexFilters.updatedSince(1_700_000_000)
        assertEquals(mapOf("updatedAt>>" to "1700000000"), filter)
        val url = "https://h:32400/library/sections/1/all".toHttpUrl().newBuilder()
            .apply { filter.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        assertTrue(url.toString().endsWith("?updatedAt%3E%3E=1700000000"))
        assertEquals("updatedAt>>=1700000000", url.query)
    }

    private fun meta(id: Int) = PlexMetadata(ratingKey = "$id")

    @Test
    fun `paging follows the returned offset and stops at the total`() = runTest {
        val requests = mutableListOf<Pair<Int, Int>>()
        val result = PlexPaging.pageAll(
            pageSize = 2,
            fetch = { start, size ->
                requests += start to size
                // The server returns 3 items per page whatever was asked.
                PlexPage(items = (start until minOf(start + 3, 7)).map(::meta), offset = start, totalSize = 7)
            },
            onPage = { _, _ -> },
        )
        assertEquals(listOf(0 to 2, 3 to 2, 6 to 2), requests)
        assertEquals(PlexPagingResult(received = 7, total = 7, complete = true), result)
    }

    @Test
    fun `a listing that ends short of its total is incomplete`() = runTest {
        val result = PlexPaging.pageAll(
            pageSize = 2,
            fetch = { start, _ -> PlexPage(if (start == 0) listOf(meta(1), meta(2)) else emptyList(), start, 5) },
            onPage = { _, _ -> },
        )
        assertEquals(PlexPagingResult(received = 2, total = 5, complete = false), result)
    }

    @Test
    fun `without a total a short page ends the listing, and size is capped at 1000`() = runTest {
        val sizes = mutableListOf<Int>()
        val result = PlexPaging.pageAll(
            pageSize = 5000,
            fetch = { start, size ->
                sizes += size
                PlexPage(if (start == 0) (1..1000).map(::meta) else listOf(meta(1001)), offset = null, totalSize = null)
            },
            onPage = { _, _ -> },
        )
        assertEquals(listOf(1000, 1000), sizes)
        assertEquals(PlexPagingResult(received = 1001, total = null, complete = true), result)
    }

    @Test
    fun `a server that does not advance cannot loop forever`() = runTest {
        var calls = 0
        PlexPaging.pageAll(2, { _, _ -> calls++; PlexPage(listOf(meta(1), meta(2)), offset = -2, totalSize = null) }) { _, _ -> }
        assertEquals(1, calls)
    }

    // ── Runs ──────────────────────────────────────────────────────────────────

    private val listedTracks = decode(PlexResponse.serializer(), PlexFixtures.TRACKS_PAGE_1).mediaContainer!!.metadata!!
    private val detailedTracks = decode(PlexResponse.serializer(), PlexFixtures.METADATA_BATCH).mediaContainer!!.metadata!!

    private val albums = listOf(
        PlexMetadata(ratingKey = "901", title = "Ágætis byrjun", parentRatingKey = "801", parentTitle = "Sigur Rós", leafCount = 1, updatedAt = 1_700_000_050),
        PlexMetadata(ratingKey = "902", title = "Compilation", parentRatingKey = "802", parentTitle = "Various Artists", leafCount = 1, updatedAt = 1_600_000_000),
    )
    private val artists = listOf(
        PlexMetadata(ratingKey = "801", title = "Sigur Rós", updatedAt = 1_650_000_000),
        PlexMetadata(ratingKey = "802", title = "Various Artists", updatedAt = 1_650_000_000),
    )
    private val playlists = decode(PlexResponse.serializer(), PlexFixtures.PLAYLISTS).mediaContainer!!.metadata!!
    private val playlistItems = decode(PlexResponse.serializer(), PlexFixtures.PLAYLIST_ITEMS).mediaContainer!!.metadata!!

    /** A one-section server serving the fixtures; filters are applied like PMS would. */
    private inner class FakeSource : PlexLibrarySource {
        var tracks: List<PlexMetadata> = listedTracks
        var albumList: List<PlexMetadata> = albums
        var reportedTotal: Int? = null
        val metadataCalls = mutableListOf<List<String>>()
        val filtersSeen = mutableListOf<Map<String, String>>()
        var metadataFailure: Exception? = null

        override suspend fun sectionPage(sectionKey: String, type: Int, start: Int, size: Int, filters: Map<String, String>): PlexPage {
            filtersSeen += filters
            val since = filters["updatedAt>>"]?.toLong()
            val all = when (type) {
                PlexTypes.TRACK  -> tracks
                PlexTypes.ALBUM  -> albumList
                PlexTypes.ARTIST -> artists
                else             -> emptyList()
            }.filter { since == null || (it.updatedAt ?: 0) >= since }
            val page = all.drop(start).take(size)
            return PlexPage(page, start, if (type == PlexTypes.TRACK) reportedTotal ?: all.size else all.size)
        }

        override suspend fun metadata(ratingKeys: List<String>): List<PlexMetadata> {
            metadataCalls += ratingKeys
            metadataFailure?.let { throw it }
            return detailedTracks.filter { it.ratingKey in ratingKeys }
        }

        override suspend fun playlists(): List<PlexMetadata> = playlists

        override suspend fun playlistItems(playlistKey: String, start: Int, size: Int): PlexPage =
            if (playlistKey == "5001") PlexPage(playlistItems.drop(start).take(size), start, playlistItems.size)
            else PlexPage(emptyList(), start, 0)
    }

    private class RecordingSink : IndexSink {
        val tracks = LinkedHashMap<String, IndexTrack>()
        val albums = LinkedHashMap<String, IndexAlbum>()
        val artists = LinkedHashMap<String, IndexArtist>()
        val playlists = LinkedHashMap<String, IndexPlaylist>()
        val complete = mutableSetOf<IndexKind>()
        override suspend fun putArtists(artists: List<IndexArtist>) { artists.forEach { this.artists[it.itemId] = it } }
        override suspend fun putAlbums(albums: List<IndexAlbum>) { albums.forEach { this.albums[it.itemId] = it } }
        override suspend fun putTracks(tracks: List<IndexTrack>) { tracks.forEach { this.tracks[it.itemId] = it } }
        override suspend fun putPlaylist(playlist: IndexPlaylist) { playlists[playlist.itemId] = playlist }
        override suspend fun remove(kind: IndexKind, itemIds: List<String>) = Unit
        override fun markComplete(kind: IndexKind) { complete += kind }
    }

    private val now = 1_759_000_000_000L
    private val sections = listOf("1")

    private fun sync(source: PlexLibrarySource, stored: Map<String, IndexTrack> = emptyMap()) =
        PlexSync(source, stored = { stored[it] }, config = PlexSyncConfig(pageSize = 1), clock = { now })

    @Test
    fun `a first sync is full, enriched from metadata batches, and marks every kind complete`() = runTest {
        val source = FakeSource()
        val sink = RecordingSink()
        val progress = mutableListOf<SyncProgress>()
        val cursor = sync(source).run(null, sections, sink) { progress += it }

        assertEquals(setOf("1001", "1002"), sink.tracks.keys)
        assertEquals(96_000, sink.tracks.getValue("1001").format?.sampleRateHz)
        assertEquals(24, sink.tracks.getValue("1001").format?.bitDepth)
        assertEquals(44_100, sink.tracks.getValue("1002").format?.sampleRateHz)
        assertEquals(listOf(listOf("1001"), listOf("1002")), source.metadataCalls)   // page size 1 → one batch per page
        assertEquals(475_000L, sink.albums.getValue("901").durationMs)
        assertEquals(1, sink.artists.getValue("801").albumCount)
        assertEquals(setOf(IndexKind.TRACKS, IndexKind.ALBUMS, IndexKind.ARTISTS, IndexKind.PLAYLISTS), sink.complete)
        assertEquals(listOf(SyncProgress(1, 2), SyncProgress(2, 2)), progress)
        assertTrue(source.filtersSeen.all { it.isEmpty() })

        // Newest updatedAt seen is the second track's 1_700_000_200, minus the hour of overlap.
        assertEquals(PlexSyncCursor(updatedSince = 1_700_000_200 - 3600, lastFullAt = now, sections = sections), cursor)
    }

    @Test
    fun `playlists are written with all their entries in order`() = runTest {
        val sink = RecordingSink()
        sync(FakeSource()).run(null, sections, sink) { }
        val road = sink.playlists.getValue("5001")
        assertEquals(listOf("1002", "1001"), road.entries.map { it.trackItemId })
        assertEquals(listOf("7002", "7001"), road.entries.map { it.entryId })
        assertEquals(emptyList(), sink.playlists.getValue("5002").entries)
    }

    @Test
    fun `an unchanged track re-uses the stored details instead of a metadata call`() = runTest {
        val source = FakeSource()
        val listed1001 = PlexMapping.track(listedTracks[0])!!
        val stored = mapOf(
            "1001" to listed1001.copy(
                format = AudioFormatInfo("flac", "flac", 2900, 96_000, 24, 2, lossless = true),
                replayGainTrackDb = -8.5f,
            ),
        )
        val sink = RecordingSink()
        sync(source, stored).run(null, sections, sink) { }
        assertEquals(listOf(listOf("1002")), source.metadataCalls)
        assertEquals(96_000, sink.tracks.getValue("1001").format?.sampleRateHz)
        assertEquals(-8.5f, sink.tracks.getValue("1001").replayGainTrackDb)
    }

    @Test
    fun `a changed part key is fetched again`() = runTest {
        val source = FakeSource()
        val listed1001 = PlexMapping.track(listedTracks[0])!!
        val stored = mapOf(
            "1001" to listed1001.copy(
                streamRef = "/library/parts/3001/1500000000/file.flac",
                format = AudioFormatInfo("flac", "flac", 900, 44_100, 16, 2, lossless = true),
            ),
        )
        sync(source, stored).run(null, sections, RecordingSink()) { }
        assertEquals(listOf(listOf("1001"), listOf("1002")), source.metadataCalls)
    }

    @Test
    fun `a metadata batch the server fails keeps the stored details`() = runTest {
        val source = FakeSource().apply { metadataFailure = ProviderException(ProviderError.SERVER, "HTTP 500") }
        val listed1001 = PlexMapping.track(listedTracks[0])!!
        val stored = mapOf(
            "1001" to listed1001.copy(updatedAt = 1, format = AudioFormatInfo("flac", "flac", 2900, 96_000, 24, 2, true)),
        )
        val sink = RecordingSink()
        sync(source, stored).run(null, sections, sink) { }
        assertEquals(96_000, sink.tracks.getValue("1001").format?.sampleRateHz)
        assertNull(sink.tracks.getValue("1002").format?.sampleRateHz)   // nothing stored: listing details only
        assertEquals("mp3", sink.tracks.getValue("1002").format?.codec)
    }

    @Test
    fun `a network failure in a metadata batch fails the run`() = runTest {
        val source = FakeSource().apply { metadataFailure = IOException("reset") }
        assertFailsWith<IOException> { sync(source).run(null, sections, RecordingSink()) { } }
    }

    @Test
    fun `a listing short of its total marks nothing complete and keeps the next run full`() = runTest {
        val source = FakeSource().apply { reportedTotal = 3 }
        val sink = RecordingSink()
        val previous = PlexSyncCursor(updatedSince = 0, lastFullAt = 42, sections = sections)
        val cursor = sync(source).run(previous, sections, sink) { }
        assertEquals(setOf(IndexKind.PLAYLISTS), sink.complete)
        assertEquals(0L, cursor.updatedSince)
        assertEquals(42L, cursor.lastFullAt)
    }

    @Test
    fun `an incremental run filters on updatedAt and marks no library kind complete`() = runTest {
        val source = FakeSource()
        val since = 1_700_000_150L
        val sink = RecordingSink()
        val previous = PlexSyncCursor(updatedSince = since, lastFullAt = now - 1000, sections = sections)
        val cursor = sync(source).run(previous, sections, sink) { }

        assertEquals(setOf("1002"), sink.tracks.keys)                       // only the track updated since
        assertTrue(source.filtersSeen.any { it == mapOf("updatedAt>>" to "$since") })
        assertEquals(emptySet(), sink.albums.keys)                          // no album changed since
        assertEquals(setOf(IndexKind.PLAYLISTS), sink.complete)
        assertEquals(now - 1000, cursor.lastFullAt)
        assertEquals(maxOf(since, 1_700_000_200 - 3600), cursor.updatedSince)
    }

    @Test
    fun `a changed album whose tracks all changed is written with their duration`() = runTest {
        val source = FakeSource().apply {
            albumList = listOf(albums[0], albums[1].copy(updatedAt = 1_700_000_190))
        }
        val sink = RecordingSink()
        val previous = PlexSyncCursor(updatedSince = 1_700_000_150, lastFullAt = now - 1000, sections = sections)
        sync(source).run(previous, sections, sink) { }
        assertEquals(setOf("902"), sink.albums.keys)
        assertEquals(215_000L, sink.albums.getValue("902").durationMs)
        assertTrue(IndexKind.ALBUMS !in sink.complete)
    }

    @Test
    fun `a changed album the run cannot account for turns the run into a full one`() = runTest {
        val source = FakeSource().apply {
            albumList = listOf(albums[0].copy(updatedAt = 1_700_000_190, leafCount = 1), albums[1])
        }
        val sink = RecordingSink()
        val previous = PlexSyncCursor(updatedSince = 1_700_000_150, lastFullAt = now - 1000, sections = sections)
        val cursor = sync(source).run(previous, sections, sink) { }
        assertEquals(setOf("1001", "1002"), sink.tracks.keys)
        assertTrue(IndexKind.TRACKS in sink.complete)
        assertEquals(now, cursor.lastFullAt)
    }

    @Test
    fun `the periodic full run and a new library selection both list everything`() = runTest {
        val stale = PlexSyncCursor(updatedSince = 1_700_000_150, lastFullAt = now - PlexSyncConfig().fullSyncEveryMs, sections = sections)
        val sink = RecordingSink()
        sync(FakeSource()).run(stale, sections, sink) { }
        assertTrue(IndexKind.TRACKS in sink.complete)

        val otherSections = PlexSyncCursor(updatedSince = 1_700_000_150, lastFullAt = now, sections = listOf("5"))
        val sink2 = RecordingSink()
        sync(FakeSource()).run(otherSections, sections, sink2) { }
        assertTrue(IndexKind.TRACKS in sink2.complete)
    }
}
