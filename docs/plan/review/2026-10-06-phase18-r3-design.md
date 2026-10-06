# Phase 18 r3 — Reviewer 1 (design / correctness)

Saved by the lead from the reviewer's final message (an independent Opus subagent, read-only; brief
`review/2026-10-06-phase18-r3-brief.md`; 2026-10-06).

`r11/files-pass2.md` exists (16510dab) and was used. Doc lines are `docs/plan/phase-18-files.md`; code paths are under `app/src/main/`.

**D1 — BLOCKING — l.118, 329-330, 474-477 (E6): open-with names the wrong components and no URI form.**
- Evidence: `AndroidManifest.xml:632-635` "Other apps' pictures arrive at ViewerActivity, never here"; `:740-741` VideoActivity is the hub, `:761-766` PlayerActivity is "the one player".
- `photos/ViewerRules.kt:63` shows nothing unless the scheme is `content`.
- `video/PlayerRules.kt:256-260` allows `file` only under the shell's own dirs, so a shared-storage `file:` path is refused.
- Fix, Decisions + task 4: "An image → `photos.ViewerActivity`, a video → `video.PlayerActivity`, by explicit component, `ACTION_VIEW`, always a `content://` URI: the MediaStore row URI when a path query finds one, else the shell FileProvider's URI (same uid, so no grant is needed; `UriAccessRules.isOwnLaunch` makes it the shell's own launch). With no row (`.nomedia`, unscanned) the viewer offers Share only (`ViewerRules.actions`, `hasRow=false`). A Delete made inside the viewer is Photos' MediaStore delete and does not pass through the bin."
- Fix, E6: assert `.photos.ViewerActivity` and `.video.PlayerActivity`; add one `.nomedia` image leg.
- Phase 17 D1 (either answer): no change, Files is an own-uid launch. D3: only the viewer's process changes; E6 keeps asserting the component name.

**D2 — BLOCKING — l.24, 228, 254-259, 306, 408, 529-530, 579, 610, 648: pass-2 §4 governs and contradicts six applied values.** Replacements:
1. Landing (pass-2 §1 UNMEASURED-4, §4.4, x2): cold start is on **Recent with the pane open**, not This Device.
   - Change Scope l.24, T18-8 (1), task 2, Y1, E1 (`files_pane` open, `files_pane:recent` selected), the l.648 edge case.
   - E16's "This device" shortcut still lands on This Device.
   - H4 loses "landing"; it joins H1 as a MEDIUM candidate.
2. Y2 selection: status becomes MEASURED (20 square, cx 22, shift 32). Add "all four bar buttons dim at 0 selected" and "1 item selected" singular to E11. H4 loses selection geometry; the sort picker stays.
3. Y4 picker (§4.5): replace "bottom confirm bar" with: "Choose a folder" in the sort line's place; app bar ✓ · ✕ · ••• at 150 / 82 / 24 from the right; no checkboxes; the pane works inside it.
4. Y4 move progress: a top box "Moving files…" with indeterminate dots (top 24, about 66 tall, inset about 21) over a wash, then the destination folder is shown.
   - W10M had no percentage and no cancel. The doc's cancel stays in the notification as a P4 addition under H5.
   - R7's dialog remains for conflict / delete / rename / new folder only.
5. Y5 and E11 motion:
   - Pane close: a one-frame cut to an empty list.
   - Folder change, ↑, breadcrumb, list↔icons: a cut to EMPTY, then rows fade and slide up 7 epx over 300 ± 33 ms (names, details +100 ms, icons +130 ms). This replaces "250 ± 17-ms fade".
   - Selection enter: 200 ms (160–240) ease-out slide. This replaces "one-frame cut".
   - Hold menu: first frame at 700 ms; box from 76 % settling at +233–367 ms.
   - Pane open: a right-edge reveal with static labels, 250–283 ms.
   - ••• expand stays R7's value. H3 still applies to all (LOW video, mouse input).
6. Recent (§4.8, UNMEASURED-5): rows are date-only with no sort line; bar is Select · Icons · Search · •••.
   - The hold menu Remove from recent · Share · Properties, and the empty string "You haven't opened any files recently.", do not fit "recently changed". State both as H8's.
7. E11 Icons: add two-line centred labels and row pitch 172 ± 8 for thumbnail folders (camera source, structure only).

