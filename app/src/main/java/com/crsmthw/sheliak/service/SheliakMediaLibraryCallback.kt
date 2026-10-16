@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.service

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.db.NormalizedKeys
import com.crsmthw.sheliak.data.repository.LibraryRepository
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.toPlayerRepeatMode
import com.crsmthw.sheliak.util.AlphabetSections
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The session's library side: the Android Auto browse tree, search, and the one place requested media items are
 * turned into playable ones (a controller sends media ids; Auto sends node ids; a resumption sends nothing).
 *
 * Tree: root → Tracks / Albums / Artists / Playlists / Recently played, all from the index (LibraryRepository);
 * Auto's root-children hint (four tabs on a phone projection) drops the last, Recently played. Albums and playlists
 * are playable folders; a track row inside a list carries its list ([BrowseNode.InContext]), so playing it queues
 * the whole list from that track. Auto does not page and lists only so many rows of one level, so a top-level list
 * of more than [AutoBrowseGrouping.AUTO_GROUP_MIN] items is LETTER FOLDERS ([BrowseNode.Section], one per alphabet
 * section, exactly the app's fast-scroller sections), each listing all of its rows; a track row inside one queues
 * that letter from that track. Only Recently played ([RECENT_CAP]) and search ([SEARCH_LIMIT]) are capped.
 *
 * Work runs on [scope] off the main thread (building thousands of MediaItems must not jank the UI, which shares
 * the main thread with this service); only player calls hop back to main.
 *
 * Only Sheliak, trusted controllers and a short allowlist may connect ([SessionAccessPolicy]): the items carry
 * provider art URLs with the server token in them.
 */
class SheliakMediaLibraryCallback(
    private val context: Context,
    private val library: LibraryRepository,
    private val items: MediaItemFactory,
    private val queueStore: QueueStore,
    private val scope: CoroutineScope,
) : MediaLibrarySession.Callback {

    /** Android Auto's root-children hint (tabs), remembered from the root request. */
    @Volatile
    private var rootChildrenLimit = Int.MAX_VALUE

    // ── Connection + custom commands ─────────────────────────────────────────

    /**
     * The access gate. Media3 1.11's default `onConnect` is a placeholder result with NO commands (the session
     * then asks `onConnectAsync`), so nothing here is derived from `super`: an accepted controller gets Media3's
     * full default commands explicitly — an allowlisted one too, which Media3 would otherwise hold to read-only
     * commands when it is not trusted (Android Auto must play, skip and browse).
     */
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val access = SessionAccessPolicy.decide(
            packageName            = controller.packageName,
            ownPackage             = context.packageName,
            trusted                = controller.isTrusted,
            packageNameVerified    = controller.isPackageNameVerified,
            notificationController = session.isMediaNotificationController(controller),
        )
        if (access == SessionAccess.REJECT) {
            Log.i(TAG, "Rejected media controller ${controller.packageName}")
            return MediaSession.ConnectionResult.reject()
        }
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .setAvailableSessionCommands(
                if (access == SessionAccess.OWN_APP) {
                    // Our own controller (PlayerStateManager) may reorder a shuffled queue in play order.
                    sessionCommands.buildUpon().add(SheliakCommands.moveInPlayOrder).build()
                } else {
                    sessionCommands
                },
            )
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != SheliakCommands.MOVE_IN_PLAY_ORDER) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        val player = session.player as? ExoPlayer
        val order = player?.shuffleOrder as? SheliakShuffleOrder
        if (player == null || order == null) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        val from = args.getInt(SheliakCommands.ARG_FROM, -1)
        val to = args.getInt(SheliakCommands.ARG_TO, -1)
        if (from !in 0 until order.length || to !in 0 until order.length) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
        }
        player.setShuffleOrder(order.movedInPlayOrder(from, to))
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    // ── Browse tree ──────────────────────────────────────────────────────────

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        params?.extras?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 0)
            ?.takeIf { it > 0 }
            ?.let { rootChildrenLimit = it }
        val rootParams = LibraryParams.Builder()
            .setExtras(
                Bundle().apply {
                    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                    putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
                },
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(folderItem(BrowseNode.Root), rootParams))
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.Default) {
        val item = itemFor(mediaId)
        if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.Default) {
        val node = BrowseIds.parse(parentId)
        val children = node?.let { childrenOf(it) }
        if (children == null) {
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else {
            LibraryResult.ofItemList(pageOf(children, page, pageSize), params)
        }
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future(Dispatchers.Default) {
        val count = searchItems(query).size
        withContext(Dispatchers.Main.immediate) { session.notifySearchResultChanged(browser, query, count, params) }
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.Default) {
        LibraryResult.ofItemList(pageOf(searchItems(query), page, pageSize), params)
    }

    // ── Requested items → playable items ─────────────────────────────────────

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = scope.future(Dispatchers.Default) {
        resolve(mediaItems).flatten().toMutableList()
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.future(Dispatchers.Default) {
        // One row tapped in a browse list: the whole list (a letter folder: that letter), from that row.
        val row = mediaItems.singleOrNull()?.let { BrowseIds.parse(it.mediaId) } as? BrowseNode.InContext
        if (row != null) {
            val list = tracksOf(row.parent).orEmpty()
            val at = list.indexOfFirst { it.key == row.track }
            if (at >= 0) return@future MediaItemsWithStartPosition(list.map(items::playable), at, startPositionMs)
        }
        val groups = resolve(mediaItems)
        // A voice search that matched nothing (phone Assistant and Auto both send ONE item with an empty mediaId
        // and a searchQuery) fails the request, so the queue that is playing is left alone instead of being
        // replaced by an empty one. Media3 never touches the player when this future fails.
        val voiceSearch = mediaItems.isNotEmpty() &&
            mediaItems.all { it.mediaId.isEmpty() && it.requestMetadata.searchQuery != null }
        if (voiceSearch && groups.all { it.isEmpty() }) {
            throw NoSuchElementException("No tracks match the voice search")
        }
        val start = PlaylistExpansion.remapStartIndex(groups.map { it.size }, startIndex)
        MediaItemsWithStartPosition(groups.flatten(), start, startPositionMs)
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.future(Dispatchers.Default) {
        val restored = restoredQueue()
        if (restored == null) {
            MediaItemsWithStartPosition(emptyList(), 0, 0)
        } else {
            if (isForPlayback) {
                withContext(Dispatchers.Main.immediate) {
                    mediaSession.player.shuffleModeEnabled = restored.shuffle
                    mediaSession.player.repeatMode = restored.repeat.toPlayerRepeatMode()
                }
            }
            MediaItemsWithStartPosition(restored.items, restored.startIndex, restored.positionMs)
        }
    }

    /** The persisted queue as playable items, realigned past tracks no longer in the index; null when none. */
    suspend fun restoredQueue(): RestoredQueue? {
        val snapshot = queueStore.restore() ?: return null
        val tracks = library.tracks(snapshot.keys)
        val kept = tracks.mapTo(HashSet()) { it.key }
        val (start, position) =
            QueueSnapshots.realign(snapshot.keys, snapshot.currentIndex, snapshot.positionMs, kept) ?: return null
        return RestoredQueue(tracks.map(items::playable), start, position, snapshot.shuffle, snapshot.repeat)
    }

    /**
     * Each requested item → the playable items it stands for, one group per request so a start index can be
     * remapped: a track (from the index, else from the metadata the controller sent), an album or playlist (its
     * tracks), a row of a list (its track), a voice search (`requestMetadata.searchQuery`, its tracks).
     */
    private suspend fun resolve(requested: List<MediaItem>): List<List<MediaItem>> {
        val bare = requested.filter { !it.mediaId.startsWith(BrowseIds.PREFIX) }.mapNotNull { TrackKey.parseMediaId(it.mediaId) }
        val indexed = library.tracks(bare).associateBy { it.key }
        return requested.map { item ->
            val node = BrowseIds.parse(item.mediaId)
            val query = item.requestMetadata.searchQuery
            when {
                node is BrowseNode.InContext -> library.track(node.track)?.let { listOf(items.playable(it)) }.orEmpty()
                node != null                 -> tracksOf(node).orEmpty().map(items::playable)
                item.mediaId.isEmpty() && query != null -> searchTracks(query).map(items::playable)
                else -> TrackKey.parseMediaId(item.mediaId)
                    ?.let { key -> listOf(indexed[key]?.let(items::playable) ?: items.playable(key, item.mediaMetadata)) }
                    .orEmpty()
            }
        }
    }

    /**
     * The tracks a playable folder (or a track list) plays, in its order — every one: what a row of the list queues,
     * like a row in the app; null for a node with no tracks.
     */
    private suspend fun tracksOf(node: BrowseNode): List<Track>? = when (node) {
        BrowseNode.Tracks      -> library.tracks.first()
        BrowseNode.Recent      -> library.recentlyPlayed(RECENT_CAP).first()
        is BrowseNode.Album    -> library.albumTracks(node.key).first()
        is BrowseNode.Playlist -> library.playlistTracks(node.id).first()
        is BrowseNode.Section  -> if (node.list == BrowseNode.Tracks) tracksInSection(node.key) else null
        else                   -> null
    }

    /** The tracks of the Tracks letter folder [key], in the Tracks list's order. */
    private suspend fun tracksInSection(key: String): List<Track> {
        val tracks = library.tracks.first()
        return AutoBrowseGrouping.membersOf(tracks.map(Track::titleSort), key).map(tracks::get)
    }

    private suspend fun childrenOf(node: BrowseNode): List<MediaItem>? = when (node) {
        BrowseNode.Root         -> rootChildren()
        BrowseNode.Tracks, BrowseNode.Albums, BrowseNode.Artists, BrowseNode.Playlists -> listChildren(node)
        is BrowseNode.Section   -> sectionChildren(node)
        is BrowseNode.Artist    -> library.artistAlbums(node.key).first().map(items::album)
        is BrowseNode.InContext -> null
        else -> tracksOf(node)?.map { items.trackRow(it, BrowseNode.InContext(node, it.key)) }
    }

    /**
     * A top-level list's children: its rows when it has at most [AutoBrowseGrouping.AUTO_GROUP_MIN], else one
     * letter folder per alphabet section, in the order the app's list shows them ([AutoBrowseGrouping.sectionsOf]).
     */
    private suspend fun listChildren(list: BrowseNode): List<MediaItem> {
        val rows = rowsOf(list) ?: return emptyList()
        if (!AutoBrowseGrouping.isGrouped(rows.size)) return rows.browseItems(list, rows.sortKeys.indices)
        return AutoBrowseGrouping.sectionsOf(rows.sortKeys).map { sectionFolder(list, it) }
    }

    /**
     * A letter folder's children: every row of its section, uncapped, in the list's order. A track row carries the
     * folder as its parent, so playing it queues that letter from that track ([tracksOf]).
     */
    private suspend fun sectionChildren(section: BrowseNode.Section): List<MediaItem> {
        val rows = rowsOf(section.list) ?: return emptyList()
        return rows.browseItems(section, AutoBrowseGrouping.membersOf(rows.sortKeys, section.key))
    }

    /**
     * A top-level list's rows, from one read, with the sort key of each — the very keys the app's list is ordered
     * by and its alphabet scroller indexes (LibraryViewModel's `trackIndex` / `albumIndex` / `artistIndex` /
     * `playlistIndex`): a track's `titleSort`, an album's or an artist's index sort key (the provider's sort title
     * when it sent one), a playlist's normalised title. So Auto's letter folders and the app's scroller agree. Null
     * for a node that is not one of the four lists.
     */
    private suspend fun rowsOf(list: BrowseNode): ListRows? = when (list) {
        BrowseNode.Tracks -> {
            val tracks = library.tracks.first()
            ListRows(tracks.map(Track::titleSort)) { row, parent ->
                tracks[row].let { items.trackRow(it, BrowseNode.InContext(parent, it.key)) }
            }
        }
        BrowseNode.Albums -> {
            val albums = library.albumsSorted.first()
            ListRows(albums.sortKeys) { row, _ -> items.album(albums.items[row]) }
        }
        BrowseNode.Artists -> {
            val artists = library.artistsSorted.first()
            ListRows(artists.sortKeys) { row, _ -> items.artist(artists.items[row]) }
        }
        BrowseNode.Playlists -> {
            val playlists = library.playlists.first()
            ListRows(playlists.map { NormalizedKeys.sortKey(it.title) }) { row, _ -> items.playlist(playlists[row]) }
        }
        else -> null
    }

    /** Whether the Albums tab shows letter folders (one count read): its child style follows ([folderItem]). */
    private suspend fun albumsGrouped(): Boolean = AutoBrowseGrouping.isGrouped(library.counts.first().albums)

    private suspend fun rootChildren(): List<MediaItem> = listOf(
        folderItem(BrowseNode.Tracks),
        folderItem(BrowseNode.Albums, grouped = albumsGrouped()),
        folderItem(BrowseNode.Artists),
        folderItem(BrowseNode.Playlists),
        folderItem(BrowseNode.Recent),
    ).take(rootChildrenLimit)

    /**
     * A fixed folder. [grouped] says whether the list shows letter folders; only Albums' child style depends on it:
     * its albums show as a grid of covers, but its letter folders (art-less) as a list.
     */
    private fun folderItem(node: BrowseNode, grouped: Boolean = false): MediaItem = when (node) {
        BrowseNode.Root      -> items.folder(node, context.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        BrowseNode.Tracks    -> items.folder(node, context.getString(R.string.nav_tracks), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        BrowseNode.Albums    -> items.folder(
            node, context.getString(R.string.nav_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
            childStyle = if (grouped) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                         else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        )
        BrowseNode.Artists   -> items.folder(node, context.getString(R.string.nav_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
        BrowseNode.Playlists -> items.folder(node, context.getString(R.string.nav_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        BrowseNode.Recent    -> items.folder(node, context.getString(R.string.playback_recently_played), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        else                 -> error("Not a folder: $node")
    }

    /**
     * The letter folder [section] of [list]: a list item titled with the section's label ("#" through the same
     * string resource the app's scroller bubble uses). The albums inside an Albums letter show as a grid of covers,
     * like the flat Albums tab.
     */
    private fun sectionFolder(list: BrowseNode, section: AutoSection): MediaItem = items.folder(
        node       = BrowseNode.Section(list, section.key),
        title      = if (section.key == AlphabetSections.OTHER) context.getString(R.string.library_scroller_other)
                     else section.label,
        mediaType  = when (list) {
            BrowseNode.Albums    -> MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
            BrowseNode.Artists   -> MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
            BrowseNode.Playlists -> MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            else                 -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        },
        childStyle = if (list == BrowseNode.Albums) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM else null,
    )

    /**
     * A browse item for any id: a folder, a letter folder (null once its letter has no rows), an album / artist /
     * playlist, a track (bare or in a list).
     */
    private suspend fun itemFor(mediaId: String): MediaItem? {
        val node = BrowseIds.parse(mediaId)
        return when (node) {
            null -> TrackKey.parseMediaId(mediaId)?.let { library.track(it) }?.let(items::playable)
            BrowseNode.Root, BrowseNode.Tracks, BrowseNode.Artists, BrowseNode.Playlists,
            BrowseNode.Recent -> folderItem(node)
            BrowseNode.Albums       -> folderItem(node, grouped = albumsGrouped())
            // The label of a script section is its first title's first character: regroup the list to find it.
            is BrowseNode.Section   -> rowsOf(node.list)
                ?.let { rows -> AutoBrowseGrouping.sectionsOf(rows.sortKeys).firstOrNull { it.key == node.key } }
                ?.let { sectionFolder(node.list, it) }
            is BrowseNode.Album     -> library.album(node.key).first()?.let(items::album)
            is BrowseNode.Artist    -> library.artist(node.key).first()?.let(items::artist)
            is BrowseNode.Playlist  -> library.playlist(node.id).first()?.let(items::playlist)
            is BrowseNode.InContext -> library.track(node.track)?.let { items.trackRow(it, node) }
        }
    }

    private suspend fun searchItems(query: String): List<MediaItem> {
        val results = library.search(query, SEARCH_LIMIT).first()
        return results.tracks.map(items::playable) +
            results.albums.map(items::album) +
            results.artists.map(items::artist) +
            results.playlists.map(items::playlist)
    }

    /** A voice "play …": the matching tracks; an empty query plays what was played last. */
    private suspend fun searchTracks(query: String): List<Track> =
        if (query.isBlank()) {
            library.recentlyPlayed(RECENT_CAP).first()
        } else {
            library.search(query, SEARCH_LIMIT).first().let { results ->
                results.tracks.ifEmpty {
                    results.albums.firstOrNull()?.let { library.albumTracks(it.key).first() }.orEmpty()
                }
            }
        }

    private fun pageOf(all: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        val range = PlaylistExpansion.pageRange(all.size, page, pageSize) ?: return emptyList()
        return all.subList(range.first, range.last + 1)
    }

    /** The persisted queue, ready for the player. */
    data class RestoredQueue(
        val items: List<MediaItem>,
        val startIndex: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeat: RepeatMode,
    )

    /**
     * A top-level list's rows ([rowsOf]): [sortKeys], one per row in list order, and how a row becomes a browse
     * item — built only for the rows a request lists, not for the whole library.
     */
    private class ListRows(
        val sortKeys: List<String>,
        private val itemAt: (row: Int, parent: BrowseNode) -> MediaItem,
    ) {
        val size: Int get() = sortKeys.size

        /** [rows] as browse items listed inside [parent] (the list itself, or one of its letter folders). */
        fun browseItems(parent: BrowseNode, rows: Iterable<Int>): List<MediaItem> = rows.map { itemAt(it, parent) }
    }

    companion object {
        private const val TAG = "SheliakSession"

        /** Tracks in Recently played (the tab, and a voice "play …" with no query): a window, not a whole list. */
        const val RECENT_CAP: Int = 50

        /** Hits per kind for Auto's search and a voice "play …". */
        const val SEARCH_LIMIT: Int = 50
    }
}

/** The session's own commands, for its own controller only. */
object SheliakCommands {
    const val MOVE_IN_PLAY_ORDER: String = "com.crsmthw.sheliak.MOVE_IN_PLAY_ORDER"
    const val ARG_FROM: String = "from"
    const val ARG_TO: String = "to"

    val moveInPlayOrder: SessionCommand = SessionCommand(MOVE_IN_PLAY_ORDER, Bundle.EMPTY)
}
