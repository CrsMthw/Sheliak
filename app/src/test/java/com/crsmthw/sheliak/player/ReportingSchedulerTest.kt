package com.crsmthw.sheliak.player

import com.crsmthw.sheliak.data.provider.ReportedState
import com.crsmthw.sheliak.data.provider.ReportedState.BUFFERING
import com.crsmthw.sheliak.data.provider.ReportedState.PAUSED
import com.crsmthw.sheliak.data.provider.ReportedState.PLAYING
import com.crsmthw.sheliak.data.provider.ReportedState.STOPPED
import com.crsmthw.sheliak.domain.TrackKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportingSchedulerTest {

    private var now = 0L
    private val wallStart = 1_700_000_000_000L
    private val scheduler = ReportingScheduler(clock = { now }, wallClock = { wallStart + now })

    private val a = TrackKey("plex:s", "1")
    private val b = TrackKey("plex:s", "2")
    private val minutes3 = 180_000L

    private fun sample(key: TrackKey?, state: ReportedState?, position: Long, playId: Long = 1, duration: Long = minutes3) =
        PlaybackSample(key, playId, state, position, duration)

    private fun step(
        key: TrackKey?, state: ReportedState?, position: Long, playId: Long = 1, duration: Long = minutes3,
        metered: Boolean = false, seeked: Boolean = false,
    ) = scheduler.update(sample(key, state, position, playId, duration), metered, seeked)

    /** Plays [key] from [from] for [ms], waking whenever asked and once at the end; all actions in order. */
    private fun playFor(key: TrackKey, from: Long, ms: Long, playId: Long = 1, duration: Long = minutes3, metered: Boolean = false): List<ReportAction> {
        val actions = ArrayList<ReportAction>()
        val start = now
        var wake: Long? = start
        while (wake != null && wake <= start + ms) {
            now = wake
            val s = step(key, PLAYING, from + (now - start), playId, duration, metered)
            actions += s.actions
            wake = s.nextWakeAtMs
        }
        now = start + ms
        return actions
    }

    private fun timelines(actions: List<ReportAction>) = actions.filterIsInstance<ReportAction.Timeline>()

    @Test
    fun `a restored paused queue reports nothing`() {
        val s = step(a, STOPPED, 30_000)
        assertTrue(s.actions.isEmpty())
        assertNull(s.nextWakeAtMs)
        assertTrue(step(a, PAUSED, 30_000).actions.isEmpty())
    }

    @Test
    fun `starting a track reports each state change`() {
        val buffering = step(a, BUFFERING, 0).actions
        assertEquals(listOf(ReportAction.Timeline(a, BUFFERING, 0, minutes3)), buffering)
        now = 400
        val playing = step(a, PLAYING, 0).actions
        assertEquals(listOf(ReportAction.Timeline(a, PLAYING, 0, minutes3)), playing)
        now = 5_000
        val paused = step(a, PAUSED, 4_600).actions
        assertEquals(listOf(ReportAction.Timeline(a, PAUSED, 4_600, minutes3)), paused)
    }

    @Test
    fun `while playing the timeline goes every 10 s`() {
        val actions = playFor(a, from = 0, ms = 60_000)
        val positions = timelines(actions).map { it.positionMs }
        assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L, 40_000L, 50_000L, 60_000L), positions)
        assertTrue(timelines(actions).all { it.state == PLAYING })
    }

    @Test
    fun `on a metered network the timeline goes every 20 s`() {
        val actions = playFor(a, from = 0, ms = 60_000, metered = true)
        assertEquals(listOf(0L, 20_000L, 40_000L, 60_000L), timelines(actions).map { it.positionMs })
    }

    @Test
    fun `nothing wakes while paused`() {
        step(a, PLAYING, 0)
        now = 3_000
        assertNull(step(a, PAUSED, 3_000).nextWakeAtMs)
    }

    @Test
    fun `a seek reports at once`() {
        step(a, PLAYING, 0)
        now = 2_000
        val s = step(a, PLAYING, 90_000, seeked = true)
        assertEquals(listOf(ReportAction.Timeline(a, PLAYING, 90_000, minutes3)), s.actions)
    }

    @Test
    fun `the play counts at half the track and scrobbles at 90 percent, once each`() {
        val actions = playFor(a, from = 0, ms = minutes3)
        val records = actions.filterIsInstance<ReportAction.RecordPlay>()
        assertEquals(listOf(ReportAction.RecordPlay(a, wallStart, 90_000)), records)
        assertEquals(listOf(ReportAction.Scrobble(a)), actions.filterIsInstance<ReportAction.Scrobble>())
        val scrobbleAt = actions.indexOfFirst { it is ReportAction.Scrobble }
        val before = timelines(actions.take(scrobbleAt)).last()
        assertTrue(before.positionMs <= 162_000)
    }

    @Test
    fun `the count is capped at four minutes of a long track`() {
        val tenMinutes = 600_000L
        val actions = playFor(a, from = 0, ms = 250_000, duration = tenMinutes)
        assertEquals(listOf(240_000L), actions.filterIsInstance<ReportAction.RecordPlay>().map { it.playedMs })
    }

    @Test
    fun `seeking never counts as listening`() {
        val actions = ArrayList<ReportAction>()
        actions += step(a, PLAYING, 0).actions
        now = 5_000
        actions += step(a, PLAYING, 170_000, seeked = true).actions // jump to 94 % after 5 s
        actions += step(b, BUFFERING, 0, playId = 2).actions
        assertTrue(actions.none { it is ReportAction.RecordPlay })
        // Reaching 90 % while playing scrobbles, however it was reached.
        assertEquals(1, actions.count { it is ReportAction.Scrobble })
    }

    @Test
    fun `seeking back below 90 percent and through it again does not scrobble twice`() {
        val first = playFor(a, from = 150_000, ms = 20_000)
        assertEquals(1, first.count { it is ReportAction.Scrobble })
        step(a, PLAYING, 100_000, seeked = true)
        val second = playFor(a, from = 100_000, ms = 70_000)
        assertEquals(0, second.count { it is ReportAction.Scrobble })
    }

    @Test
    fun `a transition stops the previous track and starts the next`() {
        step(a, PLAYING, 0)
        now = 30_000
        val actions = step(b, PLAYING, 0, playId = 2).actions
        assertEquals(
            listOf(
                ReportAction.Timeline(a, STOPPED, 30_000, minutes3),
                ReportAction.Timeline(b, PLAYING, 0, minutes3),
            ),
            actions,
        )
    }

    @Test
    fun `a track played to its end is settled when the next one starts`() {
        step(a, PLAYING, 170_000)        // 94 %: scrobbles now
        now = 10_000
        val actions = step(b, PLAYING, 0, playId = 2).actions
        assertEquals(ReportAction.Timeline(a, STOPPED, minutes3, minutes3), actions.first())
    }

    @Test
    fun `repeat one is a new play of the same track`() {
        val first = playFor(a, from = 0, ms = minutes3, playId = 1)
        assertEquals(1, first.count { it is ReportAction.RecordPlay })
        val second = playFor(a, from = 0, ms = minutes3, playId = 2)
        assertEquals(ReportAction.Timeline(a, STOPPED, minutes3, minutes3), second.first())
        assertEquals(1, second.count { it is ReportAction.RecordPlay })
        assertEquals(1, second.count { it is ReportAction.Scrobble })
    }

    @Test
    fun `no scrobble while the duration is unknown`() {
        val actions = playFor(a, from = 0, ms = 300_000, duration = 0)
        assertTrue(actions.none { it is ReportAction.Scrobble })
        assertEquals(1, actions.count { it is ReportAction.RecordPlay }) // four minutes of listening
    }

    @Test
    fun `the end of the queue stops the track`() {
        step(a, PLAYING, 0)
        now = 2_000
        val actions = step(a, STOPPED, 2_000).actions
        assertEquals(listOf(ReportAction.Timeline(a, STOPPED, 2_000, minutes3)), actions)
        assertNull(step(a, STOPPED, 2_000).nextWakeAtMs)
    }

    @Test
    fun `stop settles and closes the current play`() {
        step(a, PLAYING, 0)
        now = 100_000
        val actions = scheduler.stop()
        assertEquals(
            listOf(
                ReportAction.RecordPlay(a, wallStart, 100_000),
                ReportAction.Timeline(a, STOPPED, 100_000, minutes3),
            ),
            actions,
        )
        assertTrue(scheduler.stop().isEmpty())
    }

    @Test
    fun `the wake-up lands on the next due report`() {
        val s = step(a, PLAYING, 0)
        assertEquals(10_000L, s.nextWakeAtMs)
        val short = step(b, PLAYING, 0, playId = 2, duration = 8_000)
        // 8 s track: counts at 4 s, which comes before the 10 s timeline.
        assertEquals(now + 4_000, short.nextWakeAtMs)
    }
}
