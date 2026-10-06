package com.crsmthw.sheliak.ui.player

import com.crsmthw.sheliak.domain.AudioFormatInfo
import com.crsmthw.sheliak.domain.RepeatMode
import java.util.Locale

/*
 * Pure helpers behind the player's numbers and labels: progress, remaining time, the repeat cycle and the quality
 * chip's exact values. No android.*, no Compose runtime — unit-tested in
 * PlayerFormatTest and QualityDetailTest. Wording stays in string resources; these return numbers and parts.
 */

/** [positionMs] as a 0..1 share of [durationMs]; 0 while the duration is unknown. */
fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

/** What is left of the track, never negative (a position can briefly run past a stale duration). */
fun remainingMs(positionMs: Long, durationMs: Long): Long = (durationMs - positionMs).coerceAtLeast(0L)

/** The repeat button's cycle: off → all → one → off. */
fun RepeatMode.nextInCycle(): RepeatMode = when (this) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}

/**
 * The exact values the quality chip shows next to the tier, as parts: the composable maps each variant to its
 * string resource (units are words in the user's language). [codec] is the codec's own name, upper-cased
 * ([codecLabel]) — technical data, not translated text.
 */
sealed interface QualityDetail {
    val codec: String

    /** Lossless with both numbers: "FLAC 24-bit / 96 kHz". */
    data class DepthAndRate(override val codec: String, val bitDepth: Int, val khz: String) : QualityDetail

    /** Lossless with only the sample rate. */
    data class Rate(override val codec: String, val khz: String) : QualityDetail

    /** Lossless with only the bit depth. */
    data class Depth(override val codec: String, val bitDepth: Int) : QualityDetail

    /** Lossy: "AAC 256 kbps". */
    data class Bitrate(override val codec: String, val kbps: Int) : QualityDetail

    /** Nothing but the codec is known. */
    data class CodecOnly(override val codec: String) : QualityDetail
}

/**
 * The chip's exact values for [format] (DESIGN §3): bit depth and sample rate for lossless audio, the bit rate
 * for lossy audio; whatever is unknown is left out.
 */
fun qualityDetailOf(format: AudioFormatInfo): QualityDetail {
    val codec = codecLabel(format.codec)
    if (!format.lossless) {
        val kbps = format.bitrateKbps?.takeIf { it > 0 } ?: return QualityDetail.CodecOnly(codec)
        return QualityDetail.Bitrate(codec, kbps)
    }
    val depth = format.bitDepth?.takeIf { it > 0 }
    val khz = format.sampleRateHz?.takeIf { it > 0 }?.let(::formatKhz)
    return when {
        depth != null && khz != null -> QualityDetail.DepthAndRate(codec, depth, khz)
        khz != null                  -> QualityDetail.Rate(codec, khz)
        depth != null                -> QualityDetail.Depth(codec, depth)
        else                         -> QualityDetail.CodecOnly(codec)
    }
}

/**
 * A codec name as the chip shows it: the provider's or decoder's own name, upper-cased — `flac` → `FLAC`,
 * `audio/x-flac` → `FLAC`; a PCM variant keeps only its family (`pcm_s24le` → `PCM`). Derived from the data
 * alone: the chip carries no codec names of its own.
 */
fun codecLabel(codec: String): String {
    val c = codec.trim().lowercase(Locale.ROOT).removePrefix("audio/").removePrefix("x-")
    val family = if (c.startsWith("pcm_")) c.substringBefore('_') else c
    return family.uppercase(Locale.ROOT)
}

/**
 * A sample rate in kHz with at most two decimals and no trailing zeros: 44100 → "44.1", 48000 → "48",
 * 88200 → "88.2", 22050 → "22.05". Built by hand: digits and the point are not language text, and a
 * formatter's implicit default locale would render the same rate differently per device.
 */
fun formatKhz(sampleRateHz: Int): String {
    val hundredths = (sampleRateHz.coerceAtLeast(0) + 5) / 10   // Hz → hundredths of a kHz, rounded
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
