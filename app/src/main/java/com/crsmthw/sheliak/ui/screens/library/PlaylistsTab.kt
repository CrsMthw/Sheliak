package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Playlist

/**
 * The Playlists tab: list cards ([PlaylistListCard]) with a small Play button each. Below 600dp a pick pushes the
 * playlist's entry with the shared art; from 600dp it is two-pane, the picked playlist in the right card. Pull to
 * sync every source.
 */
@Composable
internal fun PlaylistsTab(
    viewModel             : LibraryViewModel,
    playlists             : List<Playlist>?,
    hasSources            : Boolean?,
    twoPane               : Boolean,
    selection             : Long?,
    onSelect              : (Long) -> Unit,
    listState             : LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    navigation            : LibraryNavigation,
    paneColor             : Color,
    modifier              : Modifier = Modifier,
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val emptyTitle = stringResource(R.string.library_empty_playlists)
    val emptyBody = emptyBodyFor(hasSources, syncProgress)

    val list: @Composable (Color, Modifier) -> Unit = { listColor, frameModifier ->
        LibraryRefreshFrame(
            isRefreshing = isRefreshing,
            onRefresh    = viewModel::refresh,
            paneColor    = listColor,
            modifier     = frameModifier,
        ) {
            LibraryTabList(listState = listState, nestedScrollConnection = nestedScrollConnection) {
                when (emptyStateFor(hasSources, listLoaded = playlists != null, listEmpty = playlists.isNullOrEmpty())) {
                    LibraryEmptyKind.NOTHING_YET -> Unit
                    LibraryEmptyKind.NO_SOURCE,
                    LibraryEmptyKind.EMPTY_LIST  -> item(key = EMPTY_STATE_KEY) {
                        LibraryEmptyState(
                            icon        = Icons.AutoMirrored.Outlined.QueueMusic,
                            title       = emptyTitle,
                            body        = emptyBody,
                            onAddSource = navigation.onAddSource.takeIf { hasSources == false },
                        )
                    }
                    null -> items(items = playlists.orEmpty(), key = { it.id }, contentType = { "playlist" }) { playlist ->
                        PlaylistListCard(
                            playlist          = playlist,
                            onClick           = {
                                if (twoPane) onSelect(playlist.id) else navigation.onOpenPlaylist(playlist.id)
                            },
                            onPlay            = { viewModel.playPlaylist(playlist.id) },
                            selected          = twoPane && playlist.id == selection,
                            artSharedModifier = if (twoPane) Modifier else Modifier.libraryArtSharedBounds(playlistArtKey(playlist.id)),
                        )
                    }
                }
            }
        }
    }

    if (twoPane) {
        LibraryTwoPane(
            selection = selection,
            hintIcon  = Icons.AutoMirrored.Outlined.QueueMusic,
            hintText  = stringResource(R.string.library_pick_playlist),
            list      = { list(MaterialTheme.colorScheme.surface, Modifier) },
            detail    = { id -> PlaylistDetailPane(id, viewModel, nestedScrollConnection, navigation) },
            modifier  = modifier,
        )
    } else {
        list(paneColor, modifier)
    }
}
