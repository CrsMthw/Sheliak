package com.crsmthw.sheliak.data.repository

import com.crsmthw.sheliak.data.db.dao.LibraryDao
import com.crsmthw.sheliak.data.db.dao.PlaylistDao
import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import com.crsmthw.sheliak.data.db.toEntity
import com.crsmthw.sheliak.data.provider.IndexAlbum
import com.crsmthw.sheliak.data.provider.IndexTrack
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The merge switch's wiring: which DAO query each flow follows, live. The grouping inside the merged queries is
 * SQL (verified by Room at compile time against the schema); here the "merged" queries return what that SQL
 * returns for the fixture — one representative per key.
 */
class LibraryRepositoryTest {

    private val plexAlbum = IndexAlbum(itemId = "101", title = "OK Computer", artistItemId = "7", artistName = "Radiohead",
        trackCount = 12).toEntity("plex:aaa", 1)
    private val jellyAlbum = IndexAlbum(itemId = "f00d", title = "OK Computer", artistItemId = "a1",
        artistName = "Radiohead", trackCount = 11).toEntity("jellyfin:bbb", 1)

    private fun track(pid: String, id: String, albumId: String) =
        IndexTrack(itemId = id, title = "Airbag", artistName = "Radiohead", albumTitle = "OK Computer",
            albumItemId = albumId, discNo = 1, trackNo = 1).toEntity(pid, 1)

    private val dao = FakeLibraryDao(
        albums = listOf(plexAlbum, jellyAlbum),
        albumsMerged = listOf(plexAlbum),
    )
    private val merge = MutableStateFlow(false)
    private val repo = LibraryRepository(dao, PlaylistRepository(FakePlaylistDao()), merge)

    @Test
    fun `off, every provider's album is listed - on, one per album key`() = runTest {
        assertEquals(2, repo.albums.first().size)
        merge.value = true
        assertEquals(listOf(TrackKey("plex:aaa", "101")), repo.albums.first().map { it.key })
    }

    @Test
    fun `counts follow the switch`() = runTest {
        assertEquals(2, repo.counts.first().albums)
        merge.value = true
        assertEquals(1, repo.counts.first().albums)
    }

    @Test
    fun `a merged album's tracks are looked up by its album key`() = runTest {
        dao.albumKeys[plexAlbum.providerId to plexAlbum.itemId] = plexAlbum.albumKey
        dao.albumTracksByKey[plexAlbum.albumKey] = listOf(track("jellyfin:bbb", "x", "f00d"))
        dao.albumTracksById[plexAlbum.providerId to plexAlbum.itemId] = listOf(track("plex:aaa", "1", "101"))
        val key = TrackKey("plex:aaa", "101")

        assertEquals(listOf(TrackKey("plex:aaa", "1")), repo.albumTracks(key).first().map { it.key })
        merge.value = true
        assertEquals(listOf(TrackKey("jellyfin:bbb", "x")), repo.albumTracks(key).first().map { it.key })
    }

    @Test
    fun `blank search text queries nothing`() = runTest {
        val r = repo.search("  ?! ").first()
        assertTrue(r.isEmpty)
        assertEquals(0, dao.searches)
    }

    @Test
    fun `tracks by keys keep the caller's order and drop unknown keys`() = runTest {
        dao.byId += listOf(track("plex:aaa", "1", "101"), track("plex:aaa", "2", "101"), track("jellyfin:bbb", "x", "f00d"))
        val keys = listOf(TrackKey("jellyfin:bbb", "x"), TrackKey("plex:aaa", "9"), TrackKey("plex:aaa", "2"),
            TrackKey("plex:aaa", "1"), TrackKey("plex:aaa", "2"))
        assertEquals(
            listOf(TrackKey("jellyfin:bbb", "x"), TrackKey("plex:aaa", "2"), TrackKey("plex:aaa", "1"), TrackKey("plex:aaa", "2")),
            repo.tracks(keys).map { it.key },
        )
    }
}

