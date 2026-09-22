#!/usr/bin/env python3
"""Median milliseconds between one tile's face changes, read off the diagnostics ring on stdin.

Prints nothing when there are fewer than two changes, which is itself the answer for a picture frame:
a frame does not flip, so it has no period.

usage: diag | anim_period.py <tile id>
"""
import re
import sys

tile = sys.argv[1]
stamps = sorted({int(m) for m in re.findall(r"tile=" + re.escape(tile) + r"\s+kind=\S+\s+uptime=(\d+)", sys.stdin.read())})
if len(stamps) < 2:
    print("")
else:
    gaps = sorted(stamps[i + 1] - stamps[i] for i in range(len(stamps) - 1))
    print(gaps[len(gaps) // 2])
