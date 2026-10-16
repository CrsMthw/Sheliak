package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.player.PlaybackController
import com.crsmthw.sheliak.ui.components.TrackActionsMenu
import com.crsmthw.sheliak.ui.components.TrackMenuActions
import com.crsmthw.sheliak.ui.components.TrackRowWithMenu

/**
 * The Tracks tab, the library's home. A single pane at every width — under the library bar ("Sheliak" over
 * "N tracks", or "Syncing… N" while a source syncs), the ONE carousel, "Most played" (only once something has been
 * played: [mostPlayedTracks]), then All tracks with the alphabet fast scroller. With no art hero, a wide window
 * never inflates the header. A tap plays from that track with its list (the carousel, or every track) as the
 * queue; a long-press opens the track's sheet. Pull to sync every source.
 *
 * Nothing is listed until BOTH the tracks and the Most played read have landed: a carousel arriving after the
 * rows would be inserted above a list already anchored on its first row, and stay hidden above the top.
 */
@Composable
internal fun TracksTab(
    viewModel             : LibraryViewModel,
    hasSources            : Boolean?,
    listState             : LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    navigation            : LibraryNavigation,
    paneColor             : Color,
    modifier              : Modifier = Modifier,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val alphabet by viewModel.trackIndex.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val currentTrack by rememberCurrentTrackKey(viewModel.playerState)
    val player = viewModel.player
    val menu = rememberTrackMenuActions(player, navigation.onOpenAlbum, navigation.onOpenArtist)

    val artistFallback = stringResource(R.string.library_unknown_artist)
    val emptyTitle = stringResource(R.string.library_empty_tracks)
    val emptyBody = emptyBodyFor(hasSources, syncProgress)
    val allTracksLabel = stringResource(R.string.library_all_tracks)
    val mostPlayedLabel = stringResource(R.string.library_most_played)

    val list = tracks
    val carousel = mostPlayed.orEmpty()
    val emptyKind = emptyStateFor(
        hasSources = hasSources,
        listLoaded = list != null && mostPlayed != null,
        listEmpty  = list.isNullOrEmpty(),
    )
    // The rows start after the carousel and its "All tracks" label.
    val firstRowItem = if (emptyKind == null && carousel.isNotEmpty()) CAROUSEL_ITEMS_ABOVE_ROWS else 0
    LibraryRefreshFrame(
        isRefreshing = isRefreshing,
        onRefresh    = viewModel::refresh,
        paneColor    = paneColor,
        modifier     = modifier,
        overlay      = {
            if (emptyKind == null) {
                LibraryFastScroller(listState = listState, index = alphabet, firstRowItem = firstRowItem)
            }
        },
    ) {
        LibraryTabList(
            listState              = listState,
            nestedScrollConnection = nestedScrollConnection,
            bottomRoom             = emitsBottomRoom(emptyKind),
        ) {
            when (emptyKind) {
                LibraryEmptyKind.NOTHING_YET -> Unit
                LibraryEmptyKind.NO_SOURCE,
                LibraryEmptyKind.EMPTY_LIST  -> item(key = EMPTY_STATE_KEY) {
                    LibraryEmptyState(
                        icon        = Icons.Outlined.LibraryMusic,
                        title       = emptyTitle,
                        body        = emptyBody,
                        onAddSource = navigation.onAddSource.takeIf { hasSources == false },
                    )
                }
                null -> {
                    val rows = list.orEmpty()
                    if (carousel.isNotEmpty()) {
                        item(key = MOST_PLAYED_KEY, contentType = "carousel") {
                            MostPlayedCarousel(
                                label       = mostPlayedLabel,
                                tracks      = carousel,
                                player      = player,
                                menuActions = menu,
                            )
                        }
                        item(key = ALL_TRACKS_HEADER_KEY, contentType = "header") { SectionLabel(allTracksLabel) }
                    }
                    itemsIndexed(
                        items       = rows,
                        key         = { _, track -> trackKeyOf(track) },
                        contentType = { _, _ -> "track" },
                    ) { index, track ->
                        TrackRowWithMenu(
                            track          = track,
                            artistFallback = artistFallback,
                            isCurrent      = track.key == currentTrack,
                            onPlay         = { player.playFrom(rows, index) },
                            actions        = menu,
                        )
                    }
                }
            }
        }
    }
}

/** Lazy-list keys of the Tracks tab's own items above the rows. */
private const val MOST_PLAYED_KEY = "carousel-most-played"
private const val ALL_TRACKS_HEADER_KEY = "all-tracks-header"

/** How many lazy items sit above the first track row when the carousel shows: the carousel and its label. */
private const val CAROUSEL_ITEMS_ABOVE_ROWS = 2

/**
 * The body line of a tab's empty card: how music gets here when there is no source; that a sync is reading the
 * library while one runs; else that a pull syncs it.
 */
@Composable
internal fun emptyBodyFor(hasSources: Boolean?, syncProgress: Int?): String = stringResource(
    when {
        hasSources != true   -> R.string.library_empty_body
        syncProgress != null -> R.string.library_empty_syncing
        else                 -> R.string.library_empty_pull
    },
)

/** A section label over a carousel or a list (Tracks' "Most played" / "All tracks", an artist's sections). */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

/**
 * "Most played": its label over Material 3 Expressive's [HorizontalMultiBrowseCarousel] — large, medium and small
 * items that grow and shrink as they scroll, masked by the carousel itself, with the multi-browse fling (several
 * items per fling, settling on an item); the library carousels' shared width and height
 * ([CarouselPreferredItemWidth], [CarouselHeight]). Each item is the track's art with the M3 item label (title and
 * artist on a bottom gradient) fading as the item shrinks ([CarouselArtItem], the artist page's Albums row's item
 * too). A tap plays from that item with the carousel as the queue; a long-press opens the track's sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MostPlayedCarousel(
    label      : String,
    tracks     : List<Track>,
    player     : PlaybackController,
    menuActions: TrackMenuActions,
) {
    val artistFallback = stringResource(R.string.library_unknown_artist)
    val trackActionsLabel = stringResource(R.string.track_actions)
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(label)
        val state = rememberCarouselState { tracks.size }
        HorizontalMultiBrowseCarousel(
            state              = state,
            preferredItemWidth = CarouselPreferredItemWidth,
            itemSpacing        = 8.dp,
            flingBehavior      = CarouselDefaults.multiBrowseFlingBehavior(state = state),
            contentPadding     = PaddingValues(horizontal = 16.dp),
            modifier           = Modifier.fillMaxWidth().height(CarouselHeight),
        ) { index ->
            // The list can shrink under a pager that has not re-read its count yet.
            val track = tracks.getOrNull(index)
            if (track != null) {
                var sheetOpen by remember { mutableStateOf(false) }
                CarouselArtItem(
                    art              = track.art,
                    title            = track.title,
                    subtitle         = track.artistName.ifBlank { artistFallback },
                    onClick          = { player.playFrom(tracks, index) },
                    onLongClick      = { sheetOpen = true },
                    onLongClickLabel = trackActionsLabel,
                )
                TrackActionsMenu(
                    track     = track,
                    expanded  = sheetOpen,
                    onDismiss = { sheetOpen = false },
                    actions   = menuActions,
                )
            }
        }
    }
}
