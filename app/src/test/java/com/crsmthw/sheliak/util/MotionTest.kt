package com.crsmthw.sheliak.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The M3 timings inside the one transition envelope. Constants only: building a transition needs the Compose
 * runtime, and the arithmetic is what keeps every part ending on the same frame.
 */
class MotionTest {

    @Test
    fun `the envelope is 300ms`() {
        assertEquals(300, NavTransitionMillis)
    }

    @Test
    fun `the fades are Material's 90ms out and 210ms in`() {
        assertEquals(90, FadeOutMillis)
        assertEquals(210, FadeInMillis)
    }

    @Test
    fun `the fade in starts as the fade out ends and ends with the envelope`() {
        assertEquals(FadeOutMillis, FadeInDelayMillis)
        assertEquals(NavTransitionMillis, FadeInDelayMillis + FadeInMillis)
    }

    @Test
    fun `fade through grows the incoming content from 92 percent`() {
        assertEquals(0.92f, FadeThroughInitialScale)
    }
}
