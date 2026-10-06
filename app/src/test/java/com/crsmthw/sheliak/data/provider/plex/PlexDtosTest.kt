package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.plex.PlexFixtures.decode
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlexDtosTest {

    @Serializable
    private data class Probe(
        @Serializable(with = PlexIntSerializer::class) val i: Int? = null,
        @Serializable(with = PlexLongSerializer::class) val l: Long? = null,
        @Serializable(with = PlexBooleanSerializer::class) val b: Boolean? = null,
        @Serializable(with = PlexStringSerializer::class) val s: String? = null,
        @Serializable(with = PlexFloatSerializer::class) val f: Float? = null,
    )

    private fun probe(value: String): Probe =
        decode(Probe.serializer(), """{"i": $value, "l": $value, "b": $value, "s": $value, "f": $value}""")

    @Test
    fun `lenient scalars read numbers, quoted numbers and booleans`() {
        assertEquals(Probe(i = 1, l = 1, b = true, s = "1", f = 1f), probe("1"))
        assertEquals(Probe(i = 1, l = 1, b = true, s = "1", f = 1f), probe("\"1\""))
        assertEquals(Probe(i = 0, l = 0, b = false, s = "0", f = 0f), probe("\"0\""))
        assertEquals(Probe(i = null, l = null, b = true, s = "true", f = null), probe("true"))
        assertEquals(Probe(i = null, l = null, b = true, s = "true", f = null), probe("\"true\""))
        assertEquals(Probe(i = null, l = null, b = false, s = "false", f = null), probe("false"))
        assertEquals(Probe(i = 2, l = 2, b = true, s = "2.0", f = 2f), probe("2.0"))
    }

    @Test
    fun `empty, null, missing and wrongly shaped values read as null`() {
        assertEquals(Probe(s = ""), probe("\"\""))
        assertEquals(Probe(), probe("null"))
        assertEquals(Probe(), decode(Probe.serializer(), "{}"))
        assertEquals(Probe(), probe("{\"x\": 1}"))
        assertEquals(Probe(), probe("[1, 2]"))
        assertEquals(Probe(s = "abc"), probe("\"abc\""))
    }

    @Test
    fun `epoch seconds and byte sizes fit a Long`() {
        val p = decode(Probe.serializer(), """{"l": "4102444800123"}""")
        assertEquals(4_102_444_800_123L, p.l)
    }

    @Test
    fun `a pending PIN has no token, unknown keys are ignored`() {
        val pin = decode(PlexPin.serializer(), PlexFixtures.PIN_PENDING)
        assertEquals(1_234_567_890L, pin.id)
        assertEquals("abcdefghijklmnopqrstuvwxy", pin.code)
        assertNull(pin.authToken)
        assertEquals(1800L, pin.expiresIn)
        assertEquals("2026-10-06T10:30:00Z", pin.expiresAt)
        assertEquals(false, pin.trusted)
        assertNull(pin.newRegistration)
    }

    @Test
    fun `a claimed PIN carries the token, ids and flags as strings`() {
        val pin = decode(PlexPin.serializer(), PlexFixtures.PIN_CLAIMED)
        assertEquals(1_234_567_890L, pin.id)
        assertEquals("account-token-xyz", pin.authToken)
        assertEquals(1800L, pin.expiresIn)
        assertEquals(false, pin.trusted)
    }

    @Test
    fun `resources is a bare array with every connection kind`() {
        val resources = decode(ListSerializer(PlexResource.serializer()), PlexFixtures.RESOURCES)
        assertEquals(3, resources.size)
        val owned = resources[0]
        assertEquals(PlexFixtures.OWNED_ID, owned.clientIdentifier)
        assertEquals("owned-server-token", owned.accessToken)
        assertEquals(true, owned.owned)
        assertTrue(owned.isServer)
        val connections = owned.connections.orEmpty()
        assertEquals(5, connections.size)
        assertEquals(listOf(true, false, false, true, false), connections.map { it.local })
        assertEquals(listOf(false, true, false, false, false), connections.map { it.relay })
        assertEquals(32400, connections[0].port)
        assertEquals("https://music.example.com:443", connections[2].uri)
    }

    @Test
    fun `a shared server typed with strings still parses`() {
        val shared = decode(ListSerializer(PlexResource.serializer()), PlexFixtures.RESOURCES)[1]
        assertEquals(false, shared.owned)
        assertEquals(true, shared.presence)
        assertEquals("friend", shared.sourceTitle)
        assertEquals("shared-server-token", shared.accessToken)
        val c = shared.connections.orEmpty()
        assertEquals(listOf(true, false, false), c.map { it.local })
        assertEquals(listOf(false, true, false), c.map { it.ipv6 })
        assertEquals(32400, c[0].port)
    }

    @Test
    fun `provides is a comma list, tested by containment`() {
        val resources = decode(ListSerializer(PlexResource.serializer()), PlexFixtures.RESOURCES)
        assertEquals(listOf(true, true, false), resources.map { it.isServer })
        assertTrue(PlexResource(provides = "client, server ,player").isServer)
        assertFalse(PlexResource(provides = "servers").isServer)
        assertFalse(PlexResource().isServer)
    }

    @Test
    fun `identity names the machine and version`() {
        val c = decode(PlexResponse.serializer(), PlexFixtures.IDENTITY).mediaContainer
        assertEquals(PlexFixtures.OWNED_ID, c?.machineIdentifier)
        assertEquals("1.43.4.10000-abcdef012", c?.version)
        assertEquals(true, c?.claimed)
    }

    @Test
    fun `sections list directories with numeric or string keys`() {
        val dirs = decode(PlexResponse.serializer(), PlexFixtures.SECTIONS).mediaContainer?.directories.orEmpty()
        assertEquals(listOf("1", "2", "5"), dirs.map { it.key })
        assertEquals(listOf("artist", "movie", "artist"), dirs.map { it.type })
        assertEquals("Audiobooks", dirs[2].title)
    }

    @Test
    fun `a track listing page has paging fields, media and parts but no streams`() {
        val c = decode(PlexResponse.serializer(), PlexFixtures.TRACKS_PAGE_1).mediaContainer!!
        assertEquals(2, c.size)
        assertEquals(3, c.totalSize)
        assertEquals(0, c.offset)
        val tracks = c.metadata.orEmpty()
        assertEquals(listOf("1001", "1002"), tracks.map { it.ratingKey })
        val first = tracks[0]
        assertEquals("1", first.librarySectionId)
        assertEquals(5, first.index)
        assertEquals(1, first.parentIndex)
        assertEquals(1_700_000_100L, first.updatedAt)
        assertEquals("/library/parts/3001/1600000000/file.flac", first.firstPart?.key)
        assertEquals(172_187_500L, first.firstPart?.size)
        assertNull(first.audioStream)
        val second = tracks[1]
        assertEquals(2, second.index)
        assertEquals(2, second.absoluteIndex)
        assertEquals(215_000L, second.duration)
        assertEquals(320, second.media?.single()?.bitrate)
        assertEquals("Guest Artist", second.originalTitle)
    }

    @Test
    fun `a metadata batch carries audio and lyric streams`() {
        val tracks = decode(PlexResponse.serializer(), PlexFixtures.METADATA_BATCH).mediaContainer?.metadata.orEmpty()
        val flac = tracks[0].audioStream!!
        assertEquals("flac", flac.codec)
        assertEquals(24, flac.bitDepth)
        assertEquals(96_000, flac.samplingRate)
        assertEquals(-8.53f, flac.gain)
        assertEquals(-7.21f, flac.albumGain)
        val lyrics = tracks[0].lyricStreams.single()
        assertEquals("/library/streams/4002", lyrics.key)
        assertEquals("lrc", lyrics.format)
        assertEquals(true, lyrics.timed)
        assertEquals(5, lyrics.minLines)
        val mp3 = tracks[1].audioStream!!
        assertEquals(44_100, mp3.samplingRate)
        assertNull(mp3.bitDepth)
        assertTrue(tracks[1].lyricStreams.isEmpty())
    }

    @Test
    fun `playlists list dumb and smart audio playlists`() {
        val lists = decode(PlexResponse.serializer(), PlexFixtures.PLAYLISTS).mediaContainer?.metadata.orEmpty()
        assertEquals(listOf("5001", "5002"), lists.map { it.ratingKey })
        assertEquals(listOf(false, true), lists.map { it.smart })
        assertEquals("/playlists/5001/items", lists[0].key)
        assertEquals("/playlists/5001/composite/1700000200", lists[0].composite)
        assertEquals(5, lists[1].leafCount)
        assertEquals(1_200_000L, lists[1].duration)
    }

    @Test
    fun `playlist items carry their playlistItemID, numeric or string`() {
        val c = decode(PlexResponse.serializer(), PlexFixtures.PLAYLIST_ITEMS).mediaContainer!!
        assertEquals(2, c.totalSize)
        assertEquals(listOf("7002", "7001"), c.metadata.orEmpty().map { it.playlistItemId })
        assertEquals(listOf("1002", "1001"), c.metadata.orEmpty().map { it.ratingKey })
    }
}
