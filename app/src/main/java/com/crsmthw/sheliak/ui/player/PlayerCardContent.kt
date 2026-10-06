package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.util.confirm

/**
 * Everything below the art's chrome in the room the card leaves — header, art, title block, seek bar, chip,
 * transport and actions. Lyra measured it at 360dp; the quality chip adds a row.
 */
private val CardReservedChrome = 400.dp

/**
 * The full player's anatomy fitted to the pop-out card (Lyra's PlayerCardContent): a header (close · "Now
 * playing" · full screen), the art — the `"album-art"` shared element on the card's own visibility scope — sized
 * to whatever height the card's cap leaves, the title block, the seek bar, the quality chip, the transport row
 * and the action row with the reserved Output slot. The background is the art's edge colour fading into the
 * card colour (DESIGN §4).
 *
 * @param visibilityScope the panel's `AnimatedVisibility`, the scope the art morphs on (mini bar ↔ card, card ↔
 *   full player).
 */
@Composable
fun PlayerCardContent(
    player         : PlayerStateManager,
    state          : PlayerState,
    tints          : PlayerTints,
    visibilityScope: AnimatedVisibilityScope?,
    onClose        : () -> Unit,
    onFullScreen   : () -> Unit,
    onOpenQueue    : () -> Unit,
    modifier       : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val cardBg  = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardTop = tints.edge.copy(alpha = CARD_TINT_ALPHA).compositeOver(cardBg)
    val track   = state.track

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // The art takes what the card's height cap leaves after the chrome, never more than the width: it
        // shrinks instead of making the card scroll.
        val artSize = minOf(
            maxWidth - 40.dp,
            (maxHeight - CardReservedChrome).coerceAtLeast(60.dp),
        )
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(cardTop, cardBg)))
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { haptics.confirm(); onClose() }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.player_close_panel))
                }
                Text(
                    text  = stringResource(R.string.player_title),
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = { haptics.confirm(); onFullScreen() }) {
                    Icon(Icons.Default.OpenInFull, contentDescription = stringResource(R.string.player_full_screen))
                }
            }

            SwipeableAlbumArt(
                track           = track,
                queueIndex      = state.queueIndex,
                visibilityScope = visibilityScope,
                size            = artSize,
                onNext          = player::next,
                onPrevious      = player::previous,
            )

            Spacer(Modifier.height(16.dp))
            PlayerTrackInfo(
                track      = track,
                titleStyle = MaterialTheme.typography.titleMedium,
                showAlbum  = false,
                modifier   = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            PlayerSeekBar(
                player     = player,
                trackKey   = track?.key,
                durationMs = state.durationMs,
                tint       = tints.surfaceAccent,
                modifier   = Modifier.fillMaxWidth(),
            )
            QualityTierChip(
                format   = state.liveFormat ?: track?.format,
                tint     = tints.surfaceAccent,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            PlayerTransport(
                state          = state,
                player         = player,
                tints          = tints,
                playButtonSize = 60.dp,
            )
            Spacer(Modifier.height(16.dp))
            PlayerActionRow(tint = tints.surfaceAccent, onOpenQueue = onOpenQueue)
        }
    }
}
