# Jeremy's live QA — issue ledger

Issues Jeremy reports while testing the app (started 2026-09-22). Each is reproduced on the AVD before it
is fixed, fixed at the producer, and closed only with a re-run row. Status: open / reproducing / fixed
(commit) / phone-only / not-a-bug (why).

| # | Date | Jeremy's words | Part (phase) | Status | Evidence / fix |
|---|---|---|---|---|---|
| J1 | 2026-09-22 | "When using the ai. When the message gets sent it does not clear the input and clicking the x does not clear it and I have to hit the back button"; then "It should auto clear when it gets auto sent" | Tess text box (03) | fixed | Reproduced: J1-run1.txt 5/8 — the ✕ called goTo(HOME), which returns early because an answer is already on the Home destination; Back had its own clear. Fix: one CortanaModel.clearResult() for both. ✕ fix 8/8 (J1-run2.txt, commit 20414e5). Then Jeremy overrode R6 §3.3.7: the bar clears itself on send (INDEX Change Log): J1.txt 15/15, typed and spoken (runs 3-4 were driver faults, kept). |
