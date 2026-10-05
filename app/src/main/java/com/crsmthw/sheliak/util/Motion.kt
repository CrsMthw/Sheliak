package com.crsmthw.sheliak.util

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp

/**
 * The one envelope every screen-level transition shares: the nav slide, its paired fades, the tab
 * fade-through and the shared-element bounds morph below. Everything inside it is FINITE and ends on
 * this frame — see [artBoundsTransform] for what breaks when a part outlasts it.
 */
const val NavTransitionMillis = 300

/** Material's fade-OUT phase (shared axis and fade through): the outgoing content is gone after 90ms. */
const val FadeOutMillis = 90

/** The fade-IN phase waits for the fade-out to finish, so the two contents never show at half alpha together. */
const val FadeInDelayMillis = FadeOutMillis

/** The fade-IN phase fills the rest of the envelope: 90 + 210 = [NavTransitionMillis]. */
const val FadeInMillis = NavTransitionMillis - FadeInDelayMillis

/** Fade through's incoming scale: the new content grows from 92% while it fades in (M3). */
const val FadeThroughInitialScale = 0.92f

/**
 * M3 shared axis X's slide distance. A `Dp`: the Nav3 transition lambdas have no density, so the shell
 * converts it to px once in composition (`LocalDensity`) and hands the px to [sharedAxisXForward] /
 * [sharedAxisXBackward].
 */
val SharedAxisSlide = 30.dp

/**
 * The ONE spec for every spatial or content-swapping motion that is not one of Material's split fades:
 * the shared-axis slide, the fade-through scale, shared-element bounds (below), shared-content
 * cross-fades, `RevealSection`, and later the pane swaps, the player pop-out panel and the mini player
 * show / hide.
 *
 * Deliberately plain platform defaults: [FastOutSlowInEasing] is Compose's standard easing (the
 * same curve as `android.R.interpolator.fast_out_slow_in`) and 300ms is the standard screen-
 * transition duration. No hand-rolled curve, no overshoot.
 *
 * It must stay FINITE. See [artBoundsTransform] for the failure mode a spring reintroduces. Springs
 * belong only to value animations on content that stays on screen (`motionScheme.*SpatialSpec`).
 */
fun <T> screenTransitionSpec(): FiniteAnimationSpec<T> =
    tween(durationMillis = NavTransitionMillis, easing = FastOutSlowInEasing)

/** M3's outgoing fade: 90ms, accelerating away. */
private fun materialFadeOut(): ExitTransition =
    fadeOut(tween(durationMillis = FadeOutMillis, easing = FastOutLinearInEasing))

/** M3's incoming fade: 210ms after a 90ms wait, decelerating in. */
private fun materialFadeIn(): EnterTransition =
    fadeIn(tween(durationMillis = FadeInMillis, delayMillis = FadeInDelayMillis, easing = LinearOutSlowInEasing))

/**
 * Navigation push — **M3 shared axis X, forward**: the incoming screen slides in from [slidePx] to the end
 * and fades in late; the outgoing one slides [slidePx] toward the start and fades out early. The slide runs
 * the whole envelope on [screenTransitionSpec]; both fades end inside it.
 *
 * No `SizeTransform` is set here: NavDisplay applies its own (`null`), and every entry fills the host.
 */
fun sharedAxisXForward(slidePx: Int): ContentTransform =
    (slideInHorizontally(screenTransitionSpec()) { slidePx } + materialFadeIn())
        .togetherWith(slideOutHorizontally(screenTransitionSpec()) { -slidePx } + materialFadeOut())

/** Navigation pop (button back) — [sharedAxisXForward] mirrored. */
fun sharedAxisXBackward(slidePx: Int): ContentTransform =
    (slideInHorizontally(screenTransitionSpec()) { -slidePx } + materialFadeIn())
        .togetherWith(slideOutHorizontally(screenTransitionSpec()) { slidePx } + materialFadeOut())

/**
 * The PREDICTIVE pop — the back GESTURE, which seeks the transition by finger progress. Same mirrored slides
 * as [sharedAxisXBackward], but the two fades are a plain crossfade over the whole envelope instead of M3's
 * sequential 90ms-out / 90ms-wait / 210ms-in: with the sequential fades a finger held at a third of the way
 * sits on a screen where the outgoing page has already vanished and the incoming one has not yet appeared
 * — exactly the "nothing drawn during back" bump the first device pass reported. A crossfade keeps the
 * destination visible from the first pixel of the swipe; its alpha dip is invisible because every screen
 * paints the same `background`. On commit the remaining fraction animates with the same spec.
 */
fun sharedAxisXBackwardSeekable(slidePx: Int): ContentTransform =
    (slideInHorizontally(screenTransitionSpec()) { -slidePx } +
        fadeIn(tween(durationMillis = NavTransitionMillis, easing = LinearOutSlowInEasing)))
        .togetherWith(
            slideOutHorizontally(screenTransitionSpec()) { slidePx } +
                fadeOut(tween(durationMillis = NavTransitionMillis, easing = FastOutLinearInEasing)),
        )

/**
 * A tab change — **M3 fade through**: the old tab fades out in 90ms; the new one fades in over the next
 * 210ms while growing from [FadeThroughInitialScale] across the whole envelope. No `SizeTransform` (`togetherWith`'s
 * default is a spring), and the content never changes size anyway.
 */
fun fadeThrough(): ContentTransform = ContentTransform(
    targetContentEnter = materialFadeIn() + scaleIn(screenTransitionSpec(), initialScale = FadeThroughInitialScale),
    initialContentExit = materialFadeOut(),
    sizeTransform      = null,
)

/**
 * Text that changes in place (the library bar's title and subtitle on a tab change): fade through's two
 * fades without the scale, so the words swap while the bar itself never moves. No `SizeTransform`: the two
 * strings differ in width, and `togetherWith`'s default one would spring between them.
 */
fun fadeThroughText(): ContentTransform = ContentTransform(
    targetContentEnter = materialFadeIn(),
    initialContentExit = materialFadeOut(),
    sizeTransform      = null,
)

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
 * Matching this to the nav slide/fade envelope makes "looks finished" == "is finished": every child
 * of the nav transition lands on the same frame, so the entry is disposed the instant the motion
 * stops. Finite specs also seek cleanly under predictive back, which drives the transition by
 * gesture progress.
 *
 * Not `@Composable`: the transform is a constant, so it can be read from anywhere — a Nav3
 * `transitionSpec` lambda included.
 */
fun artBoundsTransform(): BoundsTransform = ArtBoundsTransform
