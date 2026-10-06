package com.crsmthw.sheliak.data.provider.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

/** One page of a listing as the server returned it. */
data class PlexPage(val items: List<PlexMetadata>, val offset: Int?, val totalSize: Int?) {
    companion object {
        /**
         * The page in [response]: `MediaContainer.Metadata`, its `offset`, and the total from
         * `MediaContainer.totalSize` or else the `X-Plex-Container-Total-Size` header (PLEX.md §3 "Paging").
         */
        fun from(body: PlexResponse, totalHeader: String?): PlexPage {
            val container = body.mediaContainer
            return PlexPage(
                items     = container?.metadata.orEmpty(),
                offset    = container?.offset,
                totalSize = container?.totalSize ?: totalHeader?.trim()?.toIntOrNull(),
            )
        }

        fun from(response: Response<PlexResponse>, what: String): PlexPage =
            from(response.bodyOrThrow(what), response.headers()[PlexHeaders.CONTAINER_TOTAL_SIZE])
    }
}

/** How a paged listing ended: [complete] = at least the reported total arrived (or no total and a short page). */
data class PlexPagingResult(val received: Int, val total: Int?, val complete: Boolean)

/**
 * Pages through a listing (PLEX.md §3 "Paging", correction 11): BOTH container headers on every request, at most
 * [MAX_PAGE_SIZE] items per page, and the next start taken from what the server RETURNED (its `offset` plus the
 * items received) because the server "may not page as asked". Pure; unit-tested in PlexPagingTest.
 */
object PlexPaging {

    /** PMS logs that a larger X-Plex-Container-Size "will fail with status code 400". */
    const val MAX_PAGE_SIZE: Int = 1000

    suspend fun pageAll(
        pageSize: Int,
        fetch: suspend (start: Int, size: Int) -> PlexPage,
        onPage: suspend (items: List<PlexMetadata>, total: Int?) -> Unit,
    ): PlexPagingResult {
        val size = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        var start = 0
        var received = 0
        var total: Int? = null
        var shortPage = false
        while (true) {
            val page = fetch(start, size)
            total = page.totalSize ?: total
            if (page.items.isEmpty()) {
                shortPage = true
                break
            }
            onPage(page.items, total)
            received += page.items.size
            val next = (page.offset ?: start) + page.items.size
            if (next <= start) break                        // no progress: never loop forever
            start = next
            val knownTotal = total
            if (knownTotal != null && start >= knownTotal) break
            if (knownTotal == null && page.items.size < size) {
                shortPage = true
                break
            }
        }
        val knownTotal = total
        val complete = if (knownTotal != null) received >= knownTotal else shortPage
        return PlexPagingResult(received, total, complete)
    }
}

/** Media-query filters (PLEX.md §3 "Filters", correction 10). */
object PlexFilters {

    /**
     * Items updated at or after [epochSeconds]: `updatedAt>>=<epoch>`. Date fields take `>>=` ("after"); a
     * plain `>=` would mean "ends with". As a query map entry the NAME is `updatedAt>>` and the value the epoch,
     * so the URL reads `updatedAt%3E%3E=<epoch>` (what python-plexapi's urlencode sends).
     */
    fun updatedSince(epochSeconds: Long): Map<String, String> = mapOf("updatedAt>>" to epochSeconds.toString())

    /** The stable listing order: new items append at the end instead of shifting earlier pages. */
    const val SORT_ADDED: String = "addedAt"
}

/**
 * The Json for what the Plex code stores itself (the sync cursor, the instance config): defaults are always
 * written, so a stored `"v"` survives a later change of the default, and unknown keys are ignored.
 */
internal object PlexStorageJson {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

/**
 * The opaque [com.crsmthw.sheliak.data.provider.SyncCursor] value of a Plex instance.
 *
 * @property updatedSince the next incremental run's `updatedAt>>=` watermark, epoch SECONDS on the server's
 *   clock (the newest `updatedAt` seen, minus an overlap — never the phone's clock); 0 = run a full sync.
 * @property lastFullAt when the last COMPLETE full listing ran (phone clock, ms), so deletions propagate on the
 *   periodic full run.
 * @property sections the library keys the watermark belongs to; a different selection forces a full run.
 */
@Serializable
data class PlexSyncCursor(
    @SerialName("v") val version: Int = VERSION,
    @SerialName("updatedSince") val updatedSince: Long = 0,
    @SerialName("lastFullAt") val lastFullAt: Long = 0,
    @SerialName("sections") val sections: List<String> = emptyList(),
) {
    fun encode(): String = PlexStorageJson.json.encodeToString(serializer(), this)

    companion object {
        const val VERSION: Int = 1

        /** The cursor in [value], or null (→ a full sync) when absent, unreadable or of another version. */
        fun decode(value: String?): PlexSyncCursor? {
            if (value.isNullOrBlank()) return null
            return try {
                PlexStorageJson.json.decodeFromString(serializer(), value).takeIf { it.version == VERSION }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
