# Phase 17 — the Photos rows: E3, E4, E5, E6, E6b, E19_PHOTOS, E23_PHOTOS, the Photos edge sub-steps, TRUST_PHOTOS

Written on `phase-17-qa-photos` (cut from `phase-17` at 25921fd7; `phase-17` at e5e30678 — the trust fixes — merged in
on 2026-10-05). Every driver sources `lib.sh`, `p17.sh`, then `p17_photos.sh` (the Photos rows' own floor: the lock taken
once per process, the build asserted, the builder's working steps re-cut to the gate's form), installs this worktree's
debug APK when the device holds another build, and restores what it changes. Run one as
`env -u TMPDIR bash docs/plan/qa/phase-17/scripts/<driver>`; a busy lock is exit 3. A run's folder is
`docs/plan/qa/phase-17/<ROW>/`; every earlier run is kept beside it as `<ROW>-run<k>-<why>-<pass>-<fail>-<recorded>`.

**Two builds.** The gate build was the clean build of 25921fd7, md5 `e8c26851363882da`, until the trust fixes were merged;
since then it is the clean build of e5e30678, md5 `c7336aca6b63d61b`, which the drivers now assert. By the lead's rule
(2026-10-05) a row the fixes do not touch keeps its run on the first build; E4 and E5 (the viewer and its actions) were
run again on the second. The table says which build each row's evidence is from.

