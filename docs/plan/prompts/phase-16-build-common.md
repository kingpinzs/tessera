# Phase 16 build — rules for both app builders (Calendar, People)

You are an implementer on a sub-branch of phase 16 of metro-launcher (a Windows 10 Mobile style launcher for a Galaxy
S25 Ultra: one sideloaded APK `app.tileshell`, Kotlin + Jetpack Compose, no root). A lead session owns the phase, the
INDEX, the phase doc and the QA gate. You build your app's part and hand it back as commits on your branch plus a report.

## Where you work
- Your worktree and branch are named in your own brief. Work ONLY there. Never edit `~/projects/metro-launcher`, the
  lead's tree `~/projects/metro-launcher-p16` (outside your own worktree under its `.claude/worktrees/`), or the other
  builder's worktree. The files git ignores (local.properties, keystore.properties, app/libs, the keyboard and speech
  assets, five licence files) are already hard-linked into your worktree.
- First commands: `git log --oneline -1` (must show the commit your brief names, or a later one on `phase-16`), then the
  baseline `./gradlew :app:testDebugUnitTest` — 976 tests, 0 failures at 9f137626.

## What governs
- `docs/plan/phase-16-inbox-calendar-people.md` is FINAL and is the spec. Read it fully before building: Goal, Scope,
  Decisions (every line that names your app, plus "Harness contracts", "Harness, round 3", "Trust", "Verify at build
  start"), Build tasks, the Acceptance preamble and every row that touches your app, Edge cases. The rows are how your
  work will be judged: every tag, every diagnostics line and every notice text the rows read must exist exactly as written.
- `docs/plan/qa/phase-16/BUILDSTART/README.md`: five Android facts checked on the AVD on 2026-10-01. Item 5 re-cut the
  29 February birthday rule (the doc is already annotated).
- Do not re-ask or re-decide anything the doc settles. If a clause of the doc is wrong or cannot be built as written,
  STOP that item, build the rest, and say so in your report with the evidence. Never improvise around a FINAL clause and
  never edit the phase doc, `docs/plan/INDEX.md`, `.claude-build-state.md` or `docs/plan/build-prompt.md` — the lead
  owns them. Where your work is an ADD to another phase's part (the doc says "Change Log when built"), list it in your
  report so the lead writes the Change Log line.

## Hard rules (the owner's; they bind you)
1. Verify by running. Quote real output. Never "this should work". Read exit codes from captured files, never through a pipe.
2. Root-cause at the producer; no workarounds or fallbacks at the consumer.
3. Your scope is your brief's, literally. No adjacent refactors, no "while I'm here". A defect you find in another
   phase's part or in the other builder's part: do not fix it; report it with a minimal repro.
4. Phases build the final form of their part. Nothing interim, no TODO stubs left behind, no mock data.
5. Reuse what exists (see "Reuse" in your brief). Do not build a second copy of a widget, a store or a helper the
   shell already has where its measured values fit.
6. Keep every evidence file and earlier run (rename, never delete). Timestamps you write come from `date`.
7. Kill a process only by its recorded pid; never a pattern kill.
8. Models: you are Opus. Do not start subagents of any kind.
9. Never touch the host's audio (no pactl, no audio scripts). No microphone anywhere.
10. Never push. Never create the push flag. Commit only on your own branch.

## Commits
- Logical commits on your branch as each sub-part is finished and verified, so progress survives a lost session. Never
  squash. Never `git add -A` or `git add .` (a hook blocks them): add explicit paths.
- A hook blocks any commit command that contains a backtick anywhere: write the message to a file with your file-writing
  tool and run `git commit -F <file>` as its own command (no backticks, no heredoc with backticks in that command).
- Run the WHOLE unit suite (`./gradlew :app:testDebugUnitTest`, rc captured to a file) before every commit that touches
  app code. A narrower run missed a broken test once.
- No person's name in code or commit messages (write "the owner"). End every commit message with exactly:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- Never bypass a hook.

## The code base, briefly
- `app/src/main/kotlin/app/tileshell/`: `ShellApp.kt` (process start, `startFeeds`), `diag/Diagnostics.kt`
  (`Diagnostics.add(tag, message)` → the in-memory ring the QA rows read; a line is `[tag] message`), `ui/ShellTheme.kt`
  (`ShellRoot`, `LocalShellColors`), `ui/tokens/` (`ShellType` type ramp, `Palette`, `CapMetrics` — cap-height
  positioning, `ShellDensity`: 1 dp = 1 epx on a 360-epx canvas, so write measured epx values as dp), `ui/MotionClock.kt`
  (every animation the rows time goes through `MotionClock.animate`, which writes the `[motion] <name> …` line),
  `ui/components/` (`OutlinedField` — the editor text box both apps use; `ModalOverlay.kt` — `OverlayLayer`,
  `modalOverlay`, `overlayItem`, `dismissOverlay`: every flyout, pane and menu uses these, they hold ledger fixes
  L13-3..L13-20 about touches reaching what an overlay covers), `bars/SystemBars.kt` (`W10mStatusBar`, `W10mNavBar`,
  `BarMetrics.STATUS_EPX` / `NAV_EPX` — never write the bar heights as literals), `brand/Glyph.kt` (icon font code
  points; add what you need from `res/font/fluent_icons.ttf`), `settings/SettingsWidgets.kt`, `clock/ClockWidgets.kt` and
  `clock/ClockTabs.kt` (phase 15's app bar, flyout, checkbox, press box, pivot/tab header, empty line),
  `calculator/DatePage.kt` + `CalculatorScreen.kt` (a drawn date picker panel), `cortana/ui/ReminderDetailPage.kt`
  (drawn date and time pickers).
- Your app's activity, navigation holder and intent rules already exist (build task 2): `<app>/…Activity.kt`,
  `…App.kt` (the frame: status bar, content, nav bar; `…Nav.route` / `routeToken` carry what the last intent asked for,
  `…Nav.back()` is where Back unwinds your pages), `…Route.kt` (`…Intents.route`, unit-tested). Build your pages inside
  that frame; extend the Nav class; keep the intent rules as they are unless the doc demands more.
- `testTagsAsResourceId` is already on each window root. Every node a row reads needs its `Modifier.testTag("<tag>")`
  exactly as the doc spells it; a "selected" page or tab tag carries `selected = true` in semantics (see how
  `clock/ClockTabs.kt` marks the selected tab).
- Stores: the shell writes its own JSON files in `filesDir` with temp-file-and-rename (see `tiles/LayoutStore.kt`).
- JVM tests live in `app/src/test/kotlin/app/tileshell/<package>/`. Pure rules (guards, parsers, date maths) are
  written free of Android types so they are unit-tested; that is this project's pattern, follow it.

## The device (shared — read carefully)
- One emulator, `ANDROID_SERIAL=emulator-5554` (AVD tileshell_fhd, AOSP, 1080x2340 @ 450 dpi = 360 epx wide), shared
  by you, the other builder and the lead. Never start, stop, reboot or wipe it. If it is down, stop device work and
  say so in your report; keep building.
- Never `pm clear app.tileshell`, never `adb uninstall app.tileshell`, never `adb reboot`.
- The other builder installs a DIFFERENT build of the same package. So every device session of yours is ONE bash
  script that:
  1. sources `docs/plan/qa/phase-03/scripts/lib.sh` through a symlink in your scripts folder (see below) and calls
     `take_device_lock` FIRST. If it prints "another QA driver is already driving the device" (exit 3), wait 30–90 s and
     run the script again; never remove or bypass the lock.
  2. installs your APK when the device holds another build: `[ "$(apk_matches | cut -c1-3)" = "yes" ] || adb install -r "$APK"`
     (never with `-g`; grant a permission you need with `pm grant` and say so).
  3. drives the app and asserts with the library's helpers (`row_begin <ID> "<what>"` after the install, `assert_eq`,
     `assert_contains`, `assert_absent`, `record`, `ring_mark` / `ring_since`, `dump_ui`, `has_node`, `node_text`,
     `bounds`, `tap_node`, `ensure_start`, `row_end`). Start pages that never idle are dumped with `gdump`
     (`docs/plan/qa/phase-15/scripts/p15.sh:24-49`).
  4. restores everything it changed before it exits: fixtures it inserted (delete by id), permissions it revoked
     (`pm grant`, asserted), the clock and time zone, the Start layout (never leave a pin or a re-pointed slot).
  Keep a session under about five minutes so the others get the device.
- A command whose URI carries `&`, or whose where-clause carries a quoted literal, goes to `adb shell` as ONE quoted
  string (`adb shell "content query --uri … --where \"title='x'\""`); passed as separate words the device shell eats
  the `&` or the quotes and nothing is written or matched.
- Calendar facts (BUILDSTART): calendars made through the sync-adapter URI under a non-LOCAL account type are DROPPED
  when the calendar provider's process restarts, and calendar `_id`s are reused after a delete.
- Your development scripts and their outputs go under `docs/plan/qa/phase-16/dev-<your app>/` (scripts in
  `…/scripts/`, with `lib.sh` and `within.py` symlinked as `docs/plan/qa/phase-16/scripts/` has them). These are
  development proof, not the gate: the lead writes and runs the gate's rows (E1–E28, EDGE) from the doc.
- Typing into a field: `adb shell input text` goes through the shell's own keyboard (`app.tileshell/.ime.KeyboardService`
  is the selected input method). Tess requests in a row are typed with `type_request` and confirmed by a tap on
  `cortana_card_button:confirm`.

## Your report (your final message — the lead reads only that)
Plain text, no file. In this order:
1. Commits on your branch (`git log --oneline phase-16..HEAD`).
2. What is built, per build-task clause of your brief, each with its proof: the test class and its count, or the
   device script, its output path and its PASS / FAIL totals. Quote the real lines.
3. The whole-suite result (count, failures, the output path).
4. Every clause you did NOT build or could not prove, with why. An honest "not done" is required; never round up.
5. ADDs to other phases' parts (file, what changed) for the Change Log.
6. Deviations from the doc, doc clauses you found wrong, and defects found outside your scope (with repro).
7. Device state you left (what is installed, anything not restored).
