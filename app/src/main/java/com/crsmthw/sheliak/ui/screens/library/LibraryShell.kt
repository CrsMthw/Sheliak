package com.crsmthw.sheliak.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.repository.LibraryCounts
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.ui.components.RootTopBar
import com.crsmthw.sheliak.ui.components.rememberRootTopBarScrollBehavior
import com.crsmthw.sheliak.ui.navigation.HomeLibraryTab
import com.crsmthw.sheliak.ui.navigation.LibraryTab
import com.crsmthw.sheliak.ui.navigation.LocalPlayerSurfaceChrome
import com.crsmthw.sheliak.ui.navigation.LocalPopOutPanelOpen
import com.crsmthw.sheliak.ui.navigation.SheliakNavigationSuite
import com.crsmthw.sheliak.ui.navigation.TabReselect
import com.crsmthw.sheliak.ui.navigation.WindowWidth
import com.crsmthw.sheliak.ui.navigation.currentWindowWidth
import com.crsmthw.sheliak.ui.navigation.libraryTabIsTwoPane
import com.crsmthw.sheliak.ui.navigation.libraryTabOnBack
import com.crsmthw.sheliak.ui.navigation.suiteLayoutFor
import com.crsmthw.sheliak.ui.navigation.tabReselectFor
import com.crsmthw.sheliak.ui.screens.search.SearchField
import com.crsmthw.sheliak.ui.screens.search.SearchPage
import com.crsmthw.sheliak.ui.screens.search.SearchPageState
import com.crsmthw.sheliak.ui.screens.search.SearchQuerySync
import com.crsmthw.sheliak.ui.screens.search.SearchViewModel
import com.crsmthw.sheliak.ui.screens.search.rememberSearchPageState
import com.crsmthw.sheliak.ui.screens.search.searchSubtitle
import com.crsmthw.sheliak.util.fadeThrough
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec
import kotlinx.coroutines.launch

/**
 * What a library pick asks of the shell: push a detail entry (below 600dp, and from any "go to"), or set up a
 * source. One value so the tabs take one parameter instead of five.
 */
internal class LibraryNavigation(
    val onOpenAlbum   : (TrackKey) -> Unit,
    val onOpenArtist  : (TrackKey) -> Unit,
    val onOpenPlaylist: (Long) -> Unit,
    val onAddSource   : () -> Unit,
)

/**
 * The Library navigation entry: the navigation suite with the five tabs, and ONE layout for all of them —
 *
 * ```
 * SheliakNavigationSuite                  bar / collapsed rail / expanded rail — five plain items: Tracks, Albums,
 *                                         SEARCH (the bar's centre), Artists, Playlists; the rails put Search
 *                                         last; consumes what it covers, publishes its extent to the player surface
 *   Scaffold(contentWindowInsets = 0)
 *     Column(horizontalSystemBarsPadding)
 *       RootTopBar                        ONE bar: hoisted state, Settings gear; only its TEXT changes per tab;
 *                                         belowBar = the search field, on the Search tab only
 *       Box(weight 1)
 *         AnimatedContent(tab)            M3 fade through; each tab's own list state, pull-to-refresh and seams;
 *                                         Albums / Artists / Playlists two-pane from 600dp; Search's result pages
 * ```
 *
 * The selected tab — and each list-detail tab's selected item, and the Search tab's query, page and positions —
 * is saveable state here, never a back-stack key, so a tab change or a pick in a two-pane tab neither pushes nor
 * replaces anything; system back on another tab returns to Tracks first ([libraryTabOnBack]). Every tab carries a
 * non-null subtitle (its count; "Syncing… N" on Tracks while a source syncs; on Search an empty line while the
 * field is blank, else the count of the result tab shown), so the large bar is one height on all five.
 *
 * Search is a tab like the others: the first tap shows its page with the field NOT focused; re-tapping the
 * selected Search tab focuses the field and raises the keyboard ([tabReselectFor]); leaving the tab — another
 * tab, or back — drops the focus and the keyboard. Its ViewModel ([searchViewModel]) lives in this entry's store.
 */
