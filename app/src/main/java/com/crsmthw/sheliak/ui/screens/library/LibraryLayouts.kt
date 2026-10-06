package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.navigation.LocalPlayerSurfaceInset
import com.crsmthw.sheliak.util.PullThresholdHaptics
import com.crsmthw.sheliak.util.lateralPaneSwap

/*
 * The library's shared layouts: the pull-to-refresh frame every list sits in, the list and grid shapes, and the
 * two-pane Row the list-detail tabs use from 600dp.
 */

/**
 * Pull-to-refresh around one library list (Material's [PullToRefreshBox]), with the list's seams drawn inside it:
 * [content], then the [TopBarFade] under the bar and the [BottomFadeScrim] — the box emits `content(); indicator()`,
 * so both fades draw over the rows and the indicator still slides out over the top one.
 *
 * NESTING IS LOAD-BEARING (Lyra, verified against material3's sources): the pull-to-refresh is OUTSIDE and the
 * bar's nested-scroll connection INSIDE, on the list. Post-scroll dispatches innermost-first and the refresh node
 * takes the whole leftover while it has not started pulling, so with the bar's connection outside it the
 * collapsed bar could never re-expand; inside, the bar takes the leftover first and the pull starts once the bar
 * is fully out — also the right gesture order for the user.
 *
 * The indicator is ALWAYS drawn in its pull form (an arc that follows the drag), never the indeterminate spinner:
 * while [isRefreshing] holds it at the threshold it is a still, full arc, so nothing on screen animates endlessly
 * (the app's finite-motion rule). The refresh itself is a sync of every source; the hold ends with the sync round
 * ([awaitSyncRound]), and the Tracks subtitle carries a longer sync's progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryRefreshFrame(
    isRefreshing: Boolean,
    onRefresh   : () -> Unit,
    paneColor   : Color,
    modifier    : Modifier = Modifier,
    content     : @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullThresholdHaptics(state, isRefreshing)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = onRefresh,
        state        = state,
        modifier     = modifier.fillMaxSize(),
        indicator    = {
            PullToRefreshDefaults.Indicator(
                state        = state,
                isRefreshing = false,
                modifier     = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        content()
        TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
        BottomFadeScrim(color = paneColor)
    }
}

/**
 * The one list layout the Tracks, Artists and Playlists tabs use: the hoisted [listState], the bar's
 * [nestedScrollConnection], the seam gap under the bar, the tab's [content], then the trailing bottom room. One
 * composable so the tabs cannot drift apart in insets, scroll wiring or seams.
 */
@Composable
internal fun LibraryTabList(
    listState             : LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    modifier              : Modifier = Modifier,
    content               : LazyListScope.() -> Unit,
) {
    LazyColumn(
        state          = listState,
        modifier       = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(top = BarContentGap),
    ) {
        content()
        // The bottom room the fade scrim covers, plus the player surface's inset, so the last row can scroll
        // clear of both. An inset-aware item rather than `contentPadding`, because only an inset modifier knows
        // what the navigation suite's bar has already consumed (CLAUDE.md rule 9).
        item(key = BOTTOM_ROOM_KEY, contentType = BOTTOM_ROOM_KEY) {
            BottomFadeSpacer(extra = LocalPlayerSurfaceInset.current)
        }
    }
}

/** The Albums tab's grid — [LibraryTabList]'s twin for a [LazyVerticalGrid] of adaptive columns. */
@Composable
internal fun LibraryTabGrid(
    gridState             : LazyGridState,
    nestedScrollConnection: NestedScrollConnection,
    modifier              : Modifier = Modifier,
    content               : LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = AlbumGridMinCellWidth),
        state                 = gridState,
        modifier              = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, top = BarContentGap),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
    ) {
        content()
        item(key = BOTTOM_ROOM_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = BOTTOM_ROOM_KEY) {
            BottomFadeSpacer(extra = LocalPlayerSurfaceInset.current)
        }
    }
}

/** An album cell is at least this wide; the grid fits as many columns as that allows. */
private val AlbumGridMinCellWidth = 140.dp

/** The empty state's lazy-list key, shared by the four tabs (each has its own list). */
internal const val EMPTY_STATE_KEY = "empty"

/** Key of the trailing bottom-room item; tabs' own items never use it. */
private const val BOTTOM_ROOM_KEY = "library-bottom-room"

/** Lyra's two-pane geometry: the Row's padding and gap, and the top corners of its two cards. */
private val TwoPanePadding = 8.dp
private val TwoPaneCardShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** The list card's share of the Row (Lyra's 42 %); the detail card takes the rest ([TWO_PANE_DETAIL_WEIGHT]). */
const val TWO_PANE_LIST_WEIGHT: Float = 0.42f

/** The detail card's share of the Row (Lyra's 58 %) — also the width the mini bar takes over a two-pane tab. */
const val TWO_PANE_DETAIL_WEIGHT: Float = 0.58f

/**
 * A list-detail tab at 600dp and up, in Lyra's geometry: `Row { Card(list, 0.42) ; Card(detail, 0.58) }`, 8dp
 * padding, 8dp gap, `surface` cards with rounded top corners running to the bottom edge. The right card shows
 * the [selection] — [detail] of it — swapped with the M3 lateral slide ([lateralPaneSwap], finite, clipped by
 * the card), or a hint ([hintIcon] / [hintText]) before anything is picked.
 *
 * Both cards' contents draw their own seams (the list through [LibraryRefreshFrame], the detail through its
 * frame), in the card's `surface`.
 */
@Composable
internal fun <K : Any> LibraryTwoPane(
    selection: K?,
    hintIcon : ImageVector,
    hintText : String,
    list     : @Composable () -> Unit,
    detail   : @Composable (K) -> Unit,
    modifier : Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxSize().padding(TwoPanePadding),
        horizontalArrangement = Arrangement.spacedBy(TwoPanePadding),
    ) {
        Card(
            modifier  = Modifier.weight(TWO_PANE_LIST_WEIGHT).fillMaxHeight(),
            shape     = TwoPaneCardShape,
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            list()
        }
        Card(
            modifier  = Modifier.weight(TWO_PANE_DETAIL_WEIGHT).fillMaxHeight(),
            shape     = TwoPaneCardShape,
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            AnimatedContent(
                targetState    = selection,
                modifier       = Modifier.fillMaxSize(),
                transitionSpec = { lateralPaneSwap() },
                label          = "library-detail-pane",
            ) { shown ->
                if (shown == null) PaneHint(hintIcon, hintText) else detail(shown)
            }
        }
    }
}

/** The right pane before a pick: a faint icon over one line, centred in the card. */
@Composable
private fun PaneHint(icon: ImageVector, text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.outline,
                modifier           = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text      = text,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
