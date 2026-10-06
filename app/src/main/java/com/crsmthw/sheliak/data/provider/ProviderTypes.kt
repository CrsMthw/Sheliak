package com.crsmthw.sheliak.data.provider

import android.content.Context
import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * A configured source, as stored in the `providers` table.
 *
 * @property id the instance id and the `provider_id` of every row it syncs: `<type>:<server id>` (`plex:<machineIdentifier>`),
 *   or the bare type for a singleton (`local`). Never contains `|` (see `TrackKey.mediaId`).
 * @property type the [ProviderFactory.type] that builds it.
 * @property displayName what Settings → Sources shows (the server's own name — user data, not app text).
 * @property baseUrl the connection last known to work, if the provider wants it persisted.
 * @property config the provider's own JSON (the libraries the user picked, say); opaque to everything else.
 * @property enabled false pauses its periodic sync; its rows stay in the library.
 */
data class ProviderInstance(
    val id: String,
    val type: String,
    val displayName: String,
    val baseUrl: String?,
    val serverId: String?,
    val config: String?,
    val enabled: Boolean,
)

/** What a provider can do, so the UI can hide what it cannot. */
data class ProviderCapabilities(
    val editPlaylists: Boolean,
    val favourites: Boolean,
    val servesLyrics: Boolean,
    val transcode: Boolean,
    val scan: Boolean,
    val offlineOriginal: Boolean,
)

/** What every provider is built with — app-scoped singletons from `AppContainer`. */
class ProviderDeps(
    val context: Context,
    val credentials: CredentialStore,
    /** The app's base client: derive with `newBuilder()` (interceptors, timeouts) so the connection pool is shared. */
    val httpClient: OkHttpClient,
    /** `ignoreUnknownKeys = true`, `explicitNulls = false`, `coerceInputValues = true`. */
    val json: Json,
    /** Reads back what this provider wrote into the index (e.g. a track's stream reference). */
    val index: IndexReader,
)

/**
 * Where a sync resumes. [value] is the provider's own encoding (JSON of whatever it needs: an `updatedAt`
 * watermark, per-library positions, when the last full sync ran); stored verbatim in `providers.sync_cursor`.
 */
data class SyncCursor(val value: String)

/** [done] items written so far; [total] when the provider knows it (a paged API's total size). */
data class SyncProgress(val done: Int, val total: Int?)

/**
 * The player's preferences for [MusicProvider.resolvePlayback].
 *
 * @property directPlayCodecs lower-case codec names (as [AudioFormatInfo.codec] spells them) the device decodes;
 *   anything else must be transcoded.
 * @property transcodeBitrateKbps the "transcode bit rate" setting, for a lossy transcode.
 * @property forceTranscode transcode even a decodable codec (a future "always transcode on mobile data" switch).
 */
data class PlaybackPrefs(
    val directPlayCodecs: Set<String>,
    val transcodeBitrateKbps: Int,
    val forceTranscode: Boolean = false,
)

/**
 * What ExoPlayer opens. [uri] is a String (not `android.net.Uri`) so providers and their tests stay plain JVM;
 * the player parses it. [format] is what will actually be decoded (the index's for a direct play, the
 * requested output for a transcode); null when unknown.
 */
data class PlaybackSource(
    val uri: String,
    val headers: Map<String, String>,
    val mimeType: String?,
    val format: AudioFormatInfo?,
    val isTranscode: Boolean,
)

/** Server lyrics (M2): LRC text when timed, else plain text. */
data class Lyrics(val synced: String?, val plain: String?)

/** A server-side search's hits, as index keys. */
data class SearchHits(val tracks: List<TrackKey>, val albums: List<TrackKey>, val artists: List<TrackKey>)

/** A server playlist's identity: (provider id, the server's playlist id). */
typealias PlaylistKey = TrackKey

/** Server playlist editing (M2), through the provider's API so other clients see the edits. */
interface PlaylistEditor {
    suspend fun create(title: String, tracks: List<TrackKey>): Result<PlaylistKey>
    suspend fun add(playlist: PlaylistKey, tracks: List<TrackKey>): Result<Unit>
    suspend fun remove(playlist: PlaylistKey, entryIds: List<String>): Result<Unit>
    suspend fun move(playlist: PlaylistKey, entryId: String, afterEntryId: String?): Result<Unit>
    suspend fun delete(playlist: PlaylistKey): Result<Unit>
}

/** The heart mirrored to the server (roadmap). */
interface FavouriteSync {
    suspend fun setFavourite(track: TrackKey, favourite: Boolean): Result<Unit>
}

/** The playback states a server is told about. */
enum class ReportedState { PLAYING, PAUSED, BUFFERING, STOPPED }

/**
 * Play reporting to the server (Plex `/:/timeline` and `/:/scrobble`). The playback service calls it; WHEN to
 * call (every 10 s, on state changes, at 90 %) is the service's rule, not the provider's.
 */
interface PlaybackReporter {
    suspend fun timeline(track: TrackKey, state: ReportedState, positionMs: Long, durationMs: Long): Result<Unit>
    suspend fun scrobble(track: TrackKey): Result<Unit>
}
