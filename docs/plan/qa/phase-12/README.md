# Phase 12 QA — the setup wizard and the theme presets

AVD **tileshell_fhd** (emulator-5554; AOSP API 36, 1080 × 2340 @ 450 dpi, no Google). Branch `phase-12`. Every row is a
lib.sh row (phase 03's floor, symlinked): its log stamps the driver blob and the installed APK, every assertion is a
PASS / FAIL line with an exit code, and each row's exit code is kept beside its log (`<row>/<row>.rc`). Drivers:
`scripts/`. Run one row: `bash scripts/<row>.sh`; a chain: `bash scripts/chain.sh E1 E2 …`; an edge case:
`bash scripts/edge.sh <case>`. `ANDROID_SERIAL` defaults to emulator-5554 (a second, unrelated AVD is attached).

**Scope of the runs (Jeremy's QA ruling, INDEX Change Log 2026-09-25):** a build session never runs a whole gate as one
pass. Phase 12 is newly built, so each of its rows covers what was built: each row and edge case ran once, a row that
failed was re-run alone after its fix, and there is no final end-to-end pass on one APK (phase 13's precedent, the day
after the ruling). A fix made during review re-runs only the rows that drive the changed code. The whole gate is
Jeremy's end-of-project run.

## Rows

Builds: **c464ffcd** = 907a8725 (the phase as first committed); **48000638** = e5b9cc7d (the page motion ends on the
frame nearest 217 ms — E10 run 1); **a4d7430b** = 0c8fe8f4 (review B1: a set stored before phase 12 reads Custom; the
review's hardening). Rows on the earlier builds stand: e5b9cc7d changes only the last frame of the wizard page motion
(measured by E10 alone), and 0c8fe8f4 changes how a NON-fresh install's stored set reads (every earlier row starts from
`pm clear`, which reads Default either way — the JVM test pins it) plus fail-safe paths no row reaches (a key missing from
both lists, a SecurityException from a Settings page).

| Row | Result | APK | Notes |
|---|---|---|---|
| E1 | 20 / 0 | c464ffcd | run 1 kept (provision path, harness) |
| E2 | 58 / 0 | c464ffcd | run 1 kept (E2-state appops order; quote parsing) |
| E3 | 105 / 0 (+18 recorded) | c464ffcd | every step through Android's own page or dialog; runs 1–4 kept (walker faults; the role-granted SMS steps) |
| E4 | 30 / 0 | c464ffcd | force-stop and reboot |
| E5 | 32 / 0 | c464ffcd | |
| E6 | 32 / 0 | c464ffcd | |
| E7 | 19 / 0 | c464ffcd | |
| E8 | 30 / 0 | c464ffcd | |
| E9 | 18 / 1 | a4d7430b | the one FAIL is phase 02 E1's edit-mode check, **pre-existing** — identical on the pre-phase-12 APK (BUILD_START/phase02-e1-base/); INDEX ledger L12-1. Runs 1–2 kept |
| E10 | 20 / 2 (+8 recorded) | a4d7430b | every doc gate on the shell's clock passes (settle 217 on each transition and Done → Start, maxGapMs 17); the 2 FAILs are the compositor corroboration — see the reading below. Runs 1–4 kept |
| E11 | 271 / 0 | a4d7430b | incl. the grant sub-row (both halves, urigrants.xml) and the pre-phase-12 sub-row (review B1). Runs 1–4 kept |
| E12 | 55 / 0 (+3 recorded) | a4d7430b | NOPICS build = 0c8fe8f4 with the six WebP removed (a worktree); run 1 kept |
| E13 | 64 / 0 (+1 recorded) | a4d7430b | run 1 kept (disturbed by the lead's `adb root`) |
| E14 (setup:usage) | 19 / 0 | c464ffcd | run 1 kept |
| E14 setup:full_screen_alarms | 19 / 0 | 48000638 | phase 15's step, run here (C-34) |
| E14 setup:overlay | 19 / 0 | 48000638 | phase 15's step, run here (C-34) |
| EDGE_INSTALL_R | 10 / 0 | a4d7430b | install -r mid-run: the run resumes, no marker |
| EDGE_LMK | 9 / 0 (+1) | a4d7430b | am kill with the dialog up did not kill the visible process (recorded); the answer is read from live state |
| EDGE_ROTATION | 9 / 0 | a4d7430b | portrait-locked, not recreated |
| EDGE_DISMISS | 15 / 0 (+5) | a4d7430b | Back and a tap outside (which does not dismiss the dialog on this image, recorded); the step stays, Not now advances. The button then reads "Open app info" (reading below). Run 1 kept (driver) |
| EDGE_DOUBLE_TAP | 7 / 0 | a4d7430b | one permission dialog, pid unchanged. Run 1 kept (driver edited mid-run; wrong count) |
| EDGE_PARTIAL_PHOTOS | 9 / 0 | a4d7430b | Select photos: PARTIAL advances, checklist:photos:partial |
| EDGE_PARTIAL_BGLOC | 9 / 0 | a4d7430b | While using the app: the step stays with its partial line |
| EDGE_PARTIAL_CALENDAR | 9 / 0 (+1) | a4d7430b | read only (WRITE user-fixed): Tess's page opens app info (phase 03), Back, the step stays with its partial line. Run 1 kept (driver) |
| EDGE_LOCATION_OFF | 9 / 0 | a4d7430b | PARTIAL never summons; inside a run the step stays after "Allow all the time" |
| EDGE_KB_DISMISS | 5 / 0 | a4d7430b | the keyboard picker dismissed: the step stays |
| EDGE_HOME_ONCE | 5 / 0 (+4) | a4d7430b | Default Home is step 1; the AVD's sheet is RequestRoleActivity; Cancel, and choosing Quickstep, both leave the step. Run 1 kept (driver) |
| EDGE_LIGHT | 3 / 0 | a4d7430b | the wizard follows the Light theme |
| EDGE_CUSTOM_BEFORE | 4 / 0 | a4d7430b | items changed in Settings: Custom selected, Done leaves them |
| EDGE_BATTERY_SAVER | 8 / 0 | a4d7430b | acrylic off by battery saver while transparency_effects is written true; saver off: on. Run 1 kept (driver ordering) |
| EDGE_NO_PICTURE | 7 / 0 | a4d7430b + NOPICS fault build | HAL applies with no picture and says so; the normal APK restored |
| EDGE_PROFILE | 3 / 0 (+1) | a4d7430b | a work profile (user 10) present: the step list equals E2's |
| EDGE_KEYGUARD | 5 / 0 | a4d7430b | never over the keyguard; shows after unlock |
| JVM | 49 / 49 | 0c8fe8f4 | SetupWizardTest 33, ThemePresetsTest 16 |

Runs that failed on a driver or harness fault are kept beside the passing run, renamed `<row>-run<n>-<what went wrong>`.

## Readings of the doc recorded here (no doc change)

- **`selected="true"`** in the rows reads as Compose's `checkable="true" checked="true"`: Compose reports a selected
  node as checked unless its role is a Tab (the phase 01 / phase 05 precedent, `qa/phase-01/scripts/e13_state.py`,
  `qa/phase-05/scripts/e10.sh`). `p12.sh node_checked` reads it.
- **E13's "exactly six `preset:*` entries"**: `preset:Custom` (T12-12's Custom entry) carries the same prefix; the six are
  counted without it and the Custom node is recorded apart. The variant chips are `preset_variant:*`, a different prefix.
- **E11 / E13's lens regions "(rim, iris, glow)"**: the idle persona at reveal 0 draws a glow → rim ring with no iris
  tone (`scripts/selftest/HELPERS.md`); the checker measures three radial bands against the colour predicted at each
  radius. Surfaced to Jeremy.
- **E5's Tess page** is reached through her session, which opens only for the ASSISTANT role holder (without it the
  Search key shows her role notice, `CortanaService.open`). The E2 state removes the role, so E5 adds it for the one read
  of `cortana_check:microphone:missing` (the microphone stays revoked) and removes it again.
- **E8's keys** run on a no-marker provision with notification access and Usage access revoked (a two-step run whose
  ASSISTANT role is held, so Search really opens Tess); its pin-band sub-row runs from the E2 state as written.
