package com.crsmthw.sheliak.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.RootTopBar
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.rememberRootTopBarScrollBehavior
import com.crsmthw.sheliak.ui.navigation.HomeLibraryTab
import com.crsmthw.sheliak.ui.navigation.LibraryTab
import com.crsmthw.sheliak.ui.navigation.LocalPlayerSurfaceInset
import com.crsmthw.sheliak.ui.navigation.SearchFab
import com.crsmthw.sheliak.ui.navigation.SheliakNavigationSuite
import com.crsmthw.sheliak.ui.navigation.currentWindowWidth
import com.crsmthw.sheliak.ui.navigation.libraryTabOnBack
import com.crsmthw.sheliak.ui.navigation.searchMorphEnabled
import com.crsmthw.sheliak.ui.navigation.suiteLayoutFor
import com.crsmthw.sheliak.util.fadeThrough
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press

/**
 * The Library navigation entry: the navigation suite with the four tabs, and ONE layout for all of them —
 *
 * ```
 * SheliakNavigationSuite                  bar / collapsed rail / expanded rail; consumes the inset it covers
 *   Scaffold(contentWindowInsets = 0)
 *     Column(horizontalSystemBarsPadding)
 *       RootTopBar                        ONE bar: hoisted state, Settings gear; only its TEXT changes per tab
 *       Box(weight 1)
 *         AnimatedContent(tab)            M3 fade through; each tab's own list and LazyListState
 *         TopBarFade · BottomFadeScrim
 *         SearchFab                       compact only, composed once over every tab
 * ```
 *
 * The selected tab is saveable state here, never a back-stack key, so a tab change neither pushes nor
 * replaces anything; system back on another tab returns to Tracks first ([libraryTabOnBack]). Every tab
 * carries a subtitle (its count), so the large bar is one height on all four.
 */
@Composable
fun LibraryShell(
    onOpenSearch  : () -> Unit,
    onOpenSettings: () -> Unit,
    modifier      : Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HomeLibraryTab) }
    LibraryBackHandler(tab = tab, onBackTo = { tab = it })

    val width = currentWindowWidth()
    SheliakNavigationSuite(
        layout       = suiteLayoutFor(width),
        selectedTab  = tab,
        onSelectTab  = { tab = it },
        onOpenSearch = onOpenSearch,
        modifier     = modifier.fillMaxSize(),
    ) {
        LibraryContent(
            tab            = tab,
            showSearchFab  = searchMorphEnabled(width),
            onOpenSearch   = onOpenSearch,
            onOpenSettings = onOpenSettings,
        )
    }
}

/**
 * Back on any tab but Tracks returns to Tracks — only while this entry is RESUMED (on top and settled), so it
 * never competes with the navigation host's own back during a pop onto the library. Its own small
 * composable, so the lifecycle reads recompose nothing else.
 */
@Composable
private fun LibraryBackHandler(tab: LibraryTab, onBackTo: (LibraryTab) -> Unit) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val target = libraryTabOnBack(tab, resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED))
    BackHandler(enabled = target != null) { target?.let(onBackTo) }
}

