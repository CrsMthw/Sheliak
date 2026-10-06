package com.crsmthw.sheliak.data.provider.plex

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.crsmthw.sheliak.BuildConfig
import com.crsmthw.sheliak.data.provider.CredentialStore
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Header names (PLEX.md "Rules for every request", §1, §3, §5, §7). */
object PlexHeaders {
    const val ACCEPT = "Accept"
    const val JSON = "application/json"
    const val TOKEN = "X-Plex-Token"
    const val CLIENT_IDENTIFIER = "X-Plex-Client-Identifier"
    const val PRODUCT = "X-Plex-Product"
    const val VERSION = "X-Plex-Version"
    const val PLATFORM = "X-Plex-Platform"
    const val PLATFORM_VERSION = "X-Plex-Platform-Version"
    const val DEVICE = "X-Plex-Device"
    const val MODEL = "X-Plex-Model"
    const val DEVICE_VENDOR = "X-Plex-Device-Vendor"
    const val DEVICE_NAME = "X-Plex-Device-Name"
    const val SESSION_IDENTIFIER = "X-Plex-Session-Identifier"
    const val CLIENT_PROFILE_NAME = "X-Plex-Client-Profile-Name"
    const val CLIENT_PROFILE_EXTRA = "X-Plex-Client-Profile-Extra"
    const val CONTAINER_START = "X-Plex-Container-Start"
    const val CONTAINER_SIZE = "X-Plex-Container-Size"
    const val CONTAINER_TOTAL_SIZE = "X-Plex-Container-Total-Size"
}

/**
 * What Plex is told about this client. [clientIdentifier] is generated once and reused forever (PLEX.md §1);
 * [product] is what the user's Authorized Devices list shows. [device] / [deviceName] may be non-ASCII (a
 * renamed phone, "Cris’s Fold"), which OkHttp's normal header API rejects — see [PlexHeadersInterceptor].
 */
data class PlexClientInfo(
    val clientIdentifier: String,
    val product: String = PRODUCT_NAME,
    val version: String,
    val platform: String = PLATFORM_NAME,
    val platformVersion: String,
    /** "a relatively friendly name for the client device". */
    val device: String,
    /** "a potentially less friendly identifier for the device model", e.g. SM-F971B (PLEX.md correction 2). */
    val model: String,
    val vendor: String,
    /** "a friendly name for the client". */
    val deviceName: String,
) {
    /** Every header a plex.tv or PMS request carries, in order (no token). */
    fun headers(): List<Pair<String, String>> = listOf(
        PlexHeaders.ACCEPT to PlexHeaders.JSON,
        PlexHeaders.PRODUCT to product,
        PlexHeaders.VERSION to version,
        PlexHeaders.CLIENT_IDENTIFIER to clientIdentifier,
        PlexHeaders.PLATFORM to platform,
        PlexHeaders.PLATFORM_VERSION to platformVersion,
        PlexHeaders.DEVICE to device,
        PlexHeaders.MODEL to model,
        PlexHeaders.DEVICE_VENDOR to vendor,
        PlexHeaders.DEVICE_NAME to deviceName,
    )

    /** The X-Plex-* identity as query parameters (every X-Plex-* header may be one, PLEX.md), for media URLs. */
    fun queryParameters(): List<Pair<String, String>> = headers().filter { it.first != PlexHeaders.ACCEPT }

    companion object {
        /** A protocol identifier Plex groups devices by, not app UI text. */
        const val PRODUCT_NAME = "Sheliak"
        const val PLATFORM_NAME = "Android"
    }
}

/**
 * Adds the client headers to every request and, when [token] gives one and the call did not set its own,
 * `X-Plex-Token`. Values are added with `addUnsafeNonAscii` (sent as UTF-8, as PLEX.md asks) after control
 * characters are stripped, so a device name can neither crash a request nor inject a header.
 */
class PlexHeadersInterceptor(
    private val info: () -> PlexClientInfo,
    private val token: () -> String? = { null },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val headers = request.headers.newBuilder()
        info().headers().forEach { (name, value) -> headers.setSafe(name, value) }
        if (request.header(PlexHeaders.TOKEN) == null) token()?.let { headers.setSafe(PlexHeaders.TOKEN, it) }
        return chain.proceed(request.newBuilder().headers(headers.build()).build())
    }

    companion object {
        /** Replaces [name] with [value] minus control characters; non-ASCII is kept and sent as UTF-8. */
        fun Headers.Builder.setSafe(name: String, value: String): Headers.Builder {
            removeAll(name)
            return addUnsafeNonAscii(name, sanitize(value))
        }

        fun sanitize(value: String): String = value.filterNot { it.isISOControl() }.trim()
    }
}

