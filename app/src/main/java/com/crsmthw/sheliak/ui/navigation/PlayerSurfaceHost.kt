package com.crsmthw.sheliak.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.exclude
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
import com.crsmthw.sheliak.ui.player.LocalAlbumArtColors
import com.crsmthw.sheliak.ui.player.MiniPlayer
import com.crsmthw.sheliak.ui.player.PANEL_SCRIM_ALPHA
import com.crsmthw.sheliak.ui.player.POP_OUT_PANEL_MAX_HEIGHT_FRACTION
import com.crsmthw.sheliak.ui.player.POP_OUT_PANEL_WIDTH_FRACTION
import com.crsmthw.sheliak.ui.player.PlayerPopOutPanel
import com.crsmthw.sheliak.ui.player.PlayerSurface
import com.crsmthw.sheliak.ui.player.miniBarPlacement
import com.crsmthw.sheliak.ui.player.playerSurfaceFor
import com.crsmthw.sheliak.ui.player.popOutAllowed
import com.crsmthw.sheliak.ui.player.rememberAlbumArtColors
import com.crsmthw.sheliak.ui.player.rememberPlayerTints
import com.crsmthw.sheliak.ui.player.unwindMillis
import com.crsmthw.sheliak.util.NavTransitionMillis
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.screenTransitionSpec
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
 * scroller's bottom room and lift bottom-anchored controls (the search FAB) by it, so nothing hides under the
 * bar. It is a plain value that changes only when the bar comes or goes — never animated, because a static local
 * recomposes everything it is provided to (a control that should glide animates its own copy).
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

/** Non-snapshot cell written during composition and read back on the next pass (Lyra's holder idiom). */
private class Held<T>(var value: T)

/** "No panel back gesture in progress" for the gesture-progress state (a float state, not a nullable box). */
private const val NO_GESTURE = -1f

