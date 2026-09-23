# R11 §Calculator — Calculator (Windows 10 Mobile) measurements

Scope: the W10M Calculator app — Standard, Scientific, Programmer, the Converter (category list and a converter page), the
hamburger pane, and the engine rules the phase's fixtures depend on. Gates phase 15's FINAL
(docs/plan/phase-15-inbox-clock-calculator-recorder.md: E11, E12, E13, H2, H11). Governing build: the final W10M release
(15063 / 15254). Format and rigour follow docs/plan/r8-groove-measurements.md; conventions shared with r11/clock.md and
r11/voice-recorder.md.

## Summary — what was and was not established

**Established at native resolution on two devices at two W10M scale factors (Lumia 950 at 400 %, Lumia 950 XL at 350 %,
build 10586, December 2015):** the page is a 24-epx status bar and a 48-epx header (`≡` at cx 23.5, the mode name in
semibold caps at x 60.5 with an 11-epx cap, and a 16-epx History glyph 15 epx from the right edge in Standard only), the
same header R8 measured in Groove. Everything below the 72-epx chrome down to the 48-epx nav bar is **one star-sized grid
whose row weights are exactly microsoft/calculator's** (Standard 20* expression / 72* result / 32* memory / 308* number pad
of six equal rows): the predicted row edges land within 0.1 epx of the measured ones on both devices, so every row
stretches with screen height while every glyph and text size stays fixed in epx. Columns are fractions of width (W/4
Standard, W/6 Programmer and memory row, 0.25 : 1 : 1 : 1 : 0.25 Converter number pad) — the same pixel columns on both
devices. Colours: black page; number-pad rows 2–6 a flat #1F1F1F with **no key borders, gaps or per-key fills**; row 1 of
the number pad (`% √ x² ¹⁄x` in Standard, `Lsh Rsh Or Xor Not And` in Programmer) sits on black under a 1-epx #191919
rule; every key glyph is white; **the `=` key and the operator column are not accent and not a different fill**. The
hamburger pane is 256 epx wide in epx on both devices, #2B2B2B, 48-epx rows (Standard, Scientific, Programmer, Date
calculation, CONVERTER, then the categories), with Settings pinned in the bottom 48-epx row under a 1-epx #404040 rule
inset 12 epx; its selected row is the accent at 60 % over the pane, measured with two different user accents; the page
behind it is not dimmed. The Programmer page (star model 96* display controls / 268* number pad) and the Speed converter
page (56* / 32* / 56* / 32* / Auto / 272*) match the same source to within 1 epx on the 950.

**Established from source-2 only** (microsoft/calculator, initial open-source commit 057401f5, 2019-03-05 — the desktop-era
package, min build 17134, not the W10M 15063/15254 package; used where footage fails, RV9): Standard mode calculates
immediately (no operator precedence) while Scientific and Programmer respect it; display precision 16 / 32 / 64 digits;
a result switches to e-notation when its integer digits exceed the precision (200 ! in Standard renders
7.886578673647905e+374); Overflow when the exponent needs more than 4 digits; Programmer has no history; the result font
is 46 epx and shrinks to fit down to 12 epx; the error and empty-state strings.

**Not established:** (1) the governing build — every native screenshot is 10586; the phone Calculator was updated through
2016–2017 (Currency converter, visual refresh) and no 14393/15063/15254 phone capture of it was found; (2) Scientific
geometry — its structure is known only from a May-2015 camera video; (3) the history pane, the memory flyout, Date
calculation and the ± key of the Temperature converter; (4) all motion except one LOW pane-open timing (no ≥ 55 fps
source); (5) light theme; (6) the pressed-key state.

---

## 0. Method, sources and calibration

### 0.1 Unit convention

`epx` = 1/360 of screen width (r8 §0.1). Lumia 950: 1440×2560 at 400 % → 360×640 epx, 4 px per epx. Lumia 950 XL:
1440×2560 at 350 % → 411.43×731.43 epx, 3.5 px per epx; XL values below are on its own 411-wide canvas. Scale factors
were measured, not assumed: the status bar ends at 96 px (950) and 84 px (XL) = 24.00 epx on both; the nav bar is 192 px
and 168 px = 48.00 epx on both; the `≡` glyph is 80 px and 70 px = 20.00 epx wide on both; the History glyph 64 px and
56 px = 16.00 epx on both. A value that lands on the same epx on both devices is **fixed in epx**; one that lands on the
same pixel column is **proportional to width**; one that scales with (canvas height − 120) is **proportional to content
height**. Measurements are sub-pixel runs on a single channel (`bg.py` column runs, `blobs.py` ink boxes; scratch evidence,
not committed); a JPEG edge is ±0.25 epx on the 950 and ±0.29 epx on the XL.

### 0.2 Sources

All images downloaded 2026-09-23, stored unaltered; sha256 first 16 hex. Every stored GSMArena file was re-downloaded
from its URL and its hash matched (the numbering 086–090 is right; an earlier contact sheet had labelled them 076–080).

