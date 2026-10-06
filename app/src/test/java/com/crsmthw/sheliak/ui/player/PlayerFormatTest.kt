package com.crsmthw.sheliak.ui.player

import com.crsmthw.sheliak.domain.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerFormatTest {

    // ── progressFraction / remainingMs ──────────────────────────────────────

    @Test
    fun `progress is the position's share of the duration`() {
        assertEquals(0.5f, progressFraction(90_000L, 180_000L))
        assertEquals(0f, progressFraction(0L, 180_000L))
        assertEquals(1f, progressFraction(180_000L, 180_000L))
    }

    @Test
    fun `progress is zero while the duration is unknown`() {
        assertEquals(0f, progressFraction(5_000L, 0L))
        assertEquals(0f, progressFraction(5_000L, -1L))
    }

    @Test
    fun `progress is clamped to 0 to 1`() {
        assertEquals(1f, progressFraction(200_000L, 180_000L))
        assertEquals(0f, progressFraction(-100L, 180_000L))
    }

    @Test
    fun `remaining time is the rest of the track, never negative`() {
        assertEquals(150_000L, remainingMs(30_000L, 180_000L))
        assertEquals(0L, remainingMs(190_000L, 180_000L))
    }

    // ── repeat cycle ────────────────────────────────────────────────────────

    @Test
    fun `repeat cycles off, all, one, off`() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.nextInCycle())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.nextInCycle())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.nextInCycle())
    }
}
