# Where the build stands, and what the worktrees hold — 2026-10-30

Written by phase 17's resume session on Jeremy's ask ("first show me the current plan and where we are at and if any of
the working trees help close those out"). Source: INDEX.md on branch phase-17 at c1a77fff5 (= origin/main's content;
`git merge-base --is-ancestor c1a77fff5 origin/main` rc 0; origin/main = 01dfc84be, PR #13, 2026-10-21 10:12; origin
holds only `main`). Nothing in any metro-launcher worktree changed after 2026-10-21 12:00 (`find … -newermt`).

## His instruction of 2026-10-30 23:45, as the lead reads it (INDEX Change Log)

"lets keep building and only do red tests and wait tell everything is 100% complete and ready to ship then do the full
hours of testing."

1. Every QA round waits for ONE full testing pass when everything is built and ready to ship: no emulator rows, no gate
   reviewers, no NEEDS-HUMAN sign-off rounds until then. This sets aside build-prompt.md's per-session "ONE round of
   testing" (2026-10-07 08:29) and the skill's "the next phase does not start until the current gate passes".
2. "Red tests" = each build task's own unit tests, written to fail first, then passing, run with gradle on the host.
3. Phase 17's owed device rows (Q-17-R1 (a)) and Q-17-R2 (which build) move into that final pass, which runs on the
   final build.
