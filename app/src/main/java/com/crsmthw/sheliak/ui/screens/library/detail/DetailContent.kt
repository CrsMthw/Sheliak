package com.crsmthw.sheliak.ui.screens.library.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.PlaybackController
import com.crsmthw.sheliak.ui.components.Artwork
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.DetailArtHero
import com.crsmthw.sheliak.ui.components.DetailHeroArtSize
import com.crsmthw.sheliak.ui.components.DetailTopBar
import com.crsmthw.sheliak.ui.components.DetailTopBarFade
import com.crsmthw.sheliak.ui.components.HeroTitleHandoff
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.TrackMenuActions
import com.crsmthw.sheliak.ui.components.TrackRowWithMenu
import com.crsmthw.sheliak.ui.components.artSizePx
import com.crsmthw.sheliak.ui.components.rememberHeroTitleHandoff
import com.crsmthw.sheliak.ui.screens.library.AlbumRow
import com.crsmthw.sheliak.ui.screens.library.CarouselArtItem
import com.crsmthw.sheliak.ui.screens.library.CarouselHeight
import com.crsmthw.sheliak.ui.screens.library.CarouselPreferredItemWidth
import com.crsmthw.sheliak.ui.screens.library.SectionLabel
import com.crsmthw.sheliak.ui.screens.library.albumRowKey
import com.crsmthw.sheliak.ui.screens.library.libraryArtKey
import com.crsmthw.sheliak.ui.screens.library.libraryArtSharedBounds
import com.crsmthw.sheliak.ui.screens.library.metaLine
import com.crsmthw.sheliak.ui.screens.library.playAll
import com.crsmthw.sheliak.ui.screens.library.playFrom
import com.crsmthw.sheliak.ui.screens.library.playlistArtKey
import com.crsmthw.sheliak.ui.screens.library.positionalTrackKey
import com.crsmthw.sheliak.ui.screens.library.shuffleAll
import com.crsmthw.sheliak.ui.screens.library.totalDurationMs
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.toDurationString

/*
 * The album, artist and playlist detail bodies, each drawn in one of two frames: a pushed navigation entry (an
 * overlay DetailTopBar with a back arrow, the hero clearing it, the "lib-art-…" shared art) or the right pane of a
 * two-pane library tab (under the library's own bar, which this pane's scroll collapses; no bar of its own).
 */

/** Where a detail body is drawn. */
@Immutable
sealed interface DetailMode {
    /** A pushed entry: overlay [DetailTopBar] with [onBack], hero clearance, shared art with the library card. */
    data class Screen(val onBack: () -> Unit) : DetailMode

    /** The right pane of a two-pane tab: it scrolls the library's bar through [barScroll]. */
    data class Pane(val barScroll: NestedScrollConnection) : DetailMode
}

private val DetailMode.isScreen: Boolean get() = this is DetailMode.Screen

/** Lazy-list keys of the frame's own items. */
private const val HERO_KEY = "detail-hero"
private const val MESSAGE_KEY = "detail-message"
private const val BOTTOM_KEY = "detail-bottom-room"

/**
 * The frame every detail body sits in. [paneColor] is the colour of what it sits on — the screen `background`
 * pushed, the card's `surface` in a pane — painted by the bar, the fades and (pushed) the page.
 *
 * Pushed: the list fills the screen and its hero bakes its own clearance under the overlay [DetailTopBar],
 * composed LAST so it draws and hit-tests above the rows, with [DetailTopBarFade] under it. In a pane: the list
 * hangs the library bar's connection ([DetailMode.Pane.barScroll]) and starts [BarContentGap] under the pane's top
 * edge with a [TopBarFade]. Both end with the bottom room for the fade and the player surface.
 *
 * ### Every detail opens at its top ([loaded])
 *
 * The bottom room is emitted only once the detail has [loaded]. A lazy list keeps its scroll anchored to the KEY
 * of its first visible item; while the state loads, the bottom room used to be the list's ONLY item, so when the
 * hero and the rows arrived in front of it the list held on to it — and every album, artist or playlist long
 * enough to scroll opened at its BOTTOM (M1 fix round 1, item 24; pushed and in the two-pane right pane alike,
 * each with a fresh list state). With nothing in the list until the first read, the hero lands at index 0.
 */
