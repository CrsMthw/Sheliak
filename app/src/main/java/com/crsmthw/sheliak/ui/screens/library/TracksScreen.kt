package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.crsmthw.sheliak.R

/**
 * Tracks, the default destination. A single pane at every width — the bar titled with the app name over a
 * "N tracks · N albums" subtitle, then (with play history) the Recently played and Most played carousels,
 * then the full track list. With no art hero, a wide window never inflates the header.
 *
 * M0 has no library: the counts read zero and the list is the empty state. The carousels are not composed
 * at all until there is history to fill them.
 */
@Composable
fun TracksScreen(
    onOpenSettings: () -> Unit,
    floatingAction: @Composable BoxScope.() -> Unit,
    modifier      : Modifier = Modifier,
) {
    val trackCount = 0
    val albumCount = 0
    val subtitle = stringResource(
        R.string.library_counts,
        pluralStringResource(R.plurals.library_track_count, trackCount, trackCount),
        pluralStringResource(R.plurals.library_album_count, albumCount, albumCount),
    )
    val emptyTitle = stringResource(R.string.library_empty_tracks)
    LibraryRootLayout(
        title          = stringResource(R.string.app_name),
        subtitle       = subtitle,
        onOpenSettings = onOpenSettings,
        floatingAction = floatingAction,
        modifier       = modifier,
    ) {
        item(key = EMPTY_STATE_KEY) {
            LibraryEmptyState(
                icon        = Icons.Outlined.LibraryMusic,
                title       = emptyTitle,
                onAddSource = onOpenSettings,
            )
        }
    }
}

/** The empty state's lazy-list key, shared by the four destinations. */
internal const val EMPTY_STATE_KEY = "empty"
