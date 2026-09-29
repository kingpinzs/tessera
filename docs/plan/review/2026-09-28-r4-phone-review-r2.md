# R4 phone-only run: adversarial safety review, round 2 (2026-09-28)

**Scope.** Read-only review at HEAD 0edd2d47. 439fbc76 holds the round-1 fixes; 0edd2d47 landed during this review and stops the Doze safety net after 4b. I read Runner, ProbeActivity, AdbSelf, HelperMain, HelperProvider, PairReceiver, Toggles, ProbeA11yService, RunToken and Discovery, plus the manifest, the run sheet, round 1, and the emulator runs 2054 (full run and B1) and 2102 (B1 carried). I did not build, use adb or write to git. Line numbers are at 0edd2d47.

## Verdict: NOT READY. Three new blocking findings, each a few lines to fix. The B1 fix introduced or exposed all three.

## Round-1 blockers, re-derived from the code
- **B1: fixed for its scenario.** After a kill mid-run, Run R4 first restores against the saved baseline (Runner.kt:123-130). Before the new baseline it clears Doze and the battery report and stops leftover helpers (:259-265). The 2102 run shows a kill at p3 put back and carried into the new report. B4 and B5 below come out of this fix.
- **B2: fixed.** Taps go out only while WINDOW UP holds (:508). `am start` with no extras brings the singleTop probe back (:510).
- **B3: fixed.** 4d records INFO unless `before` is a list of pids (:609).

## Blocking

### B4: A run with no baseline is still "put back": Power saving forced off, or R4 can never start again (Runner.kt:123, :188, :269, :274, :475-481)
**What happens.** Any run that stops before its baseline is complete still goes through stageCleanup:
- Stop or the 15-minute timeout while pairing (:216, :225);
- a failed baseline read (:269, :274), the new "stopped before any change" path;
- start() restoring an old run left at 'pair', 'baseline' or 'cleanup-pending'.

With no `set.global low_power*` recorded, restoreSticky reads "" as "off". It runs `cmd power set-mode 0` and `settings delete global low_power_sticky`, then prints "as it was" and **PASS clean-up: every change undone**. The navigation check also adds a false "NOTE: … switch it back".

**Scenario A: stuck for good.** On the first run Jeremy taps Stop while pairing, or it times out. adb was never paired, so the clean-up FAILs and tells him to check Wi-Fi, Bluetooth, NFC and the rest by hand, although nothing was changed. The stage becomes 'cleanup-pending'. Every later Run R4 tries to restore it first. That needs adb, adb needs pairing, and only a new run pairs, so it FAILs every time until the app's data is cleared.

**Scenario B: a setting changed under a PASS.** A second run starts with Wireless debugging off (4d of the first run turned it off). stagePair waits only for a new pairing (:215-223) and never tries the key it already has. Jeremy turns Wireless debugging on and taps Stop (the run sheet says pairing is first-time only), or waits out the 15 minutes. The clean-up now reaches adb, turns his Power saving off, deletes low_power_sticky, and PASSes.

**Fix.** `put("baseline_done","1")` just before the Go ahead prompt (:278). When it is missing, stageCleanup returns true with "no baseline taken: nothing to put back" (after unforce and battery reset if adb is up). restoreSticky leaves Power saving alone when `set.global low_power` is empty.

### B5: Restore now never appears; the only retry deletes the failed run's report (ProbeActivity.kt:110-111; Runner.kt:128-131, :167)
**What happens.** renderPrompt() runs straight after offerUnfinished() in the same post and wipes the offer. This predates 439fbc76; round 1 gave a different cause. The new FAIL text (:570, "offered until the clean-up passes") and run sheet line 53 both promise it. So the only retry is Run R4, and start() deletes run-summary and run-details, keeping only the retry's own clean-up.

**Scenario.** adb does not come back after the Wi-Fi flip, and the clean-up FAILs ('cleanup-pending'). The FAIL line says to open R4 probe again to retry, so Jeremy taps Run R4. The restore passes and a new run starts. The report he pastes says "an earlier run … was put back first: every change undone". The first run's results are gone, and so is the failure, which is itself an R4 finding.

