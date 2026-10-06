package com.crsmthw.sheliak.ui.navigation

/*
 * Which navigation-suite component the library shows, decided from the window width. Pure Kotlin so the
 * decision is unit-tested (SuiteLayoutTest); the composable side only maps the answer onto Material's
 * NavigationSuiteType.
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

/** What the suite shows: a bottom bar, or a wide rail (collapsed or expanded). */
enum class SuiteLayout { Bar, CollapsedRail, ExpandedRail }

/**
 * The suite for [width]. The suite only exists inside the Library entry, so there is no hidden case: purely
 * width-driven — bar on compact, collapsed wide rail on medium, expanded wide rail on expanded — deliberately
 * NOT Material's default `navigationSuiteType()`, which never picks the expanded rail and swaps the rail for a
 * short bar whenever the window is short (folded landscape), where the four items still fit a rail comfortably.
 */
fun suiteLayoutFor(width: WindowWidth): SuiteLayout = when (width) {
    WindowWidth.Compact  -> SuiteLayout.Bar
    WindowWidth.Medium   -> SuiteLayout.CollapsedRail
    WindowWidth.Expanded -> SuiteLayout.ExpandedRail
}

/**
 * Whether search opens from a FAB that morphs into the search bar. Only on compact: on the rail widths the
 * search button sits in the rail header and Search opens with the ordinary push transition. Both ends of the
 * morph (the FAB and the Search screen's bar) ask this, so they can never disagree.
 */
fun searchMorphEnabled(width: WindowWidth): Boolean = width == WindowWidth.Compact

/**
 * Whether the library's list-detail tabs (Albums, Artists, Playlists) show the list and the selected item side by
 * side in two cards: from the medium width class up (600dp), exactly where the suite turns into a rail. Below it
 * a pick pushes the detail as its own navigation entry.
 */
fun listDetailEnabled(width: WindowWidth): Boolean = width != WindowWidth.Compact

/**
 * Whether [tab] is laid out as two panes at [width]. Tracks never is: it is the single-pane home at every width
 * (carousels + All tracks).
 */
fun libraryTabIsTwoPane(tab: LibraryTab, width: WindowWidth): Boolean =
    listDetailEnabled(width) && tab != LibraryTab.TRACKS

/** What the suite's own component takes out of the library's area, in px: the bar's height or the rail's width. */
data class SuiteChromeExtent(val bottomBarPx: Int, val startRailPx: Int) {
    companion object {
        val None: SuiteChromeExtent = SuiteChromeExtent(0, 0)
    }
}

/**
 * The bar's or the rail's measured extent for [layout], from the suite's TOTAL size and the size left to its
 * content (both measured, in px): the bar is the height the content lost, the rail the width it lost — so each
 * includes the system inset the component covers (the navigation bar under the bar, a side cutout beside the
 * rail). Only the component [layout] shows is reported; the other is 0. Never negative (a frame where only one
 * of the two sizes has been measured yet).
 */
fun suiteChromeExtent(
    layout       : SuiteLayout,
    totalWidth   : Int,
    totalHeight  : Int,
    contentWidth : Int,
    contentHeight: Int,
): SuiteChromeExtent = when (layout) {
    SuiteLayout.Bar           -> SuiteChromeExtent(bottomBarPx = (totalHeight - contentHeight).coerceAtLeast(0), startRailPx = 0)
    SuiteLayout.CollapsedRail,
    SuiteLayout.ExpandedRail  -> SuiteChromeExtent(bottomBarPx = 0, startRailPx = (totalWidth - contentWidth).coerceAtLeast(0))
}
