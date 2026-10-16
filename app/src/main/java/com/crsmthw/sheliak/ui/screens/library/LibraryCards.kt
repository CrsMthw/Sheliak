package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.ui.components.Artwork
import com.crsmthw.sheliak.ui.components.artSizePx
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.longPress
import com.crsmthw.sheliak.util.press

/*
 * Lyra's library cards (`LibraryCards.kt`), ported onto the domain models: the album / artist / playlist list
 * cards, and the ONE carousel item (Most played's tracks, an artist's albums). Every card draws its art with
 * [Artwork] and takes the container-transform modifier for its art tile from the caller (`artSharedModifier`):
 * the "lib-art-…" shared bounds where a tap PUSHES a detail entry, nothing in a two-pane tab.
 */

/** Edge of a list card's art tile (Lyra's 56dp). */
private val ListCardArtSize = 56.dp

/** A list card's art corner (Lyra's 12dp). */
private val ListCardArtShape = RoundedCornerShape(12.dp)

/** Lyra's list-card shape and colours: a 16dp rounded `surfaceContainer` card, `primaryContainer` when selected. */
@Composable
private fun listCardColors(selected: Boolean) = CardDefaults.cardColors(
    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.surfaceContainer,
)

private val ListCardShape = RoundedCornerShape(16.dp)

