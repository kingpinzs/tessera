# L14-1 fix reviews — triage (2026-09-30)

Reviewers: opus (design / correctness) + opus (evidence integrity, adversarial on the lock gate). Codex out (CLI usage
limit until 2026-10-03 16:48); never Fable (Jeremy, 2026-09-30). Both reviewed commit 3bb91090 + QA commit afb4d1ab.
Reports (verbatim): 2026-09-30-L14-1-fix-review-design.md, 2026-09-30-L14-1-fix-review-evidence.md.

**Verdicts: design PASS · evidence PASS. BLOCKING: none from either.**

## SHOULD-FIX — all accepted

| # | from | finding | verified by the agent | action |
|---|---|---|---|---|
| S1 | design | a pass stopped by the step-aside still sends its final ("via stopped", SherpaAsr.kt ~420-430) and `onFinal` handles it: a request cut off by the Unlock tap replaces the pending one behind the PIN pad | yes — read SherpaAsr's drain path and `CortanaModel.onFinal`: no step-aside check | product: `dropStoppedFinal` names the stopped pass; its final is dropped whenever it lands (`CortanaModel.onSteppedAside` / `onFinal`, cleared by `open` / `stop`). Device case D added to l14_1_edges.sh (tap the mic, inject a 6.7-s utterance, tap Unlock 3 s in) |
| S2 | evidence | "Tess has stepped aside" reads only the focused window's dump | yes | drivers: `session_window` (dumpsys window isVisible) asserted after every Unlock tap (e8.sh, l14_1_edges.sh A-D) |
| S3 | evidence | the promised "phase 03 E10's unlock clause … none of the earlier gated requests ran" never ran (B has no earlier card); C used KEYCODE_SLEEP and recorded the drop | yes | phase 03 E10 run whole, unchanged, its row dir moved aside (phase 12 E9's pattern); C re-cut to a real 15-s screen timeout with the drop asserted |
| S4 | evidence | `reply_since` / absence checks could pass on an empty ring read | yes | drivers: replies taken from the saved slice's own speak lines; each slice asserted to hold the tap's "stepping aside" line first |
| S5 | evidence | nothing below the device rows guards the manifest | yes | unit: UnlockActivityManifestTest (red on 3bb91090~1's manifest: 2 tests, 1 failed — `qa/phase-14/L14-1/manifest-test-red.out`) |

## NOTEs

- Accepted (cheap, same function): design N3 — `UnlockBridge.start` before the step-aside, so the activity start is made
  while Tess's window is visible (the background-launch allowance) and the ordering is explicit.
- Accepted (evidence N10): the edge driver turns the media volume up for its spoken replies and restores it.
- Recorded, not built (outside the rulings or predating this fix; for phase 03's end-of-build pass):
  - design N4 `onDismissError` could re-check the keyguard and deliver true if it is gone;
  - evidence N2 `onUnlocked` sets `locked = false` without re-checking;
  - design N2 re-opening Tess over the PIN pad resets the card while the old dismiss is live (fits H12's "a new request
    replaces it");
  - design N3's "no timeout if no result ever arrives" (the next show recovers her);
  - evidence N11 paths no device run covers: Unlock with the keyguard already gone, a swipe-only keyguard, no result.
- Confirmations: no gate bypass (the gate is re-checked in `ActionLayer.run` after any unlock; the page is exported=false);
  independent red-proof of UnlockTapTest (rc 1, 3 tests / 2 failed on 3bb91090~1) and three real mutations each caught;
  the device evidence matches the fix plan's claims; the case-insensitive caption was a legitimate correction.

## Environment event during the triage runs

2026-09-30 13:09: the AVD (tileshell_fhd, emulator 37.1.11, pid 3488618) crashed during case D's red run (breakpad
minidump at 13:09; kept outside the public repo in ~/projects/metro-launcher-local/L14-1/, as it holds the host
environment). The dump's strings point at the renderer ("Failed to find ColorBuffer", GPU feature strings), not audio;
it was not symbolized (the SDK's crash tool uploads). The run's log: qa/phase-14/L14-1-EDGES-D-red-19ccac9f/ (13/6 —
every failure after the tap is "device not found"). The AVD needs relaunching (Jeremy's OK, as on 2026-09-30 12:27),
and on relaunch the PIN the run set is still on the device (its trap could not clear it).
