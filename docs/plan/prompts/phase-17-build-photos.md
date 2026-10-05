# Phase 17 build brief — Photos (build tasks 4 and 5, Photos' half of 15)

Read `docs/plan/prompts/phase-17-build-common.md` first: its rules bind you.

- Worktree: `/home/jeremyking/projects/metro-launcher-p17/.claude/worktrees/photos`, branch `phase-17-photos`, cut from
  `phase-17` at eb725c76 (build tasks 0, 1, 3, 8 and 17's core, the write layer and the editor's matrices are in).
- Scratch files: prefix them `photos-` in `/tmp/claude-1000/-home-jeremyking/b5b8c63b-5d38-49e9-84f3-de917a96acb2/scratchpad/`.
- The other builders: Camera (`.claude/worktrees/camera`: `camera/`, the OpenCV native build, `app/build.gradle.kts`,
  CI) and Movies & TV (`.claude/worktrees/video`: `video/`, `testapps/qa-flix`). You do not touch their packages,
  their resources (`res/values/camera.xml`, `video.xml`, their shortcut files), `app/build.gradle.kts`,
  `gradle/libs.versions.toml` or the manifest's Camera and Movies & TV elements. `AndroidManifest.xml`: keep any edit
  to Photos' own elements.

## Yours
The `photos/` package (PhotosActivity, PhotosApp, ViewerActivity, ViewerScreen, EditActivity, EditScreen and everything
you add), `res/values/photos.xml`, `res/xml/shortcuts_photos.xml`, Photos' glyphs in `brand/Glyph.kt`, tests under
`app/src/test/kotlin/app/tileshell/photos/`, and `docs/plan/qa/phase-17/dev-photos/`.

