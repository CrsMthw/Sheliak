package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Lyra's library cards (`LibraryCards.kt`), ported onto the domain models: the Albums grid card, the album /
 * artist / playlist list cards, and the carousel track card. Every card draws its art with [Artwork] and takes the
 * container-transform modifier for its art tile from the caller (`artSharedModifier`): the "lib-art-…" shared
 * bounds where a tap PUSHES a detail entry, nothing in a two-pane tab.
 */

/** Edge of a list card's art tile (Lyra's 56dp). */
private val ListCardArtSize = 56.dp

/** A list card's art corner (Lyra's 12dp). */
private val ListCardArtShape = RoundedCornerShape(12.dp)

/** The size bucket asked of the provider for a grid / carousel card: they are 120–200dp wide. */
private val GridCardArtRequest = 200.dp

/** Lyra's list-card shape and colours: a 16dp rounded `surfaceContainer` card, `primaryContainer` when selected. */
@Composable
private fun listCardColors(selected: Boolean) = CardDefaults.cardColors(
    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.surfaceContainer,
)

private val ListCardShape = RoundedCornerShape(16.dp)

/**
 * One album in the Albums grid: square art over the title and the artist (one line each). [selected] tints the
 * tile's text in a two-pane tab, where the card stays on screen beside its detail.
 */
@Composable
internal fun AlbumGridCard(
    album            : Album,
    onClick          : () -> Unit,
    modifier         : Modifier = Modifier,
    selected         : Boolean = false,
    artSharedModifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val artistFallback = stringResource(R.string.library_unknown_artist)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { haptics.confirm(); onClick() }, hapticFeedbackEnabled = false)
            .padding(bottom = 4.dp),
    ) {
        Artwork(
            art                = album.art,
            sizePx             = artSizePx(GridCardArtRequest),
            contentDescription = null,
            modifier           = Modifier.fillMaxWidth().aspectRatio(1f).then(artSharedModifier),
            shape              = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text     = album.title,
            style    = MaterialTheme.typography.titleSmall,
            color    = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text     = album.artistName.ifBlank { artistFallback },
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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

/**
 * One track in a carousel (Lyra's `TopTrackCard`): square art, a two-line title and the artist — `minLines = 2`
 * keeps every card the same height, so the row never re-measures as cards scroll in. Tap plays from here; hold
 * opens the track's menu.
 */
@Composable
internal fun CarouselTrackCard(
    track      : Track,
    onClick    : () -> Unit,
    onLongClick: () -> Unit,
    modifier   : Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val artistFallback = stringResource(R.string.library_unknown_artist)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick               = { haptics.confirm(); onClick() },
                onLongClick           = { haptics.longPress(); onLongClick() },
                onLongClickLabel      = stringResource(R.string.track_actions),
                hapticFeedbackEnabled = false,
            )
            .padding(bottom = 4.dp),
    ) {
        Artwork(
            art                = track.art,
            sizePx             = artSizePx(GridCardArtRequest),
            contentDescription = null,
            modifier           = Modifier.fillMaxWidth().aspectRatio(1f),
            shape              = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text     = track.title,
            style    = MaterialTheme.typography.bodySmall,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color    = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text     = track.artistName.ifBlank { artistFallback },
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
