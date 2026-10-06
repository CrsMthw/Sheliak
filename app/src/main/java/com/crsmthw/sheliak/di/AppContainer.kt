package com.crsmthw.sheliak.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.crsmthw.sheliak.data.db.SheliakDatabase
import com.crsmthw.sheliak.data.local.SheliakDataStore
import com.crsmthw.sheliak.data.provider.CredentialStore
import com.crsmthw.sheliak.data.provider.ProviderDeps
import com.crsmthw.sheliak.data.provider.ProviderFactory
import com.crsmthw.sheliak.data.provider.ProviderRegistry
import com.crsmthw.sheliak.data.provider.RoomIndexReader
import com.crsmthw.sheliak.data.repository.HistoryRepository
import com.crsmthw.sheliak.data.repository.LibraryRepository
import com.crsmthw.sheliak.data.repository.PlaylistRepository
import com.crsmthw.sheliak.data.repository.SettingsRepository
import com.crsmthw.sheliak.data.repository.SourcesRepository
import com.crsmthw.sheliak.data.sync.SyncRunner
import com.crsmthw.sheliak.data.sync.SyncScheduler
import com.crsmthw.sheliak.data.sync.SyncStateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * The app's object graph, built by hand: every app-scoped singleton is an eager `val`, created once in
 * `SheliakApplication.onCreate`, and ViewModels receive what they need through their factories. The graph is
 * small enough that a DI framework would cost more (a code generator, build time, lint surface) than it saves.
 *
 * Nothing here does I/O on the constructing (main) thread: Room opens the database on its first query, the
 * credential key is created on first use, WorkManager is reached from [appScope].
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Lives as long as the process; for app-wide work that outlives any screen (registry, scheduling). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Local storage ────────────────────────────────────────────────────────
    val dataStore = SheliakDataStore(appContext)

    /** The merged index (`sheliak.db`). */
    val database: SheliakDatabase = SheliakDatabase.build(appContext)

    /** Providers' secrets, AES-256-GCM under an AndroidKeyStore key. */
    val credentialStore = CredentialStore(appContext)

    // ── Networking ───────────────────────────────────────────────────────────
    /** The base client every provider and the player derive from (`newBuilder()`), sharing one connection pool. */
    val httpClient: OkHttpClient = OkHttpClient.Builder().build()

    /** For provider DTOs: unknown keys ignored, absent nulls allowed, bad enum values coerced to defaults. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    // ── Providers + sync ─────────────────────────────────────────────────────
    /**
     * One factory per provider type. Lane L2 adds `PlexProviderFactory()` here (or calls
     * `providerRegistry.register` once) — the registry then builds a provider for every `plex` row.
     */
    private val providerFactories: List<ProviderFactory> = emptyList()

    val providerRegistry = ProviderRegistry(
        providerDao = database.providerDao(),
        factories   = providerFactories,
        deps        = ProviderDeps(
            context     = appContext,
            credentials = credentialStore,
            httpClient  = httpClient,
            json        = json,
            index       = RoomIndexReader(database.libraryDao()),
        ),
        scope       = appScope,
    )

    val syncStateStore = SyncStateStore()

    val syncScheduler = SyncScheduler(appContext)

    /** What `SyncWorker` runs. */
    val syncRunner = SyncRunner(
        providerDao = database.providerDao(),
        indexDao    = database.indexDao(),
        providers   = providerRegistry::provider,
        states      = syncStateStore,
    )

    // ── Repositories ─────────────────────────────────────────────────────────
    val settingsRepository = SettingsRepository(dataStore)

    val playlistRepository = PlaylistRepository(database.playlistDao())

    val libraryRepository = LibraryRepository(
        dao                = database.libraryDao(),
        playlistRepository = playlistRepository,
        mergeDuplicates    = settingsRepository.mergeDuplicates,
    )

    val historyRepository = HistoryRepository(database.historyDao())

    val sourcesRepository = SourcesRepository(
        providerDao = database.providerDao(),
        indexDao    = database.indexDao(),
        registry    = providerRegistry,
        scheduler   = syncScheduler,
        syncStates  = syncStateStore,
    )

    // ── Image loader ─────────────────────────────────────────────────────────
    /**
     * The one Coil loader (`SheliakApplication` hands it to every `AsyncImage`).
     *
     * The disk cache lives in `filesDir`, not `cacheDir`: the system clears `cacheDir` under storage pressure,
     * and every cover would then be fetched from its server again — slow on a remote server, impossible while
     * it is offline. 500 MB holds the covers of a large library without evicting as the user browses.
     */
    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(appContext, IMAGE_MEMORY_CACHE_FRACTION)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(appContext.filesDir.resolve(IMAGE_CACHE_DIR).toOkioPath())
                .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                .build()
        }
        .build()

    // Last: everything above is initialised before the graph is published and its startup work begins.
    init {
        appScope.launch { sourcesRepository.schedulePeriodicSyncs() }
        ready.complete(this)
    }

    companion object {
        private const val IMAGE_CACHE_DIR             = "image_cache"
        private const val IMAGE_DISK_CACHE_BYTES      = 500L * 1024 * 1024
        private const val IMAGE_MEMORY_CACHE_FRACTION = 0.15

        private val ready = CompletableDeferred<AppContainer>()

        /**
         * The graph, once `SheliakApplication.onCreate` has built it. For code the system starts on its own
         * threads — a WorkManager worker in a process WorkManager just launched can begin before
         * `Application.onCreate` returns — so it waits instead of reading an uninitialised `container`.
         */
        suspend fun await(): AppContainer = ready.await()
    }
}
