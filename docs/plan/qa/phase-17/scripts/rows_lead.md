# Phase 17 — the lead's rows E2, E10, E16, E17, E24, E25

Written on `phase-17-qa` against build fbf69b7a (the branch at e63c6bf8: the three apps are empty shells). Every driver
sources `lib.sh` then `p17.sh`, takes the device lock, installs the worktree's debug APK when the device holds another
build, and restores what it changes. Run one as `env -u TMPDIR bash docs/plan/qa/phase-17/scripts/<driver>`; a busy lock
is exit 3 (run it again). A run's folder is `docs/plan/qa/phase-17/<ROW>/`; rename it before the next run (`row_begin`
truncates the log).

| Row | Driver | What it changes on the device | A run takes | Recorded instead of asserted, and why |
|---|---|---|---|---|
| E2 | `e2.sh` (child: phase 02 `regress.sh`, seeded with `baseline_layout-p02.json`) | The Start layout (the child's baselines, then this phase's; restored). The child launches other apps and force-stops the shell through `layout_restore`. No wipe, no uninstall. | about 4 min without the child (measured 248 s); the child adds about 2 min (not measured here) | The list as the pre-17 build drew it — re-reading it needs that build installed, which E1 owns; "unchanged apart from the three rows" is asserted as: rows = the device's launcher activities, the shell's own rows = the eight before + the three, grouping, headers, the shell's `[applist] model:` line, the grid's cells and jumps. What a tap on a dimmed jump cell does (it dismisses the grid) — the doc does not say. The count of "New" captions on other apps' rows. |
| E10 | `e10.sh` | The Start layout (baseline), the keyguard (a PIN for one leg; cleared, also from an EXIT trap). Force-stops the shell (`c6`). | about 1.5 min (77 s) | What Tess says over the keyguard ("Unlock your phone to continue."). Not asserted: phase 03 E2's audible-reply half (r3 V4: no row uses the host's audio). C-25's `wake_device` is not called inside the keyguard leg — its `wm dismiss-keyguard` raises the PIN bouncer; the same `mWakefulness` line is read and asserted without it. |
| E16 | `e16.sh` (child: phase 15 `e0.sh` with `E0_BASELINE`) | The Start layout, Music and Auxio playback, the Music fixtures and two stream volumes, `RECORD_AUDIO` (revoked for the child's span, granted back), one reminder (made, fired, deleted from Tess's History). Force-stops the shell and Auxio. No wipe. | about 6.5 min: the child 2 min (122 s), then the row 4 min (237 s). The device is free for a moment between the two. | The take `e0.sh`'s step 4 asks for: not made — the microphone rule; with `RECORD_AUDIO` revoked the record button starts nothing, and the child's notification checks ran on its timer. E13's "white glyphs survive a bright cover": the fixture tracks carry no cover; the screenshot is kept. Reminders of this row left by an earlier aborted run (removed before the legs; 0 on a clean device). |
| E17 | `e17.sh` | Nothing (it reads build outputs; the device only says which APK it holds). It runs `./gradlew :app:assembleRelease --offline` before taking the lock. | about 1.5 min, of which the device is held about 15 s | The whole-phase delta against `upgrade/phase-16-585b457f.apk` (the row says "recorded"): file size and the entries' own bytes. The doc's literal `--file res/xml/network_security_config.xml` for each APK — it does not exist under that path in a release APK (see below). The count of `tmdb.*` values `local.properties` holds. |
| E24 | `e24.sh` | The Start layout (the baseline, the pre-17 file, the baseline again). Force-stops the shell (`layout_restore`). | under 1 min (46 s) | `layout_restore`'s exit code on the pre-17 file (1: its own check refuses a file the shell re-seeds on load) — the doc grades the two `-> assigned` lines, not the helper. The baseline's `addedOnce` list. |
| E25 | `e25.sh` (child: phase 12 `e1.sh`) | **Wipes the shell's data** (`pm clear`) three times and three more in the child; revokes and grants `CAMERA` and `READ_MEDIA_VIDEO`; ends on `pm clear` → `provision.sh` → the baseline. | not run; by phase 16's E26, about 8 min plus the child's 4 | A `[wizard] step setup:videos: granted` line in (a): phase 12's reconcile logs the step that was showing (Camera); Videos is never the current step when both are granted at once, so its leaving the walk is read from the dump. |

