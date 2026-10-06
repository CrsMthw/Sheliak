package com.crsmthw.sheliak.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.crsmthw.sheliak.data.db.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

/** How many tracks one source has in the index. */
data class ProviderTrackCount(
    @ColumnInfo(name = "provider_id") val providerId: String,
    val count: Int,
)

@Dao
interface ProviderDao {

    @Query("SELECT * FROM providers ORDER BY sort, added_at")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Query("SELECT id FROM providers WHERE enabled = 1")
    suspend fun enabledIds(): List<String>

    @Query("SELECT COALESCE(MAX(sort), -1) FROM providers")
    suspend fun maxSort(): Int

    @Insert
    suspend fun insert(row: ProviderEntity)

    /** Changes what the provider itself owns, keeping its sync bookkeeping and its place in the list. */
    @Query(
        "UPDATE providers SET type = :type, display_name = :displayName, base_url = :baseUrl, " +
            "server_id = :serverId, config = :config, enabled = :enabled WHERE id = :id",
    )
    suspend fun updateInstance(
        id: String,
        type: String,
        displayName: String,
        baseUrl: String?,
        serverId: String?,
        config: String?,
        enabled: Boolean,
    )

    @Query("UPDATE providers SET sync_cursor = :cursor, last_sync_at = :syncedAt WHERE id = :id")
    suspend fun saveSyncResult(id: String, cursor: String?, syncedAt: Long)

    /** The next sync of [id] is a full one. */
    @Query("UPDATE providers SET sync_cursor = NULL WHERE id = :id")
    suspend fun resetSyncCursor(id: String)

    @Query("SELECT provider_id, COUNT(*) AS count FROM tracks GROUP BY provider_id")
    fun trackCounts(): Flow<List<ProviderTrackCount>>
}
