package com.crsmthw.sheliak.data.repository

import com.crsmthw.sheliak.data.db.FtsQuery
import com.crsmthw.sheliak.data.db.dao.PlaylistDao
import com.crsmthw.sheliak.data.db.entity.PlaylistEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import com.crsmthw.sheliak.data.db.toDomain
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Playlists, read side. Server playlists are written by the sync; Liked Songs, local playlists and editing
 * (through `MusicProvider.playlists` for server ones) arrive in M2 and will live here.
 */
class PlaylistRepository(private val dao: PlaylistDao) {

    /** Liked Songs first (from M2), then by title. */
    val playlists: Flow<List<Playlist>> = dao.playlists().map { it.map(PlaylistEntity::toDomain) }

    val count: Flow<Int> = dao.count()

    fun playlist(id: Long): Flow<Playlist?> = dao.playlist(id).map { it?.toDomain() }

    /** In playlist order; entries whose track is not in the index are skipped. */
    fun tracks(id: Long): Flow<List<Track>> = dao.playlistTracks(id).map { it.map(TrackEntity::toDomain) }

    fun search(userText: String, limit: Int): Flow<List<Playlist>> {
        val pattern = FtsQuery.likeContains(userText) ?: return flowOf(emptyList())
        return dao.search(pattern, limit).map { it.map(PlaylistEntity::toDomain) }
    }
}
