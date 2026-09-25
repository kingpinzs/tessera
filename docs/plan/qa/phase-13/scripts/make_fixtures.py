#!/usr/bin/env python3
"""Phase 13 fixtures (build task 7), generated, never hand-drawn.

  checker <out.png> [W H]   the checkerboard: 4 squares across, black / white, at the display size (T13-12)
  split <out.png> [W H]     a black / white split at x = W/2 (E3's reminder photo, the isolated step)
"""
import sys
from PIL import Image

kind, out = sys.argv[1], sys.argv[2]
W, H = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (1080, 2340)
img = Image.new("RGB", (W, H), (0, 0, 0))
px = img.load()
if kind == "checker":
    s = W // 4
    for y in range(H):
        for x in range(W):
            if ((x // s) + (y // s)) % 2:
                px[x, y] = (255, 255, 255)
elif kind == "split":
    for y in range(H):
        for x in range(W // 2, W):
            px[x, y] = (255, 255, 255)
else:
    sys.exit("unknown fixture " + kind)
img.save(out)
print(out, W, H)
