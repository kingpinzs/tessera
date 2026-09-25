# Brief — phase 15 QA: the Alarms & Clock rows (development checks on branch phase-15)

You write and RUN the device drivers for phase 15's clock rows, on your own emulator, and report exact results. This is
the build-time development pass (T15-21): the gate is a later full re-run on main, so every driver you write must be
the permanent, re-runnable row driver — the evidence of this run is real evidence, just not the gate's.

## Where and how
- Repo: the lead's worktree `/home/jeremyking/projects/metro-launcher-p15` (branch `phase-15`). Write ONLY:
  `docs/plan/qa/phase-15/scripts/<row>.sh` (and small helpers they need, as new files in that directory) and the
  evidence each row writes under `docs/plan/qa/phase-15/<ROW>/`. Do NOT edit app code, `lib.sh`, `p15.sh`, INDEX.md,
  STATE.md or any phase doc; do NOT commit (the lead commits). Never touch `/home/jeremyking/projects/metro-launcher`.
- YOUR emulator ONLY: `export ANDROID_SERIAL=emulator-5558` for every adb call and driver run; never address
  emulator-5554 or emulator-5556. Private driver lock: `export TMPDIR=/tmp/claude-1000/-home-jeremyking/5d5ffc39-5a2a-4c33-953f-07d71da27a45/scratchpad/p15/tmp5558`
  (mkdir it). PATH gets `$HOME/Android/Sdk/platform-tools`.
- The APK is already built: `app/build/outputs/apk/debug/app-debug.apk` in the worktree. Do NOT run gradle for the app
  (the lead builds). You may build nothing.
- First-time setup of emulator-5558 (it is a fresh AVD): wait for `sys.boot_completed=1`; run
  `docs/plan/qa/phase-03/scripts/provision.sh` (it installs the APK with -g, sets roles, the two ring app-ops, fixture
  apps from ~/android-fixtures, and checks a tap works); then grant the notification listener
  (`adb shell cmd notification allow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener`, phase 01's
  `final_rows.sh:8` form — provision.sh does not); install phase 05's gesture driver
  (`adb install -r -t testapps/ime-fixture/build/outputs/apk/debug/ime-fixture-debug.apk` and
  `…/androidTest/debug/ime-fixture-debug-androidTest.apk`, already built). Record what you did in your report.
- Drivers source `docs/plan/qa/phase-15/scripts/lib.sh` (a symlink to phase 03's shared floor: row_begin / row_end,
  assert_eq / assert_ne / assert_contains / assert_absent / assert_within, record, diag, dump_ui, bounds, node_text,
  has_node, tap_node, ring_mark / ring_since / reply_since / ring_save, wake_device) and then
  `docs/plan/qa/phase-15/scripts/p15.sh` (gdump — the gesture-driver dump for never-idle windows and the overlay;
  jump_clock; clock_restore; clock_pending; assert_clock_empty; next_wall_ms; gtap; resumed). Read both files fully,
  and read one existing phase driver for style (e.g. `git show main:docs/plan/qa/phase-11/scripts/e1.sh` is NOT
  available in this worktree — look at `docs/plan/qa/phase-03/scripts/j7.sh` and `micperm.sh` instead).

## The spec
`docs/plan/phase-15-inbox-clock-calculator-recorder.md`: read the Acceptance preamble IN FULL (baseline, clock-jump
rules, ring slices, recorded clauses, wake after sleep, the clock baseline T15-44 / T15-45, kills and reboots T15-56),
"Harness contracts" (every tag and diagnostics line), and then your rows: **E3, E4, E4b, E5, E6, E6c, E7, E8, E10,
E21 (only its "Full-screen intent denied" and "Overlay" and SCHEDULE_EXACT_ALARM parts; the recorder parts are not
yours), E23 (only the ring-surface clauses), E31, E32, E33**, and the Alarms & Clock EDGE CASES (DST; time zone change;
reboot straddling an alarm and the update re-arm; clock moved back / forward; a second alarm / same-minute alarms /
an alarm edited while it rings; alarm during a call; timers: 10 at once, 0:00 refused, paused-then-clock-jump; stopwatch
1,000 laps and killed between laps; world clock: no-exemplar zone, duplicate names, no match, locale change; 24-hour
setting). The app's actual tags and lines (as built) are listed in the lead's notes below — where the doc and the build
differ, drive what the BUILD does, and REPORT the difference; never bend an assertion to pass.

