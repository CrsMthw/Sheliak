package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.IndexAlbum
import com.crsmthw.sheliak.data.provider.IndexArtist
import com.crsmthw.sheliak.data.provider.IndexPlaylist
import com.crsmthw.sheliak.data.provider.IndexPlaylistEntry
import com.crsmthw.sheliak.data.provider.IndexTrack
import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.QualityClassifier
import java.util.Locale

/**
 * Plex metadata → the index's sink inputs (PLEX.md §3, §6). Pure; unit-tested in PlexMappingTest.
 *
 * - Plex times are epoch SECONDS; the index keeps epoch MILLISECONDS (play history compares `last_played_at`
 *   with a play's ms timestamp), so every time is converted here.
 * - Art is stored as the server-relative thumb path (never a URL or a token); [PlexArt] builds the URL.
 * - Items without a `ratingKey` are dropped: nothing could address them.
 */
internal object PlexMapping {

    fun secondsToMillis(seconds: Long?): Long? = seconds?.takeIf { it > 0 }?.let { it * 1000 }

    /**
     * A track. Track number = `index`; disc = `parentIndex`, else the official schema's `absoluteIndex`
     * (correction 13 — the device pass checks a multi-disc album). The row's artist is the track's own
     * (`originalTitle`) when Plex gives one, else the album artist (`grandparentTitle`); the album artist is kept
     * separately when they differ. Format, part key (streamRef), loudness gains from `Media[0].Part[0]` and its
     * audio stream when the response carried streams.
     */
    fun track(m: PlexMetadata): IndexTrack? {
        val id = m.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        val part = m.firstPart
        val audio = m.audioStream
        val albumArtist = m.grandparentTitle?.takeIf { it.isNotBlank() }
        val ownArtist = m.originalTitle?.takeIf { it.isNotBlank() }
        return IndexTrack(
            itemId            = id,
            title             = m.title.orEmpty(),
            titleSort         = m.titleSort?.takeIf { it.isNotBlank() },
            albumItemId       = m.parentRatingKey?.takeIf { it.isNotBlank() },
            albumTitle        = m.parentTitle,
            artistItemId      = m.grandparentRatingKey?.takeIf { it.isNotBlank() },
            artistName        = ownArtist ?: albumArtist.orEmpty(),
            albumArtistName   = albumArtist?.takeIf { ownArtist != null && ownArtist != it },
            discNo            = m.parentIndex ?: m.absoluteIndex,
            trackNo           = m.index,
            durationMs        = m.duration ?: m.media?.firstOrNull()?.duration ?: part?.duration ?: 0L,
            year              = m.year ?: m.parentYear,
            format            = format(m),
            fileSize          = part?.size,
            art               = (m.thumb ?: m.parentThumb ?: m.grandparentThumb)?.takeIf { it.isNotBlank() },
            streamRef         = part?.key?.takeIf { it.isNotBlank() },
            replayGainTrackDb = audio?.gain,
            replayGainAlbumDb = audio?.albumGain,
            addedAt           = secondsToMillis(m.addedAt) ?: 0L,
            updatedAt         = secondsToMillis(m.updatedAt) ?: 0L,
            playCount         = m.viewCount ?: 0,
            lastPlayedAt      = secondsToMillis(m.lastViewedAt),
        )
    }

    /**
     * The track's audio format: codec / container / bit rate / channels from `Media[0]` (in every listing),
     * sample rate and bit depth from its audio `Stream` (only in full metadata). Null without a codec.
     */
    fun format(m: PlexMetadata): AudioFormatInfo? {
        val media = m.media?.firstOrNull()
        val part = m.firstPart
        val audio = m.audioStream
        val codec = (audio?.codec ?: media?.audioCodec)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            ?: return null
        return AudioFormatInfo(
            codec        = codec,
            container    = (part?.container ?: media?.container)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
            bitrateKbps  = media?.bitrate ?: audio?.bitrate,
            sampleRateHz = audio?.samplingRate,
            bitDepth     = audio?.bitDepth,
            channels     = audio?.channels ?: media?.audioChannels,
            lossless     = QualityClassifier.isLosslessCodec(codec),
        )
    }

    /** True when [m] already carries the stream-level details (so no metadata batch is needed for it). */
    fun hasStreamDetails(m: PlexMetadata): Boolean = m.audioStream?.samplingRate != null

    /** An album; [durationMs] is summed from its tracks by the sync (a listing gives no album duration). */
    fun album(m: PlexMetadata, durationMs: Long): IndexAlbum? {
        val id = m.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        return IndexAlbum(
            itemId       = id,
            title        = m.title.orEmpty(),
            titleSort    = m.titleSort?.takeIf { it.isNotBlank() },
            artistItemId = m.parentRatingKey?.takeIf { it.isNotBlank() },
            artistName   = m.parentTitle.orEmpty(),
            year         = m.year,
            trackCount   = m.leafCount ?: 0,
            durationMs   = durationMs,
            art          = m.thumb?.takeIf { it.isNotBlank() },
            addedAt      = secondsToMillis(m.addedAt) ?: 0L,
            updatedAt    = secondsToMillis(m.updatedAt) ?: 0L,
        )
    }

    /** An artist; [albumCount] is counted by the sync from the album listing (artists carry no count). */
    fun artist(m: PlexMetadata, albumCount: Int): IndexArtist? {
        val id = m.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        return IndexArtist(
            itemId     = id,
            name       = m.title.orEmpty(),
            nameSort   = m.titleSort?.takeIf { it.isNotBlank() },
            albumCount = albumCount,
            art        = m.thumb?.takeIf { it.isNotBlank() },
            updatedAt  = secondsToMillis(m.updatedAt) ?: 0L,
        )
    }

    /** One playlist item → an entry; `playlistItemID` is what Plex's edit endpoints take (dumb playlists). */
    fun playlistEntry(item: PlexMetadata): IndexPlaylistEntry? {
        val id = item.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        return IndexPlaylistEntry(
            trackItemId = id,
            entryId     = item.playlistItemId?.takeIf { it.isNotBlank() },
            addedAt     = secondsToMillis(item.addedAt) ?: 0L,
        )
    }

    /** A playlist with ALL its [entries], in order. Art: the `composite` mosaic, else its `thumb`. */
    fun playlist(m: PlexMetadata, entries: List<IndexPlaylistEntry>): IndexPlaylist? {
        val id = m.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        return IndexPlaylist(
            itemId     = id,
            title      = m.title.orEmpty(),
            art        = (m.composite ?: m.thumb)?.takeIf { it.isNotBlank() },
            trackCount = m.leafCount ?: entries.size,
            durationMs = m.duration ?: 0L,
            updatedAt  = secondsToMillis(m.updatedAt) ?: 0L,
            entries    = entries,
        )
    }
}
