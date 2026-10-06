@file:OptIn(UnstableApi::class)

package com.crsmthw.sheliak.service

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.crsmthw.sheliak.data.provider.PlaybackPrefs
import com.crsmthw.sheliak.data.provider.PlaybackSource
import com.crsmthw.sheliak.data.provider.ProviderError
import com.crsmthw.sheliak.data.provider.ProviderRegistry
import com.crsmthw.sheliak.data.repository.SettingsRepository
import com.crsmthw.sheliak.data.repository.resultOf
import com.crsmthw.sheliak.domain.TrackKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Where the player's bytes come from. A playable MediaItem carries a [StreamUri] (`sheliak://track?id=…`); when
 * ExoPlayer opens it, the resolver asks the track's provider for a [PlaybackSource] and swaps in its URI and
 * headers. Everything else passes through untouched to [DefaultDataSource], which reads content:// and file URIs
 * itself and hands http(s) to an [OkHttpDataSource] derived from the app's one client (shared connection pool).
 *
 * The resolver runs on ExoPlayer's loader thread, where blocking is allowed, so it bridges into the suspending
 * provider API with `runBlocking`. A resolved source is kept for [RESOLVED_TTL_MS] per media id — ExoPlayer
 * re-opens a source on every seek and retry, and a transcode must not start a new server session each time — and
 * dropped as soon as opening it fails, so the retry resolves afresh (a new connection after a network change).
 *
 * A direct play the server refuses at the start of the stream with 503 / 509 ([TranscodeFallback]) marks the item
 * "force transcode": ExoPlayer's load retry resolves it again, now with `PlaybackPrefs(forceTranscode = true)`.
 * The mark lasts [RESOLVED_TTL_MS], or until the item leaves the player ([retainForcedTranscodes]).
 */
