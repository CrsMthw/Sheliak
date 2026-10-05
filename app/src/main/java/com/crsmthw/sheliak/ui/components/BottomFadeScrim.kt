package com.crsmthw.sheliak.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fade's own length above whatever navigation-bar inset is still owed to the screen. */
private val BottomFadeLength = 48.dp

/**
 * The insets a bottom fade spans: the navigation-bar inset STILL OWED to this subtree, plus [BottomFadeLength],
 * plus [extra]. Always read through an inset MODIFIER, never `WindowInsets.navigationBars.getBottom()`: a
 * modifier honours `consumeWindowInsets` from an ancestor, so under the navigation suite's bottom bar — which
 * covers the system inset itself and consumes it for its content — the fade is exactly 48dp, while on a screen
 * with no bar it reaches down into the gesture area as Lyra's did.
 */
@Composable
private fun bottomFadeInsets(extra: Dp): WindowInsets =
    WindowInsets.navigationBars.add(WindowInsets(bottom = BottomFadeLength + extra))

/**
 * The bottom room a scroller leaves so its last row can scroll clear of [BottomFadeScrim]: the trailing
 * `item` of a `LazyColumn`, or the trailing child of a `verticalScroll` `Column`. It spans the same insets as
 * the scrim, so the two can never disagree. [extra] adds the floating player surface's inset on the screens
 * that host it.
 */
@Composable
fun BottomFadeSpacer(extra: Dp = 0.dp) {
    Spacer(Modifier.windowInsetsBottomHeight(bottomFadeInsets(extra)))
}

/**
 * Fades scrolling content into [color] at the bottom edge — the scrim every scrolling screen carries so
 * rows dissolve into the edge instead of being cut off at it (edge-to-edge means the app draws there).
 *
 * Compose it in the screen's content `Box` AFTER the scroller, so it draws over the rows; it aligns itself to
 * the bottom and spans the owed navigation-bar inset plus 48dp. It is a plain background with no pointer
 * input, so drags that start on it still reach the rows below.
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
    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(bottomFadeInsets(extra = 0.dp))
            .background(Brush.verticalGradient(listOf(Color.Transparent, color))),
    )
}
