---
phase: 04
slug: action-center-volume-helper
status: DRAFT
depends-on: [01, 03]
---

# Phase 04 — W10M action center, volume panel and the in-APK privileged helper

## Goal
A W10M (final release) action center overlay, drawn through an accessibility service, pulls down from the top edge with collapsed
and expanded quick-action grids, customisable quick actions, and a notification list (tap / dismiss) from phase 01's listener. Wi-Fi,
Bluetooth, mobile data and airplane mode flip directly through a Shizuku-style privileged helper that ships inside the shell APK and
restarts after reboot with no manual steps where the platform allows it. A W10M volume panel replaces the stock one. Cortana gains
the toggle commands. The helper's state is visible in the action center, in Settings and in the checklist.

## Scope
**In:** R4 spike result as the entry gate (including the T-Mobile visual voicemail probe, PQ2, and the nav-bar probe, R7 §4.1.10); accessibility service + overlay windows (RV10 density); action center UI (R3 geometry) + finger-tracked motion (R7); quick actions incl. customisation; notification list with swipe to dismiss; privileged helper (start, daemonise, binder handoff, restart path); volume panel via accessibility key events (R3 geometry, R7 motion); Cortana toggle commands (added to phase 03's matcher); Settings pages + checklist rows (accessibility, overlay, Developer options, Wireless debugging pairing, WRITE_SECURE_SETTINGS grant, helper running).
**Out (explicitly):** replacing the lock screen, status bar or Samsung settings pages (CANNOT); any separately installed app, including Shizuku (RV1, Q13); glance (07).

## Decisions
- 2026-09-16: Privileged helper built into the shell APK, started over Wireless debugging; RV1 holds (Q13, Jeremy)
- 2026-09-16: The phase opens only after the R4 feasibility + licence spike passes; failure re-asks Jeremy, no silent fallback (Q13, R3F-02)
- 2026-09-16: Seamless restart path is attempted before settling for one-tap restart (Q13, P2, Jeremy)
- 2026-09-16: Toggle commands for Cortana are added here (split, agent under P3)
- 2026-09-16: Trust-touching part (the helper is code running at the shell uid, reachable over a binder handoff): adversarial team review is mandatory before `done`. The handoff verifies the client's uid and signature before handing out the binder; the helper accepts only an allow-listed verb set (no generic shell); the helper's shell uid would pass phase 09's adb-shell-uid provider check, so the allow-list contains no content-provider call, and phase 09's provider additionally rejects the helper's own process (a phase 09 decision, tested there, since that provider does not exist during this phase's QA). R4's spike proves the handoff check and the allow-list (review F1-M10, agent; provider requirement moved to phase 09 by review T-M21 / R2-m4)
- 2026-09-16: Action center geometry (R3 A19, MEDIUM, 14393 S1 I98ENfXJRqA @ 72.0 s expanded and 452 s collapsed, ± 1.1 epx; 15063 not filmed, H5): quick-action tiles 85 × 60.5 epx at an 89 / 64 epx column / row pitch (gaps 4 / 3.4 epx), 4 × 4 expanded and 4 × 1 collapsed, side margins 4 / 4.5 epx; grid top 64 epx expanded and 77 epx collapsed; a second status line (battery % and date) under the status bar, ending 38 epx from the top; tile glyph ≈13 epx at ≈12 epx from the tile's left and top; tile label in the 12-epx caption class at the bottom-left, inset ≈5 epx, baseline ≈8 epx above the bottom; "Clear all" (left) and "Collapse" (right) with a 9.6-epx cap, 12-epx side margins, 24 epx below the grid; a notification group header with a 21-epx app icon and the app name; notification items with a 51-epx circular avatar; a full-width 13-epx accent drag handle with an "=" grip at the bottom edge; colours as captured: tile (48,48,48), active tile accent, background black. Measured by E5 (review T-B5 / F1-M12, agent)
- 2026-09-16: Volume panel geometry (R3 A20, MEDIUM, 14393 S1 @ 228.5 s, ± 1 epx; 15063 not filmed, H6), captured in the two-slider state with media playing (state named by review R3T-M9): full width from the screen top to 274 epx, background (55,55,55) as captured; each stream row has its title in the 15-epx body class at x = 12 epx, a 29-epx glyph at x 12–39 epx, a slider track 2 epx thick from x 67 to 293 epx (unfilled (103,103,103), filled accent) with an accent thumb 6.4 × 20.4 epx, and its value in 21-epx-tall digits right-aligned at x 347 epx; the first track sits 48.7 epx from the top and the second row 100 epx lower; a "Vibrate on" caption (8.6-epx cap) at 217–225 epx, x 12 epx, and a chevron at x ≈340 epx. Measured by E5 (review T-B5 / F1-M12, agent)
- 2026-09-16: Motion source: R3 measured no action-center or volume motion (R3 §E), so E4's old "(R3) tolerance" pointed at nothing. R7 (docs/plan/r7-measurements.md §4) now supplies the pull-down, close, expand / collapse, notification swipe and volume panel motion in the R7 lines below; E4 measures them, and every value R7 leaves LOW or UNMEASURED is a row in Approximations (Y5–Y13) with its own H-row. This phase stays DRAFT until its post-R4 interview and does not freeze with the other phases (review T-B5 / F1-M12, agent; resolved by R7)
- 2026-09-16: Per-value approximations (agent): the rows in Approximations below replace the generic "accept approximations" row; each has its own H-row (review F1-M12 / T-B5, agent)
- 2026-09-17: Input paths (agent, from AOSP `InputDispatcher.cpp`): the volume panel reads volume keys through the accessibility service's key filtering (`onKeyEvent`), which sees only keys from a real input device, because `injectInputEvent` never calls the input filter; so `adb shell input keyevent KEYCODE_VOLUME_UP` reaches the audio service and shows the stock panel. Emulator rows therefore press volume keys through the emulator's virtual input device (`adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_SYN:0:0`, then `adb emu event send EV_KEY:KEY_VOLUMEUP:0 EV_SYN:0:0`), and the phone uses the physical key. The action center's top-edge strip and panel are overlay windows that receive touches directly, not through accessibility motion-event filtering, so `adb shell input motionevent` reaches them (review R3T-B1, agent)
- 2026-09-16: Action center pull-down (R7 §4.1; 14393 A1 I98ENfXJRqA 60-fps screen recording, distances ± 1.1 epx, timings ± 17 ms; the 15063 A3 9roLX1b1V-4 cross-check agrees within its slow refresh, R7 §4.6.1 LOW, Y2): a finger-following drag, not a fixed animation fired by a swipe. The panel grows down from the top edge as a clip with the accent handle (geometry above) riding its bottom edge, and the quick-action grid does not move inside it (§4.1.1, HIGH). It first appears once the finger is 124–152 epx below the top edge (§4.1.3, MEDIUM), then tracks the finger 1:1 with no resistance and holds any partial position while the finger rests, the handle's bottom 10.7 ± 1.1 epx above the touch point (§4.1.2, HIGH). "Open %" in this doc is the handle's bottom edge as a fraction of the screen height, R7's definition (review R3T-M8). The notification list is drawn below its final place while the panel is not fully open (§4.1.9, MEDIUM), in two phases (split by review R3T-B2): during the drag the offset stays between 3.3 and 8.7 epx (R7 o128: +4.0 px with the handle at 53 % open, rising to +8.1 px while held at 78 %; o244 and o197 +3.8 and +3.1 px; 1.072 epx per px), its shape between those points being approximation Y13; after release it equals 0.062 (0.060–0.064) × the handle's remaining travel (o128, 11 frames), which R7 measured only after a release at 78 %, where the two phases meet; after an earlier release, where that product would exceed the drag offset, approximation Y13 applies. On release the panel runs a fixed-duration settle of 290 ms (250–317 ± 17 ms measured) whatever distance is left (§4.1.4, MEDIUM); after a slow or stationary release the curve is cubic-bezier(0.67, 0.13, 0.30, 0.88), max residual 2.1 px of 132 px (§4.1.5, MEDIUM). The curve after a flick and the release threshold are Y5 and Y6. Background dim in dark theme follows how far the panel is open, not time (§4.1.8, HIGH form, MEDIUM values): at these open fractions of the screen height the app below keeps this fraction of its brightness: 18 % → 0.56–0.57, 29–32 % → 0.25–0.29, 48–52 % → 0.22–0.25, 57–59 % → 0.16–0.20, 66 % → 0.08, 89 % → 0.09–0.12. It is drawn as a black scrim with alpha 1 − that fraction; the curve between those points and light theme are Y7. The open panel covered W10M's nav bar (§4.1.10, MEDIUM; one 15063-era capture, A4, shows the handle above a visible nav bar). Whether an accessibility overlay can cover Android's nav bar is UNVERIFIED: R4 probes it (P5), and interview item 8 takes the result. Measured by E4 (R7, agent)
- 2026-09-16: Action center close (R7 §4.2; 14393 A1, ± 1.1 epx, ± 17 ms). Drag up: the handle stays put until the finger has moved 32–38 epx up, then follows it 1:1 (§4.2.1, MEDIUM). An upward flick closes the panel from any height (§4.2.2, HIGH): the handle keeps rising for 1–3 frames and disappears. The panel content (quick actions and notifications) then drifts up while fading out, accelerating (ease-in), and is gone 110 ms (100–133 ms) after the handle (§4.2.3, HIGH). The app below is back to full brightness 260 ms (233–317 ms) after the handle is gone (§4.2.4, MEDIUM). Tap on a quick action that launches an app (e.g. All settings), one timeline from R7's frames (rewritten by review R3T-B3): the handle vanishes first; within 50 ms the panel background's lower edge is already moving up (c73: 490 px, 107 px above the 597-px rest, 50 ms after the handle frame), and it wipes to the top at a roughly constant speed; the background is gone 216 ± 17 ms after the handle vanished (c73: 73.367 → 73.583 s). c245, the second event, has no handle frame in R7; its wipe from the first moving frame to gone took 183 ms, so 183–200 ms is the wipe's own length, not a handle-to-gone time. The content drifts 20–27 epx up while fading, and the scrim fades as the wiped edge rises, reaching 0 at the top (§4.2.5–4.2.6, MEDIUM); the app's own launch follows. Closing by Back or by tapping a notification is Y8. Measured by E4 (R7, agent)
- 2026-09-16: Quick actions expand / collapse (R7 §4.3; 14393 A1, ± 33 ms): the collapsed row stays as the bottom row of the expanded grid and the added rows appear above it, entering clipped under the status area. "Expand" becomes "Collapse" and back, and the notification list moves as a block, 188 epx for the 4 × 4 grid's 3 added rows (§4.3.1, §4.3.4, HIGH). Expand takes 200 ± 33 ms with cubic-bezier(0.34, 0.17, 0.58, 1.06); collapse takes 283 ± 33 ms with cubic-bezier(0.52, 0.19, 0.15, 1.00) (§4.3.2–4.3.3, MEDIUM). Measured by E4 (R7, agent)
- 2026-09-16: Notification swipe (R7 §4.4; 14393 A1 60 fps and A2 yi7zzB3W6Qk, ± 133 ms): dragging a notification group right moves the whole group (header and items) 1:1 with the finger, keeping the grab offset, and it follows the finger back (§4.4.1, HIGH). The group drops to 0.36–0.41 of its brightness once it is 19–28 % of the screen width from rest, and is back to full brightness under 14 % (§4.4.2, HIGH; the step point inside that bracket is Y9). Candidates (LOW, R7 §4.4.3): released at 73 % of the width it is dismissed; released at 28–30 % it snaps back. The dismiss threshold, slide-out, reflow of the groups below, and left swipe are Y9 (H12). Measured by E4 (R7, agent)
- 2026-09-16: Volume panel motion (R7 §4.5; 14393 A1, ± 1.1 epx, ± 17 ms; 15063 UNMEASURED, Y3). Show: the panel grows down from the top edge and overshoots. Its lower edge passes its rest position (100.4 epx with one slider) to a peak 12.3 epx lower (+12 %) about 150 ms in, easing with cubic-bezier(0.06, 0.99, 0.42, 0.99) (the first frame is already at 68 % of rest), then returns to rest in 67 ms, 217 ± 17 ms in total. The app below neither moves nor dims (§4.5.1, MEDIUM). Timeout hide: instant, gone in one frame with nothing moving or fading (§4.5.3, MEDIUM); the timeout length is Y11. Leaving the page while it shows: the whole panel slides up off the top as a block in 134 ± 17 ms, close to linear, cubic-bezier(0.47, 0.25, 0.70, 1.09) (§4.5.5, MEDIUM, measured on a Start-key press; the other navigations are Y11). The slider row's entrance and the second slider's slide-down are Y10 and Y12. Measured by E4 (R7, agent)

