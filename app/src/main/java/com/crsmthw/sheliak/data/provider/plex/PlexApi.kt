package com.crsmthw.sheliak.data.provider.plex

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/*
 * The two Retrofit surfaces, exactly as docs/PLEX.md gives the calls. The client headers (Accept, X-Plex-Product,
 * X-Plex-Client-Identifier, …) and, for a server, its X-Plex-Token are added by PlexHeadersInterceptor, not here.
 *
 * PMS paths are RELATIVE (no leading "/") and the base URL always ends in "/": a custom access URL behind a
 * reverse proxy may carry a path ("https://music.example.com/plex/"), which a leading "/" would drop.
 */

/** plex.tv: PIN sign-in, token check, server discovery (PLEX.md §1, §2). Base `https://plex.tv/`. */
interface PlexTvApi {

    /** PLEX.md §1 "Create PIN": `strong=true` → the 25-character code with the longer life. */
    @POST("api/v2/pins")
    suspend fun createPin(@Query("strong") strong: Boolean): Response<PlexPin>

    /** PLEX.md §1 "Poll", once per second until [PlexPin.authToken] or expiry. */
    @GET("api/v2/pins/{id}")
    suspend fun pin(@Path("id") id: Long): Response<PlexPin>

    /** PLEX.md §1 "Validate token": 200 = valid, 401 = invalid, anything else = could not check. */
    @GET("api/v2/user")
    suspend fun user(@Header(PlexHeaders.TOKEN) accountToken: String): Response<Unit>

    /** PLEX.md §2: a bare JSON array of devices, on the official host. */
    @GET("https://clients.plex.tv/api/v2/resources")
    suspend fun resources(
        @Header(PlexHeaders.TOKEN) accountToken: String,
        @Query("includeHttps") includeHttps: Int,
        @Query("includeRelay") includeRelay: Int,
        @Query("includeIPv6") includeIPv6: Int,
    ): Response<List<PlexResource>>
}

/** One Plex Media Server through one connection (PLEX.md §2, §3, §6, §7). */
interface PlexServerApi {

    /** Unauthenticated: `MediaContainer.machineIdentifier` / `version`. Proves reachability, not the token. */
    @GET("identity")
    suspend fun identity(): Response<PlexResponse>

    /** What python-plexapi calls; PLEX.md correction 9. */
    @GET("library/sections")
    suspend fun sections(): Response<PlexResponse>

    /** The official spec's path for the same list; tried when [sections] answers 404. */
    @GET("library/sections/all")
    suspend fun sectionsAll(): Response<PlexResponse>

    /**
     * A page of a section's items of [type] (8 / 9 / 10). Both container headers are always sent (PLEX.md
     * correction 11). [filters] carries the media-query filter of an incremental run (see [PlexFilters]).
     */
    @GET("library/sections/{key}/all")
    suspend fun sectionItems(
        @Path("key") sectionKey: String,
        @Query("type") type: Int,
        @Query("sort") sort: String,
        @QueryMap filters: Map<String, String>,
        @Header(PlexHeaders.CONTAINER_START) start: Int,
        @Header(PlexHeaders.CONTAINER_SIZE) size: Int,
    ): Response<PlexResponse>

    /** Full metadata (with `Stream[]`) for several items: [ids] is `id1,id2,…` (PLEX.md §3). */
    @GET("library/metadata/{ids}")
    suspend fun metadata(@Path("ids") ids: String): Response<PlexResponse>

    /** PLEX.md §6 "List". */
    @GET("playlists")
    suspend fun playlists(@Query("playlistType") playlistType: String): Response<PlexResponse>

    /** PLEX.md §6 "Items", paged like the listings. */
    @GET("playlists/{id}/items")
    suspend fun playlistItems(
        @Path("id") playlistId: String,
        @Header(PlexHeaders.CONTAINER_START) start: Int,
        @Header(PlexHeaders.CONTAINER_SIZE) size: Int,
    ): Response<PlexResponse>

    /** PLEX.md §7 "Timeline": POST; [timeMs] / [durationMs] in milliseconds. */
    @POST(":/timeline")
    suspend fun timeline(
        @Query("ratingKey") ratingKey: String,
        @Query("key") key: String,
        @Query("state") state: String,
        @Query("time") timeMs: Long,
        @Query("duration") durationMs: Long,
        @Header(PlexHeaders.SESSION_IDENTIFIER) sessionId: String?,
    ): Response<Unit>

    /** PLEX.md §7 "Scrobble": PUT (GET answers, but PUT is the documented method). */
    @PUT(":/scrobble")
    suspend fun scrobble(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String,
    ): Response<Unit>
}

/** Builds the Retrofit services with the kotlinx converter. */
internal object PlexRetrofit {

    const val PLEX_TV_BASE = "https://plex.tv/"

    private val JSON = "application/json".toMediaType()

    fun tv(client: OkHttpClient, json: Json): PlexTvApi =
        Retrofit.Builder()
            .baseUrl(PLEX_TV_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON))
            .build()
            .create(PlexTvApi::class.java)

    fun server(baseUri: String, client: OkHttpClient, json: Json): PlexServerApi =
        Retrofit.Builder()
            .baseUrl(baseUrl(baseUri))
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON))
            .build()
            .create(PlexServerApi::class.java)

    /** [uri] with exactly one trailing "/", as Retrofit needs for relative paths to keep a base path. */
    fun baseUrl(uri: String): HttpUrl = (uri.trimEnd('/') + "/").toHttpUrl()
}
