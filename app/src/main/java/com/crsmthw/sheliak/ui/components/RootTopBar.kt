package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Pane height at or above which a root screen gets the **large flexible** app bar. Below it — the
 * folded outer screen in landscape, a ≈380dp-tall pane — a 152/120dp expanded bar would eat a third
 * of the pane before a single row (and over HALF of it with a 48dp row in [RootTopBar]'s `belowBar`
 * slot), so that case gets the small pinned bar. 600dp is the M3 medium-height boundary and clears
 * portrait on every screen as well as an unfolded / tablet landscape pane (≈800dp+).
 *
 * MEASURED height, not a window-size-class breakpoint: the height buckets are `[0, 480, 900]` and
 * have no 600dp boundary, so `isHeightAtLeastBreakpoint(600)` cannot express this. One constant, so
 * every reader of the gate agrees.
 */
val LargeBarMinPaneHeight = 600.dp

/** The 600dp height gate, read from the WINDOW — one reading for the bar and its behaviour. */
@Composable
private fun rootBarIsLarge(): Boolean {
    val paneHeightDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    return paneHeightDp >= LargeBarMinPaneHeight
}

/**
 * The scroll behaviour a root screen hangs on its own scroller (`Modifier.nestedScroll(
 * behavior.nestedScrollConnection)`), decided by the same height gate [RootTopBar] uses: an
 * `exitUntilCollapsed` behaviour over the hoisted [barState] for the large bar, a `pinned` one over
 * a throwaway state for the small bar. A separate function from [RootTopBar] because a composable
 * that EMITS UI must not also RETURN a value (lint `ComposableNaming`). Call this first, then pass
 * its result to [RootTopBar] and to the content's `nestedScroll`.
 *
 * Separate `if` branches on purpose — they are separate composition groups, so each keeps its own
 * state. The small branch must NOT inherit the hoisted [barState]: `PinnedScrollBehavior` never
 * writes `heightOffset` while `SingleRowTopAppBar` still passes `scrolledOffset = { heightOffset }`,
 * so a collapsed value carried across a portrait → folded landscape rotation would draw the small
 * bar shifted up and clipped (`adjustHeightOffsetLimit` fixes only the LIMIT, not the offset).
 *
 * The small branch is also the ONE bar reset in the app (a content switch never resets an app bar):
 * it clears the hoisted state so that state only ever describes the LARGE bar — without it a
 * collapse earned in portrait would still be sitting there when a rotation back re-entered the large
 * branch, over content that may have scrolled to the top meanwhile. A pinned bar cannot own a
 * collapse, which is why this is the one place a reset belongs. It runs from a `SideEffect`: same
 * frame, after this composition is applied and before measure, so there is no frame of a shifted
 * bar; and nothing in this branch READS the hoisted state, so the write cannot invalidate the
 * composition that performs it (a write-during-composition `remember { … }` would trip lint
 * `RememberReturnType`). The large branch has NO entry reset: the collapse the user last dragged is
 * simply rendered — an entry reset there would fire on every ordinary re-entry of the screen and
 * undo it.
 *
 * @param barState the bar's hoisted [TopAppBarState] — hoist it at screen level with
 *   `rememberTopAppBarState()` (already saveable) so the collapse survives navigating away and
 *   back. The LARGE branch renders from it; the small branch only CLEARS it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberRootTopBarScrollBehavior(
    barState : TopAppBarState = rememberTopAppBarState(),
): TopAppBarScrollBehavior =
    if (rootBarIsLarge()) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = barState)
    } else {
        SideEffect {
            barState.heightOffset  = 0f
            barState.contentOffset = 0f
        }
        TopAppBarDefaults.pinnedScrollBehavior(state = rememberTopAppBarState())
    }

/**
 * The shared **root-screen** app bar: a [LargeFlexibleTopAppBar] that compresses to the small bar
 * as the content scrolls and stays small until the content is back at the top (M3's own rule for
 * the flexible bars), or a plain small pinned [TopAppBar] on a pane too short for it. Takes the
 * behaviour [rememberRootTopBarScrollBehavior] returned — the two read the same height gate, so they
 * always agree on large vs small.
 *
 * Call it as the FIRST child of the screen's own `Column`, and hang the behaviour's connection on
 * the scroller inside the `Box(weight(1f))` below it:
 *
 * ```kotlin
 * Column(Modifier.fillMaxSize().horizontalSystemBarsPadding()) {
 *     val scrollBehavior = rememberRootTopBarScrollBehavior(barState)
 *     RootTopBar(title = …, scrollBehavior = scrollBehavior)
 *     Box(Modifier.fillMaxWidth().weight(1f)) {
 *         LazyColumn(
 *             modifier       = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
 *             contentPadding = PaddingValues(top = BarContentGap, …),
 *         ) { … }
 *         TopBarFade(paneColor = …)
 *         BottomFadeScrim()
 *     }
 * }
 * ```
 *
 * `Column { bar; … }` is valid rather than an overlay because `TopAppBarLayout` reports
 * `layout(maxWidth, (maxLayoutHeight + heightOffset).coerceAtLeast(0))` — a collapsing bar's
 * MEASURED height shrinks, it does not merely translate its content, so the content below moves up
 * with no dead gap. Nothing below it needs a top INSET: the bar owns that strip via
 * [appBarWindowInsets] (and never the M3 default, which would apply the horizontal sides a second
 * time on top of the screen's one `horizontalSystemBarsPadding()`).
 *
 * ### The `belowBar` slot
 *
 * Content pinned between the bar and the scroller — a `PrimaryTabRow`, a filter row. The bar and the
 * slot are emitted as ONE node (a `Column`), so the component stays a single emitter however it is
 * placed, and the slot rides directly under the bar: as the large bar collapses, the slot moves up
 * with it and the weighted content below follows. The slot does not scroll and is not part of the
 * collapse. It paints nothing of its own — its content paints the same [containerColor] as the bar
 * (a tab row's `containerColor`) so bar and slot read as one surface. The [BarContentGap] and
 * [TopBarFade] seam then belongs under the SLOT, i.e. in the weighted `Box`, exactly as without it.
 *
 * ### The gap and the fade under the bar
 *
 * Every caller gives its scroller [BarContentGap] of TOP `contentPadding` (a `verticalScroll`
 * `Column` takes it as a `padding` applied AFTER the scroller, which is the same thing) and composes
 * a [TopBarFade] as a late child of the weighted `Box`, so the first rows dissolve into the bar
 * instead of sliding past its title. Neither is an inset: the gap scrolls away with the content, and
 * because that `Box`'s own top edge already tracks this bar's collapse, the strip needs no offset —
 * a detail screen's OVERLAY bar is the case that does ([DetailTopBarFade]).
 *
 * ### Nothing resets the large bar's collapse
 *
 * The bar is where the user's last drag left it — across a filter change, a navigation round trip
 * and a re-entry of the screen: it stays collapsed or expanded until the user scrolls. A collapsed
 * bar is never a trap, because a scroller that can consume NOTHING still dispatches its whole drag
 * through nested scroll (`ScrollingLogic.performScroll` always calls
 * `dispatchPreScroll`/`dispatchPostScroll`, and `CanDragCalculation` only excludes a mouse) and
 * `ExitUntilCollapsedScrollBehavior.onPostScroll` re-expands from `available.y > 0` — so one
 * downward drag brings the big title back even over an empty page. Callers must therefore NOT reset
 * the bar from a control that swaps their content.
 *
 * The one state no large bar can produce is an offset inherited from the SMALL branch's pane, so
 * that is cleared where it is created (see [rememberRootTopBarScrollBehavior]). **Accepted cost:** a
 * collapse earned in portrait does not survive a round trip through the folded outer screen in
 * landscape — the bar comes back expanded, and one upward drag re-collapses it.
 *
 * @param title the bar's title. One line, ellipsized.
 * @param scrollBehavior what [rememberRootTopBarScrollBehavior] returned.
 * @param belowBar content pinned directly under the bar (see above). Empty by default.
 * @param subtitle optional second line. **If the text can only arrive later (a count landing from
 *   a provider), pass a non-null — possibly empty — string from the first frame**: the expanded
 *   height is pinned from this parameter's NULLNESS (152dp with a subtitle, 120dp without), so a
 *   slot that appears later would resize the bar under the user.
 * @param navigationIcon start slot — typically a back `IconButton`.
 * @param actions end slot.
 * @param containerColor the colour of **whatever is behind the bar**, used for both the resting and
 *   the scrolled container. Never leave M3's `scrolledContainerColor` default (`surfaceContainer`):
 *   the AMOLED overlay flattens only `background` / `surface` / `surfaceVariant`, so the default
 *   lights the bar up as a grey band on a pure-black theme the moment the content moves.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RootTopBar(
    title          : String,
    scrollBehavior : TopAppBarScrollBehavior,
    modifier       : Modifier = Modifier,
    belowBar       : @Composable () -> Unit = {},
    subtitle       : String? = null,
    navigationIcon : @Composable () -> Unit = {},
    actions        : @Composable RowScope.() -> Unit = {},
    containerColor : Color = MaterialTheme.colorScheme.background,
) {
    val useLargeBar = rootBarIsLarge()

    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor         = containerColor,
        scrolledContainerColor = containerColor,
    )
    val titleSlot: @Composable () -> Unit = {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val subtitleSlot: (@Composable () -> Unit)? = subtitle?.let { text ->
        { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }

    Column(modifier = modifier) {
        // Two branches, two composition groups — see `rememberRootTopBarScrollBehavior` for why.
        if (useLargeBar) {
            LargeFlexibleTopAppBar(
                title          = titleSlot,
                subtitle       = subtitleSlot,
                navigationIcon = navigationIcon,
                actions        = actions,
                // Pinned explicitly rather than left to the subtitle-dependent default, so the two
                // heights are stated where the nullness rule above is stated.
                expandedHeight = if (subtitleSlot != null)
                    TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
                else
                    TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight,
                windowInsets   = appBarWindowInsets,
                colors         = barColors,
                scrollBehavior = scrollBehavior,
            )
        } else {
            // The small bar has no nullable-subtitle overload — the subtitle one takes a non-null
            // slot, so the two cases are two calls. [subtitle] is a screen-level decision, so this
            // `if` never flips under the user.
            if (subtitleSlot != null) {
                TopAppBar(
                    title          = titleSlot,
                    subtitle       = subtitleSlot,
                    navigationIcon = navigationIcon,
                    actions        = actions,
                    windowInsets   = appBarWindowInsets,
                    colors         = barColors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title          = titleSlot,
                    navigationIcon = navigationIcon,
                    actions        = actions,
                    windowInsets   = appBarWindowInsets,
                    colors         = barColors,
                    scrollBehavior = scrollBehavior,
                )
            }
        }
        belowBar()
    }
}
