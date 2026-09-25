# Phase 11 — tile quick actions: QA evidence

Spec: [phase-11-tile-quick-actions.md](../../phase-11-tile-quick-actions.md) (FINAL 2026-09-23). Device: AOSP AVD
`tileshell_fhd` (emulator-5554, 1080×2340 @ 450 dpi, API 36), debug build. Drivers: `scripts/` on phase 03's floor
(`lib.sh`, symlinked) with `q.sh`. The whole gate on one APK and one driver set: `scripts/run_all.sh` → `SUITE.txt`.
Earlier passes are kept: `SUITE-pass1-be98994.txt` (commit a7af265), `SUITE-pass2-f0284db.txt` (97697c8), `SUITE-pass3-e26d4fe.txt`,
`SUITE-pass4-stopped-ca73663.txt` (stopped at E7 for the L11-1 fix review's changes); SUITE.txt is pass 5 (aaf7adc), the first after
the L11-1 fix.

## Rows

| Row | Driver | What it proves | Result: pass 5 on apk 114511e7 (code 7cb3742; SUITE.txt: SUITE PASSED) |
|---|---|---|---|
| E1 | e1.sh | the burst at the 783-ms hold; labels One–Four; edit mode; both stay after UP | PASS 18/18 |
| E2 | e2.sh | 740 ms taps / 830 ms bursts; a 4-px wobble keeps it; a 40-px glide drags and closes it | PASS 17/17 |
| E3 | e3.sh | the four in corner order; tileclient-b's one; a failed icon (no glyph, still runs); Weather / folder / Unassigned / secondary give their reasons | PASS 30/30 |
| E4 | e4.sh | each satellite launches its shortcut from the tile launch path (≥ 250 ms after the close); tile promoted on return | PASS 42/42 |
| E5 | e5.sh | every tap and event while open (elsewhere, another tile, the held tile, label, Back, resize, unpin, Home, sleep, scroll, name strip, press styles, no hold in edit mode) | PASS 70/70 |
| E6 | e6.sh | five warm opens with nothing recording, each asserted on the shell's clock (peak, overshoot, maxGapMs, settle ≤ 249.1 + maxGap), the burst's t0 within one frame of the entry's first frame; separate half-size recordings retaken until their spacing passes (their shell-clock numbers noted, not asserted: E6-pass4-83ms/) | PASS 36/36 |
| E7 | e7.sh | geometry: corner, line above (row), line below (top-left), 2-column WIDE in row 1, band (clamped corner), scrolled | PASS 58/58 |
| E8 | e8.sh | not a shortcut host; disabled; disable while open; uninstall while open; unstartable shortcut | PASS 29/29 |
| E9 | e9.sh | managed profile bursts and launches as its user; quiet / locked private space → profile quiet | PASS 20/20 |
| E10 | e10.sh | on phase 01 E8's player (Fossify): a hold on a transport control fires it (no burst, no edit mode); the art hold bursts | PASS 13/13 |
| E11 | e11.sh | the app list's and Music's hold menus unchanged, no burst | PASS 7/7 |
| E12 | e12.sh | gesture navigation: the row's line clears the gesture area; a satellite runs | PASS 8/8 |
| E13 | e13.sh | phase 02 regress / E1 / E7 bracket+geometry / E8 on this build; hold+drag; 830 ms on Contacts; press styles; exports | PASS 39/39 (phase 02 E7's motion sub-rows excluded, T11-6) |
| E14 | e14.sh | every `[quick]` line, reason and close in this build's saved rings; `query failed` by its JVM test | PASS 28/28 |
| E15 | e15.sh | Music's pivots and Start settings' pages as satellites, landing on the right pivot / page; Weather and Tess none | PASS 19/19 |
| E16 | e16.sh | the promoted tile bursts in its grid cell (E1's held bounds, E7's offsets); a cell off the page → off screen | PASS 18/18 |
| L11-1 | l11_1.sh | the fix's own row: the API queue over a notification, and clearing it shows the notification; Fossify (phase 01 E8's player) and the shell's player keep the strip across notification updates, every publish after the face `-> shows music (playing)`, a pause drops it; an uninstalled player keeps nothing and a reinstall from its own APK inherits no face (F-2); phase 01 E15 / E17 asserted, phase 02 E5 (secondary tiles, F-1) exits 0 | PASS 50/50 |

Unit: 651/651 in the suite (`TileSourcePrecedenceTest` 12, the L11-1 fix's); `QuickRulesTest` (23) — the selection rule, the decision and its reasons (`query failed` included), the
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
  5's same file shows the notification publishes resolving to the music face). The engine now keeps
  each producer's content apart and shows one by a fixed order — a playing session's face > the API queue > notifications
  > a paused track's face: `ca73663`, and the fix review's findings `7cb3742` (review/2026-09-24-L11-1-fix-review.md: a
  secondary tile's key is never cleared by the arbiter; an uninstalled package keeps nothing from any source). Tests:
  `TileSourcePrecedenceTest` (12) and the L11-1 row (50/50 in pass 5). The row's first cut of its uninstall sub-step passed
  vacuously (a sideloaded app cannot come back through `install-existing`; the tile read "Tap to choose") and left the
  fixture uninstalled for the next run; kept in L11-1-smoke2-p02e5-restore/ and L11-1-smoke3-fossify-missing/, fixed in
  `aefe4b9` (the reinstall is from the APK copied off the device, and the row asserts it happened).
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
