# Phase 18 — adversarial review, pass 3: round 2's fixes, 2026-10-20

The ONE adversarial pass the one-round rule allows over round 2 (merge 5ba4238c: a868d610b files identity / staged
Replace / BinObstacle / no-replace rename; bfd6b8ec1 the provider's read-only opener; adc17689f L18-3 Clock; c8578bc04
L18-4 Music), on Jeremy's Q-18-9 (a) of 2026-10-20. One Opus reviewer, adversarial brief (assume the author wrong,
default NOT PROVEN, independent red-proof, bounded mutants, untested halves), code and JVM only — no device. Worktree
`.claude/worktrees/p3-review` detached at 5ba4238c6, restored clean at the end (`git status --porcelain` empty). Raw
evidence (on disk, git-ignored): `qa/phase-18/GATE-adversarial-pass3/` — mutants.tsv, redproof.tsv, probes-output.txt,
the adapted red-proof tests and probe tests as .kt.txt, media3-legacy-skip-bytecode.txt, runs/ (every gradle log with
.rc and .summary; baseline 263 round-2 tests / 0 failures; files-pkg baseline 516 / 0).

## Verdict per commit (the reviewer's)

- **a868d610b — NOT PROVEN.** Identity rules hold on the FoldingVolume stand-in (new identity tests, adapted to
  compile, RED 9/10 against pre-fix); 23 of 28 mutants of its guards caught. The N5 "source inside the target" guard
  still compares text (P3-3); the "originals removed only afterwards" half is pinned by no test (P3-2); whether
  `FileIdentity.Real.same` (`Files.isSameFile`) sees folded names as one file on FUSE / vfat (D2) and whether
  create-then-rename works through MediaProvider's FUSE rename (D3) only the device can answer.
- **bfd6b8ec1 — PROVEN.** RAN: P3M27 (read-write by literal) and P3M28 (opening a non-file) killed by
  FilesProviderRulesTest. READ: `openFile` is the only opener; insert / update / delete throw (FilesProvider.kt:218-230);
  ContentProvider's asset-file defaults route through `openFile` (platform knowledge, not checked in this repo). It closed
  a test gap, not a defect: the pre-fix OPENER was already MODE_READ_ONLY (READ 5ba4238c^1 FilesProvider.kt:233-234).
- **adc17689f (Clock, phase 15) — NOT PROVEN.** The pure layer is solid (ClockIntentsTest; 4/4 mutants killed); the
  activity side is held only by source-text scans; a relaunch weighs a stranger's intent against the INITIAL launcher's
  uid (P3-4, READ); pass 2's device experiments 2 and 4 unrun on this build.
- **c8578bc04 (Music, phase 10) — NOT PROVEN.** `MusicQueueStart` is right (13 tests, 3/3 mutants killed); the
  service wiring is held only by text scans; experiment 3 unrun; the class it set out to close (an out-of-range index from
  a controller reaching ExoPlayer) stays open through the legacy queue path (P3-1).

## Findings (the reviewer's), most severe first

