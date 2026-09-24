# Phase 11 — tile quick actions: QA evidence

Spec: [phase-11-tile-quick-actions.md](../../phase-11-tile-quick-actions.md) (FINAL 2026-09-23). Device: AOSP AVD
`tileshell_fhd` (emulator-5554, 1080×2340 @ 450 dpi, API 36), debug build. Drivers: `scripts/` on phase 03's floor
(`lib.sh`, symlinked) with `q.sh`. The whole gate on one APK and one driver set: `scripts/run_all.sh` → `SUITE.txt`.
Earlier passes are kept: `SUITE-pass1-be98994.txt` (commit a7af265), `SUITE-pass2-f0284db.txt` (97697c8), `SUITE-pass3-e26d4fe.txt`,
`SUITE-pass4-stopped-ca73663.txt` (stopped at E7 for the L11-1 fix review's changes), `SUITE-pass5-aaf7adc.txt` (the first after
the L11-1 fix); SUITE.txt is pass 6 (f8cd00a), after the Opus re-judge's fixes and with the EDGE row; E13 failed there on a driver race (its short press ran past the hold); the fix's
first cut (a45b2ad) still pressed ~800 ms, the second (1477e75) passes — both re-runs appended with E14.

## Rows

| Row | Driver | What it proves | Result: pass 6 on apk 50129ec4 (code 9f790b7, drivers f8cd00a; SUITE.txt: SUITE PASSED, the latest line per row: E13 / E14 re-run twice in its appended sections, last on 1477e75) |
|---|---|---|---|
| E1 | e1.sh | the burst at the 783-ms hold; labels One–Four; edit mode; both stay after UP | PASS 18/18 |
| E2 | e2.sh | 740 ms taps / 830 ms bursts; a 4-px wobble keeps it; a 40-px glide drags and closes it | PASS 17/17 |
| E3 | e3.sh | the four in corner order; tileclient-b's one; a failed icon (no glyph, still runs); Weather / folder / Unassigned / secondary give their reasons | PASS 30/30 |
| E4 | e4.sh | each satellite launches its shortcut from the tile launch path (≥ 250 ms after the close); tile promoted on return | PASS 42/42 |
| E5 | e5.sh | every tap and event while open (elsewhere, another tile, the held tile, label, Back, resize, unpin, Home, sleep, scroll, name strip, press styles, no hold in edit mode) | PASS 70/70 |
| E6 | e6.sh | five warm opens with nothing recording, each asserted on the shell's clock (peak, overshoot, maxGapMs, settle ≤ 249.1 + maxGap), the burst's t0 within one frame of the entry's first frame; separate half-size recordings retaken until their spacing passes (their shell-clock numbers noted, not asserted: E6-pass4-83ms/; a clipped window is retaken). The clock numbers are the spring's own values at frame times, so the DRAWN motion rests on H1 / H2 (R2-10) | PASS 35/35 |
| E7 | e7.sh | geometry: corner, line above (row), line below (top-left), 2-column WIDE in row 1, band (clamped corner), scrolled | PASS 58/58 |
| E8 | e8.sh | not a shortcut host; disabled; disable while open; uninstall while open; unstartable shortcut | PASS 29/29 |
| E9 | e9.sh | managed profile bursts and launches as its user; quiet / locked private space → profile quiet | PASS 20/20 |
| E10 | e10.sh | on phase 01 E8's player (Fossify): a hold on a transport control fires it (no burst, no edit mode, the control dump clean); on the Music tile SHOWING its strip (the shell's player playing), a hold on the art above the strip, on no control, enters edit mode with Music's four | PASS 19/19 |
| E11 | e11.sh | the app list's and Music's hold menus unchanged, no burst | PASS 7/7 |
| E12 | e12.sh | gesture navigation: the row's line clears the gesture area; a satellite runs | PASS 8/8 |
| E13 | e13.sh | phase 02 regress / E1 / E7 bracket+geometry / E8 on this build; hold+drag; 830 ms on Contacts; press styles; exports | PASS 45/45 (phase 02 E7's motion sub-rows excluded, T11-6) |
| E14 | e14.sh | every `[quick]` line, reason and close in this build's saved rings; `query failed` by its JVM test | PASS 28/28 |
| E15 | e15.sh | Music's pivots and Start settings' pages as satellites, landing on the right pivot / page; Weather and Tess none | PASS 19/19 |
| E16 | e16.sh | the promoted tile bursts in its grid cell (E1's held bounds, E7's offsets); a cell off the page → off screen | PASS 18/18 |
| L11-1 | l11_1.sh | the fix's own row: TileSourcePrecedenceTest's own results (12, three cases by name); the API queue over a notification, and clearing it shows the notification (a positive control); Fossify (phase 01 E8's player, its face asserted) and the shell's player (session state asserted) keep the strip across notification updates, every publish after the face `-> shows music (playing)`, a pause drops it; (d) a player uninstalled WHILE PLAYING ends with nothing shown — 9f790b7's own null publish seen after the engine's forget; the R1-1 race window itself is not produced on demand (uninstall-sequence.txt) — and, in ONE shell process, its reinstall re-pinned from the app list inherits no face (F-2); (e) a secondary tile's API content survives a listener rescan (F-1); phase 01 E15 / E17 asserted; phase 02 E5 as a regression | PASS 69/69 |
| EDGE | edge.sh | the doc's Edge Cases list as a list: each bullet no other row carried is a sub-step (a hold during a live flip, during a fling, a second finger, an incoming call, the keyguard, a listener restart, process death, Show more tiles, a two-tile folder dissolving, a blank and a long label, theme Light / Dark, X5 at 0 / 100 %, RV10 size / density / font scale); EDGE.txt's index maps all 42 cases (scripts/edge_index.tsv) | PASS 61/61 |

Unit: 651/651 in the suite, kept in UNIT-results/ (`TileSourcePrecedenceTest` 12, the L11-1 fix's, asserted by the L11-1 row); `QuickRulesTest` (23) — the selection rule, the decision and its reasons (`query failed` included), the
geometry, Compose's own `spring(0.65, 1500)` against the doc's fingerprint, the launch outcome.

## Defects the gate found and fixed at the producer (each its own commit)

1. **A hold on a transport control entered edit mode** (phase 02's gesture code; T11-38). `d34d7bb`. Verified on the device in
   pass 5's E10 (the control fired and paused the player; no `[edit] hold`, no burst), once L11-1 was fixed.
2. **Every bottom-row tile read "off screen"** — the off-screen test used the satellites' clamp area, which ends above the
   row by design (E7). `a53a51e`.
3. **A quiet-profile tile would say "no app"** — Android hides a quiet profile's activities from the catalog, so the
   quiet check never ran (E9, reasoned from the catalog code before the row's first run). `1950b89`.
4. **Music's pivot headers read `checked`, not `selected`** — Compose maps `selected` to checked without `Role.Tab` (E15).
   `be98994`.
5. **The gate review's four** (G-D1 a background-load exception could cancel Start's scope; G-D3 a row tile's satellite launch
   missed the tapped-tile fade; G-D4 a satellite launch left the New caption; G-D6 a pending close could log `closed` without
   `burst on`). `f0284db`.

## Findings recorded (not phase 11 product defects)

- **L11-1 — FIXED (phases 01 / 10's part; Jeremy "(a)", review/2026-09-24-L11-1-fix-plan.md):** three producers (MusicFeed,
  the notification listener, the Live Tile API store) wrote one `pkg:<package>` slot, so every media-notification update
  republished it as null and wiped the playing tile's face and strip (pass 3's E10/l11-1-sequence.txt, commit 0f9d116; pass
  6's same file shows the notification publishes resolving to the music face). The engine keeps each producer's content
  apart and shows one by a fixed order — a playing session's face > the API queue > notifications > a paused track's face:
  `ca73663`; the fix review's findings `7cb3742` (a secondary tile's key is never cleared by the arbiter; an uninstalled
  package keeps nothing from any source); the Opus re-judge's `9f790b7` (MusicFeed's forget also nulls its own slot on
  main, closing a race where a session dying mid-uninstall re-created the gone package's face; the forget listener
  registers once). Tests: `TileSourcePrecedenceTest` (12) and the L11-1 row. The row's first cuts were weaker than they
  read: the uninstall sub-step once passed with no reinstall (L11-1-smoke2 / -smoke3), then read the tile only after a
  layout restore had restarted the shell, which a fresh process passes with or without the fix (the re-judge, R2-3) — it
  now keeps one process and re-pins from the app list.
- **L11-2 (environment, cause not proven):** one input-dispatch ANR in `StartActivity` during pass 2's E6 (DOWN at the
  fixture, 5000 ms): E6-pass2-attempts2to5-nohold/anr_2026-09-24-08-04-57.txt — the shell's main thread sat in the
  framework's own frame draw (`ViewRootImpl.performDraw` → `DrawFrameTask::drawFrame`, no app frame on the stack) while
  `screenrecord` encoded on a host running two emulators. Not reproduced in the 30+ recorded opens since; E6 now fails an
  attempt when an app-error dialog is up and keeps its trace.
- **Phase 03's exported allow-list lacks phase 10's `MusicActivity` and `MusicService`** (exported since phase 10). Phase
  11 adds no export (E13 proves the manifest diff is meta-data only); phase 03 E5 as written fails today.
- **Cold first hold janks** on this debug build: phase 02's own edit-mode entry with no burst (folder:qa: 25.45 % and
  27.27 % janky frames), and more with the burst (the fixture: 41.67 % and 37.50 %), so the burst adds to the cold jank:
  COLDJANK/ (cold_jank.sh, gfxinfo of a cold hold on each, two runs each; re-worded by the re-judge, R2-14). E6 measures
  warm; P2 reads the cold burst open on the phone's release build — that is the open to read.
