package com.crsmthw.sheliak.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/*
 * Every destination of the app, the library's tabs, and the pure rules that decide what the back stack holds
 * and where back goes inside the library. Pure Kotlin (no android.*, no Compose), so the rules are
 * unit-tested in NavKeysTest.
 */

/** First-run welcome. Replaced by [Library] once `intro_done` is set; never under another entry. */
@Serializable data object Intro : NavKey

/**
 * The library — the navigation suite (bar or rail) and its four tabs ([LibraryTab]) in ONE entry, and the
 * bottom of every back stack. Because the suite lives inside this entry, a screen pushed on top covers the
 * bar or rail along with the rest of the library, and a (predictive) back reveals it whole.
 */
@Serializable data object Library : NavKey

@Serializable data object Search : NavKey

@Serializable data object Settings : NavKey

/** Placeholder until the player lands; pushed by the player surface's `onRequestPlayer`. */
@Serializable data object Player : NavKey

/** Placeholder until the player lands; pushed from the player. */
@Serializable data object Queue : NavKey

/** Every key, so the saved-state module below and its test cannot drift from the key list. */
val AllNavKeys: List<NavKey> = listOf(Intro, Library, Search, Settings, Player, Queue)

/**
 * The library's four tabs, in suite order. Not navigation keys: the selected tab is saveable state inside the
 * [Library] entry, so switching tabs never touches the back stack.
 */
enum class LibraryTab { TRACKS, ALBUMS, ARTISTS, PLAYLISTS }

/** The tab the library opens on, and the one back returns to from the others. */
val HomeLibraryTab: LibraryTab = LibraryTab.TRACKS

/**
 * Where system back goes while the library shows [tab]: to [HomeLibraryTab] from any other tab (Lyra's "back
 * from a tab returns home"), or null when back is not the library's to handle — on the home tab (back leaves
 * the app) and whenever the Library entry is not [resumed].
 *
 * The [resumed] gate matters during a (predictive) pop from Search or Settings: the Library entry is composed
 * underneath as the incoming screen, and a tab handler registered mid-gesture would otherwise outrank the
 * navigation host's and switch the tab instead of popping the screen. Navigation 3 resumes an entry only
 * once it is on top and its transition has settled.
 */
fun libraryTabOnBack(tab: LibraryTab, resumed: Boolean): LibraryTab? =
    if (resumed && tab != HomeLibraryTab) HomeLibraryTab else null

/** The back stack a fresh start opens on: Intro until the user has finished or skipped it, else Library. */
fun startBackStack(introDone: Boolean): List<NavKey> = listOf(if (introDone) Library else Intro)

/**
 * The back stack once Intro is done: Intro is replaced, never kept under Library, so back from the library
 * leaves the app instead of returning to the welcome screen. Any other stack is returned unchanged.
 */
fun backStackAfterIntro(backStack: List<NavKey>): List<NavKey> =
    if (Intro in backStack) listOf(Library) else backStack

/**
 * Turns this back stack into [target] by editing it in place — trimming the tail, then overwriting or
 * appending only the positions that differ — instead of clearing and refilling it, so the stack is never
 * empty at any point (a navigation host must always have an entry to show) and unchanged entries keep their
 * identity, and with it their saved state.
 */
fun MutableList<NavKey>.replaceWith(target: List<NavKey>) {
    require(target.isNotEmpty()) { "A back stack can never be empty" }
    while (size > target.size) removeAt(lastIndex)
    target.forEachIndexed { index, key ->
        when {
            index >= size     -> add(key)
            this[index] != key -> this[index] = key
        }
    }
}

/**
 * The saved-state serializers for every key, registered explicitly. The default back-stack saver resolves a
 * key by class name through reflection (`Class.forName` + a reflective serializer lookup), which depends on R8
 * keeping exactly the right members; an explicit polymorphic module is resolved at compile time instead.
 */
val NavKeySerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Intro::class)
        subclass(Library::class)
        subclass(Search::class)
        subclass(Settings::class)
        subclass(Player::class)
        subclass(Queue::class)
    }
}
