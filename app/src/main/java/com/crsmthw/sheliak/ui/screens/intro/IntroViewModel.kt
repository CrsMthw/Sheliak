package com.crsmthw.sheliak.ui.screens.intro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crsmthw.sheliak.data.repository.SourcesRepository
import com.crsmthw.sheliak.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Intro's one question: is a source configured yet (null until known)? It decides the primary action. */
class IntroViewModel(sources: SourcesRepository) : ViewModel() {
    val hasSources: StateFlow<Boolean?> = sources.sources
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Builds [IntroViewModel] from the app container (SettingsViewModelFactory's shape). */
class IntroViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(IntroViewModel::class.java)) {
            "IntroViewModelFactory cannot create ${modelClass.name}"
        }
        return checkNotNull(modelClass.cast(IntroViewModel(container.sourcesRepository)))
    }
}
