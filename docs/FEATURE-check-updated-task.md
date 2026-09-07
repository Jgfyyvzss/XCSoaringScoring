# Feature: Task alternates + quick official-task check

Supersedes the original `FEATURE-check-updated-task.md` draft. Scope grew
during review: SoaringScoring publishes alternate tasks *before* one is
flagged official ("Published tasks — including alternates — are available
before a task is made official... you can prepare while the day firms up" -
pilot guide), and potentially **none** are official until a later
announcement. That reframes this from "notice when the one task I downloaded
changed" into "grab everything on offer up front, then quickly resolve
whichever one becomes official later - using what's already on the device
whenever possible, since connectivity at the launch is typically worse than
at the pilot briefing."

## Purpose

1. **Drill-down download**: when a pilot downloads a day/class(/handicap),
   fetch and save *every* candidate task for that slot, not just the one they
   tapped - they get all alternates on the device even if none is official
   yet. Only the one flagged `isOfficialTask == true` (if any) becomes
   `default.tsk`.
2. **Check button**: from the contest list, one tap resolves "what is the
   current official task for the last thing I downloaded" - reusing an
   already-downloaded alternate's bytes with no network file transfer if
   possible, downloading fresh only if the official task is one we've never
   seen.

**Confirmed assumption**: single global "last downloaded group" slot, not a
history or per-contest memory. Explicitly accepted, not deferred - "unlikely
anyone will randomly download non-current tasks to their instrument; if they
do, that's their problem."

**Explicitly out of scope**: if new *alternates* appear after the initial
bulk download (the candidate pool grows), Check does not go fetch them - it
only resolves the official flag. A brand-new *official* task is always
fetched by Check (see outcomes below) regardless of whether it was ever seen
as an alternate; only a still-unofficial new alternate is missed, and needs a
fresh drill-down download to pick up.

## Data model changes

### `data/SettingsRepository.kt`

Replaces the original spec's flat `LastDownloadedTask` entirely.

```kotlin
@Serializable
data class DownloadedTaskVariant(
    val taskId: String,
    val fileName: String,           // retained original server filename
    val wasOfficialAtDownload: Boolean
)

@Serializable
data class LastDownloadedTaskGroup(
    val contestId: String,
    val contestName: String,
    val classId: String,
    val className: String,
    val dayId: String,
    val dhtHandicap: Double? = null,
    val variants: List<DownloadedTaskVariant>,
    val confirmedOfficialTaskId: String? = null   // last taskId actually written to default.tsk
)
```

Store as JSON under one DataStore key (`last_downloaded_task_group`), same
pattern as `dustDevilSession` (nullable `Flow`, plain suspend setter, reuse
the repository's existing `Json { ignoreUnknownKeys = true }` instance - no
new dependency).