**P3-1 HIGH if D1 confirms — any app, no permission, crashes the HOME process through Music's legacy controller**
(phase 10's part; predates round 2). READ (Media3 1.9.0 bytecode): `MediaSessionLegacyStub.onSkipToQueueItem(long)`
refuses only `queueId < 0` (`lcmp` / `ifge`), then `seekToDefaultPosition((int) queueId)` (`l2i`); `ExoPlayerImpl.seekTo`
returns on −1 (C.INDEX_UNSET), else `checkArgument(index >= 0)`; the legacy dispatch lambda's exception table catches
RemoteException only. MusicService is exported with the MediaBrowserService filter, in the main process
(AndroidManifest.xml:143-151, no `android:process`); `onConnect` accepts every controller; KnownDurationPlayer overrides
no seek. Scenario: a framework MediaBrowser / MediaBrowserCompat connects and calls
`transportControls.skipToQueueItem(2147483648L)` → `seekToDefaultPosition(Int.MIN_VALUE)` → IllegalArgumentException on
the main looper → the launcher dies, repeatably. MusicQueueStart covers only set and add. **Must guarantee:** no index a
controller supplies (legacy or Media3) reaches the player unless it is in [0, count) — e.g. the session's player wrapped
so an out-of-range seekTo / seekToDefaultPosition is ignored; a test feeding the legacy values 2^31, 2^32−2, 2^32−1.

**P3-2 MEDIUM — a destructive mutant survives all 516 tests: a cross-volume folder Replace may delete the user's
originals** (phase 18). FileOps.kt:216-226. RAN P3M36 (the moved folder's originals removed BEFORE `displace(target)` and
the rename-in): 0 of 516 tests fail. RAN probe P5 (card `album/{1.jpg,2.jpg}` moved onto the file `Download/album` with
Replace, `.Tessera` a file so the bin cannot be used): merge → Failed with the obstacle message, originals intact, old
file intact; P3M36 → same message, originals `[]`, and the copy is deleted by the `finally` — the photos are gone. P3M22
was killed only by accident. **Must guarantee:** a test where displacement or the rename-in fails after the fill of a
cross-volume folder move, asserting the originals and the old item both intact.

**P3-3 LOW — the N5 "inside what it would replace" check compares text** (phase 18). FileOps.kt:558-562 (`inside` via
`canonicalOf`), used at :167 and ZipWrites.kt:165. RAN probe P1 (link `DOCS -> Docs`, a canonicaliser keeping names as
written): moving `Docs/notes/notes` into `DOCS` with Replace → `Failed(NoSuchFileException)`, folder and source in the bin
— identical to pre-fix (RAN on 5ba4238c^1); through the real name it is refused correctly. On the device the alias is a
case variant (a launch extra resolves to the as-written name, FilesNav.kt:199; FAT folds case). Nothing lost (all in the
bin), but the guard does nothing there. **Must guarantee:** decided by identity — each ancestor of the source compared
with the target by `FileIdentity.same`.

**P3-4 LOW — a ClockActivity relaunch honours a stranger's `api_edit`** (phase 15). READ: `onNewIntent` →
`setIntent(intent)` (ClockActivity.kt:250); `onCreate` routes `intent` with `launchCaller = getLaunchedFromUid()`, the
app that INITIALLY launched the activity (:216) — the shell when Clock was opened from Start; ActivityThread keeps the
`setIntent`'d intent across a relaunch (platform knowledge); `configChanges` (manifest:184) omits locale,
fontWeightAdjustment, keyboard and others. Scenario: Clock opened from Start; any app sends `api_edit` (ignored); the user
changes language or toggles bold text; the relaunch routes the stranger's bundle as the shell's and the editor opens
prefilled. Bounded: the fields still pass AlarmApiRules' checks, nothing is stored until Save — the editor opened without
SET_ALARM. **Must guarantee:** an intent's `api_edit` is weighed against the sender of that very intent on every path,
a relaunch included.

**P3-5 LOW (performance) — file identity walks every ancestor on every call** (phase 18). RAN P4: `inShellDir` 13 µs
per call pre-fix, 46 µs on the merge (depth 10, host ext4); FilesState.kt:658 (the search walk) calls it for every folder
entered, against `isShellDir`'s own doc (FilePaths.kt:199-201). RAN P2: the bin index read 4.7 s pre-fix → 8.7 s on the
merge (300 forged records, depth 900): N8 got worse; on FUSE each stat is a MediaProvider round trip. **Must
guarantee:** a walk uses `isShellDir` per folder entered; record validation capped (N8) and the cheap
`canonical(path) == path` before any identity walk.

**P3-6 LOW — side effects of making `.Tessera` before anything at a volume root (anchor)** (phase 18). FileOps.kt:488-497,
RecycleBin.kt:336-341. RAN P3: a rename `a.txt` → `b.txt` at the card root now leaves `<card>/.Tessera` (pre-fix it did
not); on a read-only root both New folder and the rename of a gone file say "the shell's own folder cannot be made on this
storage" (pre-fix "IOException" / "zz-missing is gone"); the same refusal would hit every new name at a full volume's
root (READ). `SHELL_UNPROVEN` has no test (grep over app/src/test: none). **Must guarantee:** an owner decision on the
footprint; the refusal tested; its message only when the anchor is the obstacle.

**P3-7 LOW — on API 34, the shell's own editor hand-off is dropped whenever Clock's task is alive** (phase 15). READ:
AlarmApiActivity.kt:97-103 starts it with FLAG_ACTIVITY_NEW_TASK; the activity is singleTask (manifest:182); it arrives
through `onNewIntent`, where ClockActivity.kt:252 routes it with NO_CALLER. "Set an alarm for 6:45" without SKIP_UI from
another app shows the Alarm tab with no editor. AlarmSoundWiringScanTest pins this line as correct. **Must guarantee:** an
owner decision, or the handler launches with CLEAR_TASK.

**P3-8 LOW — guards with no test (survivors):** P3M20 `inside()` failing open when the path cannot be resolved; P3M26 the
no-replace rename's cleanup deletes a placeholder another writer has filled; P3M15 delete's re-check after `mkdirs`; READ:
a kill between the exclusive create and the rename (FilePaths.kt:348-351) leaves an empty file or folder under the
target's name, not journalled. P3M01 / P3M02 / P3M11 (the as-written / as-resolved pairs) and P3M09 (`isVolumeRoot` by
identity) are equivalent on FUSE / FAT.

## Red-proof (pre-fix main from 5ba4238c^1 with the new tests; redproof.tsv)

