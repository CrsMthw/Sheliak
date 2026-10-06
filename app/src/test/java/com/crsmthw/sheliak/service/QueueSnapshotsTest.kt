package com.crsmthw.sheliak.service

import com.crsmthw.sheliak.data.db.entity.QueueEntryEntity
import com.crsmthw.sheliak.data.db.entity.QueueStateEntity
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.TrackKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueSnapshotsTest {

    private val a = TrackKey("plex:s", "1")
    private val b = TrackKey("plex:s", "2|x")
    private val c = TrackKey("local", "content://media/external/audio/media/3")
    private val d = TrackKey("plex:t", "4")

    private val snapshot = QueueSnapshot(
        keys         = listOf(a, b, c, a),
        currentIndex = 2,
        positionMs   = 61_000,
        shuffle      = true,
        repeat       = RepeatMode.ALL,
        savedAt      = 1_700_000_000_000,
    )

    @Test
    fun `a snapshot round-trips through its rows`() {
        val rows = QueueSnapshots.entries(snapshot.keys)
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.position })
        assertEquals(snapshot, QueueSnapshots.restore(rows, QueueSnapshots.state(snapshot)))
    }

    @Test
    fun `rows are read back in position order whatever order they come in`() {
        val rows = QueueSnapshots.entries(snapshot.keys).reversed()
        assertEquals(snapshot.keys, QueueSnapshots.restore(rows, QueueSnapshots.state(snapshot))?.keys)
    }

    @Test
    fun `the state row is the single row with clamped values`() {
        val state = QueueSnapshots.state(snapshot.copy(currentIndex = 9, positionMs = -5))
        assertEquals(QueueStateEntity.SINGLE_ROW_ID, state.id)
        assertEquals(3, state.currentPosition)
        assertEquals(0, state.positionMs)
        assertEquals(RepeatMode.ALL, state.repeatMode)
    }

    @Test
    fun `nothing persisted restores nothing`() {
        assertNull(QueueSnapshots.restore(emptyList(), null))
        assertNull(QueueSnapshots.restore(listOf(QueueEntryEntity(0, "p", "i")), null))
        assertNull(QueueSnapshots.restore(emptyList(), QueueSnapshots.state(snapshot)))
    }

    @Test
    fun `realign keeps the current track and its position when tracks before it are gone`() {
        val keys = listOf(a, b, c, d)
        assertEquals(1 to 61_000L, QueueSnapshots.realign(keys, 2, 61_000, kept = setOf(a, c, d)))
    }

    @Test
    fun `realign moves to the next kept track from the start when the current one is gone`() {
        val keys = listOf(a, b, c, d)
        assertEquals(1 to 0L, QueueSnapshots.realign(keys, 1, 61_000, kept = setOf(a, c, d)))
        // The current one was last and is gone: the last kept track.
        assertEquals(1 to 0L, QueueSnapshots.realign(keys, 3, 5_000, kept = setOf(a, b)))
    }

    @Test
    fun `realign with nothing kept is nothing to restore`() {
        assertNull(QueueSnapshots.realign(listOf(a, b), 0, 0, kept = emptySet()))
    }

    @Test
    fun `realign counts a duplicate track each time it appears`() {
        val keys = listOf(a, b, a, c)
        assertEquals(2 to 10L, QueueSnapshots.realign(keys, 3, 10, kept = setOf(a, c)))
    }
}
