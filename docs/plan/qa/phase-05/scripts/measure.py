#!/usr/bin/env python3
"""Phase 05 E3: measure the running keyboard, from its own dump and from real screen pixels.

Subcommands (every number printed is MEASURED; nothing here knows what the answer should be):

  geom DUMP.xml SX              key geometry from the IME window's dump bounds, in phys (px / SX)
                                -> TSV lines: name <TAB> actual
  ink PNG X0 Y0 X1 Y1 [THR]     bounding box of pixels brighter than THR (luminance) inside a box
                                -> "top left bottom right height width" in px, or "none"
  grey PNG X0 Y0 X1 Y1 LO HI    bounding box of pixels whose luminance is in [LO, HI]
  px PNG X Y                    one pixel -> "r g b"
  segments PNG Y0 Y1 X0 X1 THR MERGE
                                horizontal ink runs along a band (columns with any pixel > THR),
                                runs closer than MERGE px joined -> "x0-x1 x0-x1 ..."
  accent PNG X0 Y0 X1 Y1        bounding box of accent-blue pixels (b > 150, b - r > 60)
"""
import re
import sys

from PIL import Image


def bounds(xml, rid):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(rid), xml)
    if not m:
        m = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"[^>]*resource-id="%s"' % re.escape(rid), xml)
    return tuple(int(v) for v in m.groups()) if m else None


def geom(path, sx):
    sx = float(sx)
    xml = open(path, encoding="utf-8", errors="replace").read()
    k = lambda i: bounds(xml, "kb_key_" + i)
    out = []
    ph = lambda v: v / sx
    row1 = [k(c) for c in "qwertyuiop"]
    row2 = [k(c) for c in "asdfghjkl"]
    row3 = [k(c) for c in "zxcvbnm"]
    panel = bounds(xml, "kb_panel")
    strip = bounds(xml, "kb_strip")
    width = panel[2] - panel[0]
    for c, b in zip("qwertyuiop", row1):
        out.append(("row1 %s width" % c, ph(b[2] - b[0])))
    for (a, b), n in zip(zip(row1, row1[1:]), range(9)):
        out.append(("row1 gap %d" % n, ph(b[0] - a[2])))
    out.append(("row1 left margin", ph(row1[0][0] - panel[0])))
    out.append(("row1 right margin", ph(panel[2] - row1[-1][2])))
    out.append(("column pitch", ph((row1[-1][0] - row1[0][0]) / 9)))
    for c, b in zip("asdfghjkl", row2):
        out.append(("row2 %s width" % c, ph(b[2] - b[0])))
    for (a, b), n in zip(zip(row2, row2[1:]), range(8)):
        out.append(("row2 gap %d" % n, ph(b[0] - a[2])))
    out.append(("row2 left inset", ph(row2[0][0] - panel[0])))
    out.append(("row2 right inset", ph(panel[2] - row2[-1][2])))
    out.append(("shift width", ph(k("shift")[2] - k("shift")[0])))
    out.append(("backspace width", ph(k("bksp")[2] - k("bksp")[0])))
    for c, b in zip("zxcvbnm", row3):
        out.append(("row3 %s width" % c, ph(b[2] - b[0])))
    for c, above in zip("zxcvbnm", "sdfghjk"):
        out.append(("row3 %s left vs %s left" % (c, above), ph(k(c)[0] - k(above)[0])))
    for name in ("sym", "emoji", "comma", "space", "period", "enter", "dotcom"):
        b = k(name)
        if b:
            out.append(("row4 %s width" % name, ph(b[2] - b[0])))
    heights = [b[3] - b[1] for b in row1 + row2 + row3 if b]
    out.append(("key height min", ph(min(heights))))
    out.append(("key height max", ph(max(heights))))
    out.append(("row pitch", ph((k("space")[1] - row1[0][1]) / 3)))
    out.append(("vertical gap", ph(row2[0][1] - row1[0][3])))
    out.append(("bottom margin", ph(panel[3] - k("space")[3])))
    out.append(("key block", ph(panel[3] - row1[0][1])))
    out.append(("strip height px", strip[3] - strip[1]))
    dot = bounds(xml, "kb_cursor_dot")
    if dot:
        out.append(("dot centre x", ph((dot[0] + dot[2]) / 2)))
        out.append(("dot above nav bar", ph(panel[3] - (dot[1] + dot[3]) / 2)))
    out.append(("panel width px", width))
    for name, v in out:
        print("%s\t%.2f" % (name, v))


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def box(path, x0, y0, x1, y1, test):
    im = Image.open(path).convert("RGB")
    px = im.load()
    top = left = 10 ** 9
    bottom = right = -1
    for y in range(int(y0), int(y1)):
        for x in range(int(x0), int(x1)):
            if test(px[x, y]):
                top, bottom = min(top, y), max(bottom, y)
                left, right = min(left, x), max(right, x)
    if bottom < 0:
        print("none")
    else:
        print(top, left, bottom, right, bottom - top + 1, right - left + 1)


def segments(path, y0, y1, x0, x1, thr, merge):
    im = Image.open(path).convert("RGB")
    px = im.load()
    cols = [any(lum(px[x, y]) > thr for y in range(y0, y1)) for x in range(x0, x1)]
    runs = []
    start = None
    for i, c in enumerate(cols + [False]):
        if c and start is None:
            start = i
        if not c and start is not None:
            runs.append([start + x0, i - 1 + x0])
            start = None
    joined = []
    for r in runs:
        if joined and r[0] - joined[-1][1] <= merge:
            joined[-1][1] = r[1]
        else:
            joined.append(r)
    print(" ".join("%d-%d" % (a, b) for a, b in joined))


def main():
    cmd = sys.argv[1]
    a = sys.argv[2:]
    if cmd == "geom":
        geom(a[0], a[1])
    elif cmd == "ink":
        thr = float(a[5]) if len(a) > 5 else 150
        box(a[0], *map(int, a[1:5]), test=lambda p: lum(p) > thr)
    elif cmd == "grey":
        lo, hi = float(a[5]), float(a[6])
        box(a[0], *map(int, a[1:5]), test=lambda p: lo <= lum(p) <= hi)
    elif cmd == "accent":
        box(a[0], *map(int, a[1:5]), test=lambda p: p[2] > 150 and p[2] - p[0] > 60)
    elif cmd == "px":
        im = Image.open(a[0]).convert("RGB")
        print(*im.getpixel((int(a[1]), int(a[2]))))
    elif cmd == "segments":
        segments(a[0], int(a[1]), int(a[2]), int(a[3]), int(a[4]), float(a[5]), int(a[6]))
    else:
        sys.exit("unknown subcommand " + cmd)


if __name__ == "__main__":
    main()