@Composable
internal fun DetailFrame(
    mode     : DetailMode,
    barTitle : String,
    paneColor: Color,
    listState: LazyListState,
    loaded   : Boolean,
    modifier : Modifier = Modifier,
    content  : LazyListScope.(handoff: HeroTitleHandoff?) -> Unit,
) {
    when (mode) {
        is DetailMode.Screen -> {
            val haptics = LocalHapticFeedback.current
            val handoff = rememberHeroTitleHandoff()
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(paneColor)
                    .horizontalSystemBarsPadding(),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    content(handoff)
                    if (loaded) item(key = BOTTOM_KEY, contentType = BOTTOM_KEY) { BottomFadeSpacer() }
                }
                DetailTopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
                DetailTopBar(
                    paneColor      = paneColor,
                    modifier       = Modifier.align(Alignment.TopCenter),
                    navigationIcon = {
                        IconButton(onClick = { haptics.confirm(); mode.onBack() }) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                            )
                        }
                    },
                    heroTitle      = handoff,
                    title          = { titleModifier ->
                        Text(barTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = titleModifier)
                    },
                )
            }
        }
        is DetailMode.Pane -> {
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    state    = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(mode.barScroll),
                ) {
                    content(null)
                    if (loaded) item(key = BOTTOM_KEY, contentType = BOTTOM_KEY) { BottomFadeSpacer() }
                }
                TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
            }
        }
    }
}

/** A detail whose item has left the library (a sync removed it) — one centred line under the bar. */
private fun LazyListScope.missingItem(mode: DetailMode, text: String) {
    item(key = MESSAGE_KEY) {
        Text(
            text      = text,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .then(if (mode.isScreen) Modifier.padding(top = DetailMessageTopScreen) else Modifier)
                .padding(horizontal = 32.dp, vertical = 48.dp),
        )
    }
}

/** A pushed screen's message starts below the overlay bar's height (status bar excluded — close enough there). */
private val DetailMessageTopScreen = 96.dp

/** The hero art: the item's [Artwork] filling the hero's square tile, which clips and borders it. */
@Composable
private fun HeroArt(art: ArtRef?) {
    Artwork(
        art                = art,
        sizePx             = artSizePx(DetailHeroArtSize),
        contentDescription = null,
        modifier           = Modifier.fillMaxSize(),
        shape              = RectangleShape,
    )
}

