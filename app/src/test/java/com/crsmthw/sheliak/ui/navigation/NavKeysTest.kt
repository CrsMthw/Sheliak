package com.crsmthw.sheliak.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavKeysTest {

    // ── Keys ────────────────────────────────────────────────────────────────

    @Test
    fun `AllNavKeys lists every key exactly once`() {
        assertEquals(
            listOf(Intro, Library, Search, Settings, Player, Queue, Sources, PlexSetup),
            AllNavKeys.filter { it !is AlbumDetail && it !is ArtistDetail && it !is PlaylistDetail },
        )
        assertEquals(1, AllNavKeys.count { it is AlbumDetail })
        assertEquals(1, AllNavKeys.count { it is ArtistDetail })
        assertEquals(1, AllNavKeys.count { it is PlaylistDetail })
        assertEquals(AllNavKeys.size, AllNavKeys.map { it::class }.toSet().size)
    }

    @Test
    fun `detail keys round-trip their item keys`() {
        val album = TrackKey("plex:abc", "101")
        val artist = TrackKey("plex:abc", "7")
        assertEquals(album, albumDetailOf(album).albumKey)
        assertEquals(artist, artistDetailOf(artist).artistKey)
        assertEquals(AlbumDetail("plex:abc", "101"), albumDetailOf(album))
    }

    @Test
    fun `detail keys with the same item are equal, so a double push is a no-op`() {
        assertEquals(AlbumDetail("p", "1"), AlbumDetail("p", "1"))
        assertEquals(PlaylistDetail(4L), PlaylistDetail(4L))
    }

    // ── Player surface ──────────────────────────────────────────────────────

    @Test
    fun `the player surface shows on the library, search and the details`() {
        listOf(Library, Search, AlbumDetail("p", "1"), ArtistDetail("p", "2"), PlaylistDetail(3L)).forEach { key ->
            assertTrue(playerSurfaceAllowed(key), "$key")
        }
    }

    @Test
    fun `the player surface never shows over intro, settings, sources, setup, the player or the queue`() {
        listOf(Intro, Settings, Sources, PlexSetup, Player, Queue).forEach { key ->
            assertFalse(playerSurfaceAllowed(key), "$key")
        }
    }

    @Test
    fun `the player surface stays hidden with no entry`() {
        assertFalse(playerSurfaceAllowed(null))
    }

    @Test
    fun `every key is decided by the player surface rule`() {
        val shown = AllNavKeys.filter(::playerSurfaceAllowed).map { it::class }.toSet()
        assertEquals(setOf(Library::class, Search::class, AlbumDetail::class, ArtistDetail::class, PlaylistDetail::class), shown)
    }

    // ── Library tabs ────────────────────────────────────────────────────────

    @Test
    fun `the library tabs are in suite order`() {
        assertEquals(
            listOf(LibraryTab.TRACKS, LibraryTab.ALBUMS, LibraryTab.ARTISTS, LibraryTab.PLAYLISTS),
            LibraryTab.entries,
        )
    }

    @Test
    fun `the library opens on Tracks`() {
        assertEquals(LibraryTab.TRACKS, HomeLibraryTab)
    }

    @Test
    fun `back from any other tab returns to Tracks`() {
        listOf(LibraryTab.ALBUMS, LibraryTab.ARTISTS, LibraryTab.PLAYLISTS).forEach { tab ->
            assertEquals(LibraryTab.TRACKS, libraryTabOnBack(tab, resumed = true), "$tab")
        }
    }

    @Test
    fun `back on Tracks is not the library's to handle`() {
        assertNull(libraryTabOnBack(LibraryTab.TRACKS, resumed = true))
    }

    @Test
    fun `back is never the library's while its entry is not resumed`() {
        LibraryTab.entries.forEach { tab -> assertNull(libraryTabOnBack(tab, resumed = false), "$tab") }
    }

    // ── Start and Intro ─────────────────────────────────────────────────────

    @Test
    fun `a fresh start opens Intro until it is done`() {
        assertEquals(listOf<NavKey>(Intro), startBackStack(introDone = false))
        assertEquals(listOf<NavKey>(Library), startBackStack(introDone = true))
    }

    @Test
    fun `finishing Intro replaces it with Library`() {
        assertEquals(listOf<NavKey>(Library), backStackAfterIntro(listOf(Intro)))
    }

    @Test
    fun `finishing Intro towards the Plex setup pushes it onto Library`() {
        assertEquals(listOf(Library, PlexSetup), backStackAfterIntro(listOf(Intro), next = PlexSetup))
    }

    @Test
    fun `finishing Intro leaves a stack without Intro untouched`() {
        val stack = listOf(Library, Settings)
        assertSame(stack, backStackAfterIntro(stack))
    }

    // ── replaceWith ─────────────────────────────────────────────────────────

    @Test
    fun `replaceWith pushes onto Library`() {
        val stack = mutableListOf<NavKey>(Library)
        stack.replaceWith(listOf(Library, Search))
        assertEquals(listOf(Library, Search), stack)
    }

    @Test
    fun `replaceWith swaps the screen on top of Library`() {
        val stack = mutableListOf(Library, Search)
        stack.replaceWith(listOf(Library, Settings))
        assertEquals(listOf(Library, Settings), stack)
    }

    @Test
    fun `replaceWith pops back to Library`() {
        val stack = mutableListOf(Library, Player, Queue)
        stack.replaceWith(listOf(Library))
        assertEquals(listOf<NavKey>(Library), stack)
    }

    @Test
    fun `replaceWith replaces Intro in place`() {
        val stack = mutableListOf<NavKey>(Intro)
        stack.replaceWith(backStackAfterIntro(stack))
        assertEquals(listOf<NavKey>(Library), stack)
    }

    @Test
    fun `replaceWith never empties the stack on the way`() {
        val stack = ObservedList(mutableListOf(Library, Player, Queue, Settings))
        stack.replaceWith(listOf(Library))
        assertEquals(listOf<NavKey>(Library), stack)
        assertTrue(stack.minSizeSeen >= 1)
    }

    @Test
    fun `replaceWith does not rewrite positions that already match`() {
        val stack = ObservedList(mutableListOf(Library, Search))
        stack.replaceWith(listOf(Library, Settings))
        assertEquals(listOf(Library, Settings), stack)
        assertEquals(listOf(1), stack.setIndices)
    }

    @Test
    fun `replaceWith turns Intro into Library and the Plex setup`() {
        val stack = mutableListOf<NavKey>(Intro)
        stack.replaceWith(backStackAfterIntro(stack, next = PlexSetup))
        assertEquals(listOf(Library, PlexSetup), stack)
    }

    @Test
    fun `replaceWith refuses an empty target`() {
        assertFailsWith<IllegalArgumentException> { mutableListOf<NavKey>(Library).replaceWith(emptyList()) }
    }

    // ── Saved state ─────────────────────────────────────────────────────────

    @Test
    fun `every key round-trips through the explicit serializers module`() {
        val json = Json { serializersModule = NavKeySerializersModule }
        val serializer = PolymorphicSerializer(NavKey::class)
        AllNavKeys.forEach { key ->
            val encoded = json.encodeToString(serializer, key)
            val decoded = json.decodeFromString(serializer, encoded)
            // Objects come back as the same instance; the detail keys as an equal value.
            val carriesData = key is AlbumDetail || key is ArtistDetail || key is PlaylistDetail
            if (carriesData) assertEquals(key, decoded, encoded) else assertSame(key, decoded, encoded)
        }
    }

    /** A list that records its smallest size and every `set` index, to check how it was edited. */
    private class ObservedList(private val delegate: MutableList<NavKey>) : AbstractMutableList<NavKey>() {
        var minSizeSeen = delegate.size
            private set
        val setIndices = mutableListOf<Int>()

        override val size: Int get() = delegate.size
        override fun get(index: Int): NavKey = delegate[index]
        override fun add(index: Int, element: NavKey) = delegate.add(index, element)
        override fun set(index: Int, element: NavKey): NavKey {
            setIndices += index
            return delegate.set(index, element)
        }
        override fun removeAt(index: Int): NavKey =
            delegate.removeAt(index).also { minSizeSeen = minOf(minSizeSeen, delegate.size) }
    }
}
