@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.service

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.crsmthw.sheliak.MainActivity
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.SheliakApplication
import com.crsmthw.sheliak.di.AppContainer
import com.crsmthw.sheliak.player.AudioFormats
import com.crsmthw.sheliak.player.PlayReportingHook
import com.crsmthw.sheliak.player.toPlayerRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * THE player: one ExoPlayer inside a [MediaLibraryService], so the notification, the lock screen, headset
 * buttons, Android Auto and the app's own [com.crsmthw.sheliak.player.PlayerStateManager] all drive the same
 * instance through its session. docs/PLAYER.md has the architecture.
 *
 * - Audio: `USAGE_MEDIA` / `AUDIO_CONTENT_TYPE_MUSIC` with audio-focus handling, pause on becoming noisy, network
 *   wake lock, the default renderers (no extension decoders), audio offload off (the visualizer needs PCM).
 * - Data: [ProviderDataSourceFactory] (provider streams, content://, files).
 * - Queue: [SheliakShuffleOrder] (play-next / add-to-queue / reorder semantics under shuffle), persisted by
 *   [QueueStore] and restored here on start (not prepared, so nothing plays and no notification appears until
 *   the user presses play).
 * - Reporting: [PlayReportingHook] (history + provider timeline / scrobble), installed on this player.
 * - The ONE notification: Media3's, with Sheliak's channel and small icon and nothing else.
 */
class PlaybackService : MediaLibraryService() {

    private lateinit var container: AppContainer
    private lateinit var player: ExoPlayer
    private lateinit var reporting: PlayReportingHook
    private lateinit var callback: SheliakMediaLibraryCallback
    private var session: MediaLibrarySession? = null

    /** Work tied to this service (browse requests, the restore); cancelled in [onDestroy]. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = (application as SheliakApplication).container

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    ProviderDataSourceFactory(this, container.httpClient, container.providerRegistry, container.settingsRepository),
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMaxSeekToPreviousPositionMs(MAX_SEEK_TO_PREVIOUS_MS)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(AudioOffloadPreferences.DEFAULT) // mode DISABLED, stated explicitly
            .build()
        player.setShuffleOrder(SheliakShuffleOrder(Random.nextLong()))
        player.addListener(shuffleKeeper)
        player.addAnalyticsListener(formatReporter)
        container.playbackSignals.publishAudioSessionId(player.audioSessionId)

        container.queueStore.attach(player)
        reporting = PlayReportingHook(
            player       = player,
            providers    = container.providerRegistry,
            history      = container.historyRepository,
            connectivity = getSystemService<ConnectivityManager>(),
            scope        = container.appScope,
        ).also { it.start() }

        val items = MediaItemFactory(this, container.providerRegistry)
        callback = SheliakMediaLibraryCallback(this, container.libraryRepository, items, container.queueStore, serviceScope)
        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(sessionActivity())
            .setBitmapLoader(CoilBitmapLoader(this, container.imageLoader, container.providerRegistry, serviceScope))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )

        serviceScope.launch { restoreQueue() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        container.queueStore.persist(player)
        container.queueStore.detach(player)
        reporting.release()
        session?.release()
        session = null
        player.removeAnalyticsListener(formatReporter)
        player.removeListener(shuffleKeeper)
        player.release()
        container.playbackSignals.clearFormats()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** The last queue, loaded but not prepared, unless something was queued while it was being read. */
    private suspend fun restoreQueue() {
        val restored = withContext(Dispatchers.Default) { callback.restoredQueue() } ?: return
        if (player.mediaItemCount > 0) return
        player.shuffleModeEnabled = restored.shuffle
        player.repeatMode = restored.repeat.toPlayerRepeatMode()
        player.setMediaItems(restored.items, restored.startIndex, restored.positionMs)
    }

    private fun sessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Starts a new or newly shuffled queue from its current track, so nothing before it is skipped. */
    private val shuffleKeeper = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) startShuffleFromCurrent()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val order = player.shuffleOrder as? SheliakShuffleOrder ?: return
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && order.fresh && player.shuffleModeEnabled) {
                startShuffleFromCurrent()
            }
        }
    }

    private fun startShuffleFromCurrent() {
        val order = player.shuffleOrder as? SheliakShuffleOrder ?: return
        if (player.mediaItemCount == 0 || order.length != player.mediaItemCount) return
        player.setShuffleOrder(order.startingWith(player.currentMediaItemIndex))
    }

    /** The decoder's input format (the truth for a transcode) and the audio session id, to PlaybackSignals. */
    private val formatReporter = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            val timeline = eventTime.timeline
            if (eventTime.windowIndex !in 0 until timeline.windowCount) return
            val mediaId = timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId
            val info = AudioFormats.fromDecoderInput(
                sampleMimeType    = format.sampleMimeType,
                containerMimeType = format.containerMimeType,
                bitrate           = format.bitrate,
                sampleRate        = format.sampleRate,
                channelCount      = format.channelCount,
                pcmEncoding       = format.pcmEncoding,
            ) ?: return
            container.playbackSignals.publishFormat(mediaId, info)
        }

        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            container.playbackSignals.publishAudioSessionId(audioSessionId)
        }
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "playback"

        /** DESIGN §3: "previous" restarts the track once 3 s of it have played (Media3's default, stated). */
        const val MAX_SEEK_TO_PREVIOUS_MS: Long = 3_000
    }
}
