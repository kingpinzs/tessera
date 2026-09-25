BLOCKING: 1 · SHOULD-FIX: 6 · NOTE: 11

# Phase 11 gate — Reviewer 2, evidence integrity (2026-09-24)

Scope: does the captured evidence under `docs/plan/qa/phase-11/` PROVE each acceptance row of
`docs/plan/phase-11-tile-quick-actions.md` (FINAL)? Read-only. Build under test: APK md5 `24a6fcef79bb96df`
(SUITE.txt:3, commit f0284db). Every claim below was re-derived from the drivers, the logs, the rings, the dumps and
— for E6 — the accepted recording's frames; commit messages and the README were treated as claims.

## Provenance (brief item 2) — verified, one exception

- Every row log E1–E16 stamps `apk installed 24a6fcef79bb96df` = SUITE.txt:3 and `apk match yes`, and its `driver … blob`
  equals `git hash-object` of the committed driver at HEAD (e1 28247758 … e16 ec21c166; checked all sixteen). `lib.sh blob
  f296b3dd` = HEAD's `qa/phase-03/scripts/lib.sh`. E13's phase-02 sub-drivers stamp their own blobs (ef27b48 / 3c86569 /
  90a30e6 = HEAD) and build guard 24a6fcef (E13/p02-E01/E01.txt:2-3 etc.).
- Exception: **E6.** SUITE.txt:10 records `E6 exit 1 … 2 passed, 10 failed`. The passing E6/E6.txt (08:33:33) was produced by a
  driver edited AFTER the suite (blob d6769100 in the suite's two kept runs → a37c6615 at HEAD: retakes 5→10, screenrecord
  bit rate 6 M→4 M, `git diff HEAD~1 HEAD -- scripts/e6.sh`) and run alone after E14 had already read the rings (E14.txt:3
  08:30:58). Its header is otherwise valid (this APK, the committed driver). See G-E6-1.

## Ring slices (brief item 3, C-20) — clean

Every ring assertion in e1–e16 reads `ring_since`/`quick_since` from a MARK taken immediately before the step's action, after
the previous step's `c6`/`restore`; absence assertions read the same slice. No assertion was found that could pass on a
line from an earlier step. (Pattern `MARK; hold_at …` dumps once before the hold — harmless, nothing else writes between.)

## Per-row verdicts

