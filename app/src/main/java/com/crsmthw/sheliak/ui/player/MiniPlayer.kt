package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.components.Artwork
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec

/** Non-snapshot cell for the last track the bar showed (written and read in the same composition). */
private class LastTrackHolder(var value: Track?)

/**
 * The mini player: 48dp art (the `"album-art"` shared element), the title and artist on one line each, play /
 * pause and next, and a thin progress line fed by the position ticker. Lyra's bar — a rounded card on
 * `surfaceContainerHigh` washed with the art's edge colour — minus the device picker: the bar never had an
 * Output slot (DESIGN §8.1 Q10).
 *
 * Its presence is [barTransition], a child of `PlayerSurfaceHost`'s one seekable transition, so the bar slides
 * in and out with a predictive-back gesture and its own `AnimatedVisibility` is the visibility scope its art
 * morphs on — into the full player (compact) or the pop-out card (wide). The bar keeps rendering the last track
 * it had while it slides away when playback ends.
 *
 * @param slideDistancePx how far below its own height the bar travels to leave the screen: the bottom offset it
 *   sits at, so it slides fully out past the window edge.
 * @param onHeightMeasured the bar's measured height in px, margins included — the host's
 *   `LocalPlayerSurfaceInset`.
 */
@Composable
fun MiniPlayer(
    player          : PlayerStateManager,
    track           : Track?,
    isPlaying       : Boolean,
    durationMs      : Long,
    tints           : PlayerTints,
    barTransition   : Transition<Boolean>,
    slideDistancePx : Int,
    onExpand        : () -> Unit,
    onHeightMeasured: (Int) -> Unit,
    modifier        : Modifier = Modifier,
) {
    val haptics   = LocalHapticFeedback.current
    val lastTrack = remember { LastTrackHolder(track) }
    if (track != null) lastTrack.value = track
    val shown     = track ?: lastTrack.value
    val openLabel = stringResource(R.string.player_open)

    barTransition.AnimatedVisibility(
        visible  = { it },
        modifier = modifier,
        enter    = slideInVertically(screenTransitionSpec<IntOffset>()) { it + slideDistancePx },
        exit     = slideOutVertically(screenTransitionSpec<IntOffset>()) { it + slideDistancePx },
    ) {
        val shownTrack = shown ?: return@AnimatedVisibility
        val shape      = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeightMeasured(it.height) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(elevation = 12.dp, shape = shape, ambientColor = tints.edge.copy(alpha = 0.15f))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .background(tints.edge.copy(alpha = 0.10f))
                .clickable(onClickLabel = openLabel) { haptics.confirm(); onExpand() },
        ) {
            Column {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        art                = shownTrack.art,
                        sizePx             = PLAYER_ART_SIZE_PX,
                        contentDescription = null,   // the title beside it says what it is
                        shape              = RoundedCornerShape(10.dp),
                        modifier           = Modifier
                            .albumArtSharedElement(this@AnimatedVisibility)
                            .size(48.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text     = shownTrack.title,
                            style    = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text     = shownTrack.artistName.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.player_unknown_artist),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { haptics.press(); player.playPause() }) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        )
                    }
                    IconButton(onClick = { haptics.press(); player.next() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next))
                    }
                }
                MiniProgressLine(
                    player     = player,
                    durationMs = durationMs,
                    color      = tints.surfaceAccent,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * The bar's thin progress line. Collects the position ticker itself — composed only while the bar is on
 * screen, so the ticker runs only then — and reads it in the indicator's draw lambda, so a tick redraws the
 * line and recomposes nothing. Flat, determinate, no stop dot: a hairline, not a wavy indicator (whose wave
 * would animate forever).
 */
@Composable
private fun MiniProgressLine(
    player    : PlayerStateManager,
    durationMs: Long,
    color     : Color,
    modifier  : Modifier = Modifier,
) {
    val positionFlow = remember(player) { player.position() }
    val position by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    LinearProgressIndicator(
        progress          = { progressFraction(position, durationMs) },
        modifier          = modifier.height(2.dp),
        color             = color,
        trackColor        = color.copy(alpha = 0.24f),
        gapSize           = 0.dp,
        drawStopIndicator = {},
    )
}
