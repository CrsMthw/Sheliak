package com.crsmthw.sheliak.data.db

import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntity
import com.crsmthw.sheliak.data.db.entity.ProviderEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import com.crsmthw.sheliak.data.provider.IndexAlbum
import com.crsmthw.sheliak.data.provider.IndexArtist
import com.crsmthw.sheliak.data.provider.IndexPlaylist
import com.crsmthw.sheliak.data.provider.IndexTrack
import com.crsmthw.sheliak.data.provider.ProviderInstance
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.PlaylistKind
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey

/*
 * Entity ↔ domain, sink input → entity, and entity → sink input (for IndexReader). Pure; unit-tested in
 * MappersTest. The sink-side mappers are where the normalised keys and sort strings are computed, so every
 * provider gets the same ones.
 */

// ── Entity → domain ─────────────────────────────────────────────────────────

private fun keyOrNull(providerId: String?, itemId: String?): TrackKey? =
    if (providerId != null && itemId != null) TrackKey(providerId, itemId) else null

private fun artOrNull(providerId: String?, path: String?): ArtRef? =
    if (providerId != null && !path.isNullOrEmpty()) ArtRef(providerId, path) else null

/** The stored format, or null when the provider reported no codec. */
fun TrackEntity.format(): AudioFormatInfo? = codec?.let {
    AudioFormatInfo(
        codec        = it,
        container    = container,
        bitrateKbps  = bitrateKbps,
        sampleRateHz = sampleRateHz,
        bitDepth     = bitDepth,
        channels     = channels,
        lossless     = lossless,
    )
}

fun TrackEntity.toDomain(): Track = Track(
    key          = TrackKey(providerId, itemId),
    title        = title,
    titleSort    = titleSort,
    artistName   = artistName,
    artistKey    = keyOrNull(artistPid, artistIid),
    albumTitle   = albumTitle,
    albumKey     = keyOrNull(albumPid, albumIid),
    discNo       = discNo,
    trackNo      = trackNo,
    durationMs   = durationMs,
    year         = year,
    format       = format(),
    art          = artOrNull(providerId, artRef),
    addedAt      = addedAt,
    playCount    = playCount,
    lastPlayedAt = lastPlayedAt,
)

fun AlbumEntity.toDomain(): Album = Album(
    key        = TrackKey(providerId, itemId),
    title      = title,
    artistName = artistName,
    artistKey  = keyOrNull(artistPid, artistIid),
    year       = year,
    trackCount = trackCount,
    durationMs = durationMs,
    art        = artOrNull(providerId, artRef),
)

fun ArtistEntity.toDomain(): Artist = Artist(
    key        = TrackKey(providerId, itemId),
    name       = name,
    albumCount = albumCount,
    art        = artOrNull(providerId, artRef),
)

fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id           = id,
    kind         = kind,
    providerKey  = keyOrNull(providerId, itemId),
    title        = title,
    trackCount   = trackCount,
    durationMs   = durationMs,
    art          = artOrNull(providerId, artRef),
    isLikedSongs = isLikedSongs,
)

fun ProviderEntity.toInstance(): ProviderInstance = ProviderInstance(
    id          = id,
    type        = type,
    displayName = displayName,
    baseUrl     = baseUrl,
    serverId    = serverId,
    config      = config,
    enabled     = enabled,
)

// ── Sink input → entity ─────────────────────────────────────────────────────

/** [syncRun] stamps the row as reported by that sync run. */
fun IndexArtist.toEntity(providerId: String, syncRun: Long): ArtistEntity = ArtistEntity(
    providerId = providerId,
    itemId     = itemId,
    name       = name,
    nameSort   = NormalizedKeys.sortKey(nameSort?.takeIf { it.isNotBlank() } ?: name),
    artistKey  = NormalizedKeys.artistKey(name),
    albumCount = albumCount,
    artRef     = art,
    updatedAt  = updatedAt,
    syncRun    = syncRun,
)

