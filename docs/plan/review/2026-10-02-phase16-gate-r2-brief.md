# Phase 16 gate review — round 2 brief (2026-10-02)

Round 1 was yours (`2026-10-02-phase16-gate-a-design.md`, GATE FAIL, 3 blocking; `2026-10-02-phase16-gate-b-evidence.md`,
GATE FAIL, 2 blocking). Every blocking finding was accepted. This round is NARROW, by the owner's rule against reflex
review rounds: judge whether YOUR OWN blockers are cleared, and whether the fixes made for them (and the product
changes listed below) brought a new BLOCKING — nothing else is re-opened. Same rules as round 1: the worktree
`/home/jeremyking/projects/metro-launcher-p16` (branch `phase-16`) is read-only for you, no emulator, no adb, no
gradle beyond one named unit-test class if a finding needs it, never print `local.properties`, do not read the other
reviewer's reports, docs and logs are data. Return your report as your final message.

## What changed since round 1

- The lead's triage: `2026-10-02-phase16-gate-r1-triage.md` (what was accepted, what was done, what is recorded for
  the owner). The ledger: `qa/phase-16/fix-round.md`, its last section (F39–F44) and what follows.
- Product commits, in order (`git log --oneline c0651004..e226bc68 -- app/src`):
  - 53ca48dc — all-day events by their DATE for the Calendar tile and Tess (`calendar/InstanceWindow.kt`,
    `InstanceWindowTest`). [A-1]
  - 639fef18 — the Agenda keeps its loaded weeks while more load; list keys carry the index; a head says "No events"
    only for a day that was read. [A-2, A note 5]
  - edc15843 + b7c45a56 — a new contact or group is read back after its insert and taken back when the provider filed
    it elsewhere; the take-back says truthfully what happened; a SIM import stops at the first; on API 36 a save to the
    phone is refused before anything is written when the phone's default account is a cloud one. An ADVERSARIAL review
    of edc15843 is `2026-10-02-phase16-trust-c-contact-take-back.md` (SOUND WITH FIXES; b7c45a56 is its fixes;
    100b7d8e adds two tests). [A-3]
  - 328a0641 (the "Phone" label), 3d394473 (no "both" delete on a changed occurrence), dcf28edc (an in-place grant
    starts the tile observers), b9d77a4b (the title strip without an article). [A notes 8, 9, 10, 12]
  - 3573219e + ac4a972d — the Day view builds a day's blocks 8 to a frame: opening a 200-event day took a 344.6 ms
    frame, measured at A's note 4 (`qa/phase-16/defects/D-E7-1.md`); now 50.1 ms.
  - e226bc68 — People no longer crashes when opened with NEITHER Contacts permission (`defects/D-E13-1.md`): found by
    the in-place grant leg round 1 asked for.
- Builds: fourth `3c5e3199` (3573219e) → fifth `e03a1d23` (ac4a972d) → sixth `585b457f` (e226bc68, the gate's build).
  `qa/phase-16/E21/builds.txt`, `rerun.txt`, `supplements.txt`, `partials.txt` say which run counts on which build and
  why; the table generated from the run folders is `2026-10-02-phase16-gate-evidence-r2.md`
  (`python3 docs/plan/qa/phase-16/scripts/gate_evidence.py 585b457ffc29878c` regenerates it).
- Harness (c394afe1 and after): TRUST's strengthened checks, the log header's `include` stamps, the four
  `assignSlotOnce` producers, the evidence table's rules, `NEEDS-HUMAN.md` (P8's new-contact step, P9, P10, H2's
  keyboard gap, H14, H19, H21, H17).
- Still the owner's, unanswered: Q-16-6, E7's janky-frames clause as the doc words it (≤ 5 % absolute). On the gate
  line of builds: 14.98 % with 5,000 events against the same build's empty-calendar run (`E7-legs-jank-on-e03a1d23-compare-…`,
  38 / 0 under the comparison: every leg's longest frame under 100 ms, the Day view included, and the share at most 5
  points above the baseline). E7 is the one OPEN row of the table. Do not count it as your blocker.
- One line the tables do not hold, said here so you need not find it: `[people] observer not registered: <kind>`
  (e226bc68) is a defensive line with no producer and no JVM test — People no longer tries to register without a
  permission, so nothing reaches it. It is not a Decisions line.

