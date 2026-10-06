package com.crsmthw.sheliak.data.db

import androidx.room.ColumnInfo
import com.crsmthw.sheliak.data.db.entity.TrackEntity

/** A track's play statistics as stored, read back before a sync batch overwrites the row. */
data class PlayStats(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long?,
)

/**
 * The rules for writing a sync batch over rows that may already exist — the only index-merge logic that lives
 * outside SQL. (Merging duplicates ACROSS providers is SQL: the merged queries group by the normalised keys and
 * keep one representative row per group; see LibraryDao.) Pure; unit-tested in IndexMergeTest.
 */
object IndexMerge {

    /**
     * Keeps the higher of the server's and the stored play count and the later of the two last-played times, so a
     * sync never rolls back a play Sheliak recorded that the server has not counted yet (and a server that counts
     * other clients' plays still raises the number).
     */
    fun mergePlayStats(incoming: List<TrackEntity>, stored: Map<String, PlayStats>): List<TrackEntity> =
        incoming.map { row ->
            val old = stored[row.itemId] ?: return@map row
            val count = maxOf(row.playCount, old.playCount)
            val last = maxOfNullable(row.lastPlayedAt, old.lastPlayedAt)
            if (count == row.playCount && last == row.lastPlayedAt) row
            else row.copy(playCount = count, lastPlayedAt = last)
        }

    /** The later of two optional instants; null only when both are. */
    fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else      -> maxOf(a, b)
    }
}
