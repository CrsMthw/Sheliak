package com.crsmthw.sheliak.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.crsmthw.sheliak.data.db.entity.PlaylistEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** Playlist reads. Server playlists are written by the sync (IndexDao); local editing is M2. */
@Dao
interface PlaylistDao {

    /** Liked Songs first, then by the user's order (M2), then by title. */
    @Query("SELECT * FROM playlists ORDER BY is_liked_songs DESC, sort_index, title COLLATE NOCASE")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlists")
    fun count(): Flow<Int>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun playlist(playlistId: Long): Flow<PlaylistEntity?>

    /** In playlist order; an entry whose track is not in the index (an unsynced library) is skipped. */
    @Query(
        "SELECT t.* FROM playlist_entries e JOIN tracks t ON t.provider_id = e.track_pid AND t.item_id = e.track_iid " +
            "WHERE e.playlist_id = :playlistId ORDER BY e.position",
    )
    fun playlistTracks(playlistId: Long): Flow<List<TrackEntity>>

    /** [pattern] comes from FtsQuery.likeContains. */
    @Query(
        "SELECT * FROM playlists WHERE title LIKE :pattern ESCAPE '\\' " +
            "ORDER BY is_liked_songs DESC, sort_index, title COLLATE NOCASE LIMIT :limit",
    )
    fun search(pattern: String, limit: Int): Flow<List<PlaylistEntity>>
}
