#!/usr/bin/env python3
"""Geometry helpers for the Calendar dev sessions: a node's bounds in epx (1080 px = 360 epx), and a pixel of a PNG.

    geo.py bounds <dump.xml> <resource-id> [n]      -> "left top right bottom" in epx (the n-th match, 0-based)
    geo.py textbounds <dump.xml> <text> [n]         -> the same, for the n-th node with that exact text
    geo.py pixel <shot.png> <x-epx> <y-epx>         -> "r g b"
"""
import re
import sys

PX = 3.0


def nodes(xml):
    return re.findall(r'<node[^>]*>', xml)


def fmt(m):
    return " ".join("%.2f" % (int(v) / PX) for v in m.groups())


def main():
    cmd = sys.argv[1]
    if cmd in ("bounds", "textbounds"):
        xml = open(sys.argv[2], encoding="utf-8", errors="replace").read()
        key = 'resource-id="%s"' % sys.argv[3] if cmd == "bounds" else 'text="%s"' % sys.argv[3]
        n = int(sys.argv[4]) if len(sys.argv) > 4 else 0
        hits = [s for s in nodes(xml) if key in s]
        if n >= len(hits):
            print("")
            return
        m = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', hits[n])
        print(fmt(m) if m else "")
    elif cmd == "pixel":
        from PIL import Image
        im = Image.open(sys.argv[2]).convert("RGB")
        x, y = int(round(float(sys.argv[3]) * PX)), int(round(float(sys.argv[4]) * PX))
        print(" ".join(str(c) for c in im.getpixel((min(x, im.width - 1), min(y, im.height - 1)))))


if __name__ == "__main__":
    main()
