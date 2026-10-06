package com.crsmthw.sheliak.data.provider.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Plex's JSON, as docs/PLEX.md lists it (the only source for these names). Every field is nullable with a default
 * and every scalar goes through a lenient serializer (PlexJson.kt): Plex mixes strings, numbers and booleans for
 * the same field. Unknown keys are ignored by the app's Json. `ratingKey` and every other id are opaque Strings.
 * Times are epoch SECONDS here; PlexMapping converts them to the index's milliseconds.
 */

// ── plex.tv ─────────────────────────────────────────────────────────────────

/** `POST/GET https://plex.tv/api/v2/pins` (PLEX.md §1). [authToken] is null until the user claims the PIN. */
@Serializable
data class PlexPin(
    @Serializable(with = PlexLongSerializer::class) val id: Long? = null,
    @Serializable(with = PlexStringSerializer::class) val code: String? = null,
    @Serializable(with = PlexStringSerializer::class) val authToken: String? = null,
    @Serializable(with = PlexLongSerializer::class) val expiresIn: Long? = null,
    /** Read as text: an ISO-8601 instant, or epoch seconds if Plex ever sends a number (PinExpiry handles both). */
    @Serializable(with = PlexStringSerializer::class) val expiresAt: String? = null,
    @Serializable(with = PlexStringSerializer::class) val createdAt: String? = null,
    @Serializable(with = PlexStringSerializer::class) val clientIdentifier: String? = null,
    @Serializable(with = PlexStringSerializer::class) val product: String? = null,
    @Serializable(with = PlexBooleanSerializer::class) val trusted: Boolean? = null,
    @Serializable(with = PlexStringSerializer::class) val qr: String? = null,
    @Serializable(with = PlexBooleanSerializer::class) val newRegistration: Boolean? = null,
)

/** One device of `GET https://clients.plex.tv/api/v2/resources` (a bare JSON array; PLEX.md §2). */
@Serializable
data class PlexResource(
    @Serializable(with = PlexStringSerializer::class) val name: String? = null,
    @Serializable(with = PlexStringSerializer::class) val product: String? = null,
    @Serializable(with = PlexStringSerializer::class) val productVersion: String? = null,
    @Serializable(with = PlexStringSerializer::class) val platform: String? = null,
    @Serializable(with = PlexStringSerializer::class) val platformVersion: String? = null,
    @Serializable(with = PlexStringSerializer::class) val device: String? = null,
    /** The PMS `machineIdentifier`. */
    @Serializable(with = PlexStringSerializer::class) val clientIdentifier: String? = null,
    @Serializable(with = PlexStringSerializer::class) val createdAt: String? = null,
    @Serializable(with = PlexStringSerializer::class) val lastSeenAt: String? = null,
    /** A comma-separated list ("server,client"): test with [isServer], never by equality. */
    @Serializable(with = PlexStringSerializer::class) val provides: String? = null,
    @Serializable(with = PlexBooleanSerializer::class) val owned: Boolean? = null,
    @Serializable(with = PlexStringSerializer::class) val ownerId: String? = null,
    /** The owner's name on a shared server. */
    @Serializable(with = PlexStringSerializer::class) val sourceTitle: String? = null,
    /** The token for THIS server — every PMS call uses it, never the account token. */
    @Serializable(with = PlexStringSerializer::class) val accessToken: String? = null,
    @Serializable(with = PlexStringSerializer::class) val publicAddress: String? = null,
    @Serializable(with = PlexBooleanSerializer::class) val publicAddressMatches: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val httpsRequired: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val relay: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val presence: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val home: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val synced: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val dnsRebindingProtection: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val natLoopbackSupported: Boolean? = null,
    val connections: List<PlexConnection>? = null,
) {
    val isServer: Boolean
        get() = provides.orEmpty().split(',').any { it.trim() == "server" }
}

/** One `connections[]` entry: LAN address, custom access URL, `*.plex.direct` or relay. */
@Serializable
data class PlexConnection(
    @Serializable(with = PlexStringSerializer::class) val protocol: String? = null,
    @Serializable(with = PlexStringSerializer::class) val address: String? = null,
    @Serializable(with = PlexIntSerializer::class) val port: Int? = null,
    /** The full URL to use verbatim (never rebuilt from address and port). */
    @Serializable(with = PlexStringSerializer::class) val uri: String? = null,
    @Serializable(with = PlexBooleanSerializer::class) val local: Boolean? = null,
    @Serializable(with = PlexBooleanSerializer::class) val relay: Boolean? = null,
    @SerialName("IPv6")
    @Serializable(with = PlexBooleanSerializer::class) val ipv6: Boolean? = null,
)

