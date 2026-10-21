# Phase 04 round 4 — Reviewer 2 (testability / evidence integrity), doc at db5d37787

Opus subagent, 2026-10-09 (brief: review/2026-10-09-phase04-r4-brief.md). Saved by the lead from the reviewer's final
message; the text below is the reviewer's, unedited except for this header.

**V1 BLOCKING — provision.sh's own force-stop turns the new service off (309-312, 376-378, 398, 406).**
provision.sh:64 ("Each later phase that adds a grant row appends its line here") comes before provision.sh:173 `am force-stop app.tileshell`. The doc's line 79 says a force-stop removes the service from `enabled_accessibility_services`. If the lines go where the convention puts them, every row starts with the service off, because the preamble runs no `a11y_on` after provision. A1(c) then reads `finished`, not `core held` (SetupWizard.kt:80-81). Phase 16 e26.sh:126, phase 17 e25.sh:133 and phase 18 e15.sh:143 fail the same way on this build.
Fix (task 12): "provision.sh's four lines go AFTER its `am force-stop app.tileshell` (line 173) and before its final Home. It then prints `settings get secure enabled_accessibility_services`. The preamble asserts that the line holds the component, and waits for `[ac] service connected` before a row's first step."

**V2 BLOCKING — the pairing helper cannot run (319-320, 437-439, 546).**
- wd_pair_dialog.sh:5 hard-codes `S=/tmp/claude-1000/…/9dace4c7…/scratchpad`, the scratchpad of a finished session, which does not exist today.
- So `adb pull … $S/wd.xml` fails, every lookup comes back empty, and the script exits at :23 ("no Wireless debugging row").
- The script only opens the pair dialog. It never turns Wireless debugging on, and never answers SystemUI's "Allow wireless debugging on this network?".
- r4_real_pairing_test.sh:25 turns it on from the host, but that write only works without a dialog on a network that is already trusted (addendum B2b).

Fix (task 13): "p04.sh carries its own copy of wd_pair_dialog.sh with `S=$ROW_DIR`. In `helper_ready` and A3 step 3: write `adb_wifi_enabled 1` from the host. If SystemUI's dialog shows, tick 'Always allow on this network' and tap Allow. Assert `adb_wifi_enabled` reads 1, and record whether AndroidWifi's BSSID was already a `<wifiAP` entry in `dumpsys adb`."

**V3 BLOCKING — A6 step 4 plays nothing and turns the service off (491-492).**
`music_open` (music_lib.sh:24-27) runs `am force-stop $PKG`, which disables the service (line 79). Step 4's `vol_up` then reaches Android's own panel, so (d) and (e) fail on a correct build. Neither `music_fixtures` nor `music_open` starts playback. Playing needs p20.sh's `to_pivot` plus a tap on a `music_song:` node (phase 20 a4.sh:57), and p04.sh copies neither.
Fix: "4. `music_fixtures`; `music_open`; `a11y_on`; `to_pivot songs`; tap the first `music_song:` node; `sstate` reads PLAYING; `am start` qa-notify's white activity; `a11y_wait` (V4); `vol_up` …"

**V4 SHOULD-FIX — a uiautomator dump drops the overlays, and rows act before they come back (379-383).**
Line 79: a dump unbinds the service, which is then rebound with a fresh `onServiceConnected`. These all dump: `ensure_start` (lib.sh:295-316), `type_request` (lib.sh:338-347), `tap_node` / `hold`, and the Settings walks. Three steps race the rebind and would fail as if the product had:
- a pull right after `ensure_start` (A5 steps 1-2, and after every C-6);
- the Back hold after reading `nav_back` (A2 step 8, which needs `GLOBAL_ACTION_RECENTS`);
- `vol_up` after the Music navigation (A6 step 4).

Fix (preamble, task 13): "`a11y_wait` (p04.sh): wait up to 5 s for an `[ac] service connected` stamped after the last dump. Every overlay step (`pull_slow`, `vol_up` / `vol_down`, the Back hold, `acdump`) that follows a uiautomator dump calls it first."

**V5 SHOULD-FIX — A2(f) asserts a line this path never writes (418-419, 430-431, 674).**
Task 4 (227-229) removes the strip at ACTION_SCREEN_OFF. e10.sh's K leg locks by sleep + wake, so the strip goes at the sleep with reason `(screen off)`, and `(keyguard)` is never logged. "The keyguard's own window" is not named. The volume panel over the keyguard (line 77, point 4) has no device check.
Fix: "(f) with `keyguard` reading `isKeyguardShowing=true`, then `vol_up`: a `wins` list that holds `TesseraVolume` lacks `TesseraStrip`. `[ac] strip removed (screen off)` at the sleep, and no `[ac] strip added` before the unlock. After the unlock: `[ac] strip added` and the strip's title back." Line 674 changes to match.

