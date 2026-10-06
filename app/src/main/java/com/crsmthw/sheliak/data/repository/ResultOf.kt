package com.crsmthw.sheliak.data.repository

import kotlin.coroutines.cancellation.CancellationException

/**
 * `runCatching` for suspend code: the block's value or its failure as a [Result] — except cancellation, which
 * is rethrown so a cancelled caller stops instead of reading "failed".
 */
suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
