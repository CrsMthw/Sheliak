package com.crsmthw.sheliak.ui.player

import com.crsmthw.sheliak.domain.AudioFormatInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class QualityDetailTest {

    private fun format(
        codec       : String,
        lossless    : Boolean,
        bitrateKbps : Int? = null,
        sampleRateHz: Int? = null,
        bitDepth    : Int? = null,
    ) = AudioFormatInfo(
        codec        = codec,
        container    = null,
        bitrateKbps  = bitrateKbps,
        sampleRateHz = sampleRateHz,
        bitDepth     = bitDepth,
        channels     = 2,
        lossless     = lossless,
    )

    // ── qualityDetailOf ─────────────────────────────────────────────────────

    @Test
    fun `lossless shows bit depth and sample rate`() {
        assertEquals(
            QualityDetail.DepthAndRate("FLAC", 24, "96"),
            qualityDetailOf(format("flac", lossless = true, bitrateKbps = 4_000, sampleRateHz = 96_000, bitDepth = 24)),
        )
        assertEquals(
            QualityDetail.DepthAndRate("ALAC", 16, "44.1"),
            qualityDetailOf(format("alac", lossless = true, sampleRateHz = 44_100, bitDepth = 16)),
        )
    }

    @Test
    fun `lossless with one number shows that number`() {
        assertEquals(QualityDetail.Rate("FLAC", "48"), qualityDetailOf(format("flac", lossless = true, sampleRateHz = 48_000)))
        assertEquals(QualityDetail.Depth("FLAC", 24), qualityDetailOf(format("flac", lossless = true, bitDepth = 24)))
    }

    @Test
    fun `lossless never shows the bit rate`() {
        assertEquals(QualityDetail.CodecOnly("FLAC"), qualityDetailOf(format("flac", lossless = true, bitrateKbps = 900)))
    }

    @Test
    fun `lossy shows the bit rate`() {
        assertEquals(
            QualityDetail.Bitrate("AAC", 256),
            qualityDetailOf(format("aac", lossless = false, bitrateKbps = 256, sampleRateHz = 44_100)),
        )
        assertEquals(QualityDetail.Bitrate("MP3", 320), qualityDetailOf(format("mp3", lossless = false, bitrateKbps = 320)))
    }

    @Test
    fun `lossy without a bit rate shows only the codec`() {
        assertEquals(QualityDetail.CodecOnly("OPUS"), qualityDetailOf(format("opus", lossless = false)))
        assertEquals(QualityDetail.CodecOnly("OPUS"), qualityDetailOf(format("opus", lossless = false, bitrateKbps = 0)))
    }

    @Test
    fun `zero or negative numbers count as unknown`() {
        assertEquals(
            QualityDetail.CodecOnly("FLAC"),
            qualityDetailOf(format("flac", lossless = true, sampleRateHz = 0, bitDepth = 0)),
        )
    }

    // ── codecLabel ──────────────────────────────────────────────────────────

    @Test
    fun `codec names are upper-cased`() {
        assertEquals("FLAC", codecLabel("flac"))
        assertEquals("AAC", codecLabel(" aac "))
        assertEquals("VORBIS", codecLabel("vorbis"))
    }

    @Test
    fun `mime spellings lose their prefix`() {
        assertEquals("FLAC", codecLabel("audio/x-flac"))
        assertEquals("OPUS", codecLabel("audio/opus"))
    }

    @Test
    fun `every pcm variant reads PCM`() {
        assertEquals("PCM", codecLabel("pcm"))
        assertEquals("PCM", codecLabel("pcm_s24le"))
        assertEquals("PCM", codecLabel("PCM_S16BE"))
    }

    // ── formatKhz ───────────────────────────────────────────────────────────

    @Test
    fun `sample rates read in kHz without trailing zeros`() {
        assertEquals("44.1", formatKhz(44_100))
        assertEquals("48", formatKhz(48_000))
        assertEquals("88.2", formatKhz(88_200))
        assertEquals("96", formatKhz(96_000))
        assertEquals("176.4", formatKhz(176_400))
        assertEquals("192", formatKhz(192_000))
        assertEquals("352.8", formatKhz(352_800))
    }

    @Test
    fun `two decimals at most, rounded`() {
        assertEquals("22.05", formatKhz(22_050))
        assertEquals("11.03", formatKhz(11_025))
        assertEquals("8", formatKhz(8_000))
    }

    @Test
    fun `zero and negative rates read zero`() {
        assertEquals("0", formatKhz(0))
        assertEquals("0", formatKhz(-44_100))
    }
}
