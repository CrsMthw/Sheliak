package com.crsmthw.sheliak.ui.screens.sources

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.sync.SyncState

/*
 * How a source's sync reads in Settings → Sources, and how a provider failure reads anywhere — pure mappings from
 * the data layer's keys to the UI's own string keys, unit-tested in SourceStatusTest. The words themselves are
 * string resources.
 */

/** Which line a source's status shows. */
enum class SourceStatusKey {
    /** Idle and never synced successfully: "Not synced yet". */
    NEVER_SYNCED,
    /** Idle after a successful sync: "Synced <when>". */
    SYNCED,
    /** Running with a known total: "Syncing… N of M". */
    RUNNING_OF_TOTAL,
    /** Running with no total (yet): "Syncing… N". */
    RUNNING_COUNT,
    /** The last run failed: "Sync failed: <the error>". */
    FAILED,
}

/** A source's status line: its [key] and the values that key's words need. */
@Immutable
data class SourceStatus(
    val key     : SourceStatusKey,
    val done    : Int = 0,
    val total   : Int = 0,
    val syncedAt: Long? = null,
    val error   : ProviderError? = null,
)

/** The status line for a source whose sync is [state] and whose last successful sync was at [lastSyncAt]. */
fun sourceStatusOf(state: SyncState, lastSyncAt: Long?): SourceStatus = when (state) {
    SyncState.Idle       ->
        if (lastSyncAt == null) SourceStatus(SourceStatusKey.NEVER_SYNCED)
        else SourceStatus(SourceStatusKey.SYNCED, syncedAt = lastSyncAt)
    is SyncState.Running -> {
        val done = state.done.coerceAtLeast(0)
        val total = state.total
        if (total != null && total > 0) SourceStatus(SourceStatusKey.RUNNING_OF_TOTAL, done = done, total = total)
        else SourceStatus(SourceStatusKey.RUNNING_COUNT, done = done)
    }
    is SyncState.Failed  -> SourceStatus(SourceStatusKey.FAILED, error = state.error)
}

/** True while a source's sync runs — its "Sync now" is then pointless and shows disabled. */
fun SourceStatus.isRunning(): Boolean =
    key == SourceStatusKey.RUNNING_OF_TOTAL || key == SourceStatusKey.RUNNING_COUNT

/** The words for a provider failure, wherever one is shown (a source's status, the Plex setup's error line). */
@StringRes
fun providerErrorMessage(error: ProviderError): Int = when (error) {
    ProviderError.NETWORK     -> R.string.provider_error_network
    ProviderError.AUTH        -> R.string.provider_error_auth
    ProviderError.SERVER      -> R.string.provider_error_server
    ProviderError.PARSE       -> R.string.provider_error_parse
    ProviderError.STORAGE     -> R.string.provider_error_storage
    ProviderError.UNAVAILABLE -> R.string.provider_error_unavailable
    ProviderError.UNKNOWN     -> R.string.provider_error_unknown
}
