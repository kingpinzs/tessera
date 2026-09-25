# Brief — phase 15, the Calculator APP (build task 6's screens, and its shares of tasks 1, 8, 9)

You are building the **Calculator app's screens** for phase 15 of a Windows-10-Mobile-style Android shell ("Tessera",
package `app.tileshell`: Kotlin, Jetpack Compose foundation only — no Material — one sideloaded APK, minSdk 34,
targetSdk 36). The ENGINE, the CONVERTER and DATE CALCULATION already exist in the `:calc` module, are ported from
Windows Calculator's own source, and pass an independent 312-case oracle. You build the app a person sees and presses.

## Where you work
- Your git worktree: `/home/jeremyking/projects/metro-launcher-p15-calc`, branch `phase-15-calc` (at 7ecb351). Work and
  commit ONLY there, logically (several commits, never one squash), each message in a file committed with
  `git commit -F <file>`, ending with these two lines:
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01Wnw3DiCvZpK4eDLHrsVq8D`
  Never push. Never touch `/home/jeremyking/projects/metro-launcher` or `/home/jeremyking/projects/metro-launcher-p15`.
- Build `./gradlew :app:assembleDebug`; tests `./gradlew :app:testDebugUnitTest :calc:testDebugUnitTest` (JUnit 4, host
  JVM). Read exit codes from a file, never through a pipe.
- NO device work: never run `adb`, never start or touch an emulator. The lead installs and verifies after merging.
- Do not edit `docs/plan/INDEX.md`, `docs/plan/qa/phase-15/STATE.md`, any phase doc, or anything under `calc/` except to
  add a missing public accessor the UI needs (then say so). The engine's answers are settled; the UI never computes.

## The spec (read in full before coding)
- `docs/plan/phase-15-inbox-clock-calculator-recorder.md`: Scope's Calculator bullet; Decisions "Calculator engine"
  (display precision, e-notation, history, error strings), T15-15 (the R11 values: "Weight and Mass", no Programmer
  history, no accent `=` and no operator fill, x² ¹⁄x xʸ x³ 10ˣ eˣ ʸ√x π set as Selawik text), T15-16 (Date calculation),
  T15-42 (the History glyph in Standard AND Scientific), T15-43 (the pane's Settings → an About page, no Feedback; the
  `converter` shortcut opens the last-used category, Volume first), T15-32 (motion: the hamburger pane's ≈167 ± 50 ms
  slide logged `[motion] calc_pane`), "Harness contracts" (EVERY tag and diagnostics line — exact names), "Bars"; build
  tasks 6 and 9; rows E1, E11, E12, E13, E27, E29 and the Calculator edge cases (they say what must be observable).
- Measurements: `docs/plan/r11/calculator.md` — §1 header, §2 Standard (row model 20* / 72* / 32* / 308*, keys, glyph
  sizes, grouping), §3 the pane (256 epx, rows, order incl. Date calculation and the CONVERTER group, Settings row),
  §4 Programmer (radix rows, tab/memory row with the word-size button in accent, bit-toggle icon, keys), §5 the
  converter pages, §6 Scientific (layout LOW, geometry UNMEASURED-2 — use its proposed approximation), §7 motion,
  §8 engine facts, the UNMEASURED / gap rows. Geometry is asserted as FRACTIONS of the content height (star rows scale).
- The key vocabulary and how rows drive keys: `docs/plan/qa/phase-15/calc-keys.md`. Every key button carries the tag
  `calc_key:<name>` with EXACTLY that name, so E11's driver can press any case of `docs/plan/qa/phase-15/calc-cases.tsv`.

## Code to reuse — read before writing (house style)
- The engine and friends (read their public APIs): `calc/src/main/kotlin/app/tileshell/calc/engine/Calculator.kt`
  (`Calculator(mode)`, `press(key)`, `displayText`, `expression`, `isEnabled(key)`, `radixValues`, memory, history,
  `encodeMemory`/`restoreMemory`/`encodeHistory`/`restoreHistory`, `paste(text)`, `errorKind`), `CalcExpression.kt`
  (Tess's, not yours), `calc/.../convert/ConverterState.kt` (`selectCategory`, `selectUnit1/2`, `press`, `fromText`,
  `toText`, `supplementaryResults`, `saveUserPreferences` — persist it for the last-used category), `ConverterCategory`,
  `UnitTables`, `calc/.../date/DateCalculatorState.kt` (difference and add / subtract; the single-line case is in
  `strDateDiffResult`). `calc/src/test/.../CalcCasesOracleTest.kt` and `CalcCasesConverterDateTest.kt` show how the
  oracle drives them.
- The in-APK app template: `music/MusicActivity.kt` (`hideSystemBars()`, `ShellRoot`, `testTagsAsResourceId`, drawn bars,
  Back / Windows), `music/MusicCollectionPage.kt` (lists, flyouts), `recorder/RecorderPages.kt` (a recent in-APK app
  built the same way), `bars/SystemBars.kt` (`BarMetrics.STATUS_EPX` / `NAV_EPX` — never the literal), `ui/tokens/*`
  (`ShellType`; 1.dp = 1 epx), `brand/Brand.kt` (`Brand.uiFont` for the math glyphs), `brand/Glyph.kt`,
  `settings/AboutPage.kt`, `ui/MotionClock.kt` (EVERY animation you run: `MotionClock.animate(name, ms, easing)`).

## What to build (permanent form — no stubs, no TODOs)
1. `app.tileshell.calculator.CalculatorActivity` — label "Calculator", its own `taskAffinity` `app.tileshell.calculator`,
   launcher, `testTagsAsResourceId`, drawn W10M bars. Manifest entry with
   `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts_calculator"/>` (task 9: ids
   `standard`, `scientific`, `programmer`, `converter`, ranks 0–3, each an intent with the extra `page` =
   `standard` / `scientific` / `programmer` / `converter`). The activity reads `page`.
2. The header (`≡`, the mode title in semibold caps, the History glyph in Standard and Scientific only) and the hamburger
   pane (§3: Standard, Scientific, Programmer, Date calculation, the CONVERTER group with exactly the twelve categories in
   order — Volume, Length, Weight and Mass, Temperature, Energy, Area, Speed, Time, Power, Data, Pressure, Angle — no
   Currency anywhere — then Settings → an About page `about_page` with no Feedback). Tags `calc_mode:<standard |
   scientific | programmer | date | converter>` (selected="true" on the page showing), `calc_converter_category:<n>`.
   Switching modes keeps the display value (the engine's `setMode`).
3. Standard, Scientific and Programmer pages to r11's geometry, driving ONE `Calculator` instance per the app (switching
   modes on it). Display `calc_display` (the engine's `displayText`, shrinking from 46 to a 12-epx floor to fit —
   r11 7.5), expression `calc_expr`, keys `calc_key:<name>` (disabled keys drawn per r11 4.14), memory row, the history
   pane (`calc_history:<n>`, Standard and Scientific only, Clear history) and the memory flyout. A hold on
   `calc_display` offers Paste (`calc_paste`) → `paste(clipboard text)`. Memory and history persist across process death
   (the engine's encode / decode, stored in the app's files dir). Programmer: the four radix rows (tap one = its
   `radix_*` key), the word-size button (`calc_key:word`), the bit-toggle keypad tab, keys per r11 4.11–4.12.
4. The Converter: one page per category (r11 §5), from / to values and unit pickers, the digit keypad
   (`0-9`, `decimal`, `backspace`, `clear`, `negate` where allowed), "About equal to" suggestions (fit what fits),
   last-used category persisted (`saveUserPreferences`).
5. Date calculation (T15-16): `calc_date_op:<difference | add | subtract>`, `calc_date_from`, `calc_date_to`,
   `calc_date_amount` (years / months / days pickers — Windows' add / subtract has no weeks input), `calc_date_result`
   (the one or two result lines). Log `[calc] date <op> <from> <to|amount> -> <result>` per computation.
6. Diagnostics: `[calc] engine port microsoft/calculator@4fd3fc5` once per process, `[calc] error <kind>` on each error
   (the engine's `errorKind`), and the `[motion] calc_pane …` line of the pane's slide. The keys and header draw the
   10586 phone's colours: flat #1F1F1F rows, row 1 on black, no key borders, no accent `=`, no operator fill.
7. The Calculator Start tile is a static glyph tile — nothing to publish.

## Tests (host JVM)
Whatever pure mapping you write: key layout per mode (every key of calc-keys.md's mode present, in r11's order), the
display-shrink rule, the pane's order, the shortcut page mapping. Also add ONE test that loads calc-cases.tsv and, for
every standard / scientific / programmer line, checks that each key it uses is on your layout for that mode (so E11's
driver can press every case). Quote the gradle result.

## Report back (final message, under 80 lines)
Commits (hash + subject); files; the test result quoted; the exact tags and diagnostics lines; every design call the
doc left open (especially Scientific's UNMEASURED geometry and the converter's "About equal to" fitting); anything not
built and why; the INDEX Change Log facts for the lead.
