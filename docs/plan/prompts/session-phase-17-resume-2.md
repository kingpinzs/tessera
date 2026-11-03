# Session prompt — phase 17's resume, part 2 (after the pause of 2026-10-31)

Start Claude in ~/projects/metro-launcher-p17 and paste the text between the two --- lines.

---
Work in ~/projects/metro-launcher-p17 only (git worktree, branch phase-17). Never edit ~/projects/metro-launcher, -p04, -p18
or -p20. Load the phased-build skill, read docs/plan/INDEX.md (master: row 17, its newest Change Log lines from 2026-10-30
23:45 on, ledger L17-1 and L20-1) and the last entries of .claude-build-state.md, then continue phase 17's resume from
the pause.

**The owner's rules for this resume (INDEX Change Log 2026-10-30 23:45 and 2026-10-31 01:06):** build now; red tests only
(each build task's own unit tests, failing first, then passing, run with gradle on the host); NO device rows, no gate
reviewers, no sign-off rounds — ONE full testing pass when every phase is built. A change to a trust part still gets ONE
adversarial Opus code review and one fix round. The whole unit suite runs at each merge.

**Where it stands (2026-10-31 01:48), each on a branch cut from phase-17 at aa00577aa, none merged yet:**
- `phase-17-d2` f0ebaa536 — D2 (the credential pages: FLAG_SECURE, the clipboard cleared after a paste). Built, reviewed
  (PASS), fix round done; suite 2494 / 0. READY TO MERGE. Record: docs/plan/review/2026-10-31-phase17-d2-build.md.
- `phase-17-l20-1` a03bf8393 — ledger L20-1 (`ServerRules.isPrivate`). Built, reviewed (PASS), fix round done; suite
  2469 / 0. READY TO MERGE; then mark ledger L20-1 fixed. Record: docs/plan/review/2026-10-31-phase17-l20-1.md.
- `phase-17-d3` ec5130383 — D3 (the exported viewer in `:photosedit`). Built, reviewed (PASS with LOW L1: the provider's
  `call()` checks no caller and the manifest test does not pin `multiprocess` / `grantUriPermissions`; LOW L2: mutants
  m05b, m10, m11, m16 survive the scan; NOTE: a failed Cortana hand-off is silent). Its ONE fix round was STOPPED at the
  pause with nothing written: re-dispatch it (one Opus builder in the existing worktree .claude/worktrees/d3, on its
  branch), red first, each surviving mutant re-run (docs/plan/qa/phase-17/REVIEW-D3/mut-<id>.diff), evidence FIX-D3/.
  Record: docs/plan/review/2026-10-31-phase17-d3-build.md.
- `phase-17-d1` e039475d0 — D1 ("Open with" on a shared-library item). Built; review FAIL on ONE MEDIUM: the launch-grant
  gap (an app can reach the viewer / player through `startNextMatchingActivity`, which skips the grant check, and be
  admitted on a temporary grant the shell holds for the same item from elsewhere — S1, S3–S6), CONFIRMED from AOSP source
  by the builder and the reviewer independently; no fail-closed fix inside the app. Record:
  docs/plan/review/2026-10-31-phase17-d1-build.md. FIRST THING: ask the owner (Stage A shape, in prose) the question he
  was asked at 01:4x and did not answer before the pause — A keep D1 as built and record the residual in NEEDS-HUMAN and
  the KDoc, fix the over-claiming KDoc and tests (the lead's lean) / B fail closed for reads (viewer and player back to
  `starterMayRead`), keep the Camera part / C revert D1 / D other. Record his answer, dated and in his words, in INDEX's
  Change Log before anything else is built on D1.
- D4: not built (the final pass's list). D1–D3's build records each list the device legs owed to the final pass.

**Then, in order:** merge the ready branches into phase-17 one at a time (explicit `git merge --no-ff`, whole suite after
each, merge conflicts resolved by reading both sides), apply the D1 ruling, merge d3 after its fix round, update
qa/phase-17/NEEDS-HUMAN.md (D1–D4 rulings, the residuals, the final-pass legs), the phase doc's stale lines through a
Change Log entry (phase-17 doc :152-153, :507, :999), INDEX row 17 and the ledger. Remove each worktree and merged branch
when its work is merged (`git worktree remove`, `git branch -d`; never --force or -D). Do not push without the owner's
word and his `~/.claude/push-approved` flag; list what a push carries first (the branch also carries phases 18 and 20's
work and phase 04's contract code).

**Standing rules:** never Fable, Sonnet, Gemini or codex (reviews and agents are Opus, `model: opus`); never touch the
host's audio; no device or adb step; exit codes from captured files, never through a pipe; never pipe into `grep -q` /
`grep -m1`; kill only by recorded pid; QA evidence is never committed; commit with explicit paths and `git commit -F`;
each agent gets its own worktree (`git worktree add`, inputs hard-linked as in docs/plan/qa/phase-18/scripts/src_worktree.sh),
never a clone, and its worktree is removed in the step its task ends; check `df -h /` before builds (the root disk was at
44 GB free on 2026-10-31). Stage A question shape for anything asked: one question, at least three lettered choices with
the lean marked, "D. Other / let me clarify" last, prose only.
---
