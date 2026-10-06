package com.crsmthw.sheliak.data.provider.plex

import android.content.Context
import com.crsmthw.sheliak.data.provider.CredentialStore
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.provider.ProviderInstance
import com.crsmthw.sheliak.data.repository.resultOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A Plex server the signed-in account can use (`/resources`, PLEX.md §2). [name] and [ownerName] are user data
 * (shown as is). [accessToken] is this server's own token: kept in memory for setup, written to the
 * CredentialStore by [PlexSetup.complete], never shown and never logged (toString redacts it).
 */
data class PlexServerCandidate(
    val machineId: String,
    val name: String,
    val owned: Boolean,
    /** The owner's name on a shared server (`sourceTitle`); null when owned. */
    val ownerName: String?,
    val productVersion: String?,
    /** plex.tv's last word on whether the server is online. */
    val online: Boolean?,
    val connections: List<PlexConnectionInfo>,
    val accessToken: String,
) {
    /** True when the server has LAN connections Sheliak would use (owned servers only). */
    val hasLocalConnections: Boolean
        get() = owned && connections.any { it.kind == PlexConnectionInfo.Kind.LOCAL }

    override fun toString(): String =
        "PlexServerCandidate(machineId=$machineId, name=$name, owned=$owned, connections=${connections.size})"
}

/**
 * The Plex sign-in and setup flow the UI drives (lane L4). Build it from AppContainer's members:
 * `PlexSetup(context, container.credentialStore, container.httpClient, container.json)`.
 *
 * 1. [startPin] → open [PinSession.authUrl] in a Custom Tab; [awaitPin] (or [pollPin] once a second) until it
 *    yields the account token. Skip both when [storedAccountToken] has one (a second server).
 * 2. [listServers] with that token → the user picks a [PlexServerCandidate].
 * 3. If [localNetworkPermission] returns a permission, request it before step 4 (Android 17 blocks LAN access
 *    without it; a denial only means LAN connections are skipped).
 * 4. [listMusicLibraries] → the user picks libraries.
 * 5. [complete] stores the tokens and returns the [ProviderInstance]; hand it to `SourcesRepository.add`.
 *
 * Later: [libraries] reads an instance's picks, [withLibraries] changes them (then
 * `SourcesRepository.update(instance, resync = true)`).
 *
 * Every call returns a [Result]; a failure carries a `ProviderException` whose [ProviderError] the UI maps to
 * its own strings (AUTH on the PIN = it expired, start again; NETWORK = no connection answered).
 */
