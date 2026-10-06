package com.crsmthw.sheliak.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpiringCacheTest {

    private var now = 0L
    private val cache = ExpiringCache<String, String>(maxEntries = 2, ttlMs = 1_000, clock = { now })

    @Test
    fun `an entry lives until its time to live`() {
        cache["a"] = "A"
        now = 999
        assertEquals("A", cache["a"])
        now = 1_000
        assertNull(cache["a"])
    }

    @Test
    fun `the least recently used entry goes first`() {
        cache["a"] = "A"
        cache["b"] = "B"
        cache["a"]                // a is now the most recent
        cache["c"] = "C"
        assertEquals("A", cache["a"])
        assertNull(cache["b"])
        assertEquals("C", cache["c"])
    }

    @Test
    fun `a removed entry is gone and a re-put one starts a new life`() {
        cache["a"] = "A"
        cache.remove("a")
        assertNull(cache["a"])
        cache["a"] = "A1"
        now = 900
        cache["a"] = "A2"
        now = 1_500
        assertEquals("A2", cache["a"])
    }
}
