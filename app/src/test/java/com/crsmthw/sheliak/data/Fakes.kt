package com.crsmthw.sheliak.data

import com.crsmthw.sheliak.data.db.PlayStats
import com.crsmthw.sheliak.data.db.dao.IndexDao
import com.crsmthw.sheliak.data.db.dao.ProviderDao
import com.crsmthw.sheliak.data.db.dao.ProviderTrackCount
import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntryEntity
import com.crsmthw.sheliak.data.db.entity.ProviderEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import com.crsmthw.sheliak.data.provider.IndexSink
import com.crsmthw.sheliak.data.provider.MusicProvider
import com.crsmthw.sheliak.data.provider.PlaybackPrefs
import com.crsmthw.sheliak.data.provider.PlaybackSource
import com.crsmthw.sheliak.data.provider.ProviderCapabilities
import com.crsmthw.sheliak.data.provider.ProviderInstance
import com.crsmthw.sheliak.data.provider.SyncCursor
import com.crsmthw.sheliak.data.provider.SyncProgress
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/*
 * In-memory stand-ins for the DAOs and a provider, so the sink, the sync runner and the repositories run their
 * real Kotlin logic on the JVM. IndexDao's @Transaction methods are NOT overridden: the fake supplies only the
 * SQL primitives, and the DAO's own merge / playlist / delete-pass code runs on top of them.
 */

private typealias RowKey = Pair<String, String>

class FakeIndexDao : IndexDao() {
    val artists = LinkedHashMap<RowKey, ArtistEntity>()
    val albums = LinkedHashMap<RowKey, AlbumEntity>()
    val tracks = LinkedHashMap<RowKey, TrackEntity>()
    val playlists = LinkedHashMap<Long, PlaylistEntity>()
    val entries = mutableListOf<PlaylistEntryEntity>()
    val trackBatches = mutableListOf<Int>()
    /** Providers whose `providers` row is gone; every other id exists. */
    val removedProviders = mutableSetOf<String>()
    private var nextPlaylistId = 0L

    override suspend fun providerExists(providerId: String): Boolean = providerId !in removedProviders

    override suspend fun upsertArtistRows(rows: List<ArtistEntity>) {
        rows.forEach { artists[it.providerId to it.itemId] = it }
    }

    override suspend fun upsertAlbumRows(rows: List<AlbumEntity>) {
        rows.forEach { albums[it.providerId to it.itemId] = it }
    }

    override suspend fun upsertTrackRows(rows: List<TrackEntity>) {
        trackBatches += rows.size
        rows.forEach { tracks[it.providerId to it.itemId] = it }
    }

    override suspend fun playStats(providerId: String, itemIds: List<String>): List<PlayStats> =
        itemIds.mapNotNull { tracks[providerId to it] }.map { PlayStats(it.itemId, it.playCount, it.lastPlayedAt) }

    override suspend fun serverPlaylist(providerId: String, itemId: String): PlaylistEntity? =
        playlists.values.firstOrNull { it.providerId == providerId && it.itemId == itemId }

    override suspend fun insertPlaylist(row: PlaylistEntity): Long {
        val id = ++nextPlaylistId
        playlists[id] = row.copy(id = id)
        return id
    }

    override suspend fun updatePlaylist(row: PlaylistEntity) {
        playlists[row.id] = row
    }

    override suspend fun clearEntries(playlistId: Long) {
        entries.removeAll { it.playlistId == playlistId }
    }

    override suspend fun insertEntries(rows: List<PlaylistEntryEntity>) {
        entries += rows
    }

    private fun <V> MutableMap<RowKey, V>.removeWhere(pred: (V) -> Boolean): Int {
        val keys = filterValues(pred).keys.toList()
        keys.forEach(::remove)
        return keys.size
    }

    override suspend fun deleteArtistsNotIn(providerId: String, syncRun: Long): Int =
        artists.removeWhere { it.providerId == providerId && it.syncRun != syncRun }

    override suspend fun deleteAlbumsNotIn(providerId: String, syncRun: Long): Int =
        albums.removeWhere { it.providerId == providerId && it.syncRun != syncRun }

    override suspend fun deleteTracksNotIn(providerId: String, syncRun: Long): Int =
        tracks.removeWhere { it.providerId == providerId && it.syncRun != syncRun }

    override suspend fun deletePlaylistEntriesNotIn(providerId: String, syncRun: Long) {
        val ids = playlists.values.filter { it.providerId == providerId && it.syncRun != syncRun }.map { it.id }
        entries.removeAll { it.playlistId in ids }
    }

    override suspend fun deletePlaylistsNotIn(providerId: String, syncRun: Long): Int {
        val ids = playlists.values.filter { it.providerId == providerId && it.syncRun != syncRun }.map { it.id }
        ids.forEach(playlists::remove)
        return ids.size
    }

    override suspend fun deleteArtists(providerId: String, itemIds: List<String>) {
        itemIds.forEach { artists.remove(providerId to it) }
    }

