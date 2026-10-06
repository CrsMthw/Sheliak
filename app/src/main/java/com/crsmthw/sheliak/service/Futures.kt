package com.crsmthw.sheliak.service

import com.crsmthw.sheliak.data.repository.resultOf
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Media3 speaks Guava `ListenableFuture`; the app speaks coroutines. These two bridges use only Guava, which
// media3-common already puts on the compile classpath (no kotlinx-coroutines-guava in the catalog).

/**
 * Runs [block] on this scope and completes the returned future with its value or failure. Cancelling the future
 * cancels the coroutine; cancelling the coroutine (the scope ending) cancels the future.
 */
internal fun <T> CoroutineScope.future(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T,
): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    val job = launch(context) {
        resultOf { block() }.fold(onSuccess = { future.set(it) }, onFailure = { future.setException(it) })
    }
    job.invokeOnCompletion { cause -> if (cause != null) future.cancel(false) }
    future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
    return future
}

/** Suspends until the future completes; cancelling the caller cancels the future. */
internal suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            if (isCancelled) {
                continuation.cancel()
            } else {
                try {
                    continuation.resume(Futures.getDone(this))
                } catch (e: ExecutionException) {
                    continuation.resumeWithException(e.cause ?: e)
                }
            }
        },
        MoreExecutors.directExecutor(),
    )
    continuation.invokeOnCancellation { cancel(false) }
}
