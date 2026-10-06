package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Retrofit interfaces as they hit the wire: an OkHttp interceptor records each request and answers with a
 * fixture, so paths, methods, query strings and headers are checked without a server.
 */
class PlexApiTest {

    private val client = PlexClientInfo(
        clientIdentifier = "client-uuid",
        version          = "0.1.0",
        platformVersion  = "17",
        device           = "Galaxy Z Fold8",
        model            = "SM-F971B",
        vendor           = "samsung",
        deviceName       = "Cris’s Fold\r\nX-Injected: 1",
    )

    private class Recorder(private val answer: (Request) -> Pair<Int, String>) : Interceptor {
        val requests = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val (code, body) = answer(request)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .header(PlexHeaders.CONTAINER_TOTAL_SIZE, "3")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun server(base: String, recorder: Recorder, token: String? = "server-token"): PlexServerApi {
        val http = OkHttpClient.Builder()
            .addInterceptor(PlexHeadersInterceptor({ client }) { token })
            .addInterceptor(recorder)
            .build()
        return PlexRetrofit.server(base, http, PlexFixtures.json)
    }

    private fun tv(recorder: Recorder): PlexTvApi {
        val http = OkHttpClient.Builder()
            .addInterceptor(PlexHeadersInterceptor({ client }))
            .addInterceptor(recorder)
            .build()
        return PlexRetrofit.tv(http, PlexFixtures.json)
    }

    @Test
    fun `a listing page sends both container headers, the sort and the filter, and reads the total header`() = runTest {
        val recorder = Recorder { 200 to PlexFixtures.TRACKS_PAGE_1 }
        val api = server("https://music.example.com:443", recorder)
        val response = api.sectionItems("1", PlexTypes.TRACK, PlexFilters.SORT_ADDED, PlexFilters.updatedSince(1_700_000_000), 500, 500)
        val page = PlexPage.from(response, "listing")
        assertEquals(2, page.items.size)
        assertEquals(3, page.totalSize)

        val request = recorder.requests.single()
        assertEquals("GET", request.method)
        assertEquals("/library/sections/1/all", request.url.encodedPath)
        assertEquals("type=10&sort=addedAt&updatedAt>>=1700000000", request.url.query)
        assertEquals("500", request.header(PlexHeaders.CONTAINER_START))
        assertEquals("500", request.header(PlexHeaders.CONTAINER_SIZE))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("client-uuid", request.header(PlexHeaders.CLIENT_IDENTIFIER))
        assertEquals("Sheliak", request.header(PlexHeaders.PRODUCT))
        assertEquals("SM-F971B", request.header(PlexHeaders.MODEL))
        assertEquals("server-token", request.header(PlexHeaders.TOKEN))
    }

    @Test
    fun `a non-ASCII device name is sent as is, control characters stripped`() = runTest {
        val recorder = Recorder { 200 to PlexFixtures.IDENTITY }
        server("https://h:32400", recorder).identity()
        val request = recorder.requests.single()
        assertEquals("Cris’s FoldX-Injected: 1", request.header(PlexHeaders.DEVICE_NAME))
        assertNull(request.header("X-Injected"))
    }

    @Test
    fun `a custom URL's base path is kept for every relative path`() = runTest {
        val recorder = Recorder { 200 to PlexFixtures.SECTIONS }
        val api = server("https://music.example.com/plex", recorder)
        api.sections()
        api.metadata("1001,1002")
        assertEquals(
            listOf("/plex/library/sections", "/plex/library/metadata/1001,1002"),
            recorder.requests.map { it.url.encodedPath },
        )
    }

    @Test
    fun `timeline is a POST with times in milliseconds and the session header`() = runTest {
        val recorder = Recorder { 200 to "" }
        val api = server("https://h:32400/", recorder)
        api.timeline("1001", PlexPlayback.metadataKey("1001"), "playing", 93_000, 475_000, "session-1")
        val request = recorder.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/:/timeline", request.url.encodedPath)
        assertEquals("1001", request.url.queryParameter("ratingKey"))
        assertEquals("/library/metadata/1001", request.url.queryParameter("key"))
        assertEquals("playing", request.url.queryParameter("state"))
        assertEquals("93000", request.url.queryParameter("time"))
        assertEquals("475000", request.url.queryParameter("duration"))
        assertEquals("session-1", request.header(PlexHeaders.SESSION_IDENTIFIER))
    }

    @Test
    fun `scrobble is a PUT with the library identifier`() = runTest {
        val recorder = Recorder { 200 to "" }
        server("https://h:32400", recorder).scrobble("1001", PlexPlayback.LIBRARY_IDENTIFIER)
        val request = recorder.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("/:/scrobble", request.url.encodedPath)
        assertEquals("1001", request.url.queryParameter("key"))
        assertEquals("com.plexapp.plugins.library", request.url.queryParameter("identifier"))
    }

