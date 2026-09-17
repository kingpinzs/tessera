# Phase-doc review, round 3: Reviewer 2 (opus). Lens: testability, evidence, internal consistency. Date: 2026-09-17

What I read: PLAN.md, INDEX.md, phase-01 to phase-09, and the round-2 files (r2-opus-test, r2-opus-design, r2-triage, r2-owner).
I opened r7-measurements.md §1–§4, w10m-measurements.md A19/A20 and r6 only to check specific claims.
Phase 04 is checked for internal consistency and against R7 only.

Verification limit: adb and the emulator are not installed here, so I ran no command on a device. I checked how commands behave in AOSP
source fetched on 2026-09-17 from android.googlesource.com `main` and the aosp-mirror GitHub copies:
- frameworks/native InputDispatcher.cpp
- PhoneWindowManager.java
- InputMethodManagerService.java (shell command help)
- location GeofenceManager.java and SystemSettingsHelper.java
- packages/Shell AndroidManifest.xml
- wm ActivityTaskManagerService.java and Task.java (dump labels)

I checked emulator console syntax against developer.android.com/studio/run/emulator-console. Where a claim rests on memory and not
on source, the finding says "not certain".

Severity (same scale as round 2):
- **BLOCKING:** a row a correct build cannot pass, or passes without testing anything; a row that cannot be run as written; or a value
  that contradicts its own Decision or source.
- **MAJOR:** a row that is ambiguous or unverifiable, a missing threshold, or a contradiction between docs.
- **MINOR:** wording, stale text or small gaps.

**Counts:** 8 BLOCKING · 14 MAJOR · 16 MINOR · 1 owner question (R3T-B6).

---

## 1. Round-2 BLOCKING and MAJOR findings (r2-opus-test.md): status in the current docs

| Round-2 ID | Status | Evidence (current line) |
|---|---|---|
| T-B1 | RESOLVED | phase-03 E8 L108: "`adb shell input keyevent KEYCODE_ASSIST` opens Cortana". E9 L109: sleep → wake → keyguard → `KEYCODE_ASSIST`, "this row uses `adb shell cmd voiceinteraction show` instead" as the fallback. The source still has no keyguard check in `launchAssistAction` |
| T-B2 | RESOLVED | phase-01 E20 L192: "start AOSP DeskClock from its launcher entry (`adb shell am start -n com.android.deskclock/.DeskClock`)", X24 L165 (different-affinity case), and the lock and reboot steps with `sys.boot_completed` polling |
| T-B3 | RESOLVED | phase-01 E10 L182: "same-frame flip coincidences summed over all tile pairs is ≤ λ + 3√λ + 3"; L85: "no global scheduler and no de-collision" |
| T-B4 | RESOLVED | phase-03 L46: "period 1.04 ± 0.02 s … R6 §3.1.8 supersedes R3 A22's single-cycle 600 ms pulse"; E4 L104 is split per source |
| T-B5 | RESOLVED; residue in R3T-B2, R3T-B3, R3T-B4, R3T-M9 | R7 is applied: phase-06 L33–L52, phase-09 L41–L46, phase-04 L29–L35 and Y5–Y12 (L67–L74). New defects in the applied values are listed below |
| T-M1 | RESOLVED for the rows it named; residue in R3T-M3 | PLAN RV11 L191–L197; phase-02 E7 L63: "`adb shell input swipe x y x y 740` (no edit mode) and … `830` (edit mode)"; P-rows 01 P10, 02 P2, 03 P5, 05 P3. The R7-derived rows added since then have no RV11 phone row |
| T-M2 | RESOLVED | phase-01 L24 and L85: "flip tiles repeat every 4.96 ± 0.22 s and image-crossfade tiles every 4.4 ± 0.4 s"; E10 L182 checks against each band |
| T-M3 | RESOLVED | phase-01 L94 (press styles), X13 L154, E10 L182: "the app list swipe is approximation X13, judged in H21, not measured" |
| T-M4 | RESOLVED | phase-01 L87 (status bar 28–29 ± 1.1 epx; X16; X17 three slots), E19 L191: "`adb shell input swipe 540 0 540 400` … hidden again within 5 s" |
| T-M5 | RESOLVED | phase-01 L74 and P4 L198: "`dumpsys gfxinfo app.tileshell reset` … 99th percentile ≤ 2 vsync periods of that mode rounded up to whole ms" |
| T-M6 | PARTLY RESOLVED; residue in R3T-M1 | PLAN RV12 L198–L201 and the header line in every phase; the rows it named now restore (01 E3 L173, 01 E12 L184, 09 E3 L70, 09 E7 L74). **But 01 E9 L180 now also moves the clock "as in E12" and restores only "`adb shell cmd connectivity airplane-mode disable`"** |
| T-M7 | RESOLVED; small residue in R3T-m11 and R3T-m16 | phase-01 L89: "`adb shell dumpsys activity service app.tileshell/<notification listener service>` prints it through `Service.dump()`"; task 18 L135; every phase's acceptance header cites it |
| T-M8 | RESOLVED | phase-02 L40 and E5 L61: "the test APK shows the arguments it received in a TextView, read from `uiautomator dump`" |
| T-M9 | RESOLVED | phase-03 L39 and L58 (R6 §3.4.2 card, calendar and delete card approximations H17/H18); E7 L107: "no reminder or calendar card offers 'add more'" |
| T-M10 | RESOLVED | phase-03 L31: "`SmsManager` (`SEND_SMS` …) and … `TelecomManager.placeCall` (`CALL_PHONE`)"; checklist rows L18; PLAN RV4 L169–L172; phase-06 L32 and E5 L76 |
| T-M11 | RESOLVED | phase-03 L55 (text box §3.3.1–3.3.9, H13, H14), L53 (locked look, H11), L54 ("Unlock to continue" card, H12) |
| T-M12 | RESOLVED | phase-05 L32: "If it cannot, the system's IME inset animation is used … accepted in H10"; E3 L68 is conditional |
| T-M13 | RESOLVED | phase-07 L29–L31 (15063 page, defaults H7, §6.1.5 layout); E8–E10 L55–L57 |
| T-M14 | RESOLVED | phase-07 P1 L59: "`dumpsys batterystats --reset`, unplug the cable (or use Wireless debugging with no charger attached)" |
| T-M15 | RESOLVED | phase-08 L29 (metered policy) and E0 L51 (airplane mode plus the `NOT_METERED` check); phase-08 E1 L52 and phase-05 E8 L73 reuse phase 01 E9's proof |
| T-M16 | RESOLVED | phase-01 L21 and L92: "`hw.ramSize=8192` and `disk.dataPartition.size=16G`"; task 1 L118 |
| T-M17 | RESOLVED | phase-01 P9 L203; phase-03 P4 L132; phase-08 L31 and P2 L55 ("baseline + 50 MB"); phase-09 L40 and P2 L79 |
| T-M18 | RESOLVED in text; residue in R3T-m12 | phase-09 E7 L74: sleep → wake → keyguard → Cortana → WAV → "the PC script's diff shows no memory or rule file changed and diagnostics hold a refused-attempt entry" → PIN cleared |
| T-M19 | RESOLVED | phase-09 L37 (frozen inputs, pinned runtime settings); E8 L75: "replayed 5 times in one run and 5 more times after `adb shell am force-stop app.tileshell`" |
| T-M20 | RESOLVED | phase-09 L39: "no debug hook swaps the template (Rule 4)"; E6 L73 triggers replay with a rules push and `install -r` at a higher versionCode |
| T-M21 | RESOLVED in text; residue in R3T-M13 | phase-09 L25 and L38 (pid rule, adversarial review); phase-04 L26 and edge L96: "the helper's verb allow-list contains no content-provider call" |
| T-M22 | RESOLVED | phase-06 L24 struck; L26: "ruled PQ2 = B"; edge L111: "VVM stops working after a successful probe …"; P4 L92 |

