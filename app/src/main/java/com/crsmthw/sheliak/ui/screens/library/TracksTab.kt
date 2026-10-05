package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import com.crsmthw.sheliak.R

/**
 * The Tracks tab, the library's home. A single pane at every width — under the library bar ("Sheliak" over
 * "N tracks"), the Recently played and Most played carousels (with play history), then the full track list. With
 * no art hero, a wide window never inflates the header. Laid out by [LibraryShell].
 *
 * M0 has no library: the list is the empty state. The carousels are not composed at all until there is history
 * to fill them.
 */
@Composable
internal fun TracksTab(
    listState             : LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onAddSource           : () -> Unit,
    modifier              : Modifier = Modifier,
) {
    val emptyTitle = stringResource(R.string.library_empty_tracks)
    LibraryTabList(
        listState              = listState,
        nestedScrollConnection = nestedScrollConnection,
        modifier               = modifier,
    ) {
        item(key = EMPTY_STATE_KEY) {
            LibraryEmptyState(
                icon        = Icons.Outlined.LibraryMusic,
                title       = emptyTitle,
                onAddSource = onAddSource,
            )
        }
    }
}
