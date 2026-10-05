@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.crsmthw.sheliak.util

import android.graphics.Matrix as AndroidMatrix
import android.graphics.Path as AndroidGraphicsPath
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed

// ─── Geometry of the two endpoints ───────────────────────────────────────────
// These MUST describe the real measured bounds of both ends of the "search-bar" container
// transform, and both call sites (the compact search FAB and the Search screen's floating bar) MUST
// lay themselves out from these constants — see the KDoc on [rememberSearchBarMorphClip] for why a
// per-call-site value would be actively worse than a uniformly-slightly-wrong one.

/**
 * The compact search FAB's container size — a `MediumFloatingActionButton` pins its child with
 * `sizeIn(minWidth = FabMediumTokens.ContainerWidth, minHeight = FabMediumTokens.ContainerHeight)`,
 * both 80dp, and the `sharedBounds` node sits outside that, so the measured bounds are 80x80. The
 * FAB end of the morph must therefore be a Medium FAB.
 */
val SearchFabSize = 80.dp

/** The Search screen's floating bar height — the screen lays its bar out at exactly this. */
val SearchBarHeight = 56.dp

/** The Search screen's floating bar horizontal margin, on each side. */
val SearchBarSideMargin = 16.dp

/**
 * The outline morph for the search FAB <-> Search bar container transform (shared key
 * `"search-bar"`). Compact widths only: on the rail widths Search opens with the ordinary push
 * transition and no container transform.
 *
 * ### The problem this solves
 *
 * `sharedBounds` interpolates BOUNDS and cross-fades the two CONTENTS. Nothing morphs the
 * silhouette: the exiting content keeps drawing its own shape and the entering one keeps drawing
 * its own, so backing out of Search reads as "stadium… stadium… stadium… *cookie*". Worse, the
 * default `resizeMode = scaleToBounds(ContentScale.FillWidth, Center)` draws the entering FAB at
 * `barWidth / 80dp` — roughly 4.75x on a phone — so the SoftBurst is a ~380x380 blob centred on a
 * 380x56 box, spilling far outside the bounds with only `fadeIn` to hide it: the full cookie appears
 * abruptly before the bounds reach the bottom.
 *
 * ### The fix
 *
 * A [SharedTransitionScope.OverlayClip] that clips the shared content — in the shared-transition
 * overlay, where it is rendered for the duration of the transition — to an
 * `androidx.graphics.shapes` [Morph] between the FAB's `MaterialShapes.SoftBurst` and a stadium.
 * It only CLIPS: no fill and no stroke are drawn (painting an intermediate path strokes a visible
 * wash over whatever the shape covers).
 *
 * ### Deriving progress without a progress parameter
 *
 * `getClipPath` is handed only the animated `bounds`, so the morph fraction has to come out of
 * them. It can, exactly: `BoundsAnimation` animates the `Rect` component-wise through a single
 * `TwoWayConverter`, so left/top/right/bottom all carry the SAME eased fraction, and therefore
 *
 * ```
 * height(t) = lerp(fabHeight, barHeight, t)   =>   t = (fabHeight - height) / (fabHeight - barHeight)
 * ```
 *
 * Height is the one dimension whose two endpoints are known without measuring the container. Two
 * consequences fall out for free:
 *
 * - **No direction handling.** Back runs the same function from 1 to 0.
 * - **It seeks.** Predictive back drives the fraction, which drives the bounds, which drive `t`.
 *
 * ### Mapping the path into the bounds
 *
 * Both polygons live in the unit square, and the path is scaled NON-uniformly onto `bounds` —
 * matching exactly how Material 3 renders a `MaterialShapes` polygon (`RoundedPolygon.toShape()`
 * builds its outline with `Matrix().scale(x = size.width, y = size.height)`), so at `t = 0` the
 * clip is the FAB's own SoftBurst silhouette, give or take the morph's cubic splitting (about
 * 0.15 px on an 80dp FAB).
 *
 * The stadium end therefore cannot be `MaterialShapes.Pill` (its `normalized()` letterboxes a 2:1
 * pill into the unit square, i.e. it would occupy only the middle half of the height) and cannot be
 * a unit square with 0.5 rounding (a circle, which stretches into an ellipse, not a stadium).
 * Instead it is built at the BAR's aspect ratio and squeezed into the unit square, so that the
 * non-uniform scale back out reproduces a true stadium with `barHeight / 2` caps.
 *
 * ### Why this takes no parameters
 *
 * The clip has to be IDENTICAL at both call sites. The exiting and entering shared-content entries
 * each build their own clip path from the same bounds; if their geometry constants differed, the two
 * would be clipped to different outlines on the same frame — visibly worse than both being uniformly
 * slightly off. So every input is read here, from constants and `LocalWindowInfo`, and nothing is
 * passed in.
 */
