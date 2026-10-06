package com.crsmthw.sheliak.player

import com.crsmthw.sheliak.domain.AudioFormatInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What only the service's ExoPlayer knows, handed to the UI side in the same process: the decoder's input
 * format per item (`AnalyticsListener.onAudioInputFormatChanged`, the truth for a transcode) and the audio
 * session id (for the visualizer). `PlaybackService` writes, [PlayerStateManager] reads. App-scoped, in
 * `AppContainer`, so it outlives both and carries no reference to either.
 *
 * Formats are keyed by media id because the renderer reads the NEXT item's format before the transition (gapless
 * pre-buffering): keying by item keeps that early report from labelling the track still playing.
 */
class PlaybackSignals {

    private val _formats = MutableStateFlow<Map<String, AudioFormatInfo>>(emptyMap())

    /** The decoder's input format of the last few items, by media id. */
    val formats: StateFlow<Map<String, AudioFormatInfo>> = _formats.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)

    /** The player's audio session id; 0 until the service has created its player. */
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    fun publishFormat(mediaId: String, format: AudioFormatInfo) {
        _formats.update { old -> withFormat(old, mediaId, format, MAX_FORMATS) }
    }

    fun publishAudioSessionId(id: Int) {
        _audioSessionId.value = id
    }

    /** The player is gone: its formats no longer describe anything. */
    fun clearFormats() {
        _formats.value = emptyMap()
    }

    companion object {
        /** The current item, the pre-buffered next one, and a little slack. */
        const val MAX_FORMATS: Int = 4

        /**
         * [old] plus [mediaId] → [format] as the newest entry, dropping the oldest beyond [max]. Pure; tested in
         * PlaybackSignalsTest.
         */
        fun withFormat(
            old: Map<String, AudioFormatInfo>,
            mediaId: String,
            format: AudioFormatInfo,
            max: Int,
        ): Map<String, AudioFormatInfo> {
            val next = LinkedHashMap(old)
            next.remove(mediaId)
            next[mediaId] = format
            val iterator = next.keys.iterator()
            while (next.size > max && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
            return next
        }
    }
}
