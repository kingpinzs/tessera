#!/usr/bin/env python3
"""Set one tile's size in the Start layout, pinned so auto-size cannot change it back.

usage: layout_size.py <layout.json> <tile key> <SMALL|MEDIUM|WIDE>   (new layout on stdout)
"""
import json
import sys

path, key, size = sys.argv[1], sys.argv[2], sys.argv[3]
layout = json.load(open(path))
found = False
for tile in layout.get("order", []):
    if tile.get("key") == key:
        tile["size"] = size
        found = True
if not found:
    sys.exit(f"{key} is not in the layout")
# A size set by hand is pinned (SpeechAssets' sibling rule in AutoSize): without this the use-driven
# sizing from item 1 is free to put the tile back where it thinks it belongs mid-row.
manual = set(layout.get("manualSizes", []))
manual.add(key)
layout["manualSizes"] = sorted(manual)
json.dump(layout, sys.stdout)
