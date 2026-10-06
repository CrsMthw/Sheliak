package com.crsmthw.sheliak.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Schedules [SyncWorker] runs: a periodic sync per source every [PERIODIC_HOURS] hours while a network is
 * connected, and [syncNow] — expedited, for pull-to-refresh and Settings → Sources → Sync now. Both are UNIQUE
 * work per source ([periodicWorkName] / [oneTimeWorkName]); a run already in flight is kept, never restarted, and
 * [SyncStateStore.tryStart] keeps the two kinds from overlapping.
 */
class SyncScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Idempotent (KEEP): call it on every start for every enabled source. */
    fun schedulePeriodic(providerId: String) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .setInputData(workDataOf(SyncWorker.KEY_PROVIDER_ID to providerId))
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(periodicWorkName(providerId), ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** A sync of [providerId] now (or as soon as a network is connected); kept if one is already queued or running. */
    fun syncNow(providerId: String) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(SyncWorker.KEY_PROVIDER_ID to providerId))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(oneTimeWorkName(providerId), ExistingWorkPolicy.KEEP, request)
    }

    /** Stops and unschedules both kinds of work for [providerId] (it was removed or disabled). */
    fun cancel(providerId: String) {
        workManager.cancelUniqueWork(periodicWorkName(providerId))
        workManager.cancelUniqueWork(oneTimeWorkName(providerId))
    }

    companion object {
        const val PERIODIC_HOURS: Long = 6
        const val TAG: String = "sheliak-sync"

        fun periodicWorkName(providerId: String): String = "sync-periodic:$providerId"
        fun oneTimeWorkName(providerId: String): String = "sync-now:$providerId"
    }
}
