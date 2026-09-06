# Development notes

Narrative context that doesn't fit CLAUDE.md's terse gotcha list - background,
open decisions, and a roadmap. Update this alongside CLAUDE.md when something
here gets resolved or superseded.

## Origin

Built as a native Kotlin/Compose reimplementation of the idea behind
[xcomps](https://github.com/DanielDe8/xcomps) (a Capacitor/Svelte app doing the
same job for SoaringSpot/GlideAndSeek), targeting SoaringScoring's own Public
API instead. Chose native Kotlin over Capacitor because the whole app is
"HTTP → JSON → SAF file write," which doesn't benefit from a web-view layer,
and scoped-storage file access maps more directly onto Kotlin/SAF APIs than
through a Capacitor plugin bridge.

## Distribution model & the API key tension

The SoaringScoring team issues **one API key to the app**, not one per user -
most of this app's users never need to touch an API key at all. That key is
baked in via `local.properties` → `BuildConfig.SS_API_KEY` at build time, and
is deliberately never committed to git.

That key originally only had `tasks:read`, so flight uploads needed a
separate personal `flights:write` key entered in Settings. SoaringScoring has
since merged both scopes onto the one existing key - confirmed live on
2026-09-05 (a test upload returned `NO_OFFICIAL_TASK`, a legitimate
downstream error, not an auth failure). Uploads now default to the same
effective key as everything else (`AppViewModel.confirmUpload()`:
`state.uploadApiKey.ifBlank { state.apiKey }`). The "Upload API key" Settings
field is an optional personal override, not a required separate credential -
and per the DustDevil sign-in decisions below, it (and the general "API key
override" field) are earmarked for full retirement once sign-in has been
tested for real, before release. Not removed yet - keep them as the fallback
until then.

This creates a real tension with F-Droid distribution: F-Droid builds strictly
from public source with no access to private secrets, so an F-Droid-built copy
would always compile with an empty key, and every F-Droid user would need to
supply their own personal key via Settings' override field - undermining the
"just works" experience that GitHub-distributed builds get. Two ways to
resolve this if F-Droid distribution becomes a priority:

1. Accept it - F-Droid users self-serve a key via the existing override.
2. Build a small server-side proxy that holds the key, so *no* distributed
   copy (GitHub or F-Droid) ever needs to contain it. Bigger lift, cleanly
   solves the problem.

Not yet decided. Dependency-wise, the app is otherwise a clean F-Droid fit -
no ads, no trackers, no Google Play Services, every dependency is Apache 2.0
(AndroxX, OkHttp, kotlinx.serialization).

## Feature history (roughly chronological)

- **Task download** - fetch a contest's tasks, download the XCSoar `.tsk` for
  a selected task, write to the correct XCSoar folder(s) under two names:
  `soaringscoring_task.tsk` (the stable name loaded by hand on day one) and
  `default.tsk` (the name XCSoar auto-loads on startup, so later comp days
  only need the download, no manual load).
- **Multi-folder support** - tick XCSoar and/or XCSoar Jet; writes go to every
  ticked folder. Nothing is ticked by default (opt-in, not opt-out), and the
  tick state persists across app restarts via `SettingsRepository` (keyed by
  folder URI).
- **Contest categorization & drill-down** - Current/Future/Past tabs
  (`ContestGrouping.categorize`), month-grouped lists matching SoaringScoring's
  own site, card-based UI, class-selection chips, timeframe-aware task
  filtering (Current shows only today's/most-recent task; Future/Past show
  everything for the selected class).
- **Waypoint download** - one action per contest (not per task, since the
  turnpoint set doesn't change day to day), writes to the `waypoints`/
  `Waypoints` subfolder (falls back to root on older XCSoar versions with no
  such folder) under the file's original server-supplied name (read from
  `Content-Disposition`, falling back to the download URL's last path
  segment, then to `soaringscoring_waypoint.cup` if neither is usable).
- **In-app help** - a minimal "Getting started" dialog behind the help icon
  in Settings' top bar (`HelpDialog` in `SettingsScreen.kt`), covering
  first-run setup and where files land in plain generic terms. Deliberately
  brief and content-light since it's expected to need rewording as real
  usage surfaces gaps.
- **IGC flight upload (v1, manual)** - separate top-level screen (not folded
  into the contest drill-down, since a pilot's entry address has no
  discoverable link to a specific contest via the API). Defaults to the same
  API key as everything else (see "the API key tension" above); only needs
  the pilot's `{competitionNumber}-{contestKey}` entry address entered once
  in Settings, with an optional personal key override for testing. Browses
  `.igc` files from the `logs`/`Logs` subfolder across every selected XCSoar
  folder, confirms before sending, shows a dedicated result dialog (not a
  snackbar) given the real stakes of a flight-scoring upload.
  **Being replaced by DustDevil.cloud sign-in - see below, actively in
  progress on the `OAuth` branch** - this manual version was always
  understood as a stopgap; keep it working as the fallback in the meantime
  (see the sign-in section for why it can't fully disappear even after
  sign-in ships).

## DustDevil.cloud sign-in (in progress - `OAuth` branch)

**Status: design settled, client-side scaffolding being built now, blocked
on the SoaringScoring dev for the pieces only they can provide** (redirect
URI approval + `client_key_id`; scope confirmation). This replaces IGC
upload's manual key/address entry (above) with a real sign-in flow. Read
`docs/DustDevil_OAuth_reference.md` (the reference doc supplied by the
SoaringScoring dev, kept verbatim in this repo - source of truth) for the
full protocol before touching this code - what follows is the design as
agreed, not a substitute for that doc.

**Context**: DustDevil.cloud is the pilot-facing side of the SoaringScoring
platform (event discovery, entry, payment) - a separate surface from the
Public API this app otherwise talks to.

**Why this is a better fit than generic OAuth**: DustDevil's OAuth server
doesn't support PKCE, so a native app can't run the standard authorization
code flow directly against it. Instead, SoaringScoring proxies the whole
exchange through their own already-registered OAuth client - our app never
talks to DustDevil directly, only to SoaringScoring's own API. This is
meaningfully less work on our side than implementing `AppAuth`/PKCE ourselves
against DustDevil.

**The two insights that shape the design**:

1. The exchange response hands back a pilot's contest entries with `localPart`
   **ready to use** directly with the Task Distribution and Flight Upload
   APIs - "no need to ask the pilot for it." This eliminates the
   hand-typed-entry-address problem entirely, not just the key problem.
2. The exchange call itself authenticates with **our app's own key**
   (`Authorization: Bearer ssk_live_...`), not a pilot-specific secret. Since
   `flights:write` is now confirmed live on our existing app-wide key (see
   "the API key tension" above - tested 2026-09-05), uploads work exactly
   like task/waypoint downloads already do: one shared key baked into the
   app, with pilot *identity* established by sign-in rather than by a
   personal secret.

**Decisions locked in** (2026-09-05):

- **Personal API keys are being retired, not just made optional** - both the
  general "API key override" and "Upload API key override" Settings fields.
  Timing: **after DustDevil sign-in has been tested for real, before
  release** - not now. They stay as the working fallback/testing path until
  then; don't remove them as part of this branch's scaffolding.
- **Sign-in is restricted to the app's built-in key only.** The
  `client_key_id` used to start the flow and the key used to redeem the code
  at exchange must be the *exact same key* - the reference doc is explicit
  that a code redeemed by a different key than the one that started the flow
  fails identically to an expired/reused code (a plain 404, no way to tell
  them apart). Since a personal override can differ from the built-in key,
  "Sign in with SoaringScoring" is hidden/disabled whenever a personal
  override is set, and the exchange call always uses `BuildConfig.SS_API_KEY`
  directly - never `state.apiKey`/`state.uploadApiKey`, which can carry an
  override.
- **Redirect scheme: `xcsoaringscoring://oauth-callback`.** Considered a
  reverse-domain scheme instead (e.g. `com.soaringscoring.taskloader.oauth`),
  which is the RFC 8252-style native-app-OAuth convention specifically
  because it's collision-resistant against other apps' custom schemes
  (Android doesn't enforce scheme uniqueness). Rejected that option here for
  two reasons: (1) it would have baked in `com.soaringscoring.taskloader`,
  the applicationId *at the time* - which was indeed renamed shortly after
  (2026-09-05, to `com.soaringscoring.xcsoaringscoring` - see "Project/display
  name" below) precisely as anticipated, validating the decision to decouple
  the two; a reverse-domain scheme would have needed re-registering with the
  dev at that point, this one didn't; (2) a reverse-domain
  scheme is meant to signal a domain *you* control, but our applicationId is
  already borrowed from SoaringScoring's own name rather than a domain we
  own, so the collision-resistance argument doesn't carry the usual weight
  here and a domain-shaped string would arguably misrepresent ownership we
  don't have. `xcsoaringscoring://oauth-callback` tracks the new product
  name instead, is decoupled from whatever the applicationId ends up being,
  and the collision risk in practice is low for a scheme this specific to a
  niche gliding-competition app. On the custom-scheme interception risk that
  motivates PKCE in general: real but low-severity here specifically because
  redeeming the code requires our app's *secret* key, which an intercepting
  app wouldn't have - worst case is a denied/dropped sign-in attempt
  (denial-of-service), not a credential or identity leak.
- **`MainActivity` needs `android:launchMode="singleTask"` plus an
  `onNewIntent()` override** to receive the redirect without spinning up a
  second Activity instance and losing in-memory `AppViewModel` state (ticked
  folders, selected contest, etc.) mid-flow.
- **Bespoke error handling, not the existing `describeError()` pattern.**
  The DustDevil endpoints' error table is bare HTTP status codes with
  different meanings at different steps (400 only at the start redirect,
  401/403/404 only at exchange) - not the `{code, message}` JSON envelope
  the rest of the API uses. Needs its own interpreter keyed on `httpCode`,
  not `code`.
- **UX cases to handle explicitly**: the pilot backing out of the Custom Tab
  without completing sign-in (no redirect ever arrives - needs a
  cancel/timeout affordance, not an indefinite spinner); the 400-at-start
  case (redirect URI not yet approved) needs its own distinct message; a
  "sign out / switch pilot" action once an entry is cached.

**Flow**:

1. One-time setup with the SoaringScoring dev (not code): confirm
   `contests:read` is already on our existing key (likely already true -
   `/contests` already works with it per the Gotchas list) or add it,
   register our app's `xcsoaringscoring://oauth-callback` redirect URI
   against that same key, get the key's public `client_key_id`.
2. "Sign in with SoaringScoring" button in Settings, shown only when no
   personal key override is set; replaces the manual entry-address field
   once tested (kept as fallback until then per the retirement timing above).
3. Opens a **Chrome Custom Tab** (not a WebView - login credentials should
   never pass through a view our app code could inspect) at
   `/api/auth/dustdevil/mobile-start?client_key_id=...`.
4. A manifest intent-filter on `xcsoaringscoring://oauth-callback` catches
   the redirect with the short-lived (2 minute), single-use code.
5. Redeem immediately - POST to `/auth/dustdevil-mobile/exchange` using
   `BuildConfig.SS_API_KEY` specifically. Must happen right away; the doc is
   explicit the code expires fast and can't be redeemed by a different key
   than the one that started the flow.
6. Store the result (pilot name/email + full entries list: contest, class,
   competition number, `localPart`) in DataStore. Upload flow becomes: signed
   in? → pick an entry from the list (or remember the last one) → upload
   using the app's key + that entry's `localPart`.
7. **Keep the manual address field as a fallback**, not a full replacement -
   the doc notes a contest DustDevil knows about but hasn't synced to
   SoaringScoring yet just won't appear in the entries list ("not an error"),
   so there's a real scenario where sign-in won't surface an entry a pilot
   actually needs.

**Still pending from the SoaringScoring dev before this can be tested
end-to-end**:

1. Confirmation that `contests:read` is (or can be) on our existing key.
2. The redirect URI (`xcsoaringscoring://oauth-callback`) registered and
   approved against that key.
3. That key's public `client_key_id`.

Until those land, `mobile-start` 400s immediately (per the doc, "rather than
starting a sign-in that could never complete") - so the client-side
scaffolding on this branch is built and ready, but the sign-in button stays
disabled with an explanatory message until a real `client_key_id` is
configured (mirrors the existing `BuildConfig.SS_API_KEY`-missing fallback
pattern).

## Known incidents worth remembering

- **Case-sensitive folder names caused silent data loss.** One XCSoar install
  used `Tasks`, another used `tasks`; the app's exact-case lookup only ever
  matched one of them, so overwrites silently succeeded on one device while
  the other quietly accumulated duplicate `Tasks (1)`, `Tasks (2)` ... folders
  that XCSoar never read from. Root-caused by testing on two real devices with
  two different XCSoar versions - not something reproducible from a single
  test device. Take device-specific folder/file behavior seriously; don't
  assume one confirmed-working device generalizes.
- **A scoped update once shipped without all its dependent files.** The
  waypoint "get waypoints" button was added across three files in one update;
  a follow-up fix touched two *different* files. If a branch was created from
  a point in history before the first update merged, applying just the second
  update's files left the UI referencing state the ViewModel didn't have
  wired up (or vice versa). Lesson: when re-applying a partial update onto an
  uncertain branch state, prefer re-shipping the *complete* set of files a
  feature touches, not just the newest diff.
- **A doc-only commit reverted unrelated doc updates because it was based on
  a stale copy.** A separate session wrote the DustDevil sign-in proposal
  into `CLAUDE.md`/`DEVELOPMENT.md` (commit `c0abcda`) from a version of
  those files that predated another session's same-day updates (merged
  API key behavior, the in-app help dialog shipping, waypoint/task filename
  changes) - so that commit silently dropped all of it even though the
  underlying *code* for those features was intact and committed separately.
  Neither session did anything wrong in isolation; the failure mode is
  editing shared markdown files without re-reading the current on-disk
  state immediately before writing. Re-read `CLAUDE.md`/`DEVELOPMENT.md`
  fresh before any edit that isn't a small, obviously-isolated addition.
- **A zip overlay once nested the project inside itself** (`SSTaskLoader/`
  containing another `SSTaskLoader/`), because extracting a zip whose root
  folder matches the destination folder's name merges incorrectly by default
  in some extraction tools. Silently caused Android Studio to keep building a
  stale copy for a while. When overlaying a scoped update: drag the *contents*
  of the top-level folder when it should merge into an existing folder, or
  drag the folder *itself* when its contents are meant to land at a new,
  correctly-named path one level down (e.g. a package folder like `app/`
  landing on top of an existing `app/`). Getting this backwards is the
  single most common failure mode in this project's update process.

## Release process

1. Bump `versionCode` (+1, never reuse) and `versionName` in
   `app/build.gradle.kts` (skip both for a genuine first release).
2. Build → Generate Signed Bundle/APK → release variant. Output filename is
   auto-versioned (`XCSoaringScoring-<versionName>-release.apk`) via a Gradle
   `applicationVariants.all` hook.
3. GitHub → Releases → Draft a new release → tag `vX.Y.Z` against `main` →
   attach the signed APK → publish.
4. Large/risky changes go through a feature branch + PR review before landing
   on `main`, not straight commits - see branching workflow discussed in
   project history if this needs re-explaining to a new contributor.

## Open items / roadmap

- **DustDevil.cloud sign-in** - see the dedicated section above. Client-side
  scaffolding in progress on the `OAuth` branch (Custom Tab launch, redirect
  handling, DustDevil API models, DataStore session storage). Blocked on the
  SoaringScoring dev for redirect URI approval + `client_key_id` before it
  can be tested end-to-end; the sign-in button stays disabled until then.
- **Personal API key retirement** - both Settings override fields (general
  and upload) are earmarked for removal once DustDevil sign-in has been
  tested for real, before release. Not done yet - see "Decisions locked in"
  above.
- **F-Droid submission** - blocked on the API-key distribution decision
  above, plus the usual F-Droid prerequisites (LICENSE file, committed
  Gradle wrapper, `fdroiddata` metadata PR).
- **Custom launcher icon** - currently a hand-drawn top-down glider silhouette
  vector (banked, gull-wing style), replaceable via Android Studio's Vector
  Asset tool or by generating new VectorDrawable XML directly.
- ~~**Project/display name**~~ - **Done (2026-09-05).** Full rename from
  SS Task Loader/SSTaskLoader to XCSoaringScoring, matching the GitHub repo
  rename: Kotlin package + `namespace` + `applicationId`
  (`com.soaringscoring.taskloader` → `com.soaringscoring.xcsoaringscoring`),
  `app_name` string resource, Gradle root project name, `Theme.SSTaskLoader` →
  `Theme.XCSoaringScoring`, release APK filename prefix, and the
  `proguard-rules.pro` keep rule (easy to miss - it hardcodes the old
  package path and silently stops matching on a rename, which only bites on
  a minified release build). The applicationId change means any device with
  the old build installed needs a fresh install, not an in-place update -
  see CLAUDE.md gotcha 14. The DustDevil redirect scheme was deliberately
  chosen not to depend on this rename (see the sign-in section above) and
  needed no changes.
