# Phase 17 gate, round 1 — findings and triage (2026-10-06)

Reviewers: Opus (A, design / correctness) + Opus (B, testability / evidence). Brief: `prompts/phase-17-gate-review.md`.
Gate build: clean debug APK md5 95b543037345b851 (app code bb154e06). **Both: GATE: FAIL.**

The lead's error, found by both: rows and edge sub-steps were left resting on earlier builds with the claim "code
unchanged since". For several the claim was false. `gate_table.py` printed it as a fixed string for every such row.

## Findings and what is done about each

| # | Sev | Who | Finding | Triage |
|---|---|---|---|---|
| 1 | HIGH | A1, B1, B2 | The kill-mid-write edges (`:camera` KILLWRITE, `:photosedit`) passed only on c7336aca; round 3 rewrote the pending ledger and the per-process write layer afterwards. The fixes file owes this leg on the device. | RE-RUN on 95b54303: `edge_camera.sh KILLWRITE`, `edge_photos.sh KILL_PHOTOSEDIT`. |
| 2 | HIGH (B) / MEDIUM (A) | A2, B2 | E7, E8, E15, E19_CAMERA rest on e8c26851; the toast path (every mode-switch, capsule and timer toast), the camera-open listener and the write layer changed since. EDGE_CAMERA REVOKE, SCREENOFF, CALL, REPOINT pass only inside a FAIL-named c7336aca run. | RE-RUN all of them on 95b54303. |
| 3 | HIGH (B) / MEDIUM (A) | A3, B1 | E6, E6b and the six Photos edges predate Living Images (a per-tile and per-picture file read) and the rewritten write layer. EDGE_THOUSANDS passed at 3.72 % janky against 5 % on the older code; its PNG fixtures skip the new read. | RE-RUN E6, E6b and the six sub-steps on 95b54303. P6 on the phone now says it is the only check of the Living read on real JPEGs. |
| 4 | MEDIUM | B3 | E18's 8 lines "recorded as held by an earlier build" contradict the doc (such a line fails the row); no ruling covered it. | `e18.py` now FAILS such a line. The re-runs of 2 and 3 produce all 8 on the gate build. |
| 5 | MEDIUM | B4 | `e18.py` matched too loosely (every placeholder was "anything"; the doc-line check was a substring test that could not fail; FAIL-named runs were searched). | Re-cut: digits for `<n>` / `<id>`, choices for `<a|b>`, the line must end where the pattern ends, passing runs only, and the doc's list is checked by exact pattern. Alternatives split into their own lines (launch answer read: granted / denied; threw X / not available into the notrun files). |
| 6 | MEDIUM | A4 | The Decisions line said "E9's legs assert them refused"; the forwarded display_photo leg is NOT RUN. | The Decisions line is corrected in place (dated). The leg stays the owner's item D4: its fixture edit was refused by this session's permission system and is not worked around. |
| 7 | MEDIUM | A5 | The liveness edge does not carry a signed-in server across a reboot, and the fixes file's legs (h) (fresh install with a PIN) and (o) (boot locked, first unlock) were not run and were listed nowhere. | The liveness driver now asserts all nine `[net]` lines after the reboot (the unlocked form of (o)) and records the locked form as not run. Two phone rows added: P17 (the key and the first-unlock lines after a phone restart), P18 (the server still signed in after a restart). (h) needs a wipe with a PIN on the shared AVD: NOT RUN, stated in the sheet. |
| 8 | MEDIUM | A6 | No device negative for a NAMED starter with no access: every shared-identity leg was a positive. | Two legs added and run: TRUST_PHOTOS (h-shared), TRUST_VIDEO (i-shared). |
| 9 | LOW | B5 | E18 did not name the `[motion]` lines of Video and two of Photos, the `(refreshed)` form, or Photos' trust lines. | Added to the producers files and to the join's doc list. |
| 10 | LOW | B6 | Tables disagree with the folders (rows_lead.md, rows_video.md, the three edge indexes, where the joined index lives). | Tables rewritten from the folders after the re-runs; the joined index moves to `scripts/edge_index.tsv`, where the doc names it. |
| 11 | LOW | A10, B7 | GUARD_SELF's log holds its deliberate FAIL control under a 0-failed name. | Accepted as is; named here. The control is the proof the guard counts. |
| 12 | LOW | B8 | Crash checks read `adb logcat … 2>/dev/null`: a logcat error would read as "no crash". `cred_names` prints empty on a `run-as` failure. | Accepted for this gate: every row with such a check also has positive legs read from the same device in the same run. Named here for the next phase's harness. |
| 13 | LOW | A7 | TRUST_VIDEO B-4 (a): the ring reads `server 10.0.2.2:8098: connected` while every request went to :8097. No token leaked. | NOT FIXED in this round (app code; a wrong host in one diagnostic line). On INDEX's ledger for the owner. |
| 14 | LOW | A8 | E22's leak scan covers the row's folder and logcat, not all of `qa/phase-17/**`. | The reviewer's own grep of all evidence found nothing; the scan's scope is stated in the Change Log. |
| 15 | LOW | A9 | TRUST_PHOTOS (k): no File information for another app's own-provider picture. | Recorded with its reason; unchanged. |

## What both reviewers judged sound

The pixel rule, the 4K edge, E9 leg (e) accepted, B-4 (b), E1's seed-line read, phase 12 E1's grant list, E13's mark
order, E20's leg order. E16, E23_PHOTOS and E23_CAMERA on earlier builds: no Start, Music or shortcut code changed.
The trust legs on the gate build show the refusals they claim. All 46 folders' totals match their names.
