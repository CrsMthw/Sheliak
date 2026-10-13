package com.crsmthw.sheliak.data.provider.plex

/**
 * Cover URLs (PLEX.md §4): `/photo/:/transcode?width=W&height=H&minSize=1&upscale=1&url=<thumb path>` with the
 * server's token as the `X-Plex-Token` QUERY parameter (every X-Plex-* may be one), so the URL alone is a complete
 * Coil model and the app's one ImageLoader needs no per-server headers. Pure; unit-tested in PlexArtTest.
 *
 * The index stores only the server-relative thumb path ([com.crsmthw.sheliak.domain.ArtRef.path]); the host and
 * the token are added here, at display time. Sizes are rounded up to a few buckets so one cover is cached once
 * per bucket rather than once per pixel size.
 *
 * The URL is a pure function of (base, thumb, size, token) and carries NO version or cache-busting parameter:
 * an art-version bump that lands on the same connection gives the same string, so Coil's memory and disk caches
 * keep answering; a bump onto another connection (or a new token) gives a new string, which is a new request.
 * Retrying a load that FAILED under an unchanged string is `Artwork`'s job (it re-keys only failed loads).
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

    /**
     * The connection a cover URL is built on: the live [current] pick; before it lands (a cold start, a network
     * change), the [lastPicked] one, then the setup-time [setupBase], then the best of [connections] — a guess
     * that lets Coil's disk cache answer at once, replaced when the pick lands (the provider's art version moves).
     *
     * A guess never names a LAN connection that may not be opened now — a server the user does not own, or
     * Android 17's local-network permission missing ([localAllowed] false): a blocked LAN connect times out
     * instead of failing, which would hold every cover on the placeholder for the whole timeout. [localAllowed]
     * is asked only when there is no pick to use (it is a permission check).
     */
    fun base(
        current: String?,
        lastPicked: String?,
        setupBase: String?,
        connections: List<PlexConnectionInfo>,
        owned: Boolean,
        localAllowed: () -> Boolean,
    ): String? {
        current?.takeIf { it.isNotBlank() }?.let { return it }
        lastPicked?.takeIf { it.isNotBlank() }?.let { return it }
        val lanAllowed = owned && localAllowed()
        val setup = setupBase?.takeIf { it.isNotBlank() }?.takeIf { base ->
            lanAllowed || connections.none { it.kind == PlexConnectionInfo.Kind.LOCAL && sameUri(it.uri, base) }
        }
        return setup ?: PlexConnectionPicker.order(connections, owned, lanAllowed).firstOrNull()?.uri
    }

    private fun sameUri(a: String, b: String): Boolean = a.trimEnd('/') == b.trimEnd('/')
}
