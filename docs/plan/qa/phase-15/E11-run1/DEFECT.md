# E11 run 1: stale angle and memory rows (product defect, fixed)

Run 1 was stopped after 80 standard and 42 scientific lines. It had 8 standard mismatches (memory: standard-071, 073–076,
078–080) and 7 scientific mismatches (angle presets: scientific-027, 028, 030–034). See results-*.tsv.

## Repro on emulator-5556, APK before the fix
Scientific page, tapping the angle key three times at its centre (108,720), then reading its label from the dump:

    $ for i in 1 2 3; do adb shell input tap 108 720; sleep 0.5; adb shell uiautomator dump /sdcard/d.xml >/dev/null; adb shell cat /sdcard/d.xml | grep -o 'text="\(DEG\|RAD\|GRAD\)"'; done
    text="DEG"
    text="DEG"
    text="DEG"

HYP tapped at (324,720): `calc_key:hyp checked=false`. Yet the display showed `cosh(1)` from an earlier line, so the
engine had toggled HYP. The row simply never redrew.

## Cause
`AngleRow(model, …)` and `MemoryRow(model, …)` take only `model`, the same instance on every recomposition, so Compose
(strong skipping) skips them. Only the page composable read `model.tick`. Their labels and `enabled` flags therefore
stayed at their first composition:
- The angle label stayed DEG. The driver tapped until the label read the wanted unit, so it went round the full cycle
  back to DEG, and with no angle presses left it in the unit before that.
- MC and MR stayed `enabled=false` after MS, and KeyCell drops taps on a disabled key (`if (up != null && live)`), so
  MR never recalled.

## Fix (at the producer)
`CalcModel`: the engine objects are read through getters that read `tick` (`calc`, `converter`, `date`). Every
composable that reads engine state now subscribes to `tick`, however deep it sits.
Unit test: `app/src/test/kotlin/app/tileshell/calculator/CalcModelObservableTest.kt` fails 5/5 before the fix
(AssertionError) and passes 5/5 after.

## After the fix, the same taps on the device

    text="RAD"
    text="GRAD"
    text="DEG"
    --- HYP
    checked="true"

Run 2 is `../E11/`.
