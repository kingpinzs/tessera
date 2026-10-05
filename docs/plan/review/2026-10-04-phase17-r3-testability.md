# Phase 17 — round 3 review, Reviewer 2: testability / evidence integrity (opus, 2026-10-04)

Brief: `review/2026-10-04-phase17-r3-brief.md`. Reviewer: a second Opus subagent (`model: opus`), read-only, dispatched
after the owner ruled codex out (his work account); it did not see the codex run's output. Written to this file by the
lead from the reviewer's final message, unedited except for this header.

**V1. BLOCKING.** The egress guard command, l.717–719.
- Evidence: `UID=$(adb shell pm list packages -U app.tileshell | sed 's/.*uid://')` is run from lib.sh, which is bash (`lib.sh:1`). In bash `UID` is readonly, so the assignment fails and `$UID` keeps the host's uid, 1000. On Android uid 1000 is `system`, so the rule polices system_server and never the app.
- Also, `pm list packages <filter>` matches substrings, so it also returns `app.tileshell.testclient.qaflix` (installed by E21) and `app.tileshell.qa.imefixture.test`. The output also carries `\r`.
- Fix: `APP_UID="$(adb shell cmd package list packages -U app.tileshell | tr -d '\r' | sed -n 's/^package:app.tileshell uid://p' | head -1)"` (the form at p16.sh:116). Assert it is numeric and ≥ 10000, then use `--uid-owner "$APP_UID"`.

**V2. BLOCKING.** The guard counts the launcher's own traffic: l.743–745, E13 l.937, E20 l.1030, E22 l.1055.
- Evidence: `WeatherFeed.kt:98` runs `refresh("start")` every time the main process starts. That reaches api.open-meteo.com (`WeatherProvider.kt:138`) and nominatim (`WeatherLocation.kt:71`) from the same uid. The guarded rows force-stop and reopen, so a correct build fails the gated "0 packets" check.
- Fix: `egress_guard_on` first runs `pm revoke` on ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION. Refresh then ends before any request (`WeatherFeed.kt:162-167`).
- After each restart the row asserts `[weather] refresh ended without new data: no coordinates`, and `absent_in` for `[weather] fetch provider=`.
- `egress_guard_off` grants both permissions back.

**V3. BLOCKING.** E9 (l.893–902) cannot run as written.
- (a) Since Android 11, implicit IMAGE_CAPTURE and VIDEO_CAPTURE from callers targeting 30 or higher resolve only to preinstalled system cameras. Source: developer.android.com, "Behavior changes: apps targeting Android 11", read today. So "the resolver lists app.tileshell beside … Open Camera and Fossify Camera" cannot hold.
- (b) For IMAGE_CAPTURE, a `file://` EXTRA_OUTPUT is moved into ClipData, and the sender throws FileUriExposedException. The doc's own l.323 says the platform refuses it on the sender, so `[camera] refused output scheme=file` can never be reached.
- (c) "no file written (`adb shell ls` fails)" reads the fixture's private cache. That `ls` always fails, so the check is vacuous.
- (d) The no-GPS check has no control, so a Camera that never writes GPS passes.
- Fix: qa-capture sets `setPackage("app.tileshell")`, which is Android's documented route to a third-party camera. Its file:// leg first relaxes its own StrictMode VmPolicy.
- The fixture logs `exists=<bool> size=<n>` for its output path. The file:// leg asserts `exists=false`; the content:// leg must log `exists=true`.
- The resolver clause becomes a recorded fact: `cmd package query-activities -a android.media.action.IMAGE_CAPTURE` lists app.tileshell.
- GPS control: with `adb emu geo fix -122.08 37.42` set, a capture from the Camera's own shutter carries GPSLatitude.
- For the lead: Q5 A's "one of Android's camera choices beside Samsung Camera" cannot happen for modern callers. That is a question for Jeremy, under rule 1.

