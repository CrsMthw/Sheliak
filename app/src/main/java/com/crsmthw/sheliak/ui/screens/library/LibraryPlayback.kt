package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.domain.PlayerState
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.PlaybackController
import com.crsmthw.sheliak.ui.components.TrackMenuActions
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/*
 * How the library screens talk to the player: thin helpers over PlaybackController (the queue is the player's
 * own timeline, so every command is local and immediate) and the one read of the player's state they need — the
 * current track, to colour its row.
 */

/** Plays [tracks] from [index] (clamped), replacing the queue; nothing for an empty list. */
fun PlaybackController.playFrom(tracks: List<Track>, index: Int) {
    if (tracks.isEmpty()) return
    play(tracks, index.coerceIn(0, tracks.lastIndex))
}

/**
 * A detail hero's (or a playlist card's) Play: the whole list in its own order from the first track — shuffle off
 * first, so a Play after a Shuffle is not a second shuffle (the player's shuffle mode is global and sticks). A row
 * tap ([playFrom]) leaves the mode as the user set it.
 */
fun PlaybackController.playAll(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    setShuffle(false)
    playFrom(tracks, 0)
}

/**
 * A detail hero's Shuffle: shuffle on, then the whole list from a random track — so a shuffle never always opens
 * on the same first song.
 */
fun PlaybackController.shuffleAll(tracks: List<Track>, random: Random = Random.Default) {
    if (tracks.isEmpty()) return
    setShuffle(true)
    play(tracks, shuffleStartIndex(tracks.size, random))
}

/** Where a shuffled play starts: any index of a list of [size] tracks (0 for an empty one). Pure; tested. */
fun shuffleStartIndex(size: Int, random: Random): Int = if (size <= 0) 0 else random.nextInt(size)

/**
 * The key of the track the player is on, as state that changes only when THAT changes — the player's state
 * also flips on play / pause, buffering and format, and a list colouring one row must not recompose for those.
 * Collected with the lifecycle: it keeps the player connected only while the screen is visible.
 */
@Composable
fun rememberCurrentTrackKey(playerState: StateFlow<PlayerState>): State<TrackKey?> {
    val state = playerState.collectAsStateWithLifecycle()
    return remember(state) { derivedStateOf { state.value.track?.key } }
}

/**
 * The long-press menu's actions over [player]: Play next / Add to queue, and the "go to" pushes the caller
 * passes (null hides that item — the album screen passes no [onGoToAlbum]).
 */
@Composable
fun rememberTrackMenuActions(
    player      : PlaybackController,
    onGoToAlbum : ((TrackKey) -> Unit)?,
    onGoToArtist: ((TrackKey) -> Unit)?,
): TrackMenuActions = remember(player, onGoToAlbum, onGoToArtist) {
    TrackMenuActions(
        onPlayNext   = { track -> player.addNext(listOf(track)) },
        onAddToQueue = { track -> player.addLast(listOf(track)) },
        onGoToAlbum  = onGoToAlbum,
        onGoToArtist = onGoToArtist,
    )
}
