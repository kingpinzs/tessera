#!/usr/bin/env python3
"""ink.py shot.png l t r b -> the ink inside the box: left, top (cap top), right, bottom in px, and its brightest pixel."""
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
l, t, r, b = [int(v) for v in sys.argv[2:6]]
xs, ys, best = [], [], (0, 0, 0)
for y in range(t, b):
    for x in range(l, r):
        p = im.getpixel((x, y))
        if max(p) >= 80:
            xs.append(x); ys.append(y)
            if sum(p) > sum(best): best = p
print("%d %d %d %d %d,%d,%d" % ((min(xs), min(ys), max(xs), max(ys)) + best) if xs else "none")
