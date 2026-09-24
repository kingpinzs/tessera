# Phase 11 re-judge, round 2 — the two reviewer briefs

Roster: Jeremy, 2026-09-24, "use opus for the re-judge" — both lenses as Opus subagents (round 1 ran the same way).

## Shared brief

Project: ~/projects/metro-launcher (Android launcher, Kotlin + Compose), branch main. READ-ONLY review: do not edit any
file except your own report, do not run adb or touch any emulator, never touch ~/projects/metro-launcher-p15. Do not push.

Context. Round 1 of this re-judge (on pass 5) returned: design lens 0 BLOCKING · 1 SHOULD-FIX · 5 NOTE
(review/2026-09-24-phase11-rejudge-design.md); evidence lens 2 BLOCKING · 6 SHOULD-FIX · 8 NOTE
(review/2026-09-24-phase11-rejudge-evidence.md). The lead's triage, with a decision per finding:
review/2026-09-24-phase11-rejudge-triage.md. Fix commits since pass 5 (git log aaf7adc..HEAD): 9f790b7 (product: R1-1 / R1-2 /
R1-6), c78f488 (fixture: tileclient-b's labels verb), 50e0f30 (E10), 585e6c3 (L11-1 row), 19a9ffa (E6), 239e74e (the NEW EDGE
row), f8cd00a (E13), then pass 6 (b2eafb8: E13 failed 38/39 on a driver race — its short press ran past the hold while a PNG
capture was taken inside it), the E13 fix in two cuts (a45b2ad: still ~800 ms, re-run kept in E13-pass6-append-800ms;
1477e75: passes) re-run with E14 in SUITE.txt's appended sections (6d75888), and the docs commit (8ff76de). Pass 6 is the full gate on ONE APK after all of that:
docs/plan/qa/phase-11/SUITE.txt (SUITE-pass5-aaf7adc.txt is the previous one), the row directories, UNIT.txt, UNIT-results/.

Read first:
- docs/plan/phase-11-tile-quick-actions.md (FINAL; Acceptance, Edge Cases :627-658, its QA evidence section — new)
- docs/plan/INDEX.md (row 11; the 2026-09-24 Change Log lines, especially "PHASE 11 EDGE ROW ADDED" and "E6 RECORD CORRECTED")
- both round-1 reports and the triage; docs/plan/qa/phase-11/README.md (check its claims against the evidence)
- git show for each commit named above

Output: write your report to the path given below, first line `BLOCKING: n · SHOULD-FIX: n · NOTE: n`, then a findings
table (id | sev | file:line | finding | evidence | exact fix), then a table "Round-1 findings" (id | status: resolved /
open / partly | evidence), then "What I checked and found right". BLOCKING = the phase 11 gate or the L11-1 fix gate cannot
be called passed as evidenced. Quote file:line or evidence-file lines for every claim; a claim you could not verify from
files is not a finding. Reply with the first line and the path only.

## Reviewer 1 — design / correctness

1. 9f790b7: does MusicFeed.forget's own null publish close R1-1's window under every interleaving (the wipe's thread vs
   main), without clearing a NEW track of the same package (R1-2's once-registration)? Anything new it breaks?
2. Your round-1 findings: resolved as the triage says? R1-3 and R1-4 were recorded, not fixed — still acceptable?
3. The two readings the lead recorded for Jeremy (INDEX "PHASE 11 EDGE ROW ADDED" (a) a ringing call as a heads-up keeps the
   burst, answering closes it with `stop`; (b) a listener rebind with Start in front keeps it): consistent with the doc's
   Decisions (the close-reason list at doc :168-178 is "exactly" ten reasons), or does either need a product change?
4. The EDGE row's sub-steps as design checks: do the flip, fling, second-finger, folder-dissolve and X5 sub-steps test what
   the doc's bullets mean (e.g. X5's model: satellite = accent at the tile's alpha over what lies behind it, StartPage.kt /
   QuickBurst.kt)?

Write your report to ~/projects/metro-launcher/docs/plan/review/2026-09-24-phase11-rejudge2-design.md.

## Reviewer 2 — testability / evidence

1. Pass 6 (SUITE.txt): one APK, one driver set, every row's exit code, E14 last; each row's evidence proves what README's
   "What it proves" column and the doc's QA evidence section claim. Any assertion that cannot fail, or passes on a
   precondition that did not hold — look hardest at the NEW and rewritten sub-steps: scripts/edge.sh (every sub-step; raw
   touches via sendevent; the call via ANSWER; X5 via the photo picker and the backdrop model), l11_1.sh (d) and (e), e10.sh's
   art sub-step, e6.sh's retake rule, e13.sh's press-style sub-step (its press built short and measured on the device clock). The smokes that found the driver's own defects are kept (EDGE-smoke1-nopressure,
   EDGE-smoke2-picker, EDGE-x5smoke..4, L11-1-smoke5-pinid, E10-smoke-artstrip-7cb3742-apk): did each fix close its hole?
2. The EDGE index (EDGE/EDGE.txt's index; scripts/edge_index.tsv) against the doc's Edge Cases :627-658: is every case
   there, and does each cited row / test actually prove it (open the cited evidence)?
3. Your round-1 findings R2-1 .. R2-16: status each against pass 6.

Write your report to ~/projects/metro-launcher/docs/plan/review/2026-09-24-phase11-rejudge2-evidence.md.
