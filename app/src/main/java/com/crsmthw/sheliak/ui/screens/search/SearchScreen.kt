package com.crsmthw.sheliak.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.repository.SearchResults
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.TrackRowWithMenu
import com.crsmthw.sheliak.ui.screens.library.AlbumListCard
import com.crsmthw.sheliak.ui.screens.library.ArtistListCard
import com.crsmthw.sheliak.ui.screens.library.PlaylistListCard
import com.crsmthw.sheliak.ui.screens.library.libraryArtKey
import com.crsmthw.sheliak.ui.screens.library.libraryArtSharedBounds
import com.crsmthw.sheliak.ui.screens.library.playFrom
import com.crsmthw.sheliak.ui.screens.library.playlistArtKey
import com.crsmthw.sheliak.ui.screens.library.rememberCurrentTrackKey
import com.crsmthw.sheliak.ui.screens.library.rememberTrackMenuActions
import com.crsmthw.sheliak.ui.screens.library.trackKeyOf
import com.crsmthw.sheliak.util.pagerTrackingIndicator
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec
import kotlinx.coroutines.launch

/*
 * Search — the library's centre tab (`LibraryTab.SEARCH`), not a destination of its own. `LibraryShell` composes
 * its three parts:
 *
 *   SearchField           in the ONE RootTopBar's `belowBar` slot, shown on the Search tab only
 *   SearchPage            the tab's content in the library's fade-through: the result-kind tabs and the pages
 *   rememberSearchPageState  everything that must outlive a tab change, hoisted above the fade
 *
 * The bar's title is "Search" and its subtitle a count (`searchSubtitleOf`). Selecting the tab shows the page and
 * leaves the field alone; re-selecting it focuses the field and raises the keyboard ([SearchPageState.focusField]).
 */

/** The kinds of result, one page each, in tab order. */
private enum class ResultKind(@param:StringRes val labelRes: Int) {
    TRACKS(R.string.nav_tracks),
    ALBUMS(R.string.nav_albums),
    ARTISTS(R.string.nav_artists),
    PLAYLISTS(R.string.nav_playlists),
}

/** The field's side margins — the bar's own title inset, so the field lines up with "Search" above it. */
private val SearchFieldSideMargin = 16.dp

/** The gap above and below the field inside the bar's `belowBar` slot. */
private val SearchFieldVerticalMargin = 8.dp

/**
 * The Search tab's state, hoisted above the library's tab fade (`AnimatedContent` drops the saveable state of the
 * content it removes): the field's text, its focus, the result pager and one list position per page, and the
 * query the pages were last reset for. So a round trip through another tab, or through a pushed detail, comes
 * back to the same query, page and scroll position.
 */
