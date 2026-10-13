package com.crsmthw.sheliak.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.memory.MemoryCache
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
 * the pop-out see it too). The root re-creates it whenever the provider list changes or any provider's
 * `artVersion` moves (a connection picked, a token arrived, the network changed), so every [Artwork] resolves its
 * cover again. The default resolves nothing, so a preview or a surface outside the shell shows placeholder art
 * instead of failing.
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
 * Re-resolving (M1 fix round 1, "covers must appear without switching screens"): the model is remembered on
 * (resolver, art, sizePx), so a new resolver asks the provider again. A CHANGED model (a URL on the connection
 * that was just picked) is a new Coil request; while it loads, the image last shown stays up as its placeholder,
 * so a cover never blanks. An UNCHANGED model is the same request to Coil — fine for a cover that loaded, but a
 * failed one would stay failed — so a load that failed is retried, and only that, once per resolver change since
 * the attempt started — also when the failure lands AFTER the change (a bump while the load was in flight)
 * ([ArtAttempts], [ArtRetry]).
 *
 * First frame (M1 fix round 1b, "the placeholder flashes before the real art"): every request carries a
 * deterministic memory-cache key — (provider, path, sizePx) — and [ArtCacheKeys] remembers which sizes of a cover
 * have loaded. A tile whose own size is not in memory yet (the hero after a 56dp row, the mini bar after a list)
 * asks Coil to show the NEAREST loaded size as its placeholder, and whatever is in memory — its own size or that
 * neighbour — is also drawn directly under the request, so the cover is on screen from the very first frame and
 * the big one fades in over it. Only a cover never loaded at any size shows [PlaceholderArt].
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
    val model = remember(resolver, art, sizePx) { art?.let { resolver.model(it, sizePx) } }
    val load = remember(art, sizePx) { ArtLoad() }
    SideEffect { load.composed(resolver, model) }
    Box(modifier = modifier.clip(shape)) {
        PlaceholderArt(modifier = Modifier.fillMaxSize())
        if (model != null && art != null) {
            val context = LocalContext.current
            val cacheKey = remember(art, sizePx) { ArtCacheKeys.key(art, sizePx) }
            // The image shown last by THIS tile, else the nearest size any tile loaded: Coil's placeholder while
            // this size loads (a changed URL, a bigger bucket), so a cover never drops to the placeholder art.
            val placeholderKey = load.shownKey ?: ArtCacheKeys.nearestLoaded(art, sizePx)
            val request = remember(model, sizePx, context, cacheKey, placeholderKey) {
                ImageRequest.Builder(context)
                    .data(model)
                    .size(sizePx)
                    .memoryCacheKey(cacheKey)
                    .placeholderMemoryCacheKey(placeholderKey)
                    .crossfade(NavTransitionMillis)
                    .build()
            }
            // What memory already holds for this cover — its own size, else the placeholder's — drawn NOW, under
            // the request: Coil's painter can take a frame even for a memory hit, and that frame is the flash.
            val warm = remember(request) {
                val cache = SingletonImageLoader.get(context).memoryCache
                (cache?.get(cacheKey) ?: placeholderKey?.let { cache?.get(it) })?.image?.asPainter(context)
            }
            if (warm != null) {
                Image(
                    painter            = warm,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            // A new attempt is a new painter, and a new painter loads again (Coil caches no failures).
            key(load.attempt) {
                AsyncImage(
                    model              = request,
                    contentDescription = contentDescription,
                    placeholder        = warm,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                    onSuccess          = { success ->
                        load.loaded(success.result.memoryCacheKey)
                        ArtCacheKeys.loaded(art, sizePx)
                    },
                    onError            = { load.failed(model) },
                )
            }
        }
    }
}

/** [size] in px for [Artwork]'s `sizePx`: the provider rounds it up to its own bucket. */
@Composable
fun artSizePx(size: Dp): Int = with(LocalDensity.current) { size.roundToPx() }

/**
 * Memory-cache keys for covers and the sizes each cover has loaded at — pure bookkeeping, unit-tested in
 * ArtCacheKeysTest. The key is (provider, path, sizePx), not the URL, so the same cover at the same size is one
 * entry whichever connection served it. Main thread only (Compose callbacks).
 */
internal object ArtCacheKeys {
    private val loadedSizes = HashMap<ArtRef, MutableSet<Int>>()

    fun key(art: ArtRef, sizePx: Int): MemoryCache.Key = MemoryCache.Key("art:${art.providerId}:${art.path}@$sizePx")

    fun loaded(art: ArtRef, sizePx: Int) {
        loadedSizes.getOrPut(art) { HashSet() }.add(sizePx)
    }

    /**
     * The key of the loaded size nearest to [sizePx]: the largest one below it (never drawn scaled up more than
     * needed), else the smallest one above it; null when no size of this cover has loaded.
     */
    fun nearestLoaded(art: ArtRef, sizePx: Int): MemoryCache.Key? {
        val sizes = loadedSizes[art] ?: return null
        if (sizePx in sizes) return key(art, sizePx)
        val pick = sizes.filter { it < sizePx }.maxOrNull() ?: sizes.filter { it > sizePx }.minOrNull() ?: return null
        return key(art, pick)
    }

    /** Test hook. */
    internal fun clear() = loadedSizes.clear()
}

/**
 * When an [Artwork] loads its cover again — pure, unit-tested in ArtworkRetryTest. Coil's default model equality
 * compares a request's data and cache keys, so a request equal to the one on screen is the SAME load: a provider
 * re-resolve that returns the unchanged URL would leave a failed cover failed until it left the screen. So the
 * attempt moves only when the load of this very model failed AND the resolver changed since that attempt
 * started; a cover that loaded, or whose model changed (a new request anyway) is left alone — no reload, no flash,
 * no cache churn.
 */
internal object ArtRetry {

    /** The attempt to draw with, given the [attempt] drawn so far and the model whose load last failed. */
    fun next(attempt: Int, failedModel: Any?, model: Any?, resolverChanged: Boolean): Int =
        if (resolverChanged && model != null && failedModel == model) attempt + 1 else attempt
}

/**
 * One cover's attempts — pure bookkeeping around [ArtRetry], unit-tested in ArtworkRetryTest. An attempt is
 * anchored to the resolver it STARTED under, not to the last one composed: a bump that lands while the load is
 * still in flight (the pick landing ~100 ms after the token at a cold start) and a failure that arrives after it
 * (a busy server timing the transcode out) still retry once. A retry that fails again under the same resolver
 * stays failed until the next change — never a loop.
 */
internal class ArtAttempts {
    /** The attempt drawn now; the image is keyed on it. */
    var attempt: Int = 0
        private set

    private var latest: ArtResolver? = null
    private var startedUnder: ArtResolver? = null
    private var startedFor: Any? = null
    private var failedModel: Any? = null

    /** After a composition with [resolver] and [model]. True when the attempt moved (draw a fresh load). */
    fun composed(resolver: ArtResolver, model: Any?): Boolean {
        latest = resolver
        if (startedUnder == null || model != startedFor) {
            // A new model is a new request: a new attempt, started under this resolver.
            startedFor = model
            startedUnder = resolver
            failedModel = null
        }
        return retryIfDue(model)
    }

    /** The load of [model] failed. True when the attempt moved (the resolver changed while it loaded). */
    fun failed(model: Any?): Boolean {
        failedModel = model
        return retryIfDue(model)
    }

    /** The load succeeded. */
    fun loaded() {
        failedModel = null
    }

    private fun retryIfDue(model: Any?): Boolean {
        val changed = startedUnder != null && startedUnder !== latest
        val next = ArtRetry.next(attempt, failedModel, model, changed)
        if (next == attempt) return false
        attempt = next
        failedModel = null
        startedUnder = latest
        return true
    }
}

/** One cover's load state: its [ArtAttempts], the attempt as Compose state, and the last image's cache key. */
private class ArtLoad {
    private val attempts = ArtAttempts()

    /** Keys the image; moved by [composed] / [failed] to retry a failed load. */
    var attempt by mutableIntStateOf(0)
        private set

    /** The memory-cache key of the image shown last: the next request's placeholder. */
    var shownKey: MemoryCache.Key? = null
        private set

    fun composed(resolver: ArtResolver, model: Any?) {
        if (attempts.composed(resolver, model)) attempt = attempts.attempt
    }

    fun failed(model: Any?) {
        if (attempts.failed(model)) attempt = attempts.attempt
    }

    fun loaded(key: MemoryCache.Key?) {
        attempts.loaded()
        shownKey = key
    }
}
