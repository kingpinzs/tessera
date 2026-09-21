#!/usr/bin/env python3
"""e9_overflow.py <dump.xml>: with two columns every tile must still be inside the 4-unit grid and on screen.

This is the defect phase 01 recorded and phase 02 owns: with "show more tiles" off, the default layout's tiles
in columns 4-6 used to fall outside the grid. The check is geometric — no tile may cross the right margin.
"""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import qa

tiles = qa.dump_tiles(sys.argv[1])
grid = {t: b for t, b in tiles.items() if not t.startswith("dock:")}
left = qa.PANEL_W * 13 / 1440
right_edge = qa.PANEL_W - qa.PANEL_W * 21 / 1440
bad = {t: b for t, b in grid.items() if b[2] > right_edge + 2 or b[0] < left - 2}
print(f"{len(grid)} grid tiles; right margin at {right_edge:.0f}px")
for t, b in sorted(grid.items(), key=lambda kv: (kv[1][1], kv[1][0])):
    print(f"  {t:28s} {b}")
print(("FAIL, outside the grid: " + ", ".join(bad)) if bad else "PASS: every tile is inside the 2-column grid")
sys.exit(1 if bad else 0)
