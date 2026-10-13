package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
 *   keylines shift there (and lay a queue of one or two tracks out start-aligned) — no custom carousel.
 *
 * The playing row's art is the `"album-art"` shared element (first in its chain, gated by [artMayMatch]); the
 * registration moves with the playing row, so every new holder is a freshly placed node.
 *
 * @param availableWidth / [availableHeight] the room the art area has; the carousel sizes itself in it
 *   ([heroCarouselGeometry]) so the centre art is square.
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
    val geometry = heroCarouselGeometry(availableWidth, availableHeight, ArtCarouselPeek, ArtCarouselSpacing)
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
        // Material's hero keylines pick the arrangement whose large item lands closest to this; the carousel is
        // exactly as wide as one such item plus two peeks, so it lands on it exactly.
        preferredItemWidth = geometry.itemSide,
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
                modifier           = (if (isCurrent) Modifier.albumArtSharedElement(visibilityScope, artMayMatch) else Modifier)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
        }
    }
}
