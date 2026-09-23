---
phase: 18
slug: files
status: DRAFT   # split 2026-09-22; interview pending; R11 §Files gates FINAL
depends-on: [01, 02, 10, 17]
---

# Phase 18 — W10M File Explorer ("Files")

## Goal
Windows 10 Mobile's File Explorer lives inside the shell APK as an app in the app list: it browses the phone's
shared storage and any removable volume as "This Device" / "SD card" the way W10M did, sorts and searches,
selects, copies, moves, renames, deletes, shares and makes folders, and opens a file in the shell's own app
when the shell has one (Photos, the video player, Music) and in Android's handler otherwise. Its storage model
is ruled in Q1 and every row depends on it. Every visual value is from R11 §Files or a tagged approximation
with a NEEDS-HUMAN row. Nothing here uses the internet (A11).

## Scope
**In:**
- Roots: the primary shared storage (`/storage/emulated/0`, "This Device") and every mounted public volume
  (`StorageManager.getStorageVolumes()`: an SD card or a USB OTG drive as "SD card" / "USB"), appearing and
  disappearing with `ACTION_MEDIA_MOUNTED` / `ACTION_MEDIA_UNMOUNTED`; the pivots / entry points R11 §Files
  finds (candidate: This Device, SD card, Recent) — Q2 rules the functional set.
- Folder listing with W10M glyphs by type; sort by name / date / size / type; search within the current tree;
  multi-select; copy and move (progress, conflicts, cancel) that survive leaving the app (a foreground service,
  `FOREGROUND_SERVICE_DATA_SYNC`); rename; delete per Q3; new folder; share; properties (name, size, date,
  path); open-with routing per Q4; a "cannot read" entry for `/Android/data` and `/Android/obb` (Android 11+
  hides them from every app, All-files access included).
- The storage grant per Q1 (All-files access: `MANAGE_EXTERNAL_STORAGE`) with its Setup checklist row, the
  app's ungranted state naming the checklist, and the grant opened from the app
  (`android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION`, which resolves to
  `com.android.settings/.Settings$ManageExternalStorageActivity` on the AVD, 2026-09-22).
- MediaStore kept in step: every write the app makes is followed by a scan of the touched paths
  (`MediaScannerConnection.scanFile`) so the Photos tile, Photos and Music see moves and deletes at once.
- The ADD to phase 10 that lets Files hand an audio file to the shell's Music player by explicit component
  (Decisions); diagnostics lines; the exported allow-list ADD; the app-list regression; the APK budget.
