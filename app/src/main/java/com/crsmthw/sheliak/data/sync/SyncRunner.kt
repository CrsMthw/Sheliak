package com.crsmthw.sheliak.data.sync

import com.crsmthw.sheliak.data.db.dao.IndexDao
import com.crsmthw.sheliak.data.db.dao.ProviderDao
import com.crsmthw.sheliak.data.provider.MusicProvider
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.RoomIndexSink
import com.crsmthw.sheliak.data.provider.SyncCursor
import kotlin.coroutines.cancellation.CancellationException

/** How one sync run ended, for the worker to turn into a WorkManager result. */
sealed interface SyncOutcome {
    /** Synced; the new cursor is stored. */
    data object Synced : SyncOutcome
    /** Another run of this source was already in flight; nothing done. */
    data object AlreadyRunning : SyncOutcome
    /** The source no longer exists (removed while the work was queued). */
    data object Gone : SyncOutcome
    data class Failed(val error: ProviderError) : SyncOutcome
}

/**
 * One sync of one source: lock it in [SyncStateStore], build an index sink for this run, call the provider,
 * then — only on success — commit the sink (flush + delete pass) and persist the returned cursor. Cancellation
 * resets the state to Idle and is rethrown; nothing is committed.
 *
 * @param providers the live provider for an id (`ProviderRegistry::provider`).
 */
class SyncRunner(
    private val providerDao: ProviderDao,
    private val indexDao: IndexDao,
    private val providers: suspend (String) -> MusicProvider?,
    private val states: SyncStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun run(providerId: String): SyncOutcome {
        if (!states.tryStart(providerId)) return SyncOutcome.AlreadyRunning
        try {
            val row = providerDao.get(providerId)
            if (row == null) {
                states.clear(providerId)
                return SyncOutcome.Gone
            }
            val provider = providers(providerId)
                ?: return fail(providerId, ProviderError.UNAVAILABLE)

            val sink = RoomIndexSink(indexDao, providerId, syncRun = clock())
            val result = provider.sync(row.syncCursor?.let(::SyncCursor), sink) { p ->
                states.progress(providerId, p.done, p.total)
            }
            val cursor = result.getOrElse { e ->
                if (e is CancellationException) throw e
                return fail(providerId, ProviderError.of(e))
            }
            sink.commit()
            providerDao.saveSyncResult(providerId, cursor.value, clock())
            states.succeeded(providerId)
            return SyncOutcome.Synced
        } catch (e: CancellationException) {
            states.cancelled(providerId)
            throw e
        } catch (e: Exception) {
            return fail(providerId, ProviderError.of(e))
        }
    }

    private fun fail(providerId: String, error: ProviderError): SyncOutcome {
        states.failed(providerId, error)
        return SyncOutcome.Failed(error)
    }
}
