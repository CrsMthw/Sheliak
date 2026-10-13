package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.ui.navigation.LocalNavSharedTransitionScope
import com.crsmthw.sheliak.util.artBoundsTransform
import kotlinx.coroutines.delay

/*
 * The player surfaces' motion and geometry constants, the `"album-art"` key and gate the host provides, and the
 * one `"album-art"` shared-element modifier. Every content swap here runs on util/Motion.kt's finite specs; the
 * only continuous motion is the isPlaying-bound pair (the wavy indicators' wave, the play button's turning
 * shape — CLAUDE.md rule 6), which stops the moment playback pauses (docs/MOTION.md).
 */

/**
 * The base of the shared-element key of the now-playing art: the mini bar, the pop-out card and the full player
 * register it, in the ONE navigation-scope `SharedTransitionScope` (`LocalNavSharedTransitionScope`). Inside the
 * shell the key carries a generation ([LocalPlayerArtKey], [albumArtKey]).
 */
const val ALBUM_ART_SHARED_KEY = "album-art"

/**
 * The key every `"album-art"` participant registers under: `PlayerSurfaceHost` provides `"album-art#<generation>"`
 * and advances the generation only at the first quiet moment after an ABANDONED seek (Lyra's workaround,
 * DESIGN §5.1 — see ArtKeyGeneration.kt). The bare key outside the host (a preview), where nothing is ever
 * re-keyed. Dynamic, so a new generation recomposes exactly the participants, which then all read the same value.
 */
val LocalPlayerArtKey: ProvidableCompositionLocal<Any> = compositionLocalOf { ALBUM_ART_SHARED_KEY }

/**
 * What the host tells the full player about its art, and what the full player tells the host back. ONE
 * instance, provided by `PlayerSurfaceHost` ([LocalPlayerArtGate]).
 *
 * @property fullPlayerMayMatch false while a fold swap replaces the full player with the card (the leaving full
 *   player's art must not fly into the card: the card is simply there); true again once that full player has
 *   left composition.
 * @property composedFullPlayers how many `PlayerScreen`s are composed — a leaving one counts until its exit
 *   transition ends. The mini bar's art may match a full player only while this, or the top entry, says one
 *   exists ([fullPlayerPresent]).
 */
@Stable
class PlayerArtGate {
    var fullPlayerMayMatch: Boolean by mutableStateOf(true)
    var composedFullPlayers: Int by mutableIntStateOf(0)
}

/** The host's [PlayerArtGate] (a throwaway default outside the host). */
val LocalPlayerArtGate = staticCompositionLocalOf { PlayerArtGate() }

/**
 * The art size every `"album-art"` participant — and the colour sampler — asks the provider for. One size, so
 * the three surfaces load ONE model (one cache entry), and the morph back down to the mini bar draws a sharp
 * bitmap instead of a 48dp decode blown up. The carousel's neighbours load at this size too, so the art that
 * slides into the centre is already the shared model.
 */
const val PLAYER_ART_SIZE_PX = 1024

/** The art size of a queue row (48dp). */
const val QUEUE_ART_SIZE_PX = 128

/** The pop-out panel's width, as a share of the window (Lyra's pop-out card). */
const val POP_OUT_PANEL_WIDTH_FRACTION = 0.54f

/** The pop-out panel's maximum height, as a share of the window height. */
const val POP_OUT_PANEL_MAX_HEIGHT_FRACTION = 0.8f

/** The scrim behind the pop-out panel, at full opacity of the transition (theme `scrim` at this alpha). */
const val PANEL_SCRIM_ALPHA = 0.45f

/** The full player's page tint: the art's edge colour at this alpha over the theme background. */
const val PAGE_TINT_ALPHA = 0.4f

/** The pop-out card's gradient top: the art's edge colour at this alpha over the card colour. */
const val CARD_TINT_ALPHA = 0.55f

/** How much of the previous / next art the centred hero carousel shows on each side (Material's small item minimum). */
val ArtCarouselPeek = 40.dp

/** The gap between the carousel's items. */
val ArtCarouselSpacing = 8.dp

/**
 * One full turn of the shape behind the full player's play / pause button while a track plays (Lyra's 8 s). The
 * turn is the isPlaying-bound exception to the finite rule: it runs only while playing and stops where it is on
 * pause.
 */
const val PLAY_SHAPE_TURN_MILLIS = 8_000

/** Disabled content and containers: Material's 38 % (a button with nothing loaded). */
const val DISABLED_ALPHA = 0.38f

/**
 * A `SharedContentConfig` that enables the shared element only while [enabled] — WITHOUT adding or removing the
 * modifier (Lyra's `rememberMatchWhenConfig`, DESIGN §5.1). The node stays attached for the participant's whole
 * life and only its `isEnabled` flips; the node observes that read, so the match is re-evaluated in the layout
 * pass. `shouldKeepEnabledForOngoingAnimation` keeps its default (true), so a flip mid-morph never strands it.
 */
