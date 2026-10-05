package com.crsmthw.sheliak.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.util.artBoundsTransform
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.rememberSearchBarMorphClip
import com.crsmthw.sheliak.util.screenTransitionSpec

/** One navigation-suite destination: its key, label and the outlined / filled icon pair. */
private data class SuiteDestination(
    val key         : NavKey,
    @param:StringRes val labelRes: Int,
    val icon        : ImageVector,
    val selectedIcon: ImageVector,
)

/** The four destinations in suite order — the same order as [TopLevelKeys]. */
private val SuiteDestinations = listOf(
    SuiteDestination(Tracks,    R.string.nav_tracks,    Icons.Outlined.LibraryMusic,          Icons.Filled.LibraryMusic),
    SuiteDestination(Albums,    R.string.nav_albums,    Icons.Outlined.Album,                 Icons.Filled.Album),
    SuiteDestination(Artists,   R.string.nav_artists,   Icons.Outlined.Person,                Icons.Filled.Person),
    SuiteDestination(Playlists, R.string.nav_playlists, Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Filled.QueueMusic),
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
    SuiteLayout.Hidden        -> NavigationSuiteType.None
}

/**
 * The app's navigation suite: Material's [NavigationSuiteScaffold] with the four destinations, around the
 * whole navigation host ([content]). The host lives in the scaffold's content slot, so everything the host
 * draws — the screens and, later, the floating player surface — stays clear of the bar and the rail.
 *
 * [layout] comes from [suiteLayoutFor]; [SuiteLayout.Hidden] removes the suite on every non-destination screen.
 * On the rail widths the search button lives in the rail's header (the scaffold's primary-action slot). On
 * compact it does NOT: there the search FAB belongs to each destination screen ([SearchFab]), because it is one
 * end of a shared-element morph and must live inside a navigation entry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheliakNavigationSuite(
    layout      : SuiteLayout,
    selectedTab : NavKey,
    onSelectTab : (NavKey) -> Unit,
    onOpenSearch: () -> Unit,
    content     : @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val type = layout.toNavigationSuiteType()
    NavigationSuiteScaffold(
        navigationItems      = {
            SuiteDestinations.forEach { destination ->
                val selected = destination.key == selectedTab
                NavigationSuiteItem(
                    selected            = selected,
                    onClick             = {
                        // Re-selecting the current tab does nothing yet (M1 scrolls its list to the top).
                        if (!selected) {
                            haptics.press()
                            onSelectTab(destination.key)
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
        navigationSuiteType  = type,
        primaryActionContent = {
            when (layout) {
                SuiteLayout.CollapsedRail -> RailSearchButton(expanded = false, onClick = onOpenSearch)
                SuiteLayout.ExpandedRail  -> RailSearchButton(expanded = true, onClick = onOpenSearch)
                // Compact: the destination screen hosts the FAB itself (see the KDoc).
                SuiteLayout.Bar, SuiteLayout.Hidden -> Unit
            }
        },
        content              = {
            // The suite's own component covers a system inset for the content: the bottom one under a bar,
            // the START side beside a rail (a 3-button bar on the END edge is still the screens' to clear).
            // Consuming it here is what makes every inset MODIFIER below — the screens' bottom fades, the FAB's
            // `navigationBarsPadding()` — measure only what is still owed, with no per-screen knowledge of
            // the suite.
            Box(Modifier.fillMaxSize().consumeWindowInsets(suiteCoveredInsets(layout))) { content() }
        },
    )
}

/** The system insets the suite's bar or rail already covers for [layout]; none when the suite is hidden. */
@Composable
private fun suiteCoveredInsets(layout: SuiteLayout): WindowInsets = when (layout) {
    SuiteLayout.Bar           -> WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
    SuiteLayout.CollapsedRail,
    SuiteLayout.ExpandedRail  -> WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start)
    SuiteLayout.Hidden        -> WindowInsets(0)
}

/**
 * The search button in the rail header: a FAB on the collapsed rail, an extended FAB (icon + label) on the
 * expanded one, in the same tertiary tone as the compact [SearchFab] so search reads as one control at every
 * width. Opening Search from here uses the ordinary push transition — no container morph on rail widths.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RailSearchButton(expanded: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.nav_search)
    val click = { haptics.press(); onClick() }
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

/** Pairs the compact search FAB with the Search screen's floating bar for the container transform. */
private const val SearchBarSharedKey = "search-bar"

/**
 * The compact search FAB, bottom-end of a destination screen's content `Box`, lifted by the player surface's
 * inset ([LocalPlayerSurfaceInset]) so it sits above the mini player. A [MediumFloatingActionButton] because
 * the morph's geometry is measured from an 80dp FAB (`util/SearchBarMorph.kt`), in a tertiary tone with the
 * expressive SoftBurst silhouette so it never reads as a play button. Tapping it morphs it into the Search
 * screen's bar ([searchBarSharedBounds]).
 *
 * Its `navigationBarsPadding()` resolves to zero under the suite's bar, which consumes that inset; it is there
 * for a compact screen without a bar, so the FAB can never sit in the gesture area.
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
