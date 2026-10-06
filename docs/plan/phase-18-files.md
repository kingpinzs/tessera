---
phase: 18
slug: files
status: FINAL   # FINAL 2026-10-06 14:08 (Jeremy: "(a)") after review round 3 (opus + opus) and Q-18-1 (a) / Q-18-2 (a); changes from here only through a dated INDEX Change Log entry. History: split 2026-09-22; interview DONE 2026-09-23; review triage round 1 applied 2026-09-23 (review/2026-09-23-phases11-19-triage.md); round 2 applied 2026-09-23 (review/2026-09-23-phases11-20-r2-triage.md); r11/files.md landed 2026-09-23 and is applied (T18-8; E11 written); round 3 applied 2026-10-06 (review/2026-10-06-phase18-r3-triage.md; r11/files-pass2.md applied); two questions asked and ruled 2026-10-06: Q-18-1 (a), Q-18-2 (a)
depends-on: [01, 02, 10, 11, 12, 13, 15, 17]   # C-23: 11 for the E2 / E16 bursts (per-activity shortcut query), 12 for the E15 template and C-15's provisioning marker; r3 D14: 13 for `record`'s first use (C-26), 15 for the Voice Recorder ADD (task 13, E17) and `fill_volume` (C-27)
---

# Phase 18 — W10M File Explorer ("Files")

## Goal
Windows 10 Mobile's File Explorer lives inside the shell APK as an app in the app list: it browses the phone's
shared storage and any removable volume as "This Device" / "SD card" the way W10M did, sorts and searches,
selects, copies, moves, renames, deletes into a shell-owned Recycle Bin, shares and makes folders, opens and makes
zips, shows ~~the phone's recently changed files~~ the files recently opened in Files (Recent; SUPERSEDED 2026-10-06 by
Q-18-1, Jeremy: "(a)"), and opens a file in the shell's own app when the shell has one (Photos' viewer, Movies & TV's
player, Music) and in Android's chooser otherwise.
It reads and writes by path under All-files access (`MANAGE_EXTERNAL_STORAGE`, Q1 A). Every visual value is from
r11/files.md as corrected by r11/files-pass2.md (its §4 governs; r3 D2) or a tagged approximation with a NEEDS-HUMAN
row. Nothing here reaches the internet — out: offline preferred (A11 as amended 2026-09-23); Jeremy can ask.

## Scope
**In:**
- Entry points in W10M's ≡ pane — there is no root page (r11/files.md 1.3; T18-8): Recent, This Device (the primary shared
  storage, `/storage/emulated/0`), one row per mounted public volume (`StorageManager.getStorageVolumes()`: an SD card or a
  USB OTG drive, named by its label, `StorageVolume.getDescription`, with no drive letter), appearing and disappearing with
  `ACTION_MEDIA_MOUNTED` / `ACTION_MEDIA_UNMOUNTED` (tracked in `ShellApp`, r3 D9), and the Recycle Bin as a fourth row (P4;
  Q2 C, Q3 C). ~~The app opens on This Device.~~ SUPERSEDED 2026-10-06 by r3 D2 / V6: a cold start shows the pane's first
  entry (Recent) with the pane OPEN and that row selected (r11/files-pass2.md §1 UNMEASURED-4, §4.4; two captures).
  ~~beside them the Recent view and the Recycle Bin (Q2 C, Q3 C — the functional set is ruled; r11/files.md
  settles how W10M drew its root page)~~ SUPERSEDED 2026-09-23 by T18-8 (R11: no root page, the ≡ pane).
- The folder page: the location bar (≡, breadcrumb, ↑), the "Sort by" line, and folder listing with W10M-style type icons
  (colour folder / page / type icons and thumbnails, A10 branding assets; r11/files.md 1.5.3); sort by name / date / size
  (W10M had no type sort, r11/files.md 1.4.5; T18-8); list and Icons views; search within the current tree;
  multi-select; "Move to" / "Copy to" (pick a folder; no cut / paste — W10M's verbs, r11/files.md 1.12.6) with progress,
  conflicts and cancel, surviving leaving the app (a foreground service, `FOREGROUND_SERVICE_DATA_SYNC`); rename; delete into
  the Recycle Bin (Q3 C; Decisions); new folder; share; a Properties page (name, size, date, path, per-type sections);
  open-with routing (Q4 A); a "cannot read" entry for `/Android/data` and `/Android/obb`
  (Android 11+ hides them from every app, All-files access included; which of two forms the entry takes is settled by build
  task 0's probe — r3 D13 / V9). No free-space line (W10M had none; phase 19's
  Storage page shows space — T18-8). ~~Folder listing with W10M glyphs by type; sort by name / date / size / type~~
  SUPERSEDED 2026-09-23 by T18-8.
- Zip (Q2 C): open a zip as a folder, extract (through the same foreground service), create one from a selection —
  `java.util.zip`, no new dependency (Decisions). A P4 addition: W10M's File Explorer had no zip handling
  (r11/files.md 1.12.7); H7 judges the flows. Entries inside a zip do not open or share ("Extract first" — r3 D6).
- Recent (Q2 C): ~~recently changed files across the phone from MediaStore's modified dates (Decisions); W10M's Recent was
  "recently accessed or downloaded" (r11/files.md 1.12.9), so the form is H8's call.~~ SUPERSEDED 2026-10-06 by Q-18-1
  (Jeremy: "(a)") and r3 D2 / V6: Recent lists the files OPENED in Files, newest first, 100 at most, kept in the app's own
  store (Android keeps no phone-wide recently-opened list); its form is pass 2's (date-only rows, no sort line, the bar
  Select · Icons · Search · •••, the hold menu Remove from recent · Share · Properties, the empty line "You haven't opened
  any files recently."; r11/files-pass2.md §1, §4.8). H8 judges it.
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
  (Decisions; honoured only for the shell's own launch — r3 D5; an audio file with no MediaStore row plays in Music too, by URI — Q-18-2, Jeremy: "(a)");
  a `FileProvider` for Share and for handing an unindexed image or video to the shell's viewer / player (a manifest ADD,
  not exported; it hands out and serves a URI only for a file under a `StorageVolume.getDirectory()` — T18-11, r3 D1, D10);
  static App Shortcuts plus the dynamic "SD card"
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
- 2026-10-06: Q-18-3 — the mid-copy rows get their window from a debug-only pacing switch (Jeremy: "(a)"), asked at the
  build's start because task 0 (e) contradicted this doc: the app copies at about 800-980 MB/s on `/sdcard` and 360-570
  MB/s onto the public volume (`qa/phase-18/BUILDSTART/records.tsv`), so no file that fits runs >= 15 s — `/sdcard`
  would need about 30 GB of a 16 GB volume and the 512 MB public volume fills in about 1 s. The catch stated to him: a
  test-only branch in product code; a release build ignores it.
- 2026-10-06 (agent, below Q-18-3): **the pacing switch.** `QaBases.FILES_RATE` = the pref `qa_files_rate_bps` in the
  `start_theme` prefs file, read through `QaBases.read` (phase 17's debug-only reader: a release build returns null
  without reading) and written by the drivers with `prefs_edit.py`. When set, the copy service's byte loop (copy, the
  copy half of a cross-volume move, zip extract and zip create — every loop that writes `[files] … progress`) sleeps so
  its average rate stays at or under that many bytes a second; nothing else changes — the same temp names, journal,
  progress lines, cancel and failure paths run. `FileOps` takes the pace as an injected function (r3 D7's shape), so the
  JVM tests run unpaced and one case proves a paced copy is byte-equal. The service logs `[files] qa pace <bps>` once per
  operation when the switch is on, so a paced run can never be read as an unpaced one. Rows that need "mid" set 8,388,608
  (8 MB/s) in `files_up` and clear it in `files_down`; every other row runs unpaced. Sizes: `big.bin` stays 200 MB
  (`count=200`) and `qa-big.zip` 200 MB — about 25 s each at 8 MB/s, on `/sdcard` and on the public volume alike (200 MB
  fits its 512 MB). Reason: the ruling names the form; a rate cap, not a per-chunk sleep, makes the run time a plain
  bytes / rate on both volumes.
- 2026-10-06: Q-18-2 — an audio file the music library does not list plays in Music anyway (Jeremy: "(a)"): Music gains
  a "play this one file" path for a file outside its library — the same player and Now Playing, and the file is not added
  to the library. Q4 A ("audio in Music") stays whole; no Android chooser and no second player for audio.
- 2026-10-06: Q-18-1 — Recent lists the files opened in Files (Jeremy: "(a)"): W10M's meaning ("You haven't opened any
  files recently.", r11/files-pass2.md §1 / §4.8), not "recently changed across the phone". Android keeps no phone-wide
  recently-opened list, so Recent knows only what was opened from Files (stated to him in the question).
- 2026-09-23: Interview Q4 — the shell's own app opens its types (Jeremy: "(a)"): images in Photos, video in the shared player,
  audio in Music (the play-one-track intent is an ADD to phase 10, INDEX Change Log when built); every other type goes to
  Android's chooser.
- 2026-09-23: The App Shortcuts under the phase 11 Q1 standing rule (agent; Jeremy can overrule): Files — This device, Recent,
  Recycle Bin, and SD card while one is inserted. [2026-09-23, C-9 / T18-6: "SD card" is a DYNAMIC shortcut — the agent line
  at the end of Decisions.] [2026-10-06, r3 V20: the first label is drawn "This Device", the pane row's casing; the
  shortcut ids are unchanged.]
- 2026-09-23: Interview Q3 — a shell-owned Recycle Bin for every file type (Jeremy: "(c)"), emptied only by the user. Agent
  mechanics: one bin folder per storage volume (so a delete is a rename on the same volume, instant, never a copy), with the
  original path recorded so Restore puts the file back where it was (a clash asks before overwriting); the bin shows in Files
  with Restore, Delete permanently and Empty; nothing is ever deleted from it automatically. A file deleted by another app never
  passes through it (the bin covers deletes made in Files).
- 2026-09-23: Interview Q2 — the full set plus zip plus Recent (Jeremy: "(c)"): browse, sort, search, select, copy / move /
  rename / delete, new folder, share, properties; zip (open a zip as a folder, extract, create one from a selection); and a
  Recent view ~~of recently changed files across the phone (read from MediaStore's modified dates, since Android keeps no
  system-wide "recently opened" list for other apps — agent note)~~. SUPERSEDED 2026-10-06 by Q-18-1 (the agent note only;
  Q2 C — a Recent view — stands): Recent lists the files opened in Files.
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
  mounted (`sm list-volumes`, 2026-09-22). Real USB OTG is a phone row. [2026-10-06, r3 D13 / V4: only `sm list-volumes`
  was ever run, so the virtual disk is a premise until build task 0 mounts one; if none mounts the build STOPS and asks.]
- 2026-09-22 (agent): **the `/Android/data` negative is a row, not a footnote.** `adb shell ls
  /sdcard/Android/data` lists `net.osmand.plus`, `org.fossify.messages`, `org.fossify.notes` on the AVD for the
  shell uid; ~~the app must show those entries as unreadable and say why (Android 11+), never crash, never hide
  them silently.~~ SUPERSEDED 2026-10-06 by r3 D13 / V9: that `ls` ran as the shell uid and proves nothing for the app's
  uid. Build task 0 lists the folder AS THE APP and the result picks the form (the r3 D13 entry below); either way the app
  says why (Android 11+), never crashes and never hides the folder silently.
- 2026-09-22 (agent): **open-with routing prefers the shell's own apps** (P2: fewer seams), by explicit
  component: ~~an image opens in phase 17's PhotosActivity viewer, a video in its VideoActivity~~ (SUPERSEDED 2026-10-06 by
  r3 D1 / V1: those are the library and the hub; an image opens in `photos.ViewerActivity`, a video in
  `video.PlayerActivity` — the r3 D1 entry below), an audio file in
  phase 10's Music. Music's activity declares only MAIN + APP_MUSIC (AndroidManifest.xml, 2026-09-22), so
  "play this file" is an **ADD to phase 10's part**: MusicActivity accepts a play intent for one MediaStore
  audio id (played through the existing MusicService session; nothing about the queue or the tile rule
  changes), recorded in the INDEX Change Log when built [2026-10-06, r3 D5: the play extra is honoured only for the shell's
  own launch; an audio file with NO MediaStore row plays in Music by URI — Q-18-2 (a), the entries at the top and end of Decisions]. Everything else goes
  to Android's resolver
  (`com.android.intentresolver`) with `ACTION_VIEW` and the file's MIME type; a type nothing handles shows
  "No app on this phone opens this". ~~Q4 asks whether this is the behaviour Jeremy wants.~~ Q4 ruled A 2026-09-23 (above).
- 2026-09-22 (agent): **writes are followed by a MediaStore scan** of every touched path, ~~because with
  All-files access a rename or move by path does not update MediaStore by itself~~ (SUPERSEDED 2026-09-23 by T18-4:
  shared storage is FUSE-backed on Android 11+ and MediaProvider follows renames and deletes of the files it indexes, so the
  scan is belt-and-braces; E9 records which case needed it); E9 proves the Photos tile and Music see a move within 3 s.
