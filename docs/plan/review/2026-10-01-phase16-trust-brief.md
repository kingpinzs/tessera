# Phase 16 — adversarial trust review, brief (2026-10-01)

Reviewers: two Opus subagents (`model: opus`), run in parallel, each with its own lens; neither sees the other's
report. Never Fable, never Sonnet, never Gemini. Codex is out until 2026-10-03 16:48 (CLI limit; its MCP fails to
connect).

- **Reviewer A — the write paths.** The calendar write guard and its one write layer, Sync and `calendar_sync.json`,
  the People write guard and `people_edit.json`, Tess's calendar insert and delete.
- **Reviewer B — the exported surface.** The intent handlers on `CalendarActivity` and `PeopleActivity`, the exported
  reminder receiver and its notification over the lock screen, the vCard share, `ACTION_PICK`'s result grant, the
  manifest and the exported allow-list.

## Mode: adversarial

Assume the author is wrong. Commit messages, code comments and the phase doc's own claims are marketing until you have
proved them; the default verdict on every item is **not proven**. You are given no list of "known issues" on purpose.
You may read anything in the worktree; you change nothing in it (work on copies under your scratch prefix).

## What the owner ruled (the things that must hold)

1. **Calendar (Q2 D, 2026-09-23):** the shell shows every calendar and writes ONLY to its own local calendar
   (`Tessera`). The one road to an account calendar is a Sync the user taps, to a calendar the user has ticked on "Can
   sync to" (nothing ticked by default). "I dont want to add anything to my work calander from my phone ever." No path
   — the app, Tess, Sync, an intent from another app, the reminder receiver — may write an account calendar otherwise.
2. **People (Q-16-3, 2026-09-30):** People edits, deletes and adds contacts only for phone-only contacts and for
   accounts ticked on "Can edit" (nothing ticked by default); every other account, work included, is read-only.
3. **One event, one reminder (Q-16-2):** a synced copy is hidden in the shell while its original exists.
4. The phase doc's "Trust" line (Decisions, r3 D10) lists the parts: (a) the calendar write guard, four cases; (b)
   `WRITE_CONTACTS` and the People write guard with "Can edit"; (c) the exported VIEW / EDIT / INSERT / PICK handlers —
   an INSERT prefill never saves without the user's tap and ignores a `calendar_id` extra and any account a contact
   INSERT names, EDIT on what the shell may not write opens it read-only, PICK grants one URI; (d) the reminder
   receiver, exported, a poke only; (e) the Sync path and `calendar_sync.json`, whose `allowed` list is the only gate to
   an account calendar; (f) the vCard share — the Contacts provider's own stream, a read grant for that one URI; (g)
   the reminder notification over the lock screen (`VISIBILITY_PRIVATE`).

## What you must do (each with real output quoted; a claim without a command and its output counts as not done)

1. **Find every write.** Search the whole app source (not only `calendar/` and `people/`) for every call that can
   write `CalendarContract` or `ContactsContract`: `insert`, `update`, `delete`, `bulkInsert`, `applyBatch`,
   `ContentProviderOperation`, `call`, and any `Intent` the shell sends that makes another app write on its behalf.
   For each: does it go through the one write layer behind the guard? List every one that does not. A write that
   bypasses the guard is a finding whatever calendar or account it happens to target today.
2. **Independent red-proof of the guards' tests.** In a scratch copy, break each guard on purpose — one mutation at a
   time: a case's condition inverted, two branches reordered, the allow-list check removed, the stale-mapping re-read
   removed, the access-level threshold changed, "every raw contact editable" turned into "any" — keep the tests as they
   are, run them, and report which mutations the tests catch and which they do not. A mutation no test catches is a
   finding. Also report any test that pins a defect as correct behaviour.
3. **Both halves of each rule.** For every rule with an allowed side and a refused side, confirm both are tested and
   both are reachable. Name the untested halves.
4. **The files that gate.** `calendar_sync.json` and `people_edit.json`: who can write them (only the app's own
   code?), what a corrupt, truncated, hand-edited or missing file yields (must fail closed: nothing allowed), whether a
   stale `_ID` can be re-used by a different calendar (BUILDSTART found calendar ids ARE reused after a delete — does
   the key hold account name and type as well, and is all of it compared?), whether an account removed and re-added
   starts NOT allowed.
5. **The exported handlers.** Read the intent parsers and the activities. Try, by reading and by running where the
   emulator is free (ask the lead for a device session — do not take the device yourself): an INSERT with
   `calendar_id`, with a named account, with oversize or wrong-typed extras, with a hostile Parcelable in the extras;
   EDIT on an event outside Tessera and on a contact in an account not allowed; VIEW with a URI of another authority or
   a path-traversing URI; PICK — what exactly is granted in the result (flags, URI, ClipData) and can the caller widen
   it; whether any handler can be driven to save, delete or send without a tap; whether any caller-supplied text
   reaches the diagnostics ring or the logcat.
6. **The receiver.** Is anything read from the incoming intent? Can a forged broadcast cause a notification, a
   provider write other than the alert-state update, repeated work (a flood), or a crash? Is the notified set
   bounded? Does the notification's public version leak a title? Can its content intent be hijacked (PendingIntent
   mutability, implicit intents)?
7. **The share.** Is the stream strictly the provider's own vCard URI for the one contact? What does the grant cover?
8. **Fail-open paths.** Any `runCatching`, `catch`, default value or null fallback on a path that decides "allowed":
   does the failure deny?
9. **The manifest.** Every component, its `exported` value, its filters and permissions, against
   `docs/plan/qa/phase-03/exported-allowlist.txt`; anything exported that the phase did not need.

## Your report

Write nothing into the repo. Your final message is the report: for each numbered item, a verdict (PROVEN / NOT PROVEN /
FINDING), the commands you ran with their real output, and for each finding its severity (HIGH: the owner's rule can be
broken; MEDIUM: a hardening gap or an untested half; LOW: hygiene), the file and lines, and the smallest fix. End with
the list of what you could not check and why.
