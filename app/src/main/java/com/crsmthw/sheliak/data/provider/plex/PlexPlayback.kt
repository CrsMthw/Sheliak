package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.PlaybackPrefs
import com.crsmthw.sheliak.domain.AudioFormatInfo
import java.util.Locale

/*
 * Direct play vs Plex's universal transcoder (PLEX.md §5, corrections 16 and 17). Pure; unit-tested in
 * PlexPlaybackTest. PlexServer.resolvePlayback puts the pieces together with the picked connection and token.
 */

/** What the transcoder is asked to produce. */
enum class PlexTranscodeTarget(val container: String, val audioCodec: String, val lossless: Boolean) {
    /** Lossless sources: PLEX.md's official FLAC target. */
    FLAC(container = "flac", audioCodec = "flac", lossless = true),
    /** Lossy sources: AAC at the "transcode bit rate" setting, in MPEG-TS (the container S9 pairs with AAC). */
    AAC(container = "mpegts", audioCodec = "aac", lossless = false),
}

/**
 * How the transcode is delivered. [HTTP] (one progressive stream) is the default: it needs no HLS module in the
 * player (the catalog has none) and is the protocol of PLEX.md's official FLAC example. [HLS] is PLEX.md's other
 * verified path (python-plexapi / S9) for AAC; switching to it needs `media3-exoplayer-hls` in the player.
 */
enum class PlexTranscodeProtocol(val param: String) { HTTP("http"), HLS("hls") }

/** The direct-play / transcode choice for one track. */
sealed interface PlexPlaybackDecision {
    data object Direct : PlexPlaybackDecision
    data class Transcode(val target: PlexTranscodeTarget, val bitrateKbps: Int?) : PlexPlaybackDecision
}

object PlexPlayback {

    /** The relay's cap is 2 Mbps for streams (PLEX.md §2, S7); keep direct play over relay well under it. */
    const val RELAY_DIRECT_MAX_KBPS: Int = 1_800

    /** A lossy transcode over relay never asks for more than this. */
    const val RELAY_TRANSCODE_MAX_KBPS: Int = 320

    /** Plex's library identifier for scrobble / rate (PLEX.md §7, §10). */
    const val LIBRARY_IDENTIFIER: String = "com.plexapp.plugins.library"

    /**
     * Containers the player cannot open even when it decodes their codec: Media3 has no AIFF extractor (an AIFF
     * file's codec is `pcm`, which plays from WAV with no decoder at all). Plex's spelling as PlexMapping stores
     * it — trimmed, lower case — plus the short form.
     */
    val NO_EXTRACTOR_CONTAINERS: Set<String> = setOf("aiff", "aif")

