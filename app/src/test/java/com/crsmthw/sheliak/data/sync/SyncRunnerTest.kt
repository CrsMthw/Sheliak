package com.crsmthw.sheliak.data.sync

import com.crsmthw.sheliak.data.FakeIndexDao
import com.crsmthw.sheliak.data.FakeProvider
import com.crsmthw.sheliak.data.FakeProviderDao
import com.crsmthw.sheliak.data.instanceOf
import com.crsmthw.sheliak.data.provider.IndexKind
import com.crsmthw.sheliak.data.provider.IndexTrack
import com.crsmthw.sheliak.data.provider.MusicProvider
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.provider.SyncCursor
import com.crsmthw.sheliak.data.provider.SyncProgress
import com.crsmthw.sheliak.data.providerRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncRunnerTest {

    private val id = "plex:aaa"
    private val providerDao = FakeProviderDao(providerRow(id, cursor = "c0"))
    private val indexDao = FakeIndexDao()
    private val states = SyncStateStore()
    private var now = 1_000L

    private fun runner(provider: MusicProvider?) =
        SyncRunner(providerDao, indexDao, providers = { provider }, states = states, clock = { now++ })

    private fun track(itemId: String) = IndexTrack(itemId = itemId, title = itemId, artistName = "A")

    @Test
    fun `a successful sync commits the sink and stores the new cursor`() = runTest {
        val provider = FakeProvider(instanceOf(id)) { _, sink, _ ->
            sink.putTracks(listOf(track("1"), track("2")))
            Result.success(SyncCursor("c1"))
        }
        assertEquals(SyncOutcome.Synced, runner(provider).run(id))
        assertEquals(SyncCursor("c0"), provider.lastCursor)
        assertEquals(2, indexDao.tracks.size)
        val row = providerDao.get(id)!!
        assertEquals("c1", row.syncCursor)
        assertTrue(row.lastSyncAt != null)
        assertEquals(SyncState.Idle, states.state(id).first())
    }

    @Test
    fun `a failed sync stores no cursor, deletes nothing and reports the error key`() = runTest {
        runner(FakeProvider(instanceOf(id)) { _, sink, _ ->
            sink.putTracks(listOf(track("1"), track("2")))
            Result.success(SyncCursor("c1"))
        }).run(id)

        val failing = FakeProvider(instanceOf(id)) { _, sink, _ ->
            sink.putTracks(listOf(track("1")))
            sink.markComplete(IndexKind.TRACKS)
            Result.failure(IOException("timeout"))
        }
        assertEquals(SyncOutcome.Failed(ProviderError.NETWORK), runner(failing).run(id))
        assertEquals("c1", providerDao.get(id)!!.syncCursor)
        assertEquals(2, indexDao.tracks.size)
        assertEquals(SyncState.Failed(ProviderError.NETWORK), states.state(id).first())
    }

    @Test
    fun `a provider exception keeps its own error key`() = runTest {
        val provider = FakeProvider(instanceOf(id)) { _, _, _ ->
            Result.failure(ProviderException(ProviderError.AUTH))
        }
        assertEquals(SyncOutcome.Failed(ProviderError.AUTH), runner(provider).run(id))
    }

    @Test
    fun `a provider that throws instead of returning a failure is still a failure`() = runTest {
        val provider = FakeProvider(instanceOf(id)) { _, _, _ -> throw IOException("reset") }
        assertEquals(SyncOutcome.Failed(ProviderError.NETWORK), runner(provider).run(id))
        assertEquals(SyncState.Failed(ProviderError.NETWORK), states.state(id).first())
    }

    @Test
    fun `progress is published while the run is in flight`() = runTest {
        val seen = mutableListOf<SyncState>()
        val provider = FakeProvider(instanceOf(id)) { _, _, progress ->
            progress(SyncProgress(10, 100))
            seen += states.state(id).first()
            Result.success(SyncCursor("c1"))
        }
        runner(provider).run(id)
        assertEquals(listOf<SyncState>(SyncState.Running(10, 100)), seen)
    }

    @Test
    fun `a second run of the same source is refused while one is in flight`() = runTest {
        val provider = FakeProvider(instanceOf(id))
        assertTrue(states.tryStart(id))
        assertEquals(SyncOutcome.AlreadyRunning, runner(provider).run(id))
        assertEquals(0, provider.syncCalls)
    }

    @Test
    fun `a removed source is gone, not failed`() = runTest {
        val provider = FakeProvider(instanceOf("plex:zzz"))
        assertEquals(SyncOutcome.Gone, runner(provider).run("plex:zzz"))
        assertNull(states.states.value["plex:zzz"])
    }

    @Test
    fun `a source without a provider is unavailable`() = runTest {
        assertEquals(SyncOutcome.Failed(ProviderError.UNAVAILABLE), runner(null).run(id))
    }

    @Test
    fun `cancellation is rethrown, resets the state and commits nothing`() = runTest {
        val provider = FakeProvider(instanceOf(id)) { _, sink, _ ->
            sink.putTracks(listOf(track("1")))
            throw CancellationException("stopped")
        }
        assertFailsWith<CancellationException> { runner(provider).run(id) }
        assertEquals(SyncState.Idle, states.state(id).first())
        assertEquals(0, indexDao.tracks.size)
        assertEquals("c0", providerDao.get(id)!!.syncCursor)
    }

    @Test
    fun `a cancellation returned as a failure is rethrown too`() = runTest {
        val provider = FakeProvider(instanceOf(id)) { _, _, _ -> Result.failure(CancellationException("stopped")) }
        assertFailsWith<CancellationException> { runner(provider).run(id) }
        assertEquals(SyncState.Idle, states.state(id).first())
    }
}