## Run or not run (2026-10-05, the device shared with three builders)

| Row | Status | Last run | Folder |
|---|---|---|---|
| E24 | run | 37 passed, 0 failed, 2 recorded | `E24-build-fbf69b7a-run1-pass-37-0-2` |
| E10 | run | 32 passed, 0 failed, 1 recorded | `E10-build-fbf69b7a-run2-pass-32-0-1` |
| E16 | run, with its child | 98 passed, 0 failed, 3 recorded; the child 53 passed, 0 failed | `E16-build-fbf69b7a-run2-pass-98-0-3` |
| E17 | run | 63 passed, **2 failed**, 12 recorded — both failures are the OpenCV clause: this branch's APK holds no `lib/arm64-v8a/libopencv*.so` (the library is being built on another branch) | `E17-build-fbf69b7a-run3-FAIL-no-libopencv-in-this-branchs-apk-63-2-12` |
| E2 | its own legs run with `E2_CHILD=0`; **the child NOT run** | 72 passed, 0 failed, 4 recorded (a development run: the log says the child was skipped) | `E2-build-fbf69b7a-dev2-no-child-pass-72-0-4` |
| E25 | **NOT run** (it wipes the shell): written, `bash -n`, walked through against `onboarding/SetupWizard.kt`, `WizardPages.kt`, `Checklist.kt`, phase 12's `p12.sh` and phase 16's `e26.sh` | — | — |

E2's child was checked only as far as its seed: `layout_restore` of `baseline_layout-p02.json` returns 0 on this build
with five `-> already run` lines and no `-> assigned` (a one-off, not a row).

## For the gate run

- E2 and E25 are run whole (no `E2_CHILD=0`, no `E25_CHILD=0`) when no builder is using the device.
- E17 passes only on an APK that carries `libopencv_pano.so` for arm64 and none for x86_64.
- E16 and E2 read tiles and rows by tag only; they do not depend on the three apps' pages.

## What the drivers found in the doc

- **E17, the network clause's command.** `aapt2 dump xmltree --file res/xml/network_security_config.xml <release apk>`
  fails with "failed to find file": the release build shortens resource paths (the file is `res/8G.xml` in this
  build). `e17.sh` finds the file through the manifest's `android:networkSecurityConfig` resource id and `aapt2 dump
  resources`, asserts that resource is `xml/network_security_config`, and dumps that path; the content the clause asks
  for is asserted on it. The debug APK keeps the literal path.
- **E16 and the microphone.** The row re-runs phase 15's `e0.sh`, whose step 4 starts a Voice Recorder take; the
  preamble forbids the microphone in every row. The driver revokes `RECORD_AUDIO` for the child's span, so no take is
  made, and proves it from `dumpsys audio`'s recording-activity log.
- **E10 and C-25.** `wake_device` after `KEYCODE_SLEEP` cannot be used while the PIN keyguard must stay up (above).
- **E25 (a).** "`wizard_step:setup:camera` and `wizard_step:setup:videos` present … Step 1 of 3": the wizard shows one
  step at a time, so the Videos step is reached with "Not now" (Step 2 of 3) and Back returns to Camera before the two
  grants. Not run, so this reading is unproven on the device.

## Added beside the drivers

- `docs/plan/qa/phase-17/baseline_layout-p02.json`: phase 16's `baseline_layout-p02.json` plus `slot:photos:v1`,
  `slot:camera:v1` and the two slots — the seed E2's child needs (phase 02's own baseline is re-seeded on load, which
  `layout_restore` refuses).
- `docs/plan/qa/phase-17/scripts/within.py`: the symlink to phase 03's, which `lib.sh`'s `assert_within` looks for
  beside itself (phases 12, 15 and 16 have the same link).