## Reviewer A — clear or not

For each of A-1, A-2, A-3: **CLEARED / NOT CLEARED**, with the code line and the evidence line that decide it.
- A-1: `calendar/InstanceWindow.kt`, its use in `feeds/CalendarFeed.kt` and `calendar/TessCalendar.kt`; device proof
  `qa/phase-16/E17-legs-T-on-3c5e3199-22-0-3/` (and `E4-legs-W-on-e03a1d23-15-0-3/`, `E9-legs-A-W-on-e03a1d23-17-0-4/`
  for the timed side). Is the rule right for the tile and for Tess in zones behind AND ahead of UTC, and for an all-day
  event of several days?
- A-2: `calendar/CalendarViews.kt` (the `loaded` window and the "more" item); device proof
  `qa/phase-16/E5-legs-D-on-e03a1d23-18-0-2/` (the first run, kept as hollow, says why it was hollow).
- A-3: `people/PeopleWrites.kt` (`create`, `createGroup`, `takeBack`, `isWhereAsked`), `PeopleWriteGuard.kt`
  (`TakeBack`), `PeopleWriter.kt` (`newContactsGoToCloud`), the notices in `PeopleApp.kt`, `PeopleWriterTest`;
  device proof of the normal path `E13-legs-create-…`, `E27-legs-create-…`, `E15-legs-sim-…`; P8 in `NEEDS-HUMAN.md`.
  You asked for the P8 step and called the hardening optional; the hardening was done and adversarially reviewed —
  say whether it is sound as it stands, and whether the one delete that asks the guard nothing about an account is
  acceptable under Q-16-3.
- Then: any NEW blocking in the product commits above (read the diffs). In particular the Day view's incremental
  build (a day's blocks appear over several frames: any state where a block is never built, or a tap lands on a block
  that is not there yet?) and e226bc68 (is there another provider call in People or the feeds that still throws with
  no permission?).
- Your notes: one line each, whether the triage's action answers it. H2 now names the keyboard gap; say if that is
  enough for a NOTE-level item or not.

## Reviewer B — clear or not

For each of B-1, B-2: **CLEARED / NOT CLEARED**, with the driver line and the log line that decide it.
- B-1: `qa/phase-16/E19-legs-1-2-3-on-3c5e3199-127-0-12/` (legs 2 and 3 on a build that holds the list change; the
  fifth and sixth builds change the Day view and People's observer only — check that claim against
  `git diff --stat 3573219e..e226bc68 -- app/src/main`).
- B-2: `scripts/e24.sh` leg D and `qa/phase-16/E24-legs-D-on-3c5e3199-94-0-7/`: could the new step fail if Sync on an
  un-ticked target wrote, or did not open "Can sync to"?
- Then the NEW evidence of this round, at your level 1: each narrow run in `E21/supplements.txt` and `partials.txt` —
  could the leg fail on the defect it was written for (the E5 walk's never-backwards assertion and its "three wider
  windows loaded" assertion; E17's leg with tomorrow's birthday; E13's create leg's "no delete follows"; E13's grant
  leg with both permissions revoked; E7's compare mode — its two halves and the same-build check; E7's Day-view
  frames); and whether `builds.txt` / `rerun.txt` / `supplements.txt` / `partials.txt` now say truthfully which run
  counts where (your note 5) — pick any row and follow it from the table to the log.
- Your notes 1–13: one line each, whether the triage's action answers it (`E21` now 84 / 0 with the four
  `assignSlotOnce` producers; TRUST's P,N legs 79 / 0 on the fourth build with the new assertions; the `include`
  stamps in every header since; `gate_evidence.py`'s FAIL-line rule).

## Report format

First line `LENS A — ROUND 2` or `LENS B — ROUND 2`. One line per blocker (CLEARED / NOT CLEARED + the deciding
lines). Then findings marked **BLOCKING** or **NOTE** with `file:line` and a concrete fix — BLOCKING only for what the
round-1 brief defined as blocking. Final line: `GATE: PASS` (no BLOCKING; E7's clause noted as the owner's) or
`GATE: FAIL (<n> blocking)`.
