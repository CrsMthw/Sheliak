package com.crsmthw.sheliak.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.crsmthw.sheliak.domain.PlaylistKind

/**
 * A playlist: LOCAL (Sheliak's own; Liked Songs is the one with [isLikedSongs], `sort_index` 0 and an empty
 * [title] — its name is a string resource) or SERVER (mirrored from a provider, identified by
 * ([providerId], [itemId]) and re-synced). [id] is local and survives re-syncs, so a server playlist keeps its
 * id while its entries are replaced.
 */
@Entity(
    tableName = "playlists",
    indices = [Index("provider_id", "item_id", unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: PlaylistKind,
    @ColumnInfo(name = "provider_id") val providerId: String?,
    @ColumnInfo(name = "item_id") val itemId: String?,
    val title: String,
    @ColumnInfo(name = "art_ref") val artRef: String?,
    @ColumnInfo(name = "is_liked_songs") val isLikedSongs: Boolean,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** The sync that last reported a SERVER playlist; 0 for a LOCAL one. */
    @ColumnInfo(name = "sync_run") val syncRun: Long,
)

/**
 * One position of a playlist. [entryId] is the server's own id for the entry (Plex `playlistItemID`), which its
 * edit endpoints take; null for a LOCAL playlist.
 */
@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlist_id", "position"],
    indices = [Index("track_pid", "track_iid")],
)
data class PlaylistEntryEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val position: Int,
    @ColumnInfo(name = "track_pid") val trackPid: String,
    @ColumnInfo(name = "track_iid") val trackIid: String,
    @ColumnInfo(name = "entry_id") val entryId: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
