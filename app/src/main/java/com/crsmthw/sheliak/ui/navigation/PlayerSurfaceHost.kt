package com.crsmthw.sheliak.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.player.ArtKeyGeneration
import com.crsmthw.sheliak.ui.player.LibraryChrome
import com.crsmthw.sheliak.ui.player.LocalAlbumArtColors
import com.crsmthw.sheliak.ui.player.LocalPlayerArtGate
import com.crsmthw.sheliak.ui.player.LocalPlayerArtKey
import com.crsmthw.sheliak.ui.player.MiniBarPlacement
import com.crsmthw.sheliak.ui.player.MiniPlayer
import com.crsmthw.sheliak.ui.player.PANEL_SCRIM_ALPHA
import com.crsmthw.sheliak.ui.player.POP_OUT_PANEL_MAX_HEIGHT_FRACTION
import com.crsmthw.sheliak.ui.player.POP_OUT_PANEL_WIDTH_FRACTION
import com.crsmthw.sheliak.ui.player.PanelScrimSegment
import com.crsmthw.sheliak.ui.player.PlacementMotion
import com.crsmthw.sheliak.ui.player.PlayerArtGate
import com.crsmthw.sheliak.ui.player.PlayerPopOutPanel
import com.crsmthw.sheliak.ui.player.PlayerSurface
import com.crsmthw.sheliak.ui.player.SurfaceSwap
import com.crsmthw.sheliak.ui.player.albumArtKey
import com.crsmthw.sheliak.ui.player.floatingArtMayMatch
import com.crsmthw.sheliak.ui.player.freshChrome
import com.crsmthw.sheliak.ui.player.fullPlayerPresent
import com.crsmthw.sheliak.ui.player.miniBarPlacement
import com.crsmthw.sheliak.ui.player.miniBarTarget
import com.crsmthw.sheliak.ui.player.morphQuiet
import com.crsmthw.sheliak.ui.player.placementMotion
import com.crsmthw.sheliak.ui.player.playerSurfaceFor
import com.crsmthw.sheliak.ui.player.popOutAllowed
import com.crsmthw.sheliak.ui.player.rememberAlbumArtColors
import com.crsmthw.sheliak.ui.player.rememberPlayerTints
import com.crsmthw.sheliak.ui.player.surfaceSwapFor
import com.crsmthw.sheliak.ui.player.unwindMillis
import com.crsmthw.sheliak.util.NavTransitionMillis
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.screenTransitionSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The bottom space the floating player surface occupies over the screens: the mini bar's measured height,
 * its margins included, while the bar is the surface the app shows — 0dp otherwise (no track, the pop-out panel
 * instead). While a screen without the surface (the full player, Settings…) is on top it keeps the value it had
 * on the last screen with one, so a screen sliding out under a push never shifts. Screens add it to their
 * scroller's bottom room and lift bottom-anchored controls by it, so nothing hides under the bar. It is a plain
 * value that changes only when the bar comes or goes — never animated, because a static local recomposes
 * everything it is provided to (a control that should glide animates its own copy).
 */
val LocalPlayerSurfaceInset = staticCompositionLocalOf { 0.dp }

/**
 * What the library's own chrome publishes to the player surface, so the mini bar can sit above the bottom
 * bar and beside the rail instead of over them. ONE instance, provided by [PlayerSurfaceHost] to everything it
 * hosts; the Library shell writes into it and resets each value when its layout changes or it leaves.
 *
 * @property bottomBar the compact bottom bar's measured TOTAL height (the system inset it consumes included)
 *   while that bar is composed, else 0dp.
 * @property startRail the navigation rail's measured total width while a rail is composed, else 0dp.
 * @property twoPane true while a two-pane (list ∣ detail) layout is composed.
 */
@Stable
class PlayerSurfaceChrome {
    var bottomBar: Dp by mutableStateOf(0.dp)
    var startRail: Dp by mutableStateOf(0.dp)
    var twoPane: Boolean by mutableStateOf(false)
}

/** The host's one [PlayerSurfaceChrome] (a throwaway default outside the host). */
val LocalPlayerSurfaceChrome = staticCompositionLocalOf { PlayerSurfaceChrome() }

/**
 * True while the pop-out panel is on screen. A hosted screen that registers its own `BackHandler` may gate it
 * on `!LocalPopOutPanelOpen.current`, so a back with the panel open always closes the panel, whatever order the
 * handlers registered in.
 */
