# Phase 15 — Stage A round 3 — Reviewer 1 (design / correctness), 2026-09-23

Reviewer: Opus (Fable at its limit; the owner's standing approval). Brief: review/2026-09-23-phase15-r3-brief.md.
Doc: docs/plan/phase-15-inbox-clock-calculator-recorder.md at 298a9d7 (1,120 lines, read in full). Line numbers below are
that file's unless another file is named. Emulator checks: emulator-5556, read-only (outputs quoted). Anything that needs a
state change to confirm says so.

---

## BLOCKING

### D1 · BLOCKING · The parallel build leaves the gate undefined (helpers from 11 and 13, E28 from 12)
**Lines:** 5; 342; 71-74; 518-522 (task 3); 589-604 (preamble); E27 922-935; E28 936-949.
**Evidence.**
- Header l.5 `depends-on: [01, 02, 03, 10, 11, 12]`, but Decisions l.342 still says "depends-on drops 17 and is
  `[01, 02, 03, 10]`", and INDEX Change Log 2026-09-23 plus prompts/session-phase-15.md say "it needs nothing from 11
  (depends-on 01, 02, 03, 10)". Three texts, two answers.
- Preamble l.600-604: every ring assertion reads `ring_since` from a `ring_mark`, replies are `reply_since`, `row_end` saves
  slices — "the helpers are phase 11's build task 7" (phase-11 l.254-257). `qa/phase-03/scripts/lib.sh` on this branch has
  none of them (its functions: take_device_lock, wake_device, row_begin, log, note, _verdict, assert_*, row_end, diag,
  speech_dump, speech_status, scroll_to_node, dump_ui, screencap, bounds, node_text, has_node, cortana_*, ensure_start, say,
  type_request, reply_text, replies_since, installed_apk_id, apk_matches, cortana_listen, tap_node). So no row with a ring,
  reply or slice clause can reach a verdict here: E0, E1 (baseline), E3-E10, E14, E17-E22, E24, E26, E27, E29-E31.
- `record` (l.592-594; used by E4 l.664, E8 l.721, E11 l.766, E14 l.805, E21 l.869-870, E24 l.885 / 892, P1 l.992) is owned by
  phase 13's build task 7 (phase-13 l.254-256: "13 is their first user in the Build order"). 13 builds after 11 in the
  13 → 12 → 14 chain, so `record` is NOT on main when this branch is rebased after 11: those clauses cannot run at the
  rebase either. With 15 built beside 11, 15 is now `record`'s first user in time.
- E28 needs phase 12's wizard, `PROVISION_FINISH_WIZARD=0` and the finished marker (phase-12 l.36, 224, 361-363). None
  exists (no wizard code; provision.sh writes no marker). Phase 12 builds after 13, i.e. after 15's gate must close, and
  phase-12 l.707 says each E14 instance is "run in its own doc on its build" — impossible for 15. Phase 12's why table
  (phase-12 l.240-262) has **no** `setup:full_screen_alarms` row and only a placeholder for `setup:overlay` (l.253), so the
  wizard step this doc says it adds (Scope l.71-74, task 3 l.519-521) has nothing to be built from — and building a why
  table or step here would be an interim part of phase 12's wizard (Rule 16).
- E27 needs phase 11's burst (`quick_sat_label:*`) and its `[quick] shortcuts for …` line.
**Fix.**
1. l.5 → `depends-on: [01, 02, 03, 10, 11]   # 11: lib.sh's ring helpers (C-20) and E27's burst. Built beside 11 on branch
   phase-15 (INDEX 2026-09-23); its gate runs on main after 11 lands. E28's wizard instance runs in phase 12's gate
   (Decisions "Parallel build")`. l.342: strike "depends-on drops 17 and is `[01, 02, 03, 10]`" → "(depends-on: see the
   header, C-23 and 'Parallel build')".
2. New Decision: "**Parallel build (2026-09-23 clearance).** Every build task is built on branch phase-15, except anything of
   phase 12's: no wizard step and no why-line table here — the two new rows' why lines are written into phase 12's table and
   phase 12 builds their steps. Drivers are written once against phase 11's helper API (`ring_mark`, `ring_since`,
   `reply_since`, `row_end`'s slices), never an interim copy, and reach a verdict only after the rebase. On the branch the
   builder may run, as development checks with no gate verdict, the rows that read no ring and need no helper from another
   phase: E2 (after D8), E11 (bar its `record` clause), E12, E13, E16, E23, E25 and E27's `dumpsys shortcut` clause. The
   gate is the full E0-E31 run on main after the rebase. `record <name> <value>` moves to this phase's build task 8 (15 is
   now its first user; phase 13's build task 7 drops it), written against phase 11's `row_end`. E28 moves to phase 12's gate:
   phase 12 E14's instance list gains 'phase 15's `setup:full_screen_alarms` and `setup:overlay`, run at phase 12's build
   because 15 was built first', and task 3's 'phase 12 E1 re-run on this build (E28)' goes with it."
