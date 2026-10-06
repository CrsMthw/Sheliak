package com.crsmthw.sheliak.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crsmthw.sheliak.data.local.SheliakDataStore
import com.crsmthw.sheliak.data.repository.SettingsRepository
import com.crsmthw.sheliak.data.repository.SourcesRepository
import com.crsmthw.sheliak.di.AppContainer
import com.crsmthw.sheliak.ui.theme.ACCENT_DEFAULT_ARGB
import com.crsmthw.sheliak.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings and its Theme sheet. Each setting is a [StateFlow] over the repository, kept alive for five seconds
 * after the last collector stops so a rotation or a quick round trip does not re-read DataStore; the initial
 * values are the stored defaults, so the first frame shows what an untouched install would. Writes are
 * fire-and-forget in [viewModelScope]: DataStore serialises them, and the change comes back through the flows.
 * [sourceCount] (the Sources row's subtitle) is null until the sources have been read.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    sources             : SourcesRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.state(ThemeMode.SYSTEM)

    val amoledBlack: StateFlow<Boolean> = settings.amoledBlack.state(false)

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor.state(true)

    /** The accent seed (ARGB) used while Material You is off. */
    val accentColor: StateFlow<Int> = settings.accentColor.state(ACCENT_DEFAULT_ARGB)

    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled.state(true)

    /** The bit rate a lossy transcode is requested at (Playback → Transcode bit rate). */
    val transcodeBitrateKbps: StateFlow<Int> =
        settings.transcodeBitrateKbps.state(SheliakDataStore.TRANSCODE_BITRATE_DEFAULT_KBPS)

    /** Playback → Merge duplicates: one row per album / artist / track across sources. */
    val mergeDuplicates: StateFlow<Boolean> = settings.mergeDuplicates.state(false)

    /** How many sources are configured; null until known. */
    val sourceCount: StateFlow<Int?> = sources.sources.map { it.size }.distinctUntilChanged().state(null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { settings.setAmoledBlack(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setAccentColor(argb: Int) {
        viewModelScope.launch { settings.setAccentColor(argb) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHapticsEnabled(enabled) }
    }

    fun setTranscodeBitrateKbps(kbps: Int) {
        viewModelScope.launch { settings.setTranscodeBitrateKbps(kbps) }
    }

    fun setMergeDuplicates(enabled: Boolean) {
        viewModelScope.launch { settings.setMergeDuplicates(enabled) }
    }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initial)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Builds [SettingsViewModel] from the app container. `Class.cast` rather than an unchecked `as T`: the factory
 * is only ever asked for this one class, and asking for another fails loudly here instead of later.
 */
class SettingsViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "SettingsViewModelFactory cannot create ${modelClass.name}"
        }
        // `Class.cast` is annotated nullable (it passes a null through); the instance here never is.
        return checkNotNull(
            modelClass.cast(SettingsViewModel(container.settingsRepository, container.sourcesRepository)),
        )
    }
}
