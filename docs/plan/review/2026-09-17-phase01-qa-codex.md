# Phase 01 QA gate — Reviewer 2 (codex-mcp), 2026-09-17

Verdict: the evidence does not prove phase acceptance yet. Rows judged PASS mean the recorded test supports the row,
not that it was repeated against the final APK.

## Most serious
1. Assigned-app removal contradicts the Decisions: uninstalling the explicitly assigned K-9 assigned FairEmail, and
   disabling Aves assigned the image's Gallery; the Decisions and the Edge cases both say the slot returns to
   unassigned. Old explicit assignments also come back after a reinstall (EDGE/edge_slots.txt).
2. E5 measures composition, not the visible preview: the render diagnostic logs when the content is composed
   (TileView SideEffect), while the first flip to the preview face started 3.32 s after the notification's post time
   (E05/diagnostics.txt). The 220 ms figure is the wrong measurement for "the preview shows".
3. Two-column Start loses tiles: E13 records Mail clipped to 3 px with six tiles outside the narrower grid. Deferring
   the reflow to phase 02 explains it, but a settings PASS does not establish a usable two-column Start.
4. EDGE.txt's blanket "every emulator-testable case" claim is not supported: security, permission, interaction and
   navigation gaps remain (below), and the adversarial review of the Live Tile API is not in the bundle.

## Row verdicts
FAIL: E5. NOT PROVEN: E3, E10, E12, E19. PASS: E1, E2, E4/E4b, E6, E7, E8, E9/E9b (data; H9 open), E11, E13 (narrowly),
E14, E15, E16, E17, E18, E20 (provenance caveat: the motion recording predates the fix), E21.

## Methods that cannot support their claims
- E10: the exit analyser treats off-screen regions as dark and reports a -100 ms dock fade; its "black" detector
  measures accent disappearance, not black pixels; RV11's 55-fps rule is not qualified against a variable-rate
  capture whose overall average is ~17 fps (16.7 ms median only during motion).
- E19: Start's dump shows both inset sources hidden, but the other screens log requested visibility, not actual
  source visibility, and the 5-second re-hide has no precise timestamps.
- E3: the comparison covers 13 tile rectangles, not every owned window, and "created count = 1" does not show that
  meaningful UI state survived.
- E12: deleting the caption store substitutes for the required "fixtures installed before onboarding" scenario.
- API: the foreign-authority test returns "no provider", so it never exercises an existing foreign provider; no
  different-signer reinstall, no grant-revoked-before-copy, no oversized-XML device test; the kill-switch row still
  reads Off after being turned back on (a dump-lag artefact, but as recorded it contradicts the next reading).
- Photos: partial access selected zero photos, so neither subset cycling nor exclusion of unselected photos is shown.
- Scale: 288 of 331 rows, 51 of 60 Quartz apps and 256 captions were observed, not all of them; the ten taps do not
  prove any tap landed during a flip; the rapid-tap log's "calendar tasks: 0" contradicts its own conclusion.
- Weather: airplane mode does not exercise a provider error or timeout; changing the app's locale is not a device
  region change. Home-on-Start scrolling (X20) has no before/after reading.
- Provenance: the bundle spans several code fixes without one final APK that passed everything; E5's unsnooze
  restore explicitly failed; the phone P rows and Jeremy's H sign-offs are outstanding.

X25 is a properly recorded approximation (H34), not a Decisions violation.
