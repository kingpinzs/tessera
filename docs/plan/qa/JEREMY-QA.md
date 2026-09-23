# Jeremy's live QA — issue ledger

Issues Jeremy reports while testing the app (started 2026-09-22). Each is reproduced on the AVD before it
is fixed, fixed at the producer, and closed only with a re-run row. Status: open / reproducing / fixed
(commit) / phone-only / not-a-bug (why).

| # | Date | Jeremy's words | Part (phase) | Status | Evidence / fix |
|---|---|---|---|---|---|
| J1 | 2026-09-22 | "When using the ai. When the message gets sent it does not clear the input and clicking the x does not clear it and I have to hit the back button" | Tess text box (03) | fixed | Reproduced: J1-run1.txt 5/8 — the ✕ called goTo(HOME), which returns early because an answer is already on the Home destination; Back had its own clear. Fix: one CortanaModel.clearResult() for both. After: qa/phase-03/J1/J1.txt 8/8 (driver scripts/j1.sh). |