**V4. BLOCKING.** These rows use the microphone or host audio: E8 l.892 (audio.sh null-sink, which uses `pactl`, `audio.sh:30-40`), E10 l.904, E9's VIDEO_CAPTURE leg, and the storage-full and screen-off recording edge cases.
- Fix for E8, E9-video and those edge cases: `pm revoke … RECORD_AUDIO` for the row and grant it back at the end. Add to task 6: "without RECORD_AUDIO, video records without sound, `[camera] recording without sound (no microphone permission)`". Assert `ffprobe` shows one video stream and zero audio streams. The audio stream is checked on the phone (P1).
- Fix for E10: type "take a photo" with `type_request`. The text box draws over the keyguard: `CortanaSessionRoot.kt:181-190` sits outside `if (!state.locked)`.
- For E10's keyguard, use e10.sh's setup: `locksettings set-disabled false` before `set-pin 1234` (`e10.sh:51-55`; this AVD otherwise shows no keyguard). Restore with `clear --old 1234; set-disabled true`.

**V5. BLOCKING.** E11's routing check (l.916–917) cannot see the failure.
- Evidence: after `force-stop` the video session is gone, so "no tile carries a face" holds whatever the routing did.
- Fix: from a MARK taken before play, while the video plays, the launcher slice holds `[music] session app.tileshell id=video -> none` (`MusicFeed.kt:164`, `TileRouting.kt:44-51`). Add `absent_in "id=video -> cmp:"`.
- Task 7 must state the Media3 `setId("video")`.

**V6. BLOCKING.** "Or" branches let a wrong build pass: E13 l.934–936, E22 l.1063–1066, E18 l.976/979, P14 l.1151.
- Evidence: Decisions l.84–85 says B and C are not built. A driver that accepts either branch passes a build that refuses cleartext.
- Fix: strike the B/C expectations and add "Q-D A is the only form; `cleartext refused` or a 0-packet count on 10.0.2.3 FAILS the row".

**V7. BLOCKING.** The preamble (l.733–760) lacks the current floor, which leaves vacuous absences.
- Missing: `ensure_start` (T11-16; the pod bay; `lib.sh:295-314`), `absent_in` (`p16.sh:132-138`), and page-proven no-node checks.
- `c6` saves only the launcher ring (`p16.sh:141-147`), so the `:camera` and `:video` rings die unsaved.
- Affected absences: E16 l.954, E24 l.1092, E21 l.1039, E3 l.833, E22 l.1065.
- Fix, as replacement text: "Drivers source lib.sh, then `qa/phase-17/scripts/p17.sh` (task 16: `absent_in`, `gdump`, `top_activity`, and a `c6` that runs `ring_save` for every ring in RINGS before the force-stop). Every 'Home' is `c6` + `ensure_start`. Every absence is `absent_in`. Every no-node check first asserts its page's tag (`start_page`, `hub_title`, the Photos page)."
- E24 also asserts the `-> already run` line for all six markers.

**V8. BLOCKING.** E22's token source (l.1056).
- Evidence: Jellyfin's stable OpenAPI (12.1.0, fetched today) shows `GET /Sessions` → SessionInfoDto, which has no AccessToken. AccessToken is on DeviceInfoDto (`GET /Devices`, which requires elevation).
- With an empty `$TOKEN`, `grep -rlF ""` matches every file, and the leak scan scans for nothing.
- Fix: "`GET /Devices` with the fixture admin's token; take the item whose `AppName` is the shell's client, read its `AccessToken`, and assert it is non-empty."

**V9. SHOULD-FIX.** Media fixtures, RV12 and order (Fixtures l.761–772; E3, E4, E5, E6, E6b, E7–E9).
- No route removes the edit copies, the captures or the re-pushed deletes. E3's "six rows" fails on a second run.
- Ties in mtime leave E4's "next = (40,180,80)" undefined.
- `make_photos.py:28-30` makes only `qa-photo-0..5`.
- The video colours are never defined, and the indexing is inconsistent: E6b's 2–5 s trim expects "colour 3" (1-based), but E11 expects colour 7 at 7.15 s (0-based) and reads "colour 3" at the 3-s boundary.
- Fix: task 16 adds `media_up <names>` and `media_down` to p17.sh:
  - Take a census of image and video ids first.
  - Push only the named files, then `touch -m -t` each so that qa-photo-i is i minutes older than qa-photo-0.
  - Scan.
  - `media_down` removes everything the row pushed, plus every row with `owner_package_name=app.tileshell` and `date_added ≥ MARK/1000`, scans, and asserts the counts equal the census.
