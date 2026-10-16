package com.crsmthw.sheliak.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.LargeBarMinPaneHeight
import com.crsmthw.sheliak.util.press

/** One library tab in the suite: its tab, label and the outlined / filled icon pair. */
private data class SuiteDestination(
    val tab         : LibraryTab,
    @param:StringRes val labelRes: Int,
    val icon        : ImageVector,
    val selectedIcon: ImageVector,
)

/** The suite's item for [tab]. */
private fun suiteDestinationOf(tab: LibraryTab): SuiteDestination = when (tab) {
    LibraryTab.TRACKS    -> SuiteDestination(tab, R.string.nav_tracks,    Icons.Outlined.LibraryMusic,           Icons.Filled.LibraryMusic)
    LibraryTab.ALBUMS    -> SuiteDestination(tab, R.string.nav_albums,    Icons.Outlined.Album,                  Icons.Filled.Album)
    LibraryTab.SEARCH    -> SuiteDestination(tab, R.string.nav_search,    Icons.Outlined.Search,                 Icons.Filled.Search)
    LibraryTab.ARTISTS   -> SuiteDestination(tab, R.string.nav_artists,   Icons.Outlined.Person,                 Icons.Filled.Person)
    LibraryTab.PLAYLISTS -> SuiteDestination(tab, R.string.nav_playlists, Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Filled.QueueMusic)
}

/**
 * The current window's width class, read the standard way (M3 adaptive window size class). The V2 reader
 * also reports the large and extra-large buckets (1200dp, 1600dp); [windowWidthOf] folds both into expanded.
 */
@Composable
fun currentWindowWidth(): WindowWidth =
    windowWidthOf(currentWindowAdaptiveInfoV2().windowSizeClass.minWidthDp)

private fun SuiteLayout.toNavigationSuiteType(): NavigationSuiteType = when (this) {
    SuiteLayout.Bar           -> NavigationSuiteType.ShortNavigationBarCompact
    SuiteLayout.CollapsedRail -> NavigationSuiteType.WideNavigationRailCollapsed
    SuiteLayout.ExpandedRail  -> NavigationSuiteType.WideNavigationRailExpanded
}