| ID | URL | Date | Pixels / fps | Device, canvas | Build / app version (how known) | Stored file | sha256 |
|---|---|---|---|---|---|---|---|
| C1 | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_086.jpg — Standard, "5,512" | GSMArena Lumia 950 review p10 (https://www.gsmarena.com/microsoft_lumia_950-review-1347p10.php), Dec 2015 | 1440×2560 | Lumia 950, 360×640 | ≈10586 (review date; lock screen "Wednesday 2 December"); app version not visible | docs/plan/r11/src/calculator/gsm950_086.jpg | 2214b9edc5a49cc1 |
| C2 | …/microsoft-lumia-950/shots/gsmarena_087.jpg — hamburger pane open over Standard | as C1 | 1440×2560 | as C1 | as C1 | docs/plan/r11/src/calculator/gsm950_087.jpg | 26af8527ad1c4721 |
| C3 | …/microsoft-lumia-950/shots/gsmarena_088.jpg — Programmer, "4F15" | as C1 | 1440×2560 | as C1 | as C1 | docs/plan/r11/src/calculator/gsm950_088.jpg | b5e72d7093f492d6 |
| C4 | …/microsoft-lumia-950/shots/gsmarena_089.jpg — Speed converter, 523 km/h | as C1 | 1440×2560 | as C1 | as C1 | docs/plan/r11/src/calculator/gsm950_089.jpg | f46ad6574d0841af |
| C5 | …/microsoft-lumia-950/shots/gsmarena_090.jpg — Speed converter, 523,535 km/h | as C1 | 1440×2560 | as C1 | as C1 | docs/plan/r11/src/calculator/gsm950_090.jpg | ff4c4b462d8a77dd |
| X1 | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_084.jpg — pane open over Standard, "62,622", blue accent, dual SIM | GSMArena Lumia 950 XL review, Dec 2015 (lock screen "Monday 7 December") | 1440×2560 | Lumia 950 XL, 411.43×731.43 | ≈10586 (review date) | docs/plan/r11/src/calculator/gsm950xl_084.jpg | 8fc13b69508ab58b |
| V1 | https://www.youtube.com/watch?v=6OCdjJqOy0Y "Calculator app gets a complete revamp in Windows 10 Mobile" (channel "Views") @ 64 s, 67 s, 102 s, 104 s, 123 s, 158 s | uploaded 2015-05-22 | 1280×720 @ 30 fps | camera, hand-held, Nokia-branded WVGA Lumia (≈320×533 epx); no ruler | ≈10080 Insider (upload date); **not governing** | docs/plan/r11/src/calculator/6OCdjJqOy0Y_t064.png, _t067, _t102, _t104, _t123, _t158 (frames extracted with ffmpeg, unaltered) | 5821b3961f9bd5a2, c335980b66354b28, 6d66499cfc36486e, 2e9638205968901d, ea1fb1985375ec4e, 89931b6db9253673 |
| S1 | https://github.com/microsoft/calculator at commit 057401f5f2b4bb1ea143da02c773ac18d1bb9a2e (initial open-source commit, 2019-03-05, MIT): src/Calculator/App.xaml, Views/Calculator.xaml, Views/CalculatorStandardOperators.xaml, CalculatorScientificOperators.xaml, CalculatorProgrammerRadixOperators.xaml, CalculatorProgrammerDisplayPanel.xaml, UnitConverter.xaml, Resources/en-US/Resources.resw, CEngineStrings.resw; src/CalcManager/CalculatorManager.h/.cpp, CEngine/scidisp.cpp, Ratpack/conv.cpp | 2019-03-05 | text | — | desktop-era package (min build 17134); **source-2, not footage** | scratch evidence (not committed); cited file:line | — |
| S2 | https://learn.microsoft.com/en-us/windows/apps/design/style/segoe-ui-symbol-font (Segoe MDL2 Assets icon list) | fetched 2026-09-23 | text | — | — | — | — |

### 0.3 Searched without finding

- **A 14393 / 15063 / 15254 phone capture of Calculator.** GSMArena reviews: Lumia 650 (35 shots) and 550 have no
  Calculator shot; the 950 XL has only 084. Microsoft Store listing: the current display catalogue and the Wayback
  snapshots of 2016-03-08, 2017-01-19 and 2017-10-03 carry only 1366×768 or 3840×2160 **desktop** screenshots.
- **A native Scientific-mode capture.** None in the 950 (19 app shots) or 950 XL sets; only V1 (camera, 2015).
- **Screen recordings.** Index sheets of every downloaded candidate were checked (gl4xwft2UxU, XrcggfMxvf4, OGNKtVEXdLE,
  lpRcZAO91lw, 6XzfhogUBt0, ypkpcWrxx2M — the last shows Standard and the converters in light theme on 10166, camera);
  no Project My Screen recording of Calculator exists among them, so no 60-fps motion. New YouTube downloads are refused
  ("Sign in to confirm you're not a bot") on every player client tried.

### 0.4 Shared values cited, not re-measured

- Header block: R8 §1.2 (Groove, 10586) — `≡` cx 24.00, title left 61.0, cap 11.0, header 24 → 72 epx. Calculator
  agrees within 0.5 epx (1.1–1.4 below), so this is the W10M UWP hamburger header, not a Calculator-specific one.
- Hamburger pane width: R7 §3.1.5 (Cortana, 256 ± 1 epx, laid out in epx) — Calculator agrees (3.1).
- Nav bar 48 epx: phase 01 X6 — agrees (1.4).
- Status bar: the shell draws 28 epx (R3 C4, measured on Start); these app captures show 24 epx, as R8 §1.1 found in
  Groove. Recorded as a gap, not changed here.

---

## 1. Page frame and header

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 1.1 | Status bar | **24.00 epx**, black; the pane (3.x) starts exactly under it | HIGH | 10586 | C2, X1 | pane top at 96 px (950) / 84 px (XL) = 24.00 epx on both — fixed epx |
| 1.2 | Header | **24 → 72 epx (48 epx)**, black on the page, #2B2B2B inside the open pane | HIGH | 10586 | C1–C5, C2, X1 | pane header band 24.00–72.00 on both devices |
| 1.3 | `≡` glyph | three bars **20.0 epx** long, **1.25 epx** thick (1.43 on the XL, JPEG), 5.0-epx pitch, x **13.5–33.5 (cx 23.5)**, bars at y 43.0 / 48.0 / 53.0 (cy 48.6) | HIGH | 10586 | C1 x 54–133 y 172–216; X1 x 47–116 y 150–189 | same epx on both — fixed |
| 1.4 | Nav bar | **48.00 epx**, black | HIGH | 10586 | C1 y 2368–2559; X1 y 2392–2559 | both scales |
| 1.5 | Title | mode or category name in **semibold caps**, left edge **60.5 epx**, cap **11.0 epx** (11.14 XL), cap centre **y 50.0**; white | HIGH | 10586 | C1 "STANDARD" x 242, y 178–221; X1 "CALCULATOR" x 212, y 156–194 | fixed epx; ≈15–16-epx semibold by cap/0.70 (MEDIUM for the size) |
| 1.6 | Title strings seen | STANDARD, PROGRAMMER, SPEED (a converter page is titled with its category), CALCULATOR (pane header); SCIENTIFIC (V1) | HIGH | 10586 (V1 2015) | C1, C3, C4, C2, X1; V1 @ 158 | read |
| 1.7 | History glyph | **16.0 × 16.0 epx**, x 329.0–345.0 (right inset **15.0**; XL 14.86), cy **48.0**, white; present in **Standard only** (absent on Programmer C3 and converter C4/C5) | HIGH | 10586 | C1 x 1316–1379 y 160–223; X1 x 1332–1387 | fixed epx, right-anchored |
| 1.8 | Page background | #000000 (dark theme) | HIGH | 10586 | C1–C5 | samples |

## 2. Standard

Rows are the source's star rows (S1 Calculator.xaml:611–625: RowExpression 20*, RowResult 72*, RowMemoryControls 32*,
RowNumPad 308*) laid in the content area between the header (72 epx) and the nav bar. Predicted from 1 star =
content / 432 (950: 520 / 432 = 1.2037 epx; XL: 611.43 / 432 = 1.4154 epx):

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 2.1 | Row model | expression **20***, result **72***, memory **32***, number pad **308*** (6 equal rows), over content = canvas − 72 − 48 epx | HIGH | 10586 + S1 | C1, X1; S1 Calculator.xaml:613–625 | predicted memory-row bottom 221.26 (950) / 247.50 (XL) vs measured rule top **221.25 / 247.43**; predicted key row 61.79 / 72.65 vs measured **61.80 / 72.69** |
| 2.2 | Rows on 360×640 | expression 72.0–96.1; result 96.1–182.7; memory 182.7–221.3; number pad 221.3–592.0 = 6 × **61.8 epx** | HIGH | 10586 | C1 | from 2.1 |
| 2.3 | Rows on 411×731 (XL) | number pad 247.4–683.4 = 6 × **72.7 epx** | HIGH | 10586 | X1 | from 2.1 — rows grow with height, glyphs do not |
| 2.4 | Number-pad top rule | **1 epx**, **#191919** (25,25,25), full width, the top line of number-pad row 1 | HIGH | 10586 | C1 y 885–888; X1 y 866–869 | both devices |
| 2.5 | Number-pad row 1 | `%` `√` `x²` `¹⁄x` on **black** (no fill), 221.3–283.0 epx | HIGH | 10586 | C1 y 889–1131 = (0,0,0) | |
| 2.6 | Number-pad rows 2–6 | flat **#1F1F1F** (31,31,31) from 283.00 to 592.00 epx; **no borders, gaps or separators** between keys (every row sampled is 31 across all 1440 px); **no separate operator-column fill; `=` is not accent** | HIGH | 10586 | C1 y 1132–2367; X1 y 1120–2391 | row scans at y 1180/1400/1650/1900/2140/2350 |
| 2.7 | Columns | **4 × W/4**; glyph centres 45 / 135 / 225 / 315 epx (measured 44.4–45.0 / 134.8–135.1 / 224.8–225.3 / 314.4) | HIGH | 10586 | C1; X1 operator column at 1257.5 px on both devices | same pixel column on 400 % and 350 % — proportional to width |
| 2.8 | Key order | `% √ x² ¹⁄x` / `CE C ⌫ ÷` / `7 8 9 ×` / `4 5 6 −` / `1 2 3 +` / `± 0 . =` | HIGH | 10586 | C1, X1 (right columns) | read |
| 2.9 | Glyph vertical placement | centred in their rows (digit ink centres 376.5 / 438.3 / 500.0 / 562.0 vs row centres 375.7 / 437.5 / 499.3 / 561.1) | HIGH | 10586 | C1 | |
| 2.10 | Digit glyphs | ink height **20.25 ± 0.25 epx** (0–9), white, semibold (≈29-epx SemiBold by cap/0.70; S1 NumericButtonStyle is SemiBold, App.xaml:206–209) | MEDIUM | 10586 | C1 digits y 1465–2288 | one device (XL digits hidden by the pane) |
| 2.11 | Operator glyphs | `÷ + − =` **19.25 epx** wide (XL 19.14 — fixed epx), `+` 19.25 × 19.25, `×` 16.75 × 16.75, strokes **1.75 epx**; `=` two bars 7.4 epx apart | HIGH | 10586 | C1 x 1219–1295; X1 x 1224–1290 | both devices |
| 2.12 | `CE` / `C` / `⌫` | `C` ink 14.75 epx tall (smaller than digits, ≈21-epx font); `⌫` 20.5 × 15.5 epx | MEDIUM | 10586 | C1 y 1219–1283 | |
| 2.13 | Row-1 glyphs | `%` 17.5 tall, `√` 18.5, `x²` 17.5, `¹⁄x` 17.5 epx ink; `x²` and `¹⁄x` are italic serif math glyphs | MEDIUM | 10586 | C1 y 971–1044 | |
| 2.14 | Result text | digits **33.0 epx** tall (XL 33.43 — fixed), right edge **344.25** → right inset **15.75** (XL 16.29) → **16 ± 0.5 epx**; digit top = result-row top **+ 17.4 epx** on both devices (113.50 − 96.07; 117.71 − 100.31); white, semibold | HIGH | 10586 | C1 "5,512" y 451–606; X1 "62,622" y 412–547 | 33.0 / 0.70 ≈ 46–47 → S1 CalcResultFontSizeM **46** (App.xaml:129), right / top aligned (Calculator.xaml:276–277) |
| 2.15 | Digit grouping | locale separators: "5,512", "62,622" (en-GB); V1 shows "5,00,715" (en-IN grouping) | HIGH | 10586 (V1 2015) | C1, X1; V1 @ 99 | the phone follows the region format |
| 2.16 | Memory row | `MC MR M+ M- MS M˅` on **6 × W/6** (centres 30.25 / 90.5 / 149.9 / 209.9 / 269.9 / 330.0), centred in the memory row (label cy 202.9 vs row centre 202.0); cap **8.75 epx** (XL 8.57 — fixed); MS at 1079.5 px on both devices (proportional) | HIGH | 10586 | C1 y 791–828; X1 MS x 1050–1106 | ≈12-epx semibold caps |
| 2.17 | Memory label colours | enabled **(155,155,155)** ≈ white 61 % (M+ M- MS M˅); disabled with nothing stored **(53,53,53)** ≈ white 20 % (MC MR) | HIGH | 10586 | C1; X1 (MS 156) | p90 samples; 60 % / 20 % match UWP BaseMedium / BaseLow |
| 2.18 | `M˅` glyph | a path, not a font glyph: "M" 9.0 × 8.4 plus a 5 × 2.5 down-triangle at x 10.5–15.5, top-aligned; measured **15.5 × 8.5 epx** | HIGH | 10586 + S1 | C1 x 1289–1350; S1 Calculator.xaml:766–769 (PathFakeButtonStyle path, 1 unit = 1 epx) | footage and source agree |
| 2.19 | Expression line | right-aligned, small, grey, above the result in the 20* row: "48 ÷" while an operation is pending | LOW | ≈10080 | V1 @ 102 | camera; empty in C1 (after =) |