| Test | Against the pre-fix source |
|---|---|
| AlarmSoundWiringScanTest (4 changed / new) | RED 4/4 — text scans only |
| UriAccessWiringScanTest (7 changed) | RED 7/7 — text scans only |
| FileOpsBinTest (1 changed) | RED on message text only ("fails and the file stays" was already true) |
| FilesNavTest (2 call sites) | GREEN — a signature-only edit |
| ClockIntentsTest (new) | does not compile (a new module; the pre-fix logic lived in ClockActivity) — bridged only by the text scan |
| MusicQueueStartTest (new) | does not compile; the pre-fix bug is in Media3's default — bridged only by text forms |
| FileOpsBinHardeningTest | does not compile (BinObstacle); a message-only change |
| FilesProviderRulesTest (1 new) | does not compile; mutant-killing; the pre-fix opener was already read-only |
| FileOpsDisplaceTest (new) | adapted: RED 4/7 (folder-over-file cancel, cross-volume cancel, move-from-inside, zip over a holder), GREEN 3/7 (mutant-killers: R4, R5, folder-over-file Done); the 3 N13 tests need the new gap hook |
| FileOpsIdentityTest + FoldingVolume (new) | adapted: RED 9/10 (Kotlin's `equals(ignoreCase)` already maps ſ to S, so `.index.jſon` was refused pre-fix) |

Mutants: 28 caught of 36 made (mutants.tsv; files.*, clock.*, music.* and UriAccessWiringScanTest, 516 tests). Not caught:
P3M01, P3M02, P3M09, P3M11, P3M15, P3M20, P3M26, P3M36 — P3M36 the only destructive one.

## Device experiments (the reviewer's; safe outcome stated)

1. P3-1: a legacy MediaBrowserCompat / MediaControllerCompat probe → `skipToQueueItem(2147483648L)`, then `4294967294L`.
   Safe: the launcher's pid unchanged. Control `4294967295L` (−1) ignored.
2. N2 identity on FUSE and vfat: `mkdir -p /sdcard/.Tessera/bin`, `stat -c '%d:%i %n'` on `.Tessera`, `.Teßera`,
   `.Tess<U+200B>era`, `.TESSERA`. Safe: each "No such file" or the same dev:inode. Then New folder `.Teßera` at the root:
   "inside the shell's own folder". On the card too, with `TESSER~1` and `.Tessera.`.
3. A no-replace rename through MediaProvider (file, folder, move into a folder; /sdcard and the card): each Done, no 0-byte
   leftover, MediaStore has one row for `b.txt` and none for `a.txt`.
4. P3-4: Clock from Start; the probe sends `api_edit = {kind=alarm, hour=6, minute=45}` (ignored); then
   `settings put secure font_weight_adjustment 300` (restore 0). Safe: no editor, and the `[clock] open` line after the
   relaunch does not say `api=alarm`.
5. P3-6: rename a file at the card root: `.Tessera` exists (by design), hidden in Files, no MediaStore row.
6. Staged Replace under a kill: a 200 MB+ folder onto a same-named file, Replace, kill mid-copy. Safe: after the restart
   the old file in place, no `.album.*.part` once Files opened, the source intact.
7. P3-5 (optional): a search over ~5,000 folders, this build against the pass-2 build.

## The lead's check (2026-10-20)

- P3-2: READ 5ba4238c FileOps.kt `staged()` — `displace(target)` and `FilePaths.rename(temp, target, …)` run inside the
  `try`, and `removeCopied(src, cross)` only after it returns: the code is right today; the finding is the missing test.
  CONFIRMED as a test gap (no live defect).
- P3-1: READ media3-legacy-skip-bytecode.txt (`lcmp` / `ifge 7` at onSkipToQueueItem; `l2i` then
  `PlayerWrapper.seekToDefaultPosition:(I)V`; the dispatch's exception table names RemoteException only); manifest
  :143-151 (exported, MediaBrowserService filter, no process). HEAD (after phase 20's MusicService changes):
  `KnownDurationPlayer` (a ForwardingPlayer) still overrides no seek (grep `seekTo` over music/*.kt: none in it). NOT
  PROVEN until device experiment 1 runs; if it crashes, CONFIRMED HIGH.
- The rest is taken as the reviewer reports it until its device experiment or its fix's red test shows otherwise.

## The lead's triage (2026-10-20)

- **Phase 18's own — in this session's one fix round** (Q-18-9 (a)): P3-2 (the test), P3-3, P3-5, P3-6's message and
  test (the footprint itself is Jeremy's: NEEDS-HUMAN H2 already carries "an empty .Tessera may appear at a volume root
  before any delete"), P3-8 (P3M20, P3M26, P3M15, the unjournalled placeholder; P3M01 / 02 / 09 / 11 equivalent, no
  action). Sent to the files fix builder.
- **Older phases' parts (P3-1 phase 10; P3-4, P3-7 phase 15):** the one-round rule puts them on INDEX's Blocked-on ledger,
  not fixed here unless Jeremy says so. P3-1 is first run on the device (l18_4.sh leg b), P3-4 too (l18_3.sh leg e); the
  question to Jeremy rests on what they show.
- Device experiments 2, 3, 5 and 6 are legs of r2_exp.sh; 7 is dropped (P3-5's fix pins the walk's form instead).
