package com.crsmthw.sheliak.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityClassifierTest {

    private fun lossless(bitDepth: Int?, sampleRateHz: Int?) = AudioFormatInfo(
        codec = "flac", container = "flac", bitrateKbps = 900, sampleRateHz = sampleRateHz, bitDepth = bitDepth,
        channels = 2, lossless = true,
    )

    private fun lossy(bitrateKbps: Int?) = AudioFormatInfo(
        codec = "mp3", container = "mp3", bitrateKbps = bitrateKbps, sampleRateHz = 44_100, bitDepth = null,
        channels = 2, lossless = false,
    )

    @Test
    fun `CD quality lossless is Lossless`() {
        assertEquals(QualityTier.LOSSLESS, QualityClassifier.classify(lossless(16, 44_100)))
    }

    @Test
    fun `16 bit at 48 kHz is still Lossless - the boundary is inclusive`() {
        assertEquals(QualityTier.LOSSLESS, QualityClassifier.classify(lossless(16, 48_000)))
    }

    @Test
    fun `24 bit at 44_1 kHz is Hi-Res`() {
        assertEquals(QualityTier.HI_RES_LOSSLESS, QualityClassifier.classify(lossless(24, 44_100)))
    }

    @Test
    fun `16 bit at 96 kHz is Hi-Res`() {
        assertEquals(QualityTier.HI_RES_LOSSLESS, QualityClassifier.classify(lossless(16, 96_000)))
    }

    @Test
    fun `just above either CD limit is Hi-Res`() {
        assertEquals(QualityTier.HI_RES_LOSSLESS, QualityClassifier.classify(lossless(17, 44_100)))
        assertEquals(QualityTier.HI_RES_LOSSLESS, QualityClassifier.classify(lossless(16, 48_001)))
    }

    @Test
    fun `unknown bit depth or sample rate never promotes lossless to Hi-Res`() {
        assertEquals(QualityTier.LOSSLESS, QualityClassifier.classify(lossless(null, null)))
        assertEquals(QualityTier.LOSSLESS, QualityClassifier.classify(lossless(null, 44_100)))
        assertEquals(QualityTier.LOSSLESS, QualityClassifier.classify(lossless(16, null)))
        assertEquals(QualityTier.HI_RES_LOSSLESS, QualityClassifier.classify(lossless(null, 192_000)))
        assertEquals(QualityTier.HI_RES_LOSSLESS, QualityClassifier.classify(lossless(24, null)))
    }

    @Test
    fun `lossy at 256 kbps is High Quality`() {
        assertEquals(QualityTier.HIGH_QUALITY, QualityClassifier.classify(lossy(256)))
        assertEquals(QualityTier.HIGH_QUALITY, QualityClassifier.classify(lossy(320)))
    }

    @Test
    fun `lossy at 255 kbps is High Efficiency`() {
        assertEquals(QualityTier.HIGH_EFFICIENCY, QualityClassifier.classify(lossy(255)))
        assertEquals(QualityTier.HIGH_EFFICIENCY, QualityClassifier.classify(lossy(128)))
    }

    @Test
    fun `lossy with an unknown bit rate is High Efficiency`() {
        assertEquals(QualityTier.HIGH_EFFICIENCY, QualityClassifier.classify(lossy(null)))
    }

    @Test
    fun `a lossy stream's sample rate or bit depth never makes it Hi-Res`() {
        val f = lossy(320).copy(sampleRateHz = 96_000, bitDepth = 24)
        assertEquals(QualityTier.HIGH_QUALITY, QualityClassifier.classify(f))
    }

    @Test
    fun `lossless codec names are recognised in every spelling providers use`() {
        listOf("flac", "FLAC", "alac", "wav", "pcm_s16le", "pcm_s24be", "aiff", "ape", "wavpack", "audio/flac",
            "audio/x-flac", "audio/raw", "audio/alac", " Flac ").forEach {
            assertTrue(QualityClassifier.isLosslessCodec(it), it)
        }
    }

    @Test
    fun `lossy codec names are not lossless`() {
        listOf("mp3", "aac", "opus", "vorbis", "audio/mpeg", "audio/mp4a-latm", "wma", "").forEach {
            assertFalse(QualityClassifier.isLosslessCodec(it), it)
        }
    }
}
