---
phase: 18
slug: files
status: DRAFT   # split 2026-09-22; interview DONE 2026-09-23; review triage round 1 applied 2026-09-23 (review/2026-09-23-phases11-19-triage.md); round 2 applied 2026-09-23 (review/2026-09-23-phases11-20-r2-triage.md); r11/files.md landed 2026-09-23 and is applied (T18-8; E11 written); DRAFT → FINAL after Stage A step 7
depends-on: [01, 02, 10, 11, 12, 17]   # C-23: 11 for the E2 / E16 bursts (per-activity shortcut query), 12 for the E15 template and C-15's provisioning marker
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
- Entry points in W10M's ≡ pane — there is no root page (r11/files.md 1.3; T18-8): Recent, This Device (the primary shared
  storage, `/storage/emulated/0`), one row per mounted public volume (`StorageManager.getStorageVolumes()`: an SD card or a
  USB OTG drive, named by its label, `StorageVolume.getDescription`, with no drive letter), appearing and disappearing with
  `ACTION_MEDIA_MOUNTED` / `ACTION_MEDIA_UNMOUNTED`, and the Recycle Bin as a fourth row (P4; Q2 C, Q3 C). The app opens on
  This Device. ~~beside them the Recent view and the Recycle Bin (Q2 C, Q3 C — the functional set is ruled; r11/files.md
  settles how W10M drew its root page)~~ SUPERSEDED 2026-09-23 by T18-8 (R11: no root page, the ≡ pane).
