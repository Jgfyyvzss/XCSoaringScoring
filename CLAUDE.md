# CLAUDE.md

Context for Claude Code (or any coding agent) working in this repo. Keep this
file current when architecture or conventions change - it's read automatically
at the start of a session.

## What this is

XCSoaringScoring (originally "SS Task Loader") - an Android app that fetches
contest tasks, waypoints, and flight-upload access from the SoaringScoring
Public API and loads them straight into XCSoar / XCSoar Jet, replacing a manual
download-and-copy routine during gliding competitions.

## Stack

- Kotlin + Jetpack Compose (Material 3), single-Activity + Navigation Compose
- OkHttp + kotlinx.serialization for the API client (no Retrofit)
- DataStore Preferences for settings (not SharedPreferences)
- Storage Access Framework (SAF) for reading/writing XCSoar's folders - **not**
  plain file APIs, and **not** `MANAGE_EXTERNAL_STORAGE`
- minSdk 26, targetSdk/compileSdk 34
- AGP **8.5.2** + Gradle **8.7** + Kotlin **1.9.24** - this combination is
  load-bearing, see "Gotchas" below before touching any of these versions

## Where things live

```
app/src/main/java/io/github/jgfyyvzss/xcsoaringscoring/
  api/              SoaringScoringApi.kt (OkHttp client), Models.kt (all @Serializable data classes)
  data/             SettingsRepository.kt - DataStore-backed settings (API
                     keys, entry address, media tree URI, which target
                     folders are ticked, cached DustDevil.cloud pilot/entries,
                     and the last downloaded task group - see gotcha 15)
  storage/          XcsoarFolderStore.kt - all SAF folder/file resolution logic
                     (read AND write - readTaskFile() added for the "check for
                     updated task" flow's local-file-first path)
  ui/               AppViewModel.kt (single ViewModel, single AppUiState) +
                     ContestGrouping.kt (date categorization/grouping/filtering,
                     pure functions, incl. TaskGroup/TaskGroupKey/
                     groupedVisibleTasks() for day/class/handicap grouping)
  ui/screens/       Compose screens - one file per screen, plus FolderPicker.kt
                     (two composables: MediaFolderAccessSetting for Settings,
                     TargetFolderCheckboxes for the home screen)
  MainActivity.kt   NavHost + SAF folder-picker launcher
  util/             DateFormat.kt - dateOnly() strips time-of-day from API dates
```

Single `AppViewModel` + single `AppUiState` data class for the whole app - not
one ViewModel per screen. All screens read from and call into this one state
holder.

## Build

Open the project root in Android Studio and use Run/Build - this is the
primary, tested workflow. There's no guarantee `./gradlew` works standalone
from a terminal in this environment; if you need CLI builds, verify the
wrapper actually invokes Gradle 8.7 first (see Gotchas).

## Gotchas - read before changing these

