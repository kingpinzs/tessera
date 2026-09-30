# L14-1 fix review — Reviewer 1 (opus, design / correctness), 2026-09-30

Reviewed: commit 3bb91090 against Jeremy's Q1 (a), Q2 (a) and H12, read-only. Report as returned (written here by the
orchestrating session; subagents do not write report files).

VERDICT: PASS

The diff does what the rulings ask and nothing else. The manifest and activity lose showWhenLocked and turnScreenOn.
`requestUnlock` uses L13-1's step-aside and brings Tess back before the result. `confirm(Locked)` returns the same card
with an empty line and awaiting=null. Nothing is out of scope, and nothing the rulings require is missing.

The tap's Outcome flows cleanly through `CortanaModel.reply` (CortanaModel.kt:435-465). The spoken line is blank, so
nothing is spoken and utteranceId is null. close=false and awaiting=null mean no listen-after and no close. Persona ends
IDLE_AFTER_SPEAKING and the route is Result. `requestUnlock` runs inside `confirm`, before `reply`. The result can't
arrive first, because it comes from the activity's `onCreate`, which is always posted.

On Android 16 the premise holds with this build. spike1-run1/logcat-all.txt:55 shows "Activity requesting to dismiss
Keyguard" accepted even with isVisibleRequested=false and isSleeping=true, which fits AOSP's `visibleIgnoringKeyguard`
check. Line 679 shows `notifyDismissSucceeded(1)`.

**BLOCKING:** none.

**SHOULD-FIX**

1. **A speech result that arrives after the tap can replace the pending request while Tess is stepped aside.**
   - Evidence: `onSteppedAside` (CortanaModel.kt:391) calls `SpeechClient.stopListening()`. That calls `asr.interrupt()`
     (SpeechService.kt:429), and SherpaAsr.kt:360-430 still sends `onFinal(...)` "via stopped" with whatever it decoded.
     SpeechClient.kt:100 passes it on unfiltered. The event collector keeps running during the step-aside, so
     `onFinal` → `handle()` runs.
   - How it's reached: the card stays on screen and tappable while the mic is open (CortanaSessionRoot.kt:157 draws the
     card for any Result route; the listening box only replaces the text bar).
   - Failure: locked, the Unlock card is up, the user taps the mic, starts speaking (say "unlock…"), then taps Unlock.
     The PIN pad comes up and the cut-off text arrives as a final. `handle` runs "a new request replaced the pending
     Locked" and the not-understood reply may speak over the PIN pad. If that reply has awaiting set, the mic opens
     behind the PIN pad, which breaks the plan's own step-aside decision. After the PIN, `onUnlocked` logs "unlocked
     with nothing pending" and the gated request never runs. That is the ledger symptom by a narrow route.
   - The mechanism comes from L13-1, but this fix now sends the unlock through it.
   - Fix: while stepped aside, drop or hold speech results (a flag or generation checked in `onFinal`).

**NOTE**

1. **setUiEnabled after a hide or destroy is harmless (about 85% sure).** AOSP `setUiEnabled` only shows or hides the
   window when `mWindowVisible` is true. On a hidden session it just sets the flag, and `doShow` calls `onShow` before its
   own show step, so onShow's `setUiEnabled(true)` (CortanaSession.kt:220) brings Tess back correctly next time. AOSP
   hides a session before destroying it (`cancelLocked` → `hideLocked` → `destroy`, about 75% sure), so `mWindowVisible`
   is false there too. Edge part C confirms the order: cancel at .079, then "session hidden" at .083.
2. **Re-opening Tess while she is stepped aside resets the card.** Pressing the assist button (long-press power) over the
   PIN pad calls `onShow` on the still-shown session: `setUiEnabled(true)` puts Tess over the PIN pad and `model.open()`
   wipes the pending request. The old dismiss and the process-wide listener are still live. If the user then unlocks by
   fingerprint while Tess shows a new gated card, the old callback runs that new request without an Unlock tap. This is
   narrow, and arguably fits H12's "a new request replaces it".
3. **No timeout if the result never comes.** If the activity start is silently blocked by background-launch rules, or no
   dismiss callback ever arrives (possible on some OEM builds), Tess stays shown but invisible, and the user sees a plain
   lock screen. The start is allowed today as `BAL_ALLOW_NON_APP_VISIBLE_WINDOW` (spike1-run1/logcat-all.txt:38). That
   works only because `setUiEnabled(false)` hasn't reached the window manager yet when `startActivity` is called. It's
   reliable (the hide lands on the next frame) but implicit. Calling `UnlockBridge.start` before `setUiEnabled(false)`
   would make the ordering explicit.
4. **Other phone makers (medium-low confidence).** Calling `requestDismissKeyguard` from an activity that doesn't show
   over the lock screen relies on the AOSP `visibleIgnoringKeyguard` check. SystemUI forks may differ, and
   `onDismissError` (for example when the keyguard vanishes between the `isKeyguardLocked` check and SystemUI's
   handling) is treated as a cancel. Tess comes back silent under Q2 and the card still says "Unlock to continue" on an
   unlocked phone. It recovers on the next tap through the "keyguard already gone" branch. Suggestion: in
   `onDismissError`, re-check `isKeyguardLocked` and deliver `true` if it's gone.
5. **Race on the unlocked path (unchanged by this commit).** `onUnlocked` → `actions.run` → `LockGate.locked` checks
   `isKeyguardLocked` again at the moment the dismiss succeeds. Because SystemUI tells the window manager and the app over
   separate channels, a build that reports success before it updates the keyguard state would re-show the Unlock card
   instead of running the request. The AVD passed every time (E8 25/0, edges 53/0).
6. **Removing turnScreenOn is safe.** The screen is always on at a tap. The screen going off on the PIN pad cancels the
   dismiss and hides the session (edge C), which drops the request under H12 and runs nothing. That matches the plan's
   expected result.
7. **Double-tap is harmless.** A second tap queued before the window hides re-enters `requestUnlock`.
   `FLAG_ACTIVITY_NEW_TASK` plus the same taskAffinity brings the live task to the front with no second `onCreate`, and
   the listener is replaced by the same session's own lambda.
8. **Test coverage.** UnlockTapTest covers only Q2 in ActionLayer. The step-aside, come-back and listener lifecycle are
   covered only by the device runs.

Files: app/src/main/kotlin/app/tileshell/cortana/CortanaSession.kt, CortanaModel.kt, CortanaSessionService.kt,
CortanaUnlockActivity.kt, speech/SherpaAsr.kt, ui/CortanaSessionRoot.kt, action/ActionLayer.kt.