Round-1 items still open in round 2:
- **F1-M12 (phase 04):** RESOLVED. Approximations table Y1–Y12 at L61–L74, each with its own H-row (H4–H15).
- **F2-M5 (phase 09):** RESOLVED. L40 derives the replay thresholds; P2 L79 uses them.

**Unresolved round-2 IDs:** T-M6 is only partly resolved (phase 01 E9; see R3T-M1). Every other round-2 BLOCKING and MAJOR is resolved in
the text. Residue is tracked under the new IDs below.

---

## 2. BLOCKING

### R3T-B1 — Phase 04 — injected volume keys never reach the accessibility service, so E3 and E4(g) fail for a correct build
- **Claim:** L18: "volume panel via accessibility key events". E3 L81: "Volume keys (`adb shell input keyevent KEYCODE_VOLUME_UP`) show the W10M panel, not the stock one". E4(g) L82: "`KEYCODE_VOLUME_UP` shows the volume panel with its lower edge peaking 12.3 ± 1.1 epx below rest".
- **Evidence (AOSP source, frameworks/native InputDispatcher.cpp, main):**
  - The accessibility service's `onKeyEvent` gets keys only through the input filter, which `notifyKey` calls: `if (shouldSendKeyToInputFilterLocked(args)) { … if (!mPolicy.filterInputEvent(event, policyFlags)) {` (L4526–L4530).
  - `InputDispatcher::injectInputEvent` (L4826 onward) runs only `mPolicy.interceptKeyBeforeQueueing(keyEvent, …)`, then `enqueueInboundEventLocked`. It never calls `filterInputEvent`.
  - So `adb shell input keyevent` (and `input motionevent`) skips the accessibility input filter. PhoneWindowManager hands the volume key to the audio service, and the stock panel shows.
- **Fix:**
  - Drive the volume key through the emulator's virtual input device, which goes EventHub → `notifyKey` → filter. The console form is `adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_KEY:KEY_VOLUMEUP:0 EV_SYN:0:0`, with `event send type[:code]:[value] …` plus a final `EV_SYN:0:0`, per the emulator console docs.
  - Use the same form in E3 and E4(g). On the phone, the physical key covers it.
  - Add one Decisions line: the action-center pull-down is an overlay window that receives touches directly, not accessibility motion-event filtering. Otherwise E4(a)'s `input motionevent` bypasses it in the same way.