**Latent.** Once the offer renders, "Carry on" on 'cleanup-pending' becomes `indexOf -1 → 0 = 'pair'` (:167). That takes a fresh baseline over the settings the failed clean-up left changed: round 1's B1 again.

**Fix.** Call renderPrompt() before offerUnfinished(). resume() sends 'cleanup-pending' (or any stage not in STAGES) to restoreNow. start() carries the old summary and details into the new report instead of deleting them.

### B6: 4b can PASS after the safety net has already ended Doze (Runner.kt:421-429)
**What happens.** The net exists for the case where the probe's adb stalls in forced Doze. In that case the `ps` at :427 blocks until the net's unforce at 40 s. The ping then succeeds, and PASS "4b forced deep Doze: the helper lives and answers" is recorded for a phone no longer in Doze. The `get deep` read (:428) comes after the verdict and does not gate it.

**Fix.** Read `dumpsys deviceidle get deep` first. PASS only when it says IDLE; otherwise INFO "Doze ended before the check: not tested".

## Held on re-derivation
- **Doze safety net (0edd2d47).** `$!` is the net's own pid: `setsid` is not a group leader under `sh -c`, so it runs in place. The net is killed after 4b's own unforce and reset, so it no longer lands in blur's unplugged Power-saving pass.
- **Leftover-helper sweep.** It matches only the literal `app.tessera.r4probe.HelperMain --daemon` command line. `grep -v grep` drops its own pipeline, only integer first columns are used, and the shell uid cannot signal another uid.
- **start() races.** A second tap is ignored, because `running` is set on the main thread before the thread starts. A 4a `run=resume` that arrives during the restore is ignored the same way.
- **Redaction.** It only rewrites dotted quads, so it cannot hide a FAIL.
- **Lock screen.** setShowWhenLocked and setTurnScreenOn are fine for one run. While the probe is in front, Run R4 and Go ahead can be tapped on a locked phone, so uninstall it after the run, as the run sheet says.

## Notes (non-blocking; do 1 before the run)
1. **Airplane mode with Wi-Fi remembered (Android 14+).** If Jeremy ever turned Wi-Fi on during a flight, the airplane flip keeps both Wi-Fi and adbd's port. bringBackAdb (:378) marks that live port dead, and ensureAdb (:95) refuses it for 3 minutes. The run records "FAIL: adb NOT back", and every step up to the clean-up fails, safely. The one run is wasted and its adb finding is false. Try the "dead" port last instead of never.
2. **endHelper.**
   - Its "stopped" is not checked after 4d: adb is gone, so `gone` (:624) compares "adb not reachable" with the pid. Require the `ps` to have run.
   - After a reboot, the old helper_pid may belong to another shell-uid process (a Shizuku server, say), and :622-623 would kill it. Check `ps -o ARGS -p` for HelperMain first.
3. **4d `after`.** A failure string from the helper (a timeout or an exception) counts as a "new pid" and PASSes (:612). Accept only "" or a list of pids.
4. **start():** `running = false` just before launch() (:138) leaves a microsecond gap in which a second tap starts a restore of stage 'pair' (B4). Drop the line; launch() sets it.
5. **Privacy.** The HELPER section of the copied report still shows "paired with <LAN IP>:<port>" unredacted (PairReceiver.kt:30 through HelperLink's notes), although the run sheet says the address is written as "<this phone>".
6. **P5 second pass.** If switching the navigation type does not recreate the activity, the runner can read the last pass's "OVERLAY DONE" before the UI thread clears it. It then sends no taps and says "no window came up", which is untrue but errs on the safe side. Clear the result on the runner thread, or match a per-pass nonce.
7. **Still open from round 1.**
   - Kadb shell has no timeout: outside 4b, a hang mid-change stalls with the setting still changed.
   - After bringBackAdb fails, the run keeps asking for blur looks and a navigation switch for about 10 minutes, and they measure nothing.
   - PairTestReceiver still ships in the release, and kadb and spake2 are not checksum-pinned.
8. **Evidence gaps.** The emulator never exercised a failed restore or 'cleanup-pending', the offer, a Stop at pairing, forced Doze with the net, P5's second pass, or the sweep killing a real leftover (the restore's endHelper had already stopped it).
