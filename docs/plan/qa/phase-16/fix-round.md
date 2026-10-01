# Phase 16 — the fix round (lead's ledger)

Everything that changes product code after the merge of tasks 3–7 and 9 (build 686506a7) lands in ONE round, so the
rows are re-run once, on one final APK. The row writers keep testing 686506a7 until then. Sources: the owner's
rulings, the trust reviewers' findings, the row writers' defect files (`defects/D-*.md`), the builders' own notes.

| # | From | What | Owner | State |
|---|---|---|---|---|
| F1 | Ruling Q-16-4 (Jeremy: "a", 2026-10-01) | The reminder receiver reminds only for alerts due after the shell's first start (`remindersSince`); older rows skipped, one line per poke | Calendar builder | BUILT on `phase-16-cal` 873bdbd8 (unit suite 1119 / 0, `dev-cal/unit-H.out`); not merged, not on a device |
| F2 | Calendar builder's note on F1 | The clock set back while the shell's process stays alive: the first poke afterwards lowers the cut-off to now and so skips the very alert that caused it, once. Fix at the producer: lower the cut-off when the clock change is announced (the shell already receives `TIME_SET`), before any poke | Calendar builder | OPEN |
