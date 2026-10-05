package com.crsmthw.sheliak.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.DetailTopBar
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press

/**
 * Placeholder for the full player, so the route, its back behaviour and its bar exist before playback does.
 * It already uses the detail-screen anatomy the real player keeps: an overlay [DetailTopBar] composed last
 * over the page, painted in the page's own colour, with the body padded by the bar's height.
 */
@Composable
fun PlayerScreen(
    onBack     : () -> Unit,
    onOpenQueue: () -> Unit,
    modifier   : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val paneColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(paneColor)
            .horizontalSystemBarsPadding(),
    ) {
        Text(
            text      = stringResource(R.string.player_placeholder),
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .align(Alignment.Center)
                .statusBarsPadding()
                .padding(top = TopAppBarDefaults.TopAppBarExpandedHeight)
                .padding(horizontal = 32.dp),
        )
        DetailTopBar(
            paneColor      = paneColor,
            modifier       = Modifier.align(Alignment.TopCenter),
            navigationIcon = {
                IconButton(onClick = { haptics.confirm(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
            actions        = {
                IconButton(onClick = { haptics.press(); onOpenQueue() }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = stringResource(R.string.queue_title))
                }
            },
            title          = { titleModifier ->
                Text(stringResource(R.string.player_title), modifier = titleModifier)
            },
        )
    }
}