3. Scope l.72-73 "and, with it, one wizard step in phase 12's form (`wizard_step:setup:full_screen_alarms`, C-4; E28)" →
   "whose wizard step phase 12 builds from its why table (C-4; run in phase 12's gate)". Phase 12's why table gains both rows
   (text in D5).
4. The lead's texts: INDEX Change Log "(it needs nothing from 11)" and session-phase-15.md "(depends-on 01, 02, 03, 10)" →
   "its build needs nothing from 11; its gate runs after 11 lands".

### D2 · BLOCKING · Alarms are not re-armed after a reboot until the owner unlocks (no direct-boot handling)
**Lines:** Goal 14-15 ("after a reboot"); Scope 41-44; Decisions 165-175 (re-arm on `BOOT_COMPLETED`); task 2 l.506-508;
E6 l.697-702; E7 l.706-707; P2 l.994-995; edge l.1078.
**Evidence.** `AndroidManifest.xml:269-274`: `ReminderReceiver` receives only `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`; no
component is `directBootAware` and nothing uses device-protected storage (grep of the manifest and app/src/main/kotlin:
none). The AVD is file-based-encrypted: `getprop ro.crypto.type` → `file`, `ro.crypto.state` → `encrypted`. With a lock
credential set, Android sends `BOOT_COMPLETED` only after the user's first unlock, and task 2 keeps alarms.json /
timers.json in the app's (credential-encrypted) files directory. After an overnight restart (One UI's automatic restart, an
update) no shell alarm is armed until the owner unlocks, so the morning alarm is silent. E6's and E7's reboot passes run under
RV12's baseline "no lock set", where CE storage unlocks at boot, so they pass while the phone fails. (AOSP DeskClock is
directBootAware for exactly this.)
**Fix.** Decision "Direct boot": the alarm / timer receiver, the ring service and the ring activity are
`android:directBootAware="true"`; alarms.json, timers.json and stopwatch.json live in device-protected storage
(`createDeviceProtectedStorageContext()`); re-arm also on `LOCKED_BOOT_COMPLETED`; `ShellApp.onCreate` runs its launcher
start-up only once `UserManager.isUserUnlocked()` (else on `ACTION_USER_UNLOCKED`) — an ADD to phase 01's ShellApp,
Change Log; a "Pick from my music" file cannot be read before unlock → the default sound with `[alarms] sound <uri> locked ->
default`. New row E6c: `locksettings set-pin 1234`; an alarm 5 min ahead; `adb reboot`, boot poll, NO unlock; `dumpsys alarm`
"Next alarm clock information" shows it for user 0; jump the clock → `dumpsys audio` player usage ALARM from `app.tileshell`
and `dumpsys window` `isKeyguardShowing=true` with the ring activity on top; `[alarms] rearm (locked boot)` read after the
unlock from the same process's ring. Restore: unlock with the PIN, `locksettings clear --old 1234`. P2 gains "with the PIN
set and the phone not unlocked after the reboot". (Not run: needs a PIN and a reboot.)

### D3 · BLOCKING · In use, Android's heads-up sits over the W10M toast for the whole ring; E4b cannot see it
**Lines:** 176-186; 403-412 (T15-14); task 3 l.509-514; E4b l.671-685.
**Evidence.** The ring posts one notification "on an alarm channel whose full-screen intent opens the ring surface's
activity" (l.177-179). Posted while the phone is awake and unlocked, a full-screen-intent notification is shown by SystemUI
as a heads-up, and that heads-up is sticky — pinned until acted on (AOSP SystemUI `HeadsUpEntry.isSticky()` is true when
`fullScreenIntent != null`) — in SystemUI's window, which sits above every `TYPE_APPLICATION_OVERLAY` window. The AVD has
already shown that order for the nav bar: an app overlay draws under it and loses its touches there
(qa/phase-04/P5/P5.txt:11-26, 43-46). So under Q-E A the in-use toast (0 → 248 epx) sits under Android's own heads-up. E4b
asserts an APPLICATION_OVERLAY window exists and `ring_surface` is in the gesture-driver dump — both true while the heads-up
covers it; no clause asserts its absence. Also unstated: the phone locking while the overlay rings (the keyguard hides app
overlays, and an already-posted full-screen intent does not relaunch → nothing over the lock screen) and unlocking while the
locked toast rings. (Needs a state change to confirm on API 36: post a full-screen-intent notification with the AVD
unlocked.)
**Fix.** Add to "Ringing": "The ring notification has two forms, chosen at fire time from `KeyguardManager.isKeyguardLocked()`,
`PowerManager.isInteractive()` and `Settings.canDrawOverlays()`: locked, screen off or no overlay grant → the alarm channel
(IMPORTANCE_HIGH, category alarm, full-screen intent, Snooze / Dismiss); in use with the grant → a second channel
`alarm_ringing_quiet` (IMPORTANCE_LOW, no full-screen intent, the same actions, still the ring service's foreground
notification), so no heads-up shows while the overlay toast does. While ringing the service follows `ACTION_SCREEN_OFF` →
remove the overlay and re-post the full-screen form (the locked toast appears), and `ACTION_USER_PRESENT` → finish the locked
toast and add the overlay." E4b gains: from its MARK, `dumpsys notification --noredact` shows the ring notification at
importance LOW with no full-screen intent while the overlay shows, and the gesture-driver dump holds no
`com.android.systemui` node with the alarm's name; then `KEYCODE_SLEEP` mid-ring → `[alarms] surface: toast-locked` and the
ring activity over the keyguard.

