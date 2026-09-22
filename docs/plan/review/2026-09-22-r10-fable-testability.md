# R10 plan change — Reviewer 2 (Fable, testability and evidence lens), 2026-09-22

Read-only review of the 2026-09-22 additions in PLAN.md (R10-Q1..Q5 and the two scope adds) against the FINAL
phase docs, the QA harness (qa/phase-03/scripts/lib.sh, qa/phase-01/scripts/music8.sh) and the running AVD.
Lens: what each addition can and cannot PROVE, on which device, and what a row has to measure so it can FAIL.
Design and correctness are Reviewer 1's lens and are not repeated here. Default verdict where a doc asserts
without evidence: not proven.

## Facts established today (read-only probes on emulator-5554, sdk_phone64_x86_64 API 36 userdebug)

These are the evidence base for the findings below; nothing in PLAN.md records them yet.

- Cross-window blur is AVAILABLE on this AVD: `getprop ro.surface_flinger.supports_background_blur` = 1,
  `dumpsys window` shows `mBlurEnabled=true`, `settings get global disable_window_blurs` = null (not disabled),
  `hw.gpu.mode=auto` (host GPU). So blur rows CAN run here. Nothing is known about the S25 Ultra.
- The shell holds HOME and ASSISTANT (`cmd role get-role-holders`), so `LauncherApps.getShortcuts` is callable.
- Ten installed fixtures publish App Shortcuts (`dumpsys shortcut`): Open Camera 5 manifest shortcuts (settings,
  video, gallery, selfie, camera — ALL targeting `MainActivity`), OsmAnd 3 dynamic, Fossify Messages 2, Fossify
  Notes 2, AOSP Settings 2, Contacts 1, Auxio 1 dynamic, Gramophone 1, FairEmail 1, DocumentsUI 1. `dumpsys
  shortcut` prints each shortcut's `activity=` and `shortLabel` but NOT its intent extras.
- 19 AppWidget providers exist (`dumpsys appwidget`), including DeskClock analog/digital and Calendar.
- `pm list users` → user 0 only: the managed profile phase 01's E18 used no longer exists.
- `content query --uri content://com.android.calendar/calendars` → "No result found": the AVD has NO calendar.
- `hw.camera.back=emulated`, `hw.camera.front=none` (tileshell_fhd config.ini).
- `navigation_mode` = 0 (3-button); the gestural overlay `com.android.internal.systemui.navbar.gestural` is
  installed and disabled, so the emulator CAN be switched to gesture navigation for a row.
- `appops get app.tileshell MANAGE_EXTERNAL_STORAGE` / `WRITE_SETTINGS` / `SYSTEM_ALERT_WINDOW` → default. The
  manifest has none of: CAMERA, WRITE_CONTACTS, MANAGE_EXTERNAL_STORAGE, WRITE_SETTINGS, USE_FULL_SCREEN_INTENT,
  FOREGROUND_SERVICE_MICROPHONE / _CAMERA, ACCESS_NOTIFICATION_POLICY.
- There is no `app/src/androidTest` — every device row is adb-driven (`input motionevent`, `uiautomator dump`,
  screencap / screenrecord), as phase 02's and 03's drivers are.
- The hold today: `EditGestures.kt:78` `withTimeoutOrNull(Edit.HOLD_MS) { waitForMoveOrUp(...) }` then
  `edit.enter(hit.key)` at line 81, then a drag only if the finger moves (line 84). The same 783 ms constant
  drives the app-list hold menu (`AppListMenu.kt:68`) and the Music hold menus (`MusicCollectionPage.kt:518`).
- Start's pager has exactly two pages (`StartActivity.kt:143-157`, `index == 0` = Start, else the app list);
  Back's rule is `pager.currentPage == 1 → page 0` (line 134); `userScrollEnabled = !edit.active` (line 151).

## Findings

### BLOCKING

