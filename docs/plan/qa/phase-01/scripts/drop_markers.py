#!/usr/bin/env python3
"""Remove one-shot ADD markers from a Start layout, so the ADD behind them can be watched again.

usage: drop_markers.py <layout.json> <marker> [<marker> ...]   (new layout on stdout)
"""
import json
import sys

layout = json.load(open(sys.argv[1]))
drop = set(sys.argv[2:])
layout["addedOnce"] = [m for m in layout.get("addedOnce", []) if m not in drop]
json.dump(layout, sys.stdout)
