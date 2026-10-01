# Phase 14 QA gate — round 1 reviews and triage (2026-09-30)

Reviewers: opus (design / correctness) + opus (evidence integrity, adversarial). Codex out both ways (MCP failed to
connect; CLI usage limit until 2026-10-03 16:48). Never Fable (Jeremy, 2026-09-30). Reviewed: branch phase-14 at
92ee879b / bb0a4f1c, the final run on apk a869ae73 (`qa/phase-14/FINAL-a869ae73/final-run.txt`). The reports were
returned as messages (subagents do not write files); their findings are recorded below in the reviewers' own terms.

**Verdicts: design NOT PASS (1 BLOCKING) · evidence NOT PASS (2 BLOCKING).**

## Design reviewer

| # | sev | finding | verified by the agent | action |
|---|---|---|---|---|
| B1 | BLOCKING | The Weather pod's "Location is off" line calls `WeatherFeed.onAppResumed()` on every Start resume and swipe-in; with Location off, the permission held and a cached report, that retry fetches with the last coordinates — one request per Home press. Breaks "no new internet use" / "no network beyond Weather's own refresh". The retry came in with ce1260f5 | yes (`PodBayPage.kt` retry, `WeatherFeed.onAppResumed` → `refresh`, `LocationOff` keeps the cached coordinates) | FIXED 08cb8a35: `WeatherFeed.onAccessLineShown()` refreshes only when access is back (`accessRestored`, WeatherAccessTest 5/0). Device: EDGE case W — four resumes + two swipe-ins with Location off: 0 `refresh start`, 0 `fetch provider=`; Location on → `refresh start reason=access restored`, rows return |
| S1 | SHOULD-FIX | A destination in the show's args (`EXTRA_DESTINATION`, phase 14's Reminders pod) is not gated when locked: `goTo` has no locked check, and any foreground activity can call `showAssist(args)` — Tess opens on Reminders over the keyguard | yes (code) | FIXED 08cb8a35: `goTo` ignores a non-Home destination while locked (as `openPane` does). NOT proven on a device — it needs a helper app that calls `showAssist`; code-level only |
| S2 | SHOULD-FIX | A pod-bay request can stay pending indefinitely (nothing clears `PodBayRequests` on hide; any closing reply sends HOME while one is pending): Tess dismissed over another app → the pod bay opens at the next Home, or over a later request's result | plausible from the code; the doc says the request survives dismissal and sets no bound | FOR JEREMY (a design bound the doc does not give) |
| S3 | SHOULD-FIX | The Agenda pod sticks on "Calendar access is off" after a grant with an empty calendar (`hasAccess` read in composition; the agenda goes empty → empty) | yes | FIXED 08cb8a35: access is state, re-read on resume. Device: EDGE case A |
| S4 | SHOULD-FIX | "Process death → Start" holds only for force-stop: `rememberPagerState` is saveable | NOT REPRODUCED: `am kill` does not kill the launcher (same pid) and Home → Start; with "Don't keep activities" (the same saved-state restore) Home → Start alone (`qa/phase-14/GATE-FIXES/s4-probe/probe.out`) | NOTE |
| S5 | SHOULD-FIX | L14-2's fix makes every shell activity count, so a ring or the unlock prompt becomes a Back target through `firstForPackage` | yes | FIXED 08cb8a35: of the shell's activities only Start and its catalog apps count (`shellPageCounts`, BackHistorySurfacesTest 5/0) |
| S6 | SHOULD-FIX | The Now playing pod hides in transitional states (skipping, seeking, connecting) | yes | FIXED 08cb8a35: shown for every state but STOPPED / NONE / ERROR (MusicRulesTest red first: 20 tests, 1 failed; then 20/0) |
| N | NOTE | multi-day all-day events show on their first day only (the doc's "day = BEGIN in UTC", literally); E3's "9:30 AM" vs the doc's "9:30"; the L14-2 test does not pin that `findTarget` uses the helper; `pendingCause` can outlive a voice scroll that ends where it started (diagnostics only) | — | recorded |

Checked with no finding: page order and indices, Back / Home / the Windows key, the focus and edge guards, the wizard
hold and release, single take per request, the lock gate before `record`, matcher order / hotwords / Brand strings, the
Settings page, the shared backdrop key, the diagnostics literals, the manifest (no new component or permission), no crash path.

## Evidence reviewer

All 17 logs stamp a869ae73 with `apk match yes`; driver blobs in the logs equal the files; phase 03's E3 / E5 / E10 ran
unchanged with their directories restored; E7's exported set is 17 = 17 and unchanged by phase 14; "fossify" → "clock"
and the removed `wm dismiss-keyguard` fallback are legitimate (the second a strengthening); E15 matches the ruling.

| # | sev | finding | action |
|---|---|---|---|
| B1 | BLOCKING | E13 is a failed row (70/1: phase 03 E10 as a whole row). Its reading of E10's nine failures: eight are `e10.sh:98` grepping the match line for the utterance id; one is `e10.sh:29,79` counting DeskClock alarms while Tess now sets the alarm in the shell's own store (`[cortana] alarm set in-process`). Phase 03's own E10 never passed; every security clause passes (nine Unlock cards, nothing sent / dialled / added / stored, the bouncer, the carded request after unlock) | Q-E10 is with Jeremy (asked 2026-09-30, not answered) |
| B2 | BLOCKING | The working tree held uncommitted product edits (the design fixes) that no row had run on | the fixes are committed (08cb8a35), built (apk 823c308a) and every row re-run on that APK (`qa/phase-14/FINAL/final-run.txt`) |
| S1 | SHOULD-FIX | E3 replaces a doc clause with a RECORD (the reminder utterance's parse), compares titles lower-cased and asserts "9:30 AM" — agent readings in the 2026-09-29 Change Log, not owner rulings | FOR JEREMY (sign-off on the 2026-09-29 agent readings) |
| S2 | SHOULD-FIX | E8 never asserts the window between the Unlock tap and the PIN | driver: the doors line and `[podbay] opened` must come after `unlock bridge: dismissed` |
| S3 | SHOULD-FIX | "each `<n> rows` line matches the node count" is asserted for weather only | driver: `assert_rows_match` for all four pods in E3 |
| S4 | SHOULD-FIX | E13's C-3 check has no guard that its slice covers a restore; no `seed_baseline` | driver: the guard added; the seed not added (recorded) |
| S5 | SHOULD-FIX | `session_window` is only ever asserted "no" | driver: asserted "yes" before the Unlock tap in E8 |
| S6 | SHOULD-FIX | absence checks would pass on an unreadable ring (E8, E10, E11, E14) | driver: `absent_in` — the slice must hold a ring line first |
| N | NOTE | E2's "screencap shows the drawn bars" is node presence plus an unread PNG; E8's post-unlock RMS is not captured; the allow-list is phase 03's as extended by phases 05 / 10 / 15; small tautologies in E13 / E15 labels | recorded |

### Edge cases no E-row exercised (the reviewer's list) → `qa/phase-14/scripts/edge.sh`

Driven on the AVD by EDGE (run 2 on 823c308a: 39/0): the phrase while the pod bay is open (P1), "close" on Start (P2),
"open the bay" and the one-word "podbay" typed (P3), the session dismissed mid-line (P4: two Backs — the first clears
the result page), a pan from a bottom-row tile (G1), 20 events → 6 rows and a long title on one line (C).
NOT driven, for Jeremy: Home while the pane is mid-swipe (this AVD does not re-deliver HOME to a resumed home activity
— phone row P5's path); a reminder firing or completed while the pane shows; a place or person reminder's subline; a
second request in the same session; a pan during a live flip; Show more tiles / theme / accent / the X5 slider; a pod
switched off while its feed updates; the grammar and open passes disagreeing (cannot be forced).
