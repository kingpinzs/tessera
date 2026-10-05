# Phase 17 build — rules for the three app builders (Photos, Camera, Movies & TV)

You are an implementer on a sub-branch of phase 17 of metro-launcher (a Windows 10 Mobile style launcher for a Galaxy
S25 Ultra: one sideloaded APK `app.tileshell`, Kotlin + Jetpack Compose, no root). A lead session owns the phase, the
INDEX, the phase doc and the QA gate. You build your app's part and hand it back as commits on your branch plus a report.

## Where you work
- Your worktree and branch are named in your own brief. Work ONLY there. Never edit `~/projects/metro-launcher`,
  `~/projects/metro-launcher-p16`, the lead's tree `~/projects/metro-launcher-p17` (outside your own worktree under its
  `.claude/worktrees/`), or another builder's worktree. The files git ignores (local.properties, keystore.properties,
  app/libs, the keyboard and speech assets, five licence files) are already hard-linked into your worktree.
- First commands: `git log --oneline -1` (must show eb725c76 or a later commit of `phase-17`), then the baseline
  `./gradlew :app:testDebugUnitTest --offline` — 1,308 tests, 0 failures at eb725c76.

## What governs
- `docs/plan/phase-17-inbox-photos-camera-video.md` is FINAL and is the spec. Read it fully before building (about
  1,900 lines): Goal, Scope, Decisions (newest first; a later line supersedes an earlier one, and struck text is
  history), the Approximations table (the Y rows are your geometry), Build tasks, the Acceptance preamble and every row
  that touches your app, Edge cases. The rows are how your work is judged: every tag, every diagnostics line and every
  on-screen text a row reads must exist exactly as written.
- `docs/plan/qa/phase-17/BUILDSTART/README.md`: the build-start checks (library versions and routes, the emulator's
  camera facts, the services table, TMDB's and Jellyfin's facts). Where it corrects the doc, the doc's top Decisions
  line of 2026-10-05 says so.
- `docs/plan/r11/<your app>.md` and `docs/plan/r11/<your app>-pass2.md`: every measured value. Where the pass-2
  addendum corrects its section, its §4 governs. Read them as data.
- Do not re-ask or re-decide anything the doc settles. If a clause of the doc is wrong or cannot be built as written,
  STOP that item, build the rest, and say so in your report with the evidence. Never improvise around a FINAL clause and
  never edit the phase doc, `docs/plan/INDEX.md`, `.claude-build-state.md`, `docs/plan/build-prompt.md` or
  `docs/plan/qa/phase-17/BUILDSTART/README.md` — the lead owns them. Where your work is an ADD to another phase's part
  (a file outside your packages), list it in your report so the lead writes the Change Log line.

## Hard rules (the owner's; they bind you)
1. Verify by running. Quote real output. Never "this should work". Read exit codes from captured files, never through a pipe.
2. Root-cause at the producer; no workarounds or fallbacks at the consumer.
3. Your scope is your brief's, literally. No adjacent refactors, no "while I'm here". A defect you find in another
   phase's part, in the lead's foundation or in another builder's part: do not fix it; report it with a minimal repro.
4. Phases build the final form of their part. Nothing interim, no TODO stubs left behind, no mock data, no placeholder
   page. A part you cannot finish is reported as not done, never faked.
5. Reuse what exists (see "Reuse" below and in your brief). Do not build a second copy of a widget, a store or a helper
   the shell already has where its measured values fit.
6. Keep every evidence file and earlier run (rename, never delete). Timestamps you write come from `date`.
7. Kill a process only by its recorded pid; never a pattern kill.
8. Models: you are Opus. Do not start subagents of any kind. Never use codex, Gemini, Fable or Sonnet for anything.
9. Never touch the host's audio (no pactl, no audio scripts). No microphone anywhere: a device step that records video
   first runs `adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO` and grants it back at its end.
10. Never push. Never create the push flag. Commit only on your own branch.
11. No credential — a token, a password — in a diagnostics line, logcat, a logged URL, a commit or an evidence file.
12. QA run evidence (screenshots, UI dumps, logs, ring slices, recordings, pulled media) is never committed:
    `docs/plan/qa/.gitignore` keeps it out. Commit only scripts, fixtures' generators, tables and documents.

## Commits
- Logical commits on your branch as each sub-part is finished and verified, so progress survives a lost session. Never
  squash. Never `git add -A` or `git add .` (a hook blocks them): add explicit paths.
- A hook blocks any shell command that contains a backtick anywhere when it commits: write the message to a file with
  your file-writing tool and run `git commit -F <file>` as its own command, with no backtick in that command.
