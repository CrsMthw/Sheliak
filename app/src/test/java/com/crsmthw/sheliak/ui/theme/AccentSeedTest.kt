package com.crsmthw.sheliak.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccentSeedTest {

    private fun close(a: Float, b: Float, eps: Float) = abs(a - b) <= eps

    /** Hue distance on the circle (0 and 360 are the same hue). */
    private fun hueClose(a: Float, b: Float, eps: Float): Boolean {
        val d = abs(a - b) % 360f
        return minOf(d, 360f - d) <= eps
    }

    // ── Seed ↔ slider positions ──────────────────────────────────────────────

    @Test
    fun `seed round-trips through ARGB back to the same slider positions`() {
        for (hue in listOf(0f, 37f, 120f, 200f, 271f, 359f)) {
            for (sat in listOf(ACCENT_SAT_MIN, 0.5f, 0.66f, ACCENT_SAT_MAX)) {
                val back = accentHueSat(accentSeedColor(hue, sat).toArgb())
                assertTrue(hueClose(back.hue, hue, 1.5f), "hue $hue → ${back.hue}")
                assertTrue(close(back.saturation, sat, 0.01f), "sat $sat → ${back.saturation}")
            }
        }
    }

    @Test
    fun `seed is stored at the fixed seed lightness`() {
        val hsl = accentSeedColor(200f, 0.6f).toHsl()
        assertTrue(close(hsl.l, ACCENT_SEED_LIGHTNESS, 0.01f), "l=${hsl.l}")
    }

    @Test
    fun `seed accepts the range edges and clamps out-of-range inputs instead of throwing`() {
        // Color.hsl validates its arguments; these must not reach it unclamped.
        accentSeedColor(ACCENT_HUE_MAX, ACCENT_SAT_MAX)
        accentSeedColor(ACCENT_HUE_MIN, ACCENT_SAT_MIN)
        val low = accentSeedColor(-10f, 0f).toHsl()
        val high = accentSeedColor(400f, 1f).toHsl()
        assertTrue(close(low.s, ACCENT_SAT_MIN, 0.01f), "low s=${low.s}")
        assertTrue(close(high.s, ACCENT_SAT_MAX, 0.01f), "high s=${high.s}")
    }

    @Test
    fun `a grey stored colour maps to hue 0 and the minimum saturation`() {
        val hs = accentHueSat(Color(0xFF808080).toArgb())
        assertEquals(0f, hs.hue)
        assertEquals(ACCENT_SAT_MIN, hs.saturation)
    }

    @Test
    fun `presets outside the saturation window come back clamped`() {
        val blueGrey = accentHueSat(0xFF90A4AE.toInt())   // s ≈ 0.16
        val orange = accentHueSat(0xFFFB8C00.toInt())     // s = 1.0
        assertEquals(ACCENT_SAT_MIN, blueGrey.saturation)
        assertEquals(ACCENT_SAT_MAX, orange.saturation)
        assertTrue(orange.hue in ACCENT_HUE_MIN..ACCENT_HUE_MAX && blueGrey.hue in ACCENT_HUE_MIN..ACCENT_HUE_MAX)
    }

    @Test
    fun `a hue released at the right end is stored as hue 0`() {
        // Why the echo must never re-seed the Hue slider: 360 and 0 are one colour, and toHsl reads 0.
        val argb = accentSeedColor(360f, 0.6f).toArgb()
        assertEquals(argb, accentSeedColor(0f, 0.6f).toArgb())
        assertTrue(accentHueSat(argb).hue < 1f)
    }

    // ── Presets ──────────────────────────────────────────────────────────────

    @Test
    fun `ten distinct presets, the default Purple first`() {
        assertEquals(10, AccentPresets.size)
        assertEquals(ACCENT_DEFAULT_ARGB, AccentPresets.first().argb)
        assertEquals(10, AccentPresets.map { it.argb }.toSet().size)
        assertEquals(10, AccentPresets.map { it.nameRes }.toSet().size)
    }

    @Test
    fun `the presets are exactly the agreed colours in the agreed order`() {
        val expected = listOf(
            0xFF8B6BD1, // Purple (default)
            0xFF43A047, // Green — neutral Material green
            0xFFE53935, // Red
            0xFFFB8C00, // Orange
            0xFFFDD835, // Yellow
            0xFF00897B, // Teal
            0xFF1E88E5, // Blue
            0xFFD81B60, // Pink
            0xFF8D6E63, // Brown
            0xFF90A4AE, // Blue grey
        ).map { it.toInt() }
        assertEquals(expected, AccentPresets.map { it.argb })
    }

    // ── AccentOwnWrites: the picker must not re-seed its sliders from its own write's echo ──

    @Test
    fun `an own write's echo is recognised, an outside value is not`() {
        val own = AccentOwnWrites()
        own.record(1)
        assertTrue(own.isEcho(1))
        assertFalse(own.isEcho(1), "consumed: the same value again is an outside change")
        assertFalse(own.isEcho(2))
    }

    @Test
    fun `two quick writes arriving one by one are both echoes`() {
        val own = AccentOwnWrites()
        own.record(10); own.record(20)
        assertTrue(own.isEcho(10))
        assertTrue(own.isEcho(20))
    }

    @Test
    fun `a conflated older write is dropped with the newer echo`() {
        val own = AccentOwnWrites()
        own.record(10); own.record(20)
        assertTrue(own.isEcho(20))
        assertFalse(own.isEcho(10), "10 was superseded; seeing it now is an outside change")
    }

    @Test
    fun `a repeated value arriving in order is recognised every time`() {
        val own = AccentOwnWrites()
        own.record(10); own.record(20); own.record(10)
        assertTrue(own.isEcho(10))
        assertTrue(own.isEcho(20))
        assertTrue(own.isEcho(10))
    }

    @Test
    fun `an outside change clears what was in flight`() {
        val own = AccentOwnWrites()
        own.record(10)
        assertFalse(own.isEcho(99))
        assertFalse(own.isEcho(10))
    }

    @Test
    fun `expected is the newest in-flight write, else the stored value`() {
        val own = AccentOwnWrites()
        assertEquals(7, own.expected(7))
        own.record(12); own.record(120)
        assertEquals(120, own.expected(7), "the store will hold 120 once both land")
        assertTrue(own.isEcho(12))
        assertEquals(120, own.expected(12))
        assertTrue(own.isEcho(120))
        assertEquals(120, own.expected(120))
        own.record(60)
        assertFalse(own.isEcho(99), "an outside change")
        assertEquals(99, own.expected(99), "clears the in-flight writes")
    }

    // ── toHsl ────────────────────────────────────────────────────────────────

    @Test
    fun `toHsl reads the primaries, secondaries and greys exactly`() {
        fun check(argb: Long, h: Float, s: Float, l: Float) {
            val hsl = Color(argb).toHsl()
            assertTrue(hueClose(hsl.h, h, 0.01f) && close(hsl.s, s, 0.01f) && close(hsl.l, l, 0.01f), "$hsl for ${argb.toString(16)}")
        }
        check(0xFFFF0000, 0f, 1f, 0.5f)
        check(0xFFFFFF00, 60f, 1f, 0.5f)
        check(0xFF00FF00, 120f, 1f, 0.5f)
        check(0xFF00FFFF, 180f, 1f, 0.5f)
        check(0xFF0000FF, 240f, 1f, 0.5f)
        check(0xFFFF00FF, 300f, 1f, 0.5f)
        check(0xFFFFFFFF, 0f, 0f, 1f)
        check(0xFF000000, 0f, 0f, 0f)
        check(0xFF808080, 0f, 0f, 128f / 255f)
    }

    @Test
    fun `toHsl inverts Color hsl across the wheel`() {
        // Tolerances cover the 8-bit quantisation Color stores sRGB at.
        for (h in (0 until 360 step 15).map { it.toFloat() }) {
            for (s in listOf(0.35f, 0.6f, 0.9f)) {
                for (l in listOf(0.3f, 0.5f, 0.7f)) {
                    val back = Color.hsl(h, s, l).toHsl()
                    assertTrue(hueClose(back.h, h, 1.5f), "h $h,$s,$l → ${back.h}")
                    assertTrue(close(back.s, s, 0.02f), "s $h,$s,$l → ${back.s}")
                    assertTrue(close(back.l, l, 0.01f), "l $h,$s,$l → ${back.l}")
                }
            }
        }
    }

    @Test
    fun `toHsl hue is always in 0 until 360`() {
        // The magenta-to-red sector computes a negative hue first; it must wrap, not leak out of range.
        val hsl = Color(0xFFFF0080).toHsl()
        assertTrue(hsl.h >= 0f && hsl.h < 360f, "h=${hsl.h}")
        assertTrue(hueClose(hsl.h, 330f, 0.5f), "h=${hsl.h}")
    }
}
