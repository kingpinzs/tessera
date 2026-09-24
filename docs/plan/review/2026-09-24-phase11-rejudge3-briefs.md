# Phase 11 re-judge, round 3 (the cap's last) — the two reviewer briefs

Roster: Jeremy, 2026-09-24, "use opus for the re-judge" — both lenses as Opus subagents (rounds 1 and 2 ran the same way).

## Shared brief

Project: ~/projects/metro-launcher (Android launcher, Kotlin + Compose), branch main. READ-ONLY review: do not edit any
file except your own report, do not run adb or touch any emulator, never touch ~/projects/metro-launcher-p15. Do not push.

Context. Round 2 (on pass 6) returned: design 0 BLOCKING · 1 SHOULD-FIX · 2 NOTE (review/2026-09-24-phase11-rejudge2-design.md);
evidence 1 BLOCKING · 4 SHOULD-FIX · 10 NOTE (review/2026-09-24-phase11-rejudge2-evidence.md); the lead's triage, all accepted:
review/2026-09-24-phase11-rejudge2-triage.md. The fixes are in 9805f5e (drivers; smokes and probes kept beside the rows:
EDGE-smoke3-round2, EDGE-callsmoke, EDGE2-probe, FLING-probe), then pass 7 — the full gate end to end on ONE APK (code 9f790b7)
and ONE driver set, no appended re-runs unless SUITE.txt shows one: docs/plan/qa/phase-11/SUITE.txt (SUITE-pass6-f8cd00a.txt
is the previous), the row directories, UNIT.txt, UNIT-results/. Then the docs commit (README, the doc's QA evidence section,
INDEX row 11 and its Change Log line "PHASE 11 RE-JUDGE, ROUND 2").

This is the last round the review cap allows. Judge whether the phase 11 gate — and the L11-1 fix's own gate — can be called
passed as evidenced, apart from what only Jeremy can close (NEEDS-HUMAN H1-H11, the phone rows, and the two readings recorded
for him). BLOCKING = it cannot. Do not re-raise a round-1 or round-2 finding the triage recorded as not fixed unless the evidence
changed; say so in one line instead.

Read first: docs/plan/phase-11-tile-quick-actions.md (FINAL; Acceptance, Edge Cases :627-658, its QA evidence section);
docs/plan/INDEX.md (row 11, the 2026-09-24 Change Log lines); both round-2 reports and the round-2 triage;
docs/plan/qa/phase-11/README.md (check its claims against the evidence); git log --oneline b0311b3..HEAD and each commit's diff.

Output: write your report to the path given below, first line `BLOCKING: n · SHOULD-FIX: n · NOTE: n`, then a findings table
(id | sev | file:line | finding | evidence | exact fix), then a table "Round-2 findings" (id | status: resolved / open / partly |
evidence), then "What I checked and found right". Quote file:line or evidence-file lines for every claim; a claim you could not
verify from files is not a finding. Reply with the first line and the path only.

## Reviewer 1 — design / correctness

Your round-2 findings D2-1 .. D2-3 against pass 7 and 9805f5e. Then the new EDGE sub-steps as design checks (the keyguard with
a PIN; the fling with its control and positive control; the second finger's control; the still press in edit mode with a burst
open — does each test what its Edge Cases bullet means?). Any design question in the product itself (9f790b7 and before) that
pass 7's evidence newly exposes.

Write your report to ~/projects/metro-launcher/docs/plan/review/2026-09-24-phase11-rejudge3-design.md.

## Reviewer 2 — testability / evidence

Your round-2 findings EV-1 .. EV-15 against pass 7 and 9805f5e: each resolved, open or partly, with the evidence. Then pass 7
itself: one APK, one driver set (every row's driver blob against the committed driver), every exit code, E14 last and on the
gate rows only; every assertion that cannot fail or passes on a precondition that did not hold — look hardest at the sub-steps
9805f5e changed or added (scripts/edge.sh, l11_1.sh (d), e13.sh's press styles, e6.sh's retake rule, run_all.sh's unit line).

Write your report to ~/projects/metro-launcher/docs/plan/review/2026-09-24-phase11-rejudge3-evidence.md.