| Row | Driver | What it changes on the device (all restored and asserted) | A run takes | Recorded instead of asserted, and why |
|---|---|---|---|---|
| E3 | `e3.sh` | The Start layout (baseline), media (`media_down`), READ_MEDIA_IMAGES / _VIDEO / _VISUAL_USER_SELECTED. Force-stops the shell. | 2 min 50 s | The video disc's size (E19_PHOTOS asserts it). What is resumed after Back on Start. The permissions as found; the picker's activity, package and cells. |
| E4 | `e4.sh` | Media. | 1 min 20 s | "Expanding from its thumbnail": the form is Y6's approximation (H4) and the `[motion]` line carries no geometry. The gdump's `viewer_image` bounds while zoomed (the dump clips it; the `zoom … width=` line is asserted — Change Log (9)). Pinch (P5 / H9). The VIEW leg as the shell uid (the lead's order: recorded). |
| E5 | `e5.sh` | Media, `testapps/qa-capture` (installed, uninstalled), the shell's prefs (tar backup, verified before it is put back), `files/backgrounds`, the lock wallpaper, READ_MEDIA_IMAGES. | 3 min 45 s | The dialog's button texts; that a tap stops the slideshow (not a clause of the row). |
| E6 | `e6.sh` | Media (13 copies made and removed). | 10 min (14 editor sessions; the device is held throughout) | The red-eye tool's hint text; each tool's expected / got pixel beside its assertion. |
| E6b | `e6b.sh` | Media. | 1 min 25 s | The streams' codec names (the row asks for one video and one audio stream); the trim screen's status text. |
| E19_PHOTOS | `e19_photos.sh` | Media. | 2 min 25 s | LOW values, as the row sets them: the bar's and the strip's centres from the right (recorded: 218 / 150 / 82 / 24 and 286 / 218 / 150 / 82 / 24), the strip's fill, Set as → File information (159 px = 53 epx; Change Log (9)), the gap's width in the screenrecord (60 px = 20 epx; presence asserted). |
| E23_PHOTOS | `e23_photos.sh` | The Start layout (baseline). | 1 min 10 s | The two shortcuts' flag strings. |
| EDGE_* | `edge_photos.sh [<ID>…]` | Per sub-step: media; READ_MEDIA_IMAGES; the shell's prefs (FRAME_DELETED); root for one `rm` (FILE_GONE; off again at once, asserted); 3,000 files in a host temp folder and on the device, both removed in the sub-step (THOUSANDS). | 1–2 min each | See "The edge sub-steps". |
| TRUST_PHOTOS | `trust_photos.sh` | Media, `testapps/qa-photoview` (installed without -g, uninstalled). | 1 min 50 s | Legs (i) and (m) by the fix file's own words; (p) not run. |

## Run or not run (2026-10-05)

| Row | Build | Last run | Folder | Earlier runs (kept) |
|---|---|---|---|---|
| E3 | e8c26851 | 79 passed, 0 failed, 23 recorded — **with a reading the lead must rule on** (below) | `E3-run2-pass-with-the-VIDEO-reading-79-0-23` | `E3-run1-FAIL-doc-sequence-VIDEO-held-the-link-grants-in-full-no-picker-72-7-22`: the doc's sequence as written |
| E4 | c7336aca | 32 passed, 0 failed, 16 recorded | `E4-run3-pass-build-c7336aca-32-0-16` | run 1 (harness: a tag of ViewerActivity read on Photos' own viewer); `E4-run2-pass-32-0-17` on e8c26851 |
| E5 | c7336aca | 69 passed, 0 failed, 18 recorded | `E5-run3-pass-build-c7336aca-69-0-18` | run 1 (harness: the driver polled the ring while a step ran; that step read maxGapMs=50); `E5-run2-pass-build-e8c26851-69-0-18` |
| E6 | c7336aca | 190 passed, 0 failed, 20 recorded | `E6-run2-pass-190-0-20` | run 1 (harness: the crop drag landed one screen pixel off, 320 × 241) |
| E6b | c7336aca | 36 passed, 0 failed, 12 recorded | `E6b-run1-pass-36-0-12` | — |
| E19_PHOTOS | c7336aca | 96 passed, 0 failed, 19 recorded | `E19_PHOTOS-run3-pass-96-0-19` | runs 1 and 2 (harness: the screenrecord's frames were not decoded, then the gap matcher was too strict; everything else passed in both) |
| E23_PHOTOS | e8c26851 | 35 passed, 0 failed, 3 recorded | `E23_PHOTOS-run1-pass-35-0-3` | — |
| TRUST_PHOTOS | c7336aca | **41 passed, 6 failed**, 26 recorded — all six are leg (j) | `TRUST_PHOTOS-run3-FAIL-leg-j-an-app-holding-READ_MEDIA_IMAGES-is-refused-41-6-26` | runs 1 and 2 (harness: the sender's task was reused; a permission read through a closed pipe) |
| EDGE_FILE_GONE | c7336aca | 24 passed, 0 failed, 7 recorded | `EDGE_FILE_GONE-run2-pass-24-0-7` | run 1 (harness: `ls` through /sdcard still names the file) |
| EDGE_REVOKE_VIEWER | c7336aca | 21 passed, 0 failed, 6 recorded | `EDGE_REVOKE_VIEWER-run1-pass-21-0-6` | — |
| EDGE_KILL_PHOTOSEDIT | c7336aca | 18 passed, 0 failed, 7 recorded | `EDGE_KILL_PHOTOSEDIT-run1-pass-18-0-7` | — |
| EDGE_FRAME_DELETED | c7336aca | 20 passed, 0 failed, 4 recorded | `EDGE_FRAME_DELETED-run2-pass-20-0-4` | run 1 (harness: a MARK taken after the home app's own restart) |
| EDGE_HEIC_RAW | c7336aca | 30 passed, 0 failed, 11 recorded | `EDGE_HEIC_RAW-run1-pass-30-0-11` | — |
| EDGE_THOUSANDS | c7336aca | 21 passed, 0 failed, 7 recorded | `EDGE_THOUSANDS-run3-pass-21-0-7` | runs 1 and 2 (harness: the same MARK; then the census alone held fewer images than the tile shows, so "before" and "after" differed for a reason that is not the 3,000) |

No run of these rows was in flight when the host's disk filled (about 14:34): the first run of any of them began at 16:04.

## For the lead to rule

1. **E3, the partial leg — the doc's sequence cannot reach the picker on this device.** The row revokes READ_MEDIA_IMAGES
   only, grants READ_MEDIA_VISUAL_USER_SELECTED and expects the link to open the selected-photos picker. With
   READ_MEDIA_VIDEO still held (the baseline holds it), Android answers the link's permission request at once and in
   full — no dialog, no picker: `[photosapp] permission request: access=GRANTED videos=granted` (run 1, kept, 7 FAILs,
   all from this). The passing run revokes READ_MEDIA_VIDEO for that leg too — the state Android's own "Allow limited
   access" leaves — and says so in its log; then the link opens Android's dialog, its "Allow limited access" opens
   `PhotoPickerUserSelectActivity`, qa-photo-1 and qa-photo-2 are picked, `images=2`, exactly those two tiles.
   Also a reading: the device held READ_MEDIA_VISUAL_USER_SELECTED from the start (Android grants it with "Allow all"),
   so the denied leg revokes it too — the doc's next step grants it, so its sequence presupposes it is not held.
2. **E3, "the two `qa-steps` videos".** The row names no second file; the driver pushes `qa-steps.mp4` and `qa-steps.webm`.
3. **TRUST_PHOTOS leg (j) FAILS on c7336aca.** An app holding READ_MEDIA_IMAGES that does not share its identity is
   refused: `viewer request from an unnamed app: refused`, `refused view: no grant`, the error state — where the fix
   file expects "shown read-only". The same app WITH `setShareIdentityEnabled(true)` (leg (l)) is shown read-only as
   "another app". So `getInitialCaller().checkContentUriPermission` did not answer "granted" for a permission holder
   on API 36; the build writes no line saying whether it answered "denied" or threw, so the row cannot tell which.
   The failure is in the closed direction (nothing is shown that should not be).
4. **TRUST_PHOTOS leg (m) and E4's VIEW leg:** `adb shell am start` of ViewerActivity (the shell uid) is now refused as
   "an unnamed app" (the error state, no actions). On e8c26851 the same start showed the picture with Share, Edit,
   Delete and Set as (`E4-run2-pass-32-0-17`). No Photos row depends on it; any row that opens the viewer that way does.
5. **TRUST_PHOTOS leg (i):** with FLAG_GRANT_READ_URI_PERMISSION on a MediaStore URI it cannot read, the SENDER's own
   `startActivity` throws SecurityException; nothing reaches the viewer.
6. **TRUST_PHOTOS leg (p)** (the Probe page on a picked Living Image) is **not run**: no fixture of these rows is a
   Living Image.

## The edge sub-steps (the index is `edge_index_photos.tsv`)

- **THOUSANDS.** `am start -W` reports `LaunchState: WARM` and TotalTime 66–131 ms over the three runs: the shell is the home app, so its
  process is up again the moment it is stopped; a cold start of Photos cannot be made on this device without it.
  gfxinfo over the 20 swipes: 376 frames, 14 janky (3.72 %), 99th percentile 24 ms. The six fixtures are pushed first
  so that the tile's line reads its full count before the 3,000 arrive (`photos=8` before and after).
- **HEIC_RAW.** Made on the host (`make_stills.sh`; `gen/media/qa-stills.source`): `qa-still.heic` by ImageMagick's
  libheif delegate from qa-photo-0 (604 bytes, one HEVC still, no EXIF); `qa-still.dng` by hand — a baseline TIFF with
  the DNG tags of a linear DNG, **with no embedded preview** (ImageMagick's DNG coder is read-only and needs
  ufraw-batch, which is not installed). On the AVD the HEIC is decoded (221,40,39 on screen) and its edit is saved as
  `qa-still_edit_….jpg`, `image/jpeg`; the DNG is a placeholder tile and the viewer's error state
  (`viewer <id>: cannot be shown (IOException)`), no crash. **Not shown, mapped to P3:** a HEIC from the S25U, and a
  DNG showing its embedded preview.
- **FILE_GONE.** The file is removed under /data/media/0 as root. `ls` through /sdcard still names it afterwards, so
  "gone" is read by opening it (with a control on its neighbour).
- **REVOKE_VIEWER.** The pid changes (20931 → 21069); on return the page is the PARTIAL state's text (the device holds
  READ_MEDIA_VISUAL_USER_SELECTED), which names the Setup checklist. Either state's text is accepted and recorded.
- **KILL_PHOTOSEDIT.** The kill lands while a `.pending-…` file of the save exists in the folder (the precondition is
  asserted); the query for pending rows as the shell uid returned 0 at that moment — the file is the evidence. The next
  start writes `pending cleanup: 1` and nothing pending is left.
- **FRAME_DELETED.** The main photo is set through phase 01 item 4's route (the `photo_frame` key of
  `shared_prefs/start_theme.xml`), then the fixture is deleted in Photos' viewer.

Bullets that name PHOTOS but are not in this index: "PHOTOS or CAMERA re-pointed to another app" (E1's leg B and the
lead's edge index).

## E18

`E18/producers_photos.tsv`: every alternative of every `[photosapp]` line of E18 and the four `[motion]` lines these
rows time, each with its row. `E18/notrun_photos.tsv`: none — every alternative is produced on the AVD.

## What the drivers found (harness and doc)

- **`lib.sh` `take_device_lock` lets go of the lock when called twice** (it re-opens the file; `row_begin` calls it
  again). A script with several rows lost the device between two of them. `p17_photos.sh` takes it once per process.
- **`dumpsys` of the shell is answered on its main thread.** A driver that polls a ring while a timed animation runs
  costs it a frame (E5 run 1: maxGapMs=50). The Photos rows read the ring only after the motion.
- **The shell is the home app:** after `am force-stop` it is running again before the next command, so a MARK for "the
  start's own lines" is taken BEFORE the stop.
- **`status_bar` is not the drawn status bar's tag** (it is `w10m_status_bar`); the builder's development scripts
  checked the wrong name. E19_PHOTOS checks the right one, with the drawn nav bar's node as its control.
- **`grep -q` at the end of a pipe from `adb shell`, under `pipefail`,** reads as a failure when it matches early.

## Added beside the drivers

- `scripts/p17_photos.sh` (the floor above), `scripts/make_stills.sh` (the HEIC and the DNG, with their source record).
- `testapps/qa-photoview` and its line in `settings.gradle.kts`: the image VIEW sender of TRUST_PHOTOS (it declares
  READ_MEDIA_IMAGES and has its own provider). qa-capture cannot send a VIEW and holds no permission by design; the
  Movies & TV rows have a video sender, `testapps/qa-view`, in their own branch — this module first had that name and
  application id and was renamed so the two branches merge.