## 3. Hamburger pane (modes and the converter list)

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 3.1 | Width | **256.0 epx** on both devices (1024 px at 400 %, 896 px at 350 %) — fixed epx | HIGH | 10586 + S1 | C2 edge x 1024; X1 edge x 896; S1 App.xaml:108 SplitViewOpenPaneLength 256 | equals R7 §3.1.5 |
| 3.2 | Extent and fill | 24.00 epx → nav bar top, **#2B2B2B** (43,43,43); covers the header (its own `≡` + title row at the page's positions) | HIGH | 10586 + S1 | C2, X1; S1 App.xaml:19 ChromeMediumLowColor #FF2B2B2B | |
| 3.3 | Page behind the pane | **not dimmed** (result digits stay 255) | HIGH | 10586 | C2, X1 | samples right of the pane |
| 3.4 | Row pitch | **48.0 epx**, fixed (950 label cap tops 91.0 / 139.0 / 187.75 / 235.75 / 284.5 / 331.0 / 379.0 / 427.0 / 475.75 / 523.75; XL identical to ±0.2) | HIGH | 10586 | C2, X1 | |
| 3.5 | First row | starts at **72.0 epx**, directly under the pane header | HIGH | 10586 | C2 y 288, X1 y 252 | |
| 3.6 | Row labels | left **60.0–61.25 epx** (origin 60), cap **11.0–11.5 epx** (≈15-epx Body, regular), cap top **19.0 epx** below the row top; white | HIGH | 10586 | C2, X1 | same epx on both |
| 3.7 | Selected row | full-width **accent at 60 % over #2B2B2B**: 950 magenta accent (132,16,87), XL blue accent (54,77,170); label stays white | HIGH | 10586 | C2 y 288–479; X1 y 252–419 | 0.6 × accent + 0.4 × 43 reproduces both fills (950 accent (192,0,119) from C3's accent text; XL (61,100,255) = R3's capture rendition of blue) |
| 3.8 | Group header | "CONVERTER": a 48-epx row in the same pitch, **semibold caps, cap 11.0 epx** at x 60.5 (the title style) | HIGH | 10586 | C2 y 1138–1181; X1 y 996–1035 | |
| 3.9 | Order | Standard, Scientific, Programmer, **Date calculation**, CONVERTER, Volume, Length, Weight and Mass, Temperature, Energy, Area, Speed, Time, Power, Data, Pressure, Angle, then Settings | HIGH (to Speed) / LOW (Time → Angle) | 10586 / ≈10080 | C2 (to Energy), X1 (to Speed); V1 @ 64, 67 (Time, Power, Data, Pressure, Angle, Settings) | no Currency in either build |
| 3.10 | List scrolls under Settings | the list is clipped by the bottom rule (C2 "Energy", X1 "Speed" cut) | HIGH | 10586 | C2, X1 | |
| 3.11 | Bottom rule | **1 epx**, **#404040** (64,64,64), x **12.0 → 244.0 epx** (12-epx insets), its top exactly **48.0 epx above the nav bar** | HIGH | 10586 | C2 y 2176–2179, x 48–975; X1 y 2224–2227, x 42–853 | both devices |
| 3.12 | Settings row | gear **20 × 20 epx** at x 14.0–34.0 (cx 24, aligned with `≡`), centred 24 epx above the nav bar; label "Settings" at x 60.75 | HIGH | 10586 | C2 y 2232–2311; X1 y 2273–2342 | |

