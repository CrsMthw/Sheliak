@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.crsmthw.sheliak.ui.player

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.components.Artwork
import com.crsmthw.sheliak.ui.components.ValueSlider
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec
import com.crsmthw.sheliak.util.tick
import com.crsmthw.sheliak.util.toTimeString
import com.crsmthw.sheliak.util.toggle
import kotlinx.coroutines.launch
import kotlin.math.sign

/*
 * The pieces the full player (ui/screens/player/PlayerScreen) and the pop-out card (PlayerCardContent) share:
 * the swipeable art, the title block, the seek bar, the transport row and the action row with the reserved
 * Output slot. Lyra's PlayerControls anatomy, rebuilt on PlayerStateManager: every command is a
 * PlaybackController call and every value comes from PlayerState or the position ticker.
 */

/** Remembers the last value of a track change, outside snapshot state (only read and written by one effect). */
private class ArtSlideMemory(var key: TrackKey?, var queueIndex: Int)

/**
 * The now-playing art: the `"album-art"` shared element ([visibilityScope] decides which surface it rides),
 * swipeable to skip, and sliding in REACTIVELY when the track changes — from the end for a later queue row,
 * from the start for an earlier one. Nothing moves pre-emptively on a button press (DESIGN §8.1 Q12): the
 * slide starts when the player reports the new track, on the one finite spec.
 *
 * The shared-element registration comes first in the chain, before the [size] and the graphics-layer
 * translations, so the slide and the swipe never disturb the morph's bounds (Lyra's order).
 */
@Composable
fun SwipeableAlbumArt(
    track          : Track?,
    queueIndex     : Int,
    visibilityScope: AnimatedVisibilityScope?,
    size           : Dp,
    onNext         : () -> Unit,
    onPrevious     : () -> Unit,
    modifier       : Modifier = Modifier,
    cornerRadius   : Dp = 16.dp,
) {
    val density    = LocalDensity.current
    val haptics    = LocalHapticFeedback.current
    val scope      = rememberCoroutineScope()
    val nextAction = rememberUpdatedState(onNext)
    val prevAction = rememberUpdatedState(onPrevious)
    val thresholdPx = with(density) { ArtSwipeThreshold.toPx() }
    val maxTravelPx = with(density) { ArtSwipeMaxTravel.toPx() }
    // The slide starts just off the window's edge, so the new art travels in from outside the screen.
    val slideInPx  = LocalWindowInfo.current.containerSize.width.toFloat()

    val slide  = remember { Animatable(0f) }
    var dragX  by remember { mutableFloatStateOf(0f) }
    val memory = remember { ArtSlideMemory(track?.key, queueIndex) }

    LaunchedEffect(track?.key) {
        val newKey = track?.key
        val oldKey = memory.key
        val direction = (queueIndex - memory.queueIndex).sign.toFloat().takeIf { it != 0f } ?: 1f
        memory.key = newKey
        memory.queueIndex = queueIndex
        if (newKey != null && oldKey != null && newKey != oldKey) {
            slide.snapTo(direction * slideInPx)
            slide.animateTo(0f, screenTransitionSpec())
        }
    }

    val albumArtLabel = stringResource(R.string.player_album_art)
    Artwork(
        art                = track?.art,
        sizePx             = PLAYER_ART_SIZE_PX,
        contentDescription = track?.albumTitle?.takeIf { it.isNotBlank() } ?: albumArtLabel,
        shape              = RoundedCornerShape(cornerRadius),
        modifier           = modifier
            .albumArtSharedElement(visibilityScope)
            .size(size)
            .graphicsLayer {
                translationX = (dragX * ART_SWIPE_FOLLOW).coerceIn(-maxTravelPx, maxTravelPx) + slide.value
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart      = { dragX = 0f },
                    onDragEnd        = {
                        when {
                            dragX < -thresholdPx -> { haptics.press(); nextAction.value() }
                            dragX > thresholdPx  -> { haptics.press(); prevAction.value() }
                        }
                        val from = dragX
                        scope.launch {
                            animate(from, 0f, animationSpec = screenTransitionSpec()) { value, _ -> dragX = value }
                        }
                    },
                    onDragCancel     = {
                        val from = dragX
                        scope.launch {
                            animate(from, 0f, animationSpec = screenTransitionSpec()) { value, _ -> dragX = value }
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                    },
                )
            },
    )
}

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

/**
 * The seek bar with elapsed / remaining time. The position ticker ([PlayerStateManager.position]) is collected
 * HERE, so only this block recomposes on a tick and the ticker runs only while a seek bar is on screen. A drag
 * shows the finger's value and ticks every 5 %. On release it seeks, and the slider keeps the released value
 * until the ticker's NEXT report — the controller masks a seek at once, so that report is already the new
 * position; without it the thumb would flick back to the old position for up to one tick. One tick, no
 * tolerance, no timer.
 */
@Composable
fun PlayerSeekBar(
    player    : PlayerStateManager,
    trackKey  : TrackKey?,
    durationMs: Long,
    tint      : Color,
    modifier  : Modifier = Modifier,
) {
    val haptics      = LocalHapticFeedback.current
    val positionFlow = remember(player) { player.position() }
    val position     by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)

    var dragging     by remember { mutableStateOf(false) }
    var showReleased by remember { mutableStateOf(false) }
    var dragValue    by remember { mutableFloatStateOf(0f) }
    var lastNotch    by remember { mutableIntStateOf(-1) }

    LaunchedEffect(position, trackKey) { showReleased = false }

    val showDrag = dragging || showReleased
    val shownMs  = if (showDrag) (dragValue * durationMs).toLong() else position
    val seekLabel = stringResource(R.string.player_seek)

    Column(modifier = modifier) {
        ValueSlider(
            value                 = if (showDrag) dragValue else progressFraction(position, durationMs),
            onValueChange         = { value ->
                dragging  = true
                dragValue = value
                val notch = (value / SEEK_NOTCH).toInt()
                if (notch != lastNotch) {
                    lastNotch = notch
                    haptics.tick()
                }
            },
            onValueChangeFinished = {
                player.seekTo((dragValue * durationMs).toLong())
                showReleased = true
                dragging     = false
                lastNotch    = -1
            },
            enabled               = durationMs > 0L,
            colors                = SliderDefaults.colors(
                thumbColor         = tint,
                activeTrackColor   = tint,
                inactiveTrackColor = tint.copy(alpha = 0.24f),
            ),
            modifier              = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = seekLabel },
        )
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

/** The seek bar's haptic notch: one tick per 5 % of the track while dragging (Lyra). */
private const val SEEK_NOTCH = 0.05f

/**
 * Shuffle · previous · play / pause · next · repeat. Previous is the player's own rule (back to the start after
 * 3 s, else the previous track — Media3's `seekToPrevious`), so the button just calls it. The play button is
 * the M3 Expressive cookie in the art's accent, never rotating (every motion here is finite).
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
        FilledIconButton(
            onClick  = { haptics.press(); player.playPause() },
            enabled  = hasTrack,
            shape    = MaterialShapes.Cookie12Sided.toShape(),
            colors   = IconButtonDefaults.filledIconButtonColors(
                containerColor = tints.accent,
                contentColor   = tints.onAccent,
            ),
            modifier = Modifier.size(playButtonSize),
        ) {
            Icon(
                imageVector        = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                modifier           = Modifier.size(playButtonSize / 2),
            )
        }
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