## R4 spike — results so far (2026-09-21)

R4 is this phase's entry gate. Its parts are being run as they become runnable; this section is the
record PLAN.md's item 1 asks for. Nothing here opens the phase on its own.

### Part 5, the licence of the on-device ADB pairing + TLS client — PASSES (agent, 2026-09-21)

R4's failure condition was "GPL = not usable". It does not bite: there are at least four non-GPL
implementations, and the most directly on-point one does the hard part.

| Library | Licence | Why it matters here |
|---|---|---|
| adb-kt (rhythmcache) | Apache-2.0 | Pure Kotlin, TLS 1.3, and **SPAKE2 password-authenticated wireless pairing** — the pairing half, which is the part that is awkward to write |
| dadb (mobile-dev-inc) | Apache-2.0 | Connects to a device with no adb binary and no adb server |
| Kadb (flyfishxu) | Apache-2.0 | Kotlin Multiplatform ADB client |
| adblib (tananaev) | BSD-3-Clause | Java ADB network protocol |
| libadb-android (MuntashirAkon) | **dual GPL-3.0-or-later OR Apache-2.0** | The one the spike was worried about. It can be taken under Apache-2.0, so even this is usable |

The worry behind R4's wording was that the only mature on-device ADB library — the one App Manager uses
— is GPL. It is dual-licensed, and it is not the only one. Nothing about the licence re-asks Q13.

