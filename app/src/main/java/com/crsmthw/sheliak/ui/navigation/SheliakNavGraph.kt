package com.crsmthw.sheliak.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.crsmthw.sheliak.di.AppContainer
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.ui.components.ArtResolver
import com.crsmthw.sheliak.ui.components.LocalArtResolver
import com.crsmthw.sheliak.ui.screens.intro.IntroScreen
import com.crsmthw.sheliak.ui.screens.intro.IntroViewModel
import com.crsmthw.sheliak.ui.screens.intro.IntroViewModelFactory
import com.crsmthw.sheliak.ui.screens.library.LibraryShell
import com.crsmthw.sheliak.ui.screens.library.LibraryViewModel
import com.crsmthw.sheliak.ui.screens.library.LibraryViewModelFactory
import com.crsmthw.sheliak.ui.screens.library.detail.AlbumDetailScreen
import com.crsmthw.sheliak.ui.screens.library.detail.AlbumDetailViewModel
import com.crsmthw.sheliak.ui.screens.library.detail.AlbumDetailViewModelFactory
import com.crsmthw.sheliak.ui.screens.library.detail.ArtistDetailScreen
import com.crsmthw.sheliak.ui.screens.library.detail.ArtistDetailViewModel
import com.crsmthw.sheliak.ui.screens.library.detail.ArtistDetailViewModelFactory
import com.crsmthw.sheliak.ui.screens.library.detail.PlaylistDetailScreen
import com.crsmthw.sheliak.ui.screens.library.detail.PlaylistDetailViewModel
import com.crsmthw.sheliak.ui.screens.library.detail.PlaylistDetailViewModelFactory
import com.crsmthw.sheliak.ui.screens.player.PlayerScreen
import com.crsmthw.sheliak.ui.screens.queue.QueueScreen
import com.crsmthw.sheliak.ui.screens.search.SearchScreen
import com.crsmthw.sheliak.ui.screens.search.SearchViewModel
import com.crsmthw.sheliak.ui.screens.search.SearchViewModelFactory
import com.crsmthw.sheliak.ui.screens.settings.SettingsScreen
import com.crsmthw.sheliak.ui.screens.settings.SettingsViewModel
import com.crsmthw.sheliak.ui.screens.settings.SettingsViewModelFactory
import com.crsmthw.sheliak.ui.screens.sources.PlexSetupScreen
import com.crsmthw.sheliak.ui.screens.sources.PlexSetupViewModel
import com.crsmthw.sheliak.ui.screens.sources.PlexSetupViewModelFactory
import com.crsmthw.sheliak.ui.screens.sources.SourcesScreen
import com.crsmthw.sheliak.ui.screens.sources.SourcesViewModel
import com.crsmthw.sheliak.ui.screens.sources.SourcesViewModelFactory
import com.crsmthw.sheliak.util.SharedAxisSlide
import com.crsmthw.sheliak.util.sharedAxisXBackward
import com.crsmthw.sheliak.util.sharedAxisXBackwardSeekable
import com.crsmthw.sheliak.util.sharedAxisXForward
import kotlinx.coroutines.launch

/**
 * The shell's [SharedTransitionScope] — the one around the navigation host — for shared elements that pair
 * across destinations (the search FAB ↔ search bar morph, library card art ↔ detail hero, the player art). Null
 * outside the shell, which turns every such element into a plain one.
 */
val LocalNavSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The back stack's saved-state format: every key registered explicitly (see [NavKeySerializersModule]). */
private val BackStackSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = NavKeySerializersModule
}

/**
 * The app shell: the back stack, the art resolver, the player surface host and the Navigation 3 host, in that
 * nesting —
 *
 * ```
 * SharedTransitionLayout            (one scope for every cross-destination shared element)
 *   LocalArtResolver                (ArtRef → the live provider's Coil model, for every cover in the app)
 *     PlayerSurfaceHost             (mini bar + pop-out; told the top entry and whether it may show over it)
 *       NavDisplay                  (the destinations, M3 shared axis X on push / pop / predictive pop)
 *         Library                   (LibraryShell: the navigation suite + the four tabs, one entry)
 *         AlbumDetail · ArtistDetail · PlaylistDetail · Search · Settings · Sources · PlexSetup
 *         Player · Queue · Intro
 * ```
 *
 * The navigation suite (bar / rail) is NOT around the host: it lives inside the Library entry, so a pushed
 * screen covers it with the rest of the library and a predictive back shows the whole library, suite included,
 * following the finger. The tabs — and a two-pane tab's selection — are state inside that entry, never keys.
 *
 * Back-stack rules are pure functions in NavKeys.kt: the stack always starts with Library, everything else
 * pushes on top, and the player surface shows only where [playerSurfaceAllowed] says. The start is Intro until
 * [introDone], after which Intro is replaced by Library — reacting to the stored flag rather than to the tap, so
 * the write has landed before Intro leaves; Intro's "Connect a Plex server" goes on to [PlexSetup] over Library.
 * Every entry's ViewModel comes from a factory over [container] and lives in that entry's own store.
 *
 * @param introDone the stored `intro_done` flag; the caller composes the shell only once it is known, because
 *   the back stack takes its start destination exactly once.
 */
