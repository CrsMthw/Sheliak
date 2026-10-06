package com.crsmthw.sheliak.data.provider

import com.crsmthw.sheliak.data.db.dao.IndexDao
import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntryEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import com.crsmthw.sheliak.data.db.toEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.EnumSet

/**
 * The [IndexSink] one sync run writes through: rows are stamped with [syncRun], buffered, and written [batchSize]
 * at a time, each batch one transaction (skipped if the source was removed meanwhile — see IndexDao). [commit] (called by the sync runner after the provider's `sync`
 * SUCCEEDED) flushes what is left and deletes, for each kind the provider declared complete, the rows of this
 * provider that this run did not stamp. A failed or cancelled run never commits, so it deletes nothing.
 *
 * Safe to call from several coroutines at once (a provider paging in parallel): one mutex orders the writes.
 */
class RoomIndexSink(
    private val indexDao: IndexDao,
    private val providerId: String,
    val syncRun: Long,
    private val batchSize: Int = BATCH_SIZE,
) : IndexSink {

    private val mutex = Mutex()
    private val artists = ArrayList<ArtistEntity>()
    private val albums = ArrayList<AlbumEntity>()
    private val tracks = ArrayList<TrackEntity>()
    private val complete: MutableSet<IndexKind> = Collections.synchronizedSet(EnumSet.noneOf(IndexKind::class.java))

    /** Rows written so far (all kinds, including playlist rows). */
    var written: Int = 0
        private set

    override suspend fun putArtists(artists: List<IndexArtist>) = mutex.withLock {
        artists.mapTo(this.artists) { it.toEntity(providerId, syncRun) }
        drain(this.artists, all = false) { indexDao.upsertArtists(providerId, it) }
    }

    override suspend fun putAlbums(albums: List<IndexAlbum>) = mutex.withLock {
        albums.mapTo(this.albums) { it.toEntity(providerId, syncRun) }
        drain(this.albums, all = false) { indexDao.upsertAlbums(providerId, it) }
    }

    override suspend fun putTracks(tracks: List<IndexTrack>) = mutex.withLock {
        tracks.mapTo(this.tracks) { it.toEntity(providerId, syncRun) }
        drain(this.tracks, all = false) { indexDao.upsertTracks(providerId, it) }
    }

    override suspend fun putPlaylist(playlist: IndexPlaylist) = mutex.withLock {
        val row = playlist.toEntity(providerId, syncRun, id = 0, sortIndex = 0)
        val entries = playlist.entries.mapIndexed { position, e ->
            PlaylistEntryEntity(
                playlistId = 0,
                position   = position,
                trackPid   = providerId,
                trackIid   = e.trackItemId,
                entryId    = e.entryId,
                addedAt    = e.addedAt,
            )
        }
        if (indexDao.putServerPlaylist(row, entries) != null) written += 1
    }

    override suspend fun remove(kind: IndexKind, itemIds: List<String>) = mutex.withLock {
        if (itemIds.isEmpty()) return@withLock
        flushAll()   // a removal must win over a buffered write of the same item
        itemIds.distinct().chunked(batchSize).forEach { chunk ->
            when (kind) {
                IndexKind.ARTISTS   -> indexDao.deleteArtists(providerId, chunk)
                IndexKind.ALBUMS    -> indexDao.deleteAlbums(providerId, chunk)
                IndexKind.TRACKS    -> indexDao.deleteTracks(providerId, chunk)
                IndexKind.PLAYLISTS -> indexDao.deletePlaylists(providerId, chunk)
            }
        }
    }

    override fun markComplete(kind: IndexKind) {
        complete += kind
    }

    /** The kinds declared complete so far. */
    fun completeKinds(): Set<IndexKind> = synchronized(complete) { complete.toSet() }

    /** Flushes the buffers and runs the delete pass for the complete kinds. Call once, after a successful sync. */
    suspend fun commit() = mutex.withLock {
        flushAll()
        val kinds = completeKinds()
        if (kinds.isNotEmpty()) {
            indexDao.deleteNotSeen(
                providerId = providerId,
                syncRun    = syncRun,
                artists    = IndexKind.ARTISTS in kinds,
                albums     = IndexKind.ALBUMS in kinds,
                tracks     = IndexKind.TRACKS in kinds,
                playlists  = IndexKind.PLAYLISTS in kinds,
            )
        }
    }

    private suspend fun flushAll() {
        drain(artists, all = true) { indexDao.upsertArtists(providerId, it) }
        drain(albums, all = true) { indexDao.upsertAlbums(providerId, it) }
        drain(tracks, all = true) { indexDao.upsertTracks(providerId, it) }
    }

    /**
     * Writes full batches from the front of [buffer] — and the partial rest too when [all]. [write] returns false
     * when the provider was removed meanwhile; the batch is dropped and not counted.
     */
    private suspend fun <T> drain(buffer: MutableList<T>, all: Boolean, write: suspend (List<T>) -> Boolean) {
        while (buffer.size >= batchSize || (all && buffer.isNotEmpty())) {
            val n = minOf(batchSize, buffer.size)
            val batch = ArrayList(buffer.subList(0, n))
            val stored = write(batch)
            buffer.subList(0, n).clear()
            if (stored) written += n
        }
    }

    companion object {
        /** Rows per transaction (and under SQLite's 999 bind-variable floor for the `IN` queries). */
        const val BATCH_SIZE: Int = 500
    }
}