private class FakeLibraryDao(
    private val albums: List<AlbumEntity>,
    private val albumsMerged: List<AlbumEntity>,
) : LibraryDao {
    val albumKeys = mutableMapOf<Pair<String, String>, String>()
    val albumTracksByKey = mutableMapOf<String, List<TrackEntity>>()
    val albumTracksById = mutableMapOf<Pair<String, String>, List<TrackEntity>>()
    val byId = mutableListOf<TrackEntity>()
    var searches = 0

    override fun tracks(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun tracksMerged(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun trackCount(): Flow<Int> = flowOf(0)
    override fun trackCountMerged(): Flow<Int> = flowOf(0)
    override suspend fun track(providerId: String, itemId: String): TrackEntity? =
        byId.firstOrNull { it.providerId == providerId && it.itemId == itemId }
    override suspend fun tracks(providerId: String, itemIds: List<String>): List<TrackEntity> =
        byId.filter { it.providerId == providerId && it.itemId in itemIds }
    override fun albums(): Flow<List<AlbumEntity>> = flowOf(albums)
    override fun albumsMerged(): Flow<List<AlbumEntity>> = flowOf(albumsMerged)
    override fun albumCount(): Flow<Int> = flowOf(albums.size)
    override fun albumCountMerged(): Flow<Int> = flowOf(albumsMerged.size)
    override fun album(providerId: String, itemId: String): Flow<AlbumEntity?> = flowOf(null)
    override suspend fun albumKeyOf(providerId: String, itemId: String): String? = albumKeys[providerId to itemId]
    override fun albumTracks(providerId: String, itemId: String): Flow<List<TrackEntity>> =
        flowOf(albumTracksById[providerId to itemId].orEmpty())
    override fun albumTracksMerged(albumKey: String): Flow<List<TrackEntity>> =
        flowOf(albumTracksByKey[albumKey].orEmpty())
    override fun artists(): Flow<List<ArtistEntity>> = flowOf(emptyList())
    override fun artistsMerged(): Flow<List<ArtistEntity>> = flowOf(emptyList())
    override fun artistCount(): Flow<Int> = flowOf(0)
    override fun artistCountMerged(): Flow<Int> = flowOf(0)
    override fun artist(providerId: String, itemId: String): Flow<ArtistEntity?> = flowOf(null)
    override suspend fun artistKeyOf(providerId: String, itemId: String): String? = null
    override fun artistAlbums(providerId: String, itemId: String): Flow<List<AlbumEntity>> = flowOf(emptyList())
    override fun artistAlbumsMerged(artistKey: String): Flow<List<AlbumEntity>> = flowOf(emptyList())
    override fun recentlyPlayed(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun mostPlayed(limit: Int): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun searchTracks(query: String, limit: Int): Flow<List<TrackEntity>> = searched()
    override fun searchTracksMerged(query: String, limit: Int): Flow<List<TrackEntity>> = searched()
    override fun searchAlbums(query: String, limit: Int): Flow<List<AlbumEntity>> = searched()
    override fun searchAlbumsMerged(query: String, limit: Int): Flow<List<AlbumEntity>> = searched()
    override fun searchArtists(query: String, limit: Int): Flow<List<ArtistEntity>> = searched()
    override fun searchArtistsMerged(query: String, limit: Int): Flow<List<ArtistEntity>> = searched()

    private fun <T> searched(): Flow<List<T>> {
        searches++
        return flowOf(emptyList())
    }
}

private class FakePlaylistDao : PlaylistDao {
    override fun playlists(): Flow<List<PlaylistEntity>> = flowOf(emptyList())
    override fun count(): Flow<Int> = flowOf(0)
    override fun playlist(playlistId: Long): Flow<PlaylistEntity?> = flowOf(null)
    override fun playlistTracks(playlistId: Long): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun search(pattern: String, limit: Int): Flow<List<PlaylistEntity>> = flowOf(emptyList())
}
