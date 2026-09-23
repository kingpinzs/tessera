---
phase: 15
slug: inbox-clock-calculator-recorder
status: DRAFT   # Stage A step 3 (split) 2026-09-22; interview (step 4), cross-model review and R11 §Alarms & Clock / §Calculator / §Voice Recorder before FINAL
depends-on: [01, 02, 03, 10, 17]   # 17 for its live-tile routing fix only (Decisions "Tiles")
---

# Phase 15 — W10M inbox apps I: Alarms & Clock, Calculator, Voice Recorder

## Goal
Three Windows 10 Mobile (final release) inbox apps live inside the shell APK, each a launcher activity in the app list
beside every other app, exactly as phase 10's Music is. **Alarms & Clock** has W10M's four pivots — Alarm, World clock,
Timer, Stopwatch — and its alarms ring at the exact time, over the keyguard, through Doze, after a reboot and after the
shell is killed, on Android's alarm stream; Tess's "set an alarm" and "set a timer" land in it (interview Q1).
**Calculator** has Standard, Scientific and Programmer modes (plus the converter if Q4 rules it in) and gives Windows
Calculator's answers, exactly, including its error wording. **Voice Recorder** records real microphone audio in the
foreground and the background, survives its process dying mid-take, plays back, trims, renames, deletes and shares, and
holds the microphone through the same arbitration Tess and the keyboard already share. Every screen hides Samsung's bars
and draws the W10M bars (phase 01's bar rule). Every visual value comes from R11's section for that app or is a tagged
approximation with a NEEDS-HUMAN row; nothing here is internet-connected (A11).

## Scope
**In:**
- App identities: three launcher activities (`Alarms & Clock`, `Calculator`, `Voice Recorder`) in the shell APK, each with
  its own `taskAffinity` like `MusicActivity`; app-list entries, pinnable from the app list (phase 02); excluded from the
  hold menu's Uninstall (`AppUninstall.canUninstall` already refuses the shell's own package); ADDs to phase 03's exported
  allow-list (qa/phase-03/exported-allowlist.txt)
- Alarms & Clock: Alarm pivot (list, on/off toggle per alarm, editor: time, repeat days, sound, snooze length, name),
  World clock pivot (city search from the device's own zone database, no map — A11), Timer pivot (several timers, named),
  Stopwatch pivot (laps); the ring page (Snooze / Dismiss) that shows over the keyguard and turns the screen on; the
  ringing sound on the alarm stream with vibration; missed-alarm handling; persistence of alarms, timers and the stopwatch
  across process death, force-stop, update and reboot
- The exact-alarm scheduling for alarms and timers as an ADD to phase 03's `ReminderScheduler` (one scheduler, Rule 16 —
  Decisions), re-armed through the existing `ReminderReceiver` on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / process start
- Tess: `Request.SetAlarm` / `Request.SetTimer` re-pointed at the shell's own Alarms & Clock per Q1, which re-cuts phase 03's
  E2 alarm/timer row, E10's locked alarm and timer, and P6 (named in E9 below); Tess's arithmetic per Q5
- Calculator: Standard / Scientific (DEG / RAD / GRAD, functions, memory) / Programmer (HEX / DEC / OCT / BIN, QWORD / DWORD
  / WORD / BYTE, bitwise, shifts, bit-toggle keypad); history; the converter per Q4; an exact arithmetic engine whose results
  and error strings match Windows Calculator (reuse check of microsoft/calculator, MIT, at build start — Decisions)
- Voice Recorder: capture in its own `:recorder` process as a foreground service (`FOREGROUND_SERVICE_MICROPHONE`); a
  kill-safe take that is recovered after process death; list of recordings, playback with phase 01's slider (X23), trim,
  rename, delete, share (vCard-style `ACTION_SEND` with a content URI); storage per Q3; the Music cross-effect per Q2;
  pause on a call; storage floor; the shell's microphone arbitration gains a third owner (an ADD to phase 03/05's
  `MicArbiter`, Change Log when built)
- Live tiles: the pinned Alarms & Clock tile shows the next alarm (face from R11 §Alarms & Clock or an approximation, H14);
  Calculator and Voice Recorder tiles are static glyph tiles. None of the three joins the default Start layout (X27 stands)
- Manifest ADDs: `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE_MICROPHONE`, `VIBRATE`; the Setup checklist gains one row,
  "Full-screen alarms" (the special app access behind ringing over the keyguard); the microphone row is phase 03's existing
  `microphone` row (no second row)
- Diagnostics lines for every silent-empty state (Decisions); `testTagsAsResourceId` on every window root and a tag on every
  node a row reads
**Out (explicitly):**
- Any internet use: the world clock's map (W10M's pivot drew a Bing map), a Currency converter with live rates, downloaded
  alarm sounds (A11; R10-Q4)
- Reading or importing another clock app's alarms (Android exposes no API for it; Samsung Clock's alarms stay Samsung Clock's
  and keep ringing there — Decisions)
