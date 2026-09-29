# R4 phone-only run: adversarial safety review, round 3 (2026-09-28)

**Scope.** Read-only review at 7ffe360e, the commit with round 2's fixes. I read Runner, ProbeActivity, AdbSelf, HelperMain, HelperProvider, PairReceiver, Toggles, Discovery, RunToken and ProbeA11yService, plus the manifest, the run sheet, rounds 1-2 and kit review r1 B3. Emulator evidence: harness-r2fix-b4-b5, harness-r2fix-full, run-details-r2fix and r2_b4_b5_test.sh. I did not build, use adb or write to git. Line numbers are at 7ffe360e.

## Verdict: NOT READY. Two blocking findings, each a few lines to fix. B7 is a regression from the r2 fixes; B8 is new to the phone-only run.

## Round-2 blockers, re-derived from the code
- **B4: fixed.** `baseline_taken` is written only after every baseline read succeeds (Runner.kt:289). Nothing is changed before that point: the pre-baseline block (:269-275) only unforces Doze, resets the battery report and stops R4's own helpers. Every early-stop path goes through restoreIfNeeded (:125, :169, :198, :204), which does nothing without the flag. restoreSticky refuses a starting value that was never read (:496).
- **B5: fixed.** The offer is drawn after renderPrompt (ProbeActivity.kt:108-112), and `offering` keeps it up until a button is tapped or a run starts (:130). A run stopped at 'cleanup-pending' is offered only Restore now (:145-153), and resume() sends that stage to restoreNow (Runner.kt:149). start() carries the old summary and its restore into the new report (:128). The harness's screen dump shows the offer.
- **B6: fixed.** 4b reads `get deep` first and passes only on IDLE (:442-450). In the stall case that read is the call that blocks, so it sees the safety net's unforce and records INFO. With the screen off and the battery reported unplugged, deep can still be IDLE after the unforce, and a PASS is then still true.

## Blocking

### B7: 4d reports the helper dying as "NOT tested" (Runner.kt:632-633; a regression from the r2 note-3 fix)
**What happens.** The new INFO branch "the helper's adbd read failed" (:632) sits above the ping FAIL (:633). With the helper dead, HelperLink.call catches the DeadObjectException and returns exit -1 with "call failed: …" (HelperProvider.kt:88-90). That is a non-empty read that is not a pid, with a non-zero exit, so :632 fires. The run records **INFO "4d NOT tested: the helper's adbd read failed"**, and the FAIL "the helper did NOT answer" can no longer be reached in the very case it exists for. Before 7ffe360e this case was a FAIL.
**Scenario.** This is the likely 4d outcome. Kit review r1 B3 explains why: init stops adbd by killing its whole cgroup, and setsid does not take the helper out of that cgroup. Jeremy turns both debugging switches off and the helper dies with adbd. Phase 04's key question, whether the helper outlives adbd, then comes back as "not tested" instead of "no".
**Fix.** Put `!ping.contains("pid=$pid ") -> FAIL` above the after-read branch, so a failed read counts as "not tested" only while the helper still answers.

### B8: With USB debugging off, the 3b Wi-Fi flip stops adbd, the helper dies with it, and Wi-Fi stays off (Runner.kt:366-368, :328, :379; run sheet step 2)
**What happens.**
- Turning Wi-Fi off makes Android turn Wireless debugging off, which the run relies on (:383).
- In AOSP, AdbService.stopAdbd() then runs `ctl.stop adbd` if USB debugging is also off, and init kills adbd's cgroup, helper included. This is kit review r1 B3's mechanism.
- Neither the run sheet nor the baseline asks for USB debugging, and it is off by default when Developer options are first turned on.
- Every emulator run had it on (`global adb_enabled = 1`, run-details-r2fix.txt:6), so this path has never run.

**Scenario.** USB debugging is off.
1. The helper turns Wi-Fi off (:366) and is killed before its flip back 4 s later (:368). The app cannot turn Wi-Fi on, and WD_ON goes to a dead binder.
2. bringBackAdb FAILs after 3 minutes. The battery reset (:379) fails, so the phone stays reported unplugged (:328).
3. 4a–PQ2 then run for about 15 minutes against nothing: every sh() spends 6 s in discovery, and Jeremy is still asked the blur and navigation questions.
4. The clean-up FAILs: the binder is dead and adb needs Wi-Fi. Wi-Fi stays off and the battery stays falsely "unplugged" until Jeremy turns Wi-Fi on and retries, or reboots.
5. A re-run repeats all of this.

This is unverified on One UI, which may differ from AOSP.
**Fix.** In stageBaseline, before Go ahead: if `global adb_enabled` is not 1, ask Jeremy to turn USB debugging on (no cable is needed) and read it again. Keep the earlier value for 4d's "turn it back on" line (:637), and add the step to run-sheet step 2. adbd then stays up while Wireless debugging drops, as on every emulator run, and 4d stays the only step that tests adbd going away.

## Held on re-derivation (round 2's other changes)
- **"adb stayed up" (:390).** The echo uses the existing connection. Losing Wi-Fi drops the address, and adbd drops its TLS connections when Wireless debugging goes off, so the echo fails fast (both r2fix flips show "adb back after 9 s"). A Wi-Fi that stays on in airplane mode keeps its live port. The dead port is tried again after a minute (:94, :393).
- **endHelper (:645).** It kills a pid only if the pid's ARGS still name r4probe.HelperMain. app_process keeps its argv when no --nice-name is given, and the harness counts helpers with the same match.
- **Redaction (ProbeActivity.kt:296, :616).** Only dotted quads outside 127.x are rewritten, so a PASS or FAIL word cannot change, and no logic reads the redacted files back.
- **The `run` check before the offer (:112).** It is always true after handleRun removes the extra (:192). This is harmless: start, resume and restoreNow all set `running` on the main thread before the check runs.

## Notes (non-blocking)
1. **The committed evidence never exercised B5's Restore now.**
   - r2_b4_b5_test.sh looks for `text="Restore now"`, but the screen dump has "RESTORE NOW", so the tap never happened. harness-r2fix-b4-b5.txt then shows stage 'p3', 1 helper and low_power 1.
   - Something unrecorded cleaned the emulator before the full run, which carried nothing over. The commit message's "Restore now put back … battery saver, battery report and helper" is not what the file shows.
   - The B4 check ran with adb unreachable and sticky 0, so it cannot show B4's Power-saving half.
   - Re-run both with a case-insensitive match.
2. **Restore now does not come back on the same screen.** The FAIL text says it "is offered until the clean-up passes" (Runner.kt:592), but the offer is drawn only in onCreate, so reopening from the launcher after a failed clean-up shows none. The obvious tap is then Run R4, which restores and starts a new run. That deletes the failed run's details, because only its summary and its restore are carried (:128, :130). Fix: re-offer from onChange when a run ends unfinished, and carry all of details().
3. **Stop at "Go ahead" still runs a clean-up**, including restoreSticky's set-mode, although nothing changed. Writing baseline_taken after the answer (:294) makes that Stop touch nothing.
4. **Still open from rounds 1-2:**
   - Kadb shell has no timeout.
   - After adb is lost, the run keeps asking questions for 10-15 minutes. B8 makes this the likely path; it should skip to the clean-up.
   - After 4d, endHelper's "stopped" compares a failure string with the pid (:647).
   - PairTestReceiver ships in the release, and kadb and spake2 are not checksum-pinned.
