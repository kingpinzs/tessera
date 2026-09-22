#!/usr/bin/env python3
"""E15: measure R7 3.2's destination transition out of 60-fps frames.

    fade.py <frames-dir>

Prints black_ms, fade_ms and first_frame_alpha. Exits non-zero when no black stretch followed by a
fade is present at all, rather than returning numbers it could not measure.

Brightness is the mean luminance of the whole frame, which is what "the whole page fades up" means:
header, background and app bar together (R7 3.2.2).
"""
import glob
import os
import sys

from PIL import Image

FPS = 60.0
BLACK = 3.0          # mean luminance at or below this is "fully black"


def luminance(path):
    with Image.open(path) as raw:
        small = raw.convert("L").resize((64, 128))
        pixels = list(small.getdata())
    return sum(pixels) / len(pixels)


def main():
    frames = sorted(glob.glob(os.path.join(sys.argv[1], "f_*.png")))
    if len(frames) < 30:
        print(f"only {len(frames)} frames")
        sys.exit(2)
    lums = [luminance(p) for p in frames]

    # The transition: the last run of near-black frames, then the climb back up.
    black_runs = []
    start = None
    for i, l in enumerate(lums):
        if l <= BLACK and start is None:
            start = i
        elif l > BLACK and start is not None:
            black_runs.append((start, i - 1))
            start = None
    if start is not None:
        black_runs.append((start, len(lums) - 1))
    runs = [r for r in black_runs if r[1] - r[0] >= 6]
    if not runs:
        print("no black stretch found; the transition was not captured")
        sys.exit(2)
    b_start, b_end = max(runs, key=lambda r: r[1] - r[0])
    black_frames = b_end - b_start + 1
    print(f"black_ms={black_frames / FPS * 1000:.0f}")

    rest = lums[b_end + 1:]
    if len(rest) < 5:
        print("the fade runs past the end of the capture")
        sys.exit(2)
    settled = max(rest)
    if settled <= BLACK:
        print("the page never came back up")
        sys.exit(2)
    print(f"first_frame_alpha={rest[0] / settled:.3f}")

    # The fade ends at the first frame within 2 % of the settled brightness.
    end = next((i for i, l in enumerate(rest) if l >= settled * 0.98), len(rest) - 1)
    print(f"fade_ms={(end + 1) / FPS * 1000:.0f}")


if __name__ == "__main__":
    main()
