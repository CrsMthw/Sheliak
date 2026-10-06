package com.crsmthw.sheliak.ui.screens.sources

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.repository.Source
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.RootTopBar
import com.crsmthw.sheliak.ui.components.SettingsItem
import com.crsmthw.sheliak.ui.components.SettingsSectionHeader
import com.crsmthw.sheliak.ui.components.SettingsTip
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.rememberRootTopBarScrollBehavior
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press

/**
 * Settings → Sources: every configured source — its name, track count and sync status ("Syncing… N of M",
 * "Synced 5 minutes ago", "Sync failed: …") — with Sync now and Remove (behind a confirmation), then "Add Plex
 * server", which opens the Plex setup. The Settings anatomy: [RootTopBar] with a back arrow over a scrolling
 * column, the bar's connection outside the scroller.
 */
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel,
    onBack   : () -> Unit,
    onAddPlex: () -> Unit,
    modifier : Modifier = Modifier,
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val barState = rememberTopAppBarState()
    val paneColor = MaterialTheme.colorScheme.background
    // The source awaiting the user's confirmation to remove; saveable, so the dialog survives a rotation.
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier            = modifier,
        containerColor      = paneColor,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .horizontalSystemBarsPadding(),
        ) {
            val scrollBehavior = rememberRootTopBarScrollBehavior(barState)
            RootTopBar(
                title          = stringResource(R.string.sources_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { haptics.confirm(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                containerColor = paneColor,
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // ORDER IS LOAD-BEARING: the connection outside the scroller (see SettingsScreen).
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(top = BarContentGap),
                ) {
                    val list = sources
                    if (list != null) {
                        if (list.isEmpty()) {
                            SettingsTip(stringResource(R.string.sources_empty))
                        } else {
                            SettingsSectionHeader(stringResource(R.string.sources_section_servers))
                            list.forEach { source ->
                                SourceRow(
                                    source   = source,
                                    onSync   = { haptics.press(); viewModel.syncNow(source.instance.id) },
                                    onRemove = { haptics.press(); pendingRemoval = source.instance.id },
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        SettingsItem(
                            icon     = Icons.Outlined.Add,
                            title    = stringResource(R.string.sources_add_plex),
                            subtitle = stringResource(R.string.sources_add_plex_desc),
                            onClick  = onAddPlex,
                        )
                    }
                    BottomFadeSpacer()
                }
                TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
            }
        }
    }

    val removing = pendingRemoval?.let { id -> sources?.firstOrNull { it.instance.id == id } }
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title            = { Text(stringResource(R.string.sources_remove_title, removing.instance.displayName)) },
            text             = { Text(stringResource(R.string.sources_remove_body)) },
            confirmButton    = {
                TextButton(
                    onClick = {
                        haptics.confirm()
                        pendingRemoval = null
                        viewModel.remove(removing.instance.id)
                    },
                ) {
                    Text(stringResource(R.string.sources_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { haptics.press(); pendingRemoval = null }) {
                    Text(stringResource(R.string.sources_cancel))
                }
            },
        )
    }

    error?.let { failure ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title            = { Text(stringResource(R.string.sources_remove_failed)) },
            text             = { Text(stringResource(providerErrorMessage(failure))) },
            confirmButton    = {
                TextButton(onClick = { haptics.press(); viewModel.dismissError() }) {
                    Text(stringResource(R.string.sources_ok))
                }
            },
        )
    }
}

/**
 * One source: the server's name (user data, shown as is), "N tracks · <status>", and Sync now (disabled while it
 * syncs) / Remove at the end.
 */
@Composable
private fun SourceRow(
    source  : Source,
    onSync  : () -> Unit,
    onRemove: () -> Unit,
) {
    val status = sourceStatusOf(source.syncState, source.lastSyncAt)
    val count = pluralStringResource(R.plurals.library_track_count, source.trackCount, source.trackCount)
    ListItem(
        leadingContent    = {
            Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        content           = { Text(source.instance.displayName) },
        supportingContent = {
            Text(
                text  = stringResource(R.string.sources_row_summary, count, statusText(status)),
                color = if (status.key == SourceStatusKey.FAILED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent   = {
            Row {
                IconButton(onClick = onSync, enabled = !status.isRunning()) {
                    Icon(Icons.Outlined.Sync, contentDescription = stringResource(R.string.sources_sync_now))
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector        = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.sources_remove),
                        tint               = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

/** A source's status in words ([sourceStatusOf]). */
@Composable
private fun statusText(status: SourceStatus): String = when (status.key) {
    SourceStatusKey.NEVER_SYNCED     -> stringResource(R.string.sources_status_never)
    SourceStatusKey.SYNCED           -> stringResource(
        R.string.sources_status_synced,
        DateUtils.getRelativeTimeSpanString(
            status.syncedAt ?: 0L,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString(),
    )
    SourceStatusKey.RUNNING_OF_TOTAL -> stringResource(R.string.sources_status_running_of, status.done, status.total)
    SourceStatusKey.RUNNING_COUNT    -> stringResource(R.string.sources_status_running, status.done)
    SourceStatusKey.FAILED           -> stringResource(
        R.string.sources_status_failed,
        stringResource(providerErrorMessage(status.error ?: ProviderError.UNKNOWN)),
    )
}
