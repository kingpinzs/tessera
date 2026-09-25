# E13 — product defects (fidelity, H2): 9 of 145 measurements outside r11's tolerance

Build: app-debug.apk built 2026-09-24 08:06 (md5 39402722396d9129), emulator-5560 (1080×2340, 3 px/epx), driver
`docs/plan/qa/phase-15/scripts/e13.sh` run 1 (`E13/E13.txt`: 136 passed, 9 failed, 7 recorded). The dump bounds are all
within tolerance (the star rows, the W/4 · W/6 · 0.25:1:1:1:0.25 columns, the pane, the rules and fills). Every failure is
an INK measurement — where r11 measured the glyph's ink on the phone, the build positions or sizes the text BOX, and the
font's bearings, cap height and glyph design move the ink off r11's numbers. Measured with
`scripts/calc_geo.py ink <png> x1 y1 x2 y2 [thr]` (the ink box of pixels with min(r,g,b) ≥ thr inside the region; the
values below are identical at thr 128 and thr 64, so anti-aliasing is not the cause).

## 1. Header title (r11 1.5): ink left 61.33 epx, cap 11.67 epx, cap centre 25.17 epx — wanted 60.5 / 11.0 / 26.0 (± 0.5)

```
$ python3 scripts/calc_geo.py ink E13/std.png 150 84 700 228 128     # the STANDARD title's ink in the header
184 142 430 176 247 35                                              # left top right bottom w h (px)
→ left 184/3 = 61.33 epx; height 35/3 = 11.67 epx; centre ((142+176+1)/2 − 84)/3 = 25.17 epx below the status bar
```
Source: `CalcHeader` (CalculatorScreen.kt:229-235) places the text box at `CalcMetrics.TITLE_LEFT = 60.5` and sizes the
font as `11 / CapMetrics.CAP_RATIO` (CapMetrics.kt: 1434/2048 = 0.700); the S glyph's left bearing (≈ 0.8 epx) and the
font's real cap height (35 px at a 47.1-px font = 0.74 of the em, overshoot included) land the ink 0.83 epx right and
0.67 epx taller than r11's phone measurement, which was ink ("STANDARD" x 242 px, y 178–221 on C1 at 4 px/epx).

## 2. History glyph (r11 1.7): ink 11.33 × 11.33 epx at a 17.33-epx right inset — wanted 16 × 16 at 15.0 (± 0.5)

```
$ python3 scripts/calc_geo.py ink E13/std.png 936 84 1080 228 128    # the glyph inside calc_history_toggle
994 139 1027 172 34 34                                              # 34 px = 11.33 epx square; right edge 1027 → (1080−1028)/3 = 17.33 epx
```
Source: `CalcHeader` draws `Glyph.HISTORY` with `CortanaIcons.Font(…, CalcMetrics.HISTORY_GLYPH = 16, Modifier.size(16.dp))`
(CalculatorScreen.kt:248-255) — the icon font's glyph fills ≈ 71 % of its em, so a 16-epx box gives 11.33 epx of ink,
centred (cy 24 epx below the status bar — PASS) but 2.33 epx short on every side. r11 measured the ink: 64 px = 16.0 epx
on the 950, right edge at 345 epx. The glyph needs ≈ 22.6 epx of font (16 / 0.708) and its INK right edge at 345 epx.

## 3. Result display (r11 2.14): digits 33.67 epx tall, right inset 19.33 epx, cap top 14.96 epx into the row — wanted 33.0 (± 0.5) / 16 (± 0.5) / 17.4 (± 1)

```
$ python3 scripts/calc_geo.py ink E13/std.png 600 319 1080 647 128   # "512" in the result row (row top 319.1 px predicted, 20/432 of the content)
807 364 1021 464 215 101                                            # height 101/3 = 33.67; right (1080−1022)/3 = 19.33; top (364−319.11)/3 = 14.96
$ bounds E13/std.xml calc_display → right edge 1032 px = 344 epx (the box IS at the 16-epx inset — PASS)
```
Source: `ResultDisplay` (CalcPages.kt:170-182) pads the text BOX by `RESULT_RIGHT_INSET = 16` and by
`capPad(RESULT_CAP_TOP = 17.4, base)`; the `2`'s right side bearing (10 px at a 138-px font) leaves the ink 3.33 epx
inside the box, the digits' real height at 46 epx is 101 px (0.73 of the em, not CAP_RATIO's 0.70), and the cap-top pad
computed from CAP_RATIO / ASCENT_RATIO puts the ink top 2.44 epx above r11's 17.4. r11 2.14 measured the ink ("5,512"
digits 33.0 epx tall, right edge 344.25 epx, digit top = row top + 17.4 on both devices).

## Recorded (not graded) values that show the same pattern

- pane label ink left (Programmer row) 61.33 epx — r11 3.6 measured 60.0–61.25; the box is at 60 (PASS).
- pane label cap top 17.33 epx below the row top — r11 3.6: 19.0 (`CapTopText` with `PANE_LABEL_CAP_TOP = 19` lands the
  ink 1.67 epx high).
- Settings gear ink 50 × 52 px = 16.7 × 17.3 epx at cx 24.0 — r11 3.12: 20 × 20 at cx 24 (the icon font's glyph again
  smaller than its 20-epx box).

## What is NOT defective

All 24 dump-bounds rows, columns and edges (2.1–2.3, 2.7, 2.16, 4.1, 4.7, 4.12, 5.1–5.2, 5.5–5.7, 3.1–3.5, 3.11–3.12), the
≡ glyph (cx 23.5, bars 20 epx at y 19/24/29 below the status bar — 1.3), the rule and fills (2.4–2.6, 3.7, 3.11), the
accent word-size label (4.7), the History glyph's absence on Programmer and converter pages and its presence on
Scientific — see `E13/E13.txt`.
