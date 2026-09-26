#!/usr/bin/env python3
"""E8's screenrecord corroboration (never the clock — the shell's own [motion] line is, C-5): phase 05's frame-spacing
rule on a variable-rate emulator capture.

  motion_frames.py <capture.mp4> <frames_dir> [threshold] [x0,y0,x1,y1]

Decodes every SOURCE frame (-vsync passthrough) with its presentation time (ffprobe best_effort_timestamp_time), marks
a frame as moving when its mean absolute difference from the previous frame exceeds the threshold (default 0.5 levels),
splits the moving frames into bursts wherever two of them are more than 100 ms apart, and takes the motion window as the
burst that changes the screen most (summed difference). A burst boundary is not a place a stall can hide: a stall
longer than 100 ms splits the motion, and the chosen burst's window_ms then falls short of the motion's own duration,
which the caller compares against the [motion] line's settle. (The first cut took the first to the last moving frame of
the whole capture, so the show_touches dot at the hold's touch-down and later live-tile flips widened the window over
idle time where an emulator capture emits no frames: 584-784 ms "gaps" that were not in the motion, E8 2026-09-26.)
The optional region (capture pixels) restricts the difference to the animated surface, so the show_touches dot and live
tiles elsewhere on screen neither open nor widen a burst (gate review B, B2: the whole-screen mean also dropped the
ease-out tail of a small surface below the threshold). Prints:
  frames=<source frames> bursts=<n> window_frames=<n> window_ms=<ms> max_gap_ms=<largest source-frame spacing inside the window>
Phase 05's rule: a capture whose max_gap_ms is over 18.2 ms during the motion is rejected (not used to corroborate).
"""
import glob
import os
import subprocess
import sys

import numpy as np
from PIL import Image


def main():
    mp4, d = sys.argv[1], sys.argv[2]
    thr = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
    roi = tuple(int(v) for v in sys.argv[4].split(",")) if len(sys.argv) > 4 else None
    os.makedirs(d, exist_ok=True)
    for f in glob.glob(os.path.join(d, "f*.png")):
        os.remove(f)
    subprocess.run(["ffmpeg", "-v", "error", "-i", mp4, "-vsync", "passthrough", os.path.join(d, "f%05d.png")], check=True)
    pts = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=best_effort_timestamp_time",
         "-of", "csv=p=0", mp4], capture_output=True, text=True, check=True).stdout.split()
    pts = [float(p.strip(",")) for p in pts if p.strip(",")]
    files = sorted(glob.glob(os.path.join(d, "f*.png")))
    n = min(len(files), len(pts))
    moving = []
    change = {}
    prev = None
    for i in range(n):
        img = Image.open(files[i]).convert("L")
        if roi:
            img = img.crop(roi)
        a = np.asarray(img, dtype=np.float32)
        if prev is not None:
            d = float(np.abs(a - prev).mean())
            if d > thr:
                moving.append(i)
                change[i] = d
        prev = a
    if not moving:
        print("frames=%d bursts=0 window_frames=0 window_ms=0 max_gap_ms=0" % n)
        return
    bursts = [[moving[0]]]
    for i in moving[1:]:
        if (pts[i] - pts[bursts[-1][-1]]) * 1000 > 100:
            bursts.append([i])
        else:
            bursts[-1].append(i)
    best = max(bursts, key=lambda b: sum(change[i] for i in b))
    # The window opens at the burst's first moving frame: the frame before it is the last still one, which a
    # variable-rate capture may have emitted any time earlier, so that spacing is idle time, not the motion's.
    lo, hi = best[0], best[-1]
    gaps = [(pts[i] - pts[i - 1]) * 1000 for i in range(lo + 1, hi + 1)]
    print("frames=%d bursts=%d window_frames=%d window_ms=%.1f max_gap_ms=%.1f" % (
        n, len(bursts), hi - lo + 1, (pts[hi] - pts[lo]) * 1000, max(gaps) if gaps else 0))


if __name__ == "__main__":
    main()
