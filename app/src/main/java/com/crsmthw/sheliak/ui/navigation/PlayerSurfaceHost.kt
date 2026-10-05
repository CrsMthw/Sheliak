package com.crsmthw.sheliak.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The bottom space the floating player surface occupies over the screens — the mini bar's height plus its
 * margin while it shows, 0dp otherwise. Screens add it to their scroller's bottom padding and lift bottom-anchored
 * controls (the search FAB) by it, so nothing ever hides under the mini bar. Always 0dp until the player
 * lands; reading it now means the player can arrive without touching a single screen.
 */
val LocalPlayerSurfaceInset = staticCompositionLocalOf { 0.dp }

/**
 * THE host of the floating player surface: the mini bar and the pop-out panel, wrapped once around the whole
 * navigation host — so they survive every navigation instead of leaving and re-entering with each screen.
 * The navigation suite lives inside the Library entry, below this host; when the mini bar lands (M1) the
 * Library publishes its bar / rail extent to the host through a state the host provides, so the surface sits
 * above the bar on compact and beside the rail on wider windows, never over either.
 *
 * [content] receives `onRequestPlayer`, the one way a screen asks for the player: a push of the full player on
 * compact, the pop-out panel at wider widths once the player exists. Until then this is a pass-through that
 * fixes the API and the inset contract ([LocalPlayerSurfaceInset], 0dp) and always pushes the full player.
 *
 * @param onOpenPlayer pushes the full-player destination.
 */
@Composable
fun PlayerSurfaceHost(
    onOpenPlayer: () -> Unit,
    modifier    : Modifier = Modifier,
    content     : @Composable (onRequestPlayer: () -> Unit) -> Unit,
) {
    val inset: Dp = 0.dp
    Box(modifier = modifier) {
        CompositionLocalProvider(LocalPlayerSurfaceInset provides inset) {
            content(onOpenPlayer)
        }
    }
}
