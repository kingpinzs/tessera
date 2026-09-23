# Jeremy's live QA — issue ledger

Issues Jeremy reports while testing the app (started 2026-09-22). Each is reproduced on the AVD before it
is fixed, fixed at the producer, and closed only with a re-run row. Status: open / reproducing / fixed
(commit) / phone-only / not-a-bug (why).

| # | Date | Jeremy's words | Part (phase) | Status | Evidence / fix |
|---|---|---|---|---|---|
| J1 | 2026-09-22 | "When using the ai. When the message gets sent it does not clear the input and clicking the x does not clear it and I have to hit the back button"; then "It should auto clear when it gets auto sent" | Tess text box (03) | fixed | Reproduced: J1-run1.txt 5/8 — the ✕ called goTo(HOME), which returns early because an answer is already on the Home destination; Back had its own clear. Fix: one CortanaModel.clearResult() for both. ✕ fix 8/8 (J1-run2.txt, commit 20414e5). Then Jeremy overrode R6 §3.3.7: the bar clears itself on send (INDEX Change Log): J1.txt 15/15, typed and spoken (runs 3-4 were driver faults, kept). |
| J2 | 2026-09-22 | "The media player does not show the total time and scrolling the playing song does not move the cursor" | Music now-playing (10) | fixed | Reproduced: qa/phase-01/J2/J2-run1.txt 9/12 — a constant-bitrate MP3 with no Xing/Info header reads total 0:00 and every scrub seeks to ~0:02; files with the header work (why MUSIC7 passed). Cause: E17 index seeking takes the length only from a header. Fix: KnownDurationPlayer reports MediaStore's measured length when the extractor has none; the session and crossfade both use it. After: J2.txt 12/12; MUSIC7 46/46 and MUSIC17 26/26 re-run on the same APK. |
| J3 | 2026-09-22 | "When the folder is open it goes underneath the bottom line instead of over and does not scroll when its to far down" | Start folders (02) + bottom tile row | reading code (emulator busy with J2) | |