**V6 SHOULD-FIX — A3 passes vacuously on a re-run, and leg (c) rests on state nobody asserts (435-450).**
The preamble leaves `adb_allowed_connection_time` as the product set it (388-389). After the first run, the "reads 0" in (a) cannot fail. A re-run also finds:
- AndroidWifi already trusted, so step 3 shows no dialog;
- the app's key present, so step 2's "not set up" fails;
- the old helper alive, so two helpers run.

Leg (c) needs the BSSID, which needs Location on (line 81), and nothing asserts it.
Fix: "1. Kill any running helper (`helper_pid`). `pm clear` → provision.sh → `a11y_on`. `pm revoke … WRITE_SECURE_SETTINGS`. `settings delete global adb_allowed_connection_time` (reads null). `cmd location is-location-enabled` reads true. Record the `<wifiAP` entries of `dumpsys adb`." Step 3's dialog becomes conditional, as in V2.

**V7 SHOULD-FIX — expectations taken from the app itself (423-424, 445, 525-527).**
- A3(a): `uid=2000` is the helper's own claim. Add "`ps -o USER=,LABEL= -p <p>` reads `shell u:r:shell:s0`".
- A8(a) "`QuickGrid.layout` of the saved order" and A8(b) "the next `BrightnessCycle` step" grade the app with its own functions. Write out the literal Y18 step, and the expected grid using task 1's flash and cast facts. Also say where a just-switched-on VPN joins the order: `QuickPrefs` only says "newly offered appended".
- A8(b): "1 → 0 → 1" assumes the AVD starts at 1. Add "`settings put system accelerometer_rotation 1` first (restored)".
- A2(a): take the expected battery % from `dumpsys battery`, and the date from the device's `date` in a stated format.

**V8 SHOULD-FIX — A4's clauses can pass without testing what they name (454-466, 728).**
- (b): nothing shows that Wireless debugging stayed off. The keeper runs on taps and home starts (213-214) and would write 1 on a trusted BSSID. Add "`adb_wifi_enabled` still reads 0 when `<p>` is checked".
- (c): the home activity starts before BOOT_COMPLETED, so the first start can correctly be tagged home rather than boot. Line 728 says (c) proves the service and strip after a reboot, but A4 never asserts it, and `a11y_on` would hide it. Fix: "(c) the first start after a boot is tagged `(boot)` whatever triggered it; `[ac] service connected` and `[ac] strip added` after `sys.boot_completed`, with no `a11y_on` since the reboot."
- Step 1 (trust; Out lines 38-39, task 14 line 334): "after the force stop + Home, wait 10 s, then read `enabled_accessibility_services` BEFORE `a11y_on`: it must not hold the component." The shell holds WRITE_SECURE_SETTINGS by then, and no row checks that it does not turn its own service back on.

**V9 SHOULD-FIX — "Android's Wi-Fi panel" has no recorded identity (465, 515, 166-167).**
Task 1(c) records only what `GLOBAL_ACTION_RECENTS` and `ACTION_LOCATION_SOURCE_SETTINGS` resume. A4(d) and A7(c) therefore compare `top` against nothing. From memory of AOSP, not a saved copy: since Android 12, `Panel.ACTION_INTERNET_CONNECTIVITY` is a SystemUI dialog, not an activity. A4 step 4 also reads "No Wi-Fi" only after the tap, which closes the panel (Y16).
Fix:
- Task 1(c) adds "what each of the four fallback intents shows: the resumed activity, or the window title in `wins` for a dialog".
- A4(d) and A7(c) assert that recorded identity.
- A4 step 4 becomes "open the action center; `acdump`; tap Wi-Fi".

**V10 SHOULD-FIX — A5 step 3 assumes the panel survives Ack (472-473, 243).**
Task 5's close reasons include `action`, so after Ack there may be no Reply left to tap. Fix: task 7 states whether an action without a RemoteInput closes the panel (`[ac] closed by action`). A5 step 3 then adds "if closed, `pull_slow` and expand N2 again". For (c): "`qa5` is absent from a `dumpsys notification --noredact` output that still lists N1", so the slice is proven readable.

**V11 SHOULD-FIX — p04.sh is missing helpers the rows name (313-323, 382, 420, 518).**
Neither lib.sh nor task 13 provides:
- `absent_in` (p18.sh:99);
- `c6` / `rings_save` (p18.sh:108-119), which save the rings before a force-stop empties them;
- `hold` (p20.sh:120) and `to_pivot` (p20.sh:148);
- p12.sh's `provision_no_marker`, `restore_fresh`, `resume_start` and `start_slice` (A1 is E14's template);
- a hold-and-drag for A8 step 1: `input swipe` gives no long press, and qa/phase-02/scripts/gestures.sh has down / move / up;
- installing qa-notify's APK.

