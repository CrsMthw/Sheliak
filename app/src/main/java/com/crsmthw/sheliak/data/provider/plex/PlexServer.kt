package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.CredentialStore
import com.crsmthw.sheliak.data.provider.PlaybackPrefs
import com.crsmthw.sheliak.data.provider.PlaybackSource
import com.crsmthw.sheliak.data.provider.ProviderDeps
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.provider.ProviderInstance
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One Plex Media Server as the provider uses it at run time: its access token (the RESOURCE's, never the
 * account's — PLEX.md correction 6), the picked connection ([PlexConnectionPicker], in memory only), one Retrofit
 * service per connection, and the call policy every request goes through ([call]). Built lazily: constructing it
 * does no I/O (the registry builds providers on the main thread).
 */
internal class PlexServer(
    private val instance: ProviderInstance,
    private val config: PlexInstanceConfig?,
    private val deps: ProviderDeps,
) : PlexLibrarySource {

    val machineId: String =
        instance.serverId?.takeIf { it.isNotBlank() } ?: instance.id.removePrefix(PlexProvider.ID_PREFIX)

    private val credentials = deps.credentials

    val clientInfo: PlexClientInfo by lazy { PlexDevice.clientInfo(deps.context, PlexClientIdentity.get(credentials)) }

    private val http: PlexHttp by lazy { PlexHttp(deps.httpClient, deps.json) { clientInfo } }

    private val client: OkHttpClient by lazy { http.serverClient(::token) }

    private val apis = ConcurrentHashMap<String, PlexServerApi>()

    private val refreshMutex = Mutex()

    private val tokenLock = Any()

    @Volatile
    private var tokenLoaded = false

    @Volatile
    private var cachedToken: String? = null

    /** Background warm-up (token + first pick) for non-suspending callers; its jobs are short-lived. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warming = AtomicBoolean(false)

    /** The session id of each track's last resolvePlayback, for its timeline reports. */
    val sessions = PlexSessions()

    val picker: PlexConnectionPicker by lazy {
        PlexNetworkMonitor.start(deps.context)
        PlexConnectionPicker(
            owned               = config?.owned ?: true,
            connections         = config?.connections.orEmpty(),
            prober              = PlexHttpProber(machineId, ::token, http),
            localNetworkAllowed = { PlexLocalNetwork.granted(deps.context) },
            networkGeneration   = { PlexNetworkMonitor.generation },
            refresh             = { refreshResource()?.connections },
        )
    }

    /** This server's access token (cached after the first read). */
    fun token(): String? {
        if (!tokenLoaded) {
            synchronized(tokenLock) {
                if (!tokenLoaded) {
                    cachedToken = credentials.token(PlexKeys.serverToken(machineId))
                    tokenLoaded = true
                }
            }
        }
        return cachedToken
    }

    private fun api(connection: PlexConnectionInfo): PlexServerApi =
        apis.getOrPut(connection.uri) { http.server(connection.uri, client) }

    /**
     * Runs [block] against the picked connection and returns its SUCCESSFUL response, or throws:
     * - a network failure forgets the pick and retries once on a freshly probed connection;
     * - a 401 re-fetches `/resources` once (the server's token may have changed — PLEX.md §11) and retries if
     *   the token did change; otherwise AUTH;
     * - any other status → [PlexErrors.forStatus].
     */
    suspend fun <T> call(what: String, block: suspend (PlexServerApi) -> Response<T>): Response<T> {
        var retriedNetwork = false
        var retriedAuth = false
        while (true) {
            val connection = picker.current()
            val response = try {
                block(api(connection))
            } catch (e: IOException) {
                picker.invalidate(connection)
                if (retriedNetwork) throw e
                retriedNetwork = true
                continue
            }
            if (response.code() == HTTP_UNAUTHORIZED && !retriedAuth) {
                retriedAuth = true
                val before = token()
                refreshResourceSafely()
                if (token() != before) continue
            }
            if (!response.isSuccessful) throw PlexErrors.exception(response.code(), what)
            return response
        }
    }

    // ── /resources refresh ──────────────────────────────────────────────────

    data class Refreshed(val accessToken: String?, val connections: List<PlexConnectionInfo>)

    /**
     * Re-reads this server's resource with the account token: stores a changed access token and returns the
     * current connections. Null when there is no account token or the account no longer lists this server.
     */
    suspend fun refreshResource(): Refreshed? = refreshMutex.withLock {
        val accountToken = withContext(Dispatchers.IO) { credentials.token(PlexKeys.ACCOUNT_TOKEN) } ?: return@withLock null
        val resource = http.tv.servers(accountToken).firstOrNull { it.clientIdentifier == machineId }
            ?: return@withLock null
        val fresh = resource.accessToken?.takeIf { it.isNotBlank() }
        if (fresh != null && fresh != token()) {
            withContext(Dispatchers.IO) { credentials.putToken(PlexKeys.serverToken(machineId), fresh) }
            cachedToken = fresh
            tokenLoaded = true
        }
        Refreshed(fresh, resource.connections.orEmpty().mapNotNull(PlexConnectionInfo::from))
    }

    private suspend fun refreshResourceSafely() {
        try {
            refreshResource()?.let { picker.replaceCandidates(it.connections) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // plex.tv unreachable: the 401 stands.
        }
    }

    // ── PlexLibrarySource ───────────────────────────────────────────────────

    override suspend fun sectionPage(
        sectionKey: String,
        type: Int,
        start: Int,
        size: Int,
        filters: Map<String, String>,
    ): PlexPage = PlexPage.from(
        call("listing") { it.sectionItems(sectionKey, type, PlexFilters.SORT_ADDED, filters, start, size) },
        "listing",
    )

    override suspend fun metadata(ratingKeys: List<String>): List<PlexMetadata> {
        if (ratingKeys.isEmpty()) return emptyList()
        return call("metadata") { it.metadata(ratingKeys.joinToString(",")) }
            .bodyOrThrow("metadata").mediaContainer?.metadata.orEmpty()
    }

    override suspend fun playlists(): List<PlexMetadata> =
        call("playlists") { it.playlists(PLAYLIST_TYPE_AUDIO) }
            .bodyOrThrow("playlists").mediaContainer?.metadata.orEmpty()

    override suspend fun playlistItems(playlistKey: String, start: Int, size: Int): PlexPage =
        PlexPage.from(call("playlist items") { it.playlistItems(playlistKey, start, size) }, "playlist items")

    // ── Playback ────────────────────────────────────────────────────────────

    /**
     * Direct play or a transcode of [track] through the picked connection (PLEX.md §5). The part key and format
     * come from the index (streamRef); a track the index lacks them for is looked up on the server. The token goes
     * in the headers, never the URL. Direct play relies on PMS's ad-hoc decision, which may answer 503/509: the
     * player then retries with `forceTranscode = true`.
     */
    suspend fun resolvePlayback(track: TrackKey, prefs: PlaybackPrefs): PlaybackSource {
        val stored = deps.index.track(track)
        var partKey = stored?.streamRef
        var format = stored?.format
        if (partKey == null) {
            val item = metadata(listOf(track.itemId)).firstOrNull()
                ?: throw ProviderException(ProviderError.UNAVAILABLE, "Track not on the server")
            partKey = item.firstPart?.key?.takeIf { it.isNotBlank() }
                ?: throw ProviderException(ProviderError.UNAVAILABLE, "Track without a media part")
            format = format ?: PlexMapping.format(item)
        }
        val connection = picker.current()
        val token = token() ?: throw ProviderException(ProviderError.AUTH, "No token for this Plex server")
        val session = sessions.start(track)
        val headers = mapOf(
            PlexHeaders.TOKEN to token,
            PlexHeaders.CLIENT_IDENTIFIER to clientInfo.clientIdentifier,
            PlexHeaders.SESSION_IDENTIFIER to session,
        )
        return when (val decision = PlexPlayback.decide(format, prefs, connection.relay)) {
            PlexPlaybackDecision.Direct -> PlaybackSource(
                uri         = PlexPlayback.directUrl(connection.uri, partKey),
                headers     = headers,
                mimeType    = PlexPlayback.mimeTypeForContainer(format?.container),
                format      = format,
                isTranscode = false,
            )
            is PlexPlaybackDecision.Transcode -> PlaybackSource(
                uri = PlexPlayback.transcodeUrl(
                    baseUri       = connection.uri,
                    ratingKey     = track.itemId,
                    target        = decision.target,
                    bitrateKbps   = decision.bitrateKbps,
                    offsetSeconds = 0,
                    location      = PlexPlayback.location(connection.local, PlexNetworkMonitor.isCellular),
                    sessionId     = session,
                    client        = clientInfo,
                    protocol      = TRANSCODE_PROTOCOL,
                ),
                headers     = headers,
                mimeType    = PlexPlayback.transcodeMimeType(decision.target, TRANSCODE_PROTOCOL),
                format      = PlexPlayback.transcodeFormat(decision.target, decision.bitrateKbps, format),
                isTranscode = true,
            )
        }
    }

    // ── Art ─────────────────────────────────────────────────────────────────

    /**
     * The cover URL for [thumb], built on the current pick — or, before the first pick lands, on the last one,
     * the setup-time [ProviderInstance.baseUrl], or the first stored connection (and a pick is started).
     */
    fun artModel(thumb: String, sizePx: Int): String? {
        val current = picker.currentOrNull()
        if (current == null) warmUp()
        val base = current?.uri
            ?: picker.lastPicked()?.uri
            ?: instance.baseUrl
            ?: PlexConnectionPicker.order(config?.connections.orEmpty(), config?.owned ?: true, localAllowed = true)
                .firstOrNull()?.uri
            ?: return null
        val token = token() ?: return null
        return PlexArt.url(base, thumb, sizePx, token)
    }

    /** Starts a background pick (once at a time), so art and the first call find a connection ready. */
    fun warmUp() {
        if (!warming.compareAndSet(false, true)) return
        scope.launch {
            try {
                token()
                picker.current()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing answered yet; the next call probes again.
            } finally {
                warming.set(false)
            }
        }
    }

    // ── Removal ─────────────────────────────────────────────────────────────

    /** Deletes this server's secrets; the account token goes too when this was the last Plex server. */
    suspend fun forget(): Unit = withContext(Dispatchers.IO) {
        credentials.removeAll(PlexKeys.serverPrefix(machineId))
        cachedToken = null
        if (PlexAccountServers.remove(credentials, machineId).isEmpty()) {
            credentials.remove(PlexKeys.ACCOUNT_TOKEN)
            credentials.remove(PlexKeys.ACCOUNT_SERVERS)
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val PLAYLIST_TYPE_AUDIO = "audio"

        /** The one switch for how transcodes are delivered (see [PlexTranscodeProtocol]). */
        val TRANSCODE_PROTOCOL: PlexTranscodeProtocol = PlexTranscodeProtocol.HTTP
    }
}

/** The X-Plex-Session-Identifier of each track's latest resolvePlayback (the last [MAX] tracks). */
internal class PlexSessions {
    private val lock = Any()
    private val byTrack = LinkedHashMap<TrackKey, String>()

    fun start(track: TrackKey): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        byTrack.remove(track)
        byTrack[track] = id
        while (byTrack.size > MAX) byTrack.remove(byTrack.keys.first())
        id
    }

    fun current(track: TrackKey): String? = synchronized(lock) { byTrack[track] }

    private companion object {
        const val MAX = 16
    }
}

/** The machine ids of the Plex servers added with the stored account token (comma-separated in the store). */
internal object PlexAccountServers {
    private val lock = Any()

    fun add(credentials: CredentialStore, machineId: String): Set<String> =
        synchronized(lock) { write(credentials, read(credentials) + machineId) }

    fun remove(credentials: CredentialStore, machineId: String): Set<String> =
        synchronized(lock) { write(credentials, read(credentials) - machineId) }

    private fun read(credentials: CredentialStore): Set<String> =
        credentials.token(PlexKeys.ACCOUNT_SERVERS).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun write(credentials: CredentialStore, ids: Set<String>): Set<String> {
        if (ids.isEmpty()) credentials.remove(PlexKeys.ACCOUNT_SERVERS)
        else credentials.putToken(PlexKeys.ACCOUNT_SERVERS, ids.sorted().joinToString(","))
        return ids
    }
}
