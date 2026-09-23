# Session prompt — phase 15 (inbox apps I), parallel worktree

Paste into a fresh Claude Code session started in ~/projects (it creates its own worktree):

---
Load the phased-build skill, read docs/plan/INDEX.md, and follow docs/plan/build-prompt.md — with this session's scope fixed to **phase 15, inbox apps I: Alarms & Clock, Calculator, Voice Recorder** (docs/plan/phase-15-inbox-clock-calculator-recorder.md). It runs IN PARALLEL with a session building phase 11 in the main checkout; phase 15 needs nothing from 11 (depends-on 01, 02, 03, 10). Jeremy cleared the parallel start on 2026-09-23 (INDEX Change Log).

**Isolation — do this before anything else:**
1. Worktree: git -C ~/projects/metro-launcher worktree add ~/projects/metro-launcher-p15 -b phase-15, and work only in ~/projects/metro-launcher-p15. Never edit ~/projects/metro-launcher.
2. Copy the gitignored build inputs the worktree lacks, from the main checkout: local.properties, app/libs/, app/src/main/assets/keyboard/, app/src/main/assets/speech/, and the ignored files in app/src/main/assets/licenses/. Check git status --ignored in the main checkout for anything else under app/ or testapps/ that the build needs, then prove the worktree builds (./gradlew :app:assembleDebug) before writing code.
3. Your own emulator: create AVD tileshell_fhd2 from the same system image and 1080x2340 @ 450 dpi profile as tileshell_fhd, start it on port 5556, and export ANDROID_SERIAL=emulator-5556 for every adb and driver call. Provision it with docs/plan/qa/phase-03/scripts/provision.sh, then finish the setup wizard once as the C-15 rule says. Never touch emulator-5554.
4. Set TMPDIR to a private directory for every driver run, so lib.sh's device lock is yours and never blocks, or is blocked by, the phase 11 session.

**Stage A first, for phase 15 only (it is still DRAFT):** round 3 review — the last allowed — of phase 15 alone (two reviewers per the skill's roster ladder; Opus under Jeremy's standing approval if Fable is at its limit), triage against docs/plan/review/2026-09-23-phases11-20-r2-triage.md, apply the doc updates, bring only true forks to Jeremy one question at a time in the Stage A shape; with no BLOCKING left, set status: FINAL and add the INDEX Change Log line.

**Then build phase 15** per the Stage C loop. Its build task 0, the live-tile routing fix, edits StartPage.kt and MusicFeed.kt, which phase 11 also edits — keep task 0 minimal and self-contained. QA on emulator-5556 through the phase 03 driver floor. In INDEX.md edit only row 15 and your own Change Log lines; commit logically on branch phase-15 with git commit -F <file>. HARD STOP when the row is done. Do NOT merge into main and never push: leave the branch for the lead to rebase onto main after phase 11 lands, resolve conflicts, and re-run phase 15's rows there.
---