@Composable
fun SheliakNavGraph(
    container: AppContainer,
    introDone: Boolean,
) {
    val backStack = rememberNavBackStack(
        BackStackSavedStateConfiguration,
        *startBackStack(introDone).toTypedArray(),
    )
    val navigator = remember(backStack) { ShellNavigator(backStack) }

    // Where Intro's primary action asked to go once Intro is done (the Plex setup), saveable across a rotation
    // between the tap and the stored flag landing.
    var afterIntro by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(introDone) {
        if (introDone) {
            backStack.replaceWith(backStackAfterIntro(backStack.toList(), next = PlexSetup.takeIf { afterIntro }))
            afterIntro = false
        }
    }

    // The shell's own scope, not Intro's: Intro leaves composition as soon as the flag lands, and a write
    // launched from a scope that dies with it could be cancelled half-way.
    val shellScope = rememberCoroutineScope()

    // M3 shared axis X slides 30dp. The transition lambdas below have no density, so convert it here.
    val slidePx = with(LocalDensity.current) { SharedAxisSlide.roundToPx() }

    // ArtRef → Coil model through the live provider. Keyed by the provider list, so a cover asked for before its
    // provider was built (a cold start) resolves again once it is.
    val providers by container.providerRegistry.providers.collectAsStateWithLifecycle()
    val artResolver = remember(providers) {
        ArtResolver { ref, px -> container.providerRegistry.current(ref.providerId)?.artModel(ref, px) }
    }

    val topKey = backStack.lastOrNull()
    val openAlbum: (TrackKey) -> Unit = { key -> navigator.push(albumDetailOf(key)) }
    val openArtist: (TrackKey) -> Unit = { key -> navigator.push(artistDetailOf(key)) }
    val openPlaylist: (Long) -> Unit = { id -> navigator.push(PlaylistDetail(id)) }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalNavSharedTransitionScope provides this,
            LocalArtResolver provides artResolver,
        ) {
            PlayerSurfaceHost(
                player         = container.playerStateManager,
                topKey         = topKey,
                surfaceAllowed = playerSurfaceAllowed(topKey),
                onOpenPlayer   = { navigator.push(Player) },
                onOpenQueue    = { navigator.push(Queue) },
                modifier       = Modifier.fillMaxSize(),
            ) { _ ->
                NavDisplay(
                    backStack                   = backStack,
                    modifier                    = Modifier.fillMaxSize(),
                    // The system back (button, gesture, predictive gesture) is never debounced: a
                    // predictive commit that was ignored would leave the host showing a screen the
                    // back stack still holds. Only on-screen back arrows go through the debounce.
                    onBack                      = navigator::popFromSystem,
                    entryDecorators             = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    sharedTransitionScope       = this@SharedTransitionLayout,
                    transitionSpec              = { sharedAxisXForward(slidePx) },
                    popTransitionSpec           = { sharedAxisXBackward(slidePx) },
                    // Passed explicitly: the library default for the back GESTURE is a scale-down. The
                    // gesture gets the pop's slides with a CROSSFADE instead of M3's sequential fades, so
                    // the destination is visible from the first pixel of the swipe (see Motion.kt).
                    predictivePopTransitionSpec = { sharedAxisXBackwardSeekable(slidePx) },
                    entryProvider               = entryProvider {
                        entry<Intro> {
                            val viewModel: IntroViewModel = viewModel(factory = IntroViewModelFactory(container))
                            IntroScreen(
                                viewModel     = viewModel,
                                onSkip        = {
                                    shellScope.launch { container.settingsRepository.setIntroDone(true) }
                                },
                                onConnectPlex = {
                                    afterIntro = true
                                    shellScope.launch { container.settingsRepository.setIntroDone(true) }
                                },
                            )
                        }
                        entry<Library> {
                            val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(container))
                            LibraryShell(
                                viewModel      = viewModel,
                                onOpenSearch   = { navigator.push(Search) },
                                onOpenSettings = { navigator.push(Settings) },
                                onAddSource    = { navigator.push(PlexSetup) },
                                onOpenAlbum    = openAlbum,
                                onOpenArtist   = openArtist,
                                onOpenPlaylist = openPlaylist,
                            )
                        }
                        entry<AlbumDetail> { key ->
                            val album = key.albumKey
                            val viewModel: AlbumDetailViewModel =
                                viewModel(factory = AlbumDetailViewModelFactory(container, album))
                            AlbumDetailScreen(
                                albumKey     = album,
                                viewModel    = viewModel,
                                onBack       = navigator::popFromUi,
                                onOpenArtist = openArtist,
                            )
                        }
                        entry<ArtistDetail> { key ->
                            val artist = key.artistKey
                            val viewModel: ArtistDetailViewModel =
                                viewModel(factory = ArtistDetailViewModelFactory(container, artist))
                            ArtistDetailScreen(
                                artistKey   = artist,
                                viewModel   = viewModel,
                                onBack      = navigator::popFromUi,
                                onOpenAlbum = openAlbum,
                            )
                        }
                        entry<PlaylistDetail> { key ->
                            val viewModel: PlaylistDetailViewModel =
                                viewModel(factory = PlaylistDetailViewModelFactory(container, key.id))
                            PlaylistDetailScreen(
                                playlistId   = key.id,
                                viewModel    = viewModel,
                                onBack       = navigator::popFromUi,
                                onOpenAlbum  = openAlbum,
                                onOpenArtist = openArtist,
                            )
                        }
                        entry<Search> {
                            val viewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(container))
                            SearchScreen(
                                viewModel      = viewModel,
                                onBack         = navigator::popFromUi,
                                onOpenAlbum    = openAlbum,
                                onOpenArtist   = openArtist,
                                onOpenPlaylist = openPlaylist,
                            )
                        }
                        entry<Settings> {
                            val viewModel: SettingsViewModel =
                                viewModel(factory = SettingsViewModelFactory(container))
                            SettingsScreen(
                                viewModel     = viewModel,
                                onBack        = navigator::popFromUi,
                                onOpenSources = { navigator.push(Sources) },
                            )
                        }
                        entry<Sources> {
                            val viewModel: SourcesViewModel = viewModel(factory = SourcesViewModelFactory(container))
                            SourcesScreen(
                                viewModel = viewModel,
                                onBack    = navigator::popFromUi,
                                onAddPlex = { navigator.push(PlexSetup) },
                            )
                        }
                        entry<PlexSetup> {
                            val viewModel: PlexSetupViewModel =
                                viewModel(factory = PlexSetupViewModelFactory(container))
                            PlexSetupScreen(
                                viewModel  = viewModel,
                                onBack     = navigator::popFromUi,
                                onFinished = { navigator.popIfTop(PlexSetup) },
                            )
                        }
                        entry<Player> {
                            PlayerScreen(
                                player      = container.playerStateManager,
                                onBack      = navigator::popFromUi,
                                onOpenQueue = { navigator.push(Queue) },
                            )
                        }
                        entry<Queue> {
                            QueueScreen(
                                player = container.playerStateManager,
                                onBack = navigator::popFromUi,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Every back-stack edit the shell's controls make. Pushes and on-screen back arrows share one debounce, so a
 * double tap cannot push a screen twice or pop two screens. Not snapshot state: it is only read and written
 * from callbacks.
 */
private class ShellNavigator(private val backStack: MutableList<NavKey>) {

    private var lastNavAt = 0L

    private fun debounced(action: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastNavAt >= NAV_DEBOUNCE_MILLIS) {
            lastNavAt = now
            action()
        }
    }

    fun push(key: NavKey) = debounced {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    fun popFromUi() = debounced { popFromSystem() }

    fun popFromSystem() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /**
     * Pops [key] only while it is on top — a flow finishing on its own (the Plex setup) — so a repeated
     * "finished" can never pop a second screen. Not debounced: it is not a tap.
     */
    fun popIfTop(key: NavKey) {
        if (backStack.lastOrNull() == key) popFromSystem()
    }

    private companion object {
        /** Longer than a double tap, shorter than a deliberate second navigation. */
        const val NAV_DEBOUNCE_MILLIS = 350L
    }
}
