@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.crsmthw.sheliak.data.repository.LibraryRepository
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.service.MediaItemFactory
import com.crsmthw.sheliak.service.PlaybackService
import com.crsmthw.sheliak.service.SheliakCommands
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

/**
 * The app's view of the player: owns the `MediaController` to [PlaybackService]'s session, turns its callbacks
 * into one immediate [state], and implements the [PlaybackController] commands. App-scoped (AppContainer);
 * every ViewModel reads it.
 *
 * - Connected only while wanted: while anything collects [state] or [position], and for [IDLE_RELEASE_MS] after a
 *   command. A process started for a background sync never binds the service; the UI going away releases the
 *   controller, as Media3 recommends, so the service can stop. Commands issued before the connection is up wait
 *   for it, in order.
 * - [state] changes on events only (`Player.Listener`); [position] is a UI-side ticker reading the controller's
 *   own position estimate while it is collected — never a poll of anything remote.
 * - `queue` is in PLAY order (the shuffle order when shuffle is on) and one-to-one with the player's timeline:
 *   a track the index no longer has is shown from the item's own metadata rather than dropped, so every queue
 *   index still points at the right item.
 * - [audioSessionId] and `liveFormat` come from the service's ExoPlayer through [PlaybackSignals].
 *
 * Main thread: the controller is built on, and only touched from, the main looper.
 */
