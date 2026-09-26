#!/usr/bin/env python3
"""E7's pixel reads for the two lights (T13-1, T13-13, T11-31), single pixels against SAME-pixel reference captures, so
the material's deterministic noise cancels (preamble's E7 exception).

  at <U.png> <J.png> <held:u|p15> <x> <y> <a>
      P = the pixel's held look without the lights: U itself ("u", items that draw nothing while held) or
      U + 0.15*(255 - U) ("p15", the app-list and Music menu items' ROW_PRESS_ALPHA fill). Expected = P + a*(255 - P)
      (white at alpha a over P). Prints "worst_channel_abs_diff judged(r,g,b) expected(r,g,b)".
"""
import sys

from PIL import Image


def px(path, x, y):
    return Image.open(path).convert("RGB").getpixel((x, y))


def main():
    if sys.argv[1] != "at":
        sys.exit("unknown command " + sys.argv[1])
    u, j, held = sys.argv[2], sys.argv[3], sys.argv[4]
    x, y, a = int(sys.argv[5]), int(sys.argv[6]), float(sys.argv[7])
    U = px(u, x, y)
    J = px(j, x, y)
    P = [c + 0.15 * (255 - c) for c in U] if held == "p15" else list(U)
    E = [p + a * (255 - p) for p in P]
    d = max(abs(jc - ec) for jc, ec in zip(J, E))
    print("%.1f" % d, "(%d,%d,%d)" % J, "(%.1f,%.1f,%.1f)" % tuple(E))


if __name__ == "__main__":
    main()