**D3 — BLOCKING — l.170-172, 452-453, 651-652: the bin name can overwrite and can fail.**
- `<deleted-at ms>-<name>`: two same-named files binned in one millisecond (E8's search lists `b.bin` and `sub/b.bin`; select both, delete) collide. `rename(2)` replaces the target, so one file is lost.
- A 255-byte name (edge case, l.621) plus the prefix exceeds NAME_MAX, so the delete fails.
- Rename-then-index leaves an unindexed file if the process dies in between.
- Fix: "Bin name = `<ms>-<seq>-<name, truncated to fit 255 bytes>`; the delete never renames onto an existing bin name (`seq` is bumped until free; case-insensitive on FAT). Order: index record written first, then the rename, then the scan; a record whose file is absent is dropped (existing rule); a bin file with no record is listed by bin name and restores to `Download/Restored/`. If the index cannot be written (0 bytes free), the delete fails with `bin delete …: failed no space` and the file stays."
- Add a same-ms pair and a 255-byte name to E4b.

**D4 — BLOCKING — l.129-132, 192-193, 644-646: "dies with nothing half-written" is false on a process kill or reboot, and nothing in the doc sweeps.**
- The row `find /sdcard -name '*.part'` empty after reboot cannot pass as designed.
- The temp name appears only in E4 (`*.part`); the extract temp folder has no name.
- Fix: "Temp names: `.<name>.<opid>.part` (file) and `.<zip>.<opid>.extract/` (folder), dot-hidden. Each running operation is journalled in `filesDir/files-ops.json` (temp path, volume UUID). The main process sweeps journalled temps at its first start after unlock and when a journalled volume mounts; a sweep never touches `.Tessera/bin`. A grant revoked mid-copy fails `access removed` and the temp is swept after the re-grant."

**D5 — SHOULD-FIX — l.119-121, 329, 476-477: the Music ADD's "one MediaStore audio id" cannot play unindexed audio, and the activity is exported.**
- A `.nomedia` folder or an unscanned file has no id.
- `AndroidManifest.xml:143-146`: MusicActivity is `exported="true"` with no launch mode.
- Fix: "The play extra is honoured only for the shell's own launch (`getLaunchedFromUid() == myUid`); it carries a MediaStore audio id; audio with no row goes to Android's chooser with the FileProvider URI and `[files] music: no library row`." Add that leg to E6.
- This narrows Q4 A for unindexed audio. The lead should confirm it with Jeremy, or else specify a URI form for MusicService.

**D6 — SHOULD-FIX — l.190-199, 346-347, 614-615, 655: zip leaves four things to guess.**
- (a) `ZipFile` needs a real file, so "a nested zip opens the same way" (l.191) and any open or share of an entry need a temp copy. T18-11's guard refuses `cacheDir`.
  - Fix: "Entries inside a zip do not open or share (`Extract first`, as bin rows); a nested zip is copied under D4's temp rule into `<volume>/.Tessera/tmp/` under the same guards and removed on leaving."
- (b) Encrypted detection: `java.util.zip.ZipEntry` exposes no flag bit (API fact). State "bit 0 of the central directory's general-purpose flag, read by Files' own parser".
- (c) Names H7 judges but the doc never defines: extract goes to `<zip base name>/` beside the zip; create writes `<single item's name>.zip` or `Archive.zip` in the current folder; "keep both" everywhere = `name (2).ext`.
- (d) A symlink entry extracts as a regular file — state it.

**D7 — SHOULD-FIX — tasks 3, 8, 9; l.484: only the share guard has a JVM proof.** The built standard is ports with JVM tests (`media/MediaWrites.kt`, `people/PeopleWrites.kt`).
- Fix, new task: "`files/FileOps.kt` owns every write (copy, move, rename, new folder, bin delete / restore / purge / empty, extract, create) over `java.io` with injected volume roots, canonicaliser, clock and scanner port. JVM tests on a temp dir cover temp-and-rename, cancel, each conflict answer, a cross-volume move failing after the copy, D3's cases, index damage, and the zip guards on `make_zips.py`'s fixtures."
- New row E18: `./gradlew :app:testDebugUnitTest --tests '*FileOps*'`, gated on the exit code.
- E7's guard test needs the same injection: `File.canonicalPath` on the host cannot see `/storage`. `PlayerRules.sourcePath` already takes `canonical:` this way.

**D8 — SHOULD-FIX — l.129, 298-303: the foreground service is under-specified for target 36** (`app/build.gradle.kts:22`).
- The manifest holds `FOREGROUND_SERVICE` but no `FOREGROUND_SERVICE_DATA_SYNC` (`AndroidManifest.xml:21-22`, `:82`). Task 1 adds neither the permission nor the `<service foregroundServiceType="dataSync" exported="false">`.
- Android 15+ caps dataSync at 6 h per 24 h and calls `Service.onTimeout(int,int)`; the app crashes if it does not stop (platform behaviour change, from memory of the Android 15 documentation).
- Fix: add both to task 1; "on `onTimeout` the operation ends `failed time limit`, temps removed"; "started only from a visible FilesActivity".

**D9 — SHOULD-FIX — l.206-210, 304-305, 350-355: volume tracking and the dynamic shortcut.**
- The doc does not say where the listener lives. If it lives in FilesActivity, a card inserted before Files is opened publishes nothing, and one pulled while the process is dead leaves `files_sdcard` to burst.
- Fix: "ShellApp (main process) registers `StorageManager.registerStorageVolumeCallback`, and reconciles pane rows and `files_sdcard` against `getStorageVolumes()` at process start; a broadcast filter, if used, needs `addDataScheme("file")`; `setDynamicShortcuts` returning false logs `[files] shortcut sdcard failed (rate limit)`; with two removable volumes the shortcut targets the first."
- Task 11: `res/xml/shortcuts.xml` → `res/xml/shortcuts_files.xml` with a `meta-data` on FilesActivity (the pattern at `AndroidManifest.xml:157`).
- The MusicActivity claim holds: it is still the first MAIN + LAUNCHER activity. The cite `:111-120` → `:143-158`.

**D10 — SHOULD-FIX — l.223-224, 325, 489: the adversarial list holds only the provider scope.** Phase 17's doc defers to this phase (its l.159, 303, 491-492, 723).
- Fix, the GATE list becomes:
  - (a) `MANAGE_EXTERNAL_STORAGE` against every exported component: re-run `UriAccessRulesTest`, `ViewerRulesTest`, the `PlayerRules` and `CaptureOutputGuard` tests and phase 17's refused-caller emulator rows on this build, plus `tiles/api/ImageIngest.kt`'s authority rule;
  - (b) the FileProvider;
  - (c) zip extraction;
  - (d) delete / purge / `empty(volume)`;
  - (e) FilesActivity's `path` extra.
- The provider guard is at `getUriForFile` only, while a `root-path` provider serves any path to a grant holder. Add: "the FileProvider subclass repeats the canonical check in `openFile` / `query`", which also closes check-then-use.
- Missing row promised by phase 17 l.491-492. Add to E9: "RECORDED: with All-files held, Photos' delete of `qa-photo-1.png` — whether MediaProvider's consent dialog shows (`record photos_delete_dialog <shown|skipped>`)."

**D11 — SHOULD-FIX — l.199-201, 348, 558-562: the Recent query.**
- MediaStore `Files` under All-files access returns folders and files in hidden trees (media type none); it has no "under `.nomedia`" column.
- Fix: "`MIME_TYPE IS NOT NULL`; rows whose path has a dot-segment, or whose ancestors up to the volume root hold `.nomedia` (checked by path, cached per folder), are dropped; D4's temps are dot-named so they drop too."
- E14's "written by `adb shell`, no scan → absent" is unsafe: a shell write goes through FUSE, and MediaProvider inserts rows on create for non-bypassing callers (from memory of MediaProvider's source; unverified). Make that clause RECORDED and keep the `.nomedia` absence asserted.

