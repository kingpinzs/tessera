# R4 addendum for phase 04 — windows and input, the helper's life, W10M's quick actions (2026-10-08)

Phase 04's planning session (2026-10-08) dispatched three Opus research agents, read-only on the repo, to close the gaps
the R4 phone run (docs/plan/qa/r4/results-2026-09-29.md) left before the phase 04 interview. The lead writes their reports
here. Every claim carries its source inline; the fetched copies are under `docs/plan/r4/src/` (git-ignored, local only).
Anything marked UNVERIFIED or MEASURED-only is not established for One UI; Samsung's framework is closed source.

## A — How the action center meets the system bars, and how volume keys flow (agent A)

**Ref read:** AOSP tag `android-16.0.0_r4` = the head of `android16-qpr2-release` on 2026-10-08 (frameworks/base
45034f06, frameworks/native fcbde2bc, Launcher3 bbd0e27b). The phone's build `BP4A.251205` is QPR2 (inferred from the
BP4A prefix). Each file was fetched as `https://android.googlesource.com/<repo>/+/refs/tags/android-16.0.0_r4/<path>?format=TEXT`
and saved under `src/a-windows/aosp16r4_*` (line 1 of each copy holds its URL; **file:line below is the original source
line; in the saved copy add 2**).

| Q | Answer | Confidence | Proof |
|---|---|---|---|
| A1 | The accessibility overlay (layer 31) is above the nav bar (24), the nav bar panel (25), the status bar (15), the notification shade (17), the volume overlay (22) and the keyguard (17 / 19); it shows over the lock screen | HIGH (AOSP); on the phone MEASURED for touch, not for drawing | WindowManagerPolicy.java:518-635; WindowState.java:3907-3923; R4 P5 |
| A2 | It is a trusted overlay: never blocked or flagged as obscuring, always receives its own touches | HIGH | InputMonitor.java:735; InputDispatcher.cpp:3090 |
| A3 | With gesture navigation the home / recents swipe-up and the side back swipe still work over it: the system's gesture spies sit above every window and pilfer the touch; the bottom edge cannot be excluded; no exemption for an accessibility window | HIGH (AOSP); Samsung UNVERIFIED | DisplayContent.java:1390; OtherActivityInputConsumer.java:432 |
| A4 | A top-edge strip gets the touch-down before the status bar, **but** AOSP's window manager transfers a FAST swipe that starts in the top zone to the status bar, so the system shade opens instead; slow drags stay with the strip | HIGH (AOSP); Samsung not measured | DisplayPolicy.java:2422-2439 |
| A5 | Screen on and not ringing: the service gets volume keys after the key policy runs but before the volume changes or the panel shows; returning true stops both; a volume set without `FLAG_SHOW_UI` shows no AOSP panel; `adb shell input keyevent` skips the service | HIGH (AOSP); Samsung UNVERIFIED | InputDispatcher.cpp:4478-4498 |

### A1 — Layer order
- `WindowManagerPolicy.getWindowLayerFromTypeLw` (:518-635): SYSTEM_DIALOG 6 (:542-543); APPLICATION_OVERLAY 11
  (:565-566); INPUT_METHOD 13 (:567-569); STATUS_BAR 15, STATUS_BAR_ADDITIONAL 16, NOTIFICATION_SHADE 17,
  STATUS_BAR_SUB_PANEL 18, KEYGUARD_DIALOG 19 (:573-582); VOLUME_OVERLAY 22 (:588-591); NAVIGATION_BAR 24, "shows atop
  most things" (:596-598); NAVIGATION_BAR_PANEL 25 (:599-601); ACCESSIBILITY_OVERLAY 31, "overlay put by accessibility
  services to intercept user interaction" (:619-621). Only these are higher: magnification overlay 32, secure system
  overlay 33, boot progress 34, pointer 35 (:622-630), rounded corners 36 (:521-522, :646-647).
