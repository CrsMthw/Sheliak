package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.util.confirm

/** The hero art tile's default edge — a constant, so a narrower pane only shrinks the space around it. */
val DetailHeroArtSize = 220.dp

/**
 * The shared detail hero (Lyra's `DetailArtHero`, ported): the art clipped to M3's square shape (bordered,
 * centred), then a row with the [title] + [subtitle] (+ an optional smaller [meta] line) on the left and the
 * Shuffle / Play cookie buttons on the right. Pass `onShuffle` / `onPlay` = null to omit a button (the artist
 * hero has neither). [content] draws the art inside the tile (an [Artwork] filling it).
 *
 * It is item 0 of a detail list and scrolls under the screen's overlay [DetailTopBar]; pass that bar's
 * [HeroTitleHandoff] as [titleHandoff] and the bar's own title takes over as this [title] goes under it.
 *
 * ### Top clearance
 *
 * With [barClearance] (a pushed detail screen) the art tile's top padding clears the overlay [DetailTopBar]: the
 * status bar plus the bar's own collapsed height plus 8dp plus [BarContentGap] — baked in HERE, never added as a
 * list `contentPadding` (it would double). Without it (the right pane of a two-pane library tab, which sits under
 * the library's own bar and has no detail bar) the tile only keeps the app-wide [BarContentGap].
 *
 * @param artModifier carries the `"lib-art-…"` shared bounds onto the tile on a pushed detail screen; a no-op
 *   in a pane.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailArtHero(
    title       : String,
    modifier    : Modifier = Modifier,
    subtitle    : String? = null,
    meta        : String? = null,
    onPlay      : (() -> Unit)? = null,
    onShuffle   : (() -> Unit)? = null,
    artSize     : Dp = DetailHeroArtSize,
    barClearance: Boolean = true,
    artModifier : Modifier = Modifier,
    titleHandoff: HeroTitleHandoff? = null,
    content     : @Composable BoxScope.() -> Unit,
) {
    val haptics      = LocalHapticFeedback.current
    val shuffleLabel = stringResource(R.string.detail_shuffle)
    val playLabel    = stringResource(R.string.detail_play)
    if (titleHandoff != null) {
        // A lazy list disposes item 0 once it is off screen, and a fling can dispose it before a final position
        // lands — without this the bar title would be stranded part-faded.
        DisposableEffect(titleHandoff) {
            onDispose { titleHandoff.onHeroTitleGone() }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .then(
                    if (barClearance) {
                        Modifier
                            .statusBarsPadding()
                            .padding(top = TopAppBarDefaults.TopAppBarExpandedHeight + 8.dp + BarContentGap)
                    } else {
                        Modifier.padding(top = BarContentGap)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val artTileShape = MaterialShapes.Square.toShape()
            Box(
                modifier         = Modifier
                    .then(artModifier)
                    .size(artSize)
                    .border(2.dp, MaterialTheme.colorScheme.outline, artTileShape)
                    .clip(artTileShape),
                contentAlignment = Alignment.Center,
                content          = content,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Root coordinates, reported from layout and read by the bar in its draw phase.
                    modifier = Modifier
                        .semantics { heading() }
                        .then(
                            if (titleHandoff == null) Modifier else Modifier.onGloballyPositioned {
                                val top = it.positionInRoot().y
                                titleHandoff.onHeroTitleBounds(top, top + it.size.height)
                            },
                        ),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!meta.isNullOrBlank()) {
                    Text(
                        text     = meta,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (onShuffle != null || onPlay != null) {
                Spacer(Modifier.width(12.dp))
                onShuffle?.let { shuffle ->
                    FilledTonalIconButton(
                        onClick  = { haptics.confirm(); shuffle() },
                        shape    = MaterialShapes.Clover4Leaf.toShape(),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = shuffleLabel, modifier = Modifier.size(22.dp))
                    }
                }
                if (onShuffle != null && onPlay != null) Spacer(Modifier.width(8.dp))
                onPlay?.let { play ->
                    FilledIconButton(
                        onClick  = { haptics.confirm(); play() },
                        shape    = MaterialShapes.Cookie6Sided.toShape(),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = playLabel, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
