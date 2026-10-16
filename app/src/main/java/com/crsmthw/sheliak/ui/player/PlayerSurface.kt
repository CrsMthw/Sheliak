package com.crsmthw.sheliak.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.ui.navigation.WindowWidth

/*
 * The pure rules of the floating player surface (PlayerSurfaceHost): which surface shows, where the mini bar
 * sits, whether the pop-out panel may exist at all, what crossing the pop-out line does to an open player,
 * which `"album-art"` registrations may match, and how much of the card's scrim shows. No Compose runtime, no
 * android.* — unit-tested in PlayerSurfaceTest and PlacementSeedingTest.
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
 * @param routeAllowsSurface the screen on top shows the floating surface (the Library — every tab, Search
 *   included — and the detail entries; never Intro, Settings, the sign-in flow, Player or Queue).
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

// ── Crossing the pop-out line ──────────────────────────────────────────────────────────────────────────────

/** What crossing the pop-out line ([popOutAllowed] flipping) does to a player that is open at that moment. */
enum class SurfaceSwap {
    /** Nothing to swap. */
    None,

    /** The full player was on top and the window can now host the card: close the player, show the card. */
    PlayerToCard,

    /** The card was open over a screen and the window can no longer host it: close the card, push the player. */
    CardToPlayer,
}

/**
 * The swap for a change of [popOutAllowed] from [canShowPanelBefore] to [canShowPanelNow]. Keyed on the pop-out
 * rule, not on the width class: folded landscape is wide but short, where the card cannot exist (a folded
 * rotation with the full player open must not trade it for a card that cannot show).
 *
 * @param playerOnTop the full player is the top entry.
 * @param hasTrack something is loaded (no card is opened for an empty player).
 * @param cardOverScreen the card is wanted over the screen on top (it was on screen before the change).
 */
fun surfaceSwapFor(
    canShowPanelBefore: Boolean,
    canShowPanelNow   : Boolean,
    playerOnTop       : Boolean,
    hasTrack          : Boolean,
    cardOverScreen    : Boolean,
): SurfaceSwap = when {
    canShowPanelBefore == canShowPanelNow      -> SurfaceSwap.None
    canShowPanelNow && playerOnTop && hasTrack -> SurfaceSwap.PlayerToCard
    !canShowPanelNow && cardOverScreen         -> SurfaceSwap.CardToPlayer
    else                                       -> SurfaceSwap.None
}

// ── Where the mini bar sits ────────────────────────────────────────────────────────────────────────────────

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
 * Library is the screen the bar shows over ([onLibrary]): a pushed screen has none of that chrome. So:
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
 * What the Library's navigation suite publishes to the player surface (`PlayerSurfaceChrome`), as one value:
 * the compact bottom bar's measured height, the rail's measured width, and whether a two-pane tab is shown.
 */
data class LibraryChrome(val bottomBar: Dp, val startRail: Dp, val twoPane: Boolean) {
    companion object {
        /** Nothing published: the Library is not composed (or has not measured yet). */
        val None: LibraryChrome = LibraryChrome(bottomBar = 0.dp, startRail = 0.dp, twoPane = false)
    }
}

/**
 * Whether [chrome] describes a Library laid out for this width class: a bottom bar and no rail on compact, a rail
 * and no bottom bar on the rail widths. Anything else is a Library that is not composed, or one still laid out
 * for the previous width (the suite publishes its new extent a frame after a fold).
 */
fun chromeFits(wide: Boolean, chrome: LibraryChrome): Boolean =
    if (wide) chrome.startRail > 0.dp && chrome.bottomBar == 0.dp else chrome.bottomBar > 0.dp && chrome.startRail == 0.dp

/**
 * The published chrome when it can be trusted for the CURRENT width class, else null.
 *
 * [staleAtWidthChange] is what was published when the width class last changed (null once something fresh
 * arrived): until the publication differs from it, it still describes the old layout — a medium → expanded
 * change keeps a rail on screen, so the collapsed rail's width would otherwise "fit" the expanded class and be
 * taken for it.
 */
fun freshChrome(wide: Boolean, live: LibraryChrome, staleAtWidthChange: LibraryChrome?): LibraryChrome? = when {
    staleAtWidthChange != null && live == staleAtWidthChange -> null
    chromeFits(wide, live)                                   -> live
    else                                                     -> null
}

/**
 * The bar's target placement, or null when it cannot be known yet: the bar shows over the Library
 * ([onLibrary]) but no chrome for this width class has been published ([libraryChrome] — the fresh chrome, else
 * the last fresh chrome remembered for this width class). Over any other screen the bar sits on the navigation
 * bar at full width, which needs nothing from the Library.
 */
fun miniBarTarget(
    onLibrary          : Boolean,
    wide               : Boolean,
    libraryChrome      : LibraryChrome?,
    navigationBarBottom: Dp,
): MiniBarPlacement? = when {
    !onLibrary            -> MiniBarPlacement(bottom = navigationBarBottom, start = 0.dp, widthFraction = 1f)
    libraryChrome == null -> null
    else                  -> miniBarPlacement(
        onLibrary           = true,
        wide                = wide,
        libraryBottomBar    = libraryChrome.bottomBar,
        libraryStartRail    = libraryChrome.startRail,
        libraryTwoPane      = libraryChrome.twoPane,
        navigationBarBottom = navigationBarBottom,
    )
}

/** How the bar moves to a new placement. */
enum class PlacementMotion { Snap, Animate }

