# E26 run 1: 6 passed, 19 failed — driver faults (typed half) + the audio route (spoken half); no product finding

- The typed loop read calc-cases.tsv with IFS=tab. Tab is IFS whitespace, so the tess rows' empty setup/keys columns
  collapsed and shifted every field: "tess-001 typed" received the SOURCE column, and the reply assertion passed
  vacuously (empty = empty). And adb inside the loop read the loop's stdin, so only the first line ran. Fixed: the tess
  lines are extracted by Python, joined by \x1f and read on fd 3; each case asserts a non-empty utterance/reply and
  the count of lines driven equals the oracle's.
- Every spoken step: NOT RUN, audio.sh check failed (capture source: easyeffects_source, want vmic.monitor), the host
  audio hazard in STATE.md. The negatives' ring checks failed for the same reason (no capture).