Build facts you need (from the builders' reports):
- Activities: `app.tileshell/.clock.ClockActivity` (extra `page` = alarm | world_clock | timer | stopwatch; singleTask),
  `app.tileshell/.clock.AlarmApiActivity` (the AlarmClock API: SET_ALARM / SET_TIMER / SHOW_ALARMS / SHOW_TIMERS,
  guarded by com.android.alarm.permission.SET_ALARM — the shell uid holds it; use it to SEED alarms and timers in rows
  whose subject is not the editor: `am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR H
  --ei android.intent.extra.alarm.MINUTES M --es android.intent.extra.alarm.MESSAGE <name> --ez
  android.intent.extra.alarm.SKIP_UI true -n app.tileshell/.clock.AlarmApiActivity`). E3 itself creates its alarm
  through the editor as the doc says.
- Stores: `/data/user_de/0/app.tileshell/files/{alarms,timers,stopwatch}.json` (device-protected; read with
  `adb shell "run-as app.tileshell cat <path>"` — ONE quoted string to adb shell).
- Ring: lines `[alarms] fired <id> kind=… late=…`, `[alarms] notification <id>: fullscreen|quiet`, `[alarms] surface:
  toast-locked|toast-overlay|heads-up|lockscreen-notification <id>`, `[alarms] ring <id> sound=…`, `[alarms] ring ended
  <id>: dismiss|snooze|timeout|superseded|missed`, `[alarms] full-screen intent: allow|deny`, `[alarms] rearm (<why>): n
  alarms, m timers, exact=…`, `[alarms] api …`, `[motion] ring_toast …`, `[motion] flyout …`, `[motion] clock_tab …`,
  `[motion] clock_swipe …`, `[timer] <id> remaining=… uptime=…`, `[stopwatch] elapsed=… uptime=…`. All in the launcher
  ring (`diag`). Toast tags: ring_surface, ring_title, ring_name, ring_time, ring_snooze_for, ring_snooze_choice:<m>,
  ring_snooze, ring_dismiss. The locked toast is RingActivity over an OCCLUDED keyguard with the WALLPAPER beneath (the
  build-start check); the in-use toast is an APPLICATION_OVERLAY window "TesseraRing" that `uiautomator dump` does not
  see — dump it with `gdump`.
- Clock UI tags (beyond the doc's): clock_page:<id>, clock_bar:<add|save|delete>, alarm_spinner:<hour|minute|ampm>,
  alarm_day:<sun..sat>, alarm_snooze:<m>, clock_search, clock_search_result:<zone>, clock_remove:<zone>,
  clock_compare_prev/next, timer_play/timer_reset/timer_length:<id>, stopwatch_play/lap/reset. Read the source under
  `app/src/main/kotlin/app/tileshell/clock/` for anything else — it is the truth of what was built.

## Rules that are not negotiable (the phase 02 gate was returned for breaking them)
1. Every assertion must be able to FAIL: read the thing itself (a node's own text via its tag, the dumpsys line, the
   ring slice from a MARK taken just before the action) — never a parent's text, never a whole-ring grep, never a
   timestamp-free guess. A row with zero assertions fails.
2. Every row starts from the baseline (`assert_clock_empty` at the start), and restores what it changed (RV12) with the
   restore steps the doc lists, then `assert_clock_empty` again. A row that moved the clock ends with `clock_restore`.
3. Run every driver for real on emulator-5558 and keep the log (`docs/plan/qa/phase-15/<ROW>/<ROW>.txt`) and dumps /
   screencaps. A failed first run is kept (rename its folder `<ROW>-run1`), fixed only where the DRIVER was wrong, and
   re-run. If the PRODUCT is wrong, stop that row, write a minimal repro (commands + the output) into
   `docs/plan/qa/phase-15/<ROW>/DEFECT.md`, and report it — do not work around it.
4. A step the AVD cannot do (e.g. the USE_EXACT_ALARM-removed QA build) is reported NOT RUN with the exact reason; never
   faked, never silently skipped.
5. Long waits (E32's 10 minutes, reboots) are real waits — poll with a loop and a timeout; never shorten a product
   timer.

## Report back (final message, under 90 lines)
Per row: the driver path, the final run's `<ROW>: n passed, m failed, k recorded` line quoted, and each FAIL or NOT RUN
with its reason. Then: every product DEFECT (row, repro path, one line), every doc-vs-build difference you found, and
the emulator-5558 setup you did.
