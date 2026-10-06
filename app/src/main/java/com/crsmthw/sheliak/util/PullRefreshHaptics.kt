package com.crsmthw.sheliak.util

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Fires ONE [threshold] haptic each time a pull-to-refresh DRAG crosses its trigger point (Lyra's
 * `PullThresholdHaptics`), re-armed when the pull falls back below it.
 *
 * Only the user's own drag may buzz (haptics come from gestures, never from state): a box composed while a
 * refresh is already held ([isRefreshing] — a tab switch, a fold, a pop back during the round) animates itself up
 * to the threshold, and that crossing arms the check silently. A real pull crosses before its release, while
 * [isRefreshing] is still false; after the release the hold stays at the threshold, so it never fires twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullThresholdHaptics(state: PullToRefreshState, isRefreshing: Boolean) {
    val haptics = LocalHapticFeedback.current
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state) {
        var armed = false
        snapshotFlow { state.distanceFraction >= 1f }.collect { past ->
            when {
                past && !armed -> {
                    armed = true
                    if (!refreshing) haptics.threshold()
                }
                !past          -> armed = false
            }
        }
    }
}
