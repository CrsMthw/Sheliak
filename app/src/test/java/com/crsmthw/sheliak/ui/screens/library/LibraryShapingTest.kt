package com.crsmthw.sheliak.ui.screens.library

import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.sync.SyncState
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryShapingTest {

    private fun track(
        id: String,
        disc: Int? = null,
        no: Int? = null,
        durationMs: Long = 180_000L,
        playCount: Int = 0,
        provider: String = "plex:a",
    ) = Track(
        key          = TrackKey(provider, id),
        title        = "Track $id",
        titleSort    = "track $id",
        artistName   = "Artist",
        artistKey    = null,
        albumTitle   = "Album",
        albumKey     = null,
        discNo       = disc,
        trackNo      = no,
        durationMs   = durationMs,
        year         = null,
        format       = null,
        art          = null,
        addedAt      = 0L,
        playCount    = playCount,
        lastPlayedAt = null,
    )

    // ── Disc grouping ───────────────────────────────────────────────────────

    @Test
    fun `a single-disc album is plain rows with their indices`() {
        val tracks = listOf(track("1", disc = 1, no = 1), track("2", disc = 1, no = 2))
        assertFalse(isMultiDisc(tracks))
        assertEquals(
            listOf(AlbumRow.TrackItem(tracks[0], 0), AlbumRow.TrackItem(tracks[1], 1)),
            albumRows(tracks),
        )
    }

    @Test
    fun `tracks with no disc numbers are one disc`() {
        val tracks = listOf(track("1"), track("2"))
        assertFalse(isMultiDisc(tracks))
        assertTrue(albumRows(tracks).all { it is AlbumRow.TrackItem })
    }

    @Test
    fun `a multi-disc album gets a header before each disc`() {
        val tracks = listOf(
            track("1", disc = 1, no = 1),
            track("2", disc = 1, no = 2),
            track("3", disc = 2, no = 1),
        )
        assertTrue(isMultiDisc(tracks))
        assertEquals(
            listOf(
                AlbumRow.DiscHeader(1, 0),
                AlbumRow.TrackItem(tracks[0], 0),
                AlbumRow.TrackItem(tracks[1], 1),
                AlbumRow.DiscHeader(2, 2),
                AlbumRow.TrackItem(tracks[2], 2),
            ),
            albumRows(tracks),
        )
    }

    @Test
    fun `a track with no disc number stays under the header before it`() {
        val tracks = listOf(track("1", disc = 1), track("2", disc = null), track("3", disc = 2))
        assertEquals(
            listOf(
                AlbumRow.DiscHeader(1, 0),
                AlbumRow.TrackItem(tracks[0], 0),
                AlbumRow.TrackItem(tracks[1], 1),
                AlbumRow.DiscHeader(2, 2),
                AlbumRow.TrackItem(tracks[2], 2),
            ),
            albumRows(tracks),
        )
    }

    @Test
    fun `album row keys are unique, a repeated track included`() {
        val repeated = track("1", disc = 1)
        val rows = albumRows(listOf(repeated, track("2", disc = 2), repeated))
        assertEquals(rows.size, rows.map(::albumRowKey).toSet().size)
    }

    // ── Running times and meta ──────────────────────────────────────────────

    @Test
    fun `the running time sums every track`() {
        assertEquals(0L, totalDurationMs(emptyList()))
        assertEquals(300_000L, totalDurationMs(listOf(track("1", durationMs = 120_000L), track("2", durationMs = 180_000L))))
    }

    @Test
    fun `an unknown negative duration counts as zero`() {
        assertEquals(60_000L, totalDurationMs(listOf(track("1", durationMs = -1L), track("2", durationMs = 60_000L))))
    }

    @Test
    fun `the meta line skips blank parts`() {
        assertEquals("2019 · 12 tracks", metaLine(listOf("2019", null, "12 tracks", " "), " · "))
        assertEquals("", metaLine(listOf(null, ""), " · "))
    }

    // ── Carousels ───────────────────────────────────────────────────────────

    @Test
    fun `no history shows no carousels`() {
        assertEquals(emptyList(), carouselSections(emptyList(), emptyList()))
    }

    @Test
    fun `recently played comes before most played`() {
        val recent = listOf(track("1"))
        val most = listOf(track("2", playCount = 5))
        assertEquals(
            listOf(CarouselKind.RECENTLY_PLAYED, CarouselKind.MOST_PLAYED),
            carouselSections(recent, most).map { it.kind },
        )
    }

    @Test
    fun `most played drops tracks that were never played`() {
        val sections = carouselSections(emptyList(), listOf(track("1", playCount = 3), track("2", playCount = 0)))
        assertEquals(listOf(CarouselKind.MOST_PLAYED), sections.map { it.kind })
        assertEquals(listOf("1"), sections.single().tracks.map { it.key.itemId })
    }

    @Test
    fun `a most played list of unplayed tracks shows no carousel`() {
        assertEquals(emptyList(), carouselSections(emptyList(), listOf(track("1"), track("2"))))
    }

    // ── List keys ───────────────────────────────────────────────────────────

    @Test
    fun `a track's list key is its media id`() {
        assertEquals("plex:a|7", trackKeyOf(track("7")))
    }

    @Test
    fun `positional keys keep repeated tracks apart`() {
        val t = track("7")
        assertEquals("0|plex:a|7", positionalTrackKey(t, 0))
        assertEquals(setOf("0|plex:a|7", "3|plex:a|7"), setOf(positionalTrackKey(t, 0), positionalTrackKey(t, 3)))
    }

    // ── Sync ────────────────────────────────────────────────────────────────

    @Test
    fun `no running sync means no progress`() {
        assertNull(syncProgressOf(emptyMap()))
        assertNull(syncProgressOf(mapOf("a" to SyncState.Idle, "b" to SyncState.Failed(ProviderError.NETWORK))))
    }

    @Test
    fun `progress sums every running source`() {
        val states = mapOf(
            "a" to SyncState.Running(done = 500, total = 1_000),
            "b" to SyncState.Running(done = 20, total = null),
            "c" to SyncState.Idle,
        )
        assertEquals(520, syncProgressOf(states))
        assertTrue(anySyncRunning(states))
    }

    @Test
    fun `a just-started sync reads zero, not null`() {
        assertEquals(0, syncProgressOf(mapOf("a" to SyncState.Running(0, null))))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a refresh round waits for the sync to start and to end`() = runTest {
        val states = MutableStateFlow<Map<String, SyncState>>(emptyMap())
        var done = false
        launch { awaitSyncRound(states); done = true }
        advanceTimeBy(1_000)
        states.value = mapOf("a" to SyncState.Running(0, null))
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(done)
        states.value = mapOf("a" to SyncState.Idle)
        runCurrent()
        assertTrue(done)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a refresh round with no sync starting gives up after the start timeout`() = runTest {
        val states = MutableStateFlow<Map<String, SyncState>>(emptyMap())
        var finishedAt = -1L
        launch { awaitSyncRound(states, startTimeoutMs = 3_000, maxMs = 10_000); finishedAt = currentTime }
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(-1L, finishedAt)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(3_000L, finishedAt)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a long sync releases the indicator at the cap`() = runTest {
        val states = MutableStateFlow<Map<String, SyncState>>(mapOf("a" to SyncState.Running(1, 10)))
        var done = false
        launch { awaitSyncRound(states, startTimeoutMs = 3_000, maxMs = 10_000); done = true }
        advanceTimeBy(9_999)
        runCurrent()
        assertFalse(done)
        advanceTimeBy(2)
        runCurrent()
        assertTrue(done)
    }

    // ── Empty states ────────────────────────────────────────────────────────

    @Test
    fun `nothing shows until the sources and the list are known`() {
        assertEquals(LibraryEmptyKind.NOTHING_YET, emptyStateFor(hasSources = null, listLoaded = true, listEmpty = true))
        assertEquals(LibraryEmptyKind.NOTHING_YET, emptyStateFor(hasSources = true, listLoaded = false, listEmpty = true))
    }

    @Test
    fun `no source offers to add one`() {
        assertEquals(LibraryEmptyKind.NO_SOURCE, emptyStateFor(hasSources = false, listLoaded = true, listEmpty = true))
    }

    @Test
    fun `sources with an empty list show the sync line`() {
        assertEquals(LibraryEmptyKind.EMPTY_LIST, emptyStateFor(hasSources = true, listLoaded = true, listEmpty = true))
    }

    @Test
    fun `a loaded list with rows shows no empty state`() {
        assertNull(emptyStateFor(hasSources = true, listLoaded = true, listEmpty = false))
    }

    @Test
    fun `rows are listed even before the sources are known`() {
        assertNull(emptyStateFor(hasSources = null, listLoaded = true, listEmpty = false))
        assertNull(emptyStateFor(hasSources = false, listLoaded = true, listEmpty = false))
    }

    // ── Shared-element keys ─────────────────────────────────────────────────

    @Test
    fun `library art keys follow the contract`() {
        assertEquals("lib-art-plex:abc|101", libraryArtKey(TrackKey("plex:abc", "101")))
        assertEquals("lib-art-playlist|42", playlistArtKey(42L))
    }

    @Test
    fun `a playlist's art key never collides with an album's`() {
        assertFalse(playlistArtKey(1L) == libraryArtKey(TrackKey("plex:abc", "1")))
    }

    // ── Shuffle ─────────────────────────────────────────────────────────────

    @Test
    fun `a shuffle starts inside the list`() {
        val random = Random(7)
        repeat(100) { assertTrue(shuffleStartIndex(5, random) in 0 until 5) }
        assertEquals(0, shuffleStartIndex(1, random))
        assertEquals(0, shuffleStartIndex(0, random))
    }
}
