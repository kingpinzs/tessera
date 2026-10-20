# Phase 04 — build contract (lead, 2026-10-20)

The seam every builder of phase 04 codes against. The phase doc (`docs/plan/phase-04-action-center-volume-helper.md`, FINAL
2026-10-09) is the spec; this file only fixes **who owns which file** and **the cross-module signatures**, so seven builders
can work at once in their own worktrees and merge cleanly. Where this file and the phase doc disagree, the phase doc wins
and the builder reports the disagreement (never silently picks).

## Rules for every builder

1. **Your worktree only.** You work in `~/projects/metro-launcher-p04/.claude/worktrees/<name>` on branch `phase-04-<name>`.
   Never touch `~/projects/metro-launcher-p04` itself or any other worktree, and never `~/projects/metro-launcher`,
   `-p17`, `-p18`, `-p20`.
2. **Never stage or commit.** No `git add`, `git mv`, `git rm`, `git commit`, `git stash`, `git checkout -- …`. The lead
   commits your work. Use plain file edits.
3. **Own files only.** Create and edit only the files the table below gives you. A file another builder owns that you
   need changed: write the exact change you need into your report; do not make it. Shared files (manifest, `ShellApp.kt`,
   `app/build.gradle.kts`, the exported allow-list) — edit only the block the table names for you, adding lines, never
   reformatting or moving others.
4. **Contract stubs are signatures.** `helper/Radio.kt`, `HelperState.kt`, `HelperTileRule.kt`, `RadioStates.kt`,
   `HelperKeeper.kt` (signatures), `ac/AcPanel.kt`, `ac/AcService.kt` (signatures), `ac/AcGeometry.kt`, and the stub
   composables are committed on `phase-04` (commit "Phase 04: build contract and shared seams"). The owner named below
   fills a stub's body; nobody changes a contract signature. Adding members is fine.
5. **Pure rules are pure.** Every rule the phase doc's "Unit tests (JVM)" table names is a pure Kotlin function or object
   with no Android framework calls in its logic (framework constants are fine), with a JUnit 4 test under
   `app/src/test/kotlin/app/tileshell/<pkg>/` carrying **every case the table lists for it**, named as the table names it.
   If a case cannot be tested as written, say so in your report — never weaken the case.
6. **Build and test.** `./gradlew :app:testDebugUnitTest --console=plain` and `./gradlew :app:assembleDebug --console=plain`
   from your worktree root, output to a file, exit code read from the file (`echo $? > …rc`), never through a pipe. Both
   must exit 0 before you report. Report the test count for your new test classes and the suite total.
7. **No device.** The emulator is not yours (the lead runs every device step). Do not run `adb`.
8. **Logs.** `app.tileshell.diag.Diagnostics.add("<tag>", "<message>")` writes the `[<tag>] <message>` lines the rows
   assert; use the exact line texts the phase doc gives. `[notif]` lines carry package and counts only, never text.
9. **Style.** Match the surrounding code: kdoc on each class saying which build task / decision / Y-row it implements,
   comments only where they say why. Compose screens use `.dp` as epx (`ShellRoot` / `ShellDensity` map RV10).
   Test tags are `Modifier.testTag("<tag>")` with the doc's exact names.
10. **Report.** End with: files created / changed; the exact build + test commands and their rc; test counts; **every
    choice the doc left open that you made** (an agent pick: what and why) — these go into the phase doc's Approximations
    or Decisions by the lead; and every change you need in a file you do not own.

## Ownership

