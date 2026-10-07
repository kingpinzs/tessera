# Phase 18 — the adversarial review of the GATE list (r3 D10), 2026-10-06

Reviewer: one Opus subagent, adversarial brief (assume the authors are wrong; default verdict "not proven"; independent
red-proof and mutation checks required). Never Fable, never codex. Reviewed: commit 80810722 in its own detached worktree
(`~/projects/metro-launcher-p18-review`), no device. "RAN" = a JVM run there; "READ" = code or library bytecode. Its
mutation results, logs and proof-of-concept test are kept on disk under `docs/plan/qa/phase-18/GATE-adversarial/`
(`mut-results.tsv`, `mut.py`, `mut-<id>.log/.rc`, `AdvReviewPocTest.kt`, `poc2.log` — rc 0, 10 PoC tests, each asserting
the UNSAFE outcome). The lead read H1's and H2's code lines and confirms them as quoted.

## Verdicts

| Item | Verdict | Why |
|---|---|---|
| (a) All-files access against every exported component | **VULNERABLE** | SET_ALARM's ringtone string reaches RingService unvalidated and is opened with the shell's identity (H2). The viewer, the player, the capture guard and Live Tile ingest showed no new reach (READ). |
| (b) the FileProvider | **NOT PROVEN** | The pure rule is real (red-proofed), but no test reaches the provider class: `query` with its scope check removed passes all 1,922 tests (M6). No exploit found. |
| (c) zip extraction | **VULNERABLE** | `..` / absolute / bomb / space guards hold (red-proofed), but a zip named `.Tessera.zip` at a volume root extracts into the shell folder and writes the bin index (M4, RAN). The speech models' unzip is safe without the platform validator (an APK asset, sha256-pinned, its own canonical check, the `:speech` process). |
| (d) delete / purge / empty / restore / sweep | **VULNERABLE** | A forged `.index.json` steers Restore anywhere under the volume (M5), and one "Replace" permanently deletes a folder tree (H3, RAN). The sweep and the journal are PROVEN SAFE (private filesDir; four guards red-proofed). |
| (e) FilesActivity's extras | **NOT PROVEN** | Nothing an intent carries acts on a file or reaches an opener (READ); open: unsanitised diagnostics text (L8) and a probable parcel crash (L9). FilesOpenReceiver is fine (explicit, unexported; a fill-in cannot replace an existing extra). |
| (f) Music's play extra | **VULNERABLE** | The launch, onNewIntent and PLAY_FILE rules are sound, but any app's Media3 controller can hand MusicService a MediaItem carrying its own URI and it is played as-is (H1; READ, library bytecode). |

## Findings

**H1 (f) — any app can make Music play and probe any file.** `music/MusicService.kt:197-202` (phase 10's part; commit
0472d121, before this phase). `onAddMediaItems` returns the items untouched when every item has a `localConfiguration`.
Media3 1.9.0's `MediaSessionStub.setMediaItem*` builds items with `MediaItem.fromBundle`, which reads the local
configuration; a hand-built bundle (`toBundleIncludeLocalConfiguration()`) carries one. An app with no permission binds
the exported service, is accepted with default commands, and sends an item with `file:///storage/emulated/0/<anything>`,
`file:///data/user/0/app.tileshell/files/…`, `content://app.tileshell.files/root/…` or `http://…`; the player
(`DefaultMediaSourceFactory`) opens it with the shell's identity. Gain: playback aloud, plus tags, artwork, duration and
an error class that tells "missing" from "not media" (an existence oracle for private and shared paths). All-files access
widens it from media files to everything on every volume. No test covers it. **A fix must guarantee:** an item from a
controller that is not the shell's uid never keeps a caller-supplied URI.

**H2 (a) — any app holding SET_ALARM (a normal permission) can make the alarm play an arbitrary file.**
`clock/AlarmApiRules.kt:70`, `clock/AlarmApiActivity.kt:33-41`, `clock/RingService.kt:277` and `:242` (phase 15's part;
commit 77937452). `else -> AlarmSound(Kind.TONE, ringtone, null)` keeps any string; with SKIP_UI the alarm is created
with no tap; RingService then opens it (`openFileDescriptor`, `MediaPlayer.setDataSource`), which opens `file:` and the
shell's own unexported provider. RAN: `parse(SET_ALARM, …, skipUi=true, ringtone=…)` keeps
`file:///storage/emulated/0/Documents/private-memo.m4a`, `file:///data/user/0/app.tileshell/files/anything` and
`content://app.tileshell.files/root/…` verbatim. `AlarmApiRulesTest.ringtoneMapsToTheSound` PINS "any string becomes
TONE". This phase adds every non-media file plus the new provider to what the sink can open. **A fix must guarantee:** a
ringtone from the API is a URI the caller could read itself (the UriAccessRules question), never `file:` and never a
shell authority.

