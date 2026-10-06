package com.crsmthw.sheliak.ui.screens.library.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.ui.screens.library.rememberCurrentTrackKey
import com.crsmthw.sheliak.ui.screens.library.rememberTrackMenuActions

/*
 * The three detail screens as pushed navigation entries (below 600dp from the library, and from search, an
 * artist, or a track's "Go to" at every width). Each is its ViewModel's state in the screen frame of
 * DetailContent.kt; the two-pane library draws the same bodies in its right pane.
 */

/** The album entry. Its track menu offers "Go to artist" (never "Go to album": this is the album). */
@Composable
fun AlbumDetailScreen(
    albumKey    : TrackKey,
    viewModel   : AlbumDetailViewModel,
    onBack      : () -> Unit,
    onOpenArtist: (TrackKey) -> Unit,
    modifier    : Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentTrack by rememberCurrentTrackKey(viewModel.playerStates)
    val mode = remember(onBack) { DetailMode.Screen(onBack) }
    AlbumDetailContent(
        albumKey     = albumKey,
        state        = state,
        mode         = mode,
        paneColor    = MaterialTheme.colorScheme.background,
        listState    = rememberLazyListState(),
        player       = viewModel.player,
        currentTrack = currentTrack,
        menuActions  = rememberTrackMenuActions(viewModel.player, onGoToAlbum = null, onGoToArtist = onOpenArtist),
        modifier     = modifier,
    )
}

/** The artist entry: their albums, each opening its own album entry. */
@Composable
fun ArtistDetailScreen(
    artistKey  : TrackKey,
    viewModel  : ArtistDetailViewModel,
    onBack     : () -> Unit,
    onOpenAlbum: (TrackKey) -> Unit,
    modifier   : Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode = remember(onBack) { DetailMode.Screen(onBack) }
    ArtistDetailContent(
        artistKey   = artistKey,
        state       = state,
        mode        = mode,
        paneColor   = MaterialTheme.colorScheme.background,
        listState   = rememberLazyListState(),
        onOpenAlbum = onOpenAlbum,
        modifier    = modifier,
    )
}

/** The playlist entry. Its track menu offers both "Go to album" and "Go to artist". */
@Composable
fun PlaylistDetailScreen(
    playlistId  : Long,
    viewModel   : PlaylistDetailViewModel,
    onBack      : () -> Unit,
    onOpenAlbum : (TrackKey) -> Unit,
    onOpenArtist: (TrackKey) -> Unit,
    modifier    : Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentTrack by rememberCurrentTrackKey(viewModel.playerStates)
    val mode = remember(onBack) { DetailMode.Screen(onBack) }
    PlaylistDetailContent(
        playlistId   = playlistId,
        state        = state,
        mode         = mode,
        paneColor    = MaterialTheme.colorScheme.background,
        listState    = rememberLazyListState(),
        player       = viewModel.player,
        currentTrack = currentTrack,
        menuActions  = rememberTrackMenuActions(viewModel.player, onGoToAlbum = onOpenAlbum, onGoToArtist = onOpenArtist),
        modifier     = modifier,
    )
}
