package com.crsmthw.sheliak.player

import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.crsmthw.sheliak.data.provider.PlaybackReporter
import com.crsmthw.sheliak.data.provider.ProviderRegistry
import com.crsmthw.sheliak.data.provider.ReportedState
import com.crsmthw.sheliak.data.repository.HistoryRepository
import com.crsmthw.sheliak.data.repository.resultOf
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The play-history + reporting hook. `PlaybackService` installs it on its ExoPlayer, so plays are recorded and
 * reported however playback was started — the app, the notification, a headset, Android Auto — and whether or
 * not any UI is alive.
 *
 * It samples the player on every relevant event and whenever [ReportingScheduler] asks to be woken (the 10 s
 * timeline, 90 %, the counted-play threshold) — a local timer, never a poll of anything remote — and runs the
 * resulting actions in order on [scope] (the app scope, so the final STOPPED goes out after the service is gone).
 * A failed report is logged and dropped: the next timeline carries the state anyway.
 *
 * Main thread only (the player's application thread), except the sender coroutine.
 */
class PlayReportingHook(
    private val player: Player,
    private val providers: ProviderRegistry,
    private val history: HistoryRepository,
    private val connectivity: ConnectivityManager?,
    private val scope: CoroutineScope,
) : Player.Listener {

    private val scheduler = ReportingScheduler(
        clock     = SystemClock::elapsedRealtime,
        wallClock = System::currentTimeMillis,
    )
    private val mainScope = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate)
    private val outbox = Channel<ReportAction>(Channel.UNLIMITED)
    private var sender: Job? = null
    private var wake: Job? = null
    private var playId = 0L
    private var seeked = false
    private var released = false

    fun start() {
        sender = scope.launch { for (action in outbox) send(action) }
        player.addListener(this)
        evaluate()
    }

    /** Before the player is released: closes the current play (STOPPED, and a due record/scrobble). */
    fun release() {
        if (released) return
        released = true
        player.removeListener(this)
        wake?.cancel()
        scheduler.stop().forEach { outbox.trySend(it) }
        outbox.close()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        playId++
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) seeked = true
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_TIMELINE_CHANGED,
            )
        ) {
            evaluate()
        }
    }

    private fun evaluate() {
        if (released) return
        val step = scheduler.update(sample(), metered = isMetered(), seeked = seeked)
        seeked = false
        step.actions.forEach { outbox.trySend(it) }
        wake?.cancel()
        wake = step.nextWakeAtMs?.let { at ->
            mainScope.launch {
                delay(at - SystemClock.elapsedRealtime())
                evaluate()
            }
        }
    }

    private fun sample(): PlaybackSample {
        val item = player.currentMediaItem
        val key = item?.let { TrackKey.parseMediaId(it.mediaId) }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: item?.mediaMetadata?.durationMs
            ?: 0L
        return PlaybackSample(
            key        = key,
            playId     = playId,
            state      = if (key == null) null else reportedState(player),
            positionMs = player.currentPosition,
            durationMs = duration,
        )
    }

    private fun isMetered(): Boolean = connectivity?.isActiveNetworkMetered ?: false

    private suspend fun send(action: ReportAction) {
        val result = resultOf {
            when (action) {
                is ReportAction.RecordPlay -> history.recordPlay(action.key, action.playedAt, action.playedMs).getOrThrow()
                is ReportAction.Timeline   -> reporterFor(action.key)
                    ?.timeline(action.key, action.state, action.positionMs, action.durationMs)?.getOrThrow()
                is ReportAction.Scrobble   -> reporterFor(action.key)?.scrobble(action.key)?.getOrThrow()
            }
        }
        result.exceptionOrNull()?.let { Log.w(TAG, "Report failed: $action", it) }
    }

    private suspend fun reporterFor(key: TrackKey): PlaybackReporter? =
        (providers.current(key.providerId) ?: providers.provider(key.providerId))?.reporter

    private companion object {
        const val TAG = "PlayReporting"

        fun reportedState(player: Player): ReportedState = when {
            player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED -> ReportedState.STOPPED
            player.isPlaying -> ReportedState.PLAYING
            player.playbackState == Player.STATE_BUFFERING && player.playWhenReady -> ReportedState.BUFFERING
            else -> ReportedState.PAUSED
        }
    }
}