| Row | Verdict | Decided by | Notes |
|---|---|---|---|
| E1 | PASS | e1.sh:18-61 · E1/E1.txt:14-31 | cell 365 439 708 782 ±1; burst, sats 0-3, labels One–Four, Five absent, discs, dim, resumed; shortcuts + burst lines from the pre-DOWN MARK; UP → dumpdiff IDENTICAL + burst/discs ±1 with equal texts; held-reference saved |
| E2 | PASS | e2.sh:12-68 · E2/E2.txt:15-35 | 740: ring has no `burst on`/`[edit] hold` (the dump checks are vacuous, G-E2-1); 830: burst + discs + Start resumed; wobble: burst stays, bounds equal, no drag; glide: dump BEFORE UP has no burst, drag copy 40 px, `drag start` + `closed: drag`, back in cell ±1 |
| E3 | PASS | e3.sh:22-114 · E3/E3.txt:15-45 | corner order + rank labels; b: sat 0 only + `1 (1 shown: qa_dyn)`; badicon: sat 1 UNIFORM (spread 0, ±2) vs sat 0 VARIED, icon-failed line, tap `startShortcut ok`; weather/folder/MAIL/secondary: edit on, no burst, exact reason; Weather activity line |
| E4 | PASS | e4.sh:11-48 · E4/E4.txt:15-57 | ×4 satellites: ShortcutActivity resumed, `shortcut_id` exact, tap ok + `closed: launch` + `start exit finished tile=…`, ok−launch 352–389 ms (≥ 250); Home → no discs/burst, `recent_app_row`, promoted 13 px above the row. Recording saved, not analysed (G-E4-1) |
| E5 | PASS | e5.sh:21-220 · E5/E5.txt:16-97 | every tap/event asserted from its own MARK; Tess centre checked against `quick_sat*` rects; held-tile tap has NO `[edit] tap on the held tile`; resize → `MEDIUM -> SMALL` + `closed: resize`; Home via `nav_windows` + `[start] home:`; SLEEP → `Awake` + `stop`; scroll Δy 91, sats Δ 0, fresh `rest=`; no-hold-in-edit; press tilt/p4 EQUAL ±1, burst stays, `press_none` restored |
| E6 | PASS (shell clock) · NOT PROVEN (corroboration) | E6/E6.txt:22-31 · E6.txt:34 | accepted attempt: peak 100 (107±17), overshoot 6.5 (6.8±2), settle 266 (≤267), maxGapMs 16.7; close alpha0 50 (≤53), settle 50, 16.7 — all identical across every warm attempt of the three kept runs except ONE (G-E6-2). Recording ±1-frame check cannot fail (G-E6-4). SUITE.txt says FAILED (G-E6-1). ANR unrecorded (G-E6-3) |
| E7 | PASS | e7.sh:27-112 · E7/E7.txt:15-86 | `rest=` vs dump ≤1 px and `on_tile 0` in all six cases; corner 165 px / standoff 48-47; bottomrow line_above, bottoms 13 px above the dock tops, gaps 13; topleft line_below inside 84…dock−12 and margins; wide spans grid → line; band corner (clamped, none on tile) with `folder_band_top:qa`; tall scrolled 98 px, offsets = corner ±1 |
| E8 | PASS | e8.sh:17-96 · E8/E8.txt:15-48 | (a) HOME+ASSISTANT removed → `not the shortcut host`, both restored; (b) `0 (0 shown)` + `no shortcuts`; disable while open → resumed, no burst, edit on, `shortcuts changed`; (c) exactly one `removed`; (d) `startShortcut failed android.content.ActivityNotFoundException`, `start entrance`, no edit mode. Negative listing weak (G-E8-1) |
| E9 | PASS | e9.sh:53-123 · E9/E9.txt:15-40 | work u14: four sats, `…/14: 5 (4 shown…)`, ShortcutActivity `u14`, `qa_one`; QUIET_MODE → tile present, edit on, `profile quiet`; unlock → burst back; private u15 QUIET_MODE → `profile quiet`; both removed |
| E10 | FAIL (blocked) | E10/E10.txt:15,19,23 | first assertion fails (no `tile_control:…PLAY_PAUSE`); the 4 "passes" that follow are vacuous — the swipe threw `Argument expected after "1000"` (.e10.console), nothing was pressed. Art hold → edit on + Music's four is real. Diagnosis under-supported: G-E10-1, G-E10-2 |
| E11 | PASS (app list) · NOT PROVEN (Music `quick_burst absent`) | e11.sh:13-28 · E11/E11.txt:15-19 | app list: `applist_menu`, no burst, no `[quick]` in the slice. Music: `music_menu` present is real; `no quick_burst` on a MusicActivity dump cannot fail (G-E11-1) |
| E12 | PASS | e12.sh:11-32 · E12/E12.txt:15-19 | overlay on, GTOP 2250 from dumpsys window, line_above, max sat bottom 1922 < 2250, sat 1 → `qa_two`, overlay off |
| E13 | PASS | e13.sh:21-102 · E13/E13.txt:14-64 | phase-02 regress/e1/e8 exit 0 on this APK; e7's 4 counted sub-rows PASS, its 3 failures are exit/entry motion + scale/dimming timings (E13/p02-E07/E07.txt), the motion sub-rows T11-6 excludes; C-3 after each; hold+drag → no burst, `closed: drag`; Contacts 830 ms → `quick_sat:0` only; press styles EQUAL/DIFF/DIFF with no burst/hold; exports: manifest diff adds no `android:exported`, outside-list = exactly MusicActivity + MusicService |
| E14 | PASS | e14.sh:8-44 · E14/E14.txt:12-57 | union of 15 rows' `ring-*.txt`, each row's log checked for `apk installed 24a6fcef…`; every Decisions line / 7 reasons / 10 closes matched with end-anchored patterns; `query failed` by `aThrowingQueryIsCaughtAndBecomesTheReason` (tests=23, failures=0). Regex/provenance notes G-E14-1 |
| E15 | PASS | e15.sh:16-50 · E15/E15.txt:15-34 | Music labels + `…MusicActivity/0: 4 (4 shown: songs,albums,artists,playlists)` and no Settings line; sat 1 → MusicActivity, `music_pivot_header:albums selected="true"`, others false; Settings labels + line, no Music line; sat 3 → `DIAGNOSTICS` header, `settings_diagnostics` absent; weather/cortana: edit on, no burst, `no shortcuts` |
| E16 | PASS | e16.sh:16-68 · E16/E16.txt:14-27 | promoted → `recent_app_row`; hold → row gone, edit on, tile = E1's held reference 365 489 708 832 ±1, `burst on … 4 satellites`, on_tile 0, offsets = E7 corner ±1; tall+promoted+6 swipes → `[edit] hold 783ms … edit mode on`, no burst, `off screen` (G-E16-1) |

