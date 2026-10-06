# Phase 17 — round 3 review, Reviewer 1: design / correctness (opus, 2026-10-04)

Brief: `review/2026-10-04-phase17-r3-brief.md`. Reviewer: an Opus subagent (`model: opus`), read-only. Written to this
file by the lead from the reviewer's final message, unedited except for this header.

**D1. BLOCKING. The capture-intent output guard doesn't hold, and the no-output contract is missing.** Doc lines L439-445 (T17-4), L1218-1219, L634-636.
- **Evidence:** L440-441 says the camera "writes only through `contentResolver.openOutputStream(uri)` under the caller's URI grant". `openOutputStream` runs as the shell, so it succeeds wherever the shell can write, whether or not the caller granted anything.
- **What the shell can write today:** contacts (`WRITE_CONTACTS`, AndroidManifest.xml:47) and calendars (`WRITE_CALENDAR`, :49). After phase 18 it gains All-files access (L442).
- **The hole:** a caller holding no permission can aim `EXTRA_OUTPUT` at a raw contact's `display_photo` URI, which is writable by URI (the `RawContacts.DisplayPhoto` javadoc shows how to write it). The shell would overwrite that photo.
- **Why the platform doesn't catch it:** Android adds and checks the caller's grant itself only when the caller's intent carries no ClipData of its own (`Intent.migrateExtraStreamToClipData`).
- **L1219's refusal has nothing behind it:** the doc gives no way to produce `[camera] refused output: no grant`.
- **The no-output cases are not covered:**
  - `IMAGE_CAPTURE` without `EXTRA_OUTPUT` must return a small Bitmap in the "data" extra.
  - `VIDEO_CAPTURE` without it must save to the standard video location and return that Uri (both javadocs). This contradicts L443-444, "an intent capture leaves NO copy in DCIM".

**Fix.** Replace L440-441 with:
> The capture form accepts a `content://` EXTRA_OUTPUT only when all three hold:
> (a) it was started for a result (`callingPackage != null`);
> (b) the intent's ClipData holds that same URI and its flags include FLAG_GRANT_WRITE_URI_PERMISSION, which Android checks against the caller's own access when the activity starts;
> (c) the authority is not one of the shell's own.
> Otherwise it returns RESULT_CANCELED with `[camera] refused output: no grant`.
> With no EXTRA_OUTPUT: IMAGE_CAPTURE returns a thumbnail Bitmap in `data` and writes no file. VIDEO_CAPTURE saves to DCIM/Camera and returns that row's URI with a read grant.

Add an E9 negative:
> qa-capture sets its own ClipData with no grant flag, and EXTRA_OUTPUT is a fixture contact's display_photo URI. Expected: RESULT_CANCELED, the refusal line, and the contact's PHOTO_FILE_ID unchanged.

**D2. BLOCKING. The Q-A2 consequence is false as built, and P13 / H10 / H11 can't run on the build Jeremy installs.** L91-92, L1145-1158, L1168-1171.
- **Evidence:** L91-92 says "release-signed with the same key as CI … so either can update the other". That fails on versionCode:
  - app/build.gradle.kts:27 is `versionCode = (System.getenv("TESSERA_VERSION_CODE") ?: "1").toInt()`.
  - Its comment at :24-25 says Android "will refuse a DOWNGRADE outright".
  - CI sets the code to `github.run_number` (apk.yml:93).
  - So a PC build is versionCode 1, and the phone refuses it over the installed CI build.
- **The CI build itself:** it has no key, so task 12's no-key form makes no request. Browse has no results, no title page and no "Watch on" row. That means P13, H10 and H11 can't run on it either, not just P15.
- **No route to the phone:** the doc names no way to get a PC-built APK onto the phone without USB, which the owner's rules forbid.

**Fix.**
- L91-92 becomes:
  > … same key as CI, built with `TESSERA_VERSION_CODE` ≥ the installed CI build's run number (else Android refuses it as a downgrade, build.gradle.kts:27); the next higher CI build replaces it and drops the key.
- P13, H10 and H11 each get: "on a build made on Jeremy's PC (Q-A2: B), as P15."
- Lead's question for Jeremy: how does that APK reach the phone with no PC/USB step?

**D3. SHOULD-FIX. Camera values that camera-pass2.md corrects (§1, §4, §5).** Y3 L525, Y6 L528, E19 L993-996 and L1013, E7 L872-875.

