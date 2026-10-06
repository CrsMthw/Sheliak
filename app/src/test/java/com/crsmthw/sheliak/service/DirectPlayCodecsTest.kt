package com.crsmthw.sheliak.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectPlayCodecsTest {

    @Test
    fun `pcm plays with no decoder at all`() {
        val codecs = DirectPlayCodecs.fromMimeTypes(emptyList())
        assertEquals(DirectPlayCodecs.ALWAYS, codecs)
        assertTrue("pcm" in codecs)
        assertTrue("wav" in codecs)
    }

    @Test
    fun `decoder mime types map to the index's codec names`() {
        val codecs = DirectPlayCodecs.fromMimeTypes(
            listOf("audio/flac", "audio/mpeg", "audio/mp4a-latm", "audio/opus", "audio/vorbis", "audio/alac", "audio/raw"),
        )
        listOf("flac", "mp3", "aac", "opus", "vorbis", "alac", "pcm").forEach { assertTrue(it in codecs, it) }
    }

    @Test
    fun `a codec with no decoder is left out, so it is transcoded`() {
        val codecs = DirectPlayCodecs.fromMimeTypes(listOf("audio/flac", "audio/mpeg"))
        listOf("alac", "opus", "ac3", "eac3", "dca", "wmav2", "ape", "wavpack").forEach { assertFalse(it in codecs, it) }
    }

    @Test
    fun `mime types are matched case-insensitively and video types are ignored`() {
        val codecs = DirectPlayCodecs.fromMimeTypes(listOf(" AUDIO/FLAC ", "video/avc", "video/hevc"))
        assertEquals(DirectPlayCodecs.ALWAYS + "flac", codecs)
    }

    @Test
    fun `dts and dolby decoders cover every name the index may use`() {
        val codecs = DirectPlayCodecs.fromMimeTypes(listOf("audio/vnd.dts", "audio/eac3-joc", "audio/ac3"))
        listOf("dca", "dts", "eac3", "ac3").forEach { assertTrue(it in codecs, it) }
    }
}
