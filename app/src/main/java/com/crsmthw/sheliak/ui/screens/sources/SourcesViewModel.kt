package com.crsmthw.sheliak.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.plex.PlexSetup
import com.crsmthw.sheliak.data.repository.Source
import com.crsmthw.sheliak.data.repository.SourcesRepository
import com.crsmthw.sheliak.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the screen's actions a [SourcesFailure] belongs to (its dialog title). */
enum class SourcesAction { REMOVE, FORGET_ACCOUNT }

/** A failed action and the provider's failure key (the screen words it). */
data class SourcesFailure(val action: SourcesAction, val error: ProviderError)

/**
 * Settings → Sources: the configured sources with their track counts, last sync and live sync state, the two
 * per-source actions, and the Plex account ([plexSignedIn], "Forget Plex account"). [sources] is null until the
 * first read lands. A failed action is reported through [error] until [dismissError].
 */
class SourcesViewModel(
    private val repository: SourcesRepository,
    private val plexAccount: PlexSetup,
) : ViewModel() {

    val sources: StateFlow<List<Source>?> =
        repository.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Moves after "Forget Plex account", which changes no source but does change [plexSignedIn]. */
    private val accountChanges = MutableStateFlow(0)

    /**
     * True while a plex.tv account token is stored. Read again whenever the set of sources changes (a server added
     * through the setup stores the token; this entry stays alive under the setup) and after a forget — not on
     * every sync progress tick, which re-emits the same sources.
     */
    val plexSignedIn: StateFlow<Boolean> =
        combine(
            repository.sources.map { list -> list.map { it.instance.id } }.distinctUntilChanged(),
            accountChanges,
        ) { _, _ -> plexAccount.hasAccount() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    private val _error = MutableStateFlow<SourcesFailure?>(null)
    val error: StateFlow<SourcesFailure?> = _error.asStateFlow()

    /** Queues an expedited sync of [id] (kept if one is already queued or running). */
    fun syncNow(id: String) = repository.syncNow(id)

    /**
     * Removes [id] and everything it put in the library (after the screen's confirmation). A Plex server's own
     * token goes with it; the Plex account token stays, so adding it again skips the sign-in.
     */
    fun remove(id: String) {
        viewModelScope.launch {
            repository.remove(id).onFailure { _error.value = SourcesFailure(SourcesAction.REMOVE, ProviderError.of(it)) }
        }
    }

    /** Deletes the stored Plex account token (after the screen's confirmation); the added servers keep playing. */
    fun forgetPlexAccount() {
        viewModelScope.launch {
            plexAccount.forgetAccount()
                .onFailure { _error.value = SourcesFailure(SourcesAction.FORGET_ACCOUNT, ProviderError.of(it)) }
            accountChanges.update { it + 1 }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Builds [SourcesViewModel] from the app container and the Application the creation extras carry
 * (`APPLICATION_KEY`, for the [PlexSetup] facade — the container keeps its own context private), on the
 * extras-taking `create` as PlexSetupViewModelFactory does.
 */
class SourcesViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SourcesViewModel::class.java)) {
            "SourcesViewModelFactory cannot create ${modelClass.name}"
        }
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
            "SourcesViewModelFactory needs the Application in its creation extras"
        }
        val plexAccount = PlexSetup(application, container.credentialStore, container.httpClient, container.json)
        return checkNotNull(modelClass.cast(SourcesViewModel(container.sourcesRepository, plexAccount)))
    }
}
