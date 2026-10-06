package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.provider.plex.PlexFixtures.decode
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlexConnectionPickerTest {

    private val resources = decode(ListSerializer(PlexResource.serializer()), PlexFixtures.RESOURCES)
    private val owned = resources[0].connections.orEmpty().mapNotNull(PlexConnectionInfo::from)
    private val shared = resources[1].connections.orEmpty().mapNotNull(PlexConnectionInfo::from)

    private val lanHttps = "https://192-168-1-20.0123abcd.plex.direct:32400"
    private val lanHttp = "http://192.168.1.20:32400"
    private val domain = "https://music.example.com:443"
    private val remoteDirect = "https://203-0-113-7.0123abcd.plex.direct:32400"
    private val relay = "https://198-51-100-4.0123abcd.plex.direct:8443"

    /** Answers per URI after a delay; unknown URIs are unreachable. Records every probe. */
    private class FakeProber(private val answers: Map<String, Pair<Long, PlexProbeResult>>) : PlexProber {
        val probed = mutableListOf<String>()
        override suspend fun probe(connection: PlexConnectionInfo): PlexProbeResult {
            probed += connection.uri
            val (delayMs, result) = answers[connection.uri] ?: (0L to PlexProbeResult.UNREACHABLE)
            delay(delayMs)
            return result
        }
    }

    @Test
    fun `order is local, remote, relay - https before http within each`() {
        val ordered = PlexConnectionPicker.order(owned, owned = true, localAllowed = true).map { it.uri }
        assertEquals(listOf(lanHttps, lanHttp, domain, remoteDirect, relay), ordered)
    }

    @Test
    fun `the custom domain is a remote connection like any other`() {
        val custom = owned.single { it.uri == domain }
        assertEquals(PlexConnectionInfo.Kind.REMOTE, custom.kind)
        assertEquals(PlexConnectionInfo.Kind.RELAY, owned.single { it.uri == relay }.kind)
    }

    @Test
    fun `local connections are skipped on a shared server and without the LAN permission`() {
        val sharedOrder = PlexConnectionPicker.order(shared, owned = false, localAllowed = true)
        assertTrue(sharedOrder.none { it.local })
        // IPv4 before IPv6 among the remote ones.
        assertEquals(
            listOf("https://192-0-2-9.fedcba98.plex.direct:32400", "https://2001-db8--5.fedcba98.plex.direct:32400"),
            sharedOrder.map { it.uri },
        )
        val noPermission = PlexConnectionPicker.order(owned, owned = true, localAllowed = false).map { it.uri }
        assertEquals(listOf(domain, remoteDirect, relay), noPermission)
    }

    @Test
    fun `duplicate and blank URIs are dropped`() {
        val list = listOf(
            PlexConnectionInfo(uri = "https://a:1/"),
            PlexConnectionInfo(uri = "https://a:1"),
            PlexConnectionInfo(uri = " "),
        )
        assertEquals(1, PlexConnectionPicker.order(list, owned = true, localAllowed = true).size)
    }

    @Test
    fun `the best kind that answers wins even when a worse one answers first`() = runTest {
        val prober = FakeProber(
            mapOf(
                lanHttps to (900L to PlexProbeResult.OK),
                relay to (10L to PlexProbeResult.OK),
            ),
        )
        val pick = PlexConnectionPicker.choose(PlexConnectionPicker.order(owned, true, true), prober)
        assertEquals(lanHttps, pick.connection?.uri)
    }

    @Test
    fun `when the LAN fails the custom domain is used before plex direct and relay`() = runTest {
        val prober = FakeProber(
            mapOf(
                domain to (200L to PlexProbeResult.OK),
                remoteDirect to (50L to PlexProbeResult.OK),
                relay to (10L to PlexProbeResult.OK),
            ),
        )
        val pick = PlexConnectionPicker.choose(PlexConnectionPicker.order(owned, true, true), prober)
        assertEquals(domain, pick.connection?.uri)
        assertEquals(5, prober.probed.size)   // every connection is probed at once
    }

    @Test
    fun `relay is used when it is the only one that answers`() = runTest {
        val prober = FakeProber(mapOf(relay to (100L to PlexProbeResult.OK)))
        assertEquals(relay, PlexConnectionPicker.choose(PlexConnectionPicker.order(owned, true, true), prober).connection?.uri)
    }

    @Test
    fun `a probe that hangs is cut at its kind's timeout`() = runTest {
        val timeouts = PlexProbeTimeouts(localMs = 3_000, remoteMs = 6_000, relayMs = 10_000)
        val prober = FakeProber(
            mapOf(
                lanHttps to (60_000L to PlexProbeResult.OK),
                lanHttp to (60_000L to PlexProbeResult.OK),
                domain to (100L to PlexProbeResult.OK),
            ),
        )
        val pick = PlexConnectionPicker.choose(PlexConnectionPicker.order(owned, true, true), prober, timeouts)
        // Without the cut, the LAN connections would answer OK at 60 s and win.
        assertEquals(domain, pick.connection?.uri)
    }

    @Test
    fun `nothing answering gives no pick, and a refused token is reported`() = runTest {
        val none = PlexConnectionPicker.choose(PlexConnectionPicker.order(owned, true, true), FakeProber(emptyMap()))
        assertNull(none.connection)
        assertEquals(false, none.unauthorized)
        val refused = PlexConnectionPicker.choose(
            PlexConnectionPicker.order(owned, true, true),
            FakeProber(mapOf(domain to (0L to PlexProbeResult.UNAUTHORIZED))),
        )
        assertNull(refused.connection)
        assertEquals(true, refused.unauthorized)
    }

    @Test
    fun `a prober that throws counts as unreachable`() = runTest {
        val prober = PlexProber { c -> if (c.uri == lanHttps) error("boom") else PlexProbeResult.OK }
        assertEquals(lanHttp, PlexConnectionPicker.choose(PlexConnectionPicker.order(owned, true, true), prober).connection?.uri)
    }

    @Test
    fun `the pick is kept in memory until the network changes`() = runTest {
        var generation = 0L
        val prober = FakeProber(mapOf(domain to (0L to PlexProbeResult.OK)))
        val picker = PlexConnectionPicker(
            owned = true,
            connections = owned,
            prober = prober,
            networkGeneration = { generation },
        )
        assertEquals(domain, picker.current().uri)
        val probesAfterFirstPick = prober.probed.size
        assertEquals(domain, picker.current().uri)
        assertEquals(probesAfterFirstPick, prober.probed.size)

        generation = 1
        assertNull(picker.currentOrNull())
        assertEquals(domain, picker.lastPicked()?.uri)
        assertEquals(domain, picker.current().uri)
        assertEquals(probesAfterFirstPick * 2, prober.probed.size)
    }

    @Test
    fun `a failed call forgets the pick`() = runTest {
        val prober = FakeProber(mapOf(domain to (0L to PlexProbeResult.OK)))
        val picker = PlexConnectionPicker(owned = true, connections = owned, prober = prober)
        val first = picker.current()
        picker.invalidate(first)
        assertNull(picker.currentOrNull())
        picker.current()
        assertEquals(10, prober.probed.size)
    }

    @Test
    fun `when nothing answers the connections are refreshed and probed again`() = runTest {
        val fresh = PlexConnectionInfo(uri = "https://198-51-100-77.0123abcd.plex.direct:32400")
        var refreshed = 0
        val picker = PlexConnectionPicker(
            owned = true,
            connections = owned,
            prober = FakeProber(mapOf(fresh.uri to (0L to PlexProbeResult.OK))),
            refresh = { refreshed++; listOf(fresh) },
        )
        assertEquals(fresh, picker.current())
        assertEquals(1, refreshed)
    }

    @Test
    fun `nothing answering fails with NETWORK, a refused token with AUTH`() = runTest {
        val offline = PlexConnectionPicker(owned = true, connections = owned, prober = FakeProber(emptyMap()))
        assertEquals(ProviderError.NETWORK, assertFailsWith<ProviderException> { offline.current() }.error)
        val refused = PlexConnectionPicker(
            owned = true,
            connections = owned,
            prober = FakeProber(mapOf(remoteDirect to (0L to PlexProbeResult.UNAUTHORIZED))),
            refresh = { error("plex.tv unreachable") },
        )
        assertEquals(ProviderError.AUTH, assertFailsWith<ProviderException> { refused.current() }.error)
    }
}
