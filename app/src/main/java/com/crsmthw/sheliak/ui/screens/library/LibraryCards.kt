package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.ui.components.Artwork
import com.crsmthw.sheliak.ui.components.artSizePx
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.longPress
import com.crsmthw.sheliak.util.press

/*
 * Lyra's library cards (`LibraryCards.kt`), ported onto the domain models: the album / artist / playlist list
 * cards, and the two carousel items (Most played's track, an artist's album). Every card draws its art with
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

/** The corner of a carousel item's mask: M3's carousel items use the extra-large shape. */
@Composable
private fun carouselItemShape() = MaterialTheme.shapes.extraLarge

/** The size bucket asked of the provider for a carousel item's art (the items are up to ~200dp). */
private val CarouselArtRequest = 200.dp

/**
 * One item of the Most played carousel: the track's art filling the item, masked by the carousel to its current
 * size ([maskClip], the extra-large shape), with the M3 carousel's item label — the title over the artist on a
 * bottom gradient. The label follows the mask: it is shifted to the mask's start edge (the item's
 * `carouselItemDrawInfo.maskRect`) and fades with the item's size ([carouselLabelAlpha]), gone by the time the
 * item is a small sliver — both read in the draw phase, so a scroll never recomposes the item.
 *
 * The gradient runs from transparent to the `surface` role and the label is `onSurface`, so it reads in both
 * themes without a colour of its own. Tap plays from here; hold opens the track's sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarouselItemScope.CarouselTrackItem(
    track         : Track,
    artistFallback: String,
    onClick       : () -> Unit,
    onLongClick   : () -> Unit,
    modifier      : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val drawInfo = carouselItemDrawInfo
    val scrimColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxSize()
            .maskClip(carouselItemShape())
            .combinedClickable(
                onClick               = { haptics.confirm(); onClick() },
                onLongClick           = { haptics.longPress(); onLongClick() },
                onLongClickLabel      = stringResource(R.string.track_actions),
                hapticFeedbackEnabled = false,
            ),
    ) {
        Artwork(
            art                = track.art,
            sizePx             = artSizePx(CarouselArtRequest),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            shape              = RectangleShape,
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
                text     = track.title,
                style    = MaterialTheme.typography.titleSmall,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = track.artistName.ifBlank { artistFallback },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How opaque the label's gradient gets at the item's bottom edge. */
private const val CAROUSEL_LABEL_SCRIM_ALPHA = 0.9f

/** An album item's width in an artist's Albums row (an uncontained carousel keeps every item this wide). */
val ArtistAlbumItemWidth = 148.dp

/**
 * One album in an artist's "Albums" row (an uncontained carousel — every item full size, cut off at the edge):
 * square art masked to the extra-large shape over the title and the year (one line each). The art takes the
 * caller's [artSharedModifier] (the "lib-art-…" bounds: every album here PUSHES its detail). Tap opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarouselItemScope.ArtistAlbumItem(
    album            : Album,
    onClick          : () -> Unit,
    modifier         : Modifier = Modifier,
    artSharedModifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { haptics.confirm(); onClick() }, hapticFeedbackEnabled = false)
            .padding(bottom = 4.dp),
    ) {
        Artwork(
            art                = album.art,
            sizePx             = artSizePx(ArtistAlbumItemWidth),
            contentDescription = null,
            modifier           = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(artSharedModifier)
                .maskClip(carouselItemShape()),
            shape              = RectangleShape,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text     = album.title,
            style    = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text     = album.year?.toString().orEmpty(),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
