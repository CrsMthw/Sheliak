package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.TrackKey

/**
 * The Artists tab: list cards with round art ([ArtistListCard]) and the alphabet fast scroller (by the index's
 * sort names). Below 600dp a pick pushes the artist's entry (Play / Shuffle, their albums newest first, their
 * tracks) with the shared art; from 600dp it is two-pane, the picked artist in the right card, whose albums push
 * their own entries. Pull to sync every source.
 */
@Composable
internal fun ArtistsTab(
    viewModel             : LibraryViewModel,
    artists               : List<Artist>?,
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
    val emptyTitle = stringResource(R.string.library_empty_artists)
    val emptyBody = emptyBodyFor(hasSources, syncProgress)
    val alphabet by viewModel.artistIndex.collectAsStateWithLifecycle()
    val emptyKind = emptyStateFor(hasSources, listLoaded = artists != null, listEmpty = artists.isNullOrEmpty())

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
                            icon        = Icons.Outlined.Person,
                            title       = emptyTitle,
                            body        = emptyBody,
                            onAddSource = navigation.onAddSource.takeIf { hasSources == false },
                        )
                    }
                    null -> items(items = artists.orEmpty(), key = { it.key.mediaId }, contentType = { "artist" }) { artist ->
                        ArtistListCard(
                            artist            = artist,
                            onClick           = {
                                if (twoPane) onSelect(artist.key) else navigation.onOpenArtist(artist.key)
                            },
                            selected          = twoPane && artist.key == selection,
                            artSharedModifier = if (twoPane) Modifier else Modifier.libraryArtSharedBounds(libraryArtKey(artist.key)),
                        )
                    }
                }
            }
        }
    }

    if (twoPane) {
        LibraryTwoPane(
            selection = selection,
            hintIcon  = Icons.Outlined.Person,
            hintText  = stringResource(R.string.library_pick_artist),
            list      = { list(MaterialTheme.colorScheme.surface, Modifier) },
            detail    = { key -> ArtistDetailPane(key, viewModel, nestedScrollConnection, navigation) },
            modifier  = modifier,
        )
    } else {
        list(paneColor, modifier)
    }
}
