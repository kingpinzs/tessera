# Brief — phase 15, the Alarms & Clock APP (build task 4, T15-37, and its shares of tasks 1, 8, 9)

You are building the **Alarms & Clock app's screens** — one of three Windows 10 Mobile inbox apps phase 15 adds to a
Windows-10-Mobile-style Android shell ("Tessera", package `app.tileshell`: Kotlin, Jetpack Compose with foundation only —
no Material — one sideloaded APK, minSdk 34, targetSdk 36). The clock's BACK END already exists and is verified on the
device: the store, the rules, the scheduler, direct boot and the ring (toast, overlay, notifications). You build the app
the user sees on top of it.

## Where you work
- Your git worktree: `/home/jeremyking/projects/metro-launcher-p15-clock`, branch `phase-15-clock` (at b90b2c7). Work
  and commit ONLY there, logically (several commits, never one squash), each message in a file committed with
  `git commit -F <file>` and ending with these two lines:
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01Wnw3DiCvZpK4eDLHrsVq8D`
  Never push. Never touch `/home/jeremyking/projects/metro-launcher` or `/home/jeremyking/projects/metro-launcher-p15`.
- Build `./gradlew :app:assembleDebug`; tests `./gradlew :app:testDebugUnitTest` (JUnit 4, host JVM; Android classes are
  stubs there — put rules you want proven in pure Kotlin). Read exit codes from a file, never through a pipe.
- NO device work: never run `adb`, never start or touch an emulator. The lead installs and verifies after merging.
- Do not edit `docs/plan/INDEX.md`, `docs/plan/qa/phase-15/STATE.md` or any phase doc (the FINAL doc is the spec). Do not
  edit `cortana/reminders/ReminderScheduler.kt`, `clock/RingService.kt`, `clock/RingActivity.kt`, `clock/RingOverlay.kt`,
  `clock/RingToast.kt` or `clock/ClockRules.kt` — if you need a change there, say so in your report. You MAY add
  functions to `clock/ClockStore.kt` (keep every existing signature and behaviour; every mutation must still end in its
  `changed()` so the scheduler re-arms).

## The spec (read in full before coding)
- `docs/plan/phase-15-inbox-clock-calculator-recorder.md`: Goal; Scope's Alarms & Clock bullets; Decisions "Clock
  rules", "World clock", "Tiles", "Harness contracts" (EVERY tag and diagnostics line — names exact), "Bars",
  T15-15 (the R11 values), T15-16 (compare mode, expanded views, pin, stopwatch Share, Pick from my music), T15-37 (the
  `AlarmClock` API), T15-40 (the secondary tiles), T15-43 (Select, More → About / Notification settings, Sounds page,
  timer editor), C-5 + T15-32 (motion); build tasks 4 and 9; rows E1, E3, E7, E8, E10, E27, E31, E33 and the clock edge
  cases (they say what must be observable).
- Measurements: `docs/plan/r11/clock.md` — §1 (tabs, app bars, 1.13 buttons per tab, 1.14 "…" menu), §2 (alarm list,
  "No alarms"), §3 (the 14393 editor), §4 (flyouts: 4.3 Sound, 4.4 Snooze, 4.6 Sounds page, 4.8 timer editor), §5 (world
  clock, 5.7 compare), §6 (timers, 6.9 expanded), §7 (stopwatch, 7.5 expanded), §9 (tile faces), the U / G rows.
  Shared: `docs/plan/r7-measurements.md` §3.5.8 (app bar 48.2 epx, 68-epx pitch), §3.5.9 (empty-list line), §3.6.2
  (flyout), §1.3.9 (top-anchored dialog), R3 (`docs/plan/w10m-measurements.md`: C1 toggle, A20 / X23 slider, C2 rows).

## Code to reuse — read before writing (house style)
- The back end you build on: `clock/ClockModel.kt`, `clock/ClockStore.kt` (StateFlows `alarms` / `timers` / `stopwatch`,
  `newAlarm`, `putAlarm`, `setAlarmEnabled`, `deleteAlarms`, `addTimer`, `startTimer` / `pauseTimer` / `resetTimer` /
  `updateTimer` / `deleteTimers`, `timerRemaining`, stopwatch start / stop / lap / reset / `stopwatchElapsed`),
  `clock/ClockRules.kt`, `brand/AlarmSounds.kt` (the Sounds page lists these plus `RingtoneManager.TYPE_ALARM` tones;
  a brand sound's URI is `AlarmSounds.uri(id)`), `clock/ClockNotifications.kt` (`openClock(context, page)` is how other
  surfaces open you: extra `page` = `alarm` / `world_clock` / `timer` / `stopwatch`), `ui/MotionClock.kt` (EVERY motion
  you animate goes through `MotionClock.animate(name, …)`; a tap that jumps logs `MotionClock.jump`).
- The in-APK app template: `music/MusicActivity.kt` (`hideSystemBars()`, `ShellRoot`, `testTagsAsResourceId`, drawn bars,
  Back / Windows), `music/MusicCollectionPage.kt` / `MusicNowPlaying.kt` (lists, app bar, menus as this codebase draws
  them), `bars/SystemBars.kt` (`BarMetrics.STATUS_EPX` / `NAV_EPX` — never the literal), `ui/tokens/*` (`ShellType`,
  density: 1.dp = 1 epx), `brand/Brand.kt`, `brand/Glyph.kt` (add glyphs you need from the same Fluent font, with a
  comment), `settings/SettingsWidgets.kt`, `settings/AboutPage.kt`, `settings/SettingsActivity.kt` (`EXTRA_PAGE`; the
  Setup checklist page is how More → Notification settings opens — find its `SettingsPage` value).
- Live tiles: `tiles/engine/LiveTileEngine.kt`, `tiles/engine/TileRouting.kt` (a shell app's tile reads
  `TileRouting.componentKey(pkg, cls)` — publish the next-alarm face THERE), `tiles/engine/TileContent` / `TileFace`,
  `tiles/api/SecondaryTiles.kt` (`request`, `contentKey`, `launch` — T15-40: `launch` must target the Alarms & Clock
  component explicitly for a secondary tile the shell itself owns, instead of `AppCatalog.firstForPackage`; the
  in-process `request` must apply the provider's checks — tile-id pattern `LiveTileProtocol.TILE_ID_PATTERN`, size, name —
  itself), `tiles/api/LiveTileProtocol.kt` (`EXTRA_LAUNCH_TILE_ID`), `StartActivity.kt:187` (the pin band).

## What to build (permanent form — no stubs, no TODOs)
1. `app.tileshell.clock.ClockActivity` — label "Alarms & Clock", own `taskAffinity` `app.tileshell.clock`, launcher,
   `testTagsAsResourceId`, drawn W10M bars, hide Samsung's bars. Reads the extra `page` and `EXTRA_LAUNCH_TILE_ID`
   (`timer.<id>` → the Timer tab with that timer; `stopwatch` → the Stopwatch tab). Manifest entry + its
   `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts_clock"/>` (task 9: ids `alarm`,
   `timer`, `stopwatch`, `world_clock`, ranks 0–3, labels Alarm / Timer / Stopwatch / World Clock, each an intent to
   this activity with `page`).
2. The four icon-over-caption tabs of r11 §1 (a tap JUMPS — `MotionClock.jump("clock_tab", …)`; a swipe settles in
   X13's 250 ms — `MotionClock.animate("clock_swipe", 250, …)`), each tab with its §1.13 app bar and the "…" More menu
   (About; Notification settings → the shell's Setup page; NO Send feedback).
3. Alarm tab: list (§2), "No alarms" (`alarm_empty`), per-row toggle, row tap → the 14393 editor (§3: time spinner,
   Alarm name, Repeats, Sound — Vibrate only / Pick from my music (a shell-drawn picker over MediaStore audio, READ_MEDIA_AUDIO)
   / Pick from ringtones (the Sounds page, §4.6) — and Snooze time §4.4), Save / Delete; Select (multi-select delete);
   flyouts with `MotionClock.animate("flyout", 233, …)`. 12/24-hour per Android's setting.
4. World Clock tab (§5, Decision "World clock"): "Local time" accent row, city search from ICU's
   `android.icu.text.TimeZoneNames.getExemplarLocationName` over the tz ids (no bundled asset, no network), difference lines
   in W10M's wording, remove, compare mode (§5.7, the 48-epx accent hour strip replacing the app bar). Persist the list
   (a small JSON in the app's files dir is fine — credential storage; the world clock never runs before an unlock).
5. Timer tab: several named timers (§6), the timer editor (§4.8), start / pause / reset / delete, the expanded full-screen
   view (§6.9), Pin (`timer_pin:<id>` → `SecondaryTiles.request`, tile id `timer.<id>`), and the face of a pinned timer kept
   current under `SecondaryTiles.contentKey`. Log `[timer] <id> remaining=<ms> uptime=<ms>` on start, stop, pause,
   resume, the tab's resume and every 5 s while a running timer is on screen.
6. Stopwatch tab (§7): start / stop / lap / reset, laps list, Share (`ACTION_SEND text/plain`, one line per lap as
   `stopwatch_lap:<n>` shows it), expanded view (§7.5), Pin (`stopwatch_pin`, tile id `stopwatch`). Log `[stopwatch]
   elapsed=<ms> uptime=<ms>` on the same cadence (the store already logs start / stop / lap / reset).
7. The next-alarm Start tile face (r11 §9's 2015 form: wide — time, name, repeat days, the "Alarms & Clock" label, a bell
   glyph; small — the glyph with a bell badge), published under `TileRouting.componentKey(<pkg>, ClockActivity)` whenever
   the alarms change, cleared when there is none.
8. The `AlarmClock` API handler (T15-37): an exported translucent trampoline activity
   `app.tileshell.clock.AlarmApiActivity`, `android:permission="com.android.alarm.permission.SET_ALARM"`, filters for
   `android.intent.action.SET_ALARM`, `SET_TIMER`, `SHOW_ALARMS`, `SHOW_TIMERS` (category DEFAULT); validates every request
   (hour 0–23, minutes 0–59, days, a message ≤ 64 characters, a timer length ≤ `ClockTimer.MAX_LENGTH_MS`); with
   `EXTRA_SKIP_UI` creates the item (and shows a short notice), without it opens the editor filled in; never edits or
   deletes an existing alarm; logs `[alarms] api <action> from <calling package or "?"> -> <created <id> | opened |
   refused: <why>>`.
9. Tags and diagnostics exactly as "Harness contracts" names them (`clock_pivot:*`, `alarm_row:<id>`, `alarm_time:<id>`,
   `alarm_repeat:<id>`, `alarm_toggle:<id>`, `alarm_empty`, `alarm_editor_field:*`, `alarm_sound:*`, `alarm_sound_pick:<id>`,
   `alarm_select`, `alarm_select_delete`, `clock_more:*`, `sounds_row:<id>`, `timer_editor_field:*`, `about_page`,
   `clock_row:<zone>`, `clock_time:<zone>`, `clock_diff:<zone>`, `clock_local_row`, `clock_compare`,
   `clock_compare_strip`, `timer_remaining:<id>`, `timer_expand:<id>`, `timer_expanded:<id>`, `timer_pin:<id>`,
   `stopwatch_elapsed`, `stopwatch_lap:<n>`, `stopwatch_expand`, `stopwatch_expanded`, `stopwatch_pin`, `stopwatch_share`),
   never on a parent whose text lives in its children.

## Tests (host JVM)
World-clock difference wording and day names (DST-aware, computed with java.time), exemplar-name fallbacks (a zone with
none → the last segment of its id; two zones with one name → with their region), the timer / stopwatch display formats,
the API handler's validation, the editor's repeat-days summary text, the tile face's text. Quote the
`./gradlew :app:testDebugUnitTest` result.

## Report back (final message, under 80 lines)
Commits (hash + subject); files; the test result quoted; the exact tags and diagnostics lines you implemented; every
design call the doc left open; anything you could not build and why; the INDEX Change Log facts the lead must record (the
SecondaryTiles.launch change to phases 01 / 02's part, the next-alarm tile under the component key, the AlarmClock handler).
