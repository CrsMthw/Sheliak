package com.crsmthw.sheliak.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.crsmthw.sheliak.R

/**
 * The ONE idiom for "keep this screen's content clear of a system bar that sits on a SIDE edge".
 *
 * With 3-button navigation in landscape the navigation bar moves to the left **or** the right edge
 * (it follows the rotation direction), and `enableEdgeToEdge()` means the app owns that inset. A
 * screen that only handles top/bottom insets runs its rows, lists and floating controls underneath
 * the nav buttons on whichever edge they happen to be. `union(displayCutout)` additionally keeps
 * content off a hole-punch camera, which lands on a side edge in landscape too.
 *
 * **Apply it ONCE, on a screen's outermost content container** (the `Box`/`Column` inside the
 * `Scaffold` body), so every child — lists, `contentPadding`-inset rows and floating controls alike —
 * clears the bar without each of them repeating the inset. Never combine it with
 * `navigationBarsPadding()` on the SAME element: that modifier already carries the horizontal sides,
 * so the pair double-pads. Elements that need the bottom inset as well keep plain
 * `navigationBarsPadding()` instead of this.
 *
 * Full-bleed decoration (top/bottom scrims) is fine either inside or outside the padded container —
 * it is drawn, never touched or read.
 */
@Composable
fun Modifier.horizontalSystemBarsPadding(): Modifier = this.windowInsetsPadding(
    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
)

/**
 * Clickable that ignores a second tap inside [debounceMs] of the first, so a double tap on a
 * navigating control (a back arrow, a row that pushes a screen) cannot fire the action twice and pop
 * or push two entries.
 */
@Composable
fun Modifier.debouncedClickable(
    debounceMs       : Long    = 400L,
    enabled          : Boolean = true,
    role             : Role?   = null,
    onClick          : () -> Unit,
): Modifier {
    var lastClick by remember { mutableLongStateOf(0L) }
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        enabled           = enabled,
        interactionSource = interactionSource,
        indication        = ripple(),
        role              = role,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastClick >= debounceMs) {
            lastClick = now
            onClick()
        }
    }
}

/**
 * Formats a position or duration in milliseconds as `m:ss`, or `h:mm:ss` from one hour up.
 *
 * The hours branch exists because a long track (a DJ set, an audiobook chapter, a live recording)
 * otherwise reads as `83:45`. Negative input clamps to `0:00`: a position read a frame before the
 * player knows its duration can briefly be negative, and a `0:-1` label is never what the user should
 * see. Sub-second remainders truncate, so the label ticks over exactly on the second.
 *
 * Built by hand rather than with `String.format`: digits and separators here are not language text,
 * and the formatter's implicit default locale would make the same position render differently per
 * device for no benefit.
 */
fun Long.toTimeString(): String {
    val totalSeconds = coerceAtLeast(0L) / 1000L
    val hours        = totalSeconds / 3600L
    val minutes      = (totalSeconds % 3600L) / 60L
    val seconds      = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
    } else {
        "$minutes:${seconds.twoDigits()}"
    }
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')

/**
 * Whole hours and the whole minutes left over, of a duration in milliseconds — the pure half of
 * [toDurationString], split out so the arithmetic is unit-tested on the JVM while the wording stays in
 * string resources. Negative input clamps to zero; seconds are floored away.
 */
data class HoursMinutes(val hours: Long, val minutes: Long)

/** See [HoursMinutes]. */
fun Long.toHoursMinutes(): HoursMinutes {
    val totalSeconds = coerceAtLeast(0L) / 1000L
    return HoursMinutes(
        hours   = totalSeconds / 3600L,
        minutes = (totalSeconds % 3600L) / 60L,
    )
}

/**
 * A total running time ("1h 5m", "42m") for an album or playlist header. The unit letters are words
 * in the user's language, so they come from string resources; the arithmetic is [toHoursMinutes].
 * Floors to whole minutes — a caller showing "time left" should floor its input at one minute so the
 * last stretch does not read "0m".
 */
@Composable
@ReadOnlyComposable
fun Long.toDurationString(): String {
    val (hours, minutes) = toHoursMinutes()
    return if (hours > 0L) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}
