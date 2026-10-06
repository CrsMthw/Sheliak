package com.crsmthw.sheliak.data.provider.plex

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ONE process-wide watch on the default network, shared by every Plex server: providers are rebuilt whenever
 * their row changes and have no close hook, so a callback per provider would leak one registration per rebuild.
 * [generation] moves whenever the default network changes or is lost; each [PlexConnectionPicker] compares it
 * with the generation its pick was made under and re-probes on a mismatch (Cris: re-pick on network change).
 *
 * Uses ACCESS_NETWORK_STATE (declared in the merged manifest by WorkManager). If the callback cannot be
 * registered, picks are only redone after a failed call.
 */
internal object PlexNetworkMonitor {

    private val started = AtomicBoolean(false)
    private val generationCounter = AtomicLong(0)
    private val sawFirstNetwork = AtomicBoolean(false)

    @Volatile
    private var current: Network? = null

    /** True while the default network is cellular (the transcoder's `location=cellular`). */
    @Volatile
    var isCellular: Boolean = false
        private set

    val generation: Long get() = generationCounter.get()

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            connectivity.registerDefaultNetworkCallback(Callback())
        } catch (_: SecurityException) {
            // No monitoring: picks are redone after failed calls only.
        }
    }

    private class Callback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            current = network
            // The first callback only reports the network the app started on: nothing was picked on another.
            if (!sawFirstNetwork.compareAndSet(false, true)) generationCounter.incrementAndGet()
        }

        override fun onLost(network: Network) {
            if (current == network) {
                current = null
                isCellular = false
                generationCounter.incrementAndGet()
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (network == current) isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
    }
}

/**
 * Android 17's local-network protection (PLEX.md §11, correction 8): with target 37, TCP to private and
 * link-local addresses needs the runtime permission ACCESS_LOCAL_NETWORK, and a blocked connect times out
 * instead of failing. Setup asks for it (the UI lane); the picker skips LAN connections while it is missing.
 */
object PlexLocalNetwork {

    /** The permission to request on this device, or null below Android 17 (none needed there). */
    fun permission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) Manifest.permission.ACCESS_LOCAL_NETWORK else null

    /** True when LAN connections may be opened (the permission is granted, or not needed on this version). */
    fun granted(context: Context): Boolean {
        val permission = permission() ?: return true
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
