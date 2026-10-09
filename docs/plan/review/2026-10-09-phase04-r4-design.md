# Phase 04 round 4: Reviewer 1 (design and correctness)

Opus subagent, 2026-10-09, over `docs/plan/phase-04-action-center-volume-helper.md` at db5d37787 (brief:
review/2026-10-09-phase04-r4-brief.md). Saved by the lead from the reviewer's final message; the text below is the reviewer's,
unedited except for this header.

I reviewed `docs/plan/phase-04-action-center-volume-helper.md` at commit db5d37787. HEAD (76a51da9e) changes only the brief, INDEX and the state file.

**D1: BLOCKING.** Lines 309-312, 376-379 and 406 (A1 c).
- **Problem.** Task 12 lists the `provision.sh` ADDs but does not say where they go. provision.sh's own convention puts grant lines in the grants block (provision.sh:64, "Each later phase that adds a grant row appends its line here"). provision.sh then runs `am force-stop app.tileshell` at :173. Line 79 of this doc says a force stop removes the service from `enabled_accessibility_services`. The keyboard has the same problem, which is why provision.sh re-asserts it after the stop (:181-183).
- **Effect.** If the lines are appended in the grants block, every provisioned AVD ends with the service off:
  - A1 (c), "core held", cannot pass.
  - Built rows that assert `[wizard] not shown: core held` straight after provision fail when re-run on this build: phase-12 e1.sh:22 and e14.sh:50, phase-16 e26.sh:126, phase-17 e25.sh:133, phase-18 e15.sh:143.
- **Fix (task 12).** "The two accessibility lines go AFTER provision.sh's force-stop (:173), next to the `ime enable` / `ime set` re-assert (:181-183), with a comment saying why. `WRITE_SETTINGS` and `allow_dnd` stay in the grants block. The `accessibility` row reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` containing the component, not the bound-service list, which a `uiautomator dump` empties while it runs."

**D2: SHOULD-FIX.** Lines 185-188 and 535.
- **Problem.** Task 2 takes over the spike's daemon loop (HelperMain.kt:73-90). That loop finds the app's new process with `appPid()` (:80), which runs `sh("pidof …")` (:121) through `ProcessBuilder("sh","-c",…)` (:129).
- The static check forbids `ProcessBuilder("sh"` and `"-c"` in `helper/`.
- Line 188 drops `pidof` + `/proc` for the uid, but nothing says what still finds the new pid that R4 4a and A4 (a) depend on.
- **Fix (line 188).** "…the uid comes from the package manager. The loop still finds the app's main process every 2 s with `ProcessBuilder(listOf("/system/bin/pidof","app.tileshell"))` (argv, no shell) and delivers when the pid is new."

**D3: SHOULD-FIX.** Lines 185, 197-199, 561 and 696.
- **The token is not secret.** It is passed on the start command line (`--daemon <token>`), so any uid-2000 process can read it (`ps -A -o ARGS`, `/proc/<pid>/cmdline`). "The adb shell planting a binder (refused by token)" (line 696) is only true for a planter that doesn't look.
- **The start is a shell line, not an argv.** Kadb's `shell` sends a string that adbd runs under `sh -c`. Daemonising needs shell syntax: the spike used `CLASSPATH=… setsid nohup app_process … >/dev/null 2>&1 </dev/null &` (Runner.kt:346). `StartCommand.argv` doesn't say this.
- **The token is never stored.** The door has to accept the token in a NEW app process (A4 a), so the token must be persisted; no task says so. After a `pm clear` the running helper's token is lost and that helper is left orphaned.
- **Fix.**
  - Rename the rule to `StartCommand.line(apk, token, vc)`, which produces exactly `CLASSPATH='<apk>' setsid nohup app_process /system/bin --nice-name=tessera_helper app.tileshell.helper.HelperMain --daemon '<token>' '<vc>' >/dev/null 2>&1 </dev/null &`, and make its test pin that string.
  - Store the token and versionCode in app-private prefs.
  - `HandoffRule.accept(callingUid, callingPid, token, expected)`: the first accepted handoff after the app's own start records `Binder.getCallingPid()`, and every later handoff must come from that pid.
  - A refused handoff's reply tells the helper to exit.

**D4: SHOULD-FIX.** Lines 212-219, 562 and 567 (the restart path).
- **(a) Missing trigger.** Joining a Wi-Fi network is not in the trigger list, although Q-04-1's reading (line 65) is "whenever the phone joins an access point in the trusted list" and `RestartPlan.next` has "Wi-Fi joined". Add: "and on joining a Wi-Fi network (a `NetworkCallback` with location info, registered for the launcher process's life)".
- **(b) "The first time" is not enough.** Turning Developer options off writes the 7-day default back (saved `AdbAuthorizationTimeoutPreferenceController` :64-73), so the key later expires. Replace with: "at every helper start, grant when `WRITE_SECURE_SETTINGS` is not held, and write 0 when `adb_allowed_connection_time` reads non-zero".
- **(c) Wrong order on an update.** "Exit the old one, start a new one": after an update installed away from a trusted Wi-Fi, this kills a helper that USB debugging kept alive, and nothing can start the new one. Replace with: "start the new helper first; tell the old one to exit only after the new one's ping answers; until then the old one serves". Change the `HelperVersion.mustRestart` row to match.