### R3T-B2 — Phase 04 — the notification-list offset in Decisions and E4(a) misreads R7 §4.1.9
- **Claim:** L31: "While partly open, the notification list is drawn 0.062 (0.060–0.064) × the handle's remaining travel below its final place (§4.1.9, MEDIUM)". E4(a) L82: "At 30 %, 50 % and 58 % open … the notification list offset is 0.060–0.064 × the remaining travel".
- **Evidence:**
  - r7-measurements.md L524: the list is "**3–8 px (3.3–8.7 epx) below its final position** while the panel is partly open … o128: +4.0 px (128.567), +6.5 (.633), +8.1 while held at 78 % … **After release** the offset equals **0.062 × remaining handle travel**".
  - At 128.567 the handle bottom is 317 px of 597 (L517), so remaining travel is 280 px: 0.062 × 280 = 17.4 px, but R7 measured +4.0 px.
  - On the 780-epx AVD canvas, the E4(a) formula needs ≈34 epx at 30 % open (0.062 × 546). R7 never saw more than 8.7 epx.
  - A build that follows R7 fails E4(a). A build that follows L31 is off by up to 4×.
- **Fix:** L31 should split the two phases:
  - During the drag, the offset grows up to ≈8 epx: +3.3 → +8.7 epx per R7 §4.1.9. The shape between those points is an approximation with its own row in Approximations and an H-row.
  - After release, offset = 0.062 × remaining travel.
  - E4(a) checks ≤ 8.7 epx (+ RV11) during the drag. E4(b) checks 0.060–0.064 × remaining travel frame by frame during the 78 % settle.

### R3T-B3 — Phase 04 — the tap-close wipe timeline in Decisions contradicts E4(d), and both are off from R7's frame times
- **Claim:** L32: "the handle vanishes first, and 50 ms later the panel background is wiped from the bottom edge to the top … in 190 ms (183–200 ± 17 ms)". That is 233–250 ms from handle to background gone. E4(d) L82: "the handle goes first, then the background is gone 183–200 ms later".
- **Evidence:**
  - r7 L540, c73: handle vanishes at 73.367; the boundary already sits at 490 px (107 px up from the 597-px rest) at 73.417; the background is gone at 73.583.
  - Handle to gone is therefore **216 ms**, and the wipe itself is ≈170–200 ms.
  - A build that follows L32 (240 ms) fails E4(d)'s limit of 200 + 17 ms. E4(d)'s 183–200 ms is also below c73's 216 ms.
- **Fix:** write one timeline from the frames and use it in both L32 and E4(d): handle gone → boundary moving within ≤ 33–50 ms → background gone 216 ± 17 ms after the handle (c73). For c245 the handle frame is not stated in R7, so L32 should say so rather than quote 183–200 as a handle-to-gone time.

### R3T-B4 — Phase 06 — every Phone and Messaging value is measured against drawn W10M bars that no line gives these apps
- **Claim:**
  - L36: "on the S25 Ultra and the AVD … 780 epx tall, with phase 01's 48-epx drawn nav bar (X6). Distances given from the screen top stay fixed".
  - E9 L80: "row pitch (Call bar top − 282.6 epx) / 3.71 … (≈101 epx on the AVD) … after `adb shell wm size 1080x1920` (≈63 epx expected)".
  - E7, E11, E13 and E17 all measure "on the nav bar" or "under the status bar".
- **Evidence:**
  - Only Start (phase-01 L75) and the Cortana session (phase-03 L56) hide the system bars and draw W10M bars.
  - phase-01 L64: "inside other apps Samsung's 3-button bar stays".
  - No Scope line, Decision or build task in phase 06 hides the system bars in Phone, the incoming-call or in-call screens, or Messaging.
  - Both ≈101 and ≈63 epx come from 780 or 640 − 48 − 74.4. With the AVD's system nav bar (≈45 epx at 450 dpi, not certain of the exact dp), a build that keeps the documented system bars fails E9. Every "from the screen top" value then shifts by the system status bar.
  - R7 §1.5.1 also shows a lock-screen nav-bar variant (camera / Start / Search) under the incoming call, which the doc does not carry.
- **Fix:**
  - ADD a Decision like phase-03 L56: Phone, the incoming-call and in-call screens, and Messaging hide the system status and nav bars and draw phase 01's W10M bars.
  - Over the keyguard this is UNVERIFIED. The fallback keeps the system bars and re-derives E9's expected values from the window insets, with the result recorded.
  - Add an E-row in phase-03 E11's form and a P-row in P3's form.
  - Tag the incoming-call nav-bar variant (R7 §1.5.1) or list it as an approximation with an H-row.