## Read (fully, before code)
1. The phase doc, all of it. Your clauses: Scope's Photos bullet; Decisions — the editor's matrices (the top lines of
   2026-10-05), Q1 and its note, Q4, "the Photos tile already exists", "Photos writes through createWriteRequest /
   createDeleteRequest" with r3 D7, "processes" (r3 D8), "harness contracts", "bars" (T17-16), T17-3 (the editor's
   transforms), C-5 (motion by the shell's clock), T17-16 / T17-17 (R11 applied; the version choice), the R11 pass-2
   line (r3 D3–D5), r3 D6 (the helpers; `startFeeds`), r3 D7 (the write layer), r3 D8 (memory), r3 D9 (Set as Start
   background), r3 V12, r3 V20; Approximations Y1, Y2, Y6 (the Photos motions), Y7, Y11, Y13; Build tasks 4, 5, 15;
   the Acceptance preamble; rows E3, E4, E5, E6, E6b, E18 (the `[photosapp]` lines and their `[motion]` lines), E19
   (Photos), E23 (Photos' half); P3, P5, P6, P16; H1, H4, H9, H13a, H13b, H17; the Edge cases that name Photos, HEIC,
   RAW, a missing file, a killed `:photosedit`, the picture-frame photo, a permission revoked mid-session.
2. `docs/plan/r11/photos.md` and `docs/plan/r11/photos-pass2.md` (its §4 governs where it corrects).
3. Code you extend or reuse: `photos/` (the shells), `feeds/PhotosFeed.kt` (the tile's reader — you do NOT replace it;
   `PhotosFeed.access` is the GRANTED / PARTIAL / DENIED rule and its ContentObserver form is the one to follow),
   `media/MediaWrites.kt`, `media/AndroidMediaStorePort.kt`, `onboarding/Checklist.kt` (the Photos and Videos rows;
   `ShellApp.startFeeds`), `prefs/ShellSettings.kt` and `settings/ThemePresetsUi.kt:235` with
   `ui/fluent/StaticBackdrop.kt` (how the Start background is set and drawn — r3 D9 changes what you store),
   `music/MusicCollectionPage.kt` (the pivot header and its 250-ms settle), `people/PeopleListPage.kt` (a pager with
   pivots, the app bar and its "…" menu), `clock/ClockWidgets.kt`, `ui/MotionClock.kt`, `ui/components/ModalOverlay.kt`,
   `video/PlayerActivity.kt` (what you start for a video; the Movies & TV builder fills it).

## Build (each clause as its Decisions line specifies it; commit after each lettered part is verified)

### A. Library and collection (build task 4)
1. A library index of images AND videos together from MediaStore (Q4 A), with a ContentObserver, read off the main
   thread and paged so thousands of rows stay responsive (the 3,000-photo edge case: `am start -W` TotalTime < 2000 ms).
   Videos need READ_MEDIA_VIDEO (the Videos row); images READ_MEDIA_IMAGES or the partial grant. The line `[photosapp]
   library: images=<n> videos=<n> access=<GRANTED|PARTIAL|DENIED>` after every read.
2. The Collection pivot (by month, with day rows) and the Albums pivot (MediaStore buckets with counts), drawn per Y1:
   tags `photos_pivot:collection` / `photos_pivot:albums` (the selected one `selected = true`), `photos_item:<id>` per
   tile, the album tiles and their counts tagged so E3 can read "Camera" and "QA-Album" with their counts. Newest first.
   A video tile carries Y1's disc with the play triangle. No status bar on any page; the first chrome row's top at 0.
3. The empty, denied and partial states, each with its text naming the Setup checklist, its line (`[photosapp]
   access=DENIED` …) and the grant where the empty state is (phase 10 task 10's pattern: Android's real dialog; "don't
   ask again" opens the app's settings page). The partial state's link opens Android's selected-photos picker
   (requesting the permission again re-opens it). After a grant made in place, call
   `(applicationContext as ShellApp).startFeeds("photos grant")` — only PhotosActivity does (r3 D6).
4. A tap on a video opens `app.tileshell/.video.PlayerActivity` by explicit component: `Intent(ACTION_VIEW)` with the
   item's MediaStore URI as data and its MIME type. Photos itself holds no player (E3 asserts no `video_surface` node
   in Photos' dump). Until the Movies & TV builder's player is merged, PlayerActivity is an empty page: prove the launch
   (the resumed activity), not playback.

### B. Viewer and actions (build task 5, first half)
1. The full-screen viewer per Y2 (the 50-epx date header, the photo fitted to width and centred on the whole screen, the
   48-epx black app bar Share · Edit · Delete · More, the "•••" expansion and the overflow Slideshow / Set as / File
   information): swipe between items with the 20-epx gap, pinch zoom, double-tap zoom; decoded at screen size through
   ImageDecoder's target size and the zoomed region re-decoded with BitmapRegionDecoder (r3 D8) — never a
   full-resolution bitmap in this process. Motions `viewer_open`, `viewer_close`, `photo_swipe` through MotionClock with
   Y6's values (the photo expands from its thumbnail and shrinks back to it).
2. `ViewerActivity` (other apps' `VIEW` on an image): the same viewer on the ONE picture the intent names, read under
   the caller's grant; the actions that need a MediaStore row (Edit, Delete, Set as) appear only when the URI is a
   MediaStore image the shell can read by itself; nothing is written from an intent.
3. Share (`ACTION_SEND` with the content URI and a read grant, through the system chooser); Delete
   (`MediaWrites.deleteRequest` → the system consent; Deny → `[photosapp] delete <id>: refused by user`, nothing
   changes); Set as Start background (r3 D9: decode at screen size, write `files/backgrounds/<id>.jpg`, point the
   theme's background at that file, `[photosapp] set as background <id> -> <file>` — an ADD to phase 01 / 12's theme
   store if its setter needs one: report it); Set as lock screen (`WallpaperManager`, FLAG_LOCK); File information;
   Slideshow (`[photosapp] slideshow next <id>` per step, 5 s apart ± 100 ms, in collection order, each with `[motion]
   slideshow_step …`, 250-ms settle).
4. A row whose file is gone: a placeholder tile, the viewer's error state, no crash. HEIC through the platform decoder;
   a DNG's embedded preview or a placeholder.

### C. The editor and trim (build task 5, second half) — in `.photos.EditActivity`, process `:photosedit`
1. `photos/EditMatrices.kt`: the doc's matrix block as a table, with a JVM test that READS THE BLOCK FROM THE DOC
   (`docs/plan/phase-17-inbox-photos-camera-video.md`, the lines starting `  matrix `) and fails when the table and the
   doc differ, plus tests of the step rules (light k, colour k) and the compose order (filter → light → colour →
   enhance), all free of Android types.
2. The tools (Q1 C): crop, rotate, straighten (rotate by the angle, centre-crop to the largest axis-aligned rectangle —
   the geometry pure and JVM-tested), auto-enhance, light, colour, filters, red-eye (T17-3's rule: red-dominant blobs,
   `r > 1.5·max(g, b)`, ≥ 6 px across; the red channel halved inside, nothing changed outside — the detector pure and
   JVM-tested on small arrays). The editor works on a ≤ 4096-px preview; "Save a copy" re-reads the original at full
   resolution and renders tile by tile (r3 D8, V12) — a file removed after a tool is applied fails the save with
   `[photosapp] edit <tool> failed: <why>` and leaves no row.
3. The UI per Y11 (the 48-epx bottom strip Crop · Enhance · Rotate · Save · More, the Edit sheet on the screen bottom;
   the four added tools reached from the strip with sliders — P4) and the trim screen per Y13 (the top command bar Save
   a copy · Cancel · More, the track with two handles and a time label at each end, no centred readout).
4. Saving: every result is a COPY through `MediaWrites.save` in `MediaWrites.copyPlacement`'s folder — a JPEG at
   quality 95 (PNG when the original is PNG; an edited HEIC saves as JPEG), upright with EXIF Orientation 1 and
   DATE_TAKEN / DateTimeOriginal copied (androidx.exifinterface is NOT in the build: use `android.media.ExifInterface`);
   `[photosapp] edit <tool> -> <uri>` in the `:photosedit` ring. The original row and file are never touched.
5. Video trim through Media3 Transformer (in the build): MP4 (H.264 / AAC) keeping the rotation and DATE_TAKEN, saved
   as a copy; `[photosapp] trim <id> <fromMs>..<toMs> -> <uri>`; an undecodable source → no row and `[photosapp] trim
   <id> failed: <why>`. No microphone, no audio capture: the source's own audio track is copied.
6. On each start of `:photosedit`, `MediaWrites.cleanUpPending()` (the edge case "a process killed mid-write").

### D. App Shortcuts (build task 15, Photos' half)
The static file exists (`photos_collection`, `photos_albums`); make each open its pivot with the header selected, and
prove it (`dumpsys shortcut`, the `page` extra).

### Not yours
Living Images' glyph and hold-to-play (it needs the Camera builder's Motion Photo reader; the lead joins the two after
the merge). The Settings > Diagnostics probe page. The gate's drivers and fixtures (`media_up`, `edit_expect.py`) — for
your own dev proof, push fixtures made with `docs/plan/qa/phase-01/scripts/make_photos.py` and remove them after.

## Proof you owe (dev proof, on the emulator, restoring what you change)
The library with pushed fixtures in two folders (counts, order, 3 columns, the observer removing a row with no
restart, the three access states); the video hand-off's resumed activity; the viewer's centre pixel, swipe, Back to the
same scroll position, double-tap; Share reaching the chooser; delete Deny then Accept; Set as background (the file under
`files/backgrounds/`, Start's pixel, and still there with READ_MEDIA_IMAGES revoked); lock-screen wallpaper changed and
restored; three slideshow lines 5 s apart; each editor tool's copy against the matrix result on the flat fixtures (± 4
per channel; every copy a new row with the original's md5 unchanged); straighten and red-eye on fixtures you make; the
failure line; a 2–5 s trim of a 10-s fixture made with ffmpeg (`ffprobe` duration 3.0 ± 0.1, one video stream, the
source's audio stream if it has one); `:photosedit` alive during an edit and the launcher's pid unchanged; E19's Photos
geometry from a dump (px ÷ 3 = epx).
