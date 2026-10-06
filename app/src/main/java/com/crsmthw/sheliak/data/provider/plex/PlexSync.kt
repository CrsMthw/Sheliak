package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.IndexKind
import com.crsmthw.sheliak.data.provider.IndexPlaylistEntry
import com.crsmthw.sheliak.data.provider.IndexSink
import com.crsmthw.sheliak.data.provider.IndexTrack
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.provider.SyncProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException

/** What the sync reads from one server — [PlexServer] in the app, a fake in PlexSyncTest. */
interface PlexLibrarySource {
    /** `/library/sections/{key}/all?type=&sort=addedAt[&updatedAt>>=]` with both container headers. */
    suspend fun sectionPage(sectionKey: String, type: Int, start: Int, size: Int, filters: Map<String, String>): PlexPage

    /** `/library/metadata/{id,id,…}`: full metadata with `Stream[]`. */
    suspend fun metadata(ratingKeys: List<String>): List<PlexMetadata>

    /** `/playlists?playlistType=audio`. */
    suspend fun playlists(): List<PlexMetadata>

    /** `/playlists/{id}/items`, paged. */
    suspend fun playlistItems(playlistKey: String, start: Int, size: Int): PlexPage
}

/**
 * Tunables of [PlexSync]. [pageSize] ≤ 1000 (PLEX.md: "500 is a safe default"); [metadataBatchSize] ids per
 * `/library/metadata/{ids}` call (URL length — PLEX.md has no limit for it); [fullSyncEveryMs] how often a full
 * listing runs so deletions and other clients' play counts propagate; [watermarkOverlapSeconds] how far the
 * next `updatedAt>>=` reaches back, so an item updated while a run was paging is never skipped.
 */
data class PlexSyncConfig(
    val pageSize: Int = 500,
    val metadataBatchSize: Int = 100,
    val metadataConcurrency: Int = 4,
    val fullSyncEveryMs: Long = 24L * 60 * 60 * 1000,
    val watermarkOverlapSeconds: Long = 60L * 60,
)

/**
 * One sync of the chosen music libraries into the index (DESIGN §2, PLEX.md §3, docs/INDEX.md "How a provider
 * feeds the sink").
 *
 * **Full run** (no cursor, another library selection, the periodic full due, or an incremental run that could not
 * account for an album): every section's tracks → albums → artists, paged with a stable sort, then all playlists.
 * Each kind is `markComplete`d only when every section's listing of it delivered its reported total, so the
 * sink's delete pass can never remove what a short page left out.
 *
 * **Incremental run**: tracks and artists with `updatedAt>>=<watermark>`; albums are listed in full (a few pages)
 * to count each artist's albums and to see which changed, and only the changed ones are written. An album row
 * needs its duration, which only its tracks give: when a changed album's tracks did not all change too, the run
 * becomes a full run (rare; the full run costs one listing — stream details are re-used from the index).
 *
 * **Stream details** (sample rate, bit depth, loudness): `/all` listings normally lack `Stream[]`, so for every
 * listed track the index's stored details are re-used when the track is unchanged (same `updatedAt` and part
 * key), and fetched in batches of `/library/metadata/{ids}` otherwise. A batch the server fails (not a network
 * failure) falls back to the stored details rather than writing nulls over them.
 *
 * Playlists are listed in full on every run.
 *
 * @param stored the index's current row for a ratingKey (IndexReader), or null.
 */
