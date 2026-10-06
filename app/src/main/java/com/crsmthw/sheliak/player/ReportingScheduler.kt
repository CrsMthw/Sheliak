package com.crsmthw.sheliak.player

import com.crsmthw.sheliak.data.provider.ReportedState
import com.crsmthw.sheliak.domain.TrackKey

/** What the reporting hook must do, in order. */
sealed interface ReportAction {
    val key: TrackKey

    /** `provider.reporter?.timeline(...)`. */
    data class Timeline(
        override val key: TrackKey,
        val state: ReportedState,
        val positionMs: Long,
        val durationMs: Long,
    ) : ReportAction

    /** `provider.reporter?.scrobble(...)`: the play reached 90 %. */
    data class Scrobble(override val key: TrackKey) : ReportAction

    /** `historyRepository.recordPlay(...)`: the play counts (see [ReportingScheduler.countThresholdMs]). */
    data class RecordPlay(override val key: TrackKey, val playedAt: Long, val playedMs: Long) : ReportAction
}

/**
 * One look at the player, as the hook samples it on every player event and on every wake-up.
 *
 * @property key the current item; null when the queue is empty.
 * @property playId changes on every media item transition, including a repeat-one loop — a new play of the
 *   same track.
 * @property state null with no item; STOPPED when the player is idle or ended.
 * @property durationMs ≤ 0 when unknown (then nothing is scrobbled).
 */
