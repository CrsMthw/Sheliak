package com.crsmthw.sheliak.domain

import androidx.compose.runtime.Immutable

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Everything the UI shows about playback except the position, which is a separate ticker flow (it changes
 * every frame of a progress bar; this changes on events). [liveFormat] is the decoder's input format when known
 * (the truth for a transcode), else null and the UI falls back to the track's indexed format.
 */
@Immutable
data class PlayerState(
    val track: Track?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val durationMs: Long,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val queue: List<Track>,
    val queueIndex: Int,
    val liveFormat: AudioFormatInfo?,
    val audioSessionId: Int,
) {
    companion object {
        /** Nothing loaded: the initial value of every `StateFlow<PlayerState>`. */
        val Empty: PlayerState = PlayerState(
            track          = null,
            isPlaying      = false,
            isBuffering    = false,
            durationMs     = 0L,
            shuffle        = false,
            repeat         = RepeatMode.OFF,
            queue          = emptyList(),
            queueIndex     = -1,
            liveFormat     = null,
            audioSessionId = 0,
        )
    }
}