1. **AGP/Gradle version pairing is fragile.** AGP 8.5.2 requires Gradle 8.x;
   jumping to Gradle 9.x (which Android Studio may substitute silently if the
   wrapper isn't properly bootstrapped) causes an internal R8/Kotlin-compiler
   crash that looks nothing like a version-mismatch error. If a build fails
   with an obscure internal tooling exception (ArrayIndexOutOfBounds,
   NoSuchMethodError inside `com.android.tools.r8.internal.*`, etc.), check
   the actual Gradle version in use before debugging anything else.

2. **XCSoar's subfolder names vary by install - always case-insensitive, never
   hardcode a single case.** Different XCSoar versions/forks use `Tasks` vs
   `tasks`, `Waypoints` vs `waypoints`, `Logs` vs `logs`. All folder resolution
   goes through `XcsoarFolderStore`'s shared `resolveSubfolderOrRoot()` -
   never add a new hardcoded-case folder lookup elsewhere.

3. **Never create XCSoar's own folders.** `findXcsoarFolders()` and every
   subfolder resolver only ever *find* existing folders (`tasks`, `waypoints`,
   `logs`) - they never call `createDirectory()` for these. Those folders are
   always created by XCSoar itself; if a subfolder genuinely doesn't exist
   (older XCSoar version), the code falls back to the XCSoar folder's root
   rather than creating a new one.

4. **`DocumentFile.findFile()` can silently miss existing files/folders** on
   some devices/providers. Once a target file/folder is resolved, its URI gets
   cached (`fileUriCache` in `XcsoarFolderStore`) and reused directly rather
   than re-searching on every write - re-searching every time previously
   caused duplicate `Tasks (1)`, `Tasks (2)` ... folders to appear.

5. **The live SoaringScoring API requires a key on every endpoint**, including
   `/contests` and `/classes`, despite the docs describing those as needing no
   key. Send the effective key everywhere.

6. **`dhtHandicap` is a `Double`, not an `Int`** - real DHT handicap values are
   fractional (e.g. 0.86). Getting this wrong causes a JSON parse crash on any
   contest with non-integer handicaps.

7. **Contest/task dates from the API sometimes carry a bogus time-of-day**
   (`T00:00:00.000Z` on everything, even genuinely time-sensitive contests) -
   `util/dateOnly()` strips it for display, but be aware same-day contests can
   be mis-bucketed into Current vs Past because the API just doesn't expose
   real end times. Not fixable client-side.

8. **The app-wide API key (`BuildConfig.SS_API_KEY`) only exists on locally
   built APKs** - it comes from a gitignored `local.properties` (`ss.apiKey=`)
   that never reaches F-Droid's build server or a fresh clone. Don't assume
   it's present; the Settings screen's "personal override" field is the
   fallback path for any build that doesn't have it baked in.

9. **This one key covers `tasks:read` and `flights:write`, confirmed live** -
   flight uploads default to the same effective key as task/contest reads
   (`state.uploadApiKey.ifBlank { state.apiKey }` in `AppViewModel`), and this
   was verified against the real API on 2026-09-05 (a test upload returned a
   legitimate `NO_OFFICIAL_TASK` business error, not an auth failure). The
   personal "Upload API key" override field is optional, not required - and
   is itself slated for retirement once DustDevil sign-in (below) is tested,
   before release. Don't reintroduce a hard requirement for a separate
   upload key.

10. **When editing multiple files for one change, keep them together.** This
    project's update workflow has been: edit files here, zip just the changed
    ones, drag into the GitHub repo. A partial set (e.g. a UI file added in one
    update, its ViewModel wiring in another, applied out of order across
    branches) has caused real regressions - see DEVELOPMENT.md's "Known
    incidents" for a concrete example.

11. **IGC upload's manual key/address entry is being replaced by DustDevil.cloud
    sign-in - actively in progress on the `OAuth` branch, not just proposed.**
    See DEVELOPMENT.md's "DustDevil.cloud sign-in" section for full status,
    decisions locked in, and what's still pending confirmation from the
    SoaringScoring dev before the flow can be exercised end-to-end. Don't
    assume the manual fields are the long-term design, but also don't rip
    them out yet - they stay as the fallback path (a contest DustDevil.cloud
    hasn't synced to SoaringScoring yet won't appear in the sign-in entries
    list either) until sign-in has been tested for real and the personal-key
    retirement above actually happens.

12. **The OAuth redirect scheme (`xcsoaringscoring://oauth-callback`) is
    deliberately NOT derived from applicationId.** This paid off: the app was
    renamed from `com.soaringscoring.taskloader` to
    `com.soaringscoring.xcsoaringscoring` (2026-09-05, alongside the
    repo/product rename to XCSoaringScoring) with zero impact on the redirect
    scheme - no re-registration with the SoaringScoring dev needed, since it
    was never tied to the package name in the first place. Keep it that way if
    the applicationId ever changes again.

13. **DustDevil sign-in must use the app's own built-in key
    (`BuildConfig.SS_API_KEY`) end-to-end, never the effective/override key.**
    The `client_key_id` used to start the flow and the key used to redeem the
    code must be the exact same key - the doc is explicit that a code redeemed
    by a different key than the one that started the flow fails (a plain 404,
    indistinguishable from an expired/reused code). Since Settings supports a
    personal API key override that can differ from the built-in key, the
    "Sign in with SoaringScoring" action is hidden/disabled whenever a
    personal override is set, rather than silently using the wrong key.

14. **applicationId changed (2026-09-05): `com.soaringscoring.taskloader` →
    `com.soaringscoring.xcsoaringscoring`.** Android treats this as a
    different app, not an upgrade - any device with the old build installed
    needs to uninstall it before installing a new one; DataStore-persisted
    settings (API key, ticked folders, DustDevil session) are lost, not
    migrated. Done now deliberately, before any public store listing or the
    DustDevil redirect URI being registered, since it only gets more
    disruptive later. Don't assume a device with the old app still installed
    will "just update."

15. **Task downloads no longer write a single fixed filename.** Since
    SoaringScoring publishes alternate tasks before one is made official
    (see `docs/FEATURE-check-updated-task.md`), a drill-down download now
    grabs *every* candidate task for a day/class(/handicap) via
    `downloadTaskGroup()`, saving each under a filename built by
    `AppViewModel.taskFileName()` (not a hardcoded `soaringscoring_task.tsk`
    - that name is gone). Only the variant flagged `isOfficialTask` (if any)
    additionally gets written to `default.tsk`. `TaskListScreen` renders one
    card per day/class/handicap slot (`TaskGroup`), not one per task row -
    don't reintroduce a per-row download action without also reintroducing
    this grouping, or alternates will collide on write.

15a. **`taskFileName()` always appends a taskId stub - TEMPORARY, don't
    remove without checking with the SoaringScoring dev first.**
    SoaringScoring's tasks endpoint currently returns the same
    `displayLabel` for every alternate on a day, with no other
    human-distinguishable field, and there's no guarantee the server's own
    download filename is any more unique (it may be derived from that same
    label). Until the dev fixes this upstream, every saved task filename -
    server-supplied or fallback, single download or a whole group - gets
    `_<last 8 chars of taskId>` inserted before the extension. This is a
    workaround for a live server bug, not a permanent design choice; once
    fixed, this should go back to trusting the server's filename outright
    (matching the waypoint download's existing, unmodified behavior).

16. **The "check for updated task" flow prefers local files over the
    network, deliberately.** `checkForUpdatedTask()` only ever makes one
    lightweight `getTasks()` metadata call; if the newly-official task
    matches something already downloaded as an alternate, it's read back
    via `XcsoarFolderStore.readTaskFile()` and copied straight to
    `default.tsk` with **no file re-download**. This matters most exactly
    when connectivity is worst (a pilot out at the launch vs. at the pilot
    briefing) - don't "simplify" this into always re-downloading, that
    defeats the actual point of the feature.

17. **`downloadAllAlternates` is set-before-use-and-retain, deliberately not
    handled gracefully mid-comp.** Toggling it always clears the stored
    `LastDownloadedTaskGroup` (`AppViewModel.setDownloadAllAlternates()`),
    since the record's `variants` no longer reliably describes what's on
    disk once the setting changes - don't try to reconcile/merge the old
    record instead, the project owner explicitly chose "clear and force a
    re-download" over any cleverer alternative. If nothing is flagged
    official yet, `downloadTaskGroup()` downloads everything regardless of
    this setting - there's nothing to narrow to.

18. **applicationId changed AGAIN (2026-09-08): `com.soaringscoring.xcsoaringscoring`
    → `io.github.jgfyyvzss.xcsoaringscoring`.** Reason this time: the
    `com.soaringscoring.*` prefix made the app look like an official
    SoaringScoring product, which it isn't - this is an independent,
    unaffiliated client of their public API. `io.github.<username>` is the
    standard fallback reverse-DNS scheme for apps with no owned domain (also
    F-Droid's recommended convention). Same disruption as gotcha 14 above -
    another fresh install required, DataStore lost again - and same non-impact
    on the DustDevil redirect scheme (still not derived from applicationId,
    see gotcha 12). If the applicationId changes a third time, remember the
    `proguard-rules.pro` keep rule and both README.md/CLAUDE.md's "Where
    things live" ASCII paths use `/`-separated package paths, not
    dotted ones - a dot-only find/replace across the repo will silently miss
    them.

## Conventions

- No Retrofit - plain OkHttp with manual `Request`/`Response` handling in
  `SoaringScoringApi`, wrapped in a small `ApiResult<T>` sealed class
  (`Success`/`Failure`), not exceptions.
- Errors from the API get mapped to specific documented error codes
  (`MISSING_API_KEY`, `INVALID_API_KEY`, `INSUFFICIENT_SCOPE`, etc.) into
  human-readable messages via `describeError()`/`describeUploadError()` in
  `AppViewModel` - extend these when adding new endpoints rather than showing
  raw API error text.
- Card-based UI (Material 3 `Card`), not `ListItem` rows, for anything
  representing a distinct item (contests, tasks, IGC files).
- Read `docs/DustDevil_OAuth_reference.md` before touching the DustDevil sign-in
  flow - it's the SoaringScoring dev's own reference doc, kept verbatim, and
  is the source of truth over any summary of it elsewhere in these files.
  `docs/FlightUpload_API_errors.md` has the Flight Upload endpoint's error
  table (2026-09-09) - not the full API doc, just that one endpoint's error
  contract. Earlier versions of this file also referenced a full
  `SoaringSCoring_API.md` and `SoaringScoringUpload_API.txt` - those still
  aren't in the repo beyond the error-table excerpt above; don't assume the
  rest exists. The live API has diverged from documented behavior at least
  twice before (auth requirements on `/contests`/`/classes`, and Current/Past
  categorization for same-day contests) - verify against real responses when
  in doubt.