- Bedtime / sleep schedules and Samsung Modes & Routines integration (Samsung's own; recorded on the phone rows only)
- Transcription of recordings (phase 06's voicemail transcription is the only speech-to-text consumer besides Tess; no
  transcription verb here)
- Phase 06's Voice note (its attach-menu recorder, AMR) — it stays phase 06's; this app does not replace it
- Photos, Camera, video, Files, Calendar, People, the Settings front (phases 16–19)

## Decisions
- 2026-09-23: Interview Q5 — Tess does arithmetic offline through the Calculator engine (Jeremy: "(a) so anything
  deterministic can be dont with out an llm and then feed the llm with better smaller info"). Spoken and typed ("15 % of 80 is
  12."), an ADD to phase 03's matcher and action layer (INDEX Change Log when built). Jeremy's reason is recorded as standing
  principle P6 in PLAN.md.
- 2026-09-23: The App Shortcuts each app declares under the phase 11 Q1 standing rule (agent; Jeremy can overrule): Alarms &
  Clock — Alarm, Timer, Stopwatch, World clock; Calculator — Standard, Scientific, Programmer, Converter; Voice Recorder — New
  recording, Recordings. Each is its app's top-level screen, and phase 11's four-satellite limit is met by every app.
- 2026-09-23: Interview Q4 — Calculator includes W10M's Converter, every category except Currency (Jeremy: "(a)"): Volume,
  Length, Weight, Temperature, Energy, Area, Speed, Time, Power, Data, Pressure, Angle. Currency is out because live rates are
  internet (A11).
- 2026-09-23: Interview Q3 — recordings are .m4a (AAC) in the shared Recordings folder (Jeremy: "(a)"), written through
  MediaStore with IS_RECORDING set: other apps, a PC over USB and file managers see them, and they survive uninstalling the shell.
- 2026-09-23: Interview Q2 — recordings stay out of Music, because Voice Recorder plays them (Jeremy: "(c) unless there is
  another way to play them"). There is: Voice Recorder's own list plays, pauses and scrubs recordings (W10M's did). So by Jeremy's
  condition the answer is A — Music skips anything MediaStore marks IS_RECORDING (one predicate in phase 10's MusicStore, INDEX
  Change Log when built) and there is no "Show voice recordings" switch. To make Voice Recorder truly "the other way", it lists
  EVERY recording on the phone that MediaStore marks IS_RECORDING, whichever app made it (Samsung's recorder included), not only
  its own (agent, following the condition). Jeremy can still ask for the Music switch.
- 2026-09-23: Interview Q1 — Tess's clock is the shell's Alarms & Clock, always (Jeremy: "(a)"). Tess sets alarms and timers
  in-process with no intent and no chooser, so it also works over the keyguard (E10's PQ3 fallback becomes moot). Samsung
  Clock's existing alarms are untouched and keep ringing there; they cannot be read or imported (no API), so the user recreates
  the ones they want. An ADD to phase 03's action layer, recorded in the INDEX Change Log when built.
- 2026-09-22: From phase 11 interview Q1 (Jeremy: "A"), a standing rule for every shell app: this phase's apps declare their
  own top-level screens as static App Shortcuts, so a hold on their tiles bursts those screens (phase 11). Which screens each app
  declares is settled at this phase's own interview; a build task and an acceptance row carry it.
- 2026-09-22: Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out thing at a glance
  thing"). PLAN.md: "The W10M inbox apps that can be built inside the rules, each an app in the shell APK like Music:
  Calculator (standard / scientific / programmer), Alarms & Clock (alarm, timer, stopwatch, world clock), … Voice Recorder".
  Read as A8 reads the feature list: everything buildable is in; this phase is the agent's placement of these three (P3) (Jeremy)
- 2026-09-22: R10-Q4 (Jeremy: "(a)"): "A11 stands as written." Consequences here: the World clock pivot has no map, the
  converter has no Currency, and no alarm sound is fetched (Jeremy; consequences agent)
- 2026-09-22: R10 agent call (PLAN.md Rulings): "the shell's own apps take their slots once, following phase 10 Q5's Music
  precedent, and Tess's actions target them; R11, a measurement pass for the inbox apps, gates each app phase's FINAL." None
  of these three apps has a slot in `Slot` (PHONE … CALENDAR), so nothing is seeded here; the takeover that applies is Tess's
  alarm and timer actions (Q1), and R11 gates FINAL (agent)
- 2026-09-22: Fidelity (agent, RV9 / Q10; R10 testability 4 and 26). Every visual and motion value in this doc is one of:
  (1) "from R11 §Alarms & Clock", "from R11 §Calculator" or "from R11 §Voice Recorder" — not yet measured, R11 pending
  (INDEX research table); (2) a value this build already measured or sourced, cited where used: the drawn status bar 28 epx
  (R3 C4, `SystemBars.STATUS_EPX`) and nav bar 48 epx (phase 01 X6, `SystemBars.NAV_EPX`); list rows at a 44-epx pitch with a
  41-epx icon and text at x 57 epx (R3 C2, R6 §5.1.4; `AppListMetrics`); the type ramp (R1 §5.1: caption 12, body 15, base 15
  semibold, subtitle 20, title 24 semilight, subheader 34 light, header 46 light); the pivot header block as phase 10 task 6
  built it (P4 design, `MusicMetrics` PIVOT_BLOCK 54 epx, gap 14 epx, unselected ink 0.4) and the pivot settle (X13, 250 ms);
  the app bar 48.2 ± 1 epx on the nav bar with a 68-epx button pitch (R7 §3.5.8); the cross-app flyout 242.6 ± 0.5 epx wide
  with 44-epx items and a 14-epx inset (R7 §2.2.5) in the dark fill (40,40,40) with a 1-epx (71,76,70) border (R7 §3.6.2);
  the top-anchored dialog 194 epx tall, fill (74,74,74), page dimmed to (2,2,2) (R7 §1.3.9); sliders (X23, R3 A20); the list-row
  press (X19); the empty-list line in white subtitle type at x 11.7 epx, cap top 70.4 epx (R7 §3.5.9); or (3) an approximation
  with its own NEEDS-HUMAN row. When R11 lands, each (2) stand-in is replaced by R11's own value where R11 measured one and
  stays as a tagged approximation where R11 is UNMEASURED. NEEDS-HUMAN rows carry their kind: **[fidelity]** = matches R11
  within its tolerance, judged on the phone; **[accept]** = a P4 design or an approximation with no footage to close it
  against (R10 testability 26) (agent)
- 2026-09-22: One exact-alarm scheduler (agent; R10 design 13, Rule 16). Alarms and timers are armed by phase 03's
  `ReminderScheduler`, which gains two kinds — an ADD to a FINAL part, recorded in the INDEX Change Log when built. Alarms are
  armed with `AlarmManager.setAlarmClock` (Doze-exempt; published through `getNextAlarmClock`, which is what Samsung's lock
  screen and the system's "next alarm" read), timers with `setExactAndAllowWhileIdle(RTC_WAKEUP)` exactly as timed reminders
  are (a timer is not the phone's next alarm). Both use the existing `USE_EXACT_ALARM` grant (a sideloaded app gets it at
  install, phase 03 F1-m7); if a system revokes it, `ReminderScheduler.exactAlarmsDenied` already exists and the checklist's
  `exact_alarms` row goes red — alarms fall back to `setAndAllowWhileIdle` with the same spoken and shown notice reminders give.
  Re-arm follows phase 03's mechanics line (3): `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, process start, and every store change;
  a force-stop cancels everything until the next start (phase 03 E6's rule). Cancel-by-same-request-code keeps re-arming
  idempotent, as it does for reminders
- 2026-09-22: Ringing (agent). A fired alarm starts a foreground service that plays the chosen sound on `USAGE_ALARM`
  (alarm stream, so a silent ringer does not silence it and Do not disturb passes it through as `CATEGORY_ALARM`), vibrates,
  and posts a notification on an alarm channel whose full-screen intent opens the ring page, an activity with
  `showWhenLocked` + `turnScreenOn` and `excludeFromRecents`. That needs `USE_FULL_SCREEN_INTENT`, a special app access on
  API 34+ that AOSP grants a sideloaded app by default (R10 testability 19; `appops get app.tileshell USE_FULL_SCREEN_INTENT`
  must read allow) and One UI 8 is not proven to (P1); with it denied the alarm still sounds and its notification still shows,
  only the page over the keyguard is missing, and the checklist's new "Full-screen alarms" row is red and opens
  `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. The service's foreground type is a build-start check recorded here:
  `systemExempted` (eligible for a `USE_EXACT_ALARM` holder ringing an alarm) else `mediaPlayback`, which the manifest holds.
  Ring timeout: an alarm nobody answers stops after 10 minutes and counts as missed (approximation, H6). Snooze length default
  10 minutes, settable per alarm 5 / 10 / 20 / 30 minutes (from R11 §Alarms & Clock if captured; else approximation, H6).
  During a call (`TelephonyManager` call state OFFHOOK) the sound plays at one eighth volume with vibration and the ring page
  still shows (approximation patterned on AOSP DeskClock's in-call level, H7). A second alarm firing while one rings takes
  the ring page and the first counts as missed. Missed alarms post one "Missed alarm h:mm" notification each (approximation,
  H6); an alarm whose time passed while the phone was off rings on boot if less than its 10-minute ring timeout has passed,
  else it is missed (approximation, H6). Alarm sounds: W10M's are Microsoft assets, so the branding module ships original
  sound-alikes exactly as phase 03 did for its sounds, with real files droppable for personal builds (A10; H8)
- 2026-09-22: Clock rules (agent). An alarm is wall-clock: a time zone change keeps its wall-clock time (the armed instant
  moves), a timer is elapsed-time: its deadline does not move. A one-shot alarm set for a time already past today arms for
  tomorrow. On a DST spring-forward night an alarm at a wall-clock time that does not exist rings at the first instant after
  the gap; on a fall-back night a time that occurs twice rings at its first occurrence only; a repeating alarm keeps its
  wall-clock time across the change (approximations, H9). Times follow Android's 12/24-hour setting. Timers: several at once,
  each named, max 99 h 59 min 59 s (approximation, H10); a timer whose deadline passed while the process was dead fires as soon
  as it is re-armed, as an overdue reminder does ("timer ended while the phone was off" on its notification). Stopwatch: laps;
  it keeps running across process death and force-stop from its stored start instant, and across a reboot from its stored
  wall-clock start (elapsed time resets at boot), rolling over at 99:59:59.99 (approximation, H10)
- 2026-09-22: World clock (agent, P5 / A11). City names and search come from the device's own ICU data
  (`android.icu.text.TimeZoneNames.getExemplarLocationName` over the tz database's zone ids), so there is no bundled city
  asset, no network and no Google; a zone ICU has no exemplar name for shows the last segment of its id; two zones with one
  exemplar name are shown with their region. Each row shows the city's time and a difference line ("+8 hours", "tomorrow"),
  DST-aware from the zone rules. The map W10M drew is out (A11); H4
- 2026-09-22: Calculator engine (agent, R2's reuse discipline). Windows Calculator's engine is open source under MIT
  (github.com/microsoft/calculator: CalcManager and ratpak, C++) and its UWP XAML is the same app W10M ran, which makes the
  repo an RV9 source-2 fidelity input for R11 §Calculator where footage fails. Build-start reuse check, recorded here when
  made: port the engine's arithmetic to Kotlin or build it through the NDK; either way the results must equal Windows
  Calculator's for the fixture table (E11) — exact rational arithmetic, so 0.1 + 0.2 reads 0.3 and never 0.30000000000000004;
  error strings "Cannot divide by zero", "Result is undefined" (0 ÷ 0), "Invalid input" (√−1 in Standard), "Overflow";
  Windows' percent semantics (80 + 15 % = 92); repeated = repeats the last operation; Programmer mode wraps to the word size
  in two's complement (BYTE: 255 + 1 = 0; QWORD: −1 = FFFFFFFFFFFFFFFF) and shifts past the word size give 0. History
  persists until cleared (approximation, H11). The app's fixture expectations are computed on the host with Python's
  `fractions` / `decimal`, never by the app's own engine (RV: no self-grading)
- 2026-09-22: Voice Recorder mechanics (agent). Capture runs in its own process `:recorder` (R10 design 27: a Start crash must
  not end a take, and capture memory stays out of the launcher process; `ShellApp` already returns early outside the main
  process) as a foreground service of type `microphone` with an ongoing notification carrying Stop. The take is written
  kill-safe: AAC frames from `MediaCodec` go to a raw ADTS stream, where every frame stands alone, and the `.m4a` container
  is muxed at stop; a stream left behind by a dead process is recovered at the next start into a normal recording (E18).
  `MediaRecorder`'s MP4 is not used because a kill leaves it unplayable. Where the file lands is Q3. The recorder takes the
  microphone through the shell's `MicArbiter` as a third owner ("recorder"), so Tess and the keyboard's voice key are refused
  with `MICROPHONE_BUSY` while a take runs and Tess's notice names it ("The voice recorder is using the microphone right now.",
  approximation, H13) — an ADD to phase 03/05's arbitration, Change Log when built; and it registers
  `AudioManager.AudioRecordingCallback`, so a capture Android silences for another app (its concurrent-capture rule) pauses
  the take with a notice instead of writing silence. A call (`TelephonyManager` RINGING then OFFHOOK) pauses the take;
  the user resumes or stops afterwards (approximation, H12). Before a take starts the recorder checks
  `StorageManager.getAllocatableBytes` and stops at a 50 MB floor, saving what it has with "Not enough space" (approximation,
  H12). Recording names "Recording N" (from R11 §Voice Recorder if legible; else approximation, H12). Trim writes a new file
  and keeps the original until the trim is saved (a real cut, asserted in E16); rename changes `DISPLAY_NAME`; delete removes
  the MediaStore row and the file; share sends the recording's content URI with `ACTION_SEND audio/mp4`
- 2026-09-22: Bars (agent, phase 01's bar rule of 2026-09-17): every page of the three apps hides Samsung's bars and draws
  the W10M status bar and the Back / Windows / Search nav bar; the drawn Back is Back for the page, Windows goes Home. The
  ring page over the keyguard draws the status bar and the lock-screen nav variant with inactive keys, as phase 06's incoming
  call does (its H23), so a stray touch cannot leave a ringing alarm; from R11 §Alarms & Clock if its ring page was captured,
  else approximation, H5
- 2026-09-22: Tiles (agent). None of the three is added to the default layout (X27, H37 stand). Pinned from the app list
  (phase 02), the Alarms & Clock tile shows the next alarm's time and day on its face (R11 §Alarms & Clock if captured; else
  approximation, H14), published through `LiveTileEngine` under the pinned tile's own key (its component), never under the
  package. **Why, and the one fix this depends on** (cross-phase fact from the lead, verified in code by the phase 17 writer):
  `StartPage.kt` (around lines 150 and 230) hands a tile the content published under `LiveTileEngine.packageKey(<its app's
  package>)`, and `MusicFeed` publishes the now-playing face under the session owner's package and grows every tile of that
  package (`ActiveTiles.setPackage`); every in-APK app shares `app.tileshell`, so a pinned Alarms & Clock, Calculator or Voice
  Recorder tile would otherwise show and grow with Music's face. The fix is phase 17's — phase-17-inbox-photos-camera-video.md,
  Decisions "the package-keyed live-tile fallback must be fixed before the seed" and its build task 2: a shell-owned session
  is attributed by its tag (`MediaController.getTag()`), `StartPage`'s fallback and `ActiveTiles.setPackage` follow, an ADD to
  phases 01 and 10 with its Change Log entry. This phase invents no second fix and lists 17 in depends-on for that routing
  alone; E1 proves the negative on its pinned tiles. Calculator and Voice Recorder tiles carry the static glyph
- 2026-09-22: Harness contracts (agent; R10 testability 24, 25, 33, 34). Every window root sets `testTagsAsResourceId` (the
  contract `MusicActivity` documents after MUSIC6's first run failed 27 of 35 without it); every node whose text a row reads
  carries its own tag (`alarm_row:<id>`, `alarm_time:<id>`, `alarm_toggle:<id>`, `timer_remaining:<id>`, `stopwatch_elapsed`,
  `stopwatch_lap:<n>`, `clock_row:<zone>`, `clock_time:<zone>`, `calc_display`, `calc_expr`, `calc_key:<name>`,
  `calc_history:<n>`, `rec_row:<id>`, `rec_duration:<id>`, `rec_button`), never a parent whose text lives in its children
  (the MUSIC8 vacuous-pass defect). Drivers symlink qa/phase-03/scripts/lib.sh as phase 01 does, source
  qa/phase-03/scripts/audio.sh for the microphone, and stay adb-driven (no instrumentation APK). Diagnostics ring lines, read
  with phase 01's command, for each state that would otherwise render as a legitimate empty: `[alarms] rearm (<why>): n
  alarms, m timers, exact=<bool>`, `[alarms] fired <id> kind=alarm|timer late=<ms>`, `[alarms] ring ended <id>:
  dismiss|snooze|timeout|missed|superseded`, `[alarms] full-screen intent: allow|deny`, `[recorder] start <name> free=<MB>`,
  `[recorder] paused: call|silenced by <pkg>`, `[recorder] stop <name> ms=<n> bytes=<n>`, `[recorder] recovered <name> ms=<n>`,
  `[recorder] refused: microphone busy owner=<owner>`, `[recorder] storage floor`, `[calc] error <kind>`, `[calc] engine
  <port|ndk> <version>`
- 2026-09-22: APK budget (agent; phase 03's ≤ 600 MB ceiling). These three apps add code and sound-alike alarm sounds only;
  the build records the APK size before and after (E25) and the delta must be ≤ 3 MB with no single new asset ≥ 1 MB
- 2026-09-22: App-list regression (agent; R10 testability 35). Three new launcher entries change the A-Z groups and the jump
  grid phase 01's E12 and the Running / Recently added sections count on; E1 runs the phase-02 `regress.sh` pattern once for
  this phase and asserts the three rows carry no "New" caption (X14: the package's `firstInstallTime` is the shell's)

## Interview queue (Stage A step 4)
Ask one at a time, in this order.

1. ~~Tess's clock~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Where Tess sets alarms and timers.** Today "set an alarm for 7" starts `AlarmClock.ACTION_SET_ALARM` with `EXTRA_SKIP_UI`
   as a voice activity, and whichever clock app handles it (DeskClock on the AVD, Samsung Clock on the phone) takes it; once
   the shell's Alarms & Clock declares the same intent there are two handlers and Android shows a chooser (R10 design 13,
   testability 5). Which clock is Tess's?
   A. **(lean)** The shell's Alarms & Clock, always, set in-process with no intent at all — so it also works over the keyguard
      with nothing to show (E10's PQ3 fallback becomes moot). Samsung Clock's existing alarms are not touched, cannot be read
      or imported (no API), and keep ringing there; the user recreates the ones they want here.
   B. A "Clock app" choice in Tess's Settings page, like the Notes slot, defaulting to the shell's; another clock keeps the
      `ACTION_SET_ALARM` path and its chooser-free explicit target.
   C. Keep the implicit intent as built and let Android's chooser / "Always" choice decide the target.
   D. Other / let me clarify.
2. ~~Recordings in Music~~ RULED 2026-09-23: A by Jeremy's own condition (see Decisions). Original question kept below.
   **Recordings and Music.** Phase 10 Q6 admits everything MediaStore calls audio into Music's library, recordings included,
   as a NEEDS-HUMAN row; R10 design 14 asks this phase to re-ask rather than inherit it. Once the shell records its own audio:
   A. **(lean)** Recordings are left out of Music (the `IS_RECORDING` flag, API 31+) and live only in Voice Recorder — an ADD
      of one predicate to phase 10's `MusicStore`, Change Log when built.
   B. Keep Q6 as ruled: recordings also appear in Music's songs pivot and can be queued and playlisted there.
   C. A Music setting "Show voice recordings", default off.
   D. Other / let me clarify.
3. ~~Where recordings live~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Where recordings live.**
   A. **(lean)** `.m4a` (AAC) in the phone's shared Recordings folder through MediaStore with `IS_RECORDING` set — any app, a
      PC over USB and a file manager see them, and they survive uninstalling the shell (W10M kept its .m4a in Documents\Sound
      recordings).
   B. App-private storage: only the shell's Voice Recorder sees them; uninstalling the shell deletes them.
   C. `.wav` (PCM) in the Recordings folder — lossless, about ten times the size, playable anywhere.
   D. Other / let me clarify.
4. ~~Converter~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **The converter.** PLAN.md names Calculator's three modes; W10M's Calculator also had a Converter (Volume, Length, Weight,
   Temperature, Energy, Area, Speed, Time, Power, Data, Pressure, Angle, Currency).
   A. **(lean)** In, every unit converter except Currency (live rates are internet, A11).
   B. Out: Standard, Scientific and Programmer only.
   C. In, Currency included with a bundled fixed rate table (stale by design, no network).
   D. Other / let me clarify.
5. ~~Tess and arithmetic~~ RULED 2026-09-23: A, and a standing principle (see Decisions). Original question kept below.
   **Tess and arithmetic** (R10 design 15: "what's 15 % of 80" is outside phase 03's ruled command list).
   A. **(lean)** In: Tess answers spoken and typed arithmetic offline through the Calculator engine ("15 % of 80 is 12."),
      an ADD to phase 03's matcher and action layer, Change Log when built.
   B. Out: arithmetic goes to the not-understood handler as today (phase 08's LLM layer may pick it up later).
   C. Tess opens Calculator with the expression typed into its display and says nothing.
   D. Other / let me clarify.

## Build tasks
Ordered so the scheduler and the process boundaries exist before the screens that depend on them, and so the one task that
touches shipped phase 03 code lands early enough to be re-verified.
1. **App identities and contracts.** Three launcher activities with their own task affinities; app-list entries; the
   Uninstall exclusion asserted (`AppUninstall.canUninstall`); ADDs to qa/phase-03/exported-allowlist.txt; `testTagsAsResourceId`
   on each root; the manifest ADDs (`USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE_MICROPHONE`, `VIBRATE`); the APK size before.
2. **Scheduler ADD (touches phase 03).** Alarm and timer kinds in `ReminderScheduler` (`setAlarmClock` / `setExactAndAllowWhileIdle`),
   their stores (alarms.json, timers.json, stopwatch.json in the app's files directory, temp-file-and-rename like
   `LayoutStore`), re-arm through `ReminderReceiver`, the `exactAlarmsDenied` fallback and notice. INDEX Change Log line.
3. **Ringing.** The ring service (sound on `USAGE_ALARM`, vibration, in-call level, timeout), the alarm notification channel
   with the full-screen intent, the ring page (`showWhenLocked`, `turnScreenOn`, Snooze / Dismiss, lock-screen nav variant),
   missed-alarm notifications, the "Full-screen alarms" checklist row. Foreground type recorded (Decisions).
4. **Alarms & Clock app.** The four pivots on the shell's pivot geometry (phase 10 task 6's, until R11); the alarm list and
   editor (time picker, repeat days, sound, snooze, name); several named timers; the stopwatch with laps; the world clock with
   ICU city search and difference lines; every value from R11 §Alarms & Clock once it lands.
5. **Tess re-wire (touches phase 03).** `ActionLayer.alarm` / `timer` per Q1; `LockGate` unchanged (alarms and timers stay
   allowed while locked); phase 03 E2's alarm/timer observable, E10's locked alarm and timer and P6 re-cut (E9 here names them);
   Q5's arithmetic command if ruled in. INDEX Change Log line.
6. **Calculator.** The engine (reuse check recorded), Standard / Scientific / Programmer, memory, history, the converter per
   Q4, the fixture table docs/plan/qa/phase-15/calc-cases.tsv with host-computed expectations; geometry from R11 §Calculator.
7. **Voice Recorder.** The `:recorder` process and foreground service; the ADTS writer, muxer and recovery; MediaStore
   publish per Q3; the list, playback (X23 slider), trim, rename, delete, share; `MicArbiter`'s third owner and the
   `AudioRecordingCallback` pause; the call pause; the storage floor; the Music predicate per Q2; geometry from R11 §Voice Recorder.
8. **Tiles, diagnostics, evidence.** The next-alarm tile face (component-keyed), the static tiles, every diagnostics line in
   Decisions, the APK size after, the phase-02-style app-list regression run.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps follow RV13.
Drivers source docs/plan/qa/phase-03/scripts/lib.sh (symlinked into qa/phase-15/scripts as phase 01 did); microphone rows
use qa/phase-03/scripts/audio.sh (the null sink `vmic`, `adb emu avd hostmicon`, `paplay -d vmic <wav>`, its `rms`);
"diagnostics" is read with phase 01's command. **AVD limits, stated up front:** the clock is jumped with
`adb shell cmd alarm set-time <epoch-ms>` (the shell user holds SET_TIME; if this image refuses it, the RV12 root + `date`
form) after `settings put global auto_time 0`, and restored per RV12's clock restore; the time zone with
`adb shell cmd alarm set-timezone <zone>` (restore to the recorded `getprop persist.sys.timezone`); the AVD ships with
`USE_FULL_SCREEN_INTENT` allowed for a sideloaded app (`appops get app.tileshell USE_FULL_SCREEN_INTENT` recorded at the top of
every alarm row) and its keyguard disabled (`locksettings set-disabled false` before lock steps, `true` after, phase 01
E20's finding); the virtual microphone is phase 03's audio route. Geometry rows are R11-gated: until R11 lands they assert
structure (pivot names and order, row counts, tags present) and the bars against R3 C4 / X6, and their numeric assertions are
added from R11's section when it lands, so this doc cannot go FINAL before R11 (INDEX research table).
**Emulator:**
- E1 App identity and list: the app list shows "Alarms & Clock" under A, "Calculator" under C and "Voice Recorder" under V
  (dump; tags `applist_row:` per phase 01), each with NO "New" caption (X14); the jump grid marks A, C and V available (X8);
  each row's hold menu offers Pin to Start and NOT Uninstall (phase 10 E2's form); `qa/phase-02/scripts/regress.sh`'s pattern
  passes for the whole list; each app's window dump carries resource-ids (a node with `resource-id="alarm_pivot"`,
  `calc_display`, `rec_button` respectively), proving `testTagsAsResourceId`. **Music negative (phase 17's routing, Decisions
  "Tiles"):** with all three pinned to Start and the shell's Music playing a fixture track (phase 10 E10's form), the three
  tiles keep their own faces (dump tags; screencap) and none has grown (`ActiveTiles` bounds unchanged); only the MUSIC tile
  carries the now-playing face, and phase 10's E10 and E13 pass on the same build
- E2 Exported components: `adb shell dumpsys package app.tileshell` matches qa/phase-03/exported-allowlist.txt after its ADDs
  (three activities, the ring page, the recorder service), so phase 03's E5 still passes (`scripts/exported.py`)
- E3 Alarm armed: an alarm at now + 3 min created through the editor (`input tap` at dump bounds, the time picker driven by
  swipes at its loop bounds) → `adb shell dumpsys alarm` lists an RTC_WAKEUP alarm for `app.tileshell` and its "Next alarm
  clock" section shows that time for user 0 (`setAlarmClock`); diagnostics `[alarms] rearm (store change): 1 alarms, 0 timers,
  exact=true`; the alarm's toggle off → both gone; on again → back at the same instant. A one-shot alarm at a time already
  past today arms for tomorrow (dump `alarm_time:` shows "Tomorrow")
- E4 Alarm fires over the keyguard (clock jump): alarm at 09:05 tomorrow; `adb shell locksettings set-disabled false`,
  `locksettings set-pin 1234`, `input keyevent KEYCODE_SLEEP`; `settings put global auto_time 0`; `cmd alarm set-time` to
  09:04:50 tomorrow → within 30 s `adb shell dumpsys window` shows the ring page on top with the keyguard still showing,
  `dumpsys power` shows the display on, `dumpsys notification --noredact` shows the alarm channel's notification with a
  full-screen intent and category alarm, `dumpsys audio` shows a player from `app.tileshell` on usage ALARM in state started,
  diagnostics `[alarms] fired <id> kind=alarm late=<n>` with late ≤ 1000 ms; a tap on Snooze at its bounds → the page closes,
  `dumpsys alarm` shows a new alarm 10 min ahead (the default snooze), the notification reads "Snoozed until h:mm"; jump again →
  rings; Dismiss → nothing left in `dumpsys alarm` for it (one-shot) and the missed count is 0; a daily alarm dismissed instead
  re-arms for the next day. Restore: RV12's clock restore, `locksettings clear --old 1234`, `locksettings set-disabled true`
- E5 Doze and offline: an alarm 2 min ahead; `adb shell cmd connectivity airplane-mode enable`; phase 03 E2's Doze procedure
  (`dumpsys deviceidle enable deep`, `dumpsys battery unplug`, `KEYCODE_SLEEP`, `dumpsys deviceidle force-idle`); jump the
  clock to 5 s before → it rings (E4's checks) with `late` ≤ 1000 ms; restore per RV12 (`deviceidle unforce`, `disable deep`,
  `battery reset`, airplane-mode disable, the clock)
- E6 Timer across process death and reboot: a 90-s timer → `dumpsys alarm` shows an RTC_WAKEUP for `app.tileshell` at start
  + 90 s ± 1 s that is NOT in the "Next alarm clock" section; `adb root`, `adb shell kill -9 $(adb shell pidof app.tileshell)` (phase 03 E12's form),
  `adb unroot`, `adb wait-for-device`, reopen → `timer_remaining:` reads 90 − elapsed ± 2 s and the alarm is still armed; at the deadline the
  timer rings (E4's audio and notification checks, kind=timer) and Dismiss stops it. Second pass: a 10-min timer, `adb reboot`,
  `adb wait-for-device`, poll `getprop sys.boot_completed` = 1, `wm dismiss-keyguard` → `dumpsys alarm` shows it re-armed at
  the original wall-clock deadline (diagnostics `[alarms] rearm (boot): 0 alarms, 1 timers`) and its remaining is right. Third
  pass: a 1-min timer, reboot straddling its deadline → after boot it fires at once with the "ended while the phone was off"
  notification text. `adb shell am force-stop app.tileshell` then Home: the timer is re-armed at process start (phase 03 E6's rule)
- E7 Stopwatch: start; dumps at t and t + 10 s show `stopwatch_elapsed` advanced by 10.0 ± 0.5 s; Lap adds `stopwatch_lap:1`
  with the split; `kill -9` the process (E6's route) and reopen → still running, elapsed continuous (± 1 s); `adb reboot` →
  still running from the wall-clock start (± 2 s over the reboot); Reset clears to 0:00.00 and the laps
- E8 World clock, offline (airplane mode on for the whole row; the shell uid's byte counters in `dumpsys netstats --uid`
  unchanged, phase 06 E18's form): search "Tok" lists Tokyo (an ICU exemplar name; dump); adding it shows `clock_time:Asia/Tokyo`
  equal to the host's `TZ=Asia/Tokyo date +%H:%M` ± 1 min and a difference line consistent with the AVD's zone;
  `cmd alarm set-timezone Europe/London` → the local row and every difference line change accordingly (dump), and an alarm
  set at 07:00 before the change still reads 07:00 while its `dumpsys alarm` instant moved by the zone offset, and a running
  timer's instant did not move (Decisions); restore the zone
- E9 Tess sets alarms and timers in the shell (pending Q1, written for its lean A; **re-cut of phase 03 E2's alarm/timer row,
  E10's locked alarm and timer, and P6**, recorded in the INDEX Change Log when built): through the audio route, "set an alarm for 7 30 a m" → the
  Alarm pivot lists 7:30 AM (dump `alarm_time:`), `dumpsys alarm` shows it for `app.tileshell` in the "Next alarm clock" section,
  the reply text is "Alarm set for 7:30 AM." (diagnostics), NO chooser and no DeskClock activity resumed
  (`dumpsys activity activities`), and the DeskClock fixture's own list is unchanged (`content query --uri
  content://com.android.deskclock/alarms` count before = after); "set a timer for 5 minutes" → `timer_remaining:` shows 5:00
  counting and the reply "Timer set for 5 minutes."; over the keyguard (E10's route: PIN, sleep, wake, `KEYCODE_ASSIST` or
  `cmd voiceinteraction show`) both land while `dumpsys window` still shows the keyguard and no shell activity started
  (in-process; diagnostics has no "startVoiceActivity" line); `LockGate.allowedWhileLocked` still lists both. Restore the PIN
- E10 Alarms & Clock geometry (R11-gated; [fidelity] H1): the four pivot headers in order Alarm / World clock / Timer /
  Stopwatch (dump), the alarm row with its toggle, the editor's time picker, the ring page — each value within its R11
  §Alarms & Clock tolerance once R11 lands; now: the drawn status bar 28 epx (R3 C4) and nav bar 48 epx (X6) on every pivot
  and on the ring page (dump bounds), the pivot settle 250 ± 17 ms + one frame (X13, screenrecord), the app bar 48.2 ± 1 epx
  on the nav bar with a 68-epx pitch (R7 §3.5.8), rows at 44 ± 1 epx (R3 C2)
- E11 Calculator correctness: docs/plan/qa/phase-15/calc-cases.tsv (≥ 200 lines: expression, mode, base/word size/angle unit,
  expected display), each driven by `input tap` at `calc_key:<name>` bounds and read from `calc_display` (dump), expectations
  computed on the host with Python `fractions` / `decimal` and the Windows error strings written by hand: 1 ÷ 0 → "Cannot divide
  by zero"; 0 ÷ 0 → "Result is undefined"; √−1 (Standard) → "Invalid input"; 10 xʸ 10000 → "Overflow"; 0.1 + 0.2 → 0.3;
  80 + 15 % → 92; 2 × = = → 8; 200 ! → its 375-digit value rendered as Windows renders it (from R11 §Calculator: e-notation
  threshold; until then the case asserts the leading digits); sin 30 in DEG → 0.5, in RAD → −0.9880…, in GRAD → 0.4540…;
  Programmer: 255 DEC → HEX FF, OCT 377, BIN 1111 1111; BYTE 255 + 1 → 0; QWORD −1 → FFFF FFFF FFFF FFFF; 1 Lsh 64 (QWORD) → 0;
  NOT 0 (WORD) → FFFF; AND / OR / XOR pairs; memory MS / M+ / MR / MC; the clipboard paste of "12+3" evaluates to 15
- E12 Calculator modes: the hamburger lists Standard / Scientific / Programmer (and the converter groups per Q4) (dump);
  switching modes keeps the display value; the converter (if in) converts 1 mile → 1.609344 kilometres exactly and 100 °C →
  212 °F; history rows `calc_history:<n>` list the last expressions and survive `kill -9` (Decisions); Clear history empties them
- E13 Calculator geometry (R11-gated; [fidelity] H2): the key grid, display and mode pane within R11 §Calculator's tolerance
  once R11 lands; now the bars as E10
- E14 Recorder records real audio (both directions, R10 testability 21): `audio.sh setup`; tap `rec_button`; `paplay -d vmic
  tone440_5s.wav` (a 5-s 440 Hz WAV at −12 dBFS, generated by the driver); tap stop → the new `rec_row:` shows `rec_duration:`
  5 ± 0.5 s; `adb shell content query --uri content://media/external/audio/media --projection
  _id:display_name:duration:is_recording:relative_path --where "is_recording=1"` lists it with `relative_path` Recordings/
  (Q3 A) and duration within ± 500 ms; `adb pull` the file → `ffprobe -show_entries format=duration` 5 ± 0.5 s and
  `ffmpeg -af astats` overall RMS > −40 dBFS. Second take with nothing played for 5 s → RMS < −60 dBFS. Diagnostics
  `[recorder] start …` and `[recorder] stop … ms=<5000 ± 500>`
- E15 Recorder in the background: start a take with the tone looping; `input keyevent KEYCODE_HOME` → `adb shell dumpsys
  activity services app.tileshell` shows the recorder service `isForeground=true` with `foregroundServiceType=microphone`
  and its process name `app.tileshell:recorder` (`pidof app.tileshell:recorder`); `dumpsys notification` shows its ongoing
  notification with a Stop action; `KEYCODE_SLEEP` for 20 s, `KEYCODE_WAKEUP`, `wm dismiss-keyguard`; stop from the
  notification → duration ≥ 25 s and RMS > −40 dBFS across the whole file (a 5-s window at 22 s measured separately)
- E16 Playback, trim, rename, delete, share: tap `rec_row:` → `dumpsys audio` shows a player from `app.tileshell` on usage
  MEDIA started, and the slider thumb (X23) moves between two dumps 2 s apart; Trim to 1.0–3.0 s of the 5-s take → the saved
  file's `ffprobe` duration 2.0 ± 0.1 s with RMS still > −40 dBFS and the untrimmed file gone only after the save (a query
  mid-edit shows both); Rename to "Standup" → `display_name` Standup.m4a in MediaStore; Delete → row gone, MediaStore count
  − 1, `adb shell ls /sdcard/Recordings` no longer lists it; Share → `dumpsys activity activities` shows the resolver for
  `ACTION_SEND audio/mp4` with a `content://` stream, and `adb shell content read --uri <that uri>` yields a file whose first
  bytes are an MP4 `ftyp` box
- E17 Microphone arbitration (phase 03/05's `MicArbiter`, third owner): with a take running and the tone playing,
  `KEYCODE_ASSIST` → Tess's card shows the busy notice naming the recorder (dump), the speech process's dump shows
  `MICROPHONE_BUSY` refused for owner `cortana` with owner `recorder` holding, and the take's RMS over that window is still
  > −40 dBFS (the recorder kept the microphone); a text field with the keyboard's voice key tapped → the keyboard's own notice
  (phase 05 EDGE3's form). Conversely, with Tess listening (`KEYCODE_ASSIST` then the hold), `rec_button` → the recorder's
  notice "Tess is listening" (approximation, H13), no file created (MediaStore count unchanged), diagnostics `[recorder]
  refused: microphone busy owner=cortana`
- E18 Recorder process death: start a take with the tone; after 8 s `adb root`, `adb shell kill -9 $(adb shell pidof app.tileshell:recorder)`,
  `adb unroot`, `adb wait-for-device`; reopen Voice Recorder → a recovered `rec_row:` with `rec_duration:` 8 ± 1 s, diagnostics
  `[recorder] recovered <name> ms=<n>`, the pulled file plays (`ffprobe` duration 8 ± 1 s, RMS > −40 dBFS); Start and the
  live tiles never restarted (`pidof app.tileshell` unchanged, phase 03 E12's form)
- E19 Storage floor: `adb root`, `fallocate -l $(( free_bytes - 60*1024*1024 )) /data/media/0/fill.bin` (the shell's view of
  /sdcard), `adb unroot`; start a take with the tone → it stops by itself when the free space reaches the 50 MB floor,
  the notice "Not enough space" shows (dump), diagnostics `[recorder] storage floor`, and the partial take is listed and plays;
  restore `rm /data/media/0/fill.bin` (root) and a MediaStore rescan
- E20 Music cross-effect (Q2): after E14, Music's songs pivot (qa/phase-01/scripts/music_lib.sh's driver form, MUSIC6) does
  NOT list the recording under Q2 A (or DOES under B; the row asserts whichever was ruled, never "unruled"); phase 10's
  MUSIC6 re-run passes on the same build
- E21 Permission states: `pm revoke app.tileshell android.permission.RECORD_AUDIO` → Voice Recorder's page says it cannot record
  and offers the grant in place (phase 10 E18's form), the checklist's `microphone` row (phase 03's) is red, and `rec_button`
  starts nothing; `pm grant` restores it. `appops set app.tileshell USE_FULL_SCREEN_INTENT deny` → E4's alarm still sounds and
  notifies but no ring page appears over the keyguard, the "Full-screen alarms" row is red and its tap resumes
  `com.android.settings` (`dumpsys activity activities`), diagnostics `[alarms] full-screen intent: deny`; restore `allow`.
  `appops set app.tileshell SCHEDULE_EXACT_ALARM deny` while `USE_EXACT_ALARM` is held: the result is recorded (the op does
  not govern a `USE_EXACT_ALARM` holder) and an alarm still arms with `setAlarmClock`
- E22 Diagnostics coverage: every line named in Decisions appears in the ring at least once across E3–E21 (a driver greps each
  pattern), so no silent-empty state is unlogged
- E23 Bars (phase 01's bar rule): on each of the three apps' main pages and the ring page (unlocked, and over the keyguard
  in E4), `dumpsys window` shows the system status and nav bars not visible for the shell's window (phase 01 E19's form) and
  screencap shows the drawn bars; on the ring page over the keyguard a tap on each drawn key leaves the ring page showing
  (dump); the drawn Back on a Calculator sub-page returns to the previous page and Windows goes Home
- E24 Recorder geometry (R11-gated; [fidelity] H3): the record button, the list rows and the playback controls within R11
  §Voice Recorder's tolerance once R11 lands; now the bars as E10 and rows at 44 ± 1 epx (R3 C2)
- E25 APK budget: `stat -c%s app/build/outputs/apk/debug/app-debug.apk` before task 1 and after task 8 differ by ≤ 3 MB;
  `unzip -l` lists no new entry ≥ 1 MB
**Phone-only (S25 Ultra):**
- P1 `appops get app.tileshell USE_FULL_SCREEN_INTENT` on One UI 8 recorded; a real alarm with the phone locked rings, turns the
  screen on and shows the ring page over Samsung's keyguard; Snooze and Dismiss work without unlocking; the alarm volume follows
  Samsung's Alarm slider; with Do not disturb on and with Sleep mode (Modes and Routines) on it still rings; the next alarm
  appears on Samsung's lock screen (the `getNextAlarmClock` consumer) — recorded, since Samsung may show only Samsung Clock's
- P2 Liveness (N-01): an armed alarm and a running timer survive 24 h idle, a Device care optimise and a reboot, and the alarm
  rings at its time
- P3 Real microphone: a take on the phone's microphone and one on a Bluetooth headset microphone; intelligibility is H15
- P4 A real incoming call during a take pauses it (Decisions) and it resumes or stops afterwards with the file intact
- P5 Tess's alarm and timer over Samsung's keyguard land in the shell's Alarms & Clock (re-cut of phase 03 P6)
- P6 Motion values whose tolerance is ≤ 17 ms, if R11 §Alarms & Clock / §Calculator / §Voice Recorder record any,
  re-measured from a phone screenrecord as phase 01 P10 (RV11)
**NEEDS-HUMAN:**
- H1 [fidelity] Alarms & Clock matches R11 §Alarms & Clock within tolerance on the phone (every pivot, the editor, the ring page)
- H2 [fidelity] Calculator matches R11 §Calculator
- H3 [fidelity] Voice Recorder matches R11 §Voice Recorder
- H4 [accept] the World clock pivot without W10M's map (A11), as a list with difference lines
- H5 [accept] the ring page's bars over the keyguard: lock-screen nav variant with inactive keys (approximation, phase 06 H23's
  pattern) — becomes [fidelity] if R11 captured the ring page
- H6 [accept] ring timeout 10 min, snooze default 10 min and its options, the missed-alarm notification, ringing on boot inside
  the timeout (approximations)
- H7 [accept] the in-call alarm level (one eighth, with vibration) (approximation)
- H8 [accept] the branding module's alarm sound-alikes (A10, phase 03's sounds precedent)
- H9 [accept] the DST and time-zone rules for alarms and timers (approximations)
- H10 [accept] several timers, the 99:59:59 timer ceiling, the stopwatch's reboot behaviour and rollover (approximations)
- H11 [accept] Calculator history persisting until cleared; the converter's unit list if Q4 rules it in (approximations)
- H12 [accept] the recorder's call pause, storage floor, "Recording N" names (approximations)
- H13 [accept] the microphone-busy wording in both directions (approximation)
- H14 [accept] the next-alarm tile face (approximation) — becomes [fidelity] if R11 captured the tile
- H15 [accept] recording quality on the phone's microphone and over Bluetooth (P3)
- H16 [accept] any approximation not covered by H4–H15

## Edge cases
- Alarm firing during a call: `adb emu gsm call 5551234`, `input keyevent KEYCODE_CALL`, jump the clock → the ring page shows
  over the in-call UI (`dumpsys window`), a player exists at the reduced level (`dumpsys audio`), vibration runs; `gsm cancel`
- Alarm firing while Tess is listening (the session hides), while Start is in edit mode (edit mode ends), while Music plays
  (phase 10's player pauses on the transient focus loss and, per its E9, does not resume by itself — recorded, not hidden),
  while the recorder runs (the take continues; the alarm sound is on another stream), while glance (07) shows
- A second alarm firing while one rings (the first is missed and notified); two alarms at the same minute (one ring page, both
  listed on it); an alarm edited while it rings (the ring continues with the old sound; the store takes the edit)
- Clock moved BACK past an armed one-shot alarm's set day (`cmd alarm set-time` two days back): it stays armed for its stored
  wall-clock date and does not ring twice; clock moved forward past several alarms at once (each fires or is missed by the
  timeout rule, in order, one ring page)
- DST: `cmd alarm set-timezone America/Denver`; an alarm at 02:30 on 2027-03-14 with the clock jumped to 01:59:50 that day →
  rings at 03:00:00 MST→MDT (the skipped hour), diagnostics say so; an alarm at 01:30 on 2027-11-07 rings at its first 01:30
  (MDT) only; a daily 07:00 alarm across each night rings at 07:00 wall-clock both mornings (a 23- and a 25-hour gap in
  `dumpsys alarm`)
- Time zone change with the app closed (E8's form with the process killed first), and a zone change while the ring page shows
- Reboot straddling an alarm (E6's third pass, alarm kind): rings on boot inside the timeout, otherwise "Missed alarm"; an
  update (`adb install -r`) with an alarm 1 min ahead: `MY_PACKAGE_REPLACED` re-arms it and it rings
- `USE_EXACT_ALARM` revoked by a system (simulated: `appops set app.tileshell SCHEDULE_EXACT_ALARM deny` on a build with
  `USE_EXACT_ALARM` temporarily removed from the manifest, QA build only): the `exact_alarms` row is red, alarms arm inexactly,
  the notice is shown and spoken, and the alarm still rings within Android's inexact window
- Timer: the process dies at the deadline instant (the alarm fires into a dead process → `ReminderReceiver` starts the ring
  service); 10 timers running; a timer set to 0:00 refused; a timer paused then the clock jumped (paused timers hold their
  remaining, not a deadline)
- Stopwatch: 1,000 laps (the list stays scrollable and the dump of the top rows is available within 3 s); left running for
  24 h (P2); the app killed between two laps
- World clock: a zone id with no ICU exemplar name (e.g. `Etc/GMT+5` shows "GMT+5"); two zones with one exemplar name (both
  listed with their region); search with no match (an empty-list line in R7 §3.5.9's style, wording from R11); a device
  locale change re-labels cities without a restart
- 24-hour setting (`settings put system time_12_24 24`, restore): every time in the app and on the tile reads "H:mm"
- Calculator: every error string in Decisions; 200 !; a 1,000-digit result (from R11 §Calculator: e-notation); a display
  wider than the screen (the font steps down as Windows' does, from R11, else approximation under H11); repeated = and %
  semantics; Programmer negative numbers in BIN (two's complement at the word size); shifts by more than the word size;
  switching word size with a value that no longer fits (truncated as Windows does); paste of a non-numeric string (ignored);
  memory across `kill -9` (kept, as history); the keypad under `wm size 1080x1920` (rows stretch, no key clipped; restore
  `wm size reset`)
- Recorder: a call arriving mid-take (`adb emu gsm call`): the take pauses at RINGING (diagnostics `paused: call`), stays paused
  through OFFHOOK, and resumes on Resume after `gsm cancel`; the take's audio has no gap artefact at the pause point beyond one
  AAC frame; RECORD_AUDIO revoked mid-take (`pm revoke`): the take stops and is saved, the page shows the permission state;
  storage full (E19); the microphone taken by Tess mid-take (Tess is refused, E17) and by a third-party fixture app that starts
  capture on top (`AudioRecordingCallback` reports the shell's client silenced → the take pauses with "silenced by <pkg>");
  the screen off for 30 minutes (foreground service keeps the take; the file grows); a 60-minute take (file ≈ 30 MB, listed
  with its duration, plays and seeks); rename to an existing name (the provider appends " (1)"); rename with characters
  MediaStore refuses (refused with a notice); trim to zero length (refused); a recording deleted outside the app (`adb shell rm`
  + rescan): its row disappears through the `ContentObserver` with no restart; share with no app able to receive audio (the
  resolver's empty state, Android's); the `:recorder` process low-memory-killed between takes (nothing lost, nothing recovered)
- App list: the three apps under `wm size 720x1560` (HD+, 2 px/epx, RV10); pin each to Start and unpin; the pinned
  Alarms & Clock tile when no alarm is set (the static face) and when the next alarm is a snoozed one
- Liveness (N-01): reboot, 24 h idle, Device care, force-stop (alarms re-armed at the next start, as reminders are)

## QA evidence
_None yet._
