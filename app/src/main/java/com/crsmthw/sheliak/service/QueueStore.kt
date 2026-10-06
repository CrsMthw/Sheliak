package com.crsmthw.sheliak.service

import android.util.Log
import androidx.media3.common.Player
import com.crsmthw.sheliak.data.db.dao.QueueDao
import com.crsmthw.sheliak.data.db.entity.QueueEntryEntity
import com.crsmthw.sheliak.data.db.entity.QueueStateEntity
import com.crsmthw.sheliak.data.repository.resultOf
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.repeatModeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** The queue as persisted: timeline order, the current window, where in it, the modes. */
data class QueueSnapshot(
    val keys: List<TrackKey>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val savedAt: Long,
)

/**
 * The queue persisted in Room (`queue` + `queue_state`) so it survives the process: written on every transition,
 * pause, seek, mode change and playlist change, and once more when the service is destroyed; read back when the
 * service starts and by `onPlaybackResumption` (a headset button or Android Auto after the process died).
 *
 * App-scoped (AppContainer), attached to each `PlaybackService` instance's player in turn. Writes are coalesced
 * (only the newest pending snapshot is written) and serialised on [scope] — the app scope, so the final write on
 * destroy still lands. The whole queue is rewritten only when its tracks changed; otherwise just the state row.
 */
class QueueStore(private val dao: QueueDao, private val scope: CoroutineScope) {

    private val pending = MutableStateFlow<QueueSnapshot?>(null)

    /** What the `queue` table holds, as far as this process knows; touched by the writer coroutine only. */
    private var writtenKeys: List<TrackKey>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                persist(player)
            }
        }
    }

    init {
        scope.launch { pending.filterNotNull().collect(::write) }
    }

    /** A new service's player. Main thread. */
    fun attach(player: Player) {
        sawItems = false
        player.addListener(listener)
    }

    fun detach(player: Player) = player.removeListener(listener)

    /**
     * Set once this player has held a queue. Until then an empty player is a service that has not restored yet
     * (or was bound and unbound in passing), and persisting it would wipe the saved queue. Main thread.
     */
    private var sawItems = false

    /** Queues a write of [player]'s queue as it is now. Main thread (reads the player). */
    fun persist(player: Player) {
        if (player.mediaItemCount > 0) sawItems = true
        if (!sawItems) return
        pending.value = snapshotOf(player)
    }

    /** The persisted queue, or null when there is none. */
    suspend fun restore(): QueueSnapshot? =
        resultOf { QueueSnapshots.restore(dao.entries(), dao.state()) }
            .onFailure { Log.w(TAG, "Could not read the persisted queue", it) }
            .getOrNull()

    private suspend fun write(snapshot: QueueSnapshot) {
        resultOf {
            when {
                snapshot.keys.isEmpty()      -> dao.clear()
                snapshot.keys == writtenKeys -> dao.saveState(QueueSnapshots.state(snapshot))
                else                         -> dao.replace(QueueSnapshots.entries(snapshot.keys), QueueSnapshots.state(snapshot))
            }
            writtenKeys = snapshot.keys
        }.onFailure { Log.w(TAG, "Could not persist the queue", it) }
    }

    private companion object {
        const val TAG = "QueueStore"

        fun snapshotOf(player: Player): QueueSnapshot {
            val keys = ArrayList<TrackKey>(player.mediaItemCount)
            for (i in 0 until player.mediaItemCount) {
                TrackKey.parseMediaId(player.getMediaItemAt(i).mediaId)?.let(keys::add)
            }
            return QueueSnapshot(
                keys         = keys,
                currentIndex = player.currentMediaItemIndex,
                positionMs   = player.currentPosition,
                shuffle      = player.shuffleModeEnabled,
                repeat       = repeatModeOf(player.repeatMode),
                savedAt      = System.currentTimeMillis(),
            )
        }
    }
}

/** Snapshot ↔ rows, and the restore arithmetic. Pure; tested in QueueSnapshotsTest. */
object QueueSnapshots {

    fun entries(keys: List<TrackKey>): List<QueueEntryEntity> =
        keys.mapIndexed { position, key -> QueueEntryEntity(position, key.providerId, key.itemId) }

    fun state(snapshot: QueueSnapshot): QueueStateEntity = QueueStateEntity(
        currentPosition = snapshot.currentIndex.coerceIn(0, (snapshot.keys.size - 1).coerceAtLeast(0)),
        positionMs      = snapshot.positionMs.coerceAtLeast(0),
        shuffle         = snapshot.shuffle,
        repeatMode      = snapshot.repeat,
        savedAt         = snapshot.savedAt,
    )

    /** The rows read back (in position order); null when nothing was persisted. */
    fun restore(entries: List<QueueEntryEntity>, state: QueueStateEntity?): QueueSnapshot? {
        if (state == null || entries.isEmpty()) return null
        val keys = entries.sortedBy { it.position }.map { TrackKey(it.trackPid, it.trackIid) }
        return QueueSnapshot(
            keys         = keys,
            currentIndex = state.currentPosition.coerceIn(0, keys.size - 1),
            positionMs   = state.positionMs.coerceAtLeast(0),
            shuffle      = state.shuffle,
            repeat       = state.repeatMode,
            savedAt      = state.savedAt,
        )
    }

    /**
     * Where to start once the tracks no longer in the index are dropped from [keys]: the saved current track at
     * its new index with its position, or — when it is gone itself — the next kept track from 0 ms. Null when
     * nothing is kept.
     */
    fun realign(keys: List<TrackKey>, currentIndex: Int, positionMs: Long, kept: Set<TrackKey>): Pair<Int, Long>? {
        val keptCount = keys.count { it in kept }
        if (keptCount == 0) return null
        val current = currentIndex.coerceIn(0, keys.size - 1)
        val before = keys.subList(0, current).count { it in kept }
        return if (keys[current] in kept) {
            before to positionMs
        } else {
            before.coerceAtMost(keptCount - 1) to 0L
        }
    }
}
