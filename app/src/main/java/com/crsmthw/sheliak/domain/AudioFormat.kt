package com.crsmthw.sheliak.domain

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * What a track's audio is: from the index (what the provider reported) or, while playing, from the decoder's
 * input format (which is the truth for a transcode). Every number is nullable because no source reports all of
 * them — a lossy stream has no bit depth, and a server may not know the bit rate of a file it never scanned.
 */
@Immutable
data class AudioFormatInfo(
    val codec: String,
    val container: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val lossless: Boolean,
)

/** The four tiers shown under the progress bar, best first. */
enum class QualityTier { HI_RES_LOSSLESS, LOSSLESS, HIGH_QUALITY, HIGH_EFFICIENCY }

/** The tier rules of `docs/DESIGN.md` §3. Pure; unit-tested in QualityClassifierTest. */
object QualityClassifier {

    /** Above either of these, lossless audio is hi-res. */
    const val CD_MAX_BIT_DEPTH: Int = 16
    const val CD_MAX_SAMPLE_RATE_HZ: Int = 48_000

    /** At or above this, lossy audio is High Quality. */
    const val HIGH_QUALITY_MIN_KBPS: Int = 256

    /**
     * - Hi-Res Lossless: lossless AND (bit depth > 16 OR sample rate > 48 kHz)
     * - Lossless: lossless otherwise — an unknown bit depth or sample rate never promotes a file to hi-res
     * - High Quality: lossy AND bit rate ≥ 256 kbps
     * - High Efficiency: lossy otherwise, including an unknown bit rate
     */
    fun classify(f: AudioFormatInfo): QualityTier = when {
        f.lossless && ((f.bitDepth ?: 0) > CD_MAX_BIT_DEPTH || (f.sampleRateHz ?: 0) > CD_MAX_SAMPLE_RATE_HZ) ->
            QualityTier.HI_RES_LOSSLESS
        f.lossless                                            -> QualityTier.LOSSLESS
        (f.bitrateKbps ?: 0) >= HIGH_QUALITY_MIN_KBPS        -> QualityTier.HIGH_QUALITY
        else                                                  -> QualityTier.HIGH_EFFICIENCY
    }

    /**
     * The codec names (as Plex, Jellyfin, MediaStore and Media3 spell them, lower-cased) whose audio is lossless,
     * so a provider that reports only a codec can fill [AudioFormatInfo.lossless]. PCM covers WAV and AIFF.
     */
    val LOSSLESS_CODECS: Set<String> = setOf(
        "flac", "alac", "wav", "wave", "pcm", "raw", "aiff", "aif", "ape", "wavpack", "wv", "tta", "dsd", "dsf",
        "dff", "mlp", "truehd",
    )

    /**
     * True for a lossless [codec] name, case-insensitive. Also accepts PCM variants (`pcm_s16le`, `pcm_s24be`, …)
     * and MIME types (`audio/flac`, `audio/x-flac`, `audio/raw` — what Media3 reports as a sample MIME type).
     */
    fun isLosslessCodec(codec: String): Boolean {
        val c = codec.trim().lowercase(Locale.ROOT).removePrefix("audio/").removePrefix("x-")
        return c in LOSSLESS_CODECS || c.startsWith("pcm_")
    }
}
