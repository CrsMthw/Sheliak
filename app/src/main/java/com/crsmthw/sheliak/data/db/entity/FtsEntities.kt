package com.crsmthw.sheliak.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/*
 * External-content FTS4 indexes over the three library tables: Room creates the triggers that keep each one in
 * step with its content table, so nothing writes them directly. A search joins back on rowid
 * (`JOIN tracks_fts ON tracks.rowid = tracks_fts.rowid WHERE tracks_fts MATCH ?`). The unicode61 tokenizer
 * folds case and diacritics beyond ASCII ("bjork" finds "Björk"). The content tables are only ever written with
 * upserts (insert, else update), never INSERT OR REPLACE, which would bypass the delete trigger.
 */

@Fts4(contentEntity = TrackEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "tracks_fts")
data class TrackFts(
    val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_title") val albumTitle: String?,
)

@Fts4(contentEntity = AlbumEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "albums_fts")
data class AlbumFts(
    val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
)

@Fts4(contentEntity = ArtistEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "artists_fts")
data class ArtistFts(val name: String)