**Also remove, fully, as dead code** (confirmed in review - the *write* side
is live but nothing ever reads it, so this isn't cosmetic):
- `lastContestId` / `lastContestName` `Flow`s and their `Keys` entries.
- `setLastContest(id, name)`.
- Its call site in `AppViewModel.selectContest()`.

## Filename policy (resolves the original spec's open filename question)

Tasks currently **do not** retain the server's original filename - only
waypoints do (`fileNameFromResponse()` already exists in
`SoaringScoringApi.kt` and works for any download, it's just unused for
tasks today). New policy, applied to every variant in a group:

1. Save under the **retained original filename** from the server. This
   replaces `soaringscoring_task.tsk` outright - a generic name can't
   distinguish alternate A from alternate B, but real published filenames
   presumably can.
2. If that specific variant is `isOfficialTask == true`, **additionally**
   write the same bytes to `default.tsk`.
3. **Collision safety net**: if the server doesn't supply a distinguishing
   filename for two variants in the same batch (missing/generic
   `Content-Disposition`), the existing URL-last-segment fallback could
   collide in a way it never could before when a download was always exactly
   one file. When falling back and the group has more than one variant,
   suffix the fallback name with a short slice of `taskId` so a collision is
   structurally impossible.

## `storage/XcsoarFolderStore.kt` changes

New capability needed - everything here is currently write-only:

```kotlin
/**
 * Reads back a previously-written task file's bytes, or null if it can't be
 * found/read. Used by the Check flow to reuse an already-downloaded
 * alternate's bytes instead of re-fetching over the network.
 */
fun readTaskFile(context: Context, xcsoarFolder: DocumentFile, filename: String): ByteArray?
```

Read-only, resolves through the same `tasks`/`Tasks` subfolder logic as the
write path. A `findFile()` miss here (the known device/provider flakiness -
see CLAUDE.md gotcha 4) has a **safe failure mode**: the Check flow just
falls through to a network download instead, same as "never seen this task
before." No risk of silently serving stale/wrong bytes.

## `ui/ContestGrouping.kt` changes

New grouping on top of the existing `TaskFiltering.visibleTasks()`:

```kotlin
data class TaskGroup(
    val dayId: String,
    val classId: String?,
    val dhtHandicap: Double?,
    val variants: List<TaskRow>   // 1+ rows sharing this key
)

fun groupedVisibleTasks(...): List<TaskGroup>
```

Groups `visibleTasks()`'s output by `(dayId, classId, dhtHandicap)` -
the same key the Check flow matches on, so both features agree on what
"one decision point" means. A group's `variants` list can contain zero,
one, or (per the pilot guide) even briefly *multiple simultaneously flagged*
official rows should never happen in practice, but the download/check logic
below treats "official" as `variants.firstOrNull { it.isOfficialTask }` and
doesn't assume exactly one.

## `ui/AppViewModel.kt` changes

### Download (replaces `downloadTask()`'s single-row flow)

`fun downloadTaskGroup(variants: List<TaskRow>)`:

1. Same guards as today's `downloadTask()` (blank key, no folders ticked).
2. For each variant, sequentially (not parallel - consistent with the
   existing caution about not piling concurrent load onto a tasks endpoint
   already documented as slow): download via the existing
   `api.downloadTaskFile()`, write under its retained filename (§ Filename
   policy) to every ticked folder, and - only if that variant is official -
   also write to `default.tsk`.
3. A variant only counts as "successfully cached" if at least one folder
   write succeeded (`okCount > 0`) - matches the earlier-agreed principle of
   not recording something as downloaded that never reached disk.
4. Persist `LastDownloadedTaskGroup` from whichever variants actually
   succeeded; `confirmedOfficialTaskId` = the official variant's `taskId` if
   one was present and wrote successfully, else `null`.
5. One failed variant doesn't abort the batch - keep going, then report
   what actually happened.
6. Status message, **always this exact template**, regardless of counts
   (no special-casing the common 1-active/0-alternate case):
   `"Downloaded {activeCount} active task, {alternateCount} alternates into {folderCount} folder(s)."`
   Append a short failure note if any variant's download failed outright.

### Check (`checkForUpdatedTask()`)

1. Guard: no-op if no `LastDownloadedTaskGroup`.
2. `api.getTasks(group.contestId, apiKey)` - the only network dependency;
   lightweight metadata, not a file transfer.
3. Filter to rows matching `group.dayId` / `group.classId` / `group.dhtHandicap`.
4. Find `officialRow = matches.firstOrNull { it.isOfficialTask }`.
5. Outcomes:
   - No official row at all -> `NoOfficialYet`.
   - `officialRow.taskId == group.confirmedOfficialTaskId` -> `NoChange`.
   - `officialRow.taskId` is in `group.variants` (downloaded earlier as an
     alternate, not official then) -> `ConfirmedLocally`: read that
     variant's bytes back via the new `readTaskFile()` (try each ticked
     folder in order until one succeeds), write them to `default.tsk` in
     every ticked folder, update `confirmedOfficialTaskId`. **No network
     file transfer.** If the local read fails everywhere, fall through to
     the next case rather than error out.
   - `officialRow.taskId` unknown locally (brand new task, never downloaded
     as an alternate) -> `NeedsDownload`: fetch it now via
     `api.downloadTaskFile()`, write under its retained filename **and**
     `default.tsk`, and add it to `group.variants` going forward (so a
     later Check against the same task is a local hit, not another
     download). This is the "quickly grab the newly-published official
     task" path - always resolves the official task one way or another,
     network or local.
   - API/network failure on the metadata call -> `Error(message)` via the
     existing `describeError()` mapping.