// ── PMS ─────────────────────────────────────────────────────────────────────

/** Every PMS answer is wrapped in `{"MediaContainer": {...}}`. */
@Serializable
data class PlexResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexMediaContainer? = null,
)

@Serializable
data class PlexMediaContainer(
    @Serializable(with = PlexIntSerializer::class) val size: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val totalSize: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val offset: Int? = null,
    /** `/identity` */
    @Serializable(with = PlexStringSerializer::class) val machineIdentifier: String? = null,
    @Serializable(with = PlexStringSerializer::class) val version: String? = null,
    @Serializable(with = PlexBooleanSerializer::class) val claimed: Boolean? = null,
    /** `/library/sections` */
    @SerialName("Directory") val directories: List<PlexDirectory>? = null,
    /** Listings, `/library/metadata/{ids}`, `/playlists`, playlist items. */
    @SerialName("Metadata") val metadata: List<PlexMetadata>? = null,
)

/** A library section (`/library/sections`): music is `type == "artist"`. */
@Serializable
data class PlexDirectory(
    @Serializable(with = PlexStringSerializer::class) val key: String? = null,
    @Serializable(with = PlexStringSerializer::class) val title: String? = null,
    @Serializable(with = PlexStringSerializer::class) val type: String? = null,
    @Serializable(with = PlexStringSerializer::class) val uuid: String? = null,
)

/**
 * A track (type 10), album (9), artist (8), playlist (15) or playlist item — one shape, PLEX.md §3 and §6.
 * Track: `parent*` = the album, `grandparent*` = the album artist, [originalTitle] = the track's own artist when
 * it differs, [index] = track number, disc number = [parentIndex] (python-plexapi) or [absoluteIndex] (the
 * official schema) — unresolved, so both are read.
 */
@Serializable
data class PlexMetadata(
    @Serializable(with = PlexStringSerializer::class) val ratingKey: String? = null,
    @Serializable(with = PlexStringSerializer::class) val key: String? = null,
    @Serializable(with = PlexStringSerializer::class) val guid: String? = null,
    @Serializable(with = PlexStringSerializer::class) val type: String? = null,
    @Serializable(with = PlexStringSerializer::class) val title: String? = null,
    @Serializable(with = PlexStringSerializer::class) val titleSort: String? = null,
    @Serializable(with = PlexStringSerializer::class) val originalTitle: String? = null,
    @Serializable(with = PlexIntSerializer::class) val index: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val parentIndex: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val absoluteIndex: Int? = null,
    @Serializable(with = PlexStringSerializer::class) val parentRatingKey: String? = null,
    @Serializable(with = PlexStringSerializer::class) val parentKey: String? = null,
    @Serializable(with = PlexStringSerializer::class) val parentTitle: String? = null,
    @Serializable(with = PlexStringSerializer::class) val parentThumb: String? = null,
    @Serializable(with = PlexIntSerializer::class) val parentYear: Int? = null,
    @Serializable(with = PlexStringSerializer::class) val grandparentRatingKey: String? = null,
    @Serializable(with = PlexStringSerializer::class) val grandparentKey: String? = null,
    @Serializable(with = PlexStringSerializer::class) val grandparentTitle: String? = null,
    @Serializable(with = PlexStringSerializer::class) val grandparentThumb: String? = null,
    @Serializable(with = PlexStringSerializer::class) val grandparentArt: String? = null,
    @Serializable(with = PlexStringSerializer::class) val thumb: String? = null,
    @Serializable(with = PlexStringSerializer::class) val art: String? = null,
    @Serializable(with = PlexIntSerializer::class) val year: Int? = null,
    /** Milliseconds. */
    @Serializable(with = PlexLongSerializer::class) val duration: Long? = null,
    @Serializable(with = PlexLongSerializer::class) val addedAt: Long? = null,
    @Serializable(with = PlexLongSerializer::class) val updatedAt: Long? = null,
    @Serializable(with = PlexLongSerializer::class) val lastViewedAt: Long? = null,
    @Serializable(with = PlexIntSerializer::class) val viewCount: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val skipCount: Int? = null,
    @Serializable(with = PlexFloatSerializer::class) val userRating: Float? = null,
    @SerialName("librarySectionID")
    @Serializable(with = PlexStringSerializer::class) val librarySectionId: String? = null,
    /** Album: its number of tracks. Playlist: its number of items. */
    @Serializable(with = PlexIntSerializer::class) val leafCount: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val viewedLeafCount: Int? = null,
    @Serializable(with = PlexStringSerializer::class) val originallyAvailableAt: String? = null,
    // Playlists (PLEX.md §6)
    @Serializable(with = PlexBooleanSerializer::class) val smart: Boolean? = null,
    @Serializable(with = PlexStringSerializer::class) val playlistType: String? = null,
    @Serializable(with = PlexStringSerializer::class) val composite: String? = null,
    @Serializable(with = PlexStringSerializer::class) val icon: String? = null,
    /** On every item of a non-smart playlist: the id its edit endpoints take. */
    @SerialName("playlistItemID")
    @Serializable(with = PlexStringSerializer::class) val playlistItemId: String? = null,
    @SerialName("Media") val media: List<PlexMedia>? = null,
)

