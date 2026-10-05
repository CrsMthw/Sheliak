package com.crsmthw.sheliak.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionsTest {

    // ── toTimeString (millis → "m:ss", or "h:mm:ss" from one hour up) ─────────

    @Test
    fun `toTimeString at 0 ms returns 0 colon 00`() {
        assertEquals("0:00", 0L.toTimeString())
    }

    @Test
    fun `toTimeString at 59 seconds`() {
        assertEquals("0:59", 59_000L.toTimeString())
    }

    @Test
    fun `toTimeString at exactly 1 minute`() {
        assertEquals("1:00", 60_000L.toTimeString())
    }

    @Test
    fun `toTimeString at 1 minute 5 seconds pads seconds`() {
        assertEquals("1:05", 65_000L.toTimeString())
    }

    @Test
    fun `toTimeString at 10 minutes does not pad minutes`() {
        assertEquals("10:00", 600_000L.toTimeString())
    }

    @Test
    fun `toTimeString truncates sub-second portion`() {
        // 1500 ms = 1.5 s → 0:01
        assertEquals("0:01", 1_500L.toTimeString())
    }

    @Test
    fun `toTimeString just under one hour stays in minutes`() {
        // 3_599_999 ms = 59 min 59.999 s → truncated to 59:59, not rounded up into the hours branch
        assertEquals("59:59", 3_599_999L.toTimeString())
    }

    @Test
    fun `toTimeString at exactly 1 hour switches to h mm ss`() {
        assertEquals("1:00:00", 3_600_000L.toTimeString())
    }

    @Test
    fun `toTimeString at 1 hour 23 minutes 45 seconds`() {
        // 1*3600 + 23*60 + 45 = 5025 seconds
        assertEquals("1:23:45", 5_025_000L.toTimeString())
    }

    @Test
    fun `toTimeString pads minutes and seconds in the hours branch`() {
        // 1*3600 + 5*60 + 7 = 3907 seconds
        assertEquals("1:05:07", 3_907_000L.toTimeString())
    }

    @Test
    fun `toTimeString at 10 hours does not pad hours`() {
        assertEquals("10:00:00", 36_000_000L.toTimeString())
    }

    @Test
    fun `toTimeString clamps a negative value to zero`() {
        assertEquals("0:00", (-1_000L).toTimeString())
    }

    @Test
    fun `toTimeString clamps a large negative value to zero`() {
        assertEquals("0:00", (-7_200_000L).toTimeString())
    }

    @Test
    fun `toTimeString clamps Long MIN_VALUE without overflowing`() {
        assertEquals("0:00", Long.MIN_VALUE.toTimeString())
    }

    // ── toHoursMinutes (millis → whole hours + leftover whole minutes) ────────

    @Test
    fun `toHoursMinutes at 0 ms`() {
        assertEquals(HoursMinutes(0L, 0L), 0L.toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes at 30 seconds floors to 0 minutes`() {
        assertEquals(HoursMinutes(0L, 0L), 30_000L.toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes at exactly 1 minute`() {
        assertEquals(HoursMinutes(0L, 1L), 60_000L.toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes at 59 minutes`() {
        assertEquals(HoursMinutes(0L, 59L), (59 * 60_000L).toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes at 1 hour`() {
        assertEquals(HoursMinutes(1L, 0L), 3_600_000L.toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes at 2 hours 30 minutes`() {
        assertEquals(HoursMinutes(2L, 30L), (2 * 3_600_000L + 30 * 60_000L).toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes at 1 hour 1 minute`() {
        assertEquals(HoursMinutes(1L, 1L), (3_600_000L + 60_000L).toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes floors seconds away inside the hours range`() {
        // 1 h 1 min 59 s → 1 h 1 min
        assertEquals(HoursMinutes(1L, 1L), (3_600_000L + 119_000L).toHoursMinutes())
    }

    @Test
    fun `toHoursMinutes clamps a negative value to zero`() {
        assertEquals(HoursMinutes(0L, 0L), (-60_000L).toHoursMinutes())
    }
}
