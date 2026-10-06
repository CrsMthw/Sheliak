package com.crsmthw.sheliak.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.crsmthw.sheliak.data.db.entity.QueueEntryEntity
import com.crsmthw.sheliak.data.db.entity.QueueStateEntity

/**
 * The persisted playback queue (`queue` + the one `queue_state` row), for the playback service's resumption.
 * Positions are 0-based and contiguous as written by [replace].
 */
@Dao
abstract class QueueDao {

    @Query("SELECT * FROM queue ORDER BY position")
    abstract suspend fun entries(): List<QueueEntryEntity>

    @Query("SELECT * FROM queue_state WHERE id = ${QueueStateEntity.SINGLE_ROW_ID}")
    abstract suspend fun state(): QueueStateEntity?

    /** Just the position / modes, on every transition or pause (the queue itself is unchanged). */
    @Upsert
    abstract suspend fun saveState(state: QueueStateEntity)

    @Query("DELETE FROM queue")
    protected abstract suspend fun clearEntries()

    @Query("DELETE FROM queue_state")
    protected abstract suspend fun clearState()

    @Insert
    protected abstract suspend fun insertEntries(rows: List<QueueEntryEntity>)

    /** A whole new queue and its state, atomically. */
    @Transaction
    open suspend fun replace(entries: List<QueueEntryEntity>, state: QueueStateEntity) {
        clearEntries()
        if (entries.isNotEmpty()) insertEntries(entries)
        saveState(state)
    }

    @Transaction
    open suspend fun clear() {
        clearEntries()
        clearState()
    }
}
