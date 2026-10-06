package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.ui.navigation.WindowWidth

/*
 * The pure rules of the floating player surface (PlayerSurfaceHost): which surface shows, where the mini bar
 * sits, and whether the pop-out panel may exist at all. No Compose runtime, no android.* — unit-tested in
 * PlayerSurfaceTest.
 */

/**
 * Which floating player surface is on screen. The mini bar and the pop-out panel are mutually exclusive, so ONE
 * state describes both and ONE seekable transition animates over it: the bar's hide and the panel's show are two
 * children of that transition, computed in the same composition pass, so the `"album-art"` shared element always
 * has exactly one target — and a single `seekTo` (a predictive-back gesture) moves both ends of the morph.
 */
enum class PlayerSurface { None, Bar, Panel }

/** Below this window height (dp) the screen is "short" (folded landscape): no pop-out panel, the full player instead. */
const val SHORT_SCREEN_MAX_HEIGHT_DP: Float = 500f

/** On a two-pane library screen the mini bar spans the right pane only: Lyra's 0.58 of the content width. */
const val TWO_PANE_BAR_WIDTH_FRACTION: Float = 0.58f

/**
 * The surface the app wants on screen.
 *
 * @param hasTrack something is loaded in the player.
 * @param routeAllowsSurface the screen on top shows the floating surface (Library, Search and the detail
 *   entries — never Intro, Settings, the sign-in flow, Player or Queue).
 * @param panelWanted the user opened the pop-out panel over this screen and has not closed it.
 * @param canShowPanel the window can host the panel at all ([popOutAllowed]).
 */
fun playerSurfaceFor(
    hasTrack          : Boolean,
    routeAllowsSurface: Boolean,
    panelWanted       : Boolean,
    canShowPanel      : Boolean,
): PlayerSurface = when {
    !hasTrack || !routeAllowsSurface -> PlayerSurface.None
    panelWanted && canShowPanel      -> PlayerSurface.Panel
    else                             -> PlayerSurface.Bar
}

/**
 * Whether the window can host the pop-out panel: a medium or expanded width (the rail layouts) that is not a
 * short screen. Everywhere else a tap on the mini bar opens the full player.
 */
fun popOutAllowed(width: WindowWidth, windowHeightDp: Float): Boolean =
    width != WindowWidth.Compact && windowHeightDp >= SHORT_SCREEN_MAX_HEIGHT_DP

/**
 * Where the mini bar sits: [bottom] is its distance from the window's bottom edge, [start] the room the
 * navigation rail takes at the start edge, [widthFraction] the share of the remaining width it spans
 * (end-aligned).
 */
data class MiniBarPlacement(val bottom: Dp, val start: Dp, val widthFraction: Float)

/**
 * The mini bar's placement.
 *
 * The library's own chrome — the compact bottom bar, the rail, the two-pane split — counts only while the
 * Library entry is on top ([onLibrary]): during a push the outgoing Library is still composed and still
 * publishing, and a pushed screen has none of that chrome. So:
 * - bottom: directly above the Library's bottom bar ([libraryBottomBar], which already includes the system
 *   inset the bar consumed), else on the system navigation bar ([navigationBarBottom]) — the rail layouts
 *   publish no bottom bar, so they land on the navigation bar too;
 * - start: after the rail ([libraryStartRail]) on the Library, from the edge elsewhere;
 * - width: [TWO_PANE_BAR_WIDTH_FRACTION] over a two-pane Library on a wide window, else the full width.
 */
fun miniBarPlacement(
    onLibrary          : Boolean,
    wide               : Boolean,
    libraryBottomBar   : Dp,
    libraryStartRail   : Dp,
    libraryTwoPane     : Boolean,
    navigationBarBottom: Dp,
): MiniBarPlacement = MiniBarPlacement(
    bottom        = if (onLibrary && libraryBottomBar > 0.dp) libraryBottomBar else navigationBarBottom,
    start         = if (onLibrary) libraryStartRail else 0.dp,
    widthFraction = if (wide && onLibrary && libraryTwoPane) TWO_PANE_BAR_WIDTH_FRACTION else 1f,
)

/**
 * How long an abandoned seek takes to wind back: the remaining share of the transition, so a cancel at 5 %
 * unwinds in a few milliseconds instead of a whole transition (Navigation's own cancel formula). The total is
 * floored at [floorMillis] so a transition that momentarily reports no duration cannot produce a 0 ms snap.
 */
fun unwindMillis(fraction: Float, totalDurationNanos: Long, floorMillis: Int): Int {
    val totalMillis = (totalDurationNanos / 1_000_000L).coerceAtLeast(floorMillis.toLong())
    return (fraction.coerceIn(0f, 1f) * totalMillis).toInt()
}
