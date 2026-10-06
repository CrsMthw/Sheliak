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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding

/** One source the welcome screen offers: its icon, name, one-line description, and whether it can be set up yet. */
private data class IntroSource(
    val icon: ImageVector,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val available: Boolean,
)

/** In the order they arrive: Plex first (available since M1), then this device, Jellyfin and Samba. */
private val IntroSources = listOf(
    IntroSource(Icons.Outlined.Dns,          R.string.source_plex,     R.string.intro_source_plex_desc,     available = true),
    IntroSource(Icons.Outlined.PhoneAndroid, R.string.source_local,    R.string.intro_source_local_desc,    available = false),
    IntroSource(Icons.Outlined.Storage,      R.string.source_jellyfin, R.string.intro_source_jellyfin_desc, available = false),
    IntroSource(Icons.Outlined.FolderShared, R.string.source_samba,    R.string.intro_source_samba_desc,    available = false),
)

/** Wider than this the column stops growing, so the cards stay readable on an unfolded or tablet screen. */
private val IntroMaxWidth = 560.dp

/**
 * The first-run welcome: the app name, one line on what Sheliak is, and the four kinds of source it reads —
 * Plex "Available", the others "Coming next". The way forward is M0's: a choice stores `intro_done` and the shell
 * replaces this screen with the library. While no source exists the primary action is "Connect a Plex server"
 * ([onConnectPlex]: the shell then goes on to the Plex setup, over the library) with "Skip for now" beside it;
 * once one exists (a reinstall that kept its data) it is a plain "Continue" ([onSkip]).
 *
 * No app bar: the column takes the status-bar inset itself, and the action row, the bottom-most element, takes the
 * navigation-bar inset.
 */
@Composable
fun IntroScreen(
    viewModel    : IntroViewModel,
    onSkip       : () -> Unit,
    onConnectPlex: () -> Unit,
    modifier     : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val hasSources by viewModel.hasSources.collectAsStateWithLifecycle()
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
                Row(
                    modifier              = Modifier.align(Alignment.End).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Nothing until the sources are known, so the row never swaps its buttons under a finger.
                    when (hasSources) {
                        null  -> Unit
                        false -> {
                            TextButton(onClick = { haptics.confirm(); onSkip() }) {
                                Text(stringResource(R.string.intro_skip))
                            }
                            Button(onClick = { haptics.confirm(); onConnectPlex() }) {
                                Text(stringResource(R.string.intro_connect_plex))
                            }
                        }
                        true  -> Button(onClick = { haptics.confirm(); onSkip() }) {
                            Text(stringResource(R.string.intro_continue))
                        }
                    }
                }
            }
        }
    }
}

/**
 * One source card: a plain card whose label says whether it can be set up now ("Available") or later ("Coming
 * next"); TalkBack reads the name, the description and the label as one item. The setup itself is the primary
 * action below the cards.
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
                    text  = stringResource(if (source.available) R.string.intro_available else R.string.intro_coming_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (source.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
