package com.crsmthw.sheliak.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import com.crsmthw.sheliak.ui.components.LargeBarMinPaneHeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.util.artBoundsTransform
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.rememberSearchBarMorphClip
import com.crsmthw.sheliak.util.screenTransitionSpec

/** One library tab in the suite: its tab, label and the outlined / filled icon pair. */
private data class SuiteDestination(
    val tab         : LibraryTab,
    @param:StringRes val labelRes: Int,
    val icon        : ImageVector,
    val selectedIcon: ImageVector,
)

/** The four tabs in suite order — the same order as [LibraryTab]. */
private val SuiteDestinations = listOf(
    SuiteDestination(LibraryTab.TRACKS,    R.string.nav_tracks,    Icons.Outlined.LibraryMusic,          Icons.Filled.LibraryMusic),
    SuiteDestination(LibraryTab.ALBUMS,    R.string.nav_albums,    Icons.Outlined.Album,                 Icons.Filled.Album),
    SuiteDestination(LibraryTab.ARTISTS,   R.string.nav_artists,   Icons.Outlined.Person,                Icons.Filled.Person),
    SuiteDestination(LibraryTab.PLAYLISTS, R.string.nav_playlists, Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Filled.QueueMusic),
)

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
 * The library's navigation suite: Material's [NavigationSuiteScaffold] with the four tabs, around the library's
 * content ([content]). It lives INSIDE the Library navigation entry (`LibraryShell`), so a pushed screen covers
 * it like any other part of the library and a predictive back reveals it whole, and the content beside it —
 * the bar, the tab lists, the compact search FAB — never moves when a screen is pushed.
 *
 * [layout] comes from [suiteLayoutFor]. On the rail widths the search button is the FIRST child of the rail's
 * items ([RailSearchButton]) and the whole group — search + the four tabs — is centred vertically on the rail
 * (`navigationItemVerticalArrangement = Arrangement.Center`), within thumb reach on a tablet or an unfolded
 * phone, the way Gmail's rail does it. It does not use the scaffold's primary-action slot: that is the rail's
 * header, which `WideNavigationRail` pins to the rail's top whatever the arrangement. On compact the search FAB
 * is NOT in the suite: it is composed by the library's content ([SearchFab]), because it is one end of a
 * shared-element morph and must live inside a navigation entry, outside the bar's own layout.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheliakNavigationSuite(
    layout      : SuiteLayout,
    selectedTab : LibraryTab,
    onSelectTab : (LibraryTab) -> Unit,
    onOpenSearch: () -> Unit,
    modifier    : Modifier = Modifier,
    content     : @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val type = layout.toNavigationSuiteType()
    NavigationSuiteScaffold(
        navigationItems                   = {
            // The same items feed the compact bar, so the rail-only button is guarded here: on the bar it would
            // become a fifth bar item.
            when (layout) {
                SuiteLayout.CollapsedRail -> RailSearchButton(expanded = false, onClick = onOpenSearch)
                SuiteLayout.ExpandedRail  -> RailSearchButton(expanded = true, onClick = onOpenSearch)
                SuiteLayout.Bar           -> Unit
            }
            SuiteDestinations.forEach { destination ->
                val selected = destination.tab == selectedTab
                NavigationSuiteItem(
                    selected            = selected,
                    onClick             = {
                        // Re-selecting the current tab does nothing yet (M1 scrolls its list to the top).
                        if (!selected) {
                            haptics.press()
                            onSelectTab(destination.tab)
                        }
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
        modifier                          = modifier,
        navigationSuiteType               = type,
        // EXACTLY Arrangement.Center: WideNavigationRail tests for that instance and only then centres the items on
        // the rail's full height. The bar ignores the arrangement. No primaryActionContent: the rail's header
        // stays empty (zero height, so it adds no header gap) and compact has its own FAB (see the KDoc).
        // Centred only on a pane at least LargeBarMinPaneHeight tall (the same measured 600dp gate the large
        // bar uses): the search button + four tabs are ~344dp, and on the folded outer screen in landscape
        // (~390dp usable, 44dp of rail padding) a centred group would overflow equally off both ends and
        // clip, while a top arrangement there is within reach anyway.
        navigationItemVerticalArrangement = if (railTallEnoughToCentre()) Arrangement.Center else Arrangement.Top,
        content                           = {
            // The suite's own component covers a system inset for the content: the bottom one under a bar,
            // the START side beside a rail (a 3-button bar on the END edge is still the content's to clear).
            // Consuming it here is what makes every inset MODIFIER below — the bottom fade, the FAB's
            // `navigationBarsPadding()` — measure only what is still owed, with no knowledge of the suite.
            Box(Modifier.fillMaxSize().consumeWindowInsets(suiteCoveredInsets(layout))) { content() }
        },
    )
}

/** The system insets the suite's bar or rail already covers for [layout]. */
@Composable
private fun suiteCoveredInsets(layout: SuiteLayout): WindowInsets = when (layout) {
    SuiteLayout.Bar           -> WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
    SuiteLayout.CollapsedRail,
    SuiteLayout.ExpandedRail  -> WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start)
}

/**
 * Start padding of the rail search button, so it sits on the rail's own item geometry instead of flush with its
 * edge. `WideNavigationRail` places EVERY child of its items at x = 0 (its items layout is not a centring column),
 * and the destinations carry the item horizontal padding themselves — material3's `internal`
 * `WNRItemHorizontalPadding`, 20dp in 1.5.0-alpha29. The button mirrors it:
 * - collapsed rail (96dp): a top-icon item is 20 + 56 + 20dp wide with its icon centred at 48dp, and a 56dp FAB
 *   after 20dp is centred at 48dp too — on the item column;
 * - expanded rail: the item indicators start at 20dp, and so does the extended FAB.
 * Padding, never a filling or centring wrapper: the expanded rail's width follows its widest item, so a button
 * that fills would widen the rail.
 */
private val RailItemStartPadding = 20.dp

/**
 * The gap the rail search button leaves below itself before the first destination, so it reads as the group's
 * action rather than a fifth tab. The rail adds its own item spacing on top (4dp collapsed, 0dp expanded), so the
 * layout gap is 20dp on the collapsed rail and 16dp on the expanded one; the first tab's indicator sits a little
 * lower still, inside its item.
 */
private val RailSearchButtonGap = 16.dp

/**
 * The rail search button, first in the rail's items and centred with them: a FAB on the collapsed rail, an
 * extended FAB (icon + label) on the expanded one, in the same tertiary tone as the compact [SearchFab] so search
 * reads as one control at every width, offset by [RailItemStartPadding] onto the rail's item column and
 * [RailSearchButtonGap] above the first tab. Opening Search from here uses the ordinary push transition — no
 * container morph on rail widths.
 *
 * The [Box] around the button is load-bearing: the rail measures every item child with a MINIMUM height (64dp on
 * the collapsed rail), which would stretch a bare 56dp FAB into a distorted SoftBurst. A `Box` measures its
 * content with the minimums dropped, so the button keeps its own size whatever the rail imposes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RailSearchButton(expanded: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.nav_search)
    val click = { haptics.press(); onClick() }
    Box(Modifier.padding(start = RailItemStartPadding, bottom = RailSearchButtonGap)) {
        if (expanded) {
            ExtendedFloatingActionButton(
                onClick        = click,
                icon           = { Icon(Icons.Filled.Search, contentDescription = null) },
                text           = { Text(label) },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor   = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            FloatingActionButton(
                onClick        = click,
                shape          = MaterialShapes.SoftBurst.toShape(),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor   = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Icon(Icons.Filled.Search, contentDescription = label)
            }
        }
    }
}

/** Pairs the compact search FAB with the Search screen's floating bar for the container transform. */
private const val SearchBarSharedKey = "search-bar"

/**
 * The compact search FAB, bottom-end of the library's content `Box` — composed ONCE there, over whichever tab
 * shows, so a tab change never recomposes or re-shadows it — lifted by the player surface's inset
 * ([LocalPlayerSurfaceInset]) so it sits above the mini player. A [MediumFloatingActionButton] because
 * the morph's geometry is measured from an 80dp FAB (`util/SearchBarMorph.kt`), in a tertiary tone with the
 * expressive SoftBurst silhouette so it never reads as a play button. Tapping it morphs it into the Search
 * screen's bar ([searchBarSharedBounds]). The suite lives in the same entry, so pushing Search never moves the
 * FAB: the morph starts exactly where the FAB is.
 *
 * Its `navigationBarsPadding()` resolves to zero under the suite's bar, which consumes that inset; it is there
 * so the FAB can never sit in the gesture area should it ever be composed without the bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.SearchFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    MediumFloatingActionButton(
        onClick        = { haptics.press(); onClick() },
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor   = MaterialTheme.colorScheme.onTertiaryContainer,
        shape          = MaterialShapes.SoftBurst.toShape(),
        modifier       = modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 16.dp + LocalPlayerSurfaceInset.current)
            .searchBarSharedBounds(),
    ) {
        Icon(
            imageVector        = Icons.Filled.Search,
            contentDescription = stringResource(R.string.nav_search),
            modifier           = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
        )
    }
}

/**
 * The `"search-bar"` container transform, applied identically at both ends — the compact [SearchFab] and the
 * Search screen's floating bar — so the two halves share one bounds transform and one outline clip
 * (`rememberSearchBarMorphClip`) on every frame. The bounds run on the ONE finite spec, and so does the
 * cross-fade of the two contents: `sharedBounds`' default fades are springs, which would outlast the nav slide.
 *
 * A no-op outside a navigation entry that has the shell's shared-transition scope, and on rail widths, where
 * Search opens with the plain push transition.
 */
@Composable
fun Modifier.searchBarSharedBounds(): Modifier {
    val sharedScope = LocalNavSharedTransitionScope.current
    if (sharedScope == null || !searchMorphEnabled(currentWindowWidth())) return this
    val visibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        this@searchBarSharedBounds.sharedBounds(
            sharedContentState            = rememberSharedContentState(key = SearchBarSharedKey),
            animatedVisibilityScope       = visibilityScope,
            enter                         = fadeIn(screenTransitionSpec()),
            exit                          = fadeOut(screenTransitionSpec()),
            boundsTransform               = artBoundsTransform(),
            clipInOverlayDuringTransition = rememberSearchBarMorphClip(),
        )
    }
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
