# R4 kit — adversarial review, round 3 (2026-09-28)

**Scope.** I reviewed b7760ae0 against the round-2 report and read r4.sh, the README (now at 01f53715: PQ2 ruled observe-only), Toggles.kt, the P5 and CHANGED paths in ProbeActivity, the manifest, and runs 180205 (clean) and 180521 (two SIGINTs).

**Method.** No adb, no builds, no git writes. I did two things locally:
- **Signal simulations** (bash 5.1.16, coreutils timeout 8.32). The script was run as its own process group, the way a terminal job runs, with SIGINT at its default.
- **AOSP source read** (`main`): BatterySaverStateMachine, and PowerManagerService's `setLowPowerModeInternal` and `runSetMode`.

## Verdict: NOT READY. One blocking finding (R3-1); the fix is about five lines. Everything else holds.

### Round-2 blockers, re-derived
- **R2-1: fixed.**
  - A Ctrl-C inside `{ … } > file` now prints "interrupted" and "Cleaning up" to the terminal through fd 5, and not to the file.
  - In every case I ran, all six clean-up steps finished: a second SIGINT 10 ms or 3 s after the first, bursts of 100 INT / TERM / HUP at 0.1 s intervals, and HUP followed by INT. Both kinds of child survive the extra signals: those in the foreground group (`sleep`, `tr`) inherit the ignore, and those under `timeout` sit in their own process group.
  - A Ctrl-C during a 300 s wait is still held until the wait ends, but the wait is now announced.
  - Run 180521 agrees: "interrupted" is in SUMMARY and not in part3-direct.txt, and clean-up set auto_time back.
- **R2-2: fixed as far as PASS goes.** A PASS now needs an adbd pid that is non-empty and different, plus a ping bound to the helper's pid. A new pid means init ended the adbd service instance, and with it the process group; that is the event under test. `pidof` prints one pid on one line, and an empty ADBD1 gives INFO. The leftover false FAIL is note 1.
- **R2-3: NOT fixed when Power saving is on.** See R3-1. The off case is correct on every path I traced: a full run, a stop in 3a after `set-mode 1`, and "Go ahead? n".

## Blocking

### R3-1 — Power saving that was ON ends OFF, and clean-up says "as it was" (r4.sh:90-97, run at :342 and :155)
**How AOSP behaves.**
- `cmd power set-mode` does nothing while the phone is powered (`setLowPowerModeInternal` returns false when `mIsPowered`).
- On USB with Power saving on, the phone sits in PENDING_STICKY_ON: `low_power` 0 and `low_power_sticky` 1, so BASE_STICKY=1.

**What the kit does to that state.**
- **Blur.** `battery unplug` brings Power saving back (MANUAL_ON), and `set-mode 1` does nothing.
- **restore_sticky, still unplugged.** `set-mode 0` is a manual off: the state goes to OFF and sticky to 0. The following `settings put … 1` only updates the cached flag. In the OFF state sticky is never read; only `onBootCompleted` reads it. So Power saving stays OFF until the next reboot, while the read-back checks only the setting and prints "as it was (1)".
- **Clean-up.** `set-mode 0` while powered does nothing, and sticky already reads 1, so SUMMARY prints **PASS clean-up: every change undone**.

**If One UI keeps Power saving on while charging** (BASE_LOWPOWER=1), the result is the same or worse. The toggle loop sets battery_saver back on (:149), then restore_sticky (:155) runs `set-mode 0` and turns it off again.

Under either behaviour, restore_sticky can only ever turn Power saving off.

**Scenario.** Jeremy runs with Power saving on and the battery below 90 %. He unplugs after the run: Power saving is off, and the summary says every change was undone. R2-3 blocked on exactly this case.

**Fix.** In restore_sticky:
- Set `want=1` when BASE_STICKY=1 or BASE_LOWPOWER=1, otherwise `want=0`.
- Run `dumpsys battery unplug; cmd power set-mode $want`, then read `low_power`. Keep the existing put/delete of sticky, then run `dumpsys battery reset`.
- Print the FAIL line unless `low_power`, read while unplugged, equals `want` and sticky equals BASE_STICKY.

Traced on AOSP: the on case goes MANUAL_ON, then PENDING_STICKY_ON after reset, which is the state before the run. The off case ends OFF with sticky 0 or null. The `low_power` check turns any One UI difference into a FAIL instead of a false PASS.

## Notes (non-blocking)
1. **4d (:324).** If the phone does not come back within 5 min, ADBD2 is empty and the step falls through to FAIL "adbd gone (new adbd pid)". That is a false FAIL on R4's key question. Make an empty ADBD2 an INFO "phone did not come back". If there are several pids, PASS only when no ADBD1 pid is in ADBD2 (a theoretical case).
2. **The accessibility restore can fail and still PASS (pre-existing since 11838efb).** Clean-up writes that line as `accessibility services: FAIL: …` (:140), and `grep -q '^FAIL'` (:161) does not catch it, so SUMMARY stays PASS. Fix: start the line with `FAIL`.
3. **The unreachable-phone message (:123-125)** leaves out automatic date & time and the navigation type. Otherwise the branch is right: FAIL, "reboot", uninstall.
4. **"Go ahead?"** prints the right states and reads from the terminal. With Power saving on, battery_saver shows "off 0" (it is read while charging), so Jeremy may answer n, which is safe. Clean-up ignores Ctrl-C for its whole run; Ctrl-\ still kills it, and the message could say so.
5. **Still open, low odds, the raw output is visible in each file:**
   - A failed BASE_A11Y read becomes "" (:79; R2 note 6).
   - 4c PASSes if the cable is never pulled (R2 note 4). The `transport_id` in `adb devices -l` changes on a replug, which is a cheap proof.
   - CHANGED counts an unknown middle read as a change (R2 note 3).
   - The hotspot probe reports "PROBE reached" on "Unknown command" (R2 note 9).