    override suspend fun deleteAlbums(providerId: String, itemIds: List<String>) {
        itemIds.forEach { albums.remove(providerId to it) }
    }

    override suspend fun deleteTracks(providerId: String, itemIds: List<String>) {
        itemIds.forEach { tracks.remove(providerId to it) }
    }

    override suspend fun deletePlaylistEntries(providerId: String, itemIds: List<String>) {
        val ids = playlists.values.filter { it.providerId == providerId && it.itemId in itemIds }.map { it.id }
        entries.removeAll { it.playlistId in ids }
    }

    override suspend fun deletePlaylistRows(providerId: String, itemIds: List<String>) {
        playlists.values.filter { it.providerId == providerId && it.itemId in itemIds }.map { it.id }
            .forEach(playlists::remove)
    }

    override suspend fun deleteAllArtists(providerId: String) {
        artists.removeWhere { it.providerId == providerId }
    }

    override suspend fun deleteAllAlbums(providerId: String) {
        albums.removeWhere { it.providerId == providerId }
    }

    override suspend fun deleteAllTracks(providerId: String) {
        tracks.removeWhere { it.providerId == providerId }
    }

    override suspend fun deleteAllPlaylistEntries(providerId: String) {
        val ids = playlists.values.filter { it.providerId == providerId }.map { it.id }
        entries.removeAll { it.playlistId in ids }
    }

    override suspend fun deleteAllPlaylists(providerId: String) {
        playlists.values.filter { it.providerId == providerId }.map { it.id }.forEach(playlists::remove)
    }

    override suspend fun deleteHistory(providerId: String) = Unit

    override suspend fun deleteLyrics(providerId: String) = Unit

    override suspend fun deleteProviderRow(providerId: String) {
        removedProviders += providerId
    }
}

class FakeProviderDao(vararg rows: ProviderEntity) : ProviderDao {
    val rows = MutableStateFlow(rows.toList())

    override fun observeAll(): Flow<List<ProviderEntity>> = rows.map { list -> list.sortedBy { it.sort } }
    override suspend fun get(id: String): ProviderEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun enabledIds(): List<String> = rows.value.filter { it.enabled }.map { it.id }
    override suspend fun maxSort(): Int = rows.value.maxOfOrNull { it.sort } ?: -1
    override suspend fun insert(row: ProviderEntity) {
        rows.value += row
    }

    override suspend fun updateInstance(
        id: String,
        type: String,
        displayName: String,
        baseUrl: String?,
        serverId: String?,
        config: String?,
        enabled: Boolean,
    ) = edit(id) { it.copy(type = type, displayName = displayName, baseUrl = baseUrl, serverId = serverId, config = config, enabled = enabled) }

    override suspend fun saveSyncResult(id: String, cursor: String?, syncedAt: Long) =
        edit(id) { it.copy(syncCursor = cursor, lastSyncAt = syncedAt) }

    override suspend fun resetSyncCursor(id: String) = edit(id) { it.copy(syncCursor = null) }

    override fun trackCounts(): Flow<List<ProviderTrackCount>> = MutableStateFlow(emptyList())

    private fun edit(id: String, change: (ProviderEntity) -> ProviderEntity) {
        rows.value = rows.value.map { if (it.id == id) change(it) else it }
    }
}

fun providerRow(id: String, cursor: String? = null, enabled: Boolean = true) = ProviderEntity(
    id          = id,
    type        = id.substringBefore(':'),
    displayName = id,
    baseUrl     = null,
    serverId    = null,
    config      = null,
    sort        = 0,
    addedAt     = 0L,
    lastSyncAt  = null,
    syncCursor  = cursor,
    enabled     = enabled,
)

fun instanceOf(id: String) = ProviderInstance(
    id          = id,
    type        = id.substringBefore(':'),
    displayName = id,
    baseUrl     = null,
    serverId    = null,
    config      = null,
    enabled     = true,
)

/** A provider whose [sync] is whatever the test says. */
class FakeProvider(
    override val instance: ProviderInstance,
    private val onSync: suspend (SyncCursor?, IndexSink, (SyncProgress) -> Unit) -> Result<SyncCursor> =
        { _, _, _ -> Result.success(SyncCursor("done")) },
) : MusicProvider {
    var syncCalls = 0
        private set
    var lastCursor: SyncCursor? = null
        private set

    override val capabilities = ProviderCapabilities(
        editPlaylists = false, favourites = false, servesLyrics = false, transcode = false, scan = false,
        offlineOriginal = false,
    )

    override suspend fun sync(cursor: SyncCursor?, sink: IndexSink, progress: (SyncProgress) -> Unit): Result<SyncCursor> {
        syncCalls++
        lastCursor = cursor
        return onSync(cursor, sink, progress)
    }

    override suspend fun resolvePlayback(track: TrackKey, prefs: PlaybackPrefs): Result<PlaybackSource> =
        Result.failure(UnsupportedOperationException())

    override fun artModel(ref: ArtRef, sizePx: Int): Any? = null
}
