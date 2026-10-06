package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderException
import com.crsmthw.sheliak.data.repository.resultOf
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * A PIN the user is claiming at [authUrl] (open it in a Custom Tab). [expiresAt] is epoch milliseconds, read from
 * Plex's own `expiresAt` (PLEX.md §1: "do not hard-code it"); stop polling there.
 */
data class PinSession(val id: Long, val code: String, val authUrl: String, val expiresAt: Long) {
    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAt
}

/**
 * How Sheliak obtains and checks a plex.tv account token — the seam Cris asked for. M1 implements Plex's PIN →
 * long-lived token flow ([PlexPinAuth]), which Plex now labels "Traditional Token Authentication (Legacy)" and
 * recommends JWT instead (PLEX.md §1, correction 3; workaround register W-1). A JWT implementation (Ed25519 key,
 * 7-day tokens, nonce refresh, HTTP 498) would replace [PlexPinAuth] behind this interface; the rest of the Plex
 * code only ever sees the token string.
 */
interface PlexAuth {

    /** Creates a strong PIN. */
    suspend fun startPin(): Result<PinSession>

    /**
     * One poll (PLEX.md: once per second): the account token once the user claimed the PIN, null while it is
     * pending. Fails with `ProviderException(AUTH)` once the PIN expired or is gone — start a new one.
     */
    suspend fun pollPin(session: PinSession): Result<String?>

    /**
     * Checks a stored account token: true = valid (200), false = refused (401, the ONLY code that invalidates
     * it, PLEX.md correction 4). A failure means "could not check" (offline, plex.tv down, any other status):
     * keep the token.
     */
    suspend fun checkToken(accountToken: String): Result<Boolean>
}

/** [PlexAuth] over plex.tv's v2 PIN endpoints. */
class PlexPinAuth internal constructor(
    private val api: PlexTvApi,
    private val client: () -> PlexClientInfo,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlexAuth {

    override suspend fun startPin(): Result<PinSession> = resultOf {
        val pin = api.createPin(strong = true).bodyOrThrow("create PIN")
        val id = pin.id ?: throw ProviderException(ProviderError.PARSE, "PIN without id")
        val code = pin.code?.takeIf { it.isNotBlank() } ?: throw ProviderException(ProviderError.PARSE, "PIN without code")
        val info = client()
        PinSession(
            id        = id,
            code      = code,
            authUrl   = authUrl(info.clientIdentifier, code, info.product),
            expiresAt = PinExpiry.expiresAtMillis(pin, clock()),
        )
    }

    override suspend fun pollPin(session: PinSession): Result<String?> = resultOf {
        val response = api.pin(session.id)
        if (response.code() == HTTP_NOT_FOUND) throw ProviderException(ProviderError.AUTH, "PIN expired")
        val pin = response.bodyOrThrow("poll PIN")
        val token = pin.authToken?.takeIf { it.isNotBlank() }
        if (token == null && session.isExpired(clock())) throw ProviderException(ProviderError.AUTH, "PIN expired")
        token
    }

    override suspend fun checkToken(accountToken: String): Result<Boolean> = resultOf {
        val response = api.user(accountToken)
        when {
            response.isSuccessful              -> true
            response.code() == HTTP_UNAUTHORIZED -> false
            else                               -> throw PlexErrors.exception(response.code(), "check token")
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404

        /**
         * PLEX.md §1 "Auth URL": parameters in the fragment after `#?`, the brackets of `context[device][product]`
         * percent-encoded (correction 1). No `forwardUrl`: a native app polls.
         */
        fun authUrl(clientIdentifier: String, code: String, product: String): String =
            "https://app.plex.tv/auth#?clientID=${encode(clientIdentifier)}&code=${encode(code)}" +
                "&context%5Bdevice%5D%5Bproduct%5D=${encode(product)}"

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    }
}

/** When a PIN dies, from whatever plex.tv gave: `expiresAt` (ISO-8601 or epoch seconds), else `expiresIn`. */
object PinExpiry {
    /** Used only when plex.tv gives neither `expiresAt` nor `expiresIn` — a guess, so it is short. */
    const val FALLBACK_LIFETIME_MS: Long = 5 * 60 * 1000L
    private const val EPOCH_SECONDS_LIMIT = 100_000_000_000L

    fun expiresAtMillis(pin: PlexPin, nowMs: Long): Long =
        parseInstantMillis(pin.expiresAt)
            ?: pin.expiresIn?.let { seconds ->
                val created = parseInstantMillis(pin.createdAt) ?: nowMs
                created + seconds * 1000
            }
            ?: (nowMs + FALLBACK_LIFETIME_MS)

    /** An ISO-8601 instant (with Z or an offset), or epoch seconds / milliseconds as text; null when neither. */
    fun parseInstantMillis(text: String?): Long? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toLongOrNull()?.let { n -> return if (n < EPOCH_SECONDS_LIMIT) n * 1000 else n }
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

/**
 * The account's Plex Media Servers (`/resources`, PLEX.md §2): only devices that provide "server". A 401 means
 * the account token is no longer valid (AUTH).
 */
internal suspend fun PlexTvApi.servers(accountToken: String): List<PlexResource> =
    resources(accountToken, includeHttps = 1, includeRelay = 1, includeIPv6 = 1)
        .bodyOrThrow("resources")
        .filter { it.isServer && !it.clientIdentifier.isNullOrBlank() }