Fix: add them to task 13.

**V12 SHOULD-FIX — rows read nodes whose tags are never named (423-424, 448, 466, 478, 527).**
Unnamed: the tile's state node, the second status line, item titles and previews, the Expand / Clear all / Ack / Reply / send / pause targets, and `helper_state`. A driver could then read text off a merged parent node and pass. Fix (tasks 4-8, 12): a tag list (`quick_action_state:<id>`, `ac_status2`, `notif_title:<key>`, `notif_text:<key>`, `notif_action:<key>:<i>`, `notif_reply:<key>`, `ac_expand`, `ac_clear_all`, `volume_media_play`, …), with each leaf asserted by equality.

**V13 SHOULD-FIX — nothing on the device checks the overlays' RV10 density (616-617, 423).**
Old E5 was the only check that the new windows draw in epx (PLAN RV10: every window the shell owns sets `Density(pxPerEpx…)`). `AcGeometry` tests the constants. It does not test that OverlayHost uses 3 px/epx rather than the AVD's 2.8125 px/dp. Fix, A2(a): "`quick_action:camera` is 255 ± 3 × 181.5 ± 3 px with its left edge at 12 ± 3 px; `quick_action:wifi` starts 267 ± 3 px to its right."

**V14 SHOULD-FIX — the actions the shell does itself are never tried on One UI (631-633; addendum C4, C5.5).**
The AVD may have no flash (A2 c), and no P-row taps these four:
- Flashlight (C4: "the torch goes off if the app that set it exits");
- Quiet hours (C5.5: the automatic rule under One UI Modes);
- Brightness (One UI's mapping is UNVERIFIED);
- Rotation lock.

Fix, P2: "… then Flashlight on, leave the action center, still lit a minute later, then off. Quiet hours on and off (Samsung's panel shows Do Not Disturb each time). Brightness through its steps. Rotation lock on and off. Report each."

**V15 NOTE — edge-case cites that their rows do not assert.**
- Line 722 cites "no section: A6 b". Add "no `volume_media_title` node in that acdump" to A6(b).
- Line 720 (silent or Do Not Disturb, cited to P7) and line 725 (Tess on an untrusted Wi-Fi, cited to P2 / P3): none of P7, P2 or P3 does these. Add them there, or cite only `VolumeRules` / `HelperTileRule`.
- Say whether a Tess request counts as a "tap" for `TrustedNetworks.shouldEnable`; line 81 names only tile taps.

**V16 NOTE — cross-phase QA bookkeeping (brief points 1, 2, 9).**
- Static check at 538: the strip is "as tall as the system status bar" (Y17), not 68 px, and it takes taps too. Reword to "drivers that tap or drag at y < the strip's height (`acbounds` on the AVD)".
- Phase 12 e1.sh:52-57's "every grant" list (phase 17 extended it) needs this phase's three grants.
- Phase 12 line 44 ("phase 04 runs E14 for each", pairing and helper included) and line 923 (WRITE_SETTINGS / `allow_dnd` listed under phase 19) need INDEX Change Log corrections when built.

**V17 NOTE — restores and audio (385-389, 487).**
- N4 (ongoing) is still posted after A5: add "qa-notify force-stopped" to the restores.
- A6 sets STREAM_MUSIC "away from its ends", so the MP3 plays aloud; music_lib.sh:7-12 and p20.sh mute it. Start stream 3 at 0: the `+1` still shows.

**Author's points (testability side).**
1. Sound (SetupWizard.kt:80-81). Phase 12's text needs correcting (V16).
2. See V1 and V16. On the phone, nothing force-stops the shell in normal use.
3. Sound under the 2026-10-07 ruling, but RV11 is a PLAN rule, so record the deviation in the INDEX Change Log. RV10's device check went with it (V13).
4. Untested on the AVD until V5 is applied.
5. Handled by A2(c) and `QuickAvailability`, but Flashlight then runs nowhere (V14).
6/7. Design lens. A3(c) and A4(c) need Location on asserted (V6).
8. Sound: task 1(d) and 1(h) stop the build, and A5(b) and A6(e) can fail.
9. See V16.

Round 1–3 ids checked and found correctly applied: R3T-B1, R3T-B2, R3T-B3, R3T-M8, R3-m4, R2-13, T-M21 / R2-m4, T-B5 / F1-M12. Superseded by the re-cut, as point 3 says: R3T-M3, R3T-M9.

BLOCKING: 3 · SHOULD-FIX: 11 · NOTE: 3
