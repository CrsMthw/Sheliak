package com.crsmthw.sheliak.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.crsmthw.sheliak.domain.RepeatMode

/** The last playback queue, one row per position, for `onPlaybackResumption` after the process died. */
@Entity(tableName = "queue")
data class QueueEntryEntity(
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "track_pid") val trackPid: String,
    @ColumnInfo(name = "track_iid") val trackIid: String,
)

/** The single row ([id] = [SINGLE_ROW_ID]) that goes with [QueueEntryEntity]: where in the queue, and the modes. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "current_position") val currentPosition: Int,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    val shuffle: Boolean,
    @ColumnInfo(name = "repeat_mode") val repeatMode: RepeatMode,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID: Int = 1
    }
}

/** One play of a track, for every provider; written by the playback service's reporting hook. */
@Entity(
    tableName = "play_history",
    indices = [Index("played_at"), Index("track_pid", "track_iid")],
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_pid") val trackPid: String,
    @ColumnInfo(name = "track_iid") val trackIid: String,
    @ColumnInfo(name = "played_at") val playedAt: Long,
    @ColumnInfo(name = "played_ms") val playedMs: Long,
)

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED }

/** An offline copy of a track (M2): `resolvePlayback` checks this table first. */
@Entity(tableName = "downloads", primaryKeys = ["track_pid", "track_iid"])
data class DownloadEntity(
    @ColumnInfo(name = "track_pid") val trackPid: String,
    @ColumnInfo(name = "track_iid") val trackIid: String,
    val state: DownloadState,
    @ColumnInfo(name = "file_path") val filePath: String?,
    val bytes: Long,
    @ColumnInfo(name = "format_json") val formatJson: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

enum class LyricsSource { EMBEDDED, SIDECAR, PROVIDER, LRCLIB, NONE }

/** The lyrics chain's cache (M2). [source] NONE records "definitely none" so it is not asked again. */
@Entity(tableName = "lyrics_cache", primaryKeys = ["track_pid", "track_iid"])
data class LyricsCacheEntity(
    @ColumnInfo(name = "track_pid") val trackPid: String,
    @ColumnInfo(name = "track_iid") val trackIid: String,
    val synced: String?,
    val plain: String?,
    val source: LyricsSource,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
