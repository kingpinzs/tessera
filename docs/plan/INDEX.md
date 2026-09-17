# metro-launcher — build index

mode: standard
reviewers: fable + codex-mcp (Stage A rounds 1-3, 2026-09-16); fable + fable (phase-doc review round 1, 2026-09-16: codex out, CLI exit 1 untrusted dir + MCP usage limit until 2026-09-19); opus + opus (phase-doc review round 2, 2026-09-16: Fable limit reached (HTTP 429), Jeremy approved Opus); opus + opus (phase-doc review round 3, the last, 2026-09-17)

**Bootstrap a fresh conversation with:** "Load the phased-build skill, read docs/plan/INDEX.md, continue the build."
Process contract: `~/.claude/skills/phased-build/SKILL.md` (Stage C + Hard Rules). Plan source: [PLAN.md](PLAN.md) · Build prompt: [build-prompt.md](build-prompt.md)

## Stage A progress (planning)

| Step | Status | Evidence |
|---|---|---|
| 1 Plan iteration | done 2026-09-16 | PLAN.md Rulings Q1-Q14, principles P1-P5 |
| 2 Review before split | done 2026-09-16, 3 rounds, round 3 = 0 BLOCKING from both | review/2026-09-16-{fable,codex,triage}.md, review/2026-09-16-r2-*.md, review/2026-09-16-r3-*.md |
| 3 Split | done 2026-09-16 (Jeremy: "(a)") | the 9 phase docs below |
| 4 Per-phase interviews | done for 01-03 and 05-09 (2026-09-16); 04 parked until R4 runs on the phone | each phase doc's "Interview queue" |
| 5-6 Cross-model review of phase docs | done 2026-09-17: 3 rounds (cap reached), every finding triaged and applied; owner rulings applied (review/2026-09-16-phases-r2-owner.md, review/2026-09-17-phases-r3-owner.md) | review/2026-09-16-phases-{fable,fable2,triage}.md, review/2026-09-16-phases-r2-{opus-design,opus-test,triage}.md, review/2026-09-17-phases-r3-{opus-design,opus-test,triage}.md |
| 7 FINALIZE | done 2026-09-17 (Jeremy: "A"): phases 01-03 and 05-09 FINAL; phase 04 stays DRAFT until R4 runs on the phone and its interview is done | |
| 8-9 Build harness | done 2026-09-17: build-prompt.md written; phase rows ready | build-prompt.md |

## Research gating

| Task | Status | Output | Gates |
|---|---|---|---|
| R1 W10M fidelity reference | done 2026-09-16 | w10m-reference.md | — |
| R2 Reuse + licence check | done 2026-09-16 | r2-reuse-check.md | — |
| R3 Measure W10M values from footage (scope extended 4x, see PLAN.md) | done 2026-09-16 | w10m-measurements.md | FINAL of every phase doc |
| R5 Windows live-tile model + Android unread-count sources + Live Tile API design | done 2026-09-16 | r5-live-tile-api.md | FINAL of phase 01 |
| R6 Second measurement pass (edit mode, keyboard, Cortana flows, Back / Search keys, "New" caption, glance) | done 2026-09-16 | r6-measurements.md | FINAL of phases 01, 02, 03, 05, 07 |
| R7 Phone and Messaging apps, Cortana Notebook, action center / volume motion | done 2026-09-16 (38 HIGH / 114 MEDIUM / 32 LOW / 18 UNMEASURED; applied to phases 04, 06, 09, and to 03 on 2026-09-17) | r7-measurements.md | FINAL of phases 03, 04, 06, 09 |
| R4 In-APK helper feasibility + licence spike (phone-only), plus the T-Mobile visual voicemail probe (PQ2) and the nav-bar overlay probe (R7 §4.1.10, phase 04 P5) | pending: needs the S25 Ultra connected over USB (Auto Blocker off, USB debugging on) | review/ or qa/ spike notes | phase 04 interview; phase 06 voicemail |

## Phases

| Phase | Doc | Status | QA evidence | Notes |
|---|---|---|---|---|
| 01 | [phase-01-start-live-tiles.md](phase-01-start-live-tiles.md) | building | — | FINAL; creates Settings hub + onboarding checklist |
| 02 | [phase-02-edit-mode-folders.md](phase-02-edit-mode-folders.md) | pending | — | FINAL |
| 03 | [phase-03-cortana-commands.md](phase-03-cortana-commands.md) | pending | — | FINAL; no toggle commands (they come with 04) |
| 04 | [phase-04-action-center-volume-helper.md](phase-04-action-center-volume-helper.md) | pending | — | DRAFT; entry gate = R4 |
| 05 | [phase-05-keyboard.md](phase-05-keyboard.md) | pending | — | FINAL |
| 06 | [phase-06-dialer-messaging.md](phase-06-dialer-messaging.md) | pending | — | FINAL; needs a second phone number for QA |
| 07 | [phase-07-glance.md](phase-07-glance.md) | pending | — | FINAL |
| 08 | [phase-08-cortana-llm.md](phase-08-cortana-llm.md) | pending | — | FINAL |
| 09 | [phase-09-cortana-harness.md](phase-09-cortana-harness.md) | pending | — | FINAL; added 2026-09-16 (phase 08 interview Q3) |

Status values: `pending` → `building` → `QA` → `done`, or `blocked (L<n>)` / `reopened (L<n>)` pointing at the ledger below.
A phase is `done` only when: acceptance criteria + edge cases executed with captured evidence, BOTH reviewers on the `reviewers:` line above judged pass, every NEEDS-HUMAN row has Jeremy's sign-off, and the row links the evidence. Next phase starts only after that.

## Blocked-on ledger (out-of-scope defects: compiler, dependency, tool)

| L | Date | Phase | Symptom (one line) | Repro | Resume point | Fix plan / commit / test | Status |
|---|---|---|---|---|---|---|---|

## Change Log (post-FINAL doc changes only)
- 2026-09-17: POST-FINAL CHANGE (Jeremy, while trying the phase 01 emulator build): "I know this is going to break away from the w10m but I see another thing that needs fixed. there should be a group of riles that are anchored fixed at the bottom which can be moved there but by default should be phone, message and camera"; size ruled "(a)": one row of small tiles, up to 6. Applied to phase-01 (Scope, Decisions "Bottom tile row", DefaultLayout, E21, H35, edge cases) and phase-02 (Decisions, build task, E9, edge cases: moving tiles into and out of the row). PLAN.md feature list updated. Design details are P4 (no W10M original).
- 2026-09-17: docs FINALIZED (Jeremy: "A"): phases 01, 02, 03, 05, 06, 07, 08, 09. Phase 04 stays DRAFT (waits for R4 + its interview). If R4 or the phase 04 interview changes anything in a FINAL doc (e.g. phase 06 voicemail, phase 07/08 dependencies), it lands here as a dated entry.
