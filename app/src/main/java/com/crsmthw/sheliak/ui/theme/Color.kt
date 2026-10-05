package com.crsmthw.sheliak.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The only colours the theme sets by hand. Every other colour in the app is a role of the active ColorScheme —
 * Material You's, or the one SeedColorScheme generates from the accent — so screens never name a colour of
 * their own and both sources stay complete.
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