## 4. Programmer

One device (C3). Row edges match the source's Programmer state (S1 Calculator.xaml:507–512: RowDisplayControls 96*,
RowNumPad 268*; 1 star = 520 / 488 = 1.0656 epx) to within 1 epx.

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 4.1 | Row model | expression 20*, result 72*, radix panel **96***, tab / memory row 32*, number pad **268*** (6 rows) | HIGH | 10586 + S1 | C3; S1 Calculator.xaml:507–512 | predicted radix bottom 272.33 vs measured rule 271.25–272.25; predicted tab-row bottom 306.43 vs measured 306.5; key row 47.60 vs measured **47.6** |
| 4.2 | Result | "4F15" digits **32.5 epx** tall, right edge 344.75 (the Standard style, 2.14) | MEDIUM | 10586 | C3 y 440–574 | |
| 4.3 | Radix rows | HEX / DEC / OCT / BIN; labels left **12.5–13.0**, values left **48.0–48.5 epx**; pitch **24.6 epx** (cy 182.9 / 207.5 / 232.1 / 256.6); cap **8.5–9.0 epx** (≈12–13 epx) | MEDIUM | 10586 | C3 y 714–1043 | |
| 4.4 | Radix colours | selected radix label **and** value in accent (197,3,123); other labels white, other values (156,156,156) ≈ white 61 % | MEDIUM | 10586 | C3 | p90 samples |
| 4.5 | Radix value grouping | DEC "20,245" (locale); OCT "47 425" (groups of 3, space); BIN "0100 1111 0001 0101" (groups of 4, space, zero-padded to the nibble) | MEDIUM | 10586 | C3 | read |
| 4.6 | Rule above the tab row | 1 epx #191919 at 271.25–272.25 (the radix panel's last line) | MEDIUM | 10586 | C3 y 1085–1088 | |
| 4.7 | Tab / memory row | 272.25–306.5 epx: full-keypad icon (col 1, cx 30), bit-toggle icon (col 2, cx 90), **word-size button "QWORD" in accent** centred at **180** (spans cols 3–4), `MS` cx 270, `M˅` cx 330 — a W/6 grid | MEDIUM | 10586 | C3 y 1092–1216 | |
| 4.8 | Tab icons | full keypad = 3 × 4 dots **2 × 2 epx** at **4-epx** pitch (x 25–35, y 282.5–296.5), accent when selected; bit toggle = 2 × 3 ring dots 4 × 4 epx, grey (156) when not selected | MEDIUM | 10586 | C3 | |
| 4.9 | Selected-tab underline | accent **2.0 epx** at 304.5–306.5, x **0 → 60.0 epx** (exactly one W/6 cell) | MEDIUM | 10586 | C3 y 1218–1225, x 0–239 | |
| 4.10 | Number-pad top rule | 1 epx #191919 at 306.5–307.5 | MEDIUM | 10586 | C3 y 1226–1229 | |
| 4.11 | Number-pad row 1 | `Lsh Rsh Or Xor Not And` on black at W/6 centres (30.1 / 90.1 / 150.4 / 210.0 / 270.4 / 329.6), ink 8.75–9.25 epx; a small **↑ (5 × 6 epx, grey 104)** under Lsh and Rsh at cy 346 | MEDIUM | 10586 | C3 y 1232–1412 | |
| 4.12 | Number-pad rows 2–6 | #1F1F1F, 6 × W/6; `↑ Mod CE C ⌫ ÷` / `A B 7 8 9 ×` / `C D 4 5 6 −` / `E F 1 2 3 +` / `( ) ± 0 . =` | MEDIUM | 10586 | C3 y 1416–2367 | |
| 4.13 | Programmer glyph sizes | digits and A–F ink **13.0 epx** (≈18-epx font; S1 uses NumericButtonStyle18 here, CalculatorProgrammerRadixOperators.xaml:346); operators **11.5 epx** wide, **1-epx** strokes | MEDIUM | 10586 + S1 | C3 | smaller than Standard (2.10, 2.11) |
| 4.14 | Disabled key | `.` in integer mode drawn (79,79,79) ≈ white 19 % | MEDIUM | 10586 | C3 x 1075–1083 y 2279–2287 | |
| 4.15 | No history | no History glyph in the header (C3) and no history engine (S1 CalculatorManager.cpp:196, nullptr history) | HIGH | 10586 + S1 | C3; S1 | footage and source agree |

