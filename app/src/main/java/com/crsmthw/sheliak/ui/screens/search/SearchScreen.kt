package com.crsmthw.sheliak.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.repository.SearchResults
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.TrackRowWithMenu
import com.crsmthw.sheliak.ui.navigation.LocalPlayerSurfaceInset
import com.crsmthw.sheliak.ui.navigation.searchBarSharedBounds
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
import com.crsmthw.sheliak.util.SearchBarHeight
import com.crsmthw.sheliak.util.SearchBarSideMargin
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.pagerTrackingIndicator
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The floating bar's gap below the status bar. */
private val SearchBarTopMargin = 8.dp

/** Bar top margin + bar + the gap under it: where content below the floating bar starts. */
private val SearchBarBlockHeight = SearchBarTopMargin + SearchBarHeight + 12.dp

/**
 * What a `PrimaryTabRow` of text-only tabs measures (`PrimaryNavigationTabTokens.ContainerHeight`, internal in
 * material3; re-check on a Material3 bump).
 */
private val SearchTabRowHeight = 48.dp

/** Breathing room between the tab row and the first result. */
private val SearchTabRowGap = 12.dp

/** How far past its opaque end the top scrim fades out. */
private val TopScrimTail = 24.dp

/** The result pages, in tab order. */
private enum class SearchPage(@param:StringRes val labelRes: Int) {
    TRACKS(R.string.nav_tracks),
    ALBUMS(R.string.nav_albums),
    ARTISTS(R.string.nav_artists),
    PLAYLISTS(R.string.nav_playlists),
}

/**
 * Search: a floating Material 3 search field over the page — no app bar — whose back arrow is its own leading
 * icon, so the whole control is one box. On compact widths that box is the far end of the search FAB's container
 * transform ([searchBarSharedBounds]); on rail widths Search arrives with the ordinary push. The screen itself is
 * identical at every width.
 *
 * Below the field: a hint while it is blank; once something is typed, Lyra's result pages — a [PrimaryTabRow]
 * (Tracks / Albums / Artists / Playlists) floating under the bar, its indicator following a [HorizontalPager] of
 * one list per kind. A track plays the results from there; an album, artist or playlist opens its detail entry
 * (its art flying into the hero); a long-press on a track opens its menu. Dragging a list drops the keyboard.
 *
 * The field is focused once per visit, after this destination is RESUMED (Navigation 3 resumes an entry only on
 * top and settled), so the keyboard rises after the FAB → bar morph has landed, never during it; a re-entry over
 * a typed query does not re-focus.
 */
