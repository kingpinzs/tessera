#!/usr/bin/env python3
"""Phase 15 E13 (and the Calculator edge cases): pixel measurements on a screencap, for the geometry the dump cannot
give — glyph ink boxes, rules, fills. Every value is printed in PIXELS; the driver converts to epx (1080 / 360 = 3).

  calc_geo.py ink  PNG x1 y1 x2 y2 [thr] [chan]   ink box of pixels with channel >= thr (default 128, chan lum|r|g|b)
                                                  inside [x1,x2) x [y1,y2): "left top right bottom w h" (inclusive
                                                  edges; w = right - left + 1), or "" when there is none
  calc_geo.py rows PNG x1 y1 x2 y2 [thr] [chan]   the runs of rows holding ink in that region: "top-bottom ..." (px)
  calc_geo.py px   PNG x y                        "r,g,b"
  calc_geo.py scan PNG y x1 x2 r,g,b tol          along row y over [x1,x2): the number of pixels farther than tol from
                                                  r,g,b on any channel, then the first such x and its colour
  calc_geo.py vscan PNG x y1 y2 r,g,b tol         the same down column x over [y1,y2)
  calc_geo.py maxlum PNG x1 y1 x2 y2              the brightest pixel's min(r,g,b) in the region
  calc_geo.py inkcolor PNG x1 y1 x2 y2            the most saturated bright pixel's "r,g,b" in the region (accent text)
"""
import sys

import numpy as np
from PIL import Image


def load(png):
    return np.asarray(Image.open(png).convert("RGB")).astype(int)


def channel(a, chan):
    if chan == "lum":
        return a.min(axis=2)  # white ink on a dark ground: every channel high
    return a[:, :, {"r": 0, "g": 1, "b": 2}[chan]]


def ink_mask(a, x1, y1, x2, y2, thr, chan):
    region = a[y1:y2, x1:x2]
    return channel(region, chan) >= thr


def cmd_ink(a, x1, y1, x2, y2, thr=128, chan="lum"):
    m = ink_mask(a, x1, y1, x2, y2, thr, chan)
    ys, xs = np.nonzero(m)
    if len(xs) == 0:
        print("")
        return
    left, right, top, bottom = x1 + xs.min(), x1 + xs.max(), y1 + ys.min(), y1 + ys.max()
    print(f"{left} {top} {right} {bottom} {right - left + 1} {bottom - top + 1}")


def cmd_rows(a, x1, y1, x2, y2, thr=128, chan="lum"):
    m = ink_mask(a, x1, y1, x2, y2, thr, chan).any(axis=1)
    runs = []
    start = None
    for i, on in enumerate(list(m) + [False]):
        if on and start is None:
            start = i
        elif not on and start is not None:
            runs.append(f"{y1 + start}-{y1 + i - 1}")
            start = None
    print(" ".join(runs))


def off_count(pixels, rgb, tol):
    d = np.abs(pixels - np.array(rgb)).max(axis=1)
    bad = np.nonzero(d > tol)[0]
    return bad


def cmd_scan(a, y, x1, x2, rgb, tol):
    bad = off_count(a[y, x1:x2], rgb, tol)
    if len(bad) == 0:
        print("0")
    else:
        x = x1 + bad[0]
        print(f"{len(bad)} first x={x} {','.join(map(str, a[y, x]))}")


def cmd_vscan(a, x, y1, y2, rgb, tol):
    bad = off_count(a[y1:y2, x], rgb, tol)
    if len(bad) == 0:
        print("0")
    else:
        y = y1 + bad[0]
        print(f"{len(bad)} first y={y} {','.join(map(str, a[y, x]))}")


def cmd_maxlum(a, x1, y1, x2, y2):
    print(int(a[y1:y2, x1:x2].min(axis=2).max()))


def cmd_inkcolor(a, x1, y1, x2, y2):
    region = a[y1:y2, x1:x2].reshape(-1, 3)
    sat = region.max(axis=1) - region.min(axis=1)
    bright = region.max(axis=1)
    score = sat * 2 + bright
    i = int(score.argmax())
    print(",".join(map(str, region[i])))


def main():
    cmd, png = sys.argv[1], sys.argv[2]
    a = load(png)
    args = sys.argv[3:]
    if cmd in ("ink", "rows"):
        x1, y1, x2, y2 = map(int, args[:4])
        thr = int(args[4]) if len(args) > 4 else 128
        chan = args[5] if len(args) > 5 else "lum"
        (cmd_ink if cmd == "ink" else cmd_rows)(a, x1, y1, x2, y2, thr, chan)
    elif cmd == "px":
        x, y = map(int, args[:2])
        print(",".join(map(str, a[y, x])))
    elif cmd == "scan":
        y, x1, x2 = map(int, args[:3])
        rgb = list(map(int, args[3].split(",")))
        cmd_scan(a, y, x1, x2, rgb, int(args[4]))
    elif cmd == "vscan":
        x, y1, y2 = map(int, args[:3])
        rgb = list(map(int, args[3].split(",")))
        cmd_vscan(a, x, y1, y2, rgb, int(args[4]))
    elif cmd == "maxlum":
        cmd_maxlum(a, *map(int, args[:4]))
    elif cmd == "inkcolor":
        cmd_inkcolor(a, *map(int, args[:4]))
    else:
        sys.exit(f"unknown command {cmd}")


if __name__ == "__main__":
    main()
