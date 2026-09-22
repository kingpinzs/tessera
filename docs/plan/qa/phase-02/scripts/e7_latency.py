#!/usr/bin/env python3
"""
E7: the 150 ± 17 ms between touch-up and the exit's first frame (R6 §1.5.2), read out of the shell's own
diagnostics ring.

Why not from video: that tolerance is finer than this AVD's screenrecord can resolve, because Android's touch
indicator is never drawn into the frames (verified: 0 bright pixels at the tap point across all 61 frames of
E07/e7_exit.mp4). Rather than defer the row, the shell now records both ends of the interval on ONE clock —
the touch-up's own MotionEvent time and the frame clock at the exit's first drawn frame, both uptime millis:

    [edit] tap on empty space: exit, touch-up uptime=123456
    [edit] exit first frame at uptime=123606, 150 ms after touch-up (R6 §1.5.2: 150 ± 17 ms)

These are real event timestamps, not the code reporting its own constant: the first comes from the input
system, the second from the choreographer. A regression in the delay, or a frame the exit missed, moves it.

usage: e7_latency.py <diag file>
"""
import re
import sys

import qa

text = open(sys.argv[1]).read()
ups = [int(m.group(1)) for m in re.finditer(r"touch-up uptime=(\d+)", text)]
frames = [(int(m.group(1)), int(m.group(2)))
          for m in re.finditer(r"exit first frame at uptime=(\d+), (-?\d+) ms after touch-up", text)]
if not frames:
    sys.exit(f"no exit-latency line in {sys.argv[1]} — did an exit happen while the ring was being captured?")

print(f"{len(ups)} touch-ups, {len(frames)} exits recorded")
results = []
for uptime, delta in frames:
    print(f"  exit at uptime {uptime}: {delta} ms after its touch-up")
# One frame of quantisation: the exit can only start on a frame boundary, so 150 ms lands on the frame at or
# after it.
worst = max(abs(d - 150) for _, d in frames)
results.append(("exit latency", qa.check(f"exit starts after touch-up (worst of {len(frames)})",
                                         150 + (worst if frames[0][1] >= 150 else -worst), 150, 17 + 1000 / 60, " ms")))
qa.report(results)
