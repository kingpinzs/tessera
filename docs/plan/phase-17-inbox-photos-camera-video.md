---
phase: 17
slug: inbox-photos-camera-video
status: DRAFT   # split 2026-09-22; interview pending; R11 §Photos / §Camera / §Movies & TV gate FINAL
depends-on: [01, 02, 03, 10]
---

# Phase 17 — W10M inbox apps III: Photos, Camera and the video player

## Goal
Three Windows 10 Mobile inbox apps live inside the shell APK, each an app in the app list like phase 10's
Music: **Photos** (what the Photos tile opens), **Camera** (what the bottom-row Camera tile and Tess's
"take a photo" open) and a **video player** in Movies & TV's role (the library of the phone's own videos and
the one player every video in the shell plays through). The PHOTOS and CAMERA slots are seeded to the shell's
apps once, the way phase 10 seeded MUSIC, and never over a choice the user made. Every visual and motion value
is measured by R11 or recorded as a tagged approximation with a NEEDS-HUMAN row. Nothing here uses the
internet (A11).

## Scope
**In:**
- Photos: a library of the phone's images (and videos, Q4) read from MediaStore and watched for changes the
  way PhotosFeed watches images; a collection pivot (by date) and albums (MediaStore buckets); a full-screen
  viewer (swipe, zoom); share, delete, "set as" (the shell's Start background; Android's lock-screen
  wallpaper), slideshow; editing per Q1; the app's own empty / denied / partial-access states naming the Setup
  checklist; it takes the PHOTOS slot once (marker `slot:photos:v1`) and appears in the app list.
- Camera: still and video capture on the back and front cameras with flash, timer, grid, tap-to-focus and
  zoom; modes per Q2; files saved through MediaStore into DCIM/Camera so the Photos tile, the Photos app and
  every other gallery see them; the capture-intent contract per Q5; it takes the CAMERA slot once (marker
  `slot:camera:v1`); its own `android:process` (`:camera`).
- Video player: a Videos library per Q3 and one player screen (transport, scrubber, aspect and rotation
  handling, subtitles when the file carries a text track); it answers `ACTION_VIEW video/*`; it takes audio
  focus so Music pauses; a media session for headset and Bluetooth buttons that never lands on a tile; the
  same player screen is what a video tapped in Photos or Files plays in; its own process (`:video`).
- Slot seeding and the re-cut of the rows that touched these slots (phase 01 E4; phase 03 E2, E10), and the
  fix for the package-keyed live-tile fallback (Decisions) with its re-run of phase 10 E10 / E13.
- Settings + checklist rows: Camera (CAMERA permission), Videos (READ_MEDIA_VIDEO); the Photos row exists
  (phase 01) and gains the video half; each app offers its grant where its empty state is (phase 10 task 10).
- Diagnostics lines for every silent-empty state; ADDs to the exported-components allow-list
  (qa/phase-03/exported-allowlist.txt); the app-list regression; the APK budget check.
**Out (explicitly):** OneDrive, cloud albums, purchased content, streaming or any http source (A11, R10-Q4:
an `http://` VIEW is refused); a Store; replacing Samsung's side-key double-press camera launch (the shell
cannot set it — Decisions); the lock-screen camera; Samsung's extra lenses through Samsung's SDK (not public
Camera2 — P1 records what the S25U exposes); DRM; Files (phase 18); the pod bay's pods; new Tess commands
("show my photos" is not in the ruled list, phase 03 Decisions); any interim "viewer-only" Photos or
"player-only" video build if the interview rules the fuller form (Hard Rule 16).

## Decisions
- 2026-09-23: Interview Q3b — Movies & TV is the full hub (Jeremy: "(a)"): local videos; an online catalogue of films and shows
  from a public film database; per title, which streaming apps on the phone have it, with "Watch on <service>" opening that app
  at the title (the user still signs in inside each service's own app); and the user's own media server (Jellyfin or Plex) when
  there is one. The same answer asked for a TV-channels experience and said it "could be a seprate app thing": that is recorded
  as its own scope add (PLAN.md 2026-09-23) and its own phase, not part of this one. This phase builds the streaming hand-off
  (catalogue lookup and "open this title in that app") that the channels app and Music's streaming side reuse.
- 2026-09-23: The video app is Movies & TV WITH its online half (Jeremy: "I want the movies & TV. microsoft has cancled theres
  so I need a way to fake it using streaming services and other methods pluse anything local", then the A11 amendment in
  PLAN.md). Q3 as first written assumed A11 kept it local and is superseded; its shape is re-asked (Q3b).
- 2026-09-23: Interview Q2 — every Windows Camera mode (Jeremy: "(c)"): photo and video with automatic everything, flash /
  timer / grid / front-back / zoom, the Lumia pro dial (manual focus, ISO, shutter, white balance, exposure), panorama, slow
  motion and Living Images. Agent notes: a mode the phone's camera cannot do (Camera2 reports no high-speed sessions, no manual
  sensor control) is not shown, and the diagnostics say why — no mode that silently fails; the AVD's virtual camera cannot
  prove slow motion or manual sensor control, so those rows are phone-only (P rows); panorama stitching must be code the shell
  may ship (licence checked at build start, P5), and is the largest single task here.