/** "Disc N" above a disc's first track on a multi-disc album. */
@Composable
private fun DiscHeader(disc: Int) {
    Text(
        text     = stringResource(R.string.library_disc, disc),
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

// ── Album ───────────────────────────────────────────────────────────────────

/**
 * An album: the hero (art, title, artist, "year · N tracks · running time", Shuffle / Play), then its tracks with
 * a "Disc N" header per disc on a multi-disc album. A tap plays the album from that track.
 */
@Composable
internal fun AlbumDetailContent(
    albumKey    : TrackKey,
    state       : AlbumDetailState,
    mode        : DetailMode,
    paneColor   : Color,
    listState   : LazyListState,
    player      : PlaybackController,
    currentTrack: TrackKey?,
    menuActions : TrackMenuActions,
    modifier    : Modifier = Modifier,
) {
    val album = state.album
    val artistFallback = stringResource(R.string.library_unknown_artist)
    val missing = stringResource(R.string.library_missing_album)
    val meta = album?.let { albumMeta(it, state.tracks) }
    DetailFrame(
        mode      = mode,
        barTitle  = album?.title.orEmpty(),
        paneColor = paneColor,
        listState = listState,
        loaded    = !state.loading,
        modifier  = modifier,
    ) { handoff ->
        when {
            state.loading -> Unit
            album == null -> missingItem(mode, missing)
            else          -> {
                item(key = HERO_KEY) {
                    DetailArtHero(
                        title        = album.title,
                        subtitle     = album.artistName.ifBlank { artistFallback },
                        meta         = meta,
                        onPlay       = if (state.tracks.isEmpty()) null else ({ player.playAll(state.tracks) }),
                        onShuffle    = if (state.tracks.isEmpty()) null else ({ player.shuffleAll(state.tracks) }),
                        barClearance = mode.isScreen,
                        artModifier  = if (mode.isScreen) Modifier.libraryArtSharedBounds(libraryArtKey(albumKey)) else Modifier,
                        titleHandoff = handoff,
                    ) { HeroArt(album.art) }
                }
                items(
                    items       = state.rows,
                    key         = ::albumRowKey,
                    contentType = { row -> if (row is AlbumRow.DiscHeader) "disc" else "track" },
                ) { row ->
                    when (row) {
                        is AlbumRow.DiscHeader -> DiscHeader(row.disc)
                        is AlbumRow.TrackItem  -> TrackRowWithMenu(
                            track          = row.track,
                            artistFallback = artistFallback,
                            isCurrent      = row.track.key == currentTrack,
                            onPlay         = { player.playFrom(state.tracks, row.index) },
                            actions        = menuActions,
                        )
                    }
                }
            }
        }
    }
}

/** "2019 · 12 tracks · 48m" for an album hero. */
@Composable
private fun albumMeta(album: Album, tracks: List<Track>): String {
    val count = if (tracks.isNotEmpty()) tracks.size else album.trackCount
    val duration = if (tracks.isNotEmpty()) totalDurationMs(tracks) else album.durationMs
    return metaLine(
        parts     = listOf(
            album.year?.toString(),
            pluralStringResource(R.plurals.library_track_count, count, count),
            duration.takeIf { it > 0L }?.toDurationString(),
        ),
        separator = stringResource(R.string.library_meta_separator),
    )
}

// ── Artist ──────────────────────────────────────────────────────────────────

/** Lazy-list keys of the artist page's own items. */
private const val ARTIST_ALBUMS_HEADER_KEY = "artist-albums-header"
private const val ARTIST_ALBUMS_ROW_KEY = "artist-albums-row"
private const val ARTIST_TRACKS_HEADER_KEY = "artist-tracks-header"

/**
 * An artist: the hero (art, name, "N albums · M tracks", Shuffle / Play over the artist's tracks — Lyra lacked
 * these only because its streaming source offered no artist track list), then "Albums" — a row of album covers newest first ([ArtistAlbumsRow]),
 * each opening its own album entry (pushed at every width, its art flying into that hero) — then "Tracks": every
 * track of the artist album by album (`LibraryRepository.artistTracks`), plain rows with no disc headers. A tap
 * plays the artist's tracks from that row; the track sheet offers "Go to album" (never "Go to artist": this is
 * the artist).
 */
@Composable
internal fun ArtistDetailContent(
    artistKey   : TrackKey,
    state       : ArtistDetailState,
    mode        : DetailMode,
    paneColor   : Color,
    listState   : LazyListState,
    player      : PlaybackController,
    currentTrack: TrackKey?,
    menuActions : TrackMenuActions,
    onOpenAlbum : (TrackKey) -> Unit,
    modifier    : Modifier = Modifier,
) {
    val artist = state.artist
    val missing = stringResource(R.string.library_missing_artist)
    val artistFallback = stringResource(R.string.library_unknown_artist)
    val albumsLabel = stringResource(R.string.library_artist_albums)
    val tracksLabel = stringResource(R.string.library_artist_tracks)
    val albumCount = if (state.albums.isNotEmpty()) state.albums.size else artist?.albumCount ?: 0
    val trackCount = state.tracks.size
    val subtitle = metaLine(
        parts     = listOf(
            pluralStringResource(R.plurals.library_album_count, albumCount, albumCount),
            if (trackCount > 0) pluralStringResource(R.plurals.library_track_count, trackCount, trackCount) else null,
        ),
        separator = stringResource(R.string.library_meta_separator),
    )
    DetailFrame(
        mode      = mode,
        barTitle  = artist?.name.orEmpty(),
        paneColor = paneColor,
        listState = listState,
        loaded    = !state.loading,
        modifier  = modifier,
    ) { handoff ->
        when {
            state.loading  -> Unit
            artist == null -> missingItem(mode, missing)
            else           -> {
                item(key = HERO_KEY) {
                    DetailArtHero(
                        title        = artist.name.ifBlank { artistFallback },
                        subtitle     = subtitle,
                        onPlay       = if (state.tracks.isEmpty()) null else ({ player.playAll(state.tracks) }),
                        onShuffle    = if (state.tracks.isEmpty()) null else ({ player.shuffleAll(state.tracks) }),
                        barClearance = mode.isScreen,
                        artModifier  = if (mode.isScreen) Modifier.libraryArtSharedBounds(libraryArtKey(artistKey)) else Modifier,
                        titleHandoff = handoff,
                    ) { HeroArt(artist.art) }
                }
                if (state.albums.isNotEmpty()) {
                    item(key = ARTIST_ALBUMS_HEADER_KEY, contentType = "header") { SectionLabel(albumsLabel) }
                    item(key = ARTIST_ALBUMS_ROW_KEY, contentType = "albums-row") {
                        ArtistAlbumsRow(albums = state.albums, onOpenAlbum = onOpenAlbum)
                    }
                }
                if (state.tracks.isNotEmpty()) {
                    item(key = ARTIST_TRACKS_HEADER_KEY, contentType = "header") { SectionLabel(tracksLabel) }
                    itemsIndexedTracks(
                        tracks         = state.tracks,
                        artistFallback = artistFallback,
                        currentTrack   = currentTrack,
                        menuActions    = menuActions,
                        onPlay         = { index -> player.playFrom(state.tracks, index) },
                    )
                }
            }
        }
    }
}

/**
 * An artist's albums, newest first, as the SAME Material 3 Expressive carousel as the Tracks tab's Most played
 * (M1 fix round 2, item 10): [HorizontalMultiBrowseCarousel] with large / medium / small items, the multi-browse
 * fling (settling on an item), the library carousels' width and FIXED height, and [CarouselArtItem] — the cover
 * with the album's title over its year on the item's own gradient label, which follows the mask's start edge and
 * fades as the item shrinks.
 *
 * It replaced an uncontained carousel whose title and year sat UNDER each cover, laid out at the item's start:
 * Material masks every carousel item around its centre and the uncontained keylines shrink the cut-off item at
 * the edge, so those labels lost their first letters wherever the mask was narrower than the item; and its free,
 * non-snapping fling stopped wherever its speed ran out — against the end of the row, speed left over, whenever a
 * fling carried that far. The multi-browse fling's target is always an item's resting place.
 *
 * Every album here PUSHES its detail, so its cover keeps the `"lib-art-…"` shared bounds and flies into that hero
 * at any width ([CarouselArtItem] explains where the bounds sit and why).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistAlbumsRow(albums: List<Album>, onOpenAlbum: (TrackKey) -> Unit) {
    val state = rememberCarouselState { albums.size }
    HorizontalMultiBrowseCarousel(
        state              = state,
        preferredItemWidth = CarouselPreferredItemWidth,
        itemSpacing        = 8.dp,
        flingBehavior      = CarouselDefaults.multiBrowseFlingBehavior(state = state),
        contentPadding     = PaddingValues(horizontal = 16.dp),
        modifier           = Modifier.fillMaxWidth().height(CarouselHeight),
    ) { index ->
        // The list can shrink under a pager that has not re-read its count yet.
        val album = albums.getOrNull(index)
        if (album != null) {
            CarouselArtItem(
                art               = album.art,
                title             = album.title,
                subtitle          = album.year?.toString(),
                onClick           = { onOpenAlbum(album.key) },
                artSharedModifier = Modifier.libraryArtSharedBounds(libraryArtKey(album.key)),
            )
        }
    }
}

// ── Playlist ────────────────────────────────────────────────────────────────

/**
 * A playlist: the hero (art, title — "Liked Songs" from a resource for that one — "N tracks · running time",
 * Shuffle / Play), then its tracks in playlist order (a track may appear twice, so rows are keyed by position).
 */
@Composable
internal fun PlaylistDetailContent(
    playlistId  : Long,
    state       : PlaylistDetailState,
    mode        : DetailMode,
    paneColor   : Color,
    listState   : LazyListState,
    player      : PlaybackController,
    currentTrack: TrackKey?,
    menuActions : TrackMenuActions,
    modifier    : Modifier = Modifier,
) {
    val playlist = state.playlist
    val missing = stringResource(R.string.library_missing_playlist)
    val artistFallback = stringResource(R.string.library_unknown_artist)
    val title = when {
        playlist == null       -> ""
        playlist.isLikedSongs -> stringResource(R.string.playback_liked_songs)
        else                   -> playlist.title
    }
    val count = if (state.tracks.isNotEmpty()) state.tracks.size else playlist?.trackCount ?: 0
    val duration = if (state.tracks.isNotEmpty()) totalDurationMs(state.tracks) else playlist?.durationMs ?: 0L
    val meta = metaLine(
        parts     = listOf(
            pluralStringResource(R.plurals.library_track_count, count, count),
            duration.takeIf { it > 0L }?.toDurationString(),
        ),
        separator = stringResource(R.string.library_meta_separator),
    )
    DetailFrame(
        mode      = mode,
        barTitle  = title,
        paneColor = paneColor,
        listState = listState,
        loaded    = !state.loading,
        modifier  = modifier,
    ) { handoff ->
        when {
            state.loading    -> Unit
            playlist == null -> missingItem(mode, missing)
            else             -> {
                item(key = HERO_KEY) {
                    DetailArtHero(
                        title        = title,
                        subtitle     = meta,
                        onPlay       = if (state.tracks.isEmpty()) null else ({ player.playAll(state.tracks) }),
                        onShuffle    = if (state.tracks.isEmpty()) null else ({ player.shuffleAll(state.tracks) }),
                        barClearance = mode.isScreen,
                        artModifier  = if (mode.isScreen) Modifier.libraryArtSharedBounds(playlistArtKey(playlistId)) else Modifier,
                        titleHandoff = handoff,
                    ) { HeroArt(playlist.art) }
                }
                itemsIndexedTracks(
                    tracks         = state.tracks,
                    artistFallback = artistFallback,
                    currentTrack   = currentTrack,
                    menuActions    = menuActions,
                    onPlay         = { index -> player.playFrom(state.tracks, index) },
                )
            }
        }
    }
}

/** Track rows keyed by position (a playlist may hold one track twice). */
private fun LazyListScope.itemsIndexedTracks(
    tracks        : List<Track>,
    artistFallback: String,
    currentTrack  : TrackKey?,
    menuActions   : TrackMenuActions,
    onPlay        : (Int) -> Unit,
) {
    items(
        count       = tracks.size,
        key         = { index -> positionalTrackKey(tracks[index], index) },
        contentType = { "track" },
    ) { index ->
        val track = tracks[index]
        TrackRowWithMenu(
            track          = track,
            artistFallback = artistFallback,
            isCurrent      = track.key == currentTrack,
            onPlay         = { onPlay(index) },
            actions        = menuActions,
        )
    }
}