Y3 changes:
- **Shutter:** "72-epx #666666 disc" → "72-epx disc: white fill to r 31.9, #2B2B2B ring at r 32–34, white ring to r 36.1" (§4.1).
- **Capsule:** "capsule along the top … settings top-right; camera switch top-left" → (§1, §4.2-3):
  - The capsule is vertical on the right edge, 2→45.5 epx from it, centred on the screen centre, with glyph centres at −66 / −22 / +22 / +66.
  - Photo mode: flash, HDR, timer, chevron. Video mode: video light, slow motion, chevron. Panorama: no capsule.
  - Settings: a 36-epx disc bottom-right (24 from the right, 27.9 above the nav top).
  - Camera switch: top-right (24, 28). Camera roll: a 36 square bottom-left (24.7, 28.0).
- **Grid and Living Images** move to the settings page ("Framing grid", "Capture living images", camera.md 1.8).
- **HDR:** CameraX Extensions HDR, hidden with a `[camera] mode hdr: unavailable` line where the phone lacks it.
- **Preview:** "centred above the nav bar" → "centred on the full screen" (V-2016+).
- **Mode discs:** right = next, left = previous, in the cycle photo → video → panorama.

Motion (Y6 / E19):
- Mode switch: "cut" / "a single frame" → "a ≈300-ms slide along the short axis: out ≈150 ms, black gap, in ≈150 ms".
- Add capture feedback: "preview cuts to black for ≈167 ms, chrome stays".
- Add dial open: "a ≈333-ms sweep after the flick; labels fade in over ≈100 ms".

Timer:
- W10M offered only 2 s, 5 s and off. E7's "set 3 s" → "set 5 s", with bounds 5000 ≤ saved.wall − MARK ≤ 6500.

H2 and H18 still apply (the values are MEDIUM, from a video). H4 still applies for the chevron and the value change, which are still unmeasured.

**D4. SHOULD-FIX. Photos values that photos-pass2.md corrects.** Y2 L524, Y11 L533, Y13 L535, Y6 L528, E4 L835, E19 L991-993 and L1012, plus L31 and L145 ("trim … one LOW still, 1.11").
- **Viewer app bar:** the V-2016+ viewer is now captured on three devices (§1). Under T17-17's own rule (build the later form), the bar is Share · Edit · Delete · More at 218 / 150 / 82 / 24, black fill, header still #171717 (§4.3-4). Favorite drops out; it had no task or row anyway.
- **Editor (Y11):** "the F2 panel" → "a 48-epx bottom tool strip (§4.2), with the Edit sheet on the screen bottom (§4.9)".
- **Trim (Y13):** R11's 1.11 still is the frame picker, not Trim (§4.1). Replace with "two white handles, range times at the track ends, a top bar; no centred readout".
- **Viewer open / close (Y6, E4):** "fade-in from black" → "expands from its thumbnail and shrinks back (§4.7)". The 250 ms stays as an approximation.
- **Photo swipe:** "settle ≈290 ms" → "a 20-epx gap between photos; the whole fling takes ≤ 234 ms" (§5). E19's "290 ± 17" → "≤ 234".

H1, H17, H13a, H13b and H4 still apply.

**D5. SHOULD-FIX. Movies & TV values that movies-tv-pass2.md corrects (§4, §5).** Y5 L527, Y8 L530, Y6, task 7 L652-654, E19 L1009-1013.
- **Right time label:** "total" → "remaining". The format follows the locale ("0:17:34" on en-US). Baseline is nav − 67.6.
- **Scrubber thumb:** Ø 24 → Ø 22, stroke 2.0, 1.2 epx below the track centre. This is v3.6.1867, the release the doc's back 10 / forward 30 skips came with.
- **Captions button:** shown only when the file has a text track; the row re-centres without it.
- **"•••" menu:** Cast / Zoom to fill / Repeat / Autoplay.
- **Playback default:** letterbox (fit).
- **Posters (Y8):** "112 × 168 on a 124 pitch" → "112 × 160 on a 120 pitch".
- **Pane motion:** "133 ms" / "133 ± 17" → "≈78 % at 100 ms, settled at 167–200 ms; close in 67–100 ms".
- **Library → player:** a hard cut.
- **Player controls:** hold ≈3.2 s, fade out over 367–400 ms.

H3, H19 and H4 still apply.