@Composable
fun SearchScreen(
    viewModel     : SearchViewModel,
    onBack        : () -> Unit,
    onOpenAlbum   : (TrackKey) -> Unit,
    onOpenArtist  : (TrackKey) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    modifier      : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val queryState = rememberTextFieldState()
    // Derived, so only a blank ↔ non-blank flip recomposes the screen — not every keystroke.
    val queryBlank by remember(queryState) { derivedStateOf { queryState.text.isBlank() } }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val background = MaterialTheme.colorScheme.background

    // The field's text → the ViewModel, as it changes (and once on entry, for a restored query).
    LaunchedEffect(queryState, viewModel) {
        snapshotFlow { queryState.text.toString() }.collect { viewModel.setQuery(it) }
    }

    val pagerState = rememberPagerState { SearchPage.entries.size }
    val listStates = SearchPage.entries.map { rememberLazyListState() }
    // A new query starts every page at its top — but a re-entry (popping back from an album) keeps where the user
    // was: the guard is saveable, so it survives the save / restore that brings the scroll offsets back.
    var lastResetQuery by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(results?.query) {
        val shown = results?.query ?: return@LaunchedEffect
        if (shown == lastResetQuery) return@LaunchedEffect
        lastResetQuery = shown
        listStates.forEach { it.requestScrollToItem(0) }
    }

    // Dragging the results drops the keyboard: a real vertical drag (not a fling, not the IME's own relocation),
    // once per gesture while the field holds focus. Observes, never consumes.
    val fieldFocused = remember { mutableStateOf(false) }
    val dismissImeOnScroll = remember(focusManager, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (fieldFocused.value && source == NestedScrollSource.UserInput && available.y != 0f) {
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .horizontalSystemBarsPadding(),
    ) {
        val shown = results
        if (queryBlank) {
            Text(
                text      = stringResource(R.string.search_hint),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(top = SearchBarBlockHeight + 16.dp, start = 24.dp, end = 24.dp),
            )
        } else if (shown != null) {
            SearchResultPages(
                results            = shown,
                pagerState         = pagerState,
                listStates         = listStates,
                viewModel          = viewModel,
                imeDismissal       = dismissImeOnScroll,
                onLeave            = { keyboard?.hide() },
                onOpenAlbum        = onOpenAlbum,
                onOpenArtist       = onOpenArtist,
                onOpenPlaylist     = onOpenPlaylist,
                topPadding         = SearchBarBlockHeight + SearchTabRowHeight + SearchTabRowGap,
            )
        }

        BottomFadeScrim(color = background)

        // The top scrim: opaque down to the bottom of whatever floats there (the bar, plus the tab row while there
        // are results), then fading over a short tail, so rows dissolve into the floating controls.
        val opaqueHeight = SearchBarBlockHeight + if (queryBlank) 0.dp else SearchTabRowHeight
        TopScrim(opaqueHeight = opaqueHeight, color = background)

        SearchInputBar(
            queryState     = queryState,
            focusRequester = focusRequester,
            onBack         = { keyboard?.hide(); haptics.confirm(); onBack() },
            // Clearing starts the next query, so the field is left focused with the keyboard up from either
            // state the ✕ can be tapped in (unfocused, or focused after the keyboard was swiped away).
            onClear        = {
                haptics.press()
                queryState.clearText()
                focusRequester.requestFocus()
                keyboard?.show()
            },
            onSearch       = { keyboard?.hide() },
            onFocusChanged = { fieldFocused.value = it },
            modifier       = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = SearchBarSideMargin, end = SearchBarSideMargin, top = SearchBarTopMargin)
                .searchBarSharedBounds(),
        )

        if (!queryBlank) {
            SearchTabRow(
                pagerState = pagerState,
                modifier   = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = SearchBarBlockHeight),
            )
        }
    }

    // `rememberSaveable` so the flag survives the save / restore a pop performs: popping back here from a
    // later destination composes Search afresh, and must not throw the keyboard up over a typed query.
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        val reEntry = autoFocused
        if (reEntry && queryState.text.isNotBlank()) return@LaunchedEffect
        autoFocused = true
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        focusRequester.requestFocus()
    }
}

/**
 * The results: one [LazyColumn] per [SearchPage] in a [HorizontalPager], so the kinds can be swiped as well as
 * tapped. The pager's own snap IS the swap (gesture-driven, not a content transition). Each list starts under the
 * floating bar and tab row ([topPadding], under the status bar) and ends with the bottom room for the fade and
 * the player surface. [onLeave] drops the keyboard before a result opens or plays something.
 */
