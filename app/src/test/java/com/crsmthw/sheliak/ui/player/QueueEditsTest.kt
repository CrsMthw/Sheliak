package com.crsmthw.sheliak.ui.player

import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueueEditsTest {

    private fun track(id: String) = Track(
        key          = TrackKey("plex:abc", id),
        title        = "Track $id",
        titleSort    = "track $id",
        artistName   = "Artist",
        artistKey    = null,
        albumTitle   = null,
        albumKey     = null,
        discNo       = null,
        trackNo      = null,
        durationMs   = 180_000L,
        year         = null,
        format       = null,
        art          = null,
        addedAt      = 0L,
        playCount    = 0,
        lastPlayedAt = null,
    )

    private fun ids(vararg id: String) = id.map { "plex:abc|$it" }

    // ── queueRows ───────────────────────────────────────────────────────────

    @Test
    fun `row keys are the media id and its occurrence`() {
        val rows = queueRows(listOf(track("1"), track("2")))
        assertEquals(listOf("plex:abc|1#0", "plex:abc|2#0"), rows.map { it.key })
    }

    @Test
    fun `a track queued twice gets two distinct keys`() {
        val rows = queueRows(listOf(track("1"), track("2"), track("1")))
        assertEquals(listOf("plex:abc|1#0", "plex:abc|2#0", "plex:abc|1#1"), rows.map { it.key })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `an empty queue has no rows`() {
        assertEquals(emptyList<QueueRow>(), queueRows(emptyList()))
    }

    // ── moved / removedAt ───────────────────────────────────────────────────

    @Test
    fun `moving down matches Media3's moveMediaItem`() {
        assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").moved(0, 2))
    }

    @Test
    fun `moving up matches Media3's moveMediaItem`() {
        assertEquals(listOf("d", "a", "b", "c"), listOf("a", "b", "c", "d").moved(3, 0))
        assertEquals(listOf("a", "c", "b", "d"), listOf("a", "b", "c", "d").moved(2, 1))
    }

    @Test
    fun `a move out of range or onto itself changes nothing`() {
        val list = listOf("a", "b", "c")
        assertSame(list, list.moved(1, 1))
        assertSame(list, list.moved(-1, 2))
        assertSame(list, list.moved(0, 3))
    }

    @Test
    fun `adjacent swaps compose into one move`() {
        // A drag from row 0 to row 3 swaps 0↔1, 1↔2, 2↔3 on screen; the player gets ONE move(0, 3).
        val start = listOf("a", "b", "c", "d", "e")
        val stepwise = start.moved(0, 1).moved(1, 2).moved(2, 3)
        assertEquals(start.moved(0, 3), stepwise)
    }

    @Test
    fun `removing drops exactly that row`() {
        assertEquals(listOf("a", "c"), listOf("a", "b", "c").removedAt(1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").removedAt(5))
    }

    // ── QueueEdit.reconcile ─────────────────────────────────────────────────

    private fun moveEdit(): QueueEdit {
        val rows = queueRows(listOf(track("1"), track("2"), track("3")))
        return QueueEdit(base = ids("1", "2", "3"), rows = rows.moved(0, 2), shuffle = true, maskingAllowance = 0)
    }

    /**
     * The timeline is 1, 2, 3; with shuffle on the play order is 3, 2, 1. Removing the first row (track 3) expects
     * 2, 1 — while the controller's unshuffled masking queue would read 1, 2.
     */
    private fun removeEdit(shuffle: Boolean): QueueEdit {
        val rows = queueRows(listOf(track("3"), track("2"), track("1")))
        return QueueEdit(
            base             = ids("3", "2", "1"),
            rows             = rows.removedAt(0),
            shuffle          = shuffle,
            maskingAllowance = if (shuffle) 1 else 0,
        )
    }

    @Test
    fun `the edit expects its own rows`() {
        assertEquals(ids("2", "3", "1"), moveEdit().expected)
    }

    @Test
    fun `while the player still shows the old queue the edit stays`() {
        val edit = moveEdit()
        assertSame(edit, edit.reconcile(ids("1", "2", "3"), shuffle = true))
    }

    @Test
    fun `once the player shows the edit, the screen follows the player`() {
        assertNull(moveEdit().reconcile(ids("2", "3", "1"), shuffle = true))
    }

    @Test
    fun `a removal with shuffle on sits through one masking queue`() {
        val edit = removeEdit(shuffle = true)
        // The controller's masking timeline: the same tracks, unshuffled.
        val afterMasking = edit.reconcile(ids("1", "2"), shuffle = true)
        assertEquals(0, afterMasking?.maskingAllowance)
        // Then the real shuffled queue arrives and matches.
        assertNull(afterMasking?.reconcile(ids("2", "1"), shuffle = true))
    }

    @Test
    fun `a second reordered queue is followed, not held`() {
        val afterMasking = removeEdit(shuffle = true).reconcile(ids("1", "2"), shuffle = true)
        assertEquals(0, afterMasking?.maskingAllowance)
        assertNull(afterMasking?.reconcile(ids("1", "2"), shuffle = true))
    }

    @Test
    fun `a move never holds a reordered queue`() {
        assertNull(moveEdit().reconcile(ids("3", "1", "2"), shuffle = true))
    }

    @Test
    fun `a different queue is followed at once`() {
        assertNull(moveEdit().reconcile(ids("9"), shuffle = true))
        assertNull(removeEdit(shuffle = true).reconcile(ids("1", "2", "9"), shuffle = true))
    }

    @Test
    fun `a shuffle toggle drops the edit`() {
        assertNull(moveEdit().reconcile(ids("1", "2", "3"), shuffle = false))
    }

    @Test
    fun `a removal with shuffle off settles on the masked queue itself`() {
        assertNull(removeEdit(shuffle = false).reconcile(ids("2", "1"), shuffle = false))
    }
}
