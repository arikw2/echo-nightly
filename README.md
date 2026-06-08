# Echo: Music Player — Android Auto fork

An extension-based music player for Android, with a heavily polished **Android Auto** experience.
This fork is built around the **Combine** extension (Spotify metadata + Deezer streaming) and adds a
Spotify-like in-car UI, batch downloads, and a pre-configured out-of-the-box setup.

> [!NOTE]
> The developer of this application is not liable for any misuse or legal issues arising from its use
> and is not affiliated with any content providers. This application hosts zero content. Echo is
> intended for offline use only by default; the user manages any external sources. Echo does not
> condone or support piracy.

## What this fork adds

### Android Auto
- **Spotify-style home** — single Combine extension surfaced as top tabs (Home / Recent / Search /
  Library) with a large cover-art grid instead of a plain list.
- **Playback fixed** — tracks actually play (resolved a bug where the playlist id was used as the
  extension id, causing "Source error").
- **Full-playlist queue** — tapping a track plays its whole playlist/album; next/previous stay in
  context (no more jumping to an unrelated song). The first page starts instantly and the rest of
  the queue fills in the background.
- **Reliable back navigation** — returning to a playlist no longer shows an empty "no items" screen.
- **Search** — working in-car search with results shown directly.
- **Recent** — a Recent tab listing your recently played tracks.
- **Voice search** — "Hey Google, play … on Echo" resolves and plays via Combine.
- **Now-playing controls** — shuffle, repeat and like buttons on the player.
- **Podcasts hidden** — Spotify shows/episodes can't be streamed through Deezer, so they're filtered
  out instead of failing.
- **Faster cold start** — the Combine home feed is warmed as soon as the app launches.

### Quality
- **320 kbps default** for Combine playback (selected by server title), with a **Combine playback
  quality** dropdown in *Settings → Player* (Auto / FLAC / 320 / 128).
- Download quality is configurable in the Downloader (echodown) extension's settings.

### Phone app
- **Multi-select downloads** — open a playlist/album → *⋮* → **Select tracks to download** → pick
  tracks (already-downloaded ones are pre-unchecked) → download them all at once.

### Out-of-the-box setup
- On first launch the app **pre-installs the Spotify, Deezer, Combine and Downloader extensions**
  (bundled in the APK) and sets sensible defaults, so it works without manual extension setup.

## How it works
Echo plays nothing on its own — it loads **extensions** that provide music. This fork ships with:
- **Spotify** extension → catalog/metadata (search, playlists, albums, artists).
- **Deezer** extension → actual audio streams.
- **Combine** extension → merges the two: Spotify metadata, Deezer audio.
- **Downloader** (echodown) → offline downloads.

Everything is built on the upstream [Echo](https://github.com/brahmkshatriya/echo) extension API.

## Updates
The in-app updater points at this repository. Stable builds check this repo's
[Releases](../../releases) for a newer version and offer to update in-app.

## Credits
Fork of [brahmkshatriya/echo](https://github.com/brahmkshatriya/echo). Android Auto and download
enhancements added on top of the `nightly` line.
