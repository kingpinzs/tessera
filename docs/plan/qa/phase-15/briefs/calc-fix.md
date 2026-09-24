# Brief — phase 15: fix the Calculator's E13 ink geometry, its row text semantics, and check the date picker's drag

You are a builder. Fix three things the Calculator QA pass found (docs/plan/qa/phase-15/E13/DEFECT.md and the QA report
below), verify each on your own emulator by re-running the QA drivers, and report. The lead reviews and commits.

## Where and how
- Worktree `/home/jeremyking/projects/metro-launcher-p15` (branch `phase-15`). Edit ONLY
  `app/src/main/kotlin/app/tileshell/calculator/**` and, if a unit test is warranted, `app/src/test/kotlin/app/tileshell/calculator/**`.
  Do NOT edit other app code, the `:calc` module, the QA drivers or helpers, INDEX.md, STATE.md or any phase doc.
  Do NOT commit and never push. Never touch `/home/jeremyking/projects/metro-launcher`.
- Build: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest --tests 'app.tileshell.calculator.*'`
  from the worktree root. Read the exit code from a file, never through a pipe. Another agent may be building at the
  same time; Gradle waits on its lock, which is fine.
- Device: emulator-5560 ONLY (`export ANDROID_SERIAL=emulator-5560`, PATH += `$HOME/Android/Sdk/platform-tools`, and
  `export TMPDIR=/tmp/claude-1000/-home-jeremyking/5d5ffc39-5a2a-4c33-953f-07d71da27a45/scratchpad/p15/tmp5560`). Never
  address emulator-5554, -5556 or -5558. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, then
  re-assert the home activity the way docs/plan/qa/phase-03/scripts/provision.sh does.
- Verify by running the QA agent's drivers (read them first; do not edit them):
  `docs/plan/qa/phase-15/scripts/e13.sh`, then `e12.sh` and `e29.sh` as regressions. Before each re-run, rename the
  previous evidence dir to `<ROW>-runN` (never delete evidence). The measuring tool is `scripts/calc_geo.py`.

## 1. E13: position and size by INK, not by the text box (the defect file has the numbers and repros)
r11 (docs/plan/r11/calculator.md) measured glyph INK on the phone. The build places text BOXES and derives cap heights
from a single CAP_RATIO. Nine measurements fail:
- header title (1.5): ink left 60.5, cap 11.0, cap centre 26 epx below the status bar (± 0.5);
- History glyph (1.7): ink 16 × 16 epx, ink right edge at 345 epx (inset 15) (± 0.5);
- result display (2.14): digits 33.0 epx tall, ink right inset 16, ink top 17.4 epx into the row (± 0.5 / ± 0.5 / ± 1).
Recorded, same pattern, fix them too: pane label cap top 19.0 (r11 3.6) and the Settings gear 20 × 20 (r11 3.12).
Root cause, not a per-number fudge: size and place each by the glyph's real ink metrics, measured from the font the
shell ships (e.g. TextMeasurer or Paint text bounds, or the icon font glyph's ink fraction), so every value r11 gives
lands. The result display also shrinks to fit (H11, CalcDisplayFit); the fix must keep that working (E11 and E12 pass).
Keep the dump-bounds geometry that passes today unchanged (E13's 136 passes).

## 2. The tagged node carries its own text (Harness contracts)
The Harness contracts section of docs/plan/phase-15-inbox-clock-calculator-recorder.md says the node a driver reads
carries its own tag, never a parent whose text lives in its children. Today these have EMPTY text on the tagged node,
with the label in an untagged child: the pane's `calc_mode:<id>` and `calc_converter_category:<n>` rows, the unit
picker's `calc_unit:<id>` rows, and the date picker's `calc_date_pick:<col>:<value>` rows. Make the tagged node report
the label, e.g. merged semantics on the row, without changing the visuals or the tags. calc_ui.py currently reads the
first child's text; after your fix the tagged node's own text must equal it (check with a uiautomator dump).

## 3. The date pickers' drag: check it against r11, fix if it is wrong
QA recorded that a 3-row `input swipe` on a date picker column moves ONE row (E29 RECORD; taps work).
Read r11/calculator.md's date picker section and DatePage.kt's drag handling. If r11 or the W10M looping selector it
models makes a drag move the rows 1:1 with the finger, fix it at the producer and show before/after on the device
(a 3-row swipe moving 3 rows). If r11 says nothing, report what the code does and why; do not invent a spec.

## Report back (final message, under 60 lines)
Per item: what you changed (file:line) and why, the before and after measurements (quote calc_geo.py / dump output),
the final e13 / e12 / e29 summary lines, the unit tests' result, and anything you could not do, with its reason.
