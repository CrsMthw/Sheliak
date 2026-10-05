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
