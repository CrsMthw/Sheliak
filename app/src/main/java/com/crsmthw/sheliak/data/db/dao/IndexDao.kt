package com.crsmthw.sheliak.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.crsmthw.sheliak.data.db.IndexMerge
import com.crsmthw.sheliak.data.db.PlayStats
import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntryEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity

/**
 * The sync's writes (through RoomIndexSink) and a source's removal. Every library write is an upsert — insert,
 * else update — never INSERT OR REPLACE: REPLACE deletes the old row without firing the FTS delete trigger and
 * would leave stale search hits behind.
 */
@Dao
abstract class IndexDao {

    // ── Upserts ─────────────────────────────────────────────────────────────
    //
    // Each batch first checks, inside its transaction, that the provider still exists: a source removed while
    // its sync is in flight (WorkManager's cancel is asynchronous) must not get rows back that nothing would ever
    // delete. A skipped batch is not an error — the run is about to be cancelled.

    @Query("SELECT EXISTS(SELECT 1 FROM providers WHERE id = :providerId)")
    protected abstract suspend fun providerExists(providerId: String): Boolean

    @Upsert
    protected abstract suspend fun upsertArtistRows(rows: List<ArtistEntity>)

    @Upsert
    protected abstract suspend fun upsertAlbumRows(rows: List<AlbumEntity>)

    @Upsert
    protected abstract suspend fun upsertTrackRows(rows: List<TrackEntity>)

    @Query("SELECT item_id, play_count, last_played_at FROM tracks WHERE provider_id = :providerId AND item_id IN (:itemIds)")
    protected abstract suspend fun playStats(providerId: String, itemIds: List<String>): List<PlayStats>

    /** One provider's batch; false (nothing written) when the provider no longer exists. */
    @Transaction
    open suspend fun upsertArtists(providerId: String, rows: List<ArtistEntity>): Boolean {
        if (!providerExists(providerId)) return false
        if (rows.isNotEmpty()) upsertArtistRows(rows)
        return true
    }

    /** One provider's batch; false (nothing written) when the provider no longer exists. */
    @Transaction
    open suspend fun upsertAlbums(providerId: String, rows: List<AlbumEntity>): Boolean {
        if (!providerExists(providerId)) return false
        if (rows.isNotEmpty()) upsertAlbumRows(rows)
        return true
    }

    /**
     * One provider's batch (≤ 999 rows); stored play numbers are kept when higher (IndexMerge). False (nothing
     * written) when the provider no longer exists.
     */
    @Transaction
    open suspend fun upsertTracks(providerId: String, rows: List<TrackEntity>): Boolean {
        if (!providerExists(providerId)) return false
        if (rows.isEmpty()) return true
        val stored = playStats(providerId, rows.map { it.itemId }).associateBy { it.itemId }
        upsertTrackRows(IndexMerge.mergePlayStats(rows, stored))
        return true
    }

    // ── Server playlists ────────────────────────────────────────────────────

    @Query("SELECT * FROM playlists WHERE provider_id = :providerId AND item_id = :itemId")
    protected abstract suspend fun serverPlaylist(providerId: String, itemId: String): PlaylistEntity?

    @Insert
    protected abstract suspend fun insertPlaylist(row: PlaylistEntity): Long

    @Update
    protected abstract suspend fun updatePlaylist(row: PlaylistEntity)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId")
    protected abstract suspend fun clearEntries(playlistId: Long)

    @Insert
    protected abstract suspend fun insertEntries(rows: List<PlaylistEntryEntity>)

    /**
     * Stores one server playlist and replaces its entries. The local id and sort index of a playlist already
     * stored are kept; [entries]' `playlistId` is ignored and set here. Returns the local id, or null (nothing
     * written) when the provider no longer exists.
     */
    @Transaction
    open suspend fun putServerPlaylist(row: PlaylistEntity, entries: List<PlaylistEntryEntity>): Long? {
        val providerId = requireNotNull(row.providerId) { "A server playlist has a provider" }
        val itemId = requireNotNull(row.itemId) { "A server playlist has an item id" }
        if (!providerExists(providerId)) return null
        val existing = serverPlaylist(providerId, itemId)
        val id = if (existing == null) {
            insertPlaylist(row.copy(id = 0))
        } else {
            updatePlaylist(row.copy(id = existing.id, sortIndex = existing.sortIndex))
            existing.id
        }
        clearEntries(id)
        if (entries.isNotEmpty()) insertEntries(entries.map { it.copy(playlistId = id) })
        return id
    }

    // ── Delete passes (rows a complete sync did not report) ─────────────────

    @Query("DELETE FROM artists WHERE provider_id = :providerId AND sync_run <> :syncRun")
    protected abstract suspend fun deleteArtistsNotIn(providerId: String, syncRun: Long): Int