class PlexSync(
    private val source: PlexLibrarySource,
    private val stored: suspend (ratingKey: String) -> IndexTrack?,
    private val config: PlexSyncConfig = PlexSyncConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Runs a full or incremental sync of [sections] and returns the cursor for the next run. */
    suspend fun run(
        cursor: PlexSyncCursor?,
        sections: List<String>,
        sink: IndexSink,
        progress: (SyncProgress) -> Unit,
    ): PlexSyncCursor {
        val now = clock()
        val incremental = cursor != null &&
            cursor.updatedSince > 0 &&
            cursor.sections == sections &&
            now - cursor.lastFullAt < config.fullSyncEveryMs
        if (incremental) {
            incrementalRun(cursor, sections, sink, progress)?.let { return it }
        }
        return fullRun(cursor, sections, sink, progress, now)
    }

    // ── Full ────────────────────────────────────────────────────────────────

    private suspend fun fullRun(
        previous: PlexSyncCursor?,
        sections: List<String>,
        sink: IndexSink,
        progress: (SyncProgress) -> Unit,
        startedAt: Long,
    ): PlexSyncCursor {
        val counter = Progress(progress)
        val newest = Newest()
        val albumDurations = HashMap<String, Long>()
        var complete = true

        for (section in sections) {
            val result = PlexPaging.pageAll(
                config.pageSize,
                fetch = { start, size -> source.sectionPage(section, PlexTypes.TRACK, start, size, emptyMap()) },
            ) { items, total ->
                counter.sectionTotal(section, total)
                val tracks = buildTracks(items)
                sink.putTracks(tracks)
                tracks.forEach { t -> t.albumItemId?.let { albumDurations.merge(it, t.durationMs, Long::plus) } }
                items.forEach { newest.see(it.updatedAt) }
                counter.add(items.size)
            }
            complete = complete && result.complete
        }

        val albumsByArtist = HashMap<String, Int>()
        for (section in sections) {
            val result = PlexPaging.pageAll(
                config.pageSize,
                fetch = { start, size -> source.sectionPage(section, PlexTypes.ALBUM, start, size, emptyMap()) },
            ) { items, _ ->
                sink.putAlbums(items.mapNotNull { m -> PlexMapping.album(m, m.ratingKey?.let(albumDurations::get) ?: 0L) })
                items.forEach { m ->
                    m.parentRatingKey?.let { albumsByArtist.merge(it, 1, Int::plus) }
                    newest.see(m.updatedAt)
                }
            }
            complete = complete && result.complete
        }

        for (section in sections) {
            val result = PlexPaging.pageAll(
                config.pageSize,
                fetch = { start, size -> source.sectionPage(section, PlexTypes.ARTIST, start, size, emptyMap()) },
            ) { items, _ ->
                sink.putArtists(items.mapNotNull { m -> PlexMapping.artist(m, m.ratingKey?.let(albumsByArtist::get) ?: 0) })
                items.forEach { newest.see(it.updatedAt) }
            }
            complete = complete && result.complete
        }

        if (complete) {
            sink.markComplete(IndexKind.TRACKS)
            sink.markComplete(IndexKind.ALBUMS)
            sink.markComplete(IndexKind.ARTISTS)
        }
        syncPlaylists(sink)

        // An incomplete full run does not count: watermark 0 makes the next run a full one again.
        return PlexSyncCursor(
            updatedSince = if (complete) newest.watermark(0L, config.watermarkOverlapSeconds) else 0L,
            lastFullAt   = if (complete) startedAt else previous?.lastFullAt ?: 0L,
            sections     = sections,
        )
    }

    // ── Incremental ─────────────────────────────────────────────────────────

    /** The next cursor, or null when the run must become a full one. */
    private suspend fun incrementalRun(
        cursor: PlexSyncCursor,
        sections: List<String>,
        sink: IndexSink,
        progress: (SyncProgress) -> Unit,
    ): PlexSyncCursor? {
        val since = cursor.updatedSince
        val filter = PlexFilters.updatedSince(since)
        val counter = Progress(progress)
        val newest = Newest()
        val seenPerAlbum = HashMap<String, AlbumTally>()

        for (section in sections) {
            PlexPaging.pageAll(
                config.pageSize,
                fetch = { start, size -> source.sectionPage(section, PlexTypes.TRACK, start, size, filter) },
            ) { items, total ->
                counter.sectionTotal(section, total)
                val tracks = buildTracks(items)
                sink.putTracks(tracks)
                tracks.forEach { t ->
                    t.albumItemId?.let { seenPerAlbum.getOrPut(it) { AlbumTally() }.add(t.durationMs) }
                }
                items.forEach { newest.see(it.updatedAt) }
                counter.add(items.size)
            }
        }

        val albumsByArtist = HashMap<String, Int>()
        val changedAlbums = ArrayList<PlexMetadata>()
        for (section in sections) {
            PlexPaging.pageAll(
                config.pageSize,
                fetch = { start, size -> source.sectionPage(section, PlexTypes.ALBUM, start, size, emptyMap()) },
            ) { items, _ ->
                items.forEach { m ->
                    m.parentRatingKey?.let { albumsByArtist.merge(it, 1, Int::plus) }
                    if ((m.updatedAt ?: 0) >= since) changedAlbums += m
                }
            }
        }
        val unaccounted = changedAlbums.any { m ->
            (m.ratingKey?.let(seenPerAlbum::get)?.count ?: 0) < (m.leafCount ?: 0)
        }
        if (unaccounted) return null

        sink.putAlbums(
            changedAlbums.mapNotNull { m -> PlexMapping.album(m, m.ratingKey?.let(seenPerAlbum::get)?.durationMs ?: 0L) },
        )
        changedAlbums.forEach { newest.see(it.updatedAt) }

        for (section in sections) {
            PlexPaging.pageAll(
                config.pageSize,
                fetch = { start, size -> source.sectionPage(section, PlexTypes.ARTIST, start, size, filter) },
            ) { items, _ ->
                sink.putArtists(items.mapNotNull { m -> PlexMapping.artist(m, m.ratingKey?.let(albumsByArtist::get) ?: 0) })
                items.forEach { newest.see(it.updatedAt) }
            }
        }

        syncPlaylists(sink)
        return cursor.copy(updatedSince = newest.watermark(since, config.watermarkOverlapSeconds))
    }

    // ── Playlists ───────────────────────────────────────────────────────────

    /** Every audio playlist with all its items; complete only if every playlist's items all arrived. */
    private suspend fun syncPlaylists(sink: IndexSink) {
        var complete = true
        for (playlist in source.playlists()) {
            val key = playlist.ratingKey?.takeIf { it.isNotBlank() } ?: continue
            val entries = ArrayList<IndexPlaylistEntry>()
            val result = PlexPaging.pageAll(
                config.pageSize,
                fetch = { start, size -> source.playlistItems(key, start, size) },
            ) { items, _ -> items.mapNotNullTo(entries) { PlexMapping.playlistEntry(it) } }
            if (result.complete) {
                PlexMapping.playlist(playlist, entries)?.let { sink.putPlaylist(it) }
            } else {
                complete = false   // keep the stored entries rather than writing a partial list
            }
        }
        if (complete) sink.markComplete(IndexKind.PLAYLISTS)
    }

    // ── Tracks with stream details ──────────────────────────────────────────

    /** [items] as index tracks, with sample rate / bit depth / loudness from the index or a metadata batch. */
    internal suspend fun buildTracks(items: List<PlexMetadata>): List<IndexTrack> {
        val listed = items.mapNotNull { m -> PlexMapping.track(m)?.let { m to it } }
        val storedRows = HashMap<String, IndexTrack>()
        val reuse = HashSet<String>()
        val fetch = ArrayList<String>()
        for ((m, t) in listed) {
            if (PlexMapping.hasStreamDetails(m)) continue
            val old = stored(t.itemId)
            if (old != null) storedRows[t.itemId] = old
            if (old != null && canReuse(old, t)) reuse += t.itemId else fetch += t.itemId
        }
        val details = fetchDetails(fetch)
        return listed.map { (_, t) ->
            val detailed = details[t.itemId]?.let(PlexMapping::track)
            val old = storedRows[t.itemId]
            when {
                detailed != null -> detailed
                old != null      -> withStoredDetails(t, old)   // unchanged, or its batch failed
                else             -> t
            }
        }
    }

    /** The stored row describes the same file: same update time, same part, and its details are known. */
    private fun canReuse(old: IndexTrack, listed: IndexTrack): Boolean =
        old.updatedAt == listed.updatedAt &&
            old.streamRef == listed.streamRef &&
            old.format?.sampleRateHz != null

    private fun withStoredDetails(listed: IndexTrack, old: IndexTrack): IndexTrack {
        val oldFormat = old.format
        val format = listed.format?.let { f ->
            if (oldFormat == null || oldFormat.codec != f.codec) f
            else f.copy(sampleRateHz = f.sampleRateHz ?: oldFormat.sampleRateHz, bitDepth = f.bitDepth ?: oldFormat.bitDepth)
        } ?: oldFormat
        return listed.copy(
            format            = format,
            replayGainTrackDb = listed.replayGainTrackDb ?: old.replayGainTrackDb,
            replayGainAlbumDb = listed.replayGainAlbumDb ?: old.replayGainAlbumDb,
        )
    }

    /**
     * Full metadata for [ids], [PlexSyncConfig.metadataBatchSize] per call, [PlexSyncConfig.metadataConcurrency]
     * calls at a time. A batch the SERVER fails is skipped (its tracks fall back to stored details); a network or
     * auth failure fails the run.
     */
    private suspend fun fetchDetails(ids: List<String>): Map<String, PlexMetadata> {
        if (ids.isEmpty()) return emptyMap()
        val permits = Semaphore(config.metadataConcurrency.coerceAtLeast(1))
        val batches = coroutineScope {
            ids.chunked(config.metadataBatchSize.coerceAtLeast(1)).map { batch ->
                async {
                    permits.withPermit {
                        try {
                            source.metadata(batch)
                        } catch (e: ProviderException) {
                            if (e.error == ProviderError.NETWORK || e.error == ProviderError.AUTH) throw e
                            emptyList()
                        } catch (_: SerializationException) {
                            emptyList()
                        }
                    }
                }
            }.awaitAll()
        }
        val byKey = HashMap<String, PlexMetadata>(ids.size * 2)
        batches.forEach { list -> list.forEach { m -> m.ratingKey?.let { byKey[it] = m } } }
        return byKey
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private class AlbumTally {
        var count = 0
            private set
        var durationMs = 0L
            private set

        fun add(trackDurationMs: Long) {
            count++
            durationMs += trackDurationMs
        }
    }

    /** The newest `updatedAt` (server seconds) seen in a run. */
    private class Newest {
        private var max = 0L

        fun see(updatedAt: Long?) {
            if (updatedAt != null && updatedAt > max) max = updatedAt
        }

        /** The next watermark: the newest seen minus [overlap], never before [previous]. */
        fun watermark(previous: Long, overlap: Long): Long =
            if (max == 0L) previous else maxOf(previous, max - overlap)
    }

    /** Tracks written so far over the sum of the sections' reported totals. */
    private class Progress(private val report: (SyncProgress) -> Unit) {
        private val totals = HashMap<String, Int>()
        private var done = 0

        fun sectionTotal(section: String, total: Int?) {
            if (total != null) totals[section] = total
        }

        fun add(n: Int) {
            done += n
            report(SyncProgress(done, totals.values.sum().takeIf { totals.isNotEmpty() }))
        }
    }
}