### R3T-B5 — Phase 06 — the helper's "voicemail component only" rule cannot be enforced as written, and P5 needs a test-only path to run
- **Claim:** L31: "The helper refuses those verbs from any caller other than the shell's voicemail component (proven by phone row P5)". P5 L92: "the helper's VVM verbs refuse a call from a test APK and from any shell component other than the voicemail component (diagnostics entry for each refusal)".
- **Evidence:**
  - The helper hands its binder to the shell app after a uid and signature check (phase-04 L26).
  - Components in the same process share both `Binder.getCallingUid()` and `Binder.getCallingPid()`, so the helper cannot tell the voicemail component from another in-process caller.
  - Making another shell component call a VVM verb is exactly the test verb Rule 4 forbids. phase-09 L38 resolved the same shape (T-M21): "no test verb is added … the adversarial team review covers the pid rejection".
- **Fix:**
  - The voicemail fetch runs in its own `android:process` (e.g. `app.tileshell:voicemail`). The helper checks the calling pid against the pid recorded at handoff and clears it on binder death, as in phase-09 L38.
  - P5 keeps the test-APK refusal plus its diagnostics entry.
  - The "other shell component" refusal moves to the mandatory adversarial review, with no test verb, and L31 says so.

### R3T-B6 — Phase 03 — E14 tests the opposite trigger from the ruled rule, and its call path cannot fire as written
- **Claim:**
  - L64 (owner ruling R2D-13): "Person reminders fire when Jeremy calls or texts that contact, detected from the call log and the SMS provider".
  - E14 L129: "`adb emu gsm call <contact number>` answered (and separately `adb emu sms send <contact number> hi`) fires it once; a call from another number does not".
  - r2-owner L6: "firing when Jeremy calls or texts that contact".
- **Evidence:**
  - `gsm call` "Simulates an **inbound** phone call" and `sms send` "Generates an emulated **incoming** SMS" (emulator console docs). A build that implements the recorded rule (Jeremy calls or texts) fails E14.
  - E14 gives no answer command. `gsm accept` applies only to waiting or held calls (console docs); `adb shell input keyevent KEYCODE_CALL` answers a ringing call (PhoneWindowManager L5573–L5580: `telecomManager.acceptRingingCall()`).
  - E14 gives no hang-up command either. Telecom writes the call-log entry when the call disconnects, so a call-log-based trigger cannot fire while the call is still active.
- **Fix, once the direction is settled:**
  - If outgoing only: E14 places the call through the shell's own command path, i.e. phase 03's "call <contact>", confirmed. Then `adb emu gsm accept <number>` if the emulated remote does not answer by itself (not certain), then `adb emu gsm cancel <number>`, then assert it fired. The text uses "text <contact> …" confirmed. An incoming call or SMS from the contact must not fire.
  - If both directions: keep the inbound commands, add `adb shell input keyevent KEYCODE_CALL` to answer and `adb emu gsm cancel <number>` before the check, and update L64's wording.
- **Owner question (WHAT):** "Next time I talk to Mom" should fire when: (a) you call or text her, or she calls or texts you (any answered call or any text, either direction); (b) only when you call or text her; (c) other / let me clarify. The agent default if unanswered is (b), because it matches the recorded ruling.

### R3T-B7 — Phase 03 — E13's "with home saved" has no defined path, and one named source is a phase 09 feature
- **Claim:** L64: "a place is a saved address or a Notebook place, radius 150 m". E13 L128: "remind me to take out the trash when I get home" "with home saved".
- **Evidence:**
  - No Scope item, Decision, build task (task 12 L94 covers only "place and person fields on the reminder card") or H-row defines where Jeremy saves "home", how a saved address becomes coordinates, or what the UI looks like.
  - Turning an address into coordinates offline needs a geocoder. AOSP has no `Geocoder` backend (on phones it comes from Google Play services), which P5 excludes.
  - "Notebook place" belongs to phase 09, which is built after 03, so phase 03 would rely on a later phase (Rule 16).
  - QA would have to invent a setup path, and the likeliest one is a test-only injection (Rule 4).
- **Fix:**
  - Define the place source in phase 03: for example, a Cortana Settings "Places" list whose "Set to my current location" reads one `LocationManager` fix. No address geocoding, P5.
  - List its page as an approximation with an H-row. Drop "Notebook place" from phase 03; phase 09 may ADD it.
  - E13 setup: `adb emu geo fix <home lon> <home lat>` → Settings → Places → Home → "Set to my current location" (dump) → move outside → create the reminder → move inside.

### R3T-B8 — Phase 08 — E2 checks memory providers that phase 09 adds later, and phase 09 has no row for the ruling
- **Claim:** L34 (owner ruling R2D-06): "a locked-mode context that excludes phase 09's memory providers". E2 L53: "a question whose answer lives only in a Notebook memory show 'Unlock to continue' and are answered after unlock; the runtime log shows no memory provider was read while locked".
- **Evidence:**
  - Phase 08 is built and QA'd before phase 09 (INDEX build order; phase-09 L5 `depends-on: [03, 08]`). While phase 08 is under QA, no Notebook memory and no memory provider exists, so that clause cannot run.
  - Phase 09's rows (E1–E10, L68–L77) test locked voice corrections (E7) but never "the AI does not read memory while locked".
  - So the memory half of the owner ruling has no runnable test anywhere.
