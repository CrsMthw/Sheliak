package com.crsmthw.sheliak.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemColors
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.ui.components.LargeBarMinPaneHeight
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec

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
    LibraryTab.SEARCH    -> SuiteDestination(tab, R.string.nav_search,    Icons.Filled.Search,                   Icons.Filled.Search)
    LibraryTab.ARTISTS   -> SuiteDestination(tab, R.string.nav_artists,   Icons.Outlined.Person,                 Icons.Filled.Person)
    LibraryTab.PLAYLISTS -> SuiteDestination(tab, R.string.nav_playlists, Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Filled.QueueMusic)
}

/** The five items in suite order — exactly [LibraryTab]'s order, so Search ([SuiteActionTab]) is the centre one. */
private val SuiteDestinations: List<SuiteDestination> = LibraryTab.entries.map(::suiteDestinationOf)

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
 * The library's navigation suite: Material's [NavigationSuiteScaffold] with the five tabs — Tracks, Albums,
 * **Search**, Artists, Playlists — around the library's content ([content]). It lives INSIDE the Library
 * navigation entry (`LibraryShell`), so a pushed screen covers it like any other part of the library and a
 * predictive back reveals it whole, and the content beside it never moves when a screen is pushed.
 *
 * Search ([SuiteActionTab]) is an ordinary tab of the suite — the same item type, label and selection semantics
 * as the others, in the centre of the bar and of both rails — drawn as the suite's ACTION: its icon sits inside
 * an M3 Expressive shape ([SearchItemIcon]) instead of the selection pill, which is hidden for that one item
 * (its colours' `selectedIndicatorColor` is transparent). The first tap selects it like any tab; re-tapping the
 * selected tab calls [onReselectTab] — the shell focuses the search field there and scrolls lists to the top on
 * the others ([tabReselectFor]).
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
    val actionColors = suiteActionItemColors(layout)
    NavigationSuiteScaffold(
        navigationItems                   = {
            SuiteDestinations.forEach { destination ->
                val selected = destination.tab == selectedTab
                val isAction = destination.tab == SuiteActionTab
                NavigationSuiteItem(
                    selected            = selected,
                    onClick             = {
                        haptics.press()
                        // Re-selecting the current tab is the shell's to interpret (scroll to top / focus search).
                        if (selected) onReselectTab(destination.tab) else onSelectTab(destination.tab)
                    },
                    icon                = {
                        if (isAction) {
                            SearchItemIcon(icon = destination.selectedIcon, selected = selected)
                        } else {
                            Icon(
                                imageVector        = if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = null,
                            )
                        }
                    },
                    label               = { Text(stringResource(destination.labelRes)) },
                    navigationSuiteType = type,
                    colors              = if (isAction) actionColors else null,
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
 * The colours of the Search item ([SuiteActionTab]): the layout's own item defaults with the selection pill made
 * transparent, because the item's shape ([SearchItemIcon]) IS its container and shows the selection itself.
 * The 8-colour `colors(…)` overload (the 7-colour one is hidden in material3 1.5.0-alpha29).
 */
@Composable
private fun suiteActionItemColors(layout: SuiteLayout): NavigationItemColors = when (layout) {
    SuiteLayout.Bar           -> ShortNavigationBarItemDefaults.colors(selectedIndicatorColor = Color.Transparent)
    SuiteLayout.CollapsedRail,
    SuiteLayout.ExpandedRail  -> WideNavigationRailItemDefaults.colors(selectedIndicatorColor = Color.Transparent)
}

/** The item's own icon size (M3 navigation item tokens): what the Search item reports to the item's layout. */
private val SuiteIconSize = 24.dp

/**
 * The Search item's shape, drawn around its [SuiteIconSize] icon. 36dp: the top-icon items (bar and collapsed
 * rail) put their 32dp-tall pill 4dp above and below the icon and the label 4dp under the pill, so a 36dp shape
 * still clears the label by 2dp; on the expanded rail's start-icon item the label starts 8dp after the icon, so
 * it clears it by 2dp there too.
 */
private val SearchShapeSize = 36.dp

/**
 * The Search item's icon: the search glyph on an M3 Expressive shape — `primaryContainer` / `onPrimaryContainer`,
 * `primary` / `onPrimary` while selected — that MORPHS (`androidx.graphics.shapes.Morph`) from a 9-sided cookie,
 * which reads as a button, to the soft burst the old search button wore while the tab is selected, and back, on
 * the one finite spec, its colours crossfading with it. Nothing about it loops.
 *
 * The item lays it out as a [SuiteIconSize] icon, so the label sits exactly where every other item's does; the
 * shape is drawn [SearchShapeSize] wide around it, past the icon's bounds (a navigation item does not clip its
 * icon). Drawn in the draw phase from the morph's progress, so the animation redraws, never recomposes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchItemIcon(icon: ImageVector, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue   = if (selected) 1f else 0f,
        animationSpec = screenTransitionSpec(),
        label         = "searchShapeMorph",
    )
    val container by animateColorAsState(
        targetValue   = if (selected) colors.primary else colors.primaryContainer,
        animationSpec = screenTransitionSpec(),
        label         = "searchShapeContainer",
    )
    val content by animateColorAsState(
        targetValue   = if (selected) colors.onPrimary else colors.onPrimaryContainer,
        animationSpec = screenTransitionSpec(),
        label         = "searchShapeContent",
    )
    val morph = remember { Morph(start = MaterialShapes.Cookie9Sided, end = MaterialShapes.SoftBurst) }
    val overflow = (SearchShapeSize - SuiteIconSize) / 2
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(SuiteIconSize)
            .drawWithCache {
                val path = Path()
                val matrix = Matrix()
                val overflowPx = overflow.toPx()
                onDrawBehind {
                    inset(-overflowPx) {
                        // The morph is in the unit square (MaterialShapes are normalised); scale it onto the
                        // shape's square the way material3's own `RoundedPolygon.toShape()` does.
                        path.rewind()
                        morph.toPath(progress = progress, path = path)
                        matrix.reset()
                        matrix.scale(x = size.width, y = size.height)
                        path.transform(matrix)
                        drawPath(path = path, color = container)
                    }
                }
            },
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
    }
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
