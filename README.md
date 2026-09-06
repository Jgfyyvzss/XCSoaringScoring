<img width="180" height="180" alt="XC_SS" src="https://github.com/user-attachments/assets/fa428800-38da-4544-95f4-fc13cf8e1962" />

# XCSoaringScoring

_(originally "SS Task Loader")_

A small Android app largely based on [XComps](https://github.com/DanielDe8/xcomps) 
that loads task files from [SoaringScoring](https://soaringscoring.com)'s Public API 
straight into XCSoar (and XCSoar Jet), so you don't have to download and copy a 
`.tsk` file by hand each contest day.

Same idea as XComps, just built native (Kotlin + Jetpack Compose) against SoaringScoring
instead of SoaringSpot/GlideAndSeek.

<img width="270" height="567" alt="XCSS_Home" src="https://github.com/user-attachments/assets/527633c1-8ed4-435a-89c6-c1f6436ecd21" />
<img width="270" height="567" alt="XCSS_Tasks" src="https://github.com/user-attachments/assets/c0187e48-18e2-4b9d-aa14-aeb8352a756f" />
<img width="270" height="567" alt="XCSS_Upload" src="https://github.com/user-attachments/assets/a21ce0c9-8b01-404d-800f-ce743305ea01" />



## What it does

1. Lists contests from `GET /api/v1/public/contests` (no key needed).
2. You pick a contest, and it lists that contest's published tasks via
   `GET /api/v1/public/contests/:id/tasks` (needs an API key with the
   `tasks:read` scope).
3. You grant the app access to your `Android/media` folder once (system
   folder picker) — it finds any subfolder with "soar" in the name
   (`org.xcsoar`, `com.zinuzoid.xcsoar_jet`, future forks, etc.) and lets you
   tick which ones to write to.
4. Tapping the download icon on a task fetches the XCSoar `.tsk` file
   (`files.xcsoarTsk` from the tasks response) and writes it to each ticked
   folder's `Tasks/` subfolder under two names: `soaringscoring_task.tsk`
   (load this by hand as the current task on day one) and `default.tsk`
   (the name XCSoar auto-loads on startup, so every day after that just
   needs the download — no manual load required). Both are overwritten on
   every download, same filename-per-overwrite pattern as xcomps.
5. A separate "get waypoints" action downloads the contest's SeeYou `.cup`
   waypoint file once per contest (the turnpoint set doesn't change day to
   day) and writes it to the `waypoints/` subfolder under its original
   filename from the server, falling back to `soaringscoring_waypoint.cup`
   if the server doesn't supply one.
6. Which folder(s) you've ticked to write into is remembered across app
   restarts (nothing is ticked by default — you choose explicitly).

## Status
Functional.

## Project layout

```
app/src/main/java/com/soaringscoring/xcsoaringscoring/
  api/                  OkHttp client + data models for the Public API
  data/SettingsRepository.kt   DataStore: API key, last contest, saved folder tree URI
  storage/XcsoarFolderStore.kt SAF folder scan + file write
  ui/AppViewModel.kt    All app state + orchestration
  ui/screens/           Compose screens (contests, tasks, settings)
  MainActivity.kt       NavHost + SAF picker launcher
```

## API key management (app-wide key, not per-user)

Since SoaringScoring is issuing one key to the app rather than one per user,
the key is baked in at build time rather than typed in by each person:

1. `local.properties` (already gitignored) holds the real key in `ss.apiKey=...`.
2. `app/build.gradle.kts` reads that into `BuildConfig.SS_API_KEY`.
3. `AppViewModel` uses that as the default; the Settings screen only holds a
   *personal override*, for testing with your own key later — most users
   will never open it.

This one key now carries both the `tasks:read` and `flights:write` scopes, so
it's also the default for flight uploads. Settings has a second, independent
override field ("Upload API key") for a pilot who's been issued their own
separate personal key to test with — leave it blank to just use the same
key as everything else.

## Building

Open the project root in Android Studio (Koala or newer). 
Targets Gradle 8.7

Minimum SDK 26, target/compile SDK 34. No special permissions beyond
`INTERNET` — folder access goes through Storage Access Framework, not
`WRITE_EXTERNAL_STORAGE`, so it keeps working under scoped storage.

## Why SAF and not a plain file path

Since Android 11, apps can't touch `Android/data/**` or (on some versions)
`Android/obb/**` via SAF at all — but `Android/media/**` is *not* on that
blocklist, which is presumably why XCSoar stores its files there in the first
place. So: pick `Android/media` once via `ACTION_OPEN_DOCUMENT_TREE`, persist
the permission, and read/write through `DocumentFile` from then on.

## Known limitations / good next steps

- **DHT (Distance-Handicap) days** Right now they just show up as rows under the class; it might be
  worth letting the user filter to their own handicap, but it works OK as-is.
- **No offline cache** — every screen re-fetches. Fine for contest use, but
  a local cache of the last-loaded task would help on bad campsite wifi.
- **Folder permission can be revoked by the OS** on reinstall/storage
  changes — worth adding a "recheck access" step on launch that silently
  re-prompts if the persisted URI permission is gone.
