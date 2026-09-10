# Feature: "My Event" action + home screen layout rework

## Reframed from an earlier "Mine tab" idea - read this first

Earlier drafts of this spec proposed a persistent fourth tab. After
re-reading `DEVELOPMENT.md`'s existing "OAuth-aware task browsing shortcut"
design note and weighing screen real estate, the decision is to implement
that design note largely as originally proposed - **a single "My Event"
action, not a tab** - with new placement details this doc adds. Superseded
tab framing removed; this is the authoritative version.

This also folds in a related, previously-separate change: moving the
per-download "Save to" folder checkboxes off the home screen and into
Settings, since it's tightly coupled to the same screen's layout rework and
Claude Code will be touching the same file for both.

> **Status update (2026-09-10) - see DEVELOPMENT.md for the authoritative
> record.** Part 2 (home screen layout rework) has been built, with the
> "My Event" placement resolved differently than originally proposed below:
> Alternates moved to Settings too (not just "Save to"), so there's no
> Alternates row left on the home screen for "My Event" to share. Part 1
> ("My Event" itself) is held for now - a different, cheaper mechanism
> (the existing Check card's new "Open"/"Clear" actions, reusing
> `LastDownloadedTaskGroup`) is shipped instead, to see whether it's
> sufficient before building this too. This file is kept as the original
> spec for if/when Part 1 gets built - don't treat the placement details
> below as current.

## Part 1: "My Event" action

### Non-goals

- Not building or modifying sign-in itself - `DustDevilEntry` data and
  `DustDevilEntryPicker` already exist (used today by the Upload screen).
- **Not resolving handicap.** Nothing in the exchange response carries a
  pilot's glider/handicap - already logged as pending with the SoaringScoring
  dev in `DEVELOPMENT.md`. This resolves contest + class only; a DHT day
  still needs the existing manual handicap pick after landing on the task
  list.

### Critical technical requirement

**Must fetch real `Contest`/`ContestClass` objects** via `getContests()`/
`getClasses()` matching a `DustDevilEntry`'s `contestId`/`classId` - never a
synthetic `Contest` built from just the entry's `contestId`/`contestName`.
`ContestGrouping.categorize()` needs real `startDate`/`endDate`/`timezone`
to work at all, and `DustDevilEntry` doesn't carry those.

### Multi-entry handling

Reuse `DustDevilEntryPicker` (or its underlying data access) rather than
building a second picker for the same data - already established in the
existing design note. If it's only usable as a modal today, that's fine
here: this is a button-triggered action, not an embedded list, so a modal
picker is a natural fit (unlike the earlier tab framing, where it wasn't).

### Behavior

Tap "My Event" →
1. Resolve the entry (auto if the pilot has exactly one, `DustDevilEntryPicker`
   if more than one).
2. Fetch the real `Contest`/`ContestClass` for that entry's `contestId`/
   `classId` (see requirement above).
3. Navigate straight into the task list with that class pre-selected -
   existing `TaskFiltering`/`ContestGrouping` date logic handles the rest,
   unrelated to sign-in.
4. Not signed in, or signed in with no entries - button should be
   hidden/disabled with a clear reason, not silently do nothing on tap.

## Part 2: Home screen layout rework

### Move "Save to" folder checkboxes into Settings

**Rationale locked in**: most pilots only ever have one XCSoar variant
installed and will never touch this control - it's been costing every user
permanent vertical space to serve a minority "power user" case. Move
`TargetFolderCheckboxes` (currently on `ContestListScreen`) into
`SettingsScreen`, alongside the existing `MediaFolderAccessSetting`
(they're closely related - one grants access, the other picks among what
was found - consider whether they should visually merge into one section
now that both live in Settings, or stay as two adjacent sections; either is
fine, use judgement).

**Replace with a tappable one-line summary** on the home screen, above the
Current/Future/Past tabs: `To: org.xcsoar, com.zinuzoid.xcsoar_jet` (or
similar wording) - read-only display, tapping it navigates to Settings'
folder section. This is the actual point of making it tappable - it's a
fast path back to the control for the power-user case, not just a status
readout that dead-ends.

Worth checking whether the existing `SelectedFoldersSummary` composable
(already used non-interactively on `TaskListScreen`) can be generalized
with an optional `onClick` and reused here, rather than writing a second
near-identical summary line component.

### "My Event" placement: shares a row with the Alternates toggle

**Not a top app bar icon.** Explicitly rejected: the app bar's other icons
(Upload, Settings) navigate to entirely new screens; "My Event" modifies
the current screen's content instead, and grouping it visually with
navigate-away icons mixes two different kinds of action in one visual
block.

**Instead**: shorten the existing Alternates toggle's label to "Save
Alternates" and place "My Event" on the same row, `Arrangement.SpaceBetween`
- toggle + label on the left, "My Event" on the right. Needs to read as two
distinct controls sharing space, not one control group - achieve this via
different chrome (the toggle has no button outline; "My Event" should be an
outlined or tonal `Button`, not a plain text action) rather than relying on
spacing alone. Add a thin vertical divider between them only if, once built
and viewed on-device, chrome contrast plus spacing still isn't enough - not
a certainty either way, actually look at it before deciding.

**Real dependency, not yet resolved**: this placement only works if the
Alternates toggle stays on the home screen body. Whether it does is still
under discussion (may move to Settings too, pending input from more
experienced comp pilots on how often it's actually toggled mid-contest) -
**do not build this until that's settled**, since if Alternates moves to
Settings, "My Event" loses its row-mate and needs a different placement
(a standalone row - the top-app-bar rejection above still holds regardless
of what happens to Alternates, so don't fall back to an icon there).

## Touch points

- `ui/screens/ContestListScreen.kt` - remove `TargetFolderCheckboxes`, add
  tappable folder summary line, add "My Event" + Alternates toggle row
  (pending the dependency above), entry-resolution navigation.
- `ui/screens/SettingsScreen.kt` - add `TargetFolderCheckboxes`.
- `ui/screens/FolderPicker.kt` (or wherever `TargetFolderCheckboxes`/
  `MediaFolderAccessSetting`/`SelectedFoldersSummary` currently live) -
  relocate/generalize as needed per the above.
- `ui/AppViewModel.kt` - "My Event" entry resolution (fetch real Contest/
  ContestClass, navigate).
- `MainActivity.kt` - wire any new callbacks.
- `DEVELOPMENT.md` - once built, mark the "OAuth-aware task browsing
  shortcut" design note as implemented (not superseded by a tab - this
  file now matches that note's original shape, just with concrete
  placement details it didn't originally specify).

## Conventions to follow (see CLAUDE.md / DEVELOPMENT.md)

- Card-based UI, consistent with the rest of this app.
- Multi-file change touching shared state and more than one screen - ship
  and review as one unit, per this repo's own "Known incidents" entry
  about partial multi-file updates causing real regressions.