class PlayerStateManager(
    context: Context,
    private val library: LibraryRepository,
    private val signals: PlaybackSignals,
    appScope: CoroutineScope,
) : PlaybackController {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState.Empty)

    /** Everything about playback except the position. Collect with `collectAsStateWithLifecycle`. */
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** The player's audio session id (the visualizer binds to it); 0 until the service has a player. */
    val audioSessionId: StateFlow<Int> = signals.audioSessionId

    private val tickers = MutableStateFlow(0)
    private val commandHold = MutableStateFlow(false)
    private var commandHoldJob: Job? = null

    private var controller: MediaController? = null
    private var connecting: CompletableDeferred<MediaController?>? = null

    /** The timeline's tracks, in timeline order, one per window. */
    private var windowTracks: List<Track> = emptyList()

    /** Window indices in play order. */
    private var order: IntArray = IntArray(0)

    /** [windowTracks] in [order]: the published `queue`. */
    private var queue: List<Track> = emptyList()

    /** Tracks already read, so a timeline change only reads the new ones. Holds the current queue's tracks. */
    private var trackCache: Map<TrackKey, Track> = emptyMap()
    private var queueJob: Job? = null
    private var playlistChanged = false

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@PlayerStateManager.controller === controller) {
                // The service went away (stopped, or its process died): drop this controller, and reconnect if the
                // UI is still watching — that restarts the service, which restores the queue.
                this@PlayerStateManager.controller = null
                queueJob?.cancel()
                controller.removeListener(playerListener)
                controller.release()
                _state.update { it.copy(isPlaying = false, isBuffering = false) }
                if (wanted.value) scope.launch { connect() }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) playlistChanged = true
        }

        override fun onEvents(player: Player, events: Player.Events) {
            val c = controller ?: return
            if (playlistChanged) {
                playlistChanged = false
                refreshQueue(c)
            } else if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                reorder(c)
            }
            publish()
        }
    }

    private val wanted: StateFlow<Boolean> =
        combine(_state.subscriptionCount, tickers, commandHold) { subscribers, ticking, holding ->
            subscribers > 0 || ticking > 0 || holding
        }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            wanted.collectLatest { isWanted ->
                if (isWanted) {
                    connect()
                } else {
                    delay(IDLE_RELEASE_MS)
                    disconnect()
                }
            }
        }
        scope.launch {
            combine(signals.formats, signals.audioSessionId) { _, _ -> }.collect { publish() }
        }
    }

    /**
     * The playback position in ms, every [intervalMs] while collected (distinct values only, so a paused
     * player emits once). Read from the controller's local estimate; keeps the controller connected meanwhile.
     */
    fun position(intervalMs: Long = DEFAULT_TICK_MS): Flow<Long> = flow {
        tickers.update { it + 1 }
        try {
            connect()
            while (true) {
                controller?.let { emit(it.currentPosition.coerceAtLeast(0)) }
                delay(intervalMs)
            }
        } finally {
            tickers.update { it - 1 }
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Main.immediate)

    // ── PlaybackController ───────────────────────────────────────────────────

    override fun play(tracks: List<Track>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) return
        remember(tracks)
        withController { c ->
            c.setMediaItems(tracks.map(MediaItemFactory::request), startIndex.coerceIn(0, tracks.lastIndex), startPositionMs)
            c.prepare()
            c.play()
        }
    }

    override fun playPause() = withController { c -> Util.handlePlayPauseButtonAction(c) }

    override fun next() = withController { it.seekToNext() }

    override fun previous() = withController { it.seekToPrevious() }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }

    override fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    override fun setRepeat(mode: RepeatMode) = withController { it.repeatMode = mode.toPlayerRepeatMode() }

    override fun addNext(tracks: List<Track>) = enqueue(tracks, next = true)

    override fun addLast(tracks: List<Track>) = enqueue(tracks, next = false)

    override fun removeAt(index: Int) = withController { c ->
        order.getOrNull(index)?.let(c::removeMediaItem)
    }

    override fun move(from: Int, to: Int) = withController { c ->
        val count = c.mediaItemCount
        if (from !in 0 until count || to !in 0 until count || from == to) return@withController
        if (c.shuffleModeEnabled) {
            // A play-order move: the timeline stays, the service reorders its shuffle order.
            c.sendCustomCommand(
                SheliakCommands.moveInPlayOrder,
                Bundle().apply {
                    putInt(SheliakCommands.ARG_FROM, from)
                    putInt(SheliakCommands.ARG_TO, to)
                },
            )
        } else {
            c.moveMediaItem(from, to)
        }
    }

    override fun skipToQueueIndex(index: Int) = withController { c ->
        val window = order.getOrNull(index) ?: return@withController
        c.seekToDefaultPosition(window)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    private fun enqueue(tracks: List<Track>, next: Boolean) {
        if (tracks.isEmpty()) return
        remember(tracks)
        withController { c ->
            val requests = tracks.map(MediaItemFactory::request)
            when {
                c.mediaItemCount == 0 -> {
                    c.setMediaItems(requests)
                    c.prepare()
                }
                next -> c.addMediaItems(c.currentMediaItemIndex + 1, requests)
                else -> c.addMediaItems(requests)
            }
        }
    }

    // ── Connection ───────────────────────────────────────────────────────────

    /** Runs [block] on the connected controller now, or once it connects; holds the connection a while after. */
    private fun withController(block: (MediaController) -> Unit) {
        holdForCommand()
        val c = controller
        if (c != null && c.isConnected) {
            block(c)
        } else {
            scope.launch { connect()?.let(block) }
        }
    }

    private fun holdForCommand() {
        commandHold.value = true
        commandHoldJob?.cancel()
        commandHoldJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            commandHold.value = false
        }
    }

    /** The connected controller, connecting first if needed; null when the service could not be reached. */
    private suspend fun connect(): MediaController? {
        controller?.takeIf { it.isConnected }?.let { return it }
        return (connecting ?: startConnecting()).await()
    }

    /**
     * Starts building the controller. The build is not tied to any caller's coroutine: a caller that stops
     * waiting (a ticker cancelled mid-connect) leaves it running for the others, and [disconnect] abandons it.
     */
    private fun startConnecting(): CompletableDeferred<MediaController?> {
        val pending = CompletableDeferred<MediaController?>()
        connecting = pending
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).setListener(controllerListener).buildAsync()
        future.addListener(
            {
                val connected = when {
                    future.isCancelled -> null
                    else -> try {
                        Futures.getDone(future)
                    } catch (e: ExecutionException) {
                        Log.w(TAG, "Could not connect to the playback service", e.cause ?: e)
                        null
                    }
                }
                if (connecting === pending) {
                    connecting = null
                    controller = connected
                    connected?.let(::attach)
                } else {
                    connected?.release() // abandoned by disconnect() while connecting
                }
                pending.complete(connected)
            },
            appContext.mainExecutor,
        )
        return pending
    }

    private fun disconnect() {
        connecting?.let { pending ->
            connecting = null
            pending.complete(null)
        }
        val c = controller ?: return
        controller = null
        queueJob?.cancel()
        c.removeListener(playerListener)
        c.release()
    }

    private fun attach(c: MediaController) {
        c.addListener(playerListener)
        refreshQueue(c)
        publish()
    }

    // ── Queue + state ────────────────────────────────────────────────────────

    private fun remember(tracks: List<Track>) {
        trackCache = trackCache + tracks.associateBy { it.key }
    }

    /** The timeline's items → tracks (cache first, then one read for the rest) and the play order. */
    private fun refreshQueue(c: MediaController) {
        val timeline = c.currentTimeline
        val window = Timeline.Window()
        val mediaItems = List(timeline.windowCount) { timeline.getWindow(it, window).mediaItem }
        val keys = mediaItems.map { TrackKey.parseMediaId(it.mediaId) }
        val cache = trackCache
        windowTracks = mediaItems.mapIndexed { i, item -> keys[i]?.let(cache::get) ?: fallbackTrack(item, keys[i]) }
        trackCache = keys.filterNotNull().mapNotNull { key -> cache[key]?.let { key to it } }.toMap()
        reorder(c)

        val missing = keys.filterNotNull().filter { it !in cache }.distinct()
        queueJob?.cancel()
        if (missing.isEmpty()) return
        queueJob = scope.launch {
            val found = library.tracks(missing).associateBy { it.key }
            if (found.isEmpty()) return@launch
            trackCache = trackCache + found
            windowTracks = windowTracks.mapIndexed { i, track -> keys.getOrNull(i)?.let(found::get) ?: track }
            queue = order.map { windowTracks[it] }
            publish()
        }
    }

    private fun reorder(c: MediaController) {
        val timeline = c.currentTimeline
        val shuffled = c.shuffleModeEnabled
        order = if (timeline.windowCount != windowTracks.size) {
            QueueOrder.identity(windowTracks.size)
        } else {
            QueueOrder.playOrder(timeline.windowCount, timeline.getFirstWindowIndex(shuffled)) { index ->
                timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
            }
        }
        queue = order.map { windowTracks[it] }
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.update { it.copy(audioSessionId = signals.audioSessionId.value) }
            return
        }
        val window = c.currentMediaItemIndex
        val track = windowTracks.getOrNull(window)
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: track?.durationMs ?: 0L
        val live = track?.let { t -> signals.formats.value[t.key.mediaId]?.let { AudioFormats.merge(it, t.format) } }
        _state.value = PlayerState(
            track          = track,
            isPlaying      = c.isPlaying,
            isBuffering    = c.playbackState == Player.STATE_BUFFERING,
            durationMs     = duration,
            shuffle        = c.shuffleModeEnabled,
            repeat         = repeatModeOf(c.repeatMode),
            queue          = queue,
            queueIndex     = if (track == null) -1 else QueueOrder.positionOf(order, window),
            liveFormat     = live,
            audioSessionId = signals.audioSessionId.value,
        )
    }

    private companion object {
        const val TAG = "PlayerStateManager"
        const val DEFAULT_TICK_MS: Long = 200

        /** How long the controller stays connected after the last watcher or command goes away. */
        const val IDLE_RELEASE_MS: Long = 10_000

        /** A queue item the index no longer has, shown from the metadata the item carries. */
        fun fallbackTrack(item: MediaItem, key: TrackKey?): Track {
            val m = item.mediaMetadata
            val title = (m.title ?: m.displayTitle)?.toString().orEmpty()
            return Track(
                key          = key ?: TrackKey("", item.mediaId),
                title        = title,
                titleSort    = title.lowercase(),
                artistName   = m.artist?.toString().orEmpty(),
                artistKey    = null,
                albumTitle   = m.albumTitle?.toString(),
                albumKey     = null,
                discNo       = m.discNumber,
                trackNo      = m.trackNumber,
                durationMs   = m.durationMs ?: 0L,
                year         = m.releaseYear,
                format       = null,
                art          = MediaItemFactory.artRefOf(m.extras),
                addedAt      = 0L,
                playCount    = 0,
                lastPlayedAt = null,
            )
        }
    }
}
