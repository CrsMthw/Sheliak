package com.crsmthw.sheliak.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscodeFallbackTest {

    @Test
    fun `a direct play refused with 503 or 509 at the start falls back to a transcode`() {
        assertTrue(TranscodeFallback.shouldForceTranscode(503, failedWasTranscode = false, positionBytes = 0))
        assertTrue(TranscodeFallback.shouldForceTranscode(509, failedWasTranscode = false, positionBytes = 0))
    }

    @Test
    fun `an unknown failed source counts as a direct play`() {
        assertTrue(TranscodeFallback.shouldForceTranscode(503, failedWasTranscode = null, positionBytes = 0))
    }

    @Test
    fun `a transcode that fails is not forced again`() {
        assertFalse(TranscodeFallback.shouldForceTranscode(503, failedWasTranscode = true, positionBytes = 0))
        assertFalse(TranscodeFallback.shouldForceTranscode(509, failedWasTranscode = true, positionBytes = 0))
    }

    @Test
    fun `other failures keep retrying the same source`() {
        listOf(400, 401, 403, 404, 410, 416, 500, 502, 504).forEach { code ->
            val force = TranscodeFallback.shouldForceTranscode(code, failedWasTranscode = false, positionBytes = 0)
            assertFalse(force, "HTTP $code")
        }
        assertFalse(TranscodeFallback.shouldForceTranscode(null, failedWasTranscode = false, positionBytes = 0))
    }

    @Test
    fun `a re-open inside the stream does not switch to a transcode`() {
        assertFalse(TranscodeFallback.shouldForceTranscode(503, failedWasTranscode = false, positionBytes = 1))
        assertFalse(TranscodeFallback.shouldForceTranscode(509, failedWasTranscode = null, positionBytes = 4_000_000))
    }
}
