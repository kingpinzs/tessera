# Phase 18 build notes (the lead's carry-list)

Things the builders reported that a later task, a driver or the gate must act on. INDEX.md stays master; this file only
keeps detail that would be lost with the conversation. Each line says who must act.

## From the pure layer (task 14, commit c39de461), 2026-10-06

1. **Android refuses `qa-bad.zip` at open.** With targetSdk 36 `java.util.zip.ZipFile`'s constructor throws when an
   entry name holds `..` or starts with `/` (the platform's `ZipPathValidator`). E13 needs `ok.txt` extracted and the two
   `refused entry` lines, so the Android side must clear the validator (`dalvik.system.ZipPathValidator.clearCallback()`,
   process-wide) before Files' first open. OWED: the zip UI builder wires it; the lead logs it as an INDEX Change Log
   line (a trust-touching mechanic: Files' own entry-name guard then is the only guard — item (c) of the GATE list).
2. Paths in `[files]` lines are the real ones (`/storage/emulated/0/…`), not `/sdcard/…`. OWED: drivers match on the
   real path.
3. The first delete ever made on a volume also logs `bin index <root>: rebuilt (bin folder missing)`. OWED: E4b's
   driver asserts that line only after its own `rm -r .Tessera`, from a MARK.
4. A skipped conflict writes no line for restore / extract / create (the doc's grammar is `ok | failed`).
5. Lines the doc does not list: `rename <path> -> <name>: ok | failed <why>`, `new folder <path>: ok | failed <why>`;
   zip create also ends `cancelled` / `failed <reason>`. OWED: E12's producers.tsv gains them or the lead rules them
   out of E12.
6. Search match = case-insensitive substring, so "b" also matches the folder `sub`; E8 names only `b.bin` and
   `sub/b.bin`. OWED: the lead settles E8's expected list before the row runs (doc question or a files-only rule).
7. Size text: three significant figures, rounded half up, binary units, "bytes" under 1,024. OWED: the driver's
   fixture table uses the same rule.
8. Replace of a folder by a folder MERGES; a cancelled multi-file copy keeps the files already completed and removes
   only the temp in flight. OWED: H5 / H7 wording says so.
9. Extra fixtures: `qa-symlink.zip`, and the 70,000-entry archive is `qa-zip64.zip`.
10. The nested zip's copy keeps its temp name `.Tessera/tmp/.<name>.<opid>.part`; the folder also holds `.nomedia`, so
    E13's "that folder is empty" is a plain `ls`, not `ls -a`.
11. Restore ignores an index path outside its own volume or inside `.Tessera` and sends the file to
    `Download/Restored/` (the index sits on shared storage, so it is untrusted input).
12. Invented limit: a central directory over 256 MB is not read ("too many entries"). OWED: the lead logs it.

## From task 1 (commit 03c924af), 2026-10-06

- Q-18-4 RULED (a) 2026-10-06 15:23: the per-app page (`MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` + package). Code changed in
  `Checklist.allFilesAccessIntent()`; OWED: one device check that the link, the checklist row and the wizard step start
  `Settings$AppManageExternalStorageActivity` (the emulator was busy when it was changed).
- E1 / E15: Settings forwards `Settings$ManageExternalStorageActivity` to `.spa.SpaActivity`; the rows must assert the
  START line (logcat `START u0 {… cmp=com.android.settings/.Settings$ManageExternalStorageActivity}`), not top-resumed.
  DONE: Change Log 2026-10-06 15:23.
- A root tag `files_root` exists that the doc's tag list does not name (harmless; drivers may use it).
- E15's wizard legs and `provision.sh`'s new line are NOT run yet.

## From the QA floor (task 12), 2026-10-06 — self-test `FLOOR: 181 passed, 0 failed, 25 recorded`

Where the doc's text could not be met as written; each is OWED a Change Log line by the lead before the gate:
1. `find /sdcard …` prints one line (a symlink): the snapshot uses `find /sdcard/ …`.
2. make_photos.py's images are nearly one size, so the fixtures are `make_fixtures.py`'s (same png() and colours, distinct
   dimensions): img-0..5.png, r1/r2/r3/never.png, qa-hidden.png.
3. media_up puts qa-steps.mp4 in Movies, not DCIM/Camera; only qa-photo-0..2 reach DCIM/Camera.
4. files_up snapshots FIRST, then makes fixtures (the Acceptance text; the Decisions entry says the other order).
5. Sizes round to nearest (the pure layer's rule); r11 1.5.8 does not say.
6. MediaProvider's scan rewrites hidden/.nomedia (35 bytes, mtime now): files_up scans before dating.
7. files_down removes /sdcard/qa.xml and /sdcard/.Tessera when the before-snapshot did not hold them.
8. E12 gaps: no producer for `move … cancelled|failed`, none written for `bin restore|purge|empty … failed`.
9. E20 names "restore on the public volume" and "keep-both naming" with no Edge bullet: BIN_PUBVOL and KEEP_BOTH added.
10. MP3 fixtures are under qa/phase-01/MUSIC6-fixtures: 03.mp3 in QA-Files, 04.mp3 stripped of tags as qa-hidden.mp3.
Not proved by the floor (needs app code): mid_progress on a real progress line; the pace pref being READ by the app
(`QaBases.FILES_RATE` is not in app source yet — the service builder adds it); two-stream cross-app grants (E7).
`pubvol_up` must be called directly, never in `$(…)`. `jvm_gate` runs cleanTestDebugUnitTest (wipes test-results).

## From the ADDs builder (provider, Music, recorder, static shortcuts), 2026-10-06 15:30 — compiled and unit-tested, NOT run on a device

Device checks OWED (the lead or the E6 / E7 / E16 / E17 drivers):
- E6 own-launch negatives: `am start -n app.tileshell/.music.MusicActivity --el app.tileshell.music.PLAY_ID <id>` and
  `… --es app.tileshell.music.PLAY_URI content://app.tileshell.files/root/storage/emulated/0/QA-Files/hidden/qa-hidden.mp3`
  → Music opens, not playing, `[music] play extra ignored: not the shell`; repeat with Music already open and with
  `-f 0x20000000` (onNewIntent).
- Provider: `content read --uri content://app.tileshell.files/root/data/data/app.tileshell/files/files-recent.json` fails;
  through qa-capture with a grant, a rewritten path logs `[files] share refused: outside shared storage`.
- `dumpsys shortcut`: three manifest shortcuts, ranks 0, 1, 2. E17: rec_row hold → `rec_menu:location`.
For the open-with builder: Files sends PLAY_ID only when Music's library holds the id (Music skips IS_RECORDING), else
the URI form; PLAY_ID is a Long, PLAY_URI a String from `FilesProvider.uriFor`.
For the adversarial review (GATE b, f): the symlink swap between canonicalise and open is NOT closed by an fd re-check
(emulated and FAT volumes hold no symlinks); a grant holder can flood the ring with refusal lines; paths under
`<volume>/Android/data/app.tileshell` count as "under a volume"; the onNewIntent caller rule (Change Log 2026-10-06 15:30).

## From the service builder (FileOpsService, FilesEnv, FileOpsClient, FileOpsRun), 2026-10-06 15:43 — compiled, 160 files.* unit tests pass, NOT run on a device, NOT YET COMMITTED (it depends on the UI builder's uncommitted FilesStores.kt / FileVolumes.kt; commit together)

- The zip path validator is cleared process-wide in FilesEnv's init (line "zip: platform path validator cleared"). The main
  process's other unzip, cortana/speech/SpeechModels.kt (phase 03's part), then relies on its own ZipSafety.resolve alone.
  OWED: an INDEX Change Log line (a trust change touching a built part) and the adversarial review's eye (GATE c).
- Extra lines not in E12's list: "zip create progress", "zip: platform path validator cleared", "<verb> not started: <why>",
  "<verb> ended on an error: <Exception>". OWED: producers.tsv / a Change Log line.
- Built where the doc is silent: a partial wake lock while bytes move; a sweep also when FilesActivity comes on screen with
  a non-empty journal and nothing running (re-grant restarts nothing); no progress line during a same-volume move (a
  rename has no bytes) — so E4's "Move of big.bin reads Moving files…" needs a CROSS-volume move or the box will flash;
  OWED: the lead checks E4 / E11's move legs against this before the drivers are written.
- Without POST_NOTIFICATIONS the notification's Cancel cannot be reached; the doc puts Cancel only there (H5).
- Device checks owed: E4's service legs (pace line + mid progress, Home mid-copy isForeground + dataSync, Cancel action,
  no .part after cancel, force-stop then sweep, revoke / unmount / screen-off, qa-bad.zip partial extract).

## From the UI builder (tasks 2 / 5 / 7 + the dynamic sdcard shortcut), 2026-10-06 16:05 — run on the AVD, APK 61ce7af5; smoke evidence TASK2-smoke/

OWED Change Log lines / rulings (the lead):
1. E11 / E14's ABSOLUTE y values (pane rows "from 72", Recent's empty line "cap top 88.8") are on W10M's 24-epx status bar.
   Built relative to our bar (BarMetrics.STATUS_EPX = 28): rows start at 76, the empty line's cap top 92.6. C-17 says the
   status bar is cited, never hard-coded — the rows read "+ (STATUS_EPX - 24)".
2. E8: the search walk over a 10,000-entry folder finishes in about 0.25 s, so no dump can catch files_search_progress.
   A 250,000-file tree showed it, but removing that tree with one rm -rf SEGFAULTED the platform's MediaProvider
   (TASK2-smoke/18-rm-incident.txt). Q-18-5 asked of Jeremy.
3. The hidden-files setting's home (the doc names none): a fifth overflow line "Settings" (files_more:settings) with
   files_setting_hidden. P4; goes on H4.
4. Back with an empty history calls moveTaskToBack (a warm return next time), not finish(). Goes on H4 (Y3).
5. Bin row detail = "<deleted date> <volume name>"; a search hit's detail = its path relative to the searched folder.
6. dumpsys reports rank 0 for files_sdcard though rank 3 is set (the platform renumbers dynamic shortcuts per activity;
   manifest ones sort first) — E2's order assertion is on the burst's order, not the dumpsys rank.
7. Type icons are drawn in code; the SD-card pane glyph is a stand-in (no such glyph in the font). H1 / H4.
NOT verified by it: the shortcut reconcile with the process truly dead; the Files tile's burst (E2 / E16); the hold
gesture; video thumbnails; Icons view's two-line wrap and the 172 pitch in DCIM/Camera.
QA RULE from its incident: never bulk-delete a huge tree in one command on the AVD.

## 2026-10-06 16:11: Change Log lines written

Q-18-5 (a) and the nine build-time calls are in the doc's Decisions and INDEX's Change Log (2026-10-06 16:11). Still OWED from
the lists above: the E12 producers.tsv rows for the extra lines; the device checks; H4 / H5 wording for the P4 calls.

## From the screens builder (tasks 3 / 8 / 9), 2026-10-06 16:55 — run on the AVD, APK 291d1bc4; smoke evidence TASK3-smoke/<leg>/

NOT verified by it (each still owed by a row or an edge sub-step): the full-volume legs; qa-huge.zip; the symlink and CP437
zips; the 255-byte name; the same-ms twin delete; the unindexed restore to Download/Restored; the audio Properties section;
the 70,000-entry zip's LAST row; the uninstall leg; a public-volume share; restore into a folder removed by adb rm -r
(it removed the folder through Files); the dim glyph's colour; floor_selftest.sh after its p18.sh fix.
Floor fix it made: _pace_write force-stops again AFTER the pref write (HOME restarts between stop and write and kept the
old prefs: the first paced run copied unpaced — f-big/out-copy-run1-unpaced.txt).
Calls it made (P4 / silent in the doc — H rows to carry them; OWED a Change Log line with the next batch):
1. files_zip_create = the last line of the selection's overflow ("Create zip"). 2. files_bin_empty in the bin's overflow;
files_bin_restore / files_bin_delete are the bin's selection bar; a hold on a bin row or inside a zip does nothing.
3. [motion] files_hold carries t0 = the press and an appended first=<ms>. 4. The dialog's text box is a local field in
OutlinedField's metrics (rename selects the whole name). 5. Dialog scrim black 60 %. 6. Back during an operation does
nothing; Back on a conflict = Skip. 7. A nested zip has Extract dimmed; an operation lands on the folder HOLDING the
output. 8. Failure text "Couldn't copy the files: <reason>". 9. Picker overflow = Refresh only; bar labels Done / Cancel;
no New folder in the picker (open question). 10. Zip pages have no Select / New folder; a new line "properties <path>".
11. HOLD_MS is 688 so the first frame lands at 700 (measured 701-719). 12. The progress box's top = STATUS_EPX (28).
Seam for the next builder: FilesBehaviour(activity, opening: FilesOpening) — FilesOpening.open(state, entry) and
.holdRecent(behaviour, state, press); helpers behaviour.showMenu / share / showProperties.
ENVIRONMENT: a safety check refused two inline adb rm commands of that builder; drivers must be script files.
The qa-capture test app was left installed on the AVD (uninstall at the end of the build's device work).

## From the open-with / Recent builder (tasks 4 / 10 + owed device checks), 2026-10-06 17:50 — run on the AVD, APK 7339f9d1; TASK4-smoke/<leg>/

Smoke-verified: E6 (95 pass), E14 (65 / 0), bursts E16 / E2 (39), E17 (22), the Q-18-4 intent (13), provision.sh's first run.
For the row drivers (facts measured):
1. Phase 17 writes "[photosapp] viewer open <id>" at an open ("show" is its swipe line); for a provider URI the id is "external".
2. ".xyz" maps to chemical/x-xyz: the no-handler line reads that type; application/octet-stream HAS handlers on the AVD.
3. On a public volume the APP's MediaStore query finds no row (the shell's does), so every public-volume file opens via
   the provider (Share only in the viewer) — r3 D1's "a volume MediaStore has not indexed".
4. logcat REDACTS the START line's data to "dat=package:": E1 / E15 prove the package by the settings page's own text.
5. "pm clear" does NOT reset the MANAGE_EXTERNAL_STORAGE appop. 6. Music is started NEW_TASK | SINGLE_TOP.
7. MediaProvider leaves /sdcard/Pictures/.thumbnails/<id>.jpg for a public-volume picture; files_down flags it.
8. A plain "am start" to a RUNNING Music delivers no new intent (0 lines); the negatives use a cold start or -f flags.
9. The chooser's PendingIntent is MUTABLE | ONE_SHOT | CANCEL_CURRENT, its path re-checked in the receiver — GATE list.
10. Extra lines: "open <path> not started: <Exception>", "open <path>: media query failed: …".
NOT verified on a device: the provider's OWN refusal line (the framework refuses an ungranted URI before FilesProvider
runs; only the JVM test reaches the check); a true dead-process shortcut reconcile; the wizard step's intent; the
recording-from-Files leg's session read (a 3-s clip ended before the read — driver timing).
OPEN: Q-18-6 (asked): selection mode on the Recent page shows the folder page's bar (Delete / Move to / Copy to / Share),
so a file CAN be binned from Recent; the Decisions' reason says Recent "can never delete a file".
WHOLE SUITE: 1913 tests, 3 FAIL — phase 17's scan guards (UriAccessWiringScanTest; TrustWiringScanTest ×2) tripped by
phase 18's caller-uid reads and its two QA prefs. A builder is conforming phase 18's code to the guards' form.

