package com.crsmthw.sheliak.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.crsmthw.sheliak.domain.TrackKey
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
 * The library — the navigation suite (bar or rail) and its five tabs ([LibraryTab], Search among them) in ONE
 * entry, and the bottom of every back stack. Because the suite lives inside this entry, a screen pushed on top
 * covers the bar or rail along with the rest of the library, and a (predictive) back reveals it whole.
 */
@Serializable data object Library : NavKey

@Serializable data object Settings : NavKey

/** The full player; pushed by the player surface (the mini bar) and from nowhere else. */
@Serializable data object Player : NavKey

/** The play queue; pushed from the player and the player surface. */
@Serializable data object Queue : NavKey

/**
 * One album, pushed from the library below 600dp (at 600dp and up the Albums tab shows it in its own right pane
 * instead), from the Search tab, from an artist and from a track's "Go to album". [providerId] / [itemId] are the
 * album's `TrackKey`, flattened so the key stays a plain serializable value.
 */
@Serializable data class AlbumDetail(val providerId: String, val itemId: String) : NavKey

/** One artist and their albums; pushed like [AlbumDetail]. */
@Serializable data class ArtistDetail(val providerId: String, val itemId: String) : NavKey

/** One playlist ([id] is its local row id, stable across syncs); pushed like [AlbumDetail]. */
@Serializable data class PlaylistDetail(val id: Long) : NavKey

/** The album an [AlbumDetail] key opens. */
val AlbumDetail.albumKey: TrackKey get() = TrackKey(providerId, itemId)

/** The artist an [ArtistDetail] key opens. */
val ArtistDetail.artistKey: TrackKey get() = TrackKey(providerId, itemId)

/** The detail key for an album. */
fun albumDetailOf(album: TrackKey): AlbumDetail = AlbumDetail(album.providerId, album.itemId)

/** The detail key for an artist. */
fun artistDetailOf(artist: TrackKey): ArtistDetail = ArtistDetail(artist.providerId, artist.itemId)

/** Settings → Sources: the configured sources, their sync state, Sync now / Remove, and "Add Plex server". */
@Serializable data object Sources : NavKey

/**
 * The Plex sign-in and setup flow — ONE entry with its own steps (PIN, server, local network, libraries). Pushed
 * from Sources, from the library's empty state and after Intro; finishing pops it, which lands on whatever pushed
 * it.
 */
@Serializable data object PlexSetup : NavKey

/**
 * Every key, so the saved-state module below and its test cannot drift from the key list. The detail keys carry
 * data, so a sample instance of each stands in for its class.
 */
val AllNavKeys: List<NavKey> = listOf(
    Intro, Library, Settings, Player, Queue,
    AlbumDetail(providerId = "plex:sample", itemId = "1"),
    ArtistDetail(providerId = "plex:sample", itemId = "2"),
    PlaylistDetail(id = 3L),
    Sources, PlexSetup,
)

/**
 * Whether the floating player surface (the mini bar and the pop-out panel) may show over the entry on top of the
 * back stack: on the library (all five tabs, Search included) and the detail screens, which are where music is
 * browsed; never over Intro, Settings, Sources or the Plex setup (nothing to browse there), nor over the full
 * player and the queue (which ARE the player). Null — no entry yet — is false.
 */
fun playerSurfaceAllowed(top: NavKey?): Boolean = when (top) {
    Library                                            -> true
    is AlbumDetail, is ArtistDetail, is PlaylistDetail -> true
    else                                               -> false
}

/**
 * The library's five tabs, in suite order — the order of the bar's and the rails' items, Search in the CENTRE
 * ([SuiteActionTab]). Not navigation keys: the selected tab is saveable state inside the [Library] entry, so
 * switching tabs never touches the back stack.
 */
enum class LibraryTab { TRACKS, ALBUMS, SEARCH, ARTISTS, PLAYLISTS }

/**
 * The tab the suite draws as its action — inside an M3 Expressive shape instead of the selection pill — and
 * places in the centre of the bar and the rails: Search.
 */
val SuiteActionTab: LibraryTab = LibraryTab.SEARCH

/** What re-tapping the selected tab in the suite does. */
enum class TabReselect {
    /** The tab's list jumps back to the top. */
    ScrollToTop,

    /** The search field takes the focus and the keyboard rises (the first tap only shows the page). */
    FocusSearchField,
}

/** What re-selecting [tab] does: Search focuses its field, every other tab scrolls its list to the top. */
fun tabReselectFor(tab: LibraryTab): TabReselect =
    if (tab == LibraryTab.SEARCH) TabReselect.FocusSearchField else TabReselect.ScrollToTop

/** The tab the library opens on, and the one back returns to from the others. */
val HomeLibraryTab: LibraryTab = LibraryTab.TRACKS

/**
 * Where system back goes while the library shows [tab]: to [HomeLibraryTab] from any other tab (Lyra's "back
 * from a tab returns home"), or null when back is not the library's to handle — on the home tab (back leaves
 * the app) and whenever the Library entry is not [resumed].
 *
 * The [resumed] gate matters during a (predictive) pop from a detail or Settings: the Library entry is composed
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
 * leaves the app instead of returning to the welcome screen — with [next] pushed on top when Intro's primary
 * action asked to go on somewhere (the Plex setup, when no source exists yet). Any stack without Intro is
 * returned unchanged.
 */
fun backStackAfterIntro(backStack: List<NavKey>, next: NavKey? = null): List<NavKey> =
    if (Intro in backStack) listOfNotNull(Library, next) else backStack

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
        subclass(Settings::class)
        subclass(Player::class)
        subclass(Queue::class)
        subclass(AlbumDetail::class)
        subclass(ArtistDetail::class)
        subclass(PlaylistDetail::class)
        subclass(Sources::class)
        subclass(PlexSetup::class)
    }
}
