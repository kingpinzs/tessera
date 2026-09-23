# Session prompt — phase 11 (tile quick actions), main checkout

Paste into a fresh Claude Code session started in ~/projects/metro-launcher:

---
Load the phased-build skill, read docs/plan/INDEX.md, and follow docs/plan/build-prompt.md — with this session's scope fixed to **phase 11, tile quick actions** (docs/plan/phase-11-tile-quick-actions.md). Jeremy cleared starting it on 2026-09-23 with the earlier phases' open gates (01 NEEDS-HUMAN, 02 deferred, 03 partial, 05 open, 10 phone rows) left for the end-of-build pass — INDEX Change Log.

**Stage A first, for phase 11 only (it is still DRAFT):**
1. Round 3 review — the last one allowed — of phase 11 alone. Two reviewers per the skill's roster ladder (codex MCP → codex CLI → a second reviewer; if Fable is at its limit, Opus under Jeremy's standing approval), one design/correctness lens and one testability/evidence lens, checking against docs/plan/review/2026-09-23-phases11-20-r2-triage.md. Triage; apply the doc updates; bring only true forks to Jeremy, one question at a time, in the Stage A shape (one question, three or more lettered choices with the lean marked, "D. Other / let me clarify" last).
2. When no BLOCKING remains: set the doc to status: FINAL, add the INDEX Change Log line, set row 11 to building.

**Then build phase 11** per the Stage C loop: its build tasks; the QA gate on the AVD tileshell_fhd (emulator-5554) through the phase 03 driver floor (lib.sh — wake_device, verified layout_restore, a per-phase baseline, ring slicing by wall= timestamps); both reviewers on the captured evidence; INDEX row 11 and .claude-build-state.md updated every iteration; HARD STOP when the row is done.

**This session owns:** the main checkout (branch main) and emulator-5554. A parallel session may be building phase 15 in the worktree ~/projects/metro-launcher-p15 on its own emulator — never touch that directory or emulator-5556. In INDEX.md edit only row 11 and your own Change Log lines. Commit logically with git commit -F <file> (no backticks in messages). Never push without Jeremy's approval (he runs touch ~/.claude/push-approved).
---
