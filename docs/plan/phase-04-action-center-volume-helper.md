---
phase: 04
slug: action-center-volume-helper
status: FINAL   # FINAL 2026-10-09 12:30 (Jeremy: "(a)", Q-04-14); from here this doc changes only through a dated INDEX Change Log entry. Was DRAFT: interview DONE 2026-10-09 (Q-04-0 … Q-04-10); rewritten 2026-10-09 after the interview (R4 recorded, build tasks against the code as built, acceptance re-cut to the one-round shape); round 4 (opus + opus, Q-04-0) RAN 2026-10-09 and its triage is applied (review/2026-10-09-phase04-r4-triage.md); Q-04-11 (a) 2026-10-09 12:11, Q-04-12 (a) 12:18, Q-04-13 (b) 12:28
depends-on: [01, 03, 10, 12, 13, 14]   # 01: the drawn nav bar (`bars/SystemBars.kt`, Back hold ADD), the notification listener (`feeds/TileNotificationListener.kt`, the shade's list ADD), the Settings hub and the setup checklist this phase ADDs to; 03: `Request` / `CommandMatcher` / `LockGate` / `ActionLayer` (the switch commands), `qa/phase-03/scripts/lib.sh`, `provision.sh` and the exported allow-list; 10: the shell's Music app is the media session device row A6 plays (`music_fixtures`); 12: the setup wizard walks this phase's grant rows and its E14 template runs for each (T12-5 / C-4); 13: the Fluent engine (`ui/fluent/Fluent.kt`) the volume panel's acrylic is an ADD to (cross-window source); 14: `MusicFeed.now` and `Transport.PREVIOUS` (`feeds/MusicFeed.kt:57`, `:150-151`), which the volume panel's media section reads and drives. Not 15: its `overlay` row is not needed (Decisions 2026-10-09 "The setup rows"). Not 19: built after this phase (Build order 20 → 04 → 19)
---

# Phase 04 — W10M action center, volume panel and the in-APK privileged helper

