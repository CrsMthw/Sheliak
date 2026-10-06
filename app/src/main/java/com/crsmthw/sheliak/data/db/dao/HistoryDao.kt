package com.crsmthw.sheliak.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.crsmthw.sheliak.data.db.entity.PlayHistoryEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** One play with its track, newest first. */
data class HistoryRow(
    @Embedded val track: TrackEntity,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "played_ms") val playedMs: Long,
)

@Dao
abstract class HistoryDao {

    @Insert
    protected abstract suspend fun insert(row: PlayHistoryEntity): Long

    @Query(
        "UPDATE tracks SET play_count = play_count + 1, " +
            "last_played_at = MAX(COALESCE(last_played_at, 0), :playedAt) " +
            "WHERE provider_id = :providerId AND item_id = :itemId",
    )
    protected abstract suspend fun bumpTrack(providerId: String, itemId: String, playedAt: Long)

    /** The history row and the track's play count / last-played time, together. */
    @Transaction
    open suspend fun recordPlay(row: PlayHistoryEntity) {
        insert(row)
        bumpTrack(row.trackPid, row.trackIid, row.playedAt)
    }

    /** Plays of tracks still in the index, newest first. */
    @Query(
        "SELECT t.*, h.played_at AS played_at, h.played_ms AS played_ms FROM play_history h " +
            "JOIN tracks t ON t.provider_id = h.track_pid AND t.item_id = h.track_iid " +
            "ORDER BY h.played_at DESC LIMIT :limit",
    )
    abstract fun history(limit: Int): Flow<List<HistoryRow>>
}
