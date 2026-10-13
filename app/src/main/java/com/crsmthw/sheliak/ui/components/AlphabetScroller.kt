package com.crsmthw.sheliak.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.util.screenTransitionSpec
import com.crsmthw.sheliak.util.tick
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The thumb's touch box: as wide as a touch target, as tall as the drawn thumb. */
private val ThumbTouchWidth = 40.dp
private val ThumbHeight = 52.dp

/** The drawn thumb: a pill on the end edge that widens while it is held. */
private val ThumbWidth = 6.dp
private val ThumbWidthHeld = 10.dp
private val ThumbEndInset = 4.dp

/** The letter bubble beside the held thumb. */
private val BubbleSize = 72.dp
private val BubbleGap = 8.dp

/** Room kept under the thumb's lowest position, above the insets the caller passes. */
private val TrackBottomGap = 8.dp

/** How long the scroller stays after the list stops scrolling and the thumb is let go. */
private const val HIDE_DELAY_MS = 1_500L

/** The bubble grows from this scale as it appears (and shrinks back to it as it goes). */
private const val BUBBLE_INITIAL_SCALE = 0.6f

/**
 * The alphabet fast scroller: a thumb on the end edge of a long [LazyListState] list that tracks the scroll
 * position, and — while it is HELD and dragged — a letter bubble in an M3 Expressive shape (a 9-sided cookie in
 * `primaryContainer`) that shows the letter under the finger, the list jumping to that letter's first row as the
 * finger crosses into it (a `tick()` haptic per letter). Built in-house: Material 3 ships only a NON-interactive
 * scrollbar (`Modifier.nonInteractiveScrollbar`, alpha29), which cannot be dragged.
 *
 * - The track and the letters are [index]'s section space (equal slots per letter, [AlphabetIndex]), so the
 *   thumb and the jump always agree on the letter.
 * - It shows only for lists of at least [minRows] rows, appears when the list scrolls (or the thumb is held) and
 *   fades out [HIDE_DELAY_MS] after both stop — finite fades on [screenTransitionSpec]. While hidden nothing of it
 *   is composed, so it takes no touches.
 * - The thumb's own rectangle (only that — Android caps exclusions at 200dp per edge) is excluded from the system
 *   back gesture, so a drag that starts on it at the screen edge scrolls instead of going back.
 * - It is an overlay in the list's [BoxScope]: compose it AFTER the list (and its fades). It fills the box, takes
 *   no touches outside the thumb, and keeps clear of the bar seam ([topPadding]), the navigation bar (an inset
 *   MODIFIER, so whatever the navigation suite consumed is honoured) and [bottomPadding] (the player surface).
 *
 * @param index the list's sections; its rows are the lazy items from [firstRowItem] on (the Tracks tab has its
 *   carousel and a header above the rows).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.AlphabetFastScroller(
    listState    : LazyListState,
    index        : AlphabetIndex,
    modifier     : Modifier = Modifier,
    firstRowItem : Int = 0,
    topPadding   : Dp = BarContentGap,
    bottomPadding: Dp = 0.dp,
    minRows      : Int = ALPHABET_SCROLLER_MIN_ROWS,
) {
    if (index.rowCount < minRows || index.sectionCount == 0) return

    val currentIndex by rememberUpdatedState(index)
    val currentFirstRowItem by rememberUpdatedState(firstRowItem)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { ThumbHeight.roundToPx() }

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var held by remember { mutableStateOf(false) }
    var heldFraction by remember { mutableFloatStateOf(0f) }
    var heldSection by remember { mutableIntStateOf(0) }

    // Shown while the list scrolls or the thumb is held; hidden a moment after both stop.
    val scrolling = listState.isScrollInProgress
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, held) {
        if (scrolling || held) {
            shown = true
        } else {
            delay(HIDE_DELAY_MS)
            shown = false
        }
    }
    val alphaState = animateFloatAsState(
        targetValue   = if (shown) 1f else 0f,
        animationSpec = screenTransitionSpec(),
        label         = "fast-scroller-alpha",
    )
    val composed by remember { derivedStateOf { alphaState.value > 0f } }

    /** Where the list's scroll puts the thumb (read in layout / gesture callbacks only, never in composition). */
    fun scrollFraction(): Float = listState.thumbFraction(currentIndex, currentFirstRowItem)

    fun travelPx(): Int = (trackHeightPx - thumbHeightPx).coerceAtLeast(0)

    /** A held thumb at [fraction]: jump to the letter under it the moment the finger crosses into a new one. */
    fun followFinger(fraction: Float) {
        val idx = currentIndex
        val section = idx.sectionAt(fraction)
        if (section == heldSection) return
        heldSection = section
        haptics.tick()
        val target = currentFirstRowItem + idx.firstRowOf(section)
        scope.launch { listState.scrollToItem(target) }
    }

    Box(
        modifier = modifier
            .matchParentSize()
            .padding(top = topPadding)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(bottom = bottomPadding + TrackBottomGap)
            .onSizeChanged { trackHeightPx = it.height }
            .graphicsLayer { alpha = alphaState.value },
    ) {
        if (composed) {
            val otherLabel = stringResource(R.string.library_scroller_other)
            val label = index.labels.getOrNull(heldSection)
                ?.let { if (it == ALPHABET_OTHER_SECTION) otherLabel else it }
                .orEmpty()
            val bubbleShape = MaterialShapes.Cookie9Sided.toShape()
            // The bubble grows out of the thumb's side: the end of the bubble, which is its right in LTR.
            val bubbleOrigin = if (LocalLayoutDirection.current == LayoutDirection.Ltr) TransformOrigin(1f, 0.5f)
                               else TransformOrigin(0f, 0.5f)
            AnimatedVisibility(
                visible  = held,
                enter    = fadeIn(screenTransitionSpec()) + scaleIn(
                    animationSpec   = screenTransitionSpec(),
                    initialScale    = BUBBLE_INITIAL_SCALE,
                    transformOrigin = bubbleOrigin,
                ),
                exit     = fadeOut(screenTransitionSpec()) + scaleOut(
                    animationSpec   = screenTransitionSpec(),
                    targetScale     = BUBBLE_INITIAL_SCALE,
                    transformOrigin = bubbleOrigin,
                ),
                label    = "fast-scroller-bubble",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val bubblePx = BubbleSize.roundToPx()
                        val thumbY = (heldFraction * travelPx()).roundToInt()
                        val y = (thumbY + thumbHeightPx / 2 - bubblePx / 2)
                            .coerceIn(0, (trackHeightPx - bubblePx).coerceAtLeast(0))
                        IntOffset(-(ThumbTouchWidth + BubbleGap).roundToPx(), y)
                    },
            ) {
                Box(
                    modifier         = Modifier
                        .size(BubbleSize)
                        .clip(bubbleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            val thumbWidth by animateDpAsState(
                targetValue   = if (held) ThumbWidthHeld else ThumbWidth,
                animationSpec = screenTransitionSpec(),
                label         = "fast-scroller-thumb-width",
            )
            val thumbColor by animateColorAsState(
                targetValue   = if (held) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = screenTransitionSpec(),
                label         = "fast-scroller-thumb-colour",
            )
            Box(
                modifier         = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val fraction = if (held) heldFraction else scrollFraction()
                        IntOffset(0, (fraction * travelPx()).roundToInt())
                    }
                    .size(ThumbTouchWidth, ThumbHeight)
                    .systemGestureExclusion()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart  = {
                                heldFraction = scrollFraction()
                                // The letter showing now — no jump until the finger crosses into another one.
                                heldSection = currentIndex.sectionAt(heldFraction)
                                held = true
                            },
                            onDragEnd    = { held = false },
                            onDragCancel = { held = false },
                        ) { change, dragAmount ->
                            change.consume()
                            val travel = travelPx()
                            if (travel > 0) {
                                heldFraction = (heldFraction + dragAmount / travel).coerceIn(0f, 1f)
                                followFinger(heldFraction)
                            }
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = ThumbEndInset)
                        .width(thumbWidth)
                        .fillMaxHeight()
                        .background(thumbColor, CircleShape),
                )
            }
        }
    }
}

/**
 * Where this list's scroll position puts the thumb, in [index]'s section space: the first visible row and how far
 * it has scrolled off, stretched over the scrollable range ([alphabetScrollRow]), then placed in its letter's slot
 * ([AlphabetIndex.fractionOfRow]). Scrolled to the very end is the bottom. Reads the list's layout state, so call it
 * from a layout or draw lambda or a gesture callback — never from composition.
 */
private fun LazyListState.thumbFraction(index: AlphabetIndex, firstRowItem: Int): Float {
    if (!canScrollForward && canScrollBackward) return 1f
    val visible = layoutInfo.visibleItemsInfo
    val firstIndex = firstVisibleItemIndex
    val firstSize = visible.firstOrNull { it.index == firstIndex }?.size ?: 0
    val offsetFraction = if (firstSize > 0) firstVisibleItemScrollOffset.toFloat() / firstSize else 0f
    val visibleRows = visible.count { it.index >= firstRowItem && it.index < firstRowItem + index.rowCount }
    val row = alphabetScrollRow(firstIndex - firstRowItem, offsetFraction, visibleRows, index.rowCount)
    return index.fractionOfRow(row)
}
