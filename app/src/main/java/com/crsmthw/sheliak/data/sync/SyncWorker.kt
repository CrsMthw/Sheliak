package com.crsmthw.sheliak.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crsmthw.sheliak.di.AppContainer

/**
 * WorkManager's entry to [SyncRunner]: one source per run, its id in the input data ([KEY_PROVIDER_ID]).
 * Built by WorkManager's default factory (public `(Context, WorkerParameters)` constructor, kept by
 * work-runtime's own consumer R8 rules), so it reaches the app graph through [AppContainer.await].
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val providerId = inputData.getString(KEY_PROVIDER_ID) ?: return Result.failure()
        val container = AppContainer.await()
        return when (val outcome = container.syncRunner.run(providerId)) {
            SyncOutcome.Synced, SyncOutcome.AlreadyRunning, SyncOutcome.Gone -> Result.success()
            is SyncOutcome.Failed ->
                if (outcome.error.retryable && runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_PROVIDER_ID: String = "provider_id"

        /** Retries (with WorkManager's exponential backoff) of a retryable failure before giving up until next time. */
        const val MAX_RETRIES: Int = 3
    }
}
