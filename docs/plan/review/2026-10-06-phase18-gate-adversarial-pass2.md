# Phase 18 — adversarial review, pass 2: the fixes themselves, 2026-10-06

Reviewer: one Opus subagent, adversarial brief ("break the fixes; the builders' mutation tables are marketing"). Never
Fable, never codex. Reviewed: commit 0cdc96b6 (the merge of the pass-1 fixes) in its own detached worktree, no device.
"RAN" = a JVM or kernel run there; "READ" = code or library bytecode. Evidence kept on disk under
`docs/plan/qa/phase-18/GATE-adversarial-pass2/` (`AdvReview2PocTest.kt`, `poc-cf.log` rc 0, `cf.sh`, `mut.py`,
`mut2-results.tsv`, `mut2-<id>.log/.rc`). Pass 1: `2026-10-06-phase18-gate-adversarial.md`.

## Pass-1 findings: status

| Finding | Status | Why |
|---|---|---|
| H1 (L18-1, Music) | CLOSED for URI injection | Every Media3 and legacy item route ends in onAddMediaItems; the uid comes from Binder; no sharedUserId; minSdk 34 (READ). A player wedge remains (N3). |
| H2 (L18-2, Alarm) | CLOSED for the ringtone string | 19 crafted forms RAN, none bypass. The same patch left the exported editor path crashable (N1). |
| H3 (Replace) | CLOSED in code, not by tests | No path removes a displaced item outside the bin; two destructive mutants pass (R4, R5); a cancelled Replace has side effects (N5). |
| M4 (`.Tessera`) | PARTLY | The rule compares text; a case-folding filesystem compares by its own folding (N2, RAN). |
| M5 (restore targets) | PARTLY | One function for display and target, mutants red; the "not Android/" clause falls to N2; the row text keeps bidi and zero-width characters (N9). |
| M6 (provider) | PARTLY | The pure rule and the instance side are held; the device half is pinned by nothing (W1–W3, one of them a read-write open). |
| M7 (survivors) | PARTLY | Pass 1's are killed; 12 of 45 new mutants survive. |
| L8 | CLOSED for FilesActivity | The same raw-text lines remain in Music, Clock and the alarm handler (N10). |
| L9 | CLOSED for FilesActivity only | Other exported activities in the HOME process read extras unguarded (N1, N4). |
| L10 | CLOSED for zips | The same class of bug is in the bin index reader (N8). |
| L11 | CLOSED for ASCII case | Unicode-equivalent reserved names fall under N2. |
| L12, L13 | CLOSED | L12 as far as it can be (check-then-use needs a symlink, which FUSE and FAT lack). |

## New findings

