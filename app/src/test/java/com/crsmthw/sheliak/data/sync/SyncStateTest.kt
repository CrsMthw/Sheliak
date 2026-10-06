package com.crsmthw.sheliak.data.sync

import com.crsmthw.sheliak.data.provider.ProviderError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncStateTest {

    private val running = SyncState.Running(5, 10)
    private val failed = SyncState.Failed(ProviderError.AUTH)

    @Test
    fun `a start runs from idle or after a failure`() {
        assertEquals(SyncState.Running(0, null), SyncState.Idle.on(SyncEvent.Started))
        assertEquals(SyncState.Running(0, null), failed.on(SyncEvent.Started))
    }

    @Test
    fun `a start while running is rejected`() {
        assertNull(running.on(SyncEvent.Started))
    }

    @Test
    fun `progress updates a running sync`() {
        assertEquals(SyncState.Running(7, null), running.on(SyncEvent.Progress(7, null)))
    }

    @Test
    fun `a run's own events are ignored when no run is in flight`() {
        listOf(SyncState.Idle, failed).forEach { state ->
            assertEquals(state, state.on(SyncEvent.Progress(1, 2)))
            assertEquals(state, state.on(SyncEvent.Succeeded))
            assertEquals(state, state.on(SyncEvent.Failed(ProviderError.NETWORK)))
            assertEquals(state, state.on(SyncEvent.Cancelled))
        }
    }

    @Test
    fun `a run ends idle on success or cancellation and failed on failure`() {
        assertEquals(SyncState.Idle, running.on(SyncEvent.Succeeded))
        assertEquals(SyncState.Idle, running.on(SyncEvent.Cancelled))
        assertEquals(SyncState.Failed(ProviderError.SERVER), running.on(SyncEvent.Failed(ProviderError.SERVER)))
    }

    @Test
    fun `the store locks per provider`() = runTest {
        val store = SyncStateStore()
        assertTrue(store.tryStart("a"))
        assertFalse(store.tryStart("a"))
        assertTrue(store.tryStart("b"))
        store.progress("a", 3, 9)
        assertEquals(SyncState.Running(3, 9), store.state("a").first())
        store.succeeded("a")
        assertTrue(store.tryStart("a"))
    }

    @Test
    fun `sources the store never saw are idle, and a cleared source is forgotten`() = runTest {
        val store = SyncStateStore()
        assertEquals(SyncState.Idle, store.state("x").first())
        store.tryStart("x")
        store.failed("x", ProviderError.NETWORK)
        assertEquals(SyncState.Failed(ProviderError.NETWORK), store.state("x").first())
        store.clear("x")
        assertFalse("x" in store.states.value)
    }

    @Test
    fun `the tail of a run whose source was cleared does not bring it back`() = runTest {
        val store = SyncStateStore()
        store.tryStart("x")
        store.clear("x")
        store.progress("x", 1, 2)
        store.succeeded("x")
        store.cancelled("x")
        assertFalse("x" in store.states.value)
    }
}