val LocalPopOutPanelOpen = compositionLocalOf { false }

/** Non-snapshot cell written during composition (or by one effect) and read back later (Lyra's holder idiom). */
private class Held<T>(var value: T)

/**
 * The library chrome the bar's placement is seeded from, per width class (non-snapshot, written during
 * composition): the width class it last saw, what the chrome said when that class changed (stale until the
 * publication moves), and the last FRESH chrome of each width class — so the bar over a Library that is not
 * composed (a full player or Settings on top), or one still laid out for the previous width, is placed where the
 * Library will be, not where it was.
 */
private class PlacementSeed(var width: WindowWidth) {
    var staleAtWidthChange: LibraryChrome? = null
    val lastFresh: MutableMap<WindowWidth, LibraryChrome> = mutableMapOf()
}

/** "No panel back gesture in progress" for the gesture-progress state (a float state, not a nullable box). */
private const val NO_GESTURE = -1f

/** The mini bar's height before its first measure: the 64dp card (art and ring, padding) + its 8dp margins. */
private val MiniBarEstimatedHeight = 80.dp

/** The pop-out card's margin above the navigation bar. */
private val PanelBottomMargin = 16.dp

/**
 * THE host of the floating player surface — the mini bar and the pop-out panel — wrapped once around the
 * navigation host, so the surface survives every navigation instead of leaving and re-entering with each
 * screen (Lyra's PlayerPanelHost topology, rebuilt on [PlayerStateManager]).
 *
 * ### One seekable transition
 * The bar and the panel are the two children of ONE `SeekableTransitionState<PlayerSurface>` (None / Bar /
 * Panel): their show and hide are computed in the same composition pass, so the `"album-art"` shared element
 * always has exactly one target, and one `seekTo` drives both ends of the morph. Three things drive it:
 * - the panel's own predictive back (a standard `PredictiveBackHandler`, composed last and only while the
 *   panel shows, so it registers after every handler inside the content and wins) seeks Panel → Bar with the
 *   finger;
 * - a back GESTURE off the full player seeks None → the surface the screen below shows (the bar or the panel),
 *   so the art flies back with the finger. The full player is only ever opened from a screen with the surface,
 *   so "the screen below" is the last surface-allowed screen;
 * - everything else (a tap, a push, a committed or cancelled gesture) animates to the wanted surface, or winds
 *   an abandoned seek back — Navigation's own pattern, on the finite specs — or SNAPS (a fold swap, below).
 *
 * The card's scrim is not an animation of a child: it reads this state itself ([PanelScrim]), because a snap to a
 * state that is neither the current nor the target one leaves a child transition's animations stale (fix round 2,
 * item 41).
 *
 * ### Lyra's §5.1 workarounds (fix round 1)
 * - `"album-art#<generation>"` ([LocalPlayerArtKey]): the generation advances at the first quiet moment after an
 *   ABANDONED seek, and at no other settle ([ArtKeyGeneration]).
 * - The `PreEnter` bail-outs in [MiniPlayer] / [PlayerPopOutPanel].
 * - `SharedContentConfig` gating: every `"album-art"` registration is permanent; the bar's may match only while
 *   a partner exists, the full player's not while a fold swap replaces it ([PlayerArtGate]). The card's is NOT
 *   gated (Lyra's card carried no config either): it must be the enabled, settled participant at rest, so the
 *   flight out of it always starts from it (fix round 1b — see the gating block below).
 *
 * ### Crossing the pop-out line
 * When the window becomes able to host the card while the full player is on top, the host closes the player
 * ([onClosePlayer]) and shows the card in the same pass — snapped, no flight. When the card is open and the
 * window can no longer host it, the card goes without animation and the full player opens ([onOpenPlayer]).
 *
 * ### Placement
 * Compact: full width, directly above the Library's bottom bar while the Library is the screen below, on the
 * system navigation bar on every other screen, gliding between the two with the page transition. ≥ 600dp: after
 * the rail, full width over single-pane screens and 0.58 of the width, end-aligned, over two-pane ones; a tap
 * opens the pop-out card bottom-end over a scrim instead of the full player — except on short windows
 * (< 500dp tall), where it opens the full player. The Library's chrome is remembered per width class, so a bar
 * over a Library that is not composed is placed where the Library will be; after a fold the bar snaps to the
 * Library's FIRST chrome for the new width class (a provisional placement for the frame before it), never glides.
 *
 * @param topKey the back stack's top entry.
 * @param surfaceAllowed whether [topKey] shows the floating surface (`playerSurfaceAllowed(topKey)`).
 * @param onOpenPlayer pushes the full player at once (never debounced: the fold swap above calls it too, and must
 *   not be dropped for following another push closely).
 * @param onOpenQueue pushes the queue (from the pop-out card).
 * @param onClosePlayer pops the full player when it is the top entry (the swap to the card when the window
 *   crosses the pop-out line).
 * @param content the navigation host; it receives `onRequestPlayer`, the one way a screen asks for the player:
 *   the pop-out panel where it can exist, else the full player.
 */