| builder | build tasks | files (create unless marked ADD) |
|---|---|---|
| **helper** | 2, and 1 (j) licences | `helper/HelperMain.kt`, `HelperVerbs.kt`, `HelperDoor.kt` (provider + `HelperLink`), `HandoffRule.kt`, `HelperDoorRule.kt`, `CallerRule.kt`, `StartCommand.kt`, `HelperVersion.kt`, `PairingCode.kt`, `AdbSession.kt`, `Discovery.kt`, `AdbSessionCommands.kt`, `PairingReceiver.kt` (+ the pairing notification); tests for each rule; ADD manifest block "phase 04 helper" (`WRITE_SECURE_SETTINGS`, the door provider, the pairing receiver); ADD `app/build.gradle.kts` (Kadb 2.1.1 + coroutines, as `r4probe/build.gradle.kts`); ADD the door line to `docs/plan/qa/phase-03/exported-allowlist.txt` under a `# ---- phase 04 ----` heading; the licence files for Kadb's dependency tree on the About page (task 1 j) |
| **keeper** | 3 | fill `helper/HelperKeeper.kt`; `helper/TrustedNetworks.kt`, `RestartPlan.kt`, `HelperReason.kt` (`HelperState.reason`), `HelperBootReceiver.kt` (BOOT_COMPLETED + MY_PACKAGE_REPLACED, non-exported); tests; ADD manifest block "phase 04 keeper" (the receiver, `RECEIVE_BOOT_COMPLETED` if not held); ADD in `ShellApp.startLauncher` one line `HelperKeeper.init(this)`; ADD in `StartActivity` the home-start trigger (one call) |
| **ac** | 4, 5, 11 | fill `ac/AcService.kt`; `ac/ShellAccessibilityService.kt`, `ac/OverlayHost.kt`, `ac/StripRule.kt`, `ac/PanelWindow.kt`, `ac/KeyFilter.kt`, `ac/ActionCenter.kt`, `ac/AcMotion.kt`, `ac/PullRules.kt` (+ `SettleCurve`, `ScrimRule`, `ListOffset`, `CloseRules`, `ExpandMotion` in `ac/` files of your choice); `res/xml/shell_accessibility_service.xml`; tests incl. `AcGeometryTest`; ADD manifest block "phase 04 accessibility" (the service); ADD the service line to the exported allow-list under `# ---- phase 04 ----`; task 11's ADD to `bars/SystemBars.kt` (`onBackHold` on `W10mNavBar`, the Back `NavSlot`'s long-click) |
| **quick** | 6 | fill `ac/QuickActionGrid.kt`, `ac/QuickActionsPage.kt`; `ac/QuickActions.kt` (+ `QuickDefaults`, `QuickGrid`, `QuickAvailability`, `QuickPrefs`, `QuickMechanism`, `QuietHoursRule`, `BrightnessCycle`, `RotationRule`); tests (+ `HelperTileRuleTest` exists — extend only if a case is missing); ADD manifest block "phase 04 quick actions" (`WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`, `ACCESS_WIFI_STATE`) |
| **notif** | 7, and 13's `testapps/qa-notify` | fill `ac/NotificationList.kt`; `feeds/NotificationShade.kt` (`NotificationShade.state`, `ShadeRules`), `ac/SwipeRules.kt`, `ac/NotifText.kt`, `ac/NotifLine.kt`; the ADD to `feeds/TileNotificationListener.kt` (publish the shade's list, `cancel(key)`, `cancelAll()`; the tile path unchanged); tests; `testapps/qa-notify/**` and its `include` line in `settings.gradle.kts` |
| **volume** | 8, 9 | fill `volume/VolumeKeys.kt`; `volume/VolumePanel.kt`, `volume/VolumeRules.kt`, `VolumeMotion`, `VolumeTimeout`, `VolumeGeometry` (files of your choice under `volume/`); the ADD to `ui/fluent/Fluent.kt` (`FluentSource.WINDOW`, `FluentSurface.VOLUME_PANEL` / `ACTION_CENTER`, `FluentReason.UNSUPPORTED`, `FluentWindowRule`, the cross-window listener); tests |
| **tess** | 10, 12 | the ADDs to `cortana/match/Request.kt`, `CommandMatcher.kt`, `cortana/action/LockGate.kt`, `ActionLayer.kt`; `cortana/action/RadioReply.kt`; fill `helper/HelperPage.kt`; `helper/HelperChecklist.kt`; the ADDs to `settings/SettingsActivity.kt` (`SettingsPage.ACTION_CENTER` → `ActionCenterSettingsPage()`, `HELPER` → `HelperPage()`, two `HomePage` rows), `onboarding/Checklist.kt` (three grant rows + three observation rows), the setup wizard's steps and why lines (phase 12's files), phase 12's why-lines test; `docs/plan/qa/phase-03/scripts/provision.sh` (the four lines + the print, placed as build task 12 says); tests |

