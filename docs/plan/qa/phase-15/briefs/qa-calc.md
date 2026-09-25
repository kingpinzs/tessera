# Brief — phase 15 QA: the Calculator rows E12, E13, E29 and the Calculator edge cases (development checks)

You write and RUN the device drivers for these rows on your own emulator and report exact results. This is the
build-time development pass (T15-21); the gate is a later full re-run on main, so every driver you write is the
permanent, re-runnable row driver, and this run's evidence is real evidence.

## Where and how
- Repo: the lead's worktree `/home/jeremyking/projects/metro-launcher-p15` (branch `phase-15`). Write ONLY:
  `docs/plan/qa/phase-15/scripts/<row>.sh` (+ small new helper files there, e.g. a Python driver) and each row's
  evidence under `docs/plan/qa/phase-15/<ROW>/`. Do NOT edit app code, `lib.sh`, `p15.sh`, `calc_drive.py`, `e11.sh`,
  INDEX.md, STATE.md or any phase doc; do NOT commit (the lead commits). Never touch
  `/home/jeremyking/projects/metro-launcher`.
- YOUR emulator ONLY: `export ANDROID_SERIAL=emulator-5560` for every adb call and driver run; never address
  emulator-5554, -5556 or -5558. Private driver lock: `export TMPDIR=/tmp/claude-1000/-home-jeremyking/5d5ffc39-5a2a-4c33-953f-07d71da27a45/scratchpad/p15/tmp5560`
  (mkdir it). PATH gets `$HOME/Android/Sdk/platform-tools`.
- The APK is built: `app/build/outputs/apk/debug/app-debug.apk` in the worktree. Do NOT run gradle.
- First-time setup of emulator-5560 (fresh AVD): wait for `sys.boot_completed=1`; run
  `docs/plan/qa/phase-03/scripts/provision.sh`; grant the notification listener (`adb shell cmd notification
  allow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener`); install phase 05's gesture driver
  (`adb install -r -t testapps/ime-fixture/build/outputs/apk/debug/ime-fixture-debug.apk` and
  `…/androidTest/debug/ime-fixture-debug-androidTest.apk`). Record it in your report.
- Drivers source `docs/plan/qa/phase-15/scripts/lib.sh` (the shared floor: row_begin / row_end, assert_*, record,
  diag, dump_ui, bounds, node_text, has_node, tap_node, scroll_to_node, ring_mark / ring_since / ring_save, wake_device)
  then `docs/plan/qa/phase-15/scripts/p15.sh`. Read both fully, and read `calc_drive.py` + `e11.sh` (the E11 driver the
  lead wrote: it shows how the Calculator's keys are pressed and read, and that Compose reports `selected` semantics as
  `checked="true"` on non-Tab nodes — accept either attribute wherever a row says `selected="true"`).

## The spec
`docs/plan/phase-15-inbox-clock-calculator-recorder.md`: the Acceptance preamble IN FULL; "Harness contracts"; rows
**E12, E13, E29**; the Calculator EDGE CASES line ("every error string …; 200 !; a 1,000-digit result; a display wider
than the screen; repeated = and %; Programmer negatives in BIN; shifts past the word size; switching word size with a
value that no longer fits; paste of a non-numeric string; memory across kill -9; the keypad under `wm size 1080x1920`").
Measurements: `docs/plan/r11/calculator.md`. The oracle table `docs/plan/qa/phase-15/calc-cases.tsv` (schema
`calc-keys.md`): E12 runs its 40 `converter` lines THROUGH THE APP'S UI, E29 its 22 `date` lines through the date page's
pickers — the expected values are never computed by you or read from the app's engine.

Build facts (from the builder's report; the source under `app/src/main/kotlin/app/tileshell/calculator/` is the truth):
- `app.tileshell/.calculator.CalculatorActivity` is singleTask; extra `page` = standard | scientific | programmer |
  converter (and the pane chooses date). Tags: calc_root, calc_header, calc_menu (≡), calc_title, calc_history_toggle,
  calc_pane, calc_pane_list, calc_pane_settings, about_page; calc_mode:<standard|scientific|programmer|date|converter> on
  the page root, SUPPRESSED while the pane is open, when the pane rows carry calc_mode:<…> and the CONVERTER header
  carries calc_mode:converter; calc_converter_category:<1..12> (text = the label); calc_display, calc_expr,
  calc_key:<name>, calc_paste; calc_history_pane, calc_history:<n> (expression, 1 = newest), calc_history_result:<n>,
  calc_history_empty, calc_history_clear; calc_memory_pane, calc_memory:<n>.
- Converter: calc_converter_value1 / value2 (value2 is the "to" text when unit1 is the source), calc_converter_unit1 /
  unit2 (text = en-US unit name; tap → calc_unit_picker with calc_unit:<Windows unit id>, text = the name),
  calc_converter_about, calc_converter_about:<n>, calc_key:<0-9|decimal|backspace|clear|negate>.
- Date: calc_date_page, calc_date_op:<difference|add|subtract>, calc_date_from, calc_date_to, calc_date_amount,
  calc_date_result (ONE node; two lines joined with "\n" where the tsv has " | "), pickers calc_date_picker,
  calc_date_pick:<month|day|year|years|months|days>:<value> (7 visible rows per column, tap picks, drag scrolls),
  calc_date_pick_ok / _cancel.
- Diagnostics (launcher ring, `diag`): `[calc] engine port microsoft/calculator@4fd3fc5`, `[calc] error <kind>`,
  `[calc] date <op> <from> <to | years=n months=n days=n> -> <result lines joined ' | '>`, `[motion] calc_pane …`,
  `[motion] flyout …`.
- Known, reported by the builder: a paste of a non-numeric string shows "Invalid input" (Windows' DisplayPasteError;
  the edge-case line says "ignored" — drive and REPORT which it is, do not bend the assertion); the memory flyout has
  no per-item Clear / M+ / M−.

## Rules that are not negotiable
1. Every assertion must be able to FAIL: read the node's OWN text via its tag, the dumpsys line, or the ring slice from
   a MARK taken just before the action. A row with zero assertions fails.
2. Every row starts from the baseline and restores what it changed (RV12) — incl. `wm size reset`, memory / history
   cleared, the converter's last category back to Volume if the row changed it is NOT required (it is the app's state),
   but say what you left.
3. Run every driver for real on emulator-5560 and keep its log and dumps / screencaps under `docs/plan/qa/phase-15/<ROW>/`.
   A failed run is kept (`<ROW>-run1`), fixed only where the DRIVER was wrong, and re-run. If the PRODUCT is wrong, stop
   that row, write `docs/plan/qa/phase-15/<ROW>/DEFECT.md` (commands + output), and report it — no workaround.
4. E13's geometry: compute every value in epx from the dump bounds / screencap pixels (px per epx = 1080 / 360 = 3) and
   compare with r11's value and tolerance; a value r11 marks LOW is `record`ed, not asserted, as the row says.
5. Something the AVD cannot do is NOT RUN with the exact reason — never faked.

## Report back (final message, under 90 lines)
Per row: the driver path, the final run's summary line quoted, each FAIL / NOT RUN with its reason. Every product
DEFECT (row, repro path, one line), every doc-vs-build difference, and the emulator-5560 setup you did.