- **Fix:**
  - Phase 08 E2 keeps the general question and the contacts / calendar gate.
  - Phase 09 ADDs an E-row: with a memory pushed by the PC script, PIN set and the keyguard showing, a question answered only by that memory shows "Unlock to continue"; diagnostics show no memory provider read while locked; after unlock the question is answered from the memory.
  - Replace "the runtime log" with diagnostics read by phase 01's command.

---

## 3. MAJOR

### R3T-M1 — Phase 01 — E9 and E13 leave state behind (RV12); E12's clock restore is not verified (T-M6 residue)
- **Evidence:**
  - E9 L180: "once the data is older than 60 minutes (clock moved as in E12) … restore: `adb shell cmd connectivity airplane-mode disable`". E12's clock move is `adb root`, `settings put global auto_time 0`, `date MMDDhhmm`, so E9 leaves adbd as root, automatic time off and the clock moved. E16 L188 then asserts rejection "from the shell uid", but the calls would arrive as uid 0.
  - E13 L185 changes "accent, background image, transparency, show-more-tiles, theme and press effect style" with no restore.
  - E12 L184 restores with `auto_time 1`, which does not guarantee the clock snaps back at once.
- **Fix:**
  - E9 ends with `settings put global auto_time 1`, `adb unroot`, `adb wait-for-device`.
  - E13 ends by restoring the recorded Settings values (dump check).
  - Every clock-moving row (01 E9, E12; 07 E10; 09 E3) sets the clock back with `date` before `auto_time 1` and records `adb shell date` within 2 s of the host.

### R3T-M2 — Phases 03, 09 — `cmd uimode night yes` does not set the shell's own theme
- **Evidence:**
  - phase-03 E15 L130 and phase-09 E9 L76: "in dark theme (`adb shell cmd uimode night yes`; restore `night no`)".
  - The shell's theme is its own setting: phase-01 L33 "Start/theme page (… light/dark …)", X21 L162 "dark (agent pick)", E13 L185 "Changing … theme … in Settings changes Start".
  - The command changes the system night mode, which the shell does not follow, and RV12's baseline list does not include system night mode.
- **Fix:** the precondition is "shell theme = Dark (Settings > Start, dump shows the Dark option selected)". Drop the `uimode` commands.

### R3T-M3 — Phases 03, 04, 06, 09 — PLAN RV11's phone P-row for tolerances ≤ 17 ms was not applied to the R7-derived rows
- **Evidence:**
  - PLAN L196–L197: "Timings whose tolerance is ≤ 17 ms also get a phone P-row from a faster capture".
  - These rows are ≤ 17 ms:
    - phase-03 E15 L130: "pane settling 250 ± 17 ms", "black … 283–300 ± 17 ms".
    - phase-04 E4(g) L82: "217 ± 17 ms", "134 ± 17 ms"; E4(d) L82: "183–200" (± 17).
    - phase-06 E12 L83: "(± 17 ms + one frame)"; E17 L88: "257 ± 17 ms + one frame".
    - phase-09 E10 L77 reuses 03's.
  - Phase-03 P5 L132 lists only the waveform, speaking-halo and entrance timings. Phase 04 (P1–P5) and phase 06 (P1–P6) have no such row.
- **Fix:** add a "re-measured from a phone screenrecord as phase 01 P10" row to 04 and 06, and extend 03 P5 with E15's timings.

### R3T-M4 — Phase 03 — the Settings item's position in the ≡ pane contradicts itself once Feedback is dropped
- **Evidence:**
  - L65: "a bottom group Settings (gear icon) anchored just above the text box … Settings' cap top 172 ± 1 epx above the screen bottom … W10M's Feedback item below Settings is left out".
  - E15 L130: "Settings cap top 172 epx above the screen bottom".
  - r7 L348: "Settings label cap top 172 ± 1 epx above the screen bottom; Feedback 48 epx below it; Feedback cap top 30–32 epx above the query-bar top". The item "just above the query bar" on W10M is Feedback.
  - A builder who anchors Settings just above the text box puts its cap top ≈124 epx above the bottom and fails E15. A builder who keeps 172 epx leaves an empty 48-epx slot, which contradicts "anchored just above".
- **Fix:** say which. Recommended: Settings keeps W10M's 172-epx position and Feedback's slot stays empty (approximation, H23 widened). E15 then checks that no item occupies the 124-epx slot.

### R3T-M5 — Phase 03 — E15's setup needs a reminder with no time, which the reminder card cannot create
- **Evidence:**
  - E15 L130: "Setup through the reminder card: a reminder later today with a photo, one for tomorrow and one without a time".
  - L58: "Missing time … the card title reads 'When would you like to be reminded?' over the same fields". That card asks for a time; nothing says "Remind" with an empty time stores a Whenever reminder.
  - L71: "+ opens the reminder page layout empty, save enabled once the text field has text" (H25).
- **Fix:** create the Whenever reminder with + on the Reminders page (L71), or with a place reminder, since L72 lists those under Whenever. Name that path in E15.

