package com.crsmthw.sheliak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The Settings → Theme "Mode" choice, persisted by name as `theme_mode`. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Whether this mode renders dark, given the system's current dark flag. One definition shared by
 * [SheliakTheme] and the Activity's system-bar styling, so the bars can never disagree with the content.
 */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT  -> false
    ThemeMode.DARK   -> true
}

/**
 * The app theme. Colours come from Material You when [dynamicColor] is on, else from the accent seed
 * ([accentArgb], see [SeedColorScheme]); the AMOLED overlay is applied on top of either source. Observed from
 * the Activity's `setContent`, so a theme change recomposes in place — the Activity is never recreated for it
 * (`uiMode` is in the manifest's `configChanges`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheliakTheme(
    themeMode   : ThemeMode = ThemeMode.SYSTEM,
    amoledBlack : Boolean   = false,
    dynamicColor: Boolean   = true,
    accentArgb  : Int       = ACCENT_DEFAULT_ARGB,
    content     : @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark(isSystemInDarkTheme())
    val context = LocalContext.current

    val baseScheme = if (dynamicColor) {
        // Not remembered: it only reads the system's colour resources, so there is nothing worth caching.
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Generating 48 roles through HCT is the one costly step, so it runs only when the seed or mode changes.
        remember(accentArgb, darkTheme) { SeedColorScheme.from(accentArgb, darkTheme) }
    }

    MaterialExpressiveTheme(
        colorScheme  = baseScheme.withAmoledOverlay(darkTheme, amoledBlack),
        typography   = SheliakTypography,
        motionScheme = MotionScheme.expressive(),
        content      = content,
    )
}

/**
 * The AMOLED overlay: only in dark mode with the AMOLED switch on, and only `background`, `surface` and
 * `surfaceVariant` — the `surfaceContainer*` roles keep their tones so sheets, cards and menus still lift off
 * the black page. This is also why every app bar paints the pane colour in both of its colour slots rather
 * than M3's default `surfaceContainer` scrolled colour. Pure, so it is unit-tested.
 */
internal fun ColorScheme.withAmoledOverlay(darkTheme: Boolean, amoledBlack: Boolean): ColorScheme =
    if (darkTheme && amoledBlack) {
        copy(
            background     = AmoledBlack,
            surface        = AmoledBlack,
            surfaceVariant = AmoledSurfaceVariant,
        )
    } else {
        this
    }
