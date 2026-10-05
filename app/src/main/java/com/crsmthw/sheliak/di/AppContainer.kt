package com.crsmthw.sheliak.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.crsmthw.sheliak.data.local.SheliakDataStore
import com.crsmthw.sheliak.data.repository.SettingsRepository
import okio.Path.Companion.toOkioPath

/**
 * The app's object graph, built by hand: every app-scoped singleton is an eager `val`, created once in
 * `SheliakApplication.onCreate`, and ViewModels receive what they need through their factories. The graph is
 * small enough that a DI framework would cost more (a code generator, build time, lint surface) than it saves.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    // ── Local storage ────────────────────────────────────────────────────────
    val dataStore = SheliakDataStore(appContext)

    // ── Repositories ─────────────────────────────────────────────────────────
    val settingsRepository = SettingsRepository(dataStore)

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

    private companion object {
        const val IMAGE_CACHE_DIR             = "image_cache"
        const val IMAGE_DISK_CACHE_BYTES      = 500L * 1024 * 1024
        const val IMAGE_MEMORY_CACHE_FRACTION = 0.15
    }
}