**N1 HIGH — any app, with no permission, crashes the HOME process through ClockActivity** (phase 15's part).
`clock/ClockActivity.kt:283` (`api.getIntArray(API_DAYS)?.map { DayOfWeek.of(it) }`), reached from `route(intent)` at
`:213` and `:247` with no catch; the activity is exported with no permission, in the main process. A foreground app
starts it with `api_edit` = Bundle{kind="alarm", days=intArrayOf(99)}: DateTimeException on the main thread, the launcher
dies, repeatable. RAN (the exception); READ (the call sites). The L18-2 patch re-weighed only the sound in this block:
hour, minute and message from the bundle still skip `AlarmApiRules.parse`, and no SET_ALARM permission is needed to open
the editor prefilled. **Must guarantee:** no intent can throw in `route`, and the bundle is either unreachable by
strangers or passes the API handler's validation.

**N2 HIGH if the device folds names as the kernel's casefold tables do (device experiment 1), else MEDIUM — the
`.Tessera` and `Android/` rules are text comparisons the filesystem does not share.** `files/FilePaths.kt:127-136`
(`inShellDir`), `files/RecycleBin.kt:297-300` (`underAndroid`), `:349` (`reserved`), `files/FileShareGuard.kt:47-52`,
`files/FilesNav.kt:200`. RAN on a casefold tmpfs (the utf8 tables f2fs / ext4 use on /data/media): `.Teßera`,
`.Tess<U+200B>era`, `.Tessera<U+00AD>`, `.Teſſera` and `Andr<U+200B>oid` resolve to the existing folder and `realpath`
returns the name as written, so "as written" and "as resolved" both pass. Then, on that mount: `inShellDir` false for
`<root>/.Teßera/bin`; `newFolder` there succeeds inside the real bin; `FilesNav.resolve` opens the bin as a folder page
from a launch extra; extracting `.Teßera.zip` at the root with Replace merges into the shell folder and replaces the live
index (the user's record gone); a forged record under `Andr<U+200B>oid/media/com.victim/` is honoured. With no bin yet
the extract asks nothing — pass 1's M4, reopened. On FAT (READ): trailing dots are stripped and 8.3 aliases match
(`.Tessera..zip`, `TESSER~1.zip`). Precondition as M4. **Must guarantee:** "is the shell folder / is Android" is decided
by FILE IDENTITY (each existing ancestor compared with `<root>/.Tessera` by `Files.isSameFile` or the directory's own
listed name), never by string; the same for the bin's reserved names.

**N3 MEDIUM — any app can wedge Music's player** (phase 10's part; the answer to the admitted "possible crash").
`music/MusicService.kt:203-224`. Not a process crash (Media3 catches the Throwable), but
`ExoPlayerImpl.setMediaSourcesInternal` bumps `pendingOperationAcks` and sets the source holders before it throws
IllegalSeekPositionException: the player is left with a new source list and an ack that never returns. Call:
`setMediaItems([<a real library id>, "x"], 1, 0)` — one item survives the rule, so start index 1 is out of range. The
mechanism predates the fix; the fix makes dropping easier. **Must guarantee:** when items are dropped the start index
and position are re-derived, or the whole request fails before the player is touched.

**N4 probable HIGH, unproven — L9 was fixed in one activity only** (phase 15's part). `clock/AlarmApiActivity.kt:25-36`;
`:30` `getIntegerArrayListExtra(EXTRA_DAYS)`: a list holding a Parcelable of the caller's own class should throw
BadParcelableException uncaught (the caller needs SET_ALARM, a normal permission). Device experiment 4. **Must
guarantee:** every exported activity in the main process reads extras through a reader that cannot throw.

**N5 MEDIUM — a cancelled or failed Replace has already displaced what was there.** `files/FileOps.kt:174`, `:161`.
RAN: a folder copied onto a file with Replace, cancelled mid-copy → `Cancelled`, the old file in the bin, a partial
folder in its place; a same-volume move whose source lies inside the folder being replaced → `Failed`, folder and source
both in the bin. Nothing is lost, but "a cancelled or failed copy leaves it where it was" holds only for file over file.
**Must guarantee:** the displaced item is binned only when the new one is ready to be renamed in, for folders too; a
source under the displaced target is refused up front.

**N6 MEDIUM / LOW — the Recycle Bin can be killed for a volume and Files cannot repair it.** RAN: with `<root>/.Tessera`
pre-made as a FILE, delete fails ("the bin folder cannot be made") and every Replace fails; removing the obstacle through
Files is refused three ways. Who: an All-files app, MTP, a prepared card, or N2. **Must guarantee:** a recovery path, or
a message that names the obstacle and how to remove it.

**LOW.** N7 `FileOps.kt:222-225`: renaming a link onto the file it points to passes `sameFile` and replaces the real
file (needs a symlink: not constructible on FUSE / FAT). N8 `RecycleBin.kt:245`: the index is read with `readText()`,
uncapped (a 52 MB index with 400,000 records took 2.6 s per list on the desktop; every delete reads it). N9
`FileListing.kt:54-64`: `binDetail` keeps U+202E and U+200B; the record, not the file, supplies the shown name and size.
N10: caller text goes raw into Diagnostics at `MusicService.kt:187,190`, `AlarmApiActivity.kt:24`, `:86`, and
ClockActivity's `open page=… tile=…` line (phases 10 / 15). N11 `RingService.kt:239-248`: a sound that opens but will not
decode leaves the alarm vibrating silently, no fallback (phase 15). N12: on API 35+ `initialCaller.checkContentUriPermission`
on a string-extra URI likely throws, so an external ringtone is never kept (fail-closed, unverified). N13:
`FilePaths.rename(replace=false)` is exists-then-move (a concurrent All-files writer could be overwritten). N14: Replace
no longer frees space and nothing purges the bin. N15: Rebuild(id) lets any controller play library track N and read its
metadata from the session — predates the fix; the open-session contract; the owner's decision.

## Red-proof — 45 mutants of the reviewer's own: 33 red, 12 not

Not red (each an untested guard): **R4** a same-volume move, file over file, with Replace done as `rename(replace=true)`
— the old file destroyed; **R5** `copyFile` bins the target before the copy; **S5** the nested-zip exemption matching the
journal by file name; **G2** the sweep's bin check without its "as written" half; **G3** `rename` checking the target's
volume; **W1** the provider's `CANONICAL` as `absolutePath`; **W2** the provider's `OPENER` opening READ_WRITE — a grant
holder gets a writable descriptor; **W3** `roots()` gaining `/data`; **X5** the `name` extra used without `validName`;
**A6** any `content://settings/system/<name>` kept; S1, S2 (equivalent while a volume root is canonical). Table:
`GATE-adversarial-pass2/mut2-results.tsv`.

Tests that pin a defect or one side: FilesProviderWiringScanTest stops at `companion object {` (it cannot see the opener,
the canonicaliser, the roots or `AndroidOpenedFile` — hence W1–W3); the "under any case" tests use ASCII only (N2);
AlarmRingtoneRulesTest pins that a caller-readable external ringtone is kept by the handler and then dropped by the
editor when SKIP_UI is absent; no test covers a cancel or failure after displacement, or ClockActivity's bundle fields
other than the sound.

## Device experiments (the safe outcome stated)

1. N2: `mkdir -p /sdcard/.Tessera/bin`, then `ls -d` of `.Teßera`, `.Tess<U+200B>era`, `Andr<U+200B>oid` under /sdcard.
   Safe: three "No such file". If any resolves: push `.Teßera.zip` holding `bin/.index.json`, extract in Files — safe:
   refused. On the FAT card: `ls -d /storage/<UUID>/TESSER~1 "/storage/<UUID>/.Tessera."`.
2. N1: from qa-capture, start ClockActivity with `api_edit` = Bundle{kind=alarm, days=[99]}. Safe: no crash, the
   launcher's pid unchanged.
3. N3: the l18_1 probe sends `setMediaItems([valid id, "x"], 1, 0)`, then play / pause from Music's own UI. Safe: the UI
   and `dumpsys media_session` still follow the player.
4. N4: SET_ALARM with EXTRA_DAYS = a list holding qa-capture's own Parcelable. Safe: refused or default, no crash.
5. N5: copy a folder onto a same-named file, Replace, Cancel. Safe: the file is still in place.
6. R4: move file `a` onto file `a` in another folder of the same volume, Replace. Safe: the old `a` is a bin row.
7. N11: an alarm whose sound is a zero-byte .mp3 rings. Safe: the default sound plays.

## The lead's triage (2026-10-06)

- **Phase 18's own, fixed in a second round on the fix branch** (each with tests that fail first, every surviving mutant
  killed): N2 (identity, not text), N5, N6, N8, N9, N13 where closable, R4, R5, S5, G2, G3, W1–W3 (the provider's device
  half pinned; the opener read-only by test), X5.
- **Built phases' parts — Blocked-on ledger:** **L18-3** (phase 15: N1 and N4, the exported Clock activities throw on
  hostile extras and skip the API's validation; with N10's and N11's clock lines and A6) and **L18-4** (phase 10: N3, the
  player wedge; with N10's Music lines). N15 is put to the owner as it stands (no change proposed).
- A third pass, limited to the round-2 fixes, closes the review (the cap of three).
