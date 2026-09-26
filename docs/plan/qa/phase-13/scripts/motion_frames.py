#!/usr/bin/env python3
"""E8's screenrecord corroboration (never the clock — the shell's own [motion] line is, C-5): phase 05's frame-spacing
rule on a variable-rate emulator capture.

  motion_frames.py <capture.mp4> <frames_dir> [threshold]

Decodes every SOURCE frame (-vsync passthrough) with its presentation time (ffprobe best_effort_timestamp_time), marks
a frame as moving when its mean absolute difference from the previous frame exceeds the threshold (default 0.5 levels),
and takes the motion window as the first to the last moving frame. Prints:
  frames=<source frames> window_frames=<n> window_ms=<ms> max_gap_ms=<largest source-frame spacing inside the window>
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
    prev = None
    for i in range(n):
        a = np.asarray(Image.open(files[i]).convert("L"), dtype=np.float32)
        if prev is not None and np.abs(a - prev).mean() > thr:
            moving.append(i)
        prev = a
    if not moving:
        print("frames=%d window_frames=0 window_ms=0 max_gap_ms=0" % n)
        return
    lo, hi = moving[0] - 1, moving[-1]
    gaps = [(pts[i] - pts[i - 1]) * 1000 for i in range(lo + 1, hi + 1)]
    print("frames=%d window_frames=%d window_ms=%.1f max_gap_ms=%.1f" % (
        n, hi - lo + 1, (pts[hi] - pts[lo]) * 1000, max(gaps) if gaps else 0))


if __name__ == "__main__":
    main()
