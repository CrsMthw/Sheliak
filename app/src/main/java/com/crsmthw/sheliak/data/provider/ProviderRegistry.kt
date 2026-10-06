package com.crsmthw.sheliak.data.provider

import android.util.Log
import com.crsmthw.sheliak.data.db.dao.ProviderDao
import com.crsmthw.sheliak.data.db.toInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * The live [MusicProvider]s, one per `providers` row whose type has a registered [ProviderFactory]. Built at
 * startup from the table and kept in step with it: a row added builds its provider, a row changed REBUILDS it
 * (so a provider never watches its own row), a row removed drops it. A row whose type has no factory yet is
 * skipped until one is [register]ed.
 */
class ProviderRegistry(
    private val providerDao: ProviderDao,
    factories: List<ProviderFactory>,
    private val deps: ProviderDeps,
    scope: CoroutineScope,
) {

    private val factories = ConcurrentHashMap<String, ProviderFactory>().apply {
        factories.forEach { put(it.type, it) }
    }
    private val factoryVersion = MutableStateFlow(0)
    private val mutex = Mutex()

    /** Built providers by instance id, with the instance each was built from. Guarded by [mutex]. */
    @Volatile
    private var built: Map<String, BuiltProvider> = emptyMap()

    /** Every live provider, in Settings → Sources order. */
    val providers: StateFlow<List<MusicProvider>> =
        combine(providerDao.observeAll(), factoryVersion) { rows, _ -> rows.map { it.toInstance() } }
            .map { instances ->
                val next = mutex.withLock { reconcileProviders(built, instances, ::create).also { built = it } }
                next.values.map { it.provider }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Adds (or replaces) the factory for its type; rows of that type are (re)built. */
    fun register(factory: ProviderFactory) {
        factories[factory.type] = factory
        factoryVersion.value += 1
    }

    /**
     * The provider for [id], built from its row right now if the [providers] flow has not caught up yet (a sync
     * started by WorkManager in a fresh process, a source added a moment ago). Null when the row is gone or its
     * type has no factory.
     */
    suspend fun provider(id: String): MusicProvider? {
        val instance = providerDao.get(id)?.toInstance() ?: return null
        return mutex.withLock {
            built[id]?.takeIf { it.instance == instance }?.provider
                ?: create(instance)?.also { built = built + (id to BuiltProvider(instance, it)) }
        }
    }

    /** The provider for [id] as last built, without suspending (for the player's loader threads); may be null. */
    fun current(id: String): MusicProvider? = built[id]?.provider

    /** Null (logged) when the type has no factory or the factory throws: one bad source never stops the rest. */
    private fun create(instance: ProviderInstance): MusicProvider? {
        val factory = factories[instance.type]
        if (factory == null) {
            Log.w(TAG, "No provider factory for type ${instance.type}; ${instance.id} is skipped")
            return null
        }
        return try {
            factory.create(instance, deps)
        } catch (e: Exception) {
            Log.e(TAG, "Could not build ${instance.id}", e)
            null
        }
    }

    private companion object {
        const val TAG = "ProviderRegistry"
    }
}

/** A provider and the instance it was built from. */
data class BuiltProvider(val instance: ProviderInstance, val provider: MusicProvider)

/**
 * The providers for [instances], in their order: each one reused from [previous] when its instance is unchanged,
 * built with [create] when it is new or changed, left out when [create] cannot build it. Instances no longer
 * listed are dropped. Pure; unit-tested in ProviderRegistryTest.
 */
fun reconcileProviders(
    previous: Map<String, BuiltProvider>,
    instances: List<ProviderInstance>,
    create: (ProviderInstance) -> MusicProvider?,
): Map<String, BuiltProvider> {
    val next = LinkedHashMap<String, BuiltProvider>(instances.size * 2)
    instances.forEach { instance ->
        val reused = previous[instance.id]?.takeIf { it.instance == instance }
        val built = reused ?: create(instance)?.let { BuiltProvider(instance, it) }
        if (built != null) next[instance.id] = built
    }
    return next
}
