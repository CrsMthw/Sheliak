package com.crsmthw.sheliak.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.crsmthw.sheliak.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The accent picker's model (Settings → Theme, shown while Material You is off): ten preset swatches plus ONE Hue
 * slider, both producing ONE seed colour persisted as `accent_color` (ARGB) and turned into the full scheme by
 * SeedColorScheme. Pure Kotlin (no android.*), unit-tested in AccentSeedTest.
 */

/** Purple, the launcher icon's background: switching Material You off lands on the icon's own colour. */
const val ACCENT_DEFAULT_ARGB: Int = 0xFF8B6BD1.toInt()

/** The Hue slider's range in degrees; both ends are the same red. */
const val ACCENT_HUE_MIN: Float = 0f
const val ACCENT_HUE_MAX: Float = 360f

/**
 * The saturation and lightness every hue-slider seed is stored at — the default Purple's own, so a slider seed at
 * Purple's hue IS the Purple swatch (same dot, same scheme). Neither is a user control: Material's Tonal Spot
 * scheme takes only the seed's hue and fixes each palette's chroma and every role's tone itself, so a saturation
 * or lightness slider would move the swatch dot without changing the app.
 */
val ACCENT_SEED_SATURATION: Float = Color(ACCENT_DEFAULT_ARGB).toHsl().s
val ACCENT_SEED_LIGHTNESS: Float = Color(ACCENT_DEFAULT_ARGB).toHsl().l

/** One preset swatch: its ARGB and the colour name TalkBack reads. */
data class AccentPreset(val argb: Int, @param:StringRes val nameRes: Int)

/** The ten presets, the default Purple first (it is also the picker's initial selection). */
val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset(ACCENT_DEFAULT_ARGB, R.string.theme_accent_purple),
    AccentPreset(0xFF43A047.toInt(),  R.string.theme_accent_green),
    AccentPreset(0xFFE53935.toInt(),  R.string.theme_accent_red),
    AccentPreset(0xFFFB8C00.toInt(),  R.string.theme_accent_orange),
    AccentPreset(0xFFFDD835.toInt(),  R.string.theme_accent_yellow),
    AccentPreset(0xFF00897B.toInt(),  R.string.theme_accent_teal),
    AccentPreset(0xFF1E88E5.toInt(),  R.string.theme_accent_blue),
    AccentPreset(0xFFD81B60.toInt(),  R.string.theme_accent_pink),
    AccentPreset(0xFF8D6E63.toInt(),  R.string.theme_accent_brown),
    AccentPreset(0xFF90A4AE.toInt(),  R.string.theme_accent_blue_grey),
)

/** Hue slider position → the seed. The hue is clamped first, because `Color.hsl` throws outside its range. */
fun accentSeedColor(hue: Float): Color = Color.hsl(
    hue        = hue.coerceIn(ACCENT_HUE_MIN, ACCENT_HUE_MAX),
    saturation = ACCENT_SEED_SATURATION,
    lightness  = ACCENT_SEED_LIGHTNESS,
)

/** A stored accent (ARGB) → the Hue slider position; a grey (no hue) reads 0. */
fun accentHue(argb: Int): Float = Color(argb).toHsl().h.coerceIn(ACCENT_HUE_MIN, ACCENT_HUE_MAX)

/**
 * The accent values the picker has itself sent to DataStore and not yet seen come back.
 *
 * The Hue slider must NOT be re-seeded from the picker's own write landing: the stored ARGB reads back through
 * [toHsl], whose hue is in [0, 360) — a Hue released at the right end (exactly 360) would come back as 0 and
 * snap the thumb across the track — and a drag begun before the echo lands would be reset under the finger.
 * So every write (a slider release AND a swatch tap, which re-seeds the slider itself at the tap) is
 * [record]ed, and an emission counts as an outside change only when [isEcho] says it is not one of ours.
 *
 * A queue rather than a single "last written" value: two quick writes (a Hue release, then a swatch tap) send A
 * then B, and if A's emission arrives on its own it must still count as ours.
 *
 * Not snapshot state — it is read and written only from callbacks and effects.
 */
class AccentOwnWrites {
    private val pending = ArrayDeque<Int>()

    /** Call BEFORE handing [argb] to the persister. */
    fun record(argb: Int) {
        pending.addLast(argb)
    }

    /**
     * Whether a persisted [argb] is the echo of one of our writes. The OLDEST matching write is consumed
     * together with every write older than it (those were conflated away, or never emitted because they equalled
     * the stored value) — oldest, so writes A, B, A arriving in order are all recognised. A miss is an outside
     * change, which supersedes everything we had in flight, so the queue is cleared.
     */
    fun isEcho(argb: Int): Boolean {
        val at = pending.indexOf(argb)
        return if (at >= 0) {
            repeat(at + 1) { pending.removeFirst() }
            true
        } else {
            pending.clear()
            false
        }
    }

    /**
     * What the store will hold once our in-flight writes land: the newest pending write, else [stored]. A
     * composed `stored` lags a write by one echo, so "is this new?" asks this instead.
     */
    fun expected(stored: Int): Int = pending.lastOrNull() ?: stored
}

/** HSL triple: hue 0..360, saturation 0..1, lightness 0..1. */
data class Hsl(val h: Float, val s: Float, val l: Float)

/**
 * sRGB → HSL, the textbook conversion. Compose has `Color.hsl` for the other direction but no inverse, and this
 * stays pure Kotlin so it runs in JVM tests. Hue is in [0, 360); a grey (no chroma) reports hue 0, saturation 0.
 */
fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d < 1e-5f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        r    -> 60f * (((g - b) / d) % 6f)
        g    -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return Hsl(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
}
