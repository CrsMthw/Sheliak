package com.crsmthw.sheliak.data.provider

import com.crsmthw.sheliak.data.FakeProvider
import com.crsmthw.sheliak.data.instanceOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProviderRegistryTest {

    private val built = mutableListOf<String>()
    private val create: (ProviderInstance) -> MusicProvider? = { instance ->
        if (instance.type == "unknown") null else FakeProvider(instance).also { built += instance.id }
    }

    @Test
    fun `new instances are built, in order`() {
        val result = reconcileProviders(emptyMap(), listOf(instanceOf("plex:a"), instanceOf("plex:b")), create)
        assertEquals(listOf("plex:a", "plex:b"), result.keys.toList())
        assertEquals(listOf("plex:a", "plex:b"), built)
    }

    @Test
    fun `an unchanged instance keeps its provider`() {
        val first = reconcileProviders(emptyMap(), listOf(instanceOf("plex:a")), create)
        val second = reconcileProviders(first, listOf(instanceOf("plex:a")), create)
        assertSame(first.getValue("plex:a").provider, second.getValue("plex:a").provider)
        assertEquals(1, built.size)
    }

    @Test
    fun `a changed instance is rebuilt`() {
        val first = reconcileProviders(emptyMap(), listOf(instanceOf("plex:a")), create)
        val changed = instanceOf("plex:a").copy(config = """{"sections":["1"]}""")
        val second = reconcileProviders(first, listOf(changed), create)
        assertNotSame(first.getValue("plex:a").provider, second.getValue("plex:a").provider)
        assertEquals(changed, second.getValue("plex:a").instance)
    }

    @Test
    fun `removed instances are dropped and unbuildable ones skipped`() {
        val first = reconcileProviders(emptyMap(), listOf(instanceOf("plex:a"), instanceOf("plex:b")), create)
        val second = reconcileProviders(first, listOf(instanceOf("plex:b"), instanceOf("unknown:c")), create)
        assertEquals(listOf("plex:b"), second.keys.toList())
        assertTrue("unknown:c" !in built)
    }
}