**D5: SHOULD-FIX.** Lines 66, 81, 249 and 584.
- **Problem.** Android's "Allow wireless debugging on this network?" is an activity: `startConfirmationForNetwork` → `startConfirmationActivity` → `startActivityAsUser` (saved AdbDebuggingManager.java:1242-1255 and :1300-1311).
- The open action center is a full-display layer-31 overlay, so a Q-04-2 tap on an untrusted (or unknown) BSSID opens the dialog under it. The keeper's 30-s mDNS wait then runs out unseen. The same happens under Tess's card.
- **Fix (`HelperTileRule.onTap`, task 10).** "When the start will write `adb_wifi_enabled=1` on a BSSID that is not trusted or is unknown, the panel first closes with the tap wipe (Y8) and the tile reads 'Starting…'. Tess's outcome closes her pane the same way." Add this case to the rule's test.

**D6: SHOULD-FIX.** Lines 266-268.
- **Problem.** Only the `contentIntent` send carries the background-start opt-in. Action and reply sends carry none, yet many actions start activities. On API 34+ the sender's background-start right (granted by the service binding's `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`, saved AccessibilityServiceConnection.java:190-193) applies only with that opt-in.
- Also, an activity started under the open layer-31 panel is invisible, and only a notification tap closes the panel.
- **Fix.** "Every `PendingIntent.send` from the panel (content, action, reply) passes task 1 (f)'s `ActivityOptions`. When `PendingIntent.isActivity()` is true, the panel closes with the tap wipe (`[ac] closed by action`); otherwise it stays."

**D7: SHOULD-FIX.** Lines 227-232, 363 and 570.
- **Problem.** Only the panel cites R4's attempt B, and the doc names only its "fit no insets, full size" part. Attempt B also set `FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_NO_LIMITS` and `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (ProbeActivity.kt:475-481). The strip and the volume window name no cutout mode at all.
- In AOSP's `WindowLayout.computeFrames` (read from AOSP; not saved under r4/src), a DEFAULT-mode window without `FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_INSET_DECOR` has its display frame clipped to the cutout-safe area. On the S25 Ultra's punch hole the strip would sit below the status-bar band, and a top-edge pull would open Samsung's panel. The AVD has no cutout, so no row would catch it.
- Phase 15's `RingOverlay` already sets ALWAYS (`clock/RingOverlay.kt:60-67`).
- **Fix.**
  - For all three windows: `FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_NO_LIMITS`, `layoutInDisplayCutoutMode=ALWAYS`, `setFitInsetsTypes(0)`, top-start at y=0.
  - Add those as cases to `PanelWindow.flags` and to a strip-params case.
  - Add task 1 (l): with `cmd overlay enable com.android.internal.display.cutout.emulation.hole`, `dumpsys window windows` shows `TesseraStrip` starting at y=0; then disable it.

**D8: SHOULD-FIX.** Lines 235, 271-273 and 571.
- **Problem.** `KeyFilter.consume` takes every VOLUME_UP/DOWN. The filter runs before dispatch, and a consumed key never reaches the app (addendum A5.1, InputDispatcher.cpp:4492-4498). While the service is on, the camera's volume-key shutter (Samsung Camera's default) and any app that maps the volume keys stop working.
- **Fix.** Make it `KeyFilter.consume(keyCode, action, foregroundPkg)` and let the keys through while the last window-state event's package handles `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`. Add to P7: "in the camera app the volume key still takes a photo".

**D9: SHOULD-FIX.** Lines 354 (Y8), 712-715 and 572.
- **Problem.** With gesture navigation, a home or recents swipe over the open panel gives the panel a cancel, and "a cancel settles by the same rule". A fully open panel therefore stays open over Home or recents.
- **Fix.** "While fully open, a cancel the panel did not cause, or a window-state event from another package, closes it by Y8's Back sequence (`[ac] closed by system`)." Add both cases to `CloseRules`.

