package com.crsmthw.sheliak.data.repository

import com.crsmthw.sheliak.data.db.dao.HistoryDao
import com.crsmthw.sheliak.data.db.entity.PlayHistoryEntity
import com.crsmthw.sheliak.data.db.toDomain
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Play history, for every provider. The playback service records a play once per track play (its reporting
 * hook decides when a play counts); recording also bumps the track's play count and last-played time, which
 * the Recently / Most played carousels read (LibraryRepository).
 */
class HistoryRepository(private val dao: HistoryDao) {

    /** [playedAt] epoch ms when the play started; [playedMs] how long it played. */
    suspend fun recordPlay(track: TrackKey, playedAt: Long, playedMs: Long): Result<Unit> = resultOf {
        dao.recordPlay(
            PlayHistoryEntity(
                trackPid = track.providerId,
                trackIid = track.itemId,
                playedAt = playedAt,
                playedMs = playedMs,
            ),
        )
    }

    /** Newest first; plays of tracks no longer in the index are left out. */
    fun history(limit: Int = HISTORY_LIMIT): Flow<List<PlayRecord>> = dao.history(limit).map { rows ->
        rows.map { PlayRecord(it.track.toDomain(), it.playedAt, it.playedMs) }
    }

    companion object {
        const val HISTORY_LIMIT: Int = 200
    }
}