## E14 (brief item 4)

Union source: e14.sh:10-14 walks `E*/` and counts a row only if `<row>/<row>.txt` contains `apk installed <md5 of the local
APK>`; the three E6-* keep-dirs have no `<dir>.txt` and are skipped; 15 rows counted (E14.txt:12-26), all on 24a6fcef. Patterns
vs Decisions: `burst on … [1-4] satellites`, `satellite [0-3] rest=[l,t,r,b]`, `icon failed [^ ]+: `, `startShortcut ok|failed `,
`motion open … t0= peak= overshoot= settle= frames= maxGapMs=`, `motion close … t0= alpha0= settle= frames= maxGapMs=` match the
Decisions formats exactly; every `no burst on .*: <reason>$` and `burst closed: <reason>$` is end-anchored (bash `"\$"` → `$`,
lines carry no CR — `ring_since` strips it, lib.sh:191). One looser pattern: G-E14-1.

## E10 (brief item 5)

The L11-1 mechanism is real in the code: `TileNotificationListener.rebuild()` publishes under `LiveTileEngine.packageKey(pkg)`
(TileNotificationListener.kt:82-85) — the same key `MusicFeed.publish()` uses (MusicFeed.kt:170-171) — and publishes `null`
whenever the package has no eligible notification. The ring shows it: `[engine] publish pkg:app.tileshell faces=0 front=true
source=music:app.tileshell` at 08:13:59.361, then `[notif] posted app.tileshell …` → `[badge] set app.tileshell NOTIFICATIONS=0`
→ `publish pkg:app.tileshell … front=false source=null` seven times between +202 ms and +386 ms
(E10/ring-launcher.txt:138-159). So the diagnosis is supported for the fixture the driver used. Two gaps:

