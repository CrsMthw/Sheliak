package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
 * The Albums tab: a LIST of album cards ([AlbumListCard]: 56dp cover, the title, "artist · year") at every width —
 * the M1 grid left in fix round 1 (an unfolded grid cell grew to most of the pane). Below 600dp a pick PUSHES the
 * album's own entry, its art flying into the hero ("lib-art-…" shared bounds); from 600dp the tab is two-pane
 * ([LibraryTwoPane]): this list in the left card, the picked album ([selection], saveable tab state) in the right,
 * swapped with the lateral slide. The alphabet fast scroller rides on the list, in the left card too. Pull to sync
 * every source.
 */
@Composable
internal fun AlbumsTab(
    viewModel             : LibraryViewModel,
    albums                : List<Album>?,
    hasSources            : Boolean?,
    twoPane               : Boolean,
    selection             : TrackKey?,
    onSelect              : (TrackKey) -> Unit,
    listState             : LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    navigation            : LibraryNavigation,
    paneColor             : Color,
    modifier              : Modifier = Modifier,
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val alphabet by viewModel.albumIndex.collectAsStateWithLifecycle()
    val emptyTitle = stringResource(R.string.library_empty_albums)
    val emptyBody = emptyBodyFor(hasSources, syncProgress)
    val emptyKind = emptyStateFor(hasSources, listLoaded = albums != null, listEmpty = albums.isNullOrEmpty())

    val list: @Composable (Color, Modifier) -> Unit = { listColor, frameModifier ->
        LibraryRefreshFrame(
            isRefreshing = isRefreshing,
            onRefresh    = viewModel::refresh,
            paneColor    = listColor,
            modifier     = frameModifier,
            overlay      = { if (emptyKind == null) LibraryFastScroller(listState = listState, index = alphabet) },
        ) {
            LibraryTabList(
                listState              = listState,
                nestedScrollConnection = nestedScrollConnection,
                bottomRoom             = emitsBottomRoom(emptyKind),
            ) {
                when (emptyKind) {
                    LibraryEmptyKind.NOTHING_YET -> Unit
                    LibraryEmptyKind.NO_SOURCE,
                    LibraryEmptyKind.EMPTY_LIST  -> item(key = EMPTY_STATE_KEY) {
                        LibraryEmptyState(
                            icon        = Icons.Outlined.Album,
                            title       = emptyTitle,
                            body        = emptyBody,
                            onAddSource = navigation.onAddSource.takeIf { hasSources == false },
                        )
                    }
                    null -> items(items = albums.orEmpty(), key = { it.key.mediaId }, contentType = { "album" }) { album ->
                        AlbumListCard(
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
            list      = { list(MaterialTheme.colorScheme.surface, Modifier) },
            detail    = { key -> AlbumDetailPane(key, viewModel, nestedScrollConnection, navigation) },
            modifier  = modifier,
        )
    } else {
        list(paneColor, modifier)
    }
}
