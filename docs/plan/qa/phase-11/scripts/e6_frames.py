#!/usr/bin/env python3
"""e6_frames.py FRAMES_DIR PTS_FILE TILE(l,t,r,b) PROBE(l,t,r,b)

E6's corroboration from a screenrecord (never the clock — the shell's own [quick] motion line is, C-5):

  * entry  = the first source frame whose PROBE box (inside a bottom-row tile, which dims and shrinks in edit
             mode's first frame and is nowhere near a satellite) differs from the first frame by > 12 on average
  * sat    = the first source frame with satellite pixels OUTSIDE the held tile: a pixel within 40 of the pure
             accent (sampled from the held tile, which stays undimmed) that was NOT near-accent in the first
             frame, in a band 4-260 px around the tile (satellites cross the black gutters as they leave it)
  * gap    = the largest source-frame spacing from entry-1 to entry+18 frames (phase 05's rule: <= 18.2 ms)

Prints: frames, entry_ms, sat_ms, sat_minus_entry_frames, max_gap_ms. Frame times come from ffprobe's
best_effort_timestamp_time list (one per source frame, in order).
"""
import glob
import os
import sys

import numpy as np
from PIL import Image


def load(p):
    return np.asarray(Image.open(p).convert("RGB")).astype(np.int16)


def main():
    d, pts_file = sys.argv[1], sys.argv[2]
    tl, tt, tr, tb = map(int, sys.argv[3].split(","))
    pl, pt, pr, pb = map(int, sys.argv[4].split(","))
    fs = sorted(glob.glob(os.path.join(d, "f_*.png")))
    pts = [float(x) * 1000 for x in open(pts_file).read().split() if x.strip()]
    n = min(len(fs), len(pts))
    first = load(fs[0])
    accent = np.median(first[tt + 20:tb - 20, tl + 20:tl + 60].reshape(-1, 3), axis=0)
    near0 = np.abs(first - accent).max(axis=2) <= 40
    h, w = first.shape[:2]
    band = np.zeros((h, w), bool)
    band[max(0, tt - 260):min(h, tb + 260), max(0, tl - 260):min(w, tr + 260)] = True
    band[tt - 4:tb + 4, tl - 4:tr + 4] = False
    base_probe = first[pt:pb, pl:pr].mean(axis=(0, 1))
    entry = sat = None
    for i in range(1, n):
        img = load(fs[i])
        if entry is None and np.abs(img[pt:pb, pl:pr].mean(axis=(0, 1)) - base_probe).mean() > 12:
            entry = i
        if entry is not None and sat is None:
            new = (np.abs(img - accent).max(axis=2) <= 40) & ~near0 & band
            if new.sum() > 60:
                sat = i
        if entry is not None and sat is not None:
            break
    lo = max(0, (entry or 1) - 1)
    hi = min(n - 1, (entry or 1) + 18)
    gaps = [pts[i + 1] - pts[i] for i in range(lo, hi)]
    print("frames", n)
    print("accent", *map(int, accent))
    print("entry_ms", round(pts[entry] - pts[0], 1) if entry else "none")
    print("sat_ms", round(pts[sat] - pts[0], 1) if sat else "none")
    print("sat_minus_entry_frames", (sat - entry) if entry and sat else "none")
    print("max_gap_ms", round(max(gaps), 1) if gaps else "none")


if __name__ == "__main__":
    main()
