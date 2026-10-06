package com.crsmthw.sheliak.data.repository

import com.crsmthw.sheliak.data.db.FtsQuery
import com.crsmthw.sheliak.data.db.dao.LibraryDao
import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import com.crsmthw.sheliak.data.db.toDomain
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Everything the library screens, search and the carousels read: cold Room flows mapped to the domain, live
 * (a sync batch or a recorded play re-emits them). Every list honours the "merge duplicates" setting: off, every
 * provider's row is listed; on, one representative per normalised key (`docs/INDEX.md`, "Merged views").
 *
 * Detail flows take the key of the row the user opened. With merging on, an album's tracks are those of EVERY
 * album sharing its `album_key`, and an artist's albums those of every artist sharing its `artist_key`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepository(
    private val dao: LibraryDao,
    private val playlistRepository: PlaylistRepository,
    mergeDuplicates: Flow<Boolean>,
) {

    /** The "merge duplicates" setting (`SettingsRepository.mergeDuplicates`), followed live. */
    val mergeDuplicates: Flow<Boolean> = mergeDuplicates.distinctUntilChanged()

    private fun <T> byMerge(perProvider: () -> Flow<T>, merged: () -> Flow<T>): Flow<T> =
        mergeDuplicates.flatMapLatest { merge -> if (merge) merged() else perProvider() }

    // ── Lists ───────────────────────────────────────────────────────────────

    /** All tracks by title. */
    val tracks: Flow<List<Track>> =
        byMerge(dao::tracks, dao::tracksMerged).map { it.map(TrackEntity::toDomain) }

    val albums: Flow<List<Album>> =
        byMerge(dao::albums, dao::albumsMerged).map { it.map(AlbumEntity::toDomain) }

    val artists: Flow<List<Artist>> =
        byMerge(dao::artists, dao::artistsMerged).map { it.map(ArtistEntity::toDomain) }

    val playlists: Flow<List<Playlist>> = playlistRepository.playlists

    val counts: Flow<LibraryCounts> = combine(
        byMerge(dao::trackCount, dao::trackCountMerged),
        byMerge(dao::albumCount, dao::albumCountMerged),
        byMerge(dao::artistCount, dao::artistCountMerged),
        playlistRepository.count,
    ) { tracks, albums, artists, playlists -> LibraryCounts(tracks, albums, artists, playlists) }
        .distinctUntilChanged()

    // ── Details ─────────────────────────────────────────────────────────────

    fun album(key: TrackKey): Flow<Album?> = dao.album(key.providerId, key.itemId).map { it?.toDomain() }

    /** By disc, then track number. */
    fun albumTracks(key: TrackKey): Flow<List<Track>> = byMerge(
        perProvider = { dao.albumTracks(key.providerId, key.itemId) },
        merged = {
            flow { emit(dao.albumKeyOf(key.providerId, key.itemId)) }.flatMapLatest { albumKey ->
                if (albumKey == null) flowOf(emptyList()) else dao.albumTracksMerged(albumKey)
            }
        },
    ).map { it.map(TrackEntity::toDomain) }

    fun artist(key: TrackKey): Flow<Artist?> = dao.artist(key.providerId, key.itemId).map { it?.toDomain() }

    /** Newest first; albums with no year last. */
    fun artistAlbums(key: TrackKey): Flow<List<Album>> = byMerge(
        perProvider = { dao.artistAlbums(key.providerId, key.itemId) },
        merged = {
            flow { emit(dao.artistKeyOf(key.providerId, key.itemId)) }.flatMapLatest { artistKey ->
                if (artistKey == null) flowOf(emptyList()) else dao.artistAlbumsMerged(artistKey)
            }
        },
    ).map { it.map(AlbumEntity::toDomain) }

    fun playlist(id: Long): Flow<Playlist?> = playlistRepository.playlist(id)

    fun playlistTracks(id: Long): Flow<List<Track>> = playlistRepository.tracks(id)

    // ── Carousels ───────────────────────────────────────────────────────────

    /** Newest first, one entry per track (Sheliak's plays and, from the first sync, the server's). */
    fun recentlyPlayed(limit: Int = CAROUSEL_SIZE): Flow<List<Track>> =
        dao.recentlyPlayed(limit).map { it.map(TrackEntity::toDomain) }

    /** Highest play count first. */
    fun mostPlayed(limit: Int = CAROUSEL_SIZE): Flow<List<Track>> =
        dao.mostPlayed(limit).map { it.map(TrackEntity::toDomain) }

    // ── Search ──────────────────────────────────────────────────────────────

    /**
     * Prefix search over titles, artists and albums (FTS4) and playlist titles (LIKE); at most [limit] hits per
     * kind, each kind by title. Blank or punctuation-only text emits empty results without querying.
     */
    fun search(userText: String, limit: Int = SEARCH_LIMIT): Flow<SearchResults> {
        val match = FtsQuery.build(userText) ?: return flowOf(SearchResults.empty(userText))
        return combine(
            byMerge({ dao.searchTracks(match, limit) }, { dao.searchTracksMerged(match, limit) }),
            byMerge({ dao.searchAlbums(match, limit) }, { dao.searchAlbumsMerged(match, limit) }),
            byMerge({ dao.searchArtists(match, limit) }, { dao.searchArtistsMerged(match, limit) }),
            playlistRepository.search(userText, limit),
        ) { tracks, albums, artists, playlists ->
            SearchResults(
                query     = userText,
                tracks    = tracks.map(TrackEntity::toDomain),
                albums    = albums.map(AlbumEntity::toDomain),
                artists   = artists.map(ArtistEntity::toDomain),
                playlists = playlists,
            )
        }
    }

    // ── One-shot reads (player, Android Auto) ───────────────────────────────

    suspend fun track(key: TrackKey): Track? = dao.track(key.providerId, key.itemId)?.toDomain()

    /**
     * The tracks for [keys], in the order given; keys not in the index are dropped (a duplicate key yields the
     * track twice). For resolving a persisted queue or Android Auto's media ids.
     */
    suspend fun tracks(keys: List<TrackKey>): List<Track> {
        if (keys.isEmpty()) return emptyList()
        val found = HashMap<TrackKey, Track>(keys.size * 2)
        keys.groupBy({ it.providerId }, { it.itemId }).forEach { (providerId, itemIds) ->
            itemIds.distinct().chunked(ID_CHUNK).forEach { chunk ->
                dao.tracks(providerId, chunk).forEach { e -> found[TrackKey(e.providerId, e.itemId)] = e.toDomain() }
            }
        }
        return keys.mapNotNull(found::get)
    }

    companion object {
        const val CAROUSEL_SIZE: Int = 20
        const val SEARCH_LIMIT: Int = 50
        private const val ID_CHUNK = 500
    }
}
