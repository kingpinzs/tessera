#!/usr/bin/env python3
"""make_tall.py <in> <out>: the same layout with every tile WIDE, so Start is taller than one screen.

The default ten tiles do not fill the panel, so a scroll check on them proves nothing either way.
Every size is also marked hand-set (manualSizes): without that the shell's auto-sizer is free to change
the sizes on load, and REGRESS step 4 ran on a Start that was never taller than the screen (2026-09-22).
"""
import json, sys
d = json.load(open(sys.argv[1]))
for o in d["order"]:
    o["size"] = "WIDE"
d["manualSizes"] = sorted({o["key"] for o in d["order"]})
json.dump(d, open(sys.argv[2], "w"))
