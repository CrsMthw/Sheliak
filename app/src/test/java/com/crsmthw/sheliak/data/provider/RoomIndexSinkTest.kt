package com.crsmthw.sheliak.data.provider

import com.crsmthw.sheliak.data.FakeIndexDao
import com.crsmthw.sheliak.data.db.NormalizedKeys
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomIndexSinkTest {

    private val dao = FakeIndexDao()
    private val plex = "plex:aaa"

    private fun track(id: String, title: String = "Track $id", playCount: Int = 0, lastPlayedAt: Long? = null) =
        IndexTrack(itemId = id, title = title, artistName = "Artist", albumTitle = "Album", albumItemId = "al1",
            playCount = playCount, lastPlayedAt = lastPlayedAt)

    private fun sink(run: Long, providerId: String = plex) = RoomIndexSink(dao, providerId, syncRun = run)

    @Test
    fun `rows are written in batches of 500, the rest on commit`() = runTest {
        val s = sink(run = 1)
        s.putTracks((1..1200).map { track("$it") })
        assertEquals(listOf(500, 500), dao.trackBatches)
        assertEquals(1000, dao.tracks.size)
        s.commit()
        assertEquals(listOf(500, 500, 200), dao.trackBatches)
        assertEquals(1200, dao.tracks.size)
        assertEquals(1200, s.written)
    }

    @Test
    fun `small puts accumulate into one batch`() = runTest {
        val s = sink(run = 1)
        repeat(10) { page -> s.putTracks((1..60).map { track("$page-$it") }) }
        assertEquals(listOf(500), dao.trackBatches)
        s.commit()
        assertEquals(listOf(500, 100), dao.trackBatches)
    }

    @Test
    fun `rows carry the provider, the run and the normalised keys`() = runTest {
        val s = sink(run = 42)
        s.putTracks(listOf(track("1", title = "Ágætis byrjun")))
        s.putAlbums(listOf(IndexAlbum(itemId = "al1", title = "Back in Black", artistItemId = "ar1", artistName = "AC/DC")))
        s.putArtists(listOf(IndexArtist(itemId = "ar1", name = "AC/DC", nameSort = "ACDC")))
        s.commit()
        val t = dao.tracks.getValue(plex to "1")
        assertEquals(42, t.syncRun)
        assertEquals("agætis byrjun", t.titleSort)
        assertEquals(plex, t.albumPid)
        val al = dao.albums.getValue(plex to "al1")
        assertEquals(NormalizedKeys.albumKey("AC/DC", "Back in Black"), al.albumKey)
        assertEquals("acdc", al.artistKey)
        assertEquals(plex, al.artistPid)
        assertEquals("acdc", dao.artists.getValue(plex to "ar1").nameSort)
    }

    @Test
    fun `a complete kind deletes what this run did not report`() = runTest {
        sink(run = 1).apply {
            putTracks(listOf(track("1"), track("2"), track("3")))
            putAlbums(listOf(IndexAlbum(itemId = "old", title = "Old", artistItemId = null, artistName = "A")))
            commit()
        }
        sink(run = 2).apply {
            putTracks(listOf(track("1"), track("2")))
            markComplete(IndexKind.TRACKS)
            commit()
        }
        assertEquals(setOf("1", "2"), dao.tracks.keys.map { it.second }.toSet())
        assertTrue((plex to "old") in dao.albums, "albums were not declared complete, so none is deleted")
    }

    @Test
    fun `an incremental run deletes nothing`() = runTest {
        sink(run = 1).apply { putTracks(listOf(track("1"), track("2"))); commit() }
        sink(run = 2).apply { putTracks(listOf(track("2", title = "Renamed"))); commit() }
        assertEquals(2, dao.tracks.size)
        assertEquals("Renamed", dao.tracks.getValue(plex to "2").title)
    }

    @Test
    fun `a run that never commits deletes nothing`() = runTest {
        sink(run = 1).apply { putTracks(listOf(track("1"), track("2"))); commit() }
        sink(run = 2).apply { putTracks(listOf(track("1"))); markComplete(IndexKind.TRACKS) }   // failed: no commit
        assertEquals(2, dao.tracks.size)
    }

    @Test
    fun `the delete pass touches only this provider`() = runTest {
        sink(run = 1, providerId = "jellyfin:bbb").apply { putTracks(listOf(track("1"))); commit() }
        sink(run = 2).apply { putTracks(listOf(track("9"))); markComplete(IndexKind.TRACKS); commit() }
        assertTrue(("jellyfin:bbb" to "1") in dao.tracks)
        assertTrue((plex to "9") in dao.tracks)
    }

    @Test
    fun `a removal wins over a buffered write of the same item`() = runTest {
        val s = sink(run = 1)
        s.putTracks(listOf(track("1"), track("2")))
        s.remove(IndexKind.TRACKS, listOf("1"))
        s.commit()
        assertFalse((plex to "1") in dao.tracks)
        assertTrue((plex to "2") in dao.tracks)
    }

    @Test
    fun `a sync keeps Sheliak's own higher play numbers`() = runTest {
        sink(run = 1).apply { putTracks(listOf(track("1", playCount = 5, lastPlayedAt = 1_000))); commit() }
        sink(run = 2).apply { putTracks(listOf(track("1", playCount = 3, lastPlayedAt = 2_000))); commit() }
        val t = dao.tracks.getValue(plex to "1")
        assertEquals(5, t.playCount)
        assertEquals(2_000, t.lastPlayedAt)
    }

    @Test
    fun `a re-synced playlist keeps its local id and replaces its entries`() = runTest {
        val first = IndexPlaylist(itemId = "pl1", title = "Road", trackCount = 2,
            entries = listOf(IndexPlaylistEntry("1", "e1"), IndexPlaylistEntry("2", "e2")))
        sink(run = 1).apply { putPlaylist(first); commit() }
        val id = dao.playlists.keys.single()
        sink(run = 2).apply {
            putPlaylist(first.copy(title = "Road trip", trackCount = 1, entries = listOf(IndexPlaylistEntry("3", "e3"))))
            commit()
        }
        assertEquals(id, dao.playlists.keys.single())
        assertEquals("Road trip", dao.playlists.getValue(id).title)
        assertEquals(listOf("3"), dao.entries.map { it.trackIid })
        assertEquals(listOf(0), dao.entries.map { it.position })
        assertEquals(id, dao.entries.single().playlistId)
        assertEquals(plex, dao.entries.single().trackPid)
    }

    @Test
    fun `a complete playlist listing deletes the playlists it no longer has, with their entries`() = runTest {
        sink(run = 1).apply {
            putPlaylist(IndexPlaylist(itemId = "keep", title = "Keep", trackCount = 1, entries = listOf(IndexPlaylistEntry("1"))))
            putPlaylist(IndexPlaylist(itemId = "gone", title = "Gone", trackCount = 1, entries = listOf(IndexPlaylistEntry("2"))))
            commit()
        }
        sink(run = 2).apply {
            putPlaylist(IndexPlaylist(itemId = "keep", title = "Keep", trackCount = 1, entries = listOf(IndexPlaylistEntry("1"))))
            markComplete(IndexKind.PLAYLISTS)
            commit()
        }
        assertEquals(listOf("keep"), dao.playlists.values.map { it.itemId })
        assertEquals(listOf("1"), dao.entries.map { it.trackIid })
    }

    @Test
    fun `two providers with the same album give two rows and one album key`() = runTest {
        val album = IndexAlbum(itemId = "x", title = "Ágætis byrjun", artistItemId = "a", artistName = "Sigur Rós")
        sink(run = 1, providerId = "plex:aaa").apply { putAlbums(listOf(album)); commit() }
        sink(run = 1, providerId = "jellyfin:bbb").apply {
            putAlbums(listOf(album.copy(itemId = "y", title = "Agætis Byrjun", artistName = "Sigur Ros")))
            commit()
        }
        assertEquals(2, dao.albums.size)
        assertEquals(1, dao.albums.values.map { it.albumKey }.toSet().size)
        assertNotEquals(dao.albums.values.first().providerId, dao.albums.values.last().providerId)
    }

    @Test
    fun `concurrent puts are serialised`() = runTest {
        val s = sink(run = 1)
        (0 until 8).map { page -> async { s.putTracks((1..100).map { track("$page-$it") }) } }.awaitAll()
        s.commit()
        assertEquals(800, dao.tracks.size)
        assertEquals(800, s.written)
    }

    @Test
    fun `nothing is deleted for an empty removal`() = runTest {
        sink(run = 1).apply { putTracks(listOf(track("1"))); remove(IndexKind.TRACKS, emptyList()); commit() }
        assertNull(dao.tracks[plex to "missing"])
        assertEquals(1, dao.tracks.size)
    }

    @Test
    fun `a source removed mid-sync gets no rows back`() = runTest {
        val s = sink(run = 1)
        s.putTracks((1..500).map { track("$it") })
        assertEquals(500, dao.tracks.size)
        dao.deleteProvider(plex)   // Settings → Sources → remove, while the sync is still running
        assertEquals(setOf(plex), dao.removedProviders)
        s.putTracks((501..1100).map { track("$it") })
        s.putAlbums(listOf(IndexAlbum(itemId = "al1", title = "A", artistItemId = null, artistName = "B")))
        s.putArtists(listOf(IndexArtist(itemId = "ar1", name = "B")))
        s.putPlaylist(IndexPlaylist(itemId = "pl", title = "P", trackCount = 1, entries = listOf(IndexPlaylistEntry("1"))))
        s.commit()
        assertTrue(dao.tracks.isEmpty())
        assertTrue(dao.albums.isEmpty())
        assertTrue(dao.artists.isEmpty())
        assertTrue(dao.playlists.isEmpty())
        assertTrue(dao.entries.isEmpty())
        assertEquals(500, s.written)
    }
}
