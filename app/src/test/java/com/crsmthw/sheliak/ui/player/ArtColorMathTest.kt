package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtColorMathTest {

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    // ── perimeterColorOf ────────────────────────────────────────────────────

    @Test
    fun `a uniform image's border is its colour`() {
        val teal = argb(0, 128, 128)
        assertEquals(teal, perimeterColorOf(100, 100, 0.06f, 4) { _, _ -> teal })
    }

    @Test
    fun `the result is opaque`() {
        val translucent = (0x40 shl 24) or (200 shl 16) or (10 shl 8) or 10
        val result = perimeterColorOf(64, 64, 0.06f, 4) { _, _ -> translucent }
        assertEquals(0xFF, (result ushr 24) and 0xFF)
    }

    @Test
    fun `only the border ring is sampled`() {
        // A red centre inside a blue frame: the result is the frame's blue, untouched by the centre.
        val blue = argb(0, 0, 255)
        val red = argb(255, 0, 0)
        val result = perimeterColorOf(100, 100, 0.06f, 1) { x, y ->
            if (x in 10..89 && y in 10..89) red else blue
        }
        assertEquals(blue, result)
    }

    @Test
    fun `a colourful minority outweighs a grey majority on the border`() {
        // Every 4th border pixel is saturated green, the rest near-black grey: the chroma weighting follows the green.
        val green = argb(0, 200, 0)
        val grey = argb(20, 20, 20)
        val result = perimeterColorOf(80, 80, 0.06f, 1) { x, _ -> if (x % 4 == 0) green else grey }
        assertTrue(green(result) > 150, "green channel ${green(result)}")
        assertTrue(red(result) < 30 && blue(result) < 30)
    }

    @Test
    fun `a grey border is averaged plainly`() {
        // Chroma is zero everywhere, so the weighted average has nothing to latch onto.
        val result = perimeterColorOf(40, 40, 0.1f, 1) { x, _ -> if (x % 2 == 0) argb(100, 100, 100) else argb(200, 200, 200) }
        assertEquals(red(result), green(result))
        assertEquals(green(result), blue(result))
        assertTrue(red(result) in 140..160, "plain average ${red(result)}")
    }

    @Test
    fun `tiny images return their first pixel`() {
        assertEquals(argb(1, 2, 3), perimeterColorOf(3, 3, 0.06f, 4) { x, y -> if (x == 0 && y == 0) argb(1, 2, 3) else 0 })
    }

    // ── isDarkColor / lighterAndDarker ──────────────────────────────────────

    @Test
    fun `dark and light colours are told apart`() {
        assertTrue(isDarkColor(Color(0xFF000000)))
        assertTrue(isDarkColor(Color(0xFF1D1B20)))
        assertFalse(isDarkColor(Color(0xFFFFFFFF)))
        assertFalse(isDarkColor(Color(0xFFFEF7FF)))
    }

    @Test
    fun `saturated colours are judged by their luma`() {
        assertTrue(isDarkColor(Color(0xFF0000FF)))    // pure blue: luma 0.11
        assertFalse(isDarkColor(Color(0xFF00FF00)))   // pure green: luma 0.59
    }

    @Test
    fun `the lighter of two roles comes first, whichever order they are given in`() {
        val surface = Color(0xFF141218)
        val onSurface = Color(0xFFE6E0E9)
        assertEquals(onSurface to surface, lighterAndDarker(surface, onSurface))
        assertEquals(onSurface to surface, lighterAndDarker(onSurface, surface))
    }
}
