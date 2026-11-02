# Session prompt — phase 04's resume (action center, volume panel, in-APK helper), after the park of 2026-10-20

Start Claude in ~/projects/metro-launcher-p04 and paste the text between the two --- lines.

---
Work in ~/projects/metro-launcher-p04 only (git worktree, branch phase-04). Never edit ~/projects/metro-launcher, -p17, -p18,
-p20 or any other session's worktree. Load the phased-build skill. Read docs/plan/INDEX.md (master: row 04; the Change Log
entries of 2026-10-20 15:40 and 17:23 and of 2026-10-30 23:45 and 2026-10-31 01:06; the Blocked-on ledger). Read
docs/plan/phase-04-action-center-volume-helper.md (FINAL 2026-10-09) fully, then docs/plan/qa/phase-04/BUILD-CONTRACT.md and the
last entries of .claude-build-state.md. Then do the steps below in order.

**Where it stands (2026-11-02):**
- **Phase 04 is PARKED** (row 04 `pending`; his "(c)", 2026-10-20 17:23). The reason was to close phases 18 and 17 first, and
  both are now built: 17's resume is merged on `phase-17` and on origin. His pull request to `main` is his to do.
- **The 2026-10-20 build session built only the shared contract,** a1d0b6d33 "Phase 04: build contract and shared seams":
  - Radio, HelperState, HelperTileRule and its test (7 / 0), and RadioStates;
  - the HelperKeeper, AcService and OverlayWindows signatures, AcPanelControl, AcGeometry, and stub composables.
  Then it halted. Two builder dispatches were each **stopped by a safety classifier** and not retried:
  - task 2, the helper. It pairs with the phone's own Wireless debugging and runs a small shell-uid process that flips Wi-Fi,
    Bluetooth, Cellular and Airplane mode. It was stopped at 15:27.
  - tasks 4, 5 and 11, the accessibility service that draws the action center over every app and Start, plus hold Back →
    recents. They were stopped at 15:41.
  Task 3 (the restart path) needs task 2. Tasks 6, 7, 8, 9, 10, 12 and 13 were never dispatched.
- His 15:40 ruling "(a)" for that session: build every part but tasks 2 and 3 against the committed seam, with HelperKeeper a
  stub reading `NotPaired`, and send nothing up until the helper is settled.
- The doc's Scope rules out any separately installed app, Shizuku included (doc :36; RV1, Q13).
- Every phase left after 04 in the build order (19, 06, 07, 08, 09) leans on 04's helper or its accessibility service.
- The worktree is clean at 051940b46, which is an ancestor of `phase-17` (checked 2026-11-02). Its git-ignored inputs are
  hard-linked: local.properties, keystore.properties, app/libs, the speech and keyboard assets, and `.opencvsrc/` (4 links on
  its zip). No agent worktrees remain under it. The AVD tileshell_fhd exists (recreated 2026-10-20), but this session runs no
  device step.
- Root disk: 34 GB free on 2026-11-02. Check `df -h /` before every build wave.

**His testing rule (INDEX Change Log 2026-10-30 23:45 and 2026-10-31 01:06):**
- "lets keep building and only do red tests and wait tell everything is 100% complete and ready to ship then do the full hours
  of testing", then "red tests only full suite test after every single phase is done".
