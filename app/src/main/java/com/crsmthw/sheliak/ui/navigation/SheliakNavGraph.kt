package com.crsmthw.sheliak.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.crsmthw.sheliak.di.AppContainer
import com.crsmthw.sheliak.ui.screens.intro.IntroScreen
import com.crsmthw.sheliak.ui.screens.library.AlbumsScreen
import com.crsmthw.sheliak.ui.screens.library.ArtistsScreen
import com.crsmthw.sheliak.ui.screens.library.PlaylistsScreen
import com.crsmthw.sheliak.ui.screens.library.TracksScreen
import com.crsmthw.sheliak.ui.screens.player.PlayerScreen
import com.crsmthw.sheliak.ui.screens.queue.QueueScreen
import com.crsmthw.sheliak.ui.screens.search.SearchScreen
import com.crsmthw.sheliak.ui.screens.settings.SettingsScreen
import com.crsmthw.sheliak.ui.screens.settings.SettingsViewModel
import com.crsmthw.sheliak.ui.screens.settings.SettingsViewModelFactory
import com.crsmthw.sheliak.util.screenTransitionSpec
import kotlinx.coroutines.launch

/**
 * The shell's [SharedTransitionScope] — the one around the navigation host — for shared elements that pair
 * across destinations (the search FAB ↔ search bar morph today, the player art later). Null outside the shell,
 * which turns every such element into a plain one.
 */
val LocalNavSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The back stack's saved-state format: every key registered explicitly (see [NavKeySerializersModule]). */
private val BackStackSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = NavKeySerializersModule
}

/**
 * The app shell: the back stack, the navigation suite, the player surface host and the Navigation 3 host, in
 * that nesting —
 *
 * ```
 * SheliakNavigationSuite            (bar / rail, or nothing off the four destinations)
 *   SharedTransitionLayout          (one scope for every cross-destination shared element)
 *     PlayerSurfaceHost             (mini bar + pop-out later; never over the bar or the rail)
 *       NavDisplay                  (the destinations, one finite push / pop transition)
 * ```
 *
 * Back-stack rules are pure functions in NavKeys.kt: the stack always starts with Tracks, a tab is `[Tracks]`
 * or `[Tracks, tab]`, and Search / Settings / Player / Queue push on top. The start is Intro until
 * [introDone], after which Intro is replaced by Tracks — reacting to the stored flag rather than to the skip
 * tap, so the write has landed before Intro (and anything scoped to it) leaves.
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

    LaunchedEffect(introDone) {
        if (introDone) backStack.replaceWith(backStackAfterIntro(backStack.toList()))
    }

    // The shell's own scope, not Intro's: Intro leaves composition as soon as the flag lands, and a write
    // launched from a scope that dies with it could be cancelled half-way.
    val shellScope = rememberCoroutineScope()

    val width = currentWindowWidth()
    val searchFab: @Composable BoxScope.() -> Unit =
        if (searchMorphEnabled(width)) {
            { SearchFab(onClick = { navigator.push(Search) }) }
        } else {
            {}
        }

    SheliakNavigationSuite(
        layout       = suiteLayoutFor(width, backStack.lastOrNull()),
        selectedTab  = selectedTab(backStack),
        onSelectTab  = navigator::selectTab,
        onOpenSearch = { navigator.push(Search) },
    ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalNavSharedTransitionScope provides this) {
                PlayerSurfaceHost(
                    onOpenPlayer = { navigator.push(Player) },
                    modifier     = Modifier.fillMaxSize(),
                ) {
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
                        transitionSpec              = { pushTransition() },
                        popTransitionSpec           = { popTransition() },
                        // Passed explicitly: the library default for the back GESTURE is a scale-down, not
                        // the pop slide, so a gesture back and a button back would animate differently.
                        predictivePopTransitionSpec = { popTransition() },
                        entryProvider               = entryProvider {
                            entry<Intro> {
                                IntroScreen(
                                    onSkip = {
                                        shellScope.launch { container.settingsRepository.setIntroDone(true) }
                                    },
                                )
                            }
                            entry<Tracks> {
                                TracksScreen(
                                    onOpenSettings = { navigator.push(Settings) },
                                    floatingAction = searchFab,
                                )
                            }
                            entry<Albums> {
                                AlbumsScreen(
                                    onOpenSettings = { navigator.push(Settings) },
                                    floatingAction = searchFab,
                                )
                            }
                            entry<Artists> {
                                ArtistsScreen(
                                    onOpenSettings = { navigator.push(Settings) },
                                    floatingAction = searchFab,
                                )
                            }
                            entry<Playlists> {
                                PlaylistsScreen(
                                    onOpenSettings = { navigator.push(Settings) },
                                    floatingAction = searchFab,
                                )
                            }
                            entry<Search> {
                                SearchScreen(onBack = navigator::popFromUi)
                            }
                            entry<Settings> {
                                val viewModel: SettingsViewModel =
                                    viewModel(factory = SettingsViewModelFactory(container))
                                SettingsScreen(viewModel = viewModel, onBack = navigator::popFromUi)
                            }
                            entry<Player> {
                                PlayerScreen(
                                    onBack      = navigator::popFromUi,
                                    onOpenQueue = { navigator.push(Queue) },
                                )
                            }
                            entry<Queue> {
                                QueueScreen(onBack = navigator::popFromUi)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Every back-stack edit the shell's controls make. Pushes and on-screen back arrows share one debounce, so a
 * double tap cannot push a screen twice or pop two screens; tab selection edits the stack in place (see
 * [replaceWith]) and needs none. Not snapshot state: it is only read and written from callbacks.
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

    fun selectTab(tab: NavKey) {
        backStack.replaceWith(backStackForTab(tab))
    }

    private companion object {
        /** Longer than a double tap, shorter than a deliberate second navigation. */
        const val NAV_DEBOUNCE_MILLIS = 350L
    }
}

/*
 * The navigation transitions: a quarter-width slide plus a fade, all on the ONE finite spec (util/Motion.kt),
 * so the slide, the fade and any shared-element morph land on the same frame and the outgoing entry is
 * disposed the instant the motion stops. Pop mirrors push; the predictive (gesture) pop is the same as pop.
 */

private fun pushTransition(): ContentTransform =
    (slideInHorizontally(screenTransitionSpec()) { it / 4 } + fadeIn(screenTransitionSpec()))
        .togetherWith(slideOutHorizontally(screenTransitionSpec()) { -(it / 4) } + fadeOut(screenTransitionSpec()))

private fun popTransition(): ContentTransform =
    (slideInHorizontally(screenTransitionSpec()) { -(it / 4) } + fadeIn(screenTransitionSpec()))
        .togetherWith(slideOutHorizontally(screenTransitionSpec()) { it / 4 } + fadeOut(screenTransitionSpec()))
