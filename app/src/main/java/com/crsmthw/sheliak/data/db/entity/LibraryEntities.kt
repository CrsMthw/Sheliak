package com.crsmthw.sheliak.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/*
 * The three synced tables. Every row is keyed by (provider_id, item_id); the *_key columns are the normalised
 * merge keys (NormalizedKeys) the "merge duplicates" switch groups by, and sync_run stamps the sync that last
 * reported the row, so a complete sync can delete what it no longer saw (`provider_id = ? AND sync_run <> ?`).
 * No foreign keys between them: a provider may report a track before its album or artist.
 */

@Entity(
    tableName = "artists",
    primaryKeys = ["provider_id", "item_id"],
    indices = [
        Index("name_sort"),
        Index("artist_key"),
        Index("provider_id", "sync_run"),
    ],
)
data class ArtistEntity(
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val name: String,
    @ColumnInfo(name = "name_sort") val nameSort: String,
    @ColumnInfo(name = "artist_key") val artistKey: String,
    @ColumnInfo(name = "album_count") val albumCount: Int,
    @ColumnInfo(name = "art_ref") val artRef: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_run") val syncRun: Long,
)

@Entity(
    tableName = "albums",
    primaryKeys = ["provider_id", "item_id"],
    indices = [
        Index("title_sort"),
        Index("album_key"),
        Index("artist_key"),
        Index("artist_pid", "artist_iid"),
        Index("provider_id", "sync_run"),
    ],
)
data class AlbumEntity(
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val title: String,
    @ColumnInfo(name = "title_sort") val titleSort: String,
    /** Normalised album artist + title. */
    @ColumnInfo(name = "album_key") val albumKey: String,
    /** Normalised album artist — the merged Artist detail lists albums by it. */
    @ColumnInfo(name = "artist_key") val artistKey: String,
    @ColumnInfo(name = "artist_pid") val artistPid: String?,
    @ColumnInfo(name = "artist_iid") val artistIid: String?,
    @ColumnInfo(name = "artist_name") val artistName: String,
    val year: Int?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "art_ref") val artRef: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_run") val syncRun: Long,
)

@Entity(
    tableName = "tracks",
    primaryKeys = ["provider_id", "item_id"],
    indices = [
        Index("title_sort"),
        Index("track_key"),
        Index("album_pid", "album_iid"),
        Index("artist_pid", "artist_iid"),
        Index("play_count"),
        Index("last_played_at"),
        Index("added_at"),
        Index("provider_id", "sync_run"),
    ],
)
data class TrackEntity(
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val title: String,
    @ColumnInfo(name = "title_sort") val titleSort: String,
    /** Normalised artist + album + disc + track number + title: one recording across sources. */
    @ColumnInfo(name = "track_key") val trackKey: String,
    @ColumnInfo(name = "album_pid") val albumPid: String?,
    @ColumnInfo(name = "album_iid") val albumIid: String?,
    @ColumnInfo(name = "album_title") val albumTitle: String?,
    @ColumnInfo(name = "artist_pid") val artistPid: String?,
    @ColumnInfo(name = "artist_iid") val artistIid: String?,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_artist_name") val albumArtistName: String?,
    @ColumnInfo(name = "disc_no") val discNo: Int?,
    @ColumnInfo(name = "track_no") val trackNo: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val year: Int?,
    val genre: String?,
    val codec: String?,
    val container: String?,
    @ColumnInfo(name = "bitrate_kbps") val bitrateKbps: Int?,
    @ColumnInfo(name = "sample_rate_hz") val sampleRateHz: Int?,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int?,
    val channels: Int?,
    val lossless: Boolean,
    @ColumnInfo(name = "file_size") val fileSize: Long?,
    @ColumnInfo(name = "art_ref") val artRef: String?,
    /** What the provider needs to play this track (Plex: the media part key); opaque to everything else. */
    @ColumnInfo(name = "stream_ref") val streamRef: String?,
    @ColumnInfo(name = "has_embedded_lyrics") val hasEmbeddedLyrics: Boolean,
    @ColumnInfo(name = "replaygain_track_db") val replayGainTrackDb: Float?,
    @ColumnInfo(name = "replaygain_album_db") val replayGainAlbumDb: Float?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** max(server count, Sheliak's own) — see IndexMerge. */
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long?,
    @ColumnInfo(name = "sync_run") val syncRun: Long,
)
