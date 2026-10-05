#!/usr/bin/env python3
"""A PNG's pixel, or whether it is within a tolerance of an expected colour.

usage: ppx.py <png> <x> <y>                      -> "r,g,b"
       ppx.py near <r,g,b> <r,g,b> <tolerance>   -> "yes" / "no"
"""
import sys

if sys.argv[1] == "near":
    a = [int(v) for v in sys.argv[2].split(",")]
    b = [int(v) for v in sys.argv[3].split(",")]
    print("yes" if len(a) == 3 and len(b) == 3 and all(abs(x - y) <= int(sys.argv[4]) for x, y in zip(a, b)) else "no")
else:
    from PIL import Image
    r, g, b = Image.open(sys.argv[1]).convert("RGB").getpixel((int(sys.argv[2]), int(sys.argv[3])))
    print("%d,%d,%d" % (r, g, b))
