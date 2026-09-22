#!/usr/bin/env python3
"""Take the seeded folders back off a Start layout, so their ADD can be watched from a clean slate.

Removes any folder named Games or Office, its tile from the grid, and both ADD markers.

usage: clean_seeded.py <layout.json>   (new layout on stdout)
"""
import json
import sys

layout = json.load(open(sys.argv[1]))
seeded = {f["id"] for f in layout["folders"] if f.get("name") in ("Games", "Office")}
layout["folders"] = [f for f in layout["folders"] if f["id"] not in seeded]
layout["order"] = [t for t in layout["order"] if t["key"] not in {f"folder:{i}" for i in seeded}]
layout["addedOnce"] = [m for m in layout.get("addedOnce", []) if m not in ("folder:games:v1", "folder:office:v1")]
json.dump(layout, sys.stdout)
