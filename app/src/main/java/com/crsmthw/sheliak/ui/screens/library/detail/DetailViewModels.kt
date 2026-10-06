package com.crsmthw.sheliak.ui.screens.library.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crsmthw.sheliak.data.repository.LibraryRepository
import com.crsmthw.sheliak.di.AppContainer
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.PlaybackController
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.screens.library.AlbumRow
import com.crsmthw.sheliak.ui.screens.library.albumRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/*
 * The three detail screens' states, the flows that build them from the library (shared by the pushed entries'
 * ViewModels below and by the two-pane library's right pane, which collects the same flows), and the entries'
 * ViewModels with their factories.
 */

// ── States ──────────────────────────────────────────────────────────────────

/** An album and its rows. [loading] until the first read lands; a loaded null [album] = no longer in the library. */
@Immutable
data class AlbumDetailState(
    val loading: Boolean,
    val album  : Album?,
    val tracks : List<Track>,
    val rows   : List<AlbumRow>,
) {
    companion object {
        val Loading = AlbumDetailState(loading = true, album = null, tracks = emptyList(), rows = emptyList())
    }
}

/** An artist and their albums, newest first. */
@Immutable
data class ArtistDetailState(
    val loading: Boolean,
    val artist : Artist?,
    val albums : List<Album>,
) {
    companion object {
        val Loading = ArtistDetailState(loading = true, artist = null, albums = emptyList())
    }
}

/** A playlist and its tracks, in playlist order. */
@Immutable
data class PlaylistDetailState(
    val loading : Boolean,
    val playlist: Playlist?,
    val tracks  : List<Track>,
) {
    companion object {
        val Loading = PlaylistDetailState(loading = true, playlist = null, tracks = emptyList())
    }
}

// ── Flows ───────────────────────────────────────────────────────────────────

/** [key]'s album, live (a sync re-emits it). */
fun albumDetailFlow(library: LibraryRepository, key: TrackKey): Flow<AlbumDetailState> =
    combine(library.album(key), library.albumTracks(key)) { album, tracks ->
        AlbumDetailState(loading = false, album = album, tracks = tracks, rows = albumRows(tracks))
    }

/** [key]'s artist and albums, live. */
fun artistDetailFlow(library: LibraryRepository, key: TrackKey): Flow<ArtistDetailState> =
    combine(library.artist(key), library.artistAlbums(key)) { artist, albums ->
        ArtistDetailState(loading = false, artist = artist, albums = albums)
    }

/** Playlist [id] and its tracks, live. */
fun playlistDetailFlow(library: LibraryRepository, id: Long): Flow<PlaylistDetailState> =
    combine(library.playlist(id), library.playlistTracks(id)) { playlist, tracks ->
        PlaylistDetailState(loading = false, playlist = playlist, tracks = tracks)
    }

/** Kept alive five seconds past the last collector, like every screen state, so a rotation does not re-read. */
private const val STOP_TIMEOUT_MILLIS = 5_000L

private fun <T> Flow<T>.screenState(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initial)

// ── ViewModels ──────────────────────────────────────────────────────────────

/**
 * The pushed album entry. [player] is the app's [PlaybackController] (commands are local and immediate);
 * [playerStates] is its state, collected by the screen with the lifecycle only.
 */
class AlbumDetailViewModel(
    library    : LibraryRepository,
    manager    : PlayerStateManager,
    key        : TrackKey,
) : ViewModel() {
    val player: PlaybackController = manager
    val playerStates: StateFlow<PlayerState> = manager.state
    val state: StateFlow<AlbumDetailState> = albumDetailFlow(library, key).screenState(this, AlbumDetailState.Loading)
}

/** The pushed artist entry. */
class ArtistDetailViewModel(
    library    : LibraryRepository,
    manager    : PlayerStateManager,
    key        : TrackKey,
) : ViewModel() {
    val player: PlaybackController = manager
    val playerStates: StateFlow<PlayerState> = manager.state
    val state: StateFlow<ArtistDetailState> = artistDetailFlow(library, key).screenState(this, ArtistDetailState.Loading)
}

/** The pushed playlist entry. */
class PlaylistDetailViewModel(
    library    : LibraryRepository,
    manager    : PlayerStateManager,
    id         : Long,
) : ViewModel() {
    val player: PlaybackController = manager
    val playerStates: StateFlow<PlayerState> = manager.state
    val state: StateFlow<PlaylistDetailState> = playlistDetailFlow(library, id).screenState(this, PlaylistDetailState.Loading)
}

// ── Factories ───────────────────────────────────────────────────────────────

/** Builds [AlbumDetailViewModel] for [key] from the app container (SettingsViewModelFactory's shape). */
class AlbumDetailViewModelFactory(
    private val container: AppContainer,
    private val key      : TrackKey,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AlbumDetailViewModel::class.java)) {
            "AlbumDetailViewModelFactory cannot create ${modelClass.name}"
        }
        return checkNotNull(
            modelClass.cast(AlbumDetailViewModel(container.libraryRepository, container.playerStateManager, key)),
        )
    }
}

/** Builds [ArtistDetailViewModel] for [key] from the app container. */
class ArtistDetailViewModelFactory(
    private val container: AppContainer,
    private val key      : TrackKey,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ArtistDetailViewModel::class.java)) {
            "ArtistDetailViewModelFactory cannot create ${modelClass.name}"
        }
        return checkNotNull(
            modelClass.cast(ArtistDetailViewModel(container.libraryRepository, container.playerStateManager, key)),
        )
    }
}

/** Builds [PlaylistDetailViewModel] for playlist [id] from the app container. */
class PlaylistDetailViewModelFactory(
    private val container: AppContainer,
    private val id       : Long,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PlaylistDetailViewModel::class.java)) {
            "PlaylistDetailViewModelFactory cannot create ${modelClass.name}"
        }
        return checkNotNull(
            modelClass.cast(PlaylistDetailViewModel(container.libraryRepository, container.playerStateManager, id)),
        )
    }
}
