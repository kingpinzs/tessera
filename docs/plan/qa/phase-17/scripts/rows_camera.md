# Phase 17 — the Camera rows: E7, E8, E9, E15, E19 (Camera and the Pro dial), E23 (Camera), the Camera's edge cases

Written on `phase-17-qa-camera`. Every driver sources `lib.sh`, `p17.sh`, then `cam17.sh` (the Camera rows' include);
takes the device lock; installs the worktree's debug APK when the device holds another build; asserts the build's id;
restores what it changes. Run one as `env -u TMPDIR bash docs/plan/qa/phase-17/scripts/<driver>`; a busy lock is exit 3
(wait in the lock's queue and run it again). A run's folder is `docs/plan/qa/phase-17/<ROW>/`, renamed after the run.

Two builds carry this evidence, both CLEAN builds (`./gradlew clean`, then `:app:assembleDebug --offline`):

- **e8c26851363882da** (355,408,182 bytes) — `phase-17` at 25921fd7, before the trust fixes.
- **c7336aca6b63d61b** (355,474,253 bytes) — `phase-17` at e5e30678, the trust fixes merged. `cam17.sh` asserts this id
  now; a kept run of the earlier build is re-read with `GATE_APK_ID=e8c26851363882da`.

The fixes touch the capture answer, the viewer and the player, not the Camera's own viewfinder: the rows run before
the merge and not touched by it keep their run, as the lead ruled.

| Row | Driver | Build of the evidence | Last run | Folder | What it changes on the device (all restored) |
|---|---|---|---|---|---|
| E7 | `e7.sh` | e8c26851 | 101 passed, 0 failed, 35 recorded | `E7-build-e8c26851-run1-pass-101-0-35` | stills in DCIM/Camera; the grid, timer and Living Images settings |
| E8 | `e8.sh` | e8c26851 | 28 passed, 0 failed, 9 recorded | `E8-build-e8c26851-run1-pass-28-0-9` | one video; RECORD_AUDIO revoked for the row |
| E9 | `e9.sh` | c7336aca | 135 passed, 0 failed, 73 recorded (run 3; run 2 the same totals) | `E9-build-c7336aca-run3-pass-135-0-73` | a still, a video, two MediaStore rows of the fixtures', one contact; RECORD_AUDIO revoked; `qa-capture` and `qa-capture-fwd` installed and removed |
| E15 | `e15.sh` | e8c26851 | 65 passed, 0 failed, 13 recorded | `E15-build-e8c26851-run1-pass-65-0-13` | CAMERA, READ_MEDIA_VIDEO, READ_MEDIA_IMAGES revoked and granted; `adb root` for the two kills, undone at once; two test notifications |
| E19_CAMERA | `e19_camera.sh` | e8c26851 | 112 passed, 0 failed, 22 recorded | `E19_CAMERA-build-e8c26851-run1-pass-112-0-22` | two stills; two 5-s screenrecords (pulled, removed) |
| E23_CAMERA | `e23_camera.sh` | e8c26851 | 35 passed, 0 failed, 7 recorded | `E23_CAMERA-build-e8c26851-run1-pass-35-0-7` | nothing (the bottom-row tile bursts, so no pin was needed) |
| EDGE_CAMERA | `edge_camera.sh` (nine sub-steps; `edge_index_camera.tsv`) | c7336aca | **150 passed, 5 failed, 50 recorded** — STORAGE 3, INUSE 1, CALLER 1 | `EDGE_CAMERA-build-c7336aca-run2-FAIL-camera-crash-STORAGE-CALLER-no-devices-line-INUSE-150-5-50` | takes and stills; RECORD_AUDIO and CAMERA revoked and granted; the volume filled and freed (`adb root`, undone); a call made and cancelled; a copy of the baseline with two slots re-pointed |

Earlier runs are kept beside these (`…-dev<k>-…`, `…-run<k>-FAIL-…`), each named with why it was superseded.

## What FAILS, and what it is

1. **`:camera` crashes whenever the activity shows a toast of its own** — product defect, not asserted around.
   `java.lang.IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext` at
   `ui/MotionClock.animate` ← `camera/Viewfinder.kt:134` (`ViewfinderState.say`). `CameraActivity` and
   `CaptureActivity` call `state.say(scope, …)` with their own `MainScope()`, which has no frame clock; the toasts the
   viewfinder itself raises (the timer's, from the composition's scope) are fine. Seen in:
   - **edge STORAGE** — `fill_volume 3000000`, the Camera opened in Video, `camera_record` tapped: the process dies at
     once (`STORAGE-crash.txt` in the run's folder), so no "storage full" is said, no `[camera] toast: Storage full`
     line is written and the ring is lost. Nothing is left pending (root's read: no `.pending-` file) and no row is
     made. The three failing assertions are the bullet's own words plus the crash check.
   - **edge CALLER** — a caller's video capture cancelled with Back mid-take, and the caller force-stopped mid-take:
     no orphan file, no pending row, RESULT_CANCELED — and `:camera` crashes after each (`CALLER-crash.txt`, two
     FATAL blocks). The failing assertion is the crash check.
   The crash buffer also holds the same trace at 12:56 and 13:24 on 2026-10-05, before these rows existed (the
   builder's sessions). No kept run of E7, E8, E9, E15, E19_CAMERA or E23_CAMERA has a crash in its window (E9 run 3
   asserts it from the crash buffer; for the five e8c26851 rows the buffer's six entries of the day — 12:56, 13:24,
   16:26:18, 16:26:37, 16:35:35, 17:28:27 — all fall outside their runs, 16:00–16:18).
2. **edge INUSE — no `[camera] devices=1` line after `[camera] busy`.** With Open Camera brought in front the ring
   holds `[camera] busy: camera in use by another app`; on return the viewfinder is back, `camera_busy` is gone and
   `dumpsys media.camera` names the shell as the client — but the bullet's second line is not written: on a resume
   `CameraEngine.start()` re-binds and sets Ready before the state listener's OPEN branch (the only writer of that
   line) can see Busy. The doc's clause is asserted as worded and fails; the screen behaviour passes.

## Recorded instead of asserted, and why

- **E9 R, the resolver.** The doc's word is `record`, with the shell's activity "expected among them". On this device
  `cmd package query-activities -a android.media.action.IMAGE_CAPTURE` (and VIDEO_CAPTURE) lists the preinstalled
  camera ONLY (`com.android.camera2/…`): the platform filters the shell user's implicit query as it does an app's.
  Run 1 asserted the expectation and failed (kept). Now the list and "among them: no" are recorded, and the route the
  fixture takes is asserted: the same query with `-p app.tileshell` resolves to `.camera.CaptureActivity`.
- **E9 N3 (leg (c), the owner's item).** With WRITE_CONTACTS granted to the caller, its own ClipData with the write
  flag on a contact's `display_photo` URI reads `callerMayWrite=true` and the request is **accepted** (the capture
  page opens). The row never presses Done, so the photo is unchanged; recorded, not graded.
- **E9 X plain (leg (e)).** A result forwarded by a go-between that does not share its identity is **accepted** with
  `callerMayWrite=true` (the platform names no starter); recorded. With `setShareIdentityEnabled(true)` it is refused
  with the forwarded line — asserted. The `capture guard inputs:` line as built carries no raw launched-from value,
  so none can be recorded from it (the two uids are recorded beside it).
- **E9 N2 / W / M, the sender's side.** Whether `startActivityForResult` threw on the sender (it did not, in any leg).
- **E15 G, Videos.** On the first request Android grants READ_MEDIA_VIDEO with NO dialog while READ_MEDIA_IMAGES is
  held (recorded). The leg then runs again with the photos-and-videos grants revoked for its span, where the real
  dialog comes and is asserted.
- **E19_CAMERA.** "No capsule node in panorama" (panorama is never on x86_64: P9 / P10). The video capsule's centres
  (two items on the AVD, the doc gives centres for four and three; order and the 24-epx inset are asserted). The
  screenrecords' black-frame counts (corroboration, never the clock). The accent colour itself (the current list item
  is asserted not to be the list's colour).
- **E7.** The dump's facing lines; `saved.wall − MARK` of the plain still; the HDR expectation's source (the device's
  `androidx.camera.extensions.impl` library list — the dump cannot say what CameraX Extensions report).
- **E23_CAMERA.** What the hold on the bottom-row tile showed: it bursts (2 satellites, the `[quick]` lines), so the
  Camera half ran on `tile:dock:slot:CAMERA` and the pinned-tile route in the driver was not taken.
- **Edge KILLWRITE.** The shell user's `content query … is_pending=1` shows no pending row even while one exists
  (root's `content query` does not either: the command cannot ask MediaStore to match pending rows). The state is
  asserted from the `.pending-` file on disk (read as root) and the URI in `:camera`'s pending ledger; the doc's own
  query is still run and reads 0.
- **Edge CALL.** The reason the take logged (`a call`).

## What the drivers found in the doc

- E9's resolver clause (above).
- The Edge-case bullet "Camera in use": its `[camera] devices=1` after `[camera] busy` (above).
- E7's "is_pending 0 within 2 s" is read 2 s after the tap; the Photos tile's refresh is measured from the row's
  `date_added` (whole seconds), so the 2-s bound is the stricter reading (329 ms measured).

## Added beside the drivers

- `scripts/cam17.sh`, `scripts/cam_facts.py`; `scripts/edge_index_camera.tsv`; `E18/producers_camera.tsv` (every
  pattern found in the kept runs' ring files) and `E18/notrun_camera.tsv`.
- Fixtures (QA tooling, never shipped): `testapps/qa-capture` gains the legs `own-media`, `grant-only`,
  `string-output`, `forward` and the `prefill` extra, and declares WRITE_CONTACTS (never granted at install);
  `testapps/qa-capture-fwd` is new — the second app of the forwarded-result leg — with its `include` line in
  `settings.gradle.kts`. Build both with `./gradlew :testapps:qa-capture:assembleDebug
  :testapps:qa-capture-fwd:assembleDebug --offline`.
