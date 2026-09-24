# EDGE_ALARMS: the clock pass's own run, left running as an orphan after that agent hit its usage limit

It drove emulator-5558 at the same time as the lead's runners (pass 2 and 2b), which is why their baselines held
alarms like "Unanswered" and their rows cascaded. It ended at 15:00:19 (98 passed, 17 failed, 7 recorded). It is kept as
evidence of that overlap, not as a result: EDGE_ALARMS is re-run in the final gate on one build, alone on its emulator.