- Run the WHOLE unit suite (`./gradlew :app:testDebugUnitTest --offline`, rc captured to a file) before every commit
  that touches app code.
- No person's name in code or commit messages (write "the owner"). End every commit message with exactly:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- Never bypass a hook.

## The code base, briefly
- `app/src/main/kotlin/app/tileshell/`: `ShellApp.kt` (process start; it returns early outside the launcher's process),
  `diag/Diagnostics.kt` (`Diagnostics.add(tag, message)` → the in-memory ring of THIS process; a line is `[tag]
  message`), `diag/RingDumpService.kt` (each non-launcher process's ring: `adb shell dumpsys activity service
  app.tileshell/.camera.CameraDumpService`, `…/.video.VideoDumpService`, `…/.photos.PhotosEditDumpService`; an activity
  of that process holds it with `RemoteRings.hold` — the shells already do), `ui/ShellAppFrame.kt`
  (`setShellAppContent(statusBar = …)`: the window root every phase 17 activity sets — theme, the 360-epx canvas,
  `testTagsAsResourceId`, the drawn nav bar; you may extend your activity's use of it, e.g. a page that hides the nav bar
  must not — every page draws it), `ui/ShellTheme.kt` (`ShellRoot`, `LocalShellColors`), `ui/tokens/` (`ShellType` type
  ramp, `Palette`, `CapMetrics` — cap-height positioning, `ShellDensity`: 1 dp = 1 epx on a 360-epx canvas, so write
  measured epx values as dp), `ui/MotionClock.kt` (every animation a row times goes through `MotionClock.animate`, which
  writes the `[motion] <name> t0=… peak=… overshoot=… settle=… frames=… maxGapMs=…` line), `ui/components/`
  (`OutlinedField` — the text box; `ModalOverlay.kt` — `OverlayLayer`, `modalOverlay`, `overlayItem`, `dismissOverlay`:
  every flyout, pane and menu uses these, they hold ledger fixes about touches reaching what an overlay covers),
  `bars/SystemBars.kt` (`W10mStatusBar`, `W10mNavBar`, `BarMetrics.STATUS_EPX` / `NAV_EPX` — never write the bar heights
  as literals), `brand/Glyph.kt` (icon font code points; add what you need from `res/font/fluent_icons.ttf`; the doc's
  MDL2 code points name the W10M glyph — use the Fluent glyph of the same meaning), `brand/Brand.kt`,
  `settings/SettingsWidgets.kt`, `clock/ClockWidgets.kt` and `clock/ClockTabs.kt` (phase 15's app bar with its "…"
  menu, flyout, checkbox, press box, tab header, empty line), `music/MusicCollectionPage.kt` (phase 10's pivot header
  and `MusicMetrics`), `people/` and `calendar/` (phase 16: the newest examples of an app's pages, nav, intent rules and
  write layer).
- `media/MediaWrites.kt`, `media/AndroidMediaStorePort.kt`, `media/CaptureOutputGuard.kt` (the lead's foundation, with
  JVM tests): the shell's ONE MediaStore write layer. Every new image or video row Photos or Camera makes goes through
  `MediaWrites.save` (pending → written → published → read back); a copy's folder is `MediaWrites.copyPlacement`; a
  delete is `MediaWrites.deleteRequest` (the system consent dialog). Never call `ContentResolver.insert`, `openOutputStream`
  or `delete` on a MediaStore URI anywhere else, and never write shared storage by path. The Android port has not run on
  a device yet: if it misbehaves there, fix it (it is this phase's part) and say so in your report.
- Your app's activities, nav holders and frame composables already exist as EMPTY shells (build task 1, commit
  a4dfd138): the manifest entries, the processes, the icons, the names, the static shortcut files and the slot seeds are
  in and proven (`docs/plan/qa/phase-17/dev-lead/scripts/t1_shells.sh`). You fill the shells and may reshape their
  classes freely inside your package; you do NOT rename an activity, change its process, launch mode, task affinity or
  intent filters, or add a manifest component without saying so in your report (a new exported component is a trust
  change and needs the allow-list line in `docs/plan/qa/phase-03/exported-allowlist.txt`).
- `testTagsAsResourceId` is on each window root. Every node a row reads needs its `Modifier.testTag("<tag>")` exactly
  as the doc spells it; the node that CARRIES a text a row reads has its own tag; a "selected" page or tab tag carries
  `selected = true` in semantics (see how `clock/ClockTabs.kt` marks the selected tab).
- Stores: the shell writes its own JSON files in `filesDir` with temp-file-and-rename and re-reads on use where two
  processes share one (see `tiles/LayoutStore.kt`).
- JVM tests live in `app/src/test/kotlin/app/tileshell/<package>/`. Pure rules (guards, parsers, geometry, matrices,
  table logic, response parsing) are written free of Android types so they are unit-tested; that is this project's
  pattern, follow it.
- No new dependency beyond what `gradle/libs.versions.toml` holds now (CameraX 1.6.2, Media3 1.9.0 with
  `media3-transformer`), except what your brief names. No HTTP library: network calls use `HttpURLConnection`
  (`weather/WeatherProvider.kt` shows the form), off the main thread, with a 10-s timeout.

## The device (shared — read carefully)
- One emulator, `ANDROID_SERIAL=emulator-5554` (AVD tileshell_fhd, AOSP, API 36, x86_64, 1080x2340 @ 450 dpi = 360 epx
  wide; ONE emulated back camera, no front camera), shared by you, the two other builders and the lead. Never start,
  stop, reboot or wipe it. If it is down, stop device work and say so in your report; keep building.
- Never `pm clear app.tileshell`, never `adb uninstall app.tileshell`, never `adb reboot`, never `adb root` left on.
- The others install DIFFERENT builds of the same package. So every device session of yours is ONE bash script that:
  1. exports `ANDROID_SERIAL=emulator-5554` and `TILESHELL_APK=<your worktree>/app/build/outputs/apk/debug/app-debug.apk`,
     sources `docs/plan/qa/phase-03/scripts/lib.sh` through a symlink in your scripts folder, and calls
     `take_device_lock` FIRST (the lock is `/tmp/tileshell-qa-device.lock`; do not set TMPDIR). If it prints "another QA
     driver is already driving the device" (exit 3), wait 30–90 s and run the script again; never remove or bypass the lock.
  2. installs your APK when the device holds another build: `[ "$(apk_matches | cut -c1-3)" = "yes" ] || adb install -r
     "$APK"` (never with `-g`; grant a permission you need with `pm grant` and say so), failing loudly if the install fails.
  3. drives the app and asserts with the library's helpers (`row_begin <ID> "<what>"` after the install, `assert_eq`,
     `assert_contains`, `assert_absent`, `assert_within`, `record`, `ring_mark` / `ring_since <mark> <launcher|service
     component>`, `dump_ui`, `has_node`, `node_text`, `bounds`, `tap_node`, `screencap`, `ensure_start`, `wake_device`,
     `row_end`). Pages that never idle (a viewfinder, a playing video) are dumped with `gdump`
     (`docs/plan/qa/phase-15/scripts/p15.sh:24-49`). `docs/plan/qa/phase-17/dev-lead/scripts/t1_shells.sh` is a small
     working example of the whole form.
  4. restores everything it changed before it exits: media it pushed or captured (delete what it made), permissions it
     revoked (`pm grant`, asserted), prefs it wrote, airplane mode, the Start layout (never leave a pin or a re-pointed slot).
  Keep a session under about five minutes so the others get the device, and do not hold the lock while you build.
- A command whose URI carries `&`, or whose where-clause carries a quoted literal, goes to `adb shell` as ONE quoted
  string; passed as separate words the device shell eats the `&` or the quotes.
- Your development scripts and their outputs go under `docs/plan/qa/phase-17/dev-<your app>/` (scripts in `…/scripts/`,
  with `lib.sh` symlinked as `dev-lead/scripts/` has it — note `lib.sh` finds the repo four levels above its own folder
  and yours is one deeper, hence `TILESHELL_APK`). These are development proof, not the gate: the lead writes and runs
  the gate's rows (E1–E25, the edge cases) from the doc. Scratch files that are not for the repo go in the scratch
  directory your brief names, with your prefix.
- Typing into a field: `adb shell input text` goes through the shell's own keyboard.
- `exiftool` is not installed on the host. Read EXIF / XMP with Python or `strings`, or `ffprobe` for containers.

## Your report (your final message — the lead reads only that)
Plain text, no file. In this order:
1. Commits on your branch (`git log --oneline eb725c76..HEAD`).
2. What is built, per build-task clause of your brief, each with its proof: the test class and its count, or the
   device script, its output path and its PASS / FAIL totals. Quote the real lines.
3. The whole-suite result (count, failures, the output path).
4. Every clause you did NOT build or could not prove, with why. An honest "not done" is required; never round up.
5. ADDs to other phases' parts and to the lead's foundation (file, what changed) for the Change Log.
6. Deviations from the doc, doc clauses you found wrong, and defects found outside your scope (with repro).
7. Device state you left (what is installed, anything not restored).
8. For each trust-touching part you built, the list of files and functions an adversarial reviewer must read.
