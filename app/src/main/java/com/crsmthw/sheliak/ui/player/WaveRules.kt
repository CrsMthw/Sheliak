package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The pure rules behind the wavy indicators' binding to playback (rememberPlaybackWave in PlayerMotion.kt — the
 * mini bar's ring, the full player's and the card's seek bar): what amplitude Material is asked for, when that
 * target is stated again, and when the linear wave stops travelling. No Compose runtime — unit-tested in
 * WaveRulesTest. Why it is shaped like this, read from Material 1.5.0-beta01: docs/MOTION.md, "The wave".
 *
 * In short: Material ANIMATES the amplitude itself — the `amplitude` lambda is a target, every change of it runs
 * Material's own 500 ms tween, and a target that changes while that tween runs is DROPPED (until something makes
 * the indicator rebuild its draw cache after the tween). And a wave speed of 0 RESETS the wave's phase to 0. So the
 * app hands Material the step target (playing → the track's curve, paused → 0), states it once more after a tween
 * could have swallowed it, and lets the linear wave travel until it is certainly flat before stopping it.
 */

/**
 * Material's own amplitude tween, in ms: 1.5.0-beta01 `WavyProgressIndicatorKt.IncreasingAmplitudeAnimationSpec`
 * and `DecreasingAmplitudeAnimationSpec` are both `tween(500)`. Every change of the amplitude TARGET runs one of
 * them, and a target that arrives while one runs is dropped.
 */
const val MATERIAL_AMPLITUDE_TWEEN_MILLIS = 500

/** Headroom past one Material tween: the frame it starts on after the change, and the frame it ends on. */
const val WAVE_SETTLE_MARGIN_MILLIS = 150

/**
 * How long after a play / pause change the amplitude target is stated again: once any Material tween that was
 * already running at the change — and so dropped the new target — has certainly ended. A quick pause → play (or
 * play → pause) would otherwise leave the wave at the target it had before, until the next progress tick (none
 * while paused).
 */
const val WAVE_RESAMPLE_MILLIS: Int = MATERIAL_AMPLITUDE_TWEEN_MILLIS + WAVE_SETTLE_MARGIN_MILLIS

/**
 * How long after a pause the LINEAR wave stops travelling: after the tween the re-statement may itself start has
 * ended too, so the amplitude Material draws is exactly 0 — and at 0 the linear indicator draws no phase at all, so
 * the reset to phase 0 that a speed of 0 causes is invisible. (The circular indicator stops its own travel when its
 * amplitude tween ends at 0, so it never needs this.)
 */
const val WAVE_STILL_MILLIS: Int = WAVE_RESAMPLE_MILLIS + MATERIAL_AMPLITUDE_TWEEN_MILLIS + WAVE_SETTLE_MARGIN_MILLIS

/**
 * The amplitude TARGET handed to Material for a point of the track: the track's own curve while playing
 * ([trackAmplitude] — Material's `indicatorAmplitude`, flat in the first 10 % and the last 5 %), flat while paused.
 * Material animates between targets itself.
 */
fun waveAmplitudeTarget(isPlaying: Boolean, trackAmplitude: Float): Float =
    if (isPlaying) trackAmplitude.coerceIn(0f, 1f) else 0f

/**
 * The linear wave's travel speed: one [wavelength] per second while playing and while a paused wave may still be
 * flattening; 0 only once it is [still] (paused, and certainly flat — [WAVE_STILL_MILLIS]). Playing always travels,
 * whatever [still] says, so a play never waits for the flag.
 */
fun linearWaveSpeed(isPlaying: Boolean, still: Boolean, wavelength: Dp): Dp =
    if (isPlaying || !still) wavelength else 0.dp
