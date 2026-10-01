# Phase 16 r3 — Reviewer 2 (opus, testability / evidence integrity), 2026-09-30

As returned (written here by the lead; subagents do not write report files). Read-only; nothing run on the device. Doc
lines are `l.N` of `docs/plan/phase-16-inbox-calendar-people.md`; script paths are under `docs/plan/qa/`, code under
`app/src/main/kotlin/app/tileshell/`.

## BLOCKING

**V1 — E26 (a) and (c) cannot pass on phase 12 as built (l.75-76, l.824-837).**
- Evidence: `onboarding/SetupWizard.kt:76-84` — "A PARTIAL row never summons the wizard"; `visibility` returns `CoreHeld`
  unless a grant row is MISSING, and `CoreHeld` outranks `Finished`.
- Revoking only WRITE_CONTACTS leaves `setup:people` PARTIAL, so (a) shows no `wizard_page` and (c) logs `not shown: core
  held`, not `finished`.
- Fix (a): revoke READ_CONTACTS and WRITE_CONTACTS. Expect `[wizard] shown: missing=setup:people,tess:contacts`, "Step 1 of
  3", `wizard_step:setup:people` first. `pm grant READ_CONTACTS` + resume → `[wizard] step setup:people: partial`, step
  stays. Grant WRITE → `granted`, then `wizard_presets`.
- Fix (c): revoke both → `not shown: finished`, `checklist:people:missing`. Add: WRITE only → `not shown: core held`,
  `checklist:people:partial`.
- `phase-12/scripts/e14.sh:29,31` hard-codes "Step 1 of 2" and a single missing step, so E26 needs its own driver.

**V2 — E1's wiped-leg `-> assigned` lines are unobservable (l.553-554).**
- Evidence: the ring is in memory (`diag/Diagnostics.kt:14-26`). `phase-03/scripts/provision.sh:65,68` start the process
  (listener and IME binds) and `:160` force-stops it. The surviving process logs `already run` (`tiles/LayoutStore.kt:137`).
- Fix: the wiped leg asserts `layout_json` instead: `addedOnce` ⊇ both markers, `slots.CALENDAR` / `slots.PEOPLE` = the
  shell's components.
- The `-> assigned` form is asserted in the upgrade leg for CALENDAR, from a MARK taken before `adb install -r` (no
  force-stop follows it).
- Also assert `slots.PEOPLE` in `layout_json` before the upgrade, so the hand assignment is proven explicit.

**V3 — P2 is an adb row on the phone (l.855-859: "`content query` over adb", "`dumpsys netstats --uid`").**
- Phone rows are done on the phone alone, per the brief.
- Fix: counts are read in the app. Add a line `[calendar] counts: <displayName>=<n>, …` written when `cal_pane` opens, read
  on Settings > Diagnostics before and after.
- Replace the netstats clause with: "Sync tapped in airplane mode still reads 'synced to <calendar>'; the copy reaches the
  other client only after airplane mode is off."

## SHOULD-FIX

**V4 — E17 jumps the clock backwards to 2026-09-23 (l.715).**
- `ring_since` keeps `wall >= mark` (`phase-03/scripts/lib.sh:196-197`), so every pre-jump line passes the filter and
  `[calendar] birthdays: 1 synced` can pass stale.
- Fix: the driver sets `data1` to the device's own month-day (year 1990) and makes no jump.
- Preamble: "a row never jumps backwards; if it must (the edge case at l.940), it runs `ring_save`, force-stop,
  `ensure_start` before its MARK."

**V5 — Phase 14 E3 breaks on this build and no row re-cuts it (l.159-163 name only phases 01 and 03).**
- `phase-14/scripts/e3.sh:41` requires exactly one APP_CALENDAR activity; `:313-314` expect that component.
- Fix: E1 adds "re-cut of phase 14 E3's Agenda launch": under the phase-16 baseline, `tap_node pod_header:agenda` resumes
  the shell's Calendar and logs `[podbay] launch agenda -> app.tileshell/<Calendar activity>`. The expected component is
  the baseline file's `slots.CALENDAR`.
- With the calendar empty, `pod_empty:agenda` is unchanged. Change Log line.

