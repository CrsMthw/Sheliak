package com.crsmthw.sheliak.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crsmthw.sheliak.data.repository.LibraryRepository
import com.crsmthw.sheliak.data.repository.SearchResults
import com.crsmthw.sheliak.di.AppContainer
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.player.PlaybackController
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.screens.library.playAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Search over the local index (`LibraryRepository.search`: FTS prefix matching on titles, artists and albums, and
 * playlist titles). The field's text arrives through [setQuery]; [results] follows it — debounced while the user
 * types, at once when the field is cleared — and is null while the field is blank (the screen shows its hint).
 * Results are live: a sync landing while they show updates them.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val library: LibraryRepository,
    manager            : PlayerStateManager,
) : ViewModel() {

    /** The app's player commands (local and immediate). */
    val player: PlaybackController = manager

    /** The player's state, collected by the screen with the lifecycle only (the current-track colour). */
    val playerState: StateFlow<PlayerState> = manager.state

    private val query = MutableStateFlow("")

    val results: StateFlow<SearchResults?> = query
        .debounce { text -> if (text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text -> if (text.isBlank()) flowOf(null) else library.search(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The field's current text. */
    fun setQuery(text: String) {
        query.value = text
    }

    /** A playlist result's Play: the playlist from its first track, without opening it. */
    fun playPlaylist(id: Long) {
        viewModelScope.launch { player.playAll(library.playlistTracks(id).first()) }
    }

    private companion object {
        /** Long enough to skip the keystrokes of a word being typed, short enough to feel live. */
        const val SEARCH_DEBOUNCE_MS = 150L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Builds [SearchViewModel] from the app container (SettingsViewModelFactory's shape). */
class SearchViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            "SearchViewModelFactory cannot create ${modelClass.name}"
        }
        return checkNotNull(modelClass.cast(SearchViewModel(container.libraryRepository, container.playerStateManager)))
    }
}
