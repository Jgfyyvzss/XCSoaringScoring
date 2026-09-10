# Feature: First-run help, with explicit icon-annotated setup steps

## Read this first - don't trust prior assumptions about the current code

This project has moved across sessions/branches in ways not fully reflected
in earlier planning docs. Before writing anything, locate the real current
state of each of these rather than assuming names/locations from history:

- The existing help content/dialog (there is reportedly already a help
  entry point in Settings from a prior session - find it, read it, reuse
  its content/styling rather than guessing at its current shape).
- The existing settings-persistence pattern (this app already stores
  various one-time/ongoing preferences somewhere - follow whatever that
  actual current pattern is for a new "has the pilot seen first-run help"
  flag, using its real naming conventions, not invented ones).
- The actual current icons used for Settings and for Help (and anything
  else referenced in the steps below) - reuse those exact icon references.
  Don't introduce different icons that would make the help text mismatch
  what's actually on screen.
- The actual current label on the folder-access control (referenced below
  as "Choose Android/media") - confirm it still reads that way; adjust the
  content if it's drifted.

## Trigger behavior

- Shows automatically on first launch only, before anything else - before
  folder-permission prompts, before contest data finishes loading. The
  content depends on no loaded data, so there's no reason to wait.
- Blocking - dismissed only via an explicit button, not by tapping outside.
- Persists that it's been seen; never auto-appears again after first
  dismissal.
- Stays reachable manually afterward via whatever the existing help entry
  point already is - this adds a second trigger path, it doesn't replace
  the manual one.

## Why this matters - real user feedback, not a hypothetical

A tester got frustrated trying "all sorts of other folders" instead of the
correct one, despite the control being labeled reasonably clearly. The
actual confusion is with **Android's own system folder-picker UI**, not
this app's wording - specifically, that opening the right folder isn't
enough on its own; there's a separate confirm button at the bottom of the
system dialog that's easy to miss. The content below is written to address
that specific, demonstrated failure point, not just to be generically
helpful.

## Step content (first-time setup)

Cover, in this order:

1. Open Settings.
2. Tap "Choose Android/media" (verify this still matches the real current
   label per the note above).
3. In the system folder browser that opens: navigate Internal storage →
   Android → media, then tap to open the "media" folder itself.
4. **Explicitly call out the confirm button** at the bottom of that system
   screen - commonly labeled "USE THIS FOLDER" or "SELECT." Make clear that
   opening/viewing the folder isn't enough by itself; this button is what
   actually grants access. This is the specific step testing showed people
   miss - don't undersell its emphasis relative to the other steps.
5. Note Android may show a one-time warning/confirmation for this folder -
   expected behavior, not an error, tap through it.
6. Note this is one-time setup - not needed again unless the app is
   reinstalled.
7. Note the shortcut: a pilot with only one XCSoar variant installed can
   pick that app's own folder directly instead of "media."

## Icon rendering

- Any step referencing an on-screen icon this app itself displays (e.g.
  "tap the Settings icon") should render that icon inline next to the step
  text, using the app's real, current icon reference for that control - not
  a new or approximate icon.
- Steps referencing Android's own system UI (the folder picker's confirm
  button) have no in-app icon to reuse - keep those as clearly emphasized
  text (e.g. bold) instead of inventing an icon that might not match what a
  given device/Android version actually shows.

## Conventions

- Reuse existing patterns (settings persistence, dialog styling, icon
  usage) rather than introducing new ones for what's fundamentally a
  presentation-layer feature.
- Touches whatever screen renders first (likely the home screen) plus
  wherever help content and settings persistence currently live - read
  both before starting, per the top note.

## Out of scope

- Does not change the manual help trigger's existing behavior beyond
  potentially reusing its content/dialog for this automatic trigger too.
- Does not attempt to detect or special-case different Android versions'
  folder-picker wording - the steps describe the common case. If a specific
  device shows meaningfully different system UI text, that's a
  content-accuracy note for a human to catch during testing, not something
  to branch on in code.
