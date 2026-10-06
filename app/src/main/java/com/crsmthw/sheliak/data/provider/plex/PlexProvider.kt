package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.IndexSink
import com.crsmthw.sheliak.data.provider.MusicProvider
import com.crsmthw.sheliak.data.provider.PlaybackPrefs
import com.crsmthw.sheliak.data.provider.PlaybackReporter
import com.crsmthw.sheliak.data.provider.PlaybackSource
import com.crsmthw.sheliak.data.provider.ProviderCapabilities
import com.crsmthw.sheliak.data.provider.ProviderDeps
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.provider.ProviderFactory
import com.crsmthw.sheliak.data.provider.ProviderInstance
import com.crsmthw.sheliak.data.provider.SyncCursor
import com.crsmthw.sheliak.data.provider.SyncProgress
import com.crsmthw.sheliak.data.repository.resultOf
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** A music library (a `/library/sections` entry of type "artist") the user can pick. */
@Serializable
data class PlexLibrary(val key: String, val title: String)

/**
 * What a Plex instance remembers about itself (`ProviderInstance.config`, opaque to the rest of the app): the
 * picked [libraries], whether the user [owned] the server (local connections are skipped otherwise), and a
 * snapshot of its [connections] from setup, so the provider can connect without asking plex.tv first. The
 * connection actually used is picked at run time and kept in memory only; the snapshot is refreshed in memory
 * from `/resources` when none of it answers. Secrets are never in here (CredentialStore).
 */
@Serializable
data class PlexInstanceConfig(
    @SerialName("v") val version: Int = VERSION,
    val libraries: List<PlexLibrary> = emptyList(),
    val owned: Boolean = true,
    val connections: List<PlexConnectionInfo> = emptyList(),
) {
    val libraryKeys: List<String> get() = libraries.map { it.key }

    fun encode(): String = PlexStorageJson.json.encodeToString(serializer(), this)

    companion object {
        const val VERSION: Int = 1

        /** The config in [value], or null when absent or unreadable. */
        fun decode(value: String?): PlexInstanceConfig? {
            if (value.isNullOrBlank()) return null
            return try {
                PlexStorageJson.json.decodeFromString(serializer(), value)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * One Plex Media Server as a [MusicProvider] (instance id `plex:<machineIdentifier>`). Sync: [PlexSync];
 * playback: [PlexServer.resolvePlayback]; art: [PlexArt]; play reporting: [PlexReporter]. Built by
 * [PlexProviderFactory] — cheaply: nothing here touches the network or the credential store until first used.
 */
class PlexProvider internal constructor(
    override val instance: ProviderInstance,
    private val deps: ProviderDeps,
    private val clock: () -> Long = System::currentTimeMillis,
) : MusicProvider {

    private val config: PlexInstanceConfig? = PlexInstanceConfig.decode(instance.config)

    private val server = PlexServer(instance, config, deps)

    override val capabilities: ProviderCapabilities = CAPABILITIES

    override val reporter: PlaybackReporter = PlexReporter(server)

    override suspend fun sync(cursor: SyncCursor?, sink: IndexSink, progress: (SyncProgress) -> Unit): Result<SyncCursor> =
        resultOf {
            val cfg = config ?: throw ProviderException(ProviderError.UNAVAILABLE, "Plex source without a configuration")
            val sync = PlexSync(
                source = server,
                stored = { ratingKey -> deps.index.track(TrackKey(instance.id, ratingKey)) },
                clock  = clock,
            )
            val next = sync.run(PlexSyncCursor.decode(cursor?.value), cfg.libraryKeys, sink, progress)
            SyncCursor(next.encode())
        }

    override suspend fun resolvePlayback(track: TrackKey, prefs: PlaybackPrefs): Result<PlaybackSource> =
        resultOf { server.resolvePlayback(track, prefs) }

    override fun artModel(ref: ArtRef, sizePx: Int): Any? = server.artModel(ref.path, sizePx)

    override suspend fun onRemove() = server.forget()

    companion object {
        /** `providers.type` of every Plex row. */
        const val TYPE: String = "plex"
        const val ID_PREFIX: String = "plex:"

        fun instanceId(machineIdentifier: String): String = ID_PREFIX + machineIdentifier

        /**
         * M1: read-only playlists (editing is M2), no favourite mirror, lyrics not served yet (M2), no scan
         * trigger, no downloads (M2) — but the transcoder is used.
         */
        val CAPABILITIES = ProviderCapabilities(
            editPlaylists   = false,
            favourites      = false,
            servesLyrics    = false,
            transcode       = true,
            scan            = false,
            offlineOriginal = false,
        )
    }
}

/** Registered in `AppContainer.providerFactories`: builds a [PlexProvider] for every `plex` row. */
class PlexProviderFactory : ProviderFactory {
    override val type: String = PlexProvider.TYPE

    override fun create(instance: ProviderInstance, deps: ProviderDeps): MusicProvider = PlexProvider(instance, deps)
}
