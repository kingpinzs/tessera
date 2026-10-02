# Phase 16 gate, round 1 — the lead's triage (2026-10-02)

Reports: `2026-10-02-phase16-gate-a-design.md` (Reviewer A, design and correctness: GATE FAIL, 3 blocking) and
`2026-10-02-phase16-gate-b-evidence.md` (Reviewer B, testability and evidence: GATE FAIL, 2 blocking). Both Opus, read-only,
independent. Every blocking finding is ACCEPTED; none is argued. Each fix is verified by the one row, leg or sub-step
it touches (the owner's ruling of 2026-10-01), on a FOURTH build. Round 2 goes back to the same two reviewers with
only their own blockers to clear (the owner's rule against reflex rounds: no new sweep).

## Blocking

| # | Finding | Disposition | What was done | Device proof (one run on the fourth build) |
|---|---|---|---|---|
| A-1 | The tile and Tess show all-day events (every birthday) by the UTC clock: in MDT today's leaves at 18:00 and tomorrow's shows as today's; E17's re-cut fixture hid it | ACCEPTED — a product defect | 53ca48dc: `calendar/InstanceWindow.kt`, one rule for the tile's and Tess's reads (a timed instance by the clock, an all-day one by its DATE; the range read reaches back to the local start of today). `InstanceWindowTest` (8) + one test through the provider fake | E17's tile and Tess leg with the DOC's fixture (a birthday on the device's local today) and the clock at 19:00 local; a second birthday on local tomorrow is NOT shown, NOT named |
| A-2 | The Agenda throws the list back to the selected day each time it loads more weeks (8 → 16 → 32 → 56) | ACCEPTED — a product defect | 639fef18: the weeks already loaded stay on show while a wider window with the same start loads; only the end of a wholly loaded window asks for more | E5 leg D alone, with the assertion that the first date of each dump never goes backwards, past weeks 8, 16 and 32 |
| A-3 | F25 (a "Phone" save on a phone whose default account is a cloud account) was closed as phone row P8, and P8 never makes a new contact | ACCEPTED, and hardened in the product | edc15843: `PeopleWrites.create` reads the new raw contact back; when it is not in the account asked for, that one row is deleted again and the save fails aloud. `PeopleWriterTest` (3 cases). P8 gains the step. The change is trust-touching: an adversarial Opus review of that commit is running | E13's create leg (the normal path still saves, and no delete follows); P8 on the phone |
| B-1 | E19's Agenda-rows leg (K3.3–K3.7 geometry) has no run on a build that holds the Agenda's list change | ACCEPTED | `e19.sh` gains modes for legs 2 and 3 | E19 legs 2 and 3 alone |
| B-2 | The edge bullet "the allow-list cleared after a sync: the marker stays; the next Sync opens Can sync to" was mapped to E24, and no driver taps Sync on a synced event whose target is un-ticked | ACCEPTED — never driven on any build | E24 leg D gains the step (the page opens, the `no calendar allowed -> can sync to` line, no `ok`/`updated` line, the copy row unchanged, the marker still shown) | E24 leg D alone |

## Notes — acted on

| # | Note | What was done |
|---|---|---|
| A-5 | F38's list key could repeat within a day; a day says "No events" while its window is still loading | 639fef18: the index is in the key; a head says "No events" only for a day that was read |
| A-8 | A maker-named local account would show its raw name, not "Phone" | 328a0641 (`CardRules.phoneAccount`, set where the local account is read; `PeopleModelTest`); P8 reads the label |
| A-9 | "Delete here and from <calendar>" on a changed occurrence of a synced series deletes here only | 3d394473: the choice is the series'; an occurrence's delete is "this occurrence, here". E24 leg D asserts the occurrence page offers no "both" |
| A-10 | A grant made in place does not start the tile observers | dcf28edc: both in-place grants call `startFeeds`. E18 (Calendar) and a People grant leg assert the `feeds started (… grant)` line and a change reaching the tile |
| A-11 | NEEDS-HUMAN gaps (P9 / P2 / P8 "other client"; E4's question; H14; H21; the DRAFT header) | NEEDS-HUMAN.md rewritten for each; the header goes final at hand-off |
| A-12 | "add calendar event called X" without the article | b9d77a4b (`CommandMatcherTest`) |
| B-N1 | E21's tables omit the four `assignSlotOnce` forms the row names | c394afe1: four producer lines, each held by a slice of E1's run; `e21.sh` runs again at the end |
| B-N2 | TRUST asserts only that a public version exists | c394afe1: its title and the absence of the event's title are asserted; `TRUST_ONLY=N` on the fourth build |
| B-N3 | Three TRUST grant assertions cannot fail | c394afe1: recorded, not asserted; the probe's own lines are the proof |
| B-N4 | E16's negative leg has only TestDPC's switch as proof | The People writer adds the toggle back (`(+1 enterprise)` returns) and runs the leg once |
| B-N5 | builds / rerun / partials bookkeeping (E20 unpaired; E3's reason; E25's stale line) | c394afe1: `supplements.txt` pairs E20 with its plus-glyph leg; the two lines corrected |
| B-N7 | Logs cannot say which include produced them | c394afe1: `lib.sh` stamps every sourced script (`STAMP_FILES`) |
| B-N8 | `gate_evidence.py` picks by the summary line; EDGE heading; E1 wording | c394afe1 |
| B-N9 | `notrun.tsv` reasons (line 3 a RECORD; line 4 the wrong test; line 6 may be producible) | The two writers correct their lines; line 6 is driven once or its reason corrected |
| B-N10 | Edge index: B13.2 and B20.1 mapped to rows that do not drive them | The two writers drive them or map them to the JVM test by name |
| B-N11, B-N12 | Weak checks in E6, E28, E15, E1, the unanchored absences | The writers tighten their drivers (no re-run: this run's dumps are real, per the reviewer); TRUST's two are in c394afe1 |
| B-N13 | Records: clauses-open lacks E1's two readings; E15's 2 MB; F26 still OPEN; F36's "W has no bearing" unmeasured | clauses-open and the ledger corrected; F36's wording changed to what was measured (ink asserted on a Wednesday only) |

## Notes — recorded, not changed now

| # | Note | Why |
|---|---|---|
| A-4, B-N6 | E7's measure: the empty-calendar comparison is a sound control for what the events add; the percentage does not move with what a user feels, the per-frame cap does; 11 % on an empty calendar is the Calendar's own cost (Start's scroll reads 3.15 % on this AVD); the Day view composes every block at once and no leg takes frame stats there | The clause is the owner's (Q-16-6). Done now: E7 records every leg's longest frame, has a compare mode that can fail (`E7_MEASURE=compare`), and a Day-view frame leg is measured before the fourth build — a frame over 100 ms there is fixed in that build. The pager's own cost (the first drag frame builds the neighbour page; events pop in after a swipe) is recorded as its own finding and named in H15 / H17 |
| A-7 | People's pages do not end at the keyboard (`PeopleActivity` has no `adjustResize`): the app bar and the notice sit behind it in the editors and in pick-mode search | Put to the owner in H2 as a named gap with the lead's recommendation to fix it in a follow-up: it changes every People page's layout under the keyboard, which E13 / E20's geometry would have to be run again for |
| A-12 (second) | The `[people] action text / call` line names the slot's app though the intent goes to the role holder | PHONE and MESSAGING are role-following slots, so the two are the same app unless the owner hand-picks another; the line's wording is recorded in clauses-open, not changed (E12 and E21 read it) |
| B-N11 | E6 leg (b): no device run shows another app handling the alert (the AOSP Calendar posts none) | Stands on `CalendarRulesTest` and phone row P1, as the reviewer says |

## E7 (Q-16-6), as it stands for the owner

Unanswered. Both reviewers judge the comparison a sound measure of what thousands of events add, and both say the
per-frame cap is the half that tracks what a user feels. The lead's lean stays A, sharpened by their notes: the
longest frame of EVERY leg (the Day view included) under 100 ms, and the janky share with 5,000 events at most 5
points above the same build's empty-calendar run — 5 being the doc's own budget, applied to what the events add.
