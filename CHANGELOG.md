# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Project skeleton: Gradle 9.8 wrapper with a pinned distribution checksum, Android Gradle Plugin 9.4, Kotlin 2.4,
  Jetpack Compose with Material 3 Expressive, and every library version kept in one version catalog.
- Android 15 (API 35) and later, built against API 37.
- Placeholder launcher icon: a four-point star with a small companion disc on the purple accent, with a themed
  (monochrome) layer for Material You icons, and a matching splash screen.
- Nothing from the app's private storage is included in cloud backups or device-to-device transfers.
- Release builds are minified and shrunk with R8 and signed from local, uncommitted credentials.
- Continuous integration on GitHub Actions: unit tests and the full release lint on every pull request and on
  pushes to `beta`.
- Theme: Material 3 Expressive with three modes (System / Light / Dark), Material You by default, otherwise a
  full colour scheme generated from an accent seed — ten preset swatches plus Hue and Saturation sliders — and
  an AMOLED black option. The seed-to-scheme generator and the accent helpers are unit-tested.
- Navigation shell on Navigation 3 with Material's navigation suite: a bottom bar on compact screens, a collapsed
  rail on medium and an expanded rail on expanded widths, for Tracks, Albums, Artists and Playlists; one finite
  push/pop/predictive-pop transition everywhere; a reserved host for the floating player surface.
- Screens: a first-run intro listing the sources to come, the four library destinations with empty states and a
  large collapsing title bar, Search with the FAB-to-bar container transform on compact screens, Settings with
  the Theme sheet (modes, AMOLED, Material You, accent picker) and a haptics switch, and placeholder Player and
  Queue screens.
- Shared components ported from Lyra: the root and detail top bars with their seam, the capped modal sheet, the
  connected single-choice row, the value slider, haptics vocabulary, the one finite motion spec, the search-bar
  outline morph, the bottom fade scrim, the placeholder album art, and the settings row helpers.
- 78 JVM unit tests (theme generation, accent helpers, back-stack rules, suite layout, formatting).

- Plex as the first source: PIN sign-in in a Custom Tab with a long-lived token, server and music-library pick, a
  one-line local-network permission step on Android 17, and every connection kind first-class (LAN, custom domain,
  plex.direct, relay) — probed on setup and re-picked on network change, preferring local, then remote, then relay.
- Library sync into the merged index: a paged full first sync, incremental runs on `updatedAt`, a periodic full
  listing so deletions propagate, every 6 hours and on demand (pull to refresh on any list, "Sync now" per source);
  progress in the Tracks subtitle ("Syncing… N") and per source under Settings → Manage sources.
- The library over real data: Tracks with "Recently played" and "Most played" carousels (seeded from Plex history)
  above All tracks; Albums, Artists and Playlists with detail screens (hero art, Play and Shuffle, disc headers);
  two-pane list-detail cards on wide screens; the cover flies into the detail hero; long-press actions (Play next,
  Add to queue, Go to album, Go to artist); Search results in four swipeable pages.
- Playback through the app's own Media3 player: one media notification, lock-screen and headset controls, gapless
  playback, queue persistence and resumption after a force-stop, an Android Auto browse tree, voice search (Auto and
  the phone's Assistant), direct play when the phone decodes the file and Plex's transcoder otherwise (FLAC for
  lossless sources, AAC at the chosen bit rate for lossy ones, automatic fallback to the transcoder when the server
  refuses a direct stream, AIFF always transcoded), and Plex play reporting (timeline while playing, scrobble at 90 %).
- Player surfaces: a mini bar directly above the bottom bar on compact screens and a pop-out card on wide ones, the
  full player with the quality-tier chip (exact codec, bit depth / sample rate or bit rate), seek, shuffle and
  repeat, art-tinted background and the album art morphing between surfaces; the queue with tap, swipe-to-remove and
  drag reorder.
- Settings → Playback: transcode bit rate (128 / 192 / 256 / 320 kbps) and a Merge duplicates switch; Settings →
  Manage sources: status, Sync now, Remove, Add Plex server.
- Only the app itself, system-trusted media controllers and known car / assistant / wearable packages may connect to
  the playback session; everything else is rejected.
- 488 JVM unit tests (Plex parsing and sync, connection ordering, playback decisions, reporting cadence, queue
  helpers, navigation rules, library shaping, setup flow).

### Changed
- Navigation shell v2 (after the first device pass): the navigation suite lives inside the Library destination, so
  pushing Search or Settings animates the whole library and a predictive back reveals it with its bar or rail in
  place; one fixed title bar whose text crossfades between tabs; Material shared-axis transitions for push and pop
  and fade-through for tab changes; every library tab shows a count subtitle so the bar is one height.
- Rail: the Search button is centred with the destinations as one group (top-aligned on short screens so nothing
  clips), aligned to the icon column instead of the screen edge.
- Accent picker: the Saturation slider was removed — Material's colour scheme uses only the seed's hue — leaving
  the preset swatches and a Hue slider.
- The bottom fade scrim and list spacing read system insets through modifiers, so they no longer over-count under
  the navigation bar.
- 82 JVM unit tests.
- Intro's primary action is now "Connect a Plex server" and leads straight into the Plex setup; once a source exists
  it reads "Continue".
- Settings' sources row reads "Manage sources" with a count; Search's blank state is a one-line hint (no stored
  recent searches); re-selecting the current library tab scrolls its list to the top.
- Lint: the release report reads "0 errors, 1 warning" — the accepted `ExportedService` on the exported playback
  service, which Android Auto and the system controllers bind to.
