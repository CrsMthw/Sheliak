package com.crsmthw.sheliak.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.util.screenTransitionSpec

/** The fade's own length: the gradient above wherever the content ends. */
private val BottomFadeLength = 48.dp

/**
 * The bottom room the CURRENT navigation entry keeps for the floating player surface — the mini bar's height
 * while it shows over this entry, 0dp on an entry it never shows over (Settings, Sources, the Plex setup, the
 * full player, the queue) and outside the shell. Provided per entry by `SheliakNavGraph`
 * (`playerSurfaceRoomFor`), so it is never the value the host HOLDS for a screen without the surface.
 *
 * It is the default `extra` of both [BottomFadeScrim] and [BottomFadeSpacer], so on every list the fade ends
 * exactly where the room the last row scrolls clear of begins. A layout whose part the mini bar never covers (the
 * list card of a two-pane tab, beside the 0.58 bar) provides 0dp around that part.
 */
val LocalBottomFadeExtra = staticCompositionLocalOf { 0.dp }

/**
 * The insets a bottom fade spans: the navigation-bar inset STILL OWED to this subtree, plus [BottomFadeLength],
 * plus [extra]. Always read through an inset MODIFIER, never `WindowInsets.navigationBars.getBottom()`: a
 * modifier honours `consumeWindowInsets` from an ancestor, so under the navigation suite's bottom bar — which
 * covers the system inset itself and consumes it for its content — the owed inset is zero, while on a screen
 * with no bar it is the gesture area (CLAUDE.md rule 9).
 */
@Composable
private fun bottomFadeInsets(extra: Dp): WindowInsets =
    WindowInsets.navigationBars.add(WindowInsets(bottom = BottomFadeLength + extra))

/**
 * The bottom room a scroller leaves so its last row can scroll clear of [BottomFadeScrim]: the trailing
 * `item` of a `LazyColumn`, or the trailing child of a `verticalScroll` `Column`. It spans the same insets as
 * the scrim, so the two can never disagree. [extra] is the floating player surface's room — by default the
 * entry's own ([LocalBottomFadeExtra]), exactly what the scrim reads.
 */
@Composable
fun BottomFadeSpacer(extra: Dp = LocalBottomFadeExtra.current) {
    Spacer(Modifier.windowInsetsBottomHeight(bottomFadeInsets(extra)))
}

/**
 * Fades scrolling content into [color] where the content ENDS — the scrim every scrolling screen carries so
 * rows dissolve instead of being cut off by whatever sits below them (edge-to-edge means the app draws there):
 *
 * - above the compact navigation bar (the suite consumed the system inset it covers, so nothing else is owed);
 * - above the mini bar while it shows over this entry ([extra], the entry's [LocalBottomFadeExtra]);
 * - above the system navigation bar when neither is there.
 *
 * The box spans the owed navigation-bar inset + [extra] + 48dp. Its TOP 48dp is the gradient (transparent →
 * [color]); below it — behind the mini bar and its margins, and over the gesture area — it is solid [color], so
 * rows never show through around the mini bar's card. The room follows [extra] on the one finite spec, so the
 * gradient glides up and down with the mini bar instead of jumping when playback starts or stops.
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
 * @param extra the room kept for the floating player surface — the entry's [LocalBottomFadeExtra] by default;
 *   pass the same value as the scroller's [BottomFadeSpacer].
 */
@Composable
fun BoxScope.BottomFadeScrim(
    modifier: Modifier = Modifier,
    color   : Color = MaterialTheme.colorScheme.surface,
    extra   : Dp = LocalBottomFadeExtra.current,
) {
    val room by animateDpAsState(
        targetValue   = extra,
        animationSpec = screenTransitionSpec(),
        label         = "bottomFadeRoom",
    )
    val fadePx = with(LocalDensity.current) { BottomFadeLength.toPx() }
    // The gradient covers the box's top [BottomFadeLength] only; the brush clamps to [color] below it.
    val brush = remember(color, fadePx) {
        Brush.verticalGradient(colors = listOf(Color.Transparent, color), startY = 0f, endY = fadePx)
    }
    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(bottomFadeInsets(extra = room))
            .background(brush),
    )
}