**V6 — E21 cannot pass as scoped, and no row runs the Edge cases (l.766-771, l.902-971).**
- Alternatives no E-row produces: `calendars: none`, `write … failed`, `sync … refused (not allowed)` (unreachable from the
  UI by design), `failed mapping stale`, `local calendar lookup failed`, `link … failed`, `[people] write … failed`, and
  `sim import` when the SIM refuses.
- Fix: phase 15 E22's form (`phase-15-…md` l.1187-1194): every `|` alternative, a `producers.tsv`, a `notrun.tsv` with
  reasons, and the edge drivers' slices included.
- Add an EDGE row with `edge_index.tsv` mapping every bullet to a sub-step, row or JVM test (INDEX.md:196 made this
  BLOCKING at phase 11's gate).
- Not drivable on the AVD: phone off across a reminder → P7; SIM removed mid-import → P4; undecodable tile photo → a JVM
  test of `PeopleFeed`'s skip.

**V7 — E6 has two clauses that do not test the shell (l.612-616).**
- "alert state fired": by the doc's own premise (l.201) the AOSP Calendar also handles the reminder, and a calendar app
  marks its alerts fired. Run that clause with `pm disable-user com.android.calendar`, then re-enable for the recorded double.
- "NOT under `app.tileshell`": the shell arms its own alarms (`cortana/reminders/ReminderScheduler.kt`,
  `clock/RingService.kt`). Assert the pending `app.tileshell` alarm count is equal before and after the reminder insert
  (`phase-15/scripts/p15.sh:82-91`'s form).

**V8 — Absence and text reads have no anchor (tags l.250-261).**
- Add `cal_event_page:<id>` for E3 l.588 and E22 l.774; without it "no `cal_event_action:*`" passes on a missed tap.
- Add `cal_event_time:<id>` (E8 l.633 reads the time off the title node) and `cal_allday:<yyyy-mm-dd>` (E5 l.604).
- Add `cal_notice` / `people_notice` as persistent nodes, not toasts.
- E2's zero count (l.578) first asserts the slice holds an `assignSlotOnce` line (`phase-14/scripts/p14.sh:76`) and uses
  `absent_in` (`p14.sh:156-162`).

**V9 — The preamble predates the floor (l.504-548).**
- "Home" must be `ensure_start` (`phase-03/scripts/lib.sh:287-309`); the doc never mentions it or the pod bay.
- Never-idle dumps are `gdump` (`phase-15/scripts/p15.sh:25-49`), including E25's bursts over the cycling People tile.
- Clock moves are `jump_clock` / `clock_restore` (`p15.sh:56-78`).
- `mkcal` lives inside `j6.sh:18-20` and cannot be sourced.
- Fix: build task 8 creates `qa/phase-16/scripts/p16.sh` holding these plus `mkcal`, `absent_in`, `c6`.
- Stale cites: `lib.sh:47-56` → `:49-58` (l.525); `lib.sh:141-145` → `:155-159` (l.276); `provision.sh:66` → `:89` (l.706).

**V10 — RV12 restores are missing and rows depend on each other (PLAN.md:329).**
- No restore: E3 (Tess's "standup"), E5, E6 (event, reminders, notification), E8, E9, E10, E12 (slots and the "Always"
  preference, `pm clear-package-preferred-activities`), E14, E15, E17, E18 (READ_CONTACTS left revoked).
- Dependencies: E8 ← E5, E11 ← E13, E17 ← E10, E23 and E9 ← E17.
- Fix: `p16.sh` gets `people_fixtures_up/down` (their own account, not `qa`/`qa`, which is Mom's — `provision.sh:125-126`)
  and `cal_fixtures_down`. Every row calls them.
- Give the Birthdays delete command, with `account_name=Tessera%20Birthdays`.

**V11 — The Sync negatives can pass on a vanished calendar (l.542-543, l.791, l.809).**
- "Work 0" is also what a deleted Work calendar reads.
- From memory of AOSP, not checked here: CalendarProvider drops non-LOCAL calendars whose account does not exist when its
  process restarts. Verify at build start.
- Fix: every count read first asserts the calendar's `_id` still lists, and E23 / E24 keep E22's "Offsite" sentinel (Work =
  exactly Offsite).

**V12 — E18 produces a state the doc calls impossible (l.728-729, l.952-953).**
- `pm revoke` is per permission, so E18 leaves WRITE_CONTACTS held with READ revoked.
- Fix: state the rule — MISSING when READ is not held, whatever WRITE is; PARTIAL when READ is held and WRITE is not.
  Rewrite the edge case to "reachable by adb (E18): reads MISSING".

**V13 — E24's overwrite contradicts the hash rule (l.306-310, l.801-802, l.920-921).**
- The hash is of the local fields at the last push, so it is unchanged when only the copy was edited; the step then reads
  a no-op.
- Fix: "Sync re-reads the copy and compares it with the local event; it writes when they differ"; E24's step expects `updated`.

**V14 — E11's photo proof is unassertable (l.243 "at random", l.664-666).**
- Fix: the event line gains `lookup=<key>`; fixtures are solid-colour JPEGs; the settled bubble's centre pixel equals that
  contact's colour ± 8 levels.
- People's pivot `[motion]` (l.340) has no row: add it to E20.

**V15 — Fixture preconditions come from the provider, not from assumption.**
- E10 (l.648): assert two raw contacts and one `contacts` row for Cara Diaz before reading the app. Give the pair one
  shared number, since same-account name-only pairs may not aggregate.
- E14 (l.686): name one account for both Sam Reeds.
- E3 (l.581-583): "exactly one row" needs the Birthdays creation trigger stated ("created when the first birthday exists").
- E23 (l.793): assert the new `_ID` ≠ the old; create Work after Personal so the id is not reused.

**V16 — E15 (l.691-698).**
- `<the stream uri>` has no stated source, and the manifest has no exported file provider
  (`AndroidManifest.xml:226-230, 325-328`), so `content read` as the shell user is refused.
- Fix: a line `[people] share <lookup>: <uri> file=<cache path> bytes=<n>`, read with `run-as app.tileshell cat`.
- SIM: always run the import so `sim import: 0 of 0` exists for E21.

**V17 — E16 (l.704-710).**
- TestDPC is not in `~/android-fixtures`, and `provision.sh:89-106` would install it into user 0 for every phase.
- Fix: build task 8 owns the fixture at `qa/phase-16/fixtures/` (not committed); it is installed with `--user <profile>`
  inside the row only.
- Set it as profile owner before the positive leg, so the two legs differ only by the switch.

**V18 — "By any path" omits the exported intents (l.34-36, l.115-116, l.941-943).**
- Add to E22: `am start -a android.intent.action.EDIT -d content://com.android.calendar/events/<Offsite id>` → no
  `cal_editor`, Offsite's row unchanged.
- Add: `am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/event --el calendar_id <work id> --es title
  Intruder`, saved → the event is in Tessera, Work is still exactly Offsite.

**V19 — H13 is a catch-all nobody can judge (l.893).**
- Replace with named [accept] rows: the three motion approximations (l.379-380); the shortcut set, its icons and the
  "Month" behaviour (l.98-100); the two app icons and static tiles; the `setup:people` why line and every notice string
  (l.70-71 points at phase 12's H1, already gated).

## NOTE

**V20 — Tess steps.**
- `type_request` cannot carry an apostrophe (`lib.sh:347-348`). E9 (l.643) and E17 (l.716) type "what is on my calendar"
  (`cortana/match/CommandMatcher.kt:101`).
- "on 'yes'" (l.641) becomes a tap on `cortana_card_button:confirm` (`j6.sh:35-37`). No row uses the microphone.

**V21 — Smaller row fixes.**
- E12 (l.670): K-9 with no account may not stay on compose. Assert a `[people] action mail -> <component> mailto:…` line
  and the top package instead.
- E7 (l.622): take the MARK immediately before the `cal_month_cell` tap.
- E24 (l.799): "phase 06 E18's form" has no driver (no `qa/phase-06`); write the command out.

## Checked and correctly applied
T16-2 (`j6.sh:40-41` looks up by name), T16-3, T16-4, T16-7, T16-8, T16-11, T16-12, T16-14, T16-16, T16-17, T16-19; C-5 /
C-31, C-6, C-8 (line form matches `start/QuickRules.kt:99`), C-13 / C-26 (`record`, `lib.sh:422-426`), C-17, C-19, C-20
(`lib.sh:177-220`), C-25.

Not correct as applied: C-4 / C-15 / T16-15 (V1), C-3 (V8), C-10 (V9), T16-1 (V13), T16-18 (V17).

BLOCKING: 3 · SHOULD-FIX: 16 · NOTE: 2
