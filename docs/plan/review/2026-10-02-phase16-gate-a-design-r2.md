# Phase 16 gate review — Reviewer A (design and correctness), round 2 (2026-10-02)

Saved by the lead from the reviewer's hand-back (the same independent Opus agent as round 1, read-only; brief
`2026-10-02-phase16-gate-r2-brief.md`). `…/app` = `app/src/main/kotlin/app/tileshell`, `…/qa` = `docs/plan/qa/phase-16`.
Nothing was run.

LENS A — ROUND 2

## The three blockers

- **A-1 CLEARED.** Code: `…/app/calendar/InstanceWindow.kt:24-36` — the range reaches back to the local start of today,
  and an all-day instance counts by its UTC dates against the local day. Used at `…/app/feeds/CalendarFeed.kt:106,123`
  and `…/app/calendar/TessCalendar.kt:33,44`. Evidence: `…/qa/E17-legs-T-on-3c5e3199-22-0-3/E17.txt:36-40` — at 19:00
  MDT the tile and Tess show the local today's birthday and not tomorrow's.
  - Behind and ahead of UTC: right (the range and the date test worked at UTC−12 and UTC+14 as well as the tested
    Denver and Sydney cases).
  - Several days: right (`eventLast = utcDate(end − 1)`, line 35).
  - Timed side unchanged: `…/qa/E4-legs-W-on-e03a1d23-15-0-3/E4.txt` and `…/qa/E9-legs-A-W-on-e03a1d23-17-0-4/E9.txt`,
    both 0 failed.
- **A-2 CLEARED.** Code: `…/app/calendar/CalendarViews.kt:120` keeps the loaded window while a wider one with the same
  start loads; `:168` and `:266-267` ask for more only from a wholly loaded window. Evidence:
  `…/qa/E5-legs-D-on-e03a1d23-18-0-2/E5.txt:35-37` — the first date of each dump never goes backwards while three wider
  windows load, and the walk takes 10 swipes where it took 20.
- **A-3 CLEARED.** P8 now makes a new contact and reads where it is stored (`…/qa/NEEDS-HUMAN.md:51`). The hardening is
  sound as it stands:
  - Pre-check: `…/app/people/PeopleWrites.kt:105` (contacts) and `:295` (groups).
  - Read-back through the policy: `:117-118`, `:472`.
  - Take-back with a truthful outcome: `:483-489`.
  - The normal path is untouched: `…/qa/E13-legs-create-on-e03a1d23-30-0-2`, `E27-legs-create-on-e03a1d23-21-0-1`,
    `E15-legs-sim-on-e03a1d23-29-0-1` — each has its insert `ok` line and no `write delete` line.
  - **The one delete that asks the guard nothing about an account is acceptable under Q-16-3.** It names only the id
    this op's own insert returned, by a bare row URI with no selection and no sync-adapter flag; it is reachable from the
    two creators alone (`:120`, `:304`); it restores "nothing in an un-ticked account" rather than breaching it;
    `TakeBack` refuses ids of 0 or below (`…/app/people/PeopleWriteGuard.kt:153`); `PeopleWriterTest` covers the
    taken-back, left-behind, not-known and no-id cases.

## New blocking from the product commits

None.

- **Day view (3573219e, ac4a972d):** no block is ever skipped. `built` and the fill loop are keyed on the same `blocks`
  (`CalendarViews.kt:531-535`), the loop ends at `blocks.size`, and positions are computed once for all blocks, so
  nothing moves as later ones appear. A tap on a block not yet built hits the grid, which has no click, and does
  nothing; it cannot land on another event. The leg reads 50.1 ms on open with all 200 reached
  (`…/qa/E7-legs-D-frames-on-e03a1d23/E7.txt`).
- **e226bc68:** no other provider call in People or the feeds still throws without a permission. Every query in
  `PeopleData`, `PeopleWriter` and `PeopleFeed` is inside `runCatching` or behind a permission check, and the three
  observer registrations are guarded. The leg with both permissions revoked passes on the gate build
  (`…/qa/E13-legs-grant-on-585b457f-49-0-4`).
- 328a0641, 3d394473, dcf28edc, b9d77a4b: read; each does what its message says and nothing else.

## Findings (both NOTE)

**NOTE — P8 does not ask for the diagnostics line.** The trust-c review's findings 4 and 6 and the lead's own summary
say P8 "reads the line", but `…/qa/NEEDS-HUMAN.md:51` never asks for it. The line is needed because the pre-check
(`PeopleWrites.kt:105`) predicts the provider's refusal from the default-account state alone. If the S25 would in fact
accept a Phone save, People refuses it needlessly, and P8 counts "could not save" as a pass. Fix: add to P8 "paste the
`[people] write insert …` line from Diagnostics, and say whether Samsung Contacts itself can save a new contact to
Phone".

**NOTE — wrong notice in one adb-only state.** With WRITE held and READ revoked, the read-back fails, the new contact
is inserted and taken back, and the notice says the phone "filed that under another account"
(`…/app/people/PeopleApp.kt:338`). It fails closed, so this is wording only. Fix: a separate notice when the row could
not be read back.

## Round-1 notes: does the triage's action answer each?

- **4 (E7 measure):** answered. Recorded for the owner; the Day view leg was measured (344.6 ms), fixed and re-measured
  (50.1 ms); the pager's cost is named in H17. The clause itself stays the owner's (Q-16-6).
- **5 (F38 residuals):** answered. The index is in the key (`CalendarViews.kt:259`); "No events" shows only for a day
  that was read (`:257`); row geometry is covered by `…/qa/E19-legs-1-2-3-on-3c5e3199-127-0-12`.
- **6 (bearings):** nothing was asked; nothing needed.
- **7 (keyboard gap):** answered. H2 names it with a follow-up recommendation, and that is enough for a NOTE-level
  item: it costs a step (close the keyboard to reach Save), not a function.
- **8 ("Phone" label):** answered by 328a0641, and P8 reads the label.
- **9 ("both" on a changed occurrence):** answered by 3d394473; `…/qa/E24-legs-D-on-3c5e3199-94-0-7/E24.txt:117-121`.
- **10 (in-place grant):** answered by dcf28edc; the E13 grant leg and `…/qa/E18-legs-G-on-e03a1d23-25-0-2/E18.txt:33`.
- **11 (NEEDS-HUMAN gaps):** answered for P2, P8, P9, H14, H19 and H21. The DRAFT header stays until hand-off. One small
  gap remains: the first NOTE above.
- **12 (title strip; action line):** the strip is fixed by b9d77a4b; the action line's wording is recorded in
  `…/qa/clauses-open.tsv:32`, which is acceptable.

E7's janky-frames clause (≤ 5 % absolute) is still the owner's question, Q-16-6; not counted.

GATE: PASS
