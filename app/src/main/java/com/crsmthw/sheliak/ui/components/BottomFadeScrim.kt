package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fade's own length: the gradient above wherever the content ends. */
private val BottomFadeLength = 48.dp

/**
 * The bottom room the CURRENT navigation entry's lists keep for the floating player surface — the mini bar's
 * height while it shows over this entry, 0dp on an entry it never shows over (Settings, Sources, the Plex setup,
 * the full player, the queue) and outside the shell. Provided per entry by `SheliakNavGraph`
 * (`playerSurfaceRoomFor`), so it is never the value the host HOLDS for a screen without the surface.
 *
 * It is the default `extra` of [BottomFadeSpacer] — the room the last row scrolls clear of — and the room the
 * library's fast scroller keeps under its thumb. [BottomFadeScrim] never reads it: the fade stays at the content's
 * edge and the mini bar floats over it (fix round 2). A layout whose part the mini bar never covers (the list card
 * of a two-pane tab, beside the 0.58 bar) provides 0dp around that part.
 */
val LocalBottomFadeExtra = staticCompositionLocalOf { 0.dp }

/**
 * The navigation-bar inset STILL OWED to this subtree, bottom side only — the one part of a bottom fade's height
 * that is an inset. Always applied through an inset MODIFIER, never `WindowInsets.navigationBars.getBottom()`: a
 * modifier honours `consumeWindowInsets` from an ancestor, so under the navigation suite's bottom bar — which
 * covers the system inset itself and consumes it for its content — the owed inset is zero, while on a screen with
 * no bar it is the gesture area (CLAUDE.md rule 9).
 *
 * The fixed lengths (the 48dp fade, the spacer's room) are a plain `height` INSIDE that padding, never added to
 * the inset: an inset modifier subtracts what its ancestors consumed from the WHOLE inset it is given, so a single
 * `navigationBars + 48dp + room` lost the bar's full height under the compact bar (the suite consumes it) and the
 * keyboard's on the Search tab (`imePadding()` consumes it) — the fade and the spacer shrank to nothing there
 * (fix round 2, item 18).
 */
@Composable
private fun owedNavigationBarInset(): WindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)

/**
 * The bottom room a scroller leaves so its last row can scroll clear of everything over the content's bottom edge:
 * the trailing `item` of a `LazyColumn`, or the trailing child of a `verticalScroll` `Column`. It is the owed
 * navigation-bar inset + the LARGER of the fade's 48dp and [extra], the floating player surface's room — by default
 * the entry's own ([LocalBottomFadeExtra]).
 *
 * The larger, not the sum: the scrim stays at the content's edge and the mini bar floats over it, so the list alone
 * keeps the bar's room — without it the last row would come to rest behind the bar — and with the bar there the
 * fade lies behind the bar's card, so adding its 48dp on top would only leave a dead band above the card. With no
 * mini bar the spacer is the fade's height and the last row ends where the fade begins; with one, the last row
 * ends at the top of the bar's strip (the bar's own margin above its card).
 */
@Composable
fun BottomFadeSpacer(extra: Dp = LocalBottomFadeExtra.current) {
    Spacer(Modifier.windowInsetsPadding(owedNavigationBarInset()).height(maxOf(BottomFadeLength, extra)))
}

/**
 * Fades scrolling content into [color] where the content ENDS — the scrim every scrolling screen carries so
 * rows dissolve instead of being cut off by whatever sits below them (edge-to-edge means the app draws there).
 * Its bottom edge is the content's, and it never moves (fix round 2 — Cris: "it should stay above the navigation
 * bar regardless of mini bar present or not, so the fade is visible in the small gap under the mini player"):
 *
 * - the top of the compact navigation bar (the suite consumed the system inset it covers, so nothing else is owed);
 * - the top of the keyboard on the Search tab (its page is `imePadding()`ed, and the keyboard covers the inset);
 * - a card's bottom edge in a two-pane tab;
 * - the top of the system navigation bar where there is no bar.
 *
 * The mini bar floats OVER it: the gradient lies behind the bar's card, and its bottom end shows in the small gap
 * under the card and in the margins beside it. The list keeps the bar's room itself ([BottomFadeSpacer]), so the
 * scrim reads no room and has nothing to animate.
 *
 * The box spans the owed navigation-bar inset + 48dp. Its TOP 48dp is the gradient (transparent → [color]); below
 * it, over the gesture area of a screen without the bar, it is solid [color].
 *
 * Compose it in the screen's content `Box` AFTER the scroller, so it draws over the rows; it aligns itself to the
 * bottom. It is a plain background with no pointer input, so drags that start on it still reach the rows below.
 *
 * Inset rules that go with it:
 * - Neither the scroller nor its wrapper `Box` takes `navigationBarsPadding()` — the scroller ends with a
 *   [BottomFadeSpacer] instead, so content still draws behind the fade.
 * - Non-list states (empty, loading, error) apply `navigationBarsPadding()` on themselves.
 *
 * @param color the pane colour the content fades into — the screen's `surface` by default.
 */
@Composable
fun BoxScope.BottomFadeScrim(
    modifier: Modifier = Modifier,
    color   : Color = MaterialTheme.colorScheme.surface,
) {
    val fadePx = with(LocalDensity.current) { BottomFadeLength.toPx() }
    // The gradient covers the box's top [BottomFadeLength] only; the brush clamps to [color] below it.
    val brush = remember(color, fadePx) {
        Brush.verticalGradient(colors = listOf(Color.Transparent, color), startY = 0f, endY = fadePx)
    }
    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Before the padding, so the owed strip under the gradient is painted (solid) too.
            .background(brush)
            .windowInsetsPadding(owedNavigationBarInset())
            .height(BottomFadeLength),
    )
}