/** CredentialStore keys. Per-server secrets live under [serverPrefix] so `removeAll` takes exactly one server's. */
object PlexKeys {
    const val ACCOUNT_TOKEN = "plex:account:token"
    /** Comma-separated machine ids of the Plex servers added, so the last removal also signs the account out. */
    const val ACCOUNT_SERVERS = "plex:account:servers"
    /** Not a secret, but it must survive every server's removal: Plex wants one id per install, forever. */
    const val CLIENT_ID = "plex:client:id"

    fun serverPrefix(machineId: String): String = "plex:$machineId:"
    fun serverToken(machineId: String): String = "plex:$machineId:token"
}

/** The install's X-Plex-Client-Identifier: created once, then always the same. */
internal object PlexClientIdentity {
    private val lock = Any()

    fun get(credentials: CredentialStore): String = synchronized(lock) {
        credentials.token(PlexKeys.CLIENT_ID)
            ?: UUID.randomUUID().toString().also { credentials.putToken(PlexKeys.CLIENT_ID, it) }
    }
}

/** [PlexClientInfo] for this device. Android-only (Build, Settings); everything after it takes the data class. */
internal object PlexDevice {
    fun clientInfo(context: Context, clientIdentifier: String): PlexClientInfo {
        val model = Build.MODEL.orEmpty()
        val userName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
        return PlexClientInfo(
            clientIdentifier = clientIdentifier,
            version          = BuildConfig.VERSION_NAME,
            platformVersion  = Build.VERSION.RELEASE.orEmpty(),
            device           = userName ?: model,
            model            = model,
            vendor           = Build.MANUFACTURER.orEmpty(),
            deviceName       = userName ?: model,
        )
    }
}

/** HTTP status → the app's error kinds (PLEX.md §11 "Status codes"). */
object PlexErrors {
    fun forStatus(code: Int): ProviderError = when (code) {
        401, 403    -> ProviderError.AUTH
        404         -> ProviderError.UNAVAILABLE
        in 500..599 -> ProviderError.SERVER
        else        -> ProviderError.UNKNOWN
    }

    fun exception(code: Int, what: String): ProviderException =
        ProviderException(forStatus(code), "$what: HTTP $code")
}

/** The body of a successful response, or the matching [ProviderException]. */
internal fun <T : Any> Response<T>.bodyOrThrow(what: String): T {
    if (!isSuccessful) throw PlexErrors.exception(code(), what)
    return body() ?: throw ProviderException(ProviderError.PARSE, "$what: empty body")
}

/** Throws the matching [ProviderException] unless the response is a success (for bodiless calls). */
internal fun Response<*>.requireSuccess(what: String) {
    if (!isSuccessful) throw PlexErrors.exception(code(), what)
}

/**
 * The OkHttp clients and Retrofit services the Plex code uses, all derived from the app's base client (one
 * connection pool). No logging interceptor: it would write tokens to the log.
 */
internal class PlexHttp(
    base: OkHttpClient,
    private val json: Json,
    info: () -> PlexClientInfo,
) {
    /** plex.tv: client headers only; the account token is passed per call. */
    private val tvClient: OkHttpClient = base.newBuilder()
        .addInterceptor(PlexHeadersInterceptor(info))
        .build()

    /** Probes: short timeouts, and NO token until the server proved it is the one we expect. */
    private val probeClient: OkHttpClient = base.newBuilder()
        .connectTimeout(PROBE_CONNECT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_READ_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(PlexHeadersInterceptor(info))
        .build()

    private val serverBase: OkHttpClient = base.newBuilder()
        .readTimeout(SERVER_READ_SECONDS, TimeUnit.SECONDS)
        .build()

    private val infoProvider = info

    val tv: PlexTvApi by lazy { PlexRetrofit.tv(tvClient, json) }

    /** A client for one server that adds its token to every call. */
    fun serverClient(token: () -> String?): OkHttpClient =
        serverBase.newBuilder().addInterceptor(PlexHeadersInterceptor(infoProvider, token)).build()

    fun server(baseUri: String, client: OkHttpClient): PlexServerApi = PlexRetrofit.server(baseUri, client, json)

    /** For `/identity`: no token. */
    fun unauthenticatedProbe(baseUri: String): PlexServerApi = PlexRetrofit.server(baseUri, probeClient, json)

    /** For the probe's one authenticated call: probe timeouts plus [token]. */
    fun authenticatedProbe(baseUri: String, token: String): PlexServerApi = PlexRetrofit.server(
        baseUri,
        probeClient.newBuilder().addInterceptor(PlexHeadersInterceptor(infoProvider) { token }).build(),
        json,
    )

    private companion object {
        const val PROBE_CONNECT_SECONDS = 5L
        const val PROBE_READ_SECONDS = 8L
        /** A metadata batch or a 1000-item page from a busy server can take a while to start answering. */
        const val SERVER_READ_SECONDS = 30L
    }
}
