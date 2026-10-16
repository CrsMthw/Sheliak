package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.ui.components.Artwork
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec

/**
 * What the carousel remembers between its two effects, outside snapshot state (only effects read and write it):
 * the track it last followed, the row a swipe asked the player for and that has not been reported yet, and
 * whether a programmatic follow is moving it.
 */
private class CarouselSync(var followedKey: TrackKey?) {
    var pendingSkip: Int? = null
    var following: Boolean = false
}

/**
 * The now-playing art as Material 3 Expressive's centred hero carousel over the queue in PLAY order (Cris, M1
 * item 52): the playing track large in the centre, the previous and next tracks' art peeking on either side.
 *
 * - **A swipe** follows the finger continuously and settles on Material's own snap; when it comes to rest on
 *   another row, the player moves to that row (`seekToQueueIndex` through [onSeekTo] — never previous / next, so
 *   a swipe back ALWAYS goes to the previous track; only the previous BUTTON keeps the 3-second restart). Play /
 *   pause is left alone: a swipe on a paused player stays paused on the new track.
 * - **The player changing track by itself** glides the carousel one item on the finite spec; the same track moving
 *   to another row (shuffle, a removal above it) snaps. Rules in [carouselFollow] / [carouselSettled].
 * - **The edges** are the component's own: there is simply no item beyond the first or the last row, and Material's
 *   keylines shift there (and lay a queue of two tracks out start-aligned: the playing art, the other's peek) — no
 *   custom carousel.
 * - **Short queues** (fix round 2): the carousel keeps ONE width at every queue length, so Material's large item
 *   must absorb the room of the small slots it drops below three items. [heroCarouselGeometry] hands it the
 *   preferred width that fills that room exactly (the art's side from three tracks on, side + peek + gap for two,
 *   the whole carousel for one); with the art's side for every queue, Material's cost rule chose two equal large
 *   items for a two-track queue whenever the art was under 232dp (the pop-out card's portrait crops) and for a
 *   one-track queue under 472dp. Every item shows the art as a centred square of its height (of its width, should
 *   an item ever be narrower), never cropped to the item's rectangle; only Material's mask narrows the peeks.
 *
 * The playing row's art is the `"album-art"` shared element (first in its chain, gated by [artMayMatch]); the
 * registration moves with the playing row, so every new holder is a freshly placed node.
 *
 * @param availableWidth / [availableHeight] the room the art area has; the carousel sizes itself in it
 *   ([heroCarouselGeometry]) so the playing art is square.
 */
@Composable
fun AlbumArtCarousel(
    queue          : List<Track>,
    queueIndex     : Int,
    visibilityScope: AnimatedVisibilityScope?,
    artMayMatch    : Boolean,
    availableWidth : Dp,
    availableHeight: Dp,
    onSeekTo       : (Int) -> Unit,
    modifier       : Modifier = Modifier,
    cornerRadius   : Dp = 16.dp,
) {
    val geometry = heroCarouselGeometry(availableWidth, availableHeight, queue.size, ArtCarouselPeek, ArtCarouselSpacing)
    val shape    = RoundedCornerShape(cornerRadius)
    val playing  = carouselItemFor(queueIndex, queue.size)
    val albumArtLabel = stringResource(R.string.player_album_art)

    if (playing == null) {
        // Nothing queued: the placeholder art where the carousel would be.
        Artwork(
            art                = null,
            sizePx             = PLAYER_ART_SIZE_PX,
            contentDescription = null,
            shape              = shape,
            modifier           = modifier.size(geometry.itemSide),
        )
        return
    }

    val haptics       = LocalHapticFeedback.current
    val queueSizeNow  by rememberUpdatedState(queue.size)
    val playingNow    by rememberUpdatedState(playing)
    val onSeekToNow   by rememberUpdatedState(onSeekTo)
    val playingKey    = queue[playing].key
    val carouselState = rememberCarouselState(initialItem = playing) { queueSizeNow }
    val sync          = remember { CarouselSync(followedKey = playingKey) }

    // The player → the carousel: follow a new playing row, unless the user is mid-swipe (the settle below
    // reconciles that).
    LaunchedEffect(playing, playingKey, queue.size) {
        val trackChanged = playingKey != sync.followedKey
        sync.followedKey = playingKey
        if (sync.pendingSkip == playing) sync.pendingSkip = null
        if (carouselState.isScrollInProgress && !sync.following) return@LaunchedEffect
        when (val move = carouselFollow(carouselState.currentItem, playing, queue.size, trackChanged, sync.pendingSkip)) {
            CarouselMove.Stay       -> Unit
            is CarouselMove.Animate -> {
                sync.following = true
                try {
                    carouselState.animateScrollToItem(move.item, screenTransitionSpec())
                } finally {
                    sync.following = false
                }
            }
            is CarouselMove.Snap    -> carouselState.scrollToItem(move.item)
        }
    }

    // The carousel → the player: a scroll that comes to rest on another row moves the player there (play / pause
    // unchanged).
    LaunchedEffect(carouselState) {
        var startItem = carouselState.currentItem
        snapshotFlow { carouselState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                startItem = carouselState.currentItem
                return@collect
            }
            val settle = carouselSettled(
                settledItem = carouselState.currentItem,
                startItem   = startItem,
                queueIndex  = playingNow,
                queueSize   = queueSizeNow,
                pendingSkip = sync.pendingSkip,
            )
            when (settle) {
                CarouselSettle.Stay      -> Unit
                is CarouselSettle.Skip   -> {
                    sync.pendingSkip = settle.item
                    haptics.press()
                    onSeekToNow(settle.item)
                }
                is CarouselSettle.Return -> when (val move = settle.move) {
                    is CarouselMove.Animate -> carouselState.animateScrollToItem(move.item, screenTransitionSpec())
                    is CarouselMove.Snap    -> carouselState.scrollToItem(move.item)
                    CarouselMove.Stay       -> Unit
                }
            }
        }
    }

    HorizontalCenteredHeroCarousel(
        state              = carouselState,
        modifier           = modifier
            .width(geometry.width)
            .height(geometry.itemSide),
        // Material's hero keylines pick the arrangement whose large item lands closest to this (weighted by try
        // order); it is exactly what ONE large item fills beside the peeks Material keeps for this queue's length,
        // so that arrangement costs nothing and wins at every size ([heroCarouselGeometry]).
        preferredItemWidth = geometry.largeItemWidth,
        itemSpacing        = ArtCarouselSpacing,
        minSmallItemWidth  = ArtCarouselPeek,
        maxSmallItemWidth  = ArtCarouselPeek,
    ) { index ->
        val track     = queue.getOrNull(index)
        val isCurrent = index == playing
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .maskClip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                art                = track?.art,
                sizePx             = PLAYER_ART_SIZE_PX,
                // The neighbours are decoration; the centre one says what it is.
                contentDescription = if (isCurrent) track?.albumTitle?.takeIf { it.isNotBlank() } ?: albumArtLabel else null,
                shape              = shape,
                // The shared element FIRST in the chain, before the size (Lyra's order), so the carousel's own
                // placement never disturbs the morph's bounds. Only the playing row registers.
                // The size: a square of the item's height, or of its width should an item ever be narrower — the
                // Box hands loose constraints, so the strict aspect-ratio passes always fit. (With fillMaxHeight in
                // front, a narrow item fell through to a full-height square that overflowed it and was cropped to
                // the item's portrait rectangle.)
                modifier           = (if (isCurrent) Modifier.albumArtSharedElement(visibilityScope, artMayMatch) else Modifier)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
        }
    }
}
