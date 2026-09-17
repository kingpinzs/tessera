#!/usr/bin/env python3
"""e10_press.py <E10 dir> <tile id>: press feedback numbers from e10_press.sh screencaps.

- none: mean absolute RGB change in the tile region at pointer-down (R3 A10: 0.00).
- tilt / p4: the lit tile's bounding box while held vs at rest (width and height ratio, left/right edge heights for the
  tilt's perspective), and the mean colour ratio in the tile's central quarter (P4: 0.88 = 12 % black overlay).
"""
import re, sys
import numpy as np
from PIL import Image

D, tile = sys.argv[1:3]
out = []
for style in ("none", "tilt", "p4"):
    xml = open(f"{D}/press_start_{style}.xml").read()
    m = re.search(r'resource-id="tile:' + re.escape(tile) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    x1, y1, x2, y2 = map(int, m.groups())
    for spot in ("centre", "corner"):
        imgs = {k: np.asarray(Image.open(f"{D}/press_{style}_{spot}_{k}.png").convert("RGB")).astype(int) for k in ("before", "down", "after")}
        pad = 12
        region = lambda a: a[y1 - pad:y2 + pad, x1 - pad:x2 + pad]
        diff = np.abs(region(imgs["down"]) - region(imgs["before"])).mean()
        after = np.abs(region(imgs["after"]) - region(imgs["before"])).mean()
        line = f"{style} {spot}: mean abs change at pointer-down {diff:.2f}, after release {after:.2f}"
        if style != "none":
            def box(a):
                lit = region(a).sum(axis=2) > 120
                ys, xs = np.nonzero(lit)
                cols = lit.any(axis=0).nonzero()[0]
                lh = lit[:, cols[0] + 2].sum(); rh = lit[:, cols[-1] - 2].sum()
                th = lit[ys.min() + 2, :].sum(); bh = lit[ys.max() - 2, :].sum()
                return xs.min(), xs.max(), ys.min(), ys.max(), lh, rh, th, bh
            b0, b1 = box(imgs["before"]), box(imgs["down"])
            w0, w1 = b0[1] - b0[0] + 1, b1[1] - b1[0] + 1; h0, h1 = b0[3] - b0[2] + 1, b1[3] - b1[2] + 1
            cq = lambda a: region(a)[pad + (y2 - y1) // 4: pad + 3 * (y2 - y1) // 4, pad + (x2 - x1) // 4: pad + 3 * (x2 - x1) // 4].reshape(-1, 3).mean(axis=0)
            ratio = cq(imgs["down"]) / np.maximum(cq(imgs["before"]), 1)
            line += (f"; lit box {w0}x{h0} -> {w1}x{h1} px (w {w1 / w0:.3f}, h {h1 / h0:.3f}); left/right edge heights {b1[4]}/{b1[5]} px"
                     f" (rest {b0[4]}/{b0[5]}), top/bottom edge widths {b1[6]}/{b1[7]} px (rest {b0[6]}/{b0[7]}); centre colour ratio {np.round(ratio, 3).tolist()}")
        out.append(line)
print("\n".join(out))
