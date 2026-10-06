package com.crsmthw.sheliak.ui.screens.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.player.PlayerStateManager
import com.crsmthw.sheliak.ui.components.Artwork
import com.crsmthw.sheliak.ui.components.BarContentGap
import com.crsmthw.sheliak.ui.components.BottomFadeScrim
import com.crsmthw.sheliak.ui.components.BottomFadeSpacer
import com.crsmthw.sheliak.ui.components.RootTopBar
import com.crsmthw.sheliak.ui.components.TopBarFade
import com.crsmthw.sheliak.ui.components.rememberRootTopBarScrollBehavior
import com.crsmthw.sheliak.ui.player.QUEUE_ART_SIZE_PX
import com.crsmthw.sheliak.ui.player.QueueEdit
import com.crsmthw.sheliak.ui.player.QueueRow
import com.crsmthw.sheliak.ui.player.moved
import com.crsmthw.sheliak.ui.player.nextInCycle
import com.crsmthw.sheliak.ui.player.queueRows
import com.crsmthw.sheliak.ui.player.removedAt
import com.crsmthw.sheliak.util.ListScrollHaptics
import com.crsmthw.sheliak.util.confirm
import com.crsmthw.sheliak.util.horizontalSystemBarsPadding
import com.crsmthw.sheliak.util.press
import com.crsmthw.sheliak.util.screenTransitionSpec
import com.crsmthw.sheliak.util.tick
import com.crsmthw.sheliak.util.toTimeString
import com.crsmthw.sheliak.util.toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** The lazy-list key of the trailing room under the last row. */
private const val BOTTOM_ROOM_KEY = "queue-bottom-room"

/** Dragging a row this close to the list's top or bottom edge scrolls the list. */
private val EdgeScrollZone = 48.dp

/**
 * The play queue — the player's timeline, in PLAY order (the shuffle order while shuffle is on), with the
 * playing row highlighted.
 *
 * - Tap a row: play it (`skipToQueueIndex`). Swipe a row away (either direction): remove it (`removeAt`); the
 *   playing row cannot be swiped. Drag a row by its handle: reorder (`move(from, to)`, sent once on release).
 * - Both edits are shown at once, before the player confirms them: the player answers asynchronously (and
 *   briefly reports an unshuffled masking queue after a removal with shuffle on), so the screen shows its own
 *   edit until the player's queue catches up, then follows the player again ([QueueEdit.reconcile]).
 * - The shuffle / repeat state sits under the bar as two chips that also toggle it.
 *
 * Chrome: the list-screen anatomy — a collapsing [RootTopBar] with a back arrow, the seam under it, the lazy list
 * and the bottom fade scrim.
 */
