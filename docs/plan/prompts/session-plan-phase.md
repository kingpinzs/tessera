# Session prompt — planning a phase (session 1 of 2 for every phase)

Jeremy's rule (2026-10-05, INDEX Change Log): every phase is TWO sessions — a planning session (this prompt), then the
context is cleared, then a clean build session started from the prompt this session writes. Replace NN with the phase
number and paste into a fresh Claude Code session:

---
Plan **phase NN** of metro-launcher — planning only; nothing of phase NN is built in this session. Load the phased-build skill and read docs/plan/INDEX.md (master; its Build order names the phase after the last one built). Then read docs/plan/phase-NN-*.md fully, the INDEX rows and Change Log entries it depends on, and the research it cites (docs/plan/r*/, docs/plan/r*-*.md).

**Where to work:** the phase's own worktree, ~/projects/metro-launcher-pNN on branch phase-NN. If it does not exist yet, create it from the tip of the previous phase's branch (`git worktree add ~/projects/metro-launcher-pNN -b phase-NN <previous phase branch>`), then hard-link the git-ignored build inputs in from the previous phase's worktree: local.properties, keystore.properties, app/libs/, app/src/main/assets/keyboard/, app/src/main/assets/speech/, and the licence files the root .gitignore names. Never edit ~/projects/metro-launcher. All planning commits go on phase-NN, so the build session starts from them.

**What this session does — Stage A for phase NN, in order:**
1. Find what is left of Stage A for this phase (INDEX's Stage A table and the doc's frontmatter): research gaps the doc marks UNMEASURED or LOW, review rounds not yet run (cap 3), questions not yet answered.
2. Research, if a gap blocks the doc: Opus research agents (`model: opus`), read-only on tracked files, sources saved git-ignored under docs/plan/r*/src/; the lead writes their reports into dated addenda.
3. Review: two Opus subagents (`model: opus`) with different lenses — design / correctness, and testability / evidence — from a brief written in the form of docs/plan/review/2026-10-04-phase17-r3-brief.md (what changed since the doc was written: phases built since, code as built, QA rules, research addenda). Save each review and a triage to docs/plan/review/<date>-phaseNN-r<k>-*.md.
4. Triage: agent fixes are applied to the doc (an Opus agent may apply them; the lead reviews the diff); anything that contradicts or cannot meet one of Jeremy's rulings becomes a question for him.
5. Ask him each question in the Stage A shape, one per message; record each answer, dated, in the doc's Decisions before the next; apply what it changes.
6. Offer FINAL only after walking every Stage A step for this phase and showing when each ran. On his word: set the doc's status FINAL, update INDEX (Stage A table, row NN, Change Log).
7. Write the build session's prompt, docs/plan/prompts/session-phase-NN.md, in the form of docs/plan/prompts/session-phase-17.md (where it stands; the standing rules; the hard stop), and commit it on phase-NN. The build prompt MUST carry, word for word, the rule "Build, push, then ONE round of testing" from the standing rules below, as its own bullet, and its acceptance rows are written to be run once (step 3 of that rule): few rows, each a thing a person would do on the phone, with unit tests carrying correctness.
8. HARD STOP: tell Jeremy the plan is FINAL and the build prompt's path, and that the next step is to clear the context and start the build session from it. Do not start the build.

**Standing rules (Jeremy):**
- NEVER use Fable for anything. NEVER use codex in this project (his work account, 2026-10-04). Reviewers and research agents are Opus (pass model: opus). Never Gemini, never Sonnet.
- Never touch the host's audio; no microphone use. Do not run QA or gradle in a planning session unless a research question needs a fact only the code or the AVD can give — then read-only checks only, ANDROID_SERIAL=emulator-5554.
- Phone checks are phone-only: anything on the S25 Ultra is something Jeremy does on the phone and reports back; never a PC / USB / adb step.
- QA run evidence is never committed (docs/plan/qa/.gitignore). Never push without Jeremy saying push and running `touch ~/.claude/push-approved` himself; push the moment the flag exists, with a command holding only the push.
- Stage A question shape: one question per message, at least three lettered one-line choices with your lean marked, "D. Other / let me clarify" last, plain prose (never the AskUserQuestion tool). Verify any fact a question rests on before asking it.
- Timestamps you write come from `date`, read before you write them. Commit logically (never squash; explicit paths; commit messages with backticks go through `git commit -F <file>`), ending with the session's attribution lines.
- **Build, push, then ONE round of testing (Jeremy, 2026-10-07: "it does ALL building first then pushes code then does 1 round of testing of what it built"; he also said of phase 18's gate, "Its taking too long and its burning tokens").** A build session runs in exactly this order:
  1. **Build everything first.** Every build task of the phase is built, compiles, and has its unit tests green before any QA row is written or run. Builders may drive their own screens on the emulator while building (that is building, and their notes are kept), but no graded row, no review and no gate work starts until the last build task is committed.
  2. **Then push the code.** When the build is complete: list what the push carries, tell Jeremy the build is ready to push, and push `phase-NN` the moment `~/.claude/push-approved` exists (he creates it; a command holding only the push). Testing does not start before the push. If he has not made the flag, wait for him there — do not go on to testing unpushed.
  3. **Then one round of testing of what was built.** Each acceptance row and edge sub-step runs ONCE on the pushed build. A row that fails on a product defect is fixed and THAT row alone is re-run; a driver fault is fixed and that row re-run. There is no second pass over rows that passed, no second review pass, and no mutation round. A trust-touching part still gets its one adversarial Opus review inside this round — one pass, one fix round; a defect it finds in an OLDER phase's part goes on INDEX's Blocked-on ledger and is not fixed in this session unless he says so. Then the session writes NEEDS-HUMAN.md and stops for his sign-offs.
  This replaces, for this project, the skill's "repeat until both reviewers pass" loop and its two gate reviewers; the hard stop, the ledger rule and the push rule are unchanged.
- State lives in files: INDEX.md, .claude-build-state.md, and the tracking page https://claude.ai/artifact/3JgyGQ999cbUZ9nJtdX6aD (ArtifactData: board/now, board/log, phases/NN) at every step.
---
