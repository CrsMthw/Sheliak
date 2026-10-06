package com.crsmthw.sheliak.data.repository

import com.crsmthw.sheliak.data.local.SheliakDataStore
import com.crsmthw.sheliak.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * The settings seam ViewModels depend on. A 1:1 pass-through today; it exists so the storage behind it can
 * change (a migration, a second store) without touching any ViewModel.
 */
class SettingsRepository(private val dataStore: SheliakDataStore) {

    val themeMode           : Flow<ThemeMode> = dataStore.themeMode
    val amoledBlack         : Flow<Boolean>   = dataStore.amoledBlack
    val dynamicColor        : Flow<Boolean>   = dataStore.dynamicColor
    val accentColor         : Flow<Int>       = dataStore.accentColor
    val hapticsEnabled      : Flow<Boolean>   = dataStore.hapticsEnabled
    val introDone           : Flow<Boolean>   = dataStore.introDone
    val mergeDuplicates     : Flow<Boolean>   = dataStore.mergeDuplicates
    val transcodeBitrateKbps: Flow<Int>       = dataStore.transcodeBitrateKbps

    suspend fun setThemeMode(mode: ThemeMode)        = dataStore.setThemeMode(mode)
    suspend fun setAmoledBlack(enabled: Boolean)     = dataStore.setAmoledBlack(enabled)
    suspend fun setDynamicColor(enabled: Boolean)    = dataStore.setDynamicColor(enabled)
    suspend fun setAccentColor(argb: Int)            = dataStore.setAccentColor(argb)
    suspend fun setHapticsEnabled(enabled: Boolean)  = dataStore.setHapticsEnabled(enabled)
    suspend fun setIntroDone(done: Boolean)          = dataStore.setIntroDone(done)
    suspend fun setMergeDuplicates(enabled: Boolean) = dataStore.setMergeDuplicates(enabled)
    suspend fun setTranscodeBitrateKbps(kbps: Int)   = dataStore.setTranscodeBitrateKbps(kbps)
}
