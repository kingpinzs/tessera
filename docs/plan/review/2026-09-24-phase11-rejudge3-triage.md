# Phase 11 re-judge, round 3 (pass 7; the cap's last) — triage

Reports: review/2026-09-24-phase11-rejudge3-design.md (Opus, R1: 0 BLOCKING · 0 SHOULD-FIX · 3 NOTE) and
review/2026-09-24-phase11-rejudge3-evidence.md (Opus, R2: 0 BLOCKING · 0 SHOULD-FIX · 3 NOTE). Both lenses pass the gate as
evidenced. Round 3 is the cap's last and returned NOTEs only, so there is no fix round and no pass 8 (no reflex QA rounds);
every NOTE is recorded here for Jeremy, and none changes a verdict.

| id | sev | what | recorded as |
|---|---|---|---|
| D3-1 | NOTE | of the edit-mode still-press cases, the held tile WITH a burst open is not run with a long press (E5 runs it as a tap) | open, for a later pass |
| D3-2 | NOTE | phase 02's fling rule rests, on this build, on T11-38's consumed-DOWN return (EditGestures.kt:84), whose comment names only the transport control | open; a comment fix with the next edit to EditGestures |
| D3-3 | NOTE | on a full grid every corner label lies on a dimmed neighbouring tile and can cross that tile's own text | folded into H3's README note for Jeremy's look |
| EV3-1 | NOTE | the fling sub-step's "no burst" cannot fail (the press lands on a folder); the bullet stands on "no hold", the travel bounds and the positive control | open, for a later pass |
| EV3-2 | NOTE | RV10 font13's "applied" matches the settings read-back alone (the global configuration shows 1.3 too, EDGE.txt) | open, for a later pass |
| EV3-3 | NOTE | E13's press_none has no proof the press arrived (tilt and p4's DIFF show the same method's touches land) | open, for a later pass |
