# R4 phone-only run — adversarial safety review (2026-09-28)

**Scope.** I read 87badd42, dd2c21cf, 3d36e3b4 and fcb12dbc, which landed during the review. Files: Runner, AdbSelf, Discovery, PairReceiver, HelperMain, HelperProvider, Toggles, ProbeActivity, ProbeA11yService and RunToken; the manifest; r4probe/build.gradle.kts; settings.gradle.kts; the run sheet; both emulator runs (2029 and 2036-token); and review rounds r1–r3 against r4.sh. I did not build, use adb or write to git. Line numbers are at fcb12dbc.

## Verdict: NOT READY. Three blocking findings; each fix is a few lines.

**Found and already fixed.** At dd2c21cf, ProbeActivity obeyed `run` extras from any app. Any installed app could send `run=start --ez auto true` and start an unattended run that auto-answers "Go ahead" and wipes the last report, or send `run=helper_toggles` to flip every toggle through the helper with no clean-up. fcb12dbc gates every extra on RunToken: 128 bits in private files, carried only in the app's own 4a command, which only shell and root can see. The 2036-token run shows the gated restart works. The gate holds.

## Blocking

### B1 — A new run throws away an unfinished run's baseline and leaves its helper running (Runner.kt:110-117, :258-262; ProbeActivity.kt:78, :105-107)
`start()` deletes run-state.properties, which is the only copy of the baseline, and takes a fresh one. The "Run R4 on this phone" button is always on screen, including above the "Carry on / Restore now" offer. The PC script's defence against exactly this case (r4.sh:180-185: unforce, battery reset, kill leftover helpers before the baseline; round 1 B2) was not ported.

**Scenario.**
- The probe dies in 3a, after location and auto_time were switched off: killed in the background (see B2) or a crash.
- Jeremy reopens it and follows run-sheet step 1: he taps Run R4.
- The damaged states become the baseline, so "Go ahead" lists location off and auto_time off. Clean-up later "restores" them off and prints **PASS clean-up: every change undone**.
- A kill during P5 or blur is worse: the probe's accessibility service, or Power saving ON, becomes the owner's "own" setting.
- The killed run's helper keeps running. It hands its binder to the new process first, so the wait at :262 ends before the new helper writes its pid. 2a and 2b then FAIL and the run stops, and endHelper ends only the helper whose binder it holds.

**Fix.**
- In `start()`: if `unfinishedStage()` is not null, run `stageCleanup(); endHelper()` on the saved baseline first, or refuse and show Restore now.
- Before the baseline (adb works at that point): `dumpsys deviceidle unforce; dumpsys battery reset`, and kill every pid that `ps -A -o PID,ARGS` shows running `r4probe.HelperMain --daemon`.

### B2 — P5 taps the screen without an overlay up; in 3-button mode that is Home, then the owner's home screen (Runner.kt:459-461)
The wait ends on "WINDOW UP", on "OVERLAY DONE" (which is also the failure text, e.g. "the accessibility service is not connected — nothing was measured"), or after 20 s. The two `input tap`s are then sent regardless. r4.sh:371 tapped only after WINDOW UP; round 1 flagged this and round 2 confirmed that fix.

**Scenario.** The accessibility service does not connect: One UI 8 may block it for a sideloaded APK, which is untested. P5 always has one pass in 3-button mode.
1. The tap at (w/2, h-12) presses Home.
2. 700 ms later the tap at (w/2, h/2) lands on whatever is in the centre of Jeremy's home screen: an app, a folder, or a widget or shortcut action.

Even when the overlay is up, the app pass's strip tap in 3-button mode is Home. The committed emulator run shows it: the nav-0 app pass received only the y=1170 touch. So every real run puts the probe in the background with its next prompt hidden, which is the unplanned-kill and freeze exposure behind B1 and note 1.

**Fix.**
- Send the taps only when `overlayText(kind)` contains "WINDOW UP".
- After each pass, run `sh("am start -n app.tessera.r4probe/.ProbeActivity")`. It needs no extras, and the singleTop activity absorbs it.

### B3 — 4d can PASS with no adbd pid on record (Runner.kt:538, :548-551)
`before` comes from `sh1`, which returns "adb not reachable" or "SHELL FAILED: …" when adb is already lost. Any non-empty pid the helper reads afterwards then differs from it, and the step PASSes "adbd gone (new pid)", even when the owner left USB debugging on and adbd never restarted. This brings back round 2's R2-2 in a narrower case: r4.sh:337 gave INFO when ADBD1 was empty.

**Scenario.** A Wi-Fi flip's adb never comes back (for example, the network prompt in note 6 is left unanswered). Clean-up FAILs, and then 4d reports a PASS on phase 04's key question.

**Fix.** Unless `before` matches `^\d+( \d+)*$`, record INFO "4d NOT tested: adbd pid before unknown".