- **E9's phase 01 E2, phase 01 E14 and phase 10 E2** have no standalone driver (they ran inside combined scripts), so
  E9 replays their row text; phase 02 E1, phase 03 E1 / E5 and phase 05 E1 run unchanged (their own row directories are
  moved aside and put back, so the earlier phases' evidence is untouched).

- **E10's corroboration.** The doc's motion gates are on the shell's own clock (`withFrameNanos`, C-5 / C-31) and pass.
  Its "60-fps screenrecord corroborates under phase 05's frame-spacing rule": every capture (runs 1–4 and
  BUILD_START/e10-capture-probe/, at 540 × 1170 and 360 × 780) had source frames 30–50 ms apart at the motion's start,
  which phase 05's rule rejects. SurfaceFlinger's present times for Start's layer show the gap is real, not the encoder:
  each page change's first frame is presented at t0 + ~2 ms, the next at t0 + ~50 ms, and after that every 16–24 ms to
  the settle (all 14 of the motion's frames presented). So on this AVD each page change starts ~50 ms late and then runs
  within the doc's two-vsync bound (33.4 ms) but not phase 05's 18.2 ms. The shell's clock cannot see it (it times frame
  production, not presentation). Recorded as it is; surfaced to Jeremy (the phone's feel is H1).

- **A dismissed permission dialog reads as "Android will no longer ask".** Dismissing the dialog without an answer
  leaves shouldShowRequestPermissionRationale false, the same signal as a permanent "Don't allow", so the wizard relabels
  the button "Open app info" (the app-info page still grants it). It is the rule the doc names from CortanaPermissionActivity
  (phase 03), which opens app info itself in the same case (adversarial note N10). Recorded in EDGE_DISMISS; surfaced to
  Jeremy.

## Harness findings (build start)

- **`pm clear`** on this image (BUILD_START/pm_clear.txt): revokes the runtime permissions, drops notification access
  and deselects the keyboard; keeps both roles and every appop. The E2 state therefore revokes each grant explicitly.
- **Android relaunches the HOME holder the instant its process dies while it is on top.** A `pm clear` or a force-stop
  with Start in front gave a fresh Start that evaluated the wizard before the row's grants, revocations or the marker
  landed (E1 run 1). `provision.sh` and every p12 helper now put Android's Settings in front before stopping the shell;
  Home then creates Start after the state is set. `provision.sh` stops the shell this way with
  `PROVISION_FINISH_WIZARD=0` too, so both routes end on a fresh Start.
- **The ASSISTANT role grants `SYSTEM_ALERT_WINDOW`**: removing the role resets that appop to `default` (which reads
  allowed). The E2 state removes the role before it sets the appops (E2 run 1).
- **`USE_FULL_SCREEN_INTENT` has a uid mode that outranks the package mode** on this image: the E2 state sets both to
  `ignore`, and `provision.sh` sets both to `allow` (the uid mode's value before any of this, BUILD_START/pm_clear.txt).
- **uiautomator quotes an attribute that holds `"` with `'`**: a `text="…"` regex misses it (the background-location why
  line); `p12.sh ntext` parses the dump as XML.
- **`am start --es page CHECKLIST` only brings a running Settings task to the front** on whatever page it was left on
  (E14 run 1); `p12.sh open_checklist` starts it in a fresh task.
- **Two driver faults read present diagnostics headers as missing** (E11 runs 1–3, E12 run 1; every product line was
  in the dumps, kept as `*-dump-miss-*.txt`): (1) the listener's service dump indents `tileshell diagnostics: <n>
  entries` under its "Client:" block, and the check was anchored at column 0; (2) the check piped `printf` into `grep -q`
  under lib.sh's `pipefail`, and `grep -q` exiting on its match can kill `printf` with SIGPIPE (141), failing the
  pipeline at random. Both reads now use an unanchored pattern on a here-string (50 / 50 on the saved dump). The fault
  could only fail a row (a miss is a FAIL line), never pass one, so the rows that passed with the old check stand.
- **The ASSISTANT role grants `SYSTEM_ALERT_WINDOW`**: removing the role resets that appop to `default` (which reads
  allowed). The E2 state removes the role before it sets the appops (E2 run 1).
- **`USE_FULL_SCREEN_INTENT` has a uid mode that outranks the package mode** on this image: the E2 state sets both to
  `ignore`, and `provision.sh` sets both to `allow` (the uid mode's value before any of this, BUILD_START/pm_clear.txt).
- **uiautomator quotes an attribute that holds `"` with `'`**: a `text="…"` regex misses it (the background-location why
  line); `p12.sh ntext` parses the dump as XML.
- **`am start --es page CHECKLIST` only brings a running Settings task to the front** on whatever page it was left on
  (E14 run 1); `p12.sh open_checklist` starts it in a fresh task.
- **The diagnostics header can be indented**: the listener's service dump prints `tileshell diagnostics: <n> entries`
  under its "Client:" block, indented, and the Start activity's dump does in some task layouts. An anchored `^tileshell`
  check read those as "no header" (E11 run 1's Settings side, E12 run 1, E11 run 2) while every product line was in the
  dump (`E11-run2-indented-header-regex/listener-dump-miss-*.txt`). Both reads now allow leading space, and a dump
  without the header is saved whole.
- **The ASSISTANT role grants `SEND_SMS`, `READ_SMS` and `READ_CALL_LOG`** on this image (BUILD_START/
  assistant-role-grants.txt), so once `tess:assistant` is granted those three steps are GRANTED and the re-derived walk
  does not show them (E3 run 4; the wizard reads live state, T12-1). E3 asserts the walked steps plus the role-granted ones
  are the nineteen, and that no step was declined.
- **The photo picker's persisted grants are not in `dumpsys activity permissions`** on API 36; `grants.sh` reads the
  persisted table itself (`/data/system/urigrants.xml`, binary XML) as root through the AVD's `su` and `abx2xml`, after
  its deferred write (E11 run 1).
- **E3's walker (`screen_act.py`)** learned three things about AOSP 36's pages: a confirmation dialog on notification access
  (and an ElementTree node with no children is falsy, so `a or b` on nodes dropped it, run 1); the full-screen-intent and
  overlay pages are Compose ("SPA") pages whose toggle is a checkable `android.view.View` (run 2); and the digital
  assistant is chosen in the permission controller's default-app list, then an OK (run 3, probe in BUILD_START/
  assistant-probe/).
- **`adb root` restarts adbd**: running it for a probe while E13 run 1 was driving the device broke that run's fixture
  (kept as `E13-run1-disturbed-by-adb-root/`). Root reads now go through `su`, which does not restart adbd.
