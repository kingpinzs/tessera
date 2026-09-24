# Phase 11 — tile quick actions: QA evidence

Spec: [phase-11-tile-quick-actions.md](../../phase-11-tile-quick-actions.md) (FINAL 2026-09-23). Device: AOSP AVD
`tileshell_fhd` (emulator-5554, 1080×2340 @ 450 dpi, API 36), debug build. Drivers: `scripts/` on phase 03's floor
(`lib.sh`, symlinked) with `q.sh`. The whole gate on one APK and one driver set: `scripts/run_all.sh` → `SUITE.txt`.
Earlier passes are kept: `SUITE-pass1-be98994.txt` (commit a7af265), `SUITE-pass2-f0284db.txt` (97697c8); SUITE.txt is pass 3.

## Rows

| Row | Driver | What it proves | Result: pass 3 on apk 8e2f6479 (SUITE.txt, incl. its appended re-run) |
|---|---|---|---|
| E1 | e1.sh | the burst at the 783-ms hold; labels One–Four; edit mode; both stay after UP | PASS 18/18 |
| E2 | e2.sh | 740 ms taps / 830 ms bursts; a 4-px wobble keeps it; a 40-px glide drags and closes it | PASS 17/17 |
| E3 | e3.sh | the four in corner order; tileclient-b's one; a failed icon (no glyph, still runs); Weather / folder / Unassigned / secondary give their reasons | PASS 30/30 |
| E4 | e4.sh | each satellite launches its shortcut from the tile launch path (≥ 250 ms after the close); tile promoted on return | PASS 42/42 |
| E5 | e5.sh | every tap and event while open (elsewhere, another tile, the held tile, label, Back, resize, unpin, Home, sleep, scroll, name strip, press styles, no hold in edit mode) | PASS 70/70 |
| E6 | e6.sh | every warm open asserted on the shell's clock (peak, overshoot, maxGapMs, settle ≤ 249.1 + maxGap), the burst's t0 within one frame of the entry's first frame, a half-size recording retaken until its spacing passes | PASS 36/36 |
| E7 | e7.sh | geometry: corner, line above (row), line below (top-left), 2-column WIDE in row 1, band (clamped corner), scrolled | PASS 58/58 |
| E8 | e8.sh | not a shortcut host; disabled; disable while open; uninstall while open; unstartable shortcut | PASS 29/29 (append re-run) |
| E9 | e9.sh | managed profile bursts and launches as its user; quiet / locked private space → profile quiet | PASS 20/20 |
| E10 | e10.sh | on phase 01 E8's player (Fossify): a hold on a transport control fires it (no burst, no edit mode); the art hold bursts | **BLOCKED (L11-1)** — the strip never stays; the control sub-step is not run |
| E11 | e11.sh | the app list's and Music's hold menus unchanged, no burst | PASS 7/7 |
| E12 | e12.sh | gesture navigation: the row's line clears the gesture area; a satellite runs | PASS 8/8 |
| E13 | e13.sh | phase 02 regress / E1 / E7 bracket+geometry / E8 on this build; hold+drag; 830 ms on Contacts; press styles; exports | PASS 39/39 (phase 02 E7's motion sub-rows excluded, T11-6) |
| E14 | e14.sh | every `[quick]` line, reason and close in this build's saved rings; `query failed` by its JVM test | PASS 28/28 (append re-run) |
| E15 | e15.sh | Music's pivots and Start settings' pages as satellites, landing on the right pivot / page; Weather and Tess none | PASS 19/19 |
| E16 | e16.sh | the promoted tile bursts in its grid cell (E1's held bounds, E7's offsets); a cell off the page → off screen | PASS 18/18 (append re-run) |

Unit: 639/639 in the suite; `QuickRulesTest` (23) — the selection rule, the decision and its reasons (`query failed` included), the
geometry, Compose's own `spring(0.65, 1500)` against the doc's fingerprint, the launch outcome.

## Defects the gate found and fixed at the producer (each its own commit)

1. **A hold on a transport control entered edit mode** (phase 02's gesture code; T11-38). `d34d7bb`. **Fixed, UNVERIFIED on a
   device:** E10 cannot reach a control while L11-1 wipes the strip.
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

- **L11-1 (blocks E10; phases 01 / 10's part):** `TileNotificationListener.rebuild()` publishes under the same
  `pkg:<package>` key as `MusicFeed`, and republishes it as null whenever the package has no eligible notification (a
  media notification is ineligible here: the ring shows `NOTIFICATIONS=0`; which clause of `eligible()` rejects it is not
  logged). Each notification update therefore wipes the playing tile's now-playing face and its transport strip. Proven
  on the row's own fixture, Fossify Music Player (E10/l11-1-sequence.txt: the key published with the face 4 times and as
  null 5 times; the tile grows but never keeps a strip), and on the shell's own player (pass 1: seven nulls between +202
  and +386 ms after the face). Jeremy saw the strip work on the phone on 2026-09-22 — the order differs there. Not fixed
  here (Hard Rule 3); a fix plan is his call.
- **L11-2 (environment, cause not proven):** one input-dispatch ANR in `StartActivity` during pass 2's E6 (DOWN at the
  fixture, 5000 ms): E6-pass2-attempts2to5-nohold/anr_2026-09-24-08-04-57.txt — the shell's main thread sat in the
  framework's own frame draw (`ViewRootImpl.performDraw` → `DrawFrameTask::drawFrame`, no app frame on the stack) while
  `screenrecord` encoded on a host running two emulators. Not reproduced in the 30+ recorded opens since; E6 now fails an
  attempt when an app-error dialog is up and keeps its trace.
- **Phase 03's exported allow-list lacks phase 10's `MusicActivity` and `MusicService`** (exported since phase 10). Phase
  11 adds no export (E13 proves the manifest diff is meta-data only); phase 03 E5 as written fails today.
- **Cold first hold janks in phase 02's own edit-mode entry** on this debug build, a burst or not: COLDJANK/ (cold_jank.sh,
  gfxinfo of a cold hold on folder:qa and on the fixture, two runs each). E6 records its cold `motion open` (maxGapMs 50,
  11 frames), measures warm, and P2 reads the cold open on the phone's release build.
- **E6's recorder, not the app, dropped frames at full size:** 19-34 ms gaps in opens whose own frame clock read 16.7 ms
  throughout (E6-smoke-fullsize-recording/); at 540x1170 a recording meets phase 05's 18.2-ms rule.
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
press feedback judged once phase 13 is built) · H4 the 16-epx stand-off, the line arrangements and the clamping (note: a line sits one gutter from the tile, so its end
satellite covers ≈ 33 px of the unpin or resize disc and a tap there runs the satellite — built as the doc words it; gate
review G-D5 suggests the 16-epx stand-off for lines if Jeremy dislikes it) · H5
fewer than four fill top-left first · H6 a disc tap acts and closes the burst · H7 a satellite launch leaves edit mode
and promotes the tile · H8 the shell apps' shortcut sets (and their simple glyphs) · H9 any approximation not covered ·
H10 the close motion and the launch vanishing in the exit's first frame · H11 Samsung shortcut icons (phone, P3).

## Phone rows (not run: need the S25 Ultra)

P1 Samsung's App Shortcuts reach a third-party HOME holder · P2 the open line on the phone (cold, release build) · P3
Samsung icons through the glyph rule · P5 One UI's gesture area · P6 system Home with a burst open.
