#!/usr/bin/env python3
"""How much larger the emulated scene is in the second capture than in the first.

The emulated back camera draws a fixed pixel-art scene. For each candidate scale s the centre 1/s of the first capture
is enlarged to the full frame and compared with the second capture (mean absolute difference at 320 x 240); the scale
with the smallest difference is the answer — 2.0 for a true 2x zoom about the frame's centre. The difference at the
best scale and at 1.0 are printed to stderr, so a pair of unrelated pictures cannot pass silently.
"""
import sys
import numpy as np
from PIL import Image

a = Image.open(sys.argv[1]).convert('RGB')
b = np.asarray(Image.open(sys.argv[2]).convert('RGB').resize((320, 240), Image.BILINEAR), dtype=np.float64)
w, h = a.size
best = None
errors = {}
for i in range(100, 401, 5):
    s = i / 100
    cw, ch = w / s, h / s
    crop = a.crop((int(round((w - cw) / 2)), int(round((h - ch) / 2)), int(round((w + cw) / 2)), int(round((h + ch) / 2))))
    e = np.abs(np.asarray(crop.resize((320, 240), Image.BILINEAR), dtype=np.float64) - b).mean()
    errors[s] = e
    if best is None or e < errors[best]:
        best = s
print('best scale %.2f, mean abs difference %.1f there and %.1f at 1.00' % (best, errors[best], errors[1.0]), file=sys.stderr)
print('%.2f' % best)