### D4 · BLOCKING (trust) · E2 exports the ring activity and the recorder service
**Lines:** E2 643-645; Scope 32-33.
**Evidence.** qa/phase-03/exported-allowlist.txt is the WHOLE exported surface ("E5 compares … EXACTLY — a component that
appears on the device and not here fails"; exported.py keeps only components that are exported). E2 adds "the ring
surface's activity … and the recorder service" to it, i.e. they must be exported. Neither needs to be: the full-screen and
action PendingIntents, and the recorder's start and bind, all come from the shell itself. An exported `RecorderService` lets
any app start a `microphone` foreground capture under the shell's RECORD_AUDIO; an exported `showWhenLocked` +
`turnScreenOn` activity lets any app wake the phone and draw a ring over the keyguard (and reach Snooze / Dismiss). E2 would
pass on exactly that.
**Fix.** E2 → "matches exported-allowlist.txt after its ADDs — exactly the three launcher activities (`guard: none
(LAUNCHER intent filter)`); the ring activity, the ring service, the recorder service and the alarm receivers are
`exported="false"` and must not appear (any other new exported component fails the row)". Scope l.32-33: "(the three
launcher activities only)".

---

## SHOULD-FIX

### D5 · SHOULD-FIX · Q-E is ruled A; dead "pending" and B / C text remains (and one wrong mechanism)
**Evidence.** l.100-105 records Jeremy's "(a)" and says "the B and C branches are not built", yet the texts below still offer
them. The ruling line itself says the banner is shown "on the keyguard and over whatever app is in use, drawn as an overlay
window": an app overlay cannot show over the keyguard (AOSP `WindowState.canBeHiddenByKeyguard`: non-activity windows under
the notification-shade layer are hidden while the keyguard shows), which is why T15-14 (l.406-409) puts the locked toast in a
full-screen-intent activity. Jeremy's ruling is not reopened; its paraphrase of the mechanism is wrong.
**Fix (replacement text):**
- l.4 "Q-E (how alarms and timers ring) is with Jeremy — T15-14's ring rows are written for each answer, the lean first, marked
  'Q-E: A'" → "Q-E answered 2026-09-23 (Jeremy "(a)"): the locked toast through a full-screen-intent activity, the in-use
  toast as an overlay window; the B / C branches are struck".
- l.78 "**Pending Q-E, under A only (T15-14):**" → "**Q-E A (ruled 2026-09-23, T15-14):**".
- l.101-103 "on the keyguard and over whatever app is in use, drawn as an overlay window" → "on the keyguard (a translucent
  `showWhenLocked` activity the full-screen intent starts) and over whatever app is in use (a `TYPE_APPLICATION_OVERLAY`
  window; an overlay cannot show over the keyguard)"; "(phase 12's setup:overlay, recorded there)" → "(its why line is in
  phase 12's table)".
- l.262-266 Bars: "under A / B the locked toast covers" → "the locked toast covers"; strike "; under C the page draws the
  status bar and the lock-screen nav variant with inactive keys, as phase 06's incoming call does (its H23), so a stray touch
  cannot leave a ringing alarm".
- l.322-323 `[alarms] surface: toast-locked | toast-overlay | heads-up | page` → `… | heads-up | lockscreen-notification`
  (`page` cannot occur; the full-screen-intent-denied locked case of E21 is a lock-screen notification, not a heads-up);
  E21 l.865 follows.
- l.399-400 "the mechanics under each answer to Q-E, which is pending with Jeremy" → "the mechanics of Q-E A (answered
  2026-09-23)"; l.404 "**Whatever the answer:**" → "**Also:**" and "every answer's fallback" → "the fallback"; l.406
  "**A (lean; written first in every row):**" → "**The ruled form:**"; strike l.415-418 "**B:** … **C:** … as a P4 design
  ([accept], H5); in use the heads-up." and "every answer needs" → "the ring needs".
- Task 1 l.504-505 "Q-E: A, under A only, `SYSTEM_ALERT_WINDOW`" → "`SYSTEM_ALERT_WINDOW` (Q-E A)".
- Task 3 l.510 "(the heads-up form, every answer's fallback)" → "(the fallback when no toast can show)"; l.511 strike "**Q-E:
  A (ruled; the B / C branches below are not built):** under **A** (lean)"; strike l.515-517 "; under **B** the locked toast
  and, in use, the heads-up; under **C** the full-screen page (…) and, in use, the heads-up (was "the ring page …",
  SUPERSEDED 2026-09-23 by T15-14)". The C-4 why line l.519-520 "Without it an alarm still sounds, but its page waits until
  you unlock." (there is no page) → "Without it an alarm still sounds, but shows only as a notification."
- E2 l.644-645 → D4's text.
- E4 l.658 drop "**Under Q-E A or B (lean):**"; strike l.664-665 "**Under Q-E C:** the slice holds `[alarms] surface: page` and
  the full-screen page is on top with the same `ring_*` nodes."
- E4b l.674 drop "**Under Q-E A (lean):**"; strike l.682-683 "**Under Q-E B or C:** the heads-up half above is the whole row
  (…)"; l.683 "Timer kind, every answer:" → "Timer kind:".
- E6 l.696-697 "on the surface E4b names for a phone in use under the Q-E answer (Q-E: A; the timer form: …)" → "on the in-use
  overlay timer toast (E4b's timer form: `ring_dismiss`, no `ring_snooze`)".
- E10 l.754-756 "The ring surface, Q-E: A: under A / B the alarm toast" → "The ring surface: the alarm toast"; strike "; under C
  the page with the drawn bars (H5 [accept])".
- E21 l.867 "**Pending Q-E, under A only:**" → "**Overlay (Q-E A):**".
- E23 l.880 "The ring surface, Q-E: A (T15-14): under A / B, over the keyguard" → "The ring surface over the keyguard"; strike
  l.883-884 "; under C, on the page over the keyguard a tap on each drawn key leaves the page showing (dump; phase 06 H23's
  lock-screen nav variant)".
- E28 l.946-947 "**Pending Q-E, under A only (T15-14):**" → "**Overlay (Q-E A):**" (the row moves to phase 12, D1).
- P1 l.987 "(Q-E: A: the toast under A / B, the page under C)" → "(the locked toast)"; l.989-990 "(under A the overlay toast
  once … recorded; under B / C the heads-up)" → "(the overlay toast once "Display over other apps" is granted on One UI, its
  grant state recorded)".
- H1 l.1008 "and, Q-E: A under A / B, the timer toast" → "and the timer toast".
- H5 l.1020-1026 → "H5 the ring surface (Q-E A, T15-14): [fidelity] against r11/clock.md §8 — the toast form (8.1, HIGH) and
  the timer toast (8.9-8.11, MEDIUM); the alarm toast's geometry (8.2-8.6, LOW, U8), the strings "Timer finished" /
  "Dismiss" (U2) and what shows beneath the locked toast (lock screen or wallpaper, per the build-start check) are judged as
  tagged approximations; the in-use overlay toast the same, with D6's recorded differences."
- Edge l.1064 "(Q-E: A: the overlay toast under A, the heads-up under B / C — E4b's checks" → "(the overlay toast, E4b's
  checks".
- Phase 12 (cross-doc) l.253: the `setup:overlay` placeholder → "Alarms ring over the app you're using. Without it an alarm
  shows as a notification."; add `setup:full_screen_alarms` → "Alarms ring over the lock screen. Without it an alarm still
  sounds, but shows only as a notification."

### D6 · SHOULD-FIX · The in-use toast's form is partly impossible and partly unstated
**Lines:** 262-266; 404-412; E23 880-882; H5 1020-1026; C-5 l.376-378.
**Evidence.** r11/clock.md 8.1 "drawn over the status bar"; 8.12 "Page under the timer toast: dimmed" (MEDIUM). An app overlay
sits under the system status bar and SystemUI's layer (the order P5 measured for the nav bar, D3), so over a third-party app
the in-use toast cannot cover the status bar; the doc says only "the same toast" and H5 judges it as [fidelity] against 8.1.
The dim, what a touch below the toast does, and the entrance motion (r11 U12: R7 §4.5.1's 217-ms grow-down, tagged; C-5 names
"the ring surface's entrance" but no value or row) are left to the builder.
**Fix.** T15-14 line gains: "In use the overlay toast is laid out below the system status bar when the app in front shows one
(an app overlay cannot draw over it — a recorded seam, H5) and from y 0 over the shell's own pages, whose system bars are
hidden; the app beneath is dimmed (8.12) with the window's `FLAG_DIM_BEHIND` at <amount, approximation>; a touch outside the
toast <reaches the app | is consumed> (state one); the entrance is U12's 217-ms grow-down, logged `[motion] ring_toast`,
[accept] under H5."

### D7 · SHOULD-FIX · No trust surface is named for the adversarial review the build prompt requires
**Evidence.** build-prompt.md:21: trust-touching parts "need an adversarial team review before `done`, as their phase docs
say"; the doc contains neither "adversarial" nor "trust". It adds: (a) the `TYPE_APPLICATION_OVERLAY` window and
SYSTEM_ALERT_WINDOW; (b) a `showWhenLocked` + `turnScreenOn` activity over the keyguard (nothing on it may open the app or
anything else without `requestDismissKeyguard`); (c) `ISpeech.holdMicrophone` / `releaseMicrophone` (a caller can lock Tess and
the keyboard out of the microphone; `SpeechService` must stay `exported="false"`, AndroidManifest.xml:236-238); (d) the
`:recorder` microphone foreground service; (e) the T15-3 write guard (rename / delete / trim only for the shell's own files);
(f) `ACTION_SEND` read grants on other apps' recordings; (g) `LockGate` allowing `Arithmetic` over the keyguard; (h) the
in-process secondary-tile request, which skips LiveTileProvider's validation (SecondaryTiles.kt:128-132: "the provider has
already validated it"); (i) the App Shortcuts' `page` extra on exported activities (`new_recording` must never start a take).
**Fix.** Decision "Trust": "(a)-(i) get an adversarial team review (team-review, adversarial mode) recorded under qa/phase-15/
before `done`"; a task 8 line; build-prompt.md:21's list gains "phase 15's ring surfaces, microphone hold and recorder".

### D8 · SHOULD-FIX · E2 fails today on phase 10's owed allow-list lines
**Evidence.** `python3 docs/plan/qa/phase-03/scripts/exported.py app/build/outputs/apk/debug/app-debug.apk
docs/plan/qa/phase-03/exported-allowlist.txt` on this worktree's APK → rc=1, "EXPORTED BUT NOT ALLOWED:
app.tileshell.music.MusicActivity / app.tileshell.music.MusicService" (INDEX 2026-09-22, phase 05 entry (3): "phase 10's gate
owes the two lines"). E2 (l.643-645) says "so phase 03's E5 still passes" — it cannot, for a reason outside this phase.
**Fix.** E2: "phase 10's two owed lines (MusicActivity, MusicService) are added first — by phase 10's gate, or by this
phase's task 1 with a Change Log line naming phase 10's debt — then the three ADDs".

### D9 · SHOULD-FIX · The session tag is not "music" / "recorder", and nothing creates the recorder's session
**Lines:** 280-285; task 0 l.495-499; E0 l.625, 627; task 7 l.549-561.
**Evidence.** media3-session 1.8.0 (gradle/libs.versions.toml:10), `MediaSessionLegacyStub` bytecode: the platform session tag
is `TextUtils.join(".", {"androidx.media3.session.id", session.getId()})`. `music/MusicService.kt:117`
`MediaSession.Builder(this, known).setCallback(callback).build()` sets no id, so today's tag is
`androidx.media3.session.id.`. emulator-5556 `dumpsys media_session` prints records as `<tag> <pkg>/<tag>/<n>`
(`MediaPlaybackService com.android.music/MediaPlaybackService/5`), which is what `MediaController.getTag()` returns. The doc
routes on tags "music" / "recorder" and E0 expects `tag=music`; task 7 never says Voice Recorder's playback runs a session
(E0's `tag=recorder -> none` needs one; r11/voice-recorder.md 4.9, LOW: W10M's playback registered with the system media
controls). phase-17 l.185-186 carries the same claim.
**Fix.** Task 0: "ADD `.setId("music")` to phase 10's `MusicService` (Change Log for 10); a shell-owned session's tag is
matched with the `androidx.media3.session.id.` prefix removed, and the `[music] session` line logs that id". Task 7: "playback
runs a Media3 session with `setId("recorder")` (R11 4.9)".

### D10 · SHOULD-FIX · Task 0 routes content but not the notification count E0 asserts
**Evidence.** `feeds/TileNotificationListener.kt:64` keeps an ongoing notification on a named channel eligible, and `:73`
`BadgeStore.set(pkg, NOTIFICATIONS, count)` counts the shell's own notifications under `app.tileshell`; `start/StartPage.kt:164`
(slot tile) and `:174` (app tile) read `badges[<component>.packageName]`. The running timer's and the recorder's ongoing
notifications (and missed-alarm ones) therefore put a count on MUSIC and every in-APK tile. E0 (l.628-631) asserts "no in-APK
tile … shows a count", but task 0 (l.495-499) and the Tiles Decision name only content (the face, `packageKey` content,
`ActiveTiles`).
**Fix.** Task 0 adds: "and the count — `TileNotificationListener.kt:73`'s entry for the shell's own package and
`StartPage.kt:164,174`'s `badges[<package>]` follow the same routing: no count from the shell's own notifications lands on an
in-APK tile".

### D11 · SHOULD-FIX · The `holdMicrophone` ADD cannot produce what E17 / E18 read as written
**Lines:** 237-246; 314; task 7 l.558-560; E17 830-837; E18 842-846.
**Evidence.** `onCallbackDied` (`SpeechService.kt:100-102`) fires only for callbacks in `clients` (`register`, :334-338): a hold
from an unregistered binder is never freed when `:recorder` dies. `MicArbiter` keys holders by binder with a pid only; the
refusal logs pids (`SpeechService.kt:357`: "refused: the microphone is listening for pid=…"), and Tess's notice for
MICROPHONE_BUSY is hard-coded "The keyboard is using the microphone right now." (`CortanaModel.kt:276`). So "Tess's notice
names it", `[recorder] refused: microphone busy owner=<owner>` and E17's "refused for owner `cortana` with owner `recorder`
holding" need an owner name to cross the binder, which the ADD does not carry. `acquire` takes three arguments
(`MicArbiter.kt:31`), not the two l.241 writes.
**Fix.** l.240-242 → "`holdMicrophone(cb, who: String): String?` (null = held, else the holder's name) and
`releaseMicrophone(cb)`; `cb` must already be `register`ed (an unregistered one is refused) so `onCallbackDied` frees it;
`MicArbiter` keeps `who` beside the binder and every refusal — startListening's too — reports the holder's name; Tess's
MICROPHONE_BUSY notice picks "The keyboard …" / "The voice recorder …" from it; the `:speech` refusal line reads `…
refused: held by <who>`. `SpeechService` stays `exported="false"`."

### D12 · SHOULD-FIX · Call state needs READ_PHONE_STATE, which the shell does not hold or ask for
**Lines:** 190; 248-249; edge 1099-1100.
**Evidence.** The manifest holds no READ_PHONE_STATE (AndroidManifest.xml:6-67); with `targetSdk = 36`
(`app/build.gradle.kts:22`) `TelephonyManager.getCallState()` and a `TelephonyCallback.CallStateListener` both require it (a
dangerous permission: a new checklist row and wizard step), and the Manifest ADDs (l.71) do not list it.
**Fix.** Both "`TelephonyManager` call state" texts → "the audio mode, `AudioManager.getMode()` / `addOnModeChangedListener`
(API 31, no permission): MODE_RINGTONE = ringing, MODE_IN_CALL or MODE_IN_COMMUNICATION = in a call" (it also catches VoIP
calls).

### D13 · SHOULD-FIX · "silenced by <pkg>" cannot be known
**Lines:** 313; edge 1102-1103.
**Evidence.** For a caller without MODIFY_AUDIO_ROUTING, another app's `AudioRecordingConfiguration` is anonymized and
`getClientPackageName()` is a system API; the recorder learns only that its own capture `isClientSilenced()`.
**Fix.** `[recorder] paused: call|silenced|user`; the edge case's `"silenced by <pkg>"` → `paused: silenced`.

### D14 · SHOULD-FIX · The recorder service's restart after a kill is unspecified
**Lines:** 231-236; task 7; E18 838-846.
**Evidence.** `Service.onStartCommand` returns START_STICKY by default, so after E18's `kill -9` the system restarts the
service; its `startForeground(microphone)` from the background is refused on API 34+ (microphone is a while-in-use type),
giving a crash-restart loop, and recovery runs at that restart rather than at "the next start" E18 reads.
**Fix.** "`RecorderService.onStartCommand` returns START_NOT_STICKY; recovery runs in `onCreate` of the next start or bind (the
page opening, or the next take)."

### D15 · SHOULD-FIX · After an uninstall or Clear storage the shell's own takes become "other apps'" recordings
**Lines:** 363-369 (T15-3); 118-119 (Q3); E14 813-815; E20 855-856; E21 859-862; E30 966-968.
**Evidence.** MediaProvider orphans a package's rows (`owner_package_name` → NULL) when the package is fully removed, and
AOSP's MediaService runs the same `onPackageOrphaned` on `ACTION_PACKAGE_DATA_CLEARED` (`pm clear`). Under T15-3 ("rename,
delete and trim are offered only for files whose OWNER_PACKAGE_NAME is `app.tileshell`") every take made before a reinstall
or Clear storage is from then on read-only, filed under "Other apps' recordings", and hidden while READ_MEDIA_AUDIO is off.
Rows: after E14's uninstall + provision.sh, E20's restore "the E14 takes deleted in the app" has no Delete to use; E21's "the
list shows the take" fails (an orphaned file is not the shell's own, so the READ_MEDIA_AUDIO-denied list hides it); E30's
`mine` filter moves them. (Not run: needs a take and a `pm clear`; confirm with `content query … --projection
_id:owner_package_name` before and after.)
**Fix.** T15-3 gains: "A recording whose owner was cleared (the shell uninstalled or its data cleared) is another app's from
then on: play and share only (H18)" — or, the triage's call, Delete / Rename for owner-NULL files in Recordings/ through
`createDeleteRequest` / `createWriteRequest` (one consent dialog, a P2 seam). E14's uninstall step moves after E30 (or deletes
its takes first) so E20 / E21 / E30 see takes the shell owns.

### D16 · SHOULD-FIX · The pinned timer / stopwatch tile's tap reaches the right screen only by accident
**Lines:** 430-433; task 4 l.528-530; E31 974-977.
**Evidence.** `SecondaryTiles.launch` (`tiles/api/SecondaryTiles.kt:315-331`) starts `AppCatalog.firstForPackage(owner)` — the
package's first launcher entry by label (`apps/AppCatalog.kt:167` sort, `:175-176`) — with `EXTRA_LAUNCH_TILE_ID` /
`EXTRA_LAUNCH_ARGUMENTS`. For `app.tileshell` that becomes Alarms & Clock only because "Alarms & Clock" sorts first, and no
task says the Alarms & Clock activity reads the extras, so E31's "a tap resumes Alarms & Clock with `clock_pivot:timer`
selected and that timer on screen" rests on sort order and an unstated handler. `SecondaryTiles.request` expects a record "the
provider has already validated" (:128-129).
**Fix.** Task 4: "the Alarms & Clock activity reads `LiveTileProtocol.EXTRA_LAUNCH_TILE_ID` (`timer.<id>` → the Timer tab with
that timer; `stopwatch` → the Stopwatch tab); `SecondaryTiles.launch` targets the Alarms & Clock component explicitly for a
tile the shell itself owns (an ADD to phase 01 / 02's part, Change Log); the in-process request applies the provider's
checks (tile-id pattern, size, name) itself."

### D17 · SHOULD-FIX · Two alarms at once: Decisions and the edge case disagree
**Lines:** 192-193 ("A second alarm firing while one rings takes the ring surface and the first counts as missed") vs edge
1068-1069 ("two alarms at the same minute (one ring surface, both listed on it)") and 1071-1072.
**Evidence.** Same-minute alarms fire milliseconds apart, so the Decision makes the first "missed"; the toast (`ring_name`)
has one name.
**Fix.** Decisions: "Alarms due in the same minute ring together on one toast (`ring_name` lists them; Snooze / Dismiss act on
all); an alarm due later while one rings supersedes it (the first is missed, `superseded`)"; the edge cases follow.

### D18 · SHOULD-FIX · E13 puts the History glyph in Standard only; Scientific has history
**Lines:** E13 780-782 vs 226-227, E12 777-779.
**Evidence.** r11/calculator.md 1.7's "Standard only" is among the pages captured natively (Programmer, converter); 6.1
(Scientific, LOW) shows "header `≡ SCIENTIFIC` + History", and gap 5: history "yes, in Standard and Scientific". Built to E13,
Scientific's history (E12) has no way in.
**Fix.** E13: "in Standard and Scientific (6.1; Scientific's geometry is UNMEASURED-2, so its glyph's presence only), not in
Programmer (4.15) or the Converter pages, the 16 × 16-epx History glyph …".

### D19 · SHOULD-FIX · W10M controls the rows assert, or R11 lists, have no defined behaviour
**Evidence and fix, per item** (each: build it — one line in its task — or rule it out with an H row):
- Clock app bars (E10 l.753 asserts "§1.13's buttons per tab"): **Select** (MultiSelect, Alarm and Timer tabs) and **More**
  (1.14: Send feedback / Notification settings / About) have no stated action. State Select = W10M's multi-select delete, or
  out; More = About only (feedback is Microsoft's online service; Notification settings would open Android's page, a seam).
- "Pick from ringtones" (l.36, E10 l.748-749) opens W10M's **Sounds page** (r11/clock.md 4.6, MEDIUM: "Sounds", "Use
  default", ▷ rows at a 60.4-epx pitch); the doc never says what it opens or lists. State: a shell-drawn Sounds page per 4.6
  listing the branding module's sound-alikes and Android's alarm tones (`RingtoneManager.TYPE_ALARM`), or Android's picker as
  a recorded seam.
- The **timer editor** (4.8: "NEW TIMER", three W/3 spinner columns, "Timer name") is in no task or row.
- Voice Recorder's **level rings** (2.7) — H3 judges 2.1-2.14, which includes them, but no task builds or excludes them.
- Voice Recorder's "…" menus (1.7 list: Settings / Feedback; 4.8 playback: Settings / Feedback / Open file location) and
  Calculator's pane **Settings** row (3.12, E13 asserts it) have no destination.
- The `converter` App Shortcut (task 9 l.577; E27 `calc_mode:converter`): W10M has no single Converter page (each category is
  its own page, 1.6 "SPEED"). State which category it opens (the last used; Volume first).

### D20 · SHOULD-FIX · Whether Alarms & Clock takes other apps' set-alarm intents is unstated
**Lines:** 45-48; Q1 449-458; task 5 l.532-536.
**Evidence.** Task 5 removes Tess's `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` sends (`cortana/action/ActionLayer.kt:311-327`),
leaving `com.android.alarm.permission.SET_ALARM` (AndroidManifest.xml:55) unused. The doc never says whether Alarms & Clock
declares `ACTION_SET_ALARM` / `ACTION_SET_TIMER` / `ACTION_SHOW_ALARMS` for other apps: if it does, any app can set a silent
alarm in the shell (`EXTRA_SKIP_UI`) and Android shows a chooser between clocks (P2, the seam Q1 was about); if not, other
apps' requests still go to Samsung Clock.
**Fix.** Decision (agent): "Alarms & Clock declares no `AlarmClock` intent filter; other apps' set-alarm requests keep going to
the phone's clock (Q1 ruled Tess's route only); Jeremy can ask. `SET_ALARM` leaves the manifest with task 5."

---

## NOTE

### D21 · NOTE · Foreground-service details for the ring
**Lines:** 185-186; Scope 71.
`systemExempted` needs `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, which the Manifest ADDs (l.71) omit (`mediaPlayback`'s
permission is held, AndroidManifest.xml:22). API 35+ refuses `mediaPlayback` (and `microphone`) foreground services started
from `BOOT_COMPLETED`. Add: "the ADD is made if the check picks `systemExempted`; the ring service is started only from the
alarm's broadcast — a past-due alarm is armed at now and fires through AlarmManager, as `ReminderScheduler.kt:51-61` does —
never from the boot receiver."

### D22 · NOTE · What E4 reads about the keyguard
**Lines:** 406-409; E4 654-655, 662-664; Bars 263.
A `showWhenLocked` activity on top occludes the keyguard, and SystemUI then does not draw the lock screen, so the build-start
check will likely land on the wallpaper; the fallback needs `android:windowShowWallpaper="true"` on the ring activity (not
stated), and "the lock screen's own nav bar shows below it" (l.263) cannot hold then. E4's "with the keyguard still showing" →
the fields emulator-5556's `dumpsys window` prints: `isKeyguardShowing=true` and `mKeyguardOccluded=true`.

### D23 · NOTE · A timer armed RTC is not "elapsed-time"
**Lines:** 168-169; 196-197.
Timers use `setExactAndAllowWhileIdle(RTC_WAKEUP)`, so a manual or network wall-clock change moves a running timer's end;
"a timer is elapsed-time: its deadline does not move" holds only for a zone change. Say so (H9), or arm
`ELAPSED_REALTIME_WAKEUP` and convert from the stored wall-clock deadline at boot.

### D24 · NOTE · Storage floor and encoding are left to the builder
**Lines:** 249-251; E19 847-851; edge 1104.
"Before a take starts the recorder checks … and stops at a 50 MB floor" does not say whether or how often it checks during a
take (E19 needs it to), and `getAllocatableBytes` counts clearable cache, so it can exceed E19's `df` reading. The AAC
parameters are unstated; the edge case's "60-minute take ≈ 30 MB" implies ≈ 64 kbps mono, which makes E19's 10 MB take about
21 minutes. Fix: "checked before a take and every 5 s during it; AAC-LC, mono, 44.1 kHz, 64 kbps".

### D25 · NOTE · Small text fixes
- l.308-309 "stay adb-driven (no instrumentation APK)" — C-10's dump route is phase 05's gesture driver, itself an
  instrumentation (`app.tileshell.qa.imefixture.test`, qa/phase-05/TOOLING.md:72-81) → "no new instrumentation APK; phase 05's
  fixture gesture driver is the only one used".
- l.227-228 "CalcViewModel's `UnitConverterDataLoader.cpp`": at microsoft/calculator 4fd3fc5 the unit tables are
  `src/Calculator.ViewModels/DataLoaders/UnitConverterDataLoader.cs` and the date engine
  `src/Calculator.ViewModels/Common/DateCalculator.cs` (qa/phase-15/STATE.md, the reuse-check prep) — write the paths.
- l.222 "QWORD: −1 = FFFFFFFFFFFFFFFF" vs E11 l.768 "FFFF FFFF FFFF FFFF" — one form.
- The two new Setup rows need their checklist `detail` strings (`ChecklistRow.detail`, `onboarding/Checklist.kt:40`, drawn
  as "<state> · <detail>" at :143); only the wizard why lines are given.
- l.241 `mic.acquire(owner.asBinder(), pid)` → the call also takes the `nextGeneration` argument (`MicArbiter.kt:31`).

---

BLOCKING: 4 · SHOULD-FIX: 16 · NOTE: 5
