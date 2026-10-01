# Phase 16 QA — rules for the two row-driver writers (Calendar rows, People rows)

You write and run the GATE's row drivers for phase 16 of metro-launcher (a Windows 10 Mobile style Android launcher,
one APK `app.tileshell`). You did not build the apps and you do not change them: your job is to prove or disprove, on
the emulator, each acceptance row of the FINAL spec, clause by clause. A lead session owns the phase and the gate; two
reviewers will later judge your evidence against the spec, assuming you were lenient. Be the opposite.

## Where you work
- The lead's worktree: `/home/jeremyking/projects/metro-launcher-p16` (branch `phase-16`). `cd` there for every command.
- You write ONLY under `docs/plan/qa/phase-16/`: drivers in `scripts/`, each row's evidence in its own folder
  (`E<n>/`, made by `row_begin`), and the three shared files named below. You change NO product code (`app/`), no other
  phase's files, no `docs/plan/INDEX.md`, no phase doc, no `.claude-build-state.md`, and none of the existing files in
  `scripts/` that are not yours (`lib.sh`, `p16.sh`, `e1.sh`, `e2.sh`, `e26.sh`, `buildstart.sh`, …). If `p16.sh` lacks a
  helper two rows need, put it in your own include (`scripts/cal_lib.sh` or `scripts/people_lib.sh`) and tell the lead.
- Do NOT commit, stage, stash or switch branches: another writer works in the same worktree at the same time, and the
  lead commits your files. Never run `./gradlew`: the lead builds. Never edit a script while it is running (bash reads
  a script as it goes; a run was garbled that way today).
- Scratch files go in `/tmp/claude-1000/-home-jeremyking/94273004-6013-4029-a961-ffb26e4dcb1f/scratchpad/` with your prefix.
- You are Opus. Do not start subagents. Never touch the host's audio. Never push.

## Read first (fully)
1. `docs/plan/phase-16-inbox-calendar-people.md` — the FINAL spec. All of it once; then your rows, the Acceptance
   preamble (every rule in it binds your drivers: MARKs, `ring_since`, `absent_in`, `c6` + `ensure_start`, `gdump`,
   forwards-only clock jumps, restores, row independence, `cal_lists`, typed Tess requests), the Decisions lines your
   rows cite, "Harness contracts", "Harness, round 3" and "Edge cases".
2. `docs/plan/INDEX.md` — only the Change Log entries dated 2026-10-01 (search for `- 2026-10-01:`): they record what
   was built otherwise than the spec's words, with the reason. Those entries are the lead's; a deviation NOT recorded
   there is a finding.
3. `docs/plan/qa/phase-16/BUILDSTART/README.md` (provider facts), `scripts/p16.sh` (your floor: read every helper),
   `docs/plan/qa/phase-03/scripts/lib.sh` (the row machinery), `scripts/e1.sh` (a finished driver, for the form).
4. Your app's builder report under `docs/plan/review/` — its section 8, the "driver map" (tags and tap paths), and its
   sections 4 and 6 (what the builder did not prove, and what it built differently). The builder's own development
   scripts (`dev-cal/scripts/`, `dev-people/scripts/`) show how to reach each page and set each field; use them for
   NAVIGATION technique only. Their assertions are the builder's; yours come from the spec's row text.

## What a row driver is
- `scripts/e<n>.sh`, sourcing `lib.sh` then `p16.sh` (then your include), `row_begin E<n> "<what>"` … `row_end`.
- EVERY clause of the row, as the spec words it, gets its own assertion with a name a reviewer can match to the clause.
  A clause is not "covered" by a neighbouring assertion. Recorded clauses use `record` (the spec says which they are).
- Read back from the PROVIDER or the DEVICE wherever the row says so (`content query`, `dumpsys`, `layout_json`), never
  from the app's own line alone. An absence is `absent_in` on a slice proven readable; a "no node" check first asserts
  the page it is on (the spec's `cal_event_page:<id>` / `people_card:<lookup>` rule).
- Each row starts from the baseline, makes its own fixtures, and restores everything (the preamble's "Restores and
  independence"); it must pass when run alone, twice in a row.
- If a clause CANNOT be asserted as the spec words it (the image refuses the command, the spec names something that
  does not exist), do not weaken it silently: assert the nearest thing that still proves the clause's intent, and add a
  line to `docs/plan/qa/phase-16/clauses-open.tsv` (`row <TAB> clause <TAB> why it cannot hold as written <TAB> what
  the driver asserts instead`). Three such clauses are already known and recorded in the Change Log; start the file
  with them if they are yours.
- If the PRODUCT fails a clause: that is the result. Keep the failing run (rename its folder `E<n>-run<k>-<what it
  found>`), write `docs/plan/qa/phase-16/defects/D-<row>-<k>.md` (the clause, the exact steps, the real output, what
  the spec says, your one-line diagnosis if you have one — you may read `app/` to diagnose, never to edit), and go on
  to the next row. Do not adapt the driver to make a product defect pass. The lead routes fixes and tells you when a
  new build is installed; you then re-run the row.
- Diagnostics coverage (row E21): `docs/plan/qa/phase-16/E21/producers.tsv` (`alternative <TAB> row or EDGE sub-step
  that produces it <TAB> the fixed string or regex to grep in that row's ring-launcher.txt`). Add a line for every
  alternative of every diagnostics line your rows produce; `row_end` saves the row's ring slice, and `ring_save` must be
  called before anything that restarts the shell (`c6` does it). An alternative no row of yours can produce on a device
  goes in `E21/notrun.tsv` with the reason and the JVM test that covers it.
- Edge cases: `scripts/edge_index.tsv` maps every Edge-case bullet to a sub-step id. Yours are named in your brief. Write
  each as a bash function `edge_<ID>` in your include file (its own MARK, its own fixtures, its own restore, assertions
  through the same helpers); the lead's `scripts/edge.sh` sources both includes and runs every sub-step in one EDGE row.
  You may run yours with `EDGE_ONLY=<ID>[,<ID>…] bash scripts/edge.sh`. If the index maps a clause wrongly, say so.

## The device (shared)
- `ANDROID_SERIAL=emulator-5554`, one emulator shared by you, the other writer and the lead. `row_begin` takes the
  device lock; if it prints "another QA driver is already driving the device" (exit 3), wait 30–90 s and run again.
  Do not hold the device with ad-hoc commands outside a locked script.
- The build under test is the lead's: `app/build/outputs/apk/debug/app-debug.apk`. `row_begin` logs `apk match`; a row
  whose log says `NO` proves nothing about this build — install the lead's APK (`adb install -r "$APK"`, inside your
  locked script) and run again.
- Never relaunch, reboot-loop or wipe the emulator outside what a row's text itself demands (some rows do `pm clear`,
  `adb reboot`, `pm disable-user` — those are fine, inside the row, restored by the row). If the emulator process dies,
  STOP all device work at once and tell the lead (`SendMessage` to `main`): the owner's rule is that a step that crashes
  the emulator twice ends the session's device work.
- Account-style calendars made with `mkcal` vanish when the calendar provider's process restarts: make them after the
  row's last restart. Calendar ids and alert ids are reused after a delete.
- No microphone, ever. Tess requests are typed (`type_request`), confirmations tapped (`cortana_card_button:confirm`).

## When you finish
Your final message is your report to the lead (plain text; you cannot write it as a file): per row — the driver, the
final run's PASS / FAIL / recorded totals with the evidence folder, how many times it has passed in a row when run
alone; every defect file; every `clauses-open.tsv` line; what you could not drive and why; the device state you left.
Do not round up: a row with one failing clause is a failing row.
