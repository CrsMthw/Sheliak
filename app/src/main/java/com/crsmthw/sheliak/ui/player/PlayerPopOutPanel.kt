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
) {
    panelTransition.AnimatedVisibility(
        visible  = { it },
        modifier = modifier,
        enter    = slideInVertically(screenTransitionSpec<IntOffset>()) { it + slideDistancePx },
        exit     = slideOutVertically(screenTransitionSpec<IntOffset>()) { it + slideDistancePx },
    ) {
        Card(
            shape     = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
        ) {
            PlayerCardContent(
                player          = player,
                state           = state,
                tints           = tints,
                visibilityScope = this@AnimatedVisibility,
                onClose         = onClose,
                onFullScreen    = onFullScreen,
                onOpenQueue     = onOpenQueue,
            )
        }
    }
}
