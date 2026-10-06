package com.crsmthw.sheliak.ui.screens.library

import androidx.compose.runtime.Immutable
import com.crsmthw.sheliak.data.sync.SyncState
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The pure shaping the library screens do on top of the repository's lists — disc grouping, running times,
 * the carousels, list keys, the sync count and the shared-element keys — kept free of Compose and Android so
 * every rule is unit-tested (LibraryShapingTest). The only Compose import is the `@Immutable` marker.
 */

// ── Album rows ──────────────────────────────────────────────────────────────

/** One row of an album's track list. */
@Immutable
sealed interface AlbumRow {
    /**
     * "Disc N" above that disc's first track — only on an album with more than one disc. [firstIndex] is the
     * index of the track it heads, so a disc number that comes back later in the list still has a unique key.
     */
    data class DiscHeader(val disc: Int, val firstIndex: Int) : AlbumRow

    /** A track, and its [index] in the album's own track list (what "play from here" starts at). */
    data class TrackItem(val track: Track, val index: Int) : AlbumRow
}

/** True when [tracks] span more than one disc number (tracks with no disc number do not count). */
fun isMultiDisc(tracks: List<Track>): Boolean = tracks.mapNotNullTo(HashSet()) { it.discNo }.size > 1

/**
 * The rows of an album's list, from its tracks in album order (disc, then track number — the repository's
 * order, kept as is): plain track rows for a single-disc album; for a multi-disc one, a [AlbumRow.DiscHeader]
 * wherever the disc number changes to a known one. A track with no disc number stays under the header before it.
 */
fun albumRows(tracks: List<Track>): List<AlbumRow> {
    if (!isMultiDisc(tracks)) return tracks.mapIndexed { index, track -> AlbumRow.TrackItem(track, index) }
    val rows = ArrayList<AlbumRow>(tracks.size + 4)
    var currentDisc: Int? = null
    tracks.forEachIndexed { index, track ->
        val disc = track.discNo
        if (disc != null && disc != currentDisc) {
            rows += AlbumRow.DiscHeader(disc, index)
            currentDisc = disc
        }
        rows += AlbumRow.TrackItem(track, index)
    }
    return rows
}

/** The lazy-list key of an album row: the disc + the track it heads for a header, the position + track for a track. */
fun albumRowKey(row: AlbumRow): String = when (row) {
    is AlbumRow.DiscHeader -> "disc-${row.disc}|${row.firstIndex}"
    is AlbumRow.TrackItem  -> positionalTrackKey(row.track, row.index)
}

// ── Running times and meta lines ────────────────────────────────────────────

/** The total running time of [tracks] in ms (a negative duration — unknown — counts as zero). */
fun totalDurationMs(tracks: List<Track>): Long = tracks.sumOf { it.durationMs.coerceAtLeast(0L) }

/**
 * Joins the non-blank [parts] of a hero's meta line ("2019 · 12 tracks · 48m") with [separator] — a string
 * resource, so the punctuation follows the language too.
 */
fun metaLine(parts: List<String?>, separator: String): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(separator)

// ── Carousels ───────────────────────────────────────────────────────────────

/** The two Tracks-tab carousels, in display order. */
enum class CarouselKind { RECENTLY_PLAYED, MOST_PLAYED }

/** One carousel and its tracks (never empty). */
@Immutable
data class CarouselSection(val kind: CarouselKind, val tracks: List<Track>)

/**
 * The carousels to show above All tracks: Recently played, then Most played, each only when it has something —
 * so a library nobody has played yet shows neither and the list starts right under the bar. Most played keeps
 * only tracks that have actually been played (the repository orders by play count, which pads a short history
 * with never-played tracks).
 */
fun carouselSections(recentlyPlayed: List<Track>, mostPlayed: List<Track>): List<CarouselSection> = buildList {
    if (recentlyPlayed.isNotEmpty()) add(CarouselSection(CarouselKind.RECENTLY_PLAYED, recentlyPlayed))
    val played = mostPlayed.filter { it.playCount > 0 }
    if (played.isNotEmpty()) add(CarouselSection(CarouselKind.MOST_PLAYED, played))
}

// ── List keys ───────────────────────────────────────────────────────────────

