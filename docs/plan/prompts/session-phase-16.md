# Session prompt — phase 16 (inbox apps II: Calendar and People), continuing the build

Paste into a fresh Claude Code session started in ~/projects/metro-launcher-p16 (the worktree already exists):

---
Work in ~/projects/metro-launcher-p16 only (git worktree, branch phase-16). Never edit ~/projects/metro-launcher. Load the phased-build skill, read docs/plan/INDEX.md (master), and follow docs/plan/build-prompt.md — with this session's scope fixed to **phase 16, inbox apps II: Calendar and People** (docs/plan/phase-16-inbox-calendar-people.md, FINAL 2026-09-30, 1,700 lines — read it fully before building: Goal, Scope, Decisions, Build tasks, Acceptance criteria, Edge cases). INDEX's row 16 and the Change Log say where the build stands.

**Where it stands (2026-10-01):**
- The worktree was cut from phase-14 at 9db00b2a (phases 14 and 15 are in it; phase 14 is "QA passed, NEEDS-HUMAN open", phase 15 is done). The git-ignored build inputs are hard-linked in (local.properties, keystore.properties, app/libs/, app/src/main/assets/keyboard/, app/src/main/assets/speech/, five licence files). Baseline unit suite: 946 tests, 0 failures (docs/plan/qa/phase-16/baseline-unit.out).
- Build task 1's CODE is in (commit 4ca33f2e): the slot seed's guard and the one-time take-over (tiles/SlotSeed.kt, LayoutStore.assignSlotOnce(…, takeOver), SlotSeedTest 6/0), and the slot resolver and picker matching by component (tag slot_candidate:<flattened component>). NOT yet done from task 1: the two slot claims in ShellApp (slot:calendar:v1 and slot:people:v1, both passing takeOver — they need task 2's activities), the JVM test named in the doc for the guard is SlotSeedTest, and E1's device legs (wiped, upgrade, guard).
- Next: build task 2 (app identities and contracts), then the five "Verify at build start" checks on the AVD (Decisions, recorded under docs/plan/qa/phase-16/BUILDSTART/) before task 3, then tasks 3–9 in order.
- The pre-16 APK for E1's upgrade leg is kept locally at docs/plan/qa/phase-16/upgrade/phase-14-aaf9a1d8.apk (md5 aaf9a1d8ed536d25, the build of phase-14's c0d094ad app tree; git-ignored, never commit it). It is also what is installed on the emulator right now.
- Owed at this phase's build, from the round-3 triage (docs/plan/review/2026-09-30-phase16-r3-triage.md): the trust list in build-prompt.md (line 21 names only the write guard); phase 14 E3's Agenda-launch precondition and driver re-cut, with an INDEX Change Log line; the doc writer's own decisions listed at the end of that triage stand unless Jeremy overrules them.

**Standing rules (Jeremy):**
- NEVER use Fable for anything — reviewers, subagents, forks, the main loop. Reviews are Opus + Opus (pass model: opus), each with a different lens; codex replaces the second Opus only if it is back (its CLI limit ends 2026-10-03 16:48; the codex MCP has been failing to connect). Never Gemini, never Sonnet.
- Never touch the host's audio (no pactl, no audio.sh setup, no hostmicon). No microphone in QA: phase 16's rows are typed (type_request) and tapped. If a spoken step is ever needed, only the emulator's own gRPC route (AUDIO_ROUTE=emu, qa/phase-03/scripts/emu_audio.py).
- ANDROID_SERIAL=emulator-5554 only (AVD tileshell_fhd). If it is down, relaunch it with exactly: QEMU_AUDIO_DRV=none ~/Android/Sdk/emulator/emulator -avd tileshell_fhd -no-snapshot-load (in the background), wait for sys.boot_completed, and tell Jeremy you did. If a step crashes the emulator twice, stop and tell him rather than relaunching again.
- Phone checks are phone-only: anything on the S25 Ultra is something Jeremy does on the phone and reports back; never a PC / USB / adb step.
- Never push without Jeremy saying push. The push hook also needs him to run `touch ~/.claude/push-approved` on this machine himself — you cannot create that flag. phase-16 has no remote branch yet; phase-14 is pushed (origin/phase-14 = 9db00b2a).
- Stage A question shape for anything you must ask him: one question per message, at least three lettered one-line choices with your lean marked, "D. Other / let me clarify" last, in plain prose (never the AskUserQuestion tool); record his answer, dated, before the next question.
- Verify by running; quote real output. Read exit codes from captured files, never through a pipe. Kill processes only by recorded pid. Keep all QA evidence and earlier runs (rename, never delete). Timestamps you write come from `date`.
- Commit logically on phase-16 (never squash, never `git add -A` / `git add .` — a hook blocks them; add explicit paths). A hook also blocks a commit command that contains backticks anywhere in it: write the message to a file and use `git commit -F <file>`. End every commit message with the attribution lines the session's system reminder gives.
- State lives in files: update INDEX.md's row 16 and .claude-build-state.md every iteration; an out-of-scope defect (a `done` phase's part, a tool, a dependency) goes on INDEX's Blocked-on ledger — stop, log, report, plan the fix with Jeremy, then fix and resume.
- Lessons from phase 14's gate that apply here (docs/plan/review/2026-09-30-phase14-gate-r1-triage.md, 2026-10-01-phase14-gate-r2.md): an absence check must read a slice proven readable (`absent_in` in qa/phase-14/scripts/p14.sh); every row runs again on ONE final APK before the gate reviews, and any product change after that voids it; edge cases need their own driver row with an index; run the whole unit suite (`./gradlew :app:testDebugUnitTest`) before each commit that touches app code — a narrower run missed a broken test once.
- Any trust-touching part (permissions, exported components, the calendar and contacts write guards, the reminder receiver, anything over the keyguard) gets an adversarial Opus review before `done`.
- HARD STOP when phase 16's row is `done` (which needs Jeremy's sign-off on every NEEDS-HUMAN and phone row): write the handoff summary and end. Never begin, prep or read the next phase.
---

Notes for Jeremy (not part of the prompt):
- This prompt was written at the end of the phase 14 session, 2026-10-01. If phase 16 has moved on since, INDEX.md wins over anything above.
- The emulator was left running with phase 14's final build installed.