@Composable
fun rememberArtMatchConfig(enabled: Boolean): SharedTransitionScope.SharedContentConfig {
    val enabledState = rememberUpdatedState(enabled)
    return remember {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedContentState.isEnabled: Boolean get() = enabledState.value
        }
    }
}

/**
 * Registers this art as the `"album-art"` shared element in the navigation scope, under [LocalPlayerArtKey],
 * riding [visibilityScope]'s enter / exit: the mini bar's and the pop-out card's own `AnimatedVisibility`
 * (children of the host's one seekable transition), or the full player's `LocalNavAnimatedContentScope`. Bounds
 * run on [artBoundsTransform] — the finite envelope — so the navigation transition settles on the frame the
 * morph ends.
 *
 * [matchEnabled] gates WHEN this registration may match ([rememberArtMatchConfig]); the modifier itself is
 * permanent for the caller's life. Each surface registers exactly once, in this one scope. A no-op outside the
 * shell (no scope) or without a visibility scope.
 */
@Composable
fun Modifier.albumArtSharedElement(
    visibilityScope: AnimatedVisibilityScope?,
    matchEnabled   : Boolean = true,
): Modifier {
    val sharedScope = LocalNavSharedTransitionScope.current
    if (sharedScope == null || visibilityScope == null) return this
    val key    = LocalPlayerArtKey.current
    val config = rememberArtMatchConfig(matchEnabled)
    return with(sharedScope) {
        this@albumArtSharedElement.sharedElement(
            sharedContentState      = rememberSharedContentState(key = key, config = config),
            animatedVisibilityScope = visibilityScope,
            boundsTransform         = artBoundsTransform(),
        )
    }
}

/**
 * The wavy indicators' binding to playback — the ONE isPlaying-bound motion besides the play button's turn
 * (CLAUDE.md rule 6): the mini bar's `CircularWavyProgressIndicator` and the seek bar's
 * `LinearWavyProgressIndicator` wave while a track plays and go flat on pause, the wave's phase never jumping.
 * Built on what Material 1.5.0-beta01 actually does (docs/MOTION.md, "The wave"; rules in WaveRules.kt):
 *
 * - **[amplitude] is a TARGET, not an animation.** Material evaluates the lambda in its draw cache and animates any
 *   change itself (its own 500 ms tween). The lambda reads [isPlaying] from a snapshot state, so a play / pause
 *   re-runs Material's cache and hands it the new step target (playing → the track's curve, paused → 0). It is ONE
 *   instance for the indicator's life. Feeding Material a per-frame animated value does NOT work: its first frame
 *   starts a 500 ms tween and every later value — the final 0 included — is dropped while that tween runs.
 * - **The re-statement**: Material also drops a target that changes while its previous tween still runs (a quick
 *   pause → play). [WAVE_RESAMPLE_MILLIS] after every change the lambda's [resample] read moves once, so Material
 *   re-runs it and picks up the current target after that tween has ended.
 * - **[linearSpeed]**: a speed of 0 resets the linear wave's phase to 0, which is invisible only while the wave is
 *   flat. So the linear wave keeps travelling while it flattens and stops [WAVE_STILL_MILLIS] after a pause, when it
 *   is certainly flat; a play restores the speed at once (the wave grows from flat, so no phase can be seen to
 *   jump). The circular indicator stops its own travel when its amplitude reaches 0 and resumes from the same
 *   phase, so it keeps one constant speed.
 */
@Stable
class PlaybackWave internal constructor(
    private val isPlaying: State<Boolean>,
    private val resample : IntState,
    private val still    : State<Boolean>,
) {
    /** Material's `amplitude` lambda: the step target for this point of the track ([waveAmplitudeTarget]). */
    val amplitude: (Float) -> Float = { progress ->
        // Read for its invalidation only: moving it re-runs Material's draw cache, which re-reads the target.
        resample.intValue
        waveAmplitudeTarget(isPlaying.value, WavyProgressIndicatorDefaults.indicatorAmplitude(progress))
    }

    /** The linear indicator's `waveSpeed`: one [wavelength] per second until a paused wave is certainly flat. */
    fun linearSpeed(wavelength: Dp): Dp = linearWaveSpeed(isPlaying.value, still.value, wavelength)
}

/**
 * The [PlaybackWave] for [isPlaying]. One finite timer per play / pause change (the re-statement, and for a pause
 * the linear stop), cancelled by the next change — never a loop or a poll.
 */
@Composable
fun rememberPlaybackWave(isPlaying: Boolean): PlaybackWave {
    val playing  = rememberUpdatedState(isPlaying)
    val resample = remember { mutableIntStateOf(0) }
    // Composed paused: Material creates its amplitude AT the target (0) on its first draw, so the wave is flat and
    // still from the start.
    val still    = remember { mutableStateOf(!isPlaying) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) still.value = false
        delay(WAVE_RESAMPLE_MILLIS.toLong())
        resample.intValue++
        if (!isPlaying) {
            delay((WAVE_STILL_MILLIS - WAVE_RESAMPLE_MILLIS).toLong())
            still.value = true
        }
    }
    return remember { PlaybackWave(isPlaying = playing, resample = resample, still = still) }
}