The lead owns: this file, `INDEX.md`, `.claude-build-state.md`, the phase doc's Change Log entries, every merge, and build
task 13's `p04.sh` (written after the merge, from the real tags and lines).

## The seams (signatures; the stubs are on `phase-04`)

**Radios and the helper's state** (`app.tileshell.helper`): `enum Radio(quickId, verbStem, label, spoken, controlAction)`
= `WIFI("wifi","wifi")`, `BLUETOOTH("bluetooth","bluetooth")`, `MOBILE_DATA("cellular","mobile_data")`,
`AIRPLANE("airplane_mode","airplane")`; `Radio.verb(on)` = `"<stem>_on|off"`. `sealed HelperState` = `NotPaired`,
`PairingGone`, `NoWifi`, `LocationOff`, `DebuggingOff`, `UntrustedNetwork`, `Starting`, `Running(pid, versionCode, uptimeMs)`,
`Dead(reason)`. `enum BssidTrust { TRUSTED, UNTRUSTED, UNKNOWN }`. `enum Requester { TILE, TESS }`. `sealed SwitchOutcome` =
`Done(radio,on)`, `Already(radio,on)`, `Failed(radio,on,state)`. `HelperDetails(pid, versionCode, uptimeMs, trustedNetworks, paired)`.
`RadioStates.read(context, radio): Boolean?`, `RadioStates.wifiConnected(context)`.

