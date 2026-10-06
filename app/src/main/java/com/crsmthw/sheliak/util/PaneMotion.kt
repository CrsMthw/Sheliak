package com.crsmthw.sheliak.util

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * A new selection in a two-pane library tab — **M3 lateral** (peer browse), Lyra's right-pane swap: the outgoing
 * detail slides fully off toward the start as the incoming one slides in from the end, both opaque, NO fade (M3
 * Lateral cautions against one, and a full-width opaque slide has nothing to flash). On [screenTransitionSpec], the
 * one finite envelope; no `SizeTransform` (`togetherWith`'s default is a spring, and the pane never changes size).
 * The pane's card clips the slide to itself.
 */
fun lateralPaneSwap(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(screenTransitionSpec()) { fullWidth -> fullWidth },
    initialContentExit = slideOutHorizontally(screenTransitionSpec()) { fullWidth -> -fullWidth },
    sizeTransform      = null,
)
