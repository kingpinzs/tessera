#!/usr/bin/env python3
"""Ink measurements off a screencap for the clock geometry row (E10). Device pixels in, device pixels out.

  ink.py bbox PNG x0 y0 x1 y1 MODE [arg]      the ink's bounding box "left top right bottom" (inclusive-exclusive)
                                              inside the rectangle; MODE: bright (lum > arg, default 100),
                                              accent (r,g,b within 40 of the given "r,g,b"), dark (lum < arg)
  ink.py glyph PNG x0 y0 x1 y1 MODE [arg]     the FIRST glyph's box: the leftmost run of ink columns up to the first
                                              empty column (≥ 2 px wide gap), then that run's ink rows —
                                              the cap / digit height of a string's first letter
  ink.py rows PNG x0 y0 x1 y1 MODE [arg]      the runs of rows holding ink, as "top-bottom" ranges
  ink.py cols PNG x0 y0 x1 y1 MODE [arg]      the runs of columns holding ink, as "left-right" ranges
  ink.py hband PNG x y0 y1 "r,g,b" [tol]      the rows y0..y1 at column x whose pixel is within tol of the colour, as ranges
"""
import sys

import numpy as np
from PIL import Image


def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int32)  # int32: the luminance product overflows int16 (E10 run 1)


def mask(a, mode, arg):
    lum = (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000
    if mode == "bright":
        return lum > int(arg or 100)
    if mode == "dark":
        return lum < int(arg or 60)
    if mode == "accent":
        c = np.array([int(v) for v in arg.split(",")])
        return (np.abs(a - c) <= 40).all(axis=2)
    raise SystemExit("mode?")


def runs(flags):
    out, start = [], None
    for i, f in enumerate(flags):
        if f and start is None: start = i
        if not f and start is not None: out.append((start, i)); start = None
    if start is not None: out.append((start, len(flags)))
    return out


def main():
    cmd = sys.argv[1]
    a = load(sys.argv[2])
    if cmd == "hband":
        x, y0, y1 = int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
        c = np.array([int(v) for v in sys.argv[6].split(",")]); tol = int(sys.argv[7]) if len(sys.argv) > 7 else 8
        col = a[y0:y1, x]
        f = (np.abs(col - c) <= tol).all(axis=1)
        print(" ".join("%d-%d" % (r0 + y0, r1 + y0) for r0, r1 in runs(f)))
        return
    x0, y0, x1, y1 = (int(v) for v in sys.argv[3:7])
    mode = sys.argv[7]; arg = sys.argv[8] if len(sys.argv) > 8 else None
    m = mask(a[y0:y1, x0:x1], mode, arg)
    if cmd == "bbox":
        ys, xs = np.where(m)
        if len(ys) == 0: print(""); return
        print(x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1)
    elif cmd == "rows":
        print(" ".join("%d-%d" % (r0 + y0, r1 + y0) for r0, r1 in runs(m.any(axis=1))))
    elif cmd == "cols":
        print(" ".join("%d-%d" % (c0 + x0, c1 + x0) for c0, c1 in runs(m.any(axis=0))))
    elif cmd == "glyph":
        colflags = m.any(axis=0)
        cruns = runs(colflags)
        if not cruns: print(""); return
        # merge column runs separated by a gap < 2 px (anti-aliasing inside one glyph)
        merged = [list(cruns[0])]
        for c0, c1 in cruns[1:]:
            if c0 - merged[-1][1] < 2: merged[-1][1] = c1
            else: merged.append([c0, c1])
        c0, c1 = merged[0]
        sub = m[:, c0:c1]
        rows = np.where(sub.any(axis=1))[0]
        print(x0 + c0, y0 + rows.min(), x0 + c1, y0 + rows.max() + 1)


if __name__ == "__main__":
    main()
