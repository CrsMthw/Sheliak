package com.crsmthw.sheliak.ui.screens.search

import com.crsmthw.sheliak.data.repository.SearchResults
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.PlaylistKind
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchShapingTest {

    private fun track(id: String) = Track(
        key          = TrackKey("plex:s", id),
        title        = "Track $id",
        titleSort    = "track $id",
        artistName   = "Artist",
        artistKey    = null,
        albumTitle   = null,
        albumKey     = null,
        discNo       = null,
        trackNo      = null,
        durationMs   = 1_000L,
        year         = null,
        format       = null,
        art          = null,
        addedAt      = 0L,
        playCount    = 0,
        lastPlayedAt = null,
    )

    private fun album(id: String) = Album(
        key        = TrackKey("plex:s", id),
        title      = "Album $id",
        artistName = "Artist",
        artistKey  = null,
        year       = null,
        trackCount = 1,
        durationMs = 1_000L,
        art        = null,
    )

    private fun artist(id: String) = Artist(key = TrackKey("plex:s", id), name = "Artist $id", albumCount = 1, art = null)

    private fun playlist(id: Long) = Playlist(
        id           = id,
        kind         = PlaylistKind.SERVER,
        providerKey  = null,
        title        = "Playlist $id",
        trackCount   = 1,
        durationMs   = 1_000L,
        art          = null,
        isLikedSongs = false,
    )

    private val results = SearchResults(
        query     = "a",
        tracks    = listOf(track("1"), track("2"), track("3")),
        albums    = listOf(album("4")),
        artists   = listOf(artist("5"), artist("6")),
        playlists = listOf(playlist(7L)),
    )

    // ── totalCount ──────────────────────────────────────────────────────────

    @Test
    fun `the total counts every kind`() {
        assertEquals(7, results.totalCount)
    }

    @Test
    fun `an empty result counts zero`() {
        assertEquals(0, SearchResults.empty("zz").totalCount)
    }

    // ── searchSubtitleOf ────────────────────────────────────────────────────

    @Test
    fun `a blank field shows the library's track count`() {
        assertEquals(SearchSubtitle.LibraryTracks(1200), searchSubtitleOf(queryBlank = true, results = null, libraryTracks = 1200))
    }

    @Test
    fun `a blank field ignores results still held from the last query`() {
        assertEquals(SearchSubtitle.LibraryTracks(1200), searchSubtitleOf(queryBlank = true, results = results, libraryTracks = 1200))
    }

    @Test
    fun `a query shows its results' total`() {
        assertEquals(SearchSubtitle.Results(7), searchSubtitleOf(queryBlank = false, results = results, libraryTracks = 1200))
    }

    @Test
    fun `a query with nothing found shows zero results`() {
        assertEquals(
            SearchSubtitle.Results(0),
            searchSubtitleOf(queryBlank = false, results = SearchResults.empty("zz"), libraryTracks = 1200),
        )
    }

    @Test
    fun `a query whose first results are not in yet keeps the track count`() {
        assertEquals(SearchSubtitle.LibraryTracks(1200), searchSubtitleOf(queryBlank = false, results = null, libraryTracks = 1200))
    }

    // ── searchPagesNeedReset ────────────────────────────────────────────────

    @Test
    fun `a new query resets the pages`() {
        assertTrue(searchPagesNeedReset(shownQuery = "ab", lastResetQuery = "a"))
        assertTrue(searchPagesNeedReset(shownQuery = "a", lastResetQuery = null))
    }

    @Test
    fun `the same query again keeps the pages where they were`() {
        assertFalse(searchPagesNeedReset(shownQuery = "a", lastResetQuery = "a"))
    }

    @Test
    fun `no results never reset`() {
        assertFalse(searchPagesNeedReset(shownQuery = null, lastResetQuery = "a"))
        assertFalse(searchPagesNeedReset(shownQuery = null, lastResetQuery = null))
    }
}
