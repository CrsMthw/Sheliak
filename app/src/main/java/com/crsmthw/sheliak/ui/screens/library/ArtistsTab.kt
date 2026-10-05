package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import com.crsmthw.sheliak.R

/**
 * The Artists tab, a list-detail tab. From 600dp wide the list and the selected item's detail will sit side by
 * side in two cards; below 600dp the detail pushes as its own screen (M1, see docs/SCREENS.md). Both arrive with
 * real data — M0 shows the empty state only. Laid out by [LibraryShell].
 */
@Composable
internal fun ArtistsTab(
    listState             : LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onAddSource           : () -> Unit,
    modifier              : Modifier = Modifier,
) {
    val emptyTitle = stringResource(R.string.library_empty_artists)
    LibraryTabList(
        listState              = listState,
        nestedScrollConnection = nestedScrollConnection,
        modifier               = modifier,
    ) {
        item(key = EMPTY_STATE_KEY) {
            LibraryEmptyState(
                icon        = Icons.Outlined.Person,
                title       = emptyTitle,
                onAddSource = onAddSource,
            )
        }
    }
}