Sources: github.com/rhythmcache/adb-kt, github.com/mobile-dev-inc/dadb, github.com/MuntashirAkon/libadb-android,
github.com/tananaev/adblib, klibs.io/project/flyfishxu/Kadb.

### NEW RISK, not in the plan: Google may restrict on-device ADB entirely (agent, 2026-09-21)

Found while running part 5, and it is bigger than the licence question it came from.

Google's ADB maintainer has **proposed binding adbd to the Wi-Fi interface only (`wlan0`), dropping the
loopback (`127.0.0.1`) path** that every on-device ADB app uses — including a Shizuku-style helper
paired over Wireless debugging, which is exactly this phase's mechanism (RV1, Q13). The stated reason
is that the localhost socket has been an escalation route, following CVE-2026-0073, a Wireless ADB
authentication bypass.

Status as of the source's publication, 2026-07-20: a **feature request under discussion**, Google
IssueTracker #526109803. No AOSP commit, no target version, no timeline, no workaround offered. A
second issue, #541312863, covers legacy TCP/IP mode needing persistent Wi-Fi.

Why it belongs in this doc rather than a footnote: if it lands, the helper cannot start the way this
phase says it starts, and that is R4's own "failure re-asks Jeremy (Q13)" condition — not because the
spike failed, but because the platform moved under it. It does not block the phase today. It does mean
R4's device parts should be run and recorded SOON, while the path still exists, and that the helper's
design should not assume loopback ADB is permanent.

