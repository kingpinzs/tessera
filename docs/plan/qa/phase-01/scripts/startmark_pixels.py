#!/usr/bin/env python3
"""Measure the Start key's mark off a real screenshot.

Finds the white ink inside the nav_windows slot, then reports how much of each quadrant of the ink's
bounding box is lit. Mark B (three tiles, top-right place empty) lights three quadrants and leaves the
top-right one dark; the old Windows logo lit all four. Prints: w h tl tr bl br  (fractions 0..1).
"""
import sys
from PIL import Image

png, x1, y1, x2, y2 = sys.argv[1], *map(int, sys.argv[2:6])
im = Image.open(png).convert("L").crop((x1, y1, x2, y2))
W, H = im.size
px = im.load()
lit = [(x, y) for y in range(H) for x in range(W) if px[x, y] > 200]
if not lit:
    print("0 0 0 0 0 0"); sys.exit()
xs = [p[0] for p in lit]; ys = [p[1] for p in lit]
bx0, bx1, by0, by1 = min(xs), max(xs), min(ys), max(ys)
qw, qh = (bx1 - bx0 + 1) / 2, (by1 - by0 + 1) / 2
def frac(col, row):
    """Lit fraction of the INTERIOR (central 60 %) of one quadrant.

    The first version counted the whole quadrant, which includes the 8 % gap between the tiles and
    their anti-aliased edges, so a correctly drawn tile scored 0.81-0.84 and failed a 0.85 bar.
    What the row is about is whether a tile is there, and a tile's interior answers that exactly.
    """
    x0 = bx0 + col * qw + qw * 0.2; x1 = bx0 + col * qw + qw * 0.8
    y0 = by0 + row * qh + qh * 0.2; y1 = by0 + row * qh + qh * 0.8
    tot = on = 0
    for y in range(int(y0), int(y1) + 1):
        for x in range(int(x0), int(x1) + 1):
            tot += 1
            on += px[x, y] > 200
    return on / tot if tot else 0
print(bx1 - bx0 + 1, by1 - by0 + 1,
      round(frac(0, 0), 3), round(frac(1, 0), 3), round(frac(0, 1), 3), round(frac(1, 1), 3))