**D12 — SHOULD-FIX — l.160-162, 240-242, 317, 587: FilesActivity's launch form and the picker are unstated.**
- Fix: "FilesActivity: `singleTask`, `taskAffinity app.tileshell.files` (as `AndroidManifest.xml:451-457`); one activity for every page — the picker is a page of it, so L14-2 (`start/BackHistory.kt:39-40`) counts one catalog class. `page` / `path` extras are read in `onCreate` and `onNewIntent`, only choose what is shown, are ignored unless canonical under a mounted volume, and RESET the in-app history."
- Without the reset, E17's "Back returns to Voice Recorder" fails when Files was already open, under Y3's history rule.
- "Back in the picker walks its own history, then acts as ✕."
- Task 13 fits the recorder as built: a `FlyoutItem` beside `RecorderActivity.kt:380-382`.

**D13 — SHOULD-FIX — l.108-116, 471-473, 622: two premises rest on the wrong evidence.**
- (a) Virtual disk. Add task 0: "At build start run `sm set-virtual-disk true` / `sm partition disk:<id> public`, and record the volume's fs type and that `getStorageVolumes()` lists it. If no public volume mounts, STOP and ask — the removable legs of E2, E4b, E7 and the FAT / unmount edge cases are not dropped and not moved to P2 silently."
- (b) l.114's `ls` ran as the shell uid, which proves nothing for the app. Add: "Task 0 logs `File("/storage/emulated/0/Android/data").list()` from the app. If names are returned, E5 stands. If null or own-dir only, the page shows one line `Android doesn't let apps see other apps' folders here` and E5 asserts that."

**D14 — NOTE.**
- Cites: l.321-322 `:162` → `:246-250`, `:261` → `:345-348`.
- l.5 `depends-on` lacks 15 (task 13, E17, `fill_volume`) and 13 (`record`).
- l.360-362: the helpers exist in `qa/phase-03/scripts/lib.sh` (`:182`, `:188`, `:427`, `:439`, `:459`, `:51`) with the signatures the doc uses; say "exist" in place of "built by".
- E15's "Step 1 of 2" holds: `onboarding/WizardPages.kt:164`, with every other row granted by `provision.sh`.
- l.4 status line is from 2026-09-23.

**Checked and correctly applied:** T18-1 (apart from D3), T18-2 (apart from D6), T18-3, T18-4, T18-5, T18-6, T18-7, T18-8 (superseded in part by pass 2 — D2), T18-9 (phase 19 l.45-46, 384-385 agree), T18-10, T18-11 (apart from D10), T18-12, T15-16, C-3, C-4, C-6, C-9, C-15, C-17, C-20, C-21, C-23, C-25, C-26, C-27, C-28, C-31.

BLOCKING: 4 · SHOULD-FIX: 9 · NOTE: 1
