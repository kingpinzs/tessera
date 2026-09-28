# R4 kit — adversarial review (2026-09-28)

Scope: qa/r4/README.md, qa/r4/r4.sh, r4probe (manifest, HelperMain, HelperProvider, Toggles, ProbeActivity), PLAN.md R4 (l.354-361), INDEX R4 row (l.41), the three emulator dry runs, .gitignore. I only read files: no builds, no adb, no git writes. The one thing I executed was the host's own grep on the toggle patterns (B1).

## Verdict: NOT READY. Five blocking findings. Each fix is small except B5.

### B1 — NFC is left OFF, and clean-up reports it as "set back" (r4.sh:129, :266; Toggles.kt:34, :9)
The host checks the Java-syntax pattern `(?i)(mState|state)[=:]\s*on` with `grep -Eq`. Under bash that is GNU grep 3.7, which reads `(?i)` as "an optional literal i". I checked on this PC: `mState=on` gives rc=1 and `imState=on` gives rc=0, so the NFC on-pattern never matches.

- **Direct pass:** treats NFC as off, runs `svc nfc enable` then `svc nfc disable`, and ends with NFC OFF.
- **Helper pass:** its Kotlin Regex handles `(?i)` correctly, so it restores to the already-off state.
- **Clean-up (:266):** sends the same pattern through the same grep, picks `$off`, and NFC stays off. SUMMARY says "INFO some toggles had to be set back", so the owner loses tap-to-pay without being told.
- **If Samsung's `dumpsys nfc` has no matching line:** the read is blank before and after, NFC still ends off, and clean-up prints **PASS**.

This comes from the rule "a read the pattern cannot place counts as off" (Toggles.kt:9). That rule is safe for hotspot and airplane mode but harmful for NFC, Wi-Fi, location, Bluetooth and mobile data. The emulator has no NFC, so no dry run could have shown this. The hotspot pattern (l.43) has the same grep bug; it is harmless only because off is the safe direction there.

**Fix:** remove the inline `(?i)` (use `RegexOption.IGNORE_CASE` in Kotlin and `grep -Eiq` on the host). Give each toggle an explicit off-pattern, and don't flip a toggle whose before-read matches neither (record it as UNREADABLE). Clean-up re-reads every toggle and FAILs (not INFO) if any still differs from the baseline.

### B2 — Nothing is restored if the script stops early (no `trap` in r4.sh; README:63 says "safe to repeat")
The restore code (l.258-272) runs only if the script reaches the end. Concrete case: at 4c, `adb wait-for-device` (l.170) blocks forever if the phone comes back unauthorized or on a charge-only port, so Ctrl-C is the only way out. A stop at other points is worse:

- **During the 20 s at l.162:** forced deep Doze until reboot, so notifications and alarms are deferred.
- **During part 3 (l.125-149) or blur (l.213-215):** the battery stays reported unplugged, power saving stays on, or airplane mode stays on / Wi-Fi off.
- **Any time after part 2:** the shell-uid helper loops until reboot, and the probe stays installed with overlay permission and an exported ProbeActivity. Its `run=helper_toggles` extra lets any foreground app make it drive the helper.

A re-run then takes the damaged state as its baseline and "restores" to it. l.106 also deletes the old pid files, so the orphaned helper is never killed. `ping_ok` (l.104) does not check the pid, so that orphan can answer 4a/4c for the new helper.

**Fix:**
- Move l.258-272 into an idempotent `cleanup()`: `deviceidle unforce`, `cmd power set-mode <baseline>`, `battery reset`, kill the helpers, restore the toggles, uninstall the probe. Register it with `trap cleanup EXIT INT TERM HUP`.
- Before l.106, kill any pid named in an existing pid file, after checking that `/proc/<pid>/cmdline` contains HelperMain.
- In `ping_ok`, match `pid=$HUSB`.

### B3 — 4d cannot see the helper die when Wireless debugging goes off (r4.sh:191-196; README:37-39)
4d runs with USB debugging ON and the cable IN. AOSP AdbService.stopAdbd() only sets `ctl.stop adbd` when both USB adb and Wi-Fi adb are disabled. Here, turning Wireless debugging off never stops adbd, so init never kills adbd's process group. That kill is the most likely way a helper forked from adbd dies on a phone without the cable, because setsid does not leave the cgroup. The PASS therefore only proves "a listener closed". Phase 04 starts the helper over Wireless debugging on a phone alone, which is exactly the case left untested.

**Fix:** once the Wi-Fi helper answers, prompt: unplug the cable → USB debugging OFF → Wireless debugging OFF → wait 20 s → USB debugging ON → plug back in. Then run the existing `alive "$HWIFI" && ping_ok wifi`. The helper log's timestamps show whether it lived through the gap.

### B4 — 4b can PASS with no Doze at all (r4.sh:161-167)
The clean dry run (runs/20260928-161445…/part4-doze.txt) shows exactly this: it says "Unable to go deep idle; not enabled" and still got **PASS 4b**, and the INDEX row repeats "4b PASS". The PASS condition never checks that Doze was entered, and the ping runs after `unforce`.

**Fix:** PASS only if the force-idle output contains `Now forced in to deep idle mode` (or `dumpsys deviceidle get deep` = IDLE before the sleep ends); otherwise FAIL "Doze not entered".