@Serializable
data class PlexMedia(
    @Serializable(with = PlexStringSerializer::class) val id: String? = null,
    @Serializable(with = PlexLongSerializer::class) val duration: Long? = null,
    /** kbps */
    @Serializable(with = PlexIntSerializer::class) val bitrate: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val audioChannels: Int? = null,
    @Serializable(with = PlexStringSerializer::class) val audioCodec: String? = null,
    @Serializable(with = PlexStringSerializer::class) val container: String? = null,
    @SerialName("Part") val parts: List<PlexPart>? = null,
)

@Serializable
data class PlexPart(
    @Serializable(with = PlexStringSerializer::class) val id: String? = null,
    /** `/library/parts/{partId}/{changestamp}/file.{ext}` — the direct-play path, stored as the track's streamRef. */
    @Serializable(with = PlexStringSerializer::class) val key: String? = null,
    @Serializable(with = PlexLongSerializer::class) val duration: Long? = null,
    @Serializable(with = PlexStringSerializer::class) val file: String? = null,
    /** Bytes. */
    @Serializable(with = PlexLongSerializer::class) val size: Long? = null,
    @Serializable(with = PlexStringSerializer::class) val container: String? = null,
    /** Usually only in full metadata (`/library/metadata/{ids}`), not in `/all` listings. */
    @SerialName("Stream") val streams: List<PlexStream>? = null,
)

/** `streamType` 2 = audio (format details), 4 = lyrics (`key` → `/library/streams/{id}`). */
@Serializable
data class PlexStream(
    @Serializable(with = PlexStringSerializer::class) val id: String? = null,
    @Serializable(with = PlexIntSerializer::class) val streamType: Int? = null,
    @Serializable(with = PlexStringSerializer::class) val codec: String? = null,
    @Serializable(with = PlexIntSerializer::class) val bitrate: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val bitDepth: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val samplingRate: Int? = null,
    @Serializable(with = PlexIntSerializer::class) val channels: Int? = null,
    @Serializable(with = PlexStringSerializer::class) val audioChannelLayout: String? = null,
    @Serializable(with = PlexFloatSerializer::class) val gain: Float? = null,
    @Serializable(with = PlexFloatSerializer::class) val albumGain: Float? = null,
    @Serializable(with = PlexFloatSerializer::class) val peak: Float? = null,
    @Serializable(with = PlexFloatSerializer::class) val albumPeak: Float? = null,
    @Serializable(with = PlexFloatSerializer::class) val loudness: Float? = null,
    @Serializable(with = PlexFloatSerializer::class) val lra: Float? = null,
    // Lyric streams
    @Serializable(with = PlexStringSerializer::class) val key: String? = null,
    @Serializable(with = PlexStringSerializer::class) val format: String? = null,
    @Serializable(with = PlexBooleanSerializer::class) val timed: Boolean? = null,
    @Serializable(with = PlexStringSerializer::class) val provider: String? = null,
    @Serializable(with = PlexIntSerializer::class) val minLines: Int? = null,
)

/** Plex's numeric item types (PLEX.md §3) and stream types. */
object PlexTypes {
    const val ARTIST = 8
    const val ALBUM = 9
    const val TRACK = 10
    const val PLAYLIST = 15

    const val STREAM_AUDIO = 2
    const val STREAM_LYRICS = 4

    /** A music section's `type`. */
    const val SECTION_MUSIC = "artist"
}

/** The first media's first part — the one the index describes and direct play opens. */
val PlexMetadata.firstPart: PlexPart? get() = media?.firstOrNull()?.parts?.firstOrNull()

/** The first audio stream of [firstPart], when the response carried streams. */
val PlexMetadata.audioStream: PlexStream?
    get() = firstPart?.streams?.firstOrNull { it.streamType == PlexTypes.STREAM_AUDIO }

/** Lyric streams (synced `timed` LRC or plain text) of [firstPart]; M2's lyrics() fetches `key`. */
val PlexMetadata.lyricStreams: List<PlexStream>
    get() = firstPart?.streams.orEmpty().filter { it.streamType == PlexTypes.STREAM_LYRICS }
