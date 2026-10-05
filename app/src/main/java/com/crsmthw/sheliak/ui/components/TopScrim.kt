package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * What to draw where content scrolls **under the status icons with no bar over it** — the two-pane
 * RIGHT pane, whose app bar belongs to the left pane, takes a top `contentPadding` inset plus this
 * scrim. Everywhere a real app bar sits on top, the bar paints its own strip and this is not needed.
 *
 * The vertical mirror of [BottomFadeScrim]: that one fades content toward [color] at the navigation
 * bar (`listOf(Transparent, color)`); this fades content toward [color] at the status bar
 * (`listOf(color, Transparent)`) so content scrolling under the status icons stays legible. Spans
 * the status-bar height plus a short tail; place it `align(Alignment.TopCenter)` over the content
 * (no `statusBarsPadding()` — it covers the bar).
 */
@Composable
fun TopScrim(color: Color, modifier: Modifier = Modifier) {
    val statusBarDp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarDp + 24.dp)
            .background(Brush.verticalGradient(listOf(color, Color.Transparent)))
    )
}
