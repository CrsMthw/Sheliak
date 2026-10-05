package com.crsmthw.sheliak.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavKeysTest {

    // ── Top-level keys ──────────────────────────────────────────────────────

    @Test
    fun `the four suite destinations are top level in suite order`() {
        assertEquals(listOf(Tracks, Albums, Artists, Playlists), TopLevelKeys)
        TopLevelKeys.forEach { assertTrue(isTopLevel(it), "$it") }
    }

    @Test
    fun `every other key is not top level`() {
        listOf(Intro, Search, Settings, Player, Queue).forEach { assertFalse(isTopLevel(it), "$it") }
        assertFalse(isTopLevel(null))
    }

    @Test
    fun `AllNavKeys lists every key exactly once`() {
        assertEquals(9, AllNavKeys.size)
        assertEquals(AllNavKeys.size, AllNavKeys.toSet().size)
        assertTrue(AllNavKeys.containsAll(TopLevelKeys))
    }

    // ── Tab selection ───────────────────────────────────────────────────────

    @Test
    fun `selecting Tracks leaves only Tracks`() {
        assertEquals(listOf<NavKey>(Tracks), backStackForTab(Tracks))
    }

    @Test
    fun `selecting another tab puts it on top of Tracks`() {
        assertEquals(listOf(Tracks, Albums), backStackForTab(Albums))
        assertEquals(listOf(Tracks, Artists), backStackForTab(Artists))
        assertEquals(listOf(Tracks, Playlists), backStackForTab(Playlists))
    }

    @Test
    fun `selecting a non-destination as a tab is refused`() {
        assertFailsWith<IllegalArgumentException> { backStackForTab(Settings) }
        assertFailsWith<IllegalArgumentException> { backStackForTab(Search) }
    }

    @Test
    fun `the selected tab is the nearest top-level key from the top`() {
        assertEquals(Tracks, selectedTab(listOf(Tracks)))
        assertEquals(Albums, selectedTab(listOf(Tracks, Albums)))
        assertEquals(Albums, selectedTab(listOf(Tracks, Albums, Search, Settings)))
        assertEquals(Tracks, selectedTab(listOf(Tracks, Search)))
    }

    @Test
    fun `the selected tab falls back to Tracks when no destination is on the stack`() {
        assertEquals(Tracks, selectedTab(listOf(Intro)))
        assertEquals(Tracks, selectedTab(emptyList()))
    }

    // ── Start and Intro ─────────────────────────────────────────────────────

    @Test
    fun `a fresh start opens Intro until it is done`() {
        assertEquals(listOf<NavKey>(Intro), startBackStack(introDone = false))
        assertEquals(listOf<NavKey>(Tracks), startBackStack(introDone = true))
    }

    @Test
    fun `finishing Intro replaces it with Tracks`() {
        assertEquals(listOf<NavKey>(Tracks), backStackAfterIntro(listOf(Intro)))
    }

    @Test
    fun `finishing Intro leaves a stack without Intro untouched`() {
        val stack = listOf(Tracks, Albums, Settings)
        assertSame(stack, backStackAfterIntro(stack))
    }

    // ── replaceWith ─────────────────────────────────────────────────────────

    @Test
    fun `replaceWith pushes a tab onto Tracks`() {
        val stack = mutableListOf<NavKey>(Tracks)
        stack.replaceWith(backStackForTab(Albums))
        assertEquals(listOf(Tracks, Albums), stack)
    }

    @Test
    fun `replaceWith swaps the tab on top of Tracks`() {
        val stack = mutableListOf(Tracks, Albums)
        stack.replaceWith(backStackForTab(Playlists))
        assertEquals(listOf(Tracks, Playlists), stack)
    }

    @Test
    fun `replaceWith pops back to Tracks`() {
        val stack = mutableListOf(Tracks, Artists)
        stack.replaceWith(backStackForTab(Tracks))
        assertEquals(listOf<NavKey>(Tracks), stack)
    }

    @Test
    fun `replaceWith replaces Intro in place`() {
        val stack = mutableListOf<NavKey>(Intro)
        stack.replaceWith(backStackAfterIntro(stack))
        assertEquals(listOf<NavKey>(Tracks), stack)
    }

    @Test
    fun `replaceWith never empties the stack on the way`() {
        val stack = ObservedList(mutableListOf(Tracks, Albums, Search, Settings))
        stack.replaceWith(listOf(Tracks))
        assertEquals(listOf<NavKey>(Tracks), stack)
        assertTrue(stack.minSizeSeen >= 1)
    }

    @Test
    fun `replaceWith does not rewrite positions that already match`() {
        val stack = ObservedList(mutableListOf(Tracks, Albums))
        stack.replaceWith(listOf(Tracks, Artists))
        assertEquals(listOf(Tracks, Artists), stack)
        assertEquals(listOf(1), stack.setIndices)
    }

    @Test
    fun `replaceWith refuses an empty target`() {
        assertFailsWith<IllegalArgumentException> { mutableListOf<NavKey>(Tracks).replaceWith(emptyList()) }
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
