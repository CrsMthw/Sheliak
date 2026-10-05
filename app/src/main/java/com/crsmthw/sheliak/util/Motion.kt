package com.crsmthw.sheliak.util

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect

/**
 * The one duration every screen-level transition shares: the nav slide, its paired fade, and the
 * shared-element bounds morph below. They MUST all be finite AND equal — see [artBoundsTransform]
 * for what breaks when they are not.
 */
const val NavTransitionMillis = 300

/**
 * The ONE spec every content-swapping transition in the app uses — Nav3 push / pop / predictive pop,
 * pane swaps, the player pop-out panel, the mini player show/hide, the Settings expand/collapse, and
 * the shared-element bounds morph below.
 *
 * Deliberately plain platform defaults: [FastOutSlowInEasing] is Compose's standard easing (the
 * same curve as `android.R.interpolator.fast_out_slow_in`) and 300ms is the standard screen-
 * transition duration. No hand-rolled curve, no overshoot — one spec, used everywhere, so every
 * screen animates identically.
 *
 * It must stay FINITE. See [artBoundsTransform] for the failure mode a spring reintroduces. Springs
 * belong only to value animations on content that stays on screen (`motionScheme.*SpatialSpec`).
 */
fun <T> screenTransitionSpec(): FiniteAnimationSpec<T> =
    tween(durationMillis = NavTransitionMillis, easing = FastOutSlowInEasing)

private val ArtBoundsSpec: FiniteAnimationSpec<Rect> = screenTransitionSpec()

/** One instance for the whole app, so every shared element sees the same transform identity. */
private val ArtBoundsTransform: BoundsTransform = BoundsTransform { _, _ -> ArtBoundsSpec }

/**
 * [BoundsTransform] for shared-element / container-transform morphs (album art flying into a hero
 * area, the mini player's art, the search-FAB morph). Use for anything that morphs bounds, never
 * for colour/alpha.
 *
 * ### Why this is a finite tween and MUST NOT become `motionScheme.defaultSpatialSpec()`
 *
 * A shared element's bounds animation is not independent — Compose builds it as a **child
 * transition of the enclosing transition**:
 *
 * ```
 * // SharedTransitionScope.kt
 * val boundsTransition = parentTransition.createChildTransition(key.toString()) { visible(it) }
 * ```
 *
 * A parent `Transition` does not reach `currentState == targetState` until every child finishes,
 * and the navigation host disposes the outgoing entry only when that happens. During a pop it also
 * z-orders the outgoing entry **above** the incoming one, and in Compose `alpha` does not affect hit
 * testing.
 *
 * So with a spring here — `MaterialExpressiveTheme`'s `defaultSpatialSpec` is UNDERdamped
 * (dampingRatio 0.8 / stiffness 380), overshooting and oscillating for ~600ms–1s — the nav
 * transition stays "running" long after the slide and fade have visibly finished, leaving an
 * invisible, fully touchable copy of the previous screen on top: taps land on the screen the user
 * just left. Because the mini player registers its art as a shared element in the NAV scope, this
 * would apply to EVERY navigation, including ones with no visible morph at all.
 *
 * Matching this to the nav slide/fade duration makes "looks finished" == "is finished": every child
 * of the nav transition lands on the same frame, so the entry is disposed the instant the motion
 * stops. Finite specs also seek cleanly under predictive back, which drives the transition by
 * gesture progress.
 *
 * Not `@Composable`: the transform is a constant, so it can be read from anywhere — a Nav3
 * `transitionSpec` lambda included.
 */
fun artBoundsTransform(): BoundsTransform = ArtBoundsTransform