**`HelperTileRule.onTap(radio, state, wifiConnected, trust): Tap`** (written and tested by the lead; quick and tess both use
it): `Verb` (running), `StartThenVerb` (startable, trusted BSSID: the panel stays open), `CloseThenStart` (startable, BSSID
untrusted or unknown: close with the wipe first, the tile "Starting…"; Tess: her pane closes, Y16's "Starting…" reply),
`OpenHelperPage` (never paired, whatever the Wi-Fi), `OpenControl(state)` (no Wi-Fi / Wi-Fi off, pairing gone, debugging
off: `radio.controlAction` + the Y16 line of `state`).

**`HelperKeeper`** (keeper fills; quick, tess call): `state: StateFlow<HelperState>`, `details: StateFlow<HelperDetails>`,
`init(context)`, `trigger(context, Trigger)` (`BOOT`, `PACKAGE_REPLACED`, `BINDER_DIED`, `HOME_START`, `WIFI_JOINED`),
`bssidTrust()`, `suspend switch(context, radio, on, by): SwitchOutcome`, `startPairing(context)`, `startNow(context)`.
`HelperState.reason(state): String?` is the Y16 line (keeper, `HelperReason.kt`): "Not set up", "Pair again", "No Wi-Fi",
"Location off", "Debugging off", "Starting…"; null while running.

**The helper's own API (helper builds, keeper consumes)** — keeper codes against exactly these names; helper implements them:
- `object AdbSession`: `fun paired(context): Boolean` (a key exists and was accepted once); `suspend fun pair(context, code:
  String): Result<Unit>` (mDNS `_adb-tls-pairing._tcp` on the phone's own addresses only); `suspend fun connect(context,
  timeoutMs: Long): ConnectResult` (`Connected`, `NoService` (no `_adb-tls-connect._tcp` within the time), `AuthRefused`
  (the key refused → pairing gone), `Failed(message)`); `suspend fun run(context, command: String): Result<String>` —
  refuses anything `AdbSessionCommands.allowed` refuses; `fun close()`.
- `object AdbSessionCommands`: `GRANT_WRITE_SECURE_SETTINGS`, `DUMPSYS_ADB`, `fun start(apk, token, vc): String?`
  (= `StartCommand.line`), `fun allowed(command: String, apk: String, token: String, vc: Long): Boolean`.
- `object HelperDoor` (with the exported provider class `HelperDoorProvider`, authority `app.tileshell.helper`): `fun
  newStart(context): StartTicket(token: String, versionCode: Long, apk: String)` (writes a new token, clears the pin,
  stores the versionCode); the provider's `call("helper_binder", …)` runs `HandoffRule.accept`.
- `object HelperLink`: `val alive: StateFlow<Boolean>`; `fun ping(): HelperPing?` (`uid`, `pid`, `versionCode`, `uptimeMs`,
  `token`); `fun verb(name: String): Int?` (the exit code, null when the binder is gone); `fun exit()`; `fun onDeath(listener:
  () -> Unit)`; `suspend fun awaitHandoff(timeoutMs: Long): Boolean`.
- Pairing: `PairingReceiver` (non-exported) takes the code typed into the shell's notification; `fun postPairingNotification
  (context)` in `PairingReceiver.kt`; on the code: `PairingCode.parse` then `AdbSession.pair`, then `[helper] paired`.

**The accessibility service** (ac fills `AcService.kt`): `AcService.connected`, `AcService.instance`, `AcService.bind`,
`AcService.globalAction(action)`; `AcEvents.windowState` / `lastPackage` / `emit(WindowStateEvent)`;
`OverlayWindows.params(title, focusable, heightPx)`, `add(name, view, params)`, `update`, `remove`, `context()`. The service's
`onKeyEvent`: `if (KeyFilter.consume(...)) { VolumeKeys.onKey(this, event); true } else false`. Its `dump()` prints, for every
window added through `OverlayWindows`, each tagged node's tag, bounds, text and state (DEBUG builds only), then the ring.

**The panel** (ac implements `AcPanelControl`; quick and notif call it): `pxPerEpx`, `expanded`, `closeWithWipe(reason,
then)`, `launch(intent, reason)`, `send(pendingIntent, fillIn, reason): Boolean` (the background-start opt-in, task 1 f;
`isActivity()` → close with the wipe first, else stay open), `replyFocus(focused)`. `CloseReason` words: flick, drag, back,
tap, notification, action, system. `AcGeometry(pxPerEpx)` holds A19's values (lead wrote it; ac writes `AcGeometryTest`).

**Composables placed by the panel**: `QuickActionGrid(control, modifier)` (quick: lays out its own cells from `AcGeometry`,
relative to its top-left = the first row's top-left; `quickActionRows()` tells the panel how tall the expanded grid is);
`NotificationList(control, modifier)` (notif); `clearAllNotifications()` (notif). Settings pages: `ActionCenterSettingsPage()`
(quick), `HelperPage()` (tess). The volume panel adds its own window through `OverlayWindows` (volume).

**Quick-action ids** (the `quick_action:<id>` tags, `[ac] quick action <id> -> …`): `camera`, `wifi`, `bluetooth`,
`rotation_lock`, `all_settings`, `airplane_mode`, `cellular`, `mobile_hotspot`, `quiet_hours`, `brightness`, `connect`,
`battery_saver`, `flashlight`, `note`, `location`, `vpn`.

**Shared-file blocks**: each builder's manifest additions go in one contiguous block opened by a comment
`<!-- Phase 04 (<builder>): … -->`, placed after the last existing component of its kind (permissions with permissions,
components before `</application>`). `ShellApp.startLauncher`: keeper's one line after `Fluent.start(this)`.

## Notes

- `HelperTileRule` / `HelperTileRuleTest` (7 / 0) and the contract stubs compiled 2026-10-20 15:23
  (`BUILD-NOTES/contract-stubs-unit.out`, rc 0). Baseline before phase 04: assembleDebug OK, unit 2377 / 0
  (`BUILD-NOTES/baseline-build-unit.out`).
