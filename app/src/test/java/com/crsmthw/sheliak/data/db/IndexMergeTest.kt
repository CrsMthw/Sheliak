package com.crsmthw.sheliak.data.db

import com.crsmthw.sheliak.data.provider.IndexAlbum
import com.crsmthw.sheliak.data.provider.IndexArtist
import com.crsmthw.sheliak.data.provider.IndexTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class IndexMergeTest {

    private fun row(id: String, playCount: Int, lastPlayedAt: Long?) =
        IndexTrack(itemId = id, title = id, artistName = "A", playCount = playCount, lastPlayedAt = lastPlayedAt)
            .toEntity("plex:a", syncRun = 1)

    @Test
    fun `a new row is written as reported`() {
        val r = row("1", 3, 100)
        assertSame(r, IndexMerge.mergePlayStats(listOf(r), emptyMap()).single())
    }

    @Test
    fun `the higher play count and the later last play win`() {
        val stored = mapOf(
            "1" to PlayStats("1", playCount = 9, lastPlayedAt = 50),
            "2" to PlayStats("2", playCount = 1, lastPlayedAt = 500),
            "3" to PlayStats("3", playCount = 0, lastPlayedAt = null),
        )
        val merged = IndexMerge.mergePlayStats(listOf(row("1", 3, 100), row("2", 4, null), row("3", 2, 70)), stored)
        assertEquals(listOf(9, 4, 2), merged.map { it.playCount })
        assertEquals(listOf<Long?>(100, 500, 70), merged.map { it.lastPlayedAt })
    }

    @Test
    fun `maxOfNullable is null only when both are`() {
        assertNull(IndexMerge.maxOfNullable(null, null))
        assertEquals(5, IndexMerge.maxOfNullable(5, null))
        assertEquals(5, IndexMerge.maxOfNullable(null, 5))
        assertEquals(7, IndexMerge.maxOfNullable(5, 7))
    }

    // The cross-provider merge itself is SQL (LibraryDao's *Merged queries group by these keys); what Kotlin
    // owns is that both providers' rows get the SAME key while keeping their own identities.

    @Test
    fun `two providers with the same album keep two rows that share one album key`() {
        val plex = IndexAlbum(itemId = "101", title = "OK Computer", artistItemId = "7", artistName = "Radiohead")
            .toEntity("plex:aaa", syncRun = 1)
        val jelly = IndexAlbum(itemId = "f00d", title = "OK Computer (OKNOTOK)".substringBefore(" ("),
            artistItemId = "a1", artistName = "radiohead").toEntity("jellyfin:bbb", syncRun = 1)
        assertNotEquals(plex.providerId to plex.itemId, jelly.providerId to jelly.itemId)
        assertEquals(plex.albumKey, jelly.albumKey)
        assertEquals(plex.artistKey, jelly.artistKey)
    }

    @Test
    fun `the same recording on two providers shares one track key`() {
        val a = IndexTrack(itemId = "1", title = "Airbag", artistName = "Radiohead", albumTitle = "OK Computer",
            discNo = 1, trackNo = 1).toEntity("plex:aaa", 1)
        val b = IndexTrack(itemId = "x", title = "Airbag", artistName = "Radiohead", albumTitle = "OK Computer",
            discNo = 1, trackNo = 1).toEntity("jellyfin:bbb", 1)
        val other = IndexTrack(itemId = "2", title = "Airbag", artistName = "Radiohead", albumTitle = "Live",
            discNo = 1, trackNo = 1).toEntity("plex:aaa", 1)
        assertEquals(a.trackKey, b.trackKey)
        assertNotEquals(a.trackKey, other.trackKey)
    }

    @Test
    fun `the same artist on two providers shares one artist key`() {
        val a = IndexArtist(itemId = "1", name = "Björk").toEntity("plex:aaa", 1)
        val b = IndexArtist(itemId = "2", name = "BJORK").toEntity("jellyfin:bbb", 1)
        assertEquals(a.artistKey, b.artistKey)
    }
}