@Composable
fun QueueScreen(
    player  : PlayerStateManager,
    onBack  : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state     by player.state.collectAsStateWithLifecycle()
    val haptics   = LocalHapticFeedback.current
    val density   = LocalDensity.current
    val barState  = rememberTopAppBarState()
    val paneColor = MaterialTheme.colorScheme.background
    val scope     = rememberCoroutineScope()

    val controllerRows = remember(state.queue) { queueRows(state.queue) }
    val controllerIds  = remember(state.queue) { state.queue.map { it.key.mediaId } }
    var edit by remember { mutableStateOf<QueueEdit?>(null) }
    LaunchedEffect(controllerIds, state.shuffle) {
        edit = edit?.reconcile(controllerIds, state.shuffle)
    }

    // Read at call time by gesture callbacks that were set up compositions ago.
    val latestRows    by rememberUpdatedState(controllerRows)
    val latestIds     by rememberUpdatedState(controllerIds)
    val latestShuffle by rememberUpdatedState(state.shuffle)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.queueIndex.coerceAtLeast(0))
    val edgePx    = with(density) { EdgeScrollZone.toPx() }
    val drag      = remember(listState) { QueueDragState(listState, scope, edgePx) }
    LaunchedEffect(drag) {
        for (amount in drag.scrollRequests) listState.scrollBy(amount)
    }
    ListScrollHaptics(listState)

    val rows       = drag.rows ?: edit?.rows ?: controllerRows
    val currentKey = controllerRows.getOrNull(state.queueIndex)?.key

    fun commitMove(from: Int, to: Int) {
        val base = latestRows
        if (from == to || from !in base.indices || to !in base.indices) return
        edit = QueueEdit(base = latestIds, rows = base.moved(from, to), shuffle = latestShuffle, maskingAllowance = 0)
        player.move(from, to)
    }

    fun removeRow(key: String): Boolean {
        val base  = latestRows
        val index = base.indexOfFirst { it.key == key }
        if (index < 0) return false
        edit = QueueEdit(
            base             = latestIds,
            rows             = base.removedAt(index),
            shuffle          = latestShuffle,
            maskingAllowance = if (latestShuffle) 1 else 0,
        )
        player.removeAt(index)
        return true
    }

    fun moveByOne(key: String, delta: Int) {
        val from = latestRows.indexOfFirst { it.key == key }
        if (from >= 0) commitMove(from, from + delta)
    }

    fun playRow(key: String) {
        val index = latestRows.indexOfFirst { it.key == key }
        if (index >= 0) player.skipToQueueIndex(index)
    }

    Scaffold(
        modifier            = modifier,
        containerColor      = paneColor,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .horizontalSystemBarsPadding(),
        ) {
            val scrollBehavior = rememberRootTopBarScrollBehavior(barState)
            RootTopBar(
                title          = stringResource(R.string.queue_title),
                subtitle       = pluralStringResource(R.plurals.queue_track_count, rows.size, rows.size),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { haptics.confirm(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                belowBar       = {
                    QueueModeChips(
                        shuffle  = state.shuffle,
                        repeat   = state.repeat,
                        enabled  = rows.isNotEmpty(),
                        onShuffle = {
                            haptics.toggle(!state.shuffle)
                            player.setShuffle(!state.shuffle)
                        },
                        onRepeat = {
                            val next = state.repeat.nextInCycle()
                            when (next) {
                                RepeatMode.ALL -> haptics.toggle(true)
                                RepeatMode.OFF -> haptics.toggle(false)
                                RepeatMode.ONE -> haptics.press()
                            }
                            player.setRepeat(next)
                        },
                    )
                },
                containerColor = paneColor,
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (rows.isEmpty()) {
                    QueueEmptyState(modifier = Modifier.align(Alignment.Center))
                } else {
                    val moveUpLabel   = stringResource(R.string.queue_move_up)
                    val moveDownLabel = stringResource(R.string.queue_move_down)
                    val removeLabel   = stringResource(R.string.queue_remove)
                    val nowPlaying    = stringResource(R.string.player_title)
                    LazyColumn(
                        state          = listState,
                        modifier       = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(top = BarContentGap),
                    ) {
                        itemsIndexed(items = rows, key = { _, row -> row.key }) { index, row ->
                            val lifted       = row.key == drag.draggingKey
                            val isCurrent    = row.key == currentKey
                            val dismissState = rememberSwipeToDismissBoxState()
                            SwipeToDismissBox(
                                state                       = dismissState,
                                backgroundContent           = { RemoveBackground(dismissState.dismissDirection) },
                                modifier                    = Modifier
                                    .animateItem(
                                        fadeInSpec    = screenTransitionSpec(),
                                        placementSpec = if (lifted) null else screenTransitionSpec<IntOffset>(),
                                        fadeOutSpec   = screenTransitionSpec(),
                                    )
                                    .zIndex(if (lifted || row.key == drag.settlingKey) 1f else 0f)
                                    .graphicsLayer { translationY = drag.translationOf(row.key) }
                                    .semantics {
                                        if (isCurrent) stateDescription = nowPlaying
                                        customActions = buildList {
                                            if (index > 0) {
                                                add(CustomAccessibilityAction(moveUpLabel) { moveByOne(row.key, -1); true })
                                            }
                                            if (index < rows.lastIndex) {
                                                add(CustomAccessibilityAction(moveDownLabel) { moveByOne(row.key, 1); true })
                                            }
                                            if (!isCurrent) {
                                                add(CustomAccessibilityAction(removeLabel) { removeRow(row.key) })
                                            }
                                        }
                                    },
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                gesturesEnabled             = !isCurrent && drag.draggingKey == null,
                                onDismiss                   = {
                                    if (removeRow(row.key)) {
                                        haptics.confirm()
                                    } else {
                                        scope.launch { dismissState.reset() }
                                    }
                                },
                            ) {
                                QueueRowContent(
                                    row          = row,
                                    isCurrent    = isCurrent,
                                    lifted       = lifted,
                                    onPlay       = {
                                        haptics.confirm()
                                        playRow(row.key)
                                    },
                                    onDragStart  = {
                                        // A new gesture starts from the player's own order: any edit still
                                        // waiting for the player is dropped first.
                                        edit = null
                                        if (drag.start(row.key, latestRows, latestIds)) haptics.press()
                                    },
                                    onDrag       = { amount -> if (drag.drag(amount)) haptics.tick() },
                                    onDragEnd    = {
                                        val drop = drag.finish()
                                        if (drop != null && drop.from != drop.to && drop.baseIds == latestIds) {
                                            haptics.confirm()
                                            commitMove(drop.from, drop.to)
                                        }
                                    },
                                    onDragCancel = { drag.finish() },
                                )
                            }
                        }
                        // The bottom room the fade scrim covers, so the last row can scroll clear of it.
                        item(key = BOTTOM_ROOM_KEY) { BottomFadeSpacer() }
                    }
                }
                TopBarFade(paneColor = paneColor, modifier = Modifier.align(Alignment.TopCenter))
                BottomFadeScrim(color = paneColor)
            }
        }
    }
}