@Composable
fun rememberSearchBarMorphClip(): SharedTransitionScope.OverlayClip {
    val density        = LocalDensity.current.density
    // The WINDOW width. The morph only runs on compact widths, where there is no navigation rail and
    // no docked player pane, so the navigation host spans the window and this is the bar's real
    // container width (less any side system-bar inset, which compact portrait does not have).
    val containerWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    return remember(density, containerWidth) {
        val barHeightPx = SearchBarHeight.value * density
        SearchBarMorphClip(
            fabSizePx   = SearchFabSize.value * density,
            barHeightPx = barHeightPx,
            barWidthPx  = (containerWidth - SearchBarSideMargin.value * 2f * density)
                .coerceAtLeast(barHeightPx),
        )
    }
}

/** See [rememberSearchBarMorphClip] — this only exists to be built there. */
private class SearchBarMorphClip(
    private val fabSizePx  : Float,
    private val barHeightPx: Float,
    barWidthPx             : Float,
) : SharedTransitionScope.OverlayClip {

    /**
     * Built once. Deliberately NOT rebuilt per frame at the live bounds' aspect ratio, and not
     * cached against a quantised aspect either: `Morph` decides which of SoftBurst's ~20 features
     * pairs with which of the stadium's, and a pairing that flips mid-flight is a visible pop —
     * strictly worse than a fixed end aspect.
     */
    private val morph: Morph = run {
        val barAspect = (barWidthPx / barHeightPx).coerceAtLeast(1f)
        // pill() is centred on the origin and spans [-barAspect/2, barAspect/2] x [-0.5, 0.5] with
        // circular caps of radius 0.5. Squeeze x by 1/barAspect and recentre on (0.5, 0.5) to land
        // in the unit square: the caps become ellipses that the non-uniform scale onto `bounds`
        // turns back into circles of radius bounds.height / 2 — a true stadium.
        val squeezeIntoUnitSquare = AndroidMatrix().apply {
            setScale(1f / barAspect, 1f)
            postTranslate(0.5f, 0.5f)
        }
        Morph(
            start = MaterialShapes.SoftBurst,
            end   = RoundedPolygon.pill(width = barAspect, height = 1f)
                .transformed(squeezeIntoUnitSquare),
        )
    }

    // One Path, mutated in place every frame as the interface documentation asks. `composePath`
    // wraps this very instance (asComposePath is a thin AndroidPath wrapper), so mutating
    // `androidPath` mutates what was already handed out.
    private val androidPath = AndroidGraphicsPath()
    private val composePath = androidPath.asComposePath()
    private val matrix      = AndroidMatrix()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds            : Rect,
        layoutDirection   : LayoutDirection,
        density           : Density,
    ): Path? {
        if (bounds.width <= 0f || bounds.height <= 0f) return null
        val span     = fabSizePx - barHeightPx
        val progress = if (span == 0f) 0f else ((fabSizePx - bounds.height) / span).coerceIn(0f, 1f)

        morph.toPath(progress, androidPath)
        // Unit square -> bounds. The path must end up in the SharedTransitionScope's coordinate
        // space, hence the translate by bounds.topLeft (see OverlayClip.getClipPath's contract).
        matrix.setScale(bounds.width, bounds.height)
        matrix.postTranslate(bounds.left, bounds.top)
        androidPath.transform(matrix)
        return composePath
    }
}
