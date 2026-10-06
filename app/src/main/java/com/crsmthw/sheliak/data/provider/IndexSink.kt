package com.crsmthw.sheliak.data.provider

import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.TrackKey

/*
 * What a provider writes into the index during [MusicProvider.sync]. Item ids are the provider's own; the sink
 * adds the provider id, computes the normalised keys and sort strings, buffers 500 rows per table and writes
 * them in one transaction each. See docs/INDEX.md, "How a provider feeds the sink".
 */

/** The kinds a provider can declare complete with [IndexSink.markComplete]. */
enum class IndexKind { ARTISTS, ALBUMS, TRACKS, PLAYLISTS }

interface IndexSink {

    suspend fun putArtists(artists: List<IndexArtist>)

    suspend fun putAlbums(albums: List<IndexAlbum>)

    suspend fun putTracks(tracks: List<IndexTrack>)

    /** One server playlist with ALL its entries, in order; replaces the stored entries. */
    suspend fun putPlaylist(playlist: IndexPlaylist)

    /** Items the server reports as deleted (for providers whose incremental API says so). */
    suspend fun remove(kind: IndexKind, itemIds: List<String>)

    /**
     * Declares that this run reported EVERY current item of [kind] (a full listing, not an incremental one).
     * When the run succeeds, rows of that kind this run did not report are deleted. Never call it after an
     * incremental listing — it would delete everything that did not change.
     */
    fun markComplete(kind: IndexKind)
}

data class IndexArtist(
    val itemId: String,
    val name: String,
    /** The server's sort name ("Beatles, The"); null → derived from [name]. */
    val nameSort: String? = null,
    val albumCount: Int = 0,
    val art: String? = null,
    val updatedAt: Long = 0L,
)

data class IndexAlbum(
    val itemId: String,
    val title: String,
    val titleSort: String? = null,
    val artistItemId: String?,
    val artistName: String,
    val year: Int? = null,
    val trackCount: Int = 0,
    val durationMs: Long = 0L,
    val art: String? = null,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * One track. [artistName] is the track's own artist (what a row shows); [albumArtistName] the album's, when
 * they differ (a compilation). [streamRef] is whatever [MusicProvider.resolvePlayback] will need later (Plex:
 * the part key), read back through [IndexReader]. [playCount] / [lastPlayedAt] are the server's numbers; the
 * sink keeps Sheliak's own when they are higher.
 */
data class IndexTrack(
    val itemId: String,
    val title: String,
    val titleSort: String? = null,
    val albumItemId: String? = null,
    val albumTitle: String? = null,
    val artistItemId: String? = null,
    val artistName: String,
    val albumArtistName: String? = null,
    val discNo: Int? = null,
    val trackNo: Int? = null,
    val durationMs: Long = 0L,
    val year: Int? = null,
    val genre: String? = null,
    val format: AudioFormatInfo? = null,
    val fileSize: Long? = null,
    val art: String? = null,
    val streamRef: String? = null,
    val hasEmbeddedLyrics: Boolean = false,
    val replayGainTrackDb: Float? = null,
    val replayGainAlbumDb: Float? = null,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
)

data class IndexPlaylist(
    val itemId: String,
    val title: String,
    val art: String? = null,
    val trackCount: Int,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
    val entries: List<IndexPlaylistEntry>,
)

/** [entryId] is the server's id for this position (Plex `playlistItemID`), which its edit endpoints take. */
data class IndexPlaylistEntry(val trackItemId: String, val entryId: String? = null, val addedAt: Long = 0L)

/** Reads back what a provider wrote: the track as stored, or null when it is not in the index. */
interface IndexReader {
    suspend fun track(key: TrackKey): IndexTrack?
}
