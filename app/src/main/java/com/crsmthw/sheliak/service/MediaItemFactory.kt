@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.crsmthw.sheliak.R
import com.crsmthw.sheliak.data.provider.ProviderRegistry
import com.crsmthw.sheliak.domain.Album
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.domain.Artist
import com.crsmthw.sheliak.domain.Playlist
import com.crsmthw.sheliak.domain.Track
import com.crsmthw.sheliak.domain.TrackKey

/**
 * Every MediaItem the session hands out is built here, so the player, the notification, the controllers and
 * Android Auto all see the same metadata. A PLAYABLE item's media id is its `TrackKey.mediaId` and its URI a
 * [StreamUri]; browse nodes carry [BrowseIds] ids and no URI.
 */
class MediaItemFactory(private val context: Context, private val providers: ProviderRegistry) {

    /** The queue item for [track]: what the player plays. */
    fun playable(track: Track): MediaItem = playable(track.key, trackMetadata(track))

    /** The queue item for [key] when the index no longer has it: the metadata the controller sent is kept. */
    fun playable(key: TrackKey, metadata: MediaMetadata): MediaItem = MediaItem.Builder()
        .setMediaId(key.mediaId)
        .setUri(StreamUri.of(key))
        .setCustomCacheKey(key.mediaId)
        .setMediaMetadata(metadata.buildUpon().setIsBrowsable(false).setIsPlayable(true).build())
        .build()

    /** [track] as a row of a browse list; [node] (an [BrowseNode.InContext]) says which list. */
    fun trackRow(track: Track, node: BrowseNode): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseIds.id(node))
        .setMediaMetadata(trackMetadata(track))
        .build()

    fun folder(node: BrowseNode, title: String, mediaType: Int, childStyle: Int? = null): MediaItem {
        val extras = childStyle?.let { style ->
            Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            }
        }
        return MediaItem.Builder()
            .setMediaId(BrowseIds.id(node))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    /** Browsable (its tracks) and playable (the whole album). */
    fun album(album: Album): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseIds.id(BrowseNode.Album(album.key)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(album.title)
                .setAlbumTitle(album.title)
                .setArtist(album.artistName)
                .setAlbumArtist(album.artistName)
                .setReleaseYear(album.year)
                .setArtworkUri(artworkUri(album.art))
                .setExtras(artExtras(album.art))
                .setIsBrowsable(true)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                .build(),
        )
        .build()

    /** Browsable (its albums) only. */
    fun artist(artist: Artist): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseIds.id(BrowseNode.Artist(artist.key)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(artist.name)
                .setArtist(artist.name)
                .setArtworkUri(artworkUri(artist.art))
                .setExtras(artExtras(artist.art))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                .build(),
        )
        .build()

    /** Browsable (its tracks) and playable (the whole playlist). */
    fun playlist(playlist: Playlist): MediaItem = MediaItem.Builder()
        .setMediaId(BrowseIds.id(BrowseNode.Playlist(playlist.id)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(if (playlist.isLikedSongs) context.getString(R.string.playback_liked_songs) else playlist.title)
                .setArtworkUri(artworkUri(playlist.art))
                .setExtras(artExtras(playlist.art))
                .setIsBrowsable(true)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .build(),
        )
        .build()

    fun trackMetadata(track: Track): MediaMetadata = MediaMetadata.Builder()
        .setTitle(track.title)
        .setArtist(track.artistName)
        .setAlbumTitle(track.albumTitle)
        .setTrackNumber(track.trackNo)
        .setDiscNumber(track.discNo)
        .setReleaseYear(track.year)
        .setDurationMs(track.durationMs.takeIf { it > 0 })
        .setArtworkUri(artworkUri(track.art))
        .setExtras(artExtras(track.art))
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .build()

    /** The provider's art model when it is a plain URL or content URI (Android Auto loads it itself). */
    private fun artworkUri(ref: ArtRef?): Uri? {
        ref ?: return null
        val model = providers.current(ref.providerId)?.artModel(ref, ART_SIZE_PX) as? String ?: return null
        val uri = model.toUri()
        return uri.takeIf { it.scheme in ARTWORK_SCHEMES }
    }

    companion object {
        /** The art size asked of a provider for the notification, lock screen and Android Auto. */
        const val ART_SIZE_PX: Int = 512

        private val ARTWORK_SCHEMES = setOf("http", "https", "content", "file", "android.resource")
        private const val EXTRA_ART_PROVIDER = "com.crsmthw.sheliak.ART_PROVIDER"
        private const val EXTRA_ART_PATH = "com.crsmthw.sheliak.ART_PATH"

        /**
         * The item a CONTROLLER sends for [track] (`setMediaItems` / `addMediaItems`): its media id and metadata,
         * no URI — the session rebuilds the playable item ([SheliakMediaLibraryCallback]).
         */
        fun request(track: Track): MediaItem = MediaItem.Builder()
            .setMediaId(track.key.mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName)
                    .setAlbumTitle(track.albumTitle)
                    .setDurationMs(track.durationMs.takeIf { it > 0 })
                    .setExtras(artExtras(track.art))
                    .build(),
            )
            .build()

        /** The art reference [CoilBitmapLoader] asks the provider about, carried in the metadata's extras. */
        fun artRefOf(extras: Bundle?): ArtRef? {
            extras ?: return null
            val providerId = extras.getString(EXTRA_ART_PROVIDER) ?: return null
            val path = extras.getString(EXTRA_ART_PATH) ?: return null
            return ArtRef(providerId, path)
        }

        private fun artExtras(ref: ArtRef?): Bundle? = ref?.let {
            Bundle().apply {
                putString(EXTRA_ART_PROVIDER, it.providerId)
                putString(EXTRA_ART_PATH, it.path)
            }
        }
    }
}