- Nothing keeps a nav bar above it in AOSP: Launcher3's taskbar / nav bar is `isPrimaryDisplay() ? TYPE_NAVIGATION_BAR :
  TYPE_NAVIGATION_BAR_PANEL` (TaskbarActivityContext.java:821); SystemUI's back-gesture arrow is a
  TYPE_NAVIGATION_BAR_PANEL window (EdgeBackGestureHandler.java:911) and would draw under an open panel. Samsung's own
  window types: UNVERIFIED.
- Lock screen: `canBeHiddenByKeyguard` (WindowState.java:3907-3923) hides "only windows below the keyguard host window"
  (`getWindowLayerLw(this) < …(TYPE_NOTIFICATION_SHADE)`); 31 > 17, so the overlay stays visible over the keyguard.
- The hide-overlays rule (Settings, permission dialogs) skips every window that is not a system-alert type
  (WindowState.java:3110-3114); that list has no accessibility overlay (WindowManager.java:2662-2672). One app-op gate
  exists, `OP_CREATE_ACCESSIBILITY_OVERLAY`, behind a feature flag (PhoneWindowManager.java:3121-3124).
- **On the phone (R4, results-2026-09-29.md):** the accessibility overlay "reaches the bottom edge … a tap in the nav bar's
  strip reached it", both navigation modes. Hit-testing walks windows "from front to back" (InputDispatcher.cpp:1458), so
  the overlay is above One UI's nav bar in z-order. **Whether the nav glyphs were drawn over it was not recorded**; drawing
  uses the same z-order, so the inference is that it hides them — INFERRED, a screenshot confirms it.
- The R4 taps were injected, not fingers: `sh("input tap ${w / 2} ${h - 12}")` (r4probe Runner.kt:567) over adb as the
  shell uid (AdbSelf.kt:59-62); `input tap` → `InputManagerGlobal.injectInputEvent` (InputShellCommand.java:140-141).
  Injected touches are hit-tested like real ones; a tap cannot test swipe gestures. The earlier "six taps" in the
  2026-09-22 P5 probe were real fingers, on the APP overlay only.

### A2 — Touch occlusion
- `isTrustedOverlay` includes `TYPE_ACCESSIBILITY_OVERLAY` (InputMonitor.java:726-738); WindowState.java:1155,
  :1168-1170 mark it. `canBeObscuredBy` (InputDispatcher.cpp:3086-3091) is false for the same uid or `TRUSTED_OVERLAY`;
  occlusion counts only windows above the touched one (:3127-3128); untrusted touches are dropped at :5431-5445.
- A panel that receives touches: only system windows are above layer 31, so its touches are never blocked. Touches that
  pass through it: never blocked, and apps below do not get the obscured flag (:3196, :3214). App overlays are different:
  opacity-based blocking (WindowState.java:1183-1185) and an alpha clamp (DisplayPolicy.java:1053-1069).

### A3 — Gesture navigation over a covered bottom edge
- The gesture listeners are spy windows above every window: `monitorGestureInput` (InputManagerService.java:887-916)
  parents them to `dc.getInputOverlayLayer()` (InputManagerCallback.java:332-342), set to `Integer.MAX_VALUE - 1`
  (DisplayContent.java:1390); flagged `NOT_FOCUSABLE | SPY`, trusted (GestureMonitorSpyWindow.java:65-68).
- Home / recents: Launcher3 `InputMonitorCompat("swipe-up", …)` (TouchInteractionService.java:864) chooses a handler from
  `isInSwipeUpTouchRegion` and SystemUI state only (:1143-1168; RecentsAnimationDeviceState.java:412-416) and, once the
  swipe is recognised, `pilferPointers()` (OtherActivityInputConsumer.java:431-432): the panel receives a cancel.
- Back: SystemUI `InputMonitorCompat("edge-swipe", …)` (EdgeBackGestureHandler.java:849), allowed when
  `!mUsingThreeButtonNav && isWithinInsets && isWithinTouchRegion` (:1217-1218), blocked inside `mExcludeRegion`
  (:1106-1114), steals with `pilferPointers` (:1319).
- Exclusion: left and right edges only (DisplayContent.java:5921-5922); any touchable window may add rects (:5931-5988)
  up to max(200 dp, device config) per side (WindowManagerConstants.java:81, :189; View.java:13046-13050); the cap is
  lifted for the IME, the shade, `ACTIVITY_TYPE_HOME` windows, or a signature|privileged flag (:6011-6013; Session.java:166-168).
  So the panel can exclude at most 200 dp per side from the back gesture; the bottom edge cannot be excluded ("Apps can't
  opt out of these gestures as they can with the back gesture", developer docs, `src/a-windows/devdocs_gesturenav_2026-10-08.html`).
- One UI Home's gesture handler: UNVERIFIED (closed; R4 only tapped). Needs a real-finger swipe on the phone.

### A4 — The top edge
- Layer 31 > 15: a strip over the status bar gets the touch-down and the status bar gets nothing — **except** AOSP's own
  top-swipe handling: the window manager's spy (`SPY | DO_NOT_PILFER`, DisplayContent.java:1397-1411) fires "swipe from
  top" when a swipe starts in the top zone, travels past a distance threshold within `SWIPE_TIMEOUT_MS` = 500 ms
  (SystemGesturesPointerEventListener.java:53, :387-390); zone and distance 24 dp (core dimens.xml:74, :77), the zone grown
  to the cutout's height + 12 dp (:130-132). Then, if the app cannot show transient bars, it will "reroute the touch events
  here from the currently touched window to the status bar": `mStatusBar.transferTouch()` (DisplayPolicy.java:2422-2439;
  InputDispatcher.cpp:6085). No transfer on the lock screen (:2396-2398), with `CLIENT_TRANSIENT` on (off by default,
  ViewRootImpl.java:382-383), or in apps using `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` (WindowState.java:3903-3904).
- So in AOSP: a quick flick from the top edge opens Android's shade and cancels the strip; a slow drag, or one that starts
  below the zone, stays with the strip; in swipe-to-show-bars immersive apps the bars appear briefly but the strip keeps
  the gesture. **Phase 04's old E4(a) (`input motionevent DOWN 540 5` then MOVEs) is affected:** a move of more than 24 dp
  inside 500 ms is taken by the status bar.
- Ways to reach the system shade: `GLOBAL_ACTION_NOTIFICATIONS` = 4, `GLOBAL_ACTION_QUICK_SETTINGS` = 5
  (AccessibilityService.java:538-546), or leaving part of the status bar uncovered. `GLOBAL_ACTION_RECENTS` = 3, "toggle
  showing the overview of recent apps" (:532-536) serves interview item 7.
- Samsung: none of this is measured. Fast and slow pulls on the strip in a normal app, an immersive app and the lock screen
  are a phone check.

### A5 — Volume keys
1. Order: `interceptKeyBeforeQueueing` (InputDispatcher.cpp:4478), then `filterInputEvent`; "event was consumed by the
   filter" returns early (:4492-4498). (The `onKeyEvent` doc says "before they are passed to the device policy",
   AccessibilityService.java:1005-1007; the source says otherwise.)
2. PhoneWindowManager: screen on → `ACTION_PASS_TO_USER`; screen off → `result = 0` (:4445-4471). On key-down
   `sendSystemKeyToStatusBarAsync` and `nm.silenceNotificationSound()` always run (:4566-4572), so a consumed press still
   silences a playing notification sound. Ringing: `silenceRinger()`, pass-to-user cleared (:4578-4595). Screen off, or in
   a call with the key not passed on: `MediaSessionLegacyHelper…sendVolumeKeyEvent` (:4604-4623).
3. The accessibility filter skips any key without pass-to-user (AccessibilityInputFilter.java:416-422): **the service never
   sees volume keys while ringing or with the screen off.** Volume keys are first offered to multi-key chords
   (KeyboardInterceptor.java:149-153 → KeyGestureController.java:576-577). Keys the service does not handle, or does not
   answer within 500 ms, are re-injected (KeyEventDispatcher.java:36-40, :51, :262-273).
4. The volume change itself: the window manager's branch runs only on TVs or with `config_handleVolumeKeysInWindowManager`
   (PhoneWindowManager.java:3414-3421; AOSP default false, config.xml:5370). Otherwise `PhoneFallbackEventHandler`
   (:89-92, :316-317) → MediaSessionService adds `FLAG_SHOW_UI` on key-down (:2219-2227) → `adjustSuggestedStreamVolumeForUid`
   (:2627) → AudioService `postVolumeChanged` (:5906-5917) → SystemUI shows its panel only if `FLAG_SHOW_UI` is set
   (VolumeDialogControllerImpl.java:545-550). **Returning true stops both the change and the stock panel.**
5. The shell setting volume: no `FLAG_SHOW_UI` → no AOSP panel. Volume hardening is not enforced on phones by default
   (AudioService.java:1298; HardeningEnforcer.java:183). DND: a change that moves the ringer into or out of silent throws
   "Not allowed to change Do Not Disturb state" without notification-policy access (AudioService.java:5677-5681 set,
   :8077-8081 adjust; physical keys are exempt via the hidden `FLAG_FROM_KEY`); streams DND has muted are ignored unless
   `FLAG_ALLOW_RINGER_MODES` (:5775-5785). STREAM_VOICE_CALL: index 0 or mute needs MODIFY_PHONE_STATE (:5180-5185,
   :4166-4173); other levels are allowed.
6. **`adb shell input keyevent` bypasses the service — the phase 04 doc's 2026-09-17 claim is confirmed:**
   `injectInputEvent` (InputShellCommand.java:140-141 → InputManagerService.java:1036-1038); the injection path calls only
   `interceptKeyBeforeQueueing` (InputDispatcher.cpp:4836-4838), no `filterInputEvent`; the filter's re-injected keys carry
   `FLAG_FILTERED` (InputManagerService.java:3537-3540).
7. Samsung: UNVERIFIED. Volume Styles' Play listing (updated 2026-08-24) "uses Accessibility Services to override volume
   buttons and display your custom volume panel" and offers a One UI style, with no Samsung caveat
   (`src/a-windows/play_volume-styles_…html`); Precise Volume sells a separate "Screen Off Override" (search snippet), which
   matches AOSP skipping the accessibility path with the screen off. Whether One UI shows its own panel anyway is a
   physical-key test on the phone.

## B — The helper's life: why 4d killed it, restart after reboot, key expiry, the platform risk (agent B)

**Refs:** AOSP `android16-qpr2-release` (frameworks/base 45034f06; packages/modules/adb 78f769e0 = android-16.0.0_r4;
system/core and system/sepolicy on the same branch); **A17** = android17-release / android17-security-release; Shizuku tag
**v13.6.0** (2650830c, 2025-05-25, the latest release). Saved under `src/b-helper/` (118 files). AOSP-only unless the phone
measured it. Lead's spot-check: `AdbService.java` `stopAdbd()` = `if (!mIsAdbUsbEnabled && !mIsAdbWifiEnabled)
SystemProperties.set(CTL_STOP, ADBD)` (saved copy :393) and `init-service.cpp` `KillProcessGroup(SIGKILL)` on reap
(saved copy :263) — both as quoted.

| Q | Answer | Confidence | Proof |
|---|---|---|---|
| B1 | Init SIGKILLs adbd's whole cgroup when adbd stops or exits; `setsid`, `nohup` and re-parenting to init do not change cgroup membership, and uid 2000 cannot leave it. A hard platform rule; Shizuku says the same | HIGH | init-service.cpp 261-272, 998; processgroup.cpp 416-430; cgroup-v2.rst 567-570; Shizuku starter.cpp 200, strings.xml 30 |
| B2a | Wireless debugging survives a reboot through `persist.adb.tls_server.enable`, but at BOOT_COMPLETED it is written back to 0 unless Wi-Fi is up AND on a trusted BSSID. USB debugging persists. With no Wireless debugging there is no TCP listener after a reboot | HIGH (code); timing on the S25 UNVERIFIED | AdbService.java 234-270; AdbDebuggingManager.java 812-832 |
| B2b | An app holding WRITE_SECURE_SETTINGS can write `adb_wifi_enabled=1`; it turns on with no dialog only on a BSSID in the trusted list, otherwise a dialog shows and the setting returns to 0 | HIGH | AdbDebuggingManager.java 822-828, 1127-1135; AdbKeyStore.java 114, 320 |
| B2c | The port is found by mDNS `_adb-tls-connect._tcp`, keeping only the phone's own address (R4's `Discovery.kt` did this on the phone); `service.adb.tls.port` is not readable by apps | HIGH | adb_wifi.cpp 176-209; property_contexts 53 |
| B2d | Shizuku 13.6.0 added "auto start without root on Android 13+ when connected to a trusted WLAN": boot receiver → write the settings → mDNS (3 s) → connect 127.0.0.1 → starter. Its issue #2564 (opened 2026-10-06) says the boot connect always fails (NetworkOnMainThreadException) — not verified: treat it as unproven prior art | HIGH (source); works: UNPROVEN | BootCompleteReceiver.kt 41-89 |
| B2e | The paired key persists (`/data/misc/adb/adb_keys`, `adb_temp_keys.xml`) but expires under the default timeout; then pairing is needed again | HIGH | AdbKeyStore.java 28-45, 189-205 |
| B3 | The 7-day timeout applies to Wi-Fi-paired keys, and a Wi-Fi TLS connect does NOT restart the timer in A16 QPR2: a key used every boot still expires 7 days after pairing. Fix: `adb_allowed_connection_time=0` (the "Disable adb authorization timeout" option; preference key `adb_authorization_timeout`) | HIGH (code); device-verify | AdbDebuggingManager.java 337-343, 928-933, 1162; Settings.java 17326 |
| B4 | Doze, App Standby and wakelock limits apply to app uids (≥ 10000); uid 2000 is exempt; Doze does not restrict binder IPC. Samsung's auto-restart (a reboot) is the real killer | HIGH (AOSP); Samsung partly UNVERIFIED | PowerManagerService.java 4614-4633; AppStandbyController.java 1454 |
| B5 | Nothing has shipped: #526109803 Assigned / P4 / "No update yet" (last change 2026-09-30); #541312863 Assigned / P2. Public adbd (A17 security branch, Jul 2026) still binds INADDR_ANY. CVE-2026-0073 is in the May 2026 bulletin | HIGH | issue extract; aosp-a17sec-adb-daemon_adb_wifi.cpp 89; bulletin 2026-05-01 |
| B6 | Location through the helper: in AOSP the adb and helper paths are identical; the cause is unknown — a device test | UNVERIFIED | LocationShellCommand.java 134-153; SystemSettingsHelper.java 127-140 |
| B7 | Without the helper the app can flip NONE of the four radios; `pm grant` grants only development or runtime permissions | HIGH | WifiServiceImpl.java 1659-1663; BluetoothAdapter.java 1577-1590; ConnectivityService.java 8179; PermissionService.kt 1005-1038 |

### B1 — Why 4d killed the helper
- adbd is an init service; at start init calls `createProcessGroup(uid, pid)` (init-service.cpp 737), creating
  `/sys/fs/cgroup/uid_0/pid_<adbd>`; every child adbd forks inherits it.
- AdbService stops adbd only when both switches are off (AdbService.java 392-396) → `Service::Stop` →
  `KillProcessGroup(SIGKILL)` (init-service.cpp 998). adbd dying on its own does the same in `Reap` (261-272).
  libprocessgroup writes "1" to `cgroup.kill` (processgroup.cpp 416-430): the kernel kills every member whatever its
  session or parent.
- `setsid` did not help: the probe already used `setsid nohup` (r4probe Runner.kt 346), and adbd itself calls `setsid()`
  for shell sessions (shell_service.cpp 339). Moving a process needs write access to the common ancestor's `cgroup.procs`
  (cgroup-v2.rst 567-570) — the root cgroup, system:system 0775 (cgroups.json 25-29). Uid 2000 has no access. Shizuku moves
  cgroups only `if (uid == 0)` (starter.cpp 200); its UI warns "do not disable "Developer options" or "USB debugging", or
  Shizuku will be stopped" (strings.xml 30).
- Other ways it dies: "Revoke USB debugging authorisations" stops and restarts adbd (AdbDebuggingManager.java 715-731); a
  USB-mode change may stop adbd (`init.usb.configfs.rc` runs `stop adbd` when the config passes through "none"; Shizuku's
  manual advises the default USB configuration "No data transfer") — UNVERIFIED on the S25.

### B2 — Restart after a reboot
- (a) `systemReady` sets `ADB_WIFI_ENABLED` from the persistent property (AdbService.java 251-256); `bootCompleted` asks
  AdbDebuggingManager to enable it and writes 0 back if `getCurrentWifiApInfo()==null` or the network is untrusted
  (AdbDebuggingManager.java 812-832). Whether Wi-Fi is up at that moment on the S25: UNVERIFIED. USB debugging persists
  through `persist.sys.usb.config` (AdbService.java 240-243). adbd listens on TCP only with `service.adb.tcp.port` /
  `persist.adb.tcp.port` (adbd-main.cpp 275-298); only init, adbd and system_server may set the first (sepolicy
  private_property.te 527-537), and `service.*` is not persistent.
- (b) Writing `adb_wifi_enabled` 0 → 1 makes AdbService call `enableADBdWifi()` / `startAdbd()` (AdbService.java 461-486);
  then `verifyWifiNetwork(bssid)`: an untrusted BSSID gets `startConfirmationForNetwork` (a dialog) and the setting returns
  to 0 (1127-1135). "Always allow" stores the BSSID (`addTrustedNetwork`, 857), so each access point and band may need its
  own tick (INFERRED). Trusted networks were not restored after a reboot before Android 13 (Gerrit 2128832, "WiFi APs are
  never properly restored"). The only restriction check is DISALLOW_DEBUGGING_FEATURES (UserRestrictionsUtils.java
  806-812); AOSP does not check Developer options here; Samsung UNVERIFIED.
- (c) adbd publishes the port in `service.adb.tls.port` (adb_wifi.cpp 176-187), labelled `adbd_prop` (property_contexts 53)
  with no `get_prop(appdomain, adbd_prop)`; so mDNS. Shizuku's `AdbMdns.kt` 65 and R4's `Discovery.kt` keep only services
  that resolve to the phone's own address; pairing and connect both passed this way on the phone.
- (d) Shizuku's boot start requires API ≥ 33, WRITE_SECURE_SETTINGS and that the last start was by adb
  (BootCompleteReceiver.kt 41-44); at boot it writes `adb_wifi_enabled=1`, `adb_enabled=1`,
  `adb_allowed_connection_time=0`, waits 3 s on mDNS, then `AdbClient("127.0.0.1", port)` and the starter (61-89); the
  server grants WRITE_SECURE_SETTINGS to its own manager app (ShizukuService.java 242-243). Limits: one attempt; the toggle
  is still labelled "Start on boot (root)" (issue #1254); it does not wait for Wi-Fi (issue #1227); issue #2564 above. No
  Samsung-specific statement found.
- (e) Pairing stores the key with a timestamp (AdbDebuggingManager.java 1162); `filterOutOldKeys` drops keys older than the
  allowed time (AdbKeyStore.java 189-205); adbd's TLS check reads `adb_keys` (auth.cpp 327-365). No re-pairing ever only if
  the timeout is 0.

### B3 — Authorization expiry
`DEFAULT_ADB_ALLOWED_CONNECTION_TIME = 604800000` (7 days, Settings.java 17326). A Wi-Fi TLS connect goes to
`MSG_WIFI_DEVICE_CONNECTED` (337-343), whose handler only updates `mWifiConnectedKeys` (928-933); the timer is refreshed
only for USB keys. Settings' option writes 0 when checked and returns to the default when Developer options are turned off
(Settings-AdbAuthorizationTimeoutPreferenceController.java 33, 63-78).

### B4 — Doze and standby
Wakelocks are disabled in idle only for `appid >= FIRST_APPLICATION_UID` (PowerManagerService.java 4614-4633); "System
uids never go idle" (AppStandbyController.java 1454-1456). The helper is not an AMS process (no freezer, no process-state
rules) and inherits `oom_score_adj` -950 (shell_service.cpp 362-368). The app's accessibility binding is
`BIND_AUTO_CREATE|BIND_FOREGROUND_SERVICE_WHILE_AWAKE` (AccessibilityServiceConnection.java 190-193); Doze restricts
network, jobs and alarms, not binder IPC; if the app dies the helper re-delivers the binder (R4 4a PASS). Samsung's "Auto
restart → Restart when needed" restarts on memory pressure "or if it has not been restarted for a period of time", and
"Even if both … are turned off, a system restart may still occur" (`samsung-au-keeps-restarting.html`) — each a reboot.
UNVERIFIED: Samsung's sleeping / deep-sleeping apps applied to a home app; Samsung's freezer; 4b (Doze with Power saving off).

### B5 — The loopback-ADB risk, re-checked 2026-10-08
- #526109803: Assigned, P4, "No update yet", last change 2026-09-30. #3 (Jul 13, a Google engineer): "What about we
  restrict to always only binding to wifi interface wlan0 ?"; #7 "We will take it into consideration"; #15 reported to
  Android Security; #24 "I would expect further measures in the (not so near) future"; #39 (personal view): remove legacy
  tcpip, fix the Wi-Fi port at 555. #541312863: Assigned, P2 Bug, "No update yet".
- The android16 and android17 security branches still use `network_inaddr_any_server`; public AOSP adb `main` stops at
  Mar 2025 (development is private).
- CVE-2026-0073: May 2026 bulletin, A-469080888, Critical, patch level 2026-05-01 or Mainline; fix "Fix EVP_PKEY_cmp usage"
  (842d331f). A second wireless-ADB RCE: CVE-2026-28604 (A-499053500, TLS-handshake use-after-free), September 2026
  bulletin, patch level 2026-09-05.
- **Android 17:** `ADB_ENABLED` is `@Readable(redactedValue = "0")` — "always return 0 for all third-party apps"
  (aosp-a17-Settings.java 14762-14765); a Pixel 10 on CP3A (patch 2026-09-05) confirms Shizuku breaks on it (pengyuw96
  patch README). Cross-profile loopback is blocked; same-profile is unaffected. A new `ACCESS_LOCAL_NETWORK` permission
  applies to apps targeting 37. `allow_adb_wifi_reconnect` is ENABLED in A17's `cp2a` config (the platform turns Wireless
  debugging on by itself on trusted networks); in A16 QPR2 it has no release value (off by default).

### B6 — Location through the helper
`set-location-enabled` resolves `USER_CURRENT_OR_SELF`, enforces WRITE_SECURE_SETTINGS and writes `LOCATION_MODE` under a
cleared identity (LocationManagerService.java 1393-1400; SystemSettingsHelper.java 127-140): adb and the helper are
identical in AOSP. `settings put secure location_mode 3` writes the value the service reads (122-124); restrictions are
DISALLOW_CONFIG_LOCATION / DISALLOW_SHARE_LOCATION only (UserRestrictionsUtils.java 788). Cause UNVERIFIED (a Samsung
override, or two toggles in quick succession). Device test from the helper: `cmd location set-location-enabled false
--user 0; sleep 3; cmd location is-location-enabled --user 0; settings get secure location_mode; logcat -d | grep -i
location`, then helper-first / adb-second, then the `location_mode` write.

### B7 — What the app can do without the helper
Wi-Fi: no ("setWifiEnabled not allowed" for apps targeting Q+, WifiServiceImpl.java 1659-1663). Bluetooth: no (`enable()`
"will always fail" targeting 33+, BluetoothAdapter.java 1577-1590; `ACTION_REQUEST_ENABLE` still shows a user dialog).
Airplane: no (`setAirplaneMode` needs NETWORK_AIRPLANE_MODE or NETWORK_SETTINGS; the service writes the setting AND sends
the protected `ACTION_AIRPLANE_MODE_CHANGED`, ConnectivityService.java 8179-8191, core AndroidManifest.xml 459; writing
`airplane_mode_on` alone is not a toggle — do not ship it). Mobile data: no (MODIFY_PHONE_STATE, signature|privileged|role).
`pm grant` takes development or runtime permissions only ("not a changeable permission type", PermissionService.kt
1005-1038): WRITE_SECURE_SETTINGS yes (all the restart path needs); `dumpsys deviceidle whitelist +app.tileshell` optional;
NETWORK_SETTINGS, NETWORK_AIRPLANE_MODE, MODIFY_PHONE_STATE, BLUETOOTH_PRIVILEGED, MANAGE_DEBUGGING are not grantable.

### B8 — What this means for a seamless restart on the S25 Ultra (agent B's synthesis)
**One-time setup:** Developer options on and **USB debugging on** (no cable); pair once; tick "Always allow on this
network" on the home Wi-Fi (per BSSID); the helper runs `pm grant app.tileshell android.permission.WRITE_SECURE_SETTINGS`;
the app writes `adb_allowed_connection_time=0`; Samsung's "Restart when needed" / "Restart on schedule" off (recommended).

**After unlock (BOOT_COMPLETED or the home activity starting):** (1) a Wi-Fi network callback waits for Wi-Fi; (2) if
`adb_wifi_enabled` is 0, write 1 (only from 0 — writing while on restarts the server); (3) watch mDNS
`_adb-tls-connect._tcp` for the phone's own address for ≈ 30 s, re-writing the setting if AdbDebuggingManager flipped it
back (a boot race); (4) Kadb to 127.0.0.1:port, `setsid nohup app_process … --daemon`; the binder arrives as in R4;
(5) optionally write `adb_wifi_enabled=0` once the helper answers, to shrink the attack surface — the helper survives
because USB debugging keeps adbd running (R4; AdbService.java 392-396) — device-verify; (6) a DeadObjectException mid-session
runs the same path.

**Falls back to one tap:** no Wi-Fi or an untrusted Wi-Fi at boot (a dialog); a new BSSID; before the first unlock.
**Re-pairing needed:** the key expired (timeout not 0); "Revoke authorisations"; Developer options turned off (also resets
the timeout). **Nothing can restart it:** USB and Wireless debugging both off, or Developer options off; mid-session an adbd
restart (possibly a USB-mode change) kills the helper and only the trusted-Wi-Fi path brings it back.
**Future breakage to watch:** a loopback / wlan0 restriction if it ships; on Android 17 never gate on `adb_enabled` (reads
0); the mDNS lookup may need `ACCESS_LOCAL_NETWORK` when targeting 37 (UNVERIFIED).

## C — W10M's quick actions and their Android counterparts (agent C)

Sources under `src/c-quick-actions/` (`$C`): video sections and frames (`$C/video/`, `$C/frames/`), pages (`$C/pages/`,
`$C/pages/android/` for the Android API references), searches (`$C/search/`). Lead's check: `$C/frames/i98/ac_collapsed45_expanded72_settings79.5.png`
shows, on 14393 (S1 I98ENfXJRqA, 72 s and 79.5 s), the collapsed row Camera · BDNWiFi · Bluetooth · Rotation lock, the
expanded grid with that row at the bottom, and the Settings grid in the same order with the short row (Flashlight, Note,
Location) on top and no VPN — as reported below.

### C1 — The list (14393, en-US; Settings > System > Notifications & actions > "Add or remove quick actions")
All settings, Connect, Battery saver, Bluetooth, Flashlight, Note, VPN, Location, Airplane mode, Camera, Cellular, Mobile
hotspot, Quiet hours, Wi-Fi, Rotation lock, Brightness — 16 (S1 106.5 s, 112 s, every switch off;
`$C/frames/i98/addremove_list_t106.5_t112.png`). That list's order is fixed and does not follow the arrangement.

### C2 — Default order (14393; the same on three separate phones, none a factory reset)
**Camera, Wi-Fi, Bluetooth, Rotation lock | All settings, Airplane mode, Cellular, Mobile hotspot | Quiet hours,
Brightness, Connect, Battery saver | Flashlight, Note, Location. VPN off** (15 on). Proof: S1 Settings grid at 79.5 s
before the presenter rearranges anything and the action center at 72 s; yi7zzB3W6Qk (5 columns, Spanish) at 141 s
(`$C/frames/yi7/yi7_t141.png`); tenforums 47403 screenshot 3 (5 columns, Feb 2017, `$C/frames/tf47403_qa_1-4.png`).
**Collapsed row on a 4-column phone: Camera, Wi-Fi, Bluetooth, Rotation lock.**
- Layout rule (VERIFIED): the order fills the bottom row left to right, then up a row at a time; the collapsed row is the
  first four (consistent with R7 §4.3.1: the collapsed row stays as the bottom row of the expanded grid). An incomplete row
  sits at the TOP, packed left (S1 72 s: Flashlight, Note, Location, gap; yi7 173.6 s: "Nota" alone). Direction proven by
  the S1 drag at 80–91.5 s (Quiet hours moved between Wi-Fi and Bluetooth; only a bottom-up reflow fits). With exactly four
  actions on there is no "Clear all / Expand" row, just "No notifications" (`$C/frames/i98/ac_four_actions_t445.0.png`).
  Actions switched on are appended in the order switched on (S1 440–446 s).
- Earlier builds (not governing): 10586 / 14322 put Wi-Fi, Bluetooth, Rotation lock, All settings first (Microsoft's
  animation in tenforums 47403, `$C/frames/tf47403_gif_f56_f120.png`; USBlwy-kpiM 46 s); a 10586 review shows Brightness
  first (`$C/frames/phq/phq_t36_full.png`) — the 10586 sources disagree.
- **15063:** the names do not change (9roLX1b1V-4 74.5 s, en-IN; fZPBidQmNoo, Apr 2017, `$C/frames/fzp/fzp_ac_t6_rows.png`).
  Night light, Focus assist, Project, Tablet mode and Network appear in no capture; Night light never shipping on W10M rests
  on search titles only (pages 403) — UNVERIFIED. **The 15063 default order is UNVERIFIED** (no untouched grid found); the
  14393 order is used on the assumption the default carried forward.
- Device-dependent: Flashlight is missing from the 10586 picker on a Lumia with no flash (`$C/frames/phq/popup_t38-50_2fps.png`).
  Others: UNVERIFIED.
- Labels: the Settings grid shows fixed names; the action-center tiles show live state — Wi-Fi the network name or
  "Available", Bluetooth "Not connected" (10586), Cellular "Cellular data", Brightness "41%" / "100%" / "Automatic", on 15063
  "Suggested" / "Darker". en-GB: Flight mode, Mobile, WiFi.

### C3 — What a tap does
| Kind | Actions | Proof |
|---|---|---|
| Toggle (accent fill when on) | Wi-Fi, Location, Rotation lock | S1 72 s |
| | Bluetooth | 6zcTRCEPlcM 73 s |
| | Cellular | Microsoft's animation; 9ro "Mobile data" filled |
| | Flashlight | Wikipedia W10M version history, build 10149: "Flashlight toggle" |
| | Quiet hours, Battery saver, Airplane mode | Thurrott (desktop): "Toggles…"; never filmed on |
| | Mobile hotspot | UNVERIFIED |
| Cycler | Brightness | the label shows the level; the phone's step order UNVERIFIED (desktop 25→50→75→100→25, Thurrott 5116) |
| Opens something | All settings | R7 c73: opens Settings |
| | Note | a new OneNote page (WinBeta) |
| | Camera | never shown filled |
| | Connect, VPN | desktop: the wireless-display pane / VPN settings; on a phone UNVERIFIED |

Long-press, VERIFIED on 10586.29 only (WinBeta 2016-01-28, `$C/pages/winbeta_dissecting_ac_gestures.txt`;
`$C/frames/6zc/6zc_longpress_32_34_73_81.png`): Bluetooth → Bluetooth settings; Brightness and Rotation lock → Display
settings; Quiet hours → Cortana's Quiet hours page; Camera and Flashlight do nothing (the panel closes); Note = tap. 14393+:
tenforums' text only ("Press and hold a quick action for a setting to jump directly to that setting's page") — UNVERIFIED.

### C4 — The Android counterpart of each (S25 Ultra; the app targets SDK 36, `app/build.gradle.kts`)
| W10M | App alone (API, from `$C/pages/android/`) | Helper as uid 2000 (R4) | Settings / panel fallback |
|---|---|---|---|
| All settings | phase 19's front (not built); until then `Settings.ACTION_SETTINGS` | – | – |
| Connect | `Settings.ACTION_CAST_SETTINGS` ("may not exist"; reaching Smart View UNVERIFIED) | – | – |
| Battery saver | no public setter | **did not change** | `ACTION_BATTERY_SAVER_SETTINGS` |
| Bluetooth | `BluetoothAdapter.enable()` false when targeting ≥ 33; `ACTION_REQUEST_ENABLE` shows a dialog (B7) | **changes** | `ACTION_BLUETOOTH_SETTINGS` |
| Flashlight | `CameraManager.setTorchMode(id, on)`; no permission named; the torch goes off if the app that set it exits | – | – |
| Note | the shell's Notes slot (`CortanaPrefs.notesApp`, launched as `ActionLayer.takeNote` does) | – | – |
| VPN | `Settings.ACTION_VPN_SETTINGS` | – | – |
| Location | no public setter | **did not change** (B6) | `ACTION_LOCATION_SOURCE_SETTINGS` |
| Airplane mode | no (B7) | **changes** | `ACTION_AIRPLANE_MODE_SETTINGS` |
| Camera | `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` (as `Slot.CAMERA`) | – | – |
| Cellular | no (B7) | **changes** | `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` |
| Mobile hotspot | `TetheringManager.startTethering` is public in API 36; its permission not in the fetched text (UNVERIFIED) | **refused** (SecurityException) | no public tethering action; `ACTION_WIRELESS_SETTINGS` |
| Quiet hours | `NotificationManager.setInterruptionFilter` with DND access — **targeting ≥ 35 it switches the app's own automatic rule, not the global filter**, and cannot turn off a DND the user set | `cmd notification` not tested | `ACTION_ZEN_MODE_PRIORITY_SETTINGS` |
| Wi-Fi | `WifiManager.setWifiEnabled` false when targeting ≥ 29 (B7) | **changes** | `Settings.Panel.ACTION_WIFI` |
| Rotation lock | `Settings.System.ACCELEROMETER_ROTATION` with `WRITE_SETTINGS` (`canWrite`, `ACTION_MANAGE_WRITE_SETTINGS`) | not tested | `ACTION_DISPLAY_SETTINGS` |
| Brightness | `SCREEN_BRIGHTNESS` (1–255) / `SCREEN_BRIGHTNESS_MODE` with `WRITE_SETTINGS`; One UI's mapping UNVERIFIED | not tested | `ACTION_DISPLAY_SETTINGS` |

The manifest declares neither `WRITE_SETTINGS` nor `ACCESS_NOTIFICATION_POLICY` today; phase 19 plans checklist rows for
both. W10M had no NFC action; `Panel.ACTION_VOLUME` belongs to the volume panel, not a quick action.

### C5 — Open points
1. The 15063 default order (assumed = 14393's). 2. No 14393 source is a factory reset (three identical phones point to the
system default). 3. The phone's brightness steps. 4. Long-press on 14393+, and what Connect and VPN open on a phone.
5. On the S25: does `ACTION_CAST_SETTINGS` resolve; DND's automatic-rule behaviour under One UI Modes; the hotspot
permission; location through the helper (B6); whether the helper can set DND, brightness and rotation (not tested).

## What this changes in the phase 04 doc (the lead's reading, for the interview and the review)
- **Interview item 8 (the nav bar)** is no longer "a panel that ends above the buttons or runs behind them" (that was the
  APP overlay of 2026-09-22): the action center's ACCESSIBILITY overlay takes touches in the nav bar's strip in both
  navigation modes (MEASURED) and, by AOSP's layer order, draws over the nav glyphs (INFERRED; one phone screenshot
  confirms). W10M's own behaviour — the open action center covering its nav bar — is available. With gesture navigation
  the system's home / back swipes still win over it (A3).
- **Interview item 2 (the top edge)**: a strip over every app takes slow pulls from Samsung's shade; a fast flick from the
  very top still opens Android's shade in AOSP (A4); the system shade stays reachable through
  `GLOBAL_ACTION_NOTIFICATIONS` / `_QUICK_SETTINGS`. One UI is unmeasured.
- **Interview item 4 and R3F-01 (the helper's life)**: the helper lives only while USB debugging (or Wireless debugging)
  stays on (B1, a hard platform rule); after a reboot it can restart with no taps only on a Wi-Fi network marked "Always
  allow" (B2), and only if the key timeout is 0 (B3); elsewhere it falls back to a one-tap start; with Developer options or
  both debugging switches off nothing can start it.
- **Interview item 3 (quick actions)**: W10M's 16 (C1), default 15 in the 14393 order (C2); of them only Wi-Fi, Bluetooth,
  Cellular and Airplane flip through the helper; Flashlight, Rotation lock and Brightness (with WRITE_SETTINGS) and Quiet
  hours (with DND access, as an automatic rule) the app can do itself; Battery saver, Location and Mobile hotspot can only
  open Settings on this phone (R4); All settings, Connect, VPN, Note and Camera open something.
- **Interview item 7 (hold Back)**: `GLOBAL_ACTION_RECENTS` exists (A4).
- **Volume panel**: the build's E3 / E4(g) method stands (A5.6); the service never sees volume keys while ringing or with
  the screen off (A5.3), so those edge cases leave the stock behaviour; One UI's own panel is unmeasured.
- **Old E4(a)** drives the pull-down with `input motionevent` from y = 5: AOSP's top-swipe rule takes a > 24 dp move within
  500 ms (A4). Any device row that pulls from the top edge must pull slowly or start below the zone.
