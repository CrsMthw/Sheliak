package com.crsmthw.sheliak.data.provider

import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One source of music behind the index — a provider INSTANCE (one Plex server, one Jellyfin server, the device).
 * The app core (index, player, UI) talks to sources only through this interface and never imports a provider's
 * HTTP models. Built by its [ProviderFactory] from the `providers` row; see `docs/DESIGN.md` §1 and
 * `docs/INDEX.md`.
 *
 * Members after [artModel] have defaults ("never changes" for [artVersion], "not supported" for the later
 * milestones' ones), so an M1 provider implements only the first five.
 */
interface MusicProvider {

    /** Exactly the instance the factory was given. */
    val instance: ProviderInstance

    val capabilities: ProviderCapabilities

    /**
     * A full ([cursor] null) or incremental sync into the index through [sink], reporting [progress] as it goes.
     * Returns the cursor the NEXT sync should start from; it is persisted only if this call succeeds (and the
     * sink's writes committed), so a failed or cancelled sync simply runs again from the old cursor. Must be
     * cancellable (WorkManager stops a job that runs too long) and must not catch `CancellationException`.
     * Failures should be a [ProviderException] where the cause is known (auth, server, …); an `IOException`
     * reads as a network failure.
     */
    suspend fun sync(cursor: SyncCursor?, sink: IndexSink, progress: (SyncProgress) -> Unit): Result<SyncCursor>

    /** What ExoPlayer should open for [track]: a direct URI with headers, or a transcode URI. */
    suspend fun resolvePlayback(track: TrackKey, prefs: PlaybackPrefs): Result<PlaybackSource>

    /**
     * A Coil model (String / Uri / File / ByteArray / an ImageRequest-ready object) for [ref] at about [sizePx]
     * on its longer side, carrying whatever auth the provider needs; null → placeholder art.
     */
    fun artModel(ref: ArtRef, sizePx: Int): Any?

    /**
     * Moves (only ever up) whenever [artModel] may now answer differently for the same [ArtRef] — a provider
     * that builds art URLs on a connection it picks at run time bumps it when the pick lands or changes, and
     * when the credential its URLs carry arrives or changes. The app root re-creates its art resolver on every
     * move, so every visible cover is resolved again (and a load that failed is retried) without the user
     * switching screens. The default never moves ([NoArtVersion]).
     */
    val artVersion: StateFlow<Int> get() = NoArtVersion.flow

    /** Lyrics from the server (M2). `success(null)` = definitely none; failure = could not ask. */
    suspend fun lyrics(track: TrackKey): Result<Lyrics?> = Result.success(null)

    /** A server-side search; null = "use the local index only" (the default, and M1's only path). */
    suspend fun search(query: String): Result<SearchHits>? = null

    /** Play reporting (Plex timeline + scrobble); null when the source has nothing to report to. */
    val reporter: PlaybackReporter? get() = null

    /** Server playlist editing (M2); null → this provider's playlists are read-only. */
    val playlists: PlaylistEditor? get() = null

    /** Mirror of the heart to the server (roadmap); null → likes stay local. */
    val favourites: FavouriteSync? get() = null

    /**
     * The source is being removed: delete its credentials and stop anything it runs. Called before its index
     * rows and its `providers` row are deleted; a failure here does not stop the removal.
     */
    suspend fun onRemove() {}
}

/**
 * Builds the [MusicProvider] for one [ProviderInstance] of its [type] (`"plex"`, …). Registered in
 * `AppContainer` (or through `ProviderRegistry.register`); [create] runs again whenever the instance's row
 * changes, so a provider never has to watch its own row.
 */
interface ProviderFactory {
    val type: String
    fun create(instance: ProviderInstance, deps: ProviderDeps): MusicProvider
}

/** The [MusicProvider.artVersion] of a provider whose art never changes: one shared flow that stays at 0. */
object NoArtVersion {
    val flow: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
}
