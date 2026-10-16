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

    // ── result kinds ────────────────────────────────────────────────────────

    @Test
    fun `the result pages are Tracks, Albums, Artists, Playlists`() {
        assertEquals(
            listOf(SearchResultKind.TRACKS, SearchResultKind.ALBUMS, SearchResultKind.ARTISTS, SearchResultKind.PLAYLISTS),
            (0 until SearchResultKind.entries.size).map(::searchResultKindAt),
        )
    }

    @Test
    fun `each kind counts its own results`() {
        assertEquals(3, results.countOf(SearchResultKind.TRACKS))
        assertEquals(1, results.countOf(SearchResultKind.ALBUMS))
        assertEquals(2, results.countOf(SearchResultKind.ARTISTS))
        assertEquals(1, results.countOf(SearchResultKind.PLAYLISTS))
    }

    @Test
    fun `an empty result counts zero of every kind`() {
        SearchResultKind.entries.forEach { kind -> assertEquals(0, SearchResults.empty("zz").countOf(kind), "$kind") }
    }

    // ── searchSubtitleOf ────────────────────────────────────────────────────

    @Test
    fun `a blank field shows no subtitle on any result tab`() {
        SearchResultKind.entries.forEach { kind ->
            assertEquals(SearchSubtitle.None, searchSubtitleOf(queryBlank = true, results = null, shownKind = kind), "$kind")
        }
    }

    @Test
    fun `a blank field ignores results still held from the last query`() {
        assertEquals(
            SearchSubtitle.None,
            searchSubtitleOf(queryBlank = true, results = results, shownKind = SearchResultKind.TRACKS),
        )
    }

    @Test
    fun `a query whose first results are not in yet shows no subtitle`() {
        assertEquals(
            SearchSubtitle.None,
            searchSubtitleOf(queryBlank = false, results = null, shownKind = SearchResultKind.TRACKS),
        )
    }

    @Test
    fun `a query counts the result tab shown`() {
        assertEquals(
            SearchSubtitle.Count(SearchResultKind.TRACKS, 3),
            searchSubtitleOf(queryBlank = false, results = results, shownKind = SearchResultKind.TRACKS),
        )
        assertEquals(
            SearchSubtitle.Count(SearchResultKind.ALBUMS, 1),
            searchSubtitleOf(queryBlank = false, results = results, shownKind = SearchResultKind.ALBUMS),
        )
        assertEquals(
            SearchSubtitle.Count(SearchResultKind.ARTISTS, 2),
            searchSubtitleOf(queryBlank = false, results = results, shownKind = SearchResultKind.ARTISTS),
        )
        assertEquals(
            SearchSubtitle.Count(SearchResultKind.PLAYLISTS, 1),
            searchSubtitleOf(queryBlank = false, results = results, shownKind = SearchResultKind.PLAYLISTS),
        )
    }

    @Test
    fun `a result tab with nothing found counts zero`() {
        assertEquals(
            SearchSubtitle.Count(SearchResultKind.ALBUMS, 0),
            searchSubtitleOf(queryBlank = false, results = SearchResults.empty("zz"), shownKind = SearchResultKind.ALBUMS),
        )
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