/**
 * How the bar reaches a new placement: it GLIDES (the finite spec) only while it is on screen, the width class is
 * the one its current placement was computed for, and that placement was a real one. A hidden bar snaps (it must
 * be in place before it is shown); so does a fold — the whole window re-lays out on a fold, and a bar visibly
 * gliding to the rail afterwards is the re-layout Cris reported (item 63) — and so does a move away from a
 * PROVISIONAL placement (one taken while no chrome for this width class was known yet, see [miniBarTarget]).
 */
fun placementMotion(barShown: Boolean, widthClassChanged: Boolean, fromProvisional: Boolean): PlacementMotion =
    if (barShown && !widthClassChanged && !fromProvisional) PlacementMotion.Animate else PlacementMotion.Snap

// ── Which "album-art" registrations may match (Lyra's SharedContentConfig gating) ───────────────────────────

/**
 * Whether a full player is part of the picture: on top, or still composed while it leaves ([composedFullPlayers]
 * counts every composed `PlayerScreen`, a leaving one until its exit transition ends). Lyra's
 * `LocalPlayerRouteVisible` (the player among the navigation host's visible entries), rebuilt for Navigation 3.
 */
fun fullPlayerPresent(topIsPlayer: Boolean, composedFullPlayers: Int): Boolean =
    topIsPlayer || composedFullPlayers > 0

/**
 * Whether the mini bar's art may MATCH: only while a partner exists — the full player ([fullPlayerPresent]) or the
 * pop-out card taking part in the surface transition ([otherSurfaceInTransition]). The registration itself is
 * permanent; only this enabled flag moves. (The card's own registration is not gated since fix round 1b: it must
 * be the enabled, settled participant at rest — see PlayerSurfaceHost.)
 */
fun floatingArtMayMatch(fullPlayerPresent: Boolean, otherSurfaceInTransition: Boolean): Boolean =
    fullPlayerPresent || otherSurfaceInTransition

/**
 * How long an abandoned seek takes to wind back: the remaining share of the transition, so a cancel at 5 %
 * unwinds in a few milliseconds instead of a whole transition (Navigation's own cancel formula). The total is
 * floored at [floorMillis] so a transition that momentarily reports no duration cannot produce a 0 ms snap.
 */
fun unwindMillis(fraction: Float, totalDurationNanos: Long, floorMillis: Int): Int {
    val totalMillis = (totalDurationNanos / 1_000_000L).coerceAtLeast(floorMillis.toLong())
    return (fraction.coerceIn(0f, 1f) * totalMillis).toInt()
}

// ── The scrim behind the pop-out card ──────────────────────────────────────────────────────────────────────

/**
 * How much of the pop-out card's scrim shows, 0..1 (the host multiplies it by `PANEL_SCRIM_ALPHA`), read from the
 * surface's ONE seekable state itself — never from an animation of a child transition, which a fold's `snapTo`
 * leaves stale (fix round 2, item 41; docs/MOTION.md W-2).
 *
 * At rest ([current] == [target]) it is 1 on the panel and 0 anywhere else, whatever came before — so a snap to
 * None or Bar clears it by construction. Moving, it runs from [startShare] to the target's rest share over
 * [progress]: into the panel, a seek or an animation fades it in; out of it, out.
 *
 * @param current the seekable state's `currentState`.
 * @param target its `targetState`.
 * @param progress its `fraction` on the card's own easing (the host applies `screenTransitionSpec()`'s curve, so
 *   the scrim cannot drift from the card's slide); clamped to 0..1.
 * @param startShare the share on screen when this current → target segment began ([PanelScrimSegment]): a
 *   retarget mid-flight restarts the state at fraction 0 with `currentState` = the OLD target, so the state alone
 *   would jump.
 */
fun panelScrimShare(current: PlayerSurface, target: PlayerSurface, progress: Float, startShare: Float): Float {
    val end = if (target == PlayerSurface.Panel) 1f else 0f
    if (current == target) return end
    return startShare + (end - startShare) * progress.coerceIn(0f, 1f)
}

/**
 * The scrim's one piece of memory: the share it showed when the seekable state's current → target pair last
 * changed, which a new segment starts from ([panelScrimShare]). A retarget — a back swipe on a card still rising
 * from the full player (opening from None, after its back arrow), the card's full-screen button while it is still
 * moving — moves `currentState` to the old target and resets the fraction to 0; Compose hides that jump from child
 * animations, and this is the same continuity for a value read from the state: the new segment starts from what was
 * on screen. (A close or reopen that returns to the state the surface is leaving is not a retarget: the host winds
 * the fraction back instead. Nor is a back swipe on a card opening from the BAR: the state's current state is still
 * the bar, so the host has nothing to seek and the card pauses under the finger until the gesture ends.)
 *
 * One step remains, by design: a retarget wound back to the state it left (that back swipe on a card still rising
 * from the full player, released early) ends at the remembered start, and the snap back to the card then shows the
 * full share. Compose's children avoid it by letting the interrupted segment run on underneath, in time, which a
 * value read from the state cannot; it is a single small step (at most the share the opening had not reached yet)
 * on a rare gesture.
 *
 * Not snapshot state: called once per composition of the scrim (the host's holder idiom), whether or not it draws.
 */
class PanelScrimSegment {
    private var current: PlayerSurface? = null
    private var target: PlayerSurface? = null
    private var start = 0f
    private var shown = 0f

    /** The share to show for this state, remembering it as the start of the next segment. */
    fun share(current: PlayerSurface, target: PlayerSurface, progress: Float): Float {
        if (current != this.current || target != this.target) {
            this.current = current
            this.target  = target
            start        = shown
        }
        shown = panelScrimShare(current, target, progress, start)
        return shown
    }
}
