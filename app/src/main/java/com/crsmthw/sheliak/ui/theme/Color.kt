package com.crsmthw.sheliak.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The only colours the theme sets by hand. Every other colour in the app is a role of the active ColorScheme —
 * Material You's, or the one SeedColorScheme generates from the accent — so screens never name a colour of
 * their own and both sources stay complete. The one exception by design is the STATUS pair below: a meaning
 * (online / offline) that must read the same whatever the accent is.
 *
 * There is deliberately no static light/dark fallback scheme: with minSdk 35 Material You is always available,
 * and when it is switched off the accent seed generates the full scheme, so a hand-written fallback would
 * never be shown.
 */

/** AMOLED overlay: `background` and `surface` go true black, so those pixels switch off on an OLED panel. */
val AmoledBlack: Color = Color.Black

/**
 * AMOLED overlay: `surfaceVariant` stays one step above black, so the components that paint it (slider
 * tracks, chips, text-field containers) still separate from a black surface instead of vanishing into it.
 */
val AmoledSurfaceVariant: Color = Color(0xFF0D0D0D)

/**
 * Status: "online" (a Plex server plex.tv reports online — the setup's server dot). A fixed green, the same in
 * light and dark and under any accent (Cris, M1 fix round 1: "should be green, not theme color"). A mid-tone
 * (relative luminance 0.20), so it keeps ≥ 3:1 against white (4.2:1) and AMOLED black (5.0:1) alike.
 */
val StatusOnline: Color = Color(0xFF1E8E3E)

/**
 * Status: "offline" — a neutral grey, the same in light and dark (3.4:1 on white, 6.1:1 on black). Drawn as a
 * ring where [StatusOnline] is a filled dot, so the two also differ in shape, not only in colour.
 */
val StatusOffline: Color = Color(0xFF8A8A8E)