@Composable
fun LibraryShell(
    viewModel      : LibraryViewModel,
    searchViewModel: SearchViewModel,
    onOpenSettings : () -> Unit,
    onAddSource    : () -> Unit,
    onOpenAlbum    : (TrackKey) -> Unit,
    onOpenArtist   : (TrackKey) -> Unit,
    onOpenPlaylist : (Long) -> Unit,
    modifier       : Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HomeLibraryTab) }
    val search = rememberSearchPageState()
    SearchQuerySync(viewModel = searchViewModel, state = search)

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Every tab change goes through here — a suite tap or a back — so leaving Search always lets go of the field.
    val selectTab: (LibraryTab) -> Unit = { next ->
        if (tab == LibraryTab.SEARCH && next != LibraryTab.SEARCH) search.releaseField(focusManager, keyboard)
        tab = next
    }
    LibraryBackHandler(tab = tab, onBackTo = selectTab)

    val width = currentWindowWidth()
    val navigation = LibraryNavigation(onOpenAlbum, onOpenArtist, onOpenPlaylist, onAddSource)
    val lists = rememberLibraryListStates()
    val selections = rememberLibrarySelections()
    val scope = rememberCoroutineScope()
    SheliakNavigationSuite(
        layout        = suiteLayoutFor(width),
        selectedTab   = tab,
        onSelectTab   = selectTab,
        onReselectTab = { reselected ->
            when (tabReselectFor(reselected)) {
                TabReselect.FocusSearchField -> search.focusField(keyboard)
                TabReselect.ScrollToTop      -> scope.launch { lists.scrollToTop(reselected) }
            }
        },
        modifier      = modifier.fillMaxSize(),
    ) {
        LibraryContent(
            viewModel       = viewModel,
            searchViewModel = searchViewModel,
            search          = search,
            tab             = tab,
            width           = width,
            lists           = lists,
            selections      = selections,
            navigation      = navigation,
            onOpenSettings  = onOpenSettings,
        )
    }
}

/**
 * Back on any tab but Tracks returns to Tracks — only while this entry is RESUMED (on top and settled), so it
 * never competes with the navigation host's own back during a pop onto the library, and never while the player's
 * pop-out panel is open ([LocalPopOutPanelOpen]: that back closes the panel, whatever order the handlers
 * registered in). Its own small composable, so the lifecycle reads recompose nothing else.
 */
@Composable
private fun LibraryBackHandler(tab: LibraryTab, onBackTo: (LibraryTab) -> Unit) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val panelOpen = LocalPopOutPanelOpen.current
    val target = libraryTabOnBack(tab, resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED))
    BackHandler(enabled = target != null && !panelOpen) { target?.let(onBackTo) }
}

/**
 * Everything inside the suite: see [LibraryShell]. The per-tab state (list positions, two-pane selections) is
 * hoisted above it, in [LibraryShell] with the tab, so it survives tab changes and a fold / unfold.
 *
 * @param width the window's width class. A list-detail tab is two-pane where [libraryTabIsTwoPane] allows it
 *   AND it has rows (an empty tab stays one centred empty state). The shown tab's answer is published to the
 *   player surface ([LocalPlayerSurfaceChrome] `twoPane`), so the mini bar narrows to the detail card.
 */