1. **BLOCKING — R10-Q3 silently invalidates phase 02's hold evidence, and the re-cut has no t0.**
   PLAN.md:214-221; phase-02-edit-mode-folders.md:28, 61, 67, 68, 112-113; qa/phase-02/scripts/e7_bracket.py:96-102;
   qa/phase-02/scripts/e7_capture.sh:97-104; EditGestures.kt:78-87; INDEX.md:41.
   Today `adb shell input swipe x y x y 830` (a hold with no movement) is phase 02's PROOF of edit-mode entry
   ("830 ms enters edit mode", e7_bracket.py:101). Under R10-Q3 that exact gesture is a hold released without a
   drag, which opens the burst — so the 830-ms bracket flips from PASS to FAIL by design, E1 (line 61, "wait past
   the hold ... enters edit mode") and E8 (line 68, "hold ≥ 830 ms, MOVE") change meaning, and E7's entry motion
   (417 ± 50 ms scale, 550 ± 50 ms dim, measured from the hold's 783-ms mark) loses its reference event: the plan
   does not say whether the contraction starts at 783 ms while the finger is still down (then "no visible
   feedback during the hold", §1.1.1 at line 28, is broken and the burst appears over an already-contracted grid)
   or at the tap on the "Edit" satellite (then t0 is a touch-up on a satellite and the 783-ms hold is not
   observable until release). Phase 02's 14 owed re-runs are deferred to the end-of-build pass (INDEX.md:41); if
   quick actions land after that pass, the pass certifies semantics that no longer exist; if before, the rows
   must already be re-cut. What settles it: a phase-02 Change Log entry (the doc says post-FINAL changes need one,
   PLAN.md:218-220) that names t0 for E7, restates the bracket as 740 ms → tap launches / 830 ms → burst node
   present, no `edit_disc:*`, nothing launched / 830 ms + MOVE past slop → drag with no burst, and orders the
   quick-actions phase BEFORE the end-of-build re-run (finding 27).

2. **BLOCKING — Fluent acrylic on the action center and volume panel is unprovable as planned, and contradicts phase 04's measured rows.**
   PLAN.md:169-175; phase-04-action-center-volume-helper.md:27 (tile (48,48,48), background black "as captured"),
   28 (panel (55,55,55)), 32 (black scrim, alpha 1 − brightness fraction at six open fractions), 168 E4(a) ("the
   app's brightness below the handle is inside R7 §4.1.8's range"), 169 E5 (geometry and colours), 110-119 (R4
   parts still to run on the phone); r10-secondary-spec-review.md:50.
   Two separate problems. (a) An acrylic panel's pixels are a blurred, tinted copy of what is behind it, so E4(a)'s
   brightness ranges and E5's captured colours cannot both hold: a row written against R7's black-scrim values
   FAILS against acrylic by construction, and one written against acrylic has no R7 number to cite (RV9). The doc
   must say which wins on those two surfaces and re-cut E4(a)/E5 to whichever it is. (b) Both surfaces are
   overlay windows (phase 04 Scope, line 18). A non-privileged overlay cannot read the pixels behind it, so
   backdrop blur there is ONLY cross-window blur (`WindowManager.LayoutParams.setBlurBehindRadius` /
   `FLAG_BLUR_BEHIND`), which exists only when `WindowManager.isCrossWindowBlurEnabled()` is true on that device.
   It is true on this AVD (facts above); it is unknown on the S25 Ultra / One UI 8, and Samsung ships its own
   blur stack. If it is false there, phase 04's acrylic is a solid tint on the phone and the emulator evidence
   proves nothing about the product Jeremy holds. What settles it: add two commands to R4's phone-on-adb list
   (phase-04.md:110-119) — `adb shell getprop ro.surface_flinger.supports_background_blur` and
   `adb shell dumpsys window | grep mBlurEnabled` — and make the overlay-acrylic decision wait for them, the way
   P5 waited for the nav-bar probe. Also run the same two with Samsung power saving ON, because the platform
   turns cross-window blur off under battery saver and the shell must follow `addCrossWindowBlurEnabledListener`.

3. **BLOCKING — The setup wizard and the pod bay both change what every existing driver assumes about a fresh Start, and neither addition carries the harness change.**
   PLAN.md:179-182, 191-198; qa/phase-03/scripts/provision.sh:2-14, 52, 92; qa/phase-02/scripts/gestures.sh:44;
   lib.sh:205-208 (`ensure_start` = KEYCODE_HOME); StartActivity.kt:134, 143-157; phase-03.md:62, 111.
   provision.sh puts a wiped AVD into the state every row assumes by granting roles and permissions from adb and
   re-asserting the preferred HOME activity; it never drives a first-run UI. A wizard that shows before Start
   draws puts every phase 01/02/03/10 driver on the wizard page after a wipe. Separately, a pane LEFT of Start
   changes page indices: phase 02's return-to-Start is a right swipe (`input swipe 200 1200 950 1200 250`,
   gestures.sh:44) which would now open the pod bay; Back's `currentPage == 1` rule and "Home returns the pivot to
   Start at the top" (INDEX.md:87, regress.sh) both need a stated result for the pane. Each of these two phases
   must ship the provisioning change and a regression run (the phase-02 `regress.sh` pattern: at least one row
   per earlier phase) as part of its own gate, or the earlier evidence is silently orphaned. For the wizard, the
   only bypass that does not create a test-only entry point (phase 03's E5 allow-list rule forbids one) is a
   PRODUCT rule: when every grant the wizard would ask for is already held and the HOME role is held, the wizard
   has nothing to do and does not show. That rule is itself a row (finding 12).

4. **BLOCKING — Nine of the ten inbox apps have Windows 10 Mobile originals, and RV9 forbids a FINAL doc without measured values or tagged approximations; no measurement pass exists for any of them.**
   PLAN.md:199-203, 253-254 (RV9: "Phase docs go FINAL only with every value tagged: source, build, ± tolerance,
   reference ... or 'approximation' with a NEEDS-HUMAN row"); INDEX.md:32 (R8's shape and its three gaps);
   w10m-measurements.md / r6 / r7 cover none of Calculator, Alarms & Clock, Calendar, Photos, Camera, People,
   Voice Recorder, Films & TV, File Explorer or Settings.
   Phase 10 needed R8 before its now-playing task could be built, and R8 found no governing-build capture and no
   measurable motion. Expect the same per app here. Either schedule one measurement task per inbox phase (R11-x,
   gating FINAL, R8's format) or state up front that these apps are approximations throughout with one H-row
   each — but that choice has to be written before the split, because it decides whether the rows are
   "measure within ± tolerance" rows or "Jeremy accepts" rows (finding 26).

5. **BLOCKING — Five inbox apps take over slots and intent actions that phase 01 E4 and phase 03 E2/E10 already assert on, and the plan does not state the takeover rule.**
   PLAN.md:199-203; phase-01-start-live-tiles.md:81 (two fixture handlers per category), 187 (E4: "a category
   with 2+ handlers and no preference shows an unassigned tile"); phase-03.md:31 (photos → Camera slot app), 53
   (locked alarms via `AlarmClock.ACTION_SET_ALARM` + `EXTRA_SKIP_UI` as a voice activity), 116 (E10), 120 and
   125 (E2's alarm observable: "DeskClock lists it"); phase-10-media-player.md:85-93 (the correction: SlotResolver
   auto-assigns only with exactly one handler; the MUSIC slot needed `assignSlotOnce`, marker `slot:music:v1`).
   With the shell's own Camera, Photos, People, Calendar and Alarms declared, the AVD has THREE handlers for each
   of those categories, so E4's "2+ → unassigned" rows pass vacuously against tiles that now read "Tap to choose"
   for a different reason, and Tess's "set an alarm" / "take a photo" resolve to a chooser (two handlers) unless
   the action layer targets its own component explicitly. Before rows can be re-cut the plan needs: (a) which
   slots are seeded to the shell's apps (phase 10's `assignSlotOnce` path, one marker per slot), (b) whether a
   slot the user re-pointed BEFORE the update is left alone (an upgrade row: old build → assign fixture →
   `install -r` new build → assignment unchanged), (c) whether Tess's alarm / photo / calendar actions call the
   shell's own components directly (then E2's observable becomes `dumpsys alarm` under `app.tileshell` plus the
   shell's own alarm list in a dump, and E10's `startVoiceActivity` over the keyguard is to the shell's own
   activity — a different platform check than DeskClock's).

### SHOULD-FIX

6. **SHOULD-FIX — The satellite spring has a damping ratio but no stiffness, so its one measurable number has no frame to appear in.**
   PLAN.md:184-186 ("Spring animation (dampingRatio = 0.65f)"); RV11 at PLAN.md:269-274.
   ζ = 0.65 is underdamped, so the only fingerprint a recording can check is the first overshoot,
   exp(−πζ/√(1−ζ²)) ≈ 6.8 % of the travel, measurable from screenrecord frames as a satellite's maximum excursion
   past its rest position. WHEN that peak occurs depends on stiffness alone: Compose `Spring.StiffnessMedium`
   (1500) puts it ≈ 107 ms after release, `StiffnessLow` (200) ≈ 290 ms, `StiffnessHigh` (10000) ≈ 41 ms — under
   three frames at 60 fps, which RV11 routes to a phone P-row. State the stiffness (or the settle time) so the row
   can say "peak overshoot 6.8 % ± one frame's travel at t ≈ N ms"; without it the row can only assert
   "overshoots at all", which a wrong ζ also passes. `boundsInRoot` is a mechanism, not a value, and needs no row
   beyond finding 10.

7. **SHOULD-FIX — "At most three App Shortcuts show" is vacuous without a selection rule.**
   PLAN.md:216-217; `dumpsys shortcut` facts above (Open Camera publishes 5).
   Any three of Open Camera's five satisfy "at most three". The row can only fail if the doc states the rule
   (manifest before dynamic, then `ShortcutInfo.getRank`, ties by id, is Android's own convention) and the row
   asserts the EXACT three ids the rule predicts, plus the shell tile (Weather) and a folder giving "Edit" alone,
   and Auxio's single dynamic shortcut giving Edit + 1 — three tile kinds, three different counts.

8. **SHOULD-FIX — "The right shortcut launched" cannot be asserted with the fixtures on the AVD.**
   PLAN.md:187-188; phase-02.md:40 (the secondary-tile arguments check reads a TextView in the test APK because
   `dumpsys activity` prints only "(has extras)").
   All five Open Camera shortcuts target `MainActivity` and `dumpsys shortcut` hides extras, so `dumpsys activity
   activities` cannot tell "video" from "camera": a satellite that launches the wrong shortcut passes. Reuse phase
   02's client-library test APK: give it four static shortcuts targeting four distinct activities, each showing
   its shortcut id in a TextView, and have the row read that text after each satellite tap — the same pattern
   that made E5's arguments check fail-able.

9. **SHOULD-FIX — Work-profile shortcuts are a silent-pass path and the AVD no longer has a profile to prove them on.**
   PLAN.md:220-221; `pm list users` → user 0 only.
   `getShortcuts` takes a `UserHandle`; a tile for a work-profile app queried under the wrong user returns an
   empty list, which renders as "Edit alone" — indistinguishable from a tile that truly has none. The row must
   create a managed profile (`pm create-user --profileOf 0 --managed`, as E18 once did), install a shortcut
   publisher into it, and assert its shortcuts on that profile's tile; and the edge list should name it.

10. **SHOULD-FIX — Burst placement and the "Edit" outcome are unstated for the four tiles where `boundsInRoot` is not the grid.**
    PLAN.md:184-189, 215-217; INDEX.md:66 (RecentPromotion is a transform applied in onResume and NOT applied in
    edit mode); INDEX.md:41 (bottom row edited in edit mode); phase-02.md:35 (expanded folder band).
    Rows can assert satellite bounds from the dump only if the doc says what happens for: a bottom-row tile
    (satellites cannot go below it — the drawn nav bar is there), a top-left corner tile, a member tile inside an
    expanded folder band, a wide tile, and Start scrolled 1.5 screens (root bounds ≠ content bounds). And for the
    PROMOTED tile: entering edit mode drops the promotion, so "enters edit mode on that tile" means the selection
    lands on a tile that has just jumped back to its grid cell — state where the selected tile is after Edit, or
    the row has no expected bounds. Each of these is one dump assertion once the rule exists.

11. **SHOULD-FIX — The hold/drag fork needs bracketing on touch slop, not just on time.**
    PLAN.md:214-216; EditGestures.kt:78 (`viewConfiguration.touchSlop`).
    Time is bracketed (740 / 830 ms). The new fork is spatial: hold + release with a wobble UNDER slop = burst,
    hold + move OVER slop = drag with no burst. On this AVD slop is ≈ 22 px (8 dp at 450 dpi). Drive both with
    `input motionevent DOWN; sleep 0.9; MOVE +4 px; UP` and `MOVE +40 px; UP`, assert `quick_burst` present /
    absent and the tile's bounds unchanged / changed. Without the spatial bracket a build that opens the burst
    on every hold (ignoring the drag) passes the time bracket.

12. **SHOULD-FIX — The wizard's rows must include its absence, its persistence and its skip, or they cannot fail.**
    PLAN.md:179-182; onboarding/Checklist.kt:74-105 (the row states the wizard would walk); phase-03.md:62, 111.
    Minimum fail-able set on the emulator: `pm clear app.tileshell`, launch → dump shows `wizard_*` and no Start
    grid; complete every step through the REAL dialogs (`com.android.permissioncontroller` for the HOME role and
    runtime permissions is uiautomator-tappable on AOSP) → Settings > checklist shows those rows GRANTED and the
    accent chosen is the one a tile pixel shows; `am force-stop` + relaunch → no wizard; `pm clear` → wizard
    again; skip at step 1 → Start draws, rows MISSING, and the next launch does not re-show it (or does — the doc
    must say which); kill mid-wizard (`am force-stop`) → resumes at its step (or restarts — say which). Plus the
    product rule from finding 3 as its own row: all grants held → no wizard. Phone-only: the HOME-role dialog is
    Samsung's on One UI 8 and the Settings pages the wizard deep-links into (notification access, Appear on top,
    Usage access) are Samsung's — each step tapped once on the S25U with the resumed activity recorded.

13. **SHOULD-FIX — Blur rows need a control state and a fixture backdrop, or "acrylic" is a screenshot nobody can fail.**
    PLAN.md:171-175; facts above.
    Two blur forms, two controls. Cross-window (later, phase 04 overlays): `settings put global
    disable_window_blurs 1` is the platform's own switch; the row captures the region behind the panel with blur
    on and off, asserts the on-capture's high-frequency energy (Laplacian variance) is below a stated fraction of
    the off-capture's, AND asserts the shell's fallback engaged when off (tint only, and a diagnostics line
    `[blur] cross-window=unavailable`) — both directions, so a tint-only build cannot pass. In-app (app-list
    backdrop, menus): blur the backdrop of a KNOWN high-contrast Start background (a checkerboard set through
    phase 01's background URI grant, `prefs_edit.py`) and measure the edge spread in epx against the stated
    radius; against the default soft wallpaper a radius of 0 and 20 look the same. One build-start check to record:
    that `screencap` on this AVD includes SurfaceFlinger's blur (it should — the capture is the composited frame —
    but one capture proves it; not proven here).

14. **SHOULD-FIX — "Open the pod bay doors" is under-specified for a row that compares strings, orders events and must not leak.**
    PLAN.md:227-234; phase-03.md:30 (the command list includes "open an app"), 53 (locked gate), 63 (spoken reply
    pass rule: reply text EQUALS the expected string + RMS > −40 dBFS), 111 (typed path must run the same
    matcher); lib.sh:244-251 (`reply_text` = last `text="…"` in the speech ring); qa/phase-03/scripts/utterances.py
    (the 35 synthesised commands); INDEX.md:42 (phase 03's E4, E7-E10, E13-E15 NOT RUN; that Tess speaks on the
    phone is unproven).
    Needed before a driver can be written: the exact reply string (with or without the ellipsis — equality is the
    comparison); the ORDER (reply spoken, then the pane opens — assertable from the ring's timestamps only if the
    pane logs `[podbay] opened by voice` after the speech line); what the user sees (the Tess session is its own
    window over Start, so the pane opening underneath is invisible until the session closes — say the session
    closes); the typed form through the real text box (`type_request`); the locked-phone outcome (opening a Start
    surface is the "opens apps" class → "Unlock to continue"? state it, E10-style row); and two negatives so the
    match is not a substring accident: "open the pod bay" and "open pod bay doors" go where the matcher rule says
    (not-understood, or the easter egg — say which), and the phrase must NOT reach the "open <app>" action (E3's
    diagnostics show which handler took it). The utterance is one line added to utterances.py. Caveat the phase
    doc must carry: the emulator row proves the pipeline with a Kokoro-synthesised voice; recognition of Jeremy's
    voice is a phone row, and the whole path sits on speech rows that have not run.

15. **SHOULD-FIX — "Optionally real Android widgets" cannot be gated; and the widget rows need the bind dialog and `dumpsys appwidget`.**
    PLAN.md:194-196; A8 at PLAN.md:49-50; facts above (19 providers on the AVD).
    A8 says everything in the feature list is in; "optionally" leaves the reviewer unable to say whether a build
    without AppWidgetHost passes. Decide. If in: binding needs the system consent dialog
    (`ACTION_APPWIDGET_BIND`, `bindAppWidgetIdIfAllowed` is false for a non-privileged host) — tappable by
    uiautomator on AOSP, Samsung's on the phone; a bound DeskClock digital widget's RemoteViews appear in the dump
    as native TextViews, so the row asserts its text equals the emulator clock; `dumpsys appwidget` lists the
    shell's host and bound ids, so persistence is `am force-stop` → relaunch → same ids bound and the widget
    updating. Samsung's own widgets (Weather, Calendar) render on the phone only.

16. **SHOULD-FIX — Files: the permission model is unstated and every row depends on it.**
    PLAN.md:201; manifest (no MANAGE_EXTERNAL_STORAGE); `appops get … MANAGE_EXTERNAL_STORAGE` = default.
    All-files access (`MANAGE_EXTERNAL_STORAGE`, granted on the AVD with `appops set app.tileshell
    MANAGE_EXTERNAL_STORAGE allow`, on the phone through One UI's "All files access" page) and SAF
    (`ACTION_OPEN_DOCUMENT_TREE`, a DocumentsUI consent per tree) give different rows. Either way the row set must
    include: copy / move / rename / delete of a pushed fixture asserted with `adb shell ls`, the NEGATIVE that
    `/sdcard/Android/data/<other package>` stays unreadable even with all-files access (Android 11+; the app must
    say so, not crash), and the ungranted state (checklist row red, the app says why). SD card / USB OTG are
    phone-only.

17. **SHOULD-FIX — The Settings front's "partial by nature" is untestable as a sentence; it needs a per-page table, and its toggle rows cannot exist before phase 04.**
    PLAN.md:201-203; phase-04 status DRAFT (INDEX.md:43); phase-01.md:90 (diagnostics command).
    Three classes, three observables: OWNED (the shell changes it itself — brightness via `WRITE_SETTINGS`
    (`appops set … WRITE_SETTINGS allow`; assert `settings get system screen_brightness`), rotation lock
    (`accelerometer_rotation`), Do not disturb via notification-policy access (`cmd notification allow_dnd
    app.tileshell`; assert `dumpsys notification` zen mode), Start background / accent / theme (the shell's own));
    HELPER (Wi-Fi, Bluetooth, data, airplane — phase 04, R4-gated, no row possible now); DEEP-LINK (an Android
    Settings action — on the AVD assert `dumpsys activity activities` resumed = the AOSP activity for that action,
    e.g. `com.android.settings/.Settings$WifiSettingsActivity`). The deep-link class is where the phone differs:
    One UI 8 resolves the same actions to Samsung activities and some actions to nothing, so the phone row is
    "each listed action started with `am start -a` on the S25U, resumed activity recorded, and every unresolved
    action shows the shell's own 'change this in Settings' line". Sequence the Settings front after phase 04 or
    state that helper toggles are ADDed in 04's build (Rule 16 allows an ADD, not a swap) — finding 27.

18. **SHOULD-FIX — Calendar: the AVD has no calendar, so an inbox Calendar app has nowhere to write until the local-calendar rule exists.**
    PLAN.md:200; facts above ("No result found"); phase-03.md:108/127 (E2's calendar observable is a `content
    query` on events).
    Without a Google account (P5) the app must either create a local calendar as a sync adapter
    (`caller_is_syncadapter=true`, `ACCOUNT_TYPE_LOCAL`) or the rows must create one first. State which; the
    fail-able row is then: event created in the app → `content query` on `events` lists it with that calendar id;
    `content insert` of an event → the app lists it; delete both ways. On the S25U the phone row is that the same
    event appears in Samsung Calendar's local calendar and Samsung-created events appear here.

19. **SHOULD-FIX — Alarms & Clock: firing rows need the clock jump, the keyguard and a permission the manifest lacks.**
    PLAN.md:200; AndroidManifest.xml:48 (`USE_EXACT_ALARM`); PLAN.md:277-280 (RV12 clock restore); phase-03.md:126
    (the Doze procedure).
    `dumpsys alarm` proves the alarm is SET; only a fire proves the app. Row: alarm at now + 2 min,
    `locksettings set-pin 1234`, `KEYCODE_SLEEP`, jump the clock past it (`adb root`, `auto_time 0`, `date`), then
    `dumpsys window` shows the alarm activity above the keyguard within N s and `dumpsys notification --noredact`
    shows the full-screen intent; the timer likewise through Doze (E2's procedure); restore per RV12. That needs
    `USE_FULL_SCREEN_INTENT`, which is not in the manifest; on API 34+ it is a special app access, granted by
    default to a sideloaded app on AOSP — on One UI 8 not proven, so the phone row is the grant state plus one
    real fire with the phone locked. Alarm SOUND on the phone through Samsung's alarm stream and Sleep mode /
    Modes and Routines is phone-only.

20. **SHOULD-FIX — Camera: front-camera rows cannot exist on this AVD, and the app re-cuts phase 01 E4 and phase 03 E2.**
    PLAN.md:200; config.ini `hw.camera.front=none`; phase-01.md:81 (Camera resolves by
    `STILL_IMAGE_CAMERA`); phase-03.md:31 (take a photo → the Camera slot app); manifest (no CAMERA).
    Emulator rows that can fail: capture → `content query` on `MediaStore.Images` count +1 with width/height as
    projected; record → `MediaStore.Video` count +1 with `duration` ≥ the recorded seconds. Selfie / flash / HDR /
    zoom / 200 MP modes / pro mode are phone-only and each needs a NEEDS-HUMAN row for image quality (no metric
    settles "looks right"). Declaring `IMAGE_CAPTURE` / `STILL_IMAGE_CAMERA` makes the shell a third Camera handler
    on the AVD (finding 5).

21. **SHOULD-FIX — Voice Recorder is the one inbox app that is fully provable here, and the plan should say how, plus its cross-effect on Music.**
    PLAN.md:200; qa/phase-03/README.md (the null-sink microphone route, `audio.sh` `rms`); phase-10 Q6
    (INDEX.md:49: ringtones, alarms and recordings appear in the Music library, recorded as NEEDS-HUMAN).
    Row: record while `paplay` feeds a known 5-s WAV into the AVD microphone → the file's duration is 5 ± 0.5 s
    and RMS > −40 dBFS; record 5 s of silence → RMS < −60 dBFS; both directions so a recorder that writes silence
    cannot pass. Background recording needs `FOREGROUND_SERVICE_MICROPHONE` and a notification (assert via
    `dumpsys notification`). Cross-effect to rule and assert: a recording saved to MediaStore shows up in
    Music's songs pivot (MUSIC6's fixture path) unless the rule says it is filtered — either is a row, "unruled"
    is not. Real-mic quality and Bluetooth mics are phone / NEEDS-HUMAN.

22. **SHOULD-FIX — Video player: emulator rows exist with a synthetic fixture; hardware formats and the default-app takeover are phone-only.**
    PLAN.md:200; A11 at PLAN.md:46-47.
    `ffmpeg -f lavfi` can make an mp4 whose frame colour changes every second (solid colours, not `testsrc`), so a
    row asserts a `screencap` pixel at t = 3 s is the third colour, seek to 7 s lands on the seventh ± 1 s, and
    pause holds the pixel — position proven from pixels, not from the app's own label. `dumpsys media_session`
    proves it pauses Music's session (phase 10). HEVC / HDR10+ / 4K60 decode, and whether One UI hands the shell
    `ACTION_VIEW video/*` by default, are phone rows. A11 negative in one line: an `http://` VIEW intent is
    refused (no streaming).

23. **SHOULD-FIX — People: `WRITE_CONTACTS` is missing and there is no CONTACTS role, so "default People app" needs an observable.**
    PLAN.md:200; manifest (READ_CONTACTS only); provision.sh:102-108 (contact fixtures via `content insert`).
    Rows: edit a fixture contact → `content query` on `data` shows the change; the shell handles `ACTION_VIEW`
    on a contact URI (`am start -a android.intent.action.VIEW -d content://com.android.contacts/contacts/1` →
    `dumpsys activity activities` resumed = the shell's People activity). Phase 03's contact lookup and person
    reminders read the provider and are unaffected, but E4's People category (finding 5) is.

24. **SHOULD-FIX — Two harness contracts every new window and row must sign, written into each new phase doc rather than rediscovered.**
    MusicActivity.kt:42 (`testTagsAsResourceId` — MUSIC6's first run failed 27/35 without it); music8.sh:24-28
    (a row node carries no text; four comparisons passed vacuously against ""); lib.sh:110-122 (zero assertions
    = FAIL), 44-67 (blob + APK stamp).
    The burst, the pane, the wizard, and each of the ten app windows need `testTagsAsResourceId` on their root
    and a test tag on the node that CARRIES each text a row reads. Say it in the phase doc's Decisions so a
    reviewer can check it before the first device run, not after.

25. **SHOULD-FIX — Every addition needs diagnostics lines, because the silent-empty cases all render as a legitimate state.**
    lib.sh:126-130 (`diag` reads the listener's `dump()`); EditGestures.kt:80 (the existing `[edit] hold ... on
    <id>` line); INDEX.md:71 (the phone's Diagnostics page named both faults).
    Cases where a wrong result looks like a right one without a log line: a shortcut query that returns empty
    (→ "Edit alone"), cross-window blur unavailable (→ tint), a pod feed with no data (→ empty pod), the wizard
    skipped by the "all grants held" rule vs never started. Each gets a ring line with the reason (`[quick]
    shortcuts for <pkg>/<user>: n`, `[blur] cross-window=…`, `[podbay] opened by swipe|voice`, `[wizard]
    skipped: all held`), and the rows assert the line as well as the screen.

26. **SHOULD-FIX — NEEDS-HUMAN rows for the additions must say which of two kinds they are.**
    PLAN.md:84-87 (P4: each design gets a look row), 253-254 (RV9), 185-187 ("no NEEDS-HUMAN row can be closed
    against footage"); qa/phase-03/NEEDS-HUMAN.md (the sheet's shape: what / why his call / where to look).
    Fluent, the burst, the pod bay and the wizard have no W10M original: their H-rows are "accept this P4 design"
    rows and Jeremy cannot be asked whether they match footage. The inbox apps DO have originals: until finding
    4's measurement pass exists their H-rows would also be "accept" rows, and after it they become "matches R11
    within tolerance" rows judged on the phone. The phase docs should label each H-row with its kind so the sheet
    does not present an accept row as a fidelity row.

27. **SHOULD-FIX — Phase split and order, from the testability side (the brief's item 4).**
    One goal each, each independently gate-able; the order is forced by the harness and by Rule 16, not by taste.

    | # | Phase | Why this order | Touches shipped 01/02/03 |
    |---|---|---|---|
    | A | Tile quick actions (burst, satellites, App Shortcuts, "Edit") | FIRST: it re-cuts phase 02's hold rows (finding 1) and must land before the end-of-build re-run of 02's owed items, so those run once against final semantics. Emulator-provable end to end with the fixtures listed above plus finding 8's test APK | 02 (E1, E7 bracket + t0, E8; H-rows for the burst), 01 (INDEX.md:72 rationale) |
    | B | First-run setup wizard | Early and small: every later phase's provisioning is written against it (finding 3); one phone row (One UI role dialog) | 01 (checklist, H33), every phase's provision step |
    | C | Fluent materials — the one permanent in-app acrylic engine (Rule 16 / RV2) applied to surfaces that already exist: app-list backdrop, app-list hold menu, Music hold menus, the burst's backdrop from A | Engine before consumers; emulator-provable (finding 13). Its cross-window form is an ADD inside phase 04, gated on the R4 blur probe (finding 2), so nothing here waits on the phone | 01 (app-list backdrop), 02 (H21 menu), 10 (menus) |
    | D | The pod bay: pane, the four pods from existing feeds, AppWidgetHost, and the Tess command | After C (its backdrop) and after 03's speech rows have actually run (finding 14); pane rows are emulator-provable, the voice row inherits 03's status | 01 (pager, Back, Home, return-to-Start drivers), 03 (matcher, grammar, locked gate, utterances.py) |
    | E1 | Calculator + Alarms & Clock + Voice Recorder ("local apps, no slot") | Three apps with no slot and no provider; the alarm re-cuts 03's observables (finding 5c); all emulator-provable | 03 (E2 alarm row, E10) |
    | E2 | Photos + Camera + video player ("MediaStore apps, Photos / Camera slots") | Shared fixture path (make_photos.py, ffmpeg); slot seeding rule from finding 5 | 01 (E4, Photos tile feed, Camera bottom-row tile), 03 (take a photo), 10 (session focus) |
    | E3 | Calendar + People ("provider-backed apps, Calendar / People slots") | Shared provider fixtures; the local-calendar rule (finding 18) | 01 (E4, Calendar tile, People label), 03 (calendar cards insert into the shell's calendar, contact lookup) |
    | E4 | Files | Its own interview first (finding 16) | none |
    | E5 | The W10M Settings front | LAST, after phase 04, so its helper toggles exist (finding 17); or explicitly "owned + deep-link now, helper rows ADDed in 04" | 01 (Settings hub) |

    Each of E1-E5 needs its R11-x measurement task gating FINAL (finding 4). The Start mark (R10-Q2) is already
    built with evidence (qa/phase-01/STARTMARK) and is not a phase.

### NOTE

28. **NOTE — A recorded rationale in a FINAL part is now false.** INDEX.md:72: "Start tiles have NO long-press menu
    (a long press enters edit mode, R6 1.1)" was the reason the photo-tile settings live in Settings. Under
    R10-Q3 a hold DOES open a menu of sorts. Reviewer 1's call whether the photo settings move; from this lens it
    is only a stale line that a future agent would read as a rule.

29. **NOTE — The hold semantics change is scoped to Start tiles; add the control so the bracket re-cut does not leak.**
    AppListMenu.kt:44/68 and MusicCollectionPage.kt:509/518 keep hold-and-release = menu on the SAME 783-ms
    constant; music8.sh:13-18 drives it with `input swipe xy xy 1200`. One row in phase A: the app-list hold still
    opens Pin / Uninstall, not a burst, and the Music hold still opens its menu.

30. **NOTE — Gesture navigation IS available on this AVD** (the gestural overlay is listed, disabled), so the
    bottom-row hold and burst against the gesture area, and the pod-bay left-edge swipe against Android's back
    gesture, can be emulator rows (`cmd overlay enable com.android.internal.systemui.navbar.gestural`, restore
    with `disable`), with the phone row covering One UI's hint area only.

31. **NOTE — Live tiles under an open burst keep the screen non-idle (RV13).** The satellites' final bounds should
    also be written to diagnostics, as RECENT0922 did for the promoted tile, so a row has a second source when
    `uiautomator dump` fails three times.

32. **NOTE — Phase 04 P5's result bounds every acrylic overlay:** a non-privileged overlay sits below the nav bar
    (phase-04.md:94-100), so the blurred region cannot include the nav-bar strip whatever the design says; the
    panel's bottom edge follows interview item 8 and the blur measurement region must stop there.

33. **NOTE — Two harness floors already exist** (phase-03's lib.sh, symlinked by phase 01; phase-02's assert.sh).
    New phases should symlink lib.sh as phase 01 did (INDEX.md:69), not add a third; the differences between the
    two are already the kind of drift the phase-02 gate was returned for.

34. **NOTE — No instrumentation APK exists.** phase-02.md:61/68 offer `UiDevice` as an alternative; the drivers
    never used it and none of the additions needs it. Keep the new rows adb-driven so they run on the same
    harness and the same device lock (lib.sh:32-42).

35. **NOTE — App-list growth is a regression surface.** Nine new launcher entries change the A-Z groups and the
    jump grid that phase 01's E12 and the Recent / Recently added sections (INDEX.md:64, 74) count on; the
    phase-02 `regress.sh` pattern covers it in one run per inbox phase, and the row should assert the nine appear
    under their letters with no "New" caption (X14 reads the package's firstInstallTime, which is the shell's).

36. **NOTE — What cannot be proven on the emulator at all, collected** (each becomes a phone P-row or a
    NEEDS-HUMAN row in its phase): One UI 8 cross-window blur state and its power-saving behaviour; Samsung's own
    apps' App Shortcuts reaching a third-party HOME; the HOME-role and permission dialogs as Samsung draws them;
    every Settings deep-link's resolution on One UI; `USE_FULL_SCREEN_INTENT` grant state and an alarm firing
    over Samsung's keyguard; front camera, flash, HDR, zoom and image quality; HEVC / HDR10+ / 4K60 decode and the
    default video-app handoff; SD card and OTG in Files; Samsung widgets in the pod bay; Samsung Calendar's local
    calendar; real-microphone and Bluetooth recording; Jeremy's own voice on "open the pod bay doors"; the feel of
    every P4 design (burst, pods, wizard, acrylic) — no metric exists, only his sign-off.
