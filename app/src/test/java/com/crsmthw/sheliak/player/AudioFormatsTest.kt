package com.crsmthw.sheliak.player

import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.QualityClassifier
import com.crsmthw.sheliak.domain.QualityTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioFormatsTest {

    private val none = AudioFormats.NO_VALUE

    @Test
    fun `a direct-play FLAC reads as lossless with its bit depth`() {
        val f = AudioFormats.fromDecoderInput("audio/flac", "audio/flac", none, 96_000, 2, 21)!!
        assertEquals(AudioFormatInfo("flac", "flac", null, 96_000, 24, 2, lossless = true), f)
        assertEquals(QualityTier.HI_RES_LOSSLESS, QualityClassifier.classify(f))
    }

    @Test
    fun `an AAC transcode reads as lossy at its bit rate`() {
        val f = AudioFormats.fromDecoderInput("audio/mp4a-latm", "audio/mp4", 320_000, 44_100, 2, none)!!
        assertEquals(AudioFormatInfo("aac", "mp4", 320, 44_100, null, 2, lossless = false), f)
        assertEquals(QualityTier.HIGH_QUALITY, QualityClassifier.classify(f))
    }

    @Test
    fun `mp3, opus, vorbis, alac and pcm map to the index's codec names`() {
        mapOf(
            "audio/mpeg" to "mp3", "audio/opus" to "opus", "audio/vorbis" to "vorbis", "audio/alac" to "alac",
            "audio/raw" to "pcm", "audio/eac3-joc" to "eac3", "audio/vnd.dts" to "dca", "AUDIO/FLAC" to "flac",
        ).forEach { (mime, codec) -> assertEquals(codec, AudioFormats.codecOf(mime), mime) }
        assertEquals("unknown", AudioFormats.codecOf("audio/x-unknown"))
    }

    @Test
    fun `no sample mime type is no format`() {
        assertNull(AudioFormats.fromDecoderInput(null, "audio/mp4", 1, 1, 1, none))
        assertNull(AudioFormats.fromDecoderInput("  ", null, 1, 1, 1, none))
    }

    @Test
    fun `pcm encodings give the bit depth`() {
        assertEquals(16, AudioFormats.bitDepthOf(2))
        assertEquals(24, AudioFormats.bitDepthOf(21))
        assertEquals(32, AudioFormats.bitDepthOf(22))
        assertEquals(32, AudioFormats.bitDepthOf(4))
        assertEquals(8, AudioFormats.bitDepthOf(3))
        assertNull(AudioFormats.bitDepthOf(none))
    }

    @Test
    fun `a direct play borrows what the decoder input lacks from the index`() {
        val live = AudioFormatInfo("flac", null, null, 44_100, null, 2, lossless = true)
        val indexed = AudioFormatInfo("FLAC", "flac", 1_011, 44_100, 24, 2, lossless = true)
        assertEquals(AudioFormatInfo("flac", "flac", 1_011, 44_100, 24, 2, lossless = true), AudioFormats.merge(live, indexed))
    }

    @Test
    fun `a transcode borrows nothing from the source file`() {
        val live = AudioFormatInfo("aac", "mp4", 256, 44_100, null, 2, lossless = false)
        val indexed = AudioFormatInfo("flac", "flac", 2_800, 96_000, 24, 2, lossless = true)
        assertEquals(live, AudioFormats.merge(live, indexed))
        assertEquals(live, AudioFormats.merge(live, null))
    }

    @Test
    fun `pcm and wav count as the same codec`() {
        val live = AudioFormatInfo("pcm", "wav", null, 48_000, 16, 2, lossless = true)
        val indexed = AudioFormatInfo("pcm_s16le", "wav", 1_536, 48_000, 16, 2, lossless = true)
        assertEquals(1_536, AudioFormats.merge(live, indexed).bitrateKbps)
    }
}