- 2026-09-22 (agent): **long operations run in a foreground service** (`FOREGROUND_SERVICE_DATA_SYNC`, a
  notification with progress and cancel), so a 2 GB copy survives Home and a screen-off; ~~the service dies with
  nothing half-written:~~ (SUPERSEDED 2026-10-06 by r3 D4: untrue for a process kill or a reboot — a temp can be left, so
  temps are named, journalled and swept; the r3 D4 entry below) a copy writes to a temporary name in the destination and
  renames on completion (LayoutStore's temp-and-rename shape). [2026-10-06, r3 D8: the permission, the `dataSync` service
  entry and `onTimeout` are in the r3 D8 entry below.]
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
  under `[files]` (E12). Drivers symlink qa/phase-03/scripts/lib.sh; evidence under qa/phase-18/ (kept on disk).
  [2026-10-06, r3 V7 / V3: the tags for progress, cancel, the dialogs, the menus, the selection bar, search and the picker,
  and the phase's own driver floor `p18.sh`, are in the r3 V7 and r3 V3 entries below.]
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
  default (edge cases), and a bin reached by browsing would invite edits that bypass the index. **Delete** = ~~a rename
  inside
  the same volume to `<bin>/<deleted-at ms>-<name>` (instant, never a copy, works on a full volume), the index write, then a
  scan of the source path~~ SUPERSEDED 2026-10-06 by r3 D3 (that name can overwrite a same-millisecond twin and can exceed
  255 bytes, and rename-before-index can leave an unindexed file): the index record written FIRST, then a rename inside the
  same volume to `<bin>/<deleted-at ms>-<seq>-<name truncated to fit 255 bytes>`, never onto an existing bin name (instant,
  never a copy; it needs no space for the file, only the index record's bytes — the r3 D3 entry below), then a scan of the
  source path, so MediaStore drops the row and Photos, Music and the Photos tile lose the file. Android's
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
  own rows (sizes from the central directory); ~~a nested zip inside it is a file that opens the same way.~~ (SUPERSEDED
  2026-10-06 by r3 D6: `ZipFile` needs a real file, so a nested zip is first copied to `<volume>/.Tessera/tmp/`, and no
  other entry opens or shares from inside a zip — the r3 D6 entry below.) **Extract** runs
  through the copy service (progress, cancel) into a temp folder beside the zip (named `.<zip>.<opid>.extract/`, r3 D4),
  renamed to its final name on completion, so a cancel or failure leaves no partial output (a kill or a reboot is covered
  by r3 D4's sweep). **Create** from a selection (DEFLATE, UTF-8 names, the same service).
  **Security:** every entry name is normalised and refused when its canonical path escapes the extract root (`../`, an
  absolute path) — `[files] zip: refused entry <name>` — while the other entries extract; the declared uncompressed total
  is summed before extracting and refused above the volume's free space − 50 MB; extraction stops, deletes its temp folder
  and fails cleanly once the bytes written pass the declared total + 1 MB (a zip bomb that lies about its sizes); an
  encrypted entry (bit 0 of the central directory's general-purpose flag, read by Files' own parser — r3 D6) → "This zip is
  password-protected" (unsupported, stated); names honour the UTF-8 flag with a CP437
  fallback; a removable volume pulled mid-extract → the service fails with "storage removed". ~~**Recent** = MediaStore's
  `Files` collection ordered by `DATE_MODIFIED` descending, capped at 100, excluding `.nomedia` folders and the bins; a Files
  write's scan makes the file appear within 3 s; a file no scan has reached is absent — a rule, stated, not a gap.~~
  SUPERSEDED 2026-10-06 by Q-18-1 (Jeremy: "(a)"): Recent is the list of files opened in Files, with no MediaStore query —
  the "below Q-18-1" entry at the end of Decisions. Tags
  `files_zip_root`, `files_extract`, `files_zip_create`, `files_recent_row:<name>`; diagnostics in E12. Reason: the ruling
  names the features; these picks are the platform-native form and the minimum that makes extraction safe. [2026-09-23, T18-8:
  both are P4 additions — W10M's File Explorer had no zip handling (r11/files.md 1.12.7) and its Recent was "recently accessed
  or downloaded" (1.12.9); zip's virtual root reuses the folder page unchanged; H7 judges the zip flows, H8 Recent's form.]
- 2026-09-23 (agent, review triage T18-6 / C-9): **"SD card" is a DYNAMIC App Shortcut** (`ShortcutManager
  .setDynamicShortcuts`), published on mount and removed on unmount; This device, Recent and Recycle Bin are static (ranks
  0–2), SD card ranks 3. Reason: a shortcut to a volume that is not there must not burst; a manifest shortcut cannot be
  withdrawn at run time (`ShortcutManager.disableShortcuts` throws for immutable shortcuts), and phase 11's selection rule puts
  manifest before dynamic, so a conditional screen ranks after the fixed ones by a stated choice. E2 proves both
  directions. [2026-10-06, r3 D9:
  the publisher lives in `ShellApp` and reconciles at process start — the r3 D9 entry below.]
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
  project's rule for access-control changes): a GATE recorded under qa/phase-18/ before `done`. [2026-10-06, r3 D10: the
  check is repeated where the provider SERVES (`openFile` / `query`), and the GATE list grows to five items — the r3 D10
  entry below.]
- 2026-09-23 (agent, r2 triage T18-8): **r11/files.md applied, with four calls.** (1) No root page: W10M's ≡ pane (256-epx
  overlay, #171717, 48-epx rows from 72 epx, glyph cx 24, label x 60, the selected row the accent at 60 % over #171717;
  r11/files.md 1.3) with rows Recent / This Device / one per mounted volume / Recycle Bin (a fourth row, P4, H2); ~~the
  app opens
  on This Device (R11 UNMEASURED-4)~~ (SUPERSEDED 2026-10-06 by r3 D2 / V6: that was the agent's stand-in for an
  UNMEASURED row, not a ruling; pass 2 measured the cold start on Recent with the pane open); `files_root:*` tags become
  `files_pane:*`. (2) Volumes are named by their label
  (`StorageVolume.getDescription`) with no drive letter — Android volumes have none, and "(D:)" would be pure imitation (H4).
  (3) Sort by name / date / size only — "type" is dropped (W10M had no type sort, r11/files.md 1.4.5, and nothing ruled one).
  (4) No free-space line in Files (W10M had none, 1.13.1; phase 19's Storage page shows space). The measured forms replace the
  stand-ins: 64-epx two-line rows with W10M-style colour type icons and thumbnails (A10 branding assets; MDL2 Folder E8B7 /
  Page E7C3 the monochrome fallback), the location bar with breadcrumb and ↑, the "Sort by: Name ⌄" line, the W10M app bar,
  selection bar and menus, the verbs "Move to" / "Copy to" (no cut / paste), Properties as a PAGE, the other dialogs from R7
  1.3.9's top-anchored W10M dialog, Back per the bars Decision above; zip and the Recycle Bin are P4 additions in full (H7, H2),
  ~~Recent's form is H8's~~ (SUPERSEDED 2026-10-06 by r3 D2 and Q-18-1: the form is measured, the meaning ruled); E11 is
  written now. Reason: R11 measured that W10M's File Explorer had no root page, zip, recycle bin
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
- 2026-10-06 (agent, r3 triage D1 / V1): **open-with reaches the viewer and the player, always with a `content://` URI.**
  An image → `app.tileshell/.photos.ViewerActivity`, a video → `app.tileshell/.video.PlayerActivity`, by explicit component,
  `ACTION_VIEW`; the URI is the MediaStore row's when a path query finds one, else the shell FileProvider's (same uid, so no
  grant is needed; `UriAccessRules.isOwnLaunch` makes it the shell's own launch). With no row (a `.nomedia` folder, an
  unscanned file, a volume MediaStore has not indexed) the viewer offers Share only (`ViewerRules.actions`, `hasRow=false`).
  A Delete made inside the viewer is Photos' own MediaStore delete and does NOT pass through the Recycle Bin (stated on H2).
  An entry inside a zip does not open (r3 D6). Reason: `PhotosActivity` / `VideoActivity` are the library and the hub
  (`AndroidManifest.xml:638`, `:744`); `ViewerRules.kt:63` shows nothing unless the scheme is `content`, and
  `PlayerRules.kt:256-260` refuses a shared-storage `file:` path. Holds under either answer to phase 17's D1 and D3: Files
  is the shell's own launch; D3 only moves which process's ring holds the viewer line.
- 2026-10-06 (agent, r3 triage D5): **the Music play extra is honoured only for the shell's own launch**
  (`getLaunchedFromUid() == myUid`; MusicActivity is `exported="true"`, `AndroidManifest.xml:143-146`); from any other
  caller the extra is ignored and Music opens as it always does, with `[music] play extra ignored: not the shell`. It
  carries one MediaStore audio id, or (Q-18-2) one FileProvider URI. An audio file with NO MediaStore row (a hidden folder, a folder marked no-media, an unscanned file): RULED 2026-10-06, Q-18-2 (a) — Music plays it by URI (the "below Q-18-2" entry at the end of Decisions). ~~Until it is ruled the build does not pick a form~~ SUPERSEDED 2026-10-06 by Q-18-2.
  Reason: an exported activity must not start playback for any app that names an id.
- 2026-10-06 (agent, r3 triage D2 / V6): **r11/files-pass2.md applied (its §4 governs over files.md).** (1) Cold start:
  the pane's first entry (Recent) with the pane OPEN and that row selected (§1 UNMEASURED-4, two captures). A warm return
  shows the page that was left; the shortcuts and the `page` / `path` extras open their own page with the pane closed.
  (2) Selection: a 20-epx checkbox at centre x 22, content shifted 32 (icon left 20 → 52, name left 72 → 104), "1 item
  selected" in the singular, all four bar buttons dim with nothing selected (two captures; equals R7 1.3.9). (3) The Move to
  / Copy to picker: the folder page with "Choose a folder" in the sort line's place, no checkboxes, the pane working inside
  it, and the app bar ✓ · ✕ · ••• at 150 / 82 / 24 epx from the right — no bottom confirm bar. (4) Move progress: a dark box
  "Moving files…" with an indeterminate dots row, directly under the status bar (top 24, about 66 tall, inset about 21),
  over a light wash covering the whole screen; on completion the app shows the destination folder. W10M had no percentage
  and no cancel: the percentage and Cancel live in the foreground notification as a stated P4 addition (H5). Copy progress
  was not captured: it takes the same box reading "Copying files…" and also ends on the destination folder (stand-in, H5).
  R7 1.3.9's dialog remains for conflict / delete / rename / new folder only. (5) Motion: Y5 as re-cut. (6) Icons view:
  two-line centred labels; row pitch 172 ± 8 for thumbnail folders (a camera source: structure). (7) Recent's form:
  date-only rows, no sort line, the bar Select · Icons · Search · ••• (no New folder; dim when empty). Reason: A4 (fidelity
  beats the stand-in) — every replaced value was a first-pass PROPOSAL, none a ruling.
- 2026-10-06 (agent, r3 triage D3): **a delete can never overwrite or half-happen.** Bin name `<deleted-at ms>-<seq>-<name
  truncated to fit 255 bytes>` (the extension kept; the full original name is in the index); the delete never renames onto
  an existing bin name (`seq` is bumped until the name is free, compared case-insensitively so it holds on FAT). Order: the
  index record is written first (temp-and-rename), then the rename, then the scan. A record whose file is absent is dropped
  on the next read (the existing rule); a bin file with no record lists by its bin name and restores to
  `<volume>/Download/Restored/`. If the index cannot be written (no space, the bin path unusable), the delete FAILS with
  `[files] bin delete <path>: failed <why>` and the file stays where it was. Reason: `rename(2)` replaces its target, so
  the old name lost one of two same-named files deleted in the same millisecond; Q3 C's own mechanics forbid that.
- 2026-10-06 (agent, r3 triage D4): **temps are named, journalled and swept.** Temp names are dot-hidden: `.<name>.<opid>
  .part` (a file) and `.<zip>.<opid>.extract/` (a folder), in the destination folder. Each running operation is journalled
  in the app's private files (`filesDir/files-ops.json`: the temp path and its volume UUID; written temp-and-rename, removed
  when the operation ends). The main process sweeps journalled temps at its first start after unlock and when a journalled
  volume mounts; a sweep deletes only paths the journal names and never touches `.Tessera/bin`. A grant revoked mid-copy
  fails `access removed`, and the temp is swept after the re-grant. Reason: a kill or a reboot ends the service with no
  chance to clean up, so "nothing half-written" has to be made true afterwards.
- 2026-10-06 (agent, r3 triage D6): **zip, the four things left to guess.** (a) Entries inside a zip do not open or share:
  a tap shows "Extract first" (as bin rows open nothing); a nested zip is the one exception — it is copied under r3 D4's
  temp rule into `<volume>/.Tessera/tmp/` (a `.nomedia` folder beside the bin, under the same entry-name, free-space and
  bomb guards) and removed on leaving its virtual root; r3 D4's sweep covers a leftover. (b) Encrypted = bit 0 of the
  central directory's general-purpose flag, read by Files' own parser (`java.util.zip.ZipEntry` exposes no flag). (c)
  Names: extract goes to `<zip base name>/` beside the zip; create writes `<the single item's name>.zip`, or `Archive.zip`
  for more than one item, in the current folder; "keep both" everywhere is `name (2).ext`, then `(3)`…. (d) A symlink entry
  extracts as a regular file holding the link text; it is never followed. Reason: the platform-native form that needs no
  private-file hand-off (T18-11's guard refuses `cacheDir`); H7 judges the names.
- 2026-10-06 (agent, r3 triage D7): **every write goes through `files/FileOps.kt`** (copy, move, rename, new folder, bin
  delete / restore / purge / empty, extract, create) over `java.io`, with the volume roots, the canonicaliser, the clock
  and the scanner port injected, and JVM tests on a temp dir (build task 14, E18). The share guard takes the roots and the
  canonicaliser the same way (`File.canonicalPath` on the host cannot see `/storage`; `PlayerRules.sourcePath` already
  takes `canonical:` so). Reason: the built standard of phases 16–17 (`media/MediaWrites.kt`, `people/PeopleWrites.kt`).
- 2026-10-06 (agent, r3 triage D8): **the foreground service for target 36.** The manifest gains
  `FOREGROUND_SERVICE_DATA_SYNC` and `<service … foregroundServiceType="dataSync" exported="false">` (today it holds
  `FOREGROUND_SERVICE` and no data-sync permission, `AndroidManifest.xml:21-22`). The service is started only from a
  visible FilesActivity. The platform limits how long a `dataSync` service may run and calls `Service.onTimeout(int, int)`
  when the limit is reached (the figure is the platform's and is not restated here): on `onTimeout` the operation ends
  `failed time limit`, its temps removed, the service stopped. Reason: a service that outlives its limit is killed.
- 2026-10-06 (agent, r3 triage D9 / V20): **volume tracking lives in `ShellApp`** (main process):
  `StorageManager.registerStorageVolumeCallback`, plus a reconcile of the pane rows and `files_sdcard` against
  `getStorageVolumes()` at every process start; a broadcast filter, if one is used as well, needs `addDataScheme("file")`.
  `setDynamicShortcuts` returning false logs `[files] shortcut sdcard failed (rate limit)`; with two removable volumes the
  shortcut targets the first. The static shortcuts are `res/xml/shortcuts_files.xml`, named by a `meta-data` on
  FilesActivity (the pattern at `AndroidManifest.xml:157`). Reason: a listener in FilesActivity publishes nothing for a
  card inserted before Files is opened, and leaves `files_sdcard` to burst for one pulled while the process was dead.
- 2026-10-06 (agent, r3 triage D10 / V18; a trust change): **the adversarial GATE list**, recorded under qa/phase-18/
  before `done`: (a) All-files access against every exported component — `UriAccessRulesTest`, `ViewerRulesTest`, the
  `PlayerRules` and `CaptureOutputGuard` tests and phase 17's refused-caller emulator drivers re-run on THIS build with the
  appop allowed, plus `tiles/api/ImageIngest.kt`'s authority rule (E19; it re-runs phase 17's drivers and does not touch
  phase 17's evidence or its open rows); (b) the FileProvider — its subclass repeats the canonical-path check in `openFile`
  and `query`, so a `root-path` provider serves nothing outside a `StorageVolume.getDirectory()` even to a grant holder
  (this also closes check-then-use); (c) zip extraction; (d) delete / purge / `empty(volume)`; (e) FilesActivity's `path`
  extra; (f) Music's play extra in its URI form (Q-18-2). Reason: phase 17's doc defers the All-files re-check to this phase in four places.
- 2026-10-06 (agent, r3 triage D11 / V5): ~~Recent's query filters (`MIME_TYPE IS NOT NULL`, no dot-segment, no `.nomedia`
  ancestor)~~ SUPERSEDED 2026-10-06 by Q-18-1: Recent runs no MediaStore query, so the filters are not applied. What stays
  from D11 / V5: build task 0 records whether a file written through FUSE with no scan gets a MediaStore row — E9's
  RECORDED clause and T18-4's "belt-and-braces" claim still rest on it. Reason: the ruling removed the query's subject.
- 2026-10-06 (agent, r3 triage D12): **FilesActivity's launch form.** `launchMode="singleTask"`, `taskAffinity
  ="app.tileshell.files"` (as RecorderActivity, `AndroidManifest.xml:451-457`); ONE activity for every page — the picker is
  a page of it, so L14-2 (`start/BackHistory.kt:39-40`) counts one catalog class. The `page` / `path` extras are read in
  `onCreate` and `onNewIntent`, only choose what is shown, are ignored (with `[files] open ignored: <why>`) unless the path
  is canonical under a mounted volume, and RESET the in-app history. Back in the picker walks the picker's own history,
  then acts as ✕. Reason: without the reset, "Back returns to Voice Recorder" (E17) fails when Files was already open.
- 2026-10-06 (agent, r3 triage D13 / V4 / V9 / V10): **four premises become build task 0's probes, and the doc holds for
  either outcome.** (a) The public volume: if none mounts, STOP and ask Jeremy. (b) `/Android/data` listed as the app: if
  names come back, E5 asserts each other package's folder as an unreadable entry; if null or only the shell's own folder,
  the `Android/data` (and `obb`) entry ITSELF carries the line "Android doesn't let apps see other apps' folders here". (c)
  The pid across an appop revoke: if the process is killed, rows take their MARK after the revoke and the mid-session edge
  case reads "revoked during a copy → no temp after restart"; if it survives, the app re-checks on every resume and before
  each operation. (d) FUSE row-on-create (r3 D11). (e) Copy throughput, which sizes `big.bin`. Reason: each was read as the
  shell uid or recalled, not observed for the app on this image.
- 2026-10-06 (agent, r3 triage V7 / V8): **tags and lines the drivers need.** App bar `files_bar:<select|new_folder|view
  |search|more>`, overflow `files_more:<refresh|select_all|clear|properties>`; sort flyout items
  `files_sort_item:<name|date|size>`; selection bar `files_sel:<delete|move|copy|share>`; the hold menu
  `files_hold:<delete|move|copy|share|rename|properties|remove_recent>`; the progress box `files_progress` (its text
  `files_progress_text`); dialogs `files_dialog`, `files_dialog_title`, `files_dialog_input` (rename, new folder),
  `files_dialog:<ok|cancel|replace|keep_both|skip>`; search `files_search_box`, `files_search_progress`,
  `files_search_cancel`, `files_search_empty`; the picker `files_pick_title`, `files_pick_ok`, `files_pick_cancel`; the
  error and state lines `files_error`, `files_unreadable`, `files_ungranted`, `files_grant_link`. Cancel of a copy / move /
  extract is the notification's "Cancel" action (P4, r3 D2). The service writes `[files] copy progress <bytes>/<total>`
  (also `move` / `zip extract`) at least once a second; **"mid" in every row means: a progress line with 0 < bytes < total
  was read from the MARK before the row acts.** Reason: a 200 MB copy can finish before a dump returns.
- 2026-10-06 (agent, r3 triage V2 / V3 / V16 / V11–V15 / V17): **the phase's driver floor.** `qa/phase-18/scripts/p18.sh`
  (sourced after `lib.sh`): `pubvol_up` (makes the virtual disk, asserts `mounted`, prints the UUID) / `pubvol_down` (in an
  EXIT trap); `files_up` (makes the row's fixtures, then snapshots `find /sdcard -not -path '*/Android/*' | sort` and the
  `files` collection's paths) / `files_down` (removes them and asserts both snapshots equal — RV12 specified, not only
  asserted); copies of `absent_in`, `c6`, `gdump`, `top_activity` (phase 17's copy rule, `p17.sh:8-11`). Every row and edge
  case makes and restores its OWN fixtures and volume, so any row re-runs alone. The share line is `[files] share <n> files
  type=<mime> uris=<list>`; `testapps/qa-capture`'s send probe takes `SEND_MULTIPLE */*` and logs each URI with the md5 of
  the stream it read. E12 takes phase 17's form (`qa/phase-18/E12/producers.tsv`, `notrun.tsv`); the edge cases are indexed
  in `qa/phase-18/scripts/edge_index.tsv` and run by E20. Reason: the owner's rule that a row is re-run alone, and a
  provider handing out unreadable URIs must not pass.
- 2026-10-06 (agent, r3 triage V19): **phone rows are things Jeremy does and reads on the phone** — no `am start`, no PC,
  no USB, no adb; P1–P5 are re-cut below. Reason: his phone-only rule.
- 2026-10-06 (agent, r3 triage D14 / V20): **cites corrected.** The manifest's providers are at
  `AndroidManifest.xml:246-250` and `:345-348`; MusicActivity at `:143-158`; `ring_mark` / `ring_since` / `record` /
  `fill_volume` / `unfill_volume` / `wake_device` EXIST in `qa/phase-03/scripts/lib.sh` (`:182`, `:188`, `:427`, `:439`,
  `:459`, `:51`); E10 reads exported components with `qa/phase-03/scripts/exported.py`. Reason: line numbers moved since
  2026-09-23.
- 2026-10-06 (agent, below Q-18-1): **Recent's mechanics.** An open = a file handed to any opener from Files (the shell's
  viewer / player / Music, or Android's chooser once the user picks an app — Files learns of the pick from the chooser's
  own callback, `Intent.createChooser(intent, title, IntentSender)` with `EXTRA_CHOSEN_COMPONENT` delivered to a
  non-exported receiver; a chooser dismissed with no pick adds nothing; lead, 2026-10-06); a folder, a zip browsed as a folder and a file
  merely selected do not count. The list lives in the app's private store (`filesDir/files-recent.json`: path, volume UUID,
  opened-at; written temp-and-rename), newest first, 100 at most, one entry per path (re-opening moves it to the top). A
  rename or move made in Files updates the entry's path; a file deleted in Files (to the bin) or found gone on read drops
  its entry; entries on an unmounted volume are hidden while it is unmounted. The hold menu is W10M's Remove from recent ·
  Share · Properties (Remove deletes the entry, never the file). Empty state: W10M's string "You haven't opened any files
  recently." in the measured position (grey (160,160,160), left 14.5 epx, cap top 88.8 — the sort line's place). Form: pass
  2's — date-only rows, no sort line, the bar Select · Icons · Search · •••; the row's date is the OPENED-AT date, a tagged
  approximation (what W10M's date meant is unmeasured; H8). Diagnostics `[files] recent: <n>`, `[files] recent add|remove
  <path>`; tags `files_recent_row:<name>`, `files_recent_empty`, `files_hold:remove_recent`. No MediaStore query and no
  ContentObserver. Reason: the ruling names the meaning; these are the smallest mechanics that keep the list true to the
  files (a path that moved, a file that is gone) and can never delete a file from the Recent page.
- 2026-10-06 (agent, below Q-18-2; an ADD to phase 10's part and a trust change): **Music plays one file by URI.** The
  play extra carries either one MediaStore audio id (a library track) or one `content://` URI of the shell's FileProvider
  (a file with no library row). Both are honoured only for the shell's own launch (`getLaunchedFromUid() == myUid`, r3 D5),
  and the URI form only when its authority is the shell's FileProvider — any other URI is ignored with `[music] play extra
  ignored: <why>` (a pure rule with a JVM test, `MusicPlayExtra`). MusicService plays the file as a one-item queue through
  its existing session, notification and Now Playing: the title is the file's tag title, else its name without the
  extension; artist and album from its tags, else blank; art from its tags, else Music's placeholder. Nothing is written:
  the file is not scanned into MediaStore, not added to a playlist, and no library pivot lists it. The Music tile and the
  lock screen show it as any playing track (they read the session). When it ends, playback stops; the queue that was
  playing before is replaced, as tapping a song in the library replaces it. A file that vanishes mid-play (deleted, its
  volume pulled) stops with Music's existing error state. Line `[music] play file <path> (not in library)`. Files chooses
  the form: a path query that finds an audio row sends the id, otherwise the URI. The URI rule joins the adversarial GATE
  list as (f). Recorded in the INDEX Change Log for phase 10's part when built. Reason: the ruling names the behaviour;
  one player and one session (P2), and a file outside the library stays outside it — Music's library is MediaStore's, and
  a `.nomedia` folder is the user's own statement that its contents are not library media.

### Approximations (re-cut 2026-09-23 against r11/files.md, T18-8; re-cut 2026-10-06 against r11/files-pass2.md, r3 D2 /
V6; each has an H-row)
| # | Value | Status | Stand-in | H-row |
|---|---|---|---|---|
| Y1 | The ≡ pane (Recent / This Device / volumes / Recycle Bin) and the cold-start landing | MEASURED (r11/files.md 1.3, MEDIUM / LOW; width re-read 254.5 / 255.7, 256 stands — pass 2 §1) — closed. The landing is MEASURED (pass 2 §1 UNMEASURED-4, two captures → MEDIUM candidate): the pane's first entry, Recent, with the pane open. Only the Recycle Bin row (P4, no W10M bin) remains an approximation. ~~only the Recycle Bin row … and the default landing entry (UNMEASURED-4: This Device) remain approximations~~ SUPERSEDED 2026-10-06 by r3 D2 / V6. ~~Root page layout … free-space line; two-line rows at the R3 C1 64-epx pitch with a 30-epx glyph~~ SUPERSEDED 2026-09-23 by T18-8 (no root page, no free-space line) | the bin row in the pane's row form with the Delete glyph (E74D). ~~the app opens on This Device~~ SUPERSEDED 2026-10-06 by r3 D2 | H2 (bin row), H1 (landing) |
| Y2 | Folder rows, type icons, the location bar, the sort line, the selection check, the app bars and menus (also a zip's virtual root and Recent's rows) | MEASURED (r11/files.md 1.2, 1.4–1.9; MEDIUM / LOW). Selection geometry is MEASURED (pass 2 §1 UNMEASURED-7, two captures → MEDIUM candidate): a 20-epx checkbox at centre x 22, content shifted 32 (icon left 52, name left 104), accent row fill, "1 item selected", all four bar buttons dim at 0 selected. Icons view with thumbnails: two-line centred labels, row pitch 172 ± 8 (pass 2 §4.6, a camera: structure). Recent: date-only rows, no sort line, the bar Select · Icons · Search · ••• (pass 2 §4.8, LOW). ~~the selection geometry is UNMEASURED-7~~ SUPERSEDED 2026-10-06 by r3 D2 / V6 | the sort picker only: a 242.6-epx flyout with 44-epx items (R7 2.2.5, UNMEASURED-6; no frame in pass 2), three keys, folders before files, Name A → Z / Date newest first / Size largest first, ties by name (r3 V13). ~~selection: R7 1.3.9 (20.3-epx checkbox at x 22.2, content shifted 32 epx, accent row fill)~~ SUPERSEDED 2026-10-06 by r3 D2 (now measured, and equal). ~~app-list rows (R3 C2) with a 48-epx app bar~~ SUPERSEDED 2026-09-23 by T18-8 | H1 (measured, selection included), H4 (the sort picker and its order rules) |
| Y3 | Back behaviour | PARTLY documented (r11/files.md 1.8.1, 1.12.3–1.12.4; UNMEASURED-3 — pass 2 §3 #3 leaves history-versus-parent undecided: every return seen was a straight descent) | Back leaves selection mode; otherwise the previous location in history (= up one level on a straight descent; after a breadcrumb jump, the folder left); leaves the app when the history is empty; a `page` / `path` launch resets the history; in the picker Back walks the picker's own history, then acts as ✕ (r3 D12) | H4 |
| Y4 | The Move to / Copy to picker; move and copy progress; the conflict, delete-confirmation, rename and new-folder dialogs | The picker and MOVE progress are MEASURED (pass 2 §1 UNMEASURED-2, LOW, a camera): "Choose a folder" in the sort line's place, no checkboxes, the pane works inside it, the app bar ✓ · ✕ · ••• at 150 / 82 / 24 from the right; progress = a dark box "Moving files…" with indeterminate dots under the status bar (top 24, about 66 tall, inset about 21) over a light wash, ending on the destination folder; no percentage, no cancel. Still UNMEASURED (pass 2 §3 #2): COPY progress and the four dialogs. Properties is MEASURED as a page (1.10) — ~~properties dialog~~ struck 2026-09-23 by T18-8 | copy progress = the move box reading "Copying files…"; the percentage and Cancel in the foreground notification (P4 — W10M had neither); the wash drawn as white at 20 % (the camera's colour is not reliable); conflict / delete / rename / new folder in R7 1.3.9's top-anchored W10M dialog (full width, (74,74,74), title / body / two side-by-side buttons). ~~the picker = the folder page in a picker mode with a bottom confirm bar in the app-bar geometry (1.7)~~ SUPERSEDED 2026-10-06 by r3 D2 / V6. ~~P4 design in phase 03's card idiom (R6 §3.4.2)~~ SUPERSEDED 2026-09-23 by T18-8 | H5 |
| Y5 | Motion: pane open / close, folder change, list ↔ icons, ••• expand, hold menu, selection mode | PARTLY MEASURED (pass 2 §5; LOW video, tolerances = the source's frame bound, ± 33 ms unless stated — r3 V6): pane close; folder change / ↑ / breadcrumb; list ↔ icons; selection ENTER; the hold menu. Still UNMEASURED: ••• expand, the sort picker's opening, LEAVING selection, and pane open at 360 epx (measured only on the 432-epx rail form). Timed by the `[motion]` clock (C-5) | MEASURED forms: pane close a one-frame cut to an empty list, the rows then entering as a folder change; folder change / ↑ / breadcrumb a cut to an EMPTY list, then rows fade and slide up 7 epx over 300 ± 33 ms, decelerating (names first, detail lines + 100 ms, icons + 130 ms); list ↔ icons the same reload form (cut to empty, labels first); selection enter a 200 ± 40-ms ease-out slide (content right 32, the checkbox column in from the left); hold menu first frame 700 ± 33 ms after the press, the box from 76 % of its height, settled 233–367 ms later. Stand-ins: pane open a right-edge reveal with static labels, strong ease-out, 250–283 ms (the rail form's curve at 360 epx); ••• expand 317 ms (R7 2.1.16); leaving selection = the enter reversed, 200 ms; the sort picker's opening as R7 2.2.6's menu. ~~pane open 250 ms ease-out (R7 3.1.10); pane close one frame, then the page fades in; folder change / ↑ / breadcrumb: a cut, then a 250-ms ease-out fade (R7 3.2.2); hold menu box 200–233 ms (R7 2.2.6); selection mode and list ↔ icons a one-frame cut~~ SUPERSEDED 2026-10-06 by r3 D2 / V6 | H3 |
| Y6 | The Recycle Bin page (rows with volume and deleted-at, the privacy line, Restore / Delete permanently / Empty) and its confirmations; the Recent row's date | no W10M original for the bin (P4); what W10M's Recent date meant is unmeasured | folder rows (Y2) with the date as the detail line; a bin file with no index record lists by its bin name (r3 D3); confirmations in Y4's dialog form; Recent's date = the opened-at date (below Q-18-1) | H2 (bin), H8 (Recent's date) |

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
5. ~~Q-18-1 — what "Recent" means~~ RULED 2026-10-06: (a), files opened in Files (see Decisions) — asked 2026-10-06, see
   review/2026-10-06-phase18-r3-triage.md.
6. ~~Q-18-2 — an audio file with no MediaStore row~~ RULED 2026-10-06: (a), Music plays it anyway (see Decisions) —
   asked 2026-10-06, see review/2026-10-06-phase18-r3-triage.md.

## Build tasks
0. **The AVD probes, before any code** (r3 D13 / V4 / V9 / V10 / V5c / V7; each output saved with `lib.sh` `record` under
   `qa/phase-18/BUILDSTART/`, kept on disk). A throwaway debug probe in the app's uid is allowed for (b)–(d) and is removed
   before task 1. (a) **The public volume:** `sm set-virtual-disk true`, `sm list-disks`, `sm partition disk:<id> public`,
   `sm list-volumes`; record each output, the volume's filesystem and size, that `getStorageVolumes()` lists it, its
   `StorageVolume.getDescription` text (the platform label E2 compares with) and whether `adb shell ls /storage/<UUID>`
   works for the shell uid; then `sm set-virtual-disk false`. **If no public volume mounts: STOP and ask Jeremy** — the
   removable legs of E2, E4b, E7 and the FAT / unmount edge cases have no other AVD form, are not dropped and do not move
   to the phone rows silently. (b) **`/Android/data` as the app:** `run-as app.tileshell ls /sdcard/Android/data` and
   `File("/storage/emulated/0/Android/data").list()` from the app; the result picks E5's form (r3 D13 in Decisions). (c)
   **The appop revoke:** the app's pid before and after `appops set app.tileshell MANAGE_EXTERNAL_STORAGE default`; the
   result picks the wording of E1, E15 (c) and the mid-session edge case. (d) **FUSE row-on-create:** `adb shell 'echo x >
   /sdcard/QA-Files/probe.txt'` with no scan, then `content query` on the files collection after 3 s — a row or none,
   recorded (E9, T18-4). (e) **Copy throughput:** time a 200 MB copy on `/sdcard` and onto the public volume; `big.bin`
   and `qa-big.zip` are then sized so each copy or extract runs ≥ 15 s, and the sizes are written into the Fixtures
   paragraph (the virtual disk's size from (a) bounds the public-volume leg). Anything here that contradicts this doc comes
   back to Jeremy as a question, not as a silent re-cut (the review cap is reached).
1. **App identity and grant.** `FilesActivity` (LAUNCHER + APP_FILES; `launchMode="singleTask"`, `taskAffinity
   ="app.tileshell.files"`, one activity for every page, the `page` / `path` extras read in `onCreate` and `onNewIntent` —
   r3 D12) in the shell APK with `testTagsAsResourceId`; `MANAGE_EXTERNAL_STORAGE` in the manifest (Q1, ruled A);
   `FOREGROUND_SERVICE_DATA_SYNC` and the copy service's `<service foregroundServiceType="dataSync" exported="false">`,
   started only from a visible FilesActivity, its `onTimeout` ending the operation `failed time limit` with temps removed
   (r3 D8); Setup checklist row "Files"
   (`Environment.isExternalStorageManager()`), whose tap opens `MANAGE_ALL_FILES_ACCESS_PERMISSION`; the same row as a
   setup-wizard step of phase 12's Settings-page kind (`wizard_step:setup:files`, its `wizard_why` line in Decisions —
   T18-3); the ungranted state in the app names the checklist and offers the same link; the exported allow-list ADD;
   `qa/phase-03/scripts/provision.sh` gains `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow` (C-4 a).
2. **The ≡ pane, the folder page and listing** (T18-8, r11/files.md 1.1–1.7, 1.11; r11/files-pass2.md). Volumes from
   `StorageManager`, tracked in `ShellApp` (`registerStorageVolumeCallback`, reconciled against `getStorageVolumes()` at
   process start — r3 D9; ~~mount / unmount broadcasts~~ SUPERSEDED 2026-10-06 by r3 D9 as the primary source); the ≡ pane
   (`files_pane`: Recent / This Device / one row per volume named by `StorageVolume.getDescription`, no drive letter /
   Recycle Bin; ~~opens on This Device~~ SUPERSEDED 2026-10-06 by r3 D2: a cold start shows Recent with the pane open and
   `files_pane:recent` selected, the 360-epx overlay form with no compact rail) ~~the Recent and Recycle Bin entries on
   the root page~~ (SUPERSEDED 2026-09-23 by T18-8); the location bar (≡, breadcrumb with collapsed middle segments and
   tappable segments, ↑ dimmed at a volume root); the "Sort by: Name ⌄" line and its flyout; folder listing by path in 64-epx
   two-line rows with W10M-style type icons / thumbnails (A10 assets; MDL2 Folder E8B7 / Page E7C3 as the fallback) and the
   detail line ("date" for a folder, "size date" for a file; size to 3 significant figures, the phone's short date —
   r11/files.md 1.5.8); the Icons view (two-line centred labels; row pitch 172 ± 8 for thumbnail folders — r3 D2); sort by
   name / date / size (~~/ type~~ struck 2026-09-23 by T18-8; folders before files, Name A → Z, Date newest first, Size
   largest first, ties by name — r3 V13, Y2); the motion of Y5 as re-cut; the tags of r3 V7; search within the tree (a
   background walk with a cancellable progress line; the sort line reads
   "Sort by: Relevance" while searching); the app bar Select / New folder / Icons↔List / Search / More and its overflow
   (Refresh / Select all / Clear selection / Properties); no free-space line; the unreadable `/Android/data` and
   `/Android/obb` entries in the form task 0 (b) picks (r3 D13); paging so a folder of 10,000 entries lists without
   stalling.
3. **Selection and operations.** Selection mode ("<n> items selected" — "1 item selected" in the singular — in the sort
   line's place, the 20-epx checkbox column and the 32-epx content shift, accent-filled rows, the bar Delete / Move to /
   Copy to / Share, all four dim with nothing selected, Share dim with a folder selected; Back leaves it — r3 D2) and the
   tap-and-hold flyout (Delete / Move to / Copy to / Share / Rename / Properties; a folder has no Share); "Move to" /
   "Copy to" (Files itself in a picker mode: "Choose a folder" in the sort line's place, no checkboxes, the app bar ✓ · ✕ ·
   •••, Back walking the picker's history and then acting as ✕ — r3 D2, D12) through the foreground service with progress
   (the top "Moving files…" / "Copying files…" box over a wash, ending on the destination folder; the percentage and Cancel
   in the notification, P4; `[files] copy progress <bytes>/<total>` — r3 D2, V7),
   conflict resolution (replace / keep both = `name (2).ext` / skip), cancel with no partial file; the temp names, the
   operations journal and the sweep (r3 D4); rename; delete into the Recycle Bin
   (task 8); new folder; share (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` with content URIs from a `FileProvider`, logged as
   `[files] share <n> files type=<mime> uris=<list>` (r3 V2) — a
   manifest ADD, none exists today: the manifest's providers are `LiveTileProvider`
   (`app/src/main/AndroidManifest.xml:246-250`
   ~~`:162`~~) and `KeyboardConfigProvider` (`:345-348` ~~`:261`~~; cites corrected 2026-10-06, r3 D14); it is
   `exported="false"` with `grantUriPermissions="true"`, so it adds nothing to
   the exported-components allow-list — T18-5); **the provider's scope guard (T18-11):** `getUriForFile` only after the
   canonical-path check that the file lies under a `StorageVolume.getDirectory()`, else `[files] share refused: outside shared
   storage`, and the FileProvider subclass repeats the check in `openFile` / `query` (r3 D10) — a JVM test covers it (the
   roots and the canonicaliser injected, r3 D7), and the adversarial review is a GATE recorded under qa/phase-18/
   before `done` over r3 D10's five-item list (~~the adversarial review of the provider scope~~ SUPERSEDED 2026-10-06 by r3
   D10: the provider is one item of five); the Properties PAGE (breadcrumb ending in the name, thumbnail + name,
   grey-label / white-value rows,
   per-type sections, no app bar; r11/files.md 1.10); the dialogs in R7 1.3.9's form (Y4); every write followed by a
   MediaStore scan.
4. **Open-with routing** (Q4 A), including the phase 10 ADD (a play intent on MusicActivity, honoured only when
   `getLaunchedFromUid() == myUid` — r3 D5) and phase 17's explicit components: `photos.ViewerActivity` for an image,
   `video.PlayerActivity` for a video, `ACTION_VIEW` with a `content://` URI (the MediaStore row's when a path query finds
   one, else the shell FileProvider's — r3 D1, which supersedes the `PhotosActivity` / `VideoActivity` reading of this
   task). Every hand-off to an opener adds the file to Recent (below Q-18-1). **An audio file with no MediaStore row
   (Q-18-2 (a)):** the same play extra carries the shell FileProvider's `content://` URI instead of an id, and Music plays
   that one file through MusicService's session and Now Playing, outside its library ("below Q-18-2" in Decisions) — the
   second half of the phase 10 ADD, recorded in the INDEX Change Log when built.
5. **Diagnostics and states.** `[files]` lines for every silent-empty state; error states for a vanished
   folder, a pulled volume, a full volume, a denied grant (re-checked on every resume and before each operation; whether
   a revoke also kills the process is task 0 (c)'s record — r3 V10).
6. **Regressions.** App-list regression, exported allow-list, APK size, the phase 17 / phase 10 hand-off
   rows, phase 12 E1 on this build (C-4 c), the baseline assertion (E10).
7. **R11 values applied** (r11/files.md landed 2026-09-23; T18-8). The Y rows are re-cut in the Approximations table and
   E11 is written below against r11/files.md 1.1–1.10, so R11 no longer holds the doc from FINAL (RV9); the build draws those
   values and E11 proves them. ~~Once `r11/files.md` lands, the Y rows it measures are replaced and E11 is written~~ SUPERSEDED 2026-09-23
   by T18-8.
8. **Recycle Bin** (T18-1, Decisions): the per-volume `.Tessera/bin/` with `.nomedia` and `.index.json`; delete as ~~a
   same-volume rename + index write + scan~~ (SUPERSEDED 2026-10-06 by r3 D3) the index record first, then a same-volume
   rename to `<ms>-<seq>-<name ≤ 255 bytes>` that never lands on an existing bin name, then the scan; a delete whose index
   write fails leaves the file and logs `bin delete …: failed <why>`; an unindexed bin file lists by bin name; the
   pane's fourth row and the bin page (Y6) with its privacy line (T18-10);
   Restore with the conflict dialog and
   the recreated folder; Delete permanently and Empty with confirmations; the lost-index and gone-file rules; the
   `files_bin*` tags and `[files] bin …` lines; the bin index reader (per volume: entry count and bytes) and the
   `empty(volume)` call phase 19's Storage row uses (T18-9). ~~the one-line ADD to phase 19's Storage page (the bin's size
   with Empty)~~ SUPERSEDED 2026-09-23 by T18-9 (phase 19 builds the row).
9. **Zip** (T18-2, Decisions): the virtual root over `ZipFile`; extract and create through the copy service; the
   entry-name, free-space and bomb guards; the password-protected (bit 0 of the central directory's flags, Files' own
   parser) and corrupt states; "Extract first" for an entry tapped inside a zip; the nested zip's copy in
   `<volume>/.Tessera/tmp/`; the names (extract to `<zip base name>/`, create `<item>.zip` or `Archive.zip`); a symlink
   entry as a regular file (r3 D6); `[files] zip …` lines.
10. **Recent** (Q-18-1; "below Q-18-1" in Decisions): the opened-files list in the app's private store
    (`filesDir/files-recent.json`, temp-and-rename; newest first, cap 100, one entry per path), written at every hand-off to
    an opener (task 4), kept in step by Files' own rename / move / delete, entries dropped when the file is gone on read
    and hidden while their volume is unmounted; the page in pass 2's form (date-only rows with the opened-at date, no sort
    line, the bar Select · Icons · Search · •••, the empty line `files_recent_empty`); the hold menu Remove from recent ·
    Share · Properties. ~~the MediaStore query (`DATE_MODIFIED` desc, cap 100, no `.nomedia` folders, no bins), re-read
    through a ContentObserver.~~ SUPERSEDED 2026-10-06 by Q-18-1 (no MediaStore query, no ContentObserver).
11. **App Shortcuts** (phase 11 Q1's standing rule; C-8, C-9): static `res/xml/shortcuts_files.xml` (~~`res/xml/
    shortcuts.xml`~~ SUPERSEDED 2026-10-06 by r3 D9 / V20; named by a `meta-data` on FilesActivity, the pattern at
    `AndroidManifest.xml:157`) entries `files_device`,
    `files_recent`, `files_bin` (ranks 0–2, targeting FilesActivity with the page extra) and the dynamic `files_sdcard`
    (rank 3, published on mount, removed on unmount and reconciled at process start by `ShellApp` — r3 D9), built with
    `ShortcutInfo.Builder.setActivity(<FilesActivity's
    component>)` — a dynamic shortcut without it attaches to the package's first MAIN / LAUNCHER activity, `MusicActivity`
    (`app/src/main/AndroidManifest.xml:143-158` ~~`:111-120`~~, cite corrected by r3 D9), and would burst on the Music tile
    under phase 11's per-activity query (C-21). E2 and E16.
12. **Harness.** `qa/phase-18/baseline_layout.json` derived from `qa/phase-17/baseline_layout.json` with a Files tile
    pinned (for E2's and E16's bursts), keeping phase 17's `slots` (MUSIC, CALENDAR, PEOPLE, PHOTOS, CAMERA), every `addedOnce`
    marker of the build and `manualSizes` for every tile (C-28); the previous file kept as
    `qa/phase-18/baseline_layout-pre-18.json` (C-3's form; this phase adds no marker); the zip fixtures'
    `qa/phase-18/scripts/make_zips.py`; drivers in `qa/phase-18/scripts/`. Harness helpers this phase calls and does not own
    EXIST in `qa/phase-03/scripts/lib.sh` (~~built by~~ SUPERSEDED 2026-10-06 by r3 D14):
    `ring_mark` / `ring_since` (C-20, phase 11's build task 7), `record` (C-26, phase 13's build task 7), `fill_volume` /
    `unfill_volume` (C-27, phase 15's build task 8). **Added 2026-10-06 (r3 V3 / V16 / V2 / V14 / V17):**
    `qa/phase-18/scripts/p18.sh`, the phase's driver floor sourced after `lib.sh` — `pubvol_up` / `pubvol_down` (EXIT trap),
    `files_up` / `files_down` (the shared-storage and files-collection snapshots, asserted equal after), copies of
    `absent_in`, `c6`, `gdump`, `top_activity` (`p17.sh:8-11`'s copy rule), and `ensure_start` wherever a row says "Home";
    images and the video reach `DCIM/Camera` only through phase 17's `media_up` / `media_down`; `testapps/qa-capture`'s
    `SendProbeActivity` extended from `SEND image/*` (one stream) to `SEND_MULTIPLE */*`, logging each URI and the md5 of
    the stream it read; `qa/phase-18/E12/producers.tsv` and `notrun.tsv`; `qa/phase-18/scripts/edge_index.tsv` and the
    edge driver (`edge_files.sh`, E20); the JVM gates write their exit code to `<row>/gradle.rc`.
13. **"Open file location" in Voice Recorder** (T15-16; an ADD to phase 15's part, INDEX Change Log when built): a
    recording's hold menu gains `rec_menu:location` ("Open file location"), which starts FilesActivity by explicit component
    with the recording's folder (its MediaStore `RELATIVE_PATH` under `/storage/emulated/0`) and name as page extras; Files
    opens that folder with the recording's row shown and logs `[files] open at <path> (from recorder)`. E17. The entry is a
    `FlyoutItem` beside the recorder's existing ones (`RecorderActivity.kt:380-382`); the extras reset Files' in-app history
    (r3 D12), so Back returns to Voice Recorder even when Files was already open.
14. **`FileOps`** (r3 D7). `files/FileOps.kt` owns every write (copy, move, rename, new folder, bin delete / restore /
    purge / empty, extract, create) over `java.io` with injected volume roots, canonicaliser, clock and scanner port; the
    UI and the service call nothing else to write. JVM tests on a temp dir cover temp-and-rename, cancel, each conflict
    answer, a cross-volume move failing after the copy (both files left), r3 D3's cases (the same-millisecond pair, the
    255-byte name, the index that cannot be written), index damage, r3 D4's journal and sweep, and the zip guards on
    `make_zips.py`'s fixtures. E18 gates on gradle's exit code. Built before tasks 3, 8 and 9 use it.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); every row starts from
`qa/phase-18/baseline_layout.json` through `layout_restore` (`qa/phase-02/scripts/layout.sh`), and after the restore the ring
holds zero `assignSlotOnce … -> assigned` lines (C-3). Motion rows follow RV11 and take their clock from the shell's
`[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms>` lines (`withFrameNanos`), a screenrecord corroborating
under phase 05's frame-spacing rule and never the primary clock (C-5); the `[motion]` line also carries `frames=<n>
maxGapMs=<ms>`, and every motion row asserts `maxGapMs` ≤ 33.4 ms (2 vsync) beside its numbers (C-31). Every ring assertion
reads `ring_since` from a MARK taken immediately before the step's action (after any clock jump, so the MARK is on the new
clock); absence assertions read the same slice; `reply_text` is `reply_since <MARK>`; `row_end` saves each ring the row names
to `<row>/ring-<name>.txt` (C-20; `lib.sh` `ring_mark` / `ring_since` / `reply_since`, which exist — r3 D14).
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
**Round 3 floor (2026-10-06; r3 V3 / V16 / V7 / V10 / V6).** Every driver sources `qa/phase-18/scripts/p18.sh` after
`lib.sh`. (1) A row is re-runnable ALONE: it makes its own fixtures with `files_up`, its own public volume with
`pubvol_up`, and ends with `pubvol_down` and `files_down`, which asserts the shared-storage listing (`find /sdcard -not
-path '*/Android/*' | sort`) and the `files` collection's paths equal the snapshots taken before the row — so "E2's public
volume", "the file E4 made" and every other reference to another row's state below reads "the row's own, made the same
way". (2) "Home" is `ensure_start`; a force-stop between steps is `c6` (it saves every ring first). (3) "Mid-copy",
"mid-extract" and "mid-way" mean: a `[files] <op> progress <bytes>/<total>` line with 0 < bytes < total was read in the
slice from the MARK before the row acts; a row that cannot read one fails rather than guessing. A screen that never idles
(the progress box) is dumped with `gdump` (RV13). (4) After any `appops set … default` the row takes a fresh MARK and
does not assert on the ring from before it (task 0 (c) records whether the revoke kills the process; the rows hold either
way). (5) Motion tolerances are the SOURCE's frame bound (r11/files-pass2.md §0: ± 33 ms for V1 / V4, ± 40 for V3, ± 67
for V2), never tighter than the measurement; `maxGapMs` ≤ 33.4 stays (C-31). (6) A JVM gate runs `./gradlew
:app:cleanTestDebugUnitTest :app:testDebugUnitTest --tests …; echo $? > "$ROW_DIR/gradle.rc"` and asserts rc 0 AND the
`TEST-*.xml` report's named cases with 0 failures / errors / skipped (precedent `qa/phase-11/scripts/e14.sh:40-42`). (7)
Evidence is kept on disk under qa/phase-18/.
~~Written for Q1 A; re-cut if Q1 rules otherwise.~~ SUPERSEDED 2026-09-23: Q1 ruled A (T18-7).

**Fixtures.** `adb shell mkdir -p /sdcard/QA-Files/sub` and files with known names, sizes and dates: `adb shell
"dd if=/dev/zero of=/sdcard/QA-Files/b.bin bs=1024 count=300"`, `touch -d "2026-01-01 00:00"
/sdcard/QA-Files/a.txt` (toybox touch), the images from docs/plan/qa/phase-01/scripts/make_photos.py, the
`qa-steps.mp4` from phase 17, one MP3 from qa/phase-01/MUSIC6's fixtures; md5s recorded with `adb shell
md5sum`. A big file for progress: `adb shell "dd if=/dev/zero of=/sdcard/QA-Files/big.bin bs=1m count=200"`
(~~count=200~~ SUPERSEDED 2026-10-06 by r3 V7: the count is whatever task 0 (e) measured to make the copy run ≥ 15 s,
written here at build start. WRITTEN 2026-10-06, Q-18-3 (a): `count=200` stands — 200 MB, copied at the debug-only
pace of 8 MB/s (`qa_files_rate_bps` = 8388608, set by `files_up` for the rows that need "mid"), about 25 s on either
volume; unpaced it takes 0.2-0.8 s, task 0 (e)). **r3 V13 / V12 (2026-10-06):** `a.txt` holds 10 bytes (`printf 0123456789`), not 0; EVERY
fixture, pushed ones included, gets its own distinct `touch -d` date on the device after it is written (`adb push` keeps
the host's mtime) and a distinct size; the driver holds one table (name, bytes, date, the literal detail string per
r11/files.md 1.5.8 — size to 3 significant figures, en-US short date, e.g. `b.bin` "300 KB 1/2/2026", `one.txt`
"1.00 KB …") and every order and detail assertion reads from it. The images and the video go to `DCIM/Camera` only
through phase 17's `media_up` and leave through `media_down` (`p17.sh:175`, `:199`). All of it is made by `files_up` and
removed by `files_down`, per row. **Zips**
(`qa/phase-18/scripts/make_zips.py`, python 3.11 `zipfile` on the host, pushed to `/sdcard/QA-Files/zips/`): `qa.zip`
(`one.txt` 1,024 bytes, `dir/two.bin` 307,200 bytes, `ü-name.txt` with the UTF-8 flag; md5s recorded), `qa-bad.zip`
(`ok.txt` plus entries named `../../evil.txt` and `/sdcard/abs.txt`), `qa-corrupt.zip` (`head -c 500 qa.zip`), `qa-enc.zip`
(`zip -e -P qa` from Info-ZIP, `/usr/bin/zip`), `qa-bomb.zip` (one 50 MB entry of zeros whose central-directory and
local-header uncompressed sizes are patched to 1,024), `qa-huge.zip` (one 3 GB entry of zeros, about 3 MB compressed,
declared honestly), `qa-big.zip` (200 MB of `/dev/urandom`, stored, for progress and cancel), `qa-cp437.zip` (`café.txt`, its
name encoded in CP437 with the UTF-8 flag clear — the edge case's fixture, lead 2026-10-06), `qa-nested.zip` (holding
`qa.zip`; `qa-big.zip` sized by task 0 (e) as `big.bin` is — 2026-10-06: 200 MB stands, extracted at the same pace). **Recent (re-cut 2026-10-06, Q-18-1):** `r1.png`, `r2.png`,
`r3.png` and `never.png` (make_photos.py's images) under `/sdcard/QA-Files/recent/` (md5s recorded), `never.png`
`touch`ed on the device last so it
is the newest file on the phone and is never opened. ~~with `touch -d` at 2026-09-01, -02, -03 12:00, plus `r4.txt` `adb
push`ed (now), then the scan (`content call … scan_volume`, phase 01 E6's command)~~ SUPERSEDED 2026-10-06 by Q-18-1:
Recent no longer reads modified dates or MediaStore.

**Emulator:**
- E1 **Grant and checklist.** `adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow` → the
  checklist row "Files" is green (dump) and ~~the app opens on This Device (`files_crumb:0` text "This Device", the folder
  rows of `/sdcard` listed; T18-8's landing)~~ SUPERSEDED 2026-10-06 by r3 D2 / V6: after `c6`, a cold start shows
  `files_pane` present with `files_pane:recent` the selected row (its fill = 0.6 · accent + 0.4 · (23,23,23) ± 4) and the
  Recent page behind it; tap `files_pane:device` → the pane closes, `files_crumb:0` reads "This Device" and the folder rows
  of `/sdcard` are listed; `appops set … default`, then a fresh MARK and a relaunch (the pid before and after recorded
  with `record` — r3 V10) → the row is red,
  the app shows "Files can't see this phone's storage" (`files_ungranted`) with a link (`files_grant_link`), the link starts
  `Settings$ManageExternalStorageActivity` (`dumpsys activity activities`), diagnostics `[files] access=denied` in the
  slice from that MARK; restore `appops set … allow` (RV12).
- E2 **Removable volume and the SD card shortcut.** With the grant: tap `files_menu` → the ≡ pane (`files_pane`) lists
  exactly `files_pane:recent`, `files_pane:device`, `files_pane:bin` in that order and no volume row (`sm list-volumes` =
  `emulated;0 mounted`), and `dumpsys shortcut` lists no `files_sdcard` for app.tileshell; hold the pinned
  Files tile → 3 satellites "This Device" (casing per r3 V20), "Recent", "Recycle Bin" (`quick_sat_label:0..2`,
  `quick_sat:3` absent) and the
  ring slice holds `[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown:
  files_device,files_recent,files_bin)` (T11-12);
  `am force-stop` + Home (C-6); `adb shell sm set-virtual-disk true`, `sm list-disks` → `disk:<id>`, `sm partition
  disk:<id> public` → `sm list-volumes` shows a `public:<x>,<y> mounted <UUID>` volume and, from a MARK before the partition,
  within 3 s the pane lists a fourth row `files_pane:<UUID>` between `files_pane:device` and `files_pane:bin` whose text is
  non-empty, matches no drive-letter form (`\([A-Z]:\)` absent — T18-8) and equals the PLATFORM's label recorded by build
  task 0 (a) (`StorageVolume.getDescription`; ~~equals the `<name>` of `[files] volume mounted <name>`~~ SUPERSEDED
  2026-10-06 by r3 V11 — that compared the app with the app; the line's `<name>` is asserted equal to the same recorded
  label), `dumpsys shortcut` lists `files_sdcard` as a dynamic
  shortcut and the Files tile's burst shows 4 satellites with "SD card" last (T18-6), its line `…/.files.FilesActivity/0: 4
  (4 shown: files_device,files_recent,files_bin,files_sdcard)`; `am force-stop` + Home, hold the Music tile (the baseline's
  MUSIC slot) → its burst is unchanged, `[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown:
  songs,albums,artists,playlists)` with no `files_sdcard` in it (C-21 — a dynamic shortcut missing `setActivity` would land
  here); browse the volume row, create a folder on it (`adb shell ls
  /storage/<UUID>` shows it); with the volume's folder open (~~if the volume was open~~ SUPERSEDED 2026-10-06 by r3 V11:
  not optional), `sm unmount
  public:<x>,<y>` → the pane row disappears, `files_sdcard` is gone from `dumpsys
  shortcut` and the burst is back to 3, and the page shows "This storage was removed" (`files_error` text),
  no crash (`logcat -d -s AndroidRuntime` empty of `app.tileshell`). **Process-start reconcile (r3 D9):** `c6`, then mount
  the volume while the shell's main process is dead (`am force-stop app.tileshell`, `sm mount`), Home, hold the Files tile
  without opening Files → 4 satellites and `[files] shortcut sdcard published`; force-stop, `sm unmount`, Home, hold → 3
  satellites and `files_sdcard` gone from `dumpsys shortcut`. The row makes its volume with `pubvol_up` and removes it with
  `pubvol_down` (`sm set-virtual-disk false`).
  ~~the root page shows `files_root:emulated` … a second root (`files_root:public`) whose name is the volume's~~
  SUPERSEDED 2026-09-23 by T18-8 (the ≡ pane, named by label).
- E3 **Listing and sort.** The QA-Files folder lists `a.txt`, `b.bin`, `sub`, the images, the video and the
  MP3 in 64-epx two-line rows with their type icons / thumbnails (dump `files_row:` order; `files_detail:sub` a date only,
  `files_detail:b.bin` "300 KB <date>" — size then date, r11/files.md 1.5.8); the sort line reads "Sort by: Name"
  (`files_sort`); sort by name / date / size each gives the order the fixtures' names, `touch -d` dates and `dd` sizes
  dictate (three dumps, exact order asserted), and the sort flyout offers exactly those three keys
  (`files_sort_item:name|date|size`) — no "Type" entry (T18-8; W10M had none). **Order rules (r3 V13, Y2):** folders before
  files in every sort; Name A → Z ignoring case; Date newest first; Size largest first (folders among themselves by name);
  ties by name. The expected order and each `files_detail:` string are read from the driver's fixture table (Fixtures),
  where no two fixtures share a date or a size — literal strings, e.g. `files_detail:b.bin` = "300 KB 1/2/2026". ~~sort
  by name / date / size / type … (four dumps, exact order asserted)~~ SUPERSEDED 2026-09-23 by T18-8.
  **Location bar:** open `sub` → `files_crumb:` texts "This Device", "QA-Files", "sub" in order; tap `files_crumb:1` →
  QA-Files listed; tap `files_up` → `/sdcard` listed; at This Device's root `files_up` is disabled (dimmed, a tap changes
  nothing).
- E4 **Operations** (W10M's verbs: select, then "Copy to" / "Move to" and pick the folder in Files' picker mode —
  T18-8). "Copy to" `sub` for `b.bin` → `adb shell md5sum` equal on both; "Move to" `sub` for `a.txt` →
  gone from the parent, present in `sub`, md5 unchanged; rename `b.bin` → `c.bin` (`ls`); new folder `n` →
  `ls -d /sdcard/QA-Files/n`; delete `c.bin` after the confirmation → it goes to the Recycle Bin (E4b's assertions); a
  conflict (copy `sub/b.bin` back over `b.bin`) offers replace / keep both / skip (`files_dialog:replace|keep_both|skip`)
  and each does what it says (`ls`, md5; keep both leaves `b (2).bin` — r3 D6);
  copy `big.bin` → `files_progress_text` reads "Copying files…" (`gdump`), the progress notification is in `dumpsys
  notification` with a "Cancel" action, `input keyevent KEYCODE_HOME`
  mid-copy (the floor's meaning: a progress line with 0 < bytes < total) leaves the service running — `dumpsys activity
  services app.tileshell` shows it with `isForeground=true` and its type holding `dataSync` (r3 V7; not mere presence) —
  the copy completes with an equal md5 and Files, reopened, shows the destination folder; a Move of `big.bin` reads
  "Moving files…" and ends on the destination folder (r3 D2); cancel mid-copy (the notification's action) → `[files] copy
  … cancelled`, no file and no temp left in the destination (`ls -a`: nothing matching `.*.part` — r3 D4's name).
  **Kill mid-copy (r3 D4):** `am force-stop app.tileshell` mid-copy → a `.big.bin.<opid>.part` may remain (recorded);
  Home, relaunch → after the sweep `find /sdcard -name '.*.part'` is empty, `[files] sweep: removed <n>`, and the source's
  md5 is unchanged. The row makes its own fixtures (`files_up`); renames and the new folder go through
  `files_dialog_input` / `files_dialog:ok`.
  ~~delete `c.bin` after the confirmation (Q3 A) → `ls` fails~~ SUPERSEDED 2026-09-23 by Q3 C (T18-7): the file goes to the bin.
- E4b **Recycle Bin (T18-1).** The row makes its own `c.bin` (`files_up`; ~~the file E4 made~~ r3 V3). Delete `c.bin` →
  `ls /sdcard/QA-Files/c.bin` fails, `ls /sdcard/.Tessera/bin/` lists
  `<ms>-<seq>-c.bin` (~~`<ms>-c.bin`~~ SUPERSEDED 2026-10-06 by r3 D3) with the md5 unchanged,
  `/sdcard/.Tessera/bin/.nomedia` exists, `.index.json` (`adb shell cat`) holds its
  original path, and `[files] bin delete /sdcard/QA-Files/c.bin: ok`; the Recycle Bin page (reached by `files_pane:bin`)
  lists `files_bin_row:c.bin` with its volume, and its `files_bin_note` text equals "Deleted files stay on this phone until
  you empty the Recycle Bin" (T18-10). Delete `qa-photo-0.png` from `DCIM/Camera` → within 3 s `content query --uri
  content://media/external/images/media --projection _display_name` no longer lists it, the Photos tile logs its refresh
  and the file does not come back after a second scan. Restore `c.bin` → back at `/sdcard/QA-Files/c.bin` with the same md5,
  the bin row gone; restore into a deleted folder (`sub/`'s file binned, then `adb shell rm -r sub`) → `sub/` recreated with
  the file; restore over an existing name → the conflict dialog, and replace / keep both / skip each does what it says
  (`ls`, md5). Delete permanently one row → gone from the bin folder and the index; Empty → the bin folder holds only
  `.nomedia` and `.index.json` with zero records; a delete made on the bin page is permanent. A delete on the row's own
  public volume (`pubvol_up`; ~~E2's~~ r3 V3) lands in THAT volume's bin (`ls /storage/<UUID>/.Tessera/bin/`), not the
  primary one, and Restore from it puts the file back on that volume (r3 V17). **r3 D3:** search "b" (E8's form), select
  `b.bin` and `sub/b.bin` and delete both in one action → TWO bin files and two index records with different `<seq>` or
  `<ms>` (both md5s present; the JVM test in E18 forces the same millisecond), each restoring to its own path; a file whose
  name is 255 bytes long is deleted → `bin delete …: ok`, the bin name is ≤ 255 bytes, and Restore gives back the full
  original name (from the index) with the md5 unchanged. **A delete that cannot reach the bin must not destroy (r3 V14):**
  `adb shell 'rm -r /sdcard/.Tessera; : > /sdcard/.Tessera'` (the bin path is a FILE) → delete `a.txt` → `[files] bin
  delete /sdcard/QA-Files/a.txt: failed <why>`, `files_error` shown, `a.txt` still present with its md5 unchanged; remove
  the file `/sdcard/.Tessera` (RV12). Negatives: `adb shell rm
  /sdcard/QA-Files/a.txt` never appears in the bin; the bin's files never appear in Recent (E14 — still true under Q-18-1:
  bin rows open nothing, and a deleted file's Recent entry is dropped) or in a listing of `/sdcard` (dot-folders hidden).
  The photo this row bins from `DCIM/Camera` is the row's own (`media_up`) and is restored or removed by `media_down` /
  `files_down`, so E9 does not depend on it (r3 V3). Full volume: `fill_volume 1048576` (its own assert: free ≤ 1 MB + 5
  MB; C-27) and delete a
  file → it still moves to the bin (a rename needs no space; the `bin delete …: ok` line and `ls`), then `unfill_volume`.
  ~~fill with `fallocate` (phase 17's edge-case command) … restore by deleting the fill~~ SUPERSEDED 2026-09-23 by C-27.
  `adb shell rm -r /sdcard/.Tessera` →
  the next delete recreates the bin and logs `[files] bin index /sdcard: rebuilt (bin folder missing)`. `adb uninstall
  app.tileshell` → `ls /sdcard/.Tessera/bin/` still lists the binned files; reinstall through `provision.sh` (RV12).
- E5 **Negatives** (worded for either outcome of build task 0 (b); the driver reads `qa/phase-18/BUILDSTART/`'s record
  and runs the matching form — r3 D13 / V9). **If the app can list `Android/data`:** ~~`/sdcard/Android/data` lists the
  fixture packages' folders as unreadable entries (dump text "Android doesn't let apps see this folder"), tapping one
  shows the reason~~ (kept as this branch; SUPERSEDED 2026-10-06 by r3 D13 as the only form) each other package's folder
  is a `files_unreadable` entry with the text "Android doesn't let apps see this folder", tapping one shows the reason.
  **If the list comes back null or holds only the shell's own folder:** the `Android/data` entry itself, in
  `/sdcard/Android`, carries `files_unreadable` with the text "Android doesn't let apps see other apps' folders here", and
  tapping it shows the reason and opens nothing. In both: no crash (`AndroidRuntime` empty of `app.tileshell`), `[files]
  unreadable <path>` in the slice, `/sdcard/Android/obb` the same, and the shell's own
  `/sdcard/Android/data/app.tileshell` is readable when it exists.
- E6 **Open with.** ~~Tap an image → `dumpsys activity activities` topResumedActivity =
  `app.tileshell/.photos.PhotosActivity` (phase 17) showing that image (phase 17 E4's pixel rule); a video →
  `app.tileshell/.video.VideoActivity` playing it (`dumpsys media_session`)~~ SUPERSEDED 2026-10-06 by r3 D1 / V1 (those
  are the library and the hub). Tap an indexed image → `top_activity` = `app.tileshell/.photos.ViewerActivity`, `[photosapp]
  viewer show <id>` in the slice from a MARK before the tap, read from the ring of the process ViewerActivity runs in, and
  the screencap's centre pixel = the fixture's colour ± 4 (phase 17 `e4.sh`'s form); an indexed video →
  `app.tileshell/.video.PlayerActivity`, `[video] playing <id>` read from `VIDEO_RING`, `dumpsys media_session` PLAYING.
  **Unindexed legs, one per source:** an image and a video in `/sdcard/QA-Files/hidden/` (a `.nomedia` folder) and on the
  row's public volume when MediaStore holds no row for them (recorded per file with `content query`) → the same two
  components, the data URI is the shell FileProvider's (`[files] open <path> via provider`), the picture / playback is
  asserted the same way, and the viewer's bar offers Share only (no Delete, no Edit node); an entry tapped inside `qa.zip`
  → "Extract first" (`files_error`), no activity started. The MP3 → MusicActivity with
  `dumpsys media_session` showing the shell's music session PLAYING that track (the phase 10 ADD); **own-launch check (r3
  D5):** the same play extra sent with `adb shell am start -n app.tileshell/.music.MusicActivity` (the shell uid, not
  the app's)
  → Music opens, the session is NOT playing that track,
  `[music] play extra ignored: not the shell`. **An MP3 with no MediaStore row (Q-18-2 (a)):** `qa-hidden.mp3` in
  `/sdcard/QA-Files/hidden/` (the `.nomedia` folder; `content query` on the audio collection lists no row with that
  `_display_name` BEFORE the tap — the precondition, asserted) → tap it: topResumed = `app.tileshell/.music.MusicActivity`,
  `dumpsys media_session` shows the shell's music session PLAYING with the title `qa-hidden` (the fixture carries no tags),
  the slice from a MARK before the tap holds `[music] play file /storage/emulated/0/QA-Files/hidden/qa-hidden.mp3 (not in
  library)`, and AFTER it the same `content query` still lists no row (Music did not add it); the Songs pivot's count
  (`[music] library …: <n> tracks`) is unchanged. Negative, the own-launch check on the URI form: the same extra with a
  URI sent by `adb shell am start` → not played, `[music] play extra ignored: not the shell`; and a URI whose authority is
  not the shell's FileProvider, sent from the shell's own uid in the JVM test of the rule (`*MusicPlayExtra*`, the floor's
  gradle gate) → refused. Every opener hand-off above also logs `[files]
  recent add <path>` (E14). `a.txt` →
  the system chooser (`com.android.intentresolver`) with `text/plain`; a `.xyz` file → "No app on this phone
  opens this" (dump text), diagnostics `[files] no handler for application/octet-stream`. After each launch `c6`
  (~~`am force-stop app.tileshell` + Home~~ SUPERSEDED 2026-10-06 by r3 V1: `c6` saves the `:video` ring first) before the
  next grid read (C-6).
- E7 **Share.** Select two files → share → the chooser resumed (`top_activity` = `com.android.intentresolver`) ~~with
  `ACTION_SEND_MULTIPLE` and two `content://` URIs from the shell's FileProvider (`dumpsys activity activities` intent
  line)~~ SUPERSEDED 2026-10-06 by r3 V2 (the dump shows only `act=…CHOOSER` and "(has extras)" on this image): the slice
  from a MARK before the tap holds `[files] share 2 files type=<mime> uris=<list>` with two `content://app.tileshell.
  <authority>/` URIs; choosing `testapps/qa-capture`'s send probe (`SEND_MULTIPLE */*`) → the probe logs both URIs and the
  md5 of each stream it read, equal to the fixtures' recorded md5s. The same on the row's own public volume (`pubvol_up`;
  a file under `/storage/<UUID>`) → the line with one `content://` URI and the probe's md5 equal. **Scope negative
  (T18-11):** the guard's JVM test (`--tests '*FileShareGuard*'`, run by the floor's gate: `gradle.rc` = 0 AND
  `TEST-*FileShareGuard*.xml` showing the five named cases with 0 failures / errors / skipped — r3 V15; the rule takes the
  volume roots and the canonicaliser as parameters, and the symlink case uses a real temp-dir symlink) feeds it the
  app's own `filesDir` file, `/storage/emulated/0/../../data/data/app.tileshell/files/x` (a `..` traversal) and a path whose
  canonical form is private (through a symlink) → each refused with `[files] share refused: outside shared storage` (the
  test reads the
  diagnostics it wrote); `/storage/emulated/0/QA-Files/b.bin` and a `/storage/<UUID>/…` path → allowed — so a guard that
  refuses everything and one that allows everything both fail. No UI path can offer a private file, so the JVM test is the
  proof; the same five cases run against the provider's `openFile` / `query` check (r3 D10); the adversarial review is the
  phase's GATE (build task 3; r3 D10's list).
- E8 **Search.** "b" from the QA-Files root lists `b.bin` and `sub/b.bin` with their paths; a term with no match
  shows the empty line (`files_search_empty`); searching while the walk runs (over the row's own 10,000-entry folder, so
  the walk outlasts a dump) shows the progress line (`files_search_progress`) and `files_search_cancel` stops it (`[files]
  search cancelled`); the term is typed into `files_search_box` and the sort line reads "Sort by: Relevance" (r3 V7).
- E9 **MediaStore in step.** The images are the row's own, put in `DCIM/Camera` by `media_up` (r3 V12 / V3). Move
  `qa-photo-0.png` from `DCIM/Camera` to `Pictures/QA-Album` in Files → `adb
  shell content query --uri content://media/external/images/media --projection _display_name:relative_path`
  shows the new path within 3 s and the Photos tile's `[photos] refresh (mediastore change)` line follows;
  rename the MP3 → ~~Music's library shows the new title source (`content query` on the audio collection) and
  phase 10's `[music]` observer line fires~~ SUPERSEDED 2026-10-06 by r3 V12 (a rename does not change an MP3's title, and
  no "observer line" exists): `content query` on the audio collection shows the new `_display_name` within 3 s, and the
  slice from a MARK before the rename holds `[music] library (media change): <n> tracks` (`MusicStore.kt:67`). RECORDED
  alongside (T18-4): `adb shell mv
  /sdcard/Pictures/QA-Album/qa-photo-1.png /sdcard/DCIM/Camera/` (a rename by path through FUSE, no scan) and the same query
  3 s later — whether MediaProvider followed it on its own is recorded; Files keeps its scan either way. Task 0 (d)'s
  row-on-create record sits beside it (r3 D11 / V5).
- E10 **App list, surface, budget.** "Files" under F with Pin to Start and NO Uninstall (qa/phase-01/UNINSTALL's
  method), no "New" caption; `am force-stop` + Home after the hold menu (C-6); phase 02's regress.sh pattern passes from
  the phase baseline (zero `-> assigned` lines after `layout_restore`, `addedOnce` equal to the file's — C-3);
  `qa/phase-03/scripts/exported.py <apk> qa/phase-03/exported-allowlist.txt`
  (~~`dumpsys package app.tileshell`~~ SUPERSEDED 2026-10-06 by r3 V20: the project's own reader) shows the exported
  components equal the allow-list plus this phase's ADD (FilesActivity; the `dataSync` service is `exported=false`)
  — the FileProvider is listed under Providers with `exported=false` and `grantUriPermissions=true` and is NOT an
  allow-list entry (T18-5); `stat -c%s app/build/outputs/apk/debug/app-debug.apk` ≤ 629,145,600 bytes.
- E11 **Geometry and motion against `r11/files.md` and `r11/files-pass2.md`** (written 2026-09-23, T18-8; re-cut
  2026-10-06, r3 D2 / V6). Dump bounds and screencap on the AVD
  (px ÷ 3 = epx on the 360-epx canvas); tolerances per R11: ± 1 epx for MEDIUM values (the desktop-1703-exact ones), ± 2 epx
  for LOW phone-downscale values, structure / order only for rows R11 read from a camera photo; colours ± 4 per channel.
  **Frame (1.1):** phase 01's drawn status bar (`BarMetrics.STATUS_EPX`, C-17) present; the location bar 48 ± 1 epx directly
  under it (its top = the status bar's bottom), fill (31,31,31); page background (0,0,0); the app bar 48 ± 1 epx, fill
  (31,31,31), directly on the nav bar. **Location bar (1.2):** ≡ centre x 24 ± 1; breadcrumb left 60 ± 2, cap 11 ± 1, white;
  "›" separators between segments; a path four levels deep (`/sdcard/QA-Files/sub/deep/deeper`) collapses its middle
  segments to "…" (structure); ↑ centre 24 ± 1 epx from the right edge, (123,123,123) ± 4 at a volume root (LOW, ± 2 on
  position). **Pane (1.3):** `files_pane` 256 ± 1 epx wide, fill (23,23,23), overlaying the page (the pixels right of it
  unchanged ± 2 against a screencap before opening — no scrim), rows 48 ± 1 epx from 72 ± 2 epx, glyph centre x 24 ± 1, label
  left 60 ± 1, the current row = 0.6 · accent + 0.4 · (23,23,23) ± 4; order Recent / This Device / volumes / Recycle
  Bin; no compact rail
  with the pane closed (the 360-epx form, pass 2 §4.3); on a cold start the pane is open with Recent current (E1).
  **Sort line (1.4):** "Sort by:" left 12 ± 1 (ink ≈ 13.7), cap top 16 ± 1 below the bar, "Sort by:" (160,160,160) ± 4, the
  value white, a ChevronDown after it. **Rows (1.5):** pitch 64 ± 1 epx; icon left 20 ± 2, name left 72 ± 2; detail
  (165,165,165) ± 4. **Icons view (1.6):** three icons per row, column centres at W/3 intervals ± 2 epx (LOW), name centred
  under each icon in up to two lines (a long name wraps to a second centred line, line pitch 20 ± 2), and in a folder of
  thumbnails (`DCIM/Camera`) the row pitch is 172 ± 8 epx with the app bar's third slot showing the list glyph (pass 2
  §4.6; a camera source, so structure and the stated band only). **App bar (1.7):** glyph centres Select / New folder /
  Icons / Search at 286 / 218 / 150 / 82 ± 1 epx from
  the right, More at 24 ± 1, glyph centre 24 ± 1 below the bar top; ••• expanded = 60 ± 2 epx with labels; overflow
  Refresh / Select all / Clear selection / Properties in that order (Clear selection dimmed with nothing selected, (137,137,
  137) ± 4). **Selection (1.8; pass 2 §1):** Select → `files_sort` reads "0 items selected" and all four bar buttons
  (`files_sel:delete|move|copy|share`) are dim (disabled; (137,137,137) ± 4); one tap → exactly "1 item selected" (the
  singular — "1 items selected" fails), two → "2 items selected"; the checkbox is 20 ± 2 epx square at centre x 22 ± 2,
  vertically centred on the icon; icon left 52 ± 2, name left 104 ± 2 (the 32-epx shift);
  the selected rows' fill equals the accent ± 4 full width and full row height; the bar reads Delete / Move to / Copy to /
  Share, Share dimmed once `sub` is in the selection; Back leaves selection mode (the sort line returns). **Picker (pass 2
  §1, LOW):** "Move to" → `files_pick_title` reads "Choose a folder" at the sort line's left (12 ± 2) with no `files_sort`
  node; no checkbox node on any row; `files_menu`, the breadcrumb and `files_up` present and the pane opens inside it;
  the app bar holds `files_pick_ok` / `files_pick_cancel` / More at 150 / 82 / 24 ± 2 epx from the right and nothing else;
  Back inside a subfolder goes to the picker's previous folder, Back at its first folder closes the picker with nothing
  moved (r3 D12). **Progress (pass 2 §1, LOW):** during the row's own `big.bin` move, `files_progress` top = 24 ± 2 epx
  (directly under the status bar), height 66 ± 6, inset 21 ± 3 each side, text "Moving files…" centred, no percentage and
  no cancel node inside the app; a pixel outside the box differs from the same pixel before the move (the wash); on
  completion the destination folder is shown; a copy reads "Copying files…" (stand-in, H5). **Dialogs (Y4, UNMEASURED —
  structure only):** rename, new folder, delete confirmation and conflict each show `files_dialog` anchored at the top,
  full width, fill (74,74,74) ± 4, a `files_dialog_title` and two side-by-side buttons (three answers for the conflict).
  **Recent (pass 2 §1 / §4.8, LOW):** no `files_sort` node; a row's `files_detail:` is a date only; the bar reads Select ·
  Icons · Search · ••• with no New folder, all dim when the list is empty; the empty line's text and position are E14's.
  **Hold menu (1.9):** on a file Delete /
  Move to / Copy to / Share / Rename / Properties, on `sub` the same without Share, item pitch 44 ± 2 epx, width 240.5 ± 2.
  **Properties (1.10):** a page, not a dialog — no app bar node, `files_crumb:` last segment = the item's name, rows "Date
  modified:" / "File type:" / "File size:" with values, and for `qa-steps.mp4` a "Video" section (structure). **Motion
  (Y5; ~~UNMEASURED~~ partly MEASURED, pass 2 §5):** ~~the `[motion] files_pane_open …` settle 250 ± 17 ms,
  `[motion] files_folder …` a cut then a 250 ± 17-ms fade~~ SUPERSEDED 2026-10-06 by r3 D2 / V6 (± 17 ms was tighter than
  the measurement; tolerances are the source's frame bound). `[motion] files_folder …` (a row tap, ↑, a breadcrumb
  segment, list ↔ icons): the frame after the tap shows an EMPTY list with the bars unmoved (screenrecord corroborating),
  then the entrance settles in 300 ± 33 ms with a first-frame offset ≥ 7 epx below rest, names before detail lines before
  icons; `[motion] files_pane_close …` 0 ms — the pane gone and the list empty in one frame, then `files_folder`'s
  entrance; `[motion] files_select …` (entering) settle 200 ± 40 ms; `[motion] files_hold …` first frame 700 ± 33 ms after
  the press, settled 233–367 ms after that; `[motion] files_pane_open …` settle within 250–283 ms, the labels' x unchanged
  between the first and last frame (a reveal); `[motion] files_more …` 317 ± 33 ms and `[motion] files_deselect …` 200 ±
  40 ms (stand-ins); each with `maxGapMs` ≤ 33.4 (C-31). All are asserted so the build draws what H3 judges.
  ~~written once that section lands; not runnable before~~ SUPERSEDED 2026-09-23 by T18-8 (E11 is written and runnable).
- E12 **Diagnostics.** Coverage: grep the union of `qa/phase-18/*/ring-*.txt` from this build's run (the rows' APK id
  matching), each pattern at least once (C-20). **Form (r3 V14, 2026-10-06; phase 17 E18's):** every `|` alternative
  below is its own pattern; `qa/phase-18/E12/producers.tsv` names, for each one, the row or edge sub-step that produces
  it, and an alternative it names that no slice holds FAILS the row; `qa/phase-18/E12/notrun.tsv` lists the alternatives
  no AVD row can produce, each with its reason (today: `shortcut sdcard failed (rate limit)`, `failed time limit`).
  Producers the rows did not name before: `roots:` and `list` (E3), `unreadable` (E5), `bin restore` / `purge` / `empty`
  and `bin … failed` (E4b), `shortcut sdcard published | removed` (E2), `zip create` (E13), `recent:` (E14). ~~Lines
  asserted by the rows above:~~ (SUPERSEDED 2026-10-06 by r3 V14 — untrue for seven of them.) The lines:
  `[files] access=<granted|denied>`, `[files] roots:
  <n> (<names>)`, `[files] volume mounted|unmounted <name>`, `[files] list <path>: <n> entries <ms> ms`,
  `[files] unreadable <path>`, `[files] copy|move <n> files <bytes> -> <dest> done|cancelled|failed <reason>`,
  `[files] no handler for <mime>`, `[files] search cancelled`, `[files] bin <delete|restore|purge|empty> <path>: ok | failed
  <why>`, `[files] bin index <volume>: <n> entries | rebuilt (<why>)`, `[files] zip open <path>: <n> entries`, `[files] zip
  open <path>: failed <why>` (T18-12), `[files] share refused: outside shared storage` (T18-11 — asserted by E7's JVM test,
  so it is the one line outside the ring union), `[files] open at <path> (from <caller>)` (T15-16, E17), `[files] zip
  extract <path> -> <dest>: done | cancelled | failed <reason>`, `[files] zip: refused entry <name>`, `[files] zip: refused
  (needs <bytes>, free <bytes>)`, `[files] zip: stopped at <bytes> (declared <bytes>)`, `[files] zip: encrypted <path>`,
  `[files] zip create <n> files -> <path>: done`, `[files] recent: <n>`, `[files] shortcut sdcard published | removed`, and
  every `[motion] <name> …` line a row times. **Added 2026-10-06 (round 3):** `[files] share <n> files type=<mime>
  uris=<list>` (r3 V2, E7), `[files] copy|move|zip extract progress <bytes>/<total>` (r3 V7, E4 / E13), `[files] sweep:
  removed <n>` (r3 D4, E4), `[files] open <path> via provider` (r3 D1, E6), `[files] open ignored: <why>` (r3 D12, E17),
  `[files] recent add|remove <path>` (below Q-18-1, E14), `[files] shortcut sdcard failed (rate limit)` (r3 D9, notrun),
  `[files] copy … failed time limit` (r3 D8, notrun), `[music] play extra ignored: not the shell` (r3 D5, E6). **Added 2026-10-06 (Q-18-3):** `[files] qa pace <bps>` (E4).
- E13 **Zip (T18-2).** Open `qa.zip` → a virtual root (`files_zip_root`) listing `one.txt` "1.00 KB" (~~1,024 bytes~~
  SUPERSEDED 2026-10-06 by r3 V13: 3 significant figures, r11/files.md 1.5.8), `dir`, `ü-name.txt`
  (dump `files_row:` / `files_detail:` text), `[files] zip open …: 3 entries`; tapping `one.txt` inside it → "Extract
  first" and nothing opens (r3 D6); extract → the output is `/sdcard/QA-Files/zips/qa/` (the zip's base name, r3 D6), `ls`
  and `md5sum` of each entry equal the recorded values, `ü-name.txt` named correctly; extract again → the conflict dialog,
  keep both gives `qa (2)/`; `qa-bad.zip` → `ok.txt` extracted and `[files] zip: refused entry
  ../../evil.txt` and `… /sdcard/abs.txt`, `ls /sdcard/evil.txt`, `ls /sdcard/QA-Files/evil.txt` and `ls /sdcard/abs.txt`
  all fail (and the control: `ok.txt` present — both directions); `qa-corrupt.zip` → "This zip can't be opened" (dump text),
  `[files] zip open /sdcard/QA-Files/zips/qa-corrupt.zip: failed <why>` in the slice from a MARK before the tap (T18-12), no
  crash (`AndroidRuntime` empty); `qa-enc.zip` → "This zip is password-protected" and `[files] zip: encrypted …`, nothing
  written; `qa-bomb.zip` → extraction stops, `[files] zip: stopped at <n> (declared 1024)` with n ≤ 1,024 + 1,048,576, and
  no temp folder or partial file remains (`ls`); `qa-huge.zip` with `fill_volume 2147483648` (free ≤ 2 GB + 5 MB, below the
  3 GB declared; C-27) → refused before any write with `[files] zip: refused (needs …, free …)`, then `unfill_volume`
  (~~filled … (`fallocate`) … restore the fill~~ SUPERSEDED 2026-09-23 by C-27); `qa-big.zip` extract → the progress
  notification in `dumpsys notification`, cancel mid-way (the floor's meaning) → no output folder and no temp folder (`ls
  -a`: nothing matching `.*.extract`); `qa-nested.zip` →
  `qa.zip` listed as a file that opens as a zip again — while it is open `/sdcard/.Tessera/tmp/` holds its copy, and after
  leaving the virtual root that folder is empty (r3 D6). Create a zip from `a.txt` + `b.bin` → `Archive.zip` in the current
  folder; from `a.txt` alone → `a.txt.zip` (r3 D6); `adb pull` the first, host `unzip -l`
  lists exactly those two names and `unzip -t` passes.
- E14 **Recent (Q-18-1; re-cut 2026-10-06).** ~~After the Recent fixtures and the scan, the Recent view lists `r4.txt`,
  `r3.txt`, `r2.txt`, `r1.txt` in that order at the top (`files_recent_row:` order, exact); rename `r1.txt` in Files → it
  rises to the top within 3 s; a file written with `adb shell 'echo x > /sdcard/QA-Files/recent/unscanned.txt'` and no scan
  is absent, and a file in a `.nomedia` folder (`/sdcard/QA-Files/hidden/.nomedia` + a file + scan) is absent — both
  absences are the stated rule~~ SUPERSEDED 2026-10-06 by Q-18-1 (Recent lists files OPENED in Files). **Empty:** `pm clear
  app.tileshell` → `provision.sh` → `ensure_start` → Files → the Recent page shows `files_recent_empty` with the text
  exactly "You haven't opened any files recently.", (160,160,160) ± 4, left 14.5 ± 2 epx, cap top 88.8 ± 2 (pass 2 §1),
  no `files_recent_row:` node, the bar's buttons dim, and `[files] recent: 0`. **Order:** open `r1.png`, `r2.png`,
  `r3.png` through Files in that order (images, so each tap opens the shell's `photos.ViewerActivity`, then Back; each
  logs `[files] recent add <path>`; one more leg opens `a.txt` through Android's chooser and RECORDS whether the AVD offers
  a handler — when one is picked the row gains `a.txt` on top, and backing out of the chooser adds nothing) →
  `files_recent_row:` nodes are `r3.png`, `r2.png`, `r1.png`, exactly those three in
  that order, each `files_detail:` a date only (today's, the opened-at date); re-open `r1.png` → `r1.png`, `r3.png`,
  `r2.png` (one entry per path, moved to the top). **Never opened:** `never.png`, the newest file on the phone, is absent,
  and so is a file only selected (Select, tap `r2.png`'s neighbour, Back) and a folder or zip only browsed. **Remove:**
  hold `r3.png` → the menu is exactly `files_hold:remove_recent` / `share` / `properties` ("Remove from recent", "Share",
  "Properties"); Remove from recent → the row gone, `[files] recent remove <path>`, and the FILE still present with its
  md5 unchanged (`ls`, `md5sum`). **Kept in step:** rename `r1.png` → `r1b.png` in Files → the row reads `r1b.png` in the
  same position; delete `r2.png` in Files → its row gone (and the file in the bin, E4b's form); open `r3.png` again, then
  `adb shell rm` it → on the next read of the page (leave and return) its row is gone. **Volume:** a file opened on the
  row's own public volume shows a row; `sm unmount` → the row hidden; `sm mount` → the row back. No bin file appears
  (E4b); `[files] recent: <n>` with n ≤ 100; the list survives `c6` (it is in the private store). The drivers' own
  `/sdcard/qa.xml` (r3 V5a) and `adb push`'s mtime (V5b) no longer matter — Recent reads no modified date — and V5c's
  no-scan question is task 0 (d)'s record for E9, not this row's.
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
  "This Device" (casing per r3 V20; the pane row's), "Recent", "Recycle Bin" in rank order (phase 11 E3's method), and
  the ring slice from a MARK before the hold
  holds `[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown: files_device,files_recent,files_bin)`
  (the activity-keyed line, T11-12 — it names only FilesActivity's ids);
  tap each → FilesActivity resumed on that page with the pane closed (This Device: `files_crumb:0` "This Device" — the
  shortcut still lands on This Device, r3 D2; Recent: with nothing opened, `files_recent_empty` reading exactly "You haven't
  opened any files recently."; after the row opens `r1.png` through Files, `files_recent_row:r1.png` —
  ~~`files_recent_row:*`
  / its empty line~~ SUPERSEDED 2026-10-06 by Q-18-1 / r3 V6, which named no text; Recycle Bin: `files_bin` present);
  `dumpsys shortcut` lists `files_device`, `files_recent`, `files_bin` as
  manifest shortcuts with ranks 0, 1, 2; `am force-stop` + Home between holds (C-6). The SD card half is in E2.
- E17 **"Open file location" from Voice Recorder (T15-16).** An `adb push` of a host-made 3-s .m4a to
  `/sdcard/Recordings/qa-take.m4a` + the scan (or phase 15 E14's take on this build); open Voice Recorder, hold
  `rec_row:<its id>` → `rec_menu:location` present with the text "Open file location" → tap it: `dumpsys activity
  activities` topResumedActivity =
  `app.tileshell/.files.FilesActivity`, the last `files_crumb:` reads "Recordings", `files_row:qa-take.m4a` present, and
  the slice from a MARK before the tap holds `[files] open at /storage/emulated/0/Recordings (from recorder)`; Back returns to
  Voice Recorder; **with Files already open (r3 D12):** open Files, descend into `/sdcard/QA-Files/sub`, Home, repeat the
  hold-menu tap → Files shows `Recordings` (`onNewIntent`), and ONE Back returns to Voice Recorder, not to `sub` (the
  extras reset the history); a `path` extra outside every mounted volume (`adb shell am start -n
  app.tileshell/.files.FilesActivity --es path /data/data/app.tileshell`) → `[files] open ignored: <why>` and the page
  Files would show without it; `c6` (C-6).
- E18 **`FileOps` on the JVM (r3 D7).** `--tests '*FileOps*'` through the floor's gate (`gradle.rc` = 0 AND the
  `TEST-*FileOps*.xml` reports with 0 failures / errors / skipped), the report naming at least: temp-and-rename, cancel
  leaving no temp, each conflict answer (replace / keep both `name (2).ext` / skip), a cross-volume move failing after the
  copy (both files left, never neither), the same-millisecond bin pair (two bin files, neither overwritten), the 255-byte
  name, an index that cannot be written (the delete fails, the file stays), index missing / unreadable / a record whose
  file is gone, the journal and the sweep (a sweep removes only journalled temps and never a bin path), and the zip guards
  on `make_zips.py`'s fixtures (`../` and absolute names refused, the bomb stopped, the huge zip refused, the encrypted
  bit read, a symlink entry written as a regular file).
- E19 **Phase 17's guards under All-files access (r3 D10 / V18).** On THIS build with the appop `allow`: (a) the JVM
  suites `UriAccessRulesTest`, `ViewerRulesTest`, the `PlayerRules` and `CaptureOutputGuard` tests through the floor's gate
  (rc 0, 0 failures / errors / skipped); (b) phase 17's drivers `qa/phase-17/scripts/trust_photos.sh`, `trust_video.sh`,
  `guard_selftest_video.sh` and its capture-output rows re-run as they are, their output saved under `qa/phase-18/E19/` —
  every refused-caller assertion passes with All-files held (a caller's `file:` or foreign `content:` URI is still
  refused; `tiles/api/ImageIngest.kt`'s authority rule still holds). (c) RECORDED, not asserted (`record
  photos_delete_dialog <shown|skipped>`): with All-files held, Photos' delete of the row's own `qa-photo-1.png` — whether
  MediaProvider's write-consent dialog shows (phase 17's doc promises this record "in phase 18's rows"). The row re-runs
  phase 17's DRIVERS on phase 18's build; it writes nothing under qa/phase-17/ and changes none of phase 17's open rows.
  It is item (a) of r3 D10's GATE list.
- E20 **The edge cases, executed (r3 V17).** `qa/phase-18/scripts/edge_index.tsv` (phase 17's form: the bullet's first
  words, the sub-step / row / P row / JVM test that covers it, its last run) has one line per bullet of "Edge cases"
  below, and `edge_files.sh <name>` runs each AVD sub-step on its own fixtures; the row fails if a bullet has no line or a
  line names a sub-step with no pass on this build. It gives a runner to the cases no row ran before: the cross-volume
  move, restore on the public volume, `.nomedia` seen from both sides, CP437 names, the hidden-files setting, the 255-byte
  name, the incoming call, Properties' size and path values, keep-both naming, and Back in the picker (Y3).

**Phone-only** (re-cut 2026-10-06, r3 V19: each row is something Jeremy does and reads ON the phone — no PC, no USB, no
adb; he reports what he sees and the lead writes it down; evidence kept on disk):
- P1 Start settings > Setup checklist > Files: say which screen opens; turn the access on; Files lists the phone. Restart
  the phone and run Device care's optimise: Files still lists. Turn the access off: the checklist row goes red and Files
  shows its "can't see this phone's storage" message with the link. The setup-wizard step is checked on the NEXT clean
  install (it shows only on a fresh install), not by revoking. ~~One UI's "All files access" page from the checklist row
  (`am start -a android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION -d package:app.tileshell`, resumed activity recorded)
  and the grant surviving a reboot and a Device care optimise; the wizard step on a phone with the grant revoked.~~
  SUPERSEDED 2026-10-06 by r3 V19.
- P2 A USB OTG drive plugged into the phone: it appears as a ≡-pane row named by its label, no drive letter (T18-8) — he
  reports the text; browse, copy to it, pull it mid-copy ("This storage was removed", no crash, no leftover on the phone);
  on an exFAT and a FAT32 drive, rename `a.txt` → `A.TXT` (lists once); a delete on it lands in the drive's own bin, and
  the drive pulled takes its bin rows away. ~~(E2's assertions on real hardware; the label text recorded with `record`)~~
  SUPERSEDED 2026-10-06 by r3 V19 (he reports the text).
- P3 Samsung Camera's DCIM at thousands of items and the S25U's Download folder: open each in Files, then read the
  `[files] list … ms` lines from Start settings > Diagnostics and report them; the scroll is judged by eye [accept].
  ~~list time and scroll per phase 01 P4's gfxinfo method~~ SUPERSEDED 2026-10-06 by r3 V19 (gfxinfo needs a PC).
- P4 Secure Folder and Private Space contents are absent (their storage is another user's), and the app says
  nothing about them rather than an error.
- P5 One UI's "Open with" sheet for a file opened from Files: which apps it lists for a `.txt` and a `.pdf` — he reports
  the list (RECORDED, not judged; ~~`lib.sh` `record`, C-26~~ SUPERSEDED 2026-10-06 by r3 V19). After picking one, the
  file is on Files' Recent page (Q-18-1).

**NEEDS-HUMAN** (re-cut 2026-10-06, r3 D2 / V6, Q-18-1): H1 *fidelity* — Files matches r11/files.md's MEDIUM values on
the phone (the pane, location bar, sort line, rows' pitch, app bar positions; E11; r11/files.md has no HIGH value) and
pass 2's two-capture values: the cold start on Recent with the pane open, and the selection geometry (20-epx checkbox,
32-epx shift, "1 item selected", the dim bar at 0 selected); H2 *accept* — the Recycle Bin (P4 — W10M had
none, r11/files.md 1.12.8): its row in the ≡ pane, the bin page and its wording including the privacy line "Deleted files
stay on this phone until you empty the Recycle Bin" (T18-10), the Delete permanently and Empty confirmations (Y1, Y6;
T18-1), and the stated limit that a Delete made inside Photos' viewer does not pass through the bin (r3 D1); ~~the delete
rule (Q3) and its confirmation wording~~ SUPERSEDED 2026-09-23 by Q3 C (T18-7); H3 motion (Y5), in two parts — *fidelity*
for the forms pass 2 measured (pane close, folder change / ↑ / breadcrumb, list ↔ icons, entering selection, the hold
menu's 700-ms delay; LOW video, one of them mouse-driven), *accept* for the stand-ins (••• expand, the sort picker's
opening, leaving selection, pane open at 360 epx) — ~~H3 *accept* — motion approximations (Y5, R11 §4's sources)~~
SUPERSEDED 2026-10-06 by r3 V6; H4 *accept* — the LOW / UNMEASURED values that remain (Y2's sort picker and its order
rules, Y3's Back rule including Back in the picker) and the volume naming by label with no drive letter (T18-8) —
~~Y1's default landing on This Device, Y2's selection geometry~~ SUPERSEDED 2026-10-06 by r3 D2 (measured; moved to H1);
H5 *accept* — the Move to / Copy to picker and move progress as pass 2 measured them (LOW, a camera), and the stand-ins:
COPY progress in the move box's form, the percentage and Cancel in the notification (P4 — W10M had neither), the wash's
colour, and the conflict / delete / rename / new-folder dialogs (Y4: R7 1.3.9's W10M dialog form, UNMEASURED-2); H6
*accept* — the unreadable-folder wording for `/Android/data` (the form task 0 (b) picked); H7 *accept* — the zip
flows (P4 — W10M had no zip handling, r11/files.md 1.12.7): a zip opened as a folder, "Extract first" for an entry, the
names (extract to `<zip base name>/`, create `<item>.zip` / `Archive.zip`, keep both `name (2).ext` — r3 D6), the
"password-protected" / "can't be opened" / "storage removed" wording (T18-8); H8 *accept* — Recent lists what was opened
in Files (Q-18-1; W10M also counted downloads, and Android has no phone-wide list), 100 at most, with the opened-at date
as the row's date (Y6) — ~~Recent as "recently changed, newest first, 100 max" (W10M's was "recently accessed or
downloaded", r11/files.md 1.12.9; T18-8)~~ SUPERSEDED 2026-10-06 by Q-18-1.

## Edge cases
Every bullet has a line in `qa/phase-18/scripts/edge_index.tsv` and is run by E20 on its own fixtures (`files_up` /
`files_down`, `pubvol_up` / `pubvol_down`); "E2's public volume" below reads "the sub-step's own" (r3 V3 / V17).
- 10,000 files in one folder: `adb shell 'cd /sdcard/QA-Big && for i in $(seq 1 10000); do : > f$i; done'`;
  the listing's first page draws in < 2 s (`[files] list … <ms> ms`) ~~and sorting by size completes~~ (SUPERSEDED
  2026-10-06 by r3 V17: every file is 0 bytes and "completes" has no bound) and a sort by Date (the files carry 10,000
  distinct `touch -d` minutes) redraws within 5 s from a MARK before the tap to the `list` line, the first row = the
  newest file and, after a scroll to the end, the last = the oldest; no `ANR in app.tileshell` in logcat; `files_down`
  removes `QA-Big`.
- Names: unicode, emoji, a 255-byte name (listed, renamed, and deleted to the bin and restored — E4b's r3 D3 leg),
  leading dots (hidden files shown or not per a Files setting, default off: `.hidden.txt` absent, the setting on →
  present, off again → absent), a name that differs only by case on a FAT volume (E2's public volume is FAT: rename
  `a.txt` → `A.TXT`
  succeeds and lists once).
- A folder deleted underneath an open listing (`adb shell rm -r`) → the page shows "This folder is gone" and
  Back goes up; a file deleted between list and tap → the tap shows the error line.
- Back after a breadcrumb jump (T18-8, Y3; r11/files.md UNMEASURED-3): open `/sdcard/QA-Files/sub/deep`, tap
  `files_crumb:0` (This Device) → Back returns to `deep` (the folder left), not to its parent; ↑ from `deep` goes to `sub`;
  Back with selection mode on leaves selection mode first and stays in the folder. Back in the picker (r3 D12): "Move
  to", descend two folders, Back → the picker's previous folder; Back at its first folder → the picker closes, nothing
  moved (`ls` unchanged).
- Copy onto a full volume (`fill_volume 1048576` as E4b, C-27): copy `big.bin` (r3 V8 — up to 6 MB can still be free, so
  a 300 KB file would succeed) → `[files] copy … failed <reason>` and "not enough space" (`files_error`),
  no temp in the destination (`ls -a`: nothing matching `.*.part`), the source's md5 unchanged; `unfill_volume`.
  ~~(`fallocate` fill as phase 17's edge case) …
  restore by deleting the fill~~ SUPERSEDED 2026-09-23 by C-27.
- Volume unmounted mid-copy (`sm unmount`; "mid" per the floor, with a file sized by task 0 (e) for the public volume)
  → the copy fails with "storage removed", no partial file on
  the remaining side; the same mid-extract of a zip on that volume → "storage removed", no partial output; a temp left on
  the pulled volume is swept when it mounts again (r3 D4).
- Grant revoked mid-session (`appops set … default` with the app open): ~~no process restart for an appop
  change, so the app re-checks on every resume and on each operation and shows the grant state instead of an
  IOException.~~ SUPERSEDED 2026-10-06 by r3 V10 / D13 (the "no restart" premise was never observed; task 0 (c) records
  it). For either outcome: revoke during a `big.bin` copy → after a relaunch (fresh MARK) Files shows the ungranted
  state, never an IOException or a crash; if the process survived, the copy ended `failed access removed`; `appops set …
  allow`, relaunch → after the sweep no temp remains (`find /sdcard -name '.*.part'` empty) and the source's md5 is
  unchanged.
- Move across volumes = copy then delete; a failure after the copy leaves both (never neither) — the JVM case in E18,
  and on the AVD a move from `/sdcard` to the sub-step's public volume with the md5 equal and the source gone.
- `.nomedia` folders: Files lists them; Photos and Music do not show their contents (MediaStore's rule) — both
  asserted (the file's row in Files, its absence from `content query` on the images and audio collections); ~~Recent does
  not list them (E14)~~ SUPERSEDED 2026-10-06 by Q-18-1: a file in one that was opened from Files IS in Recent (opened
  through the provider, E6).
- Symlinks and special files are not present on shared storage (FUSE); a path that resolves outside the
  volume is refused.
- A file opened in Photos, then deleted in Files: Photos' stale row shows a placeholder until the scan lands
  (phase 17's edge case).
- Screen off, an incoming call (`adb emu gsm call 5551234`, then `adb emu gsm cancel 5551234`) and a reboot during a
  copy: the service finishes
  or fails cleanly; after a reboot (boot-completed poll, then `wake_device` asserting `Awake` before the next tap — C-25; the
  same after the `KEYCODE_SLEEP` screen-off) and the first start of the shell after unlock, the sweep has run and no temp
  files remain (`find /sdcard -name '.*.part' -o -name '.*.extract'` empty; ~~`find /sdcard -name '*.part'` empty~~
  SUPERSEDED 2026-10-06 by r3 D4 — it could not pass without a sweep) and no bin file was touched (the bin's listing and
  index equal before and after).
- The app list's hold menu on Files (Pin to Start present; Uninstall absent; E10) and Files pinned to Start
  (phase 02) opening, on a cold start, on Recent with the pane open (~~on This Device~~ SUPERSEDED 2026-10-06 by r3 D2;
  ~~the root page~~ SUPERSEDED 2026-09-23 by T18-8).
- Properties' values (r3 V17): for `b.bin` "File size:" reads "300 KB", "Date modified:" the fixture table's date, and
  the breadcrumb's path segments equal the file's folder; for `sub` no size row is asserted.
- Recycle Bin: `.index.json` deleted by hand (`adb shell rm`) → the bin lists its files by bin name, Restore puts them in
  `/sdcard/Download/Restored/` and `[files] bin index /sdcard: rebuilt (index missing)`; an index record whose file another
  app deleted → dropped on the next read; a binned file whose original folder is now a FILE of that name → the conflict
  dialog; two files deleted from different folders with the same name → two bin rows (the deleted-at prefix and `<seq>`
  keep them
  apart, even in the same millisecond — r3 D3), each restoring to its own path; a bin file with no index record (`adb
  shell` copies a file into the bin) → listed by its bin name, Restore → `Download/Restored/`.
- Zip: a zip64 archive (`make_zips.py` writes one with 70,000 empty entries) opens and pages ~~without stalling~~
  (SUPERSEDED 2026-10-06 by r3 V17: a bound) — `[files] zip open …: 70000 entries` within 5 s of the tap from a MARK, the
  first row asserted and, after a scroll to the end, the last, no `ANR in app.tileshell`; a zip
  whose entry names are CP437 without the UTF-8 flag (`make_zips.py` writes `qa-cp437.zip` holding `café.txt` encoded in
  CP437) lists them decoded (`files_row:café.txt`); a zip opened from the bin (Recycle Bin rows open
  nothing — Restore first, stated).
- Liveness (N-01): reboot and a force-stop on the AVD (Device care is P1's, on the phone) leave the grant
  (`appops get`), the checklist row, the bins and their indexes (md5 of each `.index.json` equal before and after), the
  Recent list and — with no operation running — an empty operations journal intact.

## QA evidence
_None yet._