/**
 * The library's navigation suite: Material's [NavigationSuiteScaffold] with the five tabs around the library's
 * content ([content]), in the order [suiteItemOrder] gives [layout] — Tracks, Albums, **Search**, Artists,
 * Playlists on the bar (Search in the centre); Tracks, Albums, Artists, Playlists, **Search** on both rails (Search
 * last, at the bottom of the group). It lives INSIDE the Library navigation entry (`LibraryShell`), so a pushed
 * screen covers it like any other part of the library and a predictive back reveals it whole, and the content
 * beside it never moves when a screen is pushed.
 *
 * Every item is the same: one [NavigationSuiteItem] with the suite's default colours, its selection pill, the
 * outlined icon swapping to the filled one while selected, and its label — Search included (Cris, fix round 2: no
 * shape, no special look). The first tap selects a tab; re-tapping the selected tab calls [onReselectTab] — the
 * shell focuses the search field on Search and scrolls lists to the top on the others ([tabReselectFor]).
 *
 * [layout] comes from [suiteLayoutFor]. On the rails the whole group is centred vertically
 * (`navigationItemVerticalArrangement = Arrangement.Center`), within thumb reach on a tablet or an unfolded
 * phone, the way Gmail's rail does it.
 *
 * It also tells the player surface where the suite is ([LocalPlayerSurfaceChrome]): the bar's measured TOTAL
 * height (the navigation-bar inset it covers included) while the bar shows, the rail's measured width while a rail
 * shows — the suite's size less its content's ([suiteChromeExtent]) — so the mini bar sits directly above the bar
 * and never over the rail. Both reset to 0dp when the suite leaves composition (a screen pushed over the library)
 * and whenever the layout changes, before the new layout measures.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheliakNavigationSuite(
    layout       : SuiteLayout,
    selectedTab  : LibraryTab,
    onSelectTab  : (LibraryTab) -> Unit,
    modifier     : Modifier = Modifier,
    onReselectTab: (LibraryTab) -> Unit = {},
    content      : @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val type = layout.toNavigationSuiteType()
    val chrome = LocalPlayerSurfaceChrome.current
    val density = LocalDensity.current
    val publisher = remember(chrome) { SuiteChromePublisher(chrome) }
    DisposableEffect(publisher, layout) {
        publisher.layout = layout
        onDispose { publisher.reset() }
    }
    val destinations = remember(layout) { suiteItemOrder(layout).map(::suiteDestinationOf) }
    NavigationSuiteScaffold(
        navigationItems                   = {
            destinations.forEach { destination ->
                val selected = destination.tab == selectedTab
                NavigationSuiteItem(
                    selected            = selected,
                    onClick             = {
                        haptics.press()
                        // Re-selecting the current tab is the shell's to interpret (scroll to top / focus search).
                        if (selected) onReselectTab(destination.tab) else onSelectTab(destination.tab)
                    },
                    icon                = {
                        Icon(
                            imageVector        = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = null,
                        )
                    },
                    label               = { Text(stringResource(destination.labelRes)) },
                    navigationSuiteType = type,
                )
            }
        },
        modifier                          = modifier.onSizeChanged { publisher.onSuiteSize(it, density) },
        navigationSuiteType               = type,
        // EXACTLY Arrangement.Center: WideNavigationRail tests for that instance and only then centres the items on
        // the rail's full height. The bar ignores the arrangement. No primaryActionContent: the rail's header
        // stays empty (zero height, so it adds no header gap) — Search is one of the items, not a header button.
        // Centred only on a pane at least LargeBarMinPaneHeight tall (the same measured 600dp gate the large
        // bar uses): the five items are ~340dp, and on the folded outer screen in landscape (~390dp usable, 44dp
        // of rail padding) a centred group would overflow equally off both ends and clip, while a top
        // arrangement there is within reach anyway.
        navigationItemVerticalArrangement = if (railTallEnoughToCentre()) Arrangement.Center else Arrangement.Top,
        content                           = {
            // The suite's own component covers part of the window for the content: the bottom under a bar, the
            // START side beside a rail (a 3-button bar on the END edge is still the content's to clear).
            // Consuming it here is what makes every inset MODIFIER below — the bottom fades, `imePadding()` on
            // the Search tab — measure only what is still owed, with no knowledge of the suite:
            // - the system inset the component sits on (known from the first frame);
            // - under the bar, the bar's whole measured height as well, so a keyboard raised on the Search tab
            //   is owed only its part ABOVE the bar (the bar is hidden behind it), never the bar's height twice.
            // Both as WindowInsets: that overload UNIONS with what is already consumed (the larger per side — the
            // bar's height, which includes the inset), while the PaddingValues overload would ADD the two.
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { publisher.onContentSize(it, density) }
                    .consumeWindowInsets(suiteCoveredInsets(layout))
                    .consumeWindowInsets(WindowInsets(bottom = chrome.bottomBar)),
            ) { content() }
        },
    )
}

/**
 * Writes the suite's extent into the player surface's [PlayerSurfaceChrome] from the two `onSizeChanged`
 * callbacks (layout phase): the suite's total size and its content's. Plain fields, not state — only the
 * callbacks read and write them; what the host reads is the chrome's own state. [reset] zeroes the chrome and
 * forgets the sizes, so a layout change publishes only what the NEW layout measures.
 */
private class SuiteChromePublisher(private val chrome: PlayerSurfaceChrome) {
    var layout: SuiteLayout? = null
    private var suite: IntSize? = null
    private var content: IntSize? = null

    fun onSuiteSize(size: IntSize, density: Density) {
        suite = size
        publish(density)
    }

    fun onContentSize(size: IntSize, density: Density) {
        content = size
        publish(density)
    }

    fun reset() {
        layout = null
        suite = null
        content = null
        chrome.bottomBar = 0.dp
        chrome.startRail = 0.dp
    }

    private fun publish(density: Density) {
        val current = layout ?: return
        val total = suite ?: return
        val inner = content ?: return
        val extent = suiteChromeExtent(current, total.width, total.height, inner.width, inner.height)
        with(density) {
            chrome.bottomBar = extent.bottomBarPx.toDp()
            chrome.startRail = extent.startRailPx.toDp()
        }
    }
}

/** The system insets the suite's bar or rail already covers for [layout]. */
@Composable
private fun suiteCoveredInsets(layout: SuiteLayout): WindowInsets = when (layout) {
    SuiteLayout.Bar           -> WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
    SuiteLayout.CollapsedRail,
    SuiteLayout.ExpandedRail  -> WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start)
}

/**
 * Whether the rail's items group may be centred: the window is at least [LargeBarMinPaneHeight] tall, read the
 * same way `RootTopBar` reads its gate (measured window height, because the window-size-class height buckets
 * have no 600dp boundary). Below it — the folded outer screen in landscape — the group keeps the top
 * arrangement so nothing is clipped off the rail's ends.
 */
@Composable
private fun railTallEnoughToCentre(): Boolean =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } >= LargeBarMinPaneHeight