    /**
     * Direct play when the device decodes the codec ([PlaybackPrefs.directPlayCodecs]), the player can open the
     * container (not one of [NO_EXTRACTOR_CONTAINERS]) and nothing forces a transcode; else the transcoder: FLAC
     * for a lossless source, AAC at [PlaybackPrefs.transcodeBitrateKbps] for a lossy (or unknown) one.
     *
     * Over [relay] (2 Mbps): a source above [RELAY_DIRECT_MAX_KBPS] — or a lossless one of unknown bit rate — is
     * transcoded to AAC at most [RELAY_TRANSCODE_MAX_KBPS] even when the device could decode it, because neither
     * the original nor a FLAC transcode of it fits the relay.
     */
    fun decide(format: AudioFormatInfo?, prefs: PlaybackPrefs, relay: Boolean): PlexPlaybackDecision {
        val codec = format?.codec?.lowercase(Locale.ROOT)
        val container = format?.container?.trim()?.lowercase(Locale.ROOT)
        val lossless = format?.lossless == true
        val sourceKbps = format?.bitrateKbps ?: if (lossless) Int.MAX_VALUE else 0
        val overRelay = relay && sourceKbps > RELAY_DIRECT_MAX_KBPS
        val decodable = codec != null && prefs.directPlayCodecs.any { it.lowercase(Locale.ROOT) == codec }
        val openable = container !in NO_EXTRACTOR_CONTAINERS
        if (decodable && openable && !prefs.forceTranscode && !overRelay) return PlexPlaybackDecision.Direct
        val lossyKbps = if (relay) minOf(prefs.transcodeBitrateKbps, RELAY_TRANSCODE_MAX_KBPS) else prefs.transcodeBitrateKbps
        return if (lossless && !overRelay) {
            PlexPlaybackDecision.Transcode(PlexTranscodeTarget.FLAC, bitrateKbps = null)
        } else {
            PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, bitrateKbps = lossyKbps.coerceAtLeast(1))
        }
    }

    /** `{connection.uri}{Part.key}` (PLEX.md §5 "Direct"); a base path of the connection is kept. */
    fun directUrl(baseUri: String, partKey: String): String =
        baseUri.trimEnd('/') + "/" + partKey.trimStart('/')

    /** The `X-Plex-Client-Profile-Extra` augmentation: one `add-transcode-target(…)` (PLEX.md §5). */
    fun profileExtra(target: PlexTranscodeTarget, protocol: PlexTranscodeProtocol): String =
        "add-transcode-target(type=musicProfile&context=streaming&protocol=${protocol.param}" +
            "&container=${target.container}&audioCodec=${target.audioCodec})"

    /**
     * The universal transcoder's start URL (PLEX.md §5 "Start" / "Params" / "Headers"):
     * `/music/:/transcode/universal/start.<ext>` with `path=/library/metadata/{ratingKey}`, media/part 0 (the
     * ones the index describes), [protocol], direct play/stream off, `musicBitrate` for a lossy target, `offset`
     * in SECONDS, `location`, a session id, the client identity as `X-Plex-*` query parameters (every X-Plex-*
     * may be one), the `Generic` profile and the target augmentation. The TOKEN is not in the URL; it goes in
     * [com.crsmthw.sheliak.data.provider.PlaybackSource.headers] so it stays out of cache keys and logs.
     *
     * HLS is HLS-only for AAC: a FLAC target is always delivered over HTTP.
     */
    fun transcodeUrl(
        baseUri: String,
        ratingKey: String,
        target: PlexTranscodeTarget,
        bitrateKbps: Int?,
        offsetSeconds: Long,
        location: String,
        sessionId: String,
        client: PlexClientInfo,
        protocol: PlexTranscodeProtocol = PlexTranscodeProtocol.HTTP,
    ): String {
        val delivery = if (target == PlexTranscodeTarget.FLAC) PlexTranscodeProtocol.HTTP else protocol
        val url = PlexRetrofit.baseUrl(baseUri).newBuilder()
            .addPathSegments("music/:/transcode/universal/start.${startExtension(target, delivery)}")
            .addQueryParameter("path", "/library/metadata/$ratingKey")
            .addQueryParameter("mediaIndex", "0")
            .addQueryParameter("partIndex", "0")
            .addQueryParameter("protocol", delivery.param)
            .addQueryParameter("directPlay", "0")
            .addQueryParameter("directStream", "0")
            .addQueryParameter("directStreamAudio", "0")
        if (!target.lossless && bitrateKbps != null) url.addQueryParameter("musicBitrate", bitrateKbps.toString())
        url.addQueryParameter("offset", offsetSeconds.coerceAtLeast(0).toString())
            .addQueryParameter("location", location)
            .addQueryParameter("transcodeSessionId", sessionId)
            .addQueryParameter(PlexHeaders.SESSION_IDENTIFIER, sessionId)
        client.queryParameters().forEach { (name, value) -> url.addQueryParameter(name, value) }
        url.addQueryParameter(PlexHeaders.CLIENT_PROFILE_NAME, "Generic")
            .addQueryParameter(PlexHeaders.CLIENT_PROFILE_EXTRA, profileExtra(target, delivery))
        return url.build().toString()
    }

    /** `start.m3u8` for HLS; for HTTP the container's own extension (`start.flac`, `start.ts`) — PLEX.md: U. */
    fun startExtension(target: PlexTranscodeTarget, protocol: PlexTranscodeProtocol): String = when {
        protocol == PlexTranscodeProtocol.HLS -> "m3u8"
        target.container == "mpegts"          -> "ts"
        else                                  -> target.container
    }

    /** The MIME type ExoPlayer is told for a transcode. */
    fun transcodeMimeType(target: PlexTranscodeTarget, protocol: PlexTranscodeProtocol): String = when {
        target == PlexTranscodeTarget.FLAC    -> "audio/flac"
        protocol == PlexTranscodeProtocol.HLS -> "application/x-mpegURL"
        else                                  -> "video/mp2t"
    }

    /** What a transcode will decode as: FLAC keeps the source's rate/depth/channels; AAC is the requested bit rate. */
    fun transcodeFormat(target: PlexTranscodeTarget, bitrateKbps: Int?, source: AudioFormatInfo?): AudioFormatInfo =
        when (target) {
            PlexTranscodeTarget.FLAC -> AudioFormatInfo(
                codec        = "flac",
                container    = "flac",
                bitrateKbps  = null,
                sampleRateHz = source?.sampleRateHz,
                bitDepth     = source?.bitDepth,
                channels     = source?.channels,
                lossless     = true,
            )
            PlexTranscodeTarget.AAC -> AudioFormatInfo(
                codec        = "aac",
                container    = "mpegts",
                bitrateKbps  = bitrateKbps,
                sampleRateHz = null,
                bitDepth     = null,
                channels     = source?.channels,
                lossless     = false,
            )
        }

    /** The transcoder's `location`: lan on a local connection, cellular on mobile data, else wan. */
    fun location(local: Boolean, cellular: Boolean): String = when {
        local    -> "lan"
        cellular -> "cellular"
        else     -> "wan"
    }

    /** A MIME hint for direct play from the file's container; null lets ExoPlayer sniff. */
    fun mimeTypeForContainer(container: String?): String? = when (container?.lowercase(Locale.ROOT)) {
        "flac"               -> "audio/flac"
        "mp3"                -> "audio/mpeg"
        "mp4", "m4a", "m4b"  -> "audio/mp4"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav"                -> "audio/wav"
        "aac"                -> "audio/aac"
        "mka"                -> "audio/x-matroska"
        else                 -> null
    }

    /** The scrobble / timeline `key` of a track. */
    fun metadataKey(ratingKey: String): String = "/library/metadata/$ratingKey"
}
