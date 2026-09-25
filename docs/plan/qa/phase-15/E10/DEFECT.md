# E10 — product defects (fidelity, H1): 4 of 133 measurements outside r11's tolerance

Build: app-debug.apk md5 33cc71c192190cc9 (branch phase-15, the lap / long-list / ended-while-off fixes in), emulator-5558
(1080×2340, 3 px/epx), driver `docs/plan/qa/phase-15/scripts/e10.sh` (blob e64aca2a), log `E10/E10.txt`:
`E10: 129 passed, 4 failed, 1 recorded`. Every dump-bounds row, fill, rule and motion passes, and the Sounds list now
reaches its last rows (the E10-run4 defect is fixed). The four failures are the Calculator's E13 pattern
(`E13-run1/DEFECT.md`, fixed there by `calculator/CalcInk.kt`): r11 measured INK, and the Clock places the text BOX at
r11's ink value (three rows), plus one layout split that is not r11's.

Measured with `scripts/ink.py glyph <png> <box> bright <thr>` (the first glyph's ink box, "left top right bottom",
inclusive-exclusive) inside the node's own dump bounds; glyph metrics read from `app/src/main/res/font/selawik_*.ttf`
with fontTools (unitsPerEm 2048, capHeight 1434, hhea ascent 2027 / descent 431).

## 1. 2.1 "No alarms": ink x 12.0 epx, wanted 9.8 (± 0.9 epx + 1 px)

```
$ bounds E10/alarm_empty.xml alarm_empty          -> 29 327 356 418      (box left 29 px = 9.67 epx: at r11's INK value)
$ python3 scripts/ink.py glyph E10/alarm_empty.png 29 327 356 418 bright 60
36 349 76 402                                     -> ink left 36 px = 12.0 epx (FAIL: 36 vs 29 ± 4)
```
Source: `clock/ClockWidgets.kt` `EmptyLine` — `BasicText(text, Modifier.offset(x = 9.8.dp, …), style = … 25.4.sp Light)`.
Selawik Light "N" has a left side bearing of 184/2048 em = 2.28 epx at 25.4 epx, so the ink starts 2.28 epx right of
the box: 9.8 + 2.28 = 12.08 epx, what the screen shows. r11 2.1: "ink x 9.8 epx".

## 2. 4.6 the Sounds page title: ink x 26.0 epx, wanted 24.2

```
$ bounds E10/sounds.xml sounds_title              -> 73 185 388 307      (box left 73 px = 24.33 epx)
$ python3 scripts/ink.py glyph E10/sounds.png 73 185 388 307 bright 100
78 213 118 287                                    -> ink left 78 px = 26.0 epx (FAIL: 78 vs 73 ± 4)
```
Source: `clock/AlarmTab.kt:456` — `BasicText("Sounds", Modifier.offset(x = 24.2.dp, y = 33.8.dp), style =
ShellType.subheader)` (34 epx Light). Selawik Light "S": left bearing 110/2048 em = 1.83 epx at 34 epx → ink at
26.0 epx. r11 4.6 measured the title's ink "at x 24.2".

## 3. 2.7 the first alarm time's cap top: 12.67 epx below the band, wanted 14.3

```
$ bounds E10/alarm_rows.xml alarm_row:<id1>        -> 0 289 1080 553      (the row starts at the band's bottom, 289 px)
$ bounds E10/alarm_rows.xml alarm_time:<id1>       -> 29 306 294 397      (box top 306 = 289 + capPad 5.70 epx; box 91 px tall)
$ python3 scripts/ink.py glyph E10/alarm_rows.png 29 306 294 397 bright 100
32 327 65 381                                     -> the "7": ink top 327 px = 12.67 epx below the band (FAIL: 327 vs 332 ± 4)
```
Source: `clock/AlarmTab.kt:143` — `Modifier.offset(x = 9.8.dp, y = capPad(14.3f, 25.4f, 32f))` with
`capPad = CapMetrics.topPaddingForCapTop` (`ui/tokens/CapMetrics.kt`). `capTopWithinBox(25.4, 32)` assumes the
declared 32-epx line height is laid out with its extra 1.51 epx (32 − the font's natural 30.49) spread above and below
the line, putting the baseline 26.39 epx and the cap top 8.60 epx into the box. Compose does not keep that extra on a
single line (its default LineHeightStyle trims it from the first line's top and the last line's bottom): the box is
91 px = 30.3 epx tall — the natural height, not 32 — and the "7"'s baseline is at 381 px = 25.0 epx into it
(hhea ascent 2027/2048 × 25.4 = 25.14), so the cap top is 7.36 epx into the box and the ink lands 1.25 epx (3.7 px)
above the cap top the offset was computed for. Every `capPad` / `topPaddingForCapTop` text in the Clock carries the
same bias; on the rows E10 measures it is inside tolerance elsewhere (2.1's cap top 349 vs 353, 1.6's 210 vs 212)
and outside it here. r11 2.7: "first time cap top 14.3 epx below the band" (ink).

## 4. 4.8 the timer editor's first column split: 119.67 epx, wanted 117.7 (± 0.9 epx)

```
$ bounds E10/timer_editor.xml timer_editor_field:hours    -> 0 204 358 969
$ bounds E10/timer_editor.xml timer_editor_field:minutes  -> 361 204 719 969
  the divider's pixels at y 300 of E10/timer_editor.png: (40,40,40) at x 358–360 -> centre 359 px = 119.67 epx
  (FAIL: 359 vs 353 ± 3; the second split, 720 vs 718, passes)
```
Source: `clock/TimerTab.kt:231-236` — three `LoopSpinner`s with `Modifier.weight(1f)` between two 1-dp dividers: equal
thirds, (360 − 2) / 3 = 119.33 epx per column. r11 4.8 measured unequal columns — splits at 117.7 and 239.4 epx
(columns 117.7 / 121.7 / 120.6) — and wrote "≈ W/3" beside them; the build took the approximation, not the values.

## Repro

    export ANDROID_SERIAL=emulator-5558
    bash docs/plan/qa/phase-15/scripts/e10.sh       # the four FAIL lines above, with the dumps and screencaps in E10/

## What is NOT defective

Every other E10 clause on this build: the tab band, tabs, underline and selected accent (1.x), the empty line's cap
height, cap top and grey (2.1), the row pitch and time digits (2.3, 2.8), the editor, snooze list, Sound flyout,
Sounds page rule, pitch and full list (4.x), the timer editor's spinner band and second split, the World Clock, Timer
and Stopwatch tabs, the app bar and drawn bars, the timer toast, the tap JUMP and the swipe / flyout motions.
