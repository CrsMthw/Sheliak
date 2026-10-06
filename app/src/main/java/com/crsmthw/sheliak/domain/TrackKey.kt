package com.crsmthw.sheliak.domain

import androidx.compose.runtime.Immutable

/**
 * The identity of every item in the index — a track, but also an album, an artist or a server playlist (the
 * same pair keys all of them): the provider INSTANCE that serves it ([providerId], e.g. `plex:<machineIdentifier>`)
 * and that provider's own id for it ([itemId], e.g. a Plex ratingKey).
 *
 * A provider id never contains [SEPARATOR]; an item id may, which is why [parseMediaId] splits on the FIRST one.
 */
@Immutable
data class TrackKey(val providerId: String, val itemId: String) {

    /** The Media3 `mediaId` of this item: `"<providerId>|<itemId>"`. [parseMediaId] is its inverse. */
    val mediaId: String get() = "$providerId$SEPARATOR$itemId"

    companion object {
        const val SEPARATOR: Char = '|'

        /**
         * The key a [mediaId] was made from, or null when [mediaId] is not one of ours (no separator, or an empty
         * provider or item id) — a browse-node id from Android Auto, say, or a stale id from another app version.
         */
        fun parseMediaId(mediaId: String): TrackKey? {
            val cut = mediaId.indexOf(SEPARATOR)
            if (cut <= 0 || cut == mediaId.lastIndex) return null
            return TrackKey(mediaId.substring(0, cut), mediaId.substring(cut + 1))
        }
    }
}