- The folder page: the location bar (≡, breadcrumb, ↑), the "Sort by" line, and folder listing with W10M-style type icons
  (colour folder / page / type icons and thumbnails, A10 branding assets; r11/files.md 1.5.3); sort by name / date / size
  (W10M had no type sort, r11/files.md 1.4.5; T18-8); list and Icons views; search within the current tree;
  multi-select; "Move to" / "Copy to" (pick a folder; no cut / paste — W10M's verbs, r11/files.md 1.12.6) with progress,
  conflicts and cancel, surviving leaving the app (a foreground service, `FOREGROUND_SERVICE_DATA_SYNC`); rename; delete into
  the Recycle Bin (Q3 C; Decisions); new folder; share; a Properties page (name, size, date, path, per-type sections);
  open-with routing (Q4 A); a "cannot read" entry for `/Android/data` and `/Android/obb`
  (Android 11+ hides them from every app, All-files access included). No free-space line (W10M had none; phase 19's
  Storage page shows space — T18-8). ~~Folder listing with W10M glyphs by type; sort by name / date / size / type~~
  SUPERSEDED 2026-09-23 by T18-8.
- Zip (Q2 C): open a zip as a folder, extract (through the same foreground service), create one from a selection —
  `java.util.zip`, no new dependency (Decisions). A P4 addition: W10M's File Explorer had no zip handling
  (r11/files.md 1.12.7); H7 judges the flows.
- Recent (Q2 C): recently changed files across the phone from MediaStore's modified dates (Decisions); W10M's Recent was
  "recently accessed or downloaded" (r11/files.md 1.12.9), so the form is H8's call.
- The Recycle Bin (Q3 C): one bin per volume, its own row in the ≡ pane (P4 — W10M had no bin, r11/files.md 1.12.8),
  Restore / Delete permanently / Empty (Decisions); this phase publishes the bin index reader and an `empty(volume)` call,
  and phase 19 builds its Storage page's bin row with them (T18-9).
- "Open file location" in phase 15's Voice Recorder: an ADD by this phase to phase 15's part (a recording's hold menu gains
  the entry, which opens Files at the recording's folder; T15-16, Decisions).
- The storage grant (Q1 A: All-files access, `MANAGE_EXTERNAL_STORAGE`) with its Setup checklist row and its setup-wizard
  step (phase 12 Q1 / Q2), the app's ungranted state naming the checklist, and the grant opened from the app
  (`android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION`, which resolves to
  `com.android.settings/.Settings$ManageExternalStorageActivity` on the AVD, 2026-09-22).
- MediaStore kept in step: every write the app makes is followed by a scan of the touched paths
  (`MediaScannerConnection.scanFile`) so the Photos tile, Photos and Music see moves and deletes at once — belt-and-braces,
  since MediaProvider already follows renames and deletes made by path on Android 11+ (Decisions, T18-4).
- The ADD to phase 10 that lets Files hand an audio file to the shell's Music player by explicit component
  (Decisions); a `FileProvider` for Share (a manifest ADD, not exported; it hands out a URI only for a file under a
  `StorageVolume.getDirectory()` — T18-11); static App Shortcuts plus the dynamic "SD card"
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
- 2026-09-22 (agent): **R11 gates FINAL.** Every visual value is from `docs/plan/r11/files.md` (landed 2026-09-23, applied
  by T18-8; ~~pending — … not written as of 2026-09-23 — C-12~~ SUPERSEDED 2026-09-23 by T18-8) or already measured:
  phase 01's drawn status bar (`BarMetrics.STATUS_EPX`, `app/src/main/kotlin/app/tileshell/bars/SystemBars.kt:77-80`; its
  value is open at phase 01 against R11's 24 epx — C-17), ~~list rows (R3 C2 / R6 §5.1.4)~~ (SUPERSEDED 2026-09-23 by T18-8:
  File Explorer's own 64-epx two-line rows, r11/files.md 1.5.2),
  Settings-page rows for the app's settings (R3 C1), the Start exit (R3 A11), the app-list hold menu's band
  (phase 02 H21, approximation). R11 files.md has no HIGH value (every phone source is a downscale or a camera photo; the
  exact source is the same UWP app on desktop 1703): its MEDIUM values are H1 [fidelity], its LOW / UNMEASURED ones and every
  P4 addition are [accept] rows. Anything else is a Y row with an H row (RV9 / Q10); motion is planned as
  approximations from the start (R10 design 10; R11 found no recording, r11/files.md §4).
- 2026-09-22 (agent): **the two kinds of NEEDS-HUMAN row are labelled** — *fidelity* (matches R11 §Files,
  judged on the phone) and *accept* (P4 design or approximation; no footage). qa/phase-18/NEEDS-HUMAN.md follows
  qa/phase-03/NEEDS-HUMAN.md's shape.
- 2026-09-22 (agent): **harness contracts** (R10 testability 24, 25): the activity root sets
  `Modifier.semantics { testTagsAsResourceId = true }` (as MusicActivity.kt), each row's name and detail carry
  their own tags (`files_row:<name>`, `files_detail:<name>`), the ≡ pane's rows theirs (`files_pane:<recent|device|<volume
  uuid>|bin>`, T18-8; the pane itself `files_pane`, the location bar `files_location`, its segments
  `files_crumb:<n>`, ↑ `files_up`, the ≡ button `files_menu`, the sort line `files_sort`), every silent-empty state writes a
  diagnostics line
  under `[files]` (E12). Drivers symlink qa/phase-03/scripts/lib.sh; evidence under qa/phase-18/.
- 2026-09-22 (agent): **app list and process.** One launcher entry "Files" (LAUNCHER + APP_FILES, the category
  DocumentsUI declares on the AVD), excluded from Uninstall by `AppUninstall.canUninstall`'s self-package rule
  (E10 proves it); main process (a list over the filesystem, no decode); the copy service in the main process
  too. App-list regression (phase 02's regress.sh pattern) runs once for this phase.
- 2026-09-22 (agent): **bars.** A shell-owned screen under phase 01's bar rule: Samsung's bars hidden, the
  drawn W10M status and nav bars (File Explorer drew its status bar, r11/files.md 1.1.1 — unlike Photos); ~~Back walks up
  one folder and leaves the app at a root (approximation Y3 until R11 §Files says how W10M's Back behaved in File
  Explorer)~~ SUPERSEDED 2026-09-23 by T18-8: Back leaves selection mode first (r11/files.md 1.8.1); ↑ and a breadcrumb
  segment go up (1.2.7–1.2.8, 1.12.3); otherwise Back returns to the previous location in the app's history (D3's UWP rule —
  equal to "up one level" for a straight descent) and leaves the app when the history is empty (Y3, R11 UNMEASURED-3).
- 2026-09-22 (agent): **APK budget.** No new dependency (zip is the platform's `java.util.zip`; the `FileProvider` is
  androidx.core's, already in the build); E10 checks the size against phase 03's ≤ 600 MB.
- 2026-09-23 (agent, review triage T18-1): **the Recycle Bin, designed below the Q3 ruling.** One bin per volume at
  `<volume root>/.Tessera/bin/` holding a `.nomedia` marker and `.index.json` (one record per binned file: bin name, original
  path, deleted-at, size; written temp-and-rename, LayoutStore's shape). The bin is its OWN entry — ~~on the root page~~ a
  fourth row of the ≡ pane under the volumes (SUPERSEDED 2026-09-23 by T18-8: W10M had no root page) — ("Recycle
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
  with it (its rows vanish with its pane row). ~~Phase 19's Storage page shows the bin's size per volume with Empty (a one-line
  ADD there, phase 19's table).~~ SUPERSEDED 2026-09-23 by T18-9: this phase publishes the bin index reader (per volume: entry
  count and bytes) and an `empty(volume)` call; phase 19 builds its Storage page's bin row with them (phase 19 builds after
  this one — Build order). **Privacy (T18-10):** binned files stay readable on shared storage — `.nomedia` hides them from
  MediaStore only, so any app with storage access and a PC over USB can read them until Empty — and the bin page says so in
  one line under its header: "Deleted files stay on this phone until you empty the Recycle Bin" (tag `files_bin_note`; H2).
  Tags `files_bin`, `files_bin_row:<name>`, `files_bin_restore`, `files_bin_delete`,
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
  names the features; these picks are the platform-native form and the minimum that makes extraction safe. [2026-09-23, T18-8:
  both are P4 additions — W10M's File Explorer had no zip handling (r11/files.md 1.12.7) and its Recent was "recently accessed
  or downloaded" (1.12.9); zip's virtual root reuses the folder page unchanged; H7 judges the zip flows, H8 Recent's form.]
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
  re-summoned on that install — this grant goes red on the Setup checklist instead. [2026-09-23, C-15: the lead's standing
  decision (provisioning writes the wizard's finished marker) holds; E15 is re-cut to phase 12's three-part E14 template.]
- 2026-09-23 (r2 triage T18-11, a doc update; a trust change): **the FileProvider's scope.** Reaching `/storage/<UUID>`
  needs a `root-path` entry, which would also cover the app's private dirs (`/data/data/app.tileshell`, `filesDir`,
  `cacheDir`), and with All-files access a Share could then hand out a private file. So `FileProvider.getUriForFile` is called
  only after a canonical-path check (`File.canonicalPath`, symlinks and `..` resolved) that the file lies under one of
  `StorageManager.getStorageVolumes()`' `StorageVolume.getDirectory()`; anything else is refused with `[files] share refused:
  outside shared storage` and the share does not start. The provider scope joins this phase's adversarial-review list (the
  project's rule for access-control changes): a GATE recorded under qa/phase-18/ before `done`.
- 2026-09-23 (agent, r2 triage T18-8): **r11/files.md applied, with four calls.** (1) No root page: W10M's ≡ pane (256-epx
  overlay, #171717, 48-epx rows from 72 epx, glyph cx 24, label x 60, the selected row the accent at 60 % over #171717;
  r11/files.md 1.3) with rows Recent / This Device / one per mounted volume / Recycle Bin (a fourth row, P4, H2); the app opens
  on This Device (R11 UNMEASURED-4); `files_root:*` tags become `files_pane:*`. (2) Volumes are named by their label
  (`StorageVolume.getDescription`) with no drive letter — Android volumes have none, and "(D:)" would be pure imitation (H4).
  (3) Sort by name / date / size only — "type" is dropped (W10M had no type sort, r11/files.md 1.4.5, and nothing ruled one).
  (4) No free-space line in Files (W10M had none, 1.13.1; phase 19's Storage page shows space). The measured forms replace the
  stand-ins: 64-epx two-line rows with W10M-style colour type icons and thumbnails (A10 branding assets; MDL2 Folder E8B7 /
  Page E7C3 the monochrome fallback), the location bar with breadcrumb and ↑, the "Sort by: Name ⌄" line, the W10M app bar,
  selection bar and menus, the verbs "Move to" / "Copy to" (no cut / paste), Properties as a PAGE, the other dialogs from R7
  1.3.9's top-anchored W10M dialog, Back per the bars Decision above; zip and the Recycle Bin are P4 additions in full (H7, H2),
  Recent's form is H8's; E11 is written now. Reason: R11 measured that W10M's File Explorer had no root page, zip, recycle bin
  or type sort, so the ruled zip and bin are stated P4 additions in the pane's least-invented slot, and every other value
  follows the measurement (A4: fidelity beats feature count).
- 2026-09-23 (agent, r2 triage T15-16): **"Open file location" is built by this phase as an ADD to phase 15's Voice
  Recorder.** A recording's hold menu gains `rec_menu:location` ("Open file location"), which opens FilesActivity by explicit
  component at the recording's folder (`/storage/emulated/0/Recordings`, from its MediaStore `RELATIVE_PATH`) with the
  recording's row shown (page extra `path`; line `[files] open at <path> (from recorder)`); recorded in the INDEX Change Log
  for phase 15's part when built; E17 proves it. Reason: phase 18 builds Files, so the entry that opens Files belongs here as
  an ADD when Files exists, not as a hook left in phase 15 (T15-16; Rule 16).
- 2026-09-23 (agent, r2 triage C-17): **the status bar is cited, not hard-coded.** Every status-bar value in this doc reads
  "phase 01's drawn status bar (`BarMetrics.STATUS_EPX`)" (today 28, `app/src/main/kotlin/app/tileshell/bars/SystemBars.kt:79`);
  R11 measured File Explorer's at 24 epx (r11/files.md 1.1.1), and the R3 C4 re-check in INDEX's research table decides the
  constant at phase 01 (Q-F only if Start and apps really differ). Reason: re-measure before anyone rules; the docs then need
  no edit whichever way it lands.

### Approximations (re-cut 2026-09-23 against r11/files.md, T18-8; each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | The ≡ pane (Recent / This Device / volumes / Recycle Bin) | MEASURED (r11/files.md 1.3, MEDIUM / LOW) — closed; only the Recycle Bin row (P4, no W10M bin) and the default landing entry (UNMEASURED-4: This Device) remain approximations. ~~Root page layout … free-space line; two-line rows at the R3 C1 64-epx pitch with a 30-epx glyph~~ SUPERSEDED 2026-09-23 by T18-8 (no root page, no free-space line) | the bin row in the pane's row form with the Delete glyph (E74D); the app opens on This Device | H2 (bin row), H4 (landing) |
| Y2 | Folder rows, type icons, the location bar, the sort line, the selection check, the app bars and menus (also a zip's virtual root and Recent) | MEASURED (r11/files.md 1.2, 1.4–1.9; MEDIUM / LOW); the selection geometry is UNMEASURED-7 | selection: R7 1.3.9 (20.3-epx checkbox at x 22.2, content shifted 32 epx, accent row fill); the sort picker: a 242.6-epx flyout with 44-epx items (R7 2.2.5, UNMEASURED-6). ~~app-list rows (R3 C2) with a 48-epx app bar~~ SUPERSEDED 2026-09-23 by T18-8 | H1 (measured), H4 (the two stand-ins) |
| Y3 | Back behaviour | PARTLY documented (r11/files.md 1.8.1, 1.12.3–1.12.4; UNMEASURED-3) | Back leaves selection mode; otherwise the previous location in history (= up one level on a straight descent; after a breadcrumb jump, the folder left); leaves the app when the history is empty | H4 |
| Y4 | Progress, conflict, delete-confirmation, rename and new-folder dialogs; the Move to / Copy to picker | UNMEASURED-2 (no capture); Properties is MEASURED as a page (1.10) — ~~properties dialog~~ struck 2026-09-23 by T18-8 | R7 1.3.9's top-anchored W10M dialog (full width, (74,74,74), title / body / two side-by-side buttons); the picker = the folder page in a picker mode with a bottom confirm bar in the app-bar geometry (1.7). ~~P4 design in phase 03's card idiom (R6 §3.4.2)~~ SUPERSEDED 2026-09-23 by T18-8 | H5 |
| Y5 | Motion: pane open / close, folder change, ••• expand, hold menu, selection mode | UNMEASURED (r11/files.md §4); timed by the `[motion]` clock (C-5) | pane open 250 ms ease-out (R7 3.1.10); pane close one frame, then the page fades in; folder change / ↑ / breadcrumb: a cut, then a 250-ms ease-out fade (R7 3.2.2); ••• expand 317 ms (R7 2.1.16); hold menu box 200–233 ms (R7 2.2.6); selection mode and list ↔ icons a one-frame cut | H3 |
| Y6 | The Recycle Bin page (rows with volume and deleted-at, the privacy line, Restore / Delete permanently / Empty) and its confirmations | no W10M original (P4) | folder rows (Y2) with the date as the detail line; confirmations in Y4's dialog form | H2 |

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
2. **The ≡ pane, the folder page and listing** (T18-8, r11/files.md 1.1–1.7, 1.11). Volumes from `StorageManager`, mount /
   unmount broadcasts; the ≡ pane (`files_pane`: Recent / This Device / one row per volume named by
   `StorageVolume.getDescription`, no drive letter / Recycle Bin; opens on This Device) ~~the Recent and Recycle Bin entries on
   the root page~~ (SUPERSEDED 2026-09-23 by T18-8); the location bar (≡, breadcrumb with collapsed middle segments and
   tappable segments, ↑ dimmed at a volume root); the "Sort by: Name ⌄" line and its flyout; folder listing by path in 64-epx
   two-line rows with W10M-style type icons / thumbnails (A10 assets; MDL2 Folder E8B7 / Page E7C3 as the fallback) and the
   detail line ("date" for a folder, "size date" for a file); the Icons view; sort by name / date / size (~~/ type~~ struck
   2026-09-23 by T18-8); search within the tree (a background walk with a cancellable progress line; the sort line reads
   "Sort by: Relevance" while searching); the app bar Select / New folder / Icons↔List / Search / More and its overflow
   (Refresh / Select all / Clear selection / Properties); no free-space line; the unreadable `/Android/data` and
   `/Android/obb` entries; paging so a folder of 10,000 entries lists without stalling.
3. **Selection and operations.** Selection mode ("<n> items selected" in the sort line's place, accent-filled rows, the bar
   Delete / Move to / Copy to / Share, Share dim with a folder selected; Back leaves it) and the tap-and-hold flyout (Delete /
   Move to / Copy to / Share / Rename / Properties; a folder has no Share); "Move to" / "Copy to" (Files itself in a picker
   mode) through the foreground service with progress,
   conflict resolution (replace / keep both / skip), cancel with no partial file; rename; delete into the Recycle Bin
   (task 8); new folder; share (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` with content URIs from a `FileProvider` — a
   manifest ADD, none exists today: the manifest's providers are `LiveTileProvider` (`app/src/main/AndroidManifest.xml:162`)
   and `KeyboardConfigProvider` (`:261`); it is `exported="false"` with `grantUriPermissions="true"`, so it adds nothing to
   the exported-components allow-list — T18-5); **the provider's scope guard (T18-11):** `getUriForFile` only after the
   canonical-path check that the file lies under a `StorageVolume.getDirectory()`, else `[files] share refused: outside shared
   storage` — a JVM test covers it, and the adversarial review of the provider scope is a GATE recorded under qa/phase-18/
   before `done`; the Properties PAGE (breadcrumb ending in the name, thumbnail + name, grey-label / white-value rows,
   per-type sections, no app bar; r11/files.md 1.10); the dialogs in R7 1.3.9's form (Y4); every write followed by a
   MediaStore scan.
4. **Open-with routing** (Q4 A), including the phase 10 ADD (a play intent on MusicActivity) and phase 17's
   explicit components.
5. **Diagnostics and states.** `[files]` lines for every silent-empty state; error states for a vanished
   folder, a pulled volume, a full volume, a denied grant.
6. **Regressions.** App-list regression, exported allow-list, APK size, the phase 17 / phase 10 hand-off
   rows, phase 12 E1 on this build (C-4 c), the baseline assertion (E10).
7. **R11 values applied** (r11/files.md landed 2026-09-23; T18-8). The Y rows are re-cut in the Approximations table and
   E11 is written below against r11/files.md 1.1–1.10, so R11 no longer holds the doc from FINAL (RV9); the build draws those
   values and E11 proves them. ~~Once `r11/files.md` lands, the Y rows it measures are replaced and E11 is written~~ SUPERSEDED 2026-09-23
   by T18-8.
8. **Recycle Bin** (T18-1, Decisions): the per-volume `.Tessera/bin/` with `.nomedia` and `.index.json`; delete as a
   same-volume rename + index write + scan; the pane's fourth row and the bin page (Y6) with its privacy line (T18-10);
   Restore with the conflict dialog and
   the recreated folder; Delete permanently and Empty with confirmations; the lost-index and gone-file rules; the
   `files_bin*` tags and `[files] bin …` lines; the bin index reader (per volume: entry count and bytes) and the
   `empty(volume)` call phase 19's Storage row uses (T18-9). ~~the one-line ADD to phase 19's Storage page (the bin's size
   with Empty)~~ SUPERSEDED 2026-09-23 by T18-9 (phase 19 builds the row).
9. **Zip** (T18-2, Decisions): the virtual root over `ZipFile`; extract and create through the copy service; the
   entry-name, free-space and bomb guards; the password-protected and corrupt states; `[files] zip …` lines.
10. **Recent** (T18-2, Decisions): the MediaStore query (`DATE_MODIFIED` desc, cap 100, no `.nomedia` folders, no bins),
    re-read through a ContentObserver.
11. **App Shortcuts** (phase 11 Q1's standing rule; C-8, C-9): static `res/xml/shortcuts.xml` entries `files_device`,
    `files_recent`, `files_bin` (ranks 0–2, targeting FilesActivity with the page extra) and the dynamic `files_sdcard`
    (rank 3, published on mount, removed on unmount), built with `ShortcutInfo.Builder.setActivity(<FilesActivity's
    component>)` — a dynamic shortcut without it attaches to the package's first MAIN / LAUNCHER activity, `MusicActivity`
    (`app/src/main/AndroidManifest.xml:111-120`), and would burst on the Music tile under phase 11's per-activity query (C-21).
    E2 and E16.
12. **Harness.** `qa/phase-18/baseline_layout.json` derived from `qa/phase-17/baseline_layout.json` with a Files tile
    pinned (for E2's and E16's bursts), keeping phase 17's `slots` (MUSIC, CALENDAR, PEOPLE, PHOTOS, CAMERA), every `addedOnce`
    marker of the build and `manualSizes` for every tile (C-28); the previous file kept as
    `qa/phase-18/baseline_layout-pre-18.json` (C-3's form; this phase adds no marker); the zip fixtures'
    `qa/phase-18/scripts/make_zips.py`; drivers in `qa/phase-18/scripts/`. Harness helpers this phase calls and does not own:
    `ring_mark` / `ring_since` (C-20, phase 11's build task 7), `record` (C-26, phase 13's build task 7), `fill_volume` /
    `unfill_volume` (C-27, phase 15's build task 8).
13. **"Open file location" in Voice Recorder** (T15-16; an ADD to phase 15's part, INDEX Change Log when built): a
    recording's hold menu gains `rec_menu:location` ("Open file location"), which starts FilesActivity by explicit component
    with the recording's folder (its MediaStore `RELATIVE_PATH` under `/storage/emulated/0`) and name as page extras; Files
    opens that folder with the recording's row shown and logs `[files] open at <path> (from recorder)`. E17.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); every row starts from
`qa/phase-18/baseline_layout.json` through `layout_restore` (`qa/phase-02/scripts/layout.sh`), and after the restore the ring
holds zero `assignSlotOnce … -> assigned` lines (C-3). Motion rows follow RV11 and take their clock from the shell's
`[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` lines (`withFrameNanos`), a screenrecord corroborating
under phase 05's frame-spacing rule and never the primary clock (C-5); the `[motion]` line also carries `frames=<n>
maxGapMs=<ms>`, and every motion row asserts `maxGapMs` ≤ 33.4 ms (2 vsync) beside its numbers (C-31). Every ring assertion
reads `ring_since` from a MARK taken immediately before the step's action (after any clock jump, so the MARK is on the new
clock); absence assertions read the same slice; `reply_text` is `reply_since <MARK>`; `row_end` saves each ring the row names
to `<row>/ring-<name>.txt` (C-20; `lib.sh` `ring_mark` / `ring_since` / `reply_since`, built by phase 11's build task 7).
After any `adb reboot` (boot-completed poll), `dumpsys battery unplug` or `KEYCODE_SLEEP` step, the driver calls
`wake_device` and asserts it printed `Awake` before the next tap (C-25). A recorded clause uses `lib.sh` `record <name>
<value>`, never an assert; a row with only recorded facts ends `<row>: recorded only (<n> facts)` with exit 0 (C-26; helper
built by phase 13's build task 7). A volume is filled only with `lib.sh` `fill_volume <leave_bytes>`, which asserts `adb
shell df /sdcard` free ≤ leave + 5 MB after the fill (a precondition that fails loudly), and emptied with `unfill_volume`
(C-27; helper built by phase 15's build task 8). Dumps follow RV13. Every row that launches an app
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
  checklist row "Files" is green (dump) and the app opens on This Device (`files_crumb:0` text "This Device", the folder
  rows of `/sdcard` listed; T18-8's landing); `appops set … default` → the row is red,
  the app shows "Files can't see this phone's storage" with a link (dump text), the link starts
  `Settings$ManageExternalStorageActivity` (`dumpsys activity activities`), diagnostics `[files] access=denied`; restore
  `appops set … allow` (RV12).
- E2 **Removable volume and the SD card shortcut.** With the grant: tap `files_menu` → the ≡ pane (`files_pane`) lists
  exactly `files_pane:recent`, `files_pane:device`, `files_pane:bin` in that order and no volume row (`sm list-volumes` =
  `emulated;0 mounted`), and `dumpsys shortcut` lists no `files_sdcard` for app.tileshell; hold the pinned
  Files tile → 3 satellites "This device", "Recent", "Recycle Bin" (`quick_sat_label:0..2`, `quick_sat:3` absent) and the
  ring slice holds `[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown:
  files_device,files_recent,files_bin)` (T11-12);
  `am force-stop` + Home (C-6); `adb shell sm set-virtual-disk true`, `sm list-disks` → `disk:<id>`, `sm partition
  disk:<id> public` → `sm list-volumes` shows a `public:<x>,<y> mounted <UUID>` volume and, from a MARK before the partition,
  within 3 s the pane lists a fourth row `files_pane:<UUID>` between `files_pane:device` and `files_pane:bin` whose text is
  non-empty, matches no drive-letter form (`\([A-Z]:\)` absent — T18-8) and equals the `<name>` of `[files] volume mounted
  <name>` (the label text recorded with `record volume_label <text>`), `dumpsys shortcut` lists `files_sdcard` as a dynamic
  shortcut and the Files tile's burst shows 4 satellites with "SD card" last (T18-6), its line `…/.files.FilesActivity/0: 4
  (4 shown: files_device,files_recent,files_bin,files_sdcard)`; `am force-stop` + Home, hold the Music tile (the baseline's
  MUSIC slot) → its burst is unchanged, `[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown:
  songs,albums,artists,playlists)` with no `files_sdcard` in it (C-21 — a dynamic shortcut missing `setActivity` would land
  here); browse the volume row, create a folder on it (`adb shell ls
  /storage/<UUID>` shows it); `sm unmount public:<x>,<y>` → the pane row disappears, `files_sdcard` is gone from `dumpsys
  shortcut` and the burst is back to 3, and, if the volume was open, the page shows "This storage was removed" (dump text),
  no crash (`logcat -d -s AndroidRuntime` empty of `app.tileshell`); restore `sm set-virtual-disk false`.
  ~~the root page shows `files_root:emulated` … a second root (`files_root:public`) whose name is the volume's~~
  SUPERSEDED 2026-09-23 by T18-8 (the ≡ pane, named by label).
- E3 **Listing and sort.** The QA-Files folder lists `a.txt`, `b.bin`, `sub`, the images, the video and the
  MP3 in 64-epx two-line rows with their type icons / thumbnails (dump `files_row:` order; `files_detail:sub` a date only,
  `files_detail:b.bin` "300 KB <date>" — size then date, r11/files.md 1.5.8); the sort line reads "Sort by: Name"
  (`files_sort`); sort by name / date / size each gives the order the fixtures' names, `touch -d` dates and `dd` sizes
  dictate (three dumps, exact order asserted), and the sort flyout offers exactly those three keys — no "Type" entry (T18-8;
  W10M had none). ~~sort by name / date / size / type … (four dumps, exact order asserted)~~ SUPERSEDED 2026-09-23 by T18-8.
  **Location bar:** open `sub` → `files_crumb:` texts "This Device", "QA-Files", "sub" in order; tap `files_crumb:1` →
  QA-Files listed; tap `files_up` → `/sdcard` listed; at This Device's root `files_up` is disabled (dimmed, a tap changes
  nothing).
- E4 **Operations** (W10M's verbs: select, then "Copy to" / "Move to" and pick the folder in Files' picker mode —
  T18-8). "Copy to" `sub` for `b.bin` → `adb shell md5sum` equal on both; "Move to" `sub` for `a.txt` →
  gone from the parent, present in `sub`, md5 unchanged; rename `b.bin` → `c.bin` (`ls`); new folder `n` →
  `ls -d /sdcard/QA-Files/n`; delete `c.bin` after the confirmation → it goes to the Recycle Bin (E4b's assertions); a
  conflict (copy `sub/b.bin` back over `b.bin`) offers replace / keep both / skip and each does what it says (`ls`, md5);
  copy `big.bin` → the progress notification is in `dumpsys notification`, `input keyevent KEYCODE_HOME`
  mid-copy leaves the service running (`dumpsys activity services app.tileshell` shows it), the copy completes
  with an equal md5; cancel mid-copy → no file and no `*.part` left in the destination (`ls`).
  ~~delete `c.bin` after the confirmation (Q3 A) → `ls` fails~~ SUPERSEDED 2026-09-23 by Q3 C (T18-7): the file goes to the bin.
- E4b **Recycle Bin (T18-1).** Delete `c.bin` → `ls /sdcard/QA-Files/c.bin` fails, `ls /sdcard/.Tessera/bin/` lists
  `<ms>-c.bin` with the md5 unchanged, `/sdcard/.Tessera/bin/.nomedia` exists, `.index.json` (`adb shell cat`) holds its
  original path, and `[files] bin delete /sdcard/QA-Files/c.bin: ok`; the Recycle Bin page (reached by `files_pane:bin`)
  lists `files_bin_row:c.bin` with its volume, and its `files_bin_note` text equals "Deleted files stay on this phone until
  you empty the Recycle Bin" (T18-10). Delete `qa-photo-0.png` from `DCIM/Camera` → within 3 s `content query --uri
  content://media/external/images/media --projection _display_name` no longer lists it, the Photos tile logs its refresh
  and the file does not come back after a second scan. Restore `c.bin` → back at `/sdcard/QA-Files/c.bin` with the same md5,
  the bin row gone; restore into a deleted folder (`sub/`'s file binned, then `adb shell rm -r sub`) → `sub/` recreated with
  the file; restore over an existing name → the conflict dialog, and replace / keep both / skip each does what it says
  (`ls`, md5). Delete permanently one row → gone from the bin folder and the index; Empty → the bin folder holds only
  `.nomedia` and `.index.json` with zero records; a delete made on the bin page is permanent. A delete on E2's public volume
  lands in THAT volume's bin (`ls /storage/<UUID>/.Tessera/bin/`), not the primary one. Negatives: `adb shell rm
  /sdcard/QA-Files/a.txt` never appears in the bin; the bin's files never appear in Recent (E14) or in a listing of
  `/sdcard` (dot-folders hidden). Full volume: `fill_volume 1048576` (its own assert: free ≤ 1 MB + 5 MB; C-27) and delete a
  file → it still moves to the bin (a rename needs no space; the `bin delete …: ok` line and `ls`), then `unfill_volume`.
  ~~fill with `fallocate` (phase 17's edge-case command) … restore by deleting the fill~~ SUPERSEDED 2026-09-23 by C-27.
  `adb shell rm -r /sdcard/.Tessera` →
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
  `content://` URIs from the shell's FileProvider (`dumpsys activity activities` intent line). The same on E2's public
  volume (a file under `/storage/<UUID>`) → one `content://` URI, the share starts. **Scope negative (T18-11):** the guard's
  JVM test (`./gradlew :app:testDebugUnitTest --tests '*FileShareGuard*'`; the row gates on gradle's exit code) feeds it the
  app's own `filesDir` file, `/storage/emulated/0/../../data/data/app.tileshell/files/x` (a `..` traversal) and a path whose
  canonical form is private → each refused with `[files] share refused: outside shared storage` (the test reads the
  diagnostics it wrote); `/storage/emulated/0/QA-Files/b.bin` and a `/storage/<UUID>/…` path → allowed — so a guard that
  refuses everything and one that allows everything both fail. No UI path can offer a private file, so the JVM test is the
  proof; the provider-scope adversarial review is the phase's GATE (build task 3).
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
- E11 **Geometry and motion against `r11/files.md`** (written 2026-09-23, T18-8). Dump bounds and screencap on the AVD
  (px ÷ 3 = epx on the 360-epx canvas); tolerances per R11: ± 1 epx for MEDIUM values (the desktop-1703-exact ones), ± 2 epx
  for LOW phone-downscale values, structure / order only for rows R11 read from a camera photo; colours ± 4 per channel.
  **Frame (1.1):** phase 01's drawn status bar (`BarMetrics.STATUS_EPX`, C-17) present; the location bar 48 ± 1 epx directly
  under it (its top = the status bar's bottom), fill (31,31,31); page background (0,0,0); the app bar 48 ± 1 epx, fill
  (31,31,31), directly on the nav bar. **Location bar (1.2):** ≡ centre x 24 ± 1; breadcrumb left 60 ± 2, cap 11 ± 1, white;
  "›" separators between segments; a path four levels deep (`/sdcard/QA-Files/sub/deep/deeper`) collapses its middle
  segments to "…" (structure); ↑ centre 24 ± 1 epx from the right edge, (123,123,123) ± 4 at a volume root (LOW, ± 2 on
  position). **Pane (1.3):** `files_pane` 256 ± 1 epx wide, fill (23,23,23), overlaying the page (the pixels right of it
  unchanged ± 2 against a screencap before opening — no scrim), rows 48 ± 1 epx from 72 ± 2 epx, glyph centre x 24 ± 1, label
  left 60 ± 1, the current row = 0.6 · accent + 0.4 · (23,23,23) ± 4; order Recent / This Device / volumes / Recycle Bin.
  **Sort line (1.4):** "Sort by:" left 12 ± 1 (ink ≈ 13.7), cap top 16 ± 1 below the bar, "Sort by:" (160,160,160) ± 4, the
  value white, a ChevronDown after it. **Rows (1.5):** pitch 64 ± 1 epx; icon left 20 ± 2, name left 72 ± 2; detail
  (165,165,165) ± 4. **Icons view (1.6):** three icons per row, column centres at W/3 intervals ± 2 epx (LOW), name centred
  under each icon. **App bar (1.7):** glyph centres Select / New folder / Icons / Search at 286 / 218 / 150 / 82 ± 1 epx from
  the right, More at 24 ± 1, glyph centre 24 ± 1 below the bar top; ••• expanded = 60 ± 2 epx with labels; overflow
  Refresh / Select all / Clear selection / Properties in that order (Clear selection dimmed with nothing selected, (137,137,
  137) ± 4). **Selection (1.8):** Select → `files_sort` reads "0 items selected", then "2 items selected" after two taps;
  the selected rows' fill equals the accent ± 4 full width; the bar reads Delete / Move to / Copy to / Share, Share dimmed
  once `sub` is in the selection; Back leaves selection mode (the sort line returns). **Hold menu (1.9):** on a file Delete /
  Move to / Copy to / Share / Rename / Properties, on `sub` the same without Share, item pitch 44 ± 2 epx, width 240.5 ± 2.
  **Properties (1.10):** a page, not a dialog — no app bar node, `files_crumb:` last segment = the item's name, rows "Date
  modified:" / "File type:" / "File size:" with values, and for `qa-steps.mp4` a "Video" section (structure). **Motion (Y5,
  UNMEASURED):** the `[motion] files_pane_open …` settle 250 ± 17 ms, `[motion] files_folder …` a cut then a 250 ± 17-ms
  fade, each with `maxGapMs` ≤ 33.4 (C-31) — [accept] values (H3), asserted so the build draws what H3 judges.
  ~~written once that section lands; not runnable before~~ SUPERSEDED 2026-09-23 by T18-8 (E11 is written and runnable).
- E12 **Diagnostics.** Coverage: grep the union of `qa/phase-18/*/ring-*.txt` from this build's run (the rows' APK id
  matching), each pattern at least once (C-20). Lines asserted by the rows above: `[files] access=<granted|denied>`, `[files] roots:
  <n> (<names>)`, `[files] volume mounted|unmounted <name>`, `[files] list <path>: <n> entries <ms> ms`,
  `[files] unreadable <path>`, `[files] copy|move <n> files <bytes> -> <dest> done|cancelled|failed <reason>`,
  `[files] no handler for <mime>`, `[files] search cancelled`, `[files] bin <delete|restore|purge|empty> <path>: ok | failed
  <why>`, `[files] bin index <volume>: <n> entries | rebuilt (<why>)`, `[files] zip open <path>: <n> entries`, `[files] zip
  open <path>: failed <why>` (T18-12), `[files] share refused: outside shared storage` (T18-11 — asserted by E7's JVM test,
  so it is the one line outside the ring union), `[files] open at <path> (from <caller>)` (T15-16, E17), `[files] zip
  extract <path> -> <dest>: done | cancelled | failed <reason>`, `[files] zip: refused entry <name>`, `[files] zip: refused
  (needs <bytes>, free <bytes>)`, `[files] zip: stopped at <bytes> (declared <bytes>)`, `[files] zip: encrypted <path>`,
  `[files] zip create <n> files -> <path>: done`, `[files] recent: <n>`, `[files] shortcut sdcard published | removed`, and
  every `[motion] <name> …` line a row times.
- E13 **Zip (T18-2).** Open `qa.zip` → a virtual root (`files_zip_root`) listing `one.txt` 1,024 bytes, `dir`, `ü-name.txt`
  (dump `files_row:` / `files_detail:` text), `[files] zip open …: 3 entries`; extract → `ls` and `md5sum` of each entry
  equal the recorded values, `ü-name.txt` named correctly; `qa-bad.zip` → `ok.txt` extracted and `[files] zip: refused entry
  ../../evil.txt` and `… /sdcard/abs.txt`, `ls /sdcard/evil.txt`, `ls /sdcard/QA-Files/evil.txt` and `ls /sdcard/abs.txt`
  all fail (and the control: `ok.txt` present — both directions); `qa-corrupt.zip` → "This zip can't be opened" (dump text),
  `[files] zip open /sdcard/QA-Files/zips/qa-corrupt.zip: failed <why>` in the slice from a MARK before the tap (T18-12), no
  crash (`AndroidRuntime` empty); `qa-enc.zip` → "This zip is password-protected" and `[files] zip: encrypted …`, nothing
  written; `qa-bomb.zip` → extraction stops, `[files] zip: stopped at <n> (declared 1024)` with n ≤ 1,024 + 1,048,576, and
  no temp folder or partial file remains (`ls`); `qa-huge.zip` with `fill_volume 2147483648` (free ≤ 2 GB + 5 MB, below the
  3 GB declared; C-27) → refused before any write with `[files] zip: refused (needs …, free …)`, then `unfill_volume`
  (~~filled … (`fallocate`) … restore the fill~~ SUPERSEDED 2026-09-23 by C-27); `qa-big.zip` extract → the progress
  notification in `dumpsys notification`, cancel mid-way → no output folder and no temp folder (`ls`); `qa-nested.zip` →
  `qa.zip` listed as a file that opens as a zip again. Create a zip from `a.txt` + `b.bin` → `adb pull` it, host `unzip -l`
  lists exactly those two names and `unzip -t` passes.
- E14 **Recent (T18-2).** After the Recent fixtures and the scan, the Recent view lists `r4.txt`, `r3.txt`, `r2.txt`,
  `r1.txt` in that order at the top (`files_recent_row:` order, exact); rename `r1.txt` in Files → it rises to the top
  within 3 s; a file written with `adb shell 'echo x > /sdcard/QA-Files/recent/unscanned.txt'` and no scan is absent, and a
  file in a `.nomedia` folder (`/sdcard/QA-Files/hidden/.nomedia` + a file + scan) is absent — both absences are the stated
  rule; no bin file appears (E4b); `[files] recent: <n>` with n ≤ 100.
- E15 **Wizard step added (phase 12 E14's three-part template; T18-3, C-4 b, C-15).** (a) `pm clear app.tileshell` →
  `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` → `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE
  default` → Home: `wizard_step:setup:files` present with its `wizard_why` equal to Decisions' line, its action starts
  `Settings$ManageExternalStorageActivity`, `wizard_progress` reads "Step 1 of 2" (the step and the presets page); `appops set
  … allow` and resume → the step gone and `wizard_presets` shows. (b) `pm clear` → `provision.sh` (marker written) → Home →
  no `wizard_page`, `[wizard] not shown: core held` (phase 12 E1 re-run on this build). (c) the finished-install rule: with
  the marker set (after (b)), `appops set … default` → Home → no `wizard_page`, `[wizard] not shown: finished`, the checklist
  row "Files" `missing`; `appops set … allow` (RV12). (Phase 12: every grant row is core; precedence "core held" before
  "finished".) ~~`pm clear app.tileshell` → `provision.sh` → `appops set … default` + Home → `wizard_step:setup:files` …;
  `appops set … allow` + Home → the step absent and the wizard not shown; then phase 12 E1 re-run on this build (C-4 c)~~
  SUPERSEDED 2026-09-23 by C-15 (provisioning writes the finished marker, so the step shows only with
  `PROVISION_FINISH_WIZARD=0`).
- E16 **App Shortcuts (phase 11 Q1's standing rule; C-8).** From the phase baseline (Files tile pinned): hold → labels
  "This device", "Recent", "Recycle Bin" in rank order (phase 11 E3's method), and the ring slice from a MARK before the hold
  holds `[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown: files_device,files_recent,files_bin)`
  (the activity-keyed line, T11-12 — it names only FilesActivity's ids);
  tap each → FilesActivity resumed on that page (This Device: `files_crumb:0` "This Device"; Recent: `files_recent_row:*` /
  its empty line; Recycle Bin: `files_bin` present); `dumpsys shortcut` lists `files_device`, `files_recent`, `files_bin` as
  manifest shortcuts with ranks 0, 1, 2; `am force-stop` + Home between holds (C-6). The SD card half is in E2.
- E17 **"Open file location" from Voice Recorder (T15-16).** An `adb push` of a host-made 3-s .m4a to
  `/sdcard/Recordings/qa-take.m4a` + the scan (or phase 15 E14's take on this build); open Voice Recorder, hold
  `rec_row:<its id>` → `rec_menu:location` present with the text "Open file location" → tap it: `dumpsys activity
  activities` topResumedActivity =
  `app.tileshell/.files.FilesActivity`, the last `files_crumb:` reads "Recordings", `files_row:qa-take.m4a` present, and
  the slice from a MARK before the tap holds `[files] open at /storage/emulated/0/Recordings (from recorder)`; Back returns to
  Voice Recorder; `am force-stop app.tileshell` + Home (C-6).

**Phone-only:**
- P1 One UI's "All files access" page from the checklist row (`am start -a
  android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION -d package:app.tileshell`, resumed activity recorded) and
  the grant surviving a reboot and a Device care optimise; the wizard step on a phone with the grant revoked.
- P2 A USB OTG drive: appears as a ≡-pane row named by its label, no drive letter (T18-8), browse / copy / pull mid-copy
  (E2's assertions on real hardware; the label text recorded with `record`);
  exFAT and FAT32 name rules (case-insensitive rename); a delete on it lands in the drive's own bin, and the drive pulled
  takes its bin rows away (E4b's assertions).
- P3 Samsung Camera's DCIM at thousands of items and the S25U's Download folder: list time and scroll per
  phase 01 P4's gfxinfo method.
- P4 Secure Folder and Private Space contents are absent (their storage is another user's), and the app says
  nothing about them rather than an error.
- P5 One UI's "Open with" sheet for `ACTION_VIEW` from Files (which apps it lists for a `.txt`, a `.pdf`). RECORDED
  (`lib.sh` `record`, C-26).

**NEEDS-HUMAN:** H1 *fidelity* — Files matches r11/files.md's MEDIUM values on the phone (the pane, location bar, sort
line, rows' pitch, app bar positions; E11; r11/files.md has no HIGH value); H2 *accept* — the Recycle Bin (P4 — W10M had
none, r11/files.md 1.12.8): its row in the ≡ pane, the bin page and its wording including the privacy line "Deleted files
stay on this phone until you empty the Recycle Bin" (T18-10), and the Delete permanently and Empty confirmations (Y1, Y6;
T18-1); ~~the delete rule (Q3) and its confirmation wording~~ SUPERSEDED 2026-09-23 by Q3 C (T18-7); H3 *accept* — motion
approximations (Y5, R11 §4's sources); H4 *accept* — the LOW / UNMEASURED values (Y1's default landing on This Device, Y2's
selection geometry and sort picker, Y3's Back rule) and the volume naming by label with no drive letter (T18-8); H5
*accept* — the progress / conflict / delete / rename / new-folder dialogs and the Move to / Copy to picker (Y4: R7 1.3.9's
W10M dialog form, UNMEASURED-2); H6 *accept* — the unreadable-folder wording for `/Android/data`; H7 *accept* — the zip
flows (P4 — W10M had no zip handling, r11/files.md 1.12.7): a zip opened as a folder, extract destination naming, the
"password-protected" / "can't be opened" / "storage removed" wording, create naming (T18-8); H8 *accept* — Recent as
"recently changed, newest first, 100 max" (W10M's was "recently accessed or downloaded", r11/files.md 1.12.9; T18-8).

## Edge cases
- 10,000 files in one folder: `adb shell 'cd /sdcard/QA-Big && for i in $(seq 1 10000); do : > f$i; done'`;
  the listing's first page draws in < 2 s (`[files] list … <ms> ms`) and sorting by size completes.
- Names: unicode, emoji, a 255-byte name, leading dots (hidden files shown or not per a Files setting, default
  off), a name that differs only by case on a FAT volume (E2's public volume is FAT: rename `a.txt` → `A.TXT`
  succeeds and lists once).
- A folder deleted underneath an open listing (`adb shell rm -r`) → the page shows "This folder is gone" and
  Back goes up; a file deleted between list and tap → the tap shows the error line.
- Back after a breadcrumb jump (T18-8, Y3; r11/files.md UNMEASURED-3): open `/sdcard/QA-Files/sub/deep`, tap
  `files_crumb:0` (This Device) → Back returns to `deep` (the folder left), not to its parent; ↑ from `deep` goes to `sub`;
  Back with selection mode on leaves selection mode first and stays in the folder.
- Copy onto a full volume (`fill_volume` as E4b, C-27) → the copy fails with "not enough space",
  the partial temp file is removed, the source untouched; `unfill_volume`. ~~(`fallocate` fill as phase 17's edge case) …
  restore by deleting the fill~~ SUPERSEDED 2026-09-23 by C-27.
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
  or fails cleanly; after a reboot (boot-completed poll, then `wake_device` asserting `Awake` before the next tap — C-25; the
  same after the `KEYCODE_SLEEP` screen-off) no temp files remain (`find /sdcard -name '*.part'` empty).
- The app list's hold menu on Files (Pin to Start present; Uninstall absent; E10) and Files pinned to Start
  (phase 02) opening on This Device (~~the root page~~ SUPERSEDED 2026-09-23 by T18-8).
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