4. Read conservatively, pending his word: a change to a trust part still gets its ONE adversarial Opus review of the
   code when it is built (a code review, not device testing; build-prompt.md's trust list and the standing rule).

## The plan

Build order (INDEX, C-14): 01 → 02 → 03 → 05 → 10 → 11 → 13 → 12 → 14 → 15 → 16 → 17 → 18 → 20 → 04 → 19 → 06 → 07 →
08 → 09; 21 (TV channels) after 17 and 20 once its doc exists; 22 (seasonal screen savers) after 07 once R14 and its doc
exist. Not split, not ruled: the Tessera bar over every app (R10 item 6).

## Every phase

| Phase | Part | Doc | Row status (INDEX) | What is open |
|---|---|---|---|---|
| 01 | Start + live tiles | FINAL | QA passed, NEEDS-HUMAN open | his sign-offs |
| 02 | Edit mode + folders | FINAL | QA returned (reviewers blocking) | the gate's blocking review items |
| 03 | Cortana commands | FINAL | QA (partial), speech fix unverified on device | device check of the speech fix; H-L13-1 |
| 05 | Keyboard | FINAL | QA — gate NOT closed | gate |
| 10 | Music player | FINAL | built — owes phone rows + reviewers | phone rows, reviewers |
| 11 | Tile quick actions | FINAL | **done** | — |
| 12 | Setup wizard | FINAL | QA passed (gate PASS/PASS) | H1–H9 + phone rows |
| 13 | Fluent materials | FINAL | QA passed (round 3 PASS/PASS) | H1–H9, P2–P4 |
| 14 | Pod Bay (Tess) | FINAL | QA passed (round 2 PASS/PASS) | H1–H13, phone rows; 3 SHOULD-FIX not built |
| 15 | Clock, Calculator, Recorder | FINAL | L18-3 / L18-6 fixed 2026-10-21 | returns to done on his sign-off of phase 18's H9 |
| 16 | Calendar, People | FINAL | QA | its final APK run, two gate reviewers, Q-16-5 |
| 17 | Photos, Camera, Movies & TV | FINAL | QA | see "Phase 17" below |
| 18 | Files | FINAL | QA — waiting on his sign-off | qa/phase-18/NEEDS-HUMAN.md |
| 20 | Music streaming + radio | FINAL | QA — one round done, D1 closed | P1–P4, H1–H5 |
| 04 | Action center, volume, helper | FINAL | pending — **PARKED** 2026-10-20 | see "The blocker" |
| 19 | Settings front | **DRAFT** | pending | Stage A not finished (round 3, FINAL); needs 04's helper rows |
| 06 | Dialer + messaging | FINAL | pending | voicemail verbs ADD to 04's helper (doc lines 30–31) |
| 07 | Glance | FINAL | pending | turns the screen off through 04's accessibility service (doc line 41) |
| 08 | Cortana LLM | FINAL | pending | covers 04's toggle commands too (doc line 12) |
| 09 | Cortana harness | FINAL | pending | its provider tells 04's helper apart by pid (doc lines 25, 38) |
| 21 | TV channels | no doc | — | R13 research pending, then planning |
| 22 | Seasonal screen savers | no doc | — | R14 research pending, then planning |

Ledger: every row is `fixed` except **L20-1** (open; phase 17's `ServerRules.isPrivate`, see below).

## The blocker: phase 04

Row 04 (INDEX): the 2026-10-20 build session built only the shared contract (a1d0b6d33 — now in main), then halted —
"the builder dispatches for task 2 (the helper) and for tasks 4, 5, 11 (the action center's service and panel) were each
stopped by a safety classifier, not retried". He then parked it ("(c)", 2026-10-20 17:23) and had 18, then 17, closed
first. Every phase left in the Build order after 04 (19, 06, 07, 08, 09) leans on 04's helper or its accessibility
service, by their docs' own lines (table above). So once phase 17 is closed, the next build question is phase 04's.

## Phase 17 — what is left that is BUILDING (not testing)

From qa/phase-17/NEEDS-HUMAN.md (the last column is the lead's lean written 2026-10-06) and the ledger:

| Item | What | Lean then |
|---|---|---|
| D1 | "Open with" from another app on a shared-library picture / video is refused (and Camera saving into another app's library entry) | build it, as its own reviewed unit |
| D2 | Block screenshots of the TMDB-key and media-server-password pages; clear the clipboard after a paste | yes (build) |
| D3 | Run the viewer other apps open in the separate editor process, not the launcher's | yes (build) |
| D4 | The Camera's go-between "show this photo" leg was never run | testing → the final pass |
| L20-1 | `ServerRules.isPrivate` (app/src/main/kotlin/app/tileshell/video/server/ServerRules.kt:109) answers false for `0.0.0.0`, `::`, `0:0:0:0:0:0:0:1`, `::ffff:10.0.0.1` | fix with a red test (read from the code: `::` and `::ffff:…` fail `toIntOrNull(16)` → false; `0.0.0.0` is not in any private range; not yet run) |

D1, D2, D3 and L20-1 all touch trust parts (the capture-intent guards, the credential store's pages, the exported
viewer, the off-home-network ask).

## Do any worktrees close an open item? No.

Measured 2026-10-30 against origin/main (01dfc84be):

| Worktree | Branch / head | In main? | Uncommitted |
|---|---|---|---|
| metro-launcher-p17 | phase-17 c1a77fff5 | yes (ancestor) | this session's INDEX / state lines; 3 old evidence folders |
| metro-launcher-p18 | phase-18 c1a77fff5 | yes | 127 evidence paths (git-ignored kind, untracked) |
| metro-launcher-p20 | phase-20 2e35bec61 | yes | 2 build-notes folders |
| metro-launcher-p04 | phase-04 051940b46 | yes | none |
| metro-launcher-p16 | phase-16 1d01c5173 | yes | none |
| metro-launcher (main checkout) | phase-14 9db00b2ae (old history) | content yes* | 8 unused theme-picture candidates in art/themes/, 2 deleted .audio-state files, caches |
| metro-launcher-p12-base, -p12-nopics | detached, old history | content yes* | p12-nopics: 6 preset pictures staged as deleted (an APK-size experiment) |
| metro-launcher-p15, -p15-calc, -p15-clock, -p15-rec | old history | content yes* | caches only |
| p16/.claude/worktrees/cal, people, trustA, trustB | old history | content yes* | none |
| metro-launcher/.claude/worktrees/agent-a2c6…, agent-a4c1…, agent-a616… | old history (Sept) | content yes* | agent-a616: an untracked app/libs |

\* Old history = before the 2026-10-02 rewrite, so commit ids differ from main's. Checked by content instead:
(1) `git diff --name-only --diff-filter=D <head> origin/main -- app testapps tools livetile-client calc` = 0 files for
every one of the 13 old heads (no code file exists only on an old branch); (2) every old-history commit that touches
code and has no patch-equal commit in main (`git cherry origin/main <head>`, `+` rows touching app/ testapps/ tools/
livetile-client/ calc/) has a commit with the identical subject in `git log origin/main` — 0 unmatched across all 13.

So every worktree's code is already in main. None holds unmerged work that closes an open item. The old-history worktrees
are leftovers. Removing them is a clean-up for him to approve, not part of this session's scope.
