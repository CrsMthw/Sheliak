package com.crsmthw.sheliak.data.repository

import androidx.compose.runtime.Immutable
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.Track

/** The library's sizes (merged counts when "merge duplicates" is on). */
@Immutable
data class LibraryCounts(val tracks: Int, val albums: Int, val artists: Int, val playlists: Int) {
    companion object {
        val Empty: LibraryCounts = LibraryCounts(0, 0, 0, 0)
    }
}

/** One search's hits per page of the search screen; [query] is the text they answer. */
@Immutable
data class SearchResults(
    val query: String,
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

    companion object {
        fun empty(query: String): SearchResults = SearchResults(query, emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** One play: the track, when it started, how long it played. */
@Immutable
data class PlayRecord(val track: Track, val playedAt: Long, val playedMs: Long)
