package com.crsmthw.sheliak.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.get
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/*
 * Lyra's album-art colour recipe (util/AlbumArtColor.kt), ported unchanged (DESIGN §4) except for three seams:
 * Lyra's fixed green fallback is the caller's (the theme's `primary`), the loader takes any Coil model (the
 * provider's `artModel`) instead of a URL string, and the border-ring average is split into a pure core
 * ([perimeterColorOf], ArtColorMath.kt) so it is unit-tested on the JVM.
 */

/**
 * The decode size the colours are computed from. Palette scales to ~112 × 112 anyway and the border ring is
 * sampled with a stride, so a small software decode is all the recipe needs.
 */
private const val COLOR_SAMPLE_SIZE_PX = 256

/**
 * Colours extracted from a piece of album art. All values are opaque ARGB ints; Compose callers wrap them
 * with `Color(...)`.
 */
data class AlbumArtColors(
    /**
     * Chroma-weighted average of the art's outer border ring (see [perimeterColorOf]). For any background that
     * should "merge" with the cover — the full player's page tint, the pop-out card's gradient, the mini bar's
     * wash — so the cover appears to dissolve into the surface instead of sitting on a contrasting accent.
     */
    val edge: Int,
    /** Palette Vibrant (→ dominant → the fallback). Punchy accent for highlights and the play button. */
    val accent: Int,
    /**
     * Theme-aware light-/dark-vibrant chain — an accent chosen to keep contrast against the current surface.
     * For foreground tints (the seek bar, shuffle / repeat, the quality chip, the mini bar's progress line) that
     * must stay legible, NOT for backgrounds.
     */
    val surfaceAccent: Int,
    /** Palette dominant (most populous colour). Kept for callers that specifically want it. */
    val dominant: Int,
)

/**
 * [AlbumArtColors] from an already-decoded **ARGB_8888** bitmap. MUST be a software bitmap — Palette and the
 * border sampling read pixels, which throws on HARDWARE bitmaps. Call off the main thread.
 *
 * @param isDark whether the active theme is dark — only affects [AlbumArtColors.surfaceAccent] (light accent
 *   on dark, dark accent on light).
 * @param fallback the colour every Palette lookup falls back to (the theme's `primary`).
 */
fun Bitmap.albumArtColors(isDark: Boolean, fallback: Int): AlbumArtColors {
    val palette = Palette.from(this).generate()
    val base    = palette.getDominantColor(fallback)
    val surface = if (isDark) {
        palette.getLightVibrantColor(palette.getVibrantColor(palette.getLightMutedColor(base)))
    } else {
        palette.getDarkVibrantColor(palette.getVibrantColor(palette.getDarkMutedColor(base)))
    }
    return AlbumArtColors(
        edge          = perimeterColor(),
        accent        = palette.getVibrantColor(base),
        surfaceAccent = surface,
        dominant      = palette.getDominantColor(base),
    )
}

/**
 * Loads [model] (a provider's `artModel`, exactly what `Artwork` shows) through the app's one Coil loader as a
 * small SOFTWARE bitmap and computes its [AlbumArtColors]. Null for a null model or a failed load. Safe to call
 * straight from a `LaunchedEffect`: the decode runs on Coil's own threads, the pixel work on
 * [Dispatchers.Default]. Cancellation is never swallowed.
 */
suspend fun loadAlbumArtColors(context: Context, model: Any?, isDark: Boolean, fallback: Int): AlbumArtColors? {
    if (model == null || (model is String && model.isBlank())) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(COLOR_SAMPLE_SIZE_PX)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val software = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
                           else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            software.albumArtColors(isDark, fallback)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/**
 * Representative colour of the art's outer border ring — the band of pixels that visually touches the
 * surface around it. See [perimeterColorOf] for the recipe. MUST be called on a software bitmap. Cheap:
 * strided sampling of only the border band.
 */
fun Bitmap.perimeterColor(insetFraction: Float = 0.06f, step: Int = 4): Int =
    perimeterColorOf(width, height, insetFraction, step) { x, y -> this[x, y] }