**H3 (d) — "Replace" destroys a folder tree outright, bypassing the bin.** `files/RecycleBin.kt:160-164`,
`files/FileOps.kt:144` (phase 18's own). RAN: an index record forged to `<root>/DCIM`, Restore with Replace leaves DCIM
as a 5-byte file and none of its photos anywhere; and with NO forgery, copying a file named `notes` onto the folder
`Docs/notes` with Replace deletes the folder's contents for good. Contradicts FileOps' own rule 5 ("a delete goes to
the bin, never straight off the disk"). **A fix must guarantee:** no conflict answer removes a directory tree without
the bin, or without a question that names it as a folder with its contents.

**M4 (c / d) — the write layer has no rule about `.Tessera`; only the UI's not listing it protects it.**
`files/ZipWrites.kt:38`, `:69`; `files/FileOps.kt:68-231` (only `binDelete` asks `inShellDir`). RAN: `.Tessera.zip` at a
volume root with no bin yet extracts with no question, creating `.Tessera/bin/.index.json` and a bin file — the bin page
lists "holiday.jpg" and Restore moves it to `Android/media/com.victim.app/holiday.jpg`; with a bin present, Replace
overwrites the live index; `copy(.., binDir, REPLACE)`, `newFolder` and `rename` inside `.Tessera` succeed. Precondition:
the zip sits at a volume root (the user or an All-files app can arrange it; an unprivileged app cannot). **A fix must
guarantee:** no FileOps write has a source, destination or resulting path inside the shell folder, compared ignoring case.

**M5 (d) — a forged index sends Restore to any path under the volume, creating folders, unasked.**
`files/RecycleBin.kt:143`, `:254-258`. RAN: a restore lands in `Android/media/com.victim.app/config/a.txt`;
`<root>/stray/../Pictures/a.txt` is honoured and leaves a stray folder. The bin row shows only the forged name, never
the destination. The index's writer needs All-files access or M4, so the gain is steering the user's own tap; H3 makes
it destructive. **A fix must guarantee:** the user sees where a restore will land, or the record is authenticated (kept
or MAC'd in private storage).

**M6 (b) — FilesProvider's wiring is held by nothing.** `files/FilesProvider.kt:106-129`. RAN: `query` rebuilt from
`pathOf` with no scope check → rc 0, 1,922 tests, 0 failures. The rule serves `.Tessera/bin/*` and
`Android/data/app.tileshell/*` as ordinary volume files (safe only because nothing hands out such a URI). The
canonicalise-then-open TOCTOU is unclosed as admitted; not constructible on FUSE / FAT (no symlinks) — a device
assumption, untested.

**M7 — contracts with one side untested** (the surviving mutants below): FileOps rule 1 for rename, newFolder and the
zip source; `inShellDir` ignoring case; the journal's bin check ignoring case; the service's PLAY_FILE gating;
`restorable`'s root and name clauses.

**L8 (e)** `files/FilesActivity.kt:77` writes the `page` extra raw into Diagnostics: any app can forge `[files] …` lines
(newlines, up to binder size) in the evidence QA rows read. **L9 (e)** `files/FilesActivity.kt:128-133` reads extras with
no `runCatching`: a `page` extra holding a custom Parcelable should throw in onCreate and kill the main (HOME) process.
**L10 (c)** `files/ZipArchive.kt:94-100`: a 256 MB central directory is read into one array plus an object per entry;
OutOfMemoryError is not caught — a zip of tens of MB with over a million entries should kill the process on tap.
**L11 (d)** `files/RecycleBin.kt:251`: `name !in RESERVED` is case-sensitive (`.INDEX.JSON`, `.NOMEDIA` on a
case-folding volume). **L12 (d)** `RecycleBin.binDir` is never canonicalised: "shared storage holds no symlinks" is
load-bearing for `empty()` and `purge()`. **L13 (c)** duplicate names, a file-then-directory pair or a backslash name on
FAT fail the whole extract (availability only).

## Red-proof (mutate, run the named class, restore) — 24 mutations, 23 went red

Red: FileShareGuardTest ×2, FilesProviderRulesTest ×2, MusicPlayExtraTest ×2, FileOpsZipTest (the `..` check, the bomb
limit, the 50 MB margin), FileOpsBinTest ×5, FileOpsJournalTest ×4, UriAccessWiringScanTest ×2, TrustWiringScanTest ×2,
FilePaceRulesTest ×1. **Not red:** FileOpsZipTest with only the canonical second half of the entry-name check dropped
(rc 0, 23 tests). Table: `GATE-adversarial/mut-results.tsv`.

## Surviving mutants (rc 0, 0 failures) — each is an untested guard

- `FilesProvider.query` without its scope check (whole suite).
- `MusicService.onConnect` offering PLAY_FILE to every controller; `MusicService.playFile` resolving the URI by hand
  instead of `FilesProvider.fileFor`.
- `FileOps.rename` / `newFolder` without `volumeFor(..) ?: throw Refused(OUTSIDE)`; `ZipWrites.extract` without
  `ops.volumeFor(zip)`.
- `FilePaths.inShellDir` case-sensitive; `OpsJournal.inBin` case-sensitive.
- `RecycleBin.restorable` without the "not the volume root" clause, and without `validName(target.name)`.
- `RecycleBin.delete` comparing taken names case-sensitively (the test named for it passes anyway); `RecycleBin.load`
  de-duplicating case-sensitively.
- `FileShareGuard.allowed` with the root not canonicalised; `FilesProviderRules.serve` re-resolving (`canonical(path)`),
  which brings the TOCTOU back; `pathOf` without its NUL check; `FilePaths.isTempName` without the leading dot.

Tests that PIN a defect: `AlarmApiRulesTest.ringtoneMapsToTheSound` (H2). No test covers Replace over a directory (H3)
or a forged in-volume restore path (M5).

## The debug-only QA switches

Proven by reading and red-proofed: a release build cannot read them (`QaBases.read` returns null unless
`BuildConfig.DEBUG`; `FilePace.rate` / `searchRate` return null unless their `debug` argument, passed as
`BuildConfig.DEBUG` at both sites), and in a debug build the value is only ever a sleep or a rate cap.

## Device experiments owed (the safe outcome stated)

1. H1: from qa-capture, a Media3 controller to `app.tileshell/.music.MusicService` sending `setMediaItem` with a bundle
   that includes the local configuration for `file:///storage/emulated/0/QA-Files/hidden/qa-hidden.mp3`; again with a
   missing path and a `.txt`. Safe: nothing plays, no metadata, the same error for all three.
2. H2: `am start -a android.intent.action.SET_ALARM … --ez android.intent.extra.alarm.SKIP_UI true --es
   android.intent.extra.alarm.RINGTONE file:///storage/emulated/0/QA-Files/hidden/qa-hidden.mp3` (and the
   `content://app.tileshell.files/root/…` form). Safe: refused, or the ring logs the default sound.
3. M4: push a `.Tessera.zip` holding `bin/.index.json` and a bin file to `/sdcard/`, extract it in Files. Safe: refused;
   the bin page unchanged.
4. H3: delete the file `/sdcard/QA-Files/n`, create a folder `n` with files, Restore, Replace. Safe: the folder's files
   survive or are in the bin.
5. M6: `ln -s` on shared storage from the shell and from an app in its own `Android/data/<pkg>/`. Safe: both fail.
6. M6: `content read` on a private path, and a qa-capture grant with a rewritten path. Safe: both refused (the first was
   seen at task 4: "not exported").
7. L8: FilesActivity started with a `page` extra holding a newline and a forged `[files]` line. Safe: one line.
8. L9: FilesActivity started with a custom Parcelable as `page`. Safe: no crash.
9. L10: a crafted zip of about 60 MB with 1.3 million entries, tapped. Safe: "can't be opened", the process alive.
10. L11: a record `{"bin": ".NOMEDIA"}`, the bin opened. Safe: not listed.

## Housekeeping the reviewer reported

Its worktree was clean at the end. While checking `sdk.dir` it printed this worktree's `local.properties` (which holds
the TMDB read token) into its own tool output — read only, nothing written or copied elsewhere.

## The lead's triage (2026-10-06)

- **H1 and H2 are defects in BUILT phases' parts** (phase 10's MusicService; phase 15's AlarmClock API handler), widened
  by this phase's grant. Out of phase 18's scope: INDEX Blocked-on ledger rows **L18-1** and **L18-2**; row 18 cannot go
  `done` past them; the fix is planned with the owner.
- **H3, M4, M5, M6, M7, L8–L13 are phase 18's own** and are fixed at the root with their tests (every surviving mutant
  killed), after the row writers hand the emulator back — a fix changes the APK, so each fix re-runs the rows it touches.
  M5's form (show the destination, or authenticate the index) is a question for the owner.
