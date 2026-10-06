package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.ui.screens.library.detail.AlbumDetailContent
import com.crsmthw.sheliak.ui.screens.library.detail.AlbumDetailState
import com.crsmthw.sheliak.ui.screens.library.detail.ArtistDetailContent
import com.crsmthw.sheliak.ui.screens.library.detail.ArtistDetailState
import com.crsmthw.sheliak.ui.screens.library.detail.DetailMode
import com.crsmthw.sheliak.ui.screens.library.detail.PlaylistDetailContent
import com.crsmthw.sheliak.ui.screens.library.detail.PlaylistDetailState

/*
 * The right pane of the two-pane Albums / Artists / Playlists tabs: the same detail bodies the pushed entries
 * draw, in the pane frame (under the library bar, which their scroll collapses through [barScroll]), on the
 * card's `surface`. Each collects its item's flow with the lifecycle for as long as the pane shows it, and starts
 * at its own top: a new pick is a new pane.
 */

@Composable
internal fun AlbumDetailPane(
    albumKey  : TrackKey,
    viewModel : LibraryViewModel,
    barScroll : NestedScrollConnection,
    navigation: LibraryNavigation,
) {
    val state by remember(albumKey) { viewModel.albumDetail(albumKey) }
        .collectAsStateWithLifecycle(AlbumDetailState.Loading)
    val currentTrack by rememberCurrentTrackKey(viewModel.playerState)
    AlbumDetailContent(
        albumKey     = albumKey,
        state        = state,
        mode         = remember(barScroll) { DetailMode.Pane(barScroll) },
        paneColor    = MaterialTheme.colorScheme.surface,
        listState    = rememberLazyListState(),
        player       = viewModel.player,
        currentTrack = currentTrack,
        menuActions  = rememberTrackMenuActions(viewModel.player, onGoToAlbum = null, onGoToArtist = navigation.onOpenArtist),
    )
}

@Composable
internal fun ArtistDetailPane(
    artistKey : TrackKey,
    viewModel : LibraryViewModel,
    barScroll : NestedScrollConnection,
    navigation: LibraryNavigation,
) {
    val state by remember(artistKey) { viewModel.artistDetail(artistKey) }
        .collectAsStateWithLifecycle(ArtistDetailState.Loading)
    ArtistDetailContent(
        artistKey   = artistKey,
        state       = state,
        mode        = remember(barScroll) { DetailMode.Pane(barScroll) },
        paneColor   = MaterialTheme.colorScheme.surface,
        listState   = rememberLazyListState(),
        onOpenAlbum = navigation.onOpenAlbum,
    )
}

@Composable
internal fun PlaylistDetailPane(
    playlistId: Long,
    viewModel : LibraryViewModel,
    barScroll : NestedScrollConnection,
    navigation: LibraryNavigation,
) {
    val state by remember(playlistId) { viewModel.playlistDetail(playlistId) }
        .collectAsStateWithLifecycle(PlaylistDetailState.Loading)
    val currentTrack by rememberCurrentTrackKey(viewModel.playerState)
    PlaylistDetailContent(
        playlistId   = playlistId,
        state        = state,
        mode         = remember(barScroll) { DetailMode.Pane(barScroll) },
        paneColor    = MaterialTheme.colorScheme.surface,
        listState    = rememberLazyListState(),
        player       = viewModel.player,
        currentTrack = currentTrack,
        menuActions  = rememberTrackMenuActions(
            viewModel.player,
            onGoToAlbum  = navigation.onOpenAlbum,
            onGoToArtist = navigation.onOpenArtist,
        ),
    )
}