**D6. SHOULD-FIX. Task 1 doesn't follow the in-shell app pattern as built, and Back on Start goes to the wrong place.** Task 1 L598-602, task 15 L701, tasks 4 and 8, E3 L832, E13 L924.
- **Back on Start:**
  - Task 1 puts the capture intents on the launcher `CameraActivity` and `VIEW video/*` on the launcher `VideoActivity`, "as MusicActivity". MusicActivity is not singleTask (manifest:129-144). Phase 15/16's apps are `singleTask` with `onNewIntent` (manifest:152-165 and :501-533).
  - A launch from Photos, Files or a capture caller runs in the caller's task.
  - BackHistory counts every catalog class (start/BackHistory.kt:40-41, :64-65) and resolves it to that app's launcher entry (:83-84).
  - So after a video watched from Photos, Back on Start opens Movies & TV's My videos. After a capture for another app, it opens the shell's Camera.
- **Shortcuts:** every shell app declares its own shortcuts file on its own activity (`@xml/shortcuts_music`, manifest:143; the same for clock, settings, calendar and people). Task 15's single `res/xml/shortcuts.xml` doesn't fit phase 11's per-activity query.
- **`startFeeds`:** phase 16's apps call it after an in-place grant (CalendarActivity.kt:78, PeopleApp.kt:292). It has no process guard (ShellApp.kt:217-229). Called from `:camera` or `:video`, it would start a second PhotosFeed, MusicFeed and WeatherFeed in that process.

