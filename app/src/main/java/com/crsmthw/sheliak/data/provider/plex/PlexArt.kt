package com.crsmthw.sheliak.data.provider.plex

/**
 * Cover URLs (PLEX.md §4): `/photo/:/transcode?width=W&height=H&minSize=1&upscale=1&url=<thumb path>` with the
 * server's token as the `X-Plex-Token` QUERY parameter (every X-Plex-* may be one), so the URL alone is a complete
 * Coil model and the app's one ImageLoader needs no per-server headers. Pure; unit-tested in PlexArtTest.
 *
 * The index stores only the server-relative thumb path ([com.crsmthw.sheliak.domain.ArtRef.path]); the host and
 * the token are added here, at display time. Sizes are rounded up to a few buckets so one cover is cached once
 * per bucket rather than once per pixel size.
 */
object PlexArt {

    val SIZE_BUCKETS: IntArray = intArrayOf(128, 256, 512, 1024, 2048)

    /** The smallest bucket ≥ [sizePx] (the largest for anything bigger). */
    fun bucket(sizePx: Int): Int = SIZE_BUCKETS.firstOrNull { it >= sizePx } ?: SIZE_BUCKETS.last()

    /** The transcode URL for [thumb] on the server at [baseUri], or null for a blank thumb. */
    fun url(baseUri: String, thumb: String, sizePx: Int, token: String): String? {
        if (thumb.isBlank()) return null
        val size = bucket(sizePx).toString()
        return PlexRetrofit.baseUrl(baseUri).newBuilder()
            .addPathSegments("photo/:/transcode")
            .addQueryParameter("width", size)
            .addQueryParameter("height", size)
            .addQueryParameter("minSize", "1")
            .addQueryParameter("upscale", "1")
            .addQueryParameter("url", thumb)
            .addQueryParameter(PlexHeaders.TOKEN, token)
            .build()
            .toString()
    }
}
