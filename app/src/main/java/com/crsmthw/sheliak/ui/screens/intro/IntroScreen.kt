package com.crsmthw.sheliak.ui.screens.intro

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding

/** One source the welcome screen offers: its icon, name and one-line description. */
private data class IntroSource(
    val icon: ImageVector,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
)

/** In the order they arrive: Plex first, then this device, Jellyfin and Samba. */
private val IntroSources = listOf(
    IntroSource(Icons.Outlined.Dns,          R.string.source_plex,     R.string.intro_source_plex_desc),
    IntroSource(Icons.Outlined.PhoneAndroid, R.string.source_local,    R.string.intro_source_local_desc),
    IntroSource(Icons.Outlined.Storage,      R.string.source_jellyfin, R.string.intro_source_jellyfin_desc),
    IntroSource(Icons.Outlined.FolderShared, R.string.source_samba,    R.string.intro_source_samba_desc),
)

/** Wider than this the column stops growing, so the cards stay readable on an unfolded or tablet screen. */
private val IntroMaxWidth = 560.dp

/**
 * The first-run welcome: the app name, one line on what Sheliak is, and the four kinds of source it reads.
 * No source can be added yet, so every card shows a "Coming next" state and the way forward is "Skip for now",
 * which stores `intro_done` — the shell then replaces this screen with Tracks.
 *
 * No app bar: the column takes the status-bar inset itself, and the skip button, the bottom-most element,
 * takes the navigation-bar inset.
 */
@Composable
fun IntroScreen(
    onSkip  : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Scaffold(
        modifier            = modifier,
        containerColor      = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .horizontalSystemBarsPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier            = Modifier
                    .widthIn(max = IntroMaxWidth)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text     = stringResource(R.string.intro_title),
                    style    = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text  = stringResource(R.string.intro_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                IntroSources.forEach { source -> IntroSourceCard(source) }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick  = { haptics.confirm(); onSkip() },
                    modifier = Modifier.align(Alignment.End).navigationBarsPadding(),
                ) {
                    Text(stringResource(R.string.intro_skip))
                }
            }
        }
    }
}

/**
 * One source card. Not clickable in M0 — nothing can be connected yet — so it is a plain card whose
 * "Coming next" label says why; TalkBack reads the name, the description and the label as one item.
 */
@Composable
private fun IntroSourceCard(source: IntroSource) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = source.icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(32.dp),
            )
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(source.nameRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    text  = stringResource(source.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = stringResource(R.string.intro_coming_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
