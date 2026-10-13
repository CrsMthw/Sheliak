package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The wavy indicators' binding to playback (fix round 1b): the target, the re-statement, the linear stop. */
class WaveRulesTest {

    @Test
    fun `playing hands Material the track's own curve, paused hands it flat`() {
        assertEquals(1f, waveAmplitudeTarget(isPlaying = true, trackAmplitude = 1f))
        assertEquals(0f, waveAmplitudeTarget(isPlaying = true, trackAmplitude = 0f))
        assertEquals(0f, waveAmplitudeTarget(isPlaying = false, trackAmplitude = 1f))
        assertEquals(0f, waveAmplitudeTarget(isPlaying = false, trackAmplitude = 0f))
    }

    @Test
    fun `the target stays inside Material's range`() {
        assertEquals(1f, waveAmplitudeTarget(isPlaying = true, trackAmplitude = 1.5f))
        assertEquals(0f, waveAmplitudeTarget(isPlaying = true, trackAmplitude = -0.5f))
    }

    @Test
    fun `the linear wave travels while playing and while a paused wave may still be flattening`() {
        assertEquals(40.dp, linearWaveSpeed(isPlaying = true, still = false, wavelength = 40.dp))
        assertEquals(40.dp, linearWaveSpeed(isPlaying = true, still = true, wavelength = 40.dp))
        assertEquals(40.dp, linearWaveSpeed(isPlaying = false, still = false, wavelength = 40.dp))
    }

    @Test
    fun `the linear wave stops only once paused and certainly flat`() {
        assertEquals(0.dp, linearWaveSpeed(isPlaying = false, still = true, wavelength = 40.dp))
    }

    @Test
    fun `the target is stated again only after a Material tween that dropped it has ended`() {
        assertTrue(WAVE_RESAMPLE_MILLIS > MATERIAL_AMPLITUDE_TWEEN_MILLIS)
    }

    @Test
    fun `the linear wave stops only after the tween the re-statement may start has ended too`() {
        assertTrue(WAVE_STILL_MILLIS > WAVE_RESAMPLE_MILLIS + MATERIAL_AMPLITUDE_TWEEN_MILLIS)
        assertEquals(1300, WAVE_STILL_MILLIS)
    }
}
