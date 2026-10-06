package com.crsmthw.sheliak.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/*
 * "Merged" queries implement the merge-duplicates switch: one representative row per normalised key. Each
 * group's representative is chosen in SQL with SQLite's bare-column rule — in an aggregate query with exactly
 * one max(), the other columns come from the row holding the max — so a merged track is the copy with the
 * highest bit rate, a merged album the copy with the most tracks, a merged artist the one with the most albums.
 * The `*_REP` subqueries return those representatives' rowids.
 */

private const val TRACKS_REP =
    "SELECT rid FROM (SELECT rowid AS rid, MAX(COALESCE(bitrate_kbps, 0)) AS q FROM tracks GROUP BY track_key)"
private const val ALBUMS_REP =
    "SELECT rid FROM (SELECT rowid AS rid, MAX(track_count) AS q FROM albums GROUP BY album_key)"
private const val ARTISTS_REP =
    "SELECT rid FROM (SELECT rowid AS rid, MAX(album_count) AS q FROM artists GROUP BY artist_key)"

@Dao
interface LibraryDao {

    // ── All tracks ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM tracks ORDER BY title_sort, artist_name")
    fun tracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE rowid IN ($TRACKS_REP) ORDER BY title_sort, artist_name")
    fun tracksMerged(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun trackCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT track_key) FROM tracks")
    fun trackCountMerged(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE provider_id = :providerId AND item_id = :itemId")
    suspend fun track(providerId: String, itemId: String): TrackEntity?

    /** At most 999 ids per call (SQLite's bind limit on older builds); callers chunk. */
    @Query("SELECT * FROM tracks WHERE provider_id = :providerId AND item_id IN (:itemIds)")
    suspend fun tracks(providerId: String, itemIds: List<String>): List<TrackEntity>

    // ── Albums ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM albums ORDER BY title_sort, artist_name")
    fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE rowid IN ($ALBUMS_REP) ORDER BY title_sort, artist_name")
    fun albumsMerged(): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums")
    fun albumCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT album_key) FROM albums")
    fun albumCountMerged(): Flow<Int>

    @Query("SELECT * FROM albums WHERE provider_id = :providerId AND item_id = :itemId")
    fun album(providerId: String, itemId: String): Flow<AlbumEntity?>

    @Query("SELECT album_key FROM albums WHERE provider_id = :providerId AND item_id = :itemId")
    suspend fun albumKeyOf(providerId: String, itemId: String): String?

    @Query(
        "SELECT * FROM tracks WHERE album_pid = :providerId AND album_iid = :itemId " +
            "ORDER BY disc_no, track_no, title_sort",
    )
    fun albumTracks(providerId: String, itemId: String): Flow<List<TrackEntity>>

    /** Every copy's tracks of the albums sharing [albumKey], one representative per recording. */
    @Query(
        "SELECT t.* FROM tracks t JOIN albums a ON a.provider_id = t.album_pid AND a.item_id = t.album_iid " +
            "WHERE a.album_key = :albumKey AND t.rowid IN (" +
            "SELECT rid FROM (SELECT t2.rowid AS rid, MAX(COALESCE(t2.bitrate_kbps, 0)) AS q FROM tracks t2 " +
            "JOIN albums a2 ON a2.provider_id = t2.album_pid AND a2.item_id = t2.album_iid " +
            "WHERE a2.album_key = :albumKey GROUP BY t2.track_key)) " +
            "ORDER BY t.disc_no, t.track_no, t.title_sort",
    )
    fun albumTracksMerged(albumKey: String): Flow<List<TrackEntity>>

    // ── Artists ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM artists ORDER BY name_sort")
    fun artists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE rowid IN ($ARTISTS_REP) ORDER BY name_sort")
    fun artistsMerged(): Flow<List<ArtistEntity>>

    @Query("SELECT COUNT(*) FROM artists")
    fun artistCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT artist_key) FROM artists")
    fun artistCountMerged(): Flow<Int>

    @Query("SELECT * FROM artists WHERE provider_id = :providerId AND item_id = :itemId")
    fun artist(providerId: String, itemId: String): Flow<ArtistEntity?>

    @Query("SELECT artist_key FROM artists WHERE provider_id = :providerId AND item_id = :itemId")
    suspend fun artistKeyOf(providerId: String, itemId: String): String?

    /** Newest first; albums with no year last. */
    @Query(
        "SELECT * FROM albums WHERE artist_pid = :providerId AND artist_iid = :itemId " +
            "ORDER BY year DESC, title_sort",
    )
    fun artistAlbums(providerId: String, itemId: String): Flow<List<AlbumEntity>>

    @Query(
        "SELECT * FROM albums WHERE artist_key = :artistKey AND rowid IN ($ALBUMS_REP) " +
            "ORDER BY year DESC, title_sort",
    )
    fun artistAlbumsMerged(artistKey: String): Flow<List<AlbumEntity>>

    // ── Carousels ───────────────────────────────────────────────────────────

    /** Distinct by construction: one row per track, ordered by its last play (Sheliak's or the server's). */
    @Query("SELECT * FROM tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    fun recentlyPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE play_count > 0 ORDER BY play_count DESC, last_played_at DESC LIMIT :limit")
    fun mostPlayed(limit: Int): Flow<List<TrackEntity>>

    // ── Search (FTS4; :query comes from FtsQuery.build) ─────────────────────

    @Query(
        "SELECT tracks.* FROM tracks JOIN tracks_fts ON tracks.rowid = tracks_fts.rowid " +
            "WHERE tracks_fts MATCH :query ORDER BY tracks.title_sort LIMIT :limit",
    )
    fun searchTracks(query: String, limit: Int): Flow<List<TrackEntity>>

    @Query(
        "SELECT tracks.* FROM tracks JOIN tracks_fts ON tracks.rowid = tracks_fts.rowid " +
            "WHERE tracks_fts MATCH :query AND tracks.rowid IN ($TRACKS_REP) ORDER BY tracks.title_sort LIMIT :limit",
    )
    fun searchTracksMerged(query: String, limit: Int): Flow<List<TrackEntity>>

    @Query(
        "SELECT albums.* FROM albums JOIN albums_fts ON albums.rowid = albums_fts.rowid " +
            "WHERE albums_fts MATCH :query ORDER BY albums.title_sort LIMIT :limit",
    )
    fun searchAlbums(query: String, limit: Int): Flow<List<AlbumEntity>>

    @Query(
        "SELECT albums.* FROM albums JOIN albums_fts ON albums.rowid = albums_fts.rowid " +
            "WHERE albums_fts MATCH :query AND albums.rowid IN ($ALBUMS_REP) ORDER BY albums.title_sort LIMIT :limit",
    )
    fun searchAlbumsMerged(query: String, limit: Int): Flow<List<AlbumEntity>>

    @Query(
        "SELECT artists.* FROM artists JOIN artists_fts ON artists.rowid = artists_fts.rowid " +
            "WHERE artists_fts MATCH :query ORDER BY artists.name_sort LIMIT :limit",
    )
    fun searchArtists(query: String, limit: Int): Flow<List<ArtistEntity>>

    @Query(
        "SELECT artists.* FROM artists JOIN artists_fts ON artists.rowid = artists_fts.rowid " +
            "WHERE artists_fts MATCH :query AND artists.rowid IN ($ARTISTS_REP) ORDER BY artists.name_sort LIMIT :limit",
    )
    fun searchArtistsMerged(query: String, limit: Int): Flow<List<ArtistEntity>>
}
