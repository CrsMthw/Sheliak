package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.IOException
import java.util.Locale

/**
 * One way to reach a server, from `/resources` `connections[]` (PLEX.md §2): a LAN address, the owner's custom
 * access URL, a `*.plex.direct` name, or Plex's relay (2 Mbps). Every kind is first class. Persisted in the
 * instance config at setup (so the provider can connect without plex.tv); the PICK is never persisted.
 */
@Serializable
data class PlexConnectionInfo(
    val uri: String,
    val protocol: String? = null,
    val local: Boolean = false,
    val relay: Boolean = false,
    val ipv6: Boolean = false,
) {
    enum class Kind { LOCAL, REMOTE, RELAY }

    val kind: Kind
        get() = when {
            relay -> Kind.RELAY
            local -> Kind.LOCAL
            else  -> Kind.REMOTE
        }

    val https: Boolean
        get() = protocol?.lowercase(Locale.ROOT) == "https" || uri.lowercase(Locale.ROOT).startsWith("https://")

    companion object {
        /** Null when Plex gave no `uri` (it is used verbatim, never rebuilt). */
        fun from(c: PlexConnection): PlexConnectionInfo? = c.uri?.trim()?.takeIf { it.isNotEmpty() }?.let {
            PlexConnectionInfo(
                uri      = it,
                protocol = c.protocol,
                local    = c.local == true,
                relay    = c.relay == true,
                ipv6     = c.ipv6 == true,
            )
        }
    }
}

/** What a probe of one connection found. */
enum class PlexProbeResult {
    /** `/identity` named the expected server and an authenticated call succeeded. */
    OK,
    /** No answer, a timeout, TLS or cleartext refusal, or a different server at that address. */
    UNREACHABLE,
    /** The right server, but it refused the token (401/403). */
    UNAUTHORIZED,
}

/** Probes one connection; see [PlexHttpProber]. Exceptions count as [PlexProbeResult.UNREACHABLE]. */
fun interface PlexProber {
    suspend fun probe(connection: PlexConnectionInfo): PlexProbeResult
}

/** How long each kind may take to answer before the next one in order is considered. */
data class PlexProbeTimeouts(
    val localMs: Long = 3_000,
    val remoteMs: Long = 6_000,
    val relayMs: Long = 10_000,
) {
    fun forKind(kind: PlexConnectionInfo.Kind): Long = when (kind) {
        PlexConnectionInfo.Kind.LOCAL  -> localMs
        PlexConnectionInfo.Kind.REMOTE -> remoteMs
        PlexConnectionInfo.Kind.RELAY  -> relayMs
    }
}

/** The outcome of probing a list: the connection to use, and whether any server refused the token. */
data class PlexPick(val connection: PlexConnectionInfo?, val unauthorized: Boolean)

/**
 * Picks the best working connection for one server and keeps it IN MEMORY: until the network changes
 * ([networkGeneration] moves), a call through it fails ([invalidate]), or the candidates are replaced. Order:
 * local → remote → relay, https before http, IPv4 before IPv6 (PLEX.md §2 "Ordering", correction 7); local
 * connections are skipped on servers the user does not own (someone else's LAN) and while Android 17's
 * local-network permission is missing ([localNetworkAllowed]: a blocked LAN connect times out instead of failing).
 *
 * When nothing answers, [refresh] may supply fresh connections (a re-fetch of `/resources`); if still nothing
 * answers the call fails with NETWORK, or AUTH when a server answered but refused the token.
 */
