package com.crsmthw.sheliak.player

import androidx.media3.common.Player
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.Track

/**
 * The playback commands every screen (and later the widget) issues. [PlayerStateManager] implements them over
 * its `MediaController`; nothing else talks to the player.
 *
 * Every index is a position in `PlayerState.queue`, which is the queue in PLAY order: with shuffle on it is the
 * shuffled order the player will actually follow, so "the third row of the queue screen" is always index 2.
 */
interface PlaybackController {

    /** Replaces the queue with [tracks] and starts `tracks[startIndex]` at [startPositionMs]. */
    fun play(tracks: List<Track>, startIndex: Int, startPositionMs: Long = 0)

    fun playPause()

    fun next()

    /** Media3's rule: back to the start of the track once 3 s of it have played, else the previous track. */
    fun previous()

    fun seekTo(positionMs: Long)

    fun setShuffle(on: Boolean)

    fun setRepeat(mode: RepeatMode)

    /** Right after the current track in play order (with shuffle on too). */
    fun addNext(tracks: List<Track>)

    /** At the end of the queue in play order (with shuffle on too). */
    fun addLast(tracks: List<Track>)

    fun removeAt(index: Int)

    /** Moves the queue row at [from] to [to] (both play-order positions). */
    fun move(from: Int, to: Int)

    /** Plays the queue row at [index] from its start. */
    fun skipToQueueIndex(index: Int)
}

/** Ours → Media3's repeat constant. */
internal fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
}

/** Media3's repeat constant → ours. */
internal fun repeatModeOf(playerRepeatMode: Int): RepeatMode = when (playerRepeatMode) {
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    else                   -> RepeatMode.OFF
}