@Composable
private fun SearchResultPages(
    results       : SearchResults,
    pagerState    : PagerState,
    listStates    : List<LazyListState>,
    viewModel     : SearchViewModel,
    imeDismissal  : NestedScrollConnection,
    onLeave       : () -> Unit,
    onOpenAlbum   : (TrackKey) -> Unit,
    onOpenArtist  : (TrackKey) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    topPadding    : Dp,
) {
    val currentTrack by rememberCurrentTrackKey(viewModel.playerState)
    val menu = rememberTrackMenuActions(viewModel.player, onGoToAlbum = onOpenAlbum, onGoToArtist = onOpenArtist)
    val artistFallback = stringResource(R.string.library_unknown_artist)
    val noResults = stringResource(R.string.search_no_results, results.query)
    val bottomRoom = LocalPlayerSurfaceInset.current

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        HorizontalPager(
            state                   = pagerState,
            modifier                = Modifier.fillMaxSize().nestedScroll(imeDismissal),
            beyondViewportPageCount = 0,
        ) { page ->
            LazyColumn(
                state          = listStates[page],
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topPadding),
            ) {
                when (SearchPage.entries[page]) {
                    SearchPage.TRACKS    -> if (results.tracks.isEmpty()) noResultsItem(noResults) else {
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
                    SearchPage.ALBUMS    -> if (results.albums.isEmpty()) noResultsItem(noResults) else {
                        items(results.albums, key = { it.key.mediaId }, contentType = { "album" }) { album ->
                            AlbumListCard(
                                album             = album,
                                onClick           = { onLeave(); onOpenAlbum(album.key) },
                                artSharedModifier = Modifier.libraryArtSharedBounds(libraryArtKey(album.key)),
                            )
                        }
                    }
                    SearchPage.ARTISTS   -> if (results.artists.isEmpty()) noResultsItem(noResults) else {
                        items(results.artists, key = { it.key.mediaId }, contentType = { "artist" }) { artist ->
                            ArtistListCard(
                                artist            = artist,
                                onClick           = { onLeave(); onOpenArtist(artist.key) },
                                artSharedModifier = Modifier.libraryArtSharedBounds(libraryArtKey(artist.key)),
                            )
                        }
                    }
                    SearchPage.PLAYLISTS -> if (results.playlists.isEmpty()) noResultsItem(noResults) else {
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
                item(key = RESULTS_BOTTOM_KEY, contentType = RESULTS_BOTTOM_KEY) { BottomFadeSpacer(extra = bottomRoom) }
            }
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
 * The top scrim: [color] held opaque over the status bar and [opaqueHeight] below it, fading out over
 * [TopScrimTail]. No pointer input, so drags start on the rows under it.
 */
@Composable
private fun TopScrim(opaqueHeight: Dp, color: Color) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color)
                .statusBarsPadding()
                .height(opaqueHeight),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = opaqueHeight)
                .height(TopScrimTail)
                .background(Brush.verticalGradient(listOf(color, Color.Transparent))),
        )
    }
}

/**
 * The result-kind tabs — Lyra's: a Material 3 [PrimaryTabRow] floating over the results, transparent (the top
 * scrim shows through) and without a divider (it would draw a hard line where the scrim turns transparent). The
 * selected tab is the PAGER's current page, so it flips mid-swipe, and the indicator follows the pager per frame
 * ([pagerTrackingIndicator]). A tap pages there on the finite spec.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTabRow(pagerState: PagerState, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val selectedIndex = pagerState.currentPage
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier         = modifier,
        containerColor   = Color.Transparent,
        // `width = Dp.Unspecified` is mandatory — `PrimaryIndicator`'s default is a 24dp stub.
        indicator        = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.pagerTrackingIndicator(this, pagerState),
                width    = Dp.Unspecified,
            )
        },
        divider          = {},
    ) {
        SearchPage.entries.forEachIndexed { index, page ->
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

/**
 * The floating field: a `SearchBarDefaults.InputField` inside a stadium [Surface] in `surfaceContainerHigh` with a
 * small shadow, laid out at exactly [SearchBarHeight] — the morph's geometry assumes that height.
 *
 * The non-deprecated `InputField` needs a `SearchBarState`; it is created already EXPANDED and left alone, because
 * nothing here ever expands into a full-screen search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputBar(
    queryState    : TextFieldState,
    focusRequester: FocusRequester,
    onBack        : () -> Unit,
    onClear       : () -> Unit,
    onSearch      : () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier      : Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    Surface(
        modifier        = modifier.fillMaxWidth().height(SearchBarHeight),
        shape           = CircleShape,
        color           = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = queryState,
            searchBarState = searchBarState,
            onSearch       = { onSearch() },
            modifier       = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            placeholder    = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon    = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                    )
                }
            },
            trailingIcon   = if (queryState.text.isNotBlank()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}
