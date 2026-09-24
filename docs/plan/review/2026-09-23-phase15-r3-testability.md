# Phase 15 — Stage A round 3 — Reviewer 2 (testability / evidence integrity), 2026-09-23

Doc: `docs/plan/phase-15-inbox-clock-calculator-recorder.md` (1,120 lines, worktree `phase-15` at 298a9d7). Reviewer: Opus
(Fable at its limit; owner's standing approval). Device checks were read-only, on **emulator-5556 only**
(`Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1`, API 36, 1080x2340 @ 450). AOSP sources quoted below were fetched
from android.googlesource.com `refs/heads/main` on 2026-09-23. Findings already triaged in rounds 1–2 are not re-raised
unless the applied text is wrong.

---

## V1 — BLOCKING — The gate is undefined on this branch: most rows call helpers that only exist after phase 11 (and `record` only after phase 13); E28 and part of build task 3 need phase 12 (fact 2)

**Lines:** 5 (depends-on), 581–620 (preamble), 593–594 (`record`), 600–604 (ring slices), 522 / 519–521 (task 3's wizard half),
898–899 (E25), 922–949 (E27, E28); `prompts/session-phase-15.md` ("phase 15 needs nothing from 11 (depends-on 01, 02, 03, 10)",
"HARD STOP when the row is done").

**Evidence.**
- `qa/phase-03/scripts/lib.sh` (read in full) has no `ring_mark`, `ring_since`, `reply_since`, `record`; `row_end` (`:125-137`)
  saves no slices. The preamble makes *every* ring assertion go through them (l.600–604: "every ring assertion reads
  `ring_since <MARK>` … `reply_text` is `reply_since <MARK>`; `row_end` saves each named ring's slice … (the helpers are phase
  11's build task 7").
- **Who owns `record`:** C-26 (r2 triage l.200: "Owner: phase 13's build task 7"), applied in `phase-13-fluent-materials.md:254-256`.
  Phase 13 is not on the rebase target: INDEX Change Log 2026-09-23 says "its branch is rebased onto main after 11 lands … The two
  chains after them are 13 → 12 → 14 (on 11) and 16 → 17 → 18 → 20 (on 15)". So after the planned rebase `record` still does
  not exist, and rows with a recorded clause (E4's wallpaper branch l.663–664, E8's void l.721, E11's "Not enough memory"
  l.766–767, E14's `content update` l.805, E21's SCHEDULE_EXACT_ALARM clause l.869–871, E24's LOW values l.885–892) cannot run
  as written even on main-after-11. Without `record`, `row_end` fails a clause-only row (`lib.sh:131-134`).
- No wizard code (`grep -rli wizard app/src/main/kotlin` → nothing); `provision.sh` writes no marker and knows no
  `PROVISION_FINISH_WIZARD` (grep). E28 (all three parts, both steps) and build task 3's "why line in phase 12's why table …
  wizard step" (l.519–521, and T15-14's `setup:overlay` step, l.410–411, 514–515) have no host until phase 12. Rule 16 forbids
  an interim wizard or why table here.
- The phase 05 gesture driver the preamble requires for never-idle screens (l.582–585; E4b, E6, E7, E31) is an
  instrumentation APK (`app.tileshell.qa.imefixture.test`, `qa/phase-05/TOOLING.md:9,72-76`); `adb shell pm list
  instrumentation` on emulator-5556 → empty. E17's keyboard half uses phase 05 EDGE3's `kb.sh` form. Neither 05 nor 13 is in
  depends-on.
- Every rebase changes the APK, and E22 counts only slices "whose logged APK id matches this build" (l.873–874), so any branch
  run is not gate evidence after the rebase. E25's "before task 0" APK is this branch's; after the rebase the "after" APK also
  carries phase 11's code, so the 3-MB delta measures 11 + 15.

**Per row, what can run where** (ring = needs 11's helpers; rec = needs `record`; 12 = needs the wizard; 11b = needs 11's burst):

| Runs and proves on this branch now | Only after the rebase onto main-with-11 | Only once 12 (and, for rec clauses, 13) is on main |
|---|---|---|
| E2; E11 (all but the rec clause); E12; E13; E15; E16; E23; E25 (as a branch-only delta); E27's `dumpsys shortcut` half and each shortcut's page-extra launch (`am start -n <activity> --es page <id>`, page tag `selected`); the ring-free clauses of E0, E1, E3, E10 (geometry), E24 (geometry), E29 (results via `calc_date_result`), E31 (compare / expanded / pin / share); host tests (engine, calc-cases.tsv, date cases); `fill_volume` / `unfill_volume` (this phase's task 8) | E0, E1 (baseline + Music negative), E3, E4b, E5, E6, E7, E9, E10 (motion), E17, E18, E19, E20, E22, E26, E27 (burst half, 11b), E29 (`[calc] date` lines), E30, E31 (sound lines); E4, E8, E14, E21, E24 minus their rec clauses | E28 (12); task 3's two why lines + wizard steps (12); every rec clause (13, per C-26) |

**Smallest fix.**
1. Preamble, new paragraph after "Three rings": "**Parallel build (INDEX 2026-09-23).** This phase is built on branch `phase-15`
   before 11, 12 and 13 are on main. On the branch the builder runs, as smoke checks and never as gate evidence, only the
   branch column of the table above; no `lib.sh` helper owned by another phase is copied here (Rule 16). The GATE is one run of
   every row on the final rebased build: ring rows need phase 11's task 7 on main; recorded clauses need `record`; E28 and task
   3's why lines / wizard steps (`setup:full_screen_alarms`, `setup:overlay`) need phase 12. E25's 'before' is that main's APK
   without this phase, its 'after' the rebased branch's. The INDEX row is not `done` until that run."
2. Move `record` to the first phase that merges: C-26's owner becomes phase 11's build task 7 (it already owns the other four
   `lib.sh` helpers and merges first); phase 13's task 7 drops it. Otherwise depends-on gains 13 and the table's third column
   governs every rec row.
3. depends-on: `[01, 02, 03, 05, 10, 11, 12]` (+13 if 2 is not taken) — 05 for the gesture driver and EDGE3/`kb.sh`. The session
   prompt's "needs nothing from 11 (depends-on 01, 02, 03, 10)" is corrected to match.
4. Build task 3: split the wizard half out as "task 3b — after phase 12 is on main: the two why-table lines and E28"; the Setup
   rows, provision lines and FSI / overlay mechanics stay in task 3.
5. Preamble: "The gesture driver is phase 05's instrumentation APK (`./gradlew :testapps:ime-fixture:assembleDebugAndroidTest`),
   installed with its fixture before the first row that dumps a never-idle screen; its install is logged." and strike l.309's
   "(no instrumentation APK)", which contradicts l.582–585 (it meant: no instrumentation of the shell).

---

## V2 — BLOCKING — E4's "the lock screen stays visible" check compares against a screencap of the UNLOCKED screen; the wallpaper branch asserts nothing

**Lines:** 652–653, 661–664.

**Evidence.** l.652–653: "`locksettings set-disabled false`, `locksettings set-pin 1234`, a screencap of the locked screen,
`input keyevent KEYCODE_SLEEP`". Setting a PIN on an awake, unlocked device does not engage the keyguard until the screen goes
off, so that screencap is Start, not the lock screen (emulator-5556 now: `locksettings get-disabled` → `true`; `dumpsys window`
→ `isKeyguardShowing=false`). l.661–663 then requires the region below the toast to "match the locked screencap within ± 8
levels in ≥ 90 %": with the keyguard correctly visible the row fails; only a build that showed Start under the toast could
match. The lock screen under the toast also carries the ring's own notification in its stack and a different date (the clock
was jumped a day), which the 90 % rule does not model. The "keyguard hidden → wallpaper" branch is `record`ed only (l.663–664),
so a build that shows any other window beneath passes.

**Smallest fix.** Replace l.652–653's order with "… `locksettings set-pin 1234`, `KEYCODE_SLEEP`, `KEYCODE_WAKEUP`, assert
`dumpsys window` `isKeyguardShowing=true`, screencap `locked_ref.png`, `KEYCODE_SLEEP`". Replace l.661–664 with: "below the
toast: `dumpsys window windows` shows the NotificationShade (keyguard) window visible beneath the ring activity's window, and
the gesture driver's `dump.windows` lists no window of any package other than `app.tileshell` (the toast) and SystemUI; pixel
corroboration against `locked_ref.png` over the wallpaper strip between the lock-screen clock and the notification stack, ± 8
levels in ≥ 90 %. If the build-start check found the keyguard hidden beneath a translucent `showWhenLocked` activity, the
assertion is instead: the region equals the wallpaper (reference: `locked_ref.png` minus clock and stack) and `dump.windows`
lists no Start / app window — asserted, not recorded."

---

## V3 — BLOCKING — Uninstall and `pm clear` orphan the shell's recordings, so E14's uninstall step silently turns "the take" into another app's file; later rows fail by design

**Lines:** 813–815 (E14 uninstall), 824–827 (E16 trims / renames / deletes "the 5-s take"), 855–856 (E20 restore "the E14
takes deleted in the app"), 859–862 (E21 "the list shows the take and NOT other.m4a"), 363–369 (T15-3 rule), 118–119 (Q3),
591–592 (the wiped-state rule), 948–949 (E28 restore `pm clear`).

**Evidence.** AOSP MediaProvider, `MediaService.java` (main): `case Intent.ACTION_PACKAGE_FULLY_REMOVED: case
Intent.ACTION_PACKAGE_DATA_CLEARED: { … onPackageOrphaned(packageName, uid); }`; `MediaProvider.java`
`orphanEntries(...)`: `values.putNull(FileColumns.OWNER_PACKAGE_NAME); db.update("files", values, "owner_package_name=? AND
_user_id=?", …)`. So after `adb uninstall app.tileshell` (E14) or any `pm clear app.tileshell` (the wiped-state rule, E28's
restore), every recording the shell made has `owner_package_name` NULL. By T15-3 (l.364–365: rename / delete / trim "only for
files whose `OWNER_PACKAGE_NAME` is `app.tileshell`") those takes become read-only foreign rows: E16 (run after E14) finds no
`rec_bar:trim` / `rec_bar:rename` / `rec_bar:delete` on "the 5-s take"; E20's restore cannot delete the E14 takes "in the app";
E21's READ_MEDIA_AUDIO sub-row cannot show "the take" (without the grant an app sees only its own files). Q3's promise (l.119,
"they survive uninstalling the shell") holds for the file, but no row or Decision says that after a reinstall or "Clear
storage" the user's own recordings become read-only in Voice Recorder and their markers (`recordings.json`, files dir) are gone
— the T15-3 rule and Q3 together produce that, untested.

**Smallest fix.** (a) E14's uninstall sub-row uses its own throwaway take and asserts the orphaning: "after reinstall
(`provision.sh`), `content query … --where "_display_name='<take>'"` shows `owner_package_name` NULL, and Voice Recorder lists it
as another app's (hold menu `rec_menu:share` only); restore: `adb shell rm` it + `scan_volume`". (b) E16, E21 and E0 each make
their own take (or state their precondition). (c) A Decision line (Reviewer 1 / triage to word it): "A reinstall or Clear
storage orphans the shell's recordings (MediaProvider clears OWNER_PACKAGE_NAME); they then list as other apps' recordings,
read-only, and lose their markers" — or, if that is not wanted, a QUESTION for Jeremy on how the T15-3 rule should treat
orphaned files in Recordings/. (d) The wiped-state rule gains "`pm clear` also orphans every recording the shell made; a row
that needs an own take makes it after the clear".

---

## V4 — BLOCKING — E19 cannot reach its floor: `getAllocatableBytes` subtracts Android's 500 MB low-storage reserve, which `fill_volume`'s `df` target ignores

**Lines:** 249–251 (Decision: "checks `StorageManager.getAllocatableBytes` and stops at a 50 MB floor"), 570–573 (task 8's
`fill_volume`), 847–851 (E19: `fill_volume $((60*1024*1024))`, "`df /sdcard` shows ≤ 65 MB free").

**Evidence.** AOSP `StorageManagerService.getAllocatableBytes` (main): `usable = path.getUsableSpace(); lowReserved =
storage.getStorageLowBytes(path); … return Math.max(0, (usable + cacheClearable) - lowReserved);` (non-aggressive flags).
emulator-5556: `dumpsys devicestoragemonitor` → `lowBytes=524288000 fullBytes=1048576`. With `df` showing 60 MB free,
`getAllocatableBytes` ≈ max(0, 60 MB + clearable cache − 500 MB) = 0, so the recorder's pre-take check refuses at once: no
"stops by itself when the free space reaches the floor" and no partial take to list and play. The row fails for a correct
product (or passes only for a recorder that ignores its own Decision).

**Smallest fix.** Keep the API (it is the right one) and re-cut the row: "`LOW=$(adb shell dumpsys devicestoragemonitor | sed -n
's/.*lowBytes=\([0-9]*\).*/\1/p' | head -1)`; `fill_volume $((LOW + 54*1024*1024))` (its own assert: `df /sdcard` free ≤ LOW +
59 MB); start a take with the tone → it stops by itself within `(4 MB ÷ the take's byte rate) + 30 s` (the byte rate from
`[recorder] start … free=<MB>` and the AAC bitrate the build records), with the notice, `[recorder] storage floor`, and a
listed, playable partial take". Task 8's helper text gains "leave is measured as `df` free; callers that test an app-side floor
add `lowBytes`". Record the AAC bitrate in the Voice Recorder mechanics Decision so the timeout is computable.

---

## V5 — BLOCKING — E17 cannot be driven as written: the converse step taps under Tess's full-screen session, and the `:speech` line it reads does not exist

**Lines:** 830–837 (E17), 238–246 (the `holdMicrophone` Decision), 245 / 835 (the notices).

**Evidence.**
- Converse (l.834–837): "with Tess listening (`KEYCODE_ASSIST` then the hold), `rec_button` → …". Tess's session window is
  `MATCH_PARENT` and touch-modal (`cortana/CortanaSession.kt:159-160`, `:261-265` clears FLAG_NOT_TOUCH_MODAL / NOT_TOUCHABLE);
  on emulator-5556 `dumpsys window windows` lists `Window{… VoiceInteractionSession}` with `(0,0)(fillxfill) …
  ty=VOICE_INTERACTION`. An `input tap` at `rec_button`'s bounds lands in Tess's window; the recorder never tries to start, so
  "the recorder's notice" and `[recorder] refused: microphone busy owner=cortana` fail for a correct build, while "no file
  created" passes vacuously.
- First half (l.832): "the speech process's dump shows `MICROPHONE_BUSY` refused for owner `cortana` with owner `recorder`
  holding". The arbiter is keyed by binder and pid, never by name (`MicArbiter.kt:31-38`); the only refusal line is
  `SpeechService.kt:357`: `"startListening from pid=$pid refused: the microphone is listening for pid=${mic.ownerPid}"`, and
  `report(SpeechError.MICROPHONE_BUSY, "the microphone is in use by another part of the shell", owner)` (`:358`) carries no
  holder identity. So neither the `:speech` assertion, Tess's notice "naming the recorder", nor `owner=cortana` in the
  recorder's line has a defined source; the keyboard's busy text is hard-coded to the assistant (`ime/KeyboardService.kt:407`,
  "`${Brand.ASSISTANT_NAME} is using the microphone.`"), which would be wrong with the recorder holding, and E17 does not assert
  the keyboard's text at all.

**Smallest fix.**
- First half: "`speech_status mic_owner_pid` = `pidof app.tileshell:recorder` (`SpeechService.kt:481`), and the `:speech`
  slice holds `startListening from pid=<pidof app.tileshell> refused: the microphone is listening for pid=<pidof
  app.tileshell:recorder>`"; the keyboard half asserts its notice text equals the recorder wording (H13).
- Converse, phase 05 EDGE3 (a)'s route (`qa/phase-05/scripts/edge3.sh:36-50`): with the Voice Recorder page in front, `am
  broadcast -a $FIX.RECOGNIZE -p $FIX` (the ime-fixture's recogniser; the shell's recognition service takes the microphone for
  the launcher process while the page stays on top) → `speech_status mic_owner_pid` = `pidof app.tileshell` → tap `rec_button`
  → the notice, no MediaStore row, `[recorder] refused: microphone busy …`.
- The T15-4 Decision states how the holder is named across `ISpeech` (e.g. `holdMicrophone(owner, name)` storing the name
  beside the binder, and `MICROPHONE_BUSY`'s detail carrying the holder's name), and the literal refusal lines both rows read.

---

## V6 — BLOCKING — E8's online pass cannot fail: `dumpsys netstats` shows the last 30-minute poll, not live counters

**Lines:** 717–721 (E8 online pass, T15-20); also the offline pass l.709–710.

**Evidence.** AOSP `NetworkStatsService.java` (Connectivity, main): the dump returns collected stats; a live collection happens
only on the periodic poll (`getPollInterval() { return 30 * MINUTE_IN_MILLIS; }`) or when the dump is called with `--poll`
(`if (poll) { performPollLocked(FLAG_PERSIST_ALL | FLAG_PERSIST_FORCE, …); pw.println("Forced poll"); return; }`). E8 reads
`dumpsys netstats --uid` before and after a 20-s window with no forced poll, so a world clock that fetched something inside the
window reads "unchanged" — the row passes for exactly the defect it exists to catch.

**Smallest fix.** l.718–720: "`adb shell dumpsys netstats --poll` (it returns `Forced poll`, asserted), then read the uid's
rx / tx from `dumpsys netstats --uid`; … the same poll-then-read after the window". Add a positive control so the reading is
shown to move: before the window, one known request from the shell's uid (the Weather refresh the doc already voids on, forced
through its tile) moves the counters after a poll; then the world-clock window must not.

---

## V7 — BLOCKING — Microphone and reply-audio rows read the wrong audio on this host: both emulators and the desktop share one PipeWire, and the AVD's capture stream is not on `vmic`

**Lines:** 308–309, 608–610 (microphone rows use `audio.sh`), E14 l.794–801, E15, E17 l.832–833, E18, E19, E26 l.911 (reply
RMS), E30, E31 (the ring sound).

**Evidence (host, read-only, 2026-09-23):** `pactl list sink-inputs` → two `qemu-system-x86_64` streams (emulator-5554 and
-5556) and a `Firefox` stream playing; `pactl list short source-outputs` → both qemu capture streams attached to source **374
`easyeffects_source`** (the desk microphone chain), not **2158 `vmic.monitor`**, although `pactl get-default-source` →
`vmic.monitor`. `audio.sh setup` only sets the default source (`:35`), which does not move an existing stream; `audio.sh
record` captures the whole default sink's monitor (`:73-74`), i.e. both emulators plus the desktop. `qa/phase-15/STATE.md` notes
the sharing; the doc does not, and the device lock is private per session (TMPDIR), so nothing serialises the two sessions'
audio. Consequences: E26's reply check (RMS > −40 dBFS over the reply window) passes on the other emulator's or Firefox's audio
with Tess mute; E14's tone take can record the room instead of the tone, and its silent take (RMS < −60 dBFS) can fail on room
noise; a `paplay -d vmic` by the phase 11 session is heard by this AVD.

**Smallest fix.** Preamble, "Audio preconditions (asserted before every microphone or reply-audio step)": "(1) this AVD's qemu
capture stream (the source-output whose `application.process.id` is emulator-5556's pid) is on `vmic.monitor` — moved with
`pactl move-source-output <id> vmic.monitor` if not, then re-checked; (2) reply audio is captured from this AVD's own qemu
sink-input only (`parecord --monitor-stream=<its sink-input id>`), never the default sink's monitor; (3) no other qemu stream
is un-corked during the step (`pactl list sink-inputs` / `source-outputs`), else the step waits or the row fails its
precondition." The `audio.sh` changes are a `qa/phase-03` ADD owned by this phase's task 8 (first to need two emulators).

---

## V8 — SHOULD-FIX — Q-E is ruled A (fact 1): these texts are dead or wrong; the A-only replacements

Jeremy's ruling (l.100–105) is not reopened. Every item below still branches on B / C or says "pending":

| Line(s) | Now | Replace with |
|---|---|---|
| 4 | "Q-E (how alarms and timers ring) is with Jeremy — T15-14's ring rows are written for each answer, the lean first, marked 'Q-E: A'" | "Q-E ruled A 2026-09-23 (Decisions); the ring rows are written for A only" |
| 78–80 | "**Pending Q-E, under A only (T15-14):**" | "Q-E: A (ruled 2026-09-23; T15-14):" |
| 100–103 | "on the keyguard and over whatever app is in use, drawn as an overlay window" | "… over whatever app is in use as an overlay window, and on the keyguard through the full-screen-intent activity (T15-14's A) — an application overlay does not draw over the keyguard" |
| 262–266 (Bars) | "under A / B … under C the page draws …" | keep the A sentence with "under A / B" struck; strike the C sentence |
| 322–323 | `[alarms] surface: toast-locked \| toast-overlay \| heads-up \| page` | drop `\| page` |
| 399–418 (T15-14 line) | "which is pending with Jeremy"; "**A (lean; written first in every row):**"; the **B:** and **C:** sentences | "ruled A by Jeremy 2026-09-23"; "**A (ruled):**"; strike B and C (keep the reason sentence) |
| 504–505 (task 1) | "Q-E: A, under A only, `SYSTEM_ALERT_WINDOW`" | "`SYSTEM_ALERT_WINDOW` (Q-E: A)" |
| 511–517 (task 3) | "**Q-E: A (ruled; the B / C branches below are not built):** under **A** (lean) … under **B** … under **C** … (was …)" | "the ring surface (Q-E: A): the translucent … activity … and a `TYPE_APPLICATION_OVERLAY` window … the Setup row `setup:overlay` … the provision line" — B, C and "(lean)" struck |
| 644–645 (E2) | "the ring surface's activity — Q-E: A, the toast activity under A / B, the page under C —" | "the ring surface's toast activity" (and see V12 on whether it is exported at all) |
| 658 (E4) | "**Under Q-E A or B (lean):**" | unconditional |
| 664–665 (E4) | "**Under Q-E C:** … same `ring_*` nodes." | strike |
| 674 (E4b) | "**Under Q-E A (lean):**" | unconditional |
| 682–684 (E4b) | "**Under Q-E B or C:** the heads-up half above is the whole row (…). Timer kind, every answer:" | strike the B / C sentence; "Timer kind:" |
| 696–697 (E6) | "on the surface E4b names for a phone in use under the Q-E answer (Q-E: A; the timer form …)" | "on the in-use overlay toast (`[alarms] surface: toast-overlay`; the timer form: `ring_dismiss`, no `ring_snooze`)" |
| 754–756 (E10) | "The ring surface, Q-E: A: under A / B the alarm toast … ; under C the page with the drawn bars (H5 [accept])" | "The ring surface: the alarm toast of E4 and the timer toast — …" (C clause struck) |
| 867 (E21) | "**Pending Q-E, under A only:**" | strike the qualifier |
| 880–884 (E23) | "under A / B, over the keyguard … ; under C, on the page … (was …)" | A sentence unconditional; C sentence struck |
| 946–947 (E28) | "**Pending Q-E, under A only (T15-14):**" | strike the qualifier |
| 987–990 (P1) | "(Q-E: A: the toast under A / B, the page under C)"; "(under A the overlay toast … ; under B / C the heads-up)" | "the locked toast"; "the overlay toast once 'Display over other apps' is granted on One UI (its grant state recorded), and the heads-up with the grant off" |
| 1008 (H1) | "Q-E: A under A / B, the timer toast" | "the timer toast" |
| 1020–1026 (H5) | "under **A / B** … under **A** the in-use overlay toast the same; under **B / C** the in-use heads-up … ; under **C** [accept] the full-screen page …" | "[fidelity] against §8 — the toast form (8.1, HIGH) and the timer toast (8.9–8.11, MEDIUM), locked and as the in-use overlay; the alarm toast's geometry (8.2–8.6, LOW, U8) and the strings (U2) as tagged approximations; the lock screen beneath or the wallpaper per the build-start check. [accept] the heads-up **fallback** without 'Display over other apps' (Android's own look, a P2 seam Jeremy accepts or not)" — the fallback still exists under A and must keep its H cover |
| 1063–1064 (edge case) | "(Q-E: A: the overlay toast under A, the heads-up under B / C — E4b's checks" | "(the overlay toast — E4b's checks" |
| E22 (l.872) | covers `[alarms] surface: … page` implicitly | E22's pattern list excludes `page` (see V14) |

---

## V9 — SHOULD-FIX — MARK-by-wall-time slicing breaks across clock jumps: lines stamped on a jumped clock pollute every later slice

**Lines:** 600–603 (C-20 in the preamble), 610–613 (clock jumps), E4 l.651–670, E21 FSI sub-row, edge cases l.1070–1076.

**Evidence.** Each ring line's `wall=` is `System.currentTimeMillis()` at write time (`diag/Diagnostics.kt:25,40`); `ring_since`
keeps lines with `wall ≥ MARK` (j7.sh's filter, `qa/phase-03/scripts/j7.sh:16-25`). E4 writes its lines at tomorrow 09:05–09:30,
then restores the clock to now; until the launcher process dies, every later MARK (now) is earlier than those lines, so they
sit in every later launcher slice. `[alarms] surface: <form>` carries no id (l.322–323), so a re-run of E4 in the same process
(MARK tomorrow 09:04:50) passes its `surface: toast-locked` assertion on the first run's line whatever the re-run showed.
Backward jumps invert it: the edge case "`cmd alarm set-time` two days back … does not ring twice" takes its MARK two days in
the past, so its slice is the whole ring (every earlier `[alarms] fired`) and the absence check fails for a correct build.

**Smallest fix.** (1) `[alarms] surface: <form> <id>` (the alarm or timer id), and every row matches its own id. (2) Preamble,
after the ring-slice rule: "A row that moved the clock ends, after RV12's clock restore, with `am force-stop app.tileshell` +
Home (all three rings reset; alarms re-arm at process start, phase 03 E6's rule), so no line stamped on a jumped clock outlives
the row. A step after a BACKWARD jump slices by position, not time: `ring_mark` also records the ring's last line, and
`ring_since` returns the lines after it" — the second clause is a C-20 change to phase 11's helper, raised for the triage.

---

## V10 — SHOULD-FIX — Rows leave alarms and timers armed; E6's exact counts and later rows' rings fail in order (RV12)

**Lines:** E3 646–650 (no restore), E4 669–670 (restore lacks the two alarms; "a daily alarm dismissed instead re-arms for the
next day"), E4b 685, E5 686–689 (its alarm is never answered: it rings for 10 min into the restore), E8 715–721 (the 07:00 alarm
and the running timer), E9 722–732 (the Tess alarm and the 5-min timer, "Restore the PIN" only), E21 (E4's route again),
E6 l.700 (`[alarms] rearm (boot): 0 alarms, 1 timers`).

**Evidence.** RV12 (PLAN.md:329-335) requires each row to restore what it changes. As written, E3's now + 3 min alarm rings
three minutes into the next row; E9's 5-minute timer rings mid-E10; E5's alarm is still ringing when E6 starts; and E6's exact
`0 alarms, 1 timers` fails once any earlier alarm is left in the store.

**Smallest fix.** Preamble: "The baseline has no alarm, timer or running stopwatch in the shell (asserted at row start:
`dumpsys alarm | grep -c 'app.tileshell'` for alarm-clock / timer entries = 0 and `run-as app.tileshell cat files/alarms.json
files/timers.json` empty). Every row that creates one deletes it in its restore through the app and re-asserts the count." E5
gains `ring_dismiss` before its restore; E3, E4, E4b, E8, E9 list the deletes.

---

## V11 — SHOULD-FIX — Fixture recordings are consumed out of order: E0 uses a take that does not exist yet, E20 deletes files E21 / E30 / E31 need, and MUSIC6's exact counts break with song.m4a present

**Lines:** 627 (E0 "E16's take"), 815 ("The two pushed files stay for E20, which runs next and removes them"), 853–856 (E20
restore `rm … other.m4a … song.m4a`; "phase 10's MUSIC6 re-run passes on the same build"), 859 (E21 "with E14's other.m4a
present"), 966–967 (E30 filter "with E14's other.m4a present"), 980 (E31 "with E14's song.m4a present").

**Evidence.** E0 runs first and E16 deletes its own take (l.826–827). E20 removes other.m4a and song.m4a before E21, E30 and E31
need them. MUSIC6 asserts exact counts — `qa/phase-01/scripts/music6.sh:52` "three albums", `:60` "three artists", `:70` "all six
songs" — so re-running it while the untagged song.m4a is in Music fails for a correct build.

**Smallest fix.** One pair of helpers in task 8, `push_fixture_recordings` (other.m4a to Recordings/, song.m4a to Music/, scan)
and `remove_fixture_recordings` (rm + scan); every row that needs them pushes at its start and removes at its restore (E0 plays
the pushed other.m4a — other apps' recordings play, T15-3). E20: "MUSIC6 re-run after `rm song.m4a` + scan, with the takes and
other.m4a still present (so the exact counts also prove the IS_RECORDING skip)".

---

## V12 — SHOULD-FIX — E2 fails today for a reason outside this phase, and its ADD list names components that need not be exported

**Lines:** 643–645, 32–33 (Scope "ADDs to phase 03's exported allow-list").

**Evidence.** `python3 qa/phase-03/scripts/exported.py app/build/outputs/apk/debug/app-debug.apk
qa/phase-03/exported-allowlist.txt` on this worktree's APK → `EXPORTED BUT NOT ALLOWED: app.tileshell.music.MusicActivity,
app.tileshell.music.MusicService` (the lines phase 10's gate owes, INDEX Change Log 2026-09-22 "phase 10's gate owes the two
lines"). So "phase 03's E5 still passes" is false before this phase adds anything. E2 also ADDs "the ring surface's activity"
and "the recorder service": a PendingIntent's full-screen target and a `dumpsys activity service` route need no export —
`SpeechService` is `android:exported="false"` (`AndroidManifest.xml:235-238`) and `speech_dump` reads it — and the allow-list
fails a listed component that the device does not export (its header).

**Smallest fix.** E2: "matches the allow-list after this phase's ADDs — the three launcher activities — and phase 10's two owed
lines (MusicActivity, MusicService; recorded here because E5 cannot pass without them); the ring activity and `RecorderService`
are `exported=false` and E2 asserts they are absent from the exported set." (If Reviewer 1's trust review decides otherwise,
the list follows that Decision.)

---

## V13 — SHOULD-FIX — E1 reads and taps the three rows through a tag all six in-APK apps share

**Lines:** 632–634 (E1), 336–338.

**Evidence.** The app-list row tag is `"${item.tagPrefix}:$pkg"` with `pkg = item.entry.component.packageName`
(`applist/AppListPage.kt:464,476`; `AppListModel.kt:44-45`), and the label is an untagged child `BasicText` (`:479-481`).
Music, Start settings, Weather and the three new apps all carry `applist_row:app.tileshell`; `tap_node` hits the first match
(`lib.sh:307-317`), so "each row's hold menu offers Pin to Start and NOT Uninstall" can be performed three times on Music's row,
and "Alarms & Clock under A" is text read off a parent (the MUSIC8 defect). Also: the jump-grid clause "marks A, C and V
available" cannot fail for A and C — Auxio, Aves, Calendar, Camera, Clock and Contacts already populate them on this AVD
(`cmd package query-activities … LAUNCHER`); and `qa/phase-02/scripts/regress.sh` (l.634, l.337) is phase 02's Start-gesture
regression (tap, pivot, scroll, Home), not an app-list walk.

**Smallest fix.** Task 1: in-APK rows keep `applist_row:app.tileshell` (existing drivers) and the label gains
`applist_name:<component short form>`; E1 finds each row by that tag and taps its bounds. Jump grid: "V becomes available (it
has no other app on this AVD), and each row sits in its letter group". Replace "regress.sh's pattern" with "phase 01 E12's walk
(`qa/phase-01/scripts/e12_part1.sh`: every row, the headers, the New captions) re-run on this build".

---

## V14 — SHOULD-FIX — E22 either passes on prefixes or fails by design: several lines are only produced by edge cases, the recorder ring is gone when `row_end` saves, and its file name is undefined

**Lines:** 872–876 (E22), 310–325 (the line list), 1062–1117 (edge cases), 596–598 (the `:recorder` ring "answers while the Voice
Recorder page is open").

**Evidence.** Lines only an edge case produces: `[recorder] paused: call` (l.1099), `paused: silenced by <pkg>` (l.1103),
`[alarms] ring ended <id>: superseded` (l.1068) and `missed`; `ring ended … timeout` is produced by nothing (V15). E22's union is
"E0–E21, E4b and E26–E31" only. `row_end` saves each ring at the row's end; when a recorder row ends with the page closed the
service is unbound, `dumpsys activity service app.tileshell/.recorder.RecorderService` finds no running service, and
`ring-recorder.txt` is empty. C-20's `row_end` names files `ring-<name>.txt` after the ring argument; for a component ring that
is `app.tileshell/.recorder.RecorderService`, not `recorder`.

**Smallest fix.** E22: "each alternative of each line (the `surface: page` form struck, V8) is found at least once in the union
of `qa/phase-15/*/ring-*.txt`, the edge-case drivers' slices included (they save slices like any row); the table names the row or
edge case that produces each". Preamble: "a recorder row ends with the Voice Recorder page open, or saves `ring_since <MARK>
app.tileshell/.recorder.RecorderService` to `<row>/ring-recorder.txt` before leaving it"; the helper maps a component ring to its
short name (`recorder`) — a note for phase 11's task 7.

---

## V15 — SHOULD-FIX — No row proves the ring timeout (an unanswered alarm stops after 10 minutes and is missed)

**Lines:** 187 (Decision), 311 (`ring ended … timeout|missed`), H6 l.1027.

**Evidence.** H6 accepts the 10-minute value, but no E row or edge case lets an alarm ring unanswered to its end; the timeout
runs on the ring service's own timer, which a clock jump does not shorten. A build whose alarm rings forever passes every row.

**Smallest fix.** E5 (or a new E32): "the alarm unanswered → at 600 ± 5 s after the `[alarms] fired` line's `uptime`, the
`dumpsys audio` player is gone, `[alarms] ring ended <id>: timeout`, and `dumpsys notification --noredact` holds 'Missed alarm
h:mm'".

---

## V16 — SHOULD-FIX — The "tap jumps" / "one-frame cut" checks are graded by the app's own log of a non-animation, and several motions the phase animates have no value, row or H row

**Lines:** 738–741 (E10), 895–897 (E24), 376–381 (C-5 names "the ring surface's entrance, any R11-measured transition").

**Evidence.** `[motion] clock_tab … settle ≤ 33.4 ms` and `[motion] rec_state … settle ≤ 33.4 ms` are written by the code under
test for a change that has no animation; nothing defines where `t0` and `settle` start and end, so a build that slides for
250 ms can log `settle=0`. The corroborating "first screencap after shows the underline under the new tab" is taken hundreds of
ms later and matches a slide too. Unwritten motion values: the toast entrance (r11/clock.md U12: "toast entrance: R7 §4.5.1's
volume-panel grow-down 217 ms, tagged"), the flyouts / Snooze dropdown (U12: R7 §3.6.4 grow-from-anchor 233 ms), Calculator's
hamburger pane (r11/calculator.md M.1 "≈167 ± 50 ms", LOW) — `grep 217\|233\|167` on the doc finds none; no row asserts them
and no H row names them, so the builder picks.

**Smallest fix.** E10 / E24: "a screenrecord of the tap under phase 05's frame-spacing rule shows no intermediate frame: the
first source frame that differs from the pre-tap frame in the content region equals, ± 8 levels, a still screencap taken 1 s
later" (the pixel record is primary for a jump; the `[motion]` line is secondary). Decisions (Fidelity): "toast entrance
grow-down 217 ms (U12, tagged), flyout / dropdown grow 233 ms (U12, tagged), Calculator pane slide ≈167 ms (M.1, LOW)"; E4b
asserts `[motion] ring_toast` settle 217 ± 17 ms with `maxGapMs` ≤ 33.4; E12 asserts `[motion] calc_pane`; H1 / H2 name them.

---

## V17 — SHOULD-FIX — E15's foreground-type assertion matches a string `dumpsys` never prints

**Lines:** 816–818.

**Evidence.** AOSP `ServiceRecord.java` (main) dumps `isForeground=… foregroundId=… types=0x%08X foregroundNoti=…`
(`pw.printf(" types=0x%08X", foregroundServiceType)`); there is no `foregroundServiceType=microphone`. The row fails for a
correct build (or a driver is changed to pass on the process name alone).

**Smallest fix.** "… `isForeground=true` and `types=` with the microphone bit set (`0x00000080`, `FOREGROUND_SERVICE_TYPE_MICROPHONE`)".

---

## V18 — SHOULD-FIX — E14's MediaStore query uses a column that does not exist

**Lines:** 796–797, 825 (E16 "`display_name` Standup.m4a").

**Evidence.** emulator-5556: `adb shell content query --uri content://media/external/audio/media --projection
_id:display_name:duration:is_recording:relative_path:owner_package_name` → `java.lang.IllegalArgumentException: Invalid column
display_name`. With `_id:_display_name:…` the same query runs (`No result found`, rc 0). E14's own `content update` already
uses `_display_name` (l.805).

**Smallest fix.** `display_name` → `_display_name` in E14's projection and E16's rename check.

---

## V19 — SHOULD-FIX — Call detection depends on a permission the manifest does not get

**Lines:** 190 (in-call alarm level, "`TelephonyManager` call state OFFHOOK"), 248 ("A call (`TelephonyManager` RINGING then
OFFHOOK) pauses the take"), 71 (manifest ADDs), edge cases l.1063, 1099; P4.

**Evidence.** `dumpsys package app.tileshell` on emulator-5556 lists no `READ_PHONE_STATE`; the ADD list (l.71) has none. For
targetSdk 36 (`app/build.gradle.kts:22`), call-state listeners (`TelephonyCallback.CallStateListener`) and
`TelephonyManager.getCallState()` require READ_PHONE_STATE, so the `adb emu gsm call` edge cases produce no `[recorder] paused:
call` and no in-call level for a build that follows the doc.

**Smallest fix.** Either the manifest ADD `READ_PHONE_STATE` with its provision line (`pm grant`), Setup row and phase 12 why
line, or the Decisions use `AudioManager.OnModeChangedListener` (`MODE_RINGTONE` / `MODE_IN_CALL`, no permission) — Reviewer 1
/ triage to choose; the edge rows then name the observable.

---

## V20 — SHOULD-FIX — E26's expected sentences have no host-side rule, and its conversion expects British spelling from an en-US unit table

**Lines:** 353–356 (the reply form), 903–904, 914, 900–902 (tess column "host-computed").

**Evidence.** The restatement mixes symbols and words with no stated rule: "15 % of 80 is 12.", "2 plus 2 is 4.", "√81 is 9.".
The host can compute the number but not the sentence, so the `tess` column's text will be copied from the app's output
(self-grading). "5 miles is 8.04672 kilometres." and utterance "What is five miles in kilometres?": unit names come from the
Converter's tables (l.350) and this AVD's locale is `en-US` (`getprop persist.sys.locale`); microsoft/calculator
`src/Calculator/Resources/en-US/Resources.resw:2176-2177` `UnitName_Kilometer` = "Kilometers" (en-GB's is "Kilometres",
`en-GB/Resources.resw:1788-1789`).

**Smallest fix.** Decisions (T15-2) state the rule: "the expression is restated with `+ − × ÷` for the four operators… (or: in
the words the matcher accepted) … `%` as '%', roots as '√'", and calc-cases.tsv's `tess` column is generated from that rule on
the host. E26: "5 miles is 8.04672 kilometers." (en-US table), and the matcher accepts both spellings.

---

## V21 — SHOULD-FIX — E6's third pass ("reboot straddling its deadline") is nondeterministic

**Lines:** 700–702.

**Evidence.** "a 1-min timer, reboot straddling its deadline … after boot it fires at once with the 'ended while the phone was
off' notification". An `adb reboot` started right after the timer can complete before the deadline, in which case the timer
fires normally and the "ended while off" text is absent for a correct build.

**Smallest fix.** "Start a 1-min timer; when its `[timer]` line reads remaining ≤ 10 000, `adb reboot`; precondition, asserted:
the first post-boot `adb shell date +%s%3N` is later than start + 60 000; then the fire-at-once and notification checks."

---

## V22 — SHOULD-FIX — E0's routing proof leans on rows that have never had a driver, and its notification negative has no positive control

**Lines:** 499–501 (task 0 "phase 10 E10 / E13 re-run"), 622–631 (E0), 639–642 (E1 "phase 10's E10 and E13 pass on the same build").

**Evidence.** INDEX row 10 (l.58): "The gate still owes … E10-E13 the tile rule" — no emulator driver exists
(`qa/phase-01/` has MUSIC6–10, MUSIC17 only), so "re-run" has nothing to run. "play in Auxio (a pinned tile)": phase 02's
baseline, from which this phase's derives (task 8), pins no Auxio tile (`qa/phase-02/baseline_layout.json` order: PEOPLE,
BROWSER, MAIL, CALENDAR, PHOTOS, weather, STORE, MAPS, MUSIC, settings). The second half ("no in-APK tile shows a count or
content from the shell's own notifications") passes if the listener never took the notifications in; the listener publishes
the shell's ongoing, non-"miscellaneous" notifications (`feeds/TileNotificationListener.kt:58-68`), so a positive control is
available.

**Smallest fix.** Task 0 writes `qa/phase-15/scripts/e0.sh` as the emulator driver for phase 10 E10–E13's tile rule (E0 is that
driver; "re-run" struck). The baseline pins Auxio too (`app:org.oxycblt.auxio/.MainActivity:0`, in `manualSizes`). E0's
negative first asserts the listener saw them: `diag` prints `listener connected=true` and `badges=` holds `app.tileshell` with a
count ≥ 1 (`TileNotificationListener.kt:88-90`), then no in-APK tile shows the count or text.

---

## V23 — NOTE — Rows that kill or reboot mid-row lose the pre-kill ring and have no stated clock for "continuous"

**Lines:** E6 692–695 ("90 − elapsed ± 2 s"), E7 706–708 ("elapsed continuous (± 1 s)", "± 2 s over the reboot").

**Evidence.** `kill -9` / `adb reboot` empties the in-memory ring (`Diagnostics.kt:19`), and `row_end` saves only at the end,
so the pre-kill `[timer]` / `[stopwatch]` lines the comparison needs are gone; neither row says whether "elapsed" is host time
or ring time. The in-process checks (`Δelapsed = Δuptime`) pass by construction if the logged `elapsed` is computed from the
same `uptimeMillis` in the same call.

**Smallest fix.** "Before the kill / reboot, save `ring_since <MARK> launcher` to `<row>/ring-launcher-prekill.txt`; continuity =
the first post-restart line's `elapsed` − the last pre-kill line's `elapsed` equals the two lines' `wall=` difference ± 1 s
(± 2 s over a reboot); the logged `elapsed` is read from the same state the display renders."

---

## V24 — NOTE — Small row defects

- **E3 l.650:** "(dump `alarm_time:` shows 'Tomorrow')" — `alarm_time:<id>` is the time; the day text needs its own tag
  (`alarm_repeat:<id>`, add to Harness contracts) and the real check is `dumpsys alarm`'s Next alarm clock = tomorrow HH:MM.
- **E5 l.688:** "(E4's checks)" includes "the keyguard still showing", but E5 sets no PIN (this AVD's keyguard is disabled).
  Name the subset: fired line `late ≤ 1000`, `dumpsys audio` player on ALARM, display on.
- **E9 l.723:** "set an alarm for 7 30 a m" has no utterance id; `utterances.py:40` `alarm` is "Set an alarm for seven twenty
  AM." — use it and expect "Alarm set for 7:20 AM." (`ActionLayer.kt:320`), or add an id to E26's list. "(E10's route …)" at
  l.730 means phase 03 E10.
- **E11 l.769:** "the clipboard paste of '12+3'" — this image has no `cmd clipboard` (`adb shell cmd clipboard help` → "No shell
  command implementation."). Name the route (`input text 12+3` into Fossify Notes, `input keycombination KEYCODE_CTRL_LEFT
  KEYCODE_A`, then `… KEYCODE_C`) and a `calc_paste` tag. "80 + 15 % → 92" lacks the `=` its Decision has (l.221).
- **E16 l.823:** "the scrubber's thumb … moves between two dumps" — a slider's position is not in a uiautomator node's
  attributes; tag the position text (`rec_position`) or a thumb node. "a query mid-edit shows both" names no observable instant.
  "the resolver … with a `content://` stream": `dumpsys activity activities` prints extras as "(has extras)" (seen on
  emulator-5556), so take the URI from the take's MediaStore `_id`. (Not verified on-device: it needs an activity start.)
- **E21 l.857–862:** `pm revoke` kills every app process; say "reopen Voice Recorder after each revoke". l.869–871: make "an
  alarm still arms with `setAlarmClock`" an assert (Next alarm clock lists it); only the appop's own effect is `record`ed.
- **E30 l.958:** "the timer text equal in two dumps" — no tag for the record state's timer (add `rec_elapsed`).
- **E4b l.672:** DeskClock targets SDK 30 with POST_NOTIFICATIONS `granted=false` (`dumpsys package com.android.deskclock`), so
  its first launch may raise the notification prompt; the row's "DeskClock resumed" check catches it but the setup should
  `pm grant com.android.deskclock android.permission.POST_NOTIFICATIONS` (restored).
- **Edge case l.1064:** "a player exists at the reduced level (`dumpsys audio`)" — the player line carries no volume
  (emulator-5556's `players:` lines: piid, type, u/pid, state, attr, sessionId, mutedState); read the track gain from `dumpsys
  media.audio_flinger`, as MUSIC17 did.
- **l.182, l.613–614:** "`appops get … USE_FULL_SCREEN_INTENT` must read allow" / "the AVD ships with it allowed" — on this
  image a declared, never-set op reads `No operations. Default mode: default` with the permission `granted=true` (DeskClock and
  Dialer, emulator-5556); it reads `allow` only after the provision line. Record both `appops get` and `dumpsys package`'s grant.
- **E1 l.637–638:** the baseline's "ZERO `-> assigned`" check gains its positive control: the slice holds
  `assignSlotOnce slot:music:v1 MUSIC -> … -> already run` (`tiles/LayoutStore.kt:137`).

---

## V25 — NOTE — Stale cites a builder will follow

- l.228: "CalcViewModel's `UnitConverterDataLoader.cpp`" — at microsoft/calculator 4fd3fc5 the unit tables are
  `src/Calculator.ViewModels/DataLoaders/UnitConverterDataLoader.cs` and the date engine
  `src/Calculator.ViewModels/Common/DateCalculator.cs` (the host generators of calc-cases.tsv read these; `qa/phase-15/STATE.md`
  already notes it).
- l.604: "the `wall=` filter is `qa/phase-03/scripts/j7.sh:13-15`'s" — lines 13–15 are `cortana_assist` and a comment; the
  MARK and filter are `:16-25` (same cite in C-20 / phase 11).
- E10's swipe asserts `[motion] pivot` (l.740–741); phase 13's task 7 also ADDs `[motion] pivot` "where their phases do not log
  them yet" (`phase-13-fluent-materials.md:247-249`). With 13 and 15 on separate chains, name one owner of the shared pivot's
  line so it is not written twice (Rule 16).

---

BLOCKING: 7 · SHOULD-FIX: 15 · NOTE: 3
