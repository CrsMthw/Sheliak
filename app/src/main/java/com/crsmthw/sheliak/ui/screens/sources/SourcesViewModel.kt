package com.crsmthw.sheliak.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.repository.Source
import com.crsmthw.sheliak.data.repository.SourcesRepository
import com.crsmthw.sheliak.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings → Sources: the configured sources with their track counts, last sync and live sync state, and the two
 * per-source actions. [sources] is null until the first read lands. A failed removal is reported through
 * [error] (the provider's failure key; the screen words it) until [dismissError].
 */
class SourcesViewModel(private val repository: SourcesRepository) : ViewModel() {

    val sources: StateFlow<List<Source>?> =
        repository.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _error = MutableStateFlow<ProviderError?>(null)
    val error: StateFlow<ProviderError?> = _error.asStateFlow()

    /** Queues an expedited sync of [id] (kept if one is already queued or running). */
    fun syncNow(id: String) = repository.syncNow(id)

    /** Removes [id] and everything it put in the library (after the screen's confirmation). */
    fun remove(id: String) {
        viewModelScope.launch {
            repository.remove(id).onFailure { _error.value = ProviderError.of(it) }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Builds [SourcesViewModel] from the app container (SettingsViewModelFactory's shape). */
class SourcesViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SourcesViewModel::class.java)) {
            "SourcesViewModelFactory cannot create ${modelClass.name}"
        }
        return checkNotNull(modelClass.cast(SourcesViewModel(container.sourcesRepository)))
    }
}
