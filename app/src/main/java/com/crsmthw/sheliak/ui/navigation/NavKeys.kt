package com.crsmthw.sheliak.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/*
 * Every destination of the app, and the pure rules that decide what the back stack holds. Pure Kotlin (no
 * android.*, no Compose), so the rules are unit-tested in NavKeysTest.
 */

/** First-run welcome. Replaced by [Tracks] once `intro_done` is set; never under another entry. */
@Serializable data object Intro : NavKey

/** The default destination and the bottom of every back stack. */
@Serializable data object Tracks : NavKey

@Serializable data object Albums : NavKey

@Serializable data object Artists : NavKey

@Serializable data object Playlists : NavKey

@Serializable data object Search : NavKey

@Serializable data object Settings : NavKey

/** Placeholder until the player lands; pushed by the player surface's `onRequestPlayer`. */
@Serializable data object Player : NavKey

/** Placeholder until the player lands; pushed from the player. */
@Serializable data object Queue : NavKey

/** The four navigation-suite destinations, in suite order. */
val TopLevelKeys: List<NavKey> = listOf(Tracks, Albums, Artists, Playlists)

/** Every key, so the saved-state module below and its test cannot drift from the key list. */
val AllNavKeys: List<NavKey> = listOf(Intro, Tracks, Albums, Artists, Playlists, Search, Settings, Player, Queue)

/** Whether [key] is one of the four suite destinations — the only screens that show the suite. */
fun isTopLevel(key: NavKey?): Boolean = key != null && key in TopLevelKeys

/**
 * The whole back stack after selecting [tab] in the suite. It always starts with [Tracks], so back from any
 * other tab returns to Tracks and back from Tracks leaves the app — tabs never stack on each other.
 */
fun backStackForTab(tab: NavKey): List<NavKey> {
    require(isTopLevel(tab)) { "$tab is not a navigation-suite destination" }
    return if (tab == Tracks) listOf(Tracks) else listOf(Tracks, tab)
}

/**
 * The suite item to show as selected for [backStack]: the nearest top-level key from the top. Non-root
 * screens hide the suite, but the answer stays defined so the suite never flashes a wrong selection while a
 * pushed screen animates away.
 */
fun selectedTab(backStack: List<NavKey>): NavKey = backStack.lastOrNull { isTopLevel(it) } ?: Tracks

/** The back stack a fresh start opens on: Intro until the user has finished or skipped it, else Tracks. */
fun startBackStack(introDone: Boolean): List<NavKey> = listOf(if (introDone) Tracks else Intro)

/**
 * The back stack once Intro is done: Intro is replaced, never kept under Tracks, so back from Tracks leaves
 * the app instead of returning to the welcome screen. Any other stack is returned unchanged.
 */
fun backStackAfterIntro(backStack: List<NavKey>): List<NavKey> =
    if (Intro in backStack) listOf(Tracks) else backStack

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
        subclass(Tracks::class)
        subclass(Albums::class)
        subclass(Artists::class)
        subclass(Playlists::class)
        subclass(Search::class)
        subclass(Settings::class)
        subclass(Player::class)
        subclass(Queue::class)
    }
}
