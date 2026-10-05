#!/usr/bin/env python3
"""The dial's ring radii in epx, fitted from a 1080-px-wide screencap: along rays from the dial's centre — (W/2, the nav
bar's top) = (540, 2196) px — the ring ink (about #B9B9B9 on the dark scrim) is found as runs of light-grey pixels, and
each ring's radius is the median over the rays of its run's centre. Prints the radii found, innermost first."""
import math, sys
import numpy as np
from PIL import Image

im = np.asarray(Image.open(sys.argv[1]).convert('RGB'), dtype=np.int32)
cx, cy = 540.0, 2196.0
found = {}
for deg in list(range(62, 80, 2)) + list(range(100, 106, 2)):
    a = math.radians(deg)
    hits = []
    for r10 in range(900, 12600):            # radius in 0.1 px
        r = r10 / 10.0
        x, y = int(round(cx + r * math.cos(a))), int(round(cy - r * math.sin(a)))
        if not (0 <= x < im.shape[1] and 0 <= y < im.shape[0]):
            break
        p = im[y, x]
        # ring ink: light grey, neutral
        if p.min() >= 150 and p.max() - p.min() <= 12 and p.max() <= 215:
            hits.append(r)
    runs = []
    for r in hits:
        if runs and r - runs[-1][-1] <= 1.0: runs[-1].append(r)
        else: runs.append([r])
    for run in runs:
        if 1.5 <= run[-1] - run[0] <= 6.0:   # a 1-epx (3-px) stroke
            mid = (run[0] + run[-1]) / 2 / 3.0
            key = min((130.5, 195.4, 260.3, 325.3, 390.2), key=lambda k: abs(k - mid))
            if abs(key - mid) < 12: found.setdefault(key, []).append(mid)
print(' '.join('%.1f' % float(np.median(found[k])) for k in sorted(found)))
