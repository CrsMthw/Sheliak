package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.longPress
import com.crsmthw.sheliak.util.toTimeString

/** The row's art tile. */
val TrackRowArtSize = 48.dp

/** The art tile's corner (Lyra's 4dp: a row thumbnail is small enough that a larger radius reads as a pill). */
private val TrackRowArtShape = RoundedCornerShape(4.dp)

/**
 * One track in a list (Lyra's `TrackRow`, ported onto [Track]): 48dp [Artwork], the title over the artist line,
 * the duration at the end. The title takes the accent colour while this is the [isCurrent] track.
 *
 * Tap fires a `confirm()` haptic and [onClick] (play from here); long-press fires `longPress()` and [onLongClick]
 * (the caller opens the track's menu — anchor a `DropdownMenu` beside this row in the same `Box`). The click's
 * own haptic is switched off: the app's haptics go through the Settings gate (`util/Haptics.kt`).
 *
 * @param artistFallback shown when the track has no artist name (a string resource, resolved by the caller).
 */
@Composable
fun TrackRow(
    track         : Track,
    artistFallback: String,
    onClick       : () -> Unit,
    modifier      : Modifier = Modifier,
    isCurrent     : Boolean = false,
    onLongClick   : (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val longClickLabel = if (onLongClick != null) stringResource(R.string.track_actions) else null
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick               = { haptics.confirm(); onClick() },
                onLongClick           = onLongClick?.let { handler -> { haptics.longPress(); handler() } },
                onLongClickLabel      = longClickLabel,
                hapticFeedbackEnabled = false,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            art                = track.art,
            sizePx             = artSizePx(TrackRowArtSize),
            contentDescription = null,
            modifier           = Modifier.size(TrackRowArtSize),
            shape              = TrackRowArtShape,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.bodyMedium,
                color    = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = track.artistName.ifBlank { artistFallback },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text  = track.durationMs.toTimeString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