## Goal
Windows 10 Mobile's action center, quick actions and volume panel, in every app on the S25 Ultra. A W10M (14393) action center
pulls down from a top-edge strip over every app and over Start — never over the lock screen (Q-04-3) — drawn through the shell's
accessibility service. It follows the finger (R7 §4.1), covers the nav bar when open as W10M's did (Q-04-4), and carries W10M's 16
quick actions (15 on by default in 14393's order, each movable and removable; Q-04-5) and the notification list: tap, swipe away,
the app's own buttons and an inline reply (Q-04-8). Wi-Fi, Bluetooth, Cellular and Airplane mode flip through a Shizuku-style
privileged helper that ships inside the shell APK. It lives while USB debugging (or Wireless debugging) stays on; after a reboot it
restarts with no taps on a Wi-Fi marked "Always allow" and with one tap elsewhere (Q-04-1, addendum B8); while it cannot run, each
of those four tiles opens Android's own control and says why (Q-04-2), and before the helper is first set up each opens the
helper's own page (Q-04-11). A W10M volume panel replaces Android's, with media controls
for whatever app is playing (Q-04-9) and acrylic where cross-window blur is allowed (Q-04-10). Tess gains the four switch commands,
behind "Unlock to continue" while the phone is locked (Q-04-6), and holding the drawn Back opens Android's recent apps (Q-04-7).
The helper's state shows in the action center, in Settings and in the setup checklist.

## Scope
**In:** R4 as the entry gate — it ran on the phone 2026-09-29 and its one FAIL (4d) was re-asked as Q-04-1 (recorded below); PQ2
was observe-only and went to phase 06 (INDEX Change Log 2026-09-28). The shell's accessibility service and its three overlay
windows (RV10 density). The action center (R3 A19 geometry, R7 §4.1–4.3 motion, solid black, Q-04-10) and its strip, removed while
the keyguard shows. The 16 quick actions, each by its mechanism (addendum C4), and their customisation page. The notification list
with tap, swipe, action buttons and RemoteInput reply. The privileged helper: an `app_process` entry shipped in the APK,
daemonised, a binder handoff checked by uid and signature, an allow-list of named verbs for the four radios, and the restart path
(Q-04-1, B8). The volume panel through the accessibility key filter (R3 A20, R7 §4.5), its media section, and the cross-window
acrylic ADD to phase 13's engine. Tess's four switch commands (ADDs to phase 03). Hold Back → recents (ADD to phase 01's bar).
Settings pages (Notifications & actions; the helper's page), and checklist rows and wizard steps (accessibility, Modify system
settings, Do Not Disturb access; the helper's three observation rows).
**Out (explicitly):** replacing the lock screen, the status bar or Samsung's settings pages (CANNOT); any action center over the
lock screen (Q-04-3); any separately installed app, Shizuku included (RV1, Q13); a generic shell, a content-provider call, or any
helper verb beyond the four radios (the 2026-09-16 trust line; phase 19 ADDs its own verbs and phase 06 its voicemail verbs, each
reviewed in its own phase); an "open a panel" placeholder IN PLACE OF the helper (phase 03 Out, phase 19 Out, Rule 16) — Q-04-2's
fallback runs only while the helper cannot, and round 4 checks that reading; the shell switching USB debugging, Developer options
or its own accessibility service back on by itself; glance (07); the Settings front (19).
**Cross-phase:** phase 01's notification listener, drawn bar, Settings hub and checklist gain recorded ADDs (INDEX Change Log
lines when built); phase 03's matcher, lock gate, action layer, `provision.sh` and exported allow-list gain ADDs; phase 12's wizard
walks the three new grant rows (E14, device row A1); phase 13's engine gains the cross-window source; phase 07 later uses this
phase's service for `GLOBAL_ACTION_LOCK_SCREEN` and phase 09's provider rejects the helper's pid, which the shell records at each
handoff for its own helper page — both are those phases' ADDs, and no hook is built for them here; phase 19 re-homes this phase's
two pages, draws its helper rows with this phase's tile-state form (Y4) and ADDs five verbs to the helper (its Q3 C), and finds the
`WRITE_SETTINGS` and Do Not Disturb rows already built here (Q-04-5).
**Doc edits recorded with this phase (INDEX Change Log lines when built)** (round 4 D10, V16; this doc edits no other doc):
- phase 12 lines 44-46, 133 and 316-320 (line 44: "phase 04 runs E14 for each", pairing and helper included): the wizard does
  not walk the helper's pairing or the helper (Q-04-11); the helper's three rows are observation rows, no step and no E14
  (Decisions "The setup rows"); the `Checklist.kt:138` on lines 45 and 317 is the `location` row, phase 15's
  `overlay` row is `onboarding/Checklist.kt:174-176`;
- phase 12 line 923: `appops set app.tileshell WRITE_SETTINGS allow` and `cmd notification allow_dnd app.tileshell` are this
  phase's `provision.sh` lines, not phase 19's;
- phase 12's `qa/phase-12/scripts/e1.sh:52-60` ((b): `pm clear`, then "every grant" from adb; phase 17 extended it) gains this
  phase's three grants — `settings put secure enabled_accessibility_services
  app.tileshell/app.tileshell.ac.ShellAccessibilityService` with `accessibility_enabled 1`, `appops set "$PKG" WRITE_SETTINGS
  allow`, `cmd notification allow_dnd "$PKG"` — or its (b) shows the wizard on this build instead of `core held`;
- phase 19 lines 69 and 209-214: both rows (`write_settings`, `dnd_access`) and their `provision.sh` lines are built here; phase
  19 rewrites their why lines for its pages and re-runs E14 (its E16) over them;
- phase 19 lines 64 and 403: the dropped E4(d) is this doc's A8 (c);
- phase 07 lines 16, 22 and 45: "phase 04's overlay row" is phase 15's `overlay` row (`onboarding/Checklist.kt:174-176`); this
  phase adds none.

## Decisions
- 2026-09-16: Privileged helper built into the shell APK, started over Wireless debugging; RV1 holds (Q13, Jeremy)
- 2026-09-16: The phase opens only after the R4 feasibility + licence spike passes; failure re-asks Jeremy, no silent fallback (Q13, R3F-02)
- 2026-09-16: Seamless restart path is attempted before settling for one-tap restart (Q13, P2, Jeremy)
- 2026-09-16: Toggle commands for Cortana are added here (split, agent under P3)
- 2026-09-16: Trust-touching part (the helper is code running at the shell uid, reachable over a binder handoff): adversarial team review is mandatory before `done`. The handoff verifies the client's uid and signature before handing out the binder; the helper accepts only an allow-listed verb set (no generic shell); the helper's shell uid would pass phase 09's adb-shell-uid provider check, so the allow-list contains no content-provider call, and phase 09's provider additionally rejects the helper's own process (a phase 09 decision, tested there, since that provider does not exist during this phase's QA). R4's spike proves the handoff check and the allow-list (review F1-M10, agent; provider requirement moved to phase 09 by review T-M21 / R2-m4)
- 2026-09-16: Action center geometry (R3 A19, MEDIUM, 14393 S1 I98ENfXJRqA @ 72.0 s expanded and 452 s collapsed, ± 1.1 epx; 15063 not filmed, H5): quick-action tiles 85 × 60.5 epx at an 89 / 64 epx column / row pitch (gaps 4 / 3.4 epx), 4 × 4 expanded and 4 × 1 collapsed, side margins 4 / 4.5 epx; grid top 64 epx expanded and 77 epx collapsed; a second status line (battery % and date) under the status bar, ending 38 epx from the top; tile glyph ≈13 epx at ≈12 epx from the tile's left and top; tile label in the 12-epx caption class at the bottom-left, inset ≈5 epx, baseline ≈8 epx above the bottom; "Clear all" (left) and "Collapse" (right) with a 9.6-epx cap, 12-epx side margins, 24 epx below the grid; a notification group header with a 21-epx app icon and the app name; notification items with a 51-epx circular avatar; a full-width 13-epx accent drag handle with an "=" grip at the bottom edge; colours as captured: tile (48,48,48), active tile accent, background black. ~~Measured by E5~~ (E5 dropped 2026-10-09 by the one-round re-cut: these values are the JVM-tested constants of `AcGeometry`, build task 5, judged in H5; Acceptance "Dropped, and why") (review T-B5 / F1-M12, agent)
- 2026-09-16: Volume panel geometry (R3 A20, MEDIUM, 14393 S1 @ 228.5 s, ± 1 epx; 15063 not filmed, H6), captured in the two-slider state with media playing (state named by review R3T-M9): full width from the screen top to 274 epx, background (55,55,55) as captured; each stream row has its title in the 15-epx body class at x = 12 epx, a 29-epx glyph at x 12–39 epx, a slider track 2 epx thick from x 67 to 293 epx (unfilled (103,103,103), filled accent) with an accent thumb 6.4 × 20.4 epx, and its value in 21-epx-tall digits right-aligned at x 347 epx; the first track sits 48.7 epx from the top and the second row 100 epx lower; a "Vibrate on" caption (8.6-epx cap) at 217–225 epx, x 12 epx, and a chevron at x ≈340 epx. ~~Measured by E5~~ (E5 dropped 2026-10-09: the JVM-tested constants of `VolumeGeometry`, build task 8, judged in H6; Acceptance "Dropped, and why") (review T-B5 / F1-M12, agent)
- 2026-09-16: Motion source: R3 measured no action-center or volume motion (R3 §E), so E4's old "(R3) tolerance" pointed at nothing. R7 (docs/plan/r7-measurements.md §4) now supplies the pull-down, close, expand / collapse, notification swipe and volume panel motion in the R7 lines below; ~~E4 measures them~~ (E4 dropped 2026-10-09 by the one-round re-cut: the R7 values are JVM-tested as the motion rules of build tasks 5, 7 and 8 and judged in H1; Acceptance "Dropped, and why"), and every value R7 leaves LOW or UNMEASURED is a row in Approximations (Y5–Y13) with its own H-row. This phase stays DRAFT until its post-R4 interview and does not freeze with the other phases (review T-B5 / F1-M12, agent; resolved by R7)
- 2026-09-16: Per-value approximations (agent): the rows in Approximations below replace the generic "accept approximations" row; each has its own H-row (review F1-M12 / T-B5, agent)
- 2026-09-17: Input paths (agent, from AOSP `InputDispatcher.cpp`): the volume panel reads volume keys through the accessibility service's key filtering (`onKeyEvent`), which sees only keys from a real input device, because `injectInputEvent` never calls the input filter; so `adb shell input keyevent KEYCODE_VOLUME_UP` reaches the audio service and shows the stock panel. Emulator rows therefore press volume keys through the emulator's virtual input device (`adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_SYN:0:0`, then `adb emu event send EV_KEY:KEY_VOLUMEUP:0 EV_SYN:0:0`), and the phone uses the physical key. The action center's top-edge strip and panel are overlay windows that receive touches directly, not through accessibility motion-event filtering, so `adb shell input motionevent` reaches them (review R3T-B1, agent)
- 2026-09-16: Action center pull-down (R7 §4.1; 14393 A1 I98ENfXJRqA 60-fps screen recording, distances ± 1.1 epx, timings ± 17 ms; the 15063 A3 9roLX1b1V-4 cross-check agrees within its slow refresh, R7 §4.6.1 LOW, Y2): a finger-following drag, not a fixed animation fired by a swipe. The panel grows down from the top edge as a clip with the accent handle (geometry above) riding its bottom edge, and the quick-action grid does not move inside it (§4.1.1, HIGH). It first appears once the finger is 124–152 epx below the top edge (§4.1.3, MEDIUM), then tracks the finger 1:1 with no resistance and holds any partial position while the finger rests, the handle's bottom 10.7 ± 1.1 epx above the touch point (§4.1.2, HIGH). "Open %" in this doc is the handle's bottom edge as a fraction of the screen height, R7's definition (review R3T-M8). The notification list is drawn below its final place while the panel is not fully open (§4.1.9, MEDIUM), in two phases (split by review R3T-B2): during the drag the offset stays between 3.3 and 8.7 epx (R7 o128: +4.0 px with the handle at 53 % open, rising to +8.1 px while held at 78 %; o244 and o197 +3.8 and +3.1 px; 1.072 epx per px), its shape between those points being approximation Y13; after release it equals 0.062 (0.060–0.064) × the handle's remaining travel (o128, 11 frames), which R7 measured only after a release at 78 %, where the two phases meet; after an earlier release, where that product would exceed the drag offset, approximation Y13 applies. On release the panel runs a fixed-duration settle of 290 ms (250–317 ± 17 ms measured) whatever distance is left (§4.1.4, MEDIUM); after a slow or stationary release the curve is cubic-bezier(0.67, 0.13, 0.30, 0.88), max residual 2.1 px of 132 px (§4.1.5, MEDIUM). The curve after a flick and the release threshold are Y5 and Y6. Background dim in dark theme follows how far the panel is open, not time (§4.1.8, HIGH form, MEDIUM values): at these open fractions of the screen height the app below keeps this fraction of its brightness: 18 % → 0.56–0.57, 29–32 % → 0.25–0.29, 48–52 % → 0.22–0.25, 57–59 % → 0.16–0.20, 66 % → 0.08, 89 % → 0.09–0.12. It is drawn as a black scrim with alpha 1 − that fraction; the curve between those points and light theme are Y7. The open panel covered W10M's nav bar (§4.1.10, MEDIUM; one 15063-era capture, A4, shows the handle above a visible nav bar). ~~Whether an accessibility overlay can cover Android's nav bar is UNVERIFIED: R4 probes it (P5), and interview item 8 takes the result.~~ SUPERSEDED 2026-10-09 by Q-04-4: R4's P5 (2026-09-29) measured the accessibility overlay taking taps in the nav bar's strip, and the open panel covers it. ~~Measured by E4~~ (E4 dropped 2026-10-09, as in the motion-source line above) (R7, agent)
- 2026-09-16: Action center close (R7 §4.2; 14393 A1, ± 1.1 epx, ± 17 ms). Drag up: the handle stays put until the finger has moved 32–38 epx up, then follows it 1:1 (§4.2.1, MEDIUM). An upward flick closes the panel from any height (§4.2.2, HIGH): the handle keeps rising for 1–3 frames and disappears. The panel content (quick actions and notifications) then drifts up while fading out, accelerating (ease-in), and is gone 110 ms (100–133 ms) after the handle (§4.2.3, HIGH). The app below is back to full brightness 260 ms (233–317 ms) after the handle is gone (§4.2.4, MEDIUM). Tap on a quick action that launches an app (e.g. All settings), one timeline from R7's frames (rewritten by review R3T-B3): the handle vanishes first; within 50 ms the panel background's lower edge is already moving up (c73: 490 px, 107 px above the 597-px rest, 50 ms after the handle frame), and it wipes to the top at a roughly constant speed; the background is gone 216 ± 17 ms after the handle vanished (c73: 73.367 → 73.583 s). c245, the second event, has no handle frame in R7; its wipe from the first moving frame to gone took 183 ms, so 183–200 ms is the wipe's own length, not a handle-to-gone time. The content drifts 20–27 epx up while fading, and the scrim fades as the wiped edge rises, reaching 0 at the top (§4.2.5–4.2.6, MEDIUM); the app's own launch follows. Closing by Back or by tapping a notification is Y8. ~~Measured by E4~~ (E4 dropped 2026-10-09: JVM-tested as `CloseRules`, build task 5; judged in H1) (R7, agent)
- 2026-09-16: Quick actions expand / collapse (R7 §4.3; 14393 A1, ± 33 ms): the collapsed row stays as the bottom row of the expanded grid and the added rows appear above it, entering clipped under the status area. "Expand" becomes "Collapse" and back, and the notification list moves as a block, 188 epx for the 4 × 4 grid's 3 added rows (§4.3.1, §4.3.4, HIGH). Expand takes 200 ± 33 ms with cubic-bezier(0.34, 0.17, 0.58, 1.06); collapse takes 283 ± 33 ms with cubic-bezier(0.52, 0.19, 0.15, 1.00) (§4.3.2–4.3.3, MEDIUM). ~~Measured by E4~~ (E4 dropped 2026-10-09: JVM-tested as `ExpandMotion`, build task 5; judged in H1) (R7, agent)
- 2026-09-16: Notification swipe (R7 §4.4; 14393 A1 60 fps and A2 yi7zzB3W6Qk, ± 133 ms): dragging a notification group right moves the whole group (header and items) 1:1 with the finger, keeping the grab offset, and it follows the finger back (§4.4.1, HIGH). The group drops to 0.36–0.41 of its brightness once it is 19–28 % of the screen width from rest, and is back to full brightness under 14 % (§4.4.2, HIGH; the step point inside that bracket is Y9). Candidates (LOW, R7 §4.4.3): released at 73 % of the width it is dismissed; released at 28–30 % it snaps back. The dismiss threshold, slide-out, reflow of the groups below, and left swipe are Y9 (H12). ~~Measured by E4~~ (E4 dropped 2026-10-09: JVM-tested as `SwipeRules`, build task 7; judged in H12) (R7, agent)
- 2026-09-16: Volume panel motion (R7 §4.5; 14393 A1, ± 1.1 epx, ± 17 ms; 15063 UNMEASURED, Y3). Show: the panel grows down from the top edge and overshoots. Its lower edge passes its rest position (100.4 epx with one slider) to a peak 12.3 epx lower (+12 %) about 150 ms in, easing with cubic-bezier(0.06, 0.99, 0.42, 0.99) (the first frame is already at 68 % of rest), then returns to rest in 67 ms, 217 ± 17 ms in total. The app below neither moves nor dims (§4.5.1, MEDIUM). Timeout hide: instant, gone in one frame with nothing moving or fading (§4.5.3, MEDIUM); the timeout length is Y11. Leaving the page while it shows: the whole panel slides up off the top as a block in 134 ± 17 ms, close to linear, cubic-bezier(0.47, 0.25, 0.70, 1.09) (§4.5.5, MEDIUM, measured on a Start-key press; the other navigations are Y11). The slider row's entrance and the second slider's slide-down are Y10 and Y12. ~~Measured by E4~~ (E4 dropped 2026-10-09: JVM-tested as `VolumeMotion`, build task 8; judged in H1) (R7, agent)
- 2026-10-09 07:38: Q-04-0, the review count (Jeremy: "(a)"). Rounds 1–3 (2026-09-16/17) covered this doc's pre-R4 DRAFT, so the cap of 3 is reached on paper; he chose A: ONE more round (round 4), run after this interview, two Opus reviewers (design / correctness and testability / evidence), over the cap on his word and the last. No round after it (Q-04-0, Jeremy)
- 2026-10-09 07:55: Q-04-1, how the helper stays alive (Jeremy: "(b)"): USB debugging AND Wireless debugging both stay on. Facts the question rested on (addendum B, AOSP 16 QPR2): the helper dies whenever both are off (R4 4d on the phone; init SIGKILLs adbd's cgroup, B1); adbd keeps running while either is on (`AdbService.java` 392-396); Wireless debugging switches itself off whenever Wi-Fi disconnects or moves to another access point (`AdbBroadcastReceiver.java` 101-121). So the helper's life rests on USB debugging (no cable needed), and the agent reads "stays on" for Wireless debugging as: the shell writes `adb_wifi_enabled=1` again whenever the phone joins an access point in the trusted list (no dialog, B2b) and never on an untrusted one (that would show Android's dialog), and it never writes 0. The setup: Developer options on, USB debugging on, Wireless debugging on, pair once, "Always allow on this network" on the home Wi-Fi, `WRITE_SECURE_SETTINGS` granted through the helper, `adb_allowed_connection_time=0`. After a reboot: no taps on a trusted Wi-Fi, one tap elsewhere (B8). Told to him, not asked: with the timeout at 0 any computer ever allowed keeps adb access over a cable; wireless adb had two remote-code bugs patched in 2026 (CVE-2026-0073, May; CVE-2026-28604, September) (Q-04-1, Jeremy)
- 2026-10-09 08:38: Q-04-2, the four helper tiles (Wi-Fi, Bluetooth, Cellular, Airplane mode) while the helper is not running (queue item 4, R3F-01) (Jeremy: "(a)"): a tap starts the helper, then flips the switch; on an untrusted Wi-Fi Android first shows its own "Allow wireless debugging on this network?" dialog (addendum B2b). When the helper cannot start — no Wi-Fi (Wireless debugging needs a Wi-Fi connection: `AdbDebuggingManager.java` 1094 "Not connected to any wireless network. Not enabling adbwifi."; so also the Wi-Fi tile while Wi-Fi is off), or the pairing expired or was revoked — the tap opens Android's own control for that switch (Wi-Fi `Settings.Panel.ACTION_WIFI`, Cellular `Settings.Panel.ACTION_INTERNET_CONNECTIVITY`, Bluetooth `Settings.ACTION_BLUETOOTH_SETTINGS`, Airplane `Settings.ACTION_AIRPLANE_MODE_SETTINGS`; addendum C4) and the tile shows a line saying why. The agent applies the same rule to Tess's toggle commands (start the helper, else open the same control and say why). Told to him: phase 03 Out and phase 19 Out ban an "open a panel" placeholder IN PLACE OF the helper (Rule 16); the agent reads this runtime fallback, used only while the helper cannot run, as outside that ban, and round 4's reviewers are asked to check that reading (Q-04-2, Jeremy)
- 2026-10-09 09:00: Q-04-3, where the action center pulls from (queue item 2, M5) (Jeremy: "(a)"): a top-edge strip (this phase's accessibility overlay) over every app and over Start, NOT over the lock screen: while the keyguard shows, the strip is removed and Samsung's panel is the only pull-down, so nothing the shell shows (notification text, the four switches) is reachable without unlocking. Facts it rested on (addendum A1, A4; AOSP 16 QPR2; One UI unmeasured, a phone check): the overlay sits above the keyguard (layer 31 > 17), so leaving the lock screen to Samsung is the shell's own rule, not the platform's; a slow pull goes to the strip, while a quick flick from the very top (> 24 dp within 500 ms) is handed to the status bar (`DisplayPolicy` `transferTouch`), except on the lock screen; Samsung's panel stays reachable from any app by that flick and by `GLOBAL_ACTION_NOTIFICATIONS` / `GLOBAL_ACTION_QUICK_SETTINGS` (Q-04-3, Jeremy)
- 2026-10-09 09:03: Q-04-4, how the open panel meets the nav bar (queue item 8, R7 §4.1.10) (Jeremy: "(a)"): the open action center COVERS the nav bar, as W10M's did: the panel runs to the bottom edge with the handle at its foot. With 3-button navigation the buttons are under the open panel (the accessibility overlay takes taps in the nav-bar strip in both modes, R4 2026-09-29 P5, qa/r4/results-2026-09-29.md) and the panel is closed by pushing it up (drag or flick); with gesture navigation Android's home and back swipes still work over it (addendum A3: spy windows above every window; no app can exclude the bottom edge). Drawing over the nav glyphs is INFERRED from AOSP's layer order (addendum A1) and confirmed by a phone screenshot; if the phone shows the glyphs drawn on top after all, the panel stops just above the nav bar with the handle above it instead (the fallback told to him with the question). This supersedes the 2026-09-22 P5 reading below ("W10M's behaviour ... is not available at all"), which was measured on the APP overlay (Q-04-4, Jeremy)
- 2026-10-09 09:07: Q-04-5, the quick-action list and default (queue item 3) (Jeremy: "(a)"): W10M 14393's full list of 16 (addendum C1) with its default of 15 on, in this order, filled bottom row first (the collapsed row is the first four; an incomplete row sits at the top, packed left; addendum C2): Camera, Wi-Fi, Bluetooth, Rotation lock | All settings, Airplane mode, Cellular, Mobile hotspot | Quiet hours, Brightness, Connect, Battery saver | Flashlight, Note, Location; VPN off. What each does on the S25 Ultra (addendum C4, R4): Wi-Fi, Bluetooth, Cellular and Airplane mode flip through the helper (Q-04-2 when it is down); Flashlight (`CameraManager.setTorchMode`), Rotation lock and Brightness (with `WRITE_SETTINGS`, the "Modify system settings" grant) and Quiet hours (with Do Not Disturb access; targeting 35+ it switches the shell's own automatic rule and cannot turn off a Do Not Disturb set in Samsung's panel) are done by the shell; All settings, Camera, Note, Connect and VPN open something; Battery saver, Location and Mobile hotspot show their state where Android lets the shell read it and open their Settings page (R4: the helper did not change battery saver or location and was refused for hotspot). Every action can be rearranged or removed in the settings. The two new grants (`WRITE_SETTINGS`, Do Not Disturb access) become checklist rows. Cross-phase, for phase 19's own planning (DRAFT): its Q3 C adds helper verbs for battery saver, location and hotspot, which R4 found do not work through the helper on this phone (Q-04-5, Jeremy)
- 2026-10-09 09:12: Q-04-6, Tess's switch commands on a locked phone (queue item 10, review R3-m4) (Jeremy: "(a)"): while the keyguard is locked, "turn Wi-Fi / Bluetooth / mobile data / airplane mode on or off" all show phase 03's "Unlock to continue" card and the switch happens right after unlock, as texts and calls do. The new toggle requests are added to the built gate's exhaustive `when` as `false` (`cortana/action/LockGate.kt` 29-47, whose rule is "anything that opens an app or reads or writes personal data is gated"; a lost phone's link to Find My Mobile is the reason here) with a `restate` caption each. Consistent with Q-04-3 (no action center over the lock screen). Samsung's own "Lock network and security" behaviour was not relied on: reports conflict (Q-04-6, Jeremy)
- 2026-10-09 09:18: Q-04-7, hold Back on the shell's drawn nav bar (queue item 7, phase 01 Q4, review R2-m12) (Jeremy: "(a)"; he had asked on 2026-09-17 "was there not a button to show all apps currently running?"): holding the drawn Back key opens Android's recent-apps screen through this phase's accessibility service (`GLOBAL_ACTION_RECENTS` = 3, the saved copy `r4/src/a-windows/aosp16r4_AccessibilityService.java` 538), as W10M's press-and-hold Back opened its app switcher (R6 §4.1.6, MEDIUM); a short press stays Back. An ADD to phase 01's bar: the Back `NavSlot` gains a long-click (`bars/SystemBars.kt` 200; today only Search has one, 202), the hold being Android's long-press timeout as Search's is (phase 03's H20). Screens with Samsung's own nav bar keep its recents button (Q-04-7, Jeremy)
- 2026-10-09 09:24: Q-04-8, notification actions in the action center (queue item 5) (Jeremy: "(a)"): everything, as on W10M: a tap opens the notification's app (its `contentIntent`), a swipe dismisses it (R7 §4.4, Y9), and expanding one shows its app's buttons (`Notification.Action`, fired by phase 01's listener) and, where an action carries a `RemoteInput`, a reply box that sends the typed answer without opening the app (`RemoteInput.addResultsToIntent` + `PendingIntent.send`). W10M source: expanding a notification in the action center to act or reply without leaving the current app (onmsft.com/?p=14041 and gsmarena.com windows_10_for_phones-review-1208p2, preview-era hands-on; not in R3 / R7, so the expanded look is an approximation the build adds to Approximations with its own H-row). No action center over the lock screen (Q-04-3), so nothing is replied to from a locked phone. Sending another app's actions and replies is part of the trust surface round 4 reviews (Q-04-8, Jeremy)
- 2026-10-09 09:25: Q-04-9, media controls in the volume panel (queue item 9) (Jeremy: "(a)"): yes, as on W10M: while anything plays, the panel grows a section under the slider with previous, play / pause and next and the track title, for any app's playback through its media session (phase 01's listener: `MediaSessionManager.getActiveSessions` with the listener's component; phase 10's player is one such session). Motion R7 §4.5.6 (LOW, 14393): the lower edge extends ≈333 ms ease-out, 41 % of the travel in the first frame, the title fading in; the controls fade out when the second slider opens (§4.5.7). Both LOW, so an Approximations row with its own H-row; the section's geometry is not in A20 and is an approximation too (Q-04-9, Jeremy)
- 2026-10-09 09:30: Q-04-10, acrylic vs the captured fills (queue item 11; confirms phase 13 interview Q1 "(a)", 2026-09-23) (Jeremy: "(a)"): phase 13's rule as it stands. The action center keeps its captured (0,0,0) and stays SOLID (a black fill over a non-black app cannot satisfy phase 13's tint derivation T = (F − 0.2·B_m) / 0.8); the volume panel's captured (55,55,55) is acrylic through this phase's ADD to phase 13's engine (cross-window blur-behind for overlay windows) wherever `WindowManager.isCrossWindowBlurEnabled()` is true, and solid (55,55,55) otherwise. R4 (2026-09-29, qa/r4/results-2026-09-29.md "Blur"): under Power saving the flag was false and nothing blurred; normal mode is unmeasured, a phone check (Q-04-10, Jeremy)
- 2026-10-09: Queue item 1, R4 (agent): R4 ran on the S25 Ultra on 2026-09-29 and is recorded below ("R4 — results (2026-09-29) and the 2026-10-08 research"). Its one FAIL, 4d (the helper dies when USB and Wireless debugging are both off), is the re-ask the 2026-09-16 entry-gate line requires; Q-04-1 answered it, so the entry gate is closed. Of the 2026-09-16 trust line, R4 proved the uid half of the handoff — the spike's binder refuses every caller but the app's uid (`r4probe/src/main/kotlin/app/tessera/r4probe/HelperMain.kt:156-160`) and its door takes a binder only from uid 2000 or 0 (`HelperProvider.kt:21`) — and the named-verb form (`Toggles.kt`, looked up by name, `HelperMain.kt:182-199`). It never checked a signature: the signature half, a one-time start token and the shipped verb table are built in build task 2 and are the first items of the adversarial review (build task 14). The shipped allow-list is the four radios R4 changed through the helper, on and off (`Toggles.kt:48-57`), plus `ping` and `exit`: no read verb (the app reads the four states itself), nothing R4 found failing (location, battery saver, NFC, hotspot) and not automatic time (phase 19's) (agent)
- 2026-10-09: Queue item 6, the volume QA matrix and the window types (agent, from addendum A1, A2, A5): every window this phase adds — the top-edge strip, the action center panel and the volume panel — is a `TYPE_ACCESSIBILITY_OVERLAY` added through the shell's accessibility service: layer 31, above the nav bar (24), the volume overlay (22), the status bar (15) and the shade and keyguard (17 / 19) (A1), and a trusted overlay, never blocked or flagged as obscuring (A2); none needs "Display over other apps". The strip and the volume panel never take key focus; the open action center does, so Back reaches it (Y8). The matrix: device row A6 covers screen on and unlocked with nothing playing (Ringer + Notifications, `STREAM_RING`) and with music playing (Media + Apps, `STREAM_MUSIC`, with the media section), keys from the emulator's virtual device only (the 2026-09-17 line; A5.6). The JVM test `VolumeRules` covers a call (`STREAM_VOICE_CALL`, never to 0: MODIFY_PHONE_STATE, A5.5), a ringer change into or out of silent without Do Not Disturb access (refused, A5.5), key repeat and the clamps. Ringing and screen off are covered by neither: the service never sees those keys (A5.2–A5.3), so Android's own behaviour stands, and phone row P7 presses real keys in both. With the screen on over the lock screen the service does get the keys, and the panel shows there (the next line) (agent)
- 2026-10-09: The volume panel over the lock screen, and the two surfaces' fills (agent): Q-04-3 keeps the action center off the lock screen; the volume panel is not the action center and holds no notification text and no switch, W10M showed it over its lock screen (UNVERIFIED: no source; round 4 D12 c), and Android's own panel and lock-screen media controls show there, so it shows over the keyguard~~, media section included~~ (round 4 D12 c: its media controls show there; the track title does not while the phone is locked with a PIN, pattern or password, Q-04-13). Its acrylic tint is phase 13's derivation (`ui/fluent/Fluent.kt:52-63`) over A20's backdrop, "the app behind stays black" (w10m-measurements.md A20): T = (55 − 0.2·0) / 0.8 → (69,69,69), which over black reads A20's (55,55,55). The action center's (0,0,0) gives no tint over any non-black backdrop and stays solid (Q-04-10). Both surfaces are added to phase 13's surface table (`FluentSurface`, `Fluent.kt:93-100`), the action center as a listed solid surface (phase 13: "the build lists every such surface") (agent)
- 2026-10-09: Queue item 12, the "All settings" target (agent): it opens the shell's own Settings (`settings/SettingsActivity.kt`, its home page), now and after phase 19. Phase 19 adds no second Settings: its 2026-09-22 Decision "the front IS the hub's W10M top level" rebuilds this same activity's home page into W10M's front, so the quick action's target never changes and nothing here is an interim (Rule 16). Android's `Settings.ACTION_SETTINGS` (addendum C4's "until then") would be exactly the interim Rule 16 forbids. Device row A8 asserts the resumed activity (agent)
- 2026-10-09: How the rows see the overlays (agent; AOSP android-16.0.0_r4, read 2026-10-09, not saved under r4/src): `uiautomator dump` connects a UiAutomation with no flags (`cmds/uiautomator/.../DumpCommand.java:77-78` → `UiAutomationShellWrapper.connect` → `UiAutomation.connect()` = `connectWithTimeout(0, …)`, `UiAutomation.java:333-335`), and while a UiAutomation without `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` is connected the system unbinds every enabled accessibility service (`UiAutomationManager.java:171-175`; `AccessibilityManagerService.java:3100-3101`, `:3122-3124`): the shell's overlays vanish for the length of a dump. So the rows read the overlays through the service's own `dump()` (`adb shell dumpsys activity service app.tileshell/.ac.ShellAccessibilityService`: each tagged overlay node's bounds, text and state, DEBUG builds only, then the diagnostics ring) and through screencaps, and use `uiautomator dump` only where no overlay must stay up. And `am force-stop app.tileshell` removes the shell's service from `enabled_accessibility_services` (`AccessibilityManagerService.onPackagesForceStoppedLocked`, `:1050-1079`), so after every C-6 force-stop (and every `pm clear`) a row turns it back on (`p04.sh a11y_on`) and waits for `[ac] service connected`. A crash, unlike a force stop, is rebound by the system (`AccessibilityServiceConnection.java:304-316`) (agent)
- 2026-10-09: The setup rows (agent; phase 12 T12-5 / C-4; Q-04-5): three grant rows, so three wizard steps — `accessibility` (the shell's accessibility service), ~~`modify_system_settings`~~ `write_settings` (phase 19's id; round 4 D10, the "Step ids" line below) (`WRITE_SETTINGS`) and `dnd_access` (Do Not Disturb access) — each with its why line, its `provision.sh` line and phase 12's E14 (device row A1). The helper's three rows — `helper`, `usb_debugging`, `wireless_debugging` — are observation rows (`grant = false`, like phase 01's `listener`): pairing is a several-step Developer-options walk that lives on the helper's page, `pm clear` drops the app's adb key so `provision.sh` cannot carry a pairing, and a core row that `provision.sh` cannot hold would turn every later phase's `[wizard] not shown: core held` into `finished` (`WizardRules.visibility`, `onboarding/SetupWizard.kt:80-81`). No overlay row is added and none is needed (every window is an accessibility overlay, the item 6 line); phase 15's `overlay` row (`onboarding/Checklist.kt:174-176`) stays phase 15's (agent). Round 4 D10: phase 12's text has the helper's pairing and the helper as wizard steps; Q-04-11 (2026-10-09 12:11) keeps them observation rows
- 2026-10-09: The trusted list, and what the shell writes (agent, Q-04-1): Android keeps the "Always allow on this network" access points in its own key store (`AdbKeyStore.java:114`, `:320`) and gives apps no read of it, but the shell uid has one: `dumpsys adb` prints the key-store file, whose `<wifiAP bssid=…>` entries are that list (`AdbService.java:534-566`, `AdbDebuggingManager.java:1543-1547`, `AdbAuthorizationStore.java:63-64`; One UI's form UNVERIFIED, build task 1 records the AVD's). The app reads it over its own adb session at every helper start and keeps the BSSIDs only; it writes `adb_wifi_enabled=1` by itself only when the current BSSID is in that list and the setting reads 0. The BSSID needs precise location granted and Location on (a `NetworkCallback` with `FLAG_INCLUDE_LOCATION_INFO`, as AOSP's own monitor registers, `AdbWifiNetworkMonitor.java:66`); without it the BSSID is unknown and nothing is written by itself — the next tap is the one tap. A tap on a helper tile writes it on any network, and Android's dialog is then Q-04-2's one tap. The shell never writes `adb_wifi_enabled=0`, `adb_enabled` or `enabled_accessibility_services` (agent)
- 2026-10-09: Device-dependent quick actions (agent, addendum C2: Flashlight is missing from the picker on a Lumia with no flash): Flashlight is offered only where a camera reports a flash unit (`CameraCharacteristics.FLASH_INFO_AVAILABLE`), and Connect only where `Settings.ACTION_CAST_SETTINGS` resolves; an action not offered is absent from the add / remove list and the grid, and the default is Q-04-5's 15 less those. The S25 Ultra offers all 16 (UNVERIFIED for Connect, phone row P2); build task 1 records the AVD's (agent)
- 2026-10-09: Device rows pull slowly (agent; refines the 2026-09-17 input-paths line by addendum A4): AOSP hands a swipe that starts in the top 24 dp and travels 24 dp within 500 ms to the status bar (`DisplayPolicy.java:2422-2439`), so a row's pull from the strip spends more than 500 ms on its first 24 dp (each `input motionevent MOVE` its own adb call); the fast flick is the phone's check (P6) (agent)
- 2026-10-09: Q-04-1's reading, corrected (agent, round 4 D12 a; his answer "(b)" unchanged, his line above untouched): the lead's reading inside Q-04-1's line says "`WRITE_SECURE_SETTINGS` granted through the helper" and "never on an untrusted one". Read instead: `WRITE_SECURE_SETTINGS` is granted over the app's own adb session (`pm grant`, one of `AdbSessionCommands`, build task 2; `HelperVerbs` has no `pm`); and the shell writes `adb_wifi_enabled=1` by itself only on a trusted access point, while a tap or a Tess request may write it on any network, and Android then shows its dialog (Q-04-2's one tap). A Tess request counts as a tap (Q-04-2's "the same rule"; round 4 V15). Read this way, Q-04-1 and Q-04-2 agree (agent)
- 2026-10-09: The helper's door, after round 4 (agent, round 4 D3): the start token is not secret — it rides the start line, which any uid-2000 process can read (`ps -A -o ARGS`, `/proc/<pid>/cmdline`) — so the token stops only a planter that does not look; the pid pin is the real door. The first handoff carrying the current token after the app's own start records the caller's pid (`Binder.getCallingPid()`), and every later handoff must come from that pid with that token (`HandoffRule.accept`). The token, the helper's versionCode and the pinned pid are kept in app-private prefs, so a new app process checks them (A4 a); a start writes a new token and clears the pin. A refused handoff's reply tells the caller to exit, so a helper orphaned by a `pm clear` (its token gone with the prefs) exits at its next handoff. The start is a shell line, not an argv: Kadb's `shell` sends one string (the spike's `AdbSelf.kt:59-62`), adbd runs it under `sh -c` (saved `adb-daemon_shell_service.cpp:393`), and daemonising needs shell syntax (the spike's `Runner.kt:346`); so the rule is `StartCommand.line`, its test pinning the exact string (build task 2) (agent)
- 2026-10-09: The restart path, after round 4 (agent, round 4 D4, V8): (a) the keeper also runs when the phone joins a Wi-Fi network (a `NetworkCallback` with location info, registered for the launcher process's life): Q-04-1's reading ("whenever the phone joins an access point in the trusted list") needs that trigger, and `RestartPlan.next` already lists it; a join on a trusted BSSID with `adb_wifi_enabled` at 0 writes 1 whether or not the helper runs. (b) At every helper start it grants `WRITE_SECURE_SETTINGS` when that is not held and writes `adb_allowed_connection_time=0` when it reads non-zero, because turning Developer options off writes the 7-day default back (saved `Settings-AdbAuthorizationTimeoutPreferenceController.java:64-73`); "the first time" was not enough. (c) On an update the new helper starts first and the old one is told to exit only after the new one's ping answers; until then the old one serves (after an update away from a trusted Wi-Fi nothing could start a new one, and USB debugging keeps the old one alive). (d) A tap or a Tess request while the helper answers its ping goes straight to the verb, and a home start then does nothing; neither writes a setting, so Wireless debugging turned off by hand stays off while the helper runs (A4 b). (e) The first start after a boot is tagged `(boot)` whatever triggered it: the home activity starts before BOOT_COMPLETED (A4 c) (agent)
- 2026-10-09: What closes the panel, after round 4 (agent, round 4 D5, D6, D9): (a) Android's "Allow wireless debugging on this network?" is an activity (`startConfirmationForNetwork` → `startConfirmationActivity` → `startActivityAsUser`, saved `AdbDebuggingManager.java:1242-1255`, `:1302-1312`); under the open panel (layer 31) or Tess's pane it would open unseen while the keeper's 30-s mDNS wait runs out. So when a helper-tile tap's start will write `adb_wifi_enabled=1` on a BSSID that is not trusted or is unknown, the panel first closes with the tap wipe (Y8) and the tile reads "Starting…" (`HelperTileRule.onTap`); a Tess request in that case closes her pane the same way. (b) Every `PendingIntent.send` from the panel (content, action, reply) passes task 1 (f)'s `ActivityOptions`: the service binding's `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` (saved `AccessibilityServiceConnection.java:190-193`) reaches a send only with the sender's opt-in (the API 34+ rule Reviewer 1 cites; not in the saved sources, so UNVERIFIED here, and task 1 (f) checks it on the AVD). When `PendingIntent.isActivity()` is true the panel closes with the tap wipe (`[ac] closed by action`), since an activity started under the layer-31 panel would be invisible; otherwise it stays. (c) While fully open, a cancel the panel did not cause (a home or recents gesture over it) or a window-state event for another package's activity closes it by Y8's Back sequence (`[ac] closed by system`); input-method windows and the shell's own overlays are ignored, because the reply box raises a keyboard that may be another package's (agent pick). Without (c) a gesture-navigation Home left the open panel over Home (agent)
- 2026-10-09: Volume keys in a camera app (agent, round 4 D8): the key filter runs before dispatch and a consumed key reaches no app (saved `InputDispatcher.cpp:4494-4499`; addendum A5.1), so consuming every volume key would break a camera's volume-key shutter (Samsung Camera's by default, UNVERIFIED) and any app that maps the keys. The keys pass at once while the last window-state event's package has an activity for `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`, the shell's own package excepted: its `CameraActivity` takes that intent (`AndroidManifest.xml:703-720`) but no volume key, and Start shares the package (`KeyFilter.consume`). Android's own panel shows there. P7 checks the photo (agent)
- 2026-10-09: Step ids (agent, round 4 D10): the "Modify system settings" row and step take phase 19's id `write_settings` (`setup:write_settings`, phase-19 lines 69 and 212), not `modify_system_settings`. This phase builds both of phase 19's rows (`write_settings`, `dnd_access`) with its own why lines, and phase 19 rewrites them for its pages (Cross-phase "Doc edits recorded with this phase"). The wizard does not walk the helper's pairing or the helper (phase 12's text; Q-04-11) (agent)
- 2026-10-09: Precise location for the BSSID (agent, round 4 D12 b): it is not a new permission. The manifest already declares `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` (`AndroidManifest.xml:84-85`), and Tess's `background_location` step asks for both (`cortana/CortanaChecklist.kt:68-77`); the Setup `location` row asks for coarse only (`onboarding/Checklist.kt:138-141`). Without precise location, or with Location off, the BSSID is unknown and `HelperState` reads "Location off", its own reason, so an unknown BSSID never reads as an untrusted network (Y16) (agent)
- 2026-10-09: Two picks behind the rows' literal expectations (agent, round 4 V7): a quick action switched on in "Add or remove quick actions" joins the end of the order (`QuickPrefs`; W10M's own placement unrecorded, H23); the action center's second line reads the battery as `<n>%` and the date as `EEEE, MMMM d` in the device's locale (A19 gives its place, not its text; H5) (agent)
- 2026-10-09 12:11: Q-04-11, whether the setup wizard walks the helper's pairing (round 4 D10: phase 12 lines 44-46, 133 and 316-320 expected the helper's pairing and the helper as wizard steps; the rewrite made them observation rows) (Jeremy: "(a)"): the wizard walks this phase's three permission steps only (`accessibility`, `write_settings`, `dnd_access`). Pairing lives on the helper's own Settings page. The checklist's `helper`, `usb_debugging` and `wireless_debugging` rows show the helper's state and are no wizard step (Decisions "The setup rows"). A tap on Wi-Fi, Bluetooth, Cellular or Airplane mode while the helper is not set up opens the helper's page, the tile reading "Not set up" (Y16). Asked of him with this choice: the wizard shows only while a step is missing and it has never been finished (`onboarding/SetupWizard.kt:80-84`), and the test setup cannot keep a pairing (`pm clear` drops the app's adb key). What the agent reads into it, recorded so the build does not guess: "not set up" means never paired; a pairing that expired or was revoked is Q-04-2's case and still opens Android's control, so `HelperState` keeps "pairing gone" apart from "not paired" and the tile reads "Pair again" (Y16); a tile tap while not set up wins over no Wi-Fi (setting up comes first); Q-04-2's "the same rule" carries this to Tess, whose switch request while the helper is not set up opens the helper's page (Y16's line for it is an agent pick); phase 12's lines become an INDEX Change Log line when built (Cross-phase "Doc edits"). Proved by device row A3 (a0), P1 and `HelperTileRule.onTap` / `RestartPlan.next` (Q-04-11, Jeremy)
- 2026-10-09 12:18: Q-04-12, the phone screen-recording row for the motion timings (round 4 D11; PLAN RV11, `PLAN.md:323-329`, an agent call from the 2026-09-16 review: "Timings whose tolerance is ≤ 17 ms also get a phone P-row from a faster capture") (Jeremy: "(a)"): no recording in this phase. RV11 is set aside for phase 04 and an INDEX Change Log line records it. The four ±17 ms timings (the 290-ms settle, the 216-ms tap-close wipe, the 217-ms volume show, the 134-ms slide-away; R7 §4.1, §4.2, §4.5) stay constants in pure rules with their JVM tests (`SettleCurve`, `CloseRules`, `VolumeMotion`), and he judges the feel on the phone (H1, H8–H18). No shell-clock motion row is added (the other choice he was offered, C, on phase 12 E10's 2026-09-29 precedent). Told to him: phase 01's P10 (the same kind of row) has no result in `qa/phase-01/NEEDS-HUMAN.md` or INDEX (searched both), and a 60-fps recording's frame is about as wide as the tolerance (Q-04-12, Jeremy)
- 2026-10-09 12:28: Q-04-13, the track title in the volume panel's media section over a secure lock screen (round 4 D12 c) (Jeremy: "(b)"): while the phone is locked with a PIN, pattern or password the media section keeps previous, play / pause and next and leaves out the track title; the title shows once he unlocks, and with a swipe lock or none. The section shows no app name (Q-04-9), so nothing else is hidden. This keeps Q-04-3's rule that nothing the shell shows (notification text, the switches) is reachable without unlocking. Facts put to him: Android 16's own lock-screen media player is on by default (`Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN`, default true, AOSP 16 QPR2 SystemUI `media/remedia/data/repository/MediaPipelineRepository.kt:161-169`; nothing hiding its title on a secure lock found in that file, `MediaCarouselInteractor.kt` or `KeyguardMediaController.kt`); apps cannot read that setting (no `@Readable`, saved `aosp-a16qpr2-Settings.java:12915-12920`); One UI's lock screen unchecked; W10M's lock-screen title has no source (UNVERIFIED). The agent's picks: "locked" is `KeyguardManager.isDeviceLocked()` (a trust agent that keeps the phone unlocked counts as unlocked), and the title's slot stays so the panel does not move on unlock. Proved by device row A6 (f), P7 and `VolumeRules` (Q-04-13, Jeremy)
- 2026-10-09 12:30: Q-04-14, is the plan final (Jeremy: "(a)"): FINAL. Shown first: every Stage A step with when it ran, and that round 4's four BLOCKING findings were applied but not re-reviewed (Q-04-0 made round 4 the last). Build prompt: `docs/plan/prompts/session-phase-04.md` (Q-04-14, Jeremy)

## R4 — results (2026-09-29) and the 2026-10-08 research
R4 ran on the S25 Ultra alone (One UI 8, Android 16 / API 36, T-Mobile; probe build 05802644). Evidence: qa/r4/results-2026-09-29.md
(every line read from the full report, which is kept locally and not committed — the repository is public) and the run sheet
qa/r4/README.md; the research that followed: r4/phase04-addendum-2026-10-08.md (A windows and input, B the helper's life, C quick
actions). The 2026-09-21 interim text this replaces (the licence table, the first loopback note, the APP-overlay P5 reading,
"parts still to run") is in git history (commit d29273259 and earlier).

- **Passed:** pairing with the phone's own Wireless debugging (the code typed into the probe's notification) and adb as the shell
  uid; 1 `app_process` as uid 2000 (`u:r:shell:s0`); 2a daemonised (parent = init); 2b the binder handed to the app and its ping
  answered; 4a the app killed — the helper lived and handed the binder again (up 213 s); every toggle put back (clean-up PASS). The
  AVD ran the same probe end to end before (commit 5c940ed4c: paired, connected, `id` = uid 2000; 09eaff939: 1, 2a, 2b, 4a PASS, adb
  back 9 s after each Wi-Fi / airplane flip), so pairing and the helper can run on emulator-5554 (device rows A3, A4).
- **4d FAILED, and why (addendum B1):** with Wireless AND USB debugging off the helper died with adbd (its binder:
  DeadObjectException). adbd is an init service; AdbService stops it only when both switches are off (`AdbService.java` 392-396), and
  init then SIGKILLs adbd's whole cgroup (`init-service.cpp` 998, 261-272; `processgroup.cpp` 416-430); `setsid` / `nohup` do not
  leave it and uid 2000 cannot move cgroups. A hard platform rule — Q-04-1 keeps USB debugging on.
- **4b (Doze) not tested** (Power saving was on: quick Doze). B4: Doze and standby apply to app uids, not uid 2000, and Doze does not
  restrict binder IPC; Samsung's auto-restart (a reboot) is the real killer, which the restart path covers.
- **The toggles through the helper:** Wi-Fi, Bluetooth, mobile data and airplane mode changed — the four this phase ships; automatic
  time changed (phase 19's verb); location did not change through the helper although the same command did over adb (cause
  unknown, B6); battery saver and NFC did not change at all; the hotspot was refused (SecurityException for uid 2000). The app alone
  can flip none of the four radios (B7), so they are the helper's whole reason.
- **P5 with the ACCESSIBILITY overlay:** it reaches the bottom edge, with or without insets fitted, and a tap in the nav bar's strip
  reached it, in 3-button (bar 144 px) and gesture (45 px) modes. The taps were injected (`input tap`, `Runner.kt:567`); whether the
  nav glyphs were drawn over it was not recorded — INFERRED hidden from AOSP's layer order (A1), confirmed by phone row P5. The
  2026-09-22 reading that the glyphs stay on top was the APP overlay (layer 11, below the nav bar), superseded by Q-04-4.
- **Blur:** under Power saving `isCrossWindowBlurEnabled` was false and the probe was not blurred; normal mode was never measured (the
  run could not turn Power saving off) — phone row P8. The probe used an APP overlay (`Runner.kt:505-511`); an accessibility
  overlay's blur is build task 1 (d) on the AVD.
- **Licence (part 5) PASSED, 2026-09-21:** "GPL = not usable" does not bite — adb-kt, dadb and Kadb are Apache-2.0, adblib is
  BSD-3-Clause, libadb-android is GPL-3.0-or-later OR Apache-2.0. The spike and this phase use Kadb 2.1.1 (Apache-2.0,
  `r4probe/build.gradle.kts`; 2.1.2+ need compileSdk 37) with its SPAKE2 dependency from JitPack (`settings.gradle.kts:13-15`); the
  licences of Kadb's own dependencies are build task 1 (j).
- **The loopback-ADB risk, re-checked 2026-10-08 (B5):** nothing has shipped. Google IssueTracker #526109803 (bind adbd to wlan0
  only) is Assigned, P4, "No update yet", last changed 2026-09-30; #541312863 is Assigned, P2. adbd in the android16 and android17
  security branches still binds INADDR_ANY. Android 17 reads `adb_enabled` as 0 for third-party apps (never gate on it). If the
  restriction ships, the helper cannot start as built — R4's "re-ask Jeremy" condition (Q13), not a silent change. The shell connects
  to the address mDNS resolves for the phone itself (as `Discovery.kt:55-87` did on the phone), not to 127.0.0.1; whether that
  survives a wlan0-only bind is INFERRED, not tested.
- **PQ2 (observe only, INDEX Change Log 2026-09-28):** the carrier's visual voicemail app holds the voicemail; Android's voicemail
  provider has no rows; the shell uid holds READ_VOICEMAIL / ADD_VOICEMAIL. Phase 06's own sign-in probe decides its voicemail; nothing
  of it is this phase's.
- **Left for the phone (the P-rows):** the nav glyphs over the open panel (P5); blur with Power saving off (P8); One UI's fast vs
  slow top pull (P6); real volume keys incl. ringing, screen off and a call (P7); Bluetooth and the restart timing after a real
  reboot (P2, P3); survival with Wireless debugging off and USB debugging on (P4); turning the service on for an APK installed from
  the browser (P1).

## Interview queue (Stage A step 4) — every item ruled
1. [agent] Run R4 on the S25 Ultra first; record the result here — DONE 2026-10-09 (agent): the R4 section above; Decisions
   "Queue item 1".
2. [Jeremy] Action center everywhere (top-edge strip over all apps) or only on Start (M5) — RULED 2026-10-09 09:00, Q-04-3 (every
   app and Start, not the lock screen).
3. [Jeremy] Quick-actions list and default order (per-toggle matrix from R4) — RULED 2026-10-09 09:07, Q-04-5 (W10M 14393's 16, 15
   on in its order).
4. [Jeremy] What the Wi-Fi tile does when the helper isn't running (R3F-01) — RULED 2026-10-09 08:38, Q-04-2 (start it, else open
   Android's control and say why; Tess the same).
5. [Jeremy] Notification actions in the action center (inline reply or tap-through only) — RULED 2026-10-09 09:24, Q-04-8 (tap,
   swipe, the app's buttons and inline reply).
6. [agent] Volume QA matrix (R2-13); overlay window types — DONE 2026-10-09 (agent): Decisions "Queue item 6".
7. [Jeremy] Long-press Back on the drawn bar (phase 01 Q4; R6 §4.1.6; his 2026-09-17 question) — RULED 2026-10-09 09:18, Q-04-7
   (opens Android's recents).
8. [agent → Jeremy] The nav-bar probe (P5) and how the open panel meets the nav bar (R7 §4.1.10) — the probe recorded 2026-09-29
   (R4 section); RULED 2026-10-09 09:03, Q-04-4 (covers it; fallback above it if the phone draws the glyphs on top).
9. [Jeremy] Media transport controls in the volume panel (R7 §4.5.6) — RULED 2026-10-09 09:25, Q-04-9 (yes, any app's playback).
10. [Jeremy] Toggle commands on a locked phone (review R3-m4) — RULED 2026-10-09 09:12, Q-04-6 (Unlock to continue).
11. [Jeremy] Acrylic behind the action center vs the captured black (phase 13 Q1) — RULED 2026-10-09 09:30, Q-04-10 (phase 13's
    rule: action center solid black; volume panel acrylic where cross-window blur is allowed).
12. [agent] "All settings" target (from phase 19's writer) — DONE 2026-10-09 (agent): Decisions "Queue item 12".

## Build tasks
Rewritten 2026-10-09 against the code as built (the 2026-09-16 eight-line list is in git history). Every task that owns a pure rule
names it and says "JVM-tested": the build session writes that test WITH the task, with the cases in Acceptance's unit-test table
(the names here, in Decisions and in that table are the same names). Every change to an older phase's part is an ADD, recorded as an
INDEX Change Log line when built. Order of work under the owner's rule (2026-10-07): build all fourteen, push, then ONE round of
testing (Acceptance).

1. **Build-start checks — only what needs the device** (each result recorded in Decisions; one that contradicts a Decision is a
   question for the lead, not a quiet edit). On emulator-5554: (a) `adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_SYN:0:0` (then `:0`)
   reaches the service's `onKeyEvent` (a `[volume] key` line) — if not, stop: device row A6 has no other key path; (b) the stock
   volume panel's window title in `dumpsys window windows`, shown with `input keyevent KEYCODE_VOLUME_UP` (which skips the service,
   A5.6) — A6 asserts its absence; (c) the AVD's facts the rows use: whether its Bluetooth adapter flips through
   `cmd bluetooth_manager enable|disable` as uid 2000, whether a camera reports a flash unit, whether `Settings.ACTION_CAST_SETTINGS`
   resolves, which activity `GLOBAL_ACTION_RECENTS` resumes, which activity `ACTION_LOCATION_SOURCE_SETTINGS` resumes, what each of
   the four helper fallbacks shows (`Settings.Panel.ACTION_WIFI`, `Settings.Panel.ACTION_INTERNET_CONNECTIVITY`,
   `ACTION_BLUETOOTH_SETTINGS`, `ACTION_AIRPLANE_MODE_SETTINGS`): the resumed activity, or the window title in `wins` for a dialog
   (the Internet panel is a SystemUI dialog since Android 12 by Reviewer 2's memory of AOSP, not saved: UNVERIFIED) — A4 (d) and
   A7 (c) assert that identity (round 4 V9) — and that the app's own radio reads (`WifiManager.isWifiEnabled` with
   `ACCESS_WIFI_STATE`, `BluetoothAdapter.isEnabled`,
   `TelephonyManager.isDataEnabled` with the held `ACCESS_NETWORK_STATE`, `Settings.Global.AIRPLANE_MODE_ON`) return the true states;
   (d) cross-window blur behind a `TYPE_ACCESSIBILITY_OVERLAY` window (R4 probed only an app overlay) with the AVD's
   `supports_background_blur=1` (phase 13) — if an accessibility overlay cannot blur, stop: a question for the lead (Q-04-10);
   (e) `dumpsys activity service app.tileshell/.ac.ShellAccessibilityService` reaches the service's `dump()`; (f) a `PendingIntent.send`
   from the service with the sender's background-start opt-in (`ActivityOptions.setPendingIntentBackgroundActivityStartMode`; the
   API 36 mode constant recorded — UNVERIFIED which) opens qa-notify's activity while another app is in front (the service binding
   carries `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`, `AccessibilityServiceConnection.java:190-193`, saved copy); (g) after "Always allow"
   on AndroidWifi, `dumpsys adb` lists its BSSID as a `<wifiAP bssid=…>` entry and the app's own BSSID read equals it; (h) with the
   reply box focused in the open panel, whether the keyboard is drawn above or below the panel — AOSP layers the input method at 13,
   under 31 (A1), so the expectation is below, and task 7 shrinks the panel window above the keyboard's insets while the box has focus;
   (i) Quiet hours' mechanism at API 36 (the shell's own `AutomaticZenRule`, switched with `setAutomaticZenRuleState` and read back);
   (j) the licences of Kadb 2.1.1's dependency tree (spake2-java from JitPack and the rest), added to the About page's licence files;
   (k) `cmd notification post` posts as `com.android.shell` (A5's second group); (l) the strip over a punch-hole cutout (the AVD has
   none of its own, so no row would see a strip pushed below one; round 4 D7): `cmd overlay enable
   com.android.internal.display.cutout.emulation.hole`, then `dumpsys window windows` shows `TesseraStrip` starting at y = 0; then
   `cmd overlay disable` the same (that the AVD's image carries this overlay is UNVERIFIED: `cmd overlay list` first, recorded).
   Left to the rows and the phone: everything One UI does (P1–P8).
2. **The helper (trust; R4's spike becomes the shipped helper).** New package `app/src/main/kotlin/app/tileshell/helper/`:
   - `HelperMain.kt` — the `app_process` entry shipped in the APK (started by `StartCommand.line`, below; process name
     `tessera_helper`), taken over from `r4probe/.../HelperMain.kt` (the daemon loop
     73-90, the delivery through `IActivityManager.getContentProviderExternal` + `IContentProvider.call` 93-112 and 114-119, the
     binder 150-204, the bounded command runner 128-138). Changed from the spike: the target is `app.tileshell` and the authority
     `app.tileshell.helper`; the target's uid comes from the package manager, not `pidof` + `/proc` (`HelperMain.kt:121-125`); the
     loop still finds the app's main process every 2 s, now with `ProcessBuilder(listOf("/system/bin/pidof", "app.tileshell"))`
     (argv, no shell; the spike's `appPid`, `HelperMain.kt:121`, went through `sh -c`, `:129`) and delivers when the pid is new
     (round 4 D2); at start
     the helper pins the signing certificate of the package whose `sourceDir` is its own CLASSPATH, and before every handoff it
     re-reads the package's uid and certificate and delivers only on a match (`HelperDoorRule.client`), exiting when the package is
     gone or re-signed, or when the door's reply to a handoff says exit (a refused handoff, round 4 D3); the binder's
     `onTransact` refuses every caller uid but the shell's (`CallerRule.allowed`); the verbs come from `HelperVerbs` only and run as
     fixed argv lists through `ProcessBuilder(list)` — no `sh -c` (the spike ran strings through `sh -c`, `HelperMain.kt:129`), no
     caller text in any argument; `ping` returns uid, pid, versionCode, uptime and the start token; `exit`; refused calls go to
     logcat (tag `TesseraHelper`); no pid file or log under `/data/local/tmp`.
   - `HelperVerbs.kt` — the allow-list: `wifi_on|off`, `bluetooth_on|off`, `mobile_data_on|off`, `airplane_on|off` (argv from
     `r4probe/.../Toggles.kt:48-57`), `ping`, `exit`. Nothing else: no read verbs, no `settings`, `pm`, `am`, `content` or `sh`.
   - `HelperDoor.kt` — the exported provider (authority `app.tileshell.helper`; from `r4probe/.../HelperProvider.kt:17-28`): takes
     `helper_binder` only from uid 2000 carrying the current start token and, once the first such handoff since the app's own start
     has recorded its caller's pid (`Binder.getCallingPid()`), only from that pid (`HandoffRule.accept` — uid 0 is refused too: the
     shipped helper never runs as root), then pings the binder and checks the token in the reply; a refused handoff's reply tells the
     caller to exit. The token, the helper's versionCode and the pinned pid live in app-private prefs, so a new app process checks
     them (A4 a); a start writes a new token and clears the pin. Said plainly (round 4 D3): the token rides the start line, which
     any uid-2000 process can read (`ps -A -o ARGS`, `/proc/<pid>/cmdline`), so it stops only a planter that does not look; the pid
     pin is the real door. `HelperLink` (from `HelperProvider.kt:39-95`) holds the binder, the helper's pid and version, and a
     `linkToDeath`.
   - `AdbSession.kt` + `Discovery.kt` — the app's own adb client (Kadb 2.1.1 + coroutines added to `app/build.gradle.kts`, as
     `r4probe/build.gradle.kts`), taken over from `r4probe/.../AdbSelf.kt:18-67` and `Discovery.kt:16-122` (mDNS
     `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp`, only the phone's own addresses). It runs exactly three fixed commands
     (`AdbSessionCommands`): the start line, `pm grant app.tileshell android.permission.WRITE_SECURE_SETTINGS`, and `dumpsys adb`
     (the trusted list, task 3). The start is a shell line, not an argv (Kadb's `shell` sends one string, `AdbSelf.kt:59-62`, that
     adbd runs under `sh -c`, saved `adb-daemon_shell_service.cpp:393`; daemonising needs shell syntax, as the spike's
     `Runner.kt:346`): `StartCommand.line(apk, token, vc)` produces exactly `CLASSPATH='<apk>' setsid nohup app_process /system/bin
     --nice-name=tessera_helper app.tileshell.helper.HelperMain --daemon '<token>' '<vc>' >/dev/null 2>&1 </dev/null &` (round 4
     D3; `--nice-name` as Shizuku's starter, saved `shizuku-v13.6.0-ServiceStarter.java:45`). The key stays in app-private files
     (`allowBackup="false"`, `AndroidManifest.xml:111`).
   - `PairingReceiver.kt` — non-exported; the code typed into the shell's pairing notification (RemoteInput, as
     `r4probe/.../PairReceiver.kt:14-34`); the spike's DUMP-guarded `PairTestReceiver` is not shipped (QA types into the notification).
   - Manifest ADDs: `WRITE_SECURE_SETTINGS` (declared, so `pm grant` can grant it; B7), the door provider, the receiver. The exported
     allow-list (`qa/phase-03/exported-allowlist.txt`) gains the door ("guard: none by design — uid 2000 + the start token + the
     pinned pid").
   JVM-tested: `HelperVerbs`, `HandoffRule.accept`, `HelperDoorRule.client`, `CallerRule.allowed`, `StartCommand.line`,
   `HelperVersion.mustRestart`, `PairingCode.parse`, `AdbSessionCommands`.
3. **The restart path and the helper's state** (Q-04-1, addendum B8, Decisions "The trusted list"). `helper/HelperKeeper.kt`: runs
   on a tile tap, on Tess's request, on BOOT_COMPLETED and MY_PACKAGE_REPLACED (a new non-exported receiver, as phase 15's
   `ReminderReceiver`, `AndroidManifest.xml:365-376`), on the helper binder's death, when the home activity starts, and on joining a
   Wi-Fi network (a `NetworkCallback` with location info, registered for the launcher process's life; round 4 D4 a). A tap or a Tess
   request while the helper answers its ping goes straight to the verb, and a home start then does nothing; neither writes a
   setting (V8: Wireless debugging turned off by hand stays off while the helper runs); a Wi-Fi join on a trusted BSSID writes 1 from 0 whether or not the helper runs. In B8's
   order: wait for Wi-Fi; if `adb_wifi_enabled` is 0 and the BSSID is trusted (or the user tapped or asked Tess), write 1 — only from
   0 (a write while on restarts the server, `HelperMain.kt:171-176`); watch mDNS for the phone's own connect service for up to 30 s,
   writing once more if Android flipped it back; connect; refresh the trusted list (`dumpsys adb`); at every helper start, grant
   `WRITE_SECURE_SETTINGS` when it is not held and write `adb_allowed_connection_time=0` when it reads non-zero (turning Developer
   options off writes the 7-day default back, saved `Settings-AdbAuthorizationTimeoutPreferenceController.java:64-73`; D4 b); start
   the helper; ping it. When a running helper's version differs from the app's, the new helper starts first and the old one is
   told to exit only after the new one's ping answers; until then the old one serves (D4 c). It never writes `adb_wifi_enabled=0`,
   `adb_enabled` or the accessibility list. `[helper] started pid=<p> uid=2000 (<trigger>)`, the trigger `boot` for the first
   start after a boot whatever set it off (the home activity starts before BOOT_COMPLETED; V8). `HelperState`
   (not paired; pairing gone — paired once, the key now refused (Q-04-11 keeps the two apart); no Wi-Fi, location off — the BSSID unknown, round 4 D12 b —, debugging off, untrusted network, starting, running,
   dead with a reason) feeds the tile line (Y16), the helper page (Y4) and the checklist. JVM-tested: `TrustedNetworks.parse`,
   `TrustedNetworks.shouldEnable`, `RestartPlan.next`, `HelperState.reason`.
4. **The accessibility service and the overlay host.** New package `app/src/main/kotlin/app/tileshell/ac/`:
   `ShellAccessibilityService.kt` (manifest: `BIND_ACCESSIBILITY_SERVICE`; its XML asks for key filtering
   (`canRequestFilterKeyEvents`, `flagRequestFilterKeyEvents`) and window-state events only — no window content, no gestures, no
   touch exploration; in the launcher's process, so it shares the diagnostics ring, `Fluent.state` and `MusicFeed`), and
   `OverlayHost.kt`: on connect it adds the strip (top edge, full width, Y17, never focusable); it removes the strip at
   `ACTION_SCREEN_OFF` and whenever `KeyguardManager.isKeyguardLocked()` reads true, and adds it back at `ACTION_USER_PRESENT` or a
   screen-on that reads unlocked (Q-04-3); the panel window is the full display (`setFitInsetsTypes(0)` and the display's own size,
   R4 P5's attempt B, `ProbeActivity.kt:463-481`), so it covers the nav bar (Q-04-4; the fallback form is not built ahead — P5
   decides), focusable only while open; the volume window (task 8). All three windows set `FLAG_LAYOUT_IN_SCREEN |
   FLAG_LAYOUT_NO_LIMITS`, `layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` and `setFitInsetsTypes(0)`, top-start
   at y = 0, as attempt B did (`ProbeActivity.kt:473-481`) and phase 15's `RingOverlay` does (`clock/RingOverlay.kt:60-67`): a
   DEFAULT-mode window without them is clipped to the cutout-safe area (Reviewer 1's reading of AOSP `WindowLayout.computeFrames`,
   not saved under r4/src: UNVERIFIED; task 1 (l) checks the strip), which on the S25 Ultra's punch hole would put the strip below
   the status-bar band, where a top-edge pull opens Samsung's panel (round 4 D7). Every window sets RV10's density and a
   `LayoutParams.title` (`TesseraStrip`, `TesseraActionCenter`, `TesseraVolume`) that `dumpsys window windows` prints. `dump()`
   prints each tagged overlay node's bounds, text and state (DEBUG builds only) and then the ring. **Tags** (`Modifier.testTag`;
   the names `dump()` prints and the rows read, each leaf read by its own tag and asserted by equality, never off a merged parent;
   round 4 V12; `<key>` is the notification's `<package>:<id>`): task 5 `action_center`, `ac_status2:battery` and
   `ac_status2:date` (the second line's two leaves),
   `ac_expand` (its text "Expand" / "Collapse"), `ac_clear_all`; task 6 `quick_action:<id>`, `quick_action_label:<id>`,
   `quick_action_state:<id>` (the live-state caption, Y16); task 7 `notif_group:<package>`, `notif_title:<key>`,
   `notif_text:<key>`, `notif_expand:<key>`, `notif_action:<key>:<i>` (the i-th action, from 0), `notif_reply:<key>` (the box),
   `notif_send:<key>`; task 8 `volume_panel`, `volume_value:<stream>`, `volume_media_title`, `volume_media_prev`,
   `volume_media_play` (play / pause), `volume_media_next`. `[ac]` lines: `service connected`, `strip added`,
   `strip removed (<why>)`. Manifest ADD; the exported allow-list gains the service ("BIND_ACCESSIBILITY_SERVICE"). JVM-tested:
   `StripRule.present`, `PanelWindow.flags`, `KeyFilter.consume`.
5. **The action center: look and motion** (R3 A19, R7 §4.1–4.3, Y2, Y5–Y8, Y13, Q-04-10). `ac/ActionCenter.kt` (Compose in the
   panel window): its own status bar (`W10mStatusBar`, `bars/SystemBars.kt:88`) and A19's second line (battery `<n>%`, the date as
   `EEEE, MMMM d` in the device's locale; Decisions "Two picks"); the grid
   (task 6); "Clear all" / "Expand" ↔ "Collapse"; the notification list (task 7); the 13-epx accent handle with its grip; the fill
   solid (0,0,0). `ac/AcMotion.kt` and `ac/PullRules.kt`, pure: the strip's events drive the panel (the touch stays with the window
   it went down on); it appears at 138 epx (inside §4.1.3's 124–152) and tracks 1:1 with the handle 10.7 epx above the finger; the
   release rule (Y6), the 290-ms settle and its curves (§4.1.5, Y5), the scrim (§4.1.8, Y7), the list offset (§4.1.9, Y13), the
   drag-up dead zone (35 epx, inside 32–38) and the flick, drag, tap and Back closes (§4.2, Y8), the closes the panel did not ask
   for (while fully open, a cancel it did not cause or a window-state event for another package's activity, input-method windows
   and the shell's own overlays ignored → Y8's Back sequence; round 4 D9, Decisions "What closes the panel"), expand / collapse
   (§4.3). `[ac]` lines: `open (release at <f>)`, `closed by <flick|drag|back|tap|notification|action|system>`. JVM-tested:
   `PullRules`, `SettleCurve`,
   `ScrimRule.alpha`, `ListOffset`, `CloseRules`, `ExpandMotion`, `AcGeometry`.
6. **Quick actions, their page and the two grants** (Q-04-5, Q-04-2, addendum C1–C4, Decisions "Device-dependent quick
   actions", item 12). `ac/QuickActions.kt`: the 16 ids; the default 15; `QuickGrid.layout` (bottom row first, an incomplete row on
   top packed left, the collapsed row = the first four, no "Clear all / Expand" row with exactly four, C2); `QuickPrefs` (order and
   switches, app prefs; an action switched on joins the end of the order, Decisions "Two picks"); each action's mechanism
   (`QuickMechanism.of`): Wi-Fi, Bluetooth, Cellular, Airplane mode → the helper, through `HelperTileRule.onTap` (Q-04-2: start it,
   else Android's control and the Y16 line — the helper's page instead while it is not set up, Q-04-11; when the start will write `adb_wifi_enabled=1` on a BSSID that is not trusted or is
   unknown, the panel first closes with the tap wipe (Y8) and the tile reads "Starting…", so Android's dialog is not under the
   panel — round 4 D5), their states read by the app itself;
   Flashlight → `CameraManager.setTorchMode`, state from its torch callback; Rotation lock → `Settings.System.ACCELEROMETER_ROTATION`
   (on = 0); Brightness → the Y18 cycle; both through `WRITE_SETTINGS`, and without it a tap opens `ACTION_MANAGE_WRITE_SETTINGS`;
   Quiet hours → the shell's own automatic rule (`QuietHoursRule.onTap`; a Do Not Disturb turned on elsewhere opens
   `ACTION_ZEN_MODE_PRIORITY_SETTINGS` with its line); All settings → `SettingsActivity` (item 12); Camera → the camera slot
   (`tiles/SlotResolver.kt:66`); Note → the Notes app chosen in Tess's settings, launched as `ActionLayer.takeNote` does
   (`cortana/action/ActionLayer.kt:706-718`), and with none chosen Tess's settings page; Connect → `ACTION_CAST_SETTINGS`; VPN →
   `ACTION_VPN_SETTINGS`; Battery saver, Location, Mobile hotspot → their state where readable, then
   `ACTION_BATTERY_SAVER_SETTINGS`, `ACTION_LOCATION_SOURCE_SETTINGS`, `ACTION_WIRELESS_SETTINGS`. The page: `SettingsPage.ACTION_CENTER`
   ("Notifications & actions", Y20) — the arrangement grid and "Add or remove quick actions" (C1's fixed order). Manifest ADDs:
   `WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`, `ACCESS_WIFI_STATE`. `[ac] quick action <id> -> <mechanism> <result>`. JVM-tested:
   `QuickDefaults`, `QuickGrid.layout`, `QuickAvailability.offered`, `QuickPrefs`, `QuickMechanism.of`, `HelperTileRule.onTap`,
   `QuietHoursRule.onTap`, `BrightnessCycle.next`, `RotationRule`.
7. **Notifications** (Q-04-8, R7 §4.4, Y1, Y9, Y14). ADD to phase 01's listener (`feeds/TileNotificationListener.kt:22-101`): it also
   publishes the shade's list (`NotificationShade.state`, built by `ShadeRules.groups` from `activeNotifications`, `:49`) and offers
   `cancel(key)` and `cancelAll()` (clearable only); the tile path (`rebuild`, `:72-89`) is unchanged. `ac/NotificationList.kt`: group
   header (21-epx icon, app name), items (51-epx avatar: the large icon, else the app icon; Y1 text through `NotifText.shown`), swipe
   (R7 §4.4, Y9: on the header the whole group, as R7 measured; on one item that item alone, by the same rules — agent pick, H12), expand → the app's actions and the reply box (Y14). Every
   `PendingIntent.send` from the panel — a tap's `contentIntent`, an action's `actionIntent`, a reply — passes task 1 (f)'s
   `ActivityOptions` (the background-start opt-in); a tap closes the panel with the wipe (Y8, `closed by notification`); an action
   sends its `actionIntent`; a reply fills the action's RemoteInputs (`RemoteInput.addResultsToIntent`) and sends it; an action or
   reply whose `PendingIntent.isActivity()` is true closes the panel with the same wipe (`[ac] closed by action`), any other
   leaves it open (round 4 D6; settles V10); while the box has focus the panel window stops above the keyboard (task 1 h).
   `[notif]` lines carry the package and counts, never notification text or reply text. JVM-tested: `ShadeRules.groups`,
   `ShadeRules.clearAll`, `SwipeRules`, `NotifText.shown`, `NotifLine`.
8. **The volume panel** (R3 A20, R7 §4.5, Y3, Y10–Y12, Y15, Y19, Q-04-9). New package `app/src/main/kotlin/app/tileshell/volume/`:
   `VolumeKeys.kt` (from the service's `onKeyEvent`: VOLUME_UP / VOLUME_DOWN consumed — except while the last window-state event's
   package has an activity for `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`, the shell's own package excepted, when they pass at
   once so the camera's volume-key shutter works (`KeyFilter.consume`, round 4 D8; Decisions "Volume keys in a camera app") —
   key-down and repeats step the target stream
   through `AudioManager` with no `FLAG_SHOW_UI`, so Android shows no panel (A5.4–A5.5); every other key returned at once);
   `VolumePanel.kt` (A20's geometry; the overshoot show, the instant timeout hide, the slide-away when a window-state event shows the
   page changing; the second row behind the chevron; the "Vibrate on" footer; the media section from `MusicFeed.now`
   (`feeds/MusicFeed.kt:57`) driving `MusicFeed.send` (`:138-155`, PREVIOUS `:150-151`) — any routed app session, phase 10's
   included; the fill per task 9). Never focusable; shows over the keyguard (Decisions); its media section keeps its three
   buttons there and leaves out the track title while `KeyguardManager.isDeviceLocked()` is true (a PIN, pattern or password lock
   showing; a swipe lock or none shows the title), the slot kept so nothing moves (Q-04-13). `[volume]` lines: `key <up|down>
   stream=<s> <a>-><b>`, `panel shown`, `panel hidden (<why>)`, `ringer change refused (no Do Not Disturb access)`. JVM-tested:
   `VolumeRules`, `VolumeMotion`, `VolumeTimeout`, `VolumeGeometry`.
9. **Cross-window acrylic** (ADD to phase 13's engine, `ui/fluent/Fluent.kt`; Q-04-10; phase 13 Decisions 2026-09-22 "Cross-window
   is phase 04's ADD"). `FluentSource.WINDOW("window")` (`:87`); `FluentSurface.VOLUME_PANEL("volume_panel", WINDOW)` and
   `ACTION_CENTER("action_center", WINDOW)`, the latter listed as solid (`:93-100`); `FluentReason.UNSUPPORTED("unsupported")` (`:104-109`);
   `FluentWindowRule.decide(state, crossWindowBlurEnabled)`, followed live through `addCrossWindowBlurEnabledListener` beside
   `Fluent.state` (`:156-199`). On: the volume window gets `FLAG_BLUR_BEHIND` and `blurBehindRadius` = 30 epx in px, and draws T =
   (69,69,69) at 0.8 with the noise over it; off: the fill (55,55,55) at opacity 1. Lines through `Fluent.logShown` / `logForm`
   (`:202-213`): `[fluent] volume_panel source=window tint=(69,69,69) …`, `form=acrylic|fallback`, and the window state's reason.
   JVM-tested: `FluentWindowRule.decide`, `FluentMaterial.deriveTint` (this phase's two fills).
10. **Tess's switch commands** (Q-04-6, Q-04-2; ADDs to phase 03's part). `cortana/match/Request.kt`: `SetRadio(radio, on)` (`Radio`
    = WIFI, BLUETOOTH, MOBILE_DATA, AIRPLANE, shared with task 2), and the kdoc lines 6-8 now point here; `CommandMatcher.command`
    (`cortana/match/CommandMatcher.kt:75-116`): the switch phrases, placed after `savePlace` (`:87`) and before the open-app rule
    (`:89`); `LockGate.allowedWhileLocked` (`cortana/action/LockGate.kt:29-47`): `SetRadio` → false; `LockGate.restate` (`:50-67`):
    "Turn Wi-Fi off" and the rest; `ActionLayer.run` (`cortana/action/ActionLayer.kt:117-146`): `SetRadio` → the helper's verb when
    it runs, else `HelperKeeper` starts it (a request counts as a tap, Decisions "Q-04-1's reading, corrected"; when that start
    will write `adb_wifi_enabled=1` on a BSSID that is not trusted or is unknown, the reply is Y16's "Starting…" line and Tess's
    pane closes first, as the panel does, so Android's dialog is not under it — round 4 D5), else `host.launch` of Android's control
    (the helper's page while it is not set up, Q-04-11) with the Y16 reply. `[cortana] radio <radio> <on|off>
    -> <helper|panel (<reason>)>`. JVM-tested: `CommandMatcher` (the switch phrases), `LockGate.allowedWhileLocked` /
    `.restate`, `RadioReply.text`.
11. **Hold Back → recents** (Q-04-7; ADD to phase 01's part). `W10mNavBar` (`bars/SystemBars.kt:187-204`) gains `onBackHold`, whose
    default asks the service for `GLOBAL_ACTION_RECENTS` (AccessibilityService.java:536, android-16.0.0_r4); the Back `NavSlot`
    (`:200`) takes it as Search's does (`:202`; `NavSlot`'s long-click, `:207-227`). With the service off the hold does nothing and
    logs `[bars] back hold: accessibility off`; otherwise `[bars] back hold -> recents`. No pure rule; proved by device row A2.
12. **Settings pages, checklist rows, wizard steps** (ADDs to phase 01's and phase 12's parts; Decisions "The setup rows").
    `settings/SettingsActivity.kt`: `SettingsPage` (`:42`) gains `ACTION_CENTER` and `HELPER`, the `when` (`:73-85`) their pages,
    `HomePage` (`:138-149`) their two rows. `helper/HelperPage.kt` (Y4): state, the setup steps (Developer options, USB debugging,
    Wireless debugging with "Always allow on this network", Pair), Start, the pid / version / uptime, the trusted networks count
    (tags `helper_state`, `helper_pair`, `helper_start`; the Notifications & actions page `quick_arrange:<id>` and
    `quick_switch:<id>`; round 4 V12). `onboarding/Checklist.kt` `rows()` (`:116-195`): grant rows `accessibility` (its state
    reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` holding the component, not the bound-service list, which a
    `uiautomator dump` empties while it runs; round 4 D1), `write_settings` (round 4 D10: phase 19's id), `dnd_access`; observation
    rows `helper`, `usb_debugging` (Developer options and USB debugging; `adb_enabled` read on Android 16 only, B5), `wireless_debugging`
    (no wizard step: Q-04-11); no second overlay row. Phase 12's wizard
    steps and why lines (`setup:accessibility` "The action center and the volume panel come from this. Without it Samsung's own
    panels show."; `setup:write_settings` "Rotation lock and Brightness in the action center switch these settings. Without it they
    ask for this first."; `setup:dnd_access` "Quiet hours and the volume panel's silent step need this. Without it Quiet hours opens
    Android's Do Not Disturb page."). `qa/phase-03/scripts/provision.sh` ADDs four lines: `settings put secure
    enabled_accessibility_services app.tileshell/app.tileshell.ac.ShellAccessibilityService`, `settings put secure
    accessibility_enabled 1`, `appops set app.tileshell WRITE_SETTINGS allow`, `cmd notification allow_dnd app.tileshell` (not the
    helper: nothing provisions a pairing). They go AFTER provision.sh's own `am force-stop app.tileshell` (`provision.sh:173`, which
    would turn the service off again, Decisions "How the rows see the overlays") and after its marker write (`:174-180`), beside the
    keyboard's re-assert (`:181-183`) and before its final Home (`:184`), with a comment saying why — not in the grants block whose
    convention line (`:64`) would put them before the stop; then provision.sh prints `settings get secure
    enabled_accessibility_services` (round 4 D1 = V1: in the grants block every provisioned AVD would end with the service off, A1
    (c) would read `finished`, and the built drivers that assert `[wizard] not shown: core held` straight after provision —
    phase 12 `e1.sh:22`, `e14.sh:50`, phase 16 `e26.sh:126`, phase 17 `e25.sh:133`, phase 18 `e15.sh:143` — would fail on this
    build). JVM-tested: `HelperChecklist.rows`, phase 12's why-lines test with the three lines.
13. **QA fixtures and harness.** `testapps/qa-notify` (QA tooling, never shipped): a plain white full-screen activity (the "app
    below") and an `OpenedActivity`, and posts on `am start` extras — N1 plain, its `contentIntent` opening `OpenedActivity`, N2 with
    an "Ack" action and a "Reply" RemoteInput action, both broadcast PendingIntents to qa-notify's own receiver (so neither
    `isActivity()`, and the panel stays open after either, round 4 D6 / V10),
    N3 plain, N4 ongoing — each logging what it receives to the `TileShellQa` logcat tag (the reply's text included: the fixture's own
    log, not the shell's). `qa/phase-04/scripts/lib.sh` → symlink to `qa/phase-03/scripts/lib.sh`; `p04.sh`: `a11y_on`, `a11y_wait`
    (waits up to 5 s for an `[ac] service connected` stamped after the last uiautomator dump: every dump unbinds the service and it
    is rebound with a fresh `onServiceConnected`, Decisions "How the rows see the overlays"; `ensure_start`, `lib.sh:295-314`,
    `type_request`, `:338-358`, `tap_node` / `hold` from a fresh dump, the Settings walks and `wd_pair_dialog` all dump; every
    overlay step — `pull_slow`, `vol_up` / `vol_down`, the Back hold, `acdump` — that follows a dump calls it first; round 4 V4),
    `acdump` (the service's dump into the row folder), `acnode` / `acbounds` (a tagged node's text / bounds from it), `pull_slow <y>`,
    `flick_up`, `vol_up` / `vol_down` (the emulator events), `wd_pair_dialog` (p04.sh's own copy of
    `qa/r4/emulator-phone-run/wd_pair_dialog.sh` with `S=$ROW_DIR`: the original's `S` at `:5` is a finished session's scratchpad,
    so its pulls fail and it exits at `:23`; round 4 V2), `helper_ready` (when `[helper]` is not running: writes `adb_wifi_enabled 1`
    from the host; if Android's "Allow wireless debugging on this network?" (SystemUI) shows, ticks "Always allow on this network"
    and taps Allow; asserts `adb_wifi_enabled` reads 1 and records whether AndroidWifi's BSSID was already a `<wifiAP` entry of
    `dumpsys adb` — the script never turns Wireless debugging on nor answers that dialog, and the host's write passes with no
    dialog only on a network already trusted, addendum B2b — then pairs through `wd_pair_dialog` and the notification reply, as
    `r4_real_pairing_test.sh:34-41` does), `helper_pid` (the `tessera_helper` process), `wins` (`dumpsys window windows` titles),
    `stream_index <n>` (`cmd media_session volume --stream <n> --get`), `patch <png> <x> <y> <w> <h>` (a pixel patch's mean and
    spread), a hold-and-drag for A8 step 1 (`input motionevent` DOWN, a hold, MOVEs, UP, as `qa/phase-02/scripts/gestures.sh:7-9`:
    `input swipe` gives no long press), the install of qa-notify's APK, and copies of `p20.sh`'s `top`, `keyguard`, `sstate`
    (`:133-145`), `hold` (`:120-125`) and `to_pivot` (`:148-163`, with the `d` / `has` / `tap` / `ids` it uses and
    `$MUSIC_ACTIVITY`), `p18.sh`'s `absent_in` (`:99-105`), `rings_save` and `c6` (`:108-121`: the rings saved before a force-stop
    empties them), and `p12.sh`'s `start_slice` (`:48-58`), `restore_fresh` (`:202-212`), `provision_no_marker` (`:215-220`) and
    `resume_start` (`:223-228`) (A1 is E14's template); A6 sources `qa/phase-01/scripts/music_lib.sh` (round 4 V11). Not built: a
    gesture-navigation variant, a call fixture, screen-off key presses, any screenrecord / ffprobe motion capture.
14. **The adversarial review (trust gate), ONE pass, one fix round** (team-review, adversarial mode; Opus), recorded under
    qa/phase-04/ before `done`. The surface it reads:
    - the helper's binder door: `HelperDoor` (uid 2000 + token + the pinned pid; the exported provider), `HandoffRule`, the
      helper's `HelperDoorRule` (certificate pinned at start, re-checked at each handoff, exit on change), `CallerRule`; the token
      is readable by any uid-2000 process from the start line, so it stops only a planter that does not look, and the pid pin is
      the real door (round 4 D3); the stored token / versionCode / pid, and the exit a refused handoff's reply orders;
      `StartCommand.line` (the one shell line the app sends);
    - the allow-list: `HelperVerbs` (four radios × on / off, ping, exit; argv only; no `sh`, no content-provider call — phase 09's
      reason), and that no other path into a shell exists;
    - the app's own adb session: exactly three fixed commands (`AdbSessionCommands`), the key's storage, the pairing receiver
      (non-exported), and the `dumpsys adb` parse keeping BSSIDs only;
    - `WRITE_SECURE_SETTINGS` (granted over the app's own adb session) and the adb settings writes: `adb_wifi_enabled=1` only from 0
      and only on a trusted BSSID or a tap (a Tess request counts as one),
      `adb_allowed_connection_time=0` (told to him: any computer ever allowed keeps adb over a cable), and that nothing writes
      `adb_wifi_enabled=0`, `adb_enabled` or the accessibility list;
    - the accessibility service's scope: key filtering for the two volume keys only (passed through while a camera app is in
      front, round 4 D8), window-state events only, no window content; the DEBUG-only node dump (no notification text in a
      release build);
    - firing other apps' notifications: `contentIntent`, `actionIntent`, RemoteInput replies, the background-start opt-in, and that
      no `[notif]` line carries text;
    - anything over the keyguard: the strip's removal (`StripRule`), the volume panel and its media section over the lock screen
      (no track title while the device is locked: Q-04-13), Tess's `SetRadio` gate (`LockGate`), and that nothing
      of the action center is reachable while locked.

## Approximations
Values this phase renders that R3 and R7 do not give, or give only as LOW candidates.

| # | Value | Status | Stand-in used | H-row |
|---|---|---|---|---|
| Y1 | Notification item text (title, preview, time) | R3 A19 gives the avatar only | title in the 15-epx body class, bold; preview in the 15-epx body class, up to 2 lines; time in the 12-epx caption class; all 12 epx right of the 51-epx avatar (agent pick) | H4 |
| Y2 | Action center geometry and motion on 15063 | R3 A19 is 14393 only (MEDIUM); R7 §4.6.1's 15063 motion cross-check is LOW (85–200 ms capture refresh) | A19's and R7 §4.1–4.4's 14393 values | H5 |
| Y3 | Volume panel geometry and motion on 15063 | R3 A20 is 14393 only (MEDIUM); R7 §4.5.8: UNMEASURED | A20's and R7 §4.5's 14393 values | H6 |
| Y4 | Helper state in the action center, Settings and the checklist | no W10M counterpart | P4 design: a W10M quick-action tile state and a Settings row (designed at build start and recorded here) | H7 |
| Y5 | Settle curve after a flick | R7 §4.1.6: LOW; the panel keeps the finger's speed and slows only in the last ≈80–100 ms; exact curve UNMEASURED | inside the fixed 290 ms settle, a cubic that starts at the finger's release speed and ends at rest with zero speed (agent pick) | H8 |
| Y6 | Open / close threshold for a release without a flick | R7 §4.1.7: LOW; a stationary release closed at 27 % and opened at 78 %, flicks opened from 29 %; exact value UNMEASURED | released without a fling: opens at ≥ 50 % of the screen height, and below that closes with the §4.2.3–4.2.4 release sequence; a downward fling (faster than `ViewConfiguration.getScaledMinimumFlingVelocity()`) opens from any height (agent pick) | H9 |
| Y7 | Scrim between the measured points, and in light theme | R7 §4.1.8 gives six open fractions (MEDIUM); §4.1.11: LOW, light theme washed toward white (luminance 103 → 198) | linear between the midpoints of the measured ranges, 1.0 at 0 %, and 0.08 held from 66 % up (89 % read 0.09–0.12); light theme draws a white scrim with the same curve (agent pick) | H10 |
| Y8 | Close by Back or by tapping a notification, and the closes the panel did not ask for | R7 §4.2.7: UNMEASURED; W10M had no gesture navigation | Back runs the drag-up release sequence (§4.2.3–4.2.4: handle gone, content drifts up and fades in 110 ms, app back by 260 ms); a notification tap, or an action or reply that starts an activity, runs the tap-close wipe (§4.2.5–4.2.6), then its app opens; while fully open, a cancel the panel did not cause (a home or recents gesture) or a window-state event for another package's activity (input-method windows and the shell's own overlays ignored) runs the Back sequence (`[ac] closed by system`, round 4 D9) (agent pick) | H11 |
| Y9 | Notification dim step point, dismiss threshold, slide-out, reflow, left swipe | R7 §4.4.2 (step vs ramp not resolved inside 19–28 %); §4.4.3–4.4.4: LOW (73 % dismissed, 28–30 % snapped back, reflow ≤ 133 ms); §4.4.5: UNMEASURED | dim steps to 0.38 at 24 % of the width; released past 50 % of the width or flung right, the group slides off the right edge in 133 ms (ease-in) and the groups below move up in 133 ms (ease-out); otherwise it snaps back in 133 ms (ease-out); a left swipe does nothing (agent pick) | H12 |
| Y10 | Slider row entrance while the volume panel shows | R7 §4.5.2: LOW (the row slides down into the panel behind the growing edge: −6 px, then +2 px, settled ≈233 ms after the first frame) | candidate as measured: the row slides down behind the growing edge, overshoots 2 epx and settles with the panel | H13 |
| Y11 | Volume panel timeout, and which navigations slide it away | R7 §4.5.4: LOW (≈3.1 s after the last on-panel change, one event); §4.5.5 measured only a Start-key press | hides (instantly) 3.1 s after the last volume change; Home, Back and app switches all use the §4.5.5 slide-away (agent pick) | H14 |
| Y12 | Second slider row appearing (chevron tapped) | R7 §4.5.7: LOW (slides down 134 epx from under the first slider, ≈283 ms, ease-out; trigger not visible) | candidate as measured, triggered by the chevron | H15 |
| Y13 | Notification-list offset during the drag, and after a release below 78 % | R7 §4.1.9: +3.3 to +8.7 epx during the drag in three events, no curve; 0.062 × remaining travel measured only after a 78 % release | during the drag the offset rises linearly with open % from 3.3 epx at the panel's first frame to 8.7 epx at 78 % open and holds 8.7 epx above that; after release it is the smaller of the offset at release and 0.062 × the remaining travel, so it never jumps (agent pick, review R3T-B2) | H16 |
| Y14 | An expanded notification: its buttons and the reply box (Q-04-8) | preview-era hands-on only (onmsft.com/?p=14041; gsmarena windows_10_for_phones-review-1208p2); not in R3 / R7 | a 12-epx chevron at the item's right expands it in place (the list below moves down as a block on §4.3.2's expand curve, 200 ms): the full text up to 8 lines (15-epx body), then the app's action titles as accent text buttons in the 15-epx body class, left-aligned at the text's x, 24 epx apart; a RemoteInput action opens a one-line box (the shell's `OutlinedField`, `ui/components/OutlinedField.kt`) with a send glyph at its right; after a send the box closes and the time line reads "Sent" until the app updates the notification (agent pick) | H17 |
| Y15 | The volume panel's media section: geometry and motion (Q-04-9) | R7 §4.5.6 LOW (lower edge 93 → 204 crop px, ≈333 ms ease-out, 41 % in the first frame; the title fades in over 100 ms); §4.5.7 LOW (the controls fade out as the second slider opens); geometry not in A20 | the lower edge goes from the one-slider rest (100.4 epx) to ≈218 epx (204 crop px × R7's 1.07 epx/px of §4.5.7, INFERRED) in 333 ms on a cubic-bezier fitted at build start to §4.5.6's fourteen points (the fit and its RMS recorded here); the track title in the 15-epx body class at x = 12 epx, 12 epx under the slider row, fading in over 100 ms; previous, play / pause and next as 29-epx glyphs centred at x = 90, 180 and 270 epx, 20 epx under the title; the controls fade out in 33 ms when the second slider opens (agent pick) | H18 |
| Y16 | The helper-down line on the four helper tiles, and Tess's words when the helper cannot run (Q-04-2) | no W10M counterpart | the tile's live-state caption (the slot W10M uses for "Available" / "Not connected", C2) reads the reason — "No Wi-Fi", "Location off" (the BSSID unknown, round 4 D12 b), "Not set up", "Pair again" (pairing gone, Q-04-11), "Debugging off", "Starting…"; a tap that opens Android's control, or the helper's page while it is not set up (Q-04-11), closes the panel with the tap-close wipe (§4.2.5–4.2.6), and so does a tap whose start will bring Android's "Allow wireless debugging on this network?" dialog, the tile then reading "Starting…" (round 4 D5). Tess: "I can't switch <radio> myself right now: <reason, lower case>. Here's Android's switch."; while the helper is not set up (Q-04-11): "I can't switch <radio> myself until the helper is set up. Here's its page." (agent pick); when her request's start brings Android's dialog (round 4 D5): "Starting… Android asks about this network first." and her pane closes (agent pick) | H19 |
| Y17 | Where a pull starts (the top-edge strip) | W10M pulled from its status bar; R7 §4.1.3 gives where the panel first appears, not the touch zone | a transparent strip across the full width at the top edge, laid out from y = 0 over any display cutout (cutout mode ALWAYS, round 4 D7), as tall as the system status bar and never under 24 dp (AOSP's top-swipe zone, addendum A4) (agent pick) | H20 |
| Y18 | Brightness steps and label | C3: the label shows the level; the phone's step order UNVERIFIED (desktop 25 → 50 → 75 → 100 → 25, Thurrott 5116); One UI's 1–255 mapping UNVERIFIED (C4) | a tap steps Automatic → 25 % → 50 % → 75 % → 100 % → Automatic (`SCREEN_BRIGHTNESS_MODE`; `SCREEN_BRIGHTNESS` = round(255 × level)); the label reads "Automatic" or "<n>%" (agent pick) | H21 |
| Y19 | Which Android streams the volume panel's rows drive, and the "Vibrate on" footer | A20 names W10M's rows ("Ringer + Notifications", "Media + Apps"); Android's streams are not W10M's | a key changes Call in a call (`STREAM_VOICE_CALL`), else Media + Apps while music plays (`AudioManager.isMusicActive`, `STREAM_MUSIC`), else Ringer + Notifications (`STREAM_RING`; Android decides whether notifications follow it); the chevron opens the other row; the footer reads "Vibrate on" / "Vibrate off" from the ringer mode and a tap switches vibrate (agent pick) | H22 |
| Y20 | The quick-actions settings page: arrangement and add / remove | C1–C2's frames show both (S1 79.5 s, 106.5 s, 112 s), unmeasured | the arrangement grid in A19's tile geometry under a "Quick actions" header, moved by hold-and-drag as S1 80–91.5 s shows; "Add or remove quick actions" opens the switches in C1's fixed order in phase 01's toggle-row form (agent pick) | H23 |
| Y21 | The action center in landscape | no landscape capture in R3 or R7 | the strip spans the top edge; the panel opens full-screen with A19's epx geometry anchored at the left as in portrait (agent pick) | H24 |

## Acceptance criteria
**The owner's rule: "Build, push, then ONE round of testing (Jeremy, 2026-10-07)."** All fourteen build tasks are built with their
JVM tests, the build is pushed, and then every row below runs ONCE on the pushed build. A failing row is fixed and that row alone is
re-run; nothing else runs a second time. Rows are few, each a thing a person would do; the unit tests carry correctness. This section
was re-cut whole on 2026-10-09 (the pre-R4 E1–E5 / P1–P6 / H1–H16 cut is in git history, commit d29273259 and earlier).

**Preamble.** Rows run ONCE on the pushed debug APK on `emulator-5554` (the AOSP AVD tileshell_fhd, 1080 × 2340 @ 450 dpi, 3 px/epx
by RV10, API 36, no Google; 3-button navigation, its default; Wi-Fi "AndroidWifi"). Start: `qa/phase-03/scripts/provision.sh` (with
this phase's lines, after its own force-stop: the service on, `WRITE_SETTINGS` and Do Not Disturb access granted; no pairing), then
`ensure_start`; before a row's first step the preamble asserts that the `enabled_accessibility_services` line provision.sh prints
holds `app.tileshell/app.tileshell.ac.ShellAccessibilityService` and waits for `[ac] service connected` (`a11y_wait`; round 4 D1 =
V1). After any launch of another app: `am force-stop app.tileshell` + Home (C-6), then `a11y_on`, because a force stop turns the
shell's service off (Decisions "How the rows see the overlays"); after `pm clear` the same. Overlays are read through `acdump` (the
service's own dump) and screencaps; `uiautomator dump` only while no overlay must stay up (it unbinds the service), and every
overlay step that follows a dump (`pull_slow`, `vol_up` / `vol_down`, the Back hold, `acdump`) calls `a11y_wait` first (round 4 V4).
Volume keys only through `vol_up` / `vol_down` (the emulator's events; never `input keyevent`); top-edge pulls only through
`pull_slow` (A4). Ring reads use `ring_mark` / `ring_since` (one ring: the service runs in the launcher's process); absence uses
`absent_in` on a slice proven readable. Tess is typed (`type_request`, `lib.sh:338`), over the keyguard as
`qa/phase-17/scripts/e10.sh` does it. A row that needs the helper calls `helper_ready` first. Every row stamps the APK
(`apk_matches`, `lib.sh:380`). Nothing is spoken and the host's audio is never touched. **Fixtures and restores:** each row makes
its own (qa-notify's notifications, the fixture MP3, a pairing) and puts back what it changed — Wi-Fi on, airplane off, mobile data
on, Bluetooth as read, the volume indexes as read, `zen_mode` 0, rotation and brightness as read, `settings delete global
disable_window_blurs`, quick-action prefs as read (rows that `pm clear` re-provision), the service on, qa-notify force-stopped (N4
is ongoing and outlives A5; round 4 V17); Wireless debugging and `adb_allowed_connection_time` are left as the product set them
(Q-04-1). Evidence goes under qa/phase-04/<row>/ and is never committed.

### Device rows (A1–A8)

**A1 — Set it up on a fresh install** (phase 12 E14, for this phase's three steps).
1. `pm clear app.tileshell` → `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` → revoke the three grants (`settings put
   secure enabled_accessibility_services ""`, `appops set app.tileshell WRITE_SETTINGS ignore`, `cmd notification disallow_dnd
   app.tileshell`) → Home.
2. Walk the wizard: at each step read `wizard_why` and `wizard_progress`, grant it from adb, resume (`am start` the Settings hub, Back).
3. `pm clear` → full `provision.sh` → Home; MARK; resume.
4. From that state revoke each grant in turn; MARK; resume; read the checklist (Settings > Setup checklist); restore it.

Pass:
- (a) the steps `wizard_step:setup:accessibility`, `setup:write_settings`, `setup:dnd_access` show in that order, each
  `wizard_why` equal to its line in build task 12, the first `wizard_progress` "Step 1 of 4"; after the third, `wizard_presets`;
- (b) no `wizard_step:setup:helper`, `setup:usb_debugging` or `setup:wireless_debugging` in step 2's walk (a walk proven readable by
  (a); Q-04-11);
- (c) after step 3: no `wizard_page`, and `[wizard] not shown: core held` in the slice;
- (d) after each revoke: no `wizard_page`, `[wizard] not shown: finished`, and that row reads `checklist:<id>:missing`;
- (e) the checklist holds `checklist:helper:missing`, `checklist:usb_debugging:` and `checklist:wireless_debugging:` rows, and exactly
  one `checklist:overlay:` row.

**A2 — Pull the action center down over an app, and put it away.**
1. Open qa-notify's white activity (no notifications posted).
2. `pull_slow 1404` (60 % of the height), hold 2 s, lift; `acdump`; screencap.
3. Tap `ac_expand` (Expand); `acdump`; tap it again (Collapse); `acdump`.
4. Tap (540, 2328), the nav bar's strip; `acdump`; `top`.
5. `input keyevent KEYCODE_BACK`; `acdump`; `top`.
6. `pull_slow` to fully open; `flick_up`; `acdump`.
7. The keyguard as `qa/phase-17/scripts/e10.sh`'s K leg sets it up (`locksettings set-disabled false`, PIN 1234, sleep + wake;
   restored by an EXIT trap); `keyguard`; `vol_up`; `wins`; unlock with the PIN; `wins`; restore.
8. Open the Settings hub; dump it for `nav_back`'s bounds; `a11y_wait`; `hold` `nav_back` (900 ms); `top`.

Pass:
- (a) after the lift: `[ac] open`; the `action_center` node's bottom = 2340 (it covers the nav bar, Q-04-4); the collapsed row is
  `quick_action:camera`, `:wifi`, `:bluetooth`, `:rotation_lock`, left to right; `quick_action:camera` is 255 ± 3 × 181.5 ± 3 px
  with its left edge at 12 ± 3 px, and `quick_action:wifi`'s left edge is 267 ± 3 px right of it (RV10's 3 px/epx: A19's 85 ×
  60.5-epx tile, 4-epx margin, 89-epx pitch; round 4 V13); `ac_status2:battery` equals `<level>%` with `level` from `dumpsys
  battery`, and `ac_status2:date` equals the device's `date +'%A, %B %-d'` (Decisions "Two picks"; round 4 V7);
- (b) the screencap patch at (520–560, 1980–2020) reads (0,0,0) ± 1 over the white app (solid, Q-04-10);
- (c) expanded: the grid's rows top to bottom are the C2 order filled from the bottom (the top row packed left, with Flashlight only if
  build task 1 found a flash unit and Connect only if `ACTION_CAST_SETTINGS` resolves) and `ac_expand` reads "Collapse"; collapsed
  again: one row, `ac_expand` "Expand";
- (d) after the nav-strip tap the `action_center` node is still there and `top` is still qa-notify (the tap reached the panel, not Home);
- (e) Back: `[ac] closed by back`, no `action_center` node, `top` qa-notify; the flick: `[ac] closed by flick`;
- (f) with `keyguard` reading `isKeyguardShowing=true`, then `vol_up`: a `wins` list that holds `TesseraVolume` lacks
  `TesseraStrip`; `[ac] strip removed (screen off)` at the sleep, and no `[ac] strip added` before the unlock; after the unlock,
  `[ac] strip added` and `TesseraStrip` back in `wins` (round 4 V5: the sleep removes the strip first, so `(keyguard)` is never
  logged on this path; the media section over a secure keyguard is A6 (f), Q-04-13 — nothing plays in this
  row);
- (g) the hold: `top` is the recents activity build task 1 recorded, and `[bars] back hold -> recents`.

**A3 — Set up the helper and flip the switches.**
1. Kill any running helper (`helper_pid`; `kill`). `pm clear app.tileshell` → provision.sh → `a11y_on`. `pm revoke app.tileshell
   android.permission.WRITE_SECURE_SETTINGS` (`dumpsys package app.tileshell`: granted=false). `settings delete global
   adb_allowed_connection_time` (`settings get` reads null). `cmd location is-location-enabled` (saved
   `LocationShellCommand.java:56`) reads true and `dumpsys package` shows `ACCESS_FINE_LOCATION: granted=true` (the BSSID needs
   both). Record the `<wifiAP` entries of `dumpsys adb`. (Round 4 V6: without this a re-run passes (a) vacuously, finds the key
   and the old helper, and leg (c) rests on state nobody asserted.)
2. Open the action center; acdump; tap Wi-Fi (Q-04-11: before pairing a helper tile opens the helper's page). Tap `helper_pair`.
3. `settings put global adb_wifi_enabled 1` from the host; if Android's "Allow wireless debugging on this network?" shows (it does
   unless step 1 found AndroidWifi's BSSID already listed), tick "Always allow on this network" and tap Allow; `adb_wifi_enabled`
   reads 1. Then `wd_pair_dialog` (p04.sh's copy) opens "Pair device with pairing code" and reads the code (round 4 V2).
4. Pull down the system shade (`cmd statusbar expand-notifications`), tap the shell's "Enter code", type the code, send.
5. Wait for `[helper] started`. Open the action center; tap Wi-Fi, read; tap Wi-Fi; tap Cellular twice and Airplane mode twice,
   reading after each; Bluetooth twice if build task 1 found the AVD's adapter flips.
6. `adb shell content call --uri content://app.tileshell.helper --method helper_binder`.

Pass:
- (a0) step 2: `quick_action_state:wifi` equals "Not set up" in the acdump; after the tap `helper_state` reads not set up and the
  slice holds `[ac] quick action wifi -> helper page (not set up)` (Q-04-11);
- (a) `[helper] paired`, then `[helper] started pid=<p> uid=2000`; `ps -o PPID= -p <p>` reads 1 and `ps -o USER=,LABEL= -p <p>`
  reads `shell u:r:shell:s0` (not the helper's own uid claim; round 4 V7); `dumpsys package` shows
  `WRITE_SECURE_SETTINGS: granted=true`; `settings get global adb_allowed_connection_time` reads 0 (from step 1's null);
- (b) each tap flips the radio as Android reads it (`cmd wifi status` first line, `settings get global mobile_data`, `cmd connectivity
  airplane-mode`) and back, `quick_action_state:<id>` agrees each time, and `[helper] verb <radio>_<on|off> -> exit 0`;
- (c) after the tap that turns Wi-Fi back on, with no further input, `settings get global adb_wifi_enabled` reads 1 within 60 s,
  and `[helper] wireless debugging back on (trusted network)` (the Wi-Fi-joined trigger, Decisions "The restart path, after
  round 4" (a));
- (d) step 6: `[helper] handoff refused: no token (uid 2000)`, and the next ping still answers pid `<p>` (the planter that does
  not look; one that reads the token off the start line is refused by the pid pin, `HandoffRule.accept`'s JVM cases and the GATE).

**A4 — The helper lives on.**
1. `helper_ready`; record `<p>`. `am force-stop app.tileshell` + Home; wait 10 s; read `settings get secure
   enabled_accessibility_services` BEFORE `a11y_on`; then `a11y_on`.
2. `settings put global adb_wifi_enabled 0`; wait 30 s; tap Cellular twice from the action center; read `adb_wifi_enabled` and
   `ps -p <p>`.
3. `adb reboot`; wait for `sys.boot_completed`; `wake_device`; send nothing else (no `a11y_on`); wait up to 90 s.
4. `cmd wifi set-wifi-enabled disabled`; wait 5 s; `adb shell kill <the new pid>` (Wi-Fi first, so the restart path finds no
   Wi-Fi); open the action center; `acdump`; tap Wi-Fi; `top`; `wins`.
5. Restore Wi-Fi; the helper comes back on the trusted network.

Pass:
- (a) after the force stop the same helper hands its binder to the new process: `[helper] handoff accepted` with pid `<p>` (R4 4a);
  and the read before `a11y_on` lacks `app.tileshell/app.tileshell.ac.ShellAccessibilityService` (the shell holds
  `WRITE_SECURE_SETTINGS` by now and still never turns its own service back on, Out; round 4 V8);
- (b) with Wireless debugging off, `<p>` is alive (`ps -p`), Cellular flips both ways (USB debugging keeps adbd, Q-04-1), and
  `adb_wifi_enabled` still reads 0 when `<p>` is checked (the taps wrote nothing while the helper answered, Decisions "The restart
  path, after round 4" (d); round 4 V8);
- (c) after the reboot, with no input and no `a11y_on` since it: `[ac] service connected` and `[ac] strip added` after
  `sys.boot_completed`; `[helper] started … (boot)` within 90 s of it (the first start after a boot is tagged `boot` whatever set
  it off, the home activity included); and a Cellular flip works (round 4 V8);
- (d) helper down and Wi-Fi off: in the acdump before the tap `quick_action_state:wifi` equals "No Wi-Fi" (Y16); after it, the
  identity build task 1 (c) recorded for `Settings.Panel.ACTION_WIFI` (`top`, or its title in `wins`), and `[ac] quick action
  wifi -> panel (no Wi-Fi)` (round 4 V9).

**A5 — Read, act on and clear notifications.**
1. `ensure_start` (Start is the app below); qa-notify posts N1–N4; `cmd notification post -t "QA shell" qa5 "from the shell"`
   posts a second group.
2. `a11y_wait` (`ensure_start` dumped); open the action center; `acdump`.
3. Tap `notif_expand:<N2>`; tap `notif_action:<N2>:0` (Ack); `acdump`. Tap `notif_action:<N2>:1` (Reply); `input text hello`;
   read `dumpsys input_method` (`mInputShown`) and the keyboard's and the panel's frames from `dumpsys window windows`; tap
   `notif_send:<N2>`.
4. Swipe the Shell group right to 70 % of the width; lift; read `dumpsys notification --noredact`.
5. Tap N1; `top`. Then C-6 + `a11y_on`; open the action center; tap `ac_clear_all`; read `dumpsys notification --noredact`.

Pass:
- (a) two groups, qa-notify's header with N1–N4 and the Shell group's, each item's `notif_title:<key>` and `notif_text:<key>`
  equal to what was posted;
- (b) Ack: qa-notify's log holds `action ack N2`, and the panel stayed open — `action_center` in the acdump after it, no `[ac]
  closed` line since the tap (`absent_in`; Ack is a broadcast, not an activity: round 4 D6 / V10); the reply: its log holds
  `reply N2 "hello"`; while the box had focus the keyboard
  was shown (`mInputShown=true`) and the panel window's bottom was at or above the keyboard window's top; `[notif] reply sent (5
  chars)`, and `hello` is absent from the ring slice that holds that line;
- (c) the swipe: `qa5` is absent from a `dumpsys notification --noredact` output that still lists N1 (the read proven readable;
  round 4 V10), and `[notif] dismissed`;
- (d) the tap: `top` is qa-notify's `OpenedActivity`, its log `opened N1`, and `[ac] closed by notification`;
- (e) Clear all: of qa-notify's, only N4 (ongoing) is still in `dumpsys notification`, and the list shows N4 alone.

**A6 — Press the volume keys.**
1. qa-notify's white activity in front, nothing playing; set and read `stream_index 2` (ring) away from its ends and `stream_index 3`
   (music) at 0, so the MP3 plays silent until step 4's +1 (as `music_lib.sh:9-12` mutes it; round 4 V17).
2. `vol_up`; 300 ms later `acdump`, `wins`, screencap.
3. Wait 4 s; `acdump`.
4. `music_fixtures`; `music_open` (`music_lib.sh:24-27`: it force-stops the shell, so the service goes off); `a11y_on`; `to_pivot
   songs`; tap the first `music_song:` node (as phase 20 `a4.sh:56-61`); `sstate` reads PLAYING; `am start` qa-notify's white
   activity; `a11y_wait`; `vol_up`; `acdump`; tap `volume_media_play`; `sstate` (round 4 V3: neither `music_fixtures` nor
   `music_open` starts playback).
5. `settings put global disable_window_blurs 1`; `vol_up`; screencap; `settings delete global disable_window_blurs`.
6. `input keyevent KEYCODE_MEDIA_PLAY`; `sstate` reads PLAYING. The keyguard as in A2 step 7 (PIN 1234); `vol_up`; `acdump`. Unlock
   with the PIN; `vol_up`; `acdump`. Restore the keyguard; `input keyevent KEYCODE_MEDIA_PAUSE` (Q-04-13).

Pass:
- (a) a `volume_panel` node shows, and the stock panel's window title (build task 1 b) is absent from a `wins` list that holds the
  shell's volume window;
- (b) ring +1, music unchanged, `[volume] key up stream=ring <a>-><a+1>`, and no `volume_media_title` node in that acdump
  (nothing plays; round 4 V15);
- (c) 4 s later no `volume_panel` node, `[volume] panel hidden (timeout)`;
- (d) with music: music 0 → 1, `volume_media_title` reads the fixture's title, and after the `volume_media_play` tap the session is
  PAUSED;
- (e) step 2's patch at (1050–1070, 240–280) has every channel's mean in 101–111 with `[fluent] volume_panel form=acrylic`; step 5's
  same patch reads (55,55,55) ± 1 with `[fluent] volume_panel form=fallback` and the reason `unsupported`;
- (f) locked: `volume_panel`, `volume_media_prev`, `volume_media_play` and `volume_media_next` show and no `volume_media_title` node
  does; after the unlock `volume_media_title` reads the fixture's title (Q-04-13).

**A7 — Ask Tess to switch things** (typed).
1. `helper_ready`. `type_request` "turn off mobile data".
2. The keyguard as in A2 step 7 (PIN 1234); `type_request` "turn on mobile data" over it; read; tap Unlock and enter the PIN;
   restore the keyguard.
3. `cmd wifi set-wifi-enabled disabled`; wait 5 s; kill the helper (Wi-Fi first, as A4 step 4); `type_request` "turn on wifi";
   `top`; `wins`. Restore.

Pass:
- (a) `settings get global mobile_data` reads 0, `[cortana] radio mobile_data off -> helper`;
- (b) locked: the Unlock card with the caption "Turn mobile data on", `[cortana] locked: SetRadio(…) gated`, mobile data still 0;
  after the unlock it reads 1;
- (c) the identity build task 1 (c) recorded for `Settings.Panel.ACTION_WIFI` (`top`, or its title in `wins`; round 4 V9), and
  Tess's reply names the reason (Y16's "no Wi-Fi").

**A8 — Arrange the quick actions and use the ones the shell does itself.**
1. Start states, each restored at the end: `settings put system accelerometer_rotation 1`, `settings put system
   screen_brightness_mode 1` (Automatic), `settings put global zen_mode 0`. Settings > Notifications & actions: turn VPN on and
   Battery saver off (`quick_switch:vpn`, `quick_switch:battery_saver`); hold-and-drag `quick_arrange:note` to the first place.
2. Open the action center; expand; `acdump`.
3. Tap Rotation lock twice, Brightness once, Quiet hours twice, reading the settings after each.
4. Tap Location; `top`; C-6 + `a11y_on`. Tap All settings; `top`; C-6 + `a11y_on`.
5. Open the action center; expand; `acdump`. Restore the prefs and the settings.

Pass (literal expectations, not the app's own functions; round 4 V7):
- (a) the saved order is Note, Camera, Wi-Fi, Bluetooth, Rotation lock, All settings, Airplane mode, Cellular, Mobile hotspot,
  Quiet hours, Brightness, Connect, Flashlight, Location, VPN (VPN, switched on, joins the end: Decisions "Two picks"), less
  Connect and Flashlight where build task 1 (c) found them not offered; the grid lays it out in fours from the bottom row up, the
  top row packed left — with both offered, top to bottom: Flashlight, Location, VPN | Mobile hotspot, Quiet hours, Brightness,
  Connect | Rotation lock, All settings, Airplane mode, Cellular | Note, Camera, Wi-Fi, Bluetooth; no `quick_action:battery_saver`;
- (b) `settings get system accelerometer_rotation` 1 → 0 → 1; Brightness from Automatic: `screen_brightness_mode` 1 → 0 with
  `screen_brightness` 64 (Y18's 25 %: round(255 × 0.25)); `settings get global zen_mode` 0 → non-zero → 0; each
  `quick_action_state:<id>` agrees;
- (c) Location: `top` is the activity build task 1 recorded; All settings: `top` is `app.tileshell/.settings.SettingsActivity`;
- (d) after the force stops the grid is unchanged.

### Static checks (one command each, not rows)
- The exported components equal `qa/phase-03/exported-allowlist.txt` with this phase's two ADDs — the accessibility service and
  the helper door — exactly.
- The APK is ≤ 629,145,600 B (`stat -c%s`).
- No `ProcessBuilder("sh"`, no `"-c"`, no `Runtime.exec`, and no `ContentResolver` / `IContentProvider` call in
  `app/src/main/kotlin/app/tileshell/helper/` other than `HelperMain`'s delivery to `app.tileshell.helper` (a grep; `HelperVerbs`'
  own test carries the table).
- Recorded, not a pass / fail: earlier phases' drivers under `qa/` that tap or drag at y < the strip's height (`acbounds` of the
  strip on the AVD: as tall as the system status bar, Y17, not a fixed 68 px), which the strip now takes while the service is on
  (round 4 V16).

### GATE
One adversarial Opus pass, one fix round, over the trust surface build task 14 lists. Recorded under qa/phase-04/ before `done`.

### Fixtures
**Survive:** `testapps/qa-notify` (the white activity, `OpenedActivity`, N1–N4, its `TileShellQa` log); `cmd notification post` for a second group; the
AVD's own Wireless debugging with p04.sh's copy of `qa/r4/emulator-phone-run/wd_pair_dialog.sh` (`S=$ROW_DIR`) and `helper_ready`'s
answer to Android's dialog; phase 01's `music_fixtures` and phase 20's `to_pivot` (playback); `provision.sh`'s four new lines, after
its force-stop.
**Gone:** `adb emu sms send` and the stock Messaging app (old E2); 60-fps screenrecords and ffprobe; the Auxio media fixture (old E5:
the shell's own Music is the session); a call fixture; a gesture-navigation variant.

### Unit tests (JVM) — carried by the build tasks
Each rule is a pure function with a JVM test written by the build task named beside it. One name per rule; the same names are used
in Decisions and Build tasks.

| rule (pure function) | cases | build task |
|---|---|---|
| `HelperVerbs` | the table exactly: `wifi_on/off` → `cmd wifi set-wifi-enabled enabled/disabled`, `bluetooth_on/off` → `cmd bluetooth_manager enable/disable`, `mobile_data_on/off` → `svc data enable/disable`, `airplane_on/off` → `cmd connectivity airplane-mode enable/disable`; `ping`, `exit`; refused: `location_*`, `battery_saver_*`, `nfc_*`, `hotspot*`, `auto_time_*`, an empty name, a name with a space or `;`; no argv element from the caller; no entry runs `sh`, `content`, `settings`, `pm` or `am` | 2 |
| `HandoffRule.accept(callingUid, callingPid, token, expected)` | 2000 + the current token, no pid pinned yet (the first handoff since the app's own start) → accept and pin that pid; 2000 + the current token from the pinned pid → accept; 2000 + the current token from another pid (a planter that read it off the start line) → refused; 2000 with no or an old token → refused; 0 → refused; an app uid → refused; every refusal's reply says exit (round 4 D3) | 2 |
| `HelperDoorRule.client(pinnedCert, currentCert, pkgUid)` | the pinned certificate → deliver to that uid; another certificate (re-signed) → refuse and exit; package gone → exit | 2 |
| `CallerRule.allowed(callingUid, shellUid)` | the shell's uid → allowed; 2000, 1000, another app → refused, logged | 2 |
| `StartCommand.line(apk, token, vc)` | exactly `CLASSPATH='<apk>' setsid nohup app_process /system/bin --nice-name=tessera_helper app.tileshell.helper.HelperMain --daemon '<token>' '<vc>' >/dev/null 2>&1 </dev/null &`; the token is 32 hex characters, the versionCode digits; an apk path holding a `'` → refused; nothing else interpolated (round 4 D3) | 2 |
| `HelperVersion.mustRestart(appVersion, pingVersion)` | equal → keep; different or missing → start a new one first, tell the old one to exit only after the new one's ping answers, the old one serving until then; the new one cannot start → the old one keeps serving (round 4 D4 c) | 2 |
| `PairingCode.parse(text)` | six digits (spaces trimmed) → the code; anything else → refused before any network call | 2 |
| `AdbSessionCommands` | exactly three: the start line (`StartCommand.line`), the `pm grant` of `WRITE_SECURE_SETTINGS`, `dumpsys adb`; any other command → refused | 2 |
| `TrustedNetworks.parse(dumpsysAdb)` | `<wifiAP bssid=…>` entries → the BSSID set; key lines dropped; an empty or unknown form → the empty set | 3 |
| `TrustedNetworks.shouldEnable(bssid, trusted, adbWifiEnabled, tapped)` | trusted + 0 → write 1; already 1 → nothing; untrusted, null or `02:00:00:00:00:00` → nothing unless tapped (a tile tap or a Tess request: round 4 V15); never a 0 | 3 |
| `RestartPlan.next(state, event)` | boot, package replaced, binder died, a tap, a Tess request, a home start, Wi-Fi joined: B8's order (Wi-Fi → 1 if trusted and 0 → mDNS ≤ 30 s, one re-write → connect → trusted list → at every start, grant when not held and the timeout written when non-zero → start → ping); a tap or a request while the helper answers its ping → straight to the verb, nothing written; a home start then → nothing; Wi-Fi joined on a trusted BSSID at 0 → write 1 with the helper running too; the first start after a boot tagged `boot` whatever the event; both debugging switches off → not running (debugging off), no retry loop; not paired → not running (not set up); paired once and the key refused → not running (pairing gone, Q-04-11) (round 4 D4, V8) | 3 |
| `HelperState.reason(state)` | each state's Y16 line, "Location off" for an unknown BSSID (never "untrusted network"; round 4 D12 b); running → none | 3 |
| `StripRule.present(serviceConnected, screenOn, keyguardLocked)` | connected, on, unlocked → present; keyguard locked (secure or swipe) → absent; screen off → absent | 4 |
| `PanelWindow.flags(state)` | closed and dragging → not focusable; open → focusable; always the full display with insets not fitted, `FLAG_LAYOUT_IN_SCREEN \| FLAG_LAYOUT_NO_LIMITS`, cutout mode ALWAYS, top-start at (0, 0); the strip's and the volume window's params: the same layout flags, cutout mode and fit-insets 0, top-start at y = 0, never focusable (round 4 D7) | 4 |
| `KeyFilter.consume(keyCode, action, foregroundPkg, cameraPkgs)` | VOLUME_UP / VOLUME_DOWN, down and up → consumed; the same with `foregroundPkg` in `cameraPkgs` (the packages with an activity for `INTENT_ACTION_STILL_IMAGE_CAMERA`) → passed at once; `app.tileshell` in front → consumed although its Camera is in that set; every other key → passed at once (round 4 D8) | 4 |
| `PullRules` | appears at 138 epx; handle = finger − 10.7 epx; holds while still; release (Y6): ≥ 50 % opens, < 50 % closes, a downward fling ≥ the minimum opens from any height, an upward flick closes from any height; a cancel while dragging settles by the same rule (a cancel while fully open: `CloseRules`) | 5 |
| `SettleCurve` | 290 ms; stationary release on cubic-bezier(0.67, 0.13, 0.30, 0.88); a flick (Y5) starts at the release speed and ends at 0 | 5 |
| `ScrimRule.alpha(open)` | R7 §4.1.8's six points (range midpoints), linear between (Y7); 0 at 0 %; 0.92 from 66 % up | 5 |
| `ListOffset` | during the drag 3.3 epx at the first frame → 8.7 at 78 %, held above (Y13); after release min(offset at release, 0.062 × remaining) | 5 |
| `CloseRules` | dead zone 35 epx; content gone 110 ms after the handle (ease-in), app back 260 ms after; tap wipe 216 ms; Back → the drag-up release (Y8); a notification tap → the wipe; a sent action or reply whose `PendingIntent.isActivity()` → the wipe (`action`), a broadcast or service one → stays open (round 4 D6); fully open + a cancel the panel did not cause → the Back sequence (`system`); fully open + a window-state event for another package's activity → the same; an input-method window's or the shell's own overlay's event → stays open (round 4 D9) | 5 |
| `ExpandMotion` | expand 200 ms on cubic-bezier(0.34, 0.17, 0.58, 1.06); collapse 283 ms on (0.52, 0.19, 0.15, 1.00); the list moves 188 epx for three added rows | 5 |
| `AcGeometry(pxPerEpx)` | A19's values at 2, 3 and 4 px/epx; the grid's x and y for each cell | 5 |
| `QuickDefaults` | the 16 in C1's fixed list order; the default 15 in C2's order, VPN off | 6 |
| `QuickGrid.layout(order, columns)` | 15 → four rows from the bottom, the top row Flashlight, Note, Location packed left; 16 → four full rows; 4 → one row, no Clear all / Expand row; 5 → one item on top; collapsed = the first four | 6 |
| `QuickAvailability.offered(id, hasFlash, castResolves)` | Flashlight only with a flash; Connect only when the cast action resolves; the rest always | 6 |
| `QuickPrefs` | order and switches round-trip; an unknown id dropped; an action newly offered appended; an action switched on appended (Decisions "Two picks"); one no longer offered hidden, kept | 6 |
| `QuickMechanism.of(id)` | each of the 16 → helper, shell (torch, rotation, brightness, quiet hours), open (its intent, C4 and item 12) or state-and-open | 6 |
| `HelperTileRule.onTap(radio, helperState, wifiOn, bssidTrust)` | running → the verb; startable on a trusted BSSID → start, then the verb, the panel open; startable on a BSSID not trusted or unknown (the start writes `adb_wifi_enabled=1` and Android's dialog follows) → close the panel with the tap wipe first, the tile "Starting…", then start and the verb (round 4 D5); not set up, whatever the Wi-Fi → the helper's page (`SettingsPage.HELPER`) and the Y16 line, the panel closed with the tap wipe (Q-04-11); no Wi-Fi (and Wi-Fi with Wi-Fi off), pairing gone → Android's control (Panel.ACTION_WIFI, Panel.ACTION_INTERNET_CONNECTIVITY, ACTION_BLUETOOTH_SETTINGS, ACTION_AIRPLANE_MODE_SETTINGS) and the Y16 line | 6 |
| `QuietHoursRule.onTap(globalFilter, ourRuleActive)` | off → our rule on; on by our rule → off; on by another source → `ACTION_ZEN_MODE_PRIORITY_SETTINGS` and its line | 6 |
| `BrightnessCycle.next(mode, level)` | Automatic → 25 → 50 → 75 → 100 → Automatic; a level between steps → the next step up | 6 |
| `RotationRule` | lock on → `accelerometer_rotation` 0; off → 1 | 6 |
| `ShadeRules.groups(list)` | by package, most recent first; a group summary with children hidden, its children shown; a lone summary shown; ongoing shown, not clearable | 7 |
| `ShadeRules.clearAll(list)` | the clearable keys only | 7 |
| `SwipeRules` | dim to 0.38 at ≥ 24 % of the width, full below (Y9); released past 50 % or flung right → dismissed; else snaps back; left swipe → nothing | 7 |
| `NotifText.shown(raw, max)` | control and bidi characters removed; title and preview capped; empty → empty | 7 |
| `NotifLine` (every `[notif]` line builder) | package and counts only; never a title, a text or a reply | 7 |
| `VolumeRules` | target: a call → voice call, music active → music, else ring (Y19); step: one per key-down incl. repeats, clamped, voice call never to 0; a ringer change into or out of silent without Do Not Disturb access → refused with its line; the media section shown when a routed session plays at show time, kept after a pause from the panel, faded when the second row opens; the track title left out while the device is locked (`isDeviceLocked`), shown with a swipe lock, none, or once unlocked, the buttons either way (Q-04-13) | 8 |
| `VolumeMotion` | show: first frame at 68 % of rest, peak +12.3 epx at ≈150 ms on cubic-bezier(0.06, 0.99, 0.42, 0.99), rest at 217 ms; hide instant; slide-away 134 ms on (0.47, 0.25, 0.70, 1.09); the media section's 333-ms extend (Y15's fit) | 8 |
| `VolumeTimeout` | hide 3.1 s after the last change (Y11); a key or a touch on the panel restarts it | 8 |
| `VolumeGeometry(pxPerEpx)` | A20's values at 2, 3 and 4 px/epx; the thumb at value / max along the 226-epx track | 8 |
| `FluentWindowRule.decide(state, crossWindowBlurEnabled)` | phase 13's state off → off with its reason; on + enabled → on; on + disabled → off, `unsupported` | 9 |
| `FluentMaterial.deriveTint` (this phase's fills) | (55,55,55) over (0,0,0) → (69,69,69); (0,0,0) over any non-black backdrop → none (solid) | 9 |
| `CommandMatcher` (the switch phrases) | "turn off wifi", "turn wi-fi off", "switch bluetooth on", "enable mobile data", "turn off cellular data", "turn on airplane mode" → `SetRadio`; "open wifi" stays `OpenApp`; "turn on the lights" → not understood; "play …" untouched | 10 |
| `LockGate.allowedWhileLocked` / `.restate` | `SetRadio` → false for all four radios both ways; restate "Turn Wi-Fi off" and the rest; every existing case unchanged | 10 |
| `RadioReply.text(radio, on, outcome)` | done; already so; starting with Android's dialog first (Y16's "Starting…" line; round 4 D5); Android's control opened, with the reason (Y16); the helper's page opened while it is not set up (Y16, Q-04-11) | 10 |
| `HelperChecklist.rows(state)` | `helper`, `usb_debugging`, `wireless_debugging` states and lines; all `grant = false` | 12 |
| phase 12's why-lines test | the three new steps' lines exactly | 12 |

### Dropped, and why (neither on the device nor in a JVM test)
The ids in this list are the 2026-09-16 cut's own (in git, commit d29273259 and earlier) and are named nowhere else in this doc.
- Old E1 (the service enabled by adb, then a pull) — A1 and A2 do both as a person would.
- Old E2 (real apps' SMS notifications through `adb emu sms send`) — A5 with qa-notify and `cmd notification post`: the same listener
  path, with actions and a reply the stock Messaging app's fixture could not give on demand.
- Old E3 (volume keys, the stream changed) — A6.
- Old E4 (a)–(g), every motion measured from 60-fps screenrecord frames — the owner's one-round rule (INDEX Change Log 2026-10-07
  08:29). Every R7 value is a constant or curve in a pure rule with its JVM test (`PullRules`, `SettleCurve`, `ScrimRule`,
  `ListOffset`, `CloseRules`, `ExpandMotion`, `SwipeRules`, `VolumeMotion`), and the feel is judged on the phone in H1 and H8–H18;
  an AVD capture would have measured the emulator's compositor, at the cost of a long row per motion. Old E4 (b)'s bracket of Y6
  (releases at 40 % and 60 %) — `PullRules`, H9.
- Old P6 (the ≤ 17 ms values again from a phone screen recording, PLAN RV11, `PLAN.md:328-329`: "Timings whose tolerance is ≤ 17
  ms also get a phone P-row") — dropped on the owner's word (Q-04-12, 2026-10-09 12:18): RV11 is set aside for this phase and
  INDEX's Change Log records it. The 290-ms settle, the 216-ms wipe, the 217-ms show and the 134-ms slide-away are pinned by
  `SettleCurve`, `CloseRules` and `VolumeMotion`, and the feel is his on the phone (H1, H8–H18).
- Old E5 (A19 / A20 geometry by dump bounds ± 1.1 epx) — `AcGeometry` and `VolumeGeometry` hold the values; A2 (a), (b) and A6 (e)
  look at each surface once; H5 / H6 judge.
- Old P1 (R4 evidence attached) — done: the R4 section. Old P2–P5 — re-cut as P1–P8 below.
- A call, ringing and screen off for the volume keys — `VolumeRules` and the AOSP facts (Decisions "Queue item 6"); P7 presses real keys.
- Gesture navigation on the AVD — its rule is AOSP's (A3: Android's swipes win over the panel); the owner uses 3-button (phase 01 Q4 B);
  P5 tries it once.
- Bluetooth on the AVD when build task 1 finds no adapter to flip — P2.
- The Y6 stand-in's feel, the scrim in light theme, the 15063 look — H-rows only.

### Phone-only (P1–P8; Jeremy does it on the phone and reports)
- **P1 Set it up.** Install the CI build. Turn the shell's accessibility service on (if One UI refuses — "Restricted setting" — App
  info > ⋮ > Allow restricted settings first; whether One UI 8 asks for a browser-installed APK is UNVERIFIED). In Developer options:
  USB debugging on, Wireless debugging on with "Always allow on this network" ticked on the home Wi-Fi. Open the action center and tap Wi-Fi:
  it reads "Not set up" and opens the helper's page (Q-04-11). Pair, and type the code into the shell's notification. Report: the helper page reads running; the checklist rows; and from Settings >
  Diagnostics the `[helper] started … uid=2000` line.
- **P2 The switches.** From the action center, Wi-Fi, Bluetooth, Cellular and Airplane mode each off and on, checked against Samsung's
  own panel; Connect present or not, and what it opens; then Tess, unlocked ("turn off bluetooth") and locked (the Unlock card, the
  switch after unlock). Then the actions the shell does itself (round 4 V14): Flashlight on, leave the action center, still lit a
  minute later, then off; Quiet hours on and off (Samsung's panel shows Do Not Disturb each time); Brightness through its steps;
  Rotation lock on and off. Report each.
- **P3 After a reboot.** Restart the phone on the home Wi-Fi, unlock, touch nothing for a minute: the helper page reads running and a
  switch tile works. Restart again with Wi-Fi off: a switch tile reads "No Wi-Fi" and opens Android's control. On another Wi-Fi
  (not "Always allow"): a tap closes the action center and brings Android's "Allow wireless debugging on this network?" dialog on
  top, then the switch (round 4 D5); once more there from Tess ("turn on bluetooth"): her pane closes and the same dialog shows
  (round 4 V15).
- **P4 One switch off.** Wireless debugging off (or leave Wi-Fi), USB debugging still on: five minutes later a switch tile works. Then
  USB debugging off too: the helper page reads "Debugging off" and the tiles fall back (R4 4d). Turn both back on.
- **P5 The nav bar (decides Q-04-4's fallback).** With 3-button navigation open the action center fully and take a screenshot: are
  Samsung's three nav glyphs drawn over the panel's foot, or is it the panel's black with the handle? Then once with gesture navigation:
  a swipe up from the bottom over the open panel goes Home and the panel is gone (not left open over Home; round 4 D9). Send the
  screenshot.
- **P6 The top edge and a real notification.** In an app: a slow pull from the very top edge opens the W10M action center, also
  right beside the camera's punch hole (the strip is laid out over the cutout, round 4 D7); a quick flick from the very top
  opens Samsung's panel (or not — report which); on the lock screen only Samsung's; in a full-screen video a slow pull. Then reply to a
  real message from the action center, use an app's button (e.g. Mark as read), and swipe one away.
- **P7 Real volume keys.** In an app, up and down: the W10M panel, not Samsung's; with music playing in any app, the media section and
  its buttons; on the lock screen with the screen on, with music playing (the buttons, no song title; Q-04-13); with the ringer on silent, and with Do Not Disturb on (round 4 V15); in the
  camera app, where the volume key still takes a photo (round 4 D8); during a call; while a call rings; with the screen off
  (Samsung's own behaviour is expected for the last two, A5.3). Report what showed each time.
- **P8 Blur.** Power saving off by hand; press a volume key over a bright app: is the panel blurred? Then Power saving on: solid. From
  Settings > Diagnostics the `[fluent] volume_panel form=…` line each time.

### NEEDS-HUMAN (H1–H24)
- **H1** *accept* — the action center's and the volume panel's feel, every R7 motion as built (the dropped E4's values).
- **H2** *accept* — living beside Samsung's shade: the fast flick, the lock screen, `GLOBAL_ACTION_NOTIFICATIONS` (P6).
- **H3** *accept* — any approximation not covered by H4–H24.
- **H4** *accept* — notification item text (Y1). **H5** — action center geometry and motion on the final release (Y2).
  **H6** — volume panel geometry and motion on the final release (Y3). **H7** — the helper's state UI and page (Y4).
- **H8** — settle after a flick (Y5). **H9** — the open / close threshold (Y6). **H10** — the scrim between points and in light
  theme (Y7). **H11** — closing by Back or a notification tap (Y8). **H12** — notification dim point, dismiss threshold, slide-out
  and reflow (Y9).
- **H13** — the volume slider row's entrance (Y10). **H14** — the timeout and the navigations that slide it away (Y11). **H15** — the
  second slider row (Y12). **H16** — the list offset during the drag and after an early release (Y13).
- **H17** — an expanded notification and its reply box (Y14). **H18** — the volume panel's media section (Y15). **H19** — the
  helper-down line and Tess's words (Y16). **H20** — where a pull starts (Y17). **H21** — Brightness's steps and label (Y18).
  **H22** — the volume rows' streams and the "Vibrate on" footer (Y19). **H23** — the quick-actions settings page (Y20).
  **H24** — the action center in landscape (Y21).

The "(H20)" in Q-04-7's line is phase 03's H20 (the drawn bar's hold uses Android's long-press timeout, which the Back hold
shares), not this doc's H20.

qa/phase-04/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's shape.

### Diagnostics lines asserted on the device
`[ac] service connected` (every row: after provision.sh, after `a11y_on` and after every uiautomator dump, `a11y_wait`; and after a
reboot with no `a11y_on`, A4 c); `[ac] open` and `closed by back` / `flick` / `notification` (A2, A5), and no `[ac] closed` line
after Ack (A5 b); `[ac] strip removed (screen off)` and `strip added` (A2 f, A4 c); `[bars] back hold -> recents` (A2); `[helper]
paired`, `started pid=<p> uid=2000`, `verb … -> exit 0`, `wireless debugging back on (trusted network)`, `handoff refused: no token`,
`[ac] quick action wifi -> helper page (not set up)` (A3); `handoff accepted`, `started … (boot)` (A4); `[ac] quick action wifi -> panel (no Wi-Fi)` (A4); `[notif] reply sent (<n>
chars)`, `dismissed` (A5); `[volume] key up stream=…`, `panel hidden (timeout)` (A6); `[fluent] volume_panel form=acrylic|fallback`
and `unsupported` (A6, P8); `[cortana] radio … -> helper`, `locked: SetRadio(…) gated` (A7); `[wizard] not shown: core held` /
`finished` (A1). Every other line of this phase is not asserted on the device: its builder is JVM-tested (`NotifLine`,
`HelperState.reason`, `RadioReply.text`).

## Edge cases
Re-cut 2026-10-09 to the rulings; each names where it is checked — a device row (A<n>), a phone row (P<n>), a JVM rule, or
"unasserted — <why>".
- The helper after a reboot on a trusted Wi-Fi (A4 c; P3), with Wi-Fi off or on an unknown network (no write by itself; one tap:
  `TrustedNetworks.shouldEnable`, `RestartPlan.next`; P3), with Location off or no precise location (the BSSID unknown, the state
  "Location off": `HelperState.reason`; round 4 D12 b), and before the first unlock (nothing until unlock: the adb key is in
  credential-encrypted storage; unasserted).
- Wireless debugging turned off with USB debugging on (A4 b; P4); both off, or Developer options off (the helper dies, R4 4d / B1, and
  nothing restarts it until they are back: `RestartPlan.next`; P4); "Revoke USB debugging authorisations" (adbd restarts, the helper
  dies and the keys go — pair again; unasserted: it would revoke the host's own adb on the AVD).
- The adb key: with `adb_allowed_connection_time` 0 it never expires (B3); left at the default it expires 7 days after pairing whatever
  its use (`RestartPlan.next` → pairing gone, "Pair again", Android's control on a tap (Q-04-2, Q-04-11); unasserted on the device — seven days).
- The shell's APK updated while the helper runs: MY_PACKAGE_REPLACED, a new helper started first and the old one told to exit only
  after the new one's ping answers; away from a trusted Wi-Fi the old one keeps serving (`HelperVersion.mustRestart`; round 4 D4 c;
  unasserted on the device — one APK per round). A `pm clear` under a running helper: its token is gone, its next handoff is refused
  and told to exit (`HandoffRule.accept`; A3 step 1 kills it first).
- Samsung's auto restart and Device care: a reboot, covered by the restart path (P3); the helper itself is exempt from Doze and
  standby (B4).
- Helper trust: another app reaching the door (refused by uid: `HandoffRule.accept`), the adb shell planting a binder (one that does
  not look is refused by the token, A3 d; one that reads the token off the start line, `ps -A -o ARGS`, is refused by the pid pin,
  `HandoffRule.accept` — round 4 D3), a verb outside the list (`HelperVerbs`), a content-provider call (none: the static grep; phase
  09 rejects the helper's pid there), the shell re-signed under the running helper (`HelperDoorRule.client` → exit), another uid
  calling the binder (`CallerRule.allowed`) — all in the GATE review.
- The accessibility service turned off by the user, or by a force stop (Decisions "How the rows see the overlays"): the strip and
  the volume panel go, Samsung's panels return, the checklist row reads missing (A1 d), and the shell never turns it back on (A4 a,
  read before `a11y_on`); a crash is rebound by the system (unasserted). Any UiAutomation without
  `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` hides the overlays while connected (the harness rule; unasserted beyond it).
- The lock screen: the strip is removed while the keyguard shows, secure or swipe (A2 f; `StripRule.present`); an app shown over the
  keyguard (an incoming call, phase 15's alarm) keeps it removed (the same rule); the volume panel and its media controls do show
  there (A2 f; Decisions; P7), without the track title while the device is locked (A6 f; Q-04-13).
- Permission dialogs and Settings: accessibility overlays are not hidden by Android's hide-overlays rule (A1), so the strip stays and
  a pull works over them (unasserted).
- A fast flick from the very top goes to Android's shade (A4 of the addendum; P6); immersive apps show their bars briefly while the
  strip keeps the pull (P6); an app with a touch target under the status bar loses that strip of taps (Y17; H20); a punch-hole
  cutout: the strip is laid out over it from y = 0 (build task 1 l; P6; round 4 D7).
- Landscape apps and rotation while the panel is open (Y21; H24; unasserted on the AVD).
- The nav bar: covered when open (A2 a, d; P5 decides the fallback); with gesture navigation Android's home and back swipes win and
  the panel gets a cancel, which while fully open closes it by the Back sequence (`[ac] closed by system`, `CloseRules`; A3; P5;
  round 4 D9); recents or another app's activity coming up over the open panel closes it the same way (`CloseRules`); the navigation
  mode changing while the panel is open (the panel stays the full display; unasserted).
- A finger lifted mid-drag by a system gesture or a call (a cancel): the panel settles by Y6 from where it is (`PullRules`).
- Notifications: removed by their app while expanded (the item closes: `ShadeRules.groups`); an action or reply that starts an
  activity closes the panel, any other leaves it open (`CloseRules`; A5 b; round 4 D6); a cancelled PendingIntent (`[notif]
  action failed`, the panel stays; unasserted); ongoing ones (A5 e); a reply the app then answers by updating ("Sent" replaced, Y14);
  posted while the panel is open (the list updates; unasserted); the keyboard over the reply box (A5 b).
- Volume keys in a camera app (passed through, Android's panel: `KeyFilter.consume`; P7; round 4 D8), in a call (`VolumeRules`; P7),
  while ringing or with the screen off (the service never sees them, A5.3; P7), with music (A6 d), with the ringer at silent or Do
  Not Disturb on (`VolumeRules`; P7), held down (repeats, `VolumeRules`).
- The media section: a session that stops while the panel shows (the section stays until the panel hides: `VolumeRules`); nothing
  playing at show time (no section: A6 b); the Recorder's own session (routed to no tile, so never shown — `MusicFeed`'s rule).
- Blur turning on or off while the volume panel shows (Power saving, `disable_window_blurs`): the next frame redraws in the other form
  (`FluentWindowRule.decide`; A6 e; P8).
- Tess: locked (A7 b), helper down (A7 c), on an untrusted Wi-Fi (her pane closes and Android's dialog shows first:
  `RadioReply.text`; P3; round 4 D5, V15).
- Process death of the launcher while the panel is open: the overlays go with it; the system rebinds the service and the strip comes
  back (unasserted). Competing accessibility overlays from another service (stacking order by the platform; unasserted).
- Liveness (N-01): the service and the strip after a reboot (A4 c), the helper after the shell's process is killed (A4 a).

## QA evidence
_None yet._ Evidence goes under qa/phase-04/ (never committed).
