package com.crsmthw.sheliak.data.provider.plex

import kotlinx.serialization.json.Json

/*
 * JSON fixtures shaped per docs/PLEX.md (field names and nesting from its §1–§7 tables), with Plex's mixed
 * typing on purpose: some numbers and booleans arrive as strings ("1", "32400", "0"), as the official examples
 * show. Values are invented; nothing here came from a live server.
 */
object PlexFixtures {

    /** The app's Json (AppContainer). */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    const val OWNED_ID = "0123456789abcdef0123456789abcdef01234567"
    const val SHARED_ID = "fedcba9876543210fedcba9876543210fedcba98"

    /** `POST /api/v2/pins?strong=true`, unclaimed; plus a key PLEX.md does not list (must be ignored). */
    val PIN_PENDING = """
        {
          "id": 1234567890,
          "code": "abcdefghijklmnopqrstuvwxy",
          "product": "Sheliak",
          "trusted": false,
          "qr": "https://plex.tv/api/v2/pins/qr/abcdefghijklmnopqrstuvwxy",
          "clientIdentifier": "6f1c3a52-0d55-4d6e-9a1b-0c8f6f0e2d11",
          "expiresIn": 1800,
          "createdAt": "2026-10-06T10:00:00Z",
          "expiresAt": "2026-10-06T10:30:00Z",
          "authToken": null,
          "newRegistration": null,
          "someFutureField": { "nested": [1, 2, 3] }
        }
    """.trimIndent()

    /** The same PIN once claimed; `id` as a string this time. */
    val PIN_CLAIMED = """
        {
          "id": "1234567890",
          "code": "abcdefghijklmnopqrstuvwxy",
          "authToken": "account-token-xyz",
          "expiresIn": "1800",
          "createdAt": "2026-10-06T10:00:00Z",
          "expiresAt": "2026-10-06T10:30:00Z",
          "trusted": "0"
        }
    """.trimIndent()

    /**
     * `GET /api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1`: a bare array. An owned server with
     * every connection kind (LAN plex.direct, LAN http, custom domain, remote plex.direct, relay), a shared server
     * typed with strings, and a client device that must be filtered out.
     */
    val RESOURCES = """
        [
          {
            "name": "Cris's Server",
            "product": "Plex Media Server",
            "productVersion": "1.43.4.10000-abcdef012",
            "platform": "Linux",
            "platformVersion": "6.1",
            "device": "PC",
            "clientIdentifier": "$OWNED_ID",
            "createdAt": "2024-01-01T10:00:00Z",
            "lastSeenAt": "2026-10-06T09:00:00Z",
            "provides": "server",
            "ownerId": null,
            "sourceTitle": null,
            "publicAddress": "203.0.113.7",
            "accessToken": "owned-server-token",
            "owned": true,
            "home": false,
            "synced": false,
            "relay": true,
            "presence": true,
            "httpsRequired": false,
            "publicAddressMatches": false,
            "dnsRebindingProtection": false,
            "natLoopbackSupported": true,
            "connections": [
              { "protocol": "http", "address": "192.168.1.20", "port": 32400, "uri": "http://192.168.1.20:32400", "local": true, "relay": false, "IPv6": false },
              { "protocol": "https", "address": "198.51.100.4", "port": 8443, "uri": "https://198-51-100-4.0123abcd.plex.direct:8443", "local": false, "relay": true, "IPv6": false },
              { "protocol": "https", "address": "music.example.com", "port": 443, "uri": "https://music.example.com:443", "local": false, "relay": false, "IPv6": false },
              { "protocol": "https", "address": "192.168.1.20", "port": 32400, "uri": "https://192-168-1-20.0123abcd.plex.direct:32400", "local": true, "relay": false, "IPv6": false },
              { "protocol": "https", "address": "203.0.113.7", "port": 32400, "uri": "https://203-0-113-7.0123abcd.plex.direct:32400", "local": false, "relay": false, "IPv6": false }
            ]
          },
          {
            "name": "Friend's Server",
            "product": "Plex Media Server",
            "productVersion": "1.42.1.9000-0abc",
            "clientIdentifier": "$SHARED_ID",
            "provides": "server",
            "owned": "0",
            "ownerId": "12345",
            "sourceTitle": "friend",
            "accessToken": "shared-server-token",
            "presence": "1",
            "connections": [
              { "protocol": "https", "address": "10.0.0.5", "port": "32400", "uri": "https://10-0-0-5.fedcba98.plex.direct:32400", "local": "1", "relay": "0", "IPv6": "0" },
              { "protocol": "https", "address": "2001:db8::5", "port": "32400", "uri": "https://2001-db8--5.fedcba98.plex.direct:32400", "local": "0", "relay": "0", "IPv6": "1" },
              { "protocol": "https", "address": "192.0.2.9", "port": "32400", "uri": "https://192-0-2-9.fedcba98.plex.direct:32400", "local": "0", "relay": "0", "IPv6": "0" }
            ]
          },
          {
            "name": "Cris's Phone",
            "product": "Plexamp",
            "clientIdentifier": "phone-client-id",
            "provides": "client,player",
            "owned": true,
            "connections": []
          }
        ]
    """.trimIndent()

