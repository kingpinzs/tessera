#!/usr/bin/env python3
"""The smallest gap, in ms, between any two DIFFERENT tiles' first face changes.

Reads the diagnostics ring on stdin. Prints nothing when fewer than two tiles have cycled.
"""
import re
import sys

first = {}
for tile, uptime in re.findall(r"tile=(\S+)\s+kind=\S+\s+uptime=(\d+)", sys.stdin.read()):
    stamp = int(uptime)
    if tile not in first or stamp < first[tile]:
        first[tile] = stamp
stamps = sorted(first.values())
if len(stamps) < 2:
    print("")
else:
    print(min(b - a for a, b in zip(stamps, stamps[1:])))
