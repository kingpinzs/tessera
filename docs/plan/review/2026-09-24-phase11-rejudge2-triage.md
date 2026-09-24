# Phase 11 re-judge, round 2 (pass 6) — triage

Reports: review/2026-09-24-phase11-rejudge2-design.md (Opus, R1: 0 BLOCKING · 1 SHOULD-FIX · 2 NOTE) and
review/2026-09-24-phase11-rejudge2-evidence.md (Opus, R2: 1 BLOCKING · 4 SHOULD-FIX · 10 NOTE). Every finding was checked
against the files before it was accepted; D2-1 / EV-1(b) was re-checked on the device (FLING-probe: a 400-px `input swipe`
over 150 ms carries the page 849 px, so Start does fling — the sub-step's raw drag never did).

## Fix (this round), then pass 7 end to end

| id | sev | decision | where |
|---|---|---|---|
| EV-1 (a) | BLOCKING | accept: the keyguard sub-step sets a PIN for its duration, asserts the keyguard is showing after the wake, unlocks with the PIN, clears it | edge.sh |
| EV-1 (b), D2-1 | BLOCKING / SHOULD-FIX | accept: a control swipe (no press) must fling past the finger; the same swipe then a raw press must stop short of the control and past the finger; no hold / burst / launch; a positive control (a still hold at the same point enters edit mode) | edge.sh |
| EV-1 (c) | BLOCKING | accept: a control after the sub-step — the same slot-1 contact alone, with a burst open, closes it (tap elsewhere), so the contact reaches the app | edge.sh |
| EV-2 | SHOULD-FIX | accept: (d) asserts 9f790b7's own null publish after the forget; the race window itself is recorded as not produced | l11_1.sh, README |
| EV-3 | SHOULD-FIX | accept: the theme asserted from the ring; the page ground from a clear patch; the ink judged against its own ground | edge.sh |
| EV-4 | SHOULD-FIX | accept: (i) a new EDGE sub-step (a 1.0-s still press on another tile with a burst open closes it and holds nothing; a still press on the held tile with none open exits edit mode); (ii)-(iv) index lines re-worded to what their evidence proves | edge.sh, edge_index.tsv |
| EV-5 | SHOULD-FIX | accept: RV10 reads each setting back and asserts it applied | edge.sh |
| D2-2, EV-7 | NOTE | accept: no satellite re-rests across the flip (ring) | edge.sh |
| D2-3 | NOTE | accept: H3's X5 note says a neighbour's glyph shows through at 100 % | README |
| EV-6 | NOTE | accept: `qa_blank: startShortcut ok`; the fixed-size box check dropped (the ellipsis is labels.png / H9) | edge.sh |
| EV-8 | NOTE | accept: the burst asserted before the kill; the ring saved before it | edge.sh |
| EV-9 | NOTE | accept: a recording with no detected entry is never accepted | e6_frames.py, e6.sh |
| EV-10 | NOTE | accept: E14 counts only the gate rows by name | e14.sh |
| EV-11 | NOTE | accept: run_all cleans the unit results first, so they are the run's own; L11-1 prints the XML's own timestamp | run_all.sh, l11_1.sh |
| EV-12 | NOTE | accept: E13's press styles press tileclient-b's tile (it has a shortcut), so "no burst" can fail; the duration's label says what it measures | e13.sh |
| EV-13 | NOTE | accept: X5 asserts the stored value from the ring (≤ 0.01, ≥ 0.99) | edge.sh |
| EV-14 | NOTE | accept: 42 cases, not 43; round 1's evidence report had 7 NOTEs and no R2-16 (round-1 triage corrected); E10's title; the old "30-36 %" and "16.7 ms throughout" wordings | triage, INDEX, e10.sh, e6.sh |
| EV-15 | NOTE | accept: the appended verdict includes the unit line; an append keeps the row directory it replaces | run_all.sh |

## Recorded, not fixed

| id | sev | why |
|---|---|---|
| D2-3's product half | — | whether a satellite should be opaque at X5 100 % is Jeremy's (H3); the doc's Decision (:86-87) says tile fill incl. transparency, and that is what is built |