### R3T-M6 — Phase 03 — E13's geofence can take 30 min or more to fire, and the row has no wait limit
- **Evidence (AOSP GeofenceManager.java main L487–L498):** `intervalMs = (long) Math.min(MAX_LOCATION_INTERVAL_MS, Math.max(mSettingsHelper.getBackgroundThrottleProximityAlertIntervalMs(), minFenceDistanceM * 1000 / MAX_SPEED_M_S))`. SystemSettingsHelper L71–L72: `DEFAULT_BACKGROUND_THROTTLE_PROXIMITY_ALERT_INTERVAL_MS = 30 * 60 * 1000`.
- **What that means:** the location request behind `addProximityAlert` asks for a fix at most every 30 min by default, whether or not the app is in the foreground. E13 L128 names no wait, no pass limit and no permission or location-mode setup.
- **Fix:**
  - Record the setting, then `adb shell settings put global location_background_throttle_proximity_alert_interval_ms 5000`. This is a real platform setting, the same kind of step as phase-07 E6's `lock_screen_lock_after_timeout`.
  - Enable location (`adb shell cmd location set-location-enabled true`; not certain of this verb, `settings put secure location_mode 3` is the older form) and grant `ACCESS_BACKGROUND_LOCATION` through the checklist.
  - Pass = the notification within 60 s of entering the radius. Restore the setting.
  - P11 keeps the real phone latency.

### R3T-M7 — Phase 03 — E10 enters the PIN without raising the bouncer, and "the pending request" is ambiguous
- **Evidence:**
  - E10 L110: "call, text, … each show the 'Unlock to continue' card … entering the PIN (`adb shell input text 1234`, `adb shell input keyevent KEYCODE_ENTER`) then completes the pending request".
  - L54: the card has "one accent button 'Unlock' that raises Android's unlock prompt (`KeyguardManager.requestDismissKeyguard`)". Without a tap on Unlock, the injected keys go to the focused Cortana session window, not a bouncer.
  - After nine gated commands in a row, it is undefined which one is "pending": only the last, or all.
- **Fix:**
  - Run each gated command, assert the card and no change, then cancel it.
  - For one chosen gated command, tap "Unlock" at its dump bounds, confirm the bouncer in `dumpsys window`, run `input text 1234` plus `KEYCODE_ENTER`, and assert that request completes.
  - L54 states what happens to earlier cards (Recommended: only the card on screen is pending).

### R3T-M8 — Phase 04 — E4(b) releases exactly at Y6's threshold, which E4 also says it does not measure
- **Evidence:**
  - E4(b) L82: "`UP` at 50 % and at 78 % open settles fully open".
  - Y6 L68: "released without a fling: opens at ≥ 50 % of the screen height".
  - E4 L82 ends: "Y5–Y12 are judged in H8–H15, not measured here".
  - "Open %" is defined only in R7 (L490: "the handle bottom as a fraction of screen height"). The finger sits 10.7 epx (1.4 % of 780) below the handle, so a 50 % release decides open versus close on which reference QA picks.
- **Fix:**
  - State in Decisions that open % = handle bottom ÷ screen height.
  - E4(b) measures the 290-ms settle at 60 % and 78 %.
  - The Y6 check brackets the threshold (40 % closes, 60 % opens) and is labelled as approximation Y6, also judged in H9.

### R3T-M9 — Phase 04 — E5's volume-panel geometry is the two-slider, media-playing state, but E5 names no state
- **Evidence:**
  - L28: "Volume panel geometry (R3 A20 …): full width from the screen top to 274 epx … the second row 100 epx lower; a 'Vibrate on' caption".
  - w10m-measurements L219: A20 is the "**two-slider state, media playing**".
  - E4(g) and L35: the default show rests at "100.4 epx with one slider".
  - E5 L83: "geometry measure within ± 1.1 epx of the R3 A19 / A20 values" gives no setup, so a correct single-slider panel fails the 274-epx check.
- **Fix:** E5 sets up media playing and expands the second slider (Y12 chevron) before measuring 274 epx. The one-slider rest height (100.4 epx, R7 §4.5.1) is checked separately.

### R3T-M10 — Phase 06 — swipe and threshold rows give no duration, no extent reference and no fling rule
- **Evidence:**
  - E12 L83: "an `adb shell input swipe` up the Slide up bar 10 epx short of the bar's height leaves it returning to rest". `input swipe` with no duration uses 300 ms (InputShellCommand default), so 73 epx in 300 ms is well above the minimum fling velocity. The L44 approximation ("commits once the bar has moved up by its own height") does not say whether a fling commits.
  - E16 L87: "`adb shell input swipe` left to 40 % of the width … a swipe to 60 % deletes". "To 40 %" could be the end x or the travel. L48: "A swipe released past 50 % of the width deletes", again with no fling rule.
  - E16's Undo check lists "row count before, after delete, and after Undo" with no expected values. Whether the provider delete happens at once or when the Deleted bar hides is undefined.
- **Fix:**
  - L44 and L48 state the release rule, e.g. "distance only; fling ignored" or a fling threshold.
  - E12 and E16 give start and end coordinates as travel distance, with a slow duration (e.g. `… 1500`).
  - E16 states the expected counts (N, N − k once the provider write happens, N after Undo) and when the write happens.