- So:
  - Build now.
  - Each build task proves itself with its own unit tests from the doc's "Unit tests (JVM)" table, written first and shown
    failing, then passing, with gradle on the host.
  - No device or adb step, no emulator rows, no gate reviewers and no sign-off rounds. Those wait for ONE full testing pass
    after every phase is built.
  - A trust part still gets ONE adversarial Opus code review (task 14's surface, reduced to what is built) and one fix round.
  - The whole unit suite runs after each merge.
- Task 1 (the build-start device checks) therefore goes on the final pass's list. Mark its (a) and (d) there as stop
  conditions: (a) is "the volume key reaches the service" and (d) is "blur behind an accessibility overlay". Either one, when
  it runs, could change the build. Task 1 (j), Kadb's licences, is not a device step and belongs with task 2.
- Task 13's `testapps/qa-notify` is built and compiled now; the lead writes `p04.sh` after the merges. Neither is run.

**Step 0 — bring the branch up to date.**
- From ~/projects/metro-launcher-p04, run `git merge --ff-only phase-17`. Confirm that HEAD equals phase-17's tip and that
  `git status --porcelain` is empty.
- If the fast-forward fails, stop and tell him. Do not make a merge commit and do not rebase.
- Then take a baseline: `./gradlew :app:assembleDebug --offline` and `./gradlew :app:testDebugUnitTest --offline`. Send output
  to files and read the rc from the files. Take the totals from the JUnit XML; phase-17's merged tree read 2,561 / 0.

**Step 1 — ONE question before any builder (Q-04-R1), in the Stage A shape.**
- Re-check each fact it rests on in the files first.
- Give him the facts above in a few lines: what was built, what was stopped and when, what was never dispatched, what leans on
  04, and the Scope line on Shizuku.
- Then ask how phase 04 is built now:
  - **A. Build the tasks that were never stopped, now (the lead's lean).** That is 6 quick actions and their two grants, 7
    notifications with qa-notify, 8 the volume panel, 9 cross-window acrylic, 10 Tess's switch commands and 12 the Settings
    pages, checklist rows and wizard steps. Each is built in final form against the committed seams. Tasks 2, 3, 4, 5 and 11
    stay unbuilt for him to build or direct. Row 04 stays `building` and nothing goes up. Any of these may be stopped too, and
    a stopped one stays stopped.
  - **B. Re-plan phase 04 first,** in a planning session of its own, around what can be built here, with the helper and the
    accessibility action center reconsidered. Nothing is built until the doc is FINAL again.
  - **C. He builds or directs tasks 2, 3, 4, 5 and 11 first,** outside these sessions and against the contract. This session
    ends now, and a later one builds the rest on his code.
  - **D. Other / let me clarify.**
- Record his answer, dated and in his words, in INDEX's Change Log before anything else. When his answer is not one of the
  letters, say back how you read it before building on it.
- If he chooses B or C: update row 04 and the state file, hand off and end.

**Step 2 — the build (on A, or on what his answer asks for).**
- **Builders.** Use one Opus builder per contract row being built (Agent tool, `model: opus`). The contract's ownership
  table and its ten rules hold. On A there are four builders:
  - quick: task 6;
  - notif: task 7 and 13's qa-notify;
  - volume: tasks 8 and 9;
  - tess: tasks 10 and 12.
- **Worktrees.** Each builder gets its own worktree, `git worktree add .claude/worktrees/<name> -b phase-04-<name>` from
  phase-04. Hard-link its ignored inputs as docs/plan/qa/phase-18/scripts/src_worktree.sh does. Never clone.
- **Briefs.**
  - Open with one plain line on what the project is: his own Windows 10 Mobile–style launcher for his own phone, sideloaded,
    no root.
  - Point at the build task, the Decisions it rests on and the unit-test table's rows. The doc is the spec.
  - Each test is written first and run red against the stub, then made green. Each guard is broken once in a COPY to show
    the test catches it, and the mutant must compile.
  - Builders never stage or commit and never run adb. The lead commits each builder's work on its branch.
- **Merges.**
  - Merge each branch into phase-04 one at a time with `git merge --no-ff -F <msgfile>`.
  - Resolve conflicts by reading both sides.
  - Run the whole unit suite after each merge, rc from a file.
  - Each ADD to an older phase's part (the doc's Cross-phase section: phases 01, 03, 12, 13) is an INDEX Change Log line when
    it is merged.
  - A builder's open choice goes into the doc's Approximations or Decisions through a Change Log entry, never a silent edit.
- **If a safety classifier stops a dispatch or any tool call,** that task stops there:
  - It is not retried, reworded, split into smaller briefs, done in the main loop, or done any other way.
  - Builders already running finish, and nothing new is dispatched.
  - Record the time and what was stopped in the Change Log and the state file.
  - Ask him what happens next, in the Stage A shape.
- **The review.** After the merges, run ONE adversarial Opus review of the built trust surface from task 14's list: other
  apps' notifications (tap, swipe, their buttons, inline reply, and `[notif]` lines carrying no text), anything over the
  keyguard (Tess's switches behind the unlock, no track title over a PIN, pattern or password), the two grants, and the
  exported allow-list's entries.
  - Brief it adversarially: red-proof against the pre-fix source, a mutation check on each guard, and both halves of every
    two-sided contract.
  - Then hold one fix round, red first. Adjudicate each finding (CONFIRMED / OVERSTATED / NOT PROVEN) yourself.
  - A defect in an older phase's part goes on the Blocked-on ledger and is not fixed here unless he says so.
  - The unbuilt surface (the helper's door, its allow-list, the adb session, the adb settings it writes, the accessibility
    service's scope) is reviewed when it is built. Put it on the list.
- **Clean-up.** Remove each worktree in the step its work is merged (`git worktree remove`, no --force), then
  `git branch -d` (never -D).

**Step 3 — the record, then HARD STOP.**
- Write docs/plan/qa/phase-04/NEEDS-HUMAN.md with:
  - what is built and what is not, and why (the classifier stops, his rulings);
  - everything owed to the ONE final testing pass: task 1 (with (a) and (d) as stop conditions), device rows A1–A8, the static
    checks, phone rows P1–P8 and sign-off rows H1–H24, `p04.sh` and qa-notify's first runs, and the review still owed on the
    unbuilt surface.
- Update INDEX row 04 and .claude-build-state.md.
- Send nothing up. If he asks for it, list what it carries first. The branch also carries phase 17's resume until his pull
  request lands. Send it the moment his flag exists, with a command that holds the push and nothing else.
- Then hand off and end. Never begin, prep or read the next phase.

**Standing rules:**
- Models:
  - NEVER use Fable for anything: reviewers, subagents, forks, the main loop.
  - NEVER use codex in this project: it runs on his work account.
  - Builders and the reviewer are Opus (`model: opus`). Never Gemini, never Sonnet.
- Hands off the machine:
  - Never touch the host's audio (no pactl, no audio.sh setup, no hostmicon).
  - Never start, stop or touch an emulator. Another session's neuromap_36 may be on emulator-5554.
- Shell:
  - Read exit codes from captured files, never through a pipe. Never pipe into `grep -q` or `grep -m1`.
  - Kill processes only by recorded pid.
  - Timestamps you write come from `date`.
- Git:
  - Never `git add -A` or `git add .`. Add explicit paths.
  - Write the message to a file and use `git commit -F`, and never put backticks in a command that commits.
  - End every commit message with the attribution lines the session's system reminder gives.
  - Never check out an old-history commit in a worktree (history rewrite 2026-10-02).
- Evidence: QA evidence (logs, dumps, captured output) is never committed and never sent up. It stays on disk, ignored by
  docs/plan/qa/.gitignore.
- Sending it up: never send anything up without his word. The push hook also needs him to run `touch ~/.claude/push-approved`
  himself; you cannot create, read or remove that flag.
- Deleting: one `rm -rf` per directory, with the exact absolute path. No globs, variables or `cd && rm`.
- State: the tracking page (claude.ai artifact 3JgyGQ999cbUZ9nJtdX6aD) has been gone since 2026-10-08, so do not use it. State
  lives in INDEX.md (master) and .claude-build-state.md, updated every iteration.
- Asking him: use the Stage A question shape for anything you ask. That is one question per message, at least three lettered
  one-line choices with your lean marked, and "D. Other / let me clarify" last, in prose, never the AskUserQuestion tool.
  Record his answer, dated and in his words, before the next question.
---

Notes for Jeremy (not part of the prompt):
- Written 2026-11-02 at the end of phase 17's resume. If INDEX.md has moved on since, INDEX.md wins.
- The session fast-forwards phase-04 onto local `phase-17`, so it does not need your pull request for phase 17 merged first.
- Task 1's device checks wait for the final pass under your testing rule. Two of them, (a) and (d), are stop conditions in
  the doc, so a result there could send part of this build back.
- This session uses no emulator and no phone.