/** Everything inside the suite: see [LibraryShell]. All per-tab state is hoisted here, outside the fade. */
@Composable
private fun LibraryContent(
    tab           : LibraryTab,
    showSearchFab : Boolean,
    onOpenSearch  : () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Saveable, so the bar's collapse survives navigating away and back; ONE state for the one bar, so a tab
    // change never resets it.
    val barState = rememberTopAppBarState()
    // One list state per tab: during the fade both tabs are composed, and each keeps its scroll position
    // across a switch.
    val tracksList    = rememberLazyListState()
    val albumsList    = rememberLazyListState()
    val artistsList   = rememberLazyListState()
    val playlistsList = rememberLazyListState()
    val paneColor = MaterialTheme.colorScheme.background

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
            RootTopBar(
                title          = stringResource(tab.titleRes),
                subtitle       = pluralStringResource(tab.countRes, EMPTY_LIBRARY_COUNT, EMPTY_LIBRARY_COUNT),
                scrollBehavior = scrollBehavior,
                actions        = { SettingsAction(onClick = onOpenSettings) },
                containerColor = paneColor,
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState    = tab,
                    modifier       = Modifier.fillMaxSize(),
                    transitionSpec = { fadeThrough() },
                    label          = "library-tab",
                ) { shownTab ->
                    val listState = when (shownTab) {
                        LibraryTab.TRACKS    -> tracksList
                        LibraryTab.ALBUMS    -> albumsList
                        LibraryTab.ARTISTS   -> artistsList
                        LibraryTab.PLAYLISTS -> playlistsList
                    }
                    val connection = scrollBehavior.nestedScrollConnection
                    when (shownTab) {
                        LibraryTab.TRACKS    -> TracksTab(listState, connection, onAddSource = onOpenSettings)
                        LibraryTab.ALBUMS    -> AlbumsTab(listState, connection, onAddSource = onOpenSettings)
                        LibraryTab.ARTISTS   -> ArtistsTab(listState, connection, onAddSource = onOpenSettings)
                        LibraryTab.PLAYLISTS -> PlaylistsTab(listState, connection, onAddSource = onOpenSettings)
                    }
                }
                TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
                if (showSearchFab) SearchFab(onClick = onOpenSearch)
            }
        }
    }
}

/** M0 has no library yet: every count reads zero. M1 replaces this with the index's counts. */
private const val EMPTY_LIBRARY_COUNT = 0

/** The bar title for a tab: the app name on Tracks (the home tab), the tab's name elsewhere. */
@get:StringRes
private val LibraryTab.titleRes: Int
    get() = when (this) {
        LibraryTab.TRACKS    -> R.string.app_name
        LibraryTab.ALBUMS    -> R.string.nav_albums
        LibraryTab.ARTISTS   -> R.string.nav_artists
        LibraryTab.PLAYLISTS -> R.string.nav_playlists
    }

/** The bar subtitle for a tab: its own count ("12 tracks", "3 albums", …). */
@get:PluralsRes
private val LibraryTab.countRes: Int
    get() = when (this) {
        LibraryTab.TRACKS    -> R.plurals.library_track_count
        LibraryTab.ALBUMS    -> R.plurals.library_album_count
        LibraryTab.ARTISTS   -> R.plurals.library_artist_count
        LibraryTab.PLAYLISTS -> R.plurals.library_playlist_count
    }

/**
 * The one list layout every tab uses: its own [listState], the bar's [nestedScrollConnection], the seam gap
 * under the bar, then the tab's [content] and the trailing bottom room. One composable so the four tabs
 * cannot drift apart in insets, scroll wiring or seams.
 */
@Composable
internal fun LibraryTabList(
    listState             : LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    modifier              : Modifier = Modifier,
    content               : LazyListScope.() -> Unit,
) {
    LazyColumn(
        state          = listState,
        modifier       = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(top = BarContentGap),
    ) {
        content()
        // The bottom room the fade scrim covers, plus the player surface's inset, so the last row can scroll
        // clear of both. An inset-aware item rather than `contentPadding`, because only an inset modifier knows
        // what the navigation suite's bar has already consumed.
        item(key = BOTTOM_ROOM_KEY, contentType = BOTTOM_ROOM_KEY) {
            BottomFadeSpacer(extra = LocalPlayerSurfaceInset.current)
        }
    }
}

/** The empty state's lazy-list key, shared by the four tabs (each has its own list). */
internal const val EMPTY_STATE_KEY = "empty"

/** Key of the trailing bottom-room item; tabs' own items never use it. */
private const val BOTTOM_ROOM_KEY = "library-bottom-room"

/** The Settings gear in the library bar. */
@Composable
private fun SettingsAction(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    IconButton(onClick = { haptics.press(); onClick() }) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
    }
}
