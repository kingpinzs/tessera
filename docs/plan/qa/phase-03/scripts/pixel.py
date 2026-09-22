#!/usr/bin/env python3
"""One pixel's RGB from a screencap, as "r,g,b" — for the rows that measure a captured colour."""
import subprocess
import sys

png, x, y = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
try:
    from PIL import Image
except ImportError:
    # No Pillow: read the pixel with ffmpeg, which is already a dependency of the utterance builder.
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", png, "-vf", f"crop=1:1:{x}:{y}", "-f", "rawvideo",
         "-pix_fmt", "rgb24", "-"],
        capture_output=True,
    ).stdout
    if len(raw) < 3:
        print("?,?,?")
        sys.exit(1)
    print(f"{raw[0]},{raw[1]},{raw[2]}")
    sys.exit(0)

with Image.open(png) as im:
    r, g, b = im.convert("RGB").getpixel((x, y))
print(f"{r},{g},{b}")