fun IndexAlbum.toEntity(providerId: String, syncRun: Long): AlbumEntity = AlbumEntity(
    providerId = providerId,
    itemId     = itemId,
    title      = title,
    titleSort  = NormalizedKeys.sortKey(titleSort?.takeIf { it.isNotBlank() } ?: title),
    albumKey   = NormalizedKeys.albumKey(artistName, title),
    artistKey  = NormalizedKeys.artistKey(artistName),
    artistPid  = artistItemId?.let { providerId },
    artistIid  = artistItemId,
    artistName = artistName,
    year       = year,
    trackCount = trackCount,
    durationMs = durationMs,
    artRef     = art,
    addedAt    = addedAt,
    updatedAt  = updatedAt,
    syncRun    = syncRun,
)

fun IndexTrack.toEntity(providerId: String, syncRun: Long): TrackEntity = TrackEntity(
    providerId        = providerId,
    itemId            = itemId,
    title             = title,
    titleSort         = NormalizedKeys.sortKey(titleSort?.takeIf { it.isNotBlank() } ?: title),
    trackKey          = NormalizedKeys.trackKey(artistName, albumTitle, discNo, trackNo, title),
    albumPid          = albumItemId?.let { providerId },
    albumIid          = albumItemId,
    albumTitle        = albumTitle,
    artistPid         = artistItemId?.let { providerId },
    artistIid         = artistItemId,
    artistName        = artistName,
    albumArtistName   = albumArtistName,
    discNo            = discNo,
    trackNo           = trackNo,
    durationMs        = durationMs,
    year              = year,
    genre             = genre,
    codec             = format?.codec,
    container         = format?.container,
    bitrateKbps       = format?.bitrateKbps,
    sampleRateHz      = format?.sampleRateHz,
    bitDepth          = format?.bitDepth,
    channels          = format?.channels,
    lossless          = format?.lossless ?: false,
    fileSize          = fileSize,
    artRef            = art,
    streamRef         = streamRef,
    hasEmbeddedLyrics = hasEmbeddedLyrics,
    replayGainTrackDb = replayGainTrackDb,
    replayGainAlbumDb = replayGainAlbumDb,
    addedAt           = addedAt,
    updatedAt         = updatedAt,
    playCount         = playCount,
    lastPlayedAt      = lastPlayedAt,
    syncRun           = syncRun,
)

/** A SERVER playlist row; [id] is the existing local id when the playlist is already stored, else 0 (insert). */
fun IndexPlaylist.toEntity(providerId: String, syncRun: Long, id: Long, sortIndex: Int): PlaylistEntity =
    PlaylistEntity(
        id           = id,
        kind         = PlaylistKind.SERVER,
        providerId   = providerId,
        itemId       = itemId,
        title        = title,
        artRef       = art,
        isLikedSongs = false,
        sortIndex    = sortIndex,
        trackCount   = trackCount,
        durationMs   = durationMs,
        updatedAt    = updatedAt,
        syncRun      = syncRun,
    )

// ── Entity → sink input (IndexReader) ───────────────────────────────────────

/** The track as its provider wrote it (sort title and play numbers as stored, i.e. normalised / merged). */
fun TrackEntity.toIndexTrack(): IndexTrack = IndexTrack(
    itemId            = itemId,
    title             = title,
    titleSort         = titleSort,
    albumItemId       = albumIid,
    albumTitle        = albumTitle,
    artistItemId      = artistIid,
    artistName        = artistName,
    albumArtistName   = albumArtistName,
    discNo            = discNo,
    trackNo           = trackNo,
    durationMs        = durationMs,
    year              = year,
    genre             = genre,
    format            = format(),
    fileSize          = fileSize,
    art               = artRef,
    streamRef         = streamRef,
    hasEmbeddedLyrics = hasEmbeddedLyrics,
    replayGainTrackDb = replayGainTrackDb,
    replayGainAlbumDb = replayGainAlbumDb,
    addedAt           = addedAt,
    updatedAt         = updatedAt,
    playCount         = playCount,
    lastPlayedAt      = lastPlayedAt,
)
