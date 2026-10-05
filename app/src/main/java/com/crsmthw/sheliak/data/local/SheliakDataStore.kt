package com.crsmthw.sheliak.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crsmthw.sheliak.ui.theme.ACCENT_DEFAULT_ARGB
import com.crsmthw.sheliak.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// Top-level delegate: DataStore must be a single instance per file, and the delegate guarantees that.
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "sheliak_settings")

/**
 * The app's settings, one Preferences file. Every read is a cold [Flow] with its default applied here, so a key
 * that was never written reads as its default and callers never see a null.
 */
class SheliakDataStore(private val context: Context) {

    /**
     * Every flow below starts from this one. An unreadable file (an IOException, which includes DataStore's
     * CorruptionException) reads as "nothing stored", so the app starts on defaults instead of crashing the
     * theme; anything else is a bug and is rethrown.
     */
    private val prefs: Flow<Preferences> = context.settingsStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /**
     * Reads one key. DataStore emits the whole file on an edit to ANY key, so without the distinct filter every
     * flow would re-emit its unchanged value whenever another setting changes — which the accent picker's
     * echo guard would misread as an outside change.
     */
    private fun <T> read(transform: (Preferences) -> T): Flow<T> = prefs.map(transform).distinctUntilChanged()

    // ── Reads ───────────────────────────────────────────────────────────────

    /** An unknown stored name (a mode removed in a later version) falls back to SYSTEM rather than throwing. */
    val themeMode: Flow<ThemeMode> = read { p ->
        p[Keys.THEME_MODE]
            ?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } }
            ?: ThemeMode.SYSTEM
    }

    val amoledBlack: Flow<Boolean> = read { it[Keys.AMOLED_BLACK] ?: false }

    val dynamicColor: Flow<Boolean> = read { it[Keys.DYNAMIC_COLOR] ?: true }

    /** The accent seed (ARGB) used while [dynamicColor] is off. */
    val accentColor: Flow<Int> = read { it[Keys.ACCENT_COLOR] ?: ACCENT_DEFAULT_ARGB }

    val hapticsEnabled: Flow<Boolean> = read { it[Keys.HAPTICS_ENABLED] ?: true }

    /** False until the user finishes or skips Intro; decides the start destination. */
    val introDone: Flow<Boolean> = read { it[Keys.INTRO_DONE] ?: false }

    // ── Writes ──────────────────────────────────────────────────────────────

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.AMOLED_BLACK] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    /** ARGB. */
    suspend fun setAccentColor(argb: Int) {
        context.settingsStore.edit { it[Keys.ACCENT_COLOR] = argb }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setIntroDone(done: Boolean) {
        context.settingsStore.edit { it[Keys.INTRO_DONE] = done }
    }

    // ── Keys ────────────────────────────────────────────────────────────────

    /** The on-disk names. Never rename one: a renamed key silently resets that setting for every user. */
    private object Keys {
        val THEME_MODE      = stringPreferencesKey("theme_mode")
        val AMOLED_BLACK    = booleanPreferencesKey("amoled_black")
        val DYNAMIC_COLOR   = booleanPreferencesKey("dynamic_color")
        val ACCENT_COLOR    = intPreferencesKey("accent_color")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val INTRO_DONE      = booleanPreferencesKey("intro_done")
    }
}
