package com.crsmthw.sheliak.ui.screens.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.components.DetailTopBar
import com.crsmthw.sheliak.ui.navigation.LocalNavSharedTransitionScope
import com.crsmthw.sheliak.ui.player.LocalAlbumArtColors
import com.crsmthw.sheliak.ui.player.PAGE_TINT_ALPHA
import com.crsmthw.sheliak.ui.player.PlayerActionRow
import com.crsmthw.sheliak.ui.player.PlayerSeekBar
import com.crsmthw.sheliak.ui.player.PlayerTints
import com.crsmthw.sheliak.ui.player.PlayerTrackInfo
import com.crsmthw.sheliak.ui.player.PlayerTransport
import com.crsmthw.sheliak.ui.player.QualityTierChip
import com.crsmthw.sheliak.ui.player.SwipeableAlbumArt
import com.crsmthw.sheliak.ui.player.rememberPlayerTints
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding

/**
 * The full player — the compact player, and the full-screen player from the pop-out card or on a short window.
 *
 * Anatomy: the detail-screen one M0's placeholder fixed — an overlay [DetailTopBar] composed last over the page,
 * painted in the page's own colour, with the body padded by the bar. The page is tinted from the art (DESIGN
 * §4): the bar strip is the art's edge colour over the theme background, and below the bar that colour fades
 * into the background, so the bar and the page meet without a seam. Foregrounds stay on theme roles.
 *
 * Body: the large art — the `"album-art"` shared element on this entry's navigation scope, so it morphs from
 * the mini bar or the pop-out card — then title / artist / album, the seek bar with elapsed and remaining time,
 * the quality chip, the transport (shuffle · previous · play · next · repeat) and the action row (the reserved
 * Output slot, the Queue button). Side by side when the window is wider than tall (folded landscape).
 */
@Composable
fun PlayerScreen(
    player     : PlayerStateManager,
    onBack     : () -> Unit,
    onOpenQueue: () -> Unit,
    modifier   : Modifier = Modifier,
) {
    val state      by player.state.collectAsStateWithLifecycle()
    val haptics    = LocalHapticFeedback.current
    val tints      = rememberPlayerTints(LocalAlbumArtColors.current)
    val background = MaterialTheme.colorScheme.background
    val pageColor  = tints.edge.copy(alpha = PAGE_TINT_ALPHA).compositeOver(background)
    // The entry's own enter / exit is the art's visibility scope. Read only inside the shell: outside it there
    // is no shared scope to morph in.
    val visibilityScope: AnimatedVisibilityScope? =
        if (LocalNavSharedTransitionScope.current != null) LocalNavAnimatedContentScope.current else null

    Box(modifier = modifier.fillMaxSize()) {
        // The page: the bar's strip in the page colour, then the same colour fading into the background.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageColor)
                    .statusBarsPadding()
                    .height(TopAppBarDefaults.TopAppBarExpandedHeight),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Brush.verticalGradient(listOf(pageColor, background))),
            )
        }

        Box(modifier = Modifier.fillMaxSize().horizontalSystemBarsPadding()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = TopAppBarDefaults.TopAppBarExpandedHeight)
                    .navigationBarsPadding(),
            ) {
                if (maxWidth > maxHeight) {
                    LandscapeBody(state, player, tints, visibilityScope, onOpenQueue)
                } else {
                    PortraitBody(state, player, tints, visibilityScope, onOpenQueue)
                }
            }

            DetailTopBar(
                paneColor      = pageColor,
                modifier       = Modifier.align(Alignment.TopCenter),
                navigationIcon = {
                    IconButton(onClick = { haptics.confirm(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                title          = { titleModifier ->
                    Text(stringResource(R.string.player_title), modifier = titleModifier)
                },
            )
        }
    }
}

/** Art on top, controls below (portrait, and any window taller than wide). */
@Composable
private fun PortraitBody(
    state          : PlayerState,
    player         : PlayerStateManager,
    tints          : PlayerTints,
    visibilityScope: AnimatedVisibilityScope?,
    onOpenQueue    : () -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier         = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            SwipeableAlbumArt(
                track           = state.track,
                queueIndex      = state.queueIndex,
                visibilityScope = visibilityScope,
                size            = minOf(maxWidth, maxHeight),
                onNext          = player::next,
                onPrevious      = player::previous,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 12.dp),
        ) {
            PlayerControls(state, player, tints, onOpenQueue)
        }
    }
}

/** Art on the start side, controls beside it (a window wider than tall: folded landscape). */
@Composable
private fun LandscapeBody(
    state          : PlayerState,
    player         : PlayerStateManager,
    tints          : PlayerTints,
    visibilityScope: AnimatedVisibilityScope?,
    onOpenQueue    : () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier         = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 8.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            SwipeableAlbumArt(
                track           = state.track,
                queueIndex      = state.queueIndex,
                visibilityScope = visibilityScope,
                size            = minOf(maxWidth, maxHeight),
                onNext          = player::next,
                onPrevious      = player::previous,
            )
        }
        // Centred in the pane; scrolls only when a very short window cannot fit the controls.
        BoxWithConstraints(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
        ) {
            val viewport = maxHeight
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                PlayerControls(state, player, tints, onOpenQueue)
            }
        }
    }
}

/** Title block, seek bar, quality chip, transport and actions — one column, shared by both layouts. */
@Composable
private fun ColumnScope.PlayerControls(
    state      : PlayerState,
    player     : PlayerStateManager,
    tints      : PlayerTints,
    onOpenQueue: () -> Unit,
) {
    val track = state.track
    PlayerTrackInfo(
        track      = track,
        titleStyle = MaterialTheme.typography.titleLarge,
        showAlbum  = true,
        modifier   = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    PlayerSeekBar(
        player     = player,
        trackKey   = track?.key,
        durationMs = state.durationMs,
        tint       = tints.surfaceAccent,
        modifier   = Modifier.fillMaxWidth(),
    )
    QualityTierChip(
        format   = state.liveFormat ?: track?.format,
        tint     = tints.surfaceAccent,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 4.dp),
    )
    Spacer(Modifier.height(12.dp))
    PlayerTransport(state = state, player = player, tints = tints)
    Spacer(Modifier.height(12.dp))
    PlayerActionRow(tint = tints.surfaceAccent, onOpenQueue = onOpenQueue)
}
