package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.util.screenTransitionSpec

/**
 * The pop-out player panel (≥ 600dp, not short): a card anchored bottom-end over a scrim, holding
 * [PlayerCardContent]. Its presence is [panelTransition], the other child of `PlayerSurfaceHost`'s one seekable
 * transition, so it slides in as the mini bar slides out — the art flying between them because the two
 * visibility scopes are children of one transition — and its own predictive back seeks it back out to the bar.
 *
 * @param slideDistancePx how far below its own height the card travels to leave the screen (its bottom margin
 *   plus the navigation bar), so it slides fully out past the window edge.
 * @param artMayMatch whether the card art's permanent `"album-art"` registration may match right now (the host's
 *   `SharedContentConfig` gate: a full player or the mini bar must be part of the picture).
 */
@Composable
fun PlayerPopOutPanel(
    panelTransition: Transition<Boolean>,
    player         : PlayerStateManager,
    state          : PlayerState,
    tints          : PlayerTints,
    slideDistancePx: Int,
    onClose        : () -> Unit,
    onFullScreen   : () -> Unit,
    onOpenQueue    : () -> Unit,
    modifier       : Modifier = Modifier,
    artMayMatch    : Boolean = true,
) {
    panelTransition.AnimatedVisibility(
        visible  = { it },
        modifier = modifier,
        enter    = slideInVertically(screenTransitionSpec<IntOffset>()) { it + slideDistancePx },
        exit     = slideOutVertically(screenTransitionSpec<IntOffset>()) { it + slideDistancePx },
    ) {
        // Lyra's PreEnter bail-out (DESIGN §5.1): compose NOTHING unless the card is genuinely part of the current
        // or the target state. AnimatedVisibility also composes its content while its transition reports initial
        // value animations, and with the card and the bar as two children of ONE transition an interrupted change
        // on the bar sets that here too: the card would compose in PreEnter and register a second, non-target
        // "album-art" participant — the copy the match then takes for the outgoing one, i.e. a wrong flight.
        if (!panelTransition.currentState && !panelTransition.targetState) return@AnimatedVisibility

        Card(
            shape     = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
        ) {
            PlayerCardContent(
                player          = player,
                state           = state,
                tints           = tints,
                visibilityScope = this@AnimatedVisibility,
                artMayMatch     = artMayMatch,
                onClose         = onClose,
                onFullScreen    = onFullScreen,
                onOpenQueue     = onOpenQueue,
            )
        }
    }
}
