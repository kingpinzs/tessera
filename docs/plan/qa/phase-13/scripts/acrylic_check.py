#!/usr/bin/env python3
"""Phase 13's acrylic-on comparisons against the host oracle (acrylic_expect.py's B), on real captures (T13-2, T13-17).

  flat [--size=N] <closed.png> <Bm:r,g,b> <radius_px> <L:l,t,r,b> <region:l,t,r,b> [<exclude:l,t,r,b> ...]
      The measured-fill precondition (preamble, Q1 A): the N x N px patch (default 10) inside <region>, clear of every <exclude>
      box, whose oracle B (the live backdrop: <closed.png> masked to L, blurred at HWUI's sigma) is within +-1 of Bm in
      every channel at EVERY pixel. Prints "x y maxdev" (the patch's top-left and its worst per-pixel |B - Bm|) of the
      qualifying patch with the smallest maxdev, or nothing when no patch qualifies (the row fails loudly on that).
  patch <judged.png> <x> <y> <w> <h>
      Mean R G B of the patch in the capture under judgement.
  profile <judged.png> <closed.png> <T:r,g,b> <radius_px> <L:l,t,r,b> <y0> <y1> <x0> <x1>
      Column means (rows y0..y1) of the judged capture against 0.8*T + 0.2*B from the closed capture, per x and per
      channel. Prints "worst_abs_diff at_x judged(r,g,b) expected(r,g,b)" and writes every column to stderr.
  patches <judged.png> <closed.png> <T:r,g,b> <radius_px> <L:l,t,r,b> <w> <h> <x,y> [<x,y> ...]
      Patch means of the judged capture against 0.8*T + 0.2*B at each listed top-left; prints the worst
      "abs_diff x,y judged expected" and every patch to stderr.
  find_color <png> <r,g,b> <region:l,t,r,b>
      The bounding box "l t r b" of the pixels within +-2 of the colour inside the region (a node with no dump entry,
      e.g. a playlist row's accent square, read from a capture with nothing over it).
  opaque <judged.png> <x> <y> <w> <h> <r,g,b>
      Acrylic off / opaque fills: the worst per-pixel, per-channel |pixel - rgb| over the patch (single pixels are
      compared only here, T13-17).
"""
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, __import__("os").path.dirname(__file__))
from acrylic_expect import ALPHA, live_backdrop  # noqa: E402


def box(s):
    return [int(v) for v in s.split(",")]


def rgb(s):
    return np.array([float(v) for v in s.split(",")])


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.float64)


def main():
    cmd = sys.argv[1]
    if cmd == "flat":
        args = sys.argv[2:]
        n = 10
        if args[0].startswith("--size="):
            n = int(args.pop(0).split("=")[1])
        closed, bm, r, L, region = args[0], rgb(args[1]), float(args[2]), box(args[3]), box(args[4])
        excl = [box(a) for a in args[5:]]
        B = live_backdrop(closed, L, r)
        dev = np.abs(B - bm).max(axis=2)
        l, t, rr, b = region
        best = None
        for y in range(t, b - n + 1, 2):
            for x in range(l, rr - n + 1, 2):
                if any(not (x + n <= e[0] or x >= e[2] or y + n <= e[1] or y >= e[3]) for e in excl):
                    continue
                m = dev[y:y + n, x:x + n].max()
                if m <= 1.0 and (best is None or m < best[2]):
                    best = (x, y, m)
        if best:
            print(best[0], best[1], "%.2f" % best[2])
    elif cmd == "patch":
        img = load(sys.argv[2])
        x, y, w, h = map(int, sys.argv[3:7])
        print(" ".join("%.1f" % v for v in img[y:y + h, x:x + w].reshape(-1, 3).mean(axis=0)))
    elif cmd == "profile":
        judged, closed, T, r, L = sys.argv[2], sys.argv[3], rgb(sys.argv[4]), float(sys.argv[5]), box(sys.argv[6])
        y0, y1, x0, x1 = map(int, sys.argv[7:11])
        E = ALPHA * T + (1 - ALPHA) * live_backdrop(closed, L, r)
        J = load(judged)
        je = J[y0:y1 + 1, x0:x1 + 1].mean(axis=0)
        ee = E[y0:y1 + 1, x0:x1 + 1].mean(axis=0)
        d = np.abs(je - ee)
        i = int(d.max(axis=1).argmax())
        for k in range(je.shape[0]):
            sys.stderr.write("%d %s %s\n" % (x0 + k, " ".join("%.1f" % v for v in je[k]), " ".join("%.1f" % v for v in ee[k])))
        print("%.1f" % d.max(), x0 + i, ",".join("%.1f" % v for v in je[i]), ",".join("%.1f" % v for v in ee[i]))
    elif cmd == "patches":
        judged, closed, T, r, L = sys.argv[2], sys.argv[3], rgb(sys.argv[4]), float(sys.argv[5]), box(sys.argv[6])
        w, h = int(sys.argv[7]), int(sys.argv[8])
        E = ALPHA * T + (1 - ALPHA) * live_backdrop(closed, L, r)
        J = load(judged)
        worst = None
        for xy in sys.argv[9:]:
            x, y = map(int, xy.split(","))
            jm = J[y:y + h, x:x + w].reshape(-1, 3).mean(axis=0)
            em = E[y:y + h, x:x + w].reshape(-1, 3).mean(axis=0)
            d = float(np.abs(jm - em).max())
            sys.stderr.write("%d,%d judged %s expected %s |d| %.1f\n" % (x, y, ",".join("%.1f" % v for v in jm), ",".join("%.1f" % v for v in em), d))
            if worst is None or d > worst[0]:
                worst = (d, xy, jm, em)
        print("%.1f" % worst[0], worst[1], ",".join("%.1f" % v for v in worst[2]), ",".join("%.1f" % v for v in worst[3]))
    elif cmd == "find_color":
        img = load(sys.argv[2])
        want = rgb(sys.argv[3])
        l, t, rr, b = box(sys.argv[4])
        sub = np.abs(img[t:b, l:rr] - want).max(axis=2) <= 2
        ys, xs = np.nonzero(sub)
        if len(xs):
            print(l + xs.min(), t + ys.min(), l + xs.max() + 1, t + ys.max() + 1)
    elif cmd == "opaque":
        img = load(sys.argv[2])
        x, y, w, h = map(int, sys.argv[3:7])
        want = rgb(sys.argv[7])
        print("%.0f" % np.abs(img[y:y + h, x:x + w] - want).max())
    else:
        sys.exit("unknown command " + cmd)


if __name__ == "__main__":
    main()
