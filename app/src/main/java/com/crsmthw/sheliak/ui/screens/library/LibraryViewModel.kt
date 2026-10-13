package com.crsmthw.sheliak.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crsmthw.sheliak.data.db.NormalizedKeys
import com.crsmthw.sheliak.data.repository.LibraryCounts
import com.crsmthw.sheliak.data.repository.LibraryRepository
import com.crsmthw.sheliak.data.repository.SortedRows
import com.crsmthw.sheliak.data.repository.SourcesRepository
import com.crsmthw.sheliak.di.AppContainer
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.PlaybackController
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.components.AlphabetIndex
import com.crsmthw.sheliak.ui.components.alphabetIndexOf
import com.crsmthw.sheliak.ui.screens.library.detail.AlbumDetailState
import com.crsmthw.sheliak.ui.screens.library.detail.ArtistDetailState
import com.crsmthw.sheliak.ui.screens.library.detail.PlaylistDetailState
import com.crsmthw.sheliak.ui.screens.library.detail.albumDetailFlow
import com.crsmthw.sheliak.ui.screens.library.detail.artistDetailFlow
import com.crsmthw.sheliak.ui.screens.library.detail.playlistDetailFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Library entry: the four tabs' lists over the index (Room flows — a sync batch or a recorded play re-emits
 * them), their alphabet scroller sections, the counts for the bar's subtitles, the sync progress for the Tracks
 * subtitle, the Most played carousel, and the pull-to-refresh round. Every list is null until its first read
 * lands, so a tab shows nothing rather than an empty state it is about to replace.
 *
 * Each state is kept alive five seconds past the last collector (a rotation, a quick round trip) and never
 * eagerly. [playerState] is the player's own flow, collected by the screens with the lifecycle only — its
 * subscribers are what keep the player connected.
 */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val sources: SourcesRepository,
    manager            : PlayerStateManager,
) : ViewModel() {

    /** The app's player commands (local and immediate). */
    val player: PlaybackController = manager

    val playerState: StateFlow<PlayerState> = manager.state

    val counts: StateFlow<LibraryCounts> = library.counts.state(LibraryCounts.Empty)

    /** Items written so far across every running sync, or null while none runs. */
    val syncProgress: StateFlow<Int?> =
        sources.syncStatesByProvider.map(::syncProgressOf).distinctUntilChanged().state(null)

    /** Whether any source is configured; null until known (the empty states wait for it). */
    val hasSources: StateFlow<Boolean?> =
        sources.sources.map { it.isNotEmpty() }.distinctUntilChanged().state(null)

    val tracks: StateFlow<List<Track>?> = library.tracks.state(null)

    /** The albums with their sort keys, read once and shared by [albums] and [albumIndex] (one Room query). */
    private val sortedAlbums: SharedFlow<SortedRows<Album>> = library.albumsSorted.shared()

    val albums: StateFlow<List<Album>?> = sortedAlbums.map { it.items }.state(null)

    /** The artists with their sort keys, shared by [artists] and [artistIndex]. */
    private val sortedArtists: SharedFlow<SortedRows<Artist>> = library.artistsSorted.shared()

    val artists: StateFlow<List<Artist>?> = sortedArtists.map { it.items }.state(null)

    val playlists: StateFlow<List<Playlist>?> = library.playlists.state(null)

    // The alphabet fast scroller's sections, one per list. Built off the main thread from the keys each list is
    // ordered by: a track's `titleSort`; an album's / artist's index sort key (the provider's own sort title when
    // it sent one, so "The Beatles" files under B where the list has it); a playlist's normalised title (the
    // playlists are not ordered alphabetically — Liked Songs first — so their sections keep each letter's FIRST
    // run, see alphabetIndexOf).

    val trackIndex: StateFlow<AlphabetIndex> =
        tracks.map { list -> list?.let { rows -> alphabetIndexOf(rows.map(Track::titleSort)) } ?: AlphabetIndex.Empty }
            .indexState()

    val albumIndex: StateFlow<AlphabetIndex> = sortedAlbums.map { alphabetIndexOf(it.sortKeys) }.indexState()

    val artistIndex: StateFlow<AlphabetIndex> = sortedArtists.map { alphabetIndexOf(it.sortKeys) }.indexState()

    val playlistIndex: StateFlow<AlphabetIndex> =
        playlists.map { list ->
            list?.let { rows -> alphabetIndexOf(rows.map { NormalizedKeys.sortKey(it.title) }) } ?: AlphabetIndex.Empty
        }.indexState()

    /**
     * The Tracks tab's one carousel, Most played ([mostPlayedTracks]); null until the first read lands, so the tab
     * waits for it before listing anything. (Recently played left the Tracks page in M1 fix round 1.)
     */
    val mostPlayed: StateFlow<List<Track>?> = library.mostPlayed().map(::mostPlayedTracks).state(null)

    private val refreshing = MutableStateFlow(false)

    /** True while a pull-to-refresh round holds its indicator ([awaitSyncRound]). */
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    /**
     * Pull-to-refresh on any library list: sync every source now, and hold the indicator for the round
     * ([awaitSyncRound]: until the syncs end, at most [REFRESH_MAX_HOLD_MS]). A pull during a round is ignored.
     */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                sources.syncAll()
                awaitSyncRound(sources.syncStatesByProvider)
            } finally {
                refreshing.value = false
            }
        }
    }

    /** A playlist card's Play: the playlist from its first track, without opening it. */
    fun playPlaylist(id: Long) {
        viewModelScope.launch {
            player.playAll(library.playlistTracks(id).first())
        }
    }

    // The two-pane tabs' right pane draws the same bodies as the pushed detail entries, from the same flows; the
    // pane collects them with the lifecycle for as long as it shows that item.

    fun albumDetail(key: TrackKey): Flow<AlbumDetailState> = albumDetailFlow(library, key)

    fun artistDetail(key: TrackKey): Flow<ArtistDetailState> = artistDetailFlow(library, key)

    fun playlistDetail(id: Long): Flow<PlaylistDetailState> = playlistDetailFlow(library, id)

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initial)

    private fun <T> Flow<T>.shared(): SharedFlow<T> =
        shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private fun Flow<AlphabetIndex>.indexState(): StateFlow<AlphabetIndex> =
        distinctUntilChanged().flowOn(Dispatchers.Default).state(AlphabetIndex.Empty)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Builds [LibraryViewModel] from the app container (SettingsViewModelFactory's shape). */
class LibraryViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            "LibraryViewModelFactory cannot create ${modelClass.name}"
        }
        return checkNotNull(
            modelClass.cast(
                LibraryViewModel(container.libraryRepository, container.sourcesRepository, container.playerStateManager),
            ),
        )
    }
}
