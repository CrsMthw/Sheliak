package com.crsmthw.sheliak.ui.screens.sources

import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SourceStatusTest {

    @Test
    fun `idle before any sync reads not synced yet`() {
        assertEquals(SourceStatus(SourceStatusKey.NEVER_SYNCED), sourceStatusOf(SyncState.Idle, lastSyncAt = null))
    }

    @Test
    fun `idle after a sync reads synced with its time`() {
        assertEquals(
            SourceStatus(SourceStatusKey.SYNCED, syncedAt = 1_700_000_000_000L),
            sourceStatusOf(SyncState.Idle, lastSyncAt = 1_700_000_000_000L),
        )
    }

    @Test
    fun `running with a total reads N of M`() {
        assertEquals(
            SourceStatus(SourceStatusKey.RUNNING_OF_TOTAL, done = 1_200, total = 9_000),
            sourceStatusOf(SyncState.Running(done = 1_200, total = 9_000), lastSyncAt = null),
        )
    }

    @Test
    fun `running without a total reads N`() {
        assertEquals(
            SourceStatus(SourceStatusKey.RUNNING_COUNT, done = 40),
            sourceStatusOf(SyncState.Running(done = 40, total = null), lastSyncAt = 5L),
        )
    }

    @Test
    fun `a zero total is no total`() {
        assertEquals(SourceStatusKey.RUNNING_COUNT, sourceStatusOf(SyncState.Running(0, 0), null).key)
    }

    @Test
    fun `a negative count never shows`() {
        assertEquals(0, sourceStatusOf(SyncState.Running(-3, null), null).done)
    }

    @Test
    fun `a failure carries its error, whatever the last sync`() {
        ProviderError.entries.forEach { error ->
            assertEquals(
                SourceStatus(SourceStatusKey.FAILED, error = error),
                sourceStatusOf(SyncState.Failed(error), lastSyncAt = 99L),
                "$error",
            )
        }
    }

    @Test
    fun `only the running keys are running`() {
        assertTrue(SourceStatus(SourceStatusKey.RUNNING_OF_TOTAL).isRunning())
        assertTrue(SourceStatus(SourceStatusKey.RUNNING_COUNT).isRunning())
        assertFalse(SourceStatus(SourceStatusKey.NEVER_SYNCED).isRunning())
        assertFalse(SourceStatus(SourceStatusKey.SYNCED).isRunning())
        assertFalse(SourceStatus(SourceStatusKey.FAILED).isRunning())
    }

    @Test
    fun `every provider error has its own words`() {
        val messages = ProviderError.entries.map(::providerErrorMessage)
        assertEquals(ProviderError.entries.size, messages.toSet().size)
        messages.forEach { assertNotEquals(0, it) }
    }
}
