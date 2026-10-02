#!/usr/bin/env python3
"""Phase 16 QA, the Calendar rows: measurements on the DRAWN pixels of a screencap (1080 px = 360 epx on this AVD).

A dump's bounds are a touch target (a text field's tag node reads 48 epx tall), so every geometry clause that names a
drawn box, bar, rule, colour or cap height is read here from the PNG. All coordinates on the command line are DEVICE
PIXELS unless a sub-command says epx; every result that is a length is printed in epx.

    pixel  <png> <x> <y>                         -> "r g b"
    count  <png> <x1> <y1> <x2> <y2> <r> <g> <b> <tol>
                                                 -> how many pixels of the box are within tol of the colour
    red    <png> <x1> <y1> <x2> <y2>             -> how many pixels of the box are red-dominant (a "red" glyph)
    box    <png> <x1> <y1> <x2> <y2> <r> <g> <b> <tol>
                                                 -> "left top right bottom" (px, right/bottom exclusive) of the pixels
                                                    within tol of the colour inside the search box, or "" when none
    ink    <png> <x1> <y1> <x2> <y2> <r> <g> <b> <tol>
                                                 -> the same box for the pixels that DIFFER from the colour by more
                                                    than tol (the ink on a known background: a glyph, a cap)
    runs   <png> <h|v> <fixed> <from> <to> <r> <g> <b> <tol>
                                                 -> the runs of matching pixels along a row (h: y fixed) or a column
                                                    (v: x fixed), "start-end" (px, end exclusive) space-separated
    mode   <png> <x1> <y1> <x2> <y2>             -> the most common colour of the box, "r g b"
    inkbox <png> <x1> <y1> <x2> <y2> [thr]       -> "left top right bottom" (px) of the ink: the pixels at least half
                                                    way (or thr levels) from the box's most common colour (its
                                                    background) to its core ink colour — R11's half-level crossing
    core   <png> <x1> <y1> <x2> <y2>             -> the ink's core colour: the pixel farthest from the box's most
                                                    common colour, "r g b"
"""
import sys

from PIL import Image
import numpy as np

PX = 3.0


def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int16)


def near(a, rgb, tol):
    return (np.abs(a - np.array(rgb, dtype=np.int16)).max(axis=2) <= tol)


def crop(a, x1, y1, x2, y2):
    h, w = a.shape[:2]
    x1, x2 = max(0, x1), min(w, x2)
    y1, y2 = max(0, y1), min(h, y2)
    return a[y1:y2, x1:x2], x1, y1


def bbox(mask, ox, oy):
    ys, xs = np.nonzero(mask)
    if len(xs) == 0:
        return ""
    return "%d %d %d %d" % (xs.min() + ox, ys.min() + oy, xs.max() + ox + 1, ys.max() + oy + 1)


def main():
    cmd, png = sys.argv[1], sys.argv[2]
    a = load(png)
    n = [int(round(float(v))) for v in sys.argv[3:] if v not in ("h", "v")]
    if cmd == "pixel":
        x, y = min(n[0], a.shape[1] - 1), min(n[1], a.shape[0] - 1)
        print(" ".join(str(int(c)) for c in a[y, x]))
    elif cmd == "count":
        c, _, _ = crop(a, *n[:4])
        print(int(near(c, n[4:7], n[7]).sum()))
    elif cmd == "red":
        c, _, _ = crop(a, *n[:4])
        r, g, b = c[:, :, 0], c[:, :, 1], c[:, :, 2]
        print(int(((r > 150) & (g < 110) & (b < 110) & (r - np.maximum(g, b) > 60)).sum()))
    elif cmd == "box":
        c, ox, oy = crop(a, *n[:4])
        print(bbox(near(c, n[4:7], n[7]), ox, oy))
    elif cmd == "ink":
        c, ox, oy = crop(a, *n[:4])
        print(bbox(~near(c, n[4:7], n[7]), ox, oy))
    elif cmd == "runs":
        axis = sys.argv[3]
        fixed, lo, hi = n[0], n[1], n[2]
        line = a[fixed, lo:hi] if axis == "h" else a[lo:hi, fixed]
        m = (np.abs(line - np.array(n[3:6], dtype=np.int16)).max(axis=1) <= n[6])
        out, start = [], None
        for i, v in enumerate(m):
            if v and start is None:
                start = i
            if not v and start is not None:
                out.append("%d-%d" % (start + lo, i + lo)); start = None
        if start is not None:
            out.append("%d-%d" % (start + lo, len(m) + lo))
        print(" ".join(out))
    elif cmd == "mode":
        c, _, _ = crop(a, *n[:4])
        flat = c.reshape(-1, 3)
        vals, counts = np.unique(flat, axis=0, return_counts=True)
        print(" ".join(str(int(v)) for v in vals[counts.argmax()]))
    elif cmd in ("inkbox", "core"):
        c, ox, oy = crop(a, *n[:4])
        flat = c.reshape(-1, 3)
        vals, counts = np.unique(flat, axis=0, return_counts=True)
        bg = vals[counts.argmax()]
        dist = np.abs(c - bg).max(axis=2)
        if dist.max() == 0:
            print("")
        elif cmd == "core":
            y, x = np.unravel_index(dist.argmax(), dist.shape)
            print(" ".join(str(int(v)) for v in c[y, x]))
        else:
            thr = n[4] if len(n) > 4 else max(1, int(dist.max()) // 2)
            print(bbox(dist >= thr, ox, oy))
    else:
        sys.exit("unknown command " + cmd)


if __name__ == "__main__":
    main()
