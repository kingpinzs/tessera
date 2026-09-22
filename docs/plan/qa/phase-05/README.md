# Phase 05 QA gate — plan and evidence index

Rows come from `docs/plan/phase-05-keyboard.md` (FINAL). Every row starts from the baseline state and
restores what it changes (PLAN RV12); motion follows RV11; dumps follow RV13. The emulator is
`tileshell_fhd` (AOSP API 36, 1080 × 2340 @ 450 dpi, no Google), so 1 phys = 0.75 px and 1 epx = 3 px.

## The floor every driver stands on

`scripts/lib.sh` is phase 03's audited driver floor (symlinked, so each log's header still records a
real blob): every row stamps its log with the driver's and the harness's git blobs and whether the
INSTALLED APK is the one just built, every assertion is a real comparison that reaches the log and the
exit code, a row that asserts nothing fails, and the summary line is computed, never written.

`scripts/kb.sh` adds the keyboard's helpers:

* **the IME window's nodes come only from the instrumentation's dump.** Plain `uiautomator dump`
  carries none of them (TOOLING.md §2); `kb_dump` uses the gesture driver's
  `UiDevice.dumpWindowHierarchy`, which walks every window. Every key has an invisible node with its
  tag (`kb_key_<id>`), so a dump gives each key's real bounds;
* the fixture app (`testapps/ime-fixture`) mirrors its focused field's raw text, selection, last editor
  action and input type into TextViews (`read_mirror`), unescaped, because the dump writes emoji as XML
  character references;
* the gesture driver's `script` op injects timed MotionEvents with real event times and reports when
  each one actually started, so every timed sub-row (E6, E12) carries the spacing that happened;
* `kb_begin` / `kb_end` select the shell's keyboard for the row and put back the previously selected one
  and the AVD's `show_ime_with_hard_keyboard` switch;
* `ime_dump` / `ime_log` read the `:ime` process's own diagnostics ring through
  `dumpsys activity service app.tileshell/.ime.KeyboardService` — the launcher's dump cannot see it.