/** The mini bar's height before its first measure: the 72dp card (art, padding, progress line) + its 8dp margins. */
private val MiniBarEstimatedHeight = 88.dp

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
 *   an abandoned seek back — Navigation's own pattern, on the finite specs.
 *
 * ### Placement
 * Compact: full width, directly above the Library's bottom bar while the Library is on top, on the system
 * navigation bar on every other screen, gliding between the two with the page transition. ≥ 600dp: after the
 * rail, full width over single-pane screens and 0.58 of the width, end-aligned, over two-pane ones; a tap
 * opens the pop-out card bottom-end over a scrim instead of the full player — except on short windows
 * (< 500dp tall), where it opens the full player.
 *
 * @param topKey the back stack's top entry.
 * @param surfaceAllowed whether [topKey] shows the floating surface (`playerSurfaceAllowed(topKey)`).
 * @param onOpenPlayer pushes the full player.
 * @param onOpenQueue pushes the queue (from the pop-out card).
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
    val windowHeight    = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val width           = currentWindowWidth()
    val wide            = width != WindowWidth.Compact
    val canShowPanel    = popOutAllowed(width, windowHeight.value)
    val onLibrary       = topKey == Library

    val chrome    = remember { PlayerSurfaceChrome() }
    val artColors = rememberAlbumArtColors(track?.art)
    val tints     = rememberPlayerTints(artColors)

    // ── The panel: the user's intent, and the screen it belongs to ──────────────────────────────────────
    // Saveable, so an open panel survives process death like any other visible UI state.
    var panelWanted by rememberSaveable { mutableStateOf(false) }
    // The surface-allowed screen the panel was opened over. While the full player or the queue is on top the
    // panel is HIDDEN, not closed (it is the art's other end on the way back); once a DIFFERENT allowed screen
    // is on top, the intent is spent. Null (after a restore) counts as "this screen".
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
    // Folding below the panel's width (or into a short window) spends the intent: the panel cannot exist here.
    LaunchedEffect(canShowPanel) {
        if (!canShowPanel) closePanel()
    }
    // Playback ending closes it too, so a later track does not bring a panel back nobody asked for.
    val hasTrackNow = rememberUpdatedState(hasTrack)
    LaunchedEffect(Unit) {
        snapshotFlow { hasTrackNow.value }.drop(1).collect { if (!it) closePanel() }
    }

    // ── The one transition ──────────────────────────────────────────────────────────────────────────────
    val surfaceTarget = playerSurfaceFor(
        hasTrack           = hasTrack,
        routeAllowsSurface = surfaceAllowed,
        panelWanted        = panelOwned,
        canShowPanel       = canShowPanel,
    )
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

    when {
        // (1) The panel's close gesture: the card retreats and the bar rises with the finger.
        panelProgress >= 0f -> LaunchedEffect(panelProgress, surfaceAfterPanelClose) {
            if (surfaceAfterPanelClose != surfaceState.currentState) {
                surfaceState.seekTo(panelProgress, surfaceAfterPanelClose)
            }
        }

        // (2) A back gesture off the full player: the surface below comes in with the finger, in step with the
        //     navigation host, which seeks its own transition from the same progress.
        topKey == Player && routeBackProgress != null && surfaceBelowPlayer != surfaceTarget ->
            LaunchedEffect(routeBackProgress, surfaceBelowPlayer) {
                surfaceState.seekTo(routeBackProgress, surfaceBelowPlayer)
            }

        // (3) Everything else — a tap, a push, a committed gesture (one frame later), a cancelled one.
        //     No spec on animateTo: the fraction then advances linearly and each child plays its own finite
        //     spec at its natural rate (a spec here would apply the easing twice).
        else -> LaunchedEffect(surfaceTarget) {
            val effectScope = this
            when {
                surfaceState.currentState != surfaceTarget -> surfaceState.animateTo(surfaceTarget)
                surfaceState.targetState != surfaceTarget -> {
                    // A seek released without committing: wind it back over the share it covered, then settle.
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

    // ── Placement of the bar ────────────────────────────────────────────────────────────────────────────
    // The navigation-bar inset is read as a VALUE here and only here — the one exception CLAUDE.md rule 9 grants
    // to this host: it sits ABOVE the navigation suite, so nothing has consumed the inset yet, and the offset
    // must animate between it and the Library's bar height. Held while the bar is hidden, so a push to the full
    // player does not retarget a bar that is sliding away; re-seeded across the 600dp line.
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val placement = miniBarPlacement(
        onLibrary           = onLibrary,
        wide                = wide,
        libraryBottomBar    = chrome.bottomBar,
        libraryStartRail    = chrome.startRail,
        libraryTwoPane      = chrome.twoPane,
        navigationBarBottom = navigationBarBottom,
    )
    val heldPlacement = remember(wide) { Held(placement) }
    if (surfaceAllowed) heldPlacement.value = placement
    val target = heldPlacement.value
    val barBottom by animateDpAsState(target.bottom, screenTransitionSpec(), label = "miniBarBottom")
    val barStart  by animateDpAsState(target.start, screenTransitionSpec(), label = "miniBarStart")
    val barWidth  by animateFloatAsState(target.widthFraction, screenTransitionSpec(), label = "miniBarWidth")
    val barSlidePx   = with(density) { target.bottom.roundToPx() }
    val panelSlidePx = with(density) { (navigationBarBottom + PanelBottomMargin).roundToPx() }

    // The bar's side room: the rail on the Library (its width already holds the inset it consumed), else the
    // navigation bar / camera cutout on whichever side they sit in landscape — the larger of the two per side.
    val railInsets = if (layoutDirection == LayoutDirection.Ltr) WindowInsets(left = barStart) else WindowInsets(right = barStart)
    val barSideInsets = WindowInsets.navigationBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
        .union(railInsets)
    // Above the keyboard when one is up (Search focuses its field): the IME's share above the navigation bar.
    val imeAboveNavigationBar = WindowInsets.ime.exclude(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)

    var barHeightPx by remember { mutableIntStateOf(0) }
    val liveInset = when {
        surfaceTarget != PlayerSurface.Bar -> 0.dp
        barHeightPx > 0                    -> with(density) { barHeightPx.toDp() }
        else                               -> MiniBarEstimatedHeight
    }
    // Held while a screen without the surface is on top: the library sliding out under a pushed full player
    // keeps its layout (its search FAB stays lifted), and comes back with it during a back gesture.
    val heldInset = remember { Held(liveInset) }
    if (surfaceAllowed) heldInset.value = liveInset
    val surfaceInset = heldInset.value

    // The scrim's alpha rides the panel's own transition, so it seeks with the panel's gestures and can never
    // drift from the card's slide.
    val scrimAlpha by panelTransition.animateFloat(
        transitionSpec = { screenTransitionSpec() },
        label          = "panelScrim",
    ) { shown -> if (shown) PANEL_SCRIM_ALPHA else 0f }

    // ONE lambda for the host's life, reading the latest values when called: a new instance per recomposition
    // would re-run the whole navigation host on every frame the host recomposes (a gesture, the bar's glide).
    val canShowPanelNow   by rememberUpdatedState(canShowPanel)
    val surfaceAllowedNow by rememberUpdatedState(surfaceAllowed)
    val topKeyNow         by rememberUpdatedState(topKey)
    val openPlayerNow     by rememberUpdatedState(onOpenPlayer)
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

        // The scrim behind the panel. Tap-to-dismiss only while the panel is actually open — a fading scrim
        // with a pointer modifier would swallow taps meant for the screen beneath it. Drawn over a pushed full
        // player only while the panel's own transition targets it (a back gesture seeking the panel in).
        val dismissLabel = stringResource(R.string.player_close_panel)
        if ((surfaceAllowed || panelTransition.targetState) && scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                    .then(
                        if (panelVisible) {
                            Modifier.clickable(
                                interactionSource = null,
                                indication        = null,
                                onClickLabel      = dismissLabel,
                            ) { haptics.confirm(); closePanel() }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        // The mini bar. A full-width strip with no pointer input of its own, so taps beside a 0.58 bar fall
        // through to the screen.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(barSideInsets)
                .padding(bottom = barBottom)
                .windowInsetsPadding(imeAboveNavigationBar),
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
                modifier         = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(barWidth),
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
        // registers after every handler inside the content and takes the gesture from them; it only records the
        // progress — the seeking, the commit and the unwind are the effects above.
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
