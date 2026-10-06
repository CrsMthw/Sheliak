@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.service

import android.content.Context
import android.os.Bundle
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
import com.crsmthw.sheliak.data.repository.LibraryRepository
import com.crsmthw.sheliak.domain.RepeatMode
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey
import com.crsmthw.sheliak.player.toPlayerRepeatMode
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
 * Tree: root → Tracks / Albums / Artists / Playlists / Recently played, all from the index (LibraryRepository).
 * Albums and playlists are playable folders; a track row inside a list carries its list ([BrowseNode.InContext]),
 * so playing it queues the whole list from that track. Lists are capped at [LIST_CAP] rows and paged.
 *
 * Work runs on [scope] off the main thread (building thousands of MediaItems must not jank the UI, which shares
 * the main thread with this service); only player calls hop back to main.
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

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val base = super.onConnect(session, controller)
        val ownController = controller.packageName == context.packageName && !session.isMediaNotificationController(controller)
        if (!base.isAccepted || !ownController) return base
        // Our own controller (PlayerStateManager) may reorder a shuffled queue in play order.
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailablePlayerCommands(base.availablePlayerCommands)
            .setAvailableSessionCommands(
                base.availableSessionCommands.buildUpon().add(SheliakCommands.moveInPlayOrder).build(),
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
        // One row tapped in a browse list: the whole list, from that row.
        val row = mediaItems.singleOrNull()?.let { BrowseIds.parse(it.mediaId) } as? BrowseNode.InContext
        if (row != null) {
            val list = tracksOf(row.parent).orEmpty()
            val at = list.indexOfFirst { it.key == row.track }
            if (at >= 0) return@future MediaItemsWithStartPosition(list.map(items::playable), at, startPositionMs)
        }
        val groups = resolve(mediaItems)
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

    /** The tracks a playable folder (or a track list) plays, in its order; null for a node with no tracks. */
    private suspend fun tracksOf(node: BrowseNode): List<Track>? = when (node) {
        BrowseNode.Tracks      -> library.tracks.first().take(LIST_CAP)
        BrowseNode.Recent      -> library.recentlyPlayed(RECENT_CAP).first()
        is BrowseNode.Album    -> library.albumTracks(node.key).first()
        is BrowseNode.Playlist -> library.playlistTracks(node.id).first()
        else                   -> null
    }

    private suspend fun childrenOf(node: BrowseNode): List<MediaItem>? = when (node) {
        BrowseNode.Root      -> rootChildren()
        BrowseNode.Albums    -> library.albums.first().take(LIST_CAP).map(items::album)
        BrowseNode.Artists   -> library.artists.first().take(LIST_CAP).map(items::artist)
        BrowseNode.Playlists -> library.playlists.first().take(LIST_CAP).map(items::playlist)
        is BrowseNode.Artist -> library.artistAlbums(node.key).first().map(items::album)
        is BrowseNode.InContext -> null
        else -> tracksOf(node)?.map { items.trackRow(it, BrowseNode.InContext(node, it.key)) }
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        folderItem(BrowseNode.Tracks),
        folderItem(BrowseNode.Albums),
        folderItem(BrowseNode.Artists),
        folderItem(BrowseNode.Playlists),
        folderItem(BrowseNode.Recent),
    ).take(rootChildrenLimit)

    private fun folderItem(node: BrowseNode): MediaItem = when (node) {
        BrowseNode.Root      -> items.folder(node, context.getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        BrowseNode.Tracks    -> items.folder(node, context.getString(R.string.nav_tracks), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        BrowseNode.Albums    -> items.folder(
            node, context.getString(R.string.nav_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
            childStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        )
        BrowseNode.Artists   -> items.folder(node, context.getString(R.string.nav_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
        BrowseNode.Playlists -> items.folder(node, context.getString(R.string.nav_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        BrowseNode.Recent    -> items.folder(node, context.getString(R.string.playback_recently_played), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        else                 -> error("Not a folder: $node")
    }

    /** A browse item for any id: a folder, an album / artist / playlist, a track (bare or in a list). */
    private suspend fun itemFor(mediaId: String): MediaItem? {
        val node = BrowseIds.parse(mediaId)
        return when (node) {
            null -> TrackKey.parseMediaId(mediaId)?.let { library.track(it) }?.let(items::playable)
            BrowseNode.Root, BrowseNode.Tracks, BrowseNode.Albums, BrowseNode.Artists, BrowseNode.Playlists,
            BrowseNode.Recent -> folderItem(node)
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

    companion object {
        /** Rows per browse list (Android Auto shows far fewer; search reaches the rest). */
        const val LIST_CAP: Int = 500
        const val RECENT_CAP: Int = 50
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
