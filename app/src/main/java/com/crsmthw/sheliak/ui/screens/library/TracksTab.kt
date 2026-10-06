package com.crsmthw.sheliak.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
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
 * "N tracks", or "Syncing… N" while a source syncs), the Recently played and Most played carousels (only once
 * there is play history: [carouselSections]), then All tracks. With no art hero, a wide window never inflates the
 * header. A tap plays from that track with its list (the carousel, or every track) as the queue; a long-press
 * opens the track's menu. Pull to sync every source.
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
    val carousels by viewModel.carousels.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val currentTrack by rememberCurrentTrackKey(viewModel.playerState)
    val player = viewModel.player
    val menu = rememberTrackMenuActions(player, navigation.onOpenAlbum, navigation.onOpenArtist)

    val artistFallback = stringResource(R.string.library_unknown_artist)
    val emptyTitle = stringResource(R.string.library_empty_tracks)
    val emptyBody = emptyBodyFor(hasSources, syncProgress)
    val allTracksLabel = stringResource(R.string.library_all_tracks)
    val carouselLabels = CarouselKind.entries.associateWith { stringResource(it.labelRes) }

    val list = tracks
    LibraryRefreshFrame(
        isRefreshing = isRefreshing,
        onRefresh    = viewModel::refresh,
        paneColor    = paneColor,
        modifier     = modifier,
    ) {
        LibraryTabList(listState = listState, nestedScrollConnection = nestedScrollConnection) {
            when (emptyStateFor(hasSources, listLoaded = list != null, listEmpty = list.isNullOrEmpty())) {
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
                    carousels.forEach { section ->
                        item(key = "carousel-${section.kind.name}", contentType = "carousel") {
                            TrackCarousel(
                                label        = carouselLabels.getValue(section.kind),
                                tracks       = section.tracks,
                                player       = player,
                                menuActions  = menu,
                            )
                        }
                    }
                    if (carousels.isNotEmpty()) {
                        item(key = "all-tracks-header", contentType = "header") { SectionLabel(allTracksLabel) }
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

/** A section label over a carousel or the All tracks list. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

/** Each carousel card's width — the M3 uncontained carousel keeps every item this wide. */
private val CarouselItemWidth = 140.dp

/**
 * One carousel: its label over Material's [HorizontalUncontainedCarousel] of square-art track cards
 * ([CarouselTrackCard]). A tap plays from that card with the carousel as the queue; a long-press opens the
 * track's menu under the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackCarousel(
    label      : String,
    tracks     : List<Track>,
    player     : PlaybackController,
    menuActions: TrackMenuActions,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(label)
        HorizontalUncontainedCarousel(
            state          = rememberCarouselState { tracks.size },
            itemWidth      = CarouselItemWidth,
            itemSpacing    = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier       = Modifier.fillMaxWidth(),
        ) { index ->
            val track = tracks[index]
            CarouselCardWithMenu(
                track       = track,
                onPlay      = { player.playFrom(tracks, index) },
                menuActions = menuActions,
            )
        }
    }
}

/** A carousel card with its long-press menu, composed only once opened (as `TrackRowWithMenu` does). */
@Composable
private fun CarouselCardWithMenu(
    track      : Track,
    onPlay     : () -> Unit,
    menuActions: TrackMenuActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuUsed by remember { mutableStateOf(false) }
    Box {
        CarouselTrackCard(
            track       = track,
            onClick     = onPlay,
            onLongClick = { menuUsed = true; menuOpen = true },
        )
        if (menuUsed) {
            TrackActionsMenu(
                track     = track,
                expanded  = menuOpen,
                onDismiss = { menuOpen = false },
                actions   = menuActions,
            )
        }
    }
}

/** A carousel's label. */
@get:StringRes
private val CarouselKind.labelRes: Int
    get() = when (this) {
        CarouselKind.RECENTLY_PLAYED -> R.string.library_recently_played
        CarouselKind.MOST_PLAYED     -> R.string.library_most_played
    }
