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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
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

/** The wavy progress ring around the bar's play / pause button. */
private val MiniRingSize = 48.dp

/** The play / pause and next buttons inside the bar. */
private val MiniButtonSize = 40.dp

/**
 * The mini player: 48dp art (the `"album-art"` shared element), the title and artist on one line each, play /
 * pause inside Material 3 Expressive's circular wavy progress ring (Cris, M1 item 48), and next. Lyra's bar — a
 * rounded card on `surfaceContainerHigh` washed with the art's edge colour — minus the device picker: the bar
 * never had an Output slot (DESIGN §8.1 Q10).
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
 * @param artMayMatch whether the art's permanent `"album-art"` registration may match right now (the host's
 *   `SharedContentConfig` gate: a full player or the card must be part of the picture).
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
    artMayMatch     : Boolean = true,
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
        // Lyra's PreEnter bail-out (DESIGN §5.1): compose NOTHING unless the bar is genuinely part of the current
        // or the target state. AnimatedVisibility also composes its content while its transition reports initial
        // value animations — and with the bar and the card as two children of ONE transition, an interrupted
        // change on the card sets that here too. The bar would then compose in PreEnter and register a second,
        // non-target "album-art" participant, which the match would take for the outgoing copy: a wrong flight.
        if (!barTransition.currentState && !barTransition.targetState) return@AnimatedVisibility

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
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    art                = shownTrack.art,
                    sizePx             = PLAYER_ART_SIZE_PX,
                    contentDescription = null,   // the title beside it says what it is
                    shape              = RoundedCornerShape(10.dp),
                    modifier           = Modifier
                        .albumArtSharedElement(this@AnimatedVisibility, artMayMatch)
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
                PlayPauseRing(
                    player      = player,
                    isPlaying   = isPlaying,
                    durationMs  = durationMs,
                    color       = tints.surfaceAccent,
                    onPlayPause = { haptics.press(); player.playPause() },
                )
                IconButton(
                    onClick  = { haptics.press(); player.next() },
                    modifier = Modifier.size(MiniButtonSize),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next))
                }
            }
        }
    }
}

/**
 * Play / pause wrapped in [CircularWavyProgressIndicator]: the ring is the track's progress. It collects the
 * position ticker itself — composed only while the bar is on screen, so the ticker runs only then — and reads it
 * in the indicator's progress lambda, so a tick redraws the ring and recomposes nothing.
 *
 * The wave is bound to playback (CLAUDE.md rule 6) through [rememberPlaybackWave]: wavy while playing, flat on
 * pause (Material's own amplitude tween), the phase never jumping. The speed stays constant: the circular
 * indicator stops its own travel when its amplitude tween ends at 0 and resumes from the same phase on play (a
 * speed of 0 would reset the phase). Material's own amplitude curve keeps the ring flat in the first 10 % and the
 * last 5 % of a track.
 */
@Composable
private fun PlayPauseRing(
    player     : PlayerStateManager,
    isPlaying  : Boolean,
    durationMs : Long,
    color      : Color,
    onPlayPause: () -> Unit,
) {
    val positionFlow = remember(player) { player.position() }
    val position by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val wave = rememberPlaybackWave(isPlaying)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(MiniRingSize)) {
        CircularWavyProgressIndicator(
            progress   = { progressFraction(position, durationMs) },
            modifier   = Modifier.size(MiniRingSize),
            color      = color,
            trackColor = color.copy(alpha = 0.24f),
            // The step TARGET — Material animates it itself (PlaybackWave).
            amplitude  = wave.amplitude,
            wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
            // Constant: Material stops the ring's travel itself once its amplitude reaches 0, keeping the phase.
            waveSpeed  = WavyProgressIndicatorDefaults.CircularWavelength,
        )
        IconButton(onClick = onPlayPause, modifier = Modifier.size(MiniButtonSize)) {
            Icon(
                imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
            )
        }
    }
}
