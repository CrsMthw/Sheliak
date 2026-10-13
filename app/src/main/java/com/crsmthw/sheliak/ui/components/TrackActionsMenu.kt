package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.press

/**
 * What a track's long-press sheet can do. Local and immediate (the queue is the player's own timeline), so there
 * is no result to wait for. A null "go to" hides that row everywhere this set is used (the album detail passes
 * none for "Go to album", the artist detail none for "Go to artist": the screen IS that item).
 */
@Immutable
data class TrackMenuActions(
    val onPlayNext  : (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onGoToAlbum : ((TrackKey) -> Unit)?,
    val onGoToArtist: ((TrackKey) -> Unit)?,
)

/**
 * A [TrackRow] with its long-press sheet ([TrackActionsMenu]) — Play next, Add to queue, Go to album, Go to
 * artist. The sheet is composed only while it is open: a composed modal sheet shows itself, and a list of
 * thousands of rows carries no closed sheets. Every close animates first (the sheet's own swipe, scrim tap and
 * back, or [LocalSheetDismissal] from a row) and only then leaves composition.
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
    var sheetOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TrackRow(
            track          = track,
            artistFallback = artistFallback,
            onClick        = onPlay,
            isCurrent      = isCurrent,
            onLongClick    = { sheetOpen = true },
        )
        TrackActionsMenu(
            track     = track,
            expanded  = sheetOpen,
            onDismiss = { sheetOpen = false },
            actions   = actions,
        )
    }
}

/**
 * A track's actions as Lyra's track sheet (`TrackActionsHost`, ported onto [Track] with Sheliak's four local
 * actions): a [CappedModalBottomSheet] with a header row (art, title, artist) over Play next, Add to queue, Go to
 * album and Go to artist. Nothing is composed while [expanded] is false; [onDismiss] runs once the sheet is down.
 *
 * - **Play next / Add to queue** act at the tap (they are immediate) and then close the sheet with its animation.
 *   They act WITHOUT the dismissal handle's latch, so they are gated on `!isHiding`: a second tap during the hide
 *   cannot queue the track twice.
 * - **Go to album / artist** navigate only once the sheet is down (`dismiss(then = …)`), so the detail pushes
 *   over a clean screen instead of loading behind a closing sheet. Each shows only when the track has that key
 *   and [actions] can open it.
 *
 * Why a sheet and not the M1 `DropdownMenu`: besides matching Lyra, a `DropdownMenu` is a Compose `Popup`, and a
 * Popup that leaves composition while a predictive back gesture is in flight crashes the app on this toolchain
 * (navigationevent 1.1.2 — docs/LIBRARY.md, "The back-gesture crash"). The modal sheet is a full-screen dialog
 * window whose back input is never detached, so it cannot take that path.
 *
 * The signature is the M1 menu's, kept source-compatible for its callers ([modifier] goes on the sheet's column).
 */
@Composable
fun TrackActionsMenu(
    track    : Track,
    expanded : Boolean,
    onDismiss: () -> Unit,
    actions  : TrackMenuActions,
    modifier : Modifier = Modifier,
) {
    if (!expanded) return
    val haptics = LocalHapticFeedback.current
    val artistFallback = stringResource(R.string.library_unknown_artist)
    CappedModalBottomSheet(onDismissRequest = onDismiss) {
        val dismissal = LocalSheetDismissal.current
        BoxWithConstraints {
            Column(
                modifier = modifier
                    .heightIn(max = maxHeight - sheetTopGap())
                    .verticalScroll(rememberScrollState()),
            ) {
                SheetHeader(track = track, artistFallback = artistFallback)
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                SheetAction(
                    icon    = Icons.AutoMirrored.Filled.PlaylistPlay,
                    text    = stringResource(R.string.track_play_next),
                    enabled = !dismissal.isHiding,
                    onClick = { haptics.confirm(); actions.onPlayNext(track); dismissal.dismiss() },
                )
                SheetAction(
                    icon    = Icons.AutoMirrored.Filled.PlaylistAdd,
                    text    = stringResource(R.string.track_add_to_queue),
                    enabled = !dismissal.isHiding,
                    onClick = { haptics.confirm(); actions.onAddToQueue(track); dismissal.dismiss() },
                )
                val album = track.albumKey
                val goToAlbum = actions.onGoToAlbum
                if (album != null && goToAlbum != null) {
                    SheetAction(
                        icon    = Icons.Outlined.Album,
                        text    = stringResource(R.string.track_go_to_album),
                        onClick = { haptics.press(); dismissal.dismiss { goToAlbum(album) } },
                    )
                }
                val artist = track.artistKey
                val goToArtist = actions.onGoToArtist
                if (artist != null && goToArtist != null) {
                    SheetAction(
                        icon    = Icons.Outlined.Person,
                        text    = stringResource(R.string.track_go_to_artist),
                        onClick = { haptics.press(); dismissal.dismiss { goToArtist(artist) } },
                    )
                }

                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** The header art's edge and corner (Lyra's sheet header: 48dp, 6dp). */
private val SheetHeaderArtSize = 48.dp
private val SheetHeaderArtShape = RoundedCornerShape(6.dp)

/** The sheet's header: the track's art, its title (the sheet's heading) and its artist. */
@Composable
private fun SheetHeader(track: Track, artistFallback: String) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            art                = track.art,
            sizePx             = artSizePx(SheetHeaderArtSize),
            contentDescription = null,
            modifier           = Modifier.size(SheetHeaderArtSize),
            shape              = SheetHeaderArtShape,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text     = track.artistName.ifBlank { artistFallback },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One action row (Lyra's `ActionItem`): a leading icon and a label, dimmed while [enabled] is false. */
@Composable
private fun SheetAction(
    icon   : ImageVector,
    text   : String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier       = Modifier.clickable(enabled = enabled, onClick = onClick),
        leadingContent = {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (enabled) iconTint else iconTint.copy(alpha = DISABLED_ALPHA),
            )
        },
        content        = {
            Text(text = text, color = if (enabled) textColor else textColor.copy(alpha = DISABLED_ALPHA))
        },
    )
}

/** Material's disabled-content alpha. */
private const val DISABLED_ALPHA = 0.38f