1. The driver did not use the fixture the row names. The spec's E10 says "with phase 01 E8's playback fixture playing"; phase 01
   E8's fixture is a THIRD-PARTY player — Fossify Music Player (qa/phase-01/E08/E08.txt, verdict: `[music] now playing
   org.fossify.musicplayer …`). e10.sh:15-19 plays the shell's own `MusicActivity` instead, and the notification storm that wipes
   the face is the shell's own `MusicService` posting seven notifications in 200 ms. Whether a third-party player's tile shows
   the strip on this build was never tried, although the README's own "race whose order differs there" argument says the order
   is what matters. The transport sub-step (`[tile_control] … PLAY_PAUSE (no launch)`, PAUSED, no burst, no edit mode) may be
   provable on the spec'd fixture; it was not attempted.
2. The part of E10 that does not depend on the strip — the art hold → edit mode + Music's four — was proven (E10.txt:25-26), but
   with no strip on the tile it is the same hold E15 proves, not "the art above the strip".

Consequence for the README: defect #1 "A hold on a transport control entered edit mode … d34d7bb" is listed as found-and-fixed.
d34d7bb changes only `EditGestures.kt` (+4 lines, `if (down.isConsumed) return`) with no test, and no row on this build shows a
transport control firing on a hold. The fix is unverified; the README presents it as verified.

## README findings the evidence does not support (brief item 6)

- "gfxinfo, a cold hold on a FOLDER … 30–36 % janky frames, 90th percentile 65 ms" — no gfxinfo capture exists under
  qa/phase-11/ (only README.md and an e6.sh comment mention it). The one supported number is E6's cold line `maxGapMs=50.0
  frames=11` (E6/E6.txt:15, E6/ring-launcher.txt:118).
- L11-1 text: "eight `… front=false source=null`" — the ring has seven; "within 350 ms" — the last is +386 ms; "a media
  notification is ongoing, never eligible" — `eligible()` (TileNotificationListener.kt:58-68) rejects an ongoing notification
  only when it is unranked or on the `miscellaneous` channel; the ring proves count=0, not which clause.
- "Unit: `QuickRulesTest` (21)" — the suite ran 23 (E14/TEST-app.tileshell.start.QuickRulesTest.xml `tests="23"`; f0284db added two).
- "A full-width WIDE tile mid-grid keeps a clamped corner" — no row on this build holds such a tile (E7's wide case is row 1).
- The E6 row ("recording retaken until its spacing passes") and the Rows table's "Result (final pass, see SUITE.txt)" omit
  that SUITE.txt records E6 FAILED, that the shell ANR'd during that run, and that the passing E6 came from an edited driver.
- Supported as written: the four producer fixes 2–4 (E7 bottomrow, E9 quiet, E15 `selected`), the ASSISTANT-host note (E8.txt:15
  shows both roles empty before the pass), the phase-03 allow-list gap (E13.txt:61-64), the dump-route retries (52 `.drv`
  files carry `roots=0` retries).

## Findings

id | sev | file:line | finding | evidence | exact fix
---|---|---|---|---|---
G-E6-1 | BLOCKING | qa/phase-11/SUITE.txt:10,21 · README.md:5 · scripts/e6.sh (a37c6615 vs d6769100) | The gate's headline artefact does not show a pass: SUITE.txt reads `SUITE FAILED: E6 E10` while README:5 calls it "FINAL PASS". E6's passing log was produced by a driver changed after the failure (retakes 5→10, bit rate 6 M→4 M) and run alone at 08:33, after E14 read the rings at 08:30; `run_all.sh` cannot re-run one row without truncating SUITE.txt (run_all.sh:10). | E6/E6.txt:3-4 (08:33:33, blob a37c6615) vs E6-pass2-attempts2to5-nohold/E6.txt:3-4 (08:04:12, blob d6769100); E14/E14.txt:3; `git diff HEAD~1 HEAD -- docs/plan/qa/phase-11/scripts/e6.sh` | Re-run `scripts/run_all.sh` end to end on APK 24a6fcef with the committed drivers so SUITE.txt is one pass on one driver set (keep the current file as SUITE-pass2-f0284db.txt, which it already duplicates). Until then README:5 must not say FINAL PASS and the E6 row must name both blobs and the standalone run.
G-E6-2 | SHOULD-FIX | scripts/e6.sh:24-49 | The retake loop selects the attempt by the RECORDING's frame spacing (spec'd), but then asserts the shell-clock numbers of that attempt only. Kept evidence shows one warm open that would have failed T11-29 discarded unasserted: rerun1 attempt 1 `settle=283` (> 267) with `maxGapMs=33.3`. 4 of 8 warm attempts across the kept runs read `maxGapMs=33.3`, 0.1 ms under C-31's bound. Peak/overshoot were identical (100 / 6.5) in all 8, so those are not at risk; settle is. | E6-rerun1-recording-19ms/E6.txt:17 (`settle=283 … maxGapMs=33.3`, retaken for `max_gap_ms 36.5`); E6/E6.txt:17-19; E6-pass2…/E6.txt:17 | Assert `peak / overshoot / settle / maxGapMs` on EVERY warm attempt's `motion open` line as it is taken (the recording alone is retaken), and log the distribution; a warm attempt whose shell line fails fails the row. If the lead accepts one dropped frame under C-31, T11-29's settle bound must say so (266 + 16.7 > 267) — that is a design-lens call, not this one.
G-E6-3 | SHOULD-FIX | qa/phase-11/E6-pass2-attempts2to5-nohold/burst.xml · commit 97697c8 message · README.md | The pass-2 E6 failure is described as "retakes 2-5 registered no hold at all". The dump kept from attempt 5 is an Android app-error dialog: `text="Tessera isn't responding"`, `Close app` / `Wait` (`android:id/aerr_close`, `aerr_wait`). The shell ANR'd during the gate and no document records it; the ring shows `[tile_anim]` ticks continuing (ring-launcher.txt:150-175), so the main thread was alive — an input-dispatch ANR or a focus loss, undiagnosed. e6.sh does not check that Start is in front before each attempt, so the loop ran four attempts against the dialog. | E6-pass2-attempts2to5-nohold/burst.xml (12 nodes, all `package="android"`), corroboration-2…5.txt empty, .e6.console `ValueError` from an empty TILE | Record it (README findings + INDEX Change Log, "L11-2: shell ANR during E6 pass 2, cause unknown"); on the next run capture `adb shell cat /data/anr/*` (or `adb bugreport`) when any dump shows `aerr_`; make e6.sh assert `start_page` in front (and fail) before each attempt. Escalate to the lead if it reproduces — a product ANR is not a harness note.
G-E6-4 | SHOULD-FIX | scripts/e6_frames.py:44-51 · scripts/e6.sh:67 | The corroboration "first satellite pixels vs the entry's first changed frame ± 1" cannot fail: `sat` is searched only from `entry` on (e6_frames.py:48), so `sat ≥ entry` always; the probe threshold (>12) crosses at frame 27 while the band already holds 39 025 new near-accent pixels at frame 26 (probeΔ 11.4), and at entry the band holds 50 138 vs 14 531 at rest — the detector counts the neighbours' contraction, not satellites. `sat_minus_entry_frames 0` in all 11 attempts of the three kept runs, although the driver's own comment expects ≈ 2 frames. | E6/open-3.mp4 frames 18-49 re-measured (frame 26: 39 025 new px, probeΔ 11.4; 27: 50 138, 17.6; 30: 14 670); e6.sh:64-66 comment | Detect satellites only in pixels outside EVERY tile's rest rect and its contracted rect (or restrict to the four `rest=` rects minus neighbours), search from `entry−3`, and assert the measured lag against the spring's predicted ≈ 2 frames; or drop the ± 1-frame claim and keep only the frame-spacing check as corroboration.
G-E10-1 | SHOULD-FIX | scripts/e10.sh:9-19 · phase-11 doc E10 | E10 is blocked on a fixture the row does not name. The spec says "phase 01 E8's playback fixture" = Fossify Music Player (third-party); the driver plays the shell's own `MusicActivity`, whose own `MusicService` posts the seven notifications that wipe the face. The spec'd route was never tried on this build, so "blocked by L11-1" is proved only for the shell's own player. | qa/phase-01/E08/E08.txt verdict ("Fossify Music Player … [music] now playing org.fossify.musicplayer"); E10/ring-launcher.txt:138-159 (`[notif] posted app.tileshell` ×7 → `publish … source=null` ×7) | Run E10 with Fossify (or any third-party session) per the row: if the strip shows, prove the transport sub-step and narrow L11-1 to the shell's own player; if it does not, the block is proved for the spec'd fixture and L11-1's text should say so.
G-E10-2 | SHOULD-FIX | README.md:33 · scripts/e10.sh:25-36 · commit d34d7bb | README lists "A hold on a transport control entered edit mode … d34d7bb" under "Defects the gate found and fixed". No evidence on this build shows the fix working: E10 fails at its first assertion, d34d7bb adds 4 lines and no test, and E10's four later PASS lines ("no quick_burst", "no edit mode", "no hold line", "StartActivity still in front") are vacuous — `center` returned nothing and `input swipe` threw `Argument expected after "1000"` (.e10.console), so no press happened and "8 passed" overstates. | E10/E10.txt:15-24; .e10.console lines 6-19 | README: mark #1 "fixed, unverified — E10 blocked". e10.sh: `assert_ne "control centre found" "" "$X"` and stop the sub-step when it is empty, so vacuous passes cannot be counted.
G-E11-1 | SHOULD-FIX | scripts/e11.sh:24-28 | The Music sub-step's "no quick_burst" reads a MusicActivity dump; the burst lives in StartActivity's root, so the node can never be there and the assertion cannot fail. The app-list sub-step has the real check (`no [quick] line` in the ring slice, e11.sh:18); the Music sub-step does not. `music_menu present` is real. | E11/E11.txt:18-19 | After the Music hold add `assert_absent "Music: no [quick] line" "[quick]" "$(ring_since "$MARK")"` (MusicActivity runs in the launcher process, same ring).
G-E2-1 | NOTE | scripts/e2.sh:15-17 | 740-ms sub-step: `t740.xml` is dumped with tileclient-a in front, so "no quick_burst" / "no edit_disc" cannot fail; the ring absences (no `[quick] burst on`, no `[edit] hold`, T11-45) carry the sub-step and are sound. | E2/E2.txt:15-19 | Drop the two dump checks or move them after the C-6 return to Start.
G-E4-1 | NOTE | scripts/e4.sh:17-20 | The Start-exit screenrecord for satellite 2 is saved (E4/exit-sat2.mp4) and never analysed; the row's claim rests on `[motion] start exit finished tile=…` and the ≥ 250-ms gap, which are the shell's clock and sufficient under C-5. | E4/E4.txt:19-20 | Either run phase 01 E10's exit check on the file or state in the log that the recording is kept, not asserted.
G-E5-1 | NOTE | scripts/e5.sh:105-107,155 | After the resize only the unpin disc's x offset from the new top-right corner is asserted (dy computed, noted 0, unasserted); the no-hold-in-edit sub-step proves "the discs move to it" by `[edit] selection moves to shell:settings`, not by disc bounds. | E5/E5.txt:44-45,68 | Assert dy too, and after the UP compare `edit_disc:unpin`'s centre with `tile:shell:settings`'s top-right ± 3.
G-E14-1 | NOTE | scripts/e14.sh:22 · E14/E14.txt:23 | `shortcuts for [^ ]+/[0-9]+:` also matches the pre-T11-12 form `<pkg>/<userId>` (no activity); E1/E3/E9/E15 assert the activity-keyed lines, so coverage holds. Also "counted E6" (E14.txt:23) was the pass-2 directory now kept as E6-pass2-attempts2to5-nohold/; the present E6/ ring post-dates E14 (same APK, union unchanged in kind). | — | Tighten to `shortcuts for [^ /]+/[^ /]+/[0-9]+:`; note in README that E14's union predates E6's standalone run (or re-run E14 last, per G-E6-1).
G-E8-1 | NOTE | scripts/e8.sh:43,50 | "(b) cmd shortcut lists nothing" counts `ShortcutInfo` lines in a file whose whole content is `Success`; a negative listing without a positive control does not prove the command lists shortcuts on this AVD. | E8/b-after-disable-shortcuts.txt | Run the same `cmd shortcut get-shortcuts --flags 9` after `reset` and assert `qa_dyn` appears, then the empty listing after `disable`.
G-E16-1 | NOTE | scripts/e16.sh:59-68 | Off-screen sub-step: the driver never asserts that the fixture's grid cell is outside the page area before the hold; the product's own `off screen` reason is the only evidence that the precondition held (six 800-px swipes on the tall layout make it likely). | E16/E16.txt:24-27, offscreen-pre.png | Assert from `offscreen-pre.xml` that no `tile:` node of the fixture other than the promoted one is laid out, or compute the cell's y from the layout and assert it is above the status bar / below the row.
G-E3-1 | NOTE | scripts/e3.sh:69 | The icon-failed assertion is a prefix (`…/qa_noicon: `); the spec's primary text `null drawable` is not asserted. The ring carries it. | E3/ring-launcher.txt:367 (`icon failed … qa_noicon: null drawable`) | Assert `: null drawable` (the spec's `(or : <exception>)` is the fallback, not the expectation).
G-README-1 | NOTE | README.md:51-53 | gfxinfo numbers (30–36 % janky, p90 65 ms) have no saved capture anywhere under qa/phase-11/. | `grep -rl gfxinfo qa/phase-11` → README.md, e6.sh comment only | Save the `dumpsys gfxinfo app.tileshell` capture the numbers came from, or reduce the finding to what E6's cold line shows (`maxGapMs=50.0 frames=11`).
G-README-2 | NOTE | README.md:43-48 · INDEX.md:88 | L11-1: "eight" → seven `front=false source=null` lines; "within 350 ms" → the last is +386 ms; "ongoing, never eligible" over-states `eligible()` (TileNotificationListener.kt:64: ongoing is ineligible only if unranked or `miscellaneous`); the ring proves `NOTIFICATIONS=0`, not which clause. The same-key claim is correct (TileNotificationListener.kt:82-85; MusicFeed.kt:170-171). | E10/ring-launcher.txt:135-159 | Correct the counts and quote the clause once known (log `ranked`/`canShowBadge`/channel id in the `[notif] posted` line).
G-README-3 | NOTE | README.md:28 | "QuickRulesTest (21)" — 23 ran and passed on this build. | E14/TEST-app.tileshell.start.QuickRulesTest.xml `tests="23"`; UNIT.txt `TOTAL 639` | Say 23.
G-README-4 | NOTE | README.md:55-56 | "A full-width WIDE tile mid-grid keeps a clamped corner" is a build-time observation with no row on this build; E7's wide case is row 1 (`line_below`). | E7/E7.txt:63-66 | Mark it "observed at build 2026-09-23, not a gate row" or add the mid-grid WIDE case to E7.

## On the E6 retake loop, as used (brief's closing question)

Sound for what the spec allows — the recording is corroboration and "else retaken" is the row's own rule — and the shell-clock
numbers it reports are genuine and deterministic across attempts (peak 100 / overshoot 6.5 in 11 of 11 lines). Not sound as an
assertion of the product's motion population: the shell-clock assertions inherit the recording's selection, the kept runs contain
one warm open (settle=283) that would have failed and was never asserted, and the corroboration's ± 1-frame check cannot fail. The
bit-rate change and the retake cap were raised after two failures; the final acceptance (18.2 ms) sits exactly on the limit.
Together with G-E6-1 (the suite says FAILED) and G-E6-3 (an ANR filed as "no hold"), E6's evidence needs the re-run and the
per-attempt assertions before its PASS can be read at face value.
