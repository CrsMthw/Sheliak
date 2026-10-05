package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Corner radius of every art tile in the app — rows, cards and heroes alike. */
private val ArtCornerRadius = 8.dp

/**
 * The ONE stand-in for missing album art — a track with no embedded cover, an album the provider
 * has no thumbnail for, a playlist before its mosaic exists, and every art slot while M0 has no
 * library at all. One composable so a missing cover looks the same in a 48dp row, a carousel card
 * and a full-width hero, and so a later restyle is one edit.
 *
 * A `surfaceContainerHighest` tile with an `onSurfaceVariant` music note: theme roles, so it follows
 * light / dark / AMOLED / accent like the rest of the surface, and it reads as "art goes here"
 * without competing with real covers next to it. The size comes entirely from [modifier] (a
 * `size(48.dp)` row thumbnail, an `aspectRatio(1f)` hero); the note scales to half the tile, so any
 * size works without a size parameter.
 *
 * Decorative by default ([contentDescription] null): wherever it appears, the title next to it
 * already says what the item is. Pass a description only where the art stands alone.
 */
@Composable
fun PlaceholderArt(
    modifier          : Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(ArtCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.MusicNote,
            contentDescription = contentDescription,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.fillMaxSize(0.5f),
        )
    }
}