@Stable
class SearchPageState internal constructor(
    /** The field's text — the one source of the query (the ViewModel follows it). */
    val query: TextFieldState,
    internal val pager: PagerState,
    internal val lists: List<LazyListState>,
    lastReset: MutableState<String?>,
) {
    internal val focusRequester = FocusRequester()

    /** Whether the field holds the focus (written by the field). */
    internal var fieldFocused by mutableStateOf(false)

    /** The query the pages last scrolled back to their tops for (saveable: see [searchPagesNeedReset]). */
    internal var lastResetQuery: String? by lastReset

    /**
     * The selected Search tab tapped again: the field takes the focus and the keyboard rises — also when the
     * field already had the focus with the keyboard swiped away. A field not composed yet ignores the request.
     */
    fun focusField(keyboard: SoftwareKeyboardController?) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    /** Leaving the Search tab: the field lets go of the focus and the keyboard goes down. */
    fun releaseField(focusManager: FocusManager, keyboard: SoftwareKeyboardController?) {
        if (fieldFocused) focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
}

/** The Search tab's hoisted state — call it once, above the library's tab fade. */
@Composable
fun rememberSearchPageState(): SearchPageState {
    val query = rememberTextFieldState()
    val pager = rememberPagerState { ResultKind.entries.size }
    val lists = ResultKind.entries.map { rememberLazyListState() }
    val lastReset = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(query, pager, lastReset) { SearchPageState(query, pager, lists, lastReset) }
}

/**
 * Feeds the field's text to [viewModel] as it changes (and once on composition, for a restored query). Composed
 * by the shell for the Library entry's life, so the query is the ViewModel's whether or not the tab shows.
 */
@Composable
fun SearchQuerySync(viewModel: SearchViewModel, state: SearchPageState) {
    LaunchedEffect(state, viewModel) {
        snapshotFlow { state.query.text.toString() }.collect { viewModel.setQuery(it) }
    }
}

/**
 * The Search tab's bar subtitle ([searchSubtitleOf]): "N results" for the query in the field, or the library's
 * "N tracks" while it is blank. Collects the results only while it is composed — the shell asks for it on the
 * Search tab only.
 */
@Composable
fun searchSubtitle(viewModel: SearchViewModel, state: SearchPageState, libraryTracks: Int): String {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val queryBlank by remember(state) { derivedStateOf { state.query.text.isBlank() } }
    return when (val subtitle = searchSubtitleOf(queryBlank, results, libraryTracks)) {
        is SearchSubtitle.Results       ->
            pluralStringResource(R.plurals.search_result_count, subtitle.count, subtitle.count)
        is SearchSubtitle.LibraryTracks ->
            pluralStringResource(R.plurals.library_track_count, subtitle.count, subtitle.count)
    }
}

/**
 * The search field, for the library bar's `belowBar` slot: a `SearchBarDefaults.InputField` in a stadium
 * `surfaceContainerHigh` [Surface] — a search glyph leading, a clear ✕ trailing once something is typed, the
 * placeholder "Search your library" (no other hint anywhere). It paints [paneColor] around itself, like the bar
 * it rides under. [onSearch] is the keyboard's search action.
 *
 * The non-deprecated `InputField` needs a `SearchBarState`; it is created already EXPANDED and left alone,
 * because nothing here ever expands into a full-screen search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    state    : SearchPageState,
    paneColor: Color,
    onSearch : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    // Derived, so only a blank ↔ non-blank flip recomposes the field's trailing slot — not every keystroke.
    val queryBlank by remember(state) { derivedStateOf { state.query.text.isBlank() } }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(paneColor)
            .padding(horizontal = SearchFieldSideMargin, vertical = SearchFieldVerticalMargin),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape    = CircleShape,
            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SearchBarDefaults.InputField(
                textFieldState = state.query,
                searchBarState = searchBarState,
                onSearch       = { onSearch() },
                modifier       = Modifier
                    .fillMaxWidth()
                    .focusRequester(state.focusRequester)
                    .onFocusChanged { state.fieldFocused = it.isFocused },
                placeholder    = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon    = { Icon(Icons.Filled.Search, contentDescription = null) },
                // Clearing starts the next query, so the field is left focused with the keyboard up.
                trailingIcon   = if (queryBlank) {
                    null
                } else {
                    {
                        IconButton(
                            onClick = {
                                haptics.press()
                                state.query.clearText()
                                state.focusField(keyboard)
                            },
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
            )
        }
    }
}

/**
 * The Search tab's content, under the library bar and its field: nothing while the field is blank (the
 * placeholder says what to do); once something is typed, Lyra's result pages — a [PrimaryTabRow] (Tracks / Albums
 * / Artists / Playlists) pinned at the top, its indicator following a [HorizontalPager] of one list per kind. A
 * track plays the results from there; an album, artist or playlist opens its detail entry (its art flying into
 * the hero); a long-press on a track opens its menu. Dragging a list drops the keyboard.
 *
 * Every list hangs the library bar's [nestedScrollConnection] (so the large bar collapses over results as over
 * any tab), starts [BarContentGap] under a [TopBarFade] seam, and ends with the entry's bottom room
 * ([BottomFadeSpacer]) under the [BottomFadeScrim]. The whole page sits above the keyboard (`imePadding()`; the
 * navigation suite consumed its bar's height, so only the keyboard's part above the bar is owed).
 */
@Composable
fun SearchPage(
    viewModel             : SearchViewModel,
    state                 : SearchPageState,
    nestedScrollConnection: NestedScrollConnection,
    paneColor             : Color,
    onOpenAlbum           : (TrackKey) -> Unit,
    onOpenArtist          : (TrackKey) -> Unit,
    onOpenPlaylist        : (Long) -> Unit,
    modifier              : Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val results by viewModel.results.collectAsStateWithLifecycle()
    val queryBlank by remember(state) { derivedStateOf { state.query.text.isBlank() } }

    // A new query starts every page at its top; coming back to the same query keeps where the user was.
    LaunchedEffect(results?.query) {
        val shown = results?.query
        if (!searchPagesNeedReset(shown, state.lastResetQuery)) return@LaunchedEffect
        state.lastResetQuery = shown
        state.lists.forEach { it.requestScrollToItem(0) }
    }

    // Dragging the results drops the keyboard: a real vertical drag (not a fling, not the IME's own relocation),
    // once per gesture while the field holds focus. Observes, never consumes.
    val dismissImeOnScroll = remember(state, focusManager, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (state.fieldFocused && source == NestedScrollSource.UserInput && available.y != 0f) {
                    state.releaseField(focusManager, keyboard)
                }
                return Offset.Zero
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        val shown = results
        if (!queryBlank && shown != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchTabRow(pagerState = state.pager, paneColor = paneColor)
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    SearchResultPages(
                        results                = shown,
                        state                  = state,
                        viewModel              = viewModel,
                        nestedScrollConnection = nestedScrollConnection,
                        imeDismissal           = dismissImeOnScroll,
                        onLeave                = { state.releaseField(focusManager, keyboard) },
                        onOpenAlbum            = onOpenAlbum,
                        onOpenArtist           = onOpenArtist,
                        onOpenPlaylist         = onOpenPlaylist,
                    )
                    TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                    BottomFadeScrim(color = paneColor)
                }
            }
        }
    }
}

