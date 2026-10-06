package com.crsmthw.sheliak.service

import android.media.MediaCodecList
import java.util.Locale

/**
 * `PlaybackPrefs.directPlayCodecs`: the codec names (as the index spells them — Plex's `audioCodec`) this device
 * plays without a transcode. Built once from the platform's decoders ([detect]); the mapping is pure and tested
 * in DirectPlayCodecsTest.
 *
 * The decision is per codec only (that is all `PlaybackPrefs` carries); a container Media3 cannot open is a
 * provider-side decision — see docs/PLAYER.md, "Direct play vs transcode" (AIFF is the known case).
 */
object DirectPlayCodecs {

    /**
     * Played without any MediaCodec: Media3 hands PCM (WAV) straight to the AudioTrack, so these are never in the
     * decoder list.
     */
    val ALWAYS: Set<String> = setOf(
        "pcm", "wav", "wave", "lpcm", "pcm_u8", "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le",
    )

    /** A decoder's MIME type → the codec names it covers. */
    val BY_MIME: Map<String, Set<String>> = mapOf(
        "audio/flac"       to setOf("flac"),
        "audio/mpeg"       to setOf("mp3"),
        "audio/mpeg-l1"    to setOf("mp1"),
        "audio/mpeg-l2"    to setOf("mp2"),
        "audio/mp4a-latm"  to setOf("aac"),
        "audio/opus"       to setOf("opus"),
        "audio/vorbis"     to setOf("vorbis"),
        "audio/alac"       to setOf("alac"),
        "audio/ac3"        to setOf("ac3"),
        "audio/eac3"       to setOf("eac3"),
        "audio/eac3-joc"   to setOf("eac3"),
        "audio/ac4"        to setOf("ac4"),
        "audio/true-hd"    to setOf("truehd"),
        "audio/vnd.dts"    to setOf("dca", "dts"),
        "audio/vnd.dts.hd" to setOf("dca", "dts"),
        "audio/3gpp"       to setOf("amr_nb", "amrnb"),
        "audio/amr-wb"     to setOf("amr_wb", "amrwb"),
        "audio/g711-alaw"  to setOf("pcm_alaw"),
        "audio/g711-mlaw"  to setOf("pcm_mulaw"),
    )

    /** [ALWAYS] plus every codec the decoders' [mimeTypes] cover. */
    fun fromMimeTypes(mimeTypes: Iterable<String>): Set<String> {
        val codecs = HashSet(ALWAYS)
        mimeTypes.forEach { mime -> BY_MIME[mime.trim().lowercase(Locale.ROOT)]?.let(codecs::addAll) }
        return codecs
    }

    /** This device's set, from its decoders (MediaCodecList, regular codecs, decoders only). A few ms; call once. */
    fun detect(): Set<String> {
        val mimeTypes = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder }
            .flatMap { it.supportedTypes.asList() }
        return fromMimeTypes(mimeTypes)
    }
}
