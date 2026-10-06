package com.crsmthw.sheliak.data.provider.plex

import com.crsmthw.sheliak.data.provider.PlaybackReporter
import com.crsmthw.sheliak.data.provider.ReportedState
import com.crsmthw.sheliak.data.repository.resultOf
import com.crsmthw.sheliak.domain.TrackKey

/**
 * Play reporting (PLEX.md §7): `POST /:/timeline` and `PUT /:/scrobble`. WHEN to call is the playback service's
 * rule (L3: on every state change, and every [INTERVAL_MS] while playing — [INTERVAL_CELLULAR_MS] on cellular —
 * scrobble at 90 %); this class only makes the calls. The timeline carries the `X-Plex-Session-Identifier` the
 * track's last resolvePlayback used, so the server can tie the report to a transcode session.
 *
 * Which call moves `viewCount` / `lastViewedAt` is undocumented (PLEX.md §7, U): the device pass checks that
 * Plex Web shows the plays.
 */
internal class PlexReporter(
    private val server: PlexServer,
) : PlaybackReporter {

    override suspend fun timeline(track: TrackKey, state: ReportedState, positionMs: Long, durationMs: Long): Result<Unit> =
        resultOf {
            server.call("timeline") { api ->
                api.timeline(
                    ratingKey  = track.itemId,
                    key        = PlexPlayback.metadataKey(track.itemId),
                    state      = stateName(state),
                    timeMs     = positionMs.coerceAtLeast(0),
                    durationMs = durationMs.coerceAtLeast(0),
                    sessionId  = server.sessions.current(track),
                )
            }.requireSuccess("timeline")
        }

    override suspend fun scrobble(track: TrackKey): Result<Unit> = resultOf {
        server.call("scrobble") { api -> api.scrobble(track.itemId, PlexPlayback.LIBRARY_IDENTIFIER) }
            .requireSuccess("scrobble")
    }

    companion object {
        /** "every 10 seconds on a LAN/WAN" (PLEX.md §7). */
        const val INTERVAL_MS: Long = 10_000
        /** "every 20 seconds over cellular". */
        const val INTERVAL_CELLULAR_MS: Long = 20_000

        fun stateName(state: ReportedState): String = when (state) {
            ReportedState.PLAYING   -> "playing"
            ReportedState.PAUSED    -> "paused"
            ReportedState.BUFFERING -> "buffering"
            ReportedState.STOPPED   -> "stopped"
        }
    }
}
