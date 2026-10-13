package com.crsmthw.sheliak.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a cover is loaded again after the art resolver changes (M1 fix round 1, D4): only a load that FAILED for
 * the unchanged model is retried, once per resolver change since the attempt started (also when the failure lands
 * after the change); a cover that loaded, or whose model changed, is left alone.
 */
class ArtworkRetryTest {

    private val url = "https://music.example.com/photo/:/transcode?width=256&url=%2Ft&X-Plex-Token=t"
    private val other = "https://192-168-1-20.0123abcd.plex.direct:32400/photo/:/transcode?width=256&url=%2Ft&X-Plex-Token=t"

    @Test
    fun `a failed load of the same model is retried on a new resolver`() {
        assertEquals(1, ArtRetry.next(attempt = 0, failedModel = url, model = url, resolverChanged = true))
        assertEquals(3, ArtRetry.next(attempt = 2, failedModel = url, model = url, resolverChanged = true))
    }

    @Test
    fun `without a new resolver a failure stays (no retry loop)`() {
        assertEquals(0, ArtRetry.next(attempt = 0, failedModel = url, model = url, resolverChanged = false))
    }

    @Test
    fun `a cover that loaded or is loading is never reloaded by a re-resolve`() {
        assertEquals(0, ArtRetry.next(attempt = 0, failedModel = null, model = url, resolverChanged = true))
    }

    @Test
    fun `a changed model is a new request already, so it is not retried as well`() {
        assertEquals(0, ArtRetry.next(attempt = 0, failedModel = url, model = other, resolverChanged = true))
    }

    @Test
    fun `no model, nothing to retry`() {
        assertEquals(0, ArtRetry.next(attempt = 0, failedModel = null, model = null, resolverChanged = true))
    }

    // ── ArtAttempts: one cover's attempts over time ─────────────────────────────────────────────────

    private val first = ArtResolver { _, _ -> null }
    private val second = ArtResolver { _, _ -> null }
    private val third = ArtResolver { _, _ -> null }

    @Test
    fun `a bump while the load is in flight, then the failure, retries exactly once`() {
        val attempts = ArtAttempts()
        assertFalse(attempts.composed(first, url))   // token landed: the load starts
        assertFalse(attempts.composed(second, url))  // the pick lands on the same URL while it loads
        assertTrue(attempts.failed(url))             // the server times the transcode out: retry
        assertEquals(1, attempts.attempt)
        assertFalse(attempts.composed(second, url))  // the retry's own composition
        assertFalse(attempts.failed(url))            // the retry fails too, no change since: it stays failed
        assertEquals(1, attempts.attempt)
        assertTrue(attempts.composed(third, url))    // the next change retries again
        assertEquals(2, attempts.attempt)
    }

    @Test
    fun `a failure, then a bump, retries`() {
        val attempts = ArtAttempts()
        attempts.composed(first, url)
        assertFalse(attempts.failed(url))
        assertTrue(attempts.composed(second, url))
        assertEquals(1, attempts.attempt)
    }

    @Test
    fun `without a resolver change a failure never retries`() {
        val attempts = ArtAttempts()
        attempts.composed(first, url)
        assertFalse(attempts.failed(url))
        assertFalse(attempts.composed(first, url))
        assertEquals(0, attempts.attempt)
    }

    @Test
    fun `a cover that loaded is not reloaded by bumps`() {
        val attempts = ArtAttempts()
        attempts.composed(first, url)
        attempts.loaded()
        assertFalse(attempts.composed(second, url))
        assertFalse(attempts.composed(third, url))
        assertEquals(0, attempts.attempt)
    }

    @Test
    fun `a changed model starts a new attempt instead of retrying the old one`() {
        val attempts = ArtAttempts()
        attempts.composed(first, url)
        attempts.failed(url)
        assertFalse(attempts.composed(second, other))
        assertEquals(0, attempts.attempt)
        // …and that new attempt is anchored to the resolver it started under.
        assertFalse(attempts.failed(other))
        assertTrue(attempts.composed(third, other))
    }
}