class PlexConnectionPicker(
    private val owned: Boolean,
    connections: List<PlexConnectionInfo>,
    private val prober: PlexProber,
    private val localNetworkAllowed: () -> Boolean = { true },
    private val networkGeneration: () -> Long = { 0L },
    private val refresh: suspend () -> List<PlexConnectionInfo>? = { null },
    private val timeouts: PlexProbeTimeouts = PlexProbeTimeouts(),
) {
    private data class Picked(val connection: PlexConnectionInfo, val generation: Long)

    private val mutex = Mutex()

    @Volatile
    private var candidates: List<PlexConnectionInfo> = connections

    @Volatile
    private var picked: Picked? = null

    /** The pick, if it is still valid for the current network; never probes. */
    fun currentOrNull(): PlexConnectionInfo? =
        picked?.takeIf { it.generation == networkGeneration() }?.connection

    /** The last pick even if the network changed since — a best guess for non-suspending callers (art). */
    fun lastPicked(): PlexConnectionInfo? = picked?.connection

    /** The connection to use now, probing if there is no valid pick. */
    suspend fun current(): PlexConnectionInfo {
        currentOrNull()?.let { return it }
        return mutex.withLock {
            currentOrNull() ?: pick()
        }
    }

    /** A call through [connection] failed at the network level: the next [current] probes again. */
    fun invalidate(connection: PlexConnectionInfo) {
        if (picked?.connection == connection) picked = null
    }

    /** New connections from `/resources`: forget the pick, probe these next time. */
    fun replaceCandidates(connections: List<PlexConnectionInfo>) {
        if (connections.isEmpty()) return
        candidates = connections
        picked = null
    }

    private suspend fun pick(): PlexConnectionInfo {
        val generation = networkGeneration()
        var result = choose(order(candidates, owned, localNetworkAllowed()), prober, timeouts)
        if (result.connection == null) {
            val fresh = try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null   // plex.tv unreachable or the account token gone: report the server's own state below
            }
            if (!fresh.isNullOrEmpty()) {
                candidates = fresh
                val again = choose(order(fresh, owned, localNetworkAllowed()), prober, timeouts)
                result = PlexPick(again.connection, again.unauthorized || result.unauthorized)
            }
        }
        val found = result.connection ?: throw if (result.unauthorized) {
            ProviderException(ProviderError.AUTH, "The Plex server refused its token")
        } else {
            ProviderException(ProviderError.NETWORK, "No connection to the Plex server answered")
        }
        picked = Picked(found, generation)
        return found
    }

    companion object {

        /**
         * The connections worth probing, best first: blank and duplicate URIs dropped, local ones dropped unless
         * the user [owned] the server AND [localAllowed]; then local → remote → relay, https → http,
         * IPv4 → IPv6, and Plex's own order within a tie.
         */
        fun order(
            connections: List<PlexConnectionInfo>,
            owned: Boolean,
            localAllowed: Boolean,
        ): List<PlexConnectionInfo> =
            connections
                .filter { it.uri.isNotBlank() }
                .filter { it.kind != PlexConnectionInfo.Kind.LOCAL || (owned && localAllowed) }
                .distinctBy { it.uri.trimEnd('/') }
                .sortedWith(
                    compareBy<PlexConnectionInfo> { it.kind.ordinal }
                        .thenBy { if (it.https) 0 else 1 }
                        .thenBy { if (it.ipv6) 1 else 0 },
                )

        /**
         * Probes every connection of [ordered] at once and returns the FIRST in order that answers OK — a
         * lower-ranked one that answers sooner waits for the better ones to fail or time out (per-kind
         * [timeouts]). The remaining probes are cancelled as soon as the answer is known.
         */
        suspend fun choose(
            ordered: List<PlexConnectionInfo>,
            prober: PlexProber,
            timeouts: PlexProbeTimeouts = PlexProbeTimeouts(),
        ): PlexPick = coroutineScope {
            val probes = ordered.map { connection ->
                async {
                    withTimeoutOrNull(timeouts.forKind(connection.kind)) { probeSafely(prober, connection) }
                        ?: PlexProbeResult.UNREACHABLE
                }
            }
            var unauthorized = false
            try {
                for ((i, probe) in probes.withIndex()) {
                    when (probe.await()) {
                        PlexProbeResult.OK           -> return@coroutineScope PlexPick(ordered[i], unauthorized)
                        PlexProbeResult.UNAUTHORIZED -> unauthorized = true
                        PlexProbeResult.UNREACHABLE  -> Unit
                    }
                }
                PlexPick(null, unauthorized)
            } finally {
                probes.forEach { it.cancel() }
            }
        }

        private suspend fun probeSafely(prober: PlexProber, connection: PlexConnectionInfo): PlexProbeResult =
            try {
                prober.probe(connection)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PlexProbeResult.UNREACHABLE
            }
    }
}

/**
 * The real probe (PLEX.md §2 "Probe", correction 7): `GET {uri}/identity` WITHOUT a token, which must name
 * [machineId] — an address from a stale list may now be somebody else's server, and it must not see our token —
 * then one authenticated call (`/library/sections`) with the server's own access token.
 */
internal class PlexHttpProber(
    private val machineId: String,
    private val token: () -> String?,
    private val http: PlexHttp,
) : PlexProber {

    override suspend fun probe(connection: PlexConnectionInfo): PlexProbeResult {
        val identity = try {
            http.unauthenticatedProbe(connection.uri).identity()
        } catch (_: IOException) {
            return PlexProbeResult.UNREACHABLE
        }
        if (!identity.isSuccessful) return PlexProbeResult.UNREACHABLE
        if (identity.body()?.mediaContainer?.machineIdentifier != machineId) return PlexProbeResult.UNREACHABLE
        val accessToken = token() ?: return PlexProbeResult.UNAUTHORIZED
        val sections = try {
            http.authenticatedProbe(connection.uri, accessToken).sections()
        } catch (_: IOException) {
            return PlexProbeResult.UNREACHABLE
        }
        return when {
            sections.isSuccessful                       -> PlexProbeResult.OK
            sections.code() == 401 || sections.code() == 403 -> PlexProbeResult.UNAUTHORIZED
            else                                        -> PlexProbeResult.UNREACHABLE
        }
    }
}
