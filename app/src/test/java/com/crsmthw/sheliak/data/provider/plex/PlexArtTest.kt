package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.plex.PlexFixtures.decode
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The art URL version rule (M1 fix round 1, D4): which connection a cover URL is built on before and after the
 * pick lands, and that the URL is a pure function of (base, thumb, size, token) — an art-version bump onto the
 * same connection gives the SAME string (caches keep answering), a bump onto another connection or a new token a
 * NEW one (a new Coil request). No version parameter is ever added to the URL.
 */
class PlexArtTest {

    private val resources = decode(ListSerializer(PlexResource.serializer()), PlexFixtures.RESOURCES)
    private val ownedConnections = resources[0].connections.orEmpty().mapNotNull(PlexConnectionInfo::from)

    private val lanHttps = "https://192-168-1-20.0123abcd.plex.direct:32400"
    private val lanHttp = "http://192.168.1.20:32400"
    private val domain = "https://music.example.com:443"

    private val thumb = "/library/metadata/901/thumb/1700000100"

    private fun base(
        current: String? = null,
        lastPicked: String? = null,
        setupBase: String? = null,
        connections: List<PlexConnectionInfo> = ownedConnections,
        owned: Boolean = true,
        localAllowed: Boolean = true,
    ): String? = PlexArt.base(current, lastPicked, setupBase, connections, owned) { localAllowed }

    @Test
    fun `the live pick wins over every guess`() {
        assertEquals(domain, base(current = domain, lastPicked = lanHttps, setupBase = lanHttp))
    }

    @Test
    fun `before the pick lands the last pick, then the setup connection, then the best stored one`() {
        assertEquals(lanHttps, base(lastPicked = lanHttps, setupBase = domain))
        assertEquals(domain, base(setupBase = domain))
        assertEquals(lanHttps, base())
    }

    @Test
    fun `a LAN guess is skipped while LAN connections may not be opened`() {
        // Android 17's permission missing: the setup's LAN connection would time out, so the best remote one is used.
        assertEquals(domain, base(setupBase = lanHttps, localAllowed = false))
        assertEquals(domain, base(localAllowed = false))
        // A server the user does not own: its LAN addresses are someone else's.
        assertEquals(domain, base(setupBase = lanHttp, owned = false))
        // A setup connection that is not a known LAN one is kept either way, verbatim.
        assertEquals("$domain/", base(setupBase = "$domain/", localAllowed = false))
        // A known LAN connection is recognised with or without a trailing slash.
        assertEquals(domain, base(setupBase = "$lanHttps/", localAllowed = false))
    }

    @Test
    fun `the permission is asked only when there is no pick to use`() {
        var asked = 0
        PlexArt.base(domain, null, lanHttps, ownedConnections, owned = true) { asked++; true }
        PlexArt.base(null, domain, lanHttps, ownedConnections, owned = true) { asked++; true }
        assertEquals(0, asked)
        PlexArt.base(null, null, lanHttps, ownedConnections, owned = true) { asked++; true }
        assertEquals(1, asked)
    }

    @Test
    fun `nothing to build on gives no base`() {
        assertNull(base(connections = emptyList()))
        assertNull(base(current = " ", lastPicked = "", setupBase = null, connections = emptyList()))
    }

    @Test
    fun `a bump onto the same connection gives the same URL`() {
        val before = PlexArt.url(base(setupBase = lanHttps)!!, thumb, 300, "server-token")
        val after = PlexArt.url(base(current = lanHttps, setupBase = lanHttps)!!, thumb, 300, "server-token")
        assertEquals(before, after)
    }

    @Test
    fun `a bump onto another connection, or a new token, gives a new URL`() {
        val guessed = PlexArt.url(base(setupBase = lanHttps)!!, thumb, 300, "server-token")
        val picked = PlexArt.url(base(current = domain, setupBase = lanHttps)!!, thumb, 300, "server-token")
        assertNotEquals(guessed, picked)
        assertEquals("music.example.com", picked!!.toHttpUrl().host)
        val rotated = PlexArt.url(base(current = domain)!!, thumb, 300, "rotated-token")
        assertNotEquals(picked, rotated)
    }

    @Test
    fun `the URL carries no version or cache-busting parameter`() {
        val url = PlexArt.url(domain, thumb, 300, "server-token")!!.toHttpUrl()
        assertEquals(
            setOf("width", "height", "minSize", "upscale", "url", "X-Plex-Token"),
            url.queryParameterNames,
        )
        assertEquals(thumb, url.queryParameter("url"))
    }
}
