package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.graphics.Color

/*
 * The pure arithmetic of the album-art colour recipe (AlbumArtColor.kt, PlayerColors.kt): the border-ring
 * average and the darkness test. No android.* — unit-tested in ArtColorMathTest.
 */

/** Below this average chroma (0..255) the border ring counts as grey and is averaged plainly. */
private const val MIN_AVG_CHROMA = 10L

/**
 * The border-ring colour of a [width] × [height] image whose ARGB pixels [pixelAt] returns — Lyra's
 * `perimeterColor`, unchanged, with the pixel read passed in so the arithmetic is JVM-testable.
 *
 * Border pixels are averaged **weighted by chroma** (saturation): a colourful minority on the edge counts far
 * more than near-grey / black pixels, so the result follows the hue actually present instead of being dragged
 * to mud by a desaturated majority. When the border is essentially grey (average chroma below
 * [MIN_AVG_CHROMA]) there is nothing to latch onto, so it falls back to a plain average. Returns an opaque ARGB
 * int.
 *
 * @param insetFraction thickness of the sampled ring, as a fraction of the shorter side.
 * @param step pixel stride; higher = fewer samples = faster.
 */
fun perimeterColorOf(
    width        : Int,
    height       : Int,
    insetFraction: Float,
    step         : Int,
    pixelAt      : (x: Int, y: Int) -> Int,
): Int {
    val w = width
    val h = height
    if (w < 4 || h < 4) return pixelAt(0, 0)
    val s = step.coerceAtLeast(1)
    val band = (minOf(w, h) * insetFraction).toInt().coerceIn(1, minOf(w, h) / 2)

    // Plain average (fallback for near-grey borders).
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L
    var chromaSum = 0L
    // Chroma-weighted average (primary): weight = saturation, so colourful pixels dominate.
    var wr = 0.0
    var wg = 0.0
    var wb = 0.0
    var wTot = 0.0

    fun sample(x: Int, y: Int) {
        val p = pixelAt(x, y)
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        rSum += r
        gSum += g
        bSum += b
        count++
        val chroma = maxOf(r, g, b) - minOf(r, g, b)   // 0..255
        chromaSum += chroma
        val weight = chroma.toDouble()
        wr += weight * r
        wg += weight * g
        wb += weight * b
        wTot += weight
    }

    // Top + bottom bands (full width, including corners).
    var y = 0
    while (y < band) {
        var x = 0
        while (x < w) {
            sample(x, y)
            sample(x, h - 1 - y)
            x += s
        }
        y += s
    }
    // Left + right bands (rows between the top/bottom bands, so corners aren't double-counted).
    var yy = band
    while (yy < h - band) {
        var x = 0
        while (x < band) {
            sample(x, yy)
            sample(w - 1 - x, yy)
            x += s
        }
        yy += s
    }

    if (count == 0L) return pixelAt(0, 0)

    return if (chromaSum / count >= MIN_AVG_CHROMA && wTot > 0.0) {
        (0xFF shl 24) or
            ((wr / wTot).toInt() shl 16) or
            ((wg / wTot).toInt() shl 8) or
            (wb / wTot).toInt()
    } else {
        (0xFF shl 24) or
            ((rSum / count).toInt() shl 16) or
            ((gSum / count).toInt() shl 8) or
            (bSum / count).toInt()
    }
}

/**
 * Lyra's darkness test, kept as is: the YIQ-weighted sum of the sRGB components below one half. Decides the
 * album-art recipe's theme branch (from the background) and which on-surface role reads on the accent.
 */
fun isDarkColor(color: Color): Boolean = luma(color) < 0.5f

/**
 * The two theme roles ordered (lighter, darker). The theme's `surface` / `onSurface` swap places between light
 * and dark mode, so content picked "light" or "dark" against an arbitrary art colour is still a theme role —
 * never a hardcoded white or black.
 */
fun lighterAndDarker(a: Color, b: Color): Pair<Color, Color> = if (luma(a) >= luma(b)) a to b else b to a

/** The YIQ luma of [c]'s sRGB components, 0..1. */
private fun luma(c: Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