/**
 * The results: one [LazyColumn] per [ResultKind] in a [HorizontalPager], so the kinds can be swiped as well as
 * tapped. The pager's own snap IS the swap (gesture-driven, not a content transition). [onLeave] drops the
 * keyboard before a result opens or plays something.
 *
 * The bar's connection hangs on each LIST, inside the pager: a list's vertical scroll collapses the bar, while the
 * pager's own horizontal flings never reach it.
 */
@Composable
private fun SearchResultPages(
    results               : SearchResults,
    state                 : SearchPageState,
    viewModel             : SearchViewModel,
    nestedScrollConnection: NestedScrollConnection,
    imeDismissal          : NestedScrollConnection,
    onLeave               : () -> Unit,
    onOpenAlbum           : (TrackKey) -> Unit,
    onOpenArtist          : (TrackKey) -> Unit,
    onOpenPlaylist        : (Long) -> Unit,
) {
    val currentTrack by rememberCurrentTrackKey(viewModel.playerState)
    val menu = rememberTrackMenuActions(viewModel.player, onGoToAlbum = onOpenAlbum, onGoToArtist = onOpenArtist)
    val artistFallback = stringResource(R.string.library_unknown_artist)
    val noResults = stringResource(R.string.search_no_results, results.query)

    HorizontalPager(
        state                   = state.pager,
        modifier                = Modifier.fillMaxSize().nestedScroll(imeDismissal),
        beyondViewportPageCount = 0,
    ) { page ->
        LazyColumn(
            state          = state.lists[page],
            modifier       = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(top = BarContentGap),
        ) {
            when (ResultKind.entries[page]) {
                ResultKind.TRACKS    -> if (results.tracks.isEmpty()) noResultsItem(noResults) else {
                    itemsIndexed(
                        items       = results.tracks,
                        key         = { _, track -> trackKeyOf(track) },
                        contentType = { _, _ -> "track" },
                    ) { index, track ->
                        TrackRowWithMenu(
                            track          = track,
                            artistFallback = artistFallback,
                            isCurrent      = track.key == currentTrack,
                            onPlay         = { onLeave(); viewModel.player.playFrom(results.tracks, index) },
                            actions        = menu,
                        )
                    }
                }
                ResultKind.ALBUMS    -> if (results.albums.isEmpty()) noResultsItem(noResults) else {
                    items(results.albums, key = { it.key.mediaId }, contentType = { "album" }) { album ->
                        AlbumListCard(
                            album             = album,
                            onClick           = { onLeave(); onOpenAlbum(album.key) },
                            artSharedModifier = Modifier.libraryArtSharedBounds(libraryArtKey(album.key)),
                        )
                    }
                }
                ResultKind.ARTISTS   -> if (results.artists.isEmpty()) noResultsItem(noResults) else {
                    items(results.artists, key = { it.key.mediaId }, contentType = { "artist" }) { artist ->
                        ArtistListCard(
                            artist            = artist,
                            onClick           = { onLeave(); onOpenArtist(artist.key) },
                            artSharedModifier = Modifier.libraryArtSharedBounds(libraryArtKey(artist.key)),
                        )
                    }
                }
                ResultKind.PLAYLISTS -> if (results.playlists.isEmpty()) noResultsItem(noResults) else {
                    items(results.playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                        PlaylistListCard(
                            playlist          = playlist,
                            onClick           = { onLeave(); onOpenPlaylist(playlist.id) },
                            onPlay            = { onLeave(); viewModel.playPlaylist(playlist.id) },
                            artSharedModifier = Modifier.libraryArtSharedBounds(playlistArtKey(playlist.id)),
                        )
                    }
                }
            }
            // The entry's bottom room (the fade, plus the mini bar's room while it shows), so the last row can
            // scroll clear of both — the same insets the scrim spans.
            item(key = RESULTS_BOTTOM_KEY, contentType = RESULTS_BOTTOM_KEY) { BottomFadeSpacer() }
        }
    }
}

