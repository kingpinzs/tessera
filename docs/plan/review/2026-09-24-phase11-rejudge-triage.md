# Phase 11 re-judge (pass 5) — triage

Reports: review/2026-09-24-phase11-rejudge-design.md (Opus, R1: 0 BLOCKING · 1 SHOULD-FIX · 5 NOTE) and
review/2026-09-24-phase11-rejudge-evidence.md (Opus, R2: 2 BLOCKING · 6 SHOULD-FIX · 7 NOTE; this triage first said 8 and listed an R2-16 the report does not have — corrected by the round-2 re-judge, EV-14). Roster: Jeremy, 2026-09-24,
"use opus for the re-judge" (Fable at its usage limit; codex out). Every finding below was checked against the code, the
doc or the evidence before it was accepted.

## Fix (this round)

| id | sev | decision | where |
|---|---|---|---|
| R1-1 | SHOULD-FIX | accept (confirmed: `MusicFeed.forget` clears only its own memory, so a main-thread publish between the engine's forget and it re-creates the gone package's MUSIC slot, and nothing clears it). `forget` publishes MUSIC null for the package; device: (d) uninstalls while PLAYING | MusicFeed.kt; l11_1.sh (d) |
| R1-2 | NOTE | accept with R1-1 (once `forget` nulls the slot, a duplicate listener's late copy could clear a new track): register the forget listener once | MusicFeed.kt |
| R1-6 | NOTE | accept (a KDoc ca73663 displaced) | LiveTileEngine.kt |
| R1-5 / R2-2 | BLOCKING (R2) | accept: E10 holds the art of a tile SHOWING the strip (the shell's player playing), above the strip, point asserted outside every control; edit mode + Music's four | e10.sh |
| R2-1 | BLOCKING | accept: an EDGE row maps every Edge Cases bullet (doc :627-658) to an existing row / JVM test, a new sub-step, or a recorded NEEDS-HUMAN / phone row | edge.sh, EDGE/EDGE.txt |
| R2-3 | SHOULD-FIX | accept: (d) keeps one shell process from the uninstall to the read (pid asserted), re-pins without a restore, asserts the positive label | l11_1.sh (d) |
| R2-4 | SHOULD-FIX | accept: single-word notification extras, and a positive control where the notification shows | l11_1.sh (a) |
| R2-5 | SHOULD-FIX | accept: a device step — a secondary tile's API content survives a notification-listener rescan (pre-rescan sample as the control) | l11_1.sh |
| R2-6 | SHOULD-FIX | accept: run_all keeps the unit results before any row; L11-1 asserts TileSourcePrecedenceTest's 12 and three named cases | run_all.sh, l11_1.sh |
| R2-7 | SHOULD-FIX | accept: the E6 re-cut's recorded reason is corrected to what the files show (late input + the recording ending mid-motion; no unrecorded baseline existed); a recording whose window has fewer than 19 gaps is retaken | INDEX Change Log, e6.sh, e6_frames.py |
| R2-8 | SHOULD-FIX | accept: the doc's QA evidence section written, through an INDEX Change Log line | phase-11 doc |
| R2-9 | NOTE | accept: the close's settle assertion cannot fail (settle is logged as alpha0 by construction: the satellites are gone at alpha 0) — dropped, recorded | e6.sh |
| R2-10 | NOTE | (a) record: the burst's t0 is the frame after the entry's first frame on every open, inside the one-frame bound (doc :60's "normally that frame" corrected by Change Log); (b) README: drawn motion rests on H1 / H2 | INDEX, README |
| R2-11 | NOTE | accept: assert the playback face ("An Ending") in Fossify's dumps | l11_1.sh (b) |
| R2-12 | NOTE | accept: assert the shell's session PLAYING / PAUSED in (c) | l11_1.sh (c) |
| R2-13 | NOTE | accept: assert no quick_burst / edit_disc on E10's control dump | e10.sh |
| R2-14 | NOTE | accept: the two README / INDEX wordings re-cut against COLDJANK.txt and the smoke log | README |
| R2-15 | NOTE | accept: phase 01 driver blobs stamped in the L11-1 log; E13's Contacts dump filtered to its package | l11_1.sh, e13.sh |

## Recorded, not fixed

| id | sev | why |
|---|---|---|
| R1-3 (F-6) | NOTE | the reviewer: "not a gate condition … land it with the next engine change"; no caller bypasses the arbiter today. A `require` in `publish()` would crash the launcher on a future programming error — that trade is Jeremy's, not this gate's |
| R1-4 | NOTE | the reviewer: "none required" (an identity-change wipe also clears the new install's live content until its next event; errs on phase 01's trust edge) |
