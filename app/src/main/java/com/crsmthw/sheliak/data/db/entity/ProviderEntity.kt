package com.crsmthw.sheliak.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One configured source — a provider INSTANCE (`plex:<machineIdentifier>`, `local`, …), not a provider type.
 * [config] is the provider's own JSON (the Plex library sections the user picked, say); nothing outside that
 * provider reads it. [syncCursor] is the opaque `SyncCursor.value` the last successful sync returned.
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val type: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "base_url") val baseUrl: String?,
    @ColumnInfo(name = "server_id") val serverId: String?,
    val config: String?,
    val sort: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long?,
    @ColumnInfo(name = "sync_cursor") val syncCursor: String?,
    val enabled: Boolean,
)
