# Phase 13 gate — round 1 triage (2026-09-26)

Reviewers: opus A (design and correctness) — `2026-09-26-phase13-gate-a.md`; opus B (testability and evidence,
adversarial) — `2026-09-26-phase13-gate-b.md`. Both Fable reviewers stopped at the Fable limit (HTTP 429) before writing;
codex is out until 2026-10-01 (CLI usage limit; MCP not loaded). Opus under Jeremy's ruling (b) of 2026-09-25.

Both: VERDICT FAIL, 2 BLOCKING each, no overlap. Every acceptance row except E8's corroboration clause and EDGE_RAPID was
judged PASS by B; A judged every row PASS and blocked on a product defect found by reading code, and on missing ADD lines.

## BLOCKING

| # | Finding | Kind | Resolution | Evidence |
|---|---|---|---|---|
| A-B1 | A static-layer build cancelled by acrylic turning off mid-build is caught by `mapCatching` and recorded as `Failed`, with a false `static backdrop failed` line; when the cancel lands after the off effect's `drop()`, the next "on" for the same key never builds and the app list draws the fallback under `acrylic=on` | product | FIXED a7ef430b: `StaticBackdrop.build(key, make)` lets `CancellationException` through; only a real failure is recorded | `StaticBackdropTest` 3/3, red on the old handling (the cancelled build recorded) and green now; device probe `qa/phase-13/B1_PROBE-before-db078d6a` logs `static backdrop failed … LeftCompositionCancellationException` for a switch tapped off / on / off ~65 ms apart; `B1_PROBE-after-509f6e49-{1,2,3}` do not, and rebuild after the final on. Re-runs on 509f6e49: E1, E2, E4, E10, E12, E13 |
| A-B2 | Build task 7's INDEX Change Log lines for phase 13's ADDs to phases 01 / 02 / 03 / 10 / 11 were never written | doc | WRITTEN: INDEX Change Log "PHASE 13 ADDS TO FINAL PARTS" | the commits' file lists (854e12a3, bce25b14, 365bd248) |
| B-B1 | EDGE_RAPID asserted time and PSS only; no captured line showed any hold opened a menu, and the loop's ring was lost to `clear_background`'s force-stop | driver | FIXED: MARK before the loop, slice saved before the force-stop, `10` applist_menu show lines asserted | EDGE_RAPID re-run on 509f6e49 |
| B-B2 | E8's screenrecord corroboration unmet: all three animated captures broke phase 05's 18.2 ms rule and none was retaken; the Change Log said the [motion] lines "stand alone, as C-5 has it", which is backwards | driver + doc | FIXED: E8 retakes each motion up to 10 times under phase 05's rule, reading the motion inside the surface's own bounds (`motion_frames.py` region) and checking the window against that attempt's `[motion]` settle; the band (a jump) must appear within two source frames. The Change Log line is corrected in place and marked | E8 re-run on 509f6e49 |

## NON-BLOCKING — acted on

- B-N4: E10's Photos precondition read `photos=` (true of any count) — now asserts one more photo than before the push;
  the Change Log's run labels corrected (run2 = 12 675 KB, run3 = 4412 KB).
- B-N5: E9's ring was lost to the force-stop — `ring_save` before it, and the workload's lines recorded.
- B-N6: EDGE_NO_IMAGE's MARK moved before `clear_background`.
- B-N11 (half): EDGE_LIGHT now asserts `tint=(255,255,255)` (Palette.lightBackground; T = F for the app list).
- B-N2: the Change Log's "the fade 317" corrected to "black for 283-300 ms read 317"; that two of E15's six failing
  clauses are on the ≡ pane is recorded for Jeremy.
- B-N14: the unit results are kept under `qa/phase-13/unit/`.
- B-N15: README's "band 6 ms" (the warm-up) corrected with the asserted runs.
- A-N7: row 03 now says E15 ran (20/7, 21/6), why its setup never reaches rows, and the `grep -c` fault in `e15.sh`.
- A-N10 / B-N10: T13-18's result recorded (static 128.8 px vs live 133.3 px).
- A-N3, A-N4, A-N5, A-N6, A-N9: recorded in the Change Log's "NOTES FROM GATE ROUND 1" line for phases 04 / 14.

## NON-BLOCKING — recorded, not acted on (with the reason)

- A-N1: `BackgroundDecoder` has no in-flight dedup (2–3 decodes at once on a picture change). A fix touches the decode
  every Start-background row drives (phase 01's rows included); left for Jeremy's call.
- A-N2: the static layer is a software copy, so a real GPU also holds its texture; P2 on the phone decides.
- A-N8: the lights ignore consumption; no surface in this phase scrolls under a held item.
- B-N1: E5's live sub-step also meets the doc's own ≥ 2 clause on the kept captures (2.0); the 2026-09-25 re-cut stands
  as recorded.
- B-N3: E15's "Delete in one frame … `dumpsys alarm`" clauses are not in `e15.sh` — phase 03's gate.
- B-N7, B-N8: E4 (3) and E6's discriminating power is carried by E4 (2) and E5 (e2), as B notes.
- B-N9: E2's off pass writes the pref with the shell stopped; E1 proves the live toggle's fallback on the same strip.
- B-N11 (other half): `screencap` does not show display inversion (the colour transform is applied after capture), so H1
  judges inversion on the phone by eye.
- B-N12: E7 (c)'s off point is off-screen; the outcome (A = U) is proven, the "left the item" path specifically is not.
- B-N13: EDGE_HUGE's B is 212 MB (phase 01's decode of an 8000² picture plus a Photos face) — for P2 and phase 01.
- B-N16: E2 and EDGE_EDIT_MODE read Start without phase 02's baseline restore; each needs only a gap or a present tile.

## Round 2

The re-run rows' evidence goes back to the same two reviewers (fresh contexts) with this triage; round 2 of 3.