    /** `GET /identity`. */
    val IDENTITY = """
        { "MediaContainer": { "size": 0, "claimed": true, "machineIdentifier": "$OWNED_ID", "version": "1.43.4.10000-abcdef012" } }
    """.trimIndent()

    /** `GET /library/sections`: two music libraries and a movie library. */
    val SECTIONS = """
        {
          "MediaContainer": {
            "size": 3,
            "allowSync": false,
            "title1": "Plex Library",
            "Directory": [
              { "allowSync": true, "key": "1", "type": "artist", "title": "Music", "uuid": "5b4c2a1e-1111-2222-3333-444455556666", "updatedAt": 1700000000 },
              { "allowSync": true, "key": "2", "type": "movie", "title": "Movies", "uuid": "aa4c2a1e-1111-2222-3333-444455556666" },
              { "allowSync": "1", "key": 5, "type": "artist", "title": "Audiobooks", "uuid": "bb4c2a1e-1111-2222-3333-444455556666" }
            ]
          }
        }
    """.trimIndent()

    /**
     * `GET /library/sections/1/all?type=10` page 1 of 2 (totalSize 3): Media and Part but no Stream[], as
     * PLEX.md expects of listings. The second track is typed with strings and has its own artist.
     */
    val TRACKS_PAGE_1 = """
        {
          "MediaContainer": {
            "size": 2,
            "totalSize": 3,
            "offset": 0,
            "allowSync": true,
            "identifier": "com.plexapp.plugins.library",
            "librarySectionID": 1,
            "librarySectionTitle": "Music",
            "Metadata": [
              {
                "ratingKey": "1001",
                "key": "/library/metadata/1001",
                "parentRatingKey": "901",
                "grandparentRatingKey": "801",
                "guid": "plex://track/5d07cdc0403c640290f5a001",
                "type": "track",
                "title": "Ágætis byrjun",
                "titleSort": "Agaetis byrjun",
                "grandparentKey": "/library/metadata/801",
                "parentKey": "/library/metadata/901",
                "librarySectionID": 1,
                "grandparentTitle": "Sigur Rós",
                "parentTitle": "Ágætis byrjun",
                "index": 5,
                "parentIndex": 1,
                "parentYear": 1999,
                "thumb": "/library/metadata/901/thumb/1700000100",
                "parentThumb": "/library/metadata/901/thumb/1700000100",
                "grandparentThumb": "/library/metadata/801/thumb/1700000050",
                "grandparentArt": "/library/metadata/801/art/1700000050",
                "duration": 475000,
                "addedAt": 1600000000,
                "updatedAt": 1700000100,
                "viewCount": 7,
                "lastViewedAt": 1690000000,
                "Media": [
                  {
                    "id": 2001,
                    "duration": 475000,
                    "bitrate": 2900,
                    "audioChannels": 2,
                    "audioCodec": "flac",
                    "container": "flac",
                    "Part": [
                      { "id": 3001, "key": "/library/parts/3001/1600000000/file.flac", "duration": 475000, "file": "/srv/music/Sigur Rós/Ágætis byrjun/05 Ágætis byrjun.flac", "size": 172187500, "container": "flac" }
                    ]
                  }
                ]
              },
              {
                "ratingKey": "1002",
                "key": "/library/metadata/1002",
                "parentRatingKey": "902",
                "grandparentRatingKey": "802",
                "type": "track",
                "title": "Collaboration",
                "grandparentTitle": "Various Artists",
                "originalTitle": "Guest Artist",
                "parentTitle": "Compilation",
                "index": "2",
                "absoluteIndex": "2",
                "year": "2004",
                "duration": "215000",
                "addedAt": "1600000001",
                "updatedAt": "1700000200",
                "viewCount": "0",
                "Media": [
                  {
                    "id": "2002",
                    "duration": "215000",
                    "bitrate": "320",
                    "audioChannels": "2",
                    "audioCodec": "mp3",
                    "container": "mp3",
                    "Part": [
                      { "id": "3002", "key": "/library/parts/3002/1600000001/file.mp3", "duration": "215000", "size": "8601234", "container": "mp3" }
                    ]
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    /** `GET /library/metadata/1001,1002`: the same tracks with `Stream[]` (an audio stream each, one lyric stream). */
    val METADATA_BATCH = """
        {
          "MediaContainer": {
            "size": 2,
            "allowSync": true,
            "identifier": "com.plexapp.plugins.library",
            "Metadata": [
              {
                "ratingKey": "1001",
                "key": "/library/metadata/1001",
                "parentRatingKey": "901",
                "grandparentRatingKey": "801",
                "type": "track",
                "title": "Ágætis byrjun",
                "titleSort": "Agaetis byrjun",
                "grandparentTitle": "Sigur Rós",
                "parentTitle": "Ágætis byrjun",
                "index": 5,
                "parentIndex": 1,
                "parentYear": 1999,
                "thumb": "/library/metadata/901/thumb/1700000100",
                "duration": 475000,
                "addedAt": 1600000000,
                "updatedAt": 1700000100,
                "viewCount": 7,
                "lastViewedAt": 1690000000,
                "Media": [
                  {
                    "id": 2001,
                    "duration": 475000,
                    "bitrate": 2900,
                    "audioChannels": 2,
                    "audioCodec": "flac",
                    "container": "flac",
                    "Part": [
                      {
                        "id": 3001,
                        "key": "/library/parts/3001/1600000000/file.flac",
                        "duration": 475000,
                        "file": "/srv/music/Sigur Rós/Ágætis byrjun/05 Ágætis byrjun.flac",
                        "size": 172187500,
                        "container": "flac",
                        "Stream": [
                          { "id": 4001, "streamType": 2, "codec": "flac", "channels": 2, "bitrate": 2900, "bitDepth": 24, "samplingRate": 96000, "audioChannelLayout": "stereo", "gain": "-8.53", "albumGain": "-7.21", "peak": "0.977203", "albumPeak": "0.988525", "loudness": "-9.47", "lra": "5.20" },
                          { "id": 4002, "streamType": 4, "key": "/library/streams/4002", "codec": "lrc", "format": "lrc", "timed": "1", "provider": "com.plexapp.agents.lyricfind", "minLines": "5" }
                        ]
                      }
                    ]
                  }
                ]
              },
              {
                "ratingKey": "1002",
                "key": "/library/metadata/1002",
                "parentRatingKey": "902",
                "grandparentRatingKey": "802",
                "type": "track",
                "title": "Collaboration",
                "grandparentTitle": "Various Artists",
                "originalTitle": "Guest Artist",
                "parentTitle": "Compilation",
                "index": "2",
                "duration": "215000",
                "updatedAt": "1700000200",
                "Media": [
                  {
                    "id": "2002",
                    "bitrate": "320",
                    "audioChannels": "2",
                    "audioCodec": "mp3",
                    "container": "mp3",
                    "Part": [
                      {
                        "id": "3002",
                        "key": "/library/parts/3002/1600000001/file.mp3",
                        "container": "mp3",
                        "Stream": [
                          { "id": "4003", "streamType": "2", "codec": "mp3", "channels": "2", "bitrate": "320", "samplingRate": "44100" }
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    /** `GET /playlists?playlistType=audio`: a dumb playlist and a smart one (typed with strings). */
    val PLAYLISTS = """
        {
          "MediaContainer": {
            "size": 2,
            "Metadata": [
              {
                "ratingKey": "5001",
                "key": "/playlists/5001/items",
                "guid": "com.plexapp.agents.none://9a8b7c6d",
                "type": "playlist",
                "title": "Road trip",
                "titleSort": "Road trip",
                "summary": "",
                "smart": false,
                "playlistType": "audio",
                "composite": "/playlists/5001/composite/1700000200",
                "duration": 690000,
                "leafCount": 2,
                "addedAt": 1650000000,
                "updatedAt": 1700000200
              },
              {
                "ratingKey": "5002",
                "key": "/playlists/5002/items",
                "type": "playlist",
                "title": "Recently added",
                "smart": "1",
                "playlistType": "audio",
                "icon": "playlist://image.smart",
                "duration": "1200000",
                "leafCount": "5",
                "addedAt": "1650000001",
                "updatedAt": "1700000201"
              }
            ]
          }
        }
    """.trimIndent()

    /** `GET /playlists/5001/items`: every item of a dumb playlist carries a `playlistItemID`. */
    val PLAYLIST_ITEMS = """
        {
          "MediaContainer": {
            "size": 2,
            "totalSize": 2,
            "offset": 0,
            "composite": "/playlists/5001/composite/1700000200",
            "duration": 690,
            "leafCount": 2,
            "playlistType": "audio",
            "ratingKey": "5001",
            "smart": false,
            "title": "Road trip",
            "Metadata": [
              { "ratingKey": "1002", "key": "/library/metadata/1002", "playlistItemID": 7002, "type": "track", "title": "Collaboration", "duration": 215000 },
              { "ratingKey": "1001", "key": "/library/metadata/1001", "playlistItemID": "7001", "type": "track", "title": "Ágætis byrjun", "duration": 475000 }
            ]
          }
        }
    """.trimIndent()

    fun <T> decode(deserializer: kotlinx.serialization.DeserializationStrategy<T>, text: String): T =
        json.decodeFromString(deserializer, text)
}
