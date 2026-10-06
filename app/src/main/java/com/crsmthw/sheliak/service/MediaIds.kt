package com.crsmthw.sheliak.service

import com.crsmthw.sheliak.domain.TrackKey
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

/**
 * The URI a playable MediaItem carries: `sheliak://track?id=<url-encoded mediaId>`. ExoPlayer needs a URI to build
 * a media source; this one names the track, and `ProviderDataSourceFactory` resolves it to the provider's real
 * stream only when the player opens it (so a queue of 10 000 tracks costs no network, and a URL with a token never
 * sits in the timeline, in logs or in a cache key).
 *
 * The id is in the query, not the path, so Media3's content-type inference never mistakes an item id for a file
 * extension: the source is always progressive. Pure; tested in MediaIdsTest.
 */
object StreamUri {

    const val SCHEME: String = "sheliak"
    private const val PREFIX = "$SCHEME://track?id="

    fun of(key: TrackKey): String = PREFIX + URLEncoder.encode(key.mediaId, UTF_8)

    /** The track [uri] names, or null when it is not one of ours (a content:// or file URI, say). */
    fun keyOf(uri: String): TrackKey? {
        if (!uri.startsWith(PREFIX)) return null
        val mediaId = URLDecoder.decode(uri.substring(PREFIX.length), UTF_8)
        return TrackKey.parseMediaId(mediaId)
    }
}

/**
 * A node of the Android Auto browse tree. A playable track's media id is always its bare `TrackKey.mediaId`; every
 * other node id starts with [BrowseIds.PREFIX], which no provider id can (provider ids are `<type>:<id>` with a
 * lower-case type), so a node id is never mistaken for a track.
 */
sealed interface BrowseNode {
    data object Root : BrowseNode
    data object Tracks : BrowseNode
    data object Albums : BrowseNode
    data object Artists : BrowseNode
    data object Playlists : BrowseNode
    data object Recent : BrowseNode
    data class Album(val key: TrackKey) : BrowseNode
    data class Artist(val key: TrackKey) : BrowseNode
    data class Playlist(val id: Long) : BrowseNode

    /**
     * A track listed inside [parent] (an album, a playlist, Tracks, Recently played). Playing it queues the whole
     * parent from this track on, the way tapping a row does in the app.
     */
    data class InContext(val parent: BrowseNode, val track: TrackKey) : BrowseNode
}

/** [BrowseNode] ↔ media id. Pure; tested in MediaIdsTest. */
object BrowseIds {

    const val PREFIX: String = "@"

    private const val ROOT = "@root"
    private const val TRACKS = "@tracks"
    private const val ALBUMS = "@albums"
    private const val ARTISTS = "@artists"
    private const val PLAYLISTS = "@playlists"
    private const val RECENT = "@recent"
    private const val ALBUM = "@album:"
    private const val ARTIST = "@artist:"
    private const val PLAYLIST = "@playlist:"

    /** `@in:<length of the parent id>:<parent id><track media id>` — length-prefixed, so any id nests safely. */
    private const val IN = "@in:"

    fun id(node: BrowseNode): String = when (node) {
        BrowseNode.Root         -> ROOT
        BrowseNode.Tracks       -> TRACKS
        BrowseNode.Albums       -> ALBUMS
        BrowseNode.Artists      -> ARTISTS
        BrowseNode.Playlists    -> PLAYLISTS
        BrowseNode.Recent       -> RECENT
        is BrowseNode.Album     -> ALBUM + node.key.mediaId
        is BrowseNode.Artist    -> ARTIST + node.key.mediaId
        is BrowseNode.Playlist  -> PLAYLIST + node.id
        is BrowseNode.InContext -> id(node.parent).let { parent -> "$IN${parent.length}:$parent${node.track.mediaId}" }
    }

    /** The node [id] names; null for a bare track media id or anything unknown. */
    fun parse(id: String): BrowseNode? {
        if (!id.startsWith(PREFIX)) return null
        return when {
            id == ROOT               -> BrowseNode.Root
            id == TRACKS             -> BrowseNode.Tracks
            id == ALBUMS             -> BrowseNode.Albums
            id == ARTISTS            -> BrowseNode.Artists
            id == PLAYLISTS          -> BrowseNode.Playlists
            id == RECENT             -> BrowseNode.Recent
            id.startsWith(ALBUM)     -> TrackKey.parseMediaId(id.substring(ALBUM.length))?.let(BrowseNode::Album)
            id.startsWith(ARTIST)    -> TrackKey.parseMediaId(id.substring(ARTIST.length))?.let(BrowseNode::Artist)
            id.startsWith(PLAYLIST)  -> id.substring(PLAYLIST.length).toLongOrNull()?.let(BrowseNode::Playlist)
            id.startsWith(IN)        -> parseInContext(id.substring(IN.length))
            else                     -> null
        }
    }

    /** The track [id] plays: a bare media id, or the track of an [BrowseNode.InContext] id. */
    fun trackOf(id: String): TrackKey? = when (val node = parse(id)) {
        null                    -> if (id.startsWith(PREFIX)) null else TrackKey.parseMediaId(id)
        is BrowseNode.InContext -> node.track
        else                    -> null
    }

    private fun parseInContext(rest: String): BrowseNode? {
        val colon = rest.indexOf(':')
        if (colon <= 0) return null
        val length = rest.substring(0, colon).toIntOrNull() ?: return null
        val start = colon + 1
        if (length <= 0 || start + length > rest.length) return null
        val parent = parse(rest.substring(start, start + length)) ?: return null
        if (parent is BrowseNode.InContext || parent == BrowseNode.Root) return null
        val track = TrackKey.parseMediaId(rest.substring(start + length)) ?: return null
        return BrowseNode.InContext(parent, track)
    }
}