/**
 * The lazy-list key of a track in a list where it appears at most once (All tracks, search results): its media
 * id, so the list keeps its scroll anchor while a sync inserts rows above.
 */
fun trackKeyOf(track: Track): String = track.key.mediaId

/**
 * The key of a track in a list where it may repeat (a playlist, an album list) — its [position] as well, so two
 * entries of one track never collide.
 */
fun positionalTrackKey(track: Track, position: Int): String = "$position|${track.key.mediaId}"

// ── Sync ────────────────────────────────────────────────────────────────────

/**
 * What the Tracks subtitle shows while sources sync: the number of items written so far across every source
 * that is running, or null when none is (the subtitle shows the track count instead).
 */
fun syncProgressOf(states: Map<String, SyncState>): Int? {
    val running = states.values.filterIsInstance<SyncState.Running>()
    return if (running.isEmpty()) null else running.sumOf { it.done.coerceAtLeast(0) }
}

/** True while any source is syncing. */
fun anySyncRunning(states: Map<String, SyncState>): Boolean = states.values.any { it is SyncState.Running }

/** How long a pull-to-refresh waits for its sync to start before letting the indicator go. */
const val REFRESH_START_TIMEOUT_MS: Long = 3_000

/**
 * The longest the pull-to-refresh indicator stays held. A first sync of a large library runs for minutes; past
 * this the indicator retracts and the Tracks subtitle ("Syncing… N") carries the progress instead.
 */
const val REFRESH_MAX_HOLD_MS: Long = 10_000

/**
 * Suspends for one pull-to-refresh round: until a sync starts (or [startTimeoutMs] passes with none — no source,
 * or WorkManager deferring the work) and then until no source is running any more, never longer than [maxMs] in
 * all. The indicator is held for exactly this long.
 */
suspend fun awaitSyncRound(
    states        : Flow<Map<String, SyncState>>,
    startTimeoutMs: Long = REFRESH_START_TIMEOUT_MS,
    maxMs         : Long = REFRESH_MAX_HOLD_MS,
) {
    withTimeoutOrNull(maxMs) {
        val started = withTimeoutOrNull(startTimeoutMs) { states.first { anySyncRunning(it) } } != null
        if (started) states.first { !anySyncRunning(it) }
    }
}

// ── Empty states ────────────────────────────────────────────────────────────

/**
 * The empty state for a tab, picked from what is known: null (list the rows) whenever there are rows; nothing at
 * all while the list has not loaded or [hasSources] is unknown (the first frames — never flash an empty state that
 * is about to be replaced); the "add a source" card when no source exists; the "still syncing / pull to sync" card
 * when sources exist but this list is empty.
 */
fun emptyStateFor(hasSources: Boolean?, listLoaded: Boolean, listEmpty: Boolean): LibraryEmptyKind? = when {
    !listLoaded         -> LibraryEmptyKind.NOTHING_YET
    !listEmpty          -> null
    hasSources == null  -> LibraryEmptyKind.NOTHING_YET
    hasSources == false -> LibraryEmptyKind.NO_SOURCE
    else                -> LibraryEmptyKind.EMPTY_LIST
}

/** Which empty state a tab shows ([emptyStateFor]); null = it lists its rows. */
enum class LibraryEmptyKind {
    /** Nothing known yet — draw nothing. */
    NOTHING_YET,
    /** No source configured — the "Add a Plex server" card. */
    NO_SOURCE,
    /** Sources exist, this list is empty — the "syncing / pull to sync" card. */
    EMPTY_LIST,
}

// ── Shared-element keys ─────────────────────────────────────────────────────

/** Prefix of every library art shared-element key (card art ↔ detail hero art). */
const val LIBRARY_ART_KEY_PREFIX: String = "lib-art-"

/** Stands in for a provider id in a playlist's art key: playlists are keyed by their local row id. */
const val PLAYLIST_ART_SCOPE: String = "playlist"

/** `"lib-art-<providerId>|<itemId>"` — an album's or an artist's card art and its detail hero art. */
fun libraryArtKey(item: TrackKey): String = LIBRARY_ART_KEY_PREFIX + item.mediaId

/** `"lib-art-playlist|<id>"` — a playlist's card art and its detail hero art. */
fun playlistArtKey(id: Long): String = libraryArtKey(TrackKey(PLAYLIST_ART_SCOPE, id.toString()))
