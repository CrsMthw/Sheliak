package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.PlaybackPrefs
import com.crsmthw.sheliak.domain.AudioFormatInfo
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlexPlaybackTest {

    private val client = PlexClientInfo(
        clientIdentifier = "client-uuid",
        version          = "0.1.0",
        platformVersion  = "17",
        device           = "Cris’s Fold",
        model            = "SM-F971B",
        vendor           = "samsung",
        deviceName       = "Cris’s Fold",
    )

    private val prefs = PlaybackPrefs(directPlayCodecs = setOf("flac", "mp3", "aac", "opus"), transcodeBitrateKbps = 320)

    private val cdFlac = AudioFormatInfo("flac", "flac", 900, 44_100, 16, 2, lossless = true)
    private val hiResFlac = AudioFormatInfo("flac", "flac", 2900, 96_000, 24, 2, lossless = true)
    private val alac = AudioFormatInfo("alac", "mp4", 1000, 44_100, 16, 2, lossless = true)
    private val wma = AudioFormatInfo("wmav2", "asf", 192, 44_100, null, 2, lossless = false)
    private val mp3 = AudioFormatInfo("mp3", "mp3", 320, 44_100, null, 2, lossless = false)

    // ── Decision ──────────────────────────────────────────────────────────────

    @Test
    fun `a decodable codec plays direct`() {
        assertEquals(PlexPlaybackDecision.Direct, PlexPlayback.decide(cdFlac, prefs, relay = false))
        assertEquals(PlexPlaybackDecision.Direct, PlexPlayback.decide(hiResFlac, prefs, relay = false))
        assertEquals(PlexPlaybackDecision.Direct, PlexPlayback.decide(mp3, prefs.copy(directPlayCodecs = setOf("MP3")), false))
    }

    @Test
    fun `lossless the device cannot decode becomes FLAC, lossy becomes AAC at the setting`() {
        assertEquals(PlexPlaybackDecision.Transcode(PlexTranscodeTarget.FLAC, null), PlexPlayback.decide(alac, prefs, false))
        assertEquals(PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, 320), PlexPlayback.decide(wma, prefs, false))
        assertEquals(
            PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, 192),
            PlexPlayback.decide(wma, prefs.copy(transcodeBitrateKbps = 192), false),
        )
    }

    @Test
    fun `forceTranscode transcodes even a decodable codec`() {
        val forced = prefs.copy(forceTranscode = true)
        assertEquals(PlexPlaybackDecision.Transcode(PlexTranscodeTarget.FLAC, null), PlexPlayback.decide(cdFlac, forced, false))
        assertEquals(PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, 320), PlexPlayback.decide(mp3, forced, false))
    }

    @Test
    fun `an unknown format is transcoded to AAC`() {
        assertEquals(PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, 320), PlexPlayback.decide(null, prefs, false))
    }

    @Test
    fun `over relay a source above the cap is transcoded to AAC within it`() {
        assertEquals(PlexPlaybackDecision.Direct, PlexPlayback.decide(cdFlac, prefs, relay = true))
        assertEquals(PlexPlaybackDecision.Direct, PlexPlayback.decide(mp3, prefs, relay = true))
        assertEquals(PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, 320), PlexPlayback.decide(hiResFlac, prefs, true))
        assertEquals(
            PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, PlexPlayback.RELAY_TRANSCODE_MAX_KBPS),
            PlexPlayback.decide(wma, prefs.copy(transcodeBitrateKbps = 640), relay = true),
        )
        // Lossless of unknown bit rate does not risk the relay either.
        assertEquals(
            PlexPlaybackDecision.Transcode(PlexTranscodeTarget.AAC, 320),
            PlexPlayback.decide(cdFlac.copy(bitrateKbps = null), prefs, relay = true),
        )
        // ALAC that fits the relay is still FLAC-transcoded (lossless kept).
        assertEquals(PlexPlaybackDecision.Transcode(PlexTranscodeTarget.FLAC, null), PlexPlayback.decide(alac, prefs, relay = true))
    }

    // ── Direct URL ────────────────────────────────────────────────────────────

    @Test
    fun `direct play is the connection plus the part key, base path kept`() {
        val key = "/library/parts/3001/1600000000/file.flac"
        assertEquals(
            "https://192-168-1-20.0123abcd.plex.direct:32400/library/parts/3001/1600000000/file.flac",
            PlexPlayback.directUrl("https://192-168-1-20.0123abcd.plex.direct:32400", key),
        )
        assertEquals(
            "https://music.example.com/plex/library/parts/3001/1600000000/file.flac",
            PlexPlayback.directUrl("https://music.example.com/plex/", key),
        )
        assertFalse(PlexPlayback.directUrl("https://a:1", key).contains("X-Plex-Token"))
    }

    // ── Transcode URL ─────────────────────────────────────────────────────────

    private fun flacUrl(offsetSeconds: Long = 0) = PlexPlayback.transcodeUrl(
        baseUri       = "https://music.example.com:443",
        ratingKey     = "1001",
        target        = PlexTranscodeTarget.FLAC,
        bitrateKbps   = null,
        offsetSeconds = offsetSeconds,
        location      = "wan",
        sessionId     = "session-1",
        client        = client,
    ).toHttpUrl()

    @Test
    fun `a FLAC transcode asks the universal transcoder over http with the Generic profile`() {
        val url = flacUrl(offsetSeconds = 93)
        assertEquals("/music/:/transcode/universal/start.flac", url.encodedPath)
        assertEquals("/library/metadata/1001", url.queryParameter("path"))
        assertEquals("0", url.queryParameter("mediaIndex"))
        assertEquals("0", url.queryParameter("partIndex"))
        assertEquals("http", url.queryParameter("protocol"))
        assertEquals("0", url.queryParameter("directPlay"))
        assertEquals("0", url.queryParameter("directStream"))
        assertEquals("93", url.queryParameter("offset"))
        assertEquals("wan", url.queryParameter("location"))
        assertEquals("session-1", url.queryParameter("transcodeSessionId"))
        assertEquals("session-1", url.queryParameter("X-Plex-Session-Identifier"))
        assertEquals("client-uuid", url.queryParameter("X-Plex-Client-Identifier"))
        assertEquals("Sheliak", url.queryParameter("X-Plex-Product"))
        assertEquals("Android", url.queryParameter("X-Plex-Platform"))
        assertEquals("SM-F971B", url.queryParameter("X-Plex-Model"))
        assertEquals("Cris’s Fold", url.queryParameter("X-Plex-Device-Name"))
        assertEquals("Generic", url.queryParameter("X-Plex-Client-Profile-Name"))
        assertEquals(
            "add-transcode-target(type=musicProfile&context=streaming&protocol=http&container=flac&audioCodec=flac)",
            url.queryParameter("X-Plex-Client-Profile-Extra"),
        )
        assertNull(url.queryParameter("musicBitrate"))
        assertNull(url.queryParameter("X-Plex-Token"))
    }

    @Test
    fun `the profile augmentation is percent-encoded inside the query`() {
        val raw = flacUrl().toString()
        assertTrue(raw.contains("X-Plex-Client-Profile-Extra=add-transcode-target%28type%3DmusicProfile%26context%3Dstreaming"))
    }

    @Test
    fun `an AAC transcode carries the bit rate, over http or HLS`() {
        val http = PlexPlayback.transcodeUrl(
            "https://h:32400", "1002", PlexTranscodeTarget.AAC, 256, 0, "cellular", "s", client,
        ).toHttpUrl()
        assertEquals("/music/:/transcode/universal/start.ts", http.encodedPath)
        assertEquals("256", http.queryParameter("musicBitrate"))
        assertEquals("cellular", http.queryParameter("location"))
        assertEquals(
            "add-transcode-target(type=musicProfile&context=streaming&protocol=http&container=mpegts&audioCodec=aac)",
            http.queryParameter("X-Plex-Client-Profile-Extra"),
        )
        val hls = PlexPlayback.transcodeUrl(
            "https://h:32400", "1002", PlexTranscodeTarget.AAC, 256, 0, "wan", "s", client, PlexTranscodeProtocol.HLS,
        ).toHttpUrl()
        assertEquals("/music/:/transcode/universal/start.m3u8", hls.encodedPath)
        assertEquals("hls", hls.queryParameter("protocol"))
        assertEquals("application/x-mpegURL", PlexPlayback.transcodeMimeType(PlexTranscodeTarget.AAC, PlexTranscodeProtocol.HLS))
    }

    @Test
    fun `FLAC is always delivered over http, even with the HLS switch`() {
        val url = PlexPlayback.transcodeUrl(
            "https://h:32400", "1", PlexTranscodeTarget.FLAC, null, 0, "lan", "s", client, PlexTranscodeProtocol.HLS,
        ).toHttpUrl()
        assertEquals("http", url.queryParameter("protocol"))
        assertEquals("audio/flac", PlexPlayback.transcodeMimeType(PlexTranscodeTarget.FLAC, PlexTranscodeProtocol.HLS))
    }

    @Test
    fun `a negative offset is clamped and a base path is kept`() {
        val url = PlexPlayback.transcodeUrl(
            "https://music.example.com/plex", "1", PlexTranscodeTarget.FLAC, null, -5, "wan", "s", client,
        ).toHttpUrl()
        assertEquals("/plex/music/:/transcode/universal/start.flac", url.encodedPath)
        assertEquals("0", url.queryParameter("offset"))
    }

    @Test
    fun `transcode formats describe the output, not the source`() {
        assertEquals(
            AudioFormatInfo("flac", "flac", null, 44_100, 16, 2, lossless = true),
            PlexPlayback.transcodeFormat(PlexTranscodeTarget.FLAC, null, alac),
        )
        assertEquals(
            AudioFormatInfo("aac", "mpegts", 320, null, null, 2, lossless = false),
            PlexPlayback.transcodeFormat(PlexTranscodeTarget.AAC, 320, wma),
        )
    }

    @Test
    fun `location and mime hints`() {
        assertEquals("lan", PlexPlayback.location(local = true, cellular = true))
        assertEquals("cellular", PlexPlayback.location(local = false, cellular = true))
        assertEquals("wan", PlexPlayback.location(local = false, cellular = false))
        assertEquals("audio/flac", PlexPlayback.mimeTypeForContainer("FLAC"))
        assertEquals("audio/mp4", PlexPlayback.mimeTypeForContainer("m4a"))
        assertNull(PlexPlayback.mimeTypeForContainer("asf"))
        assertNull(PlexPlayback.mimeTypeForContainer(null))
    }

    // ── Art ───────────────────────────────────────────────────────────────────

    @Test
    fun `art is the photo transcoder with the token as a query parameter`() {
        val url = PlexArt.url(
            "https://192-168-1-20.0123abcd.plex.direct:32400",
            "/library/metadata/901/thumb/1700000100",
            300,
            "server-token",
        )!!.toHttpUrl()
        assertEquals("/photo/:/transcode", url.encodedPath)
        assertEquals("512", url.queryParameter("width"))
        assertEquals("512", url.queryParameter("height"))
        assertEquals("1", url.queryParameter("minSize"))
        assertEquals("1", url.queryParameter("upscale"))
        assertEquals("/library/metadata/901/thumb/1700000100", url.queryParameter("url"))
        assertEquals("server-token", url.queryParameter("X-Plex-Token"))
    }

    @Test
    fun `art sizes round up to buckets, blank thumbs give no model`() {
        assertEquals(128, PlexArt.bucket(1))
        assertEquals(256, PlexArt.bucket(256))
        assertEquals(1024, PlexArt.bucket(700))
        assertEquals(2048, PlexArt.bucket(5000))
        assertNull(PlexArt.url("https://h:1", " ", 100, "t"))
        assertEquals("/plex/photo/:/transcode", PlexArt.url("https://h/plex/", "/t", 100, "t")!!.toHttpUrl().encodedPath)
    }

    // ── Sign-in ───────────────────────────────────────────────────────────────

    @Test
    fun `the auth URL keeps its parameters in the fragment with encoded brackets`() {
        assertEquals(
            "https://app.plex.tv/auth#?clientID=client-uuid&code=abc%20def" +
                "&context%5Bdevice%5D%5Bproduct%5D=Sheliak",
            PlexPinAuth.authUrl("client-uuid", "abc def", "Sheliak"),
        )
    }

    @Test
    fun `a PIN expires when plex tv says, whatever the format`() {
        val now = 1_000L
        val iso = PlexPin(expiresAt = "2026-10-06T10:30:00Z", expiresIn = 1800)
        assertEquals(java.time.Instant.parse("2026-10-06T10:30:00Z").toEpochMilli(), PinExpiry.expiresAtMillis(iso, now))
        val offset = PlexPin(expiresAt = "2026-10-06T12:30:00+02:00")
        assertEquals(java.time.Instant.parse("2026-10-06T10:30:00Z").toEpochMilli(), PinExpiry.expiresAtMillis(offset, now))
        val epoch = PlexPin(expiresAt = "1790000000")
        assertEquals(1_790_000_000_000L, PinExpiry.expiresAtMillis(epoch, now))
        val onlyIn = PlexPin(expiresIn = 1800, createdAt = "2026-10-06T10:00:00Z")
        assertEquals(java.time.Instant.parse("2026-10-06T10:30:00Z").toEpochMilli(), PinExpiry.expiresAtMillis(onlyIn, now))
        val onlyInNoCreated = PlexPin(expiresIn = 60)
        assertEquals(61_000L, PinExpiry.expiresAtMillis(onlyInNoCreated, now))
        assertEquals(now + PinExpiry.FALLBACK_LIFETIME_MS, PinExpiry.expiresAtMillis(PlexPin(expiresAt = "soon"), now))
        assertTrue(PinSession(1, "c", "u", expiresAt = 5).isExpired(5))
        assertFalse(PinSession(1, "c", "u", expiresAt = 5).isExpired(4))
    }
}
