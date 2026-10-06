package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.TrackKey

/**
 * The Albums tab: a grid of square-art cards ([AlbumGridCard]). Below 600dp a pick PUSHES the album's own entry,
 * its art flying into the hero ("lib-art-…" shared bounds); from 600dp the tab is two-pane ([LibraryTwoPane]):
 * the grid in the left card, the picked album ([selection], saveable tab state) in the right, swapped with the
 * lateral slide. Pull to sync every source.
 */
@Composable
internal fun AlbumsTab(
    viewModel             : LibraryViewModel,
    albums                : List<Album>?,
    hasSources            : Boolean?,
    twoPane               : Boolean,
    selection             : TrackKey?,
    onSelect              : (TrackKey) -> Unit,
    gridState             : LazyGridState,
    nestedScrollConnection: NestedScrollConnection,
    navigation            : LibraryNavigation,
    paneColor             : Color,
    modifier              : Modifier = Modifier,
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val emptyTitle = stringResource(R.string.library_empty_albums)
    val emptyBody = emptyBodyFor(hasSources, syncProgress)

    val grid: @Composable (Color, Modifier) -> Unit = { gridColor, frameModifier ->
        LibraryRefreshFrame(
            isRefreshing = isRefreshing,
            onRefresh    = viewModel::refresh,
            paneColor    = gridColor,
            modifier     = frameModifier,
        ) {
            LibraryTabGrid(gridState = gridState, nestedScrollConnection = nestedScrollConnection) {
                when (emptyStateFor(hasSources, listLoaded = albums != null, listEmpty = albums.isNullOrEmpty())) {
                    LibraryEmptyKind.NOTHING_YET -> Unit
                    LibraryEmptyKind.NO_SOURCE,
                    LibraryEmptyKind.EMPTY_LIST  -> item(key = EMPTY_STATE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                        LibraryEmptyState(
                            icon        = Icons.Outlined.Album,
                            title       = emptyTitle,
                            body        = emptyBody,
                            onAddSource = navigation.onAddSource.takeIf { hasSources == false },
                        )
                    }
                    null -> items(items = albums.orEmpty(), key = { it.key.mediaId }, contentType = { "album" }) { album ->
                        AlbumGridCard(
                            album             = album,
                            onClick           = {
                                if (twoPane) onSelect(album.key) else navigation.onOpenAlbum(album.key)
                            },
                            selected          = twoPane && album.key == selection,
                            artSharedModifier = if (twoPane) Modifier else Modifier.libraryArtSharedBounds(libraryArtKey(album.key)),
                        )
                    }
                }
            }
        }
    }

    if (twoPane) {
        LibraryTwoPane(
            selection = selection,
            hintIcon  = Icons.Outlined.Album,
            hintText  = stringResource(R.string.library_pick_album),
            list      = { grid(MaterialTheme.colorScheme.surface, Modifier) },
            detail    = { key -> AlbumDetailPane(key, viewModel, nestedScrollConnection, navigation) },
            modifier  = modifier,
        )
    } else {
        grid(paneColor, modifier)
    }
}
