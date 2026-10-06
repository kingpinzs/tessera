# Phase 17 gate review — brief for the two reviewers

You are one of two independent gate reviewers for phase 17 of metro-launcher (an Android launcher, package
`app.tileshell`; this phase builds its Photos, Camera and Movies & TV apps). You are Opus. You judge whether the
captured QA evidence meets the phase doc's acceptance criteria. You do not fix anything, run nothing on a device, and
change no file. Your whole output is your final message.

Work in `/home/jeremyking/projects/metro-launcher-p17` only, read-only. Do not read any other phase's doc beyond what
a row cites, and do not read `docs/plan/phase-18*` or later at all.

## Hard rules
- No subagents. Never codex, Gemini, Fable or Sonnet.
- No `adb`, no emulator, no gradle (and never `./gradlew --stop`), no `git` command that writes. Reading files, `grep`,
  `git log`, `git show` and `git diff` are fine.
- Do not touch the host's audio. Write no file anywhere (your report is your final message).
- Judge from what the folders hold, not from what a summary says. A PASS line counts only if the run's own log shows
  what was read from the device.

## What to read
1. `docs/plan/phase-17-inbox-photos-camera-video.md` — the top Decisions lines dated 2026-10-05 (they govern), then
   "## Acceptance criteria" (rows E1–E25 and the preamble) and "## Edge cases". The P rows and H rows are the owner's
   and the phone's: not yours to judge.
2. `docs/plan/INDEX.md` — every Change Log line dated 2026-10-05 and 2026-10-06. The doc is FINAL; these lines are
   its amendments: clauses the emulator contradicted, the lead's rulings on them, and ADDs. A row that asserts a
   re-read clause is judged against the re-read form — but say so if you think a ruling is wrong or hides a defect.
3. `docs/plan/review/2026-10-05-phase17-trust-fixes.md` — the device legs the trust reviews' fixes owe.
4. The evidence: `docs/plan/qa/phase-17/`. One folder per run, named
   `<ROW>-build-<apk id>-run<k>-<pass|why>-<passed>-<failed>-<recorded>`; each holds `<ROW>.txt` (the log: PASS / FAIL
   / RECORD lines with the value read), UI dumps, screencaps and `ring-*.txt` (the apps' diagnostic rings). Failing
   and earlier runs are kept beside the passing ones on purpose. The drivers are `docs/plan/qa/phase-17/scripts/`;
   the tables are `scripts/rows_{lead,photos,camera,video}.md`, `scripts/edge_index_*.tsv`, `E18/producers.tsv`,
   `E18/notrun.tsv`, `E18/E18.txt`. `python3 docs/plan/qa/phase-17/scripts/gate_table.py docs/plan/qa/phase-17
   95b54303` prints every row's passing run.

## Facts you should know
- The gate build is the clean debug APK md5 `95b543037345b851` (app code at commit bb154e06; 1,721 unit tests, 0
  failures). Rows whose code did not change after an earlier build (`e8c26851`, `c7336aca`, or a pre-merge branch
  build) were NOT run again on the gate build — the owner's standing rule is to test only what a change touches, with
  no final all-rows pass. For each such row, check that claim: `git log --oneline <that build's commit>..bb154e06 --
  <the app's source folders>` should show nothing that row exercises. A row resting on an earlier build whose code
  DID change since is a finding.
- No microphone and no host audio in QA: video rows run with RECORD_AUDIO revoked, and audible clauses are recorded.
- The emulator has a back camera only, no front camera, no high-speed session, and is x86_64 (the panorama library is
  arm64-only). Clauses those facts make impossible are in `E18/notrun.tsv` or mapped to a P row.
- Known and open, for the owner (not findings unless you think the evidence misstates them): another app's "Open
  with" on a MediaStore item and a capture into a caller's own MediaStore row are refused (fail closed); FLAG_SECURE
  and clipboard clearing on the key and password pages are not built; the exported viewer runs in the launcher's
  process; E9's forwarded display_photo leg was not run; the CI workflow change has never run.

## Your lens
The lead tells you which lens is yours in the message that carries this brief:
- **Design / correctness:** does each row's passing run show the behaviour the doc's row requires — every clause? Are
  the lead's rulings on contradicted clauses sound, or does one paper over a product defect? Do the trust legs
  (TRUST_PHOTOS, TRUST_VIDEO, E9, E13, E22) actually show the refusals and the no-token-leak claims on the device?
- **Testability / evidence:** is each PASS backed by a value read from the device in the log? Are there clauses
  recorded that should have been asserted, assertions that could not fail, absences proven on an empty slice, rows
  whose folder name says pass while the log holds a FAIL, or tables (`producers.tsv`, the edge index, `rows_*.md`)
  that disagree with the folders? Does every Edge-cases bullet map to a run, a P row or a JVM test?

## Your report
First line: `GATE: PASS` or `GATE: FAIL`. Then findings, most severe first, each with: severity (HIGH = a row's
clause is unproven or contradicted, or a product defect; MEDIUM = evidence weaker than claimed but the behaviour is
shown elsewhere; LOW = table or wording), the row, the file and line that shows it, and what would close it. FAIL only
for a HIGH. Say plainly what you did not read. Keep it under 900 words; no preamble.
