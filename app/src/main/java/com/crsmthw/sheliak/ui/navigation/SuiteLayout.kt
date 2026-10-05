package com.crsmthw.sheliak.ui.navigation

import androidx.navigation3.runtime.NavKey

/*
 * Which navigation-suite component the shell shows, decided from the window width and the top of the back
 * stack. Pure Kotlin so the decision is unit-tested (SuiteLayoutTest); the composable side only maps the
 * answer onto Material's NavigationSuiteType.
 */

/** The M3 window width classes the shell distinguishes. */
enum class WindowWidth { Compact, Medium, Expanded }

/** Lower bound of the medium width class, in dp (M3 window size classes). */
const val MEDIUM_WIDTH_MIN_DP: Int = 600

/** Lower bound of the expanded width class, in dp (M3 window size classes). */
const val EXPANDED_WIDTH_MIN_DP: Int = 840

/** A window width in dp → its width class. */
fun windowWidthOf(widthDp: Int): WindowWidth = when {
    widthDp >= EXPANDED_WIDTH_MIN_DP -> WindowWidth.Expanded
    widthDp >= MEDIUM_WIDTH_MIN_DP   -> WindowWidth.Medium
    else                             -> WindowWidth.Compact
}

/** What the suite shows: a bottom bar, a wide rail (collapsed or expanded), or nothing. */
enum class SuiteLayout { Bar, CollapsedRail, ExpandedRail, Hidden }

/**
 * The suite for [width] while [topKey] is on top. Hidden on every screen that is not one of the four
 * destinations (Intro, Search, Settings, Player, Queue). Otherwise purely width-driven — bar on compact,
 * collapsed wide rail on medium, expanded wide rail on expanded — deliberately NOT Material's default
 * `navigationSuiteType()`, which never picks the expanded rail and swaps the rail for a short bar whenever
 * the window is short (folded landscape), where the four items still fit a rail comfortably.
 */
fun suiteLayoutFor(width: WindowWidth, topKey: NavKey?): SuiteLayout = when {
    !isTopLevel(topKey)              -> SuiteLayout.Hidden
    width == WindowWidth.Compact     -> SuiteLayout.Bar
    width == WindowWidth.Medium      -> SuiteLayout.CollapsedRail
    else                             -> SuiteLayout.ExpandedRail
}

/**
 * Whether search opens from a FAB that morphs into the search bar. Only on compact: on the rail widths the
 * search button sits in the rail header and Search opens with the ordinary push transition. Both ends of the
 * morph (the FAB and the Search screen's bar) ask this, so they can never disagree.
 */
fun searchMorphEnabled(width: WindowWidth): Boolean = width == WindowWidth.Compact
