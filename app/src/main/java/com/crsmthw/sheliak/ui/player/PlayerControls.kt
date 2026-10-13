@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.crsmthw.sheliak.ui.player

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec
import com.crsmthw.sheliak.util.tick
import com.crsmthw.sheliak.util.toTimeString
import com.crsmthw.sheliak.util.toggle

/*
 * The pieces the full player (ui/screens/player/PlayerScreen) and the pop-out card (PlayerCardContent) share:
 * the title block, the wavy seek bar, the transport row with the turning play shape, and the action row with the
 * reserved Output slot (the art is AlbumArtCarousel). Lyra's PlayerControls anatomy, rebuilt on
 * PlayerStateManager: every command is a PlaybackController call and every value comes from PlayerState or the
 * position ticker.
 */

/** Title, artist and (optionally) album, one line each. */
@Composable
fun PlayerTrackInfo(
    track     : Track?,
    titleStyle: TextStyle,
    showAlbum : Boolean,
    modifier  : Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text     = track?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_nothing_playing),
            style    = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (track != null) {
            Text(
                text     = track.artistName.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_unknown_artist),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val album = track.albumTitle
            if (showAlbum && !album.isNullOrBlank()) {
                Text(
                    text     = album,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The seek bar's haptic notch: one tick per 5 % of the track while dragging (Lyra). */
private const val SEEK_NOTCH = 0.05f

/** The seek bar's touch height: Material's minimum touch target, the wavy line centred in it. */
private val SeekTouchHeight = 48.dp

/** The seek thumb: an M3 Expressive slider's upright bar. */
private val SeekThumbWidth = 4.dp
private val SeekThumbHeight = 24.dp

/**
 * The seek bar with elapsed / remaining time: Material 3 Expressive's [LinearWavyProgressIndicator] as the track
 * (Cris, M1 item 50), an upright thumb at the progress point, tap or drag to seek.
 *
 * - **The wave** is bound to playback (CLAUDE.md rule 6): amplitude 1 while playing, flat (0) while paused, the
 *   change animated on the finite spec; its travel speed drops to 0 once it is flat, so nothing moves on a paused
 *   player. Material's own amplitude curve keeps the wave flat in the first 10 % and the last 5 % of a track.
 * - **The position ticker** ([PlayerStateManager.position]) is collected HERE, so only this block recomposes on a
 *   tick and the ticker runs only while a seek bar is on screen; the line and the thumb read it in their draw /
 *   layout lambdas.
 * - **A drag** shows the finger's value and ticks every 5 %; a tap seeks at once. On release it seeks, and the bar
 *   keeps the released value until the ticker's NEXT report — the controller masks a seek at once, so that report
 *   is already the new position; without it the thumb would flick back to the old position for up to one tick.
 * - **Accessibility**: the slider's semantics, set by hand (the label, the 0..1 range, a set-progress action).
 */
@Composable
fun PlayerSeekBar(
    player    : PlayerStateManager,
    trackKey  : TrackKey?,
    durationMs: Long,
    isPlaying : Boolean,
    tint      : Color,
    modifier  : Modifier = Modifier,
) {
    val haptics      = LocalHapticFeedback.current
    val density      = LocalDensity.current
    val positionFlow = remember(player) { player.position() }
    val position     by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)

    var dragging     by remember { mutableStateOf(false) }
    var showReleased by remember { mutableStateOf(false) }
    var dragValue    by remember { mutableFloatStateOf(0f) }
    var lastNotch    by remember { mutableIntStateOf(-1) }
    var trackWidthPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(position, trackKey) { showReleased = false }

    val durationNow by rememberUpdatedState(durationMs)
    val enabled      = durationMs > 0L
    val showDrag     = dragging || showReleased
    val shownMs      = if (showDrag) (dragValue * durationMs).toLong() else position
    val shownFraction: () -> Float = { if (dragging || showReleased) dragValue else progressFraction(position, durationNow) }
    val waveAmplitude by animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0f,
        animationSpec = screenTransitionSpec(),
        label         = "seekWave",
    )
    val seekLabel   = stringResource(R.string.player_seek)
    val thumbPx     = with(density) { SeekThumbWidth.roundToPx() }
    val activeColor = if (enabled) tint else tint.copy(alpha = DISABLED_ALPHA)

    fun seekTo(fraction: Float) {
        dragValue    = fraction
        showReleased = true
        dragging     = false
        lastNotch    = -1
        player.seekTo((fraction * durationNow).toLong())
    }

    Column(modifier = modifier) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(SeekTouchHeight)
                .onSizeChanged { trackWidthPx = it.width }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down  = awaitFirstDown()
                        val width = size.width.toFloat()
                        val slop  = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (slop != null) {
                            val start = seekFractionAt(slop.position.x, width)
                            dragging  = true
                            dragValue = start
                            lastNotch = seekNotch(start, SEEK_NOTCH)
                            val completed = horizontalDrag(slop.id) { change ->
                                change.consume()
                                val value = seekFractionAt(change.position.x, width)
                                dragValue = value
                                val notch = seekNotch(value, SEEK_NOTCH)
                                if (notch != lastNotch) {
                                    lastNotch = notch
                                    haptics.tick()
                                }
                            }
                            if (completed) {
                                seekTo(dragValue)
                            } else {
                                dragging  = false
                                lastNotch = -1
                            }
                        } else {
                            // Released before the slop, and nobody else took the pointer: a tap.
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && up.changedToUp()) {
                                up.consume()
                                haptics.tick()
                                seekTo(seekFractionAt(up.position.x, width))
                            }
                        }
                    }
                }
                .clearAndSetSemantics {
                    contentDescription   = seekLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(shownFraction(), 0f..1f)
                    if (enabled) {
                        setProgress { target ->
                            seekTo(target.coerceIn(0f, 1f))
                            true
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            LinearWavyProgressIndicator(
                progress   = shownFraction,
                modifier   = Modifier.fillMaxWidth(),
                color      = activeColor,
                trackColor = tint.copy(alpha = 0.24f),
                // The thumb marks the junction: no gap, no stop dot (this is a seek bar, not a progress bar).
                gapSize    = 0.dp,
                stopSize   = 0.dp,
                amplitude  = { progress -> waveAmplitude * WavyProgressIndicatorDefaults.indicatorAmplitude(progress) },
                wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                // Travels while playing and while the wave is still flattening; 0 once flat (the indicator then
                // stops its own offset animation — read from the alpha29 bytecode).
                waveSpeed  = if (isPlaying || waveAmplitude > 0f) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(seekThumbOffset(shownFraction(), trackWidthPx, thumbPx), 0) }
                    .size(width = SeekThumbWidth, height = SeekThumbHeight)
                    .background(color = activeColor, shape = CircleShape),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text  = shownMs.toTimeString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = stringResource(R.string.player_time_remaining, remainingMs(shownMs, durationMs).toTimeString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shuffle · previous · play / pause · next · repeat. Previous is the player's own rule (back to the start after
 * 3 s, else the previous track — Media3's `seekToPrevious`), so the button just calls it. The play button sits on
 * the M3 Expressive cookie in the art's accent, which turns while a track plays ([PlayPauseButton]).
 */
@Composable
fun PlayerTransport(
    state         : PlayerState,
    player        : PlayerStateManager,
    tints         : PlayerTints,
    modifier      : Modifier = Modifier,
    playButtonSize: Dp = 64.dp,
) {
    val haptics  = LocalHapticFeedback.current
    val hasTrack = state.track != null
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        ModeToggle(
            on                 = state.shuffle,
            imageVector        = Icons.Default.Shuffle,
            contentDescription = stringResource(R.string.player_shuffle),
            stateDescription   = stringResource(if (state.shuffle) R.string.player_state_on else R.string.player_state_off),
            tint               = tints.surfaceAccent,
            enabled            = hasTrack,
            onClick            = {
                haptics.toggle(!state.shuffle)
                player.setShuffle(!state.shuffle)
            },
        )
        IconButton(
            onClick  = { haptics.press(); player.previous() },
            enabled  = hasTrack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.SkipPrevious, stringResource(R.string.player_previous), Modifier.size(36.dp))
        }
        PlayPauseButton(
            isPlaying = state.isPlaying,
            enabled   = hasTrack,
            tints     = tints,
            size      = playButtonSize,
            onClick   = { haptics.press(); player.playPause() },
        )
        IconButton(
            onClick  = { haptics.press(); player.next() },
            enabled  = hasTrack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.SkipNext, stringResource(R.string.player_next), Modifier.size(36.dp))
        }
        val repeatOn = state.repeat != RepeatMode.OFF
        ModeToggle(
            on                 = repeatOn,
            imageVector        = if (state.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = stringResource(R.string.player_repeat),
            stateDescription   = stringResource(state.repeat.descriptionRes()),
            tint               = tints.surfaceAccent,
            enabled            = hasTrack,
            onClick            = {
                val next = state.repeat.nextInCycle()
                when (next) {
                    RepeatMode.ALL -> haptics.toggle(true)
                    RepeatMode.OFF -> haptics.toggle(false)
                    RepeatMode.ONE -> haptics.press()
                }
                player.setRepeat(next)
            },
        )
    }
}

/**
 * Play / pause on the M3 Expressive 12-sided cookie (Cris, M1 item 50): the cookie TURNS slowly and continuously
 * while a track plays — one turn per [PLAY_SHAPE_TURN_MILLIS], linear — and stops where it is on pause (it keeps
 * its angle; the next play continues from there, no snap back). Only the shape turns: it is drawn behind the
 * button, rotated in the draw phase (no recomposition per frame), and the icon above it stays upright.
 *
 * This is the isPlaying-bound exception to the finite rule (CLAUDE.md rule 6): the loop runs inside
 * `LaunchedEffect(isPlaying)` and ends the moment playback pauses.
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    enabled  : Boolean,
    tints    : PlayerTints,
    size     : Dp,
    onClick  : () -> Unit,
) {
    val shape     = MaterialShapes.Cookie12Sided.toShape()
    val rotation  = remember { Animatable(0f) }
    val container = if (enabled) tints.accent else tints.accent.copy(alpha = DISABLED_ALPHA)

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            rotation.animateTo(rotation.value + 360f, tween(PLAY_SHAPE_TURN_MILLIS, easing = LinearEasing))
            // The same angle, one turn back: keeps the number small without a visible jump.
            rotation.snapTo(rotation.value - 360f)
        }
    }

    Box(
        modifier         = Modifier
            .size(size)
            .drawWithCache {
                val outline = shape.createOutline(this.size, layoutDirection, this)
                onDrawBehind {
                    rotate(rotation.value) { drawOutline(outline, container) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick  = onClick,
            enabled  = enabled,
            colors   = IconButtonDefaults.iconButtonColors(
                contentColor         = tints.onAccent,
                disabledContentColor = tints.onAccent.copy(alpha = DISABLED_ALPHA),
            ),
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                modifier           = Modifier.size(size / 2),
            )
        }
    }
}

/** The spoken state of each repeat mode. */
@StringRes
fun RepeatMode.descriptionRes(): Int = when (this) {
    RepeatMode.OFF -> R.string.player_state_off
    RepeatMode.ALL -> R.string.player_repeat_all
    RepeatMode.ONE -> R.string.player_repeat_one
}

/** A shuffle / repeat toggle: tinted with a dot under it while on (Lyra). */
@Composable
private fun ModeToggle(
    on                : Boolean,
    imageVector       : ImageVector,
    contentDescription: String,
    stateDescription  : String,
    tint              : Color,
    enabled           : Boolean,
    onClick           : () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick  = onClick,
            enabled  = enabled,
            colors   = IconButtonDefaults.iconButtonColors(
                contentColor = if (on) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.semantics { this.stateDescription = stateDescription },
        ) {
            Icon(imageVector = imageVector, contentDescription = contentDescription)
        }
        if (on) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-5).dp)
                    .size(5.dp)
                    .background(color = tint, shape = CircleShape),
            )
        }
    }
}

/**
 * The row under the transport: the reserved Output slot at the start (DESIGN §8.1 Q10 — the Google Cast route
 * button lands here after v1; empty until then) and the Queue button at the end.
 */
@Composable
fun PlayerActionRow(
    tint       : Color,
    onOpenQueue: () -> Unit,
    modifier   : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutputSlot(Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { haptics.press(); onOpenQueue() },
            colors  = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = tint.copy(alpha = 0.12f),
                contentColor   = tint,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = stringResource(R.string.queue_title))
        }
    }
}

/**
 * The Output slot (DESIGN §8.1 Q10): reserved for the Google Cast `MediaRouteButton`, a roadmap milestone after
 * v1. Empty until then; it keeps a button's height so the row does not reflow when the button arrives.
 */
@Composable
fun OutputSlot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.heightIn(min = 40.dp))
}