Source: kitsumed.github.io/blog/posts/android-may-soon-restrict-on-device-adb (2026-07-20), citing
Google IssueTracker #526109803 and #541312863.

### P5, the nav-bar probe — ANSWERED for 3-button (agent + Jeremy's phone, 2026-09-22)

Evidence, including the screenshot that settles it: qa/phase-04/P5/.

An overlay **can** occupy the nav bar's strip, but it has to ask: with the default flags a window is
fitted to the system-bar insets, so MATCH_PARENT gives it the leftover space and it comes back exactly
the nav bar short. With setFitInsetsTypes(0) and an explicit full-height size it is laid out to the
whole display (1080 x 2340, 0 px short) and reports the 144 px inset from inside itself.

**But Android keeps drawing its nav glyphs on top.** The screenshot of that full-height overlay shows
its tint over the bottom 144 px with the three nav glyphs still painted above it: a non-privileged
overlay sits below the navigation bar in the window layer order.

So interview item 8's choice is NOT "covered or not covered". W10M's behaviour — the open action
center covering its nav bar — is not available at all. The real choice is between a panel that ends
above the buttons and a panel whose background runs behind them while the buttons stay visible.

Not yet established, and it decides whether "runs behind them" is even usable: whether a TOUCH in that
strip reaches the overlay or the nav bar. The glyphs drawing on top strongly suggests the nav bar takes
the touches too, which would make any content placed there dead. The probe measures that next rather
than assuming it.

