#!/usr/bin/env python3
"""Phase 13 measurement helpers (build task 7), all on real captures; single source for every row's numbers.

  edge <png> <y0> <y1> <xa> <xb>
      Edge spread (T13-12): the column mean of rows y0..y1 (>= 8 rows) is read from x = xa to x = xb, the two
      adjacent square CENTRES; the step's 10-90 % width is measured between those two plateaus (the row's own values,
      not 0 and 255). Prints: width_px plateau_a plateau_b (luma).
  patch <png> <x> <y> <w> <h>
      Mean R G B of a patch (acrylic-on reads are patch means, T13-17).
  pixel <png> <x> <y>
      One pixel's R G B.
  std <png> <x> <y> <w> <h>
      Per-pixel standard deviation of the patch's luma-free channel values (mean of the three channels' stds).
  diff <png-a> <png-b> <x> <y> <w> <h>
      Mean absolute per-channel difference of two captures over the patch, and the max.
  sharpest <png> <y> <x0> <x1>
      Along row y (mean of rows y-1..y+1), the steepest step in [x0, x1] and its 10-90 % width between the values 3 px
      before it and the extreme within 4 px after it: "width x" (text and unblurred edges read <= 2 px).
  expected_width <radius_px>
      2.563 * sigma, sigma = 0.57735 * r + 0.5 (HWUI's conversion).
"""
import sys

from PIL import Image


def load(path):
    return Image.open(path).convert("RGB")


def luma(px):
    r, g, b = px
    return (r + g + b) / 3.0


def column_mean(img, y0, y1, xa, xb):
    step = 1 if xb >= xa else -1
    xs = list(range(xa, xb + step, step))
    px = img.load()
    out = []
    for x in xs:
        s = 0.0
        for y in range(y0, y1 + 1):
            s += luma(px[x, y])
        out.append(s / (y1 - y0 + 1))
    return xs, out


def edge(path, y0, y1, xa, xb):
    img = load(path)
    xs, prof = column_mean(img, y0, y1, xa, xb)
    a, b = prof[0], prof[-1]
    lo, hi = min(a, b), max(a, b)
    if hi - lo < 1.0:
        return None, a, b
    rising = b > a
    seq = prof if rising else prof[::-1]
    t10 = lo + 0.1 * (hi - lo)
    t90 = lo + 0.9 * (hi - lo)

    def cross(t):
        for i in range(1, len(seq)):
            if seq[i - 1] < t <= seq[i]:
                # linear interpolation between samples
                return (i - 1) + (t - seq[i - 1]) / (seq[i] - seq[i - 1])
        return None

    c10, c90 = cross(t10), cross(t90)
    if c10 is None or c90 is None:
        return None, a, b
    return abs(c90 - c10), a, b


def patch(path, x, y, w, h):
    img = load(path)
    px = img.load()
    n = w * h
    s = [0, 0, 0]
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            p = px[xx, yy]
            for c in range(3):
                s[c] += p[c]
    return [v / n for v in s]


def std(path, x, y, w, h):
    img = load(path)
    px = img.load()
    vals = [[], [], []]
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            p = px[xx, yy]
            for c in range(3):
                vals[c].append(p[c])
    out = []
    for c in range(3):
        m = sum(vals[c]) / len(vals[c])
        out.append((sum((v - m) ** 2 for v in vals[c]) / len(vals[c])) ** 0.5)
    return sum(out) / 3.0


def diff(pa, pb, x, y, w, h):
    a, b = load(pa).load(), load(pb).load()
    tot, mx, n = 0.0, 0, 0
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            for c in range(3):
                d = abs(a[xx, yy][c] - b[xx, yy][c])
                tot += d
                mx = max(mx, d)
                n += 1
    return tot / n, mx


def sharpest(path, y, x0, x1):
    xs, prof = column_mean(load(path), y - 1, y + 1, x0, x1)
    best, bi = 0.0, None
    for i in range(3, len(prof) - 5):
        d = abs(prof[i + 1] - prof[i])
        if d > best:
            best, bi = d, i
    if bi is None:
        return None, None
    rising = prof[bi + 1] > prof[bi]
    lo = prof[bi - 3]
    win = prof[bi + 1:bi + 5]
    hi = max(win) if rising else min(win)
    seg = prof[bi - 3:bi + 5]
    if not rising:
        seg = [-v for v in seg]
        lo, hi = -lo, -hi
    t10, t90 = lo + 0.1 * (hi - lo), lo + 0.9 * (hi - lo)

    def cross(t):
        for i in range(1, len(seg)):
            if seg[i - 1] < t <= seg[i]:
                return (i - 1) + (t - seg[i - 1]) / (seg[i] - seg[i - 1])
        return None

    c10, c90 = cross(t10), cross(t90)
    if c10 is None or c90 is None:
        return None, xs[bi]
    return abs(c90 - c10), xs[bi]


def main():
    cmd = sys.argv[1]
    a = sys.argv[2:]
    if cmd == "edge":
        w, pa, pb = edge(a[0], *map(int, a[1:5]))
        print("none" if w is None else "%.1f" % w, "%.1f" % pa, "%.1f" % pb)
    elif cmd == "patch":
        print(" ".join("%.1f" % v for v in patch(a[0], *map(int, a[1:5]))))
    elif cmd == "pixel":
        print(" ".join(str(v) for v in load(a[0]).load()[int(a[1]), int(a[2])]))
    elif cmd == "std":
        print("%.2f" % std(a[0], *map(int, a[1:5])))
    elif cmd == "diff":
        m, mx = diff(a[0], a[1], *map(int, a[2:6]))
        print("%.2f %d" % (m, mx))
    elif cmd == "sharpest":
        w, x = sharpest(a[0], *map(int, a[1:4]))
        print("none" if w is None else "%.1f" % w, x)
    elif cmd == "expected_width":
        r = float(a[0])
        print("%.1f" % (2.563 * (0.57735 * r + 0.5)))
    else:
        sys.exit("unknown command " + cmd)


if __name__ == "__main__":
    main()