    @Query("DELETE FROM albums WHERE provider_id = :providerId AND sync_run <> :syncRun")
    protected abstract suspend fun deleteAlbumsNotIn(providerId: String, syncRun: Long): Int

    @Query("DELETE FROM tracks WHERE provider_id = :providerId AND sync_run <> :syncRun")
    protected abstract suspend fun deleteTracksNotIn(providerId: String, syncRun: Long): Int

    @Query(
        "DELETE FROM playlist_entries WHERE playlist_id IN " +
            "(SELECT id FROM playlists WHERE provider_id = :providerId AND sync_run <> :syncRun)",
    )
    protected abstract suspend fun deletePlaylistEntriesNotIn(providerId: String, syncRun: Long)

    @Query("DELETE FROM playlists WHERE provider_id = :providerId AND sync_run <> :syncRun")
    protected abstract suspend fun deletePlaylistsNotIn(providerId: String, syncRun: Long): Int

    /** Deletes the rows of each named kind that sync run [syncRun] of [providerId] did not report. */
    @Transaction
    open suspend fun deleteNotSeen(
        providerId: String,
        syncRun: Long,
        artists: Boolean,
        albums: Boolean,
        tracks: Boolean,
        playlists: Boolean,
    ) {
        if (artists) deleteArtistsNotIn(providerId, syncRun)
        if (albums) deleteAlbumsNotIn(providerId, syncRun)
        if (tracks) deleteTracksNotIn(providerId, syncRun)
        if (playlists) {
            deletePlaylistEntriesNotIn(providerId, syncRun)
            deletePlaylistsNotIn(providerId, syncRun)
        }
    }

    // ── Explicit removals (≤ 999 ids per call) ──────────────────────────────

    @Query("DELETE FROM artists WHERE provider_id = :providerId AND item_id IN (:itemIds)")
    abstract suspend fun deleteArtists(providerId: String, itemIds: List<String>)

    @Query("DELETE FROM albums WHERE provider_id = :providerId AND item_id IN (:itemIds)")
    abstract suspend fun deleteAlbums(providerId: String, itemIds: List<String>)

    @Query("DELETE FROM tracks WHERE provider_id = :providerId AND item_id IN (:itemIds)")
    abstract suspend fun deleteTracks(providerId: String, itemIds: List<String>)

    @Query(
        "DELETE FROM playlist_entries WHERE playlist_id IN " +
            "(SELECT id FROM playlists WHERE provider_id = :providerId AND item_id IN (:itemIds))",
    )
    protected abstract suspend fun deletePlaylistEntries(providerId: String, itemIds: List<String>)

    @Query("DELETE FROM playlists WHERE provider_id = :providerId AND item_id IN (:itemIds)")
    protected abstract suspend fun deletePlaylistRows(providerId: String, itemIds: List<String>)

    @Transaction
    open suspend fun deletePlaylists(providerId: String, itemIds: List<String>) {
        deletePlaylistEntries(providerId, itemIds)
        deletePlaylistRows(providerId, itemIds)
    }

    // ── Removing a source ───────────────────────────────────────────────────

    @Query("DELETE FROM artists WHERE provider_id = :providerId")
    protected abstract suspend fun deleteAllArtists(providerId: String)

    @Query("DELETE FROM albums WHERE provider_id = :providerId")
    protected abstract suspend fun deleteAllAlbums(providerId: String)

    @Query("DELETE FROM tracks WHERE provider_id = :providerId")
    protected abstract suspend fun deleteAllTracks(providerId: String)

    @Query("DELETE FROM playlist_entries WHERE playlist_id IN (SELECT id FROM playlists WHERE provider_id = :providerId)")
    protected abstract suspend fun deleteAllPlaylistEntries(providerId: String)

    @Query("DELETE FROM playlists WHERE provider_id = :providerId")
    protected abstract suspend fun deleteAllPlaylists(providerId: String)

    @Query("DELETE FROM play_history WHERE track_pid = :providerId")
    protected abstract suspend fun deleteHistory(providerId: String)

    @Query("DELETE FROM lyrics_cache WHERE track_pid = :providerId")
    protected abstract suspend fun deleteLyrics(providerId: String)

    @Query("DELETE FROM providers WHERE id = :providerId")
    protected abstract suspend fun deleteProviderRow(providerId: String)

    /**
     * Everything [providerId] put in the index, its history and lyrics, and its `providers` row, in one
     * transaction. Local playlists keep their entries (they read as missing tracks); `downloads` is M2's to clean
     * up together with the files.
     */
    @Transaction
    open suspend fun deleteProvider(providerId: String) {
        deleteAllTracks(providerId)
        deleteAllAlbums(providerId)
        deleteAllArtists(providerId)
        deleteAllPlaylistEntries(providerId)
        deleteAllPlaylists(providerId)
        deleteHistory(providerId)
        deleteLyrics(providerId)
        deleteProviderRow(providerId)
    }
}