**Fix (task 1).**
- `PhotosActivity`, `CameraActivity` (STILL_IMAGE_CAMERA, the slot) and `VideoActivity` (the hub): `singleTask` with `onNewIntent` and their own affinity, phase 15/16's form.
- Entry points for other apps become helpers, not catalog entries (L14-2):
  - `.camera.CaptureActivity`: IMAGE_CAPTURE and VIDEO_CAPTURE, in `:camera`, standard launch mode.
  - `.video.PlayerActivity`: VIEW video/*, also the target of Photos' and the hub's explicit launches, in `:video`, standard launch mode.
  - `.photos.ViewerActivity`: VIEW image/*.
- Static shortcuts: `shortcuts_photos.xml`, `shortcuts_camera.xml`, `shortcuts_video.xml`, each as meta-data on its own activity, in shortcuts_people.xml's `VIEW` + `page` / `mode` extra form.
- After an in-place grant, only `PhotosActivity` (main process) calls `startFeeds("photos grant")`. The `:camera` and `:video` activities never call it.

Row changes:
- E3, E11 and E13: the player's component becomes `.video.PlayerActivity`.
- E3 adds: Home, then Back on Start, gives `[back] … -> app.tileshell/.photos.PhotosActivity`.

**D7. SHOULD-FIX. MediaStore writes have no owner, no port, no tests and no read-back; "next to the original" is often impossible; the output format is unstated.** L237-241, L432-438, tasks 5 and 6, L1210.
- **Evidence:**
  - **Phase 16's standard:** a port with a recording fake (`ContactsPort` in people/PeopleWrites.kt, tested by PeopleWriterTest and CalendarWriteLayerTest with FakeCalendarProvider), plus a read-back of each write (PeopleWrites.kt:119).
  - **What phase 17 does:** it writes MediaStore from two processes and names none of that.
  - **Allowed folders:** MediaProvider accepts a new Images row only under DCIM/ or Pictures/, and a Video row only under DCIM/, Movies/ or Pictures/ ("Primary directory … not allowed"). So an image in Download/ can't get a copy next to it.
  - **Output format:** only HEIC → JPEG is stated.

**Fix.** Add one Decision:
> One MediaStore write layer, `app.tileshell.media.MediaWrites`, owned by this phase and used by Photos and Camera. It sits behind a `MediaStorePort` (insertPending / openWrite / publish / abandon / deleteRequest / readBack), with the real port and a recording fake.
>
> JVM tests prove:
> 1. no op opens an existing row for writing, except a capture caller's granted URI;
> 2. every insert goes pending → written → published, and a failed write abandons its own pending row;
> 3. the cleanup deletes only rows whose OWNER_PACKAGE_NAME is the shell.
>
> Every publish is read back (row present, IS_PENDING 0, SIZE > 0, MIME, RELATIVE_PATH), or the op fails with its `failed:` line.
>
> Where copies go: the copy goes to the original's RELATIVE_PATH when that folder is allowed, else to Pictures/ or Movies/, and the line says so.
>
> Output format: edited stills are JPEG at quality 95 (PNG when the original is PNG), saved upright with EXIF Orientation 1, with DATE_TAKEN / DateTimeOriginal copied from the original. Trims are MP4 (H.264 / AAC), keeping rotation and DATE_TAKEN.

**D8. SHOULD-FIX. Full-resolution photos from the S25U are not handled.** Task 5; L248 ("Photos runs in the main process").
- **Evidence:**
  - The S25U's main sensor is 200 MP. An ARGB decode of one photo is about 800 MB, which exceeds the app's memory limit. A Canvas refuses to draw a bitmap over 100 MB (RecordingCanvas: "trying to draw too large bitmap").
  - E6's fixtures are 640×480, so no row catches this.
  - Photos runs in the launcher process. R10 testability 27 moved capture and decode out of that process precisely so they can't take Start down (L242-244).

**Fix.**
- The viewer decodes at screen size (ImageDecoder target size) and re-decodes the zoomed region with BitmapRegionDecoder.
- The editor works on a ≤ 4096-px preview and renders the saved copy tile by tile.
- The editor and trim run as `.photos.EditActivity` in a separate process, `:photosedit`.
- Add a phone row: edit a 200-MP photo, and get a full-resolution copy with the launcher's pid unchanged.

**D9. SHOULD-FIX. "Set as Start background" can't reuse the persisted grant.** Task 5 L623-624.
- **Evidence:**
  - The background comes from the photo picker and takes a persistable grant (settings/ThemePresetsUi.kt:233-236).
  - A MediaStore URI that Photos found by its own query has no grant to persist, so `takePersistableUriPermission` throws. The existing `runCatching` at :235 hides that.
  - The result: the background only works while the shell has photo access, and goes blank if access is revoked or partial.

**Fix.** Copy the image, decoded at screen size, to the shell's files directory as `backgrounds/<id>.jpg`, and point `backgroundUri` at that file. Log `[photosapp] set as background <id> -> <file>`. E5 adds: revoke READ_MEDIA_IMAGES, and Start's pixel is unchanged.

**D10. SHOULD-FIX. The pro-dial gates are wrong for exposure and white balance.** L415-419, E7 L884-889, P9 L1133-1135.
- **Evidence:**
  - **Exposure:** the dial's "exposure" ring is EV compensation. That works with auto-exposure on (`CONTROL_AE_COMPENSATION_RANGE`) and doesn't need AE OFF.
  - **White balance:** P9 sets "Cloudy". That is the preset `CONTROL_AWB_MODE_CLOUDY_DAYLIGHT`. Gating white balance on AWB OFF would hide presets that work. AWB OFF means manual colour gains, which needs the MANUAL_POST_PROCESSING capability.
  - **ISO / shutter:** in Camera2, turning AE OFF makes both ISO and shutter manual. There is no "ISO manual, shutter auto".

**Fix.** Replace the gates with:
> - Exposure: `CONTROL_AE_COMPENSATION_RANGE` ≠ [0,0].
> - ISO and shutter: AE OFF + MANUAL_SENSOR. When only one is set, the other is held at the last AE result (SENSOR_SENSITIVITY / SENSOR_EXPOSURE_TIME). EV is disabled while either is manual.
> - White balance: each preset is shown only if `CONTROL_AWB_AVAILABLE_MODES` lists it.

E7 derives its expected mode list from the same keys.

**D11. SHOULD-FIX. Slow motion never actually looks slow.** Task 6c L645-646, P11 L1139-1140.
- **Evidence:** P11 asks for `r_frame_rate` ≥ 120 and also for the file to "play at normal speed". A file with real-time timestamps plays in real time everywhere, and the player has no flag telling it to slow down. To make the file itself slow, Android records with a capture rate above the playback rate (MediaRecorder `setCaptureRate` / `setVideoFrameRate` javadoc; video only).

**Fix.**
- Task 6c: the take is captured at the session's fps and encoded at 30 fps, so every player shows it slowed. It has no audio.
- P11 becomes: `r_frame_rate` 30; duration (fps ÷ 30) × 5 s ± 0.5 s; plays slowed in the shared player.

**D12. SHOULD-FIX. Video sound needs RECORD_AUDIO, and the doc doesn't say what happens without it.** Tasks 6 and 8, E8 L890-892.
- **Evidence:**
  - CameraX's `withAudioEnabled()` requires RECORD_AUDIO and throws a SecurityException without it.
  - The shell already holds that permission for Tess (manifest:39; the Setup row is "microphone", CortanaChecklist.kt:47).
  - E8 records through the AVD's microphone via audio.sh, which breaks the owner's no-mic / no-host-audio rule.

**Fix.**
- Task 6: video records sound only while RECORD_AUDIO is held. Otherwise the take is silent, with `[camera] video sound: off (no microphone permission)` and a one-line hint naming the Microphone row.
- E8: run with RECORD_AUDIO revoked, and assert one video stream, no audio stream, and the line.

**D13. SHOULD-FIX. "Watch on" must set the target app's package.** L296-303, task 11, E21.
- **Evidence:** since Android 12, a generic http(s) VIEW intent with no package set opens the app only if its domain is verified; otherwise it opens the default browser ("web intent resolution" in the Android 12 behaviour changes). `qa-flix.test` can't be verified, so E21's id branch would open the browser.

**Fix.** `openTitle` sets `setPackage(<service package>)` on both the title link and the search link. On ActivityNotFoundException it falls back to search, then logs `not installed`.

**D14. SHOULD-FIX. BuildConfig is turned off.** Task 12, task 17, L355, L400.
- **Evidence:**
  - app/build.gradle.kts:77 has `buildFeatures { compose; aidl }` only, and nothing in app/src uses BuildConfig.
  - Since AGP 8 the class is generated only when `buildConfig = true`.
  - So `BuildConfig.TMDB_READ_TOKEN` and every `BuildConfig.DEBUG` gate in the doc won't compile.

**Fix (task 12).**
- Add `buildFeatures { buildConfig = true }` and `buildConfigField("String","TMDB_READ_TOKEN", …)`.
- A Gradle property that is present wins even when empty (`-Ptmdb.readToken=`). local.properties is read only when the property is absent.

**D15. SHOULD-FIX. Q-16-1 is not cited; this needs a question to Jeremy.** L157-175, task 3, E1.
- **Evidence:**
  - Q-16-1 (2026-09-30): Calendar and People took their slots once, even over a hand pick. Jeremy rejected option B, "keep the earlier pick with a phone row to re-point".
  - `takeOver` exists in code (tiles/LayoutStore.kt:133), and only those two markers pass it (:129).
  - The doc seeds PHOTOS and CAMERA without it. L168-169 itself expects Jeremy's slots to be hand-picked, so on his phone the tiles and "take a photo" keep opening Samsung's apps. That is the outcome he rejected in phase 16.

**Fix.**
- Task 3 adds: "(no `takeOver`; Q-16-1 keeps the guard for every later seed)".
- The lead asks Jeremy whether Photos and Camera should take over their slots the way Calendar and People did.
- Add phase 16 P0's form as a phone row: the PHOTOS / CAMERA slots after the update, RECORDED.

**D16. NOTE. Stale or loose text.**
- **L81-82:** "(cleartext denied by default, permitted only on the media playback path)". Android's network security config scopes by host, not by code path, and this contradicts C-16 (2) A (L392). Replace with "(fixed hosts denied by domain-config; other hosts permitted at the base, C-16 (1)–(2))".
- **L368:** "per Q-D (pending …)" → "per Q-D A".
- **L614:** `ShellApp.kt:122` → `:152-158`.
- **L452:** `AndroidManifest.xml:111-120` → `:129-144`. MusicActivity is still the first MAIN/LAUNCHER activity, so C-21 holds.
- **L235:** "the same attributes phase 10 uses" → "USAGE_MEDIA + AUDIO_CONTENT_TYPE_MOVIE, handleAudioFocus true, session id `video`", following recorder/RecorderPlayer.kt:27-34.
- **BS-7:** name the phase 15 form now. Each process's diagnostics ring is printed by a `Service.dump()` in that process (recorder/RecorderService.kt:228-249).
- **Pod bay:** MusicFeed drops shell sessions that route to no tile (feeds/MusicFeed.kt:161-167), so a video never shows in the Now playing pod. Say so in one line, and have E11 assert `[music] session app.tileshell id=video -> none`.

**Round-1 / round-2 items checked and correctly applied:**
- T17-5 / C-2: the guard's line forms match LayoutStore.kt:148-154.
- C-1: TileRouting routes any non-music shell session id to no tile.
- C-21.
- T17-18 and T17-19: the manifest has neither SET_WALLPAPER nor FOREGROUND_SERVICE_CAMERA yet, so T17-18's ADD of SET_WALLPAPER is still needed, and T17-19 correctly declares no FOREGROUND_SERVICE_CAMERA.
- T17-1 HTTP: no okhttp; HttpURLConnection at WeatherProvider.kt:57 and PlaceSaver.kt:102; INTERNET at manifest:32-33.
- C-16: no network security config exists today.
- C-17: SystemBars.kt:77-80.
- T17-2, T17-3, T17-9, T17-13 / T17-14 (apart from D14), T17-15.
- T17-16 / T17-17 (apart from D3–D5).
- The AppUninstall.kt:37-44 cite.

BLOCKING: 2 · SHOULD-FIX: 13 · NOTE: 1
