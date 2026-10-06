package com.crsmthw.sheliak.data.repository

import android.util.Log
import androidx.compose.runtime.Immutable
import com.crsmthw.sheliak.data.db.dao.IndexDao
import com.crsmthw.sheliak.data.db.dao.ProviderDao
import com.crsmthw.sheliak.data.db.entity.ProviderEntity
import com.crsmthw.sheliak.data.db.toInstance
import com.crsmthw.sheliak.data.provider.ProviderInstance
import com.crsmthw.sheliak.data.provider.ProviderRegistry
import com.crsmthw.sheliak.data.sync.SyncScheduler
import com.crsmthw.sheliak.data.sync.SyncState
import com.crsmthw.sheliak.data.sync.SyncStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.coroutines.cancellation.CancellationException

/** One configured source as Settings → Sources shows it. */
@Immutable
data class Source(
    val instance: ProviderInstance,
    val addedAt: Long,
    /** The last SUCCESSFUL sync; null before the first one. */
    val lastSyncAt: Long?,
    val trackCount: Int,
    val syncState: SyncState,
)

/**
 * The configured sources: the `providers` table, the live providers ([ProviderRegistry]), their sync schedule
 * and state. Adding a source schedules its periodic sync and starts a first (full) one; removing it stops its
 * work, lets the provider delete its secrets, and deletes everything it put in the index.
 */
class SourcesRepository(
    private val providerDao: ProviderDao,
    private val indexDao: IndexDao,
    private val registry: ProviderRegistry,
    private val scheduler: SyncScheduler,
    private val syncStates: SyncStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val sources: Flow<List<Source>> = combine(
        providerDao.observeAll(),
        providerDao.trackCounts(),
        syncStates.states,
    ) { rows, counts, states ->
        val byProvider = counts.associate { it.providerId to it.count }
        rows.map { row ->
            Source(
                instance   = row.toInstance(),
                addedAt    = row.addedAt,
                lastSyncAt = row.lastSyncAt,
                trackCount = byProvider[row.id] ?: 0,
                syncState  = states[row.id] ?: SyncState.Idle,
            )
        }
    }

    /** Every source's sync state (absent = Idle). */
    val syncStatesByProvider = syncStates.states

    fun syncState(providerId: String): Flow<SyncState> = syncStates.state(providerId)

    /**
     * Adds [instance] (or, if its id already exists, updates it as [update] does) and starts its first sync.
     * The provider's secrets must already be in the CredentialStore.
     */
    suspend fun add(instance: ProviderInstance): Result<Unit> = resultOf {
        val existing = providerDao.get(instance.id)
        if (existing == null) {
            providerDao.insert(
                ProviderEntity(
                    id          = instance.id,
                    type        = instance.type,
                    displayName = instance.displayName,
                    baseUrl     = instance.baseUrl,
                    serverId    = instance.serverId,
                    config      = instance.config,
                    sort        = providerDao.maxSort() + 1,
                    addedAt     = clock(),
                    lastSyncAt  = null,
                    syncCursor  = null,
                    enabled     = instance.enabled,
                ),
            )
        } else {
            writeInstance(instance)
        }
        if (instance.enabled) {
            scheduler.schedulePeriodic(instance.id)
            scheduler.syncNow(instance.id)
        }
    }

    /**
     * Changes what the provider owns (name, connection, config, enabled). With [resync] the next sync is a full
     * one, started now — for a config change that changes WHAT is synced (the libraries picked).
     */
    suspend fun update(instance: ProviderInstance, resync: Boolean = false): Result<Unit> = resultOf {
        writeInstance(instance)
        if (resync) providerDao.resetSyncCursor(instance.id)
        if (instance.enabled) {
            scheduler.schedulePeriodic(instance.id)
            if (resync) scheduler.syncNow(instance.id)
        } else {
            scheduler.cancel(instance.id)
        }
    }

    /** Pull-to-refresh / "Sync now": queues an expedited sync (kept if one is already queued or running). */
    fun syncNow(providerId: String) = scheduler.syncNow(providerId)

    /** [syncNow] for every enabled source (pull-to-refresh on a merged list). */
    suspend fun syncAll(): Result<Unit> = resultOf {
        providerDao.enabledIds().forEach(scheduler::syncNow)
    }

    /** Ensures every enabled source has its periodic sync (idempotent; run at startup). */
    suspend fun schedulePeriodicSyncs(): Result<Unit> = resultOf {
        providerDao.enabledIds().forEach(scheduler::schedulePeriodic)
    }

    /**
     * Removes the source: stops its sync work, asks the provider to delete its secrets ([MusicProvider.onRemove],
     * whose failure does not stop the removal), then deletes its rows, history and `providers` row in one
     * transaction.
     */
    suspend fun remove(providerId: String): Result<Unit> = resultOf {
        scheduler.cancel(providerId)
        try {
            registry.provider(providerId)?.onRemove()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "onRemove failed for $providerId", e)
        }
        indexDao.deleteProvider(providerId)
        syncStates.clear(providerId)
    }

    private suspend fun writeInstance(instance: ProviderInstance) {
        providerDao.updateInstance(
            id          = instance.id,
            type        = instance.type,
            displayName = instance.displayName,
            baseUrl     = instance.baseUrl,
            serverId    = instance.serverId,
            config      = instance.config,
            enabled     = instance.enabled,
        )
    }

    private companion object {
        const val TAG = "SourcesRepository"
    }
}
