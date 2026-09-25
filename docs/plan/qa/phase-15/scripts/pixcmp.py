#!/usr/bin/env python3
"""Pixel comparisons for the clock rows (E4's "equals ± 8 levels in ≥ 90 % of its pixels", E10's fills).

  pixcmp.py region A.png B.png y0 y1 [tol] [mask x0,y0,x1,y1 ...]
      the fraction (0..1) of pixels in rows y0..y1 (device px, full width) of A within ±tol levels on every
      channel of B's same pixel; each mask rectangle is left out of the count
  pixcmp.py sample A.png x y            the pixel's "r,g,b"
  pixcmp.py mean A.png x0 y0 x1 y1      the mean "r,g,b" of a rectangle
  pixcmp.py diffrows A.png B.png y0 y1 tol   the rows (y) whose share of differing pixels exceeds 10 %, as ranges
"""
import sys

import numpy as np
from PIL import Image


def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int16)


def region(a, b, y0, y1, tol, masks):
    a = a[y0:y1]
    b = b[y0:y1]
    keep = np.ones(a.shape[:2], dtype=bool)
    for m in masks:
        x0, my0, x1, my1 = m
        keep[max(0, my0 - y0):max(0, my1 - y0), x0:x1] = False
    close = (np.abs(a - b) <= tol).all(axis=2)
    n = keep.sum()
    return (close & keep).sum() / n if n else 0.0


def main():
    cmd = sys.argv[1]
    if cmd == "sample":
        a = load(sys.argv[2]); x, y = int(sys.argv[3]), int(sys.argv[4])
        print(",".join(str(int(v)) for v in a[y, x]))
    elif cmd == "mean":
        a = load(sys.argv[2]); x0, y0, x1, y1 = map(int, sys.argv[3:7])
        print(",".join(str(int(round(v))) for v in a[y0:y1, x0:x1].reshape(-1, 3).mean(axis=0)))
    elif cmd == "region":
        a, b = load(sys.argv[2]), load(sys.argv[3])
        y0, y1 = int(sys.argv[4]), int(sys.argv[5])
        tol = int(sys.argv[6]) if len(sys.argv) > 6 else 8
        masks = [tuple(int(v) for v in m.split(",")) for m in sys.argv[7:]]
        print("%.4f" % region(a, b, y0, y1, tol, masks))
    elif cmd == "diffrows":
        a, b = load(sys.argv[2]), load(sys.argv[3])
        y0, y1, tol = int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
        far = (np.abs(a[y0:y1] - b[y0:y1]) > tol).any(axis=2).mean(axis=1) > 0.10
        ranges, start = [], None
        for i, f in enumerate(far):
            if f and start is None: start = i
            if not f and start is not None: ranges.append((start + y0, i + y0)); start = None
        if start is not None: ranges.append((start + y0, len(far) + y0))
        print(" ".join("%d-%d" % r for r in ranges))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
