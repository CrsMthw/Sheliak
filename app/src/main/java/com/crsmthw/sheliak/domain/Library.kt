package com.crsmthw.sheliak.domain

import androidx.compose.runtime.Immutable

/**
 * A picture on a provider: [path] is the provider's own reference (a Plex `thumb`, a content URI, a file path),
 * and only that provider can turn it into something Coil loads (`MusicProvider.artModel`).
 */
@Immutable
data class ArtRef(val providerId: String, val path: String)

/**
 * One track of the index. [titleSort] is the normalised sort string the lists are ordered by (lower-case,
 * diacritics stripped). [playCount] / [lastPlayedAt] are the merge of the server's numbers and Sheliak's own
 * plays, whichever is higher.
 */
@Immutable
data class Track(
    val key: TrackKey,
    val title: String,
    val titleSort: String,
    val artistName: String,
    val artistKey: TrackKey?,
    val albumTitle: String?,
    val albumKey: TrackKey?,
    val discNo: Int?,
    val trackNo: Int?,
    val durationMs: Long,
    val year: Int?,
    val format: AudioFormatInfo?,
    val art: ArtRef?,
    val addedAt: Long,
    val playCount: Int,
    val lastPlayedAt: Long?,
)

@Immutable
data class Album(
    val key: TrackKey,
    val title: String,
    val artistName: String,
    val artistKey: TrackKey?,
    val year: Int?,
    val trackCount: Int,
    val durationMs: Long,
    val art: ArtRef?,
)

@Immutable
data class Artist(val key: TrackKey, val name: String, val albumCount: Int, val art: ArtRef?)

/** LOCAL playlists live only in Sheliak (Liked Songs is one); SERVER playlists mirror a provider's. */
enum class PlaylistKind { LOCAL, SERVER }

/**
 * A playlist. [id] is the local row id (stable across syncs for a server playlist too); [providerKey] is the
 * server's identity for a SERVER playlist and null for a LOCAL one. [title] is empty for Liked Songs: its
 * name is a string resource, rendered by the UI when [isLikedSongs] is set.
 */
@Immutable
data class Playlist(
    val id: Long,
    val kind: PlaylistKind,
    val providerKey: TrackKey?,
    val title: String,
    val trackCount: Int,
    val durationMs: Long,
    val art: ArtRef?,
    val isLikedSongs: Boolean,
)