## 2026-10-06 17:54: Q-18-6 RULED (b)

Recent's selection bar stays the folder page's (Delete → the bin, Move to, Copy to, Share). No code change. E14 gains nothing new;
H8's wording says a file can be binned from Recent's selection and that Remove from recent never touches the file.

## L18-1 / L18-2 fixes, 2026-10-06 19:17 — on branch phase-18-fix (a4721a5e, 6aa3bfb2), unit-tested, NOT merged, NOT run on a device

Builder's evidence copied to qa/phase-18/L18-fix/ (on disk). Whole suite there: 2007 tests, 0 failures (includes the other
fix builder's uncommitted tests). Mutations: L18-1 10/10 red, L18-2 16/16 red.
- L18-1: MusicItemRule.decide(controllerUid, myUid, mediaId, hasUri, hasQuery) -> Keep | Search | Rebuild(id) | Drop; Keep only
  for the shell's own uid. Driver scripts/l18_1.sh needs rowsb.sh (row writer B's, untracked when the fix branch was cut).
- L18-2: AlarmRingtoneRules.fromApi(ringtone, access) keeps settings / internal-media ringtones by form and external media
  only when the caller could read it; everything else -> the default sound (the alarm is still made). RingService refuses a
  non-content or shell-authority URI at the sink. ALSO changed: ClockActivity re-checks the handler's api_edit bundle (it is
  exported and read that bundle from any intent). Driver scripts/l18_2.sh.
OWED: run l18_1.sh and l18_2.sh after the merge; the sink has no device leg (JVM + scan only); a possible pre-existing
crash to check — a foreign setMediaItems(items, startIndex) whose items are dropped may hand the player an out-of-range
startIndex (not changed; verify on the device with the l18_1 probe or log it on the ledger).
Change Log line OWED at the merge (phase 10's and phase 15's parts changed).
