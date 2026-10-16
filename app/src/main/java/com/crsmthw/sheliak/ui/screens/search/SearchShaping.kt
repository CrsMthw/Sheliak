package com.crsmthw.sheliak.ui.screens.search

import com.crsmthw.sheliak.data.repository.SearchResults

/*
 * The Search tab's pure decisions (no Compose), unit-tested in SearchShapingTest.
 */

/**
 * The kinds of result, one page each of the Search tab's pager, in the order of its result tabs (Tracks / Albums /
 * Artists / Playlists). A page index is a kind's `ordinal`.
 */
enum class SearchResultKind { TRACKS, ALBUMS, ARTISTS, PLAYLISTS }

/** The kind on pager page [page] (a pager page is always one of the kinds). */
fun searchResultKindAt(page: Int): SearchResultKind = SearchResultKind.entries[page]

/** How many results of [kind] [this] query found. */
fun SearchResults.countOf(kind: SearchResultKind): Int = when (kind) {
    SearchResultKind.TRACKS    -> tracks.size
    SearchResultKind.ALBUMS    -> albums.size
    SearchResultKind.ARTISTS   -> artists.size
    SearchResultKind.PLAYLISTS -> playlists.size
}

/** What the Search tab's bar subtitle says. */
sealed interface SearchSubtitle {

    /**
     * Nothing: the field is blank, or the first results of the query are not in yet — exactly when the page
     * itself shows nothing. (Cris, fix round 2: no library count on a blank Search page.)
     */
    data object None : SearchSubtitle

    /** The results of [kind] for the query — the result page shown — and how many there are ([count]). */
    data class Count(val kind: SearchResultKind, val count: Int) : SearchSubtitle
}

/**
 * The Search tab's subtitle: [SearchSubtitle.None] while the field is blank or no [results] are in, else the
 * count of the result page shown ([shownKind] — the pager's current page, the tab the result row highlights), so
 * it follows a swipe or a tap on the result tabs. A page with nothing found counts zero.
 */
fun searchSubtitleOf(queryBlank: Boolean, results: SearchResults?, shownKind: SearchResultKind): SearchSubtitle =
    if (queryBlank || results == null) {
        SearchSubtitle.None
    } else {
        SearchSubtitle.Count(shownKind, results.countOf(shownKind))
    }

/**
 * Whether the result pages scroll back to their tops for [shownQuery]: only when it is a NEW query — not the one
 * the pages were last reset for ([lastResetQuery]), which is what a return to the tab, or a pop back onto the
 * library from a result's detail, shows again (the pages keep where the user was). Null — no results — never
 * resets.
 */
fun searchPagesNeedReset(shownQuery: String?, lastResetQuery: String?): Boolean =
    shownQuery != null && shownQuery != lastResetQuery
