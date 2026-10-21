# Phase 18 — NEEDS-HUMAN rows (Files)

Hard Rule 15: anything only Jeremy can verify gets a row here, and row 18 goes `done` only on his sign-off of every H
row and every P row. Nothing below is a defect found and left; each H row is a design choice or an approximation the
emulator cannot settle, recorded with what it came from and where to look. The phase doc (docs/plan/phase-18-files.md,
FINAL) carries the reasoning; INDEX row 18 and its Change Log carry the runs.

**Which build.** The emulator rows ran on the gate builds named in qa/phase-18/GATE-BUILD/gate-build.tsv. The last is
**8d35f95e** (commit 0522490db, 2026-10-21): the one fix round of 2026-10-20 (gate build 23fe18a8, every owed
row passing on it) plus the two LOW residuals and ledger L18-6. The phone's CI build comes from GitHub's
`main`, which today holds round 2 (5ba4238c) but NOT the fix round. The fix round reaches the phone only after
`phase-18` is pushed and merged, which is your call; the end of this sheet lists what that push carries. P1–P5 and H1,
H3, H4, H6, H7 and H8 read the same on either build. H2, H5 and H9 name what the fix round changes.

## Sign-off rows (H)

| H | What Jeremy judges | Why it is his call | Where to look |
|---|---|---|---|
| H1 | *Fidelity*: Files against r11/files.md's MEDIUM values on the phone — the ≡ pane, location bar, sort line, rows' pitch, app bar positions (E11) — and pass 2's two-capture values: the cold start on Recent with the pane open, and the selection geometry (20-epx checkbox, 32-epx shift, "1 item selected", the dim bar at 0 selected) | r11/files.md has no HIGH value; MEDIUM is a match to judge, not a number to assert | E11's captures (qa/phase-18/E11/, on 87f6eac1); the phone |
| H2 | *Accept* the Recycle Bin (P4 — W10M had none, r11/files.md 1.12.8): its row in the ≡ pane, the bin page and its wording including the privacy line "Deleted files stay on this phone until you empty the Recycle Bin" (T18-10), the Delete permanently and Empty confirmations (Y1, Y6; T18-1), the stated limit that a Delete inside Photos' viewer does not pass through the bin (r3 D1). **Round 2 added:** a failure sentence naming the obstacle ("A folder named .Teßera is in the way…", "A file named .Tessera is in the way on This Device…"); "it is inside what it would replace"; "the shell's own folder cannot be made on this storage"; **an empty `.Tessera` may appear at a volume root before any delete** (the shell makes its folder first so a look-alike name cannot be made — pass 3's P3-6: the footprint is yours to accept or change); a cancelled folder Replace leaves the old item in place. **The fix round added:** a bin row shows a hidden or direction-reversing character as `?` (`invoice<U+202E>fdp.exe` → `invoice?fdp.exe`) but shows emoji names whole; a bin row's size is the file's own; an index damaged past 8 MiB / 40,000 records is rebuilt and its files listed by bin name | P4 design; no W10M source | E4b, EDGE_BIN, EDGE_BIN_PUBVOL, R2_EXP captures |
| H3 | Motion (Y5), two parts — *fidelity* for the forms pass 2 measured (pane close, folder change / ↑ / breadcrumb, list ↔ icons, entering selection, the hold menu's 700-ms delay; LOW video, one of them mouse-driven), *accept* for the stand-ins (••• expand, the sort picker's opening, leaving selection, pane open at 360 epx). **Row writer A saw:** the app bar's buttons flash dim for one frame on a folder change | LOW sources; feel | E11 frames; the phone |
| H4 | *Accept* the LOW / UNMEASURED values that remain (Y2's sort picker and its order rules, Y3's Back rule including Back in the picker) and the volume naming by label with no drive letter (T18-8) | UNMEASURED in r11/files.md | E3, EDGE_BACK captures |
| H5 | *Accept* the Move to / Copy to picker and move progress as pass 2 measured them (LOW, a camera), and the stand-ins: COPY progress in the move box's form, the percentage and Cancel in the notification (P4 — W10M had neither), the wash's colour, and the conflict / delete / rename / new-folder dialogs (Y4: R7 1.3.9's form, UNMEASURED-2). **Round 2:** a cancelled folder Replace leaves the old item exactly where it was. **The fix round:** a Replace through an alias of a folder that holds its own source is refused before anything moves; a read-only root says "IOException" for New folder again. **Recorded, not changed:** on the emulator, at the fastest re-post rate (4 a second), about 1 press in 10 on the notification's Cancel did not register. A press spanning a re-post still took 5 of 6, so no product cause is shown (INDEX Change Log 2026-10-21); P6 asks the phone | LOW / P4 | E4, EDGE_KEEP_BOTH, R2_EXP captures |
| H6 | *Accept* the unreadable-folder wording for `/Android/data` (the form build task 0 (b) picked: the entry itself carries "Android doesn't let apps see other apps' folders here"). **Row writer A saw:** the unreadable-folder reason covers the first row until tapped | the form was chosen by a probe, not footage | E5 captures |
| H7 | *Accept* the zip flows (P4 — W10M had no zip handling, r11/files.md 1.12.7): a zip opened as a folder, "Extract first" for an entry, the names (extract to `<zip base name>/`, create `<item>.zip` / `Archive.zip`, keep both `name (2).ext` — r3 D6), the "password-protected" / "can't be opened" / "storage removed" wording (T18-8) | P4 design | E13, EDGE_ZIP captures |
| H8 | *Accept* Recent: what was opened in Files (Q-18-1; W10M also counted downloads, and Android has no phone-wide list), 100 at most, with the opened-at date as the row's date (Y6) | Q-18-1's form | E14 captures |
| H9 | *Accept* three Clock changes the fix round made in phase 15's part (ledger L18-3 and L18-6). (1) An alarm whose chosen sound cannot play now rings the DEFAULT alarm sound; before, it vibrated silently. (2) "Set an alarm" from another app WITHOUT skipping the editor now opens the editor with the ringtone that app named, when Android says that app could read it — the same sound the no-editor path stores (T15-37). (3) **API 34 only** (neither the AVD nor the S25 Ultra runs it): such a request while Alarms & Clock is open now starts Clock afresh so the editor shows. Before, Clock showed the Alarm tab with no editor. The cost: whatever Clock showed (a page, an unsaved editor, an activity opened on top of it) is replaced. On the emulator the sound in (1) is read from the player and the log, because the AVD plays no audio to the PC; hearing it is the phone's. (3) is held by JVM tests only | audible; T15-37 read by the agent; (3) has no API 34 device | L18_3 legs c, d; ClockIntentsTest (handOffFresh) |
| H10 | *Rule* on N6's recovery path: when something has put a FILE named `.Tessera` at a volume's root, the Recycle Bin cannot be used there. Files now says which file is in the way and that it must be removed, but it cannot remove the file itself (removing it through Files is refused). Options: accept the message; or add a repair (Files offers to move the obstacle aside) | new behaviour, not a defect | R2_EXP leg 6 |
| H11 | *Rule* on N14: Replace sends the old item to the bin instead of deleting it, so a Replace no longer frees space, and nothing empties the bin by itself. Options: accept (empty by hand, as W10M's Explorer has no bin either way); or a size or age limit that purges the oldest | new behaviour, not a defect | — |

## Phone rows (P) — on the S25 Ultra, by Jeremy, no PC

- P1 Start settings > Setup checklist > Files: say which screen opens; turn the access on; Files lists the phone. Restart
  the phone and run Device care's optimise: Files still lists. Turn the access off: the checklist row goes red and Files
  shows its "can't see this phone's storage" message with the link. The setup-wizard step is checked on the NEXT clean
  install (it shows only on a fresh install), not by revoking.
- P2 A USB OTG drive plugged into the phone: it appears as a ≡-pane row named by its label, no drive letter (T18-8) — report
  the text; browse, copy to it, pull it mid-copy ("This storage was removed", no crash, no leftover on the phone); on an
  exFAT and a FAT32 drive, rename `a.txt` → `A.TXT` (lists once); a delete on it lands in the drive's own bin, and the
  drive pulled takes its bin rows away.
- P3 Samsung Camera's DCIM at thousands of items and the S25U's Download folder: open each in Files, then read the
  `[files] list … ms` lines from Start settings > Diagnostics and report them; the scroll is judged by eye [accept].
- P4 Secure Folder and Private Space contents are absent (their storage is another user's), and the app says nothing
  about them rather than an error.
- P5 One UI's "Open with" sheet for a file opened from Files: which apps it lists for a `.txt` and a `.pdf` — report the
  list (RECORDED, not judged). After picking one, the file is on Files' Recent page (Q-18-1).
- P6 Copy a large file (a few GB) in Files, pull down the shade and tap Cancel in the notification ONCE: the copy
  stops, and nothing is left in the destination. Do it three times; report any time it took a second tap.

## Open for your ruling outside these rows

- **A platform finding (the lead's lean: record it, change nothing).** On this AVD's `/sdcard` the kernel keeps each
  spelling of a folder name as its own cache entry. Once `.Teßera` has been looked up, removing `.Tessera` leaves
  `.Teßera` answering with the removed folder's inode, and the reverse. Shown on this AVD
  (qa/phase-18/RESUME-2026-10-20/runner2/probe-r2/stale-alias*.txt). While such a stale entry exists, the shell's
  identity checks see what the kernel says. In the fix build's first R2_EXP run, a delete was binned into a folder whose
  real name on disk was `.Teßera/bin`: the file stayed on the phone, under that folder. And "A folder named .Tessera is in
  the way" was said with nothing named so on disk. With fresh caches the shell refused the folded bin as phase 18's
  decision (7) says, in every trial. Fixing this would be a workaround at the consumer for the kernel's cache (Hard
  Rule 2), so nothing was changed; R2_EXP now drops the caches before each experiment. Your call: accept, or ask for a
  check that reads the folder's listing instead of trusting a lookup.
- **Held by JVM tests only, no device driver** (the one-round rule; recorded so nothing reads as device-checked):
  - Files N9: `?` in bin rows, in the obstacle message and in `[files]` lines; the bin size and Stats bytes come from
    the bin file.
  - Files N8: an index over 8 MiB or 40,000 records is rebuilt.
  - Files N7: renaming a link onto its target's name in another case fails "the name is taken".
  - P3-6's New folder at a read-only root says "IOException", and P3-6's rename of a gone file says "<name> is gone".
  - P3-3: Replace through an alias is refused.
  - P3-5: the search walk asks the shell-folder question once per folder.
  - L18-5: the Media3-controller index paths. Only the legacy skipToQueueItem is device-checked (L18_4 b).
  - L18-3 and L18-6: the API 34 rules. N12's `kept … granted` and `threw <Class>` forms (the device answered `denied`).
- **The push.** `phase-18` is ahead of `origin/main` (`git rev-list --count origin/main..phase-18` read 76 on 2026-10-21, before this sheet's own commit), which holds round 2 (5ba4238c) and not the fix
  round. A push carries:
  - the fix round (Files; Clock L18-3 and L18-6; Music L18-5);
  - the two LOW residuals;
  - phase 04's FINAL plan and its unwired contract code (a1d0b6d33, brought in by this session's step 0);
  - the QA drivers and the docs.

  It carries no QA evidence and no APK: both are git-ignored or untracked. Nothing has been pushed as of this sheet's commit.

## Ledger rows this resume closed

- **L18-3** (phase 15, Clock's exported activities): fixed. Round 2's adc17689 and the fix round's N11, the one ringtone
  rule, N12, A6 and P3-4 are covered by L18_3 legs a–e, 55/0 on 23fe18a8 and again on 8d35f95e.
- **L18-4** (phase 10, Music's player wedge): fixed. c8578bc0 is covered by L18_4 leg a, 31/0 on 23fe18a8.
- **L18-5** (phase 10, a legacy skipToQueueItem kills the launcher): fixed in e0faa7d05. L18_4 leg b, on 23fe18a8:
  `[music] controller uid 10181: seekToDefaultPosition(-2147483648) refused, the queue has 6 item(s)`, with the pid
  unchanged.
- **L18-6** (phase 15, API 34's editor hand-off): fixed in d53a00dd6. It is held by JVM tests only, because no device
  here runs API 34; H9 (3) is the behaviour change for you to accept.

## Agent calls made in this resume (for the record)

1. The gate APK is kept in qa/phase-18/GATE-BUILD/ and named by GATE-BUILD/gate-build.tsv (it had lived in a finished
   session's scratchpad).
2. The device is found by AVD name (Q-18-11 (a)), because another session's emulator held emulator-5554.
3. The JVM rows compile the gate commit in a worktree of their own, because step 0 put later phases' code in this
   worktree's `app/`.
4. A crash is the shell's own FATAL (`Process: app.tileshell…`), not any line naming `app.tileshell`: the test apps'
   packages start with it.
5. L18-6 was fixed with CLEAR_TASK on API 34 rather than accepted, under the 23:03 instruction "fix everything" (H9 (3)
   is yours to accept or undo).
6. The tracking page's `signoffs/p18-*` rows are not written: the page (3JgyGQ999cbUZ9nJtdX6aD) still answers 404.
