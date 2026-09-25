#!/usr/bin/env python3
"""qpix.py — phase 11 pixel helpers (screencap PNG, device px).

  qpix.py uniform <png> l t r b [inset]     -> "UNIFORM <spread>" or "VARIED <spread>": every pixel of the rect
                                              (shrunk by inset px) within the rect's median ± 2 on each channel?
  qpix.py sample <png> x y                  -> "r g b"
  qpix.py equal <a.png> <b.png> l t r b tol  -> "EQUAL <maxdiff>" or "DIFF <maxdiff>" over the rect
"""
import sys
import numpy as np
from PIL import Image


def load(p):
    return np.asarray(Image.open(p).convert("RGB")).astype(np.int16)


def main():
    op = sys.argv[1]
    if op == "uniform":
        img = load(sys.argv[2])
        l, t, r, b = map(int, sys.argv[3:7])
        inset = int(sys.argv[7]) if len(sys.argv) > 7 else 4
        box = img[t + inset:b - inset, l + inset:r - inset].reshape(-1, 3)
        med = np.median(box, axis=0)
        spread = int(np.abs(box - med).max())
        print(("UNIFORM" if spread <= 2 else "VARIED"), spread)
    elif op == "sample":
        img = load(sys.argv[2])
        x, y = int(sys.argv[3]), int(sys.argv[4])
        print(*img[y, x])
    elif op == "equal":
        a, b = load(sys.argv[2]), load(sys.argv[3])
        l, t, r, bb, tol = map(int, sys.argv[4:9])
        d = int(np.abs(a[t:bb, l:r] - b[t:bb, l:r]).max())
        print(("EQUAL" if d <= tol else "DIFF"), d)


if __name__ == "__main__":
    main()