## 5. Converter (Speed page)

One device (C4, C5). Row edges match the source (S1 UnitConverter.xaml:302–319: RowDisplay1 56*, RowUnit1 32*,
RowDisplay2 56*, RowUnit2 32*, RowDltrUnits Auto (min 48), RowNumPad 272*); solving with the measured number pad
(285.25 epx = 272 × 1.0487) gives an Auto row of 50.2 epx.

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 5.1 | Row model | as above; predicted unit-row centres 147.51 / 239.79 vs measured label cap centres **147.62 / 239.88** | HIGH | 10586 + S1 | C4; S1 UnitConverter.xaml:302–319 | footage and source agree (one device) |
| 5.2 | Number pad | 306.75 → 592.0 epx, #1F1F1F, **5 × 57.05 epx** rows; columns **0.25 : 1 : 1 : 1 : 0.25** of width → centres 77.1 / 180.0 / 282.9 (measured digits 78.25 / 181.0 / 284.0, `CE` 180.1, `⌫` 283.0), margins 25.7 epx | HIGH | 10586 + S1 | C4, C5; S1 UnitConverter.xaml:657–672 | footage and source agree |
| 5.3 | Keys | `· CE ⌫` / `7 8 9` / `4 5 6` / `1 2 3` / `· 0 .` (the first cell of rows 1 and 5 is empty on Speed) | HIGH | 10586 | C4, C5 | read |
| 5.4 | Converter glyphs | digits ink **17.25 epx** (≈24–25 epx), `⌫` 16.5 × 12.5, `CE` ink 11.75 | MEDIUM | 10586 | C4 | |
| 5.5 | Input value | "523": **SemiBold**, digits **24.5 epx** tall (≈34 epx; S1 ValueMediumStyle 34, UnitConverter.xaml:199), left ≈12 epx (ink 14.5 with bearing), bottom-aligned in its 56* row | MEDIUM | 10586 + S1 | C4 y 370–469 | |
| 5.6 | Output value | "325.0062": same size, **Light** weight (S1 FontWeight Light, UnitConverter.xaml:203) | MEDIUM | 10586 + S1 | C4 y 739–838 | the active value is SemiBold, the other Light |
| 5.7 | Unit labels | "Kilometres per hour ⌄", "Miles per hour ⌄" in **accent**, cap **11.25 epx** (≈15-epx Body), chevron **11.5 × 6.25 epx** after the text; left 13.25 | MEDIUM | 10586 | C4 y 568–625, 937–994 | |
| 5.8 | "About equal to" | grey (156) ≈ white 61 %, cap **9.0 epx**, left 12.25, cap top 267.5 | MEDIUM | 10586 | C4 y 1070–1105 | |
| 5.9 | Supplementary results | one line, cap top 285.75: value white semibold (cap 11.0) + unit grey (156); items separated by ≈18 epx; C4 "0.43 M · 145.3 m/s · 282.4 kn · ✈ 0.59 jets", C5 "427.3 M · 145,426 m/s · ✈ 591.5 jets"; the airplane glyph 17.25 × 16 epx | MEDIUM | 10586 | C4, C5 | the item count varies with the value |
| 5.10 | Converter digit grouping | "523,535", "325,338.7" (locale) | MEDIUM | 10586 | C5 | |