### R3T-M11 — Phase 06 — E18's "no network request" check cannot fail, and the MMS-part checks name no trigger
- **Evidence:**
  - E18 L89: "The same run with the network off (phase 01 E9's network-off proof) shows the shell makes no network request". With the network off, a request that is attempted leaves nothing to observe.
  - E18 L89: "Contact writes an MMS whose part in `content query --uri content://mms/part` is `text/x-vcard` … Sending the MMS is P2's; the emulated modem has no MMSC". It does not say which action writes the part: a draft save, or a send attempt that leaves the message in the outbox or failed.
- **Fix:**
  - Run Your location with the network on and the emulator started with `-tcpdump <file>`. Assert no DNS or TCP to any host in the action window, with Weather refresh idle.
  - Define the trigger: tap Send → the message row lands in `content://mms` (outbox or failed box) with the named part.

### R3T-M12 — Phase 06 — E10's `grep mResumedActivity` matches nothing on current Android
- **Evidence:**
  - E10 L81: "Phone book resumes it (`adb shell dumpsys activity activities | grep mResumedActivity`)".
  - ActivityTaskManagerService main L5734–L5736 prints `"  ResumedActivity: "`, and Task.java L5967–L5968 prints `"  topResumedActivity="`. There is no "mResumedActivity" label, so a correct build fails the grep.
- **Fix:** `grep -i resumed` (as phase-01 E11 L183), or `adb shell dumpsys activity top-resumed` (ATMS L4366 header).

### R3T-M13 — Phase 09 — E5's helper-pid clause has nothing to compare on the emulator
- **Evidence:**
  - E5 L72: "its diagnostics entry shows caller uid 2000, the caller pid and the recorded helper pid, the two pids differing".
  - The helper starts over Wireless debugging and every helper row is phone-only (phase-04 P1–P3 L86–L88; no emulator row starts it). On the AVD the "recorded helper pid" is empty, so "differing" is true for any build.
- **Fix:** keep the acceptance and logging on the emulator. Add a P-row with the helper running: `adb shell content call` is accepted, diagnostics show a recorded helper pid ≠ the caller pid, and that pid matches the helper process in `adb shell ps -A`.

### R3T-M14 — Phase 05 — E1's `ime list -s` lists only enabled input methods, so it runs before the IME can appear
- **Evidence:**
  - E1 L66: "`adb shell ime list -s` includes the shell IME; `adb shell ime enable <id>` … then `adb shell ime set <id>`".
  - InputMethodManagerService main L6383–L6386 (shell help): "prints all enabled input methods. -a: see all input methods -s: only a single summary line of each".
- **Fix:** `adb shell ime list -a -s` before `ime enable`; `ime list -s` after it shows the shell IME enabled.

---

## 4. MINOR

- **R3T-m1 — Phase 03: phone rows are misnumbered.** L132 runs "P1 … P6 … P11 … P12". Renumber P11 and P12 to P7 and P8.
- **R3T-m2 — Stale H-row ranges.**
  - phase-03 H3 L134: "not covered by H5–H20", but H21–H28 exist.
  - phase-05 H2 L81: "not covered by H3–H18", and E3 L68 "judged in H3–H18", but H19–H23 exist.
- **R3T-m3 — Phase 05: the R2D-12 additions have no values.**
  - E12 L77 "one-handed left and right shift the key grid (dump bounds)" gives no width or offset, so any shift passes.
  - The double-tap Shift window, the double-space window (H23 says "timing") and the "a word typed twice" learning threshold are not in Decisions (L42).
  - H19–H23 have no stand-in recorded (RV9).
  - Two separate `adb shell input tap` processes can take longer than a double-tap window. Use instrumentation `UiDevice` with exact sleeps, as E4 does.
  - Edge L90 "switching languages if more than English is ever added" contradicts the English-only ruling (L41) and the no-future-proofing rule. Delete it.
- **R3T-m4 — Phase 03: the pane-settle tolerance is narrower than its source.** L65 "settling 250 ± 17 ms after its first frame (§3.1.10)". r7 L349 also measured "N1 first open from Home … completed in 83 ms" and N2 "≈300 ms". E15 opens the pane from Home. Either tag 250 ms as N1's from-Notebook reading with the spread noted, or make it an approximation with an H-row.
- **R3T-m5 — Phases 03 and 09: phase 09 invalidates a phase 03 check.** phase-09 L43 moves Reminders "to the third" slot. phase-03 E15 L130 still asserts "Reminders with cap tops … 119.5". Phase 09 should say that clause of 03 E15 is superseded once Notebook is ADDed, for any later re-run.
- **R3T-m6 — Force-stop side effects.**
  - `adb shell am force-stop app.tileshell` runs in 01 E4b, 02 E4, 03 E6 and 09 E8. A force-stopped package loses its AlarmManager alarms (from memory of AlarmManagerService; not re-checked this round). Cortana reminders from earlier rows then silently lose their triggers unless the shell re-arms them on start. No Decision or edge case covers it.
  - phase-09 E8 L75 says force-stop "restarts `app.tileshell:llm`"; it kills it, and the next request restarts it.
