package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crsmthw.sheliak.domain.ArtRef
import com.crsmthw.sheliak.util.NavTransitionMillis

/**
 * Turns an [ArtRef] into something Coil loads. Only the provider that indexed the picture can do that (a Plex
 * thumb needs that server's address and token), so the app root provides one resolver that asks the live
 * provider ([LocalArtResolver]); everything below it only ever hands over the [ArtRef].
 */
fun interface ArtResolver {
    /** The Coil model for [art] at about [sizePx] square, or null when there is none (→ placeholder art). */
    fun model(art: ArtRef, sizePx: Int): Any?
}

/**
 * The app's [ArtResolver], provided once at the root of the shell (above the player surface, so the mini bar and
 * the pop-out see it too). The default resolves nothing, so a preview or a surface outside the shell shows
 * placeholder art instead of failing.
 */
val LocalArtResolver = staticCompositionLocalOf<ArtResolver> { ArtResolver { _, _ -> null } }

/**
 * THE one way the app draws a cover: a Coil image of [LocalArtResolver]'s model for [art], cropped to fill and
 * clipped to [shape], over [PlaceholderArt]. The placeholder shows while there is no art, while it loads, and
 * when loading fails (the image then draws nothing), so a missing cover looks the same in a row, a card, a
 * carousel and a hero.
 *
 * The image fades in over the placeholder with Coil's own crossfade, a finite [NavTransitionMillis] fade; an image
 * already in the memory cache appears at once. The drawn size comes from [modifier] (a `size(48.dp)` row
 * thumbnail, an `aspectRatio(1f)` card); [sizePx] picks the provider's size bucket AND the decode size, so pass the
 * tile's size in px (see [artSizePx]). Decoding at [sizePx] rather than at the layout size keeps one shared model
 * sharp at every end of a morph: the mini bar, the pop-out card and the full player all pass the player's size, so
 * the bar's bitmap is never drawn scaled up through the "album-art" flight.
 *
 * @param contentDescription null where the title next to the art already says what it is (rows, cards).
 */
@Composable
fun Artwork(
    art               : ArtRef?,
    sizePx            : Int,
    contentDescription: String?,
    modifier          : Modifier = Modifier,
    shape             : Shape = MaterialTheme.shapes.small,
) {
    val resolver = LocalArtResolver.current
    // Resolved once per (art, size, resolver): the resolver changes only when the providers are rebuilt, which is
    // exactly when a model that was null (a provider not yet built) can become a URL.
    val model = remember(art, sizePx, resolver) { art?.let { resolver.model(it, sizePx) } }
    Box(modifier = modifier.clip(shape)) {
        PlaceholderArt(modifier = Modifier.fillMaxSize())
        if (model != null) {
            val context = LocalContext.current
            val request = remember(model, sizePx, context) {
                ImageRequest.Builder(context)
                    .data(model)
                    .size(sizePx)
                    .crossfade(NavTransitionMillis)
                    .build()
            }
            AsyncImage(
                model              = request,
                contentDescription = contentDescription,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        }
    }
}

/** [size] in px for [Artwork]'s `sizePx`: the provider rounds it up to its own bucket. */
@Composable
fun artSizePx(size: Dp): Int = with(LocalDensity.current) { size.roundToPx() }
