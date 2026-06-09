# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Echo Nightly is a fork of [brahmkshatriya/echo](https://github.com/brahmkshatriya/echo) — an extension-based Android music player built around Spotify-style Android Auto support. Key additions in this fork: Android Auto grid UI, Spotify + Deezer combine extension, 320 kbps playback, and pre-bundled extensions on first launch.

The app ships 4 APK extensions bundled in `app/src/main/assets/extensions/`: `spotify.apk`, `deezer.apk`, `echo_combine.apk`, and `echodown.apk`. These auto-install on first launch.

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release (unsigned, minified)
./gradlew assembleRelease

# Nightly (signed, minified, app ID suffix: .nightly)
./gradlew assembleNightly \
  -Pandroid.injected.signing.store.file=signing-key.jks \
  -Pandroid.injected.signing.store.password=PASSWORD \
  -Pandroid.injected.signing.key.alias=key0 \
  -Pandroid.injected.signing.key.password=PASSWORD

# Stable (signed, NOT minified — avoids reflection/serialization stripping)
./gradlew assembleStable \
  -Pandroid.injected.signing.store.file=signing-key.jks \
  -Pandroid.injected.signing.store.password=PASSWORD \
  -Pandroid.injected.signing.key.alias=key0 \
  -Pandroid.injected.signing.key.password=PASSWORD
```

APK outputs: `app/build/outputs/apk/<variant>/`

There are no automated tests in this project. Verification is done by installing the APK on a device or emulator.

## Architecture

### Module Structure

- **`common/`** — Kotlin Multiplatform library (Android + JVM) published to Maven via JitPack. Defines all extension contracts: 26 client interfaces, 60+ data models, and a settings framework. Extensions written against this module are loaded dynamically at runtime.
- **`app/`** — The Android application. Depends on `common/` as a library. Everything below lives here.

### Extension System

The core architectural idea: all music sources are plugins. Extensions are APK files that implement client interfaces from `common/`. The app loads them dynamically via `ExtensionLoader`, which can load from bundled assets or user-installed packages. Extensions declare their type (`ExtensionType`: Music, Lyrics, Tracker, Misc) and implement the corresponding client interfaces (e.g., `MusicClient`, `LyricsClient`).

The four bundled extensions form the default experience: Spotify provides catalog/metadata, Deezer provides audio streams, the Combine extension wires them together, and echodown handles offline downloads.

### App Internals (`app/src/main/java/dev/brahmkshatriya/echo/`)

| Package | Responsibility |
|---|---|
| `di/` | Koin modules: `baseModule`, `extensionModule`, `playerModule`, `downloadModule`, plus UI modules |
| `extensions/` | Extension loading, validation, caching, and DB tracking of installed extensions |
| `playback/` | `PlayerService` (Media3 `MediaLibraryService`), queue management (`ShufflePlayer`), Android Auto (`AndroidAutoCallback`), playback state |
| `download/` | Download task queue, Room schema for download state, WorkManager background tasks |
| `ui/` | Feature-based fragment packages: `player/`, `feed/`, `main/`, `extensions/`, `download/`, `playlist/`, `media/` |
| `widget/` | Home screen widgets (vertical, circle, horizontal) |
| `utils/` | Shared utilities |

### Data Flow

`MainActivity` hosts all fragments. `PlayerService` runs as a foreground `MediaLibraryService` and owns the `ExoPlayer` instance. Fragments communicate with the player via `PlayerViewModel` (Koin-injected). Extension data flows through Kotlin `Flow` and paging via `PagedData` helpers in `common/`.

Room stores downloads and extension metadata. SharedPreferences stores user settings. Coil handles all image loading with a 100 MB disk cache and 25%-of-heap memory cache.

### Build Variants

| Variant | Minified | App ID | Purpose |
|---|---|---|---|
| `debug` | No | `dev.brahmkshatriya.echo` | Development |
| `release` | Yes | `dev.brahmkshatriya.echo` | Unsigned release artifact |
| `nightly` | Yes | `dev.brahmkshatriya.echo.nightly` | Signed, parallel-installable |
| `stable` | **No** | `dev.brahmkshatriya.echo` | Signed production (no minification to avoid breaking reflection/serialization) |

### Versioning

Version is derived from git: `v3.0.${gitCount}_${gitHash}`. `versionCode` = commit count (auto-increments per commit).

## Key Dependencies

All versions are centralized in `gradle/libs.versions.toml`.

- **Media3/ExoPlayer 1.8.0** — playback engine and MediaSession (Android Auto)
- **Koin 4.1.0** — dependency injection
- **Room 2.7.2** — local database
- **Coil 3** — image loading
- **OkHttp 5.1.0** — networking
- **Kotlinx Coroutines 1.10.2 / Serialization 1.9.0** — async and JSON
- **Paging 3.3.6** — paginated data from extensions
- **Material Design 3 (alpha)** — UI components
- **Firebase Analytics + Crashlytics** — optional; only included when `google-services.json` is present

## CI/CD

GitHub Actions workflows in `.github/workflows/`:

| Workflow | Trigger | Output |
|---|---|---|
| `pr.yml` | PR opened | Unsigned release APK |
| `debug.yml` | Push to main | Debug APK |
| `nightly.yml` | Push to main | Signed nightly APK |
| `release.yml` | Version tag push | Signed stable APK → GitHub Release + Discord/Telegram notifications |
| `maven.yml` | Manual/tag | Publishes `common/` to Maven Central |

Required GitHub Secrets for signed builds: `KEYSTORE_B64`, `PASSWORD`. Optional: `GOOGLE_SERVICES_B64` (Firebase), `DISCORD_RELEASE_WEBHOOK`, `TELEGRAM_BOT_ID`, `TELEGRAM_CHANNEL_ID`, `TELEGRAM_THREAD_ID`.

## AI Policy

Per `AI_POLICY.md`: disclose AI usage in PRs and issues, test all code before submitting, and be able to explain and defend every change. Maintainers have final authority on code quality.