**D10: SHOULD-FIX.** Lines 40-46, 80 and 304-309 (author's points 1 and 9).
- **Problem.** Three other docs disagree with this one, and one still points at a dropped row:
  - Phase 19 plans the same two grants as steps `setup:write_settings` / `setup:dnd_access`, with its own why lines and an E16 over them (phase-19:69, :209-214). This doc calls the first one `modify_system_settings`.
  - Phase 12 still expects "Wireless debugging pairing, helper" to be wizard steps with an E14 each (phase-12:44-46, :133, :316-320).
  - Phase 07 reuses "phase 04's overlay row" (phase-07:16, :22, :45), which this doc does not add.
  - Phase 19 points at the dropped E4(d) (phase-19:64, :403).
- **Fix.** Use the id `write_settings`. Add to Cross-phase: "Doc edits recorded with this phase:
  - phase 12 lines 44-46, 133 and 316-320: the helper's rows are observation rows (no step, no E14);
  - phase 19 lines 69 and 209-214: both rows are built here; 19 rewrites their why lines for its pages and re-runs E14;
  - phase 19 lines 64 and 403: E4(d) becomes A8 (c);
  - phase 07 lines 16, 22 and 45: the overlay row is phase 15's `overlay` (Checklist.kt:174-176)."

**D11: SHOULD-FIX.** Lines 611-615 (author's point 3).
- **Problem.** PLAN.md:328-329 (RV11), "Timings whose tolerance is ≤ 17 ms also get a phone P-row", is still in force.
- The 08:29 ruling (INDEX:137) sets one round with each row run once. It says nothing about motion capture or RV11, and records that he "is still thinking about the rest of QA's form". Phone rows cost the AVD round nothing. The drop rests on the agent's reading, not on his ruling.
- **Fix.** A question for the lead to put to Jeremy: either keep P9 (the owner records a pull, close, expand and volume show with One UI's screen recorder and sends the file; the agent measures the 290-ms settle, 216-ms wipe, 217-ms show and 134-ms slide-away), or drop RV11 for this phase on his word. Until he answers, the "Dropped" line says "pending Jeremy".

**D12: NOTE.** Lines 65, 77 and 81 (author's points 4, 6 and 7).
- **(a) Line 65.** "`WRITE_SECURE_SETTINGS` granted through the helper" contradicts task 2, where `pm grant` runs over the app's own adb session and `HelperVerbs` has no `pm`. Write "over the app's own adb session".
  - Also change "never on an untrusted one" to "never *by itself* on an untrusted one". With that, Q-04-1 and Q-04-2 agree.
- **(b) Line 81.** Precise location is not a new permission. The manifest already declares FINE and BACKGROUND (:84-85), and Tess's `background_location` step asks for both (CortanaChecklist.kt:68-77). The Setup `location` row asks for COARSE only (Checklist.kt:138-141). Say so, and give `HelperState` a "Location off" reason so an unknown BSSID doesn't read as "untrusted network".
- **(c) Line 77.** Over a secure keyguard, show the media section's controls without the track title. Q-04-3's reason ("no notification text") covers a title.

## Author's points 1-9
1. Sound on the code (`WizardRules.visibility`, SetupWizard.kt:80-81); the doc edits are in D10.
2. See D1. Otherwise no built driver breaks:
   - phase 01's `input swipe 540 0 540 400 250` (edge_interaction.sh:33, e20_part1.sh:53) travels 133 epx, under the 138-epx point where the panel first appears, and the window manager's spy (DO_NOT_PILFER) still reveals the bars;
   - no built driver sends volume keys through the emulator's virtual device.

   On the phone only a manual Force stop turns the service off, and the shell never turns it back on (line 39).
3. See D11.
4. Consistent with Q-04-3, whose scope is the action center; see D12 (c).
5. Sound (C2). `QUERY_ALL_PACKAGES` is held (manifest:6), so a cast-settings intent that doesn't resolve is not a package-visibility false negative.
6. See D12 (a).
7. See D12 (b).
8. The blur stop is right. For the reply box: SystemUI's shade (layer 17) gets the keyboard above its inline reply, so the doc's "expectation below" may be wrong. Task 1 (h) measures it, and task 7's shrink works either way.
9. See D10.

The Q-04-2 fallback is outside Rule 16: it is a permanent runtime branch that nothing later replaces. "All settings" → `SettingsActivity` also holds (phase-19:120-129).

## Round 1-3 ids checked, correctly applied
R3T-B1 (confirmed by A5.6), R3T-B2, R3T-B3, R3-m4 (LockGate.kt:29-47), R2-m12 (SystemBars.kt:200-202), T-M21 / R2-m4 (consistent with phase-09:38), R3F-01, T-B5 / F1-M12.

The as-built cites match today's code: LockGate, CommandMatcher (:75-116, :87, :89), ActionLayer (:117-146, :706-718), SystemBars, TileNotificationListener, Fluent.kt (all lines), SettingsActivity, Checklist, SetupWizard, SlotResolver:66, manifest :111 and :365-376, settings.gradle.kts:13-15, and the HelperMain / HelperProvider / Toggles lines. The one exception is minor: `W10mStatusBar` is at :87, not :88.

BLOCKING: 1 · SHOULD-FIX: 10 · NOTE: 1