6. `checkingForUpdate = true` for the duration, matching other in-flight
   flags already in this file.

```kotlin
sealed class UpdateCheckOutcome {
    object NoOfficialYet : UpdateCheckOutcome()
    object NoChange : UpdateCheckOutcome()
    data class ConfirmedLocally(val fileName: String) : UpdateCheckOutcome()
    data class NeedsDownload(val newTask: TaskRow) : UpdateCheckOutcome()
    data class Error(val message: String) : UpdateCheckOutcome()
}
```

`NeedsDownload`'s actual fetch-and-write can reuse the same per-variant
download logic `downloadTaskGroup()` uses internally for one row, rather
than duplicating it.

## UI changes

### `ui/screens/TaskListScreen.kt`

Task list now renders **one card per `TaskGroup`**, not one per `TaskRow`
(Option B, chosen over leaving alternates as separate near-duplicate cards).
Card shows day/class/handicap plus either "✓ Official confirmed" or "N
candidate tasks - not yet decided," single download action wired to
`onDownloadGroup(group.variants)`.

### `ui/screens/ContestListScreen.kt`

Unchanged in shape from the original spec - a card below the folder
checkboxes, above the Current/Future/Past tabs, visible only when a
`LastDownloadedTaskGroup` exists, showing contest/class/handicap and a
"Check for updated task" action (spinner while `checkingForUpdate`). Outcome
dialogs updated for the new five-case sealed class instead of three:
`NoOfficialYet`/`NoChange` -> simple info dialog; `ConfirmedLocally` ->
"Official task confirmed and applied" (no Cancel/Download choice needed,
it's already done); `NeedsDownload` -> the original Cancel/Download
confirmation; `Error` -> error dialog.

### `MainActivity.kt`

Wire `onDownloadGroup`, `onCheckForUpdate`, `onConfirmUpdatedDownload`,
`onDismissUpdateOutcome` into the existing composable calls.

## Conventions to follow (unchanged from original spec)

- No Retrofit, no new API surface beyond the existing `getTasks()` /
  `downloadTaskFile()`.
- Card-based UI, result dialogs not snackbars, errors through
  `describeError()` - all as already established.

## Known tradeoff (unchanged from original spec)

`GET /contests/:id/tasks` is documented as slow on large contests - don't
add an aggressive client-side timeout on the Check flow's metadata call.

## Open items for discussion

1. Exact fallback-filename disambiguation format (taskId slice length,
   separator) - cosmetic, fine to settle during implementation.
2. Exact card/dialog copy for the new grouped UI and outcome set - cosmetic.
3. No "forget last downloaded group" / manual reset action has been
   specified (DustDevil sign-in has an equivalent "Sign out"). Worth
   deciding whether pilots need a manual escape hatch, or whether it's
   unnecessary since downloading any other group just overwrites the slot
   naturally.
4. `group.variants` is never pruned - if a later Check or download narrows
   the candidate set (an alternate quietly withdrawn), stale entries just
   sit in the persisted record and in the Tasks folder. Harmless in
   practice (small JSON, and the earlier decision was explicitly not to
   auto-delete files from the pilot's XCSoar folder), but flagging so it's a
   deliberate non-goal rather than an oversight.