/** One album as a list card (an artist's albums, album search results): art, title, "artist · year". */
@Composable
internal fun AlbumListCard(
    album            : Album,
    onClick          : () -> Unit,
    modifier         : Modifier = Modifier,
    selected         : Boolean = false,
    artSharedModifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val artistFallback = stringResource(R.string.library_unknown_artist)
    val subtitle = metaLine(
        parts     = listOf(album.artistName.ifBlank { artistFallback }, album.year?.toString()),
        separator = stringResource(R.string.library_meta_separator),
    )
    Card(
        onClick   = { haptics.confirm(); onClick() },
        modifier  = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape     = ListCardShape,
        colors    = listCardColors(selected),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                art                = album.art,
                sizePx             = artSizePx(ListCardArtSize),
                contentDescription = null,
                modifier           = Modifier.size(ListCardArtSize).then(artSharedModifier),
                shape              = ListCardArtShape,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text     = subtitle,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One artist as a list card: round art, the name, "N albums". */
@Composable
internal fun ArtistListCard(
    artist           : Artist,
    onClick          : () -> Unit,
    modifier         : Modifier = Modifier,
    selected         : Boolean = false,
    artSharedModifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val artistFallback = stringResource(R.string.library_unknown_artist)
    Card(
        onClick   = { haptics.confirm(); onClick() },
        modifier  = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape     = ListCardShape,
        colors    = listCardColors(selected),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                art                = artist.art,
                sizePx             = artSizePx(ListCardArtSize),
                contentDescription = null,
                modifier           = Modifier.size(ListCardArtSize).then(artSharedModifier),
                shape              = CircleShape,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = artist.name.ifBlank { artistFallback },
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text     = pluralStringResource(R.plurals.library_album_count, artist.albumCount, artist.albumCount),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One playlist as a list card (Lyra's `PlaylistListCard`): art, the title ("Liked Songs" from a resource for that
 * one), "N tracks", and a small Play button that plays it from the top without opening it.
 */
@Composable
internal fun PlaylistListCard(
    playlist         : Playlist,
    onClick          : () -> Unit,
    onPlay           : () -> Unit,
    modifier         : Modifier = Modifier,
    selected         : Boolean = false,
    artSharedModifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val title = if (playlist.isLikedSongs) stringResource(R.string.playback_liked_songs) else playlist.title
    Card(
        onClick   = { haptics.confirm(); onClick() },
        modifier  = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape     = ListCardShape,
        colors    = listCardColors(selected),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                art                = playlist.art,
                sizePx             = artSizePx(ListCardArtSize),
                contentDescription = null,
                modifier           = Modifier.size(ListCardArtSize).then(artSharedModifier),
                shape              = ListCardArtShape,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text     = pluralStringResource(R.plurals.library_track_count, playlist.trackCount, playlist.trackCount),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            SmallFloatingActionButton(
                onClick        = { haptics.press(); onPlay() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation      = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier       = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.detail_play),
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Carousel items ──────────────────────────────────────────────────────────

/**
 * The large item's preferred width in the library's carousels (the M3 multi-browse carousel sample's 186dp): the
 * carousel fits as many large items as the width allows and adds the medium / small ones itself, so a wide window
 * shows more, not bigger. Shared by Most played and an artist's Albums row so the two read as one component.
 */
internal val CarouselPreferredItemWidth = 186.dp

/**
 * The library carousels' FIXED height — a near-square large item with room for the two-line label at a large
 * font. Fixed rather than content-sized: every item is the art with its label ON it, so nothing below the art
 * decides how tall the row is.
 */
internal val CarouselHeight = 200.dp

/** The corner of a carousel item's mask: M3's carousel items use the extra-large shape. */
@Composable
private fun carouselItemShape() = MaterialTheme.shapes.extraLarge

/** The size bucket asked of the provider for a carousel item's art (the items are about 200dp: [CarouselHeight]). */
private val CarouselArtRequest = 200.dp

/**
 * One item of a library carousel (Most played's tracks, an artist's albums): the [art] filling the item, masked
 * by the carousel to its current size ([maskClip], the extra-large shape), with the M3 carousel's item label —
 * [title] over [subtitle] (no second line when it is null) on a bottom gradient. The label follows the mask: it is
 * shifted to the mask's start edge (the item's `carouselItemDrawInfo.maskRect`) and fades with the item's size
 * ([carouselLabelAlpha]), gone by the time the item is a small sliver — both read in the draw phase, so a scroll
 * never recomposes the item. (A label laid out at the item's own start instead loses its first letters whenever
 * the mask is narrower than the item: the carousel masks every item around its centre, from both sides.)
 *
 * The gradient runs from transparent to the `surface` role and the label is `onSurface`, so it reads in both
 * themes without a colour of its own. Tap runs [onClick]; with an [onLongClick] (announced as
 * [onLongClickLabel]), hold runs it — both through the app's own haptics.
 *
 * ### The container transform ([artSharedModifier])
 *
 * The caller's shared bounds (the `"lib-art-…"` key, for an item whose tap PUSHES a detail entry) go FIRST in the
 * art's own chain, before its size — the full player's hero carousel's order. The art is a node of the item's
 * FIXED layout size: the carousel measures every item at its large size and shrinks it only with a layer mask
 * and a layer translation applied in placement, never by re-measuring. At rest the shared node places the art at
 * its own origin (no match on screen, or a finished match where this end is the visible one), so the per-frame
 * mask and translation — layers ABOVE it — never meet it. In flight the art is drawn in the shared overlay at the
 * animated bounds, outside the item's layers: so the mask sits on the item box, the art's parent — inside the
 * shared node it would crop the growing cover to the item's mask rectangle — and the art clips itself to the
 * item's corner shape ([Artwork]'s `shape`), so the cover leaves with the corners it had. At rest that clip never
 * shows: the mask, the same shape at most as wide, lies inside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarouselItemScope.CarouselArtItem(
    art              : ArtRef?,
    title            : String,
    subtitle         : String?,
    onClick          : () -> Unit,
    modifier         : Modifier = Modifier,
    onLongClick      : (() -> Unit)? = null,
    onLongClickLabel : String? = null,
    artSharedModifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val drawInfo = carouselItemDrawInfo
    val scrimColor = MaterialTheme.colorScheme.surface
    val shape = carouselItemShape()
    Box(
        modifier = modifier
            .fillMaxSize()
            .maskClip(shape)
            .combinedClickable(
                onClick               = { haptics.confirm(); onClick() },
                onLongClick           = onLongClick?.let { longClick -> { haptics.longPress(); longClick() } },
                onLongClickLabel      = onLongClickLabel,
                hapticFeedbackEnabled = false,
            ),
    ) {
        Artwork(
            art                = art,
            sizePx             = artSizePx(CarouselArtRequest),
            contentDescription = null,
            modifier           = artSharedModifier.fillMaxSize(),
            shape              = shape,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = carouselLabelAlpha(drawInfo.size, drawInfo.minSize, drawInfo.maxSize)
                    translationX = drawInfo.maskRect.left
                }
                .background(
                    Brush.verticalGradient(
                        listOf(scrimColor.copy(alpha = 0f), scrimColor.copy(alpha = CAROUSEL_LABEL_SCRIM_ALPHA)),
                    ),
                )
                .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
        ) {
            Text(
                text     = title,
                style    = MaterialTheme.typography.titleSmall,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text     = subtitle,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** How opaque the label's gradient gets at the item's bottom edge. */
private const val CAROUSEL_LABEL_SCRIM_ALPHA = 0.9f
