# R4 kit — adversarial review, round 2 (2026-09-28)

Scope: 11838efb against fac112c0 and the round-1 report. I read r4.sh, README, the manifest, a11y_probe.xml, HelperMain, HelperProvider, Toggles, ProbeActivity and ProbeA11yService, plus the runs 164150 (clean) and 164919 (SIGINT). No adb, no builds, no git writes. I ran three local checks: (1) every Toggles.kt pattern through GNU grep 3.7 `-Eiq` (the grep bash resolves to) and through java.util.regex with CASE_INSENSITIVE, on 43 realistic reads; (2) bash 5.1.16 plus coreutils timeout 8.32 signal simulations; (3) `git check-ignore` on an `SM-S938U_` run folder, which is ignored.

## Verdict: NOT READY. Three blocking findings, all small to fix.

**Round-1 items, re-checked:**
- **B1: fixed.** Kotlin and grep give the same state on all 43 reads (0/1/null/2, true/false, `mState=on|off|turning on|ON|3|ON_READER`, blank, `Can't find service`, `Wifi is enabled|disabled`, `Wi-Fi is enabled`, adb errors). No read matches both patterns, and blank or error reads come out unknown and are not flipped. Clean-up now FAILs on a toggle it cannot set back. Two caveats: the reads themselves may be stale on the phone (note 2), and battery saver is blind (R2-3).
- **B4: fixed.** A PASS needs `Now forced in to deep idle mode`, and the ping runs before `unforce`.
- **B5: fixed.** TYPE_ACCESSIBILITY_OVERLAY is added through a real service. The emulator touch lists differ (a11y: the strip tap arrives; app: it does not). The two screenshots look the same, so the touch list is the only evidence that tells them apart.
- **B2: partly fixed.** The trap exists and runs on every path I traced, but it can be cut short and it runs silently (R2-1).
- **B3: partly fixed.** The step can now reach adbd-gone, but nothing records that adbd actually stopped (R2-2).
- **Claimed notes: fixed.** Screen kept on, run-as check before any change, no serial or pairing output, the sh() timeout fires, TIMED OUT instead of a stale section, rebuild every run, P5 taps only after WINDOW UP, ping bound to the pid, orphan helpers found by a ps scan.

**New surface that holds up:**
- **The accessibility service** can only be bound by the system (BIND_ACCESSIBILITY_SERVICE). It has `canRetrieveWindowContent=false` and no gesture, screenshot or button capabilities, and it ignores the window-state events it gets.
- **Restoring the service list is quoted correctly.** Putting `"'$BASE_A11Y:$A11Y'"` through adb shell survives several services and `$` in class names. The null case goes through delete (seen in run 164150), and the "" case round-trips.
- **The hotspot is never started.** Neither pass nor clean-up can flip it, and `start-softap` with no arguments throws before it starts anything.
- **Every adb call still works.** `install`, `exec-out screencap` and `uninstall` run through the wrapper; `pair`, `connect`, `-s` and `wait-for-device` call `timeout N adb` directly.
- **Ctrl-C during a wrapped adb call is safe.** timeout puts adb in its own process group, so the command in flight finishes before clean-up reads the toggle (the Bluetooth line in run 164919 shows this).

## Blocking

### R2-1 — Clean-up can be cut short, and the first Ctrl-C often shows nothing (r4.sh:103, :143, :213-236, :259-262, :270, :292)
**Silent first Ctrl-C.** The INT trap and clean-up write to whatever stdout is open at that moment. Inside a `{ … } > file` block that is the file, not the terminal. The committed run proves it: runs/20260928-164919-*/part3-direct.txt ends with "interrupted — cleaning up / PASS clean-up / Remember… / Run folder…", none of which reached the terminal. In my simulation the terminal stayed empty.

**Ctrl-C ignored for minutes.** Ctrl-C is not acted on until the current `timeout` child ends (my simulation: the trap fired only when `timeout 6` ended). At 4c/4d that child is `timeout 300 adb wait-for-device`, which prints nothing first, so a phone that does not come back means up to 5 minutes of silence in which Ctrl-C does nothing.

**The second press kills the clean-up.** `trap - … INT TERM HUP` puts the default handlers back. In my simulation a second SIGINT 2.5 s into clean-up killed it after 2 of 6 steps, with no final line.

**Scenario.** The owner stops during 4b's silent 20 s wait, sees nothing, and presses Ctrl-C again. Clean-up dies in kill_helper's `sleep 1`, before `deviceidle unforce` and `battery reset` (:110-111). Until reboot the phone stays in forced deep Doze (notifications and alarms held back) with its battery reported unplugged, and the helper and probe stay behind; in P5 the probe's accessibility service would stay on too. SUMMARY has no clean-up line. Closing the terminal can do the same: zsh's SIGHUP and the kernel's SIGHUP can both arrive, and the second lands after `trap -` (a race; I did not simulate it).

