package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.ui.navigation.LocalNavSharedTransitionScope
import com.crsmthw.sheliak.util.artBoundsTransform

/*
 * The player surfaces' motion and geometry constants, and the one `"album-art"` shared-element modifier.
 * Every animation here runs on util/Motion.kt's finite specs; this file adds no spec of its own
 * (docs/MOTION.md → "Floating player surface").
 */

/**
 * The shared-element key of the now-playing art: the mini bar, the pop-out card and the full player register
 * it, in the ONE navigation-scope `SharedTransitionScope` (`LocalNavSharedTransitionScope`). One plain key —
 * Lyra's per-generation keys are not ported (docs/MOTION.md → "Not ported").
 */
const val ALBUM_ART_SHARED_KEY = "album-art"

/**
 * The art size every `"album-art"` participant — and the colour sampler — asks the provider for. One size, so
 * the three surfaces load ONE model (one cache entry), and the morph back down to the mini bar draws a sharp
 * bitmap instead of a 48dp decode blown up.
 */
const val PLAYER_ART_SIZE_PX = 1024

/** The art size of a queue row (48dp). */
const val QUEUE_ART_SIZE_PX = 128

/** The pop-out panel's width, as a share of the window (Lyra's Search panel). */
const val POP_OUT_PANEL_WIDTH_FRACTION = 0.54f

/** The pop-out panel's maximum height, as a share of the window height. */
const val POP_OUT_PANEL_MAX_HEIGHT_FRACTION = 0.8f

/** The scrim behind the pop-out panel, at full opacity of the transition (theme `scrim` at this alpha). */
const val PANEL_SCRIM_ALPHA = 0.45f

/** The full player's page tint: the art's edge colour at this alpha over the theme background. */
const val PAGE_TINT_ALPHA = 0.4f

/** The pop-out card's gradient top: the art's edge colour at this alpha over the card colour. */
const val CARD_TINT_ALPHA = 0.55f

/** A horizontal swipe on the art past this skips a track. */
val ArtSwipeThreshold = 80.dp

/** How far the art follows the finger during that swipe (it moves at 0.3 of the finger, capped here). */
val ArtSwipeMaxTravel = 80.dp

/** The share of the finger's travel the art follows during a swipe. */
const val ART_SWIPE_FOLLOW = 0.3f

/**
 * Registers this art as the `"album-art"` shared element in the navigation scope, riding [visibilityScope]'s
 * enter / exit: the mini bar's and the pop-out card's own `AnimatedVisibility` (children of the host's one
 * seekable transition), or the full player's `LocalNavAnimatedContentScope`. Bounds run on
 * [artBoundsTransform] — the finite envelope — so the navigation transition settles on the frame the morph
 * ends.
 *
 * Each surface registers exactly ONCE and only ever in this one scope, so no scope ever holds two competing
 * participants and the modifier chain never changes shape: no `SharedContentConfig` gating is needed. A no-op
 * outside the shell (no scope) or without a visibility scope.
 */
@Composable
fun Modifier.albumArtSharedElement(visibilityScope: AnimatedVisibilityScope?): Modifier {
    val sharedScope = LocalNavSharedTransitionScope.current
    if (sharedScope == null || visibilityScope == null) return this
    return with(sharedScope) {
        this@albumArtSharedElement.sharedElement(
            sharedContentState      = rememberSharedContentState(key = ALBUM_ART_SHARED_KEY),
            animatedVisibilityScope = visibilityScope,
            boundsTransform         = artBoundsTransform(),
        )
    }
}