**Out (explicitly):** replacing Android's file picker (DocumentsUI serves `ACTION_OPEN_DOCUMENT` /
`GET_CONTENT` for every app; CANNOT); being a `DocumentsProvider` root (the phone's storage is already one);
OneDrive or any cloud tab (A11, R10-Q4); an SD card on the S25U (it has no slot — removable-volume rows use a
USB OTG drive on the phone and a virtual disk on the AVD); other apps' `/Android/data` and `/Android/obb`
(Android limit; shown as unreadable, not hidden); root, `/data`, `/system`; the shell's own private files;
a recycle bin unless Q3 rules one; zip handling unless Q2 rules it; a "Files" tile (the default Start layout has
none; pinning is phase 02's).

## Decisions
- 2026-09-22 Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out
  thing at a glance thing"): "Files" is in, "each an app in the shell APK like Music" (PLAN.md, 2026-09-22
  scope add; W10M's File Explorer).
- 2026-09-22 R10-Q4 (Jeremy: "(a)"): A11 stands, so W10M File Explorer's OneDrive tab is out; This Device and
  the removable volume are what is left, which is what a phone with no account showed.
- 2026-09-22 (agent, R10 testability 16): **the permission model decides every row and is asked first (Q1).**
  The two candidates give different rows: All-files access (`MANAGE_EXTERNAL_STORAGE`, grantable to a
  sideloaded app under A9; on the AVD `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow`; on
  the phone through One UI's "All files access" page) reads and writes shared storage by path; SAF
  (`ACTION_OPEN_DOCUMENT_TREE`, DocumentsUI's consent per tree — `com.android.documentsui/.picker.PickActivity`
  on the AVD) puts Android's own screen in front of the user for every root. Verified 2026-09-22: the manifest
  has neither, `appops get app.tileshell MANAGE_EXTERNAL_STORAGE` = default. Whatever Q1 rules is the ONE
  permanent form (Rule 16); the rows below are written for Q1's lean and are re-cut if it goes the other way.
- 2026-09-22 (agent): **removable volumes are proved on the AVD with a virtual disk**, not skipped: `adb shell
  sm set-virtual-disk true` creates a disk (`sm list-disks`), `sm partition disk:<id> public` mounts it as a
  public volume (`sm list-volumes` shows `public:<x>,<y> mounted`), `sm unmount public:<x>,<y>` pulls it, and
  `sm set-virtual-disk false` removes it (RV12 restore). Today's AVD has only `emulated;0` and `private`
  mounted (`sm list-volumes`, 2026-09-22). Real USB OTG is a phone row.
- 2026-09-22 (agent): **the `/Android/data` negative is a row, not a footnote.** `adb shell ls
  /sdcard/Android/data` lists `net.osmand.plus`, `org.fossify.messages`, `org.fossify.notes` on the AVD for the
  shell uid; the app must show those entries as unreadable and say why (Android 11+), never crash, never hide
  them silently.
- 2026-09-22 (agent): **open-with routing prefers the shell's own apps** (P2: fewer seams), by explicit
  component: an image opens in phase 17's PhotosActivity viewer, a video in its VideoActivity, an audio file in
  phase 10's Music. Music's activity declares only MAIN + APP_MUSIC (AndroidManifest.xml, 2026-09-22), so
  "play this file" is an **ADD to phase 10's part**: MusicActivity accepts a play intent for one MediaStore
  audio id (played through the existing MusicService session; nothing about the queue or the tile rule
  changes), recorded in the INDEX Change Log when built. Everything else goes to Android's resolver
  (`com.android.intentresolver`) with `ACTION_VIEW` and the file's MIME type; a type nothing handles shows
  "No app on this phone opens this". Q4 asks whether this is the behaviour Jeremy wants.
- 2026-09-22 (agent): **writes are followed by a MediaStore scan** of every touched path, because with
  All-files access a rename or move by path does not update MediaStore by itself; E9 proves the Photos tile and
  Music see a move within 3 s.
- 2026-09-22 (agent): **long operations run in a foreground service** (`FOREGROUND_SERVICE_DATA_SYNC`, a
  notification with progress and cancel), so a 2 GB copy survives Home and a screen-off; the service dies with
  nothing half-written: a copy writes to a temporary name in the destination and renames on completion
  (LayoutStore's temp-and-rename shape).
- 2026-09-22 (agent): **R11 gates FINAL.** Every visual value is "from R11 §Files" (pending,
  docs/plan/r11-inbox-apps.md) or already measured: status bar 28 epx (R3 C4), list rows (R3 C2 / R6 §5.1.4),
  Settings-page rows for the app's settings (R3 C1), the Start exit (R3 A11), the app-list hold menu's band
  (phase 02 H21, approximation). Anything else is a Y row with an H row (RV9 / Q10); motion is planned as
  approximations from the start (R10 design 10).
- 2026-09-22 (agent): **the two kinds of NEEDS-HUMAN row are labelled** — *fidelity* (matches R11 §Files,
  judged on the phone) and *accept* (P4 design or approximation; no footage). qa/phase-18/NEEDS-HUMAN.md follows
  qa/phase-03/NEEDS-HUMAN.md's shape.
- 2026-09-22 (agent): **harness contracts** (R10 testability 24, 25): the activity root sets
  `Modifier.semantics { testTagsAsResourceId = true }` (as MusicActivity.kt), each row's name and detail carry
  their own tags (`files_row:<name>`, `files_detail:<name>`), every silent-empty state writes a diagnostics line
  under `[files]` (E12). Drivers symlink qa/phase-03/scripts/lib.sh; evidence under qa/phase-18/.
- 2026-09-22 (agent): **app list and process.** One launcher entry "Files" (LAUNCHER + APP_FILES, the category
  DocumentsUI declares on the AVD), excluded from Uninstall by `AppUninstall.canUninstall`'s self-package rule
  (E10 proves it); main process (a list over the filesystem, no decode); the copy service in the main process
  too. App-list regression (phase 02's regress.sh pattern) runs once for this phase.
- 2026-09-22 (agent): **bars.** A shell-owned screen under phase 01's bar rule: Samsung's bars hidden, the
  drawn W10M status and nav bars; Back walks up one folder and leaves the app at a root (approximation Y3 until
  R11 §Files says how W10M's Back behaved in File Explorer).
- 2026-09-22 (agent): **APK budget.** No new dependency; E10 checks the size against phase 03's ≤ 600 MB.

### Approximations (until R11 lands; each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Root page layout (This Device / SD card entries, glyphs, free-space line) | R11 §Files pending | two-line rows at the R3 C1 64-epx pitch with a 30-epx glyph | H4 |
| Y2 | Folder rows, type glyphs, the selection check, the bottom app bar's glyph set | R11 §Files pending | app-list rows (R3 C2) with a 48-epx app bar | H4 |
| Y3 | Back behaviour (up one folder vs leave) and the up-navigation motion | R11 §Files pending | Back goes up one level; leaves at a root; no page motion | H4 |
| Y4 | Progress, conflict and properties dialogs | no W10M capture expected | P4 design in phase 03's card idiom (R6 §3.4.2's field and button geometry) | H5 |
| Y5 | Motion (page push, selection mode enter) | UNMEASURED | 250 ms ease-out (X13's settle) | H3 |

## Interview queue (Stage A step 4)
Load-bearing first. Implementation mechanics are the agent's (P3).

1. **Q1 — the storage model.** It decides what the app can show and every acceptance row.
   A. All-files access (`MANAGE_EXTERNAL_STORAGE`): the whole shared storage and every removable volume,
   granted once through the Setup checklist, like W10M's This Device / SD card. (lean — A9: "I will grant
   anything and everything"; P2: no Android consent screen per folder)
   B. SAF only: each folder tree is opened through Android's picker (DocumentsUI's consent screen each time; a
   visible Android seam), no path access.
   C. Media folders only, with no extra permission: DCIM, Pictures, Movies, Music, Download and Documents
   through MediaStore — no arbitrary folders, no removable volume management.
   D. Other / let me clarify.
2. **Q2 — what File Explorer holds.** W10M's had browse, sort, select, copy / move / rename / delete, new
   folder, share and properties; later builds could open zips.
   A. Browse, sort, search, select, copy / move / rename / delete, new folder, share, properties. (lean)
   B. A plus zip: open a zip as a folder, extract, and create one from a selection.
   C. A plus zip plus a Recent view across the phone.
   D. Other / let me clarify.
3. **Q3 — delete.** W10M deleted for good after a confirmation; Android since 11 has a 30-day trash for media
   files (a system consent dialog per batch; not for other file types).
   A. Permanent after a confirmation, every file type alike (W10M). (lean)
   B. Media files go to Android's trash (recoverable for 30 days, restore offered in Files), other files are
   deleted permanently — a "continued development" P4 addition.
   C. A shell-owned recycle-bin folder for every file type, emptied by the user.
   D. Other / let me clarify.
4. **Q4 — what opens a file.**
   A. The shell's own app when it has one (Photos for images, the video player, Music for audio), Android's
   chooser for everything else. (lean — P2)
   B. Always Android's chooser, even for images / videos / audio.
   C. A, plus an "open with" remembered per type inside Files.
   D. Other / let me clarify.

## Build tasks
1. **App identity and grant.** `FilesActivity` (LAUNCHER + APP_FILES) in the shell APK with
   `testTagsAsResourceId`; `MANAGE_EXTERNAL_STORAGE` in the manifest (Q1 A); Setup checklist row "Files"
   (`Environment.isExternalStorageManager()`), whose tap opens `MANAGE_ALL_FILES_ACCESS_PERMISSION`; the
   ungranted state in the app names the checklist and offers the same link; the exported allow-list ADD.
2. **Roots and listing.** Volumes from `StorageManager`, mount / unmount broadcasts; folder listing by path
   with type glyphs, sort (name / date / size / type), search within the tree (a background walk with a
   cancellable progress line); the unreadable `/Android/data` and `/Android/obb` entries; paging so a folder of
   10,000 entries lists without stalling.
3. **Selection and operations.** Multi-select; copy / move through the foreground service with progress,
   conflict resolution (replace / keep both / skip), cancel with no partial file; rename; delete per Q3; new
   folder; share (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` with content URIs from `FileProvider`); properties;
   every write followed by a MediaStore scan.
4. **Open-with routing** per Q4, including the phase 10 ADD (a play intent on MusicActivity) and phase 17's
   explicit components.
5. **Diagnostics and states.** `[files]` lines for every silent-empty state; error states for a vanished
   folder, a pulled volume, a full volume, a denied grant.
6. **Regressions.** App-list regression, exported allow-list, APK size, and the phase 17 / phase 10 hand-off
   rows.
7. **R11 values applied.** Once R11 §Files lands, the Y rows it measures are replaced and E11 is written; FINAL
   only then (RV9).

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps
follow RV13. Harness: qa/phase-18/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh. Device: the AOSP AVD
tileshell_fhd (1080×2340 @ 450 dpi, API 36, no Google). Written for Q1 A; re-cut if Q1 rules otherwise.

**Fixtures.** `adb shell mkdir -p /sdcard/QA-Files/sub` and files with known names, sizes and dates: `adb shell
"dd if=/dev/zero of=/sdcard/QA-Files/b.bin bs=1024 count=300"`, `touch -d "2026-01-01 00:00"
/sdcard/QA-Files/a.txt` (toybox touch), the images from docs/plan/qa/phase-01/scripts/make_photos.py, the
`qa-steps.mp4` from phase 17, one MP3 from qa/phase-01/MUSIC6's fixtures; md5s recorded with `adb shell
md5sum`. A big file for progress: `adb shell "dd if=/dev/zero of=/sdcard/QA-Files/big.bin bs=1m count=200"`.

**Emulator:**
- E1 **Grant and checklist.** `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow` → the
  checklist row "Files" is green (dump) and the app lists This Device; `appops set … default` → the row is red,
  the app shows "Files can't see this phone's storage" with a link (dump text), the link starts
  `Settings$ManageExternalStorageActivity` (`dumpsys activity activities`), diagnostics `[files] access=denied`.
- E2 **Removable volume.** With the grant: the root page shows `files_root:emulated` only (`sm list-volumes`
  = `emulated;0 mounted`); `adb shell sm set-virtual-disk true`, `sm list-disks` → `disk:<id>`, `sm partition
  disk:<id> public` → `sm list-volumes` shows a `public:` volume mounted and within 3 s the app shows a second
  root (`files_root:public`) whose name is the volume's; browse it, create a folder on it (`adb shell ls
  /storage/<UUID>` shows it); `sm unmount public:<x>,<y>` → the root disappears and, if it was open, the page
  shows "This storage was removed" (dump text), no crash (`logcat -d -s AndroidRuntime` empty of
  `app.tileshell`); restore `sm set-virtual-disk false`.
- E3 **Listing and sort.** The QA-Files folder lists `a.txt`, `b.bin`, `sub`, the images, the video and the
  MP3 with type glyphs (dump `files_row:` order); sort by name / date / size / type each gives the order the
  fixtures' names, `touch -d` dates and `dd` sizes dictate (four dumps, exact order asserted).
- E4 **Operations.** Copy `b.bin` into `sub` → `adb shell md5sum` equal on both; move `a.txt` into `sub` →
  gone from the parent, present in `sub`, md5 unchanged; rename `b.bin` → `c.bin` (`ls`); new folder `n` →
  `ls -d /sdcard/QA-Files/n`; delete `c.bin` after the confirmation (Q3 A) → `ls` fails; a conflict (copy
  `sub/b.bin` back over `b.bin`) offers replace / keep both / skip and each does what it says (`ls`, md5);
  copy `big.bin` → the progress notification is in `dumpsys notification`, `input keyevent KEYCODE_HOME`
  mid-copy leaves the service running (`dumpsys activity services app.tileshell` shows it), the copy completes
  with an equal md5; cancel mid-copy → no file and no `*.part` left in the destination (`ls`).
- E5 **Negatives.** `/sdcard/Android/data` lists the fixture packages' folders as unreadable entries (dump text
  "Android doesn't let apps see this folder"), tapping one shows the reason, no crash; `/sdcard/Android/obb` the
  same; the shell's own `/sdcard/Android/data/app.tileshell` is readable.
- E6 **Open with (Q4 A).** Tap an image → `dumpsys activity activities` topResumedActivity =
  `app.tileshell/.photos.PhotosActivity` (phase 17) showing that image (phase 17 E4's pixel rule); a video →
  `app.tileshell/.video.VideoActivity` playing it (`dumpsys media_session`); the MP3 → MusicActivity with
  `dumpsys media_session` showing the shell's music session PLAYING that track (the phase 10 ADD); `a.txt` →
  the system chooser (`com.android.intentresolver`) with `text/plain`; a `.xyz` file → "No app on this phone
  opens this" (dump text), diagnostics `[files] no handler for application/octet-stream`.
- E7 **Share.** Select two files → share → the chooser resumed with `ACTION_SEND_MULTIPLE` and two
  `content://` URIs from the shell's FileProvider (`dumpsys activity activities` intent line).
- E8 **Search.** "b" from the QA-Files root lists `b.bin` and `sub/b.bin` with their paths; a term with no match
  shows the empty line; searching while the walk runs shows the progress line and cancel stops it (`[files]
  search cancelled`).
- E9 **MediaStore in step.** Move `qa-photo-0.png` from `DCIM/Camera` to `Pictures/QA-Album` in Files → `adb
  shell content query --uri content://media/external/images/media --projection _display_name:relative_path`
  shows the new path within 3 s and the Photos tile's `[photos] refresh (mediastore change)` line follows;
  rename the MP3 → Music's library shows the new title source (`content query` on the audio collection) and
  phase 10's `[music]` observer line fires.
- E10 **App list, surface, budget.** "Files" under F with Pin to Start and NO Uninstall (qa/phase-01/UNINSTALL's
  method), no "New" caption; phase 02's regress.sh pattern passes; `dumpsys package app.tileshell` exported
  components equal qa/phase-03/exported-allowlist.txt plus this phase's ADD; `stat -c%s
  app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes.
- E11 **Geometry and motion against R11 §Files** — written once R11 lands; not runnable before, and the doc
  cannot go FINAL without it.
- E12 **Diagnostics.** Lines asserted by the rows above: `[files] access=<granted|denied>`, `[files] roots:
  <n> (<names>)`, `[files] volume mounted|unmounted <name>`, `[files] list <path>: <n> entries <ms> ms`,
  `[files] unreadable <path>`, `[files] copy|move <n> files <bytes> -> <dest> done|cancelled|failed <reason>`,
  `[files] no handler for <mime>`, `[files] search cancelled`.

**Phone-only:**
- P1 One UI's "All files access" page from the checklist row (`am start -a
  android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION -d package:app.tileshell`, resumed activity recorded) and
  the grant surviving a reboot and a Device care optimise.
- P2 A USB OTG drive: appears as a root, browse / copy / pull mid-copy (E2's assertions on real hardware);
  exFAT and FAT32 name rules (case-insensitive rename).
- P3 Samsung Camera's DCIM at thousands of items and the S25U's Download folder: list time and scroll per
  phase 01 P4's gfxinfo method.
- P4 Secure Folder and Private Space contents are absent (their storage is another user's), and the app says
  nothing about them rather than an error.
- P5 One UI's "Open with" sheet for `ACTION_VIEW` from Files (which apps it lists for a `.txt`, a `.pdf`).

**NEEDS-HUMAN:** H1 *fidelity* — Files matches R11 §Files on the phone; H2 *accept* — the delete rule (Q3)
and its confirmation wording; H3 *accept* — motion approximations (Y5); H4 *accept* — any Y1–Y3 value R11
does not measure; H5 *accept* — the progress / conflict / properties dialogs (Y4, P4 design); H6 *accept* —
the unreadable-folder wording for `/Android/data`.

## Edge cases
- 10,000 files in one folder: `adb shell 'cd /sdcard/QA-Big && for i in $(seq 1 10000); do : > f$i; done'`;
  the listing's first page draws in < 2 s (`[files] list … <ms> ms`) and sorting by size completes.
- Names: unicode, emoji, a 255-byte name, leading dots (hidden files shown or not per a Files setting, default
  off), a name that differs only by case on a FAT volume (E2's public volume is FAT: rename `a.txt` → `A.TXT`
  succeeds and lists once).
- A folder deleted underneath an open listing (`adb shell rm -r`) → the page shows "This folder is gone" and
  Back goes up; a file deleted between list and tap → the tap shows the error line.
- Copy onto a full volume (`fallocate` fill as phase 17's edge case) → the copy fails with "not enough space",
  the partial temp file is removed, the source untouched; restore by deleting the fill.
- Volume unmounted mid-copy (E2's `sm unmount`) → the copy fails with "storage removed", no partial file on
  the remaining side.
- Grant revoked mid-session (`appops set … default` with the app open): no process restart for an appop
  change, so the app re-checks on every resume and on each operation and shows the grant state instead of an
  IOException.
- Move across volumes = copy then delete; a failure after the copy leaves both (never neither).
- `.nomedia` folders: Files lists them; Photos and Music do not show their contents (MediaStore's rule) — both
  asserted.
- Symlinks and special files are not present on shared storage (FUSE); a path that resolves outside the
  volume is refused.
- A file opened in Photos, then deleted in Files: Photos' stale row shows a placeholder until the scan lands
  (phase 17's edge case).
- Screen off, an incoming call (`adb emu gsm call 5551234`) and a reboot during a copy: the service finishes
  or fails cleanly; after a reboot no temp files remain (`find /sdcard -name '*.part'` empty).
- The app list's hold menu on Files (Pin to Start present; Uninstall absent; E10) and Files pinned to Start
  (phase 02) opening the root page.
- Liveness (N-01): reboot, Device care and a force-stop leave the grant and the checklist row intact.

## QA evidence
_None yet._