- 2026-09-23: Interview Q1 — Photos is the full W10M Photos (Jeremy: "(c)"): collection, albums, viewer, share / delete / set
  as, slideshow, the editor (crop, rotate, straighten, auto-enhance, light and colour, filters, red-eye, saved as a copy) AND
  video trimming (saved as a copy, the original kept). One form, built complete (Hard Rule 16).
- 2026-09-22: From phase 11 interview Q1 (Jeremy: "A"), a standing rule for every shell app: this phase's apps declare their
  own top-level screens as static App Shortcuts, so a hold on their tiles bursts those screens (phase 11). Which screens each app
  declares is settled at this phase's own interview; a build task and an acceptance row carry it.
- 2026-09-22 Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out
  thing at a glance thing"): Photos, Camera and "a video player (Movies & TV's role)" are in, "each an app in
  the shell APK like Music" (PLAN.md, 2026-09-22 scope add). Read as A8 reads the feature list: everything
  buildable is in; build order is what gets decided.
- 2026-09-22 R10-Q4 (Jeremy: "(a)"): A11 stands as written. So no cloud pivot, no online catalogue, no
  streaming: the video player refuses network URIs and plays only what is on the phone.
- 2026-09-22 (agent, R10 triage): **the shell's own apps take their slots once, following phase 10 Q5's Music
  precedent** (`LayoutStore.assignSlotOnce`, one marker per slot, "never over a user's explicit choice"), and
  Tess's actions target the shell's app when it holds the slot. Verified in code: `SlotResolver.resolve`
  auto-assigns a category only with exactly one handler or a preferred one; on the AVD `STILL_IMAGE_CAMERA`
  already has three handlers and `APP_GALLERY` two (`cmd package query-activities`, 2026-09-22), so without the
  seed both tiles would read "Tap to choose". Markers `slot:photos:v1` and `slot:camera:v1`. A slot the user
  re-pointed before this build installs is left alone (the marker path only ADDs to `explicitSlots` when the
  marker has not run — an assignment made by hand is not one the marker made, and the upgrade row E1 proves it).
  Tess needs no code change for "take a photo": `ActionLayer.takePhoto()` is `launchSlot(Slot.CAMERA, …)`,
  which resolves through the same `SlotResolver`; what changes is the OBSERVABLE. Re-run rows: **phase 01 E4**
  (Photos and Camera stop being 2+-handler negatives — Mail and Store keep that proof), **phase 03 E2** (the
  "take a photo" line's resumed activity becomes `app.tileshell/.camera.CameraActivity`), **phase 03 E10**
  (expectation unchanged — "take a photo" still shows "Unlock to continue" — re-run because the resolved
  component changed). Recorded in the INDEX Change Log when built.
- 2026-09-22 (agent, verified in code): **the package-keyed live-tile fallback must be fixed before the seed,
  or Music's face lands on Photos and Camera.** `StartPage.kt:150` and `:230` give a slot tile the content
  published under `LiveTileEngine.packageKey(<resolved app's package>)`, and `MusicFeed.publish` publishes the
  now-playing face under the session OWNER's package (`c.packageName`) and grows every tile standing for that
  package (`ActiveTiles.setPackage`). Today the only slot resolving to `app.tileshell` is MUSIC. After this
  phase PHOTOS and CAMERA resolve to it too (phases 15 / 16 add ALARMS-class and PEOPLE), so a playing track
  would put its face on the Photos tile and grow the Camera row tile. Rule (one permanent form, Rule 16): a
  session owned by the shell's own package is attributed to the in-APK app that owns it, read from the session's
  tag (`MediaController.getTag()`, API 29; Media3 `MediaSession.Builder.setId` sets it): a `music` session goes
  to the MUSIC slot's tile (phase 10 Q4 read at app granularity — "the tile of the app that owns the session",
  and Music is that app), a `video` session goes nowhere (Q4: an app with no tile shows nowhere; W10M's
  Movies & TV tile carried no now-playing). The same routing covers `ActiveTiles.setPackage`. This is an ADD to
  phase 10's part (MusicFeed) and to phase 01's (StartPage fallback), recorded in the INDEX Change Log when
  built; E16 re-runs phase 10 E10 / E13 under it. Flagged to the lead for phases 15 / 16, which meet the same
  fallback.
- 2026-09-22 (agent): **R11 gates FINAL.** Every visual and motion value in this doc is either "from R11
  §Photos / §Camera / §Movies & TV" (pending, docs/plan/r11-inbox-apps.md) or a value this build already
  measured: status bar 28 epx (R3 C4), list rows and letter groups (R3 C2 / R6 §5.1.4), the pivot header and
  its 250 ms settle (phase 10 task 6's MusicMetrics, P4 for the header), the tap-launch Start exit (R3 A11),
  Settings-page rows for each app's settings (R3 C1). Anything else is an approximation (Y rows below) with its
  own NEEDS-HUMAN row (RV9 / Q10). Motion is planned as approximations from the start (R8's video-extraction
  blocker is expected to recur — R10 design review finding 10); if R11 measures it, the Y row closes.
- 2026-09-22 (agent): **the two kinds of NEEDS-HUMAN row are labelled** (R10 testability 26): *fidelity* rows
  ("matches R11 §x within tolerance", judged on the phone) and *accept* rows (a P4 design or an approximation
  with no footage; Jeremy accepts or overrules). qa/phase-17/NEEDS-HUMAN.md follows qa/phase-03/NEEDS-HUMAN.md's
  shape and carries the kind in its "why it is his call" column.
- 2026-09-22 (agent): **the Photos tile already exists (phase 01; INDEX Change Log 2026-09-22 items 3 and 4:
  slideshow and picture frame).** The app is what the tile OPENS; PhotosFeed keeps publishing the tile's faces
  and is not replaced. The app has its own library index (images + videos, grouped, paged) because the tile's
  reader (the newest N as faces) is a different shape from a gallery, so it is not a second copy of one thing.
  Both watch `MediaStore` with a ContentObserver.
- 2026-09-22 (agent, P5 stated reason): **the camera library is CameraX** (androidx.camera, Apache-2.0). It is
  a Jetpack library, not a Google service — the same class as Media3, accepted in phase 10 Q8 — and it needs no
  Play services and makes no network request. Reason it is used over raw Camera2: its device-quirk layer
  (Samsung preview/rotation quirks are the documented cases), its video-capture pipeline, and its Extensions
  API, which is the ONLY public route to Samsung's HDR / Night / Bokeh vendor extensions (the AVD ships
  `com.android.cameraextensions`, `pm list packages` 2026-09-22). The pro dial (Q2 B) reaches manual controls
  through CameraX's Camera2 interop, so raw Camera2 is not needed for it either. Recorded per P5.
- 2026-09-22 (agent): **the AVD has one camera, facing back, and no front camera.** `dumpsys media.camera`
  2026-09-22: "Number of camera devices: 1", device "1", "Facing: Back"; config.ini `hw.camera.back=emulated`,
  `hw.camera.front=none`. Capture rows run on the virtual back camera; every front-camera, flash, HDR, zoom
  and quality row is phone-only (P1) with an accept H-row for image quality.
- 2026-09-22 (agent): **Samsung's side-key double press cannot be pointed at this camera by the shell.** The
  quick-launch is One UI's own setting with no public action; whether One UI's Side key page offers "Open app"
  for a third-party camera is recorded by P2, not assumed. The doc states the limit (PLAN "Android limits")
  rather than planning around it.
- 2026-09-22 (agent): **the video player owns its own ExoPlayer and its own media session, inside the video
  activity, and MusicService is untouched.** Media3 is already a dependency (phase 10 Q8), so no second media
  library ships (Rule 16 / RV2). A video needs a Surface bound to its activity and must not share Music's queue;
  the session exists for headset / Bluetooth buttons and for `dumpsys media_session` to show it, and it is the
  `video` session the routing rule above ignores. Audio focus is requested with the same attributes phase 10
  uses, so a video pauses Music and Music does not resume by itself (phase 10 E9's rule).
- 2026-09-22 (agent): **Photos writes through `MediaStore.createWriteRequest` / `createDeleteRequest`** (the
  gallery API since API 30: one system consent dialog per batch, drawn by MediaProvider), which works with the
  permissions this phase holds. Phase 18's All-files access does not change Photos' code path, so there is one
  permanent form; whether the platform skips the dialog when that access is held is recorded in phase 18's rows,
  not assumed here. Edits are saved as a COPY next to the original (the original is never rewritten).
- 2026-09-22 (agent): **processes.** Camera runs in `:camera` and the video player in `:video` (R10
  testability 27: capture and decode must not be able to take Start down), as `:speech` and `:ime` do;
  `ShellApp.onCreate` already returns early outside the main process (INDEX Change Log 2026-09-22, phase 05
  item 1), so neither process starts the feeds. Each has its own diagnostics ring with a dump path, read the way
  phase 05 reads the `:ime` ring; the path is named in this doc at build start. Photos runs in the main process.
- 2026-09-22 (agent): **harness contracts every new window signs** (R10 testability 24, 25): the root of each
  activity's content sets `Modifier.semantics { testTagsAsResourceId = true }` as MusicActivity.kt and
  WeatherActivity.kt do, and the node that CARRIES each text a row reads has its own tag (phase 10's
  `music_pri:` / `music_sub:` lesson); every silent-empty state writes a diagnostics line the row asserts as well
  as the screen (`[photosapp]`, `[camera]`, `[video]` tags, wording in E18). Drivers symlink phase 03's
  qa/phase-03/scripts/lib.sh (INDEX 2026-09-22: phase 01 did the same); evidence lives under qa/phase-17/.
- 2026-09-22 (agent): **app list.** Three new launcher entries: "Photos", "Camera", and the video app whose
  label comes from the A10 branding module (W10M's own "Movies & TV" by default, swappable like every Microsoft
  name). They are excluded from the hold menu's Uninstall by `AppUninstall.canUninstall`'s self-package rule
  already (INDEX 2026-09-22 UNINSTALL), and E2 proves it rather than assumes it. The app-list regression
  (phase 02's regress.sh pattern, R10 testability 35) runs once for this phase: the entries appear under their
  letters with no "New" caption (X14 reads the shell's firstInstallTime).
- 2026-09-22 (agent): **APK budget.** Phase 03 Decisions set ≤ 600 MB; the debug build measured 334,447,554
  bytes on 2026-09-22. CameraX is the only new dependency; E17 checks the size after this phase.
- 2026-09-22 (agent): **bars.** All three apps are shell-owned screens under phase 01's bar rule: Samsung's
  bars hidden (`hideSystemBars()`), the drawn W10M status bar and nav bar. The viewfinder and the full-screen
  viewer / player draw the nav bar and hide the status bar, the form W10M's Camera and Photos viewer used — a
  candidate until R11 §Camera / §Photos confirms it (Y7).

### Approximations (until R11 lands; each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Photos collection: month headers, thumbnail grid pitch, album tiles | R11 §Photos pending | app-list rows (R3 C2 / R6 §5.1.4) for lists; a 4-across square grid with 2-epx gutters (agent pick) | H6 |
| Y2 | Photos viewer chrome and bottom app bar glyph set (share, delete, edit, …) | R11 §Photos pending | 48-epx app bar (R8's 48-epx app header class) with Segoe-substitute glyphs | H6 |
| Y3 | Camera viewfinder chrome: shutter, video / photo switch, flash / timer / front-back row, zoom | R11 §Camera pending | shutter 64 epx centred above the nav bar; controls in a 48-epx row at the top (agent pick) | H6 |
| Y4 | Pro dial (Q2 B): arc, ticks, value labels | R11 §Camera pending | a quarter-arc dial from the shutter, one control at a time (agent pick from Lumia Camera's form) | H6 |
| Y5 | Video player transport and scrubber | R11 §Movies & TV pending | phase 10's now-playing scrubber (R8 §1.5 thumb and track) with play / pause, ±10 s, fullscreen | H6 |
| Y6 | Motion: viewer open / close, photo swipe, camera mode switch, player controls fade | R11 or UNMEASURED | 250 ms ease-out for opens (X13's settle), 3 s controls auto-hide (agent pick) | H4 |
| Y7 | Which bars the viewfinder, viewer and player draw | R11 pending | nav bar drawn, status bar hidden | H6 |

## Interview queue (Stage A step 4)
Load-bearing first. Implementation mechanics are the agent's (P3) and are not asked.

1. ~~Q1 — what Photos is~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q1 — what Photos IS.** W10M's Photos had a collection, albums, a viewer, share / delete / set-as, a
   slideshow and an editor. Which form is built (one form only, Hard Rule 16)?
   A. Viewer + collection + albums + share / delete / set as / slideshow — no editing.
   B. A plus W10M Photos' editor: crop, rotate, straighten, auto-enhance, light and colour, filters, red-eye,
   saved as a copy. (lean — phase 10 Q7's "EVERYTHING there is only one version built" precedent)
   C. B plus video trimming (W10M Photos could trim a video).
   D. Other / let me clarify.
2. ~~Q2 — Camera modes~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q2 — Camera modes.** Windows Camera on the final release had photo and video with automatic everything,
   plus the Lumia pro dial (manual focus, ISO, shutter, white balance, exposure), and on some phones panorama,
   slow motion and Living Images.
   A. Photo and video, automatic, with flash / timer / grid / front-back / zoom.
   B. A plus the pro dial. (lean — the dial is the part people remember)
   C. B plus panorama, slow motion and Living Images.
   D. Other / let me clarify.
3. ~~Q3 as first written~~ SUPERSEDED 2026-09-23 by the A11 amendment (see Decisions); re-asked as Q3b below.
   **Q3 — what the video app IS.** Movies & TV's local half was a Videos library plus the player; its store
   half is out (A11).
   A. A Videos library (all videos, by folder) plus the player. (lean)
   B. The player only, reached from Photos, Files and "open with" — no library page, no app-list entry.
   C. A plus the player also handling audio-only files as a second Music entry point.
   D. Other / let me clarify.
3b. ~~What Movies & TV is~~ RULED 2026-09-23: A, and a TV-channels app split out (see Decisions). Original question kept below.
   (added 2026-09-23) **What Movies & TV is, now that it may go online.**
   A. A W10M-style hub: your local videos, plus an online catalogue of films and shows (info and artwork from a public film
      database) showing where each title can be watched among the streaming apps on the phone, with "Watch on <service>"
      opening that app at the title; plus your own media server (Jellyfin or Plex) if you have one (lean)
   B. Local library and player, plus a "Streaming" pivot of shortcuts into the streaming apps you have (no catalogue, no search)
   C. A without the media-server part
   D. Other / let me clarify
4. **Q4 — videos in Photos.** W10M's Photos showed videos in its collection and played them in place.
   A. Photos shows photos and videos together; tapping a video opens the shared player screen. (lean)
   B. Photos shows photos only; videos live only in the video app.
   C. Photos shows both but plays videos inline in the viewer (a second player surface).
   D. Other / let me clarify.
5. **Q5 — the capture-intent contract.** Other apps ask for a photo with `IMAGE_CAPTURE` / `VIDEO_CAPTURE`;
   answering them makes the shell's Camera one of the choices (with Samsung Camera) and lets a caller get its
   picture from it.
   A. Yes: the shell's Camera answers both and returns the result to the caller. (lean — P2, fewer seams)
   B. No: it is a launcher app only; other apps keep using Samsung Camera.
   C. Yes for IMAGE_CAPTURE only.
   D. Other / let me clarify.

## Build tasks
1. **App identities.** Three launcher activities inside the APK: `PhotosActivity` (LAUNCHER + APP_GALLERY +
   VIEW image/*), `CameraActivity` (LAUNCHER + STILL_IMAGE_CAMERA + VIDEO_CAMERA; IMAGE_CAPTURE + VIDEO_CAPTURE
   per Q5; `android:process=":camera"`), `VideoActivity` (LAUNCHER + VIEW video/*; `:video`); labels, task
   affinities and portrait lock as MusicActivity; entries ADDed to qa/phase-03/exported-allowlist.txt with their
   reasons; each root sets `testTagsAsResourceId`. Permissions: CAMERA, READ_MEDIA_VIDEO,
   FOREGROUND_SERVICE_CAMERA (video recording continues through a screen-off only until the file is finalised —
   the recorder stops on screen-off, edge cases). Uninstall exclusion verified (E2).
2. **The routing fix** (Decisions): `MusicFeed` attributes a shell-owned session by its tag; `StartPage`'s
   slot fallback and `ActiveTiles.setPackage` follow. ADD to phases 01 and 10, Change Log entry. Done before
   task 3 so the seed never puts Music on the Photos tile even for one build.
3. **Slot seeding.** `assignSlotOnce("slot:photos:v1", Slot.PHOTOS, PhotosActivity)` and
   `assignSlotOnce("slot:camera:v1", Slot.CAMERA, CameraActivity)` where phase 10 runs the Music seed; the
   diagnostics line `[layout] assignSlotOnce … -> assigned | already run` is what E1 reads.
4. **Photos library and collection.** Images (and videos, Q4) from MediaStore with a ContentObserver;
   grouped by month (collection) and by bucket (albums); paged so thousands of rows stay responsive; the empty,
   denied and partial states (`READ_MEDIA_VISUAL_USER_SELECTED`, phase 01's edge-case rule) each with their
   line and the grant link where the empty state is (phase 10 task 10's pattern).
5. **Photos viewer and actions.** Full-screen viewer (swipe, pinch zoom, double-tap), share (ACTION_SEND with
   the content URI), delete (`createDeleteRequest`), set as Start background (the shell's own theme setting,
   the same persisted grant StartThemePage takes) and as lock-screen wallpaper (`WallpaperManager`,
   FLAG_LOCK), slideshow, and the editor per Q1 saving a copy through `MediaStore` (IS_PENDING then cleared).
6. **Camera.** CameraX preview / capture / video with the Q2 mode set; front / back, flash, timer, grid,
   tap-to-focus, pinch zoom; files written into DCIM/Camera through MediaStore with IS_PENDING and cleared on
   completion (never a bare file write); the capture-intent form per Q5 (returns RESULT_OK with the image at
   EXTRA_OUTPUT, or RESULT_CANCELED on Back); "camera in use" and "no camera" states with their lines; the
   `:camera` diagnostics dump path.
7. **Video library and player.** Q3's library (MediaStore videos, by folder, with duration); the player:
   ExoPlayer on a SurfaceView, its own MediaSession tagged `video`, audio focus, transport / scrubber / ±10 s,
   aspect and rotation from the file's metadata, subtitle tracks when present, an error state for files the
   device cannot decode, an explicit refusal for non-file schemes (A11) with its line.
8. **Settings + checklist rows.** Camera row (CAMERA), Videos row (READ_MEDIA_VIDEO), the Photos row's video
   half; each app's empty state links to its grant; "don't ask again" opens the app's settings page (phase 10
   task 10's pattern).
9. **Re-runs and regressions.** Phase 01 E4 (Photos and Camera), phase 03 E2 / E10, phase 10 E10 / E13 (under
   task 2), the app-list regression (phase 02 regress.sh pattern), the exported allow-list (phase 03 E5's
   method) and the APK size (E17).
10. **R11 values applied.** Once R11 §Photos / §Camera / §Movies & TV lands, every Y row that it measures is
    replaced by the measured value and E19 is written; the doc goes FINAL only then (RV9).

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps
follow RV13. Harness: qa/phase-17/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh; every row stamps
its driver blob and the installed APK. Device: the AOSP AVD tileshell_fhd (1080×2340 @ 450 dpi, API 36, no
Google, `sdk_phone64_x86_64` userdebug). "Diagnostics" is read with phase 01's command; the `:camera` and
`:video` rings with the dump path task 6 / 7 name.

**Fixtures.** Images: docs/plan/qa/phase-01/scripts/make_photos.py (six flat-colour PNGs, known RGB) pushed
into `/sdcard/DCIM/Camera` and `/sdcard/Pictures/QA-Album`, then `adb shell content call --uri
content://media/external/file --method scan_volume --arg external_primary` (phase 01 E6's command); counts
read with `adb shell content query --uri content://media/external/images/media --projection
_id:_display_name:width:height:relative_path`. Videos: made on the host with ffmpeg 4.4 (`/usr/bin/ffmpeg`,
2026-09-22) from `lavfi color=` sources concatenated so the frame is one solid colour per second for 10 s
(`qa-steps.mp4`, h264; also `qa-steps.webm` vp9, `qa-steps-hevc.mp4`, `qa-rot90.mp4` with a 90° rotation tag,
`qa-audio-only.mp4`, `qa-truncated.mp4` = `head -c 100000`, `qa-empty.mp4` = 0 bytes) pushed into
`/sdcard/Movies`, scanned the same way, counted with `--uri content://media/external/video/media --projection
_id:_display_name:duration:relative_path` (the AVD had 0 videos and 6 images on 2026-09-22). Decoders on the
AVD: h264 / hevc / vp8 / vp9 (`c2.goldfish.*` in /vendor/etc/media_codecs.xml, 2026-09-22).

**Emulator:**
- E1 **Slot seeding (phase 01 E4 re-cut).** `adb shell pm clear app.tileshell`, open Start: diagnostics show
  `[layout] assignSlotOnce slot:photos:v1 PHOTOS -> app.tileshell/.photos.PhotosActivity -> assigned` and the
  `slot:camera:v1 CAMERA` line; the dump's `tile:slot:PHOTOS` tap resumes PhotosActivity and `dock:slot:CAMERA`
  resumes CameraActivity (`adb shell dumpsys activity activities`, topResumedActivity); with three
  `STILL_IMAGE_CAMERA` fixture handlers and two `APP_GALLERY` ones still installed (`cmd package
  query-activities`, phase 01 E4's before/after lists), so the seed and not a one-handler auto-assignment did it.
  Upgrade half: re-point PHOTOS to Aves through Settings > Tile apps (phase 01 E4b's flow), `adb install -r` the
  same APK, reopen Start: the assignment is still Aves and the line reads `-> already run`. Phase 01 E4's
  2+-handler negative is re-run on Mail and Store only.
- E2 **App list.** The three entries appear under P, C and M (dump `applist_row:` tags), each hold menu offers
  Pin to Start and NOT Uninstall (qa/phase-01/UNINSTALL's method, both halves), no "New" caption (phase 01 E12's
  caption geometry rule), and phase 02's regress.sh pattern passes: A-Z groups and jump grid unchanged apart
  from the three rows, a grid-tile tap still launches, the pivot still swipes.
- E3 **Photos library.** Six images in two folders (fixtures): the collection lists six `photos_item:<id>`
  rows newest first under one month header; the albums pivot lists `Camera` and `QA-Album` with counts 3 / 3;
  `adb shell rm /sdcard/Pictures/QA-Album/qa-photo-5.png` + the scan → the row disappears with no restart
  (the observer; diagnostics `[photosapp] library: images=5 …`). `adb shell pm revoke app.tileshell
  android.permission.READ_MEDIA_IMAGES` → the page says it cannot read the pictures, names the Setup checklist,
  offers the grant (dump text), line `[photosapp] access=DENIED`; `pm grant … READ_MEDIA_VISUAL_USER_SELECTED`
  with IMAGES still revoked → the subset shows and the line says `access=PARTIAL`; restore `pm grant …
  READ_MEDIA_IMAGES`. With Q4 A, the two `qa-steps` videos count in the collection with a duration glyph.
- E4 **Viewer.** Tap `qa-photo-0` → full-screen viewer; `screencap` centre pixel = (220,40,40) ± 4 per
  channel; `adb shell input swipe 900 1170 180 1170 200` → next image, centre pixel = (40,180,80); Back returns
  to the collection at the same scroll position (dump). Pinch zoom cannot be injected through `adb shell input`
  (single pointer), so zoom is P5 / H9.
- E5 **Photos actions.** Share → `dumpsys activity activities` shows the system chooser
  (`com.android.intentresolver`) resumed with `ACTION_SEND` and a `content://media/…` stream; delete → the
  MediaProvider consent dialog is on top (`dumpsys window` / dump shows
  `com.android.providers.media.module`), accepting it drops the `content query` count by one and the row from
  the collection; set as Start background → Start's `screencap` at a tile-free point equals the fixture colour;
  set as lock screen → `adb shell dumpsys wallpaper` shows the lock wallpaper id changed, then cleared by the
  row's restore; slideshow advances at the R11 / Y6 interval (screencap pixels at t, t+interval).
- E6 **Editing (Q1 B / C).** Crop `qa-photo-1` to its centre half → `content query` count +1 with
  `width:height` = 320×240 and the original row unchanged (same _id, same size); rotate → a new row whose
  `width:height` are swapped; the originals' md5 (`adb shell md5sum`) unchanged.
- E7 **Still capture on the virtual back camera.** Open CameraActivity (`dumpsys media.camera` shows device 1
  opened by `app.tileshell:camera`, one device on this AVD, no front camera — Decisions), tap `camera_shutter`
  → images count +1 with `relative_path` `DCIM/Camera/` and `width:height` equal to the chosen resolution
  (`is_pending` 0 within 2 s); the Photos tile's `[photos] refresh (mediastore change)` line follows within
  2 s of the row's `date_added` (phase 01's threshold); the Camera row tile's bounds and face are unchanged.
- E8 **Video capture.** Tap `camera_record`, wait 5 s, tap again → videos count +1, `duration` ≥ 4500,
  `relative_path` `DCIM/Camera/`; `adb pull` it and `ffprobe -show_streams` shows one video and one audio
  stream (the AVD microphone through qa/phase-03/scripts/audio.sh's null-sink route).
- E9 **Capture-intent contract (Q5 A).** `adb shell am start -a android.media.action.IMAGE_CAPTURE` → the
  resolver lists `app.tileshell` beside `com.android.camera2`, Open Camera and Fossify Camera (dump); a QA
  fixture APK of the same kind as phase 02's client-library test APK starts IMAGE_CAPTURE with `EXTRA_OUTPUT`
  aimed at its own cache and logs the result: choosing the shell's Camera, tapping the shutter and then Done
  gives RESULT_OK with a decodable JPEG at that URI (`adb shell run-as` is not available on a release build, so
  the fixture logs the file's size and md5; the driver reads them with `adb logcat -d -s TileShellQa`); Back on
  the capture form gives RESULT_CANCELED and leaves no `is_pending` row in MediaStore.
- E10 **Tess (phase 03 E2 / E10 re-run).** "Take a photo" through phase 03's audio route
  (qa/phase-03/scripts/audio.sh, utterances.py): `dumpsys activity activities` topResumedActivity =
  `app.tileshell/.camera.CameraActivity` and the reply "Opening the camera." in diagnostics (E2's row with the
  new component); with `adb shell locksettings set-pin 1234` and Cortana over the keyguard (E10's setup),
  "take a photo" shows "Unlock to continue" and no CameraActivity starts (expectation unchanged); restore
  `locksettings clear --old 1234`.
- E11 **Video library and player.** `qa-steps.mp4` listed with duration `0:10` (dump `video_row:` text); tap
  → plays; `screencap` at t = 3 s (timed from the `[video] playing` line) has the centre pixel = colour 3 ± 8 per
  channel; a tap on the scrubber at 70 % of its measured width seeks to colour 7 ± 1 s; pause holds the pixel
  for 2 s; `adb shell dumpsys media_session` shows an active session for `app.tileshell` (the video's); the
  dump of Start afterwards shows NO tile carrying a now-playing face and `[music]` logs no publish for it
  (Decisions: a `video` session lands nowhere).
- E12 **Audio focus and media keys.** Play a track in the shell's Music (qa/phase-01/MUSIC6's fixtures), open
  the video: `dumpsys media_session` shows Music's session PAUSED and the video's PLAYING; `adb shell input
  keyevent KEYCODE_MEDIA_PAUSE` pauses the VIDEO (the last active session), not Music; Back out of the video →
  Music stays paused (phase 10 E9: nothing resumes by itself).
- E13 **VIEW contract and the A11 negative.** `adb shell am start -a android.intent.action.VIEW -d
  content://media/external/video/media/<id> -t video/mp4` → the resolver lists the shell's player beside Aves and
  Fossify Gallery (dump); `adb shell am start -n app.tileshell/.video.VideoActivity -a
  android.intent.action.VIEW -d http://127.0.0.1:1/x.mp4 -t video/mp4` → the player shows "Only videos on this
  phone play here" (dump text), diagnostics `[video] refused scheme=http`, and no ExoPlayer source is created.
- E14 **Odd files.** `qa-steps.webm` (vp9) and `qa-steps-hevc.mp4` play (pixel rule as E11); `qa-rot90.mp4`
  draws portrait (the coloured area's aspect from `screencap` is taller than wide); `qa-audio-only.mp4` plays
  with a black frame and takes focus; `qa-truncated.mp4` and `qa-empty.mp4` show the error state ("can't play
  this file") with the player still resumed and `adb logcat -d -s AndroidRuntime` empty of `app.tileshell`.
- E15 **Permissions, checklist and processes.** `pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO`
  → the video app's empty state names the checklist and the "Videos" row is red (dump); `pm revoke … CAMERA` →
  the Camera app shows the grant page and the "Camera" row is red; granting through Android's real dialog from
  the app (phase 10 MUSIC10's method) turns both green; `adb shell pidof app.tileshell:camera` exists while the
  viewfinder is open, and after `adb root; kill -9 <pid>; adb unroot` the launcher pid is unchanged and Start
  keeps updating (phase 03 E12's method); the same for `:video`.
- E16 **Routing fix (phase 10 E10 / E13 re-run).** Play a track in the shell's Music: the MUSIC slot tile grows
  and shows the face and its controls drive playback (E13's three findings); the PHOTOS tile and the
  `dock:slot:CAMERA` tile have unchanged bounds and no now-playing tags in the dump; playing in Auxio (a pinned
  tile) still moves the face to Auxio's tile (E11). After a phase 03 reminder fires, neither the Photos tile nor
  the Camera row tile shows a badge from the shell's own notification.
- E17 **Budget and surface.** `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes
  (600 MB, phase 03 Decisions); `adb shell dumpsys package app.tileshell` exported components equal
  qa/phase-03/exported-allowlist.txt plus this phase's ADDs, exactly (phase 03 E5's method).
- E18 **Diagnostics.** Each of these lines exists when its state does, and the rows above assert the line as
  well as the screen: `[photosapp] library: images=<n> videos=<n> access=<GRANTED|PARTIAL|DENIED>`,
  `[camera] devices=<n> front=<present|absent>`, `[camera] busy: <reason>`, `[camera] saved <uri> <w>x<h>`,
  `[video] library: <n>`, `[video] playing <id>`, `[video] refused scheme=<s>`, `[video] cannot decode <name>`.
- E19 **Geometry and motion against R11.** Every value R11 §Photos / §Camera / §Movies & TV measures is within
  its tolerance (dump bounds, screencap; px ÷ 3 = epx; RV11 for motion). Written once R11 lands; until then
  this row is not runnable and the doc cannot go FINAL.

**Phone-only:**
- P1 Front camera, flash, HDR / Night (CameraX Extensions as the S25U exposes them), zoom across lenses:
  `adb shell dumpsys media.camera` lists the ids the public API exposes (logical multi-camera vs separate ids),
  each mode captures to MediaStore (E7's assertions), image quality judged in H5.
- P2 Side key double press: whether One UI's Side key page offers "Open app" for the shell's Camera; recorded
  either way; the shell cannot set it (Decisions).
- P3 The S25U's own recordings in Photos and the player: HEIC stills, HEVC / HDR10+ / 4K60 videos, Samsung
  Motion Photos (shown as stills); decode and playback asserted with E11's pixel rule where a fixture allows.
- P4 One UI's "Open with" for `VIEW video/*` and `VIEW image/*`: which apps the sheet lists and whether the shell
  can be set as default (`am start` with a MediaStore URI, the resolver captured).
- P5 Pinch zoom and double-tap in the viewer and the viewfinder (no multi-touch injection on the AVD).
- P6 Performance with Samsung Camera's DCIM at thousands of items: `dumpsys gfxinfo app.tileshell reset` /
  print during a scripted collection scroll, phase 01 P4's method and thresholds.
- P7 `dumpsys meminfo app.tileshell:camera` and `:video` during capture / playback and after close (released).
- P8 The Camera and Videos checklist rows through One UI's permission dialogs (phase 01 P2's flow).

**NEEDS-HUMAN:** H1 *fidelity* — Photos matches R11 §Photos on the phone; H2 *fidelity* — Camera matches
R11 §Camera; H3 *fidelity* — the player matches R11 §Movies & TV; H4 *accept* — motion approximations (Y6)
where R11 cannot measure; H5 *accept* — capture image and video quality on the S25U (no metric); H6 *accept* —
any Y1–Y5 / Y7 value R11 does not measure; H7 *accept* — the video app's label from the branding module;
H8 *accept* — the shell-package routing rule (Music's face on the Music tile only; a video on no tile);
H9 *accept* — pinch-zoom and double-tap feel (P5).

## Edge cases
- Thousands of photos: 3,000 fixtures generated with make_photos.py's method in a loop, pushed and scanned;
  `am start -W` of PhotosActivity reports TotalTime < 2000 ms; the collection scroll meets phase 01 P4's
  gfxinfo thresholds on the AVD; PhotosFeed's tile lines are unaffected.
- HEIC and RAW: a HEIC fixture (from the S25U; ffmpeg 4.4 cannot write one) decodes through the platform HEIF
  decoder or shows a placeholder, never a crash; a DNG shows its embedded preview or a placeholder.
- A MediaStore row whose file is gone (`adb shell rm` without a scan): the thumbnail is a placeholder, the
  viewer shows the error state, and the next scan removes the row.
- Videos: a 4K fixture above the AVD decoder's capability → error state, no crash; a file with no audio track
  still takes audio focus; a file with two audio tracks plays the first; subtitles (`.srt` beside the file or an
  embedded text track) toggle.
- Storage full while recording: `adb shell fallocate -l $(( $(adb shell df -k /sdcard | awk 'NR==2{print $4}') - 3000 ))K /sdcard/fill.bin`,
  start recording → the recorder stops with "storage full", the partial file is finalised (`is_pending` 0) and
  playable or removed, never left pending; restore by deleting fill.bin.
- Camera in use by another app: Open Camera in the foreground, then ours via `am start` → Android's camera
  service gives the foreground app the device; ours shows "camera in use" only while the other app is in front
  and re-opens on resume (`[camera] busy`, then `[camera] devices=1`); the reverse order evicts ours cleanly.
- Permission revoked mid-session: `pm revoke … CAMERA` with the viewfinder open → the platform restarts the
  process; the next open shows the grant page; `pm revoke … READ_MEDIA_IMAGES` with the viewer open → the same
  restart and the checklist state on return.
- Screen off or an incoming call mid-recording (`input keyevent KEYCODE_SLEEP`; `adb emu gsm call 5551234`):
  the recording stops and is finalised; the file plays.
- A `:camera` or `:video` process killed by the system mid-write: on the next start any `is_pending=1` row of
  the shell's is cleaned (`content query --projection _id:is_pending` shows none).
- The picture-frame photo deleted in Photos: PhotosFeed logs `main photo unreadable` and the tile falls back
  (phase 01 item 4's rule).
- PHOTOS or CAMERA re-pointed to another app: the shell's apps stay in the app list and keep working; Tess's
  "take a photo" then opens that app (slot resolution, phase 03 edge case "slot app unassigned" still holds).
- APK updated (`adb install -r`) while a video plays: the player stops cleanly and `dumpsys media_session`
  shows no orphan session.
- A capture-intent caller cancelled or killed mid-capture: no orphan file (E9's negative).
- USB OTG drive holding a video (P): plays while attached; pulled mid-play → error state, no crash. The S25U
  has no SD slot.
- Liveness (N-01): reboot and Device care leave the seeds, the checklist rows and the observers intact.

## QA evidence
_None yet._