Still to run for P5: the same two attempts with gesture navigation (navigation_mode = 2). W10M had no
gesture mode, so that result decides whether the panel's bottom edge is one design or two.

### Parts still to run — all need the phone on adb

1. `app_process` under the shell uid on One UI 8, and daemonising
2. the binder handoff to the app, with its uid + signature check
3. each toggle (Wi-Fi, Bluetooth, mobile data, airplane) via the shell uid
4. survival when Wireless debugging is switched off, and when the app is killed
6. PQ2: T-Mobile visual voicemail (mstore API + GBA SIM auth)
7. P5: whether a full-height accessibility overlay draws over Android's nav bar, with gesture
   navigation (`settings get secure navigation_mode` = 2) and with 3-button (= 0), restoring the
   setting afterwards

## Interview queue (Stage A step 4)
1. [agent] Run R4 on the S25 Ultra first; record the result here
2. [Jeremy] Action center everywhere (top-edge strip over all apps) or only on Start (M5)
3. [Jeremy] Quick actions list and default order (per-toggle matrix from R4)
4. [Jeremy] What the Wi-Fi tile does when the helper isn't running (default: "helper not running, tap to start") (R3F-01)
5. [Jeremy] Notification actions in the action center (inline reply or tap-through only)
6. [agent] Volume QA matrix (R2-13); overlay window types
7. [Jeremy] W10M long-press Back on the drawn Start bar: open Android's recents through the accessibility service, or nothing (from phase 01 Q4). Fidelity default to offer when asked: W10M's press-and-hold Back opened the app switcher (R6 §4.1.6, MEDIUM, camera footage with Microsoft's user guide l.437), so opening recents is the W10M behaviour (review R2-m12) — 2026-09-17: while trying the phase 01 emulator build Jeremy asked "was there not a button to show all apps currently running?" (W10M: hold Back); ask this item with that context, lean = open Android's recents on hold-Back
8. [agent] Record R4's nav-bar probe here (P5): can the accessibility overlay draw over Android's nav bar with gesture navigation and with 3-button navigation? W10M's open action center covered its nav bar (R7 §4.1.10). If the overlay can't, Jeremy is asked how the open panel should meet the nav bar. The default to offer is that it stops at the nav bar's top edge with the handle above it, the form one 15063-era capture shows (R7 §4.1.10, A4)
9. [Jeremy] Media transport controls in the volume panel while music plays: W10M showed them in a section that extends under the slider, ease-out, ≈333 ms, with the track title fading in (R7 §4.5.6, LOW, 14393)
10. [Jeremy] Toggle commands on a locked phone (review R3-m4): phase 03's locked gate covers only phase 03's commands, and airplane mode from a locked phone matters if the phone is lost. Ask: "While the phone is locked, should Cortana (a) switch Wi-Fi, Bluetooth, mobile data and airplane mode directly; (b) show 'Unlock to continue' for them; or (c) other / let me clarify?"

11. [Jeremy — likely answered 2026-09-23 by phase 13 Q1 ("A": a measured fill too dark to show anything through stays solid, so the captured black stays); confirm at 04's interview] Acrylic behind the action center (added 2026-09-22 at the phase 11-19 split, from phase 13's writer): A19 records the panel background "(0,0,0) as captured", which no acrylic tint can reproduce over a non-black app, yet R3's source S3 is titled "Windows 10 Mobile Action center blur effect" (10586 or later). Whether W10M's final action center was translucent is unmeasured. Ask which wins: the captured black, phase 13's acrylic, or a re-measure first.
12. [agent] "All settings" target (added 2026-09-22 at the split, from phase 19's writer): E4(d) taps "All settings" as an app launch; once phase 19 exists its target is the W10M Settings front. Settle the row's expectation when 04 is interviewed.

## Build tasks
1. Helper: start, daemonise, binder handoff, restart path, state reporting
2. Accessibility service + overlay host
3. Action center UI, finger-tracked drag, fixed-duration settle, open-linked scrim, close and wipe motion (R7 §4.1–4.2)
4. Quick actions + customisation + expand / collapse motion (R7 §4.3)
5. Notification list + swipe to dismiss (R7 §4.4)
6. Volume panel: overshoot show, instant timeout hide, slide-away on leaving the page (R7 §4.5)
7. Cortana toggle commands
8. Settings pages + checklist rows

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
| Y8 | Close by Back or by tapping a notification | R7 §4.2.7: UNMEASURED | Back runs the drag-up release sequence (§4.2.3–4.2.4: handle gone, content drifts up and fades in 110 ms, app back by 260 ms); a notification tap runs the tap-close wipe (§4.2.5–4.2.6), then its app opens (agent pick) | H11 |
| Y9 | Notification dim step point, dismiss threshold, slide-out, reflow, left swipe | R7 §4.4.2 (step vs ramp not resolved inside 19–28 %); §4.4.3–4.4.4: LOW (73 % dismissed, 28–30 % snapped back, reflow ≤ 133 ms); §4.4.5: UNMEASURED | dim steps to 0.38 at 24 % of the width; released past 50 % of the width or flung right, the group slides off the right edge in 133 ms (ease-in) and the groups below move up in 133 ms (ease-out); otherwise it snaps back in 133 ms (ease-out); a left swipe does nothing (agent pick) | H12 |
| Y10 | Slider row entrance while the volume panel shows | R7 §4.5.2: LOW (the row slides down into the panel behind the growing edge: −6 px, then +2 px, settled ≈233 ms after the first frame) | candidate as measured: the row slides down behind the growing edge, overshoots 2 epx and settles with the panel | H13 |
| Y11 | Volume panel timeout, and which navigations slide it away | R7 §4.5.4: LOW (≈3.1 s after the last on-panel change, one event); §4.5.5 measured only a Start-key press | hides (instantly) 3.1 s after the last volume change; Home, Back and app switches all use the §4.5.5 slide-away (agent pick) | H14 |
| Y12 | Second slider row appearing (chevron tapped) | R7 §4.5.7: LOW (slides down 134 epx from under the first slider, ≈283 ms, ease-out; trigger not visible) | candidate as measured, triggered by the chevron | H15 |
| Y13 | Notification-list offset during the drag, and after a release below 78 % | R7 §4.1.9: +3.3 to +8.7 epx during the drag in three events, no curve; 0.062 × remaining travel measured only after a 78 % release | during the drag the offset rises linearly with open % from 3.3 epx at the panel's first frame to 8.7 epx at 78 % open and holds 8.7 epx above that; after release it is the smaller of the offset at release and 0.062 × the remaining travel, so it never jumps (agent pick, review R3T-B2) | H16 |

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow PLAN RV11; `uiautomator dump` follows RV13.
**Emulator:**
- E1 `adb shell settings put secure enabled_accessibility_services <component>` (or onboarding) enables the service; the pull-down gesture shows the action center (screencap)
- E2 Notifications posted by real apps (`adb emu sms send …`) appear in the list; dismiss removes them from the system too (`adb shell dumpsys notification --noredact`)
- E3 Volume keys pressed through the emulator's virtual input device (`adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_SYN:0:0`, then `adb emu event send EV_KEY:KEY_VOLUMEUP:0 EV_SYN:0:0`; `adb shell input keyevent` skips the accessibility key filter, Decisions) show the W10M panel, not the stock one, and change the stream volume (`adb shell dumpsys audio`)
- E4 Motion, measured against the R7 values in Decisions from 60-fps screenrecord frames (PLAN RV11: `show_touches` on, fps read with ffprobe, pass = source tolerance + one frame), in dark theme: (a) pull-down driven with `adb shell input motionevent DOWN 540 5`, `MOVE` steps, then `UP`: no panel with the finger 120 epx down, a panel at 156 epx. At 30 %, 50 % and 58 % open (screencap at each), the handle bottom is 10.7 ± 1.1 epx above the finger and does not move during a 2 s hold, the app's brightness below the handle is inside R7 §4.1.8's range for that position, and the notification list offset is between 2.2 and 9.8 epx (R7 §4.1.9's 3.3–8.7 epx during the drag ± 1.1 epx; its shape is Y13); (b) `UP` after a 1 s stationary hold at 60 % and at 78 % open settles fully open in 250–317 ms. The 78 % release follows cubic-bezier(0.67, 0.13, 0.30, 0.88) within 1.6 % of its travel (R7 §4.1.5's max residual), and in each of its settle frames the notification list offset is 0.060–0.064 × the handle's remaining travel (± 1.1 epx). Y6's stand-in threshold is bracketed with stationary releases, as a check of the approximation: released at 40 % open the panel closes, at 60 % it opens (feel judged in H9) (review R3T-B2 / R3T-M8); (c) an upward `input swipe` from fully open closes it, with the content gone 100–133 ms and the app settled 233–317 ms after the handle disappears. A held finger moved up leaves the handle still until the finger has travelled 32–38 epx; (d) tapping "All settings" runs the wipe: the handle vanishes first; within 50 ms (+ one frame) of the first frame without the handle the background's lower edge has moved up, and the background is gone 216 ± 17 ms (+ one frame) after that first handle-less frame (review R3T-B3); (e) expand takes 200 ± 33 ms and Collapse 283 ± 33 ms, with the list moving 188 epx; (f) a notification group dragged right with `motionevent` is 0.36–0.41 bright at 30 % of the width and full brightness at 12 %; (g) a volume-up press through the emulator's virtual input device (E3's commands) shows the volume panel with its lower edge peaking 12.3 ± 1.1 epx below rest and at rest by 217 ± 17 ms; with no further key it is gone in one frame; `adb shell input keyevent KEYCODE_HOME` while it shows slides it off the top in 134 ± 17 ms. Y5 and Y7–Y13 are judged in H8 and H10–H16, not measured here; Y6 is bracketed in (b) and judged in H9 (review T-B5; volume key path by review R3T-B1)
- E5 Action center and volume panel geometry measure within ± 1.1 epx of the R3 A19 / A20 values in Decisions (dump bounds, screencap). The volume panel is measured in A20's captured state: media playing in phase 01's Auxio fixture (a test audio file pushed to the AVD), the panel shown with E3's volume-up press, and the second slider expanded with the chevron (Y12) before the 274-epx height and the row positions are read; the one-slider rest height, 100.4 epx (R7 §4.5.1), is checked separately with no media playing (review R3T-M9)

**Phone-only:**
- P1 R4 spike evidence attached, including the T-Mobile visual voicemail probe (PQ2) and the nav-bar probe (P5) (review R2D-07)
- P2 Each toggle flips the real radio (`adb shell dumpsys wifi | grep -i enabled`, `settings get global airplane_mode_on`, etc.) from the action center and from Cortana
- P3 Helper restart after reboot on trusted Wi-Fi with no taps; the other R3F-01 cases behave as ruled
- P4 Top-edge gesture coexists with Samsung's shade as ruled
- P5 Nav-bar probe (part of the R4 spike; UNVERIFIED, R7 §4.1.10): on the S25 Ultra with gesture navigation (`adb shell settings get secure navigation_mode` = 2) and with 3-button navigation (= 0), a full-height accessibility overlay either covers the nav bar area or it doesn't. The evidence is a screencap: either the overlay's content reaches the display's bottom edge with no system nav glyphs drawn over it, or it doesn't. The result is recorded under interview item 8, and the setting is restored
- P6 Motion values whose tolerance is ≤ 17 ms (release settle 250–317 ± 17 ms, drag-up close 100–133 / 233–317 ms, tap-close 216 ± 17 ms, volume panel show 217 ± 17 ms and slide-away 134 ± 17 ms) re-measured from a phone screenrecord as phase 01 P10 (RV11, review R3T-M3)

**NEEDS-HUMAN:** H1 action center and volume panel feel; H2 accept Samsung shade coexistence; H3 accept any approximation not covered by H4–H16; H4 notification item text (Y1, approximation); H5 action center geometry and motion on the final release (Y2, R3 A19 14393 only, R7 §4.6.1 LOW); H6 volume panel geometry and motion on the final release (Y3, R3 A20 14393 only, R7 §4.5.8 UNMEASURED); H7 P4 design: helper state UI (Y4); H8 settle after a flick (Y5, LOW, R7 §4.1.6); H9 open / close threshold on release (Y6, LOW, R7 §4.1.7); H10 scrim between the measured points and in light theme (Y7, R7 §4.1.8, LOW §4.1.11); H11 closing by Back or a notification tap (Y8, approximation, R7 §4.2.7 UNMEASURED); H12 notification dim point, dismiss threshold, slide-out and reflow (Y9, LOW, R7 §4.4.3–4.4.4; approximation, R7 §4.4.5 UNMEASURED); H13 volume slider row entrance (Y10, LOW, R7 §4.5.2); H14 volume panel timeout and the navigations that slide it away (Y11, LOW, R7 §4.5.4); H15 second slider row motion (Y12, LOW, R7 §4.5.7); H16 notification-list offset during the drag and after an early release (Y13, approximation, R7 §4.1.9)

## Edge cases
- Helper (R3F-01): reboot on trusted Wi-Fi / with Wi-Fi off / on an unknown network; Wireless debugging turned off by the user; adb key revoked after 7 days; APK reinstalled while the helper runs; helper killed by Device care
- Helper trust: another app obtaining the helper's binder (the handoff verifies client uid + signature and refuses); a verb outside the allow-list (refused; there is no generic shell); the helper's verb allow-list contains no content-provider call (checked by reading the verb table; phase 09's provider also rejects the helper's pid, tested in phase 09)
- Accessibility service disabled by the system or the user; overlay over secure windows (keyguard, permission dialogs)
- Apps that use the top edge; landscape apps
- Nav bar (R7 §4.1.10): the overlay cannot draw over Android's nav bar with one or both navigation modes (P5), so Jeremy is asked under interview item 8 before the panel's bottom edge is built. With gesture navigation, a swipe up from the bottom edge while the panel is open is expected to reach the system whatever the overlay covers (UNVERIFIED, observed in P5). The navigation mode changes while the panel is open
- A finger lifted mid-drag by a system gesture or an incoming call (cancel event): the panel settles with the Y6 rule from where it is
- Volume keys during a call, during media, with the ringer on silent, with DND on, with the screen off
- Process death, reboot, competing overlays
- Liveness (N-01) for the accessibility service and overlays

## QA evidence