- E3 counts as census + its fixtures.
- task 16 adds the editor fixtures to `make_photos.py`.
- `make_videos.sh` names ten RGB values, colour k on [k, k+1) seconds (0-based), with `-g 25`.
- E11 samples at 3.5 s. E6b's first frame is colour 2.

**V10. SHOULD-FIX.** E5's share clause (l.840–841) is unreadable as written.
- Evidence: `dumpsys activity activities` prints the chooser intent with "(has extras)" and does not show the inner ACTION_SEND or its stream.
- Fix: qa-capture also receives ACTION_SEND `image/*` and logs the action, the URI and the md5 of the stream. E5 picks it in the chooser and asserts `SEND`, `content://media/…`, and md5 equal to the fixture's.

**V11. SHOULD-FIX.** E3's partial-access step (l.827–828).
- Evidence: `pm grant …USER_SELECTED` selects nothing, so "the subset shows" has no expected value.
- Fix: assert `images=0 … access=PARTIAL` and the partial state's line with its link. Then the link opens Android's selected-photos picker; tap qa-photo-1 and qa-photo-2, then Allow. Expect exactly those two `photos_item:` nodes and `images=2`.

**V12. SHOULD-FIX.** E6 grades itself (l.432–437, 853).
- Evidence: `edit_expect.py` applies the builder's own matrices, so an identity matrix passes. And `rm` of the original (l.860) does not make a copy-write fail once the original is decoded.
- Fix: the matrices go into Decisions before task 5 starts, and `edit_expect.py` reads them from the doc. Each tool's copy must differ from the original by ≥ 16 on at least one channel.
- Task 5 states "Save a copy re-reads the original at full resolution". The `rm` happens after the tool is applied and before Save.

**V13. SHOULD-FIX.** E18 (l.969–984) requires "each pattern at least once", but some alternatives no AVD row produces:
- `front=present`
- `[camera] busy` (only an edge case produces it)
- `[video] library: <n>` (no row asserts it)
- E20's `(refreshed)` form is not in the list.
- Fix: use phase 16's form, `E18/producers.tsv` plus `notrun.tsv` (each with its reason and its JVM test or P row).

**V14. SHOULD-FIX.** The edge cases (l.1183–1242) have no index and no fixtures.
- Missing fixtures: 4K, two-audio-track, `.srt`, DNG, HEIC.
- `catalogue_server` serves only `/500` and `/404` (l.778), but the edge cases need 429 and 401.
- Fix: add `edge_index.tsv` mapping every bullet to an `edge_<ID>` or a P row, add `/429` and `/401` to the server, and add the missing fixture files.
- Living Images is shown "always" (E7) but no AVD row captures one. Add an E7 sub-row that runs P12's file checks on the AVD.

