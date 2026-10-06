package com.crsmthw.sheliak.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.ui.components.LocalArtResolver
import com.crsmthw.sheliak.util.screenTransitionSpec

/**
 * The current track's [AlbumArtColors], computed ONCE by `PlayerSurfaceHost` and provided to everything it
 * hosts — the mini bar, the pop-out card and (through the navigation host) the full player — so a track change
 * decodes and samples its art once, not once per surface. Null until the colours are known, or when the track
 * has no art: readers fall back to theme roles.
 */
val LocalAlbumArtColors = compositionLocalOf<AlbumArtColors?> { null }

/**
 * Loads the colours of [art] (the art the player surfaces show, at [PLAYER_ART_SIZE_PX] — the same model
 * `Artwork` loads, so the request hits Coil's disk cache) and keeps them across recompositions. Recomputed when
 * the art, the theme's darkness or its `primary` (the fallback) changes; null for no art or a failed load.
 */
@Composable
fun rememberAlbumArtColors(art: ArtRef?): AlbumArtColors? {
    val context  = LocalContext.current
    val resolver = LocalArtResolver.current
    val isDark   = isDarkColor(MaterialTheme.colorScheme.background)
    val fallback = MaterialTheme.colorScheme.primary.toArgb()
    var colors by remember { mutableStateOf<AlbumArtColors?>(null) }
    LaunchedEffect(art, isDark, fallback, resolver) {
        val model = art?.let { resolver.model(it, PLAYER_ART_SIZE_PX) }
        colors = loadAlbumArtColors(context, model, isDark, fallback)
    }
    return colors
}

/**
 * The album-art colours resolved against the theme and animated on the ONE finite spec, so a track change
 * re-tints the player in step with everything else (never a spring: colour must not overshoot).
 *
 * @property edge the cover's border colour — backgrounds that merge with the art (fallback `surfaceContainer`).
 * @property accent the vibrant accent — the play button's container (fallback `primary`).
 * @property onAccent the theme's lighter or darker on-surface role, whichever reads on [accent].
 * @property surfaceAccent the contrast-safe tint — seek bar, toggles, chip, progress line (fallback `primary`).
 */
@Immutable
data class PlayerTints(
    val edge         : Color,
    val accent       : Color,
    val onAccent     : Color,
    val surfaceAccent: Color,
)

/** [PlayerTints] for [colors] (null → theme fallbacks), animated on `screenTransitionSpec()`. */
@Composable
fun rememberPlayerTints(colors: AlbumArtColors?): PlayerTints {
    val scheme = MaterialTheme.colorScheme
    val accentTarget = colors?.accent?.let { Color(it) } ?: scheme.primary
    val (lightContent, darkContent) = lighterAndDarker(scheme.surface, scheme.onSurface)
    val edge by animateColorAsState(
        targetValue   = colors?.edge?.let { Color(it) } ?: scheme.surfaceContainer,
        animationSpec = screenTransitionSpec(),
        label         = "playerEdge",
    )
    val accent by animateColorAsState(
        targetValue   = accentTarget,
        animationSpec = screenTransitionSpec(),
        label         = "playerAccent",
    )
    val onAccent by animateColorAsState(
        targetValue   = if (isDarkColor(accentTarget)) lightContent else darkContent,
        animationSpec = screenTransitionSpec(),
        label         = "playerOnAccent",
    )
    val surfaceAccent by animateColorAsState(
        targetValue   = colors?.surfaceAccent?.let { Color(it) } ?: scheme.primary,
        animationSpec = screenTransitionSpec(),
        label         = "playerSurfaceAccent",
    )
    return PlayerTints(edge = edge, accent = accent, onAccent = onAccent, surfaceAccent = surfaceAccent)
}
