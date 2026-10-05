package com.crsmthw.sheliak

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.crsmthw.sheliak.di.AppContainer

/**
 * Process entry point: builds the [AppContainer] once, before any Activity exists, so every screen and
 * ViewModel factory reads the same singletons.
 *
 * It implements [SingletonImageLoader.Factory] so every `AsyncImage` in the app shares the container's one
 * Coil loader (one memory cache, one disk cache) instead of each call site building its own.
 */
class SheliakApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: Context): ImageLoader = container.imageLoader
}
