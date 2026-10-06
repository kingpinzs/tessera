#!/usr/bin/env python3
"""How much two screenshots differ inside a box: the share of pixels (in percent, two decimals) whose largest channel
difference is at least 24 of 255.

usage: frame_diff.py <a.png> <b.png> <left> <top> <right> <bottom>
"""
import sys
from PIL import Image, ImageChops

a = Image.open(sys.argv[1]).convert("RGB")
b = Image.open(sys.argv[2]).convert("RGB")
box = tuple(int(v) for v in sys.argv[3:7])
a, b = a.crop(box), b.crop(box)
diff = ImageChops.difference(a, b)
r, g, bl = diff.split()
peak = ImageChops.lighter(ImageChops.lighter(r, g), bl)
hist = peak.histogram()
changed = sum(hist[24:])
print("%.2f" % (100.0 * changed / max(1, a.size[0] * a.size[1])))
