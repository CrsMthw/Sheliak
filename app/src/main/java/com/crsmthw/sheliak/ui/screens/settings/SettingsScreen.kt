package com.crsmthw.sheliak.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.BuildConfig
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.RootTopBar
import com.crsmthw.sheliak.ui.components.SettingsItem
import com.crsmthw.sheliak.ui.components.SettingsSectionHeader
import com.crsmthw.sheliak.ui.components.SettingsToggleItem
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.rememberRootTopBarScrollBehavior
import com.crsmthw.sheliak.ui.theme.ThemeMode
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding

/**
 * Settings, pushed from the gear in every destination's top bar. Three sections: **Sources** (where Plex and
 * the other sources will be added and managed — empty until the first provider lands), **Sheliak** (the
 * Theme sheet and the haptics switch) and **About** (the version and the open-source licences).
 *
 * It uses the root-screen bar (large flexible, collapsing) with a back arrow rather than a detail bar: it is a
 * long list with a title, not a page about one item.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack   : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val themeMode      by viewModel.themeMode.collectAsStateWithLifecycle()
    val amoledBlack    by viewModel.amoledBlack.collectAsStateWithLifecycle()
    val dynamicColor   by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val accentColor    by viewModel.accentColor.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    // Saveable, so an open sheet survives a rotation or a fold / unfold.
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    // Hoisted at screen level (`rememberTopAppBarState` is saveable), so the bar's collapse survives
    // navigating away and back.
    val barState = rememberTopAppBarState()
    val paneColor = MaterialTheme.colorScheme.background

    Scaffold(
        modifier            = modifier,
        containerColor      = paneColor,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // The screen's one horizontal inset; the sheet opened from here is its own window and insets itself.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .horizontalSystemBarsPadding(),
        ) {
            val scrollBehavior = rememberRootTopBarScrollBehavior(barState)
            RootTopBar(
                title          = stringResource(R.string.settings_title),
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
                        // ORDER IS LOAD-BEARING: the connection must sit OUTSIDE the scroller. Reversed, it is
                        // silently inert — the build stays green and the bar never collapses.
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        // After the scroller, so the gap under the bar is scroll content, not a dead strip.
                        .padding(top = BarContentGap),
                ) {
                    // ── Sources ───────────────────────────────────────────────────
                    SettingsSectionHeader(stringResource(R.string.settings_section_sources))
                    SettingsItem(
                        icon     = Icons.Outlined.LibraryAdd,
                        title    = stringResource(R.string.settings_sources_empty),
                        subtitle = stringResource(R.string.settings_sources_empty_desc),
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // ── Sheliak ───────────────────────────────────────────────────
                    SettingsSectionHeader(stringResource(R.string.app_name))
                    SettingsItem(
                        icon     = Icons.Outlined.Palette,
                        title    = stringResource(R.string.settings_theme),
                        subtitle = stringResource(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT  -> R.string.theme_mode_light
                                ThemeMode.DARK   -> R.string.theme_mode_dark
                            },
                        ),
                        onClick  = { showThemeSheet = true },
                    )
                    SettingsToggleItem(
                        icon            = Icons.Outlined.Vibration,
                        title           = stringResource(R.string.settings_haptics),
                        subtitle        = stringResource(R.string.settings_haptics_desc),
                        checked         = hapticsEnabled,
                        onCheckedChange = viewModel::setHapticsEnabled,
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // ── About ─────────────────────────────────────────────────────
                    SettingsSectionHeader(stringResource(R.string.settings_section_about))
                    SettingsItem(
                        icon     = Icons.Outlined.Info,
                        title    = stringResource(R.string.settings_version),
                        subtitle = BuildConfig.VERSION_NAME,
                    )
                    SettingsItem(
                        icon     = Icons.Outlined.Description,
                        title    = stringResource(R.string.settings_licences),
                        subtitle = stringResource(R.string.settings_licences_desc),
                    )

                    // The bottom room the fade scrim covers, so the last row can scroll clear of it.
                    BottomFadeSpacer()
                }

                TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
            }
        }
    }

    if (showThemeSheet) {
        ThemeSheet(
            themeMode    = themeMode,
            amoledBlack  = amoledBlack,
            dynamicColor = dynamicColor,
            accentColor  = accentColor,
            onThemeMode  = viewModel::setThemeMode,
            onAmoled     = viewModel::setAmoledBlack,
            onDynamic    = viewModel::setDynamicColor,
            onAccent     = viewModel::setAccentColor,
            onDismiss    = { showThemeSheet = false },
        )
    }
}