**Fix:**
- Make clean-up's first lines `trap '' INT TERM HUP; trap - EXIT`, so children inherit the ignore and Ctrl-\ still works.
- Run `unforce` and `battery reset` first.
- Add `exec 5>&1` after `OUT=`, and start the INT trap with `exec >&5 2>&5`.
- Print "waiting up to 5 min for the phone…" before both `wait-for-device` calls.

### R2-2 — 4d can PASS without adbd ever stopping (r4.sh:291-296)
The PASS rests on the owner doing four things by hand. If he leaves USB debugging on (for fear of losing the run), or if One UI keeps adbd up with USB debugging off, adbd is never stopped. The helper then lives and "4d adbd gone: helper lives and answers" PASSes. That is round-1 B3's vacuous PASS again, and phase 04 builds on it.

**Fix:** record `adb shell pidof adbd` before the :291 prompt and again after `wait-for-device`, and write both to part4-adbd-gone.txt. PASS or FAIL only when both pids are non-empty and differ; otherwise record INFO "adbd was not restarted: not tested".

### R2-3 — Clean-up cannot see battery saver, so its PASS can hide a change to Power saving (r4.sh:80, :111, :119-131, :309-311; README:56-58)
AOSP behaviour (not verified on One UI; the fix is harmless either way):
- `cmd power set-mode` is a manual switch, and it writes `low_power_sticky`.
- While the phone is powered, battery saver is forced off, so `low_power` reads 0 whatever the sticky intent.

Clean-up runs `battery reset` (plugged in) before the toggle loop, so battery_saver always reads "as it was (off)". Two scenarios:
- **Complete run, owner uses Power saving.** Blur's final `set-mode 0` (:311) clears sticky, so Power saving does not come back after unplugging (round-1 note, still open).
- **Stop inside the ~3 s after `set-mode 1` in 3a/3b, or during blur's power-saving pass.** Sticky stays 1, so Power saving switches itself on after unplugging.

Both end with "clean-up: every change undone", and README:57 promises "power saving put back".

**Fix:** add `low_power_sticky` to baseline-settings.txt. Put it back (or delete it if it was null) after :311 and in clean-up after `battery reset`, and FAIL in cleanup.txt if it does not read back.

## Notes (non-blocking)
1. **Clean-up with the phone unreachable** (4d skipped with USB debugging off, or the 300 s wait expiring): `alive` returns false on an adb error, so cleanup.txt says "helper … stopped" (:94, :99), which is not true. Every toggle is a false-alarm FAIL, and nothing says "reboot the phone". Check `adb get-state` first and say so.
2. **Reads the engines agree on but the S25U may not track:** the global `mobile_data` (on a phone set up for dual SIM the live key is `mobile_data<subId>`), and the first `mState[=:]` line of Samsung's `dumpsys nfc`. A stale read that disagrees with the real state makes "back" flip the real state, and clean-up then says "as it was". Cheap guard: print baseline-states.txt at "Go ahead?" and have the owner confirm each state.
3. **Two bookkeeping gaps.** Part 3 flips on its own fresh read (:222), but clean-up restores only toggles recognised at baseline, so ":123 never flipped" can be false (flip only baseline-recognised toggles). CHANGED counts an unreadable mid read as a change (:234, ProbeActivity.kt:255); require the opposite state.
4. **4c still PASSes if the cable was never pulled** (round-1 note, unchanged).
5. **Untested paths.** No run exercised 4c/4d (the new pair / connect / `-s` / adbd-off path), Ctrl-C at a prompt, in 3b or in P5, a second signal, or a lost phone. `unforce` and `reset` are not logged, so cleanup.txt cannot show the commit's claim "reset the battery report".
6. **A failed baseline read wipes the owner's accessibility services.** A transient failure on `BASE_A11Y` (:78) reads as "". The owner's services are then replaced, "restored" to "", and reported "as they were". Check adb's exit status on the baseline reads (:76-80).
7. **`sleep 4` after enabling the service (:321) may be short on One UI.** The first a11y pass would then report "not connected". Wait for "accessibility service connected" in the report instead.
8. **Pairing is left on the phone.** The PC stays in Wireless debugging's paired devices (not in README "What happens"). On a re-run with Wireless debugging on, the mDNS transport has no ":", so the device count is 2 and the run refuses to start.
9. **Hotspot "PROBE reached (no state changed)"** is printed for any output that is not a SecurityException, including "unknown command".
10. **Still open from round 1:** the exported ProbeActivity is a confused deputy, now also for `overlay_a11y` (a 15 s full-screen, touch-eating overlay above the nav bar while the service is on). Pid-file kills (:148) skip the `/proc/<pid>/cmdline` check (moot on a fresh phone; the ps scan covers orphans). A part-one probe already on the phone is uninstalled at the end.