class PlexSetup(
    private val context: Context,
    private val credentials: CredentialStore,
    httpClient: OkHttpClient,
    json: Json,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val clientInfo: PlexClientInfo by lazy {
        PlexDevice.clientInfo(context, PlexClientIdentity.get(credentials))
    }

    private val http: PlexHttp by lazy { PlexHttp(httpClient, json) { clientInfo } }

    /** The account sign-in seam (PIN + long-lived token in M1). */
    val auth: PlexAuth by lazy { PlexPinAuth(http.tv, { clientInfo }, clock) }

    /** The connection each server answered on during [listMusicLibraries]: the instance's initial baseUrl. */
    private val answered = ConcurrentHashMap<String, String>()

    suspend fun startPin(): Result<PinSession> = withContext(Dispatchers.IO) { auth.startPin() }

    /** One poll: the account token, or null while the user has not claimed the PIN yet. */
    suspend fun pollPin(session: PinSession): Result<String?> = auth.pollPin(session)

    /**
     * Polls every [intervalMs] (PLEX.md: once per second) until the token arrives or the PIN expires. A failed
     * poll that is not AUTH (a Wi-Fi blip, plex.tv rate-limiting) is ignored and polling goes on — UI code that
     * calls [pollPin] itself should follow the same rule: only AUTH ends the wait (start a new PIN).
     */
    suspend fun awaitPin(session: PinSession, intervalMs: Long = PIN_POLL_INTERVAL_MS): Result<String> {
        while (true) {
            val polled = pollPin(session)
            val token = polled.getOrNull()
            if (token != null) return Result.success(token)
            val error = polled.exceptionOrNull()
            if (error is ProviderException && error.error == ProviderError.AUTH) return Result.failure(error)
            if (session.isExpired(clock())) {
                return Result.failure(ProviderException(ProviderError.AUTH, "PIN expired"))
            }
            delay(intervalMs)
        }
    }

    /** The account token stored by an earlier [complete], if any (adding another server needs no new PIN). */
    suspend fun storedAccountToken(): String? = withContext(Dispatchers.IO) { credentials.token(PlexKeys.ACCOUNT_TOKEN) }

    /** true = valid, false = refused (sign in again); a failure = could not check (keep it). */
    suspend fun checkAccountToken(accountToken: String): Result<Boolean> = auth.checkToken(accountToken)

    /** The account's servers, owned ones first, then by name. */
    suspend fun listServers(accountToken: String): Result<List<PlexServerCandidate>> = resultOf {
        http.tv.servers(accountToken).mapNotNull(::candidate)
            .sortedWith(compareBy<PlexServerCandidate> { !it.owned }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    /**
     * The runtime permission to request before probing [server] (Android 17's `ACCESS_LOCAL_NETWORK`), or null
     * when it is not needed: below Android 17, already granted, or the server has no LAN connection Sheliak
     * would use. With no [server], the answer for Plex in general.
     */
    fun localNetworkPermission(server: PlexServerCandidate? = null): String? {
        val permission = PlexLocalNetwork.permission() ?: return null
        if (server != null && !server.hasLocalConnections) return null
        return permission.takeUnless { PlexLocalNetwork.granted(context) }
    }

    /**
     * The music libraries (`type == "artist"`) of [server], reached through the best connection that answers
     * (probed as at run time). [token] is the server's own access token.
     */
    suspend fun listMusicLibraries(
        server: PlexServerCandidate,
        token: String = server.accessToken,
    ): Result<List<PlexLibrary>> = resultOf {
        val prober = PlexHttpProber(server.machineId, { token }, http)
        val ordered = PlexConnectionPicker.order(server.connections, server.owned, PlexLocalNetwork.granted(context))
        val pick = PlexConnectionPicker.choose(ordered, prober)
        val connection = pick.connection ?: throw if (pick.unauthorized) {
            ProviderException(ProviderError.AUTH, "The Plex server refused its token")
        } else {
            ProviderException(ProviderError.NETWORK, "No connection to the Plex server answered")
        }
        answered[server.machineId] = connection.uri
        val api = http.server(connection.uri, http.serverClient { token })
        val sections = api.sections().let { response ->
            if (response.code() == HTTP_NOT_FOUND) api.sectionsAll() else response
        }.bodyOrThrow("sections")
        sections.mediaContainer?.directories.orEmpty()
            .filter { it.type == PlexTypes.SECTION_MUSIC && !it.key.isNullOrBlank() }
            .map { PlexLibrary(key = it.key.orEmpty(), title = it.title.orEmpty()) }
    }

    /**
     * Stores the account token and [server]'s access token, and returns the instance to add with
     * `SourcesRepository.add`: id `plex:<machineIdentifier>`, type "plex", the server's name, the connection
     * that answered during setup as baseUrl, and the config (picked [libraries], ownership, connections).
     */
    suspend fun complete(
        accountToken: String,
        server: PlexServerCandidate,
        libraries: List<PlexLibrary>,
    ): Result<ProviderInstance> = resultOf {
        withContext(Dispatchers.IO) {
            credentials.putToken(PlexKeys.ACCOUNT_TOKEN, accountToken)
            credentials.putToken(PlexKeys.serverToken(server.machineId), server.accessToken)
            PlexAccountServers.add(credentials, server.machineId)
        }
        ProviderInstance(
            id          = PlexProvider.instanceId(server.machineId),
            type        = PlexProvider.TYPE,
            displayName = server.name,
            baseUrl     = answered[server.machineId],
            serverId    = server.machineId,
            config      = PlexInstanceConfig(
                libraries   = libraries,
                owned       = server.owned,
                connections = server.connections,
            ).encode(),
            enabled     = true,
        )
    }

    /** The libraries picked for a Plex [instance] (empty when it has no readable config). */
    fun libraries(instance: ProviderInstance): List<PlexLibrary> =
        PlexInstanceConfig.decode(instance.config)?.libraries.orEmpty()

    /** [instance] with [libraries] picked instead; follow with `SourcesRepository.update(it, resync = true)`. */
    fun withLibraries(instance: ProviderInstance, libraries: List<PlexLibrary>): ProviderInstance {
        val config = PlexInstanceConfig.decode(instance.config) ?: PlexInstanceConfig()
        return instance.copy(config = config.copy(libraries = libraries).encode())
    }

    private fun candidate(resource: PlexResource): PlexServerCandidate? {
        val machineId = resource.clientIdentifier?.takeIf { it.isNotBlank() } ?: return null
        val token = resource.accessToken?.takeIf { it.isNotBlank() } ?: return null
        return PlexServerCandidate(
            machineId      = machineId,
            name           = resource.name.orEmpty(),
            owned          = resource.owned == true,
            ownerName      = resource.sourceTitle?.takeIf { it.isNotBlank() },
            productVersion = resource.productVersion,
            online         = resource.presence,
            connections    = resource.connections.orEmpty().mapNotNull(PlexConnectionInfo::from),
            accessToken    = token,
        )
    }

    companion object {
        /** PLEX.md §1: poll "once per second". */
        const val PIN_POLL_INTERVAL_MS: Long = 1_000
        private const val HTTP_NOT_FOUND = 404
    }
}