**V15. SHOULD-FIX.** Phone rows and the CI build.
- P1, P6, P7 and P9–P15 use adb, `exiftool` or `ffprobe` on pulled files. The standing rule (INDEX R4: "Thats why we have r4") forbids PC/USB steps on the phone.
- The CI build the owner installs has no catalogue (Q-A2). So P13, P15, H10 and H11 cannot run on it, and "installed from there" (l.91) implies a PC→phone install. That contradiction needs a question for Jeremy.
- Fix: P rows read on the phone (the shell's diagnostics export, or an r4probe-style probe). P13, P15, H10 and H11 name "a PC-built release APK, same key, side-loaded from a private copy, never the public release, with its APK id recorded". Ask Jeremy whether that route is acceptable.

**V16. SHOULD-FIX.** R11 pass-2, Camera (governs).
- E7 l.872: "set 3 s" is not a W10M value. The timer is 2 s / 5 s / off, and Time lapse repeats captures (camera-pass2 §1). Change to "2 s, Time lapse off, `saved.wall − MARK` within 2000–3500, exactly one new row".
- E19 l.994: the preview is centred on the full screen (80→560), not above the nav bar.
- Add to E19, at ±2 because these are MEDIUM:
  - shutter white fill, #2B2B2B ring at r 32–34 (§4.1);
  - vertical capsule on the right edge, 2–45.5 epx from it, glyph centres H/2 −66 / −22 / +22 / +66 at 24 from the right;
  - settings 24 from the right, 27.9 above the nav top;
  - camera roll centre 24.7 from the left, 28 above the nav top;
  - camera switch 24 from the right and 28 from the top, checked on the phone (P1, since the AVD has no front camera).
- E19 l.1013: the mode switch is a slide of 300 ± 33 ms (§4.4, §5).
- Add motions: capture feedback = black for 167 ± 33 ms (§4.5); dial open = a 333 ± 33 ms sweep.
- Y3 becomes MEASURED (MEDIUM) under H2. H18 is reduced to the version choice. Y12's band becomes 0.35·W centred; H14 keeps stitch quality only.

**V17. SHOULD-FIX.** R11 pass-2, Photos.
- E4 l.835 and E19 l.1012: the viewer opens with a cut (V-2015) or, from 2016, expands from the thumbnail (§4.7). It does not fade in from black.
- Photo swipe: the whole fling is ≤ 234 ms, with a 20-epx gap between photos (§5).
- T17-17's own rule now flips the viewer, editor and trim to their V-2016+ captures:
  - viewer bar: Share · Edit · Delete · More at 218 / 150 / 82 / 24, black (§4.3–4.4);
  - editor: a 48-epx bottom strip (§4.2);
  - trim: a top bar with two handles and no centred readout (§4.1).
- E19 checks LOW values for order and presence only. H17 and H13b stay. H13a becomes fidelity.

**V18. SHOULD-FIX.** R11 pass-2, Movies & TV. E19 l.1009–1011 asserts superseded values:
- The right label shows **remaining** time, en-US "0:17:34".
- The thumb is Ø 22 with a 2-epx stroke.
- Transport centres: CC 36, back 10 at W/2 − 48, play at W/2, forward 30 at W/2 + 48, "•••" at W − 36 (HIGH, ±1). CC appears only when there is a subtitle track.
- Motion: the pane settles in 167–200 ms; controls auto-hide after 3.2 s (E11 l.915); library → player is a cut of ≤ 33 ms (§5).
- Y5's played-portion colour is now MEASURED.

**V19. SHOULD-FIX.** E17 (l.958): the OpenCV check "≤ 20 MB" passes when OpenCV is absent from the APK. Also assert ≥ 1 `lib/arm64-v8a/libopencv*.so` and none under `lib/x86_64`.

**V20. SHOULD-FIX.** E25(a)'s "Step 1 of 3" (l.1104) depends on "the Photos row's video half" (l.59, 661), which is never defined. If the Photos row also needs READ_MEDIA_VIDEO, the wizard shows 4 steps. Define it: "the Photos row stays READ_MEDIA_IMAGES / USER_SELECTED (`Checklist.kt:170`); READ_MEDIA_VIDEO is the Videos row alone".

**V21. SHOULD-FIX.** E20 (l.1026) checks absence in the fixture log on mixed clocks, from a log never proven readable. Fix: count lines from a host offset, assert the pref is honoured, and finish with a control request from the driver that must appear in the log.

**V22. NOTE.**
- E23 relies on E22's server set-up (l.1086). Do the set-up in E23 itself.
- E25 (b) and (c) lack the template's MARK plus resume (phase 12 E14).
- E14 reads `logcat` without scoping it to the row. Use `-T` from the MARK.
- E15 "red" means `checklist:<id>:missing`.
- E20's cache age: say whether it is the file's mtime.
- Double-tap can be driven on the AVD with the gesture driver's `script` op.

**Round-1 / round-2 ids checked and correctly applied:** T17-5, T17-7, T17-8, T17-9, T17-11, T17-13, T17-14, T17-15, T17-18, T17-19, T17-20, T17-22, T17-23, C-3, C-4, C-5, C-9, C-10, C-15, C-19, C-20, C-21, C-25, C-26, C-27, C-31, C-32.
**Wrongly or half applied:** T17-3 (V12), T17-4 and T17-6 (V3), T17-21 (V8), C-6 (V7), C-16 (V6), C-29 (V1, V2).

BLOCKING: 8 · SHOULD-FIX: 13 · NOTE: 1
