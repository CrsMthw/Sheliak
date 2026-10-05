package com.crsmthw.sheliak.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavKeysTest {

    // ── Keys ────────────────────────────────────────────────────────────────

    @Test
    fun `AllNavKeys lists every key exactly once`() {
        assertEquals(listOf(Intro, Library, Search, Settings, Player, Queue), AllNavKeys)
        assertEquals(AllNavKeys.size, AllNavKeys.toSet().size)
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
            assertSame(key, json.decodeFromString(serializer, encoded), encoded)
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
