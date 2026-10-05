package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.crsmthw.sheliak.R

/**
 * Artists, a list-detail destination. From 600dp wide the list and the selected item's detail will sit side
 * by side in two cards (a Navigation 3 scene strategy, see docs/SCREENS.md); below 600dp the detail pushes as
 * its own screen. Both arrive with real data — M0 shows the empty state only.
 */
@Composable
fun ArtistsScreen(
    onOpenSettings: () -> Unit,
    floatingAction: @Composable BoxScope.() -> Unit,
    modifier      : Modifier = Modifier,
) {
    val emptyTitle = stringResource(R.string.library_empty_artists)
    LibraryRootLayout(
        title          = stringResource(R.string.nav_artists),
        onOpenSettings = onOpenSettings,
        floatingAction = floatingAction,
        modifier       = modifier,
    ) {
        item(key = EMPTY_STATE_KEY) {
            LibraryEmptyState(
                icon        = Icons.Outlined.Person,
                title       = emptyTitle,
                onAddSource = onOpenSettings,
            )
        }
    }
}