/**
 * A row being dragged by its handle: the local order while the finger is down, the lifted row's translation,
 * the edge auto-scroll, and the short settle into the slot when the finger lifts. The order the drag produces
 * is only a preview; [finish] hands back the one move to send.
 *
 * The lifted row's translation is computed from the list's layout every frame (where it started + how far the
 * finger went − where its slot is now), so it stays under the finger while rows swap and while the list
 * scrolls under it.
 */
@Stable
private class QueueDragState(
    private val listState: LazyListState,
    private val scope    : CoroutineScope,
    private val edgePx   : Float,
) {
    /** The order shown while dragging; null when no drag is in progress. */
    var rows by mutableStateOf<List<QueueRow>?>(null)
        private set

    var draggingKey by mutableStateOf<String?>(null)
        private set

    /** The row easing into its slot after a drop (drawn above its neighbours meanwhile). */
    var settlingKey by mutableStateOf<String?>(null)
        private set

    /** Scroll requests for the list while the lifted row presses against an edge. */
    val scrollRequests = Channel<Float>(Channel.CONFLATED)

    private var dragDelta by mutableFloatStateOf(0f)
    private var initialOffset = 0
    private var startIndex = -1
    private var baseIds: List<String> = emptyList()
    private val settle = Animatable(0f)

    /** What a finished drag asks for: move [from] → [to], made on the player queue [baseIds]. */
    class Drop(val from: Int, val to: Int, val baseIds: List<String>)

    private fun itemInfo(key: String): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** The vertical translation of [key]'s row: the lifted row follows the finger, the settling row eases home. */
    fun translationOf(key: String): Float = when (key) {
        draggingKey -> itemInfo(key)?.let { initialOffset + dragDelta - it.offset } ?: 0f
        settlingKey -> settle.value
        else        -> 0f
    }

    /** Lifts [key]'s row out of [from] (the player's rows, [ids]). False when the row is not on screen. */
    fun start(key: String, from: List<QueueRow>, ids: List<String>): Boolean {
        val index = from.indexOfFirst { it.key == key }
        val info  = itemInfo(key) ?: return false
        if (index < 0) return false
        rows          = from
        draggingKey   = key
        startIndex    = index
        baseIds       = ids
        initialOffset = info.offset
        dragDelta     = 0f
        return true
    }

    /** Moves the lifted row by [dy] px. True when it crossed into a neighbour's slot (the two swapped). */
    fun drag(dy: Float): Boolean {
        val key     = draggingKey ?: return false
        val current = rows ?: return false
        dragDelta += dy
        val item   = itemInfo(key) ?: return false
        val top    = initialOffset + dragDelta
        val bottom = top + item.size
        val center = (top + bottom) / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != key && other.key != BOTTOM_ROOM_KEY &&
                center >= other.offset && center < other.offset + other.size
        }
        if (target != null) {
            val from = current.indexOfFirst { it.key == key }
            val to   = current.indexOfFirst { it.key == target.key }
            if (from < 0 || to < 0) return false
            // Keep the list from scrolling to follow its first visible row when that row is one of the two.
            val first = listState.firstVisibleItemIndex
            if (item.index == first || target.index == first) {
                listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
            }
            rows = current.moved(from, to)
            return true
        }
        val layout = listState.layoutInfo
        val overscroll = when {
            dragDelta > 0f -> (bottom - (layout.viewportEndOffset - edgePx)).coerceAtLeast(0f)
            dragDelta < 0f -> (top - (layout.viewportStartOffset + edgePx)).coerceAtMost(0f)
            else           -> 0f
        }
        if (overscroll != 0f) scrollRequests.trySend(overscroll)
        return false
    }

    /** Ends the drag (a release or a cancel): the move it produced, or null. The row eases into its slot. */
    fun finish(): Drop? {
        val key     = draggingKey ?: return null
        val current = rows ?: return null
        val residual = translationOf(key)
        val drop = Drop(from = startIndex, to = current.indexOfFirst { it.key == key }, baseIds = baseIds)
        draggingKey = null
        rows        = null
        dragDelta   = 0f
        settlingKey = key
        scope.launch {
            settle.snapTo(residual)
            settle.animateTo(0f, screenTransitionSpec())
            if (settlingKey == key) settlingKey = null
        }
        return drop
    }
}

