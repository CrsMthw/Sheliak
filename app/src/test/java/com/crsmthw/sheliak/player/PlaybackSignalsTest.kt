package com.crsmthw.sheliak.player

import com.crsmthw.sheliak.domain.AudioFormatInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSignalsTest {

    private fun format(rate: Int) = AudioFormatInfo("flac", "flac", null, rate, 16, 2, lossless = true)

    @Test
    fun `the newest formats are kept, oldest dropped first`() {
        var map = emptyMap<String, AudioFormatInfo>()
        listOf("a", "b", "c").forEachIndexed { i, id -> map = PlaybackSignals.withFormat(map, id, format(i), max = 2) }
        assertEquals(listOf("b", "c"), map.keys.toList())
    }

    @Test
    fun `a re-reported item becomes the newest with its new format`() {
        var map = emptyMap<String, AudioFormatInfo>()
        map = PlaybackSignals.withFormat(map, "a", format(1), max = 2)
        map = PlaybackSignals.withFormat(map, "b", format(2), max = 2)
        map = PlaybackSignals.withFormat(map, "a", format(3), max = 2)
        map = PlaybackSignals.withFormat(map, "c", format(4), max = 2)
        assertEquals(listOf("a", "c"), map.keys.toList())
        assertEquals(format(3), map["a"])
    }

    @Test
    fun `published formats and the session id are observable`() {
        val signals = PlaybackSignals()
        signals.publishFormat("plex:s|1", format(44_100))
        signals.publishAudioSessionId(7)
        assertEquals(format(44_100), signals.formats.value["plex:s|1"])
        assertEquals(7, signals.audioSessionId.value)
        signals.clearFormats()
        assertEquals(emptyMap<String, AudioFormatInfo>(), signals.formats.value)
    }
}
