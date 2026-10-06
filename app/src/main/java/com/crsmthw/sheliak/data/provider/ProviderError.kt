package com.crsmthw.sheliak.data.provider

import java.io.IOException

/**
 * Why a provider call failed, as a key the UI maps to its own string resource (never text from here).
 * [retryable] failures are worth retrying later on their own (WorkManager's backoff); the others need the user.
 */
enum class ProviderError(val retryable: Boolean) {
    /** No route to the server, a timeout, a dropped connection. */
    NETWORK(retryable = true),
    /** The token was refused or revoked: the user must sign in again. */
    AUTH(retryable = false),
    /** The server answered with an error (5xx) or is shutting down. */
    SERVER(retryable = true),
    /** The server answered something we cannot read. */
    PARSE(retryable = false),
    /** Writing the index failed (disk full, a database error). */
    STORAGE(retryable = true),
    /** No factory for this provider's type, or its row is gone. */
    UNAVAILABLE(retryable = false),
    UNKNOWN(retryable = false);

    companion object {
        /**
         * The error [t] stands for: the first [ProviderException] or [IOException] along its cause chain, a
         * database error, a serialization error; anything else is [UNKNOWN].
         */
        fun of(t: Throwable): ProviderError {
            var cur: Throwable? = t
            var depth = 0
            while (cur != null && depth < MAX_CAUSE_DEPTH) {
                when (cur) {
                    is ProviderException             -> return cur.error
                    is IOException                   -> return NETWORK
                    is android.database.SQLException -> return STORAGE
                    is kotlinx.serialization.SerializationException -> return PARSE
                }
                cur = cur.cause
                depth++
            }
            return UNKNOWN
        }

        private const val MAX_CAUSE_DEPTH = 8
    }
}

/** A provider failure whose kind is known; see [ProviderError.of]. */
class ProviderException(val error: ProviderError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.name, cause)