/** Shuffle and repeat, as the queue's header: the state at a glance, and a tap toggles it. */
@Composable
private fun QueueModeChips(
    shuffle  : Boolean,
    repeat   : RepeatMode,
    enabled  : Boolean,
    onShuffle: () -> Unit,
    onRepeat : () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected    = shuffle,
            onClick     = onShuffle,
            enabled     = enabled,
            label       = { Text(stringResource(R.string.player_shuffle)) },
            leadingIcon = {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
            },
        )
        FilterChip(
            selected    = repeat != RepeatMode.OFF,
            onClick     = onRepeat,
            enabled     = enabled,
            label       = {
                Text(
                    stringResource(
                        when (repeat) {
                            RepeatMode.OFF -> R.string.player_repeat
                            RepeatMode.ALL -> R.string.queue_repeat_all
                            RepeatMode.ONE -> R.string.queue_repeat_one
                        },
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    imageVector        = if (repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null,
                    modifier           = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
}

/**
 * One queue row: 48dp art, title and artist, the playing marker, the duration and the drag handle. Opaque — the
 * swipe's remove background sits behind it — in the pane colour, `secondaryContainer` for the playing row, and
 * a raised container while lifted. The handle reports vertical drags only, so a horizontal swipe that starts on
 * it still reaches the row's swipe-to-remove.
 */
@Composable
private fun QueueRowContent(
    row         : QueueRow,
    isCurrent   : Boolean,
    lifted      : Boolean,
    onPlay      : () -> Unit,
    onDragStart : () -> Unit,
    onDrag      : (dy: Float) -> Unit,
    onDragEnd   : () -> Unit,
    onDragCancel: () -> Unit,
) {
    // The handle's gesture detector lives as long as the row; it calls the latest callbacks.
    val dragStart  by rememberUpdatedState(onDragStart)
    val dragBy     by rememberUpdatedState(onDrag)
    val dragEnd    by rememberUpdatedState(onDragEnd)
    val dragCancel by rememberUpdatedState(onDragCancel)
    val container = when {
        lifted    -> MaterialTheme.colorScheme.surfaceContainerHigh
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else      -> MaterialTheme.colorScheme.background
    }
    val track = row.track
    Surface(
        color           = container,
        shadowElevation = if (lifted) 6.dp else 0.dp,
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.queue_play_row), onClick = onPlay)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                art                = track.art,
                sizePx             = QUEUE_ART_SIZE_PX,
                contentDescription = null,   // the title beside it says what it is
                modifier           = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = track.title,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text     = track.artistName.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_unknown_artist),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector        = Icons.Default.GraphicEq,
                    contentDescription = null,   // the row's state description says it
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text  = track.durationMs.toTimeString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .pointerInput(row.key) {
                        detectVerticalDragGestures(
                            onDragStart    = { dragStart() },
                            onDragEnd      = { dragEnd() },
                            onDragCancel   = { dragCancel() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragBy(amount)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.queue_reorder),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Behind a row being swiped away: the remove icon on the side the row is leaving from. */
@Composable
private fun RemoveBackground(direction: SwipeToDismissBoxValue) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Icon(
            imageVector        = Icons.Default.PlaylistRemove,
            contentDescription = stringResource(R.string.queue_remove),
            tint               = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** Nothing queued. */
@Composable
private fun QueueEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = stringResource(R.string.queue_empty_title),
            style     = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text      = stringResource(R.string.queue_empty_body),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
