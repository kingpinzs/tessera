# Phase 17 build brief — Camera (build tasks 6, 6a–6d, Camera's half of 15, the OpenCV native build)

Read `docs/plan/prompts/phase-17-build-common.md` first: its rules bind you.

- Worktree: `/home/jeremyking/projects/metro-launcher-p17/.claude/worktrees/camera`, branch `phase-17-camera`, cut from
  `phase-17` at eb725c76 (build tasks 0, 1, 3, 8 and 17's core, the write layer and the output guard are in).
- Scratch files: prefix them `camera-` in `/tmp/claude-1000/-home-jeremyking/b5b8c63b-5d38-49e9-84f3-de917a96acb2/scratchpad/`
  (the build-start trial link of OpenCV is under `…/scratchpad/bs1/` — read it, do not depend on it).
- The other builders: Photos (`.claude/worktrees/photos`: `photos/`) and Movies & TV (`.claude/worktrees/video`:
  `video/`, `testapps/qa-flix`). You do not touch their packages or resources. You are the ONLY builder who edits
  `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `tools/` and `.github/workflows/apk.yml`.
  `AndroidManifest.xml`: keep any edit to Camera's own elements.

## Yours
The `camera/` package (CameraActivity, CameraApp, CaptureActivity, CaptureScreen, CameraDumpService and everything you
add), `media/MotionPhoto.kt` (new: the Motion Photo container's writer AND reader, pure, JVM-tested — Photos will use
the reader), the native build (`app/src/main/cpp/`, `tools/fetch-opencv.sh`), `res/values/camera.xml`,
`res/xml/shortcuts_camera.xml`, Camera's glyphs in `brand/Glyph.kt`, `testapps/qa-capture` (the capture-intent caller
fixture, in the form of `testapps/tileclient-a`), tests under `app/src/test/kotlin/app/tileshell/camera/` and
`…/media/`, and `docs/plan/qa/phase-17/dev-camera/`.

## Read (fully, before code)
1. The phase doc, all of it. Your clauses: Scope's Camera bullet; Decisions — the build-start line (BS-1, BS-2), Q-17-2
   (b) and the agent line "the capture helper's output guard and its no-output contract" (2026-10-05), Q5, Q2 with
   T17-2 (per mode), "the camera library is CameraX", "the AVD has one camera", "Samsung's side-key", "processes",
   "harness contracts", "bars", T17-4 (as r3 D1 replaces it), C-9 applied to Camera (dynamic shortcuts), C-5, T17-16 /
   T17-17 and the pass-2 line (r3 D3), T17-19 (no camera foreground service), r3 D6, r3 D7, r3 D10 (the pro dial's
   gates), r3 D11 (slow motion), r3 D12 (video sound and RECORD_AUDIO), r3 V15; Approximations Y3, Y4, Y6 (Camera's
   motions), Y7, Y12, Y14; Build tasks 6, 6a–6d, 15; the Acceptance preamble; rows E7, E8, E9, E15 (Camera's half), E17
   (OpenCV's bound; SET_WALLPAPER / no FOREGROUND_SERVICE_CAMERA), E18 (the `[camera]` lines), E19 (Camera, the pro
   dial), E23 (Camera's half); P1, P2, P9–P12; H2, H5, H14, H15, H16, H18, H20; the Edge cases that name the camera,
   recording, storage full, screen off, a call, a capture-intent caller, `EXTRA_USE_FRONT_CAMERA`, a killed `:camera`.
2. `docs/plan/r11/camera.md` and `docs/plan/r11/camera-pass2.md` (its §4 governs where it corrects).
3. `docs/plan/qa/phase-17/BUILDSTART/README.md`: the AVD's camera (LEVEL_3, MANUAL_SENSOR, AE OFF present, EV −9…9,
   focus distance 20, AWB modes 0 1 2 3 5 8, max zoom 10, no high-speed session), BS-1 (OpenCV's route), BS-2 (slow
   motion's route), and the capture-request resolution fact (the implicit query lists only the preinstalled camera; the
   request that names the shell resolves to CaptureActivity).
4. Code you extend or reuse: `camera/` (the shells), `media/MediaWrites.kt`, `media/AndroidMediaStorePort.kt`,
   `media/CaptureOutputGuard.kt` and their tests (read the guard's contract closely: `CaptureActivity` computes its
   inputs from the real intent and writes ONLY through `MediaWrites.writeCaptureOutput` with the guard's Accepted
   token), `onboarding/Checklist.kt` (the Camera row, the Microphone row is `cortana/CortanaChecklist.kt:47`),
   `settings/SettingsWidgets.kt` (R3 C1 settings rows), `clock/ClockWidgets.kt`, `ui/MotionClock.kt`,
   `tools/fetch-speech.sh` and its use in `app/build.gradle.kts` and `.github/workflows/apk.yml` (the form the OpenCV
   fetch follows), `testapps/tileclient-a` and `settings.gradle.kts` (how a fixture APK is a module), the app's licence
   page (`grep -rn "licenses" app/src/main/kotlin`) for the third-party notices.

## Build (each clause as its Decisions line specifies it; commit after each lettered part is verified)

### A. The viewfinder and the automatic modes (build task 6)
1. CameraX preview, still capture and video on the back camera, and on the front where the phone has one (the AVD has
   none: `[camera] devices=<n> front=<present|absent>` at start; the switch control is absent with no front camera).
   Stills and videos saved into `DCIM/Camera/` through `MediaWrites.save` only (`MediaWrites.CAMERA_PATH`), `[camera]
   saved <uri> <w>x<h>` per capture; EXIF present (orientation, DateTimeOriginal; GPS when the shell holds a location
   permission and has a fix — E9's GPS control expects GPSLatitude on the Camera's own capture under `adb emu geo fix`).
2. Flash, HDR (CameraX Extensions; hidden with `[camera] mode hdr: unavailable (<reason>)` where absent), the timer 2 s /
   5 s / off with Time lapse (`[camera] timer <n>s -> shutter`; the labels of Y3), tap-to-focus (`[camera] focus at
   <x>,<y>: <locked|unsupported|…>`), pinch zoom and a zoom the driver can set (E7 sets 2× — give it a tagged control or
   a debug-free route that works without multi-touch, e.g. a tap on a zoom readout cycling 1× / 2× / …; say which in
   your report), the framing grid (two lines each way at thirds of the preview's bounds).
3. Video sound only while RECORD_AUDIO is held; otherwise a silent take, `[camera] video sound: off (no microphone
   permission)` and a one-line hint naming the Microphone row (r3 D12). The recorder stops and finalises on screen-off,
   on a call, on storage full ("storage full"; never a pending row left). No camera foreground service.
4. The chrome per Y3 exactly (the 72-epx shutter disc and its rings, the 32-epx mode discs at ±60, the vertical capsule
   on the right edge with its items per mode, settings bottom-right, switch top-right, roll bottom-left, no mode strip,
   no status bar), tags: `camera_shutter`, `camera_record` (the shutter in video mode), `camera_mode:<id>` for each mode
   SHOWN (ids photo, video, panorama, slowmo, pro, hdr, livingimages as the doc's rows read them — E7 asserts the set of
   `camera_mode:<id>` nodes equals the list derived from the camera's characteristics, so decide the id set once, tag
   exactly the available ones, and list it in your report), the capsule's items, `camera_settings`, `camera_roll`
   (opens Photos' viewer on the last capture — start `app.tileshell/.photos.PhotosActivity` until Photos is merged).
   Motions through MotionClock with Y6's values: the mode switch (a ≈300-ms slide with a black gap), capture feedback
   (the preview black for ≈167 ms with the chrome unchanged), dial open.
5. Every mode entry gated by the camera's characteristics, each hidden mode logging `[camera] mode <x>: unavailable
   (<reason>)` — the gates as pure functions over a small characteristics value, JVM-tested (so E7 and the phone's
   probe page can derive the same list). "Camera in use" (`[camera] busy: <reason>`, re-opens on resume) and "no
   camera" states; the grant page when CAMERA is not held (the Setup checklist named, Android's real dialog).
6. The settings page per r11/camera.md 1.7 / §2 minus Lenses, OneDrive and "Related settings", with "Framing grid" and
   "Capture living images" rows, resolution choices from the camera's supported sizes.
7. On each start of `:camera`, `MediaWrites.cleanUpPending()`.

### B. The capture answer (build task 6; TRUST-TOUCHING) — `.camera.CaptureActivity`
1. `IMAGE_CAPTURE` and `VIDEO_CAPTURE`: the decision is `CaptureOutputGuard.decide` over the real intent
   (`EXTRA_OUTPUT`, `callingPackage`, every ClipData item's URI, the intent's flags, the authorities of the shell's own
   providers read from PackageManager) BEFORE anything is shown. Refused → `setResult(RESULT_CANCELED)`, the guard's
   line in the `:camera` ring, finish, nothing written. If you find a case the guard decides wrongly, do not patch
   around it in the activity: report it (the guard is the lead's and under adversarial review).
2. Accepted → the viewfinder, shutter, then the accept / retake bar (Y14), then the capture written ONLY through
   `MediaWrites.writeCaptureOutput(accepted, …)`, RESULT_OK; no copy in DCIM; EXIF location stripped.
3. No EXTRA_OUTPUT: IMAGE_CAPTURE → RESULT_OK with a small thumbnail Bitmap in the `data` extra and NO file anywhere;
   VIDEO_CAPTURE → a `DCIM/Camera/` row through `MediaWrites.save`, its URI returned as the result's data with
   FLAG_GRANT_READ_URI_PERMISSION.
4. Back → RESULT_CANCELED and no pending row. `EXTRA_USE_FRONT_CAMERA` (and the legacy front-camera extras) with no
   front camera → the back camera answers.
5. `testapps/qa-capture` as the doc's Fixtures paragraph specifies it (every request names the shell with
   `setPackage("app.tileshell")`; legs: content output through its own FileProvider, the `file://` output with its
   StrictMode VmPolicy relaxed, no-output image and video, its own ClipData with no grant flag aimed at a URI the driver
   passes, and receiving `ACTION_SEND image/*`; it logs to the `TileShellQa` logcat tag the result code, `exists=` /
   `size=` and md5 of its output, the `data` Bitmap's size, the returned URI). Drive each leg in your dev proof.

### C. Pro dial (6a)
`Camera2CameraControl` interop; r3 D10's gates as pure functions; the five-arc geometry of Y4 (pure geometry,
JVM-tested: radii, centres, label positions, the single-control arc) opened by sliding the shutter left; EXIF carries
ISO, exposure time and white balance. The AVD admits all five controls: prove the dial there (E19's dial sub-row).

### D. Panorama (6b) and the native build
1. `tools/fetch-opencv.sh` fetches the official OpenCV 4.14.0 Android SDK zip (BS-1's URL; verify a pinned SHA-256
   you compute and record) into a git-ignored directory; CMake (`app/src/main/cpp/CMakeLists.txt`) links the static
   libraries `core imgproc features2d flann calib3d stitching` into ONE JNI library `libopencv_pano.so` with
   `c++_static`, built for **arm64-v8a only** (the app's Kotlin still ships x86_64; no `lib/x86_64/libopencv*` may
   exist in the APK). The JNI surface is small: frames in, one stitched image out, a status code.
2. E17's bound: `unzip -l app-debug.apk 'lib/arm64-v8a/libopencv*'` summed ≤ 20,971,520 bytes with ≥ 1 entry, and none
   under `lib/x86_64`. Quote the listing. Check the APK's other native libraries are unchanged in count and ABI.
3. `.github/workflows/apk.yml` gains the fetch and whatever the native build needs (the NDK, CMake), following how it
   fetches the speech AAR. You cannot run CI: say exactly what you changed and what is unproven.
4. The capture UI per Y12; a burst of frames while the guide is followed, stitched off the main thread; output wider
   than one frame, EXIF present, saved through `MediaWrites.save`; `[camera] mode panorama: cancelled (<reason>)` when
   interrupted, no pending row. On x86_64 the mode is hidden with `[camera] mode panorama: unavailable (no native
   library for x86_64)` — the AVD proves the hidden path; the stitch itself cannot run on this emulator. Prove the
   native library LOADS and stitches off-device if you can (e.g. a host-side build of the same shim against desktop
   OpenCV is NOT available — say plainly what is unproven; P10 is the phone's row).
5. OpenCV's and its linked third parties' licence texts added to the app's licence page.

### E. Slow motion (6c)
BS-2's route: `Recorder.getHighSpeedVideoCapabilities(cameraInfo)` null → hidden with `[camera] mode slowmo:
unavailable (no high-speed session)`; else `HighSpeedVideoSessionConfig(…, isSlowMotionEnabled = true)` at the highest
supported range, no audio, `[camera] slowmo <n> fps captured, encoded at 30 -> <uri>`. The AVD has no high-speed
session: prove the hidden path and its line; the capture is the phone's row (P11) — say so.

### F. Living Images (6d)
`media/MotionPhoto.kt`: writer (a JPEG with a trailing MP4, XMP `Camera:MotionPhoto=1`,
`MotionPhotoPresentationTimestampUs`, `Container:Directory` with the item lengths — one file) and reader (is it a
Motion Photo; the clip's offset and length), JVM-tested on byte arrays. The camera buffers about 1 s before the shutter
and writes the still and the clip as one file through `MediaWrites.save`; switched by "Capture living images" on the
settings page. E7 checks on the AVD: the file ends with an MP4 (`ftyp` in its tail), the XMP reads 1 and the directory's
item length equals the trailing MP4's size. Photos' glyph and hold-to-play are not yours.

### G. App Shortcuts (build task 15, Camera's half)
`camera_photo` and `camera_video` are static (the file exists; make each open its mode). `camera_panorama` and
`camera_slowmo` are DYNAMIC: published by CameraActivity on each start only where the mode's gate admits it (ranks 2–3),
removed otherwise, each with `ShortcutInfo.Builder.setActivity(ComponentName(CameraActivity))` (C-21). On the AVD
neither exists: prove their absence in `dumpsys shortcut` and the publishing rule in a JVM test.

## Proof you owe (dev proof, on the emulator, restoring what you change)
A still (count +1 in DCIM/Camera, `is_pending` 0, the chosen size, the saved line); the 2-s timer's window; the grid's
lines in a screencap; tap-to-focus's line; 2× zoom measured on the emulated scene; the mode list equal to the list
derived from `dumpsys media.camera` with a line for every hidden mode; a 5-s video with RECORD_AUDIO revoked (one video
stream, no audio stream, the sound-off line and hint) and the permission granted back; every capture-answer leg through
qa-capture including the three refusals and Back; the pro dial's geometry from a dump; Living Images' file checks; the
Camera row and grant page with CAMERA revoked then granted back; `:camera` killed (`adb root; kill -9 <pid>; adb
unroot` — the one allowed use of root, undone at once) with the launcher's pid unchanged; E19's Camera geometry from a
dump (px ÷ 3 = epx); E17's OpenCV listing; captures you made removed at the end.
