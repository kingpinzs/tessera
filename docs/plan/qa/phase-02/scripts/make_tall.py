#!/usr/bin/env python3
"""make_tall.py <in> <out>: the same layout with every tile WIDE, so Start is taller than one screen.

The default ten tiles do not fill the panel, so a scroll check on them proves nothing either way.
"""
import json, sys
d = json.load(open(sys.argv[1]))
for o in d["order"]:
    o["size"] = "WIDE"
json.dump(d, open(sys.argv[2], "w"))