## 6. Scientific (structure only)

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 6.1 | Layout | header `≡ SCIENTIFIC` + History; result; an angle row **DEG** (accent) **HYP F-E**; memory row `MC MR M+ M- MS M˅`; two black rows `x² xʸ sin cos tan` / `√ 10ˣ log Exp Mod`; five grey rows `↑ CE C ⌫ ÷` / `π 7 8 9 ×` / `n! 4 5 6 −` / `± 1 2 3 +` / `( ) 0 . =` — **5 columns, 7 key rows** | LOW | ≈10080 | V1 @ 158 | camera, pre-release build; matches S1 CalculatorScientificOperators.xaml:21–44 (7 × 1* rows, 5 × 1* columns) and Calculator.xaml:499–504 (angle row 32*, number pad 276*) |
| 6.2 | Geometry | UNMEASURED — proposed approximation in UNMEASURED-2 | UNMEASURED | — | — | — |

## 7. History, memory and the display fit

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 7.1 | History exists on the phone | yes in Standard (History glyph, 1.7) | HIGH | 10586 | C1, X1 | |
| 7.2 | History pane form | the list covers the number-pad area; each entry right-aligned, expression small grey above the result large white ("778 × 66 =" / "51,348"); a trash glyph at the bottom right | LOW | ≈10080 | V1 @ 123 | camera, pre-release |
| 7.3 | History / memory empty text | "There’s no history yet"; "There’s nothing saved in memory" (U+2019 apostrophe) | MEDIUM | S1 | Resources.resw:2748, 2752 | source-2 |
| 7.4 | Result font fit | an 18-character result is drawn at ≈0.66 × the normal digit height (43 px vs 62–65 px in the same shot) — the text shrinks to fit rather than scrolling or truncating | LOW | ≈10080 | V1 @ 104 vs @ 99, 105 | camera |
| 7.5 | Fit rule | result font **46 epx**, **MinFontSize 12** | MEDIUM | S1 | Calculator.xaml:272–280; App.xaml:129 | source-2 |

## 8. Engine rules the fixtures depend on (source-2)

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 8.1 | Precision | Standard **16**, Scientific **32**, Programmer **64** digits | MEDIUM | S1 | CalculatorManager.h:23–27; CalculatorManager.cpp:167, 185, 202 | |
| 8.2 | Order of operations | **Standard calculates immediately** (2 + 3 × 4 = 20); Scientific and Programmer respect precedence (= 14) | MEDIUM | S1 | CalculatorManager.cpp:161 (`false /* Respect Order of Operations */`), 179, 196 | |
| 8.3 | E-notation switch | when the integer digits exceed the precision (conv.cpp:1048, "prevent user from assuming 33rd digit is exact"), or for a small number with more than 2 zeros after the point that would exceed the precision (conv.cpp:25, 1091–1102) | MEDIUM | S1 | Ratpack/conv.cpp | |
| 8.4 | E-notation format | first digit, the decimal separator always, the remaining significant digits, `e`, an explicit `+` or `-`, the exponent: 200 ! → **7.886578673647905e+374** (Standard), **7.8865786736479050355236321393219e+374** (Scientific); 10^16 → "1.e+16" | MEDIUM | S1 + host | conv.cpp:1188–1213; 200 ! computed with Python decimal at 16 / 32 digits | host-computed from the rule, never from an app |
| 8.5 | Overflow | "Overflow" when the exponent needs more than **4** digits (> 9999) | MEDIUM | S1 | scidisp.cpp:21, 122 | |
| 8.6 | Error strings | "Cannot divide by zero" (id 99), "Invalid input" (100), "Result is undefined" (101), "Not enough memory" (105), "Overflow" (107, 119, 120), **"Result not defined" (108, 118)** | MEDIUM | S1 | CEngineStrings.resw:124–160, 416 | two different "undefined" wordings exist |

## 9. Adapting to the shell's canvas (derived)

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 9.1 | Anchoring | chrome anchored to the top (status + 48-epx header), nav bar to the bottom; every row between is a star row sharing content = height − status − 48 − 48 | HIGH | 10586 | 2.1 on two canvases | the XL's extra 91.4 epx of content went to all rows in proportion |
| 9.2 | S25U (360 × 780 epx) | with a 28-epx drawn status bar, content 656 epx → Standard key row **77.95 epx**, converter key row ≈73.6 epx; with 24 epx, 660 → 78.43 | INFERRED | — | from 2.1 / 5.1 | do not copy the 640-canvas absolute y values |

---

## Strings as shipped

All from 10586 (C, X; en-GB region) unless marked V1 (≈10080, en-IN) or S1 (en-US source).

