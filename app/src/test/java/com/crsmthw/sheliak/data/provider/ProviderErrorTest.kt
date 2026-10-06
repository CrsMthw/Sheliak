package com.crsmthw.sheliak.data.provider

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderErrorTest {

    @Test
    fun `network failures are NETWORK`() {
        listOf(IOException(), SocketTimeoutException(), UnknownHostException()).forEach {
            assertEquals(ProviderError.NETWORK, ProviderError.of(it), it.toString())
        }
    }

    @Test
    fun `a provider exception says what it is, even when it wraps an IO error`() {
        assertEquals(ProviderError.AUTH, ProviderError.of(ProviderException(ProviderError.AUTH)))
        assertEquals(ProviderError.SERVER, ProviderError.of(ProviderException(ProviderError.SERVER, cause = IOException())))
    }

    @Test
    fun `the cause chain is followed`() {
        assertEquals(ProviderError.NETWORK, ProviderError.of(IllegalStateException(RuntimeException(IOException()))))
        assertEquals(ProviderError.AUTH, ProviderError.of(RuntimeException(ProviderException(ProviderError.AUTH))))
    }

    @Test
    fun `unreadable answers are PARSE and the rest UNKNOWN`() {
        assertEquals(ProviderError.PARSE, ProviderError.of(SerializationException("bad")))
        assertEquals(ProviderError.UNKNOWN, ProviderError.of(IllegalStateException()))
    }

    @Test
    fun `only transient errors are retryable`() {
        assertTrue(ProviderError.NETWORK.retryable)
        assertTrue(ProviderError.SERVER.retryable)
        assertFalse(ProviderError.AUTH.retryable)
        assertFalse(ProviderError.PARSE.retryable)
        assertFalse(ProviderError.UNAVAILABLE.retryable)
    }
}