data class PlaybackSample(
    val key: TrackKey?,
    val playId: Long,
    val state: ReportedState?,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * When to report a play, decided from samples and an injected clock — pure, so the cadence is unit-tested
 * (ReportingSchedulerTest) and the Android hook ([PlayReportingHook]) only samples, sends and sleeps.
 *
 * The rules (docs/PLAYER.md, "Play reporting"):
 * - A play starts when a track starts PLAYING or BUFFERING (a restored, paused queue reports nothing) and ends on
 *   a transition, on STOPPED (idle / ended), or on [stop]. A repeat-one loop is a new play.
 * - Timeline: on every state change, on every seek, and every [INTERVAL_MS] while playing ([METERED_INTERVAL_MS]
 *   on a metered network); STOPPED for the previous track when a play ends.
 * - Scrobble: once per play, when the position reaches [SCROBBLE_FRACTION] of a known duration while playing
 *   (seeking back below it and through it again does not scrobble twice).
 * - Counted play (history + play count): once per play, when the time actually spent playing — measured on the
 *   clock, so seeking never counts — reaches [countThresholdMs]: half the track, at most 4 minutes (the
 *   Last.fm rule). Recorded the moment it is reached, so Recently played updates while the track plays.
 *
 * Not thread-safe: the hook drives it from the main thread only.
 */
class ReportingScheduler(
    /** Monotonic milliseconds (`SystemClock.elapsedRealtime` on the device). */
    private val clock: () -> Long,
    /** Epoch milliseconds, for when a recorded play started. */
    private val wallClock: () -> Long,
) {

    /** The actions to run now, and when to sample again (clock time), or null while nothing is playing. */
    data class Step(val actions: List<ReportAction>, val nextWakeAtMs: Long?)

    private class Play(val key: TrackKey, val playId: Long, val startedAt: Long) {
        var state: ReportedState? = null
        var positionMs = 0L
        var durationMs = 0L
        var playedMs = 0L
        var lastTimelineAt = 0L
        var counted = false
        var scrobbled = false
    }

    private var play: Play? = null
    private var lastSampleAt = 0L

    fun update(sample: PlaybackSample, metered: Boolean, seeked: Boolean = false): Step {
        val now = clock()
        val actions = ArrayList<ReportAction>(3)
        advance(now)

        play?.let { p ->
            if (sample.key != p.key || sample.playId != p.playId) end(p, actions)
        }

        val key = sample.key
        val starts = sample.state == ReportedState.PLAYING || sample.state == ReportedState.BUFFERING
        if (play == null && key != null && starts) {
            play = Play(key, sample.playId, wallClock())
        }

        val interval = if (metered) METERED_INTERVAL_MS else INTERVAL_MS
        play?.let { p ->
            val state = sample.state ?: ReportedState.STOPPED
            val previous = p.state
            p.positionMs = sample.positionMs.coerceAtLeast(0)
            if (sample.durationMs > 0) p.durationMs = sample.durationMs
            p.state = state
            if (state == ReportedState.STOPPED) {
                // Ended or stopped: a track that played to its end scrobbles here if no wake-up caught 90 % first.
                settle(p, actions, scrobbleAllowed = previous == ReportedState.PLAYING)
                actions += timeline(p, ReportedState.STOPPED)
                play = null
            } else {
                val due = state != previous || seeked ||
                    (state == ReportedState.PLAYING && now - p.lastTimelineAt >= interval)
                if (due) {
                    actions += timeline(p, state)
                    p.lastTimelineAt = now
                }
                settle(p, actions, scrobbleAllowed = state == ReportedState.PLAYING)
            }
        }

        return Step(actions, play?.let { nextWake(it, now, interval) })
    }

    /** The player is going away: settle and close the current play. */
    fun stop(): List<ReportAction> {
        advance(clock())
        val actions = ArrayList<ReportAction>(3)
        play?.let { end(it, actions) }
        return actions
    }

    /** Credits the time since the last sample to the current play, if it was playing. */
    private fun advance(now: Long) {
        val elapsed = (now - lastSampleAt).coerceAtLeast(0)
        lastSampleAt = now
        val p = play ?: return
        if (p.state == ReportedState.PLAYING) {
            p.playedMs += elapsed
            p.positionMs += elapsed
        }
    }

    /** A play ends while it may still be mid-track: settle it on its estimated position, then STOPPED. */
    private fun end(p: Play, actions: MutableList<ReportAction>) {
        val wasPlaying = p.state == ReportedState.PLAYING
        if (p.durationMs > 0) p.positionMs = p.positionMs.coerceAtMost(p.durationMs)
        settle(p, actions, scrobbleAllowed = wasPlaying)
        if (p.state != ReportedState.STOPPED) actions += timeline(p, ReportedState.STOPPED)
        play = null
    }

    private fun settle(p: Play, actions: MutableList<ReportAction>, scrobbleAllowed: Boolean) {
        if (!p.counted && p.playedMs >= countThresholdMs(p.durationMs)) {
            p.counted = true
            actions += ReportAction.RecordPlay(p.key, p.startedAt, p.playedMs)
        }
        if (scrobbleAllowed && !p.scrobbled && p.durationMs > 0 && p.positionMs >= scrobbleAtMs(p.durationMs)) {
            p.scrobbled = true
            actions += ReportAction.Scrobble(p.key)
        }
    }

    private fun timeline(p: Play, state: ReportedState) =
        ReportAction.Timeline(p.key, state, p.positionMs, p.durationMs)

    private fun nextWake(p: Play, now: Long, interval: Long): Long? {
        if (p.state != ReportedState.PLAYING) return null
        var wake = p.lastTimelineAt + interval
        if (!p.counted) wake = minOf(wake, now + (countThresholdMs(p.durationMs) - p.playedMs))
        if (!p.scrobbled && p.durationMs > 0) wake = minOf(wake, now + (scrobbleAtMs(p.durationMs) - p.positionMs))
        return wake.coerceAtLeast(now + MIN_WAKE_MS)
    }

    companion object {
        const val INTERVAL_MS: Long = 10_000
        const val METERED_INTERVAL_MS: Long = 20_000
        const val SCROBBLE_FRACTION: Double = 0.9
        const val MAX_COUNT_THRESHOLD_MS: Long = 4 * 60_000

        /** Never sleep less than this, so a wake that lands a hair early cannot spin. */
        const val MIN_WAKE_MS: Long = 250

        /** Half the track, at most 4 minutes; 4 minutes when the duration is unknown. */
        fun countThresholdMs(durationMs: Long): Long =
            if (durationMs > 0) minOf(durationMs / 2, MAX_COUNT_THRESHOLD_MS) else MAX_COUNT_THRESHOLD_MS

        fun scrobbleAtMs(durationMs: Long): Long = (durationMs * SCROBBLE_FRACTION).toLong()
    }
}