`scripts/run_rows.sh` runs rows one after another in the background (one emulator, one driver at a time,
enforced by lib.sh's device lock) while the next drivers are written.

## Method notes that decide what a result means

* **Pixels versus bounds.** A value read from the keyboard's own dump is held to R6's tolerance as
  written. A value read off screen pixels (ink heights, the grip, the dots, strip text) gets R6's
  tolerance plus one device pixel — the capture's quantum, the allowance RV11 gives a frame — and each
  such line says so.
* **Accessibility bounds are clipped to the window.** A row-1 popup rises above the panel, but its node
  is reported clipped at the IME window's top; its real top is read from its accent pixels (E3).
* **The emulator's screenrecord is variable-rate.** It emits a frame only when the screen changes, so
  frames-per-second over a second means nothing. RV11's 55-fps floor is applied to the spacing of source
  frames during the motion (≤ 18.2 ms); a capture that fails it is rejected and retaken. The fixture's
  opt-in ticker (`--ez ticker true`) makes every vsync a frame for the popup timing (E3M).
* **Force-stop is not a crash.** `am force-stop app.tileshell` makes Android select another keyboard
  (E1 runs 1–2, EDGE2). Drivers reopen shell pages with NEW_TASK | CLEAR_TASK, never `am start -S` on the
  shell's own package.

## Defects the gate found, fixed at the producer

| Found by | Defect | Fix |
|---|---|---|
| E1 | (driver) `am start -S` on the Settings page force-stopped the package, and Android deselected the keyboard | reopen with NEW_TASK \| CLEAR_TASK |
| E3 run 1 | "&123": Selawik at R6's 40-phys digit height sets it 131 wide against R6's 112 (both HIGH) | the label is condensed to the measured width |
| E3 run 1–2 | the strip placed the microphone and the words by layout box, and each item carried the 26-epx gap twice | both placed by ink (side bearings measured with the same Paint) |
| probe | popups and the trail drew UNDER the strip's text | an overlay canvas above the strip |
| E3M run 1 | (driver) Android's IME animation fades the window, which a "dark pixel" edge tracker loses | a contrast threshold, and the opacity reported per frame |
| E9 run 1 | the space-bar drag never moved the panel: each move rebuilt the metrics and re-read the stored raise (0) | a move changes only the raise and requests a layout pass |
| E9 run 2 | the lift point was ignored (a 300-px drag landed 292 px up) | the UP position is applied as a final move in every gesture mode |
| E1 | the checklist's "Keyboard selected" row read Settings.Secure | InputMethodManager.getCurrentInputMethodInfo |
| EDGE2 run 1 | in landscape the keys were laid out over the full display width while the IME window stops at the side nav bar and cutout | keys span the display width less the side insets |
| EDGE1 run 2 | a 64-letter slice of a long run was taken as a word, autocorrected and counted toward learning | a run reaching the read window's start is not a word |
| review B1 / MAJOR-2 | every character typed in a password field (a keyguard password included) was logged in clear in the :ime ring | password fields log counts only; alternates and touch lines withheld |
| review M3 | an emoji typed into a password became a Recent cell | Recent is not recorded in password fields |
| review MAJOR-1 | a strip tap during a held key committed the nearest row-1 letter; a key tapped during a strip gesture was dropped | per-pointer key-block ownership; Layout.hit stops at the key block |
| review M5 | the one-microphone refusal had no device row that could fail | MicArbiter (JVM tests) and EDGE3 (a) / (b) through the fixture's recogniser |
| review minors | trail ticker vs a new swipe; drifted &123 tap; held space; double bind after :speech death; chevrons; stopSpeaking owner; stale strip after emoji; phone field → QWERTY after emoji; "+ word" after an original pick; restartInput re-report | each fixed at the producer (commits ecbf8ea, 06f6250) |
| review B2 / M1 / M2 / m1 / m14 (drivers) | popup timing read a pixel the key fill changes; a fade could pass the first-frame check; the dot's diameters, bold, colours, separator and corners unmeasured | the gap pixel, R6's ≥ 91 % rule, and the new measurements in E3 / E5 |

## Findings for Jeremy (not defects of this phase)

* **Android's spell checker** is separate from the keyboard: a tap on a red-underlined word opens the app's
  own suggestion popup ("Add to dictionary / Delete"), which lies over the left of the strip and takes the
  next tap there (EDGE1 run 2; EDGE1 now moves the caret with the arrow keys).
* **A force-stop deselects the keyboard** (a crash does not); the checklist's "Keyboard selected" row is the
  way back (EDGE2).

## Rows

| Row | Driver | Result | Log |
|---|---|---|---|
| E1 listed, enabled, selected, checklist | `e1.sh` | see log | `E1/E1.txt` |
| E2 typing in every input type + Fossify Notes | `e2.sh` | see log | `E2/E2.txt` |
| E3 geometry, labels, colours, popup, dot, strip | `e3.sh` | see log | `E3/E3.txt` |
| E3 motion: popup timing, the system slide | `e3motion.sh` | see log | `E3M/E3M.txt` |
| E4 Word Flow | `e4.sh` | see log | `E4/E4.txt` |
| E5 autocorrect, per-keystroke strip, no password learning | `e5.sh` | see log | `E5/E5.txt` |
| E6 cursor-control dot | `e6.sh` | see log | `E6/E6.txt` |
| E7 emoji code points | `e7.sh` | see log | `E7/E7.txt` |
| E8 voice typing, network off | `e8.sh` | see log | `E8/E8.txt` |
| E9 space-bar move, band tap-through | `e9.sh` | see log | `E9/E9.txt` |
| E10 handedness | `e10.sh` | see log | `E10/E10.txt` |
| E11 switch back after an emoji | `e11.sh` | see log | `E11/E11.txt` |
| E12 alternates, caps lock, double space, &123, docking, learning | `e12.sh` | see log | `E12/E12.txt` |
| Edge cases: fields and keys | `edge1.sh` | see log | `EDGE1/EDGE1.txt` |
| Edge cases: crash, force-stop, keyguard, landscape, hardware keyboard | `edge2.sh` | see log | `EDGE2/EDGE2.txt` |
| Edge cases: voice typing | `edge3.sh` | see log | `EDGE3/EDGE3.txt` |

Earlier runs are kept beside each log (`<row>-run<n>.txt`, `<row>-prev-<time>.txt`).