@Composable
fun PlayerSurfaceHost(
    player        : PlayerStateManager,
    topKey        : NavKey?,
    surfaceAllowed: Boolean,
    onOpenPlayer  : () -> Unit,
    onOpenQueue   : () -> Unit,
    onClosePlayer : () -> Unit,
    modifier      : Modifier = Modifier,
    content       : @Composable (onRequestPlayer: () -> Unit) -> Unit,
) {
    val state           by player.state.collectAsStateWithLifecycle()
    val track           = state.track
    val hasTrack        = track != null
    val density         = LocalDensity.current
    val haptics         = LocalHapticFeedback.current
    val focusManager    = LocalFocusManager.current
    val keyboard        = LocalSoftwareKeyboardController.current
    val layoutDirection = LocalLayoutDirection.current
    val navScope        = LocalNavSharedTransitionScope.current
    val windowHeight    = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val width           = currentWindowWidth()
    val wide            = width != WindowWidth.Compact
    val canShowPanel    = popOutAllowed(width, windowHeight.value)
    val playerOnTop     = topKey == Player

    val chrome    = remember { PlayerSurfaceChrome() }
    val artGate   = remember { PlayerArtGate() }
    val artColors = rememberAlbumArtColors(track?.art)
    val tints     = rememberPlayerTints(artColors)

    // ── The panel: the user's intent, and the screen it belongs to ──────────────────────────────────────
    // Saveable, so an open panel survives process death like any other visible UI state.
    var panelWanted by rememberSaveable { mutableStateOf(false) }
    // The surface-allowed screen the panel was opened over. While the full player or the queue is on top the
    // panel is HIDDEN, not closed (it is the art's other end on the way back); once a DIFFERENT allowed screen
    // is on top, the intent is spent. Null (after a restore, or a fold swap) is adopted by the next allowed screen.
    var panelOwner by remember { mutableStateOf<NavKey?>(null) }
    val lastAllowedKey = remember { Held(topKey) }
    if (surfaceAllowed) lastAllowedKey.value = topKey
    val shownOver  = lastAllowedKey.value
    val panelOwned = panelWanted && (panelOwner == null || panelOwner == shownOver)

    fun closePanel() {
        panelWanted = false
        panelOwner  = null
    }

    LaunchedEffect(shownOver) {
        if (panelOwner != null && panelOwner != shownOver) closePanel()
    }
    LaunchedEffect(panelWanted, surfaceAllowed, shownOver) {
        if (panelWanted && panelOwner == null && surfaceAllowed) panelOwner = shownOver
    }
    // Playback ending closes it too, so a later track does not bring a panel back nobody asked for.
    val hasTrackNow = rememberUpdatedState(hasTrack)
    LaunchedEffect(Unit) {
        snapshotFlow { hasTrackNow.value }.drop(1).collect { if (!it) closePanel() }
    }

    // ── Crossing the pop-out line ───────────────────────────────────────────────────────────────────────
    // A request to SNAP the surface to this target on the next change instead of animating (consumed by effect
    // (3) below). Non-snapshot: written by the swap effect or during composition, read by that one effect.
    val snapRequest = remember { Held<PlayerSurface?>(null) }
    // The card over this screen while the window can no longer host it (a fold, or a short window): the full
    // player is about to take over (the swap effect pushes it), so the surface already targets None in this pass,
    // and snaps there — the bar never slides in for the frame in between.
    val cardStranded = panelOwned && surfaceAllowed && !canShowPanel
    val wasStranded  = remember { Held(false) }
    if (cardStranded && !wasStranded.value) snapRequest.value = PlayerSurface.None
    wasStranded.value = cardStranded

    val swapSeen        = remember { Held(canShowPanel) }
    val playerOnTopNow  by rememberUpdatedState(playerOnTop)
    val cardOverNow     by rememberUpdatedState(panelOwned && surfaceAllowed)
    val closePlayerNow  by rememberUpdatedState(onClosePlayer)
    val openPlayerNow   by rememberUpdatedState(onOpenPlayer)
    LaunchedEffect(canShowPanel) {
        val before = swapSeen.value
        swapSeen.value = canShowPanel
        val swap = surfaceSwapFor(
            canShowPanelBefore = before,
            canShowPanelNow    = canShowPanel,
            playerOnTop        = playerOnTopNow,
            hasTrack           = hasTrackNow.value,
            cardOverScreen     = cardOverNow,
        )
        when (swap) {
            // The window can host the card while the full player is on top: close the player and show the card
            // in its place, snapped — and the leaving player's art must not fly into it (its registration is
            // disabled until it has left composition; the generation is NOT bumped: nothing was abandoned).
            SurfaceSwap.PlayerToCard -> {
                artGate.fullPlayerMayMatch = false
                snapRequest.value = PlayerSurface.Panel
                panelWanted = true
                panelOwner  = null
                closePlayerNow()
            }
            // The card cannot exist here any more: it goes without animation (cardStranded above) and the full
            // player opens.
            SurfaceSwap.CardToPlayer -> {
                closePanel()
                openPlayerNow()
            }
            // Folding below the panel's width (or into a short window) with nothing open spends the intent.
            SurfaceSwap.None -> if (!canShowPanel) closePanel()
        }
    }
    // The full player's art may match again once the player a swap replaced has left composition.
    LaunchedEffect(artGate) {
        snapshotFlow { artGate.composedFullPlayers == 0 }.collect { gone ->
            if (gone) artGate.fullPlayerMayMatch = true
        }
    }

    // ── Placement of the bar, seeded per width class ────────────────────────────────────────────────────
    // The navigation-bar inset is read as a VALUE here and only here — the one exception CLAUDE.md rule 9 grants
    // to this host: it sits ABOVE the navigation suite, so nothing has consumed the inset yet, and the offset
    // must animate between it and the Library's bar height.
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val liveChrome = LibraryChrome(bottomBar = chrome.bottomBar, startRail = chrome.startRail, twoPane = chrome.twoPane)
    val seed = remember { PlacementSeed(width) }
    if (seed.width != width) {
        seed.width = width
        seed.staleAtWidthChange = liveChrome
    }
    val fresh = freshChrome(wide, liveChrome, seed.staleAtWidthChange)
    if (fresh != null) {
        seed.staleAtWidthChange = null
        seed.lastFresh[width] = fresh
    }
    // The screen the bar shows over: the top entry, or — while a screen without the surface is on top — the last
    // one with it (a push to the full player never retargets a bar that is sliding away).
    val onLibraryBelow = shownOver == Library
    val knownPlacement: MiniBarPlacement? = miniBarTarget(
        onLibrary           = onLibraryBelow,
        wide                = wide,
        libraryChrome       = fresh ?: seed.lastFresh[width],
        navigationBarBottom = navigationBarBottom,
    )
    // Until the Library has published chrome for this width class (its first frame in a new class), the bar takes
    // a PROVISIONAL placement from whatever is published, and SNAPS to the real one a frame later.
    val placementProvisional = knownPlacement == null
    val placement = knownPlacement ?: miniBarPlacement(
        onLibrary           = onLibraryBelow,
        wide                = wide,
        libraryBottomBar    = liveChrome.bottomBar,
        libraryStartRail    = liveChrome.startRail,
        libraryTwoPane      = liveChrome.twoPane,
        navigationBarBottom = navigationBarBottom,
    )

    // ── The one transition ──────────────────────────────────────────────────────────────────────────────
    val surfaceTarget = if (cardStranded) {
        PlayerSurface.None
    } else {
        playerSurfaceFor(
            hasTrack           = hasTrack,
            routeAllowsSurface = surfaceAllowed,
            panelWanted        = panelOwned,
            canShowPanel       = canShowPanel,
        )
    }
    // What the screen below the full player shows: a back off the player seeks toward it.
    val surfaceBelowPlayer = playerSurfaceFor(
        hasTrack           = hasTrack,
        routeAllowsSurface = true,
        panelWanted        = panelOwned,
        canShowPanel       = canShowPanel,
    )
    // What closing the panel leaves on this screen: the bar.
    val surfaceAfterPanelClose = playerSurfaceFor(
        hasTrack           = hasTrack,
        routeAllowsSurface = surfaceAllowed,
        panelWanted        = false,
        canShowPanel       = canShowPanel,
    )
    val panelVisible = surfaceTarget == PlayerSurface.Panel

    val surfaceState      = remember { SeekableTransitionState(surfaceTarget) }
    val surfaceTransition = rememberTransition(surfaceState, label = "playerSurface")
    val barTransition     = surfaceTransition.createChildTransition(label = "miniBar") { it == PlayerSurface.Bar }
    val panelTransition   = surfaceTransition.createChildTransition(label = "popOutPanel") { it == PlayerSurface.Panel }

    // A read-only mirror of the back gesture every handler sees. Observing the dispatcher's state registers NO
    // handler, so it cannot take the gesture from the navigation host.
    val idleGesture     = remember { MutableStateFlow<NavigationEventTransitionState>(NavigationEventTransitionState.Idle) }
    val dispatcherOwner: NavigationEventDispatcherOwner? = LocalNavigationEventDispatcherOwner.current
    val gestureFlow: StateFlow<NavigationEventTransitionState> =
        dispatcherOwner?.navigationEventDispatcher?.transitionState ?: idleGesture
    val gesture by gestureFlow.collectAsStateWithLifecycle()
    val routeBackProgress: Float? = (gesture as? NavigationEventTransitionState.InProgress)
        ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
        ?.latestEvent?.progress?.coerceIn(0f, 1f)

    // Written by the panel's PredictiveBackHandler below; every suspending call lives in the effects here,
    // because the handler's own job is cancelled when its gesture is.
    var panelBackProgress by remember { mutableFloatStateOf(NO_GESTURE) }
    val panelProgress = panelBackProgress

    // ── "album-art#<generation>" (Lyra's §5.1 workaround) ──────────────────────────────────────────────
    // The unwind below records an abandoned seek; the generation advances at the next QUIET moment only — the
    // surface settled, no panel or route gesture in progress, no shared transition running in the navigation
    // scope (the full player's art rides the navigation transition, which can still be unwinding). Every other
    // settle keeps the key. Rules and reasons: ArtKeyGeneration.kt.
    val artKeys = remember { ArtKeyGeneration() }
    var artKeyGeneration by remember { mutableIntStateOf(artKeys.generation) }
    val artKey = remember(artKeyGeneration) { albumArtKey(artKeyGeneration) }
    val artMorphQuiet = morphQuiet(
        surfaceSettled         = surfaceState.currentState == surfaceState.targetState,
        panelGestureIdle       = panelProgress == NO_GESTURE,
        routeGestureIdle       = gesture !is NavigationEventTransitionState.InProgress,
        sharedTransitionActive = navScope?.isTransitionActive == true,
    )
    LaunchedEffect(artMorphQuiet, surfaceState.currentState) {
        if (artKeys.onQuiet(artMorphQuiet)) artKeyGeneration = artKeys.generation
    }

    // ── SharedContentConfig gating of the permanent registrations ─────────────────────────────────────────
    // The bar's art may MATCH only while a partner is part of the picture: the full player (on top, or still
    // leaving), or the card in the surface transition — counted from the pass that decides it (surfaceTarget),
    // before the transition itself is told. The full player's own registration is gated by
    // artGate.fullPlayerMayMatch (the fold swap).
    //
    // The CARD's registration is never gated (fix round 1b, Cris: "card → bar disappears the album art, predictive
    // and plain back"). Gated off at rest, it was re-enabled only from inside the card's two layout-phase
    // subcompositions (BoxWithConstraints, then the carousel's pager item) — AFTER the bar, composed in this pass,
    // had registered and been enabled. In that gap compose-animation 1.13's SharedTransitionStateMachine (no
    // transition active yet) re-points its "last target" at the bar — the only entry whose target is visible once
    // the card's has flipped — so when the card is enabled a moment later the match is configured with the BAR's
    // own bounds as the start (`obtainBoundsFromLastTarget`): the flight starts at the bar, sliding in below the
    // window edge, while the card's copy is hidden as the outgoing participant — the art vanishes. Enabled at rest,
    // the card IS the settled target when the bar arrives, so the match starts from the card's art, whatever the
    // composition order. Harmless otherwise: the card is composed only while it shows or moves, and the only
    // partners it can meet are the bar (gated above) and the full player (gated by the fold swap) — the flights
    // the card exists for. The full player's art, enabled at rest the same way, is why full player → bar always flew.
    val fullPlayer      = fullPlayerPresent(topIsPlayer = playerOnTop, composedFullPlayers = artGate.composedFullPlayers)
    val barArtMayMatch  = floatingArtMayMatch(
        fullPlayerPresent        = fullPlayer,
        otherSurfaceInTransition = surfaceTarget == PlayerSurface.Panel || panelTransition.currentState ||
            panelTransition.targetState || panelTransition.isRunning,
    )

    when {
        // (1) The panel's close gesture: the card retreats and the bar rises with the finger.
        panelProgress >= 0f -> LaunchedEffect(panelProgress, surfaceAfterPanelClose) {
            if (surfaceAfterPanelClose != surfaceState.currentState) {
                surfaceState.seekTo(panelProgress, surfaceAfterPanelClose)
            }
        }

        // (2) A back gesture off the full player: the surface below comes in with the finger, in step with the
        //     navigation host, which seeks its own transition from the same progress.
        playerOnTop && routeBackProgress != null && surfaceBelowPlayer != surfaceTarget ->
            LaunchedEffect(routeBackProgress, surfaceBelowPlayer) {
                surfaceState.seekTo(routeBackProgress, surfaceBelowPlayer)
            }

        // (3) Everything else — a tap, a push, a committed gesture (one frame later), a cancelled one, a fold swap.
        //     No spec on animateTo: the fraction then advances linearly and each child plays its own finite
        //     spec at its natural rate (a spec here would apply the easing twice).
        else -> LaunchedEffect(surfaceTarget) {
            val effectScope = this
            val snap = snapRequest.value == surfaceTarget
            snapRequest.value = null
            when {
                // A fold swap: the new surface is simply there.
                snap && surfaceState.currentState != surfaceTarget -> surfaceState.snapTo(surfaceTarget)
                surfaceState.currentState != surfaceTarget -> surfaceState.animateTo(surfaceTarget)
                surfaceState.targetState != surfaceTarget -> {
                    // A seek released without committing — the ONE abandoned-seek path: the art's key owes itself
                    // a fresh generation at the next quiet moment. Wind it back over the share it covered, then
                    // settle.
                    artKeys.onSeekAbandoned()
                    val from = surfaceState.fraction
                    animate(
                        initialValue  = from,
                        targetValue   = 0f,
                        animationSpec = tween(unwindMillis(from, surfaceTransition.totalDurationNanos, NavTransitionMillis)),
                    ) { value, _ ->
                        effectScope.launch { if (value > 0f) surfaceState.seekTo(value) }
                    }
                    surfaceState.snapTo(surfaceTarget)
                }
                else -> Unit
            }
        }
    }

    // ── The bar's placement, applied ────────────────────────────────────────────────────────────────────
    // It glides between two screens with the bar, and SNAPS when the bar is hidden, when the width class changed
    // since the placement it shows was computed (a fold: no visible re-layout afterwards), or when it leaves a
    // provisional placement.
    val barBottom         = remember { Animatable(placement.bottom, Dp.VectorConverter) }
    val barStart          = remember { Animatable(placement.start, Dp.VectorConverter) }
    val barWidth          = remember { Animatable(placement.widthFraction) }
    val placedWidth       = remember { Held(width) }
    val placedProvisional = remember { Held(placementProvisional) }
    val barShownNow by rememberUpdatedState(
        surfaceState.currentState == PlayerSurface.Bar || surfaceState.targetState == PlayerSurface.Bar,
    )
    LaunchedEffect(placement, width, placementProvisional) {
        val target = placement
        val motion = placementMotion(
            barShown          = barShownNow,
            widthClassChanged = placedWidth.value != width,
            fromProvisional   = placedProvisional.value,
        )
        placedWidth.value       = width
        placedProvisional.value = placementProvisional
        when (motion) {
            PlacementMotion.Snap    -> {
                barBottom.snapTo(target.bottom)
                barStart.snapTo(target.start)
                barWidth.snapTo(target.widthFraction)
            }
            PlacementMotion.Animate -> coroutineScope {
                launch { barBottom.animateTo(target.bottom, screenTransitionSpec()) }
                launch { barStart.animateTo(target.start, screenTransitionSpec()) }
                launch { barWidth.animateTo(target.widthFraction, screenTransitionSpec()) }
            }
        }
    }
    val barSlidePx   = with(density) { barBottom.targetValue.roundToPx() }
    val panelSlidePx = with(density) { (navigationBarBottom + PanelBottomMargin).roundToPx() }

    // The bar's side room: the rail on the Library (its width already holds the inset it consumed), else the
    // navigation bar / camera cutout on whichever side they sit in landscape — the larger of the two per side.
    val railStart  = barStart.value
    val railInsets = if (layoutDirection == LayoutDirection.Ltr) WindowInsets(left = railStart) else WindowInsets(right = railStart)
    val barSideInsets = WindowInsets.navigationBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
        .union(railInsets)

    var barHeightPx by remember { mutableIntStateOf(0) }
    val liveInset = when {
        surfaceTarget != PlayerSurface.Bar -> 0.dp
        barHeightPx > 0                    -> with(density) { barHeightPx.toDp() }
        else                               -> MiniBarEstimatedHeight
    }
    // Held while a screen without the surface is on top: the library sliding out under a pushed full player
    // keeps its layout, and comes back with it during a back gesture.
    val heldInset = remember { Held(liveInset) }
    if (surfaceAllowed) heldInset.value = liveInset
    val surfaceInset = heldInset.value

    // ONE lambda for the host's life, reading the latest values when called: a new instance per recomposition
    // would re-run the whole navigation host on every frame the host recomposes (a gesture, the bar's glide).
    val canShowPanelNow   by rememberUpdatedState(canShowPanel)
    val surfaceAllowedNow by rememberUpdatedState(surfaceAllowed)
    val topKeyNow         by rememberUpdatedState(topKey)
    val keyboardNow       by rememberUpdatedState(keyboard)
    val onRequestPlayer: () -> Unit = remember {
        {
            if (canShowPanelNow && surfaceAllowedNow) {
                // The card keeps its plain navigation-bar inset, so a keyboard left up (Search) would cover its
                // controls: drop the focus first.
                focusManager.clearFocus(force = true)
                keyboardNow?.hide()
                panelWanted = true
                panelOwner  = topKeyNow
            } else {
                openPlayerNow()
            }
        }
    }

    // The art key and gate reach all three "album-art" participants — the full player inside the content, the
    // bar and the card beside it — from ONE unconditional provider, so they always read the same generation.
    CompositionLocalProvider(
        LocalPlayerArtKey  provides artKey,
        LocalPlayerArtGate provides artGate,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // ONE content call site at every width and in every state.
            CompositionLocalProvider(
                LocalPlayerSurfaceInset  provides surfaceInset,
                LocalPlayerSurfaceChrome provides chrome,
                LocalPopOutPanelOpen     provides panelVisible,
                LocalAlbumArtColors      provides artColors,
            ) {
                content(onRequestPlayer)
            }

            // The scrim behind the card, read from the seekable state itself (fix round 2, item 41 — see PanelScrim).
            PanelScrim(
                surfaceState   = surfaceState,
                cardComposed   = canShowPanel,
                surfaceAllowed = surfaceAllowed,
                dismissible    = panelVisible,
                onDismiss      = { haptics.confirm(); closePanel() },
            )

            // The mini bar. A full-width strip with no pointer input of its own, so taps beside a 0.58 bar fall
            // through to the screen.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(barSideInsets)
                    // Its placement, or the keyboard's top when one is up (the Search tab focuses its field) —
                    // the LARGER of the two (a union), never their sum: the placement above the Library's bar
                    // already includes the navigation bar the keyboard covers.
                    .windowInsetsPadding(WindowInsets(bottom = barBottom.value).union(WindowInsets.ime)),
            ) {
                MiniPlayer(
                    player           = player,
                    track            = track,
                    isPlaying        = state.isPlaying,
                    durationMs       = state.durationMs,
                    tints            = tints,
                    barTransition    = barTransition,
                    slideDistancePx  = barSlidePx,
                    onExpand         = onRequestPlayer,
                    onHeightMeasured = { barHeightPx = it },
                    artMayMatch      = barArtMayMatch,
                    modifier         = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(barWidth.value),
                )
            }

            // The pop-out card, bottom-end. A sibling of the content, so composing it or not never touches the
            // content's composition (and with it every screen's saved state).
            if (canShowPanel) {
                PlayerPopOutPanel(
                    panelTransition = panelTransition,
                    player          = player,
                    state           = state,
                    tints           = tints,
                    slideDistancePx = panelSlidePx,
                    onClose         = { closePanel() },
                    onFullScreen    = onOpenPlayer,
                    onOpenQueue     = onOpenQueue,
                    modifier        = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        )
                        .padding(start = 8.dp, end = 16.dp, bottom = PanelBottomMargin)
                        .fillMaxWidth(POP_OUT_PANEL_WIDTH_FRACTION)
                        .heightIn(max = windowHeight * POP_OUT_PANEL_MAX_HEIGHT_FRACTION),
                )
            }

            // Back closes the panel, following the finger. Composed LAST and only while the panel shows, so it
            // registers after every handler inside the content and takes the gesture from them; it only records
            // the progress — the seeking, the commit and the unwind are the effects above.
            if (panelVisible) {
                PredictiveBackHandler { events ->
                    panelBackProgress = 0f
                    try {
                        events.collect { event -> panelBackProgress = event.progress.coerceIn(0f, 1f) }
                        panelBackProgress = NO_GESTURE
                        closePanel()
                    } catch (e: CancellationException) {
                        panelBackProgress = NO_GESTURE
                        throw e
                    }
                }
            }
        }
    }
}