- **R3T-m7 — Phase 06: E12's action-row tolerance comes from the wrong source.** E12 L83 "(dump bounds, ± 1.2 epx)" is P3's ruler. §1.5.5 comes from P4 camera footage, "± 3 % distances" (L44).
- **R3T-m8 — Phase 06: E17's "Attaching..." row may never be captured.** E17 L88 "shows 'Attaching...'". R7 §2.3.8 saw it for ≈2.5 s. A local 3:4 test photo may be ready in under one frame. State a minimum display time (approximation) or read it from screenrecord frames.
- **R3T-m9 — Camera slot assignment conflicts across phases.**
  - phase-06 E18 L89 "Camera resumes phase 01's Open Camera fixture".
  - phase-03 E2 table L114 "the AOSP Camera in the Camera slot".
  - phase-01 E4 installs two camera fixtures, so the slot starts unassigned.
  - Each row should state its slot assignment step.
- **R3T-m10 — Phase 07: E3 needs a clock setting with no command.** E3 L50 "at an emulator time before 10:00" gives no clock command or restore (RV12). Reuse E10's clock commands and restore.
- **R3T-m11 — Phase 08: E2 reads the wrong log and skips the unlock step.** E2 L53 says "the runtime log" (should be diagnostics, phase 01's command). Its "answered after unlock" has no unlock step. Use R3T-M7's Unlock-button sequence.
- **R3T-m12 — Phase 09: E7's locked correction has nothing to correct.** E7 L74 plays "No, I meant Jane" in a locked session where contact commands are gated, so there is no mistake to correct. Precede it with a locked general question (e.g. "how far is the moon" → "No, I meant the sun") so the refusal is of a real correction attempt.
- **R3T-m13 — Phase 03: the checklist list misses permissions the phase needs.** Scope L18 lists "(assistant role, microphone, contacts, calendar write, send SMS, phone calls, exact alarms, …)". Build task 12 L94 adds background location and call log. SMS-provider read for person triggers, and calendar read for "what's on my calendar", are in neither.
- **R3T-m14 — INDEX and PLAN disagree on what R7 gates.** INDEX L29 R7 row: "FINAL of phases 04, 06, 09". PLAN L246: "Gates FINAL of phases 03, 04, 06, 09 (03 added 2026-09-17)"; INDEX L18 also names 03.
- **R3T-m15 — The owner record does not match the question it cites.** r2-triage L16 lists R2D-13 "(a) time reminders only; (b) time, place and person reminders". r2-owner L6 records "A. All three W10M reminder kinds … Jeremy: 'A'". If the question was re-lettered when asked, record the wording as asked. Read against the triage file, "A" means time-only.
- **R3T-m16 — Phase 06: two loose ends in E13 and E10.**
  - E13 L84 "the page is gone 5.0 s later" has no tolerance (LOW candidate; R7 L144 "≈5.0 s").
  - E10 L81 reads "the voicemail number diagnostics read from `TelephonyManager.getVoiceMailNumber()`", but no phase-06 Decision ADDs that diagnostics entry.

---

## 5. RV9: rendered values still untagged (file:line)

| File:line | Value | Note |
|---|---|---|
| phase-03:64, :94 | Place and person fields on the reminder card | H22 exists, but no stand-in is recorded |
| phase-03:64 | Places (saved home) page | No value, no H-row (R3T-B7) |
| phase-03:65 | Accent-fill hold between choosing a ≡ destination and the black frame | r7 §3.1.12 measured 350–500 ms touch → black; the doc says only "then" |
| phase-03:66 | Delay from a Reminders row tap to the reminder page | r7 §3.2.6: 550 ms; untagged |
| phase-03:60 | Cortana's "needs to be the default assistant" role-notice page | No look row |
| phase-03:65 | Pane settle 250 ± 17 ms | Tolerance narrower than the source spread (R3T-m4) |
| phase-09:44 | Notebook row tap → page | r7 §3.2.5: title cleared ≈183 ms and page at ≈617 ms after touch; the doc goes straight from the ≈117-ms fill to the page |
| phase-05:42 | Long-press alternates popup, one-handed layout, caps-lock indicator, double-space window | H19–H23 name them; no stand-in recorded (R3T-m3) |
| phase-06:37, :47 | Phone Settings (Text reply messages), Blocked calls, Messaging Settings and Blocked messages pages | No values, no H-row; r7 §2.7.1 measured the 14393 Messaging Settings structure |
| phase-06:39 | History "Details" page | Wording only (r7 §1.3.8); geometry untagged, no H-row |
| phase-06:50 | Attach flyout fill in dark theme | r7 §2.3.6 measured the light theme only ((246–249) on white) |
| phase-06:36, :44 | System-bar treatment and the lock-screen nav-bar variant under the incoming call | R3T-B4 |
| phase-08:29, task 1 | "Waiting for Wi-Fi" / "Download now" and the download progress UI | These are P4 designs with no look row (PLAN P4 L75–L77) |
| phase-04:31 | Notification-list offset during the drag | R3T-B2 (needs an approximation row) |

## 6. Owner questions

1. **R3T-B6:** "Next time I talk to Mom" should fire when: (a) any answered call or any text between you and her, either direction;
   (b) only when you call or text her; (c) other / let me clarify. Agent default if unanswered: (b), the wording already recorded.