### B5 — P5 tests the wrong window, so it will give a wrong answer (ProbeActivity.kt:336; README:14)
The question PLAN R4 (l.359-361) and phase 04 ask is whether an accessibility overlay can draw over the nav bar (phase 04: goal, "parts still to run" item 7, interview item 8); the action center is "drawn through an accessibility service". The kit tests a TYPE_APPLICATION_OVERLAY. AOSP layers TYPE_ACCESSIBILITY_OVERLAY above TYPE_NAVIGATION_BAR and the application overlay below it. So the screenshot will show the glyphs on top and the strip tap will go to the nav bar whatever the real panel can do; the dry run's p5-overlay.txt already shows the strip tap not arriving.

The phase 04 record already generalised "W10M's behaviour is not available at all" from this same window type. This run would repeat that conclusion for gesture mode and feed item 8 a false premise.

**Fix:** add a minimal AccessibilityService to r4probe that runs attempt B (full height, `setFitInsetsTypes(0)`, touch recorder) as TYPE_ACCESSIBILITY_OVERLAY. Either the owner enables it (a README step), or the script appends it to `enabled_accessibility_services` and restores the exact baseline string in `cleanup()`. Keep the application overlay as the comparison. **Interim, if B5 is deferred:** relabel the P5 INFO line "application overlay only — accessibility overlay NOT tested".

## Notes (non-blocking, most are one-liners)
- **PQ2 covers less than the plan asks.** PLAN asks whether the helper can reach mstore + GBA, and phase 06 L30 gates on "if it works / if it fails". The kit records only the prerequisites (carrier config, the shell's MODIFY_PHONE_STATE, the VVM package) and never attempts `bootstrapAuthenticationRequest` as the shell uid, so the run cannot answer "works". README:3 "answers everything R4 owes" is false for PQ2, and "no carrier login" is not in any decision record. Needs Jeremy's ruling before R4 is called done.
- **Screen timeout.** From "Go ahead" on, nothing keeps the screen awake. By the time blur/P5 run it is often ≥30 s since the last touch, so screenshots come back black and injected P5 taps are dropped while the phone is non-interactive. Pings still work, because a new intent reaches the top activity while the phone sleeps. Fix: `FLAG_KEEP_SCREEN_ON` in ProbeActivity.onCreate and `input keyevent KEYCODE_WAKEUP` before each screencap.
- **run-as is untested on this phone** (part one used the clipboard). If it fails, every app-driven check FAILs after up to 7 minutes of waiting. Fix: run `adb shell run-as $PKG id` before "Go ahead" and abort if it fails.
- **Battery saver.** The baseline (l.85) is read while charging, so a manually-on Power saving reads 0. Blur's `set-mode 0` (l.215) is a manual off, which clears `low_power_sticky` (AOSP BatterySaverStateMachine), so Power saving does not come back after unplug, and clean-up cannot see it. Fix: restore the value read right after `battery unplug`, and add `low_power_sticky` to the baseline and the end check.
- **Privacy.** The gitignore works: `git check-ignore` matches `…-SM-S938U_/`, `…-SM-S938U1_/` and `…-SM-S938B_/` to .gitignore:44 (the name ends in "_" because of the trailing newline). But README:60 "serial number: not recorded at all" is false. SUMMARY line 1 records `$ANDROID_SERIAL` (on Samsung, the device S/N), and the `adb pair` output (l.182) carries `guid=adb-<serial>-…` and the LAN IP. Fix: redact both when writing, or correct the README. The committed emulator runs hold only emulator data (AndroidWifi, 10.0.2.x).
- **Hotspot.** `start-softap R4probe wpa2 r4probe-temp-8471` puts the password in a public repo. If One UI lets the shell start an AP, it is up for about 3 s twice. The hotspot read was blank on the emulator, so neither a flip nor a stuck AP would be visible. Fix: generate the password in the helper per run.
- **Trust surface holds.** The provider check uses `Binder.getCallingUid()` (ContentProvider.Transport.call does not clear the identity; the dry run shows uid 2000). TOGGLE maps a name and an action to fixed strings with no caller-supplied command, and both calls require caller == app uid. Residual risks: any uid-2000 process (for example an app granted Shizuku) can hand the probe a fake binder, which can only fake results; the exported ProbeActivity is a confused deputy while the helper runs (bounded by B2's fix); phase 04's "uid + signature check" on the handoff is not exercised.
- **Helper hygiene.** The kill (l.260) is not re-checked, and `alive` accepts any pid (pid reuse). `sh()`'s timeout never fires, because `readText()` (HelperMain.kt:118) blocks before `waitFor`. `/data/local/tmp/r4helper-*` files are left behind; delete them in `cleanup()`.
- **Stale evidence.** `blur_pass`/`overlay_pass` ignore the result of `wait_report` (l.210, :227), so on a timeout the previous pass's section is appended under the new header; write "TIMED OUT (expected nonce N)" instead. "CHANGED yes" (l.140) also counts an adb error string in the mid read.
- **End check gaps.** `navigation_mode`, `adb_wifi_enabled` and Auto Blocker are not re-checked. Part 3's Wi-Fi flips turn off a Wireless debugging that was already on. Skipping "switch back" or "turn it OFF" leaves the setting changed. The PC stays in the phone's paired-devices list, and the README never says to turn Auto Blocker back on.
- **P5 taps.** In 3-button mode the tap at y=h-12 presses Home (harmless). If the overlay failed to show, the tap at y=h/2 lands on the home screen's centre icon. Fix: tap only once the report shows the overlay is up.
- **4c trusts that the cable was pulled.** Use `adb wait-for-disconnect` before `wait-for-device`.
- **The APK on disk is not the one dry-run tested.** It (93be25c3…) was rebuilt after the clean run (76c239e0…) with an icon-only manifest change, and r4.sh:72 installs any existing APK without rebuilding. Build every time (gradle no-ops fast).