class ProviderDataSourceFactory(
    context: Context,
    httpClient: OkHttpClient,
    private val providers: ProviderRegistry,
    private val settings: SettingsRepository,
) : DataSource.Factory {

    private val streamClient: OkHttpClient = httpClient.newBuilder().build()

    /** The codecs this device decodes (DirectPlayCodecs), built from MediaCodecList on the first resolve. */
    private val directPlayCodecs: Set<String> by lazy { DirectPlayCodecs.detect() }

    private val resolved = ExpiringCache<String, PlaybackSource>(
        maxEntries = RESOLVED_MAX_ENTRIES,
        ttlMs      = RESOLVED_TTL_MS,
        clock      = SystemClock::elapsedRealtime,
    )

    /** Media ids whose direct play the server refused (503 / 509): resolved as transcodes until the mark goes. */
    private val forced = ExpiringCache<String, Unit>(
        maxEntries = FORCED_MAX_ENTRIES,
        ttlMs      = RESOLVED_TTL_MS,
        clock      = SystemClock::elapsedRealtime,
    )

    private val resolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val key = StreamUri.keyOf(dataSpec.uri.toString()) ?: return dataSpec
            val source = resolve(key)
            return dataSpec.withUri(source.uri.toUri()).withAdditionalHeaders(source.headers)
        }
    }

    private val upstream: DataSource.Factory = ResolvingDataSource.Factory(
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(streamClient)),
        resolver,
    )

    override fun createDataSource(): DataSource = InvalidatingDataSource(upstream.createDataSource())

    /** True while some item is marked "force transcode" (so a playlist change has marks to prune). */
    fun hasForcedTranscodes(): Boolean = !forced.isEmpty()

    /** Forgets the "force transcode" mark of every item not in [queued] (the player's media ids). */
    fun retainForcedTranscodes(queued: Set<String>) = forced.retainKeys { it in queued }

    private fun resolve(key: TrackKey): PlaybackSource {
        resolved[key.mediaId]?.let { return it }
        val result = try {
            runBlocking {
                resultOf {
                    // current() is null in a process just started for playback resumption, before the registry's
                    // first build: provider() builds from the row on demand.
                    val provider = providers.current(key.providerId)
                        ?: providers.provider(key.providerId)
                        ?: throw FileNotFoundException("No provider ${key.providerId}")
                    val prefs = PlaybackPrefs(
                        directPlayCodecs     = directPlayCodecs,
                        transcodeBitrateKbps = settings.transcodeBitrateKbps.first(),
                        forceTranscode       = forced[key.mediaId] != null,
                    )
                    provider.resolvePlayback(key, prefs).getOrThrow()
                }
            }
        } catch (e: InterruptedException) {
            // The loader was cancelled while waiting (a skip, a release): ExoPlayer expects an interrupted IO.
            throw InterruptedIOException().apply { initCause(e) }
        }
        val source = result.getOrElse { throw asLoadError(key, it) }
        resolved[key.mediaId] = source
        return source
    }

    /** A failure ExoPlayer should not retry (no such provider, auth, parse) becomes a FileNotFoundException. */
    private fun asLoadError(key: TrackKey, t: Throwable): IOException {
        Log.w(TAG, "Could not resolve ${key.mediaId}", t)
        if (t is FileNotFoundException) return t
        return if (ProviderError.of(t).retryable) {
            IOException("Could not resolve ${key.mediaId}", t)
        } else {
            FileNotFoundException("Could not resolve ${key.mediaId}").apply { initCause(t) }
        }
    }

    /**
     * A failed open of [key] at [position]: the resolved source is forgotten, and a direct play the server refused
     * with 503 / 509 marks the item so the retry's resolve asks for a transcode.
     */
    private fun onOpenFailed(key: TrackKey, position: Long, error: IOException) {
        val failed = resolved.remove(key.mediaId)
        val responseCode = responseCodeOf(error)
        if (TranscodeFallback.shouldForceTranscode(responseCode, failed?.isTranscode, position)) {
            Log.i(TAG, "Direct play of ${key.mediaId} refused with HTTP $responseCode; transcoding it")
            forced[key.mediaId] = Unit
        }
    }

    /** The HTTP status of the first [HttpDataSource.InvalidResponseCodeException] in [error]'s causes, if any. */
    private fun responseCodeOf(error: Throwable): Int? =
        generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode

    /** Forwards to the resolving source, and handles a failed open ([onOpenFailed]). */
    private inner class InvalidatingDataSource(private val delegate: DataSource) : DataSource {

        override fun addTransferListener(transferListener: TransferListener) =
            delegate.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long = try {
            delegate.open(dataSpec)
        } catch (e: IOException) {
            StreamUri.keyOf(dataSpec.uri.toString())?.let { onOpenFailed(it, dataSpec.position, e) }
            throw e
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

        override fun getUri(): Uri? = delegate.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

        override fun close() = delegate.close()
    }

    private companion object {
        const val TAG = "ProviderDataSource"
        const val RESOLVED_TTL_MS: Long = 10 * 60_000
        const val RESOLVED_MAX_ENTRIES: Int = 16
        const val FORCED_MAX_ENTRIES: Int = 64
        const val MAX_CAUSE_DEPTH: Int = 8
    }
}

/**
 * A small LRU map whose entries also expire [ttlMs] after they were put. Thread-safe (the player's loader threads
 * share it). Pure; tested in ExpiringCacheTest.
 */
class ExpiringCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val clock: () -> Long,
) {
    private class Entry<V>(val value: V, val putAt: Long)

    private val map = LinkedHashMap<K, Entry<V>>(maxEntries * 2, 0.75f, true)

    @Synchronized
    operator fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (clock() - entry.putAt >= ttlMs) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    operator fun set(key: K, value: V) {
        map[key] = Entry(value, clock())
        val iterator = map.keys.iterator()
        while (map.size > maxEntries && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    /** Removes [key]; returns the value it held (null when none, or when it had expired). */
    @Synchronized
    fun remove(key: K): V? {
        val entry = map.remove(key) ?: return null
        return entry.value.takeIf { clock() - entry.putAt < ttlMs }
    }

    /** Removes every entry whose key [keep] rejects. */
    @Synchronized
    fun retainKeys(keep: (K) -> Boolean) {
        map.keys.retainAll(keep)
    }

    /** True when no entry is held (an expired one not yet dropped still counts). */
    @Synchronized
    fun isEmpty(): Boolean = map.isEmpty()
}
