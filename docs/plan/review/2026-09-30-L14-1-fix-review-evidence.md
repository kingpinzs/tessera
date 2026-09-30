# L14-1 fix review — Reviewer 2 (opus, evidence integrity + adversarial on the lock gate), 2026-09-30

Reviewed: commit 3bb91090 (fix) and afb4d1ab (QA). Report as returned (written here by the orchestrating session;
subagents do not write report files).

VERDICT: PASS (no gate bypass found. The evidence supports every result the fix plan claims. Four SHOULD-FIX items.)

**Red-proof (independent)**
- Command: `./gradlew :app:testDebugUnitTest --tests 'app.tileshell.cortana.UnlockTapTest'`, run in a worktree at
  3bb91090~1 (b845a29d) with only UnlockTapTest.kt copied in. Two environment workarounds: ANDROID_HOME instead of
  local.properties, and the git-ignored sherpa aar symlinked in.
- Result: rc=1, **3 tests, 2 failed**. The failures are "the tap keeps the unlock card on screen" (the card was null)
  and "the tap says nothing new" (it said "Unlock your phone to continue."). "raises the prompt once / keeps pending"
  passed. This matches unit-red.out.
- The fix's ActionLayer.kt in the same worktree (forced rerun): rc=0, 3/0.
- Mutations, each run for real, each rc=1 with 1 of 3 failing: blank caption (caught by the card-equality test);
  `requestUnlock()` called twice (caught by the count==1 test); `pending = null` (caught by the pending-equality test).
- The kept-spoken-line mutation is exactly the pre-fix source, already red above.
- No other test touches `Pending.Locked`, `onUnlocked` or `onUnlockCancelled`, so nothing pins the defect as correct.
- Worktree removed.

**Findings**

1. **NOTE — No gate bypass: the gate is re-checked when the request runs, not trusted from UnlockBridge.**
   `CortanaModel.onUnlocked` calls `actions.run(request)`, and `ActionLayer.kt:99` re-checks `LockGate.locked()`
   (isKeyguardLocked) first. `deliver(true)` comes from only two places: `onDismissSucceeded`, or the "keyguard already
   gone" branch, which requires `!isKeyguardLocked`. A secure keyguard reports locked even when occluded, and a
   swipe-only or trusted-state keyguard has no credential anyway. A stale listener or a different card: the listener
   closes over its own session's model, and `stop()` on hide nulls pending. A new card can only receive `true` after a
   real unlock. The unlock activity is `exported=false`. Removing showWhenLocked shrinks its reach; it did not protect
   the gate. The tested APK's manifest (checked with aapt2) has no showWhenLocked or turnScreenOn on
   CortanaUnlockActivity.
2. **NOTE — `onUnlocked` sets `locked=false` without re-checking the keyguard** (CortanaModel.kt:421, 425; predates this
   fix). Failure scenario: `onDismissSucceeded` fires while isKeyguardLocked is still true during the going-away
   animation. `run()` shows the card again and says the line again, but the ≡ pane (Settings/Reminders) is open until
   the next show. It only happens after the PIN is entered, so the exposure is brief. Fix: `locked = LockGate.locked(context)`.
3. **SHOULD-FIX — "Tess has stepped aside" cannot be shown to fail.** e8.sh and l14_1_edges.sh assert only that
   `cortana_session` is absent from a uiautomator dump, which shows only the active window. No dump anywhere in qa/ holds
   both Tess and the bouncer; defect 2 ("Tess sits above the PIN pad") is reasoned from window types, not observed.
   Failure scenario: Tess stays visible over the PIN pad while the bouncer holds focus, and the check still passes. The
   harness already has `session_window` (dumpsys window isVisible) in p14.sh:44. Mitigating evidence: the ring line
   "stepping aside for the unlock prompt", and the typed PIN reached the bouncer.
4. **SHOULD-FIX — Part of the fix gate was promised but never run.** The gate lists "phase 03 E10's unlock clause … no
   earlier gated request ran". Edge case B speaks one gated request ("Open Clock"), so there is no earlier card for it to
   prove did not run; E10's own text says "open DeskClock", with a chain of earlier cards before it. Separately, case C
   uses KEYCODE_SLEEP where the gate said "the screen timing out", and it records "pending dropped" instead of asserting it.
5. **SHOULD-FIX — Some edge-driver assertions pass if the ring read fails.** `assert_eq "" "$(reply_since "$MA")"` (A)
   makes its own separate `diag` read; an empty read passes it. B's three `assert_absent` checks on B-03-slice have no
   positive check on that same slice. The saved evidence does back them (A-03-slice.txt is 13 lines with no
   `[speech] speak` line; B-03-slice.txt is 4 real lines). Fix: take the reply from `$s`, and assert the slice is not empty.
6. **SHOULD-FIX — Nothing below the device tests guards the manifest or the step-aside wiring.** Putting
   `showWhenLocked` back, or re-enabling the UI after `onUnlocked` instead of before, passes every unit test; only E8 and
   the edge driver would catch either.
7. **NOTE — The device evidence matches the plan's claims.** E8.txt: "the Unlock button shows the PIN pad = yes",
   "unlocked = isKeyguardShowing=false", "reply_since MARK2 is the doors line = I'm afraid I can't do that, Dave.",
   "E8: 25 passed, 0 failed"; 03-unlock-slice.txt in order: dismissed → back → running → speak → done → hidden →
   `[podbay] opened`. Edges: "A: the unlock was cancelled", "A: the request stays pending", "A: the session was not
   hidden", "53 passed, 0 failed, 3 recorded". Both sides of the rulings are proven on the device (unlocked → runs: E8,
   A's second unlock, B; cancelled → same card, pending, silent: A). The e8.sh change makes the row stronger than its
   FINAL text.
8. **NOTE — The runs used the fix build, from before the commit.** "apk match yes (19ccac9f359f79c5)" compares device
   md5 with local md5 (hence it differs from the sha256 on the "built" line). The local app-debug.apk had md5 19ccac9f,
   built 12:44:10, 12 minutes before the 3bb91090 commit; its dex contains "stepping aside for the unlock prompt".
   Driver blobs match HEAD (e8 433926a5, edges 1d06e396); the lib.sh blob b4535d23 is committed.
9. **NOTE — The case-insensitive caption in run 2 is a legitimate correction, not a goalpost move.** E10's text pins no
   caption, and `restate` builds it from the recognised name ("Open ${name}"). The caption the spec does pin ("Open the
   pod bay") is still compared exactly in E8 and in A.
10. **NOTE — The edge driver never turns the media volume up.** The locked replies were recorded at about −117 dBFS in A,
    B and C, against −29.66 in E8; "nothing said" rests only on the ring's speak lines.
11. **NOTE — Paths not covered by any device run:** tapping Unlock when the keyguard is already gone; a swipe-only
    keyguard on the fix build; the helper never delivering a result (Tess stays hidden with no timeout; the next show
    recovers her).
