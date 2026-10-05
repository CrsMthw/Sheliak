package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.RootTopBar
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.rememberRootTopBarScrollBehavior
import com.crsmthw.sheliak.ui.navigation.LocalPlayerSurfaceInset
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press

/**
 * The one layout of the four navigation-suite destinations (Tracks, Albums, Artists, Playlists): a
 * [RootTopBar] carrying the Settings gear, then a scrolling list with the app-wide seam under the bar
 * ([BarContentGap] + [TopBarFade]) and the [BottomFadeScrim] over the bottom edge, then the screen's floating
 * action (the compact search FAB, or nothing on rail widths, where search lives in the rail header).
 *
 * One composable so the four screens cannot drift apart in insets, scroll wiring or seams; each screen only
 * supplies its title, subtitle and list content.
 *
 * @param subtitle pass non-null from the first frame whenever the screen has one (see [RootTopBar]).
 * @param floatingAction composed last, over the list, in the content `Box`.
 */
@Composable
internal fun LibraryRootLayout(
    title         : String,
    onOpenSettings: () -> Unit,
    floatingAction: @Composable BoxScope.() -> Unit,
    modifier      : Modifier = Modifier,
    subtitle      : String? = null,
    content       : LazyListScope.() -> Unit,
) {
    // Hoisted at screen level (`rememberTopAppBarState` is saveable), so the bar's collapse survives
    // navigating away and back.
    val barState = rememberTopAppBarState()
    val paneColor = MaterialTheme.colorScheme.background

    Scaffold(
        modifier            = modifier,
        containerColor      = paneColor,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // The screen's one horizontal inset: a 3-button navigation bar or a camera cutout on a side edge.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .horizontalSystemBarsPadding(),
        ) {
            val scrollBehavior = rememberRootTopBarScrollBehavior(barState)
            RootTopBar(
                title          = title,
                subtitle       = subtitle,
                scrollBehavior = scrollBehavior,
                actions        = { SettingsAction(onClick = onOpenSettings) },
                containerColor = paneColor,
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier       = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = BarContentGap),
                ) {
                    content()
                    // The bottom room the fade scrim covers, plus the player surface's inset, so the last row
                    // can scroll clear of both. An inset-aware item rather than `contentPadding`, because only
                    // an inset modifier knows what the navigation suite's bar has already consumed.
                    item(key = BottomRoomKey, contentType = BottomRoomKey) {
                        BottomFadeSpacer(extra = LocalPlayerSurfaceInset.current)
                    }
                }
                TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
                floatingAction()
            }
        }
    }
}

/** Key of the trailing bottom-room item; screens' own items never use it. */
private const val BottomRoomKey = "library-bottom-room"

/** The Settings gear every destination's top bar carries. */
@Composable
private fun SettingsAction(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    IconButton(onClick = { haptics.press(); onClick() }) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
    }
}
