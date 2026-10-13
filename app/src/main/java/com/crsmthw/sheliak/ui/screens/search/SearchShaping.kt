package com.crsmthw.sheliak.ui.screens.search

import com.crsmthw.sheliak.data.repository.SearchResults

/*
 * The Search tab's pure decisions (no Compose), unit-tested in SearchShapingTest.
 */

/** What the Search tab's bar subtitle counts. */
sealed interface SearchSubtitle {

    /** The results of the query in the field: every track, album, artist and playlist found. */
    data class Results(val count: Int) : SearchSubtitle

    /** The field is blank (or the first results are not in yet): the library's track count, as on Tracks. */
    data class LibraryTracks(val count: Int) : SearchSubtitle
}

/** Every hit of [this] query, over the four kinds. */
val SearchResults.totalCount: Int
    get() = tracks.size + albums.size + artists.size + playlists.size

/**
 * The Search tab's subtitle: the [results]' total while the field holds a query and they are in, else the
 * library's [libraryTracks]. Never absent, so the large bar keeps one height on all five tabs.
 */
fun searchSubtitleOf(queryBlank: Boolean, results: SearchResults?, libraryTracks: Int): SearchSubtitle =
    if (queryBlank || results == null) {
        SearchSubtitle.LibraryTracks(libraryTracks)
    } else {
        SearchSubtitle.Results(results.totalCount)
    }

/**
 * Whether the result pages scroll back to their tops for [shownQuery]: only when it is a NEW query — not the one
 * the pages were last reset for ([lastResetQuery]), which is what a return to the tab, or a pop back onto the
 * library from a result's detail, shows again (the pages keep where the user was). Null — no results — never
 * resets.
 */
fun searchPagesNeedReset(shownQuery: String?, lastResetQuery: String?): Boolean =
    shownQuery != null && shownQuery != lastResetQuery