- Pane: CALCULATOR · Standard · Scientific · Programmer · Date calculation · CONVERTER · Volume · Length · Weight and Mass ·
  Temperature · Energy · Area · Speed · Time · Power · Data · Pressure · Angle (Time → Angle V1) · Settings. S1 spells the
  mode "Date Calculation" (Resources.resw:2868) where the phone shows "Date calculation"; S1 adds Currency
  (Resources.resw:1536), absent on the phone builds seen.
- Titles: STANDARD · SCIENTIFIC (V1) · PROGRAMMER · SPEED (a converter page is titled with its category, in caps).
- Memory row: MC · MR · M+ · M- · MS · M˅ (the last is a drawn path, 2.18).
- Standard keys: % · √ · x² · ¹⁄x · CE · C · ⌫ · ÷ · × · − · + · ± · . · = · 0–9.
- Scientific keys (V1): DEG · HYP · F-E · x² · xʸ · sin · cos · tan · √ · 10ˣ · log · Exp · Mod · ↑ · CE · C · ⌫ · π · n! ·
  ± · ( · ) · . · =.
- Programmer: HEX · DEC · OCT · BIN · QWORD · MS · M˅ · Lsh · Rsh · Or · Xor · Not · And · ↑ · Mod · CE · C · ⌫ · A–F · ( ·
  ) · ± · . (disabled) · operators.
- Converter: "Kilometres per hour" / "Miles per hour" (en-GB; S1 en-US "Kilometers") · "About equal to" · M · m/s · kn ·
  jets.
- Errors (S1): Cannot divide by zero · Invalid input · Result is undefined · Result not defined · Overflow · Not enough
  memory.
- Empty states (S1): There’s no history yet · There’s nothing saved in memory. Actions (S1): Clear all history · Clear
  all memory · Clear memory item · Delete.

## Segoe MDL2 glyphs the shell's icon font needs

Names and codepoints from S2; "used at" is the S1 element and the measured phone glyph. S1 draws the calculator
operators from its private font "Calculator MDL2 Assets" (CalcMDL2.ttf, App.xaml:106, SymbolOperatorButtonStyle
App.xaml:250–254) at codepoints that coincide with the Segoe MDL2 names below.

| Glyph | Name | Codepoint | Used at | Conf |
|---|---|---|---|---|
| ≡ | GlobalNavigationButton | U+E700 | header 1.3 | MEDIUM (codepoint S2; glyph measured) |
| History | History | U+E81C | header 1.7 (S1 Calculator.xaml:668) | MEDIUM |
| ⌫ (keypad) | CalculatorBackspace | U+E94F | 2.12, 4.12 (S1 CalculatorStandardOperators.xaml:387) | MEDIUM |
| ⌫ (converter) | BackSpaceQWERTY | U+E750 | 5.4 (S1 UnitConverter.xaml:690) | MEDIUM |
| ÷ × − + = | CalculatorDivide / CalculatorMultiply / CalculatorSubtract / CalculatorAddition / CalculatorEqualTo | U+E94A / U+E947 / U+E949 / U+E948 / U+E94E | 2.11 (S1 CalculatorStandardOperators.xaml:323–351) | MEDIUM |
| % √ ± | CalculatorPercentage / CalculatorSquareroot / CalculatorNegate | U+E94C / U+E94B / U+E94D | 2.13, 2.8 (S1 :269, :276, :406) | MEDIUM |
| x², ¹⁄x, xʸ, x³, 10ˣ, ʸ√x, eˣ, π | not in Segoe MDL2 — private CalcMDL2 codepoints U+F7C8, U+F7C9, U+F7CA, U+F7CB, U+F7CC, U+F7CD, U+F7CE, U+F7CF | — | 2.13, 6.1 (S1 CalculatorScientificOperators.xaml:363–695) | MEDIUM (source); the shell has no font for them (gap) |
| ↑ (shift) | UpArrowShiftKey | U+E752 | 4.12, 6.1 (S1 CalculatorProgrammerRadixOperators.xaml:207) | MEDIUM |
| full keypad | Dialpad | U+E75F | 4.8 (S1 CalculatorProgrammerDisplayPanel.xaml:62) | MEDIUM |
| bit toggle | not in Segoe MDL2 (U+F7D0, private) | — | 4.8 (S1 :68) | MEDIUM (source) |
| gear | Setting | U+E713 | 3.12 | MEDIUM |
| ⌄ | ChevronDown | U+E70D | 5.7 | MEDIUM (codepoint S2; S1 ComboBox uses U+E0E5, a legacy symbol) |
| ✈ | Airplane | U+E709 | 5.9 | MEDIUM |
| trash | Delete | U+E74D | 7.2 (S1 HistoryList.xaml:161, Memory.xaml:128) | MEDIUM |

## Motion

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| M.1 | Hamburger pane open | slides in from the left; first moving frame 37.533 s, settled by ≈37.700 s → **≈167 ± 50 ms** | LOW | ≈10080 | V1 @ 37.5–37.8 s, 30 fps (scratch strip) | camera with display ghosting; agrees in form with R7 §3.1.10 (Cortana pane, ease-out ≈250 ms) and R3 C5 (Weather pane 133 ms) |
| M.2 | Key press feedback | UNMEASURED | UNMEASURED | — | — | see UNMEASURED-5 |
| M.3 | Mode switch, converter category switch, history open, result font step, value swap | UNMEASURED | UNMEASURED | — | — | no ≥ 55 fps source |

## UNMEASURED

