package com.crsmthw.sheliak.player

import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.QualityClassifier
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Media3's description of what the decoder is fed (`Format` from `onAudioInputFormatChanged`) → the
 * [AudioFormatInfo] the quality tier is classified from. Takes plain values, not a `Format`, so it stays JVM-pure;
 * tested in AudioFormatsTest.
 */
object AudioFormats {

    /** Media3's "no value" for an int field of `Format`. */
    const val NO_VALUE: Int = -1

    // Media3's `C.ENCODING_PCM_*` values (stable constants, repeated here so this file needs no Media3 class).
    private const val ENCODING_PCM_8BIT = 3
    private const val ENCODING_PCM_16BIT = 2
    private const val ENCODING_PCM_24BIT = 21
    private const val ENCODING_PCM_32BIT = 22
    private const val ENCODING_PCM_FLOAT = 4

    /**
     * The decoder's input as an [AudioFormatInfo], or null without a sample MIME type. [bitrate] is in bit/s
     * (`Format.bitrate`); every int may be [NO_VALUE].
     */
    fun fromDecoderInput(
        sampleMimeType: String?,
        containerMimeType: String?,
        bitrate: Int,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int,
    ): AudioFormatInfo? {
        val codec = codecOf(sampleMimeType) ?: return null
        return AudioFormatInfo(
            codec        = codec,
            container    = containerOf(containerMimeType),
            bitrateKbps  = bitrate.takeIf { it > 0 }?.let { (it / 1000.0).roundToInt() },
            sampleRateHz = sampleRate.takeIf { it > 0 },
            bitDepth     = bitDepthOf(pcmEncoding),
            channels     = channelCount.takeIf { it > 0 },
            lossless     = QualityClassifier.isLosslessCodec(codec),
        )
    }

    /**
     * The live format, with what the decoder input does not carry (a FLAC file's bit rate, sometimes its bit
     * depth) taken from the index — but only when both describe the same codec, i.e. this is a direct play.
     * For a transcode the index describes the source file, not the stream, so nothing is borrowed.
     */
    fun merge(live: AudioFormatInfo, indexed: AudioFormatInfo?): AudioFormatInfo {
        if (indexed == null || !sameCodec(live.codec, indexed.codec)) return live
        return live.copy(
            container    = live.container ?: indexed.container,
            bitrateKbps  = live.bitrateKbps ?: indexed.bitrateKbps,
            sampleRateHz = live.sampleRateHz ?: indexed.sampleRateHz,
            bitDepth     = live.bitDepth ?: indexed.bitDepth,
            channels     = live.channels ?: indexed.channels,
        )
    }

    /** A sample MIME type as the index spells codecs (Plex's `audioCodec` names). */
    fun codecOf(sampleMimeType: String?): String? {
        val mime = sampleMimeType?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return when (mime) {
            "audio/flac"                         -> "flac"
            "audio/mpeg"                         -> "mp3"
            "audio/mpeg-l1"                      -> "mp1"
            "audio/mpeg-l2"                      -> "mp2"
            "audio/mp4a-latm", "audio/aac"       -> "aac"
            "audio/opus"                         -> "opus"
            "audio/vorbis"                       -> "vorbis"
            "audio/alac"                         -> "alac"
            "audio/raw"                          -> "pcm"
            "audio/ac3"                          -> "ac3"
            "audio/eac3", "audio/eac3-joc"       -> "eac3"
            "audio/ac4"                          -> "ac4"
            "audio/true-hd"                      -> "truehd"
            "audio/vnd.dts", "audio/vnd.dts.hd"  -> "dca"
            "audio/3gpp"                         -> "amr_nb"
            "audio/amr-wb"                       -> "amr_wb"
            "audio/g711-alaw"                    -> "pcm_alaw"
            "audio/g711-mlaw"                    -> "pcm_mulaw"
            else                                 -> mime.substringAfter('/').removePrefix("x-")
        }
    }

    /** A container MIME type as a short name (`mp4`, `flac`, `ogg`, …); null when unknown. */
    fun containerOf(containerMimeType: String?): String? {
        val mime = containerMimeType?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return when (mime) {
            "audio/mp4", "video/mp4"                     -> "mp4"
            "audio/flac"                                 -> "flac"
            "audio/ogg", "audio/opus"                    -> "ogg"
            "audio/mpeg"                                 -> "mp3"
            "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "wav"
            "audio/x-matroska", "video/x-matroska"       -> "mka"
            "audio/webm", "video/webm"                   -> "webm"
            "audio/aac", "audio/mp4a-latm"               -> "aac"
            "video/mp2t"                                 -> "ts"
            else                                         -> mime.substringAfter('/').removePrefix("x-")
        }
    }

    /** The PCM sample size the decoder outputs, when the format says. */
    fun bitDepthOf(pcmEncoding: Int): Int? = when (pcmEncoding) {
        ENCODING_PCM_8BIT  -> 8
        ENCODING_PCM_16BIT -> 16
        ENCODING_PCM_24BIT -> 24
        ENCODING_PCM_32BIT, ENCODING_PCM_FLOAT -> 32
        else               -> null
    }

    private fun sameCodec(a: String, b: String): Boolean {
        val x = a.trim().lowercase(Locale.ROOT)
        val y = b.trim().lowercase(Locale.ROOT)
        return x == y || (isPcm(x) && isPcm(y))
    }

    private fun isPcm(codec: String): Boolean =
        codec == "pcm" || codec.startsWith("pcm_") || codec == "wav" || codec == "wave" || codec == "aiff"
}