## Notes (non-blocking; 1 and 2 are worth doing before the run)
1. **4a and 4b, screen off.** After the planned force-stop, the only window holding FLAG_KEEP_SCREEN_ON is gone. The last touch was minutes earlier, so the screen most likely goes off, and the resumed probe comes up behind the lock screen (ProbeActivity.kt:64; Runner.kt:359).
   - In that state, 4b's forced deep Doze and its undo (:377-388) depend on the probe's own adb session. That session may be blocked in Doze for an app that is not in front: AOSP's uid firewall exempts loopback only from the background chain (not verified on One UI). One UI may also freeze the app.
   - Kadb calls have no timeout, so the phone can sit in forced Doze, silently, until Jeremy unlocks it.
   - This path has never run: the emulator said "Unable to go deep idle".
   - Fix: `setShowWhenLocked(true); setTurnScreenOn(true)` in onCreate; `input keyevent KEYCODE_WAKEUP` in the 4a command; and a detached `setsid sh -c 'sleep 25; dumpsys deviceidle unforce; dumpsys battery reset' &` before force-idle.
   - README step 4: "keep R4 probe on screen until it asks".
2. **Clean-up without adb gives up although the helper could fix it (Runner.kt:510-513).**
   - It never uses the connected helper to set Wi-Fi or airplane back, which is exactly what brings adb back. endHelper then ends the helper.
   - Its text says "tap Restore now", but the stage is already "done" (:146, :164), so that offer never appears (ProbeActivity.kt:105).
   - After bringBackAdb fails (:351), the run keeps going for about 10 minutes with every step failing.
   - The saved dead port is skipped unconditionally (:92, :339), so an adbd that keeps its port through a flip is never found again.
   - Fix: when adb is unreachable, put Wi-Fi and airplane back through the helper and call bringBackAdb before giving up; keep the stage at "cleanup" when it FAILs; say "reboot (ends forced Doze, the battery report and the helper)".
3. **The helper's end is never verified (Runner.kt:557-562).** "binder alive after exit: true" appears in both emulator runs, where the harness counted 0 helpers. A BinderProxy stays "alive" until a transaction fails, because nothing calls linkToDeath. For the same reason `HelperLink.connected()` cannot see a dead helper. Fix: after EXIT, ping and expect "call failed"; otherwise FAIL "reboot the phone". /data/local/tmp/r4helper-phone.log is left behind.
4. **A failed baseline read becomes the baseline (:234).**
   - `sh1` turns an adb failure into the value "adb not reachable" or "SHELL FAILED: …".
   - For enabled_accessibility_services, P5 writes that string (:448-449) and "restores" it (:481). The comparison then says "as they were" while the owner's own services are gone.
   - This is round 2 note 6, with a worse failure value. Fix: stop before "Go ahead" if any baseline read's exit code is not 0.
5. **No timeout on Kadb `shell` (AdbSelf.kt:58-65).** The PC script wrapped every adb call in `timeout`, so a hung command could not stall the run mid-change.
6. **The run sheet understates "Always allow".** Without "Always allow on this network" (which is per-BSSID), every Wi-Fi or airplane flip, and clean-up, needs Jeremy to tap Allow within 3 minutes (:343), or adb is lost for the rest of the run. Make it a must in run-sheet step 2.
7. **Trust residuals after fcb12dbc.**
   - **PairTestReceiver ships in the release.** DUMP is signature|privileged|development, so an app granted it with `pm grant` can make the probe pair and connect to a fake adbd of its choice. It can then feed the run false reads that the helper acts on. Move the receiver to a debug-only manifest.
   - **The pairing PendingIntent is mutable** (RemoteInput needs it). A notification listener can add `code` or `connect_port`, but pairing still needs the real code.
   - **The rest holds.** The provider and binder uid checks hold, and ADBD_PID, WD_ON and EXIT are fixed commands for the app uid only. No shell command is built from outside input.
   - **Dependencies.** JitPack is scoped to one group but nothing is checksum-pinned, for code that holds a shell session. Add verification-metadata entries for kadb and spake2-java.
8. **Left after a run.** Harmless once uninstalled: the Paired-devices entry (useless once the key goes with the app; allowBackup=false) and the SYSTEM_ALERT_WINDOW appop (:395). Wireless debugging stays ON if 4d is skipped (:542): nothing says to turn it off, and the PC version's end-of-run check of adb_wifi_enabled is gone, as is its "turn Auto Blocker back on" reminder. Run sheet step 1's "install over it" fails, because each CI debug build is signed with a new random key (apk.yml:111-116): say "uninstall the old probe first".
9. **Privacy.** The report holds the phone's LAN IP and adb ports ("pairing: paired with …", :202; the run sheet does not list these). It holds no serial, IMEI or number: the Wi-Fi read is its first line only, and PQ2 records only carrier-config keys and counts. The serial appears only in `run=discover` output (mDNS names), which is now token-gated.
10. **Evidence gaps.** 4a does not log the old and new app pids. Clean-up's PASS does not cover the Doze or battery reset (unlogged; round 2 note 5), the helper's end, or the navigation type (a NOTE only). CHANGED counts an unknown middle read as a change (round 3 note 5). A Stop during pairing prints a false "FAIL clean-up" (:510) and leaves the ongoing pairing notification (:192).

**Held on re-derivation.** restoreSticky is round 3's R3-1 fix exactly (:429-439), in blur and in clean-up. The accessibility-restore FAIL now reaches the summary (round 3 note 2). Within the run, Wi-Fi and airplane are flipped only by the helper, and WD_ON writes only when Wireless debugging is off. PASS 1/2a/2b/4a/4b each need the recorded helper pid; 4b also needs "Now forced in to deep idle mode".