- **E6's recorder dropped frames at full size:** 19-34 ms gaps in recorded opens whose own frame clock read 16.7 ms (two
  read 33.3 ms: attempts 1 and 3, E6-smoke-fullsize-recording/E6.txt); at 540x1170 a recording meets phase 05's 18.2-ms
  rule. A recording whose motion window is clipped is retaken (R2-7).
- **The shell is a shortcut host through its ASSISTANT role too**, so E8 (a) removes HOME and ASSISTANT.
- **A full-width WIDE tile mid-grid keeps a clamped corner** (never on the tile) under the doc's own rule: observed at build
  and pinned by `QuickRulesTest.aFullWidthWideTileMidGridKeepsAClampedCorner`, not a device row; the doc's "wide tile at the
  right edge in 2-column mode" example was geometrically wrong; E7's WIDE case is in row 1.
- **Dump route:** phase 05's driver with `--no-restart` hung under a held pointer and then refused every run; without it
  the runner starts its own process. The accessibility root comes back null on a share of runs while the page changes
  (plain Start 4/8, a burst's first seconds 3/8), so `qdump` retries until a dump has nodes.

## NEEDS-HUMAN (all accept rows, Jeremy)

H1 the burst as a whole (dampingRatio 0.65, boundsInRoot tracking) · H2 stiffness 1500: ≈ 107-ms peak, ≈ 249-ms settle,
≈ 36-ms close · H3 the satellite (small-tile square, icon rule, label outside in the theme's text colour, both themes;
press feedback judged once phase 13 is built; at X5 100 % with a background picture a satellite is a fifth of the accent over the dimmed tile behind it, and that tile's glyph shows through it (People under One, Mail under Two) — EDGE-pass6/x5-100.png; an opaque satellite would be a product change to a FINAL Decision (doc :86-87)) · H4 the 16-epx stand-off, the line arrangements and the clamping (note: a line sits one gutter from the tile, so its end
satellite covers ≈ 33 px of the unpin or resize disc and a tap there runs the satellite — built as the doc words it; gate
review G-D5 suggests the 16-epx stand-off for lines if Jeremy dislikes it) · H5
fewer than four fill top-left first · H6 a disc tap acts and closes the burst · H7 a satellite launch leaves edit mode
and promotes the tile · H8 the shell apps' shortcut sets (and their simple glyphs) · H9 any approximation not covered ·
H10 the close motion and the launch vanishing in the exit's first frame · H11 Samsung shortcut icons (phone, P3).

## Phone rows (not run: need the S25 Ultra)

P1 Samsung's App Shortcuts reach a third-party HOME holder · P2 the open line on the phone (cold, release build) · P3
Samsung icons through the glyph rule · P5 One UI's gesture area · P6 system Home with a burst open.
