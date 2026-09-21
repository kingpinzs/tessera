#!/usr/bin/env python3
"""dumpdiff.py <a.xml> <b.xml>: compare the tile rectangles of two uiautomator dumps, tile by tile."""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import qa
a, b = qa.dump_tiles(sys.argv[1]), qa.dump_tiles(sys.argv[2])
only_a, only_b = sorted(set(a) - set(b)), sorted(set(b) - set(a))
moved = {t: (a[t], b[t]) for t in sorted(set(a) & set(b)) if a[t] != b[t]}
print(f"tiles: {len(a)} -> {len(b)}; gone: {only_a or 'none'}; new: {only_b or 'none'}; moved: {len(moved)}")
for t, (x, y) in moved.items():
    print(f"  {t}: {x} -> {y}")
print("IDENTICAL" if not (only_a or only_b or moved) else "DIFFERENT")
sys.exit(0 if not (only_a or only_b or moved) else 1)
