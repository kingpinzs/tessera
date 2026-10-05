# Phase 17 QA — rules for the three row writers (Photos rows, Camera rows, Movies & TV rows)

You write and run the GATE's row drivers for one app of phase 17 of metro-launcher (package `app.tileshell`). The three
apps are built and merged on `phase-17` (commit 25921fd7; debug APK md5 `dc1b8118bbc4c3ac`; 1,470 unit tests). You do
not change app code. A product defect you find is evidence to report, never something to fix or to assert around.

## Read first, fully
1. `/home/jeremyking/projects/metro-launcher-p17/docs/plan/prompts/phase-17-build-common.md` — "Hard rules", "Commits"
   and "The device" bind you (you are Opus; no subagents; never codex / Gemini / Fable / Sonnet; never push; never the
   host's audio or the microphone — a step that records video uses `mic_off` / `mic_on`; explicit `git add` paths;
   `git commit -F <file>` with no backtick in the command; never commit run evidence; no credential in any log).
2. `docs/plan/phase-17-inbox-photos-camera-video.md` — the Acceptance preamble (from "## Acceptance criteria" through
   "Upgrade:"), your rows in full, the Decisions they cite, the Edge cases, and the Decisions lines dated 2026-10-05.
3. `docs/plan/INDEX.md`'s two Change Log lines dated 2026-10-05 14:27: the doc clauses the device contradicted and the
   readings the rows assert (port 8091, the guard's condition (d), E20's bearer scope, E22's token read, and more).
   Where a Change Log line re-reads a clause, the row asserts the re-read form and says so in a comment.
4. Your app's builder's development proof — the scripts under `docs/plan/qa/phase-17/dev-<app>/scripts/` and (video)
   `dev-video/README.md`: they already drive every page and name every tag and line. Reuse their working steps; a gate
   row differs in asserting EVERY clause of the doc's row as the doc words it, in the form below.
5. The floor and the form: `docs/plan/qa/phase-17/scripts/p17.sh` (rings, `c6`, `rings_save`, `gdump`, `absent_in`, `px`
   / `assert_rgb`, `media_up` / `media_down` with the census, `mic_off` / `mic_on`, `egress_guard_on` / `_off`),
   `e1.sh` and `e24.sh` (the form of a row), `rows_lead.md`, `p17_selftest.sh`, `make_videos.sh` (the colours of
   `qa-steps.mp4` are in `gen/media/qa-steps.colours` after it runs), `edit_expect.py`, `leak_scan.sh`, and
   `docs/plan/qa/phase-03/scripts/lib.sh`.

## The form of a gate row
- One driver per row: `docs/plan/qa/phase-17/scripts/e<N>.sh` (lower case, `e6b.sh`), with a header comment mapping
  its legs to the doc's clauses; `set -uo pipefail`; sources `lib.sh` then `p17.sh`; `take_device_lock`; installs the
  worktree's debug APK on mismatch; `row_begin E<N> "<what>"`; `wake_device` asserted; every ring read is `ring_since`
  from a MARK taken just before the step; every absence is `absent_in`; every no-node check first asserts its page's
  tag; `rings_save` before anything that kills a process; restores everything (`media_down`, grants, prefs, airplane
  mode, the layout, fixtures apps uninstalled); `row_end`. Start from `layout_restore "$BASELINE"` where the row reads
  Start. Generated fixtures come from `$GEN` (`make_videos.sh "$GEN"`; `make_photos.py "$GEN" editor`).
- Every clause of the row is asserted. A clause that cannot be asserted on this emulator is `record`ed with the
  reason, and listed in your report; never silently dropped. A clause the device contradicts and no Change Log line
  covers: keep the assertion as the doc words it, let it FAIL, keep the failing run's folder (renamed with the
  reason), and report it with the evidence — the lead rules on it. Do not loosen a tolerance on your own.
- A row's evidence is ONE passing run on the gate build. Iterate until it passes or until what fails is the product
  or the doc. Rename each earlier run's folder (`E<N>-run<k>-<why>`) before the next run; never delete one.
- Your build: `./gradlew :app:assembleDebug --offline` in your worktree. It must come out as md5 `dc1b8118bbc4c3ac`
  (the build is reproducible); assert that in each row's log with a `record` of the installed APK id, and tell the
  lead at once if yours differs.
- Also write, per app: `e19_<app>.sh` (your app's part of E19, as row `E19_<APP>`), `e23_<app>.sh` (your app's part of
  E23, as row `E23_<APP>`), and `edge_<app>.sh` — one function `edge_<ID>` per Edge-cases bullet that is yours, each
  from its own MARK with its own restore, runnable alone (`edge_<app>.sh <ID>`) or all together — plus your lines of
  `edge_index_<app>.tsv` (bullet's first words, tab, `edge_<ID>` or the row / P row / JVM test that covers it, tab,
  status). A bullet only a phone can show is mapped to its P row, not skipped.
- Also write `E18/producers_<app>.tsv`: for every alternative of every `[<your tag>]` line in the doc's E18 (each `|`
  choice separately): the line pattern, tab, the row or edge sub-step that produces it. And `E18/notrun_<app>.tsv` for
  alternatives no AVD row can produce, each with its reason and its P row or JVM test.
- `rows_<app>.md`: the table `rows_lead.md` has, for your rows, with each row's last run (totals, folder).

## The device
Shared by the three row writers, one builder and the lead. The lock is `/tmp/tileshell-qa-device.lock`; when a driver
exits 3, wait in the lock's own queue (`flock -w 540 /tmp/tileshell-qa-device.lock true`) and start it again. Run a
driver as `env -u TMPDIR bash docs/plan/qa/phase-17/scripts/<driver>`. Never `pm clear`, `adb uninstall
app.tileshell`, or `adb reboot`. `adb root` only where a row's clause needs it (the doc says where), undone at once
inside the same script (`adb unroot`, `adb wait-for-device`), and never while you do not hold the lock. Another
session may install the Living Images build (a different md5) between your runs: your install-on-mismatch puts yours
back; do not treat the other build as a fault.

## Your report (final message, plain text)
Commits; per row — the last run's totals, its folder, clauses recorded instead of asserted and why, clauses that FAIL
with their evidence (product defect, or the doc contradicted); what you did not write or did not run; the device and
host state you left. Honest and complete: the gate's two reviewers will read the folders you name.