    @Test
    fun `playlists and their items`() = runTest {
        val recorder = Recorder { r -> 200 to if (r.url.encodedPath.endsWith("/items")) PlexFixtures.PLAYLIST_ITEMS else PlexFixtures.PLAYLISTS }
        val api = server("https://h:32400", recorder)
        assertEquals(2, api.playlists("audio").body()?.mediaContainer?.metadata?.size)
        assertEquals(2, PlexPage.from(api.playlistItems("5001", 0, 500), "items").items.size)
        assertEquals("playlistType=audio", recorder.requests[0].url.query)
        assertEquals("/playlists/5001/items", recorder.requests[1].url.encodedPath)
        assertEquals("0", recorder.requests[1].header(PlexHeaders.CONTAINER_START))
        assertEquals("500", recorder.requests[1].header(PlexHeaders.CONTAINER_SIZE))
    }

    @Test
    fun `an unauthenticated probe client sends no token`() = runTest {
        val recorder = Recorder { 200 to PlexFixtures.IDENTITY }
        val identity = server("https://h:32400", recorder, token = null).identity()
        assertEquals(PlexFixtures.OWNED_ID, identity.body()?.mediaContainer?.machineIdentifier)
        assertNull(recorder.requests.single().header(PlexHeaders.TOKEN))
    }

    @Test
    fun `PIN creation and polling on plex tv`() = runTest {
        val recorder = Recorder { r -> 200 to if (r.method == "POST") PlexFixtures.PIN_PENDING else PlexFixtures.PIN_CLAIMED }
        val api = tv(recorder)
        val pending = api.createPin(strong = true).body()!!
        val claimed = api.pin(pending.id!!).body()!!
        assertEquals("account-token-xyz", claimed.authToken)
        val (create, poll) = recorder.requests
        assertEquals("POST", create.method)
        assertEquals("https://plex.tv/api/v2/pins?strong=true", create.url.toString())
        assertEquals("https://plex.tv/api/v2/pins/1234567890", poll.url.toString())
        assertEquals("client-uuid", create.header(PlexHeaders.CLIENT_IDENTIFIER))
        assertEquals("Sheliak", create.header(PlexHeaders.PRODUCT))
        assertNull(create.header(PlexHeaders.TOKEN))
    }

    @Test
    fun `the PIN flow through PlexPinAuth`() = runTest {
        var claimed = false
        val recorder = Recorder { r ->
            when {
                r.method == "POST" -> 200 to PlexFixtures.PIN_PENDING
                claimed            -> 200 to PlexFixtures.PIN_CLAIMED
                else               -> 200 to PlexFixtures.PIN_PENDING
            }
        }
        val auth = PlexPinAuth(tv(recorder), { client }, clock = { 0L })
        val session = auth.startPin().getOrThrow()
        assertTrue(session.authUrl.startsWith("https://app.plex.tv/auth#?clientID=client-uuid&code=abcdefghijklmnopqrstuvwxy"))
        assertNull(auth.pollPin(session).getOrThrow())
        claimed = true
        assertEquals("account-token-xyz", auth.pollPin(session).getOrThrow())
    }

    @Test
    fun `an expired PIN and a refused token`() = runTest {
        val recorder = Recorder { r -> if (r.url.encodedPath.startsWith("/api/v2/pins/")) 404 to "{}" else 401 to "{}" }
        val auth = PlexPinAuth(tv(recorder), { client })
        val gone = auth.pollPin(PinSession(1, "c", "u", Long.MAX_VALUE)).exceptionOrNull()
        assertEquals(ProviderError.AUTH, (gone as ProviderException).error)
        assertEquals(false, auth.checkToken("old").getOrThrow())
        assertEquals("old", recorder.requests.last().header(PlexHeaders.TOKEN))
    }

    @Test
    fun `resources come from clients plex tv with the account token, servers only`() = runTest {
        val recorder = Recorder { 200 to PlexFixtures.RESOURCES }
        val servers = tv(recorder).servers("account-token")
        assertEquals(listOf(PlexFixtures.OWNED_ID, PlexFixtures.SHARED_ID), servers.map { it.clientIdentifier })
        val request = recorder.requests.single()
        assertEquals("clients.plex.tv", request.url.host)
        assertEquals("/api/v2/resources", request.url.encodedPath)
        assertEquals("includeHttps=1&includeRelay=1&includeIPv6=1", request.url.query)
        assertEquals("account-token", request.header(PlexHeaders.TOKEN))
    }
}