/**
 * The scrim behind the pop-out card: `PANEL_SCRIM_ALPHA` × `panelScrimShare` of the surface's ONE seekable state —
 * its current and target states and its fraction on the card's own easing (`screenTransitionSpec()`'s
 * FastOutSlowIn, the curve every child plays at `fraction × duration`) — so it follows every seek, animation and
 * unwind of the card, and is 0 by construction after any snap away from it.
 *
 * Fix round 2, item 41 (Cris: after a fold swapped the card for the full player, back left "the dark scrim all over
 * the screen behind the mini bar"): the scrim used to be `panelTransition.animateFloat`. compose-animation
 * 1.13.0-beta01 leaves a CHILD transition's animation at its old value when the seekable state snaps to a third
 * state — the fold swap's `snapTo(None)` from the card — so it stayed at full alpha over the Library until a real
 * card animation ran (docs/MOTION.md W-2).
 *
 * Its own composable, so the fraction it reads on every frame of every surface transition recomposes this scope
 * only, not the host. Always composed (it emits nothing while hidden), so [PanelScrimSegment] sees every state.
 *
 * @param cardComposed the card can exist here (the host composes it only then): no card, no scrim — not even for
 *   the frame before a fold swap snaps the surface away from it.
 * @param surfaceAllowed the screen on top shows the floating surface. Over one without it (a pushed full player)
 *   the scrim draws only while the state targets the card: a back gesture seeking it in.
 * @param dismissible the card is actually open: only then does a tap on the scrim close it — a fading scrim with a
 *   pointer modifier would swallow taps meant for the screen beneath it.
 */
@Composable
private fun PanelScrim(
    surfaceState  : SeekableTransitionState<PlayerSurface>,
    cardComposed  : Boolean,
    surfaceAllowed: Boolean,
    dismissible   : Boolean,
    onDismiss     : () -> Unit,
) {
    val segment      = remember { PanelScrimSegment() }
    val target       = surfaceState.targetState
    val share        = segment.share(
        current  = surfaceState.currentState,
        target   = target,
        progress = FastOutSlowInEasing.transform(surfaceState.fraction),
    )
    val dismissLabel = stringResource(R.string.player_close_panel)
    if (cardComposed && (surfaceAllowed || target == PlayerSurface.Panel) && share > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = PANEL_SCRIM_ALPHA * share))
                .then(
                    if (dismissible) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication        = null,
                            onClickLabel      = dismissLabel,
                            onClick           = onDismiss,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
