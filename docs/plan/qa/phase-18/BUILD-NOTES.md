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
