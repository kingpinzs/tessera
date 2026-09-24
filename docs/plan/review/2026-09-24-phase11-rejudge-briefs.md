# Phase 11 gate re-judge — the two reviewer briefs (OWED, not yet run)

Dispatched 2026-09-24 after pass 5 (aaf7adc, SUITE PASSED); both Fable subagents stopped on their first request with
HTTP 429 "You've reached your Fable limit" — no report was written. Codex is out for that session (MCP not loaded; the
CLI hit its usage limit 2026-09-23 and is not retried within a session, per the skill). The skill's roster forbids an
Opus / Sonnet / Gemini substitute, so the re-judge waits for Fable (or codex in a new session: MCP, then the CLI).

To run: one Fable subagent per lens, in parallel, each given "Shared brief" + its own lens section. A new session
re-checks the roster first (ToolSearch select:mcp__codex__codex, then the codex CLI) — codex, if available, takes one lens.

## Shared brief

Project: ~/projects/metro-launcher (Android launcher, Kotlin + Compose), branch main. READ-ONLY review: do not edit any
file except your own report, do not run adb or touch any emulator, never touch ~/projects/metro-launcher-p15. Do not push.

Context. Phase 11 (tile quick actions) was gated in pass 3 (all rows PASS except E10, blocked by L11-1). L11-1: three
producers (MusicFeed, the notification listener, the Live Tile API store) wrote one `pkg:<package>` content slot, so each
media-notification update wiped a playing tile's now-playing face and transport strip. Jeremy ruled "(a)": the precedence
lives in the tile engine, in one place — a playing session's face > the API queue > notifications > a paused track's face.
Fix: ca73663 (arbiter + JVM tests), 7cb3742 (the fix review's F-1..F-5). QA driver commits since: 4769695 (E6 re-cut: the
shell's clock is asserted only on opens with nothing recording), aefe4b9 (the L11-1 row: F-1 / F-2 sub-steps, phase 01
E15 / E17 asserted). Pass 5 is the full gate on ONE APK after all of that (docs/plan/qa/phase-11/SUITE.txt, the row
directories under docs/plan/qa/phase-11/, UNIT.txt).

Read first:
- docs/plan/phase-11-tile-quick-actions.md (FINAL; Acceptance, Edge Cases, QA evidence incl. NEEDS-HUMAN rows)
- docs/plan/INDEX.md (row 11, the Change Log lines dated 2026-09-23/24 about phase 11 and L11-1)
- docs/plan/review/2026-09-24-L11-1-fix-plan.md (the fix and ITS gate) and review/2026-09-24-L11-1-fix-review.md
- docs/plan/qa/phase-11/README.md (not yet updated for pass 5 — judge the evidence, not the README's result column)
- the prior gate reviews: review/2026-09-23-phase11-gate-design.md, review/2026-09-24-phase11-gate-evidence.md
- git log --oneline -25; git show for the commits named above

Output: write your report to the path given below, first line `BLOCKING: n · SHOULD-FIX: n · NOTE: n`, then a findings
table (id | sev | file:line | finding | evidence | exact fix), then "What I checked and found right". BLOCKING = the
phase 11 gate or the L11-1 fix gate cannot be called passed as evidenced. Quote file:line or evidence-file lines for every
claim; a claim you could not verify from files is not a finding. Reply with the first line and the path only.

## Reviewer 1 — design / correctness

You are Reviewer 1 (design / correctness lens) in a two-reviewer gate. Follow the shared brief above exactly.

Your lens — design and correctness of the code, not the evidence files' completeness (Reviewer 2 has that):
1. The L11-1 fix as built (git show ca73663 7cb3742; app/src/main/kotlin/app/tileshell/tiles/engine/LiveTileEngine.kt,
   feeds/MusicFeed.kt, feeds/TileNotificationListener.kt, tiles/api/LiveTileStore.kt, and
   app/src/test/kotlin/app/tileshell/tiles/engine/TileSourcePrecedenceTest.kt): does it implement Jeremy's ruling exactly
   (playing face > API queue > notifications > paused face; one place)? Did 7cb3742 actually resolve the fix review's F-1 to
   F-5 (review/2026-09-24-L11-1-fix-review.md) without new defects — threading (the forget listener posts to the main
   looper; the engine's maps and who calls them from which thread), a forget racing a publish, a reinstall, a package
   whose only content was a secondary tile, badge precedence untouched? Is anything in phase 01 / phase 10 behaviour
   changed beyond the ruling? F-6 (NOTE: publish() still accepts a bare pkg:<p> key) was left as recorded — say whether
   that is acceptable or must be fixed before the gate passes, with reasons.
2. The phase 11 design re-judge (your earlier report: review/2026-09-23-phase11-gate-design.md). E10 now runs: does the
   consumed-DOWN fix for T11-38 (d34d7bb) do what the doc requires, now that a device run exercises it
   (qa/phase-11/E10/E10.txt and its ring)? Any earlier finding of yours that is still open (G-D5 was left for Jeremy at
   H4 — leave it there unless something changed)?

Write your report to ~/projects/metro-launcher/docs/plan/review/2026-09-24-phase11-rejudge-design.md.

## Reviewer 2 — testability / evidence

You are Reviewer 2 (testability / evidence lens) in a two-reviewer gate. Follow the shared brief above exactly.

Your lens — does the captured evidence prove what the gate claims; you judge files, not code design (Reviewer 1 has that):
1. Pass 5 (docs/plan/qa/phase-11/SUITE.txt, commit aaf7adc; each row dir E1..E16, L11-1; UNIT.txt; the .<row>.console
   files): one APK, one driver set, every row's exit code; does each row's evidence prove the phase doc's Acceptance and
   Edge Cases it is mapped to (README.md's "What it proves" column and the doc's QA evidence section)? Any assertion that
   cannot fail, or passes on a precondition that did not hold? Note especially: the L11-1 row's first cut of sub-step (d)
   passed vacuously (the reinstall never happened — kept in qa/phase-11/L11-1-smoke2-p02e5-restore/, then
   L11-1-smoke3-fossify-missing/); check the rewritten (d) in scripts/l11_1.sh and pass 5's L11-1/L11-1.txt cannot do that
   again, and look for the same failure shape elsewhere in the L11-1 row.
2. The L11-1 fix plan's own gate list (review/2026-09-24-L11-1-fix-plan.md "Its gate"): JVM precedence + race tests,
   the L11-1 row, phase 11 E10 with its control sub-step, phase 01 E15 / E17 and "phase 01 E8's playback face" re-verified,
   phase 11 E14's coverage, the fix review's F-1 request to re-run phase 02 E5. Is every item evidenced in pass 5 or a named
   commit's evidence? Name any that is not.
3. The E6 method re-cut (commit 4769695; the INDEX Change Log line "PHASE 11 E6 METHOD RE-CUT"; pass 5 E6/E6.txt):
   is asserting only unrecorded opens justified by the evidence in qa/phase-11/E6-pass4-83ms/, and does E6 still fail
   when motion is wrong?
4. Re-judge your earlier report review/2026-09-24-phase11-gate-evidence.md: is each of its findings still resolved in
   pass 5?

Write your report to ~/projects/metro-launcher/docs/plan/review/2026-09-24-phase11-rejudge-evidence.md.