private const val RESULTS_BOTTOM_KEY = "search-bottom-room"

/** A page with nothing for this query. */
private fun LazyListScope.noResultsItem(text: String) {
    item(key = "no-results", contentType = "no-results") {
        Text(
            text      = text,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        )
    }
}

/**
 * The result-kind tabs — Lyra's: a Material 3 [PrimaryTabRow] pinned above the pages in the pane colour, without
 * a divider (the [TopBarFade] below it is the seam). The selected tab is the PAGER's current page, so it flips
 * mid-swipe, and the indicator follows the pager per frame ([pagerTrackingIndicator]). A tap pages there on the
 * finite spec.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTabRow(pagerState: PagerState, paneColor: Color, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val selectedIndex = pagerState.currentPage
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier         = modifier,
        containerColor   = paneColor,
        // `width = Dp.Unspecified` is mandatory — `PrimaryIndicator`'s default is a 24dp stub.
        indicator        = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.pagerTrackingIndicator(this, pagerState),
                width    = Dp.Unspecified,
            )
        },
        divider          = {},
    ) {
        ResultKind.entries.forEachIndexed { index, page ->
            Tab(
                selected               = index == selectedIndex,
                onClick                = {
                    if (index != selectedIndex) {
                        haptics.press()
                        scope.launch { pagerState.animateScrollToPage(index, animationSpec = screenTransitionSpec()) }
                    }
                },
                text                   = {
                    Text(stringResource(page.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                selectedContentColor   = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
