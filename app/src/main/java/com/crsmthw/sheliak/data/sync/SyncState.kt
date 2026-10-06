package com.crsmthw.sheliak.data.sync

import androidx.compose.runtime.Immutable
import com.crsmthw.sheliak.data.provider.ProviderError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One source's sync, as the UI shows it. The last successful sync's time is `providers.last_sync_at`. */
@Immutable
sealed interface SyncState {

    data object Idle : SyncState

    /** [done] items written so far; [total] when the provider knows it. */
    data class Running(val done: Int, val total: Int?) : SyncState

    /** The last run failed; [error] is a key the UI maps to its own string. Cleared by the next run's start. */
    data class Failed(val error: ProviderError) : SyncState
}

/** What happens to a sync. */
sealed interface SyncEvent {
    data object Started : SyncEvent
    data class Progress(val done: Int, val total: Int?) : SyncEvent
    data object Succeeded : SyncEvent
    data class Failed(val error: ProviderError) : SyncEvent
    /** Stopped before finishing (WorkManager cancelled it, the source was removed): no error to show. */
    data object Cancelled : SyncEvent
}

/**
 * The transition rule, pure (unit-tested in SyncStateTest). Null means the event is REJECTED: a second start
 * while a run is in flight — which is what makes [SyncStateStore.tryStart] the per-provider lock. Events that
 * belong to a run (progress, success, failure, cancellation) only apply while [Running]; arriving any other time
 * (a late progress callback after a failure) they leave the state unchanged.
 */
fun SyncState.on(event: SyncEvent): SyncState? = when (event) {
    SyncEvent.Started     -> if (this is SyncState.Running) null else SyncState.Running(0, null)
    is SyncEvent.Progress -> if (this is SyncState.Running) SyncState.Running(event.done, event.total) else this
    SyncEvent.Succeeded   -> if (this is SyncState.Running) SyncState.Idle else this
    is SyncEvent.Failed   -> if (this is SyncState.Running) SyncState.Failed(event.error) else this
    SyncEvent.Cancelled   -> if (this is SyncState.Running) SyncState.Idle else this
}

/**
 * Every source's [SyncState], in memory (a sync is in-process work; after a process death every source reads
 * Idle, which is true). Also the per-provider lock: [tryStart] admits one run per source at a time, so the
 * periodic work and a "sync now" never write the same provider concurrently.
 */
class SyncStateStore {

    private val _states = MutableStateFlow<Map<String, SyncState>>(emptyMap())

    /** Sources not in the map are [SyncState.Idle]. */
    val states: StateFlow<Map<String, SyncState>> = _states.asStateFlow()

    fun state(providerId: String): Flow<SyncState> =
        states.map { it[providerId] ?: SyncState.Idle }.distinctUntilChanged()

    /** True when a run for [providerId] may start (and it is now Running); false when one is already running. */
    fun tryStart(providerId: String): Boolean = apply(providerId, SyncEvent.Started)

    fun progress(providerId: String, done: Int, total: Int?) {
        apply(providerId, SyncEvent.Progress(done, total))
    }

    fun succeeded(providerId: String) {
        apply(providerId, SyncEvent.Succeeded)
    }

    fun failed(providerId: String, error: ProviderError) {
        apply(providerId, SyncEvent.Failed(error))
    }

    fun cancelled(providerId: String) {
        apply(providerId, SyncEvent.Cancelled)
    }

    /** Forgets [providerId] (it was removed). */
    fun clear(providerId: String) {
        while (true) {
            val current = _states.value
            if (providerId !in current || _states.compareAndSet(current, current - providerId)) return
        }
    }

    /**
     * Applies [event] atomically; false when it was rejected. An event that changes nothing writes nothing — so
     * the tail of a run whose source was removed (and [clear]ed) mid-flight cannot re-add it.
     */
    private fun apply(providerId: String, event: SyncEvent): Boolean {
        while (true) {
            val current = _states.value
            val state = current[providerId] ?: SyncState.Idle
            val next = state.on(event) ?: return false
            if (next == state) return true
            if (_states.compareAndSet(current, current + (providerId to next))) return true
        }
    }
}