@Composable
private fun LibraryContent(
    viewModel      : LibraryViewModel,
    searchViewModel: SearchViewModel,
    search         : SearchPageState,
    tab            : LibraryTab,
    width          : WindowWidth,
    lists          : LibraryListStates,
    selections     : LibrarySelections,
    navigation     : LibraryNavigation,
    onOpenSettings : () -> Unit,
) {
    // Saveable, so the bar's collapse survives navigating away and back; ONE state for the one bar, so a tab
    // change never resets it.
    val barState = rememberTopAppBarState()
    val paneColor = MaterialTheme.colorScheme.background

    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val hasSources by viewModel.hasSources.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // Asked of the target tab (published) and of each tab the fade composes (the outgoing half keeps its layout).
    fun isTwoPane(shown: LibraryTab): Boolean = libraryTabIsTwoPane(shown, width) && when (shown) {
        LibraryTab.TRACKS,
        LibraryTab.SEARCH    -> false
        LibraryTab.ALBUMS    -> !albums.isNullOrEmpty()
        LibraryTab.ARTISTS   -> !artists.isNullOrEmpty()
        LibraryTab.PLAYLISTS -> !playlists.isNullOrEmpty()
    }
    PublishTwoPane(isTwoPane(tab))

    Scaffold(
        containerColor      = paneColor,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // The library's one horizontal inset: a 3-button navigation bar or a camera cutout on a side edge
        // (the rail's side is already consumed by the suite).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .horizontalSystemBarsPadding(),
        ) {
            val scrollBehavior = rememberRootTopBarScrollBehavior(barState)
            val keyboard = LocalSoftwareKeyboardController.current
            RootTopBar(
                title          = stringResource(tab.titleRes),
                subtitle       = if (tab == LibraryTab.SEARCH) {
                    searchSubtitle(searchViewModel, search)
                } else {
                    subtitleFor(tab, counts, syncProgress)
                },
                scrollBehavior = scrollBehavior,
                actions        = { SettingsAction(onClick = onOpenSettings) },
                containerColor = paneColor,
                // A tab change crossfades the words; a count ticking during a sync (or Search's result count
                // appearing, changing with the query or the result tab, and going) is written in place.
                textKey        = tab,
                // The search field rides under the bar on the Search tab only. Its slot opens and closes on the
                // one finite spec, so the content below glides instead of jumping when the tab changes.
                belowBar       = {
                    AnimatedVisibility(
                        visible = tab == LibraryTab.SEARCH,
                        enter   = fadeIn(screenTransitionSpec()) +
                            expandVertically(screenTransitionSpec(), expandFrom = Alignment.Top),
                        exit    = fadeOut(screenTransitionSpec()) +
                            shrinkVertically(screenTransitionSpec(), shrinkTowards = Alignment.Top),
                        label   = "search-field",
                    ) {
                        SearchField(
                            state     = search,
                            paneColor = paneColor,
                            onSearch  = { keyboard?.hide() },
                        )
                    }
                },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState    = tab,
                    modifier       = Modifier.fillMaxSize(),
                    transitionSpec = { fadeThrough() },
                    label          = "library-tab",
                ) { shownTab ->
                    val connection = scrollBehavior.nestedScrollConnection
                    val shownTwoPane = isTwoPane(shownTab)
                    when (shownTab) {
                        LibraryTab.TRACKS    -> TracksTab(
                            viewModel              = viewModel,
                            hasSources             = hasSources,
                            listState              = lists.tracks,
                            nestedScrollConnection = connection,
                            navigation             = navigation,
                            paneColor              = paneColor,
                        )
                        LibraryTab.ALBUMS    -> AlbumsTab(
                            viewModel              = viewModel,
                            albums                 = albums,
                            hasSources             = hasSources,
                            twoPane                = shownTwoPane,
                            selection              = selections.album.value?.let { TrackKey.parseMediaId(it) },
                            onSelect               = { selections.album.value = it.mediaId },
                            listState              = lists.albums,
                            nestedScrollConnection = connection,
                            navigation             = navigation,
                            paneColor              = paneColor,
                        )
                        LibraryTab.ARTISTS   -> ArtistsTab(
                            viewModel              = viewModel,
                            artists                = artists,
                            hasSources             = hasSources,
                            twoPane                = shownTwoPane,
                            selection              = selections.artist.value?.let { TrackKey.parseMediaId(it) },
                            onSelect               = { selections.artist.value = it.mediaId },
                            listState              = lists.artists,
                            nestedScrollConnection = connection,
                            navigation             = navigation,
                            paneColor              = paneColor,
                        )
                        LibraryTab.SEARCH    -> SearchPage(
                            viewModel              = searchViewModel,
                            state                  = search,
                            nestedScrollConnection = connection,
                            paneColor              = paneColor,
                            onOpenAlbum            = navigation.onOpenAlbum,
                            onOpenArtist           = navigation.onOpenArtist,
                            onOpenPlaylist         = navigation.onOpenPlaylist,
                        )
                        LibraryTab.PLAYLISTS -> PlaylistsTab(
                            viewModel              = viewModel,
                            playlists              = playlists,
                            hasSources             = hasSources,
                            twoPane                = shownTwoPane,
                            selection              = selections.playlist.value,
                            onSelect               = { selections.playlist.value = it },
                            listState              = lists.playlists,
                            nestedScrollConnection = connection,
                            navigation             = navigation,
                            paneColor              = paneColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Publishes whether the shown tab is two-pane to the player surface ([LocalPlayerSurfaceChrome]), so the mini bar
 * narrows to the detail card's width. Reset when it changes (the new value is written in the same apply) and when
 * the library leaves composition.
 */
@Composable
private fun PublishTwoPane(twoPane: Boolean) {
    val chrome = LocalPlayerSurfaceChrome.current
    DisposableEffect(chrome, twoPane) {
        chrome.twoPane = twoPane
        onDispose { chrome.twoPane = false }
    }
}

/**
 * The bar subtitle for a library tab: its own count, or "Syncing… N" on Tracks while a source syncs. (Search's
 * subtitle is its own — `searchSubtitle`, empty while the field is blank — and is asked of it directly; the
 * Search branches here and in [countRes] only keep the `when`s exhaustive.)
 */
@Composable
private fun subtitleFor(tab: LibraryTab, counts: LibraryCounts, syncProgress: Int?): String = when {
    tab == LibraryTab.TRACKS && syncProgress != null -> stringResource(R.string.library_syncing, syncProgress)
    else -> {
        val count = when (tab) {
            LibraryTab.TRACKS,
            LibraryTab.SEARCH    -> counts.tracks
            LibraryTab.ALBUMS    -> counts.albums
            LibraryTab.ARTISTS   -> counts.artists
            LibraryTab.PLAYLISTS -> counts.playlists
        }
        pluralStringResource(tab.countRes, count, count)
    }
}

/** The bar title for a tab: the app name on Tracks (the home tab), the tab's name elsewhere. */
@get:StringRes
private val LibraryTab.titleRes: Int
    get() = when (this) {
        LibraryTab.TRACKS    -> R.string.app_name
        LibraryTab.ALBUMS    -> R.string.nav_albums
        LibraryTab.SEARCH    -> R.string.nav_search
        LibraryTab.ARTISTS   -> R.string.nav_artists
        LibraryTab.PLAYLISTS -> R.string.nav_playlists
    }

/** The bar subtitle for a tab: its own count ("12 tracks", "3 albums", …). */
@get:PluralsRes
private val LibraryTab.countRes: Int
    get() = when (this) {
        LibraryTab.TRACKS,
        LibraryTab.SEARCH    -> R.plurals.library_track_count
        LibraryTab.ALBUMS    -> R.plurals.library_album_count
        LibraryTab.ARTISTS   -> R.plurals.library_artist_count
        LibraryTab.PLAYLISTS -> R.plurals.library_playlist_count
    }

/** The Settings gear in the library bar. */
@Composable
private fun SettingsAction(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    IconButton(onClick = { haptics.press(); onClick() }) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
    }
}

/**
 * The four list tabs' scroll positions (Search keeps its own, in `SearchPageState`), hoisted above the tab fade
 * (during it both tabs are composed, and each keeps its position across a switch) and shared by a tab's single-
 * and two-pane layouts (a fold / unfold keeps it).
 */
internal class LibraryListStates(
    val tracks   : LazyListState,
    val albums   : LazyListState,
    val artists  : LazyListState,
    val playlists: LazyListState,
) {
    /**
     * Re-selecting a tab in the suite jumps its list back to the top (no scroll animation to outlive). Search has
     * no list of its own here — re-selecting it focuses the field instead (`tabReselectFor`).
     */
    suspend fun scrollToTop(tab: LibraryTab) {
        when (tab) {
            LibraryTab.TRACKS    -> tracks.scrollToItem(0)
            LibraryTab.ALBUMS    -> albums.scrollToItem(0)
            LibraryTab.SEARCH    -> Unit
            LibraryTab.ARTISTS   -> artists.scrollToItem(0)
            LibraryTab.PLAYLISTS -> playlists.scrollToItem(0)
        }
    }
}

/**
 * The two-pane picks, one per list-detail tab — a media id for an album or an artist, a row id for a playlist —
 * saveable, and hoisted with the tab itself (above the suite), so they survive tab changes, the suite switching
 * between bar and rail, and a fold / unfold (below 600dp they are simply not shown).
 */
internal class LibrarySelections(
    val album   : MutableState<String?>,
    val artist  : MutableState<String?>,
    val playlist: MutableState<Long?>,
)

@Composable
private fun rememberLibrarySelections(): LibrarySelections {
    val album = rememberSaveable { mutableStateOf<String?>(null) }
    val artist = rememberSaveable { mutableStateOf<String?>(null) }
    val playlist = rememberSaveable { mutableStateOf<Long?>(null) }
    return remember(album, artist, playlist) { LibrarySelections(album, artist, playlist) }
}

@Composable
private fun rememberLibraryListStates(): LibraryListStates {
    val tracks = rememberLazyListState()
    val albums = rememberLazyListState()
    val artists = rememberLazyListState()
    val playlists = rememberLazyListState()
    return remember(tracks, albums, artists, playlists) {
        LibraryListStates(tracks, albums, artists, playlists)
    }
}