| # | What | Why not | Proposed tagged approximation (and the measured pattern it derives from) |
|---|---|---|---|
| 1 | **The governing build (14393 / 15063 / 15254).** Every native value is 10586. | no later phone capture (0.3) | Build the 10586 layout. It is the only measured one, and its row weights equal the 2019 source's, so the lineage kept the grid from 10586 to 2019. **Do not** import the 2019 desktop colours (operator column fill `AppBackgroundAltMediumLowBrush`, accent-hover `=`), which the 10586 phone does not show (2.6). |
| 2 | **Scientific geometry.** | camera structure only (6.1) | The 2.1 star model with the S1 Scientific state: expression 20*, result 72*, angle row 32*, memory 32*, number pad 276* in 7 equal rows; 5 × W/5 columns; rows 1–2 of the pad on black under the 1-epx #191919 rule, rows 3–7 #1F1F1F; glyph sizes as Standard (2.10–2.13). On 360×640 this gives expression 24.1, result 86.7, angle and memory rows 38.5 each, key rows 47.5 epx. DEG / HYP / F-E as memory-row-style labels on a W/5 grid with the active angle unit in accent. |
| 3 | **History pane and memory flyout layout** (entry pitch, type sizes, trash button). | V1 only (LOW) | History covers the number pad (7.2) with entries right-aligned at the result's 16-epx right inset: expression in the memory-row grey (2.17) at 15 epx, result in white semibold at 24 epx, entry pitch from the phase's 44-epx list-row unit doubled for two lines (R3 A15 two-line pitch 64 epx); trash glyph (Delete) in a 48-epx app-bar cell (R7 §3.5.8). Memory flyout on the same pattern. |
| 4 | **Date calculation mode** (screens, strings). | no capture | Out of phase scope unless ruled in (gap 3). |
| 5 | **Pressed-key state and timing.** | no capture of a press | S1: instant (VisualTransition GeneratedDuration 0, App.xaml:158) change to the pressed brush #30FFFFFF over the key (App.xaml:29) → #494949 on #1F1F1F, back on release. Alternative pattern: the W10M Phone dial pad fills the whole key cell with accent for one frame (R7 §1.2.4). Pick one under H11. |
| 6 | **± on the converter pad** for negative values (Temperature). | Speed has no ± (5.3) | S1 places converterNegateButton in the empty first cell of the bottom row (UnitConverter.xaml:712) and shows it for categories that allow negatives. |
| 7 | **Light theme.** | every native source is dark | Mirror the dark values with the UWP light brushes. S1 App.xaml:50–61 gives the light-theme pane #E0E0E0 and pressed #30000000. |
| 8 | **Scientific-mode key row 1 indicator dots** (small marks under x², xʸ, sin, cos, tan) and HYP / ↑ second functions. | V1 too blurred | The Programmer shift arrow pattern (4.11): a 5 × 6-epx grey mark centred under the key label, 13 epx below its centre. |

## Gaps for the phase doc

Phase-15 lines that defer to this file, with what R11 found:

1. **E13 geometry now has numbers:** header 1.2–1.7; star rows 2.1–2.3 (rows scale with height — assert fractions of the
   content height, not absolute y); columns 2.7 / 4.7 / 5.2; fills 2.4–2.6; pane 3.1–3.12. Tolerances: ±0.5 epx for fixed-
   epx values (JPEG ±0.25), ±1 epx for star-row edges.
2. **E11 "200 ! … (from r11/calculator.md: e-notation threshold)":** Standard shows 16 significant digits and switches to
   e-notation once the integer part exceeds 16 digits: 200 ! → `7.886578673647905e+374`; Scientific
   `7.8865786736479050355236321393219e+374` (8.3–8.4). Overflow above an exponent of 9999 (8.5), consistent with the phase's
   `10 xʸ 10000 → Overflow`. These are source-2 rules with host-computed strings. They need an [accept] or [fidelity] row
   because no W10M footage shows an e-notation result.
3. **Standard is immediate-execution** (8.2). E11 cases and Tess's arithmetic (Q5, T15-2) must say which mode's rules they
   follow: `2 + 3 × 4` is 20 in Standard and 14 in Scientific. The phase does not say today.
4. **Converter categories (Q4):** W10M's list is exactly the phase's twelve, in the same order, with no Currency on the
   builds seen (3.9). The label is **"Weight and Mass"**, not "Weight". **"Date calculation"** is a fourth calculator mode on the phone
   (C2, X1) and the phase neither includes nor excludes it — needs a ruling.
5. **History on the phone:** yes, in Standard and Scientific; **not in Programmer**, on the phone (no glyph, C3) or in the
   engine (4.15). E12's history rows should not expect Programmer entries. The history layout is UNMEASURED-3 (H11).
6. **Display font step-down (edge cases, H11):** the result shrinks to fit (LOW footage, 7.4) from 46 epx to a 12-epx floor
   (source-2, 7.5). This replaces "approximation under H11" with a sourced rule; it still needs H11 [accept] for the
   phone.
7. **Error strings:** the phase's four match source-2 exactly. The engine also has "Result not defined" (ids 108 / 118)
   and "Not enough memory" (105); add both to calc-cases.tsv, or rule them out.
8. **Status bar 24 epx vs 28 epx:** every Calculator capture (and R8's Groove) has a 24-epx status bar; phase 15 draws
   28 epx (R3 C4 / `SystemBars.STATUS_EPX`). This is not changed here; it needs a ruling (E10 / E13 / E23 assert 28).
9. **No accent `=`, no operator fill** on the phone (2.6). Build tasks must not take the desktop source's key colours.
10. **Glyph font:** x², ¹⁄x, xʸ, x³, 10ˣ, eˣ, ʸ√x, π and the bit-toggle icon are not in Segoe MDL2. The shell's icon font
    cannot draw them: render them as Selawik text (italic x with superscripts) or ship outlines, with an [accept] row.
11. **Governing build:** everything here is 10586 (UNMEASURED-1). H2 [fidelity] is judged against 10586, stated as such.

## Tally

| HIGH | MEDIUM | LOW | UNMEASURED | Rows |
|---|---|---|---|---|
| 41 | 45 | 6 | 11 | 103 |

Counted by parsing the Conf column of every table row in §1–§9, Segoe MDL2 glyphs, Motion and UNMEASURED (a row with two
levels counts at the lower; INFERRED rows are not counted; UNMEASURED-table rows count as UNMEASURED).
