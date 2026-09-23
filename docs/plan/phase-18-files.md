---
phase: 18
slug: files
status: DRAFT   # split 2026-09-22; interview DONE 2026-09-23; review triage round 1 applied 2026-09-23 (review/2026-09-23-phases11-19-triage.md); r11/files.md gates FINAL (pending on disk 2026-09-23 — docs/plan/r11-inbox-apps.md is the index)
depends-on: [01, 02, 10, 17]
---

# Phase 18 — W10M File Explorer ("Files")

## Goal
Windows 10 Mobile's File Explorer lives inside the shell APK as an app in the app list: it browses the phone's
shared storage and any removable volume as "This Device" / "SD card" the way W10M did, sorts and searches,
selects, copies, moves, renames, deletes into a shell-owned Recycle Bin, shares and makes folders, opens and makes
zips, shows the phone's recently changed files (Recent), and opens a file in the shell's own app when the shell has one
(Photos, Movies & TV's player, Music) and in Android's chooser otherwise. It reads and writes by path under All-files
access (`MANAGE_EXTERNAL_STORAGE`, Q1 A). Every visual value is from r11/files.md or a tagged approximation with a
NEEDS-HUMAN row. Nothing here reaches the internet — out: offline preferred (A11 as amended 2026-09-23); Jeremy can ask.

## Scope
**In:**
- Roots: the primary shared storage (`/storage/emulated/0`, "This Device") and every mounted public volume
  (`StorageManager.getStorageVolumes()`: an SD card or a USB OTG drive as "SD card" / "USB"), appearing and
  disappearing with `ACTION_MEDIA_MOUNTED` / `ACTION_MEDIA_UNMOUNTED`; beside them the Recent view and the Recycle Bin
  (Q2 C, Q3 C — the functional set is ruled; r11/files.md settles how W10M drew its root page).
- Folder listing with W10M glyphs by type; sort by name / date / size / type; search within the current tree;
  multi-select; copy and move (progress, conflicts, cancel) that survive leaving the app (a foreground service,
  `FOREGROUND_SERVICE_DATA_SYNC`); rename; delete into the Recycle Bin (Q3 C; Decisions); new folder; share; properties
  (name, size, date, path); open-with routing (Q4 A); a "cannot read" entry for `/Android/data` and `/Android/obb`
  (Android 11+ hides them from every app, All-files access included).
- Zip (Q2 C): open a zip as a folder, extract (through the same foreground service), create one from a selection —
  `java.util.zip`, no new dependency (Decisions).
- Recent (Q2 C): recently changed files across the phone from MediaStore's modified dates (Decisions).
- The Recycle Bin (Q3 C): one bin per volume, its own entry on the root page, Restore / Delete permanently / Empty
  (Decisions).
- The storage grant (Q1 A: All-files access, `MANAGE_EXTERNAL_STORAGE`) with its Setup checklist row and its setup-wizard
  step (phase 12 Q1 / Q2), the app's ungranted state naming the checklist, and the grant opened from the app
  (`android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION`, which resolves to
  `com.android.settings/.Settings$ManageExternalStorageActivity` on the AVD, 2026-09-22).
- MediaStore kept in step: every write the app makes is followed by a scan of the touched paths
  (`MediaScannerConnection.scanFile`) so the Photos tile, Photos and Music see moves and deletes at once — belt-and-braces,
  since MediaProvider already follows renames and deletes made by path on Android 11+ (Decisions, T18-4).
- The ADD to phase 10 that lets Files hand an audio file to the shell's Music player by explicit component
  (Decisions); a `FileProvider` for Share (a manifest ADD, not exported); static App Shortcuts plus the dynamic "SD card"
  one (phase 11 Q1's standing rule as amended by C-9); diagnostics lines; the exported allow-list ADD; the app-list
  regression; the APK budget.
**Out (explicitly):** replacing Android's file picker (DocumentsUI serves `ACTION_OPEN_DOCUMENT` /
`GET_CONTENT` for every app; CANNOT); being a `DocumentsProvider` root (the phone's storage is already one);
OneDrive or any cloud tab (out: offline preferred (A11 as amended 2026-09-23), and PLAN's feature list keeps Microsoft's
cloud apps out; Jeremy can ask); an SD card on the S25U (it has no slot — removable-volume rows use a
USB OTG drive on the phone and a virtual disk on the AVD); other apps' `/Android/data` and `/Android/obb`
(Android limit; shown as unreadable, not hidden); root, `/data`, `/system`; the shell's own private files;
Android's own 30-day media trash (`IS_TRASHED`) as the bin (Decisions); password-protected zips (shown as unsupported); a
"Files" tile (the default Start layout has none; pinning is phase 02's).
~~a recycle bin unless Q3 rules one; zip handling unless Q2 rules it~~ SUPERSEDED 2026-09-23 by Q3 C / Q2 C (T18-7).

## Decisions
- 2026-09-23: Interview Q4 — the shell's own app opens its types (Jeremy: "(a)"): images in Photos, video in the shared player,
  audio in Music (the play-one-track intent is an ADD to phase 10, INDEX Change Log when built); every other type goes to
  Android's chooser.
- 2026-09-23: The App Shortcuts under the phase 11 Q1 standing rule (agent; Jeremy can overrule): Files — This device, Recent,
  Recycle Bin, and SD card while one is inserted. [2026-09-23, C-9 / T18-6: "SD card" is a DYNAMIC shortcut — the agent line
  at the end of Decisions.]
- 2026-09-23: Interview Q3 — a shell-owned Recycle Bin for every file type (Jeremy: "(c)"), emptied only by the user. Agent
  mechanics: one bin folder per storage volume (so a delete is a rename on the same volume, instant, never a copy), with the
  original path recorded so Restore puts the file back where it was (a clash asks before overwriting); the bin shows in Files
  with Restore, Delete permanently and Empty; nothing is ever deleted from it automatically. A file deleted by another app never
  passes through it (the bin covers deletes made in Files).
- 2026-09-23: Interview Q2 — the full set plus zip plus Recent (Jeremy: "(c)"): browse, sort, search, select, copy / move /
  rename / delete, new folder, share, properties; zip (open a zip as a folder, extract, create one from a selection); and a
  Recent view of recently changed files across the phone (read from MediaStore's modified dates, since Android keeps no
  system-wide "recently opened" list for other apps — agent note).
- 2026-09-23: Interview Q1 — all-files access (Jeremy: "(a)"): MANAGE_EXTERNAL_STORAGE, granted once, reaching all shared
  storage and every removable volume. The grant is a Setup checklist row AND a setup-wizard step with its "why" line (phase 12
  Q1 / Q2: every later phase adds its grant to the wizard).
- 2026-09-22: From phase 11 interview Q1 (Jeremy: "A"), a standing rule for every shell app: this phase's apps declare their
  own top-level screens as static App Shortcuts, so a hold on their tiles bursts those screens (phase 11). Which screens each app
  declares is settled at this phase's own interview; a build task and an acceptance row carry it.
- 2026-09-22 Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out
  thing at a glance thing"): "Files" is in, "each an app in the shell APK like Music" (PLAN.md, 2026-09-22
  scope add; W10M's File Explorer).
- 2026-09-22 R10-Q4 (Jeremy: "(a)"): A11 stands, so W10M File Explorer's OneDrive tab is out; This Device and
  the removable volume are what is left, which is what a phone with no account showed.
  **Reason partly SUPERSEDED 2026-09-23 by the A11 amendment (PLAN.md; triage C-7):** A11 no longer bars the internet, so the
  OneDrive tab stays out as "offline preferred" and under PLAN's feature list ("a Store and Microsoft's cloud apps" are out),
  not under A11; Jeremy can ask. R10-Q4's no-Mail / no-browser / no-Maps ruling is untouched.
- 2026-09-22 (agent, R10 testability 16): **the permission model decides every row and is asked first (Q1).**
  The two candidates give different rows: All-files access (`MANAGE_EXTERNAL_STORAGE`, grantable to a
  sideloaded app under A9; on the AVD `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow`; on
  the phone through One UI's "All files access" page) reads and writes shared storage by path; SAF
  (`ACTION_OPEN_DOCUMENT_TREE`, DocumentsUI's consent per tree — `com.android.documentsui/.picker.PickActivity`
  on the AVD) puts Android's own screen in front of the user for every root. Verified 2026-09-22: the manifest
  has neither, `appops get app.tileshell MANAGE_EXTERNAL_STORAGE` = default. Whatever Q1 rules is the ONE
  permanent form (Rule 16). ~~the rows below are written for Q1's lean and are re-cut if it goes the other way.~~
  SUPERSEDED 2026-09-23: Q1 ruled A (above); the rows are the All-files form and no re-cut is pending (T18-7).
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
  "No app on this phone opens this". ~~Q4 asks whether this is the behaviour Jeremy wants.~~ Q4 ruled A 2026-09-23 (above).
- 2026-09-22 (agent): **writes are followed by a MediaStore scan** of every touched path, ~~because with
  All-files access a rename or move by path does not update MediaStore by itself~~ (SUPERSEDED 2026-09-23 by T18-4:
  shared storage is FUSE-backed on Android 11+ and MediaProvider follows renames and deletes of the files it indexes, so the
  scan is belt-and-braces; E9 records which case needed it); E9 proves the Photos tile and Music see a move within 3 s.
- 2026-09-22 (agent): **long operations run in a foreground service** (`FOREGROUND_SERVICE_DATA_SYNC`, a
  notification with progress and cancel), so a 2 GB copy survives Home and a screen-off; the service dies with
  nothing half-written: a copy writes to a temporary name in the destination and renames on completion
  (LayoutStore's temp-and-rename shape).
- 2026-09-22 (agent): **R11 gates FINAL.** Every visual value is "from R11 §Files" (pending — R11 is now an index,
  docs/plan/r11-inbox-apps.md; this phase's section is `docs/plan/r11/files.md`, not written as of 2026-09-23 — C-12) or
  already measured: status bar 28 epx (R3 C4), list rows (R3 C2 / R6 §5.1.4),
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
- 2026-09-22 (agent): **APK budget.** No new dependency (zip is the platform's `java.util.zip`; the `FileProvider` is
  androidx.core's, already in the build); E10 checks the size against phase 03's ≤ 600 MB.
- 2026-09-23 (agent, review triage T18-1): **the Recycle Bin, designed below the Q3 ruling.** One bin per volume at
  `<volume root>/.Tessera/bin/` holding a `.nomedia` marker and `.index.json` (one record per binned file: bin name, original
  path, deleted-at, size; written temp-and-rename, LayoutStore's shape). The bin is its OWN entry on the root page ("Recycle
  Bin": every volume's bin in one list, each row naming its volume), never a browsable dot-folder — dot-files are hidden by
  default (edge cases), and a bin reached by browsing would invite edits that bypass the index. **Delete** = a rename inside
  the same volume to `<bin>/<deleted-at ms>-<name>` (instant, never a copy, works on a full volume), the index write, then a
  scan of the source path, so MediaStore drops the row and Photos, Music and the Photos tile lose the file. Android's
  `IS_TRASHED` is NOT used: it covers media only and auto-purges at 30 days, against "emptied only by the user". **Restore** =
  rename back, the original folder recreated if it is gone; a clash opens Files' existing conflict dialog (replace / keep
  both / skip). **Delete permanently** (one or a selection) and **Empty** confirm first (H2 has the wording); a delete made
  INSIDE the bin is permanent. **Damage:** a lost or unreadable index → the bin still lists its files by bin name and Restore
  puts them in `<volume>/Download/Restored/`; an index record whose file is gone is dropped on the next read; a bin folder
  deleted by another app → recreated empty on the next delete, with its line. A removable volume that is pulled takes its bin
  with it (its rows vanish with the root). Phase 19's Storage page shows the bin's size per volume with Empty (a one-line ADD
  there, phase 19's table). Tags `files_bin`, `files_bin_row:<name>`, `files_bin_restore`, `files_bin_delete`,
  `files_bin_empty`; diagnostics `[files] bin <delete|restore|purge|empty> <path>: ok | failed <why>`, `[files] bin index
  <volume>: <n> entries | rebuilt (<why>)`. Reason: Jeremy's own mechanics (same-volume rename, original path kept, nothing
  automatic) leave only these choices open, and each picks the form that cannot lose a file. User-visible, so H2 [accept].
- 2026-09-23 (agent, review triage T18-2): **zip and Recent, designed below the Q2 ruling.** Zip is `java.util.zip`
  (`ZipFile`; the platform reads zip64) — no new dependency. **Open as folder:** a zip is a virtual root listed with Files'
  own rows (sizes from the central directory); a nested zip inside it is a file that opens the same way. **Extract** runs
  through the copy service (progress, cancel) into a temp folder beside the zip, renamed to its final name on completion, so
  a cancel or failure leaves no partial output. **Create** from a selection (DEFLATE, UTF-8 names, the same service).
  **Security:** every entry name is normalised and refused when its canonical path escapes the extract root (`../`, an
  absolute path) — `[files] zip: refused entry <name>` — while the other entries extract; the declared uncompressed total
  is summed before extracting and refused above the volume's free space − 50 MB; extraction stops, deletes its temp folder
  and fails cleanly once the bytes written pass the declared total + 1 MB (a zip bomb that lies about its sizes); an
  encrypted entry → "This zip is password-protected" (unsupported, stated); names honour the UTF-8 flag with a CP437
  fallback; a removable volume pulled mid-extract → the service fails with "storage removed". **Recent** = MediaStore's
  `Files` collection ordered by `DATE_MODIFIED` descending, capped at 100, excluding `.nomedia` folders and the bins; a Files
  write's scan makes the file appear within 3 s; a file no scan has reached is absent — a rule, stated, not a gap. Tags
  `files_zip_root`, `files_extract`, `files_zip_create`, `files_recent_row:<name>`; diagnostics in E12. Reason: the ruling
  names the features; these picks are the platform-native form and the minimum that makes extraction safe.
- 2026-09-23 (agent, review triage T18-6 / C-9): **"SD card" is a DYNAMIC App Shortcut** (`ShortcutManager
  .setDynamicShortcuts`), published on mount and removed on unmount; This device, Recent and Recycle Bin are static (ranks
  0–2), SD card ranks 3. Reason: a shortcut to a volume that is not there must not burst; a manifest shortcut cannot be
  withdrawn at run time (`ShortcutManager.disableShortcuts` throws for immutable shortcuts), and phase 11's selection rule puts
  manifest before dynamic, so a conditional screen ranks after the fixed ones by a stated choice. E2 proves both directions.
- 2026-09-23 (review triage T18-3 / C-4, a doc update): **the grant joins the setup wizard.** The Files checklist row is a
  wizard step of phase 12's Settings-page kind (`wizard_step:setup:files`; action `MANAGE_ALL_FILES_ACCESS_PERMISSION` with
  `package:app.tileshell`; why line "Files can browse everything on this phone. Without it Files sees nothing.");
  `qa/phase-03/scripts/provision.sh` gains `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow`; E15 is phase 12
  E14's template for it; phase 12 E1 re-runs on this build. Phase 12's rule, restated: a finished or skipped wizard is never
  re-summoned on that install — this grant goes red on the Setup checklist instead.

### Approximations (until R11 lands; each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | Root page layout (This Device / SD card / Recent / Recycle Bin entries, glyphs, free-space line) | r11/files.md pending | two-line rows at the R3 C1 64-epx pitch with a 30-epx glyph | H4 |
| Y2 | Folder rows, type glyphs, the selection check, the bottom app bar's glyph set (also used by a zip's virtual root and Recent) | r11/files.md pending | app-list rows (R3 C2) with a 48-epx app bar | H4 |
| Y3 | Back behaviour (up one folder vs leave) and the up-navigation motion | r11/files.md pending | Back goes up one level; leaves at a root; no page motion | H4 |
| Y4 | Progress, conflict and properties dialogs | no W10M capture expected | P4 design in phase 03's card idiom (R6 §3.4.2's field and button geometry) | H5 |
| Y5 | Motion (page push, selection mode enter) | UNMEASURED; timed by the `[motion]` clock (C-5) | 250 ms ease-out (X13's settle) | H3 |
| Y6 | The Recycle Bin page (rows with volume and deleted-at, Restore / Delete permanently / Empty) and its confirmations | no W10M original (P4) | folder rows (Y2) with the date as the detail line; confirmations in Y4's card idiom | H2 |

## Interview queue (Stage A step 4)
Load-bearing first. Implementation mechanics are the agent's (P3).

1. ~~Q1 — storage model~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q1 — the storage model.** It decides what the app can show and every acceptance row.
   A. All-files access (`MANAGE_EXTERNAL_STORAGE`): the whole shared storage and every removable volume,
   granted once through the Setup checklist, like W10M's This Device / SD card. (lean — A9: "I will grant
   anything and everything"; P2: no Android consent screen per folder)
   B. SAF only: each folder tree is opened through Android's picker (DocumentsUI's consent screen each time; a
   visible Android seam), no path access.
   C. Media folders only, with no extra permission: DCIM, Pictures, Movies, Music, Download and Documents
   through MediaStore — no arbitrary folders, no removable volume management.
   D. Other / let me clarify.
2. ~~Q2 — feature set~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q2 — what File Explorer holds.** W10M's had browse, sort, select, copy / move / rename / delete, new
   folder, share and properties; later builds could open zips.
   A. Browse, sort, search, select, copy / move / rename / delete, new folder, share, properties. (lean)
   B. A plus zip: open a zip as a folder, extract, and create one from a selection.
   C. A plus zip plus a Recent view across the phone.
   D. Other / let me clarify.
3. ~~Q3 — delete~~ RULED 2026-09-23: C (see Decisions). Original question kept below.
   **Q3 — delete.** W10M deleted for good after a confirmation; Android since 11 has a 30-day trash for media
   files (a system consent dialog per batch; not for other file types).
   A. Permanent after a confirmation, every file type alike (W10M). (lean)
   B. Media files go to Android's trash (recoverable for 30 days, restore offered in Files), other files are
   deleted permanently — a "continued development" P4 addition.
   C. A shell-owned recycle-bin folder for every file type, emptied by the user.
   D. Other / let me clarify.
4. ~~Q4 — what opens a file~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Q4 — what opens a file.**
   A. The shell's own app when it has one (Photos for images, the video player, Music for audio), Android's
   chooser for everything else. (lean — P2)
   B. Always Android's chooser, even for images / videos / audio.
   C. A, plus an "open with" remembered per type inside Files.
   D. Other / let me clarify.

## Build tasks
1. **App identity and grant.** `FilesActivity` (LAUNCHER + APP_FILES) in the shell APK with
   `testTagsAsResourceId`; `MANAGE_EXTERNAL_STORAGE` in the manifest (Q1, ruled A); Setup checklist row "Files"
   (`Environment.isExternalStorageManager()`), whose tap opens `MANAGE_ALL_FILES_ACCESS_PERMISSION`; the same row as a
   setup-wizard step of phase 12's Settings-page kind (`wizard_step:setup:files`, its `wizard_why` line in Decisions —
   T18-3); the ungranted state in the app names the checklist and offers the same link; the exported allow-list ADD;
   `qa/phase-03/scripts/provision.sh` gains `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow` (C-4 a).
2. **Roots and listing.** Volumes from `StorageManager`, mount / unmount broadcasts; the Recent and Recycle Bin entries on
   the root page; folder listing by path with type glyphs, sort (name / date / size / type), search within the tree (a
   background walk with a cancellable progress line); the unreadable `/Android/data` and `/Android/obb` entries; paging so a
   folder of 10,000 entries lists without stalling.
3. **Selection and operations.** Multi-select; copy / move through the foreground service with progress,
   conflict resolution (replace / keep both / skip), cancel with no partial file; rename; delete into the Recycle Bin
   (task 8); new folder; share (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` with content URIs from a `FileProvider` — a
   manifest ADD, none exists today: the manifest's providers are `LiveTileProvider` (`app/src/main/AndroidManifest.xml:162`)
   and `KeyboardConfigProvider` (`:261`); it is `exported="false"` with `grantUriPermissions="true"`, so it adds nothing to
   the exported-components allow-list — T18-5); properties; every write followed by a MediaStore scan.
4. **Open-with routing** (Q4 A), including the phase 10 ADD (a play intent on MusicActivity) and phase 17's
   explicit components.
5. **Diagnostics and states.** `[files]` lines for every silent-empty state; error states for a vanished
   folder, a pulled volume, a full volume, a denied grant.
6. **Regressions.** App-list regression, exported allow-list, APK size, the phase 17 / phase 10 hand-off
   rows, phase 12 E1 on this build (C-4 c), the baseline assertion (E10).
7. **R11 values applied.** Once `r11/files.md` lands, the Y rows it measures are replaced and E11 is written; FINAL
   only then (RV9).
8. **Recycle Bin** (T18-1, Decisions): the per-volume `.Tessera/bin/` with `.nomedia` and `.index.json`; delete as a
   same-volume rename + index write + scan; the root-page entry and the bin page (Y6); Restore with the conflict dialog and
   the recreated folder; Delete permanently and Empty with confirmations; the lost-index and gone-file rules; the
   `files_bin*` tags and `[files] bin …` lines; the one-line ADD to phase 19's Storage page (the bin's size with Empty).
9. **Zip** (T18-2, Decisions): the virtual root over `ZipFile`; extract and create through the copy service; the
   entry-name, free-space and bomb guards; the password-protected and corrupt states; `[files] zip …` lines.
10. **Recent** (T18-2, Decisions): the MediaStore query (`DATE_MODIFIED` desc, cap 100, no `.nomedia` folders, no bins),
    re-read through a ContentObserver.
11. **App Shortcuts** (phase 11 Q1's standing rule; C-8, C-9): static `res/xml/shortcuts.xml` entries `files_device`,
    `files_recent`, `files_bin` (ranks 0–2, targeting FilesActivity with the page extra) and the dynamic `files_sdcard`
    (rank 3, published on mount, removed on unmount). E2 and E16.
12. **Harness.** `qa/phase-18/baseline_layout.json` derived from `qa/phase-17/baseline_layout.json` with a Files tile
    pinned (for E2's and E16's bursts), `manualSizes` set, every `addedOnce` marker of the build; the previous file kept as
    `qa/phase-18/baseline_layout-pre-18.json` (C-3's form; this phase adds no marker); the zip fixtures'
    `qa/phase-18/scripts/make_zips.py`; drivers in `qa/phase-18/scripts/`.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); every row starts from
`qa/phase-18/baseline_layout.json` through `layout_restore` (`qa/phase-02/scripts/layout.sh`), and after the restore the ring
holds zero `assignSlotOnce … -> assigned` lines (C-3). Motion rows follow RV11 and take their clock from the shell's
`[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` lines (`withFrameNanos`), a screenrecord corroborating
under phase 05's frame-spacing rule and never the primary clock (C-5). Dumps follow RV13. Every row that launches an app
(E6, E10's hold menu, the bursts) does `adb shell am force-stop app.tileshell` + Home before its next assertion on Start's
grid, because the promoted tile lives in memory only (`qa/phase-01/scripts/recent0922.sh:19-21`; C-6). All-files access (Q1
A) is granted by `provision.sh` (build task 1); a row that wipes the app reads "`pm clear` → `provision.sh` → Home" (C-4 d),
and phase 12's rule holds here: a finished or skipped wizard is never re-summoned on that install — this phase's grant goes
red on the Setup checklist instead (C-4 e). Harness: qa/phase-18/scripts/lib.sh → symlink to qa/phase-03/scripts/lib.sh.
Device: the AOSP AVD tileshell_fhd (1080×2340 @ 450 dpi, API 36, no Google).
~~Written for Q1 A; re-cut if Q1 rules otherwise.~~ SUPERSEDED 2026-09-23: Q1 ruled A (T18-7).

**Fixtures.** `adb shell mkdir -p /sdcard/QA-Files/sub` and files with known names, sizes and dates: `adb shell
"dd if=/dev/zero of=/sdcard/QA-Files/b.bin bs=1024 count=300"`, `touch -d "2026-01-01 00:00"
/sdcard/QA-Files/a.txt` (toybox touch), the images from docs/plan/qa/phase-01/scripts/make_photos.py, the
`qa-steps.mp4` from phase 17, one MP3 from qa/phase-01/MUSIC6's fixtures; md5s recorded with `adb shell
md5sum`. A big file for progress: `adb shell "dd if=/dev/zero of=/sdcard/QA-Files/big.bin bs=1m count=200"`. **Zips**
(`qa/phase-18/scripts/make_zips.py`, python 3.11 `zipfile` on the host, pushed to `/sdcard/QA-Files/zips/`): `qa.zip`
(`one.txt` 1,024 bytes, `dir/two.bin` 307,200 bytes, `ü-name.txt` with the UTF-8 flag; md5s recorded), `qa-bad.zip`
(`ok.txt` plus entries named `../../evil.txt` and `/sdcard/abs.txt`), `qa-corrupt.zip` (`head -c 500 qa.zip`), `qa-enc.zip`
(`zip -e -P qa` from Info-ZIP, `/usr/bin/zip`), `qa-bomb.zip` (one 50 MB entry of zeros whose central-directory and
local-header uncompressed sizes are patched to 1,024), `qa-huge.zip` (one 3 GB entry of zeros, about 3 MB compressed,
declared honestly), `qa-big.zip` (200 MB of `/dev/urandom`, stored, for progress and cancel), `qa-nested.zip` (holding
`qa.zip`). **Recent:** `r1.txt`, `r2.txt`, `r3.txt` under `/sdcard/QA-Files/recent/` with `touch -d` at 2026-09-01, -02,
-03 12:00, plus `r4.txt` `adb push`ed (now), then the scan (`content call … scan_volume`, phase 01 E6's command).

**Emulator:**
- E1 **Grant and checklist.** `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow` → the
  checklist row "Files" is green (dump) and the app lists This Device; `appops set … default` → the row is red,
  the app shows "Files can't see this phone's storage" with a link (dump text), the link starts
  `Settings$ManageExternalStorageActivity` (`dumpsys activity activities`), diagnostics `[files] access=denied`; restore
  `appops set … allow` (RV12).
- E2 **Removable volume and the SD card shortcut.** With the grant: the root page shows `files_root:emulated` only (`sm
  list-volumes` = `emulated;0 mounted`), and `dumpsys shortcut` lists no `files_sdcard` for app.tileshell; hold the pinned
  Files tile → 3 satellites "This device", "Recent", "Recycle Bin" (`quick_sat_label:0..2`, `quick_sat:3` absent);
  `am force-stop` + Home (C-6); `adb shell sm set-virtual-disk true`, `sm list-disks` → `disk:<id>`, `sm partition
  disk:<id> public` → `sm list-volumes` shows a `public:` volume mounted and within 3 s the app shows a second
  root (`files_root:public`) whose name is the volume's, `dumpsys shortcut` lists `files_sdcard` as a dynamic shortcut and
  the Files tile's burst shows 4 satellites with "SD card" last (T18-6); browse it, create a folder on it (`adb shell ls
  /storage/<UUID>` shows it); `sm unmount public:<x>,<y>` → the root disappears, `files_sdcard` is gone from `dumpsys
  shortcut` and the burst is back to 3, and, if the volume was open, the page shows "This storage was removed" (dump text),
  no crash (`logcat -d -s AndroidRuntime` empty of `app.tileshell`); restore `sm set-virtual-disk false`.
- E3 **Listing and sort.** The QA-Files folder lists `a.txt`, `b.bin`, `sub`, the images, the video and the
  MP3 with type glyphs (dump `files_row:` order); sort by name / date / size / type each gives the order the
  fixtures' names, `touch -d` dates and `dd` sizes dictate (four dumps, exact order asserted).
- E4 **Operations.** Copy `b.bin` into `sub` → `adb shell md5sum` equal on both; move `a.txt` into `sub` →
  gone from the parent, present in `sub`, md5 unchanged; rename `b.bin` → `c.bin` (`ls`); new folder `n` →
  `ls -d /sdcard/QA-Files/n`; delete `c.bin` after the confirmation → it goes to the Recycle Bin (E4b's assertions); a
  conflict (copy `sub/b.bin` back over `b.bin`) offers replace / keep both / skip and each does what it says (`ls`, md5);
  copy `big.bin` → the progress notification is in `dumpsys notification`, `input keyevent KEYCODE_HOME`
  mid-copy leaves the service running (`dumpsys activity services app.tileshell` shows it), the copy completes
  with an equal md5; cancel mid-copy → no file and no `*.part` left in the destination (`ls`).
  ~~delete `c.bin` after the confirmation (Q3 A) → `ls` fails~~ SUPERSEDED 2026-09-23 by Q3 C (T18-7): the file goes to the bin.
- E4b **Recycle Bin (T18-1).** Delete `c.bin` → `ls /sdcard/QA-Files/c.bin` fails, `ls /sdcard/.Tessera/bin/` lists
  `<ms>-c.bin` with the md5 unchanged, `/sdcard/.Tessera/bin/.nomedia` exists, `.index.json` (`adb shell cat`) holds its
  original path, and `[files] bin delete /sdcard/QA-Files/c.bin: ok`; the Recycle Bin page lists `files_bin_row:c.bin` with
  its volume. Delete `qa-photo-0.png` from `DCIM/Camera` → within 3 s `content query --uri
  content://media/external/images/media --projection _display_name` no longer lists it, the Photos tile logs its refresh
  and the file does not come back after a second scan. Restore `c.bin` → back at `/sdcard/QA-Files/c.bin` with the same md5,
  the bin row gone; restore into a deleted folder (`sub/`'s file binned, then `adb shell rm -r sub`) → `sub/` recreated with
  the file; restore over an existing name → the conflict dialog, and replace / keep both / skip each does what it says
  (`ls`, md5). Delete permanently one row → gone from the bin folder and the index; Empty → the bin folder holds only
  `.nomedia` and `.index.json` with zero records; a delete made on the bin page is permanent. A delete on E2's public volume
  lands in THAT volume's bin (`ls /storage/<UUID>/.Tessera/bin/`), not the primary one. Negatives: `adb shell rm
  /sdcard/QA-Files/a.txt` never appears in the bin; the bin's files never appear in Recent (E14) or in a listing of
  `/sdcard` (dot-folders hidden). Full volume: fill with `fallocate` (phase 17's edge-case command) and delete a file → it
  still moves to the bin (a rename needs no space), restore by deleting the fill. `adb shell rm -r /sdcard/.Tessera` →
  the next delete recreates the bin and logs `[files] bin index /sdcard: rebuilt (bin folder missing)`. `adb uninstall
  app.tileshell` → `ls /sdcard/.Tessera/bin/` still lists the binned files; reinstall through `provision.sh` (RV12).
- E5 **Negatives.** `/sdcard/Android/data` lists the fixture packages' folders as unreadable entries (dump text
  "Android doesn't let apps see this folder"), tapping one shows the reason, no crash; `/sdcard/Android/obb` the
  same; the shell's own `/sdcard/Android/data/app.tileshell` is readable.
- E6 **Open with.** Tap an image → `dumpsys activity activities` topResumedActivity =
  `app.tileshell/.photos.PhotosActivity` (phase 17) showing that image (phase 17 E4's pixel rule); a video →
  `app.tileshell/.video.VideoActivity` playing it (`dumpsys media_session`); the MP3 → MusicActivity with
  `dumpsys media_session` showing the shell's music session PLAYING that track (the phase 10 ADD); `a.txt` →
  the system chooser (`com.android.intentresolver`) with `text/plain`; a `.xyz` file → "No app on this phone
  opens this" (dump text), diagnostics `[files] no handler for application/octet-stream`. After each launch `am
  force-stop app.tileshell` + Home before the next grid read (C-6).
- E7 **Share.** Select two files → share → the chooser resumed with `ACTION_SEND_MULTIPLE` and two
  `content://` URIs from the shell's FileProvider (`dumpsys activity activities` intent line).
- E8 **Search.** "b" from the QA-Files root lists `b.bin` and `sub/b.bin` with their paths; a term with no match
  shows the empty line; searching while the walk runs shows the progress line and cancel stops it (`[files]
  search cancelled`).
- E9 **MediaStore in step.** Move `qa-photo-0.png` from `DCIM/Camera` to `Pictures/QA-Album` in Files → `adb
  shell content query --uri content://media/external/images/media --projection _display_name:relative_path`
  shows the new path within 3 s and the Photos tile's `[photos] refresh (mediastore change)` line follows;
  rename the MP3 → Music's library shows the new title source (`content query` on the audio collection) and
  phase 10's `[music]` observer line fires. RECORDED alongside (T18-4): `adb shell mv
  /sdcard/Pictures/QA-Album/qa-photo-1.png /sdcard/DCIM/Camera/` (a rename by path through FUSE, no scan) and the same query
  3 s later — whether MediaProvider followed it on its own is recorded; Files keeps its scan either way.
- E10 **App list, surface, budget.** "Files" under F with Pin to Start and NO Uninstall (qa/phase-01/UNINSTALL's
  method), no "New" caption; `am force-stop` + Home after the hold menu (C-6); phase 02's regress.sh pattern passes from
  the phase baseline (zero `-> assigned` lines after `layout_restore`, `addedOnce` equal to the file's — C-3); `dumpsys
  package app.tileshell` exported components equal qa/phase-03/exported-allowlist.txt plus this phase's ADD (FilesActivity)
  — the FileProvider is listed under Providers with `exported=false` and `grantUriPermissions=true` and is NOT an
  allow-list entry (T18-5); `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes.
- E11 **Geometry and motion against `r11/files.md`** — written once that section lands; not runnable before, and the doc
  cannot go FINAL without it.
- E12 **Diagnostics.** Lines asserted by the rows above: `[files] access=<granted|denied>`, `[files] roots:
  <n> (<names>)`, `[files] volume mounted|unmounted <name>`, `[files] list <path>: <n> entries <ms> ms`,
  `[files] unreadable <path>`, `[files] copy|move <n> files <bytes> -> <dest> done|cancelled|failed <reason>`,
  `[files] no handler for <mime>`, `[files] search cancelled`, `[files] bin <delete|restore|purge|empty> <path>: ok | failed
  <why>`, `[files] bin index <volume>: <n> entries | rebuilt (<why>)`, `[files] zip open <path>: <n> entries`, `[files] zip
  extract <path> -> <dest>: done | cancelled | failed <reason>`, `[files] zip: refused entry <name>`, `[files] zip: refused
  (needs <bytes>, free <bytes>)`, `[files] zip: stopped at <bytes> (declared <bytes>)`, `[files] zip: encrypted <path>`,
  `[files] zip create <n> files -> <path>: done`, `[files] recent: <n>`, `[files] shortcut sdcard published | removed`, and
  every `[motion] <name> …` line a row times.
- E13 **Zip (T18-2).** Open `qa.zip` → a virtual root (`files_zip_root`) listing `one.txt` 1,024 bytes, `dir`, `ü-name.txt`
  (dump `files_row:` / `files_detail:` text), `[files] zip open …: 3 entries`; extract → `ls` and `md5sum` of each entry
  equal the recorded values, `ü-name.txt` named correctly; `qa-bad.zip` → `ok.txt` extracted and `[files] zip: refused entry
  ../../evil.txt` and `… /sdcard/abs.txt`, `ls /sdcard/evil.txt`, `ls /sdcard/QA-Files/evil.txt` and `ls /sdcard/abs.txt`
  all fail (and the control: `ok.txt` present — both directions); `qa-corrupt.zip` → "This zip can't be opened" (dump text),
  no crash (`AndroidRuntime` empty); `qa-enc.zip` → "This zip is password-protected" and `[files] zip: encrypted …`, nothing
  written; `qa-bomb.zip` → extraction stops, `[files] zip: stopped at <n> (declared 1024)` with n ≤ 1,024 + 1,048,576, and
  no temp folder or partial file remains (`ls`); `qa-huge.zip` with the volume filled to leave < 3 GB (`fallocate`) → refused
  before any write with `[files] zip: refused (needs …, free …)`, restore the fill; `qa-big.zip` extract → the progress
  notification in `dumpsys notification`, cancel mid-way → no output folder and no temp folder (`ls`); `qa-nested.zip` →
  `qa.zip` listed as a file that opens as a zip again. Create a zip from `a.txt` + `b.bin` → `adb pull` it, host `unzip -l`
  lists exactly those two names and `unzip -t` passes.
- E14 **Recent (T18-2).** After the Recent fixtures and the scan, the Recent view lists `r4.txt`, `r3.txt`, `r2.txt`,
  `r1.txt` in that order at the top (`files_recent_row:` order, exact); rename `r1.txt` in Files → it rises to the top
  within 3 s; a file written with `adb shell 'echo x > /sdcard/QA-Files/recent/unscanned.txt'` and no scan is absent, and a
  file in a `.nomedia` folder (`/sdcard/QA-Files/hidden/.nomedia` + a file + scan) is absent — both absences are the stated
  rule; no bin file appears (E4b); `[files] recent: <n>` with n ≤ 100.
- E15 **Wizard step added (phase 12 E14's template; T18-3, C-4 b).** `pm clear app.tileshell` → `provision.sh` → `adb
  shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE default` + Home → `wizard_step:setup:files` present with its
  `wizard_why` text equal to Decisions' line; its action starts `Settings$ManageExternalStorageActivity`; `appops set …
  allow` + Home → the step absent and the wizard not shown; then phase 12 E1 re-run on this build (C-4 c).
- E16 **App Shortcuts (phase 11 Q1's standing rule; C-8).** From the phase baseline (Files tile pinned): hold → labels
  "This device", "Recent", "Recycle Bin" in rank order (phase 11 E3's method), `[quick] shortcuts for app.tileshell/0: …`;
  tap each → FilesActivity resumed on that page (`files_root:emulated`, `files_recent_row:*` / its empty line, `files_bin`
  present); `dumpsys shortcut` lists `files_device`, `files_recent`, `files_bin` as manifest shortcuts with ranks 0, 1, 2;
  `am force-stop` + Home between holds (C-6). The SD card half is in E2.

**Phone-only:**
- P1 One UI's "All files access" page from the checklist row (`am start -a
  android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION -d package:app.tileshell`, resumed activity recorded) and
  the grant surviving a reboot and a Device care optimise; the wizard step on a phone with the grant revoked.
- P2 A USB OTG drive: appears as a root, browse / copy / pull mid-copy (E2's assertions on real hardware);
  exFAT and FAT32 name rules (case-insensitive rename); a delete on it lands in the drive's own bin, and the drive pulled
  takes its bin rows away (E4b's assertions).
- P3 Samsung Camera's DCIM at thousands of items and the S25U's Download folder: list time and scroll per
  phase 01 P4's gfxinfo method.
- P4 Secure Folder and Private Space contents are absent (their storage is another user's), and the app says
  nothing about them rather than an error.
- P5 One UI's "Open with" sheet for `ACTION_VIEW` from Files (which apps it lists for a `.txt`, a `.pdf`). RECORDED.

**NEEDS-HUMAN:** H1 *fidelity* — Files matches r11/files.md on the phone; H2 *accept* — the Recycle Bin page and its
wording (Y6; P4 — W10M had none), including the Delete permanently and Empty confirmations (T18-1);
~~the delete rule (Q3) and its confirmation wording~~ SUPERSEDED 2026-09-23 by Q3 C (T18-7); H3 *accept* — motion
approximations (Y5); H4 *accept* — any Y1–Y3 value R11 does not measure; H5 *accept* — the progress / conflict / properties
dialogs (Y4, P4 design); H6 *accept* — the unreadable-folder wording for `/Android/data`.

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
  the remaining side; the same mid-extract of a zip on that volume → "storage removed", no partial output.
- Grant revoked mid-session (`appops set … default` with the app open): no process restart for an appop
  change, so the app re-checks on every resume and on each operation and shows the grant state instead of an
  IOException.
- Move across volumes = copy then delete; a failure after the copy leaves both (never neither).
- `.nomedia` folders: Files lists them; Photos and Music do not show their contents (MediaStore's rule) — both
  asserted; Recent does not list them (E14).
- Symlinks and special files are not present on shared storage (FUSE); a path that resolves outside the
  volume is refused.
- A file opened in Photos, then deleted in Files: Photos' stale row shows a placeholder until the scan lands
  (phase 17's edge case).
- Screen off, an incoming call (`adb emu gsm call 5551234`) and a reboot during a copy: the service finishes
  or fails cleanly; after a reboot no temp files remain (`find /sdcard -name '*.part'` empty).
- The app list's hold menu on Files (Pin to Start present; Uninstall absent; E10) and Files pinned to Start
  (phase 02) opening the root page.
- Recycle Bin: `.index.json` deleted by hand (`adb shell rm`) → the bin lists its files by bin name, Restore puts them in
  `/sdcard/Download/Restored/` and `[files] bin index /sdcard: rebuilt (index missing)`; an index record whose file another
  app deleted → dropped on the next read; a binned file whose original folder is now a FILE of that name → the conflict
  dialog; two files deleted from different folders with the same name → two bin rows (the deleted-at prefix keeps them
  apart), each restoring to its own path.
- Zip: a zip64 archive (`make_zips.py` writes one with 70,000 empty entries) opens and pages without stalling; a zip
  whose entry names are CP437 without the UTF-8 flag lists them decoded; a zip opened from the bin (Recycle Bin rows open
  nothing — Restore first, stated).
- Liveness (N-01): reboot, Device care and a force-stop leave the grant, the checklist row, the bins and their indexes
  intact.

## QA evidence
_None yet._
