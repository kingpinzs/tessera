#!/usr/bin/env python3
"""e13_pixels.py <start dump.xml> <screencap.png>: colours Start shows: the median colour of each static tile's clear band
(a strip along the tile's top inside the edge, clear of glyph and label), the page background in the gutter between the
first two tiles, and the medium tile width."""
import re, sys
import numpy as np
from PIL import Image
s = open(sys.argv[1]).read(); im = np.asarray(Image.open(sys.argv[2]).convert("RGB")).astype(int)
tiles = {m.group(1): tuple(map(int, m.groups()[1:])) for m in re.finditer(r'resource-id="tile:([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)}
for tid in ("slot:MAIL", "slot:PEOPLE", "dock:slot:CAMERA"):
    if tid in tiles:
        x1, y1, x2, y2 = tiles[tid]
        band = im[y1 + 6:y1 + 18, x1 + 10:x2 - 10].reshape(-1, 3)
        print(f"{tid}: bounds {tiles[tid]} ({x2 - x1} px wide), top-band colour {np.median(band, axis=0).astype(int).tolist()}")
p, b = tiles.get("slot:PEOPLE"), tiles.get("slot:BROWSER")
if p and b:
    gx = (p[2] + b[0]) // 2; gy = (p[1] + p[3]) // 2
    print(f"gutter between People and Browser at x {gx}: colour {im[gy - 20:gy + 20, gx].mean(axis=0).astype(int).tolist()} (gutter {b[0] - p[2]} px)")
y = max(t[3] for k, t in tiles.items() if not k.startswith("dock:")) + 60
print(f"page below the grid (y {y}, x 540): colour {im[y - 5:y + 5, 530:550].reshape(-1, 3).mean(axis=0).astype(int).tolist()}")
