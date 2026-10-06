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
