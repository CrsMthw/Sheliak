package com.crsmthw.sheliak.service

/**
 * When a failed direct play turns into a transcode (docs/PLAYER.md, "Stage 3 fixes"). Plex's direct play rests
 * on the server's ad-hoc decision, which answers **503** when it cannot decide or the decision is a transcode and
 * **509** when there is not enough bandwidth (PLEX.md §5): the item's next resolve then asks for
 * `PlaybackPrefs(forceTranscode = true)`. Pure; tested in TranscodeFallbackTest.
 */
object TranscodeFallback {

    /** The HTTP answers that mean "this server will not direct-play it now". */
    val STATUS_CODES: Set<Int> = setOf(503, 509)

    /**
     * True when the next resolve of the item must be a transcode: opening it at [positionBytes] answered
     * [responseCode] (null = no HTTP answer), and the source that failed was not already a transcode.
     *
     * - [failedWasTranscode] null (the failed source is unknown) counts as "not a transcode": forcing an item
     *   that already transcodes changes nothing.
     * - Only an open at the start of the stream (`positionBytes == 0`, where ExoPlayer first opens every item)
     *   falls back. A re-open at a byte offset belongs to a stream the extractor already reads; a transcode's
     *   bytes do not continue it, so that open keeps retrying the same source instead.
     */
    fun shouldForceTranscode(responseCode: Int?, failedWasTranscode: Boolean?, positionBytes: Long): Boolean =
        responseCode in STATUS_CODES && failedWasTranscode != true && positionBytes == 0L
}
