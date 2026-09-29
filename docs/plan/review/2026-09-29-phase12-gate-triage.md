# Phase 12 gate — round 1 triage (2026-09-29)

Reviewers (roster: `fable + codex-cli`, codex MCP not loaded): Reviewer A, a Fable subagent, design + correctness lens
(`2026-09-29-phase12-gate-fable.md`, **GATE: PASS**, 10 NOTEs); Reviewer B, codex CLI read-only, evidence lens
(`2026-09-29-phase12-gate-codex.md`, raw `-codex-raw.md`, **GATE: FAIL (7 blocking)**). Both judged code at 3bceaaec and the
evidence as committed in 69a0f093. The adversarial review of the trust surfaces is separate (`2026-09-29-phase12-adversarial.md`,
re-verification PASS).

## Blocking (codex)

| # | Finding | Accepted? | Action |
|---|---|---|---|
| B1 | E10: the screenrecord corroboration is RECORDed, not asserted; the first compositor gap (~50 ms) is excluded; two SF gaps fail | Accepted as a fact; **Jeremy's decision** | The doc's gates on the shell's clock pass; the corroboration the doc names cannot pass on this AVD, and two producer causes were ruled out (README "harness findings"). Reviewer A reads the same numbers as NOT PROVEN, not a product miss (its N5). Question to Jeremy in the handoff |
| B2 | Seeding: restores not followed by a zero-`assignSlotOnce` read per process lifetime; E9 reads only the final process | Accepted | `p12.sh assert_seeded` reads each restore's Start ring before the next restart (header present, zero assignments), used by `presets_lib.sh wizard_fixture`, E11 (b) and snapshot seeds, E13 (b); E9 checks phase 02's saved `e1_diag.txt`. a306271a; re-run below |
| B3 | E11: Custom via the effects toggle / Remove picture / Choose picture not exercised with the other items checked | Accepted | E11: effects toggle on both surfaces (flipped value, every other item unchanged, `preset:Custom` checked); Remove picture after HAL and Choose picture after Midnight each → Custom with the rest unchanged; fixture URIs asserted non-empty. a306271a |
| B4 | E13: the wizard side skipped Default → original with streaks remembered, and the hero chip; no PID checks | Accepted | Both trials complete the sequence (streaks chip, hero chip; Default → original remembers streaks); Start's PID asserted unchanged around each live capture. a306271a |
| B5 | EDGE_LMK: `am kill` did not kill; any step accepted | Accepted (Reviewer A's N3 too) | `run-as app.tileshell kill -9` with the dialog up; asserts the pid changed, the dialog stayed, a new process on return, no marker, the exact re-derived first step and "Step 1 of 19". a306271a |
| B6 | EDGE_DISMISS: the outside-tap case substitutes Back and emits PASS lines | Accepted | The outside case records NOT PROVEN (the dialog does not dismiss on an outside tap on this image) and emits no PASS line; P1 on the phone now asks for the outside tap (NEEDS-HUMAN P1 step 2). a306271a |
| B7 | EDGE_HOME_ONCE: explicit `am start` instead of the chooser; relaunch after choosing another launcher | Accepted | Entered through Android's own chooser: role removed, the preferred-Home mapping cleared (`BUILD_START/home-chooser-probe/`), Home → "Select a Home app" → Tessera → Just once; the return from choosing Quickstep is read as it lands (Start in front, step stays, same PID, one `StartActivity created`), before any launch or Home press. a306271a |

## Codex NOTEs

| Note | Action |
|---|---|
| E9's FAIL stays a FAIL (L12-1, pre-existing), not relabelled | Agreed; README and INDEX keep "18 / 1, L12-1" |
| README:61 calls every kept run a driver / harness fault; E10 run 1 was a product finding | README reworded (kept runs name their cause: harness, driver, or product fix) |
| `grants.sh` passes on malformed XML | Fixed: fails closed (exit 3, "GRANTS UNAVAILABLE"); a306271a |
| Provenance: historical driver blobs not retrievable; JVM XML predates 0c8fe8f4 | Drivers committed before this re-run (a306271a), so the new rows' blobs are in git; final JVM results archived in `qa/phase-12/JVM/` (33 / 0 and 16 / 0 on 36a301c3, APK byte-identical to a4d7430b). Earlier runs' blobs stay as recorded |
| The lens idle capture measures glow / mid / rim, not an iris | README reading reworded to say so |

## Reviewer A NOTEs

| Note | Action |
|---|---|
| N1 INDEX row 12 stale; no "go" recorded after the pause | Row rewritten this iteration. The go: Jeremy, 2026-09-29 after the pause, "call is done for now but DO NOT mess with system sound" (build state) |
| N2 the doc's "INDEX Change Log line when built" lines missing | Added: phase 01, phase 03, phase 05 ADDs and R12 §7.1's Cobalt line (INDEX Change Log 2026-09-29) |
| N3 EDGE_LMK never killed | = B5 |
| N4 a dismissed first dialog reads as blocked ("Open app info") | The doc's literal rule and phase 03's; **for Jeremy** (the handoff carries it); no change |
| N5 E10's 50 ms first-present gap | = B1 |
| N6 variant chips drawn regardless of the asset | Reading recorded: both pictures are compiled-in resources (ruling (a)), so a build without one does not compile; the chip rule is unreachable. No change |
| N7 `cameFromWizard` never reset (one needless layer) | Recorded; no change this phase (harmless, every Start capture passes inside it; a change would re-open every Start row) |
| N8 Back onto a step passed by a grant re-shows it and logs `granted` again | Recorded (cosmetic, one duplicate ring line) |
| N9 `evaluate` builds both full lists on every resume | Recorded (the doc asks for evaluate on every resume; the rule reads grant rows only) |
| N10 P1 starts from the installed phone, no Home route | NEEDS-HUMAN P1 gains an optional step 0 (Clear defaults → Home → Just once → Default Home step), a Location note, the outside-tap check, page titles in place of `dumpsys` (the phone rule), and states both differences from the doc's P1 |

## Re-run (round 1 fixes), apk a4d7430b

The six rows that drive the changed drivers, each run once on the fixes (Jeremy's QA ruling), then re-run alone only where
the run itself exposed a driver fault. Every run is kept (`<ROW>-run<n>-<why>/`).

| Row | Result | Drivers | Notes |
|---|---|---|---|
| EDGE_LMK | 14 / 0 (+1) | a306271a | `run-as kill -9` with the dialog up: pid 22159 → new pid, no marker, `setup:notifications` first, "Step 1 of 19"; the doc's `am kill` recorded as not killing |
| EDGE_DISMISS | 13 / 0 (+4) | a306271a | Back: stays, Not now advances. Outside: RECORD NOT PROVEN, no PASS line (B6) |
| E11 | 302 / 0 | a306271a | B2 seeds read, B3 Custom checks on both surfaces |
| E13 | 77 / 0 (+1) | a306271a | B4 sequence on both surfaces, PID unchanged around every capture |
| E9 | 22 / 1 | 93134be9 | the FAIL = L12-1 (phase 02 E1 "edit mode is still on", pre-existing). Run 4 (a306271a) had a second FAIL from the lead's own new check: phase 02's saved `e1_diag.txt` is a grep of the listener dump and never carries the header; the check now reads that the restore's own `assignSlotOnce` lines are in it, uncut, with zero assigned |
| EDGE_HOME_ONCE | 28 / 0 (+8) | d7f1be4d | run 3 (a306271a): choosing Quickstep brought Quickstep forward at once — the permission controller launches the newly chosen Home itself (`BUILD_START/home-return-probe/`, with a Settings > Default apps control); the row now asserts the wizard alive behind it and the same step on the same record when Tessera is reopened (README reading). Run 4 (93134be9): every return check passed; the precondition read Start's record mid-removal after the force-stop (`BUILD_START/home-prestate-probe/`); it now polls up to 5 s |

Round 2 goes to Reviewer B (codex CLI) with `2026-09-29-phase12-gate-r2-brief.md`. Reviewer A passed round 1 and its notes are
answered above; it is not re-run.

## Round 2 (2026-09-29)

Codex CLI stopped at its usage limit mid-review (no verdict; `2026-09-29-phase12-gate-r2-codex-raw.md`), so Reviewer B is a
second Fable subagent on the evidence lens (`2026-09-29-phase12-gate-r2-evidence.md`): **GATE: PASS**, B2–B7 CLEARED (each
driver checked able to fail on the defect; every re-run header's driver blob matched to its commit), no new BLOCKING,
**B1: open (Jeremy)**. Reviewer A passed round 1. Both reviewers now pass, with B1 left as Jeremy's criterion decision.

| Note | Action |
|---|---|
| 1 P1 step 0 never observes "another launcher" on One UI | APPLIED: step 0 now chooses One UI Home first, notes what comes forward and whether the wizard is still on Default Home, then Tessera (NEEDS-HUMAN.md) |
| 2 `assert_seeded` could pass on an empty slice | Recorded, not applied: every captured slice carries the restore's line (the reviewer checked each); a driver change would re-open E11 / E13 / the fixtures for no evidence gain. For the end-of-project gate run |
| 3 E9's second-restore coverage leans on later children not restarting the shell | Recorded, not applied (holds today, `E9/ring-all.txt:1`); same reason |
| 4 EDGE_HOME_ONCE reads a wrong advance only after the reopen | Recorded, not applied (the reopen's same-step check catches it) |
| 5 E13 (a) walks the chip sequence in two trials | APPLIED: README row says so |
| 6 `persisted_grants`' exit 3 unchecked in E11 | Recorded, not applied (cannot pass silently: the positive checks FAIL; only the FAIL's wording would mislead) |
| 7 EDGE_LMK notes the remaining walk, asserts only no photos | Recorded, not applied (the walk in `EDGE_LMK.txt:28` is E2's list minus photos, "Step 1 of 19" asserted) |

Per the phased-build rule, no third round: nothing HIGH remains.
