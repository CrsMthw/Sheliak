package com.crsmthw.sheliak.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.util.press

/**
 * What a track's long-press menu can do. Local and immediate (the queue is the player's own timeline), so there
 * is no result to wait for. A null "go to" hides that item everywhere this set is used (the album detail passes
 * none for "Go to album", the track's own album being the screen).
 */
@Immutable
data class TrackMenuActions(
    val onPlayNext  : (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onGoToAlbum : ((TrackKey) -> Unit)?,
    val onGoToArtist: ((TrackKey) -> Unit)?,
)

/**
 * A [TrackRow] with its long-press menu — Play next, Add to queue, Go to album, Go to artist — anchored under the
 * row. The menu is composed only once it has been opened for this row, so a list of thousands of rows carries no
 * thousand closed menus; after that it stays composed, so closing it plays the menu's own exit.
 */
@Composable
fun TrackRowWithMenu(
    track         : Track,
    artistFallback: String,
    isCurrent     : Boolean,
    onPlay        : () -> Unit,
    actions       : TrackMenuActions,
    modifier      : Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuUsed by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TrackRow(
            track          = track,
            artistFallback = artistFallback,
            onClick        = onPlay,
            isCurrent      = isCurrent,
            onLongClick    = { menuUsed = true; menuOpen = true },
        )
        if (menuUsed) {
            TrackActionsMenu(
                track     = track,
                expanded  = menuOpen,
                onDismiss = { menuOpen = false },
                actions   = actions,
            )
        }
    }
}

/**
 * The track menu itself, a Material [DropdownMenu]. Each pick fires a `press()` haptic, closes the menu, then
 * acts. "Go to album" / "Go to artist" show only when the track has that key and [actions] can open it.
 */
@Composable
fun TrackActionsMenu(
    track    : Track,
    expanded : Boolean,
    onDismiss: () -> Unit,
    actions  : TrackMenuActions,
    modifier : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val pick: (() -> Unit) -> () -> Unit = { action -> { haptics.press(); onDismiss(); action() } }
    DropdownMenu(
        expanded         = expanded,
        onDismissRequest = onDismiss,
        modifier         = modifier,
    ) {
        MenuItem(R.string.track_play_next, Icons.AutoMirrored.Filled.PlaylistPlay, pick { actions.onPlayNext(track) })
        MenuItem(R.string.track_add_to_queue, Icons.AutoMirrored.Filled.PlaylistAdd, pick { actions.onAddToQueue(track) })
        val album = track.albumKey
        val goToAlbum = actions.onGoToAlbum
        if (album != null && goToAlbum != null) {
            MenuItem(R.string.track_go_to_album, Icons.Outlined.Album, pick { goToAlbum(album) })
        }
        val artist = track.artistKey
        val goToArtist = actions.onGoToArtist
        if (artist != null && goToArtist != null) {
            MenuItem(R.string.track_go_to_artist, Icons.Outlined.Person, pick { goToArtist(artist) })
        }
    }
}

@Composable
private fun MenuItem(@StringRes labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text        = { Text(stringResource(labelRes)) },
        onClick     = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}
