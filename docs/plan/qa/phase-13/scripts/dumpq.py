#!/usr/bin/env python3
"""Dump queries for phase 13's rows (uiautomator XML).

  clear_rows <dump.xml> <x0> <x1> <y> [<y> ...]   prints the first y whose strip [x0,x1] x [y-4,y+4] meets no node that
                                                  carries text (a text-free strip, E2 / E1)
  text_nodes <dump.xml>                            every node with text: "text|l t r b"
  checker_rows <dump.xml> <fixW> <fixH>            the checker's square-centre rows inside app_list's bounds (the
                                                  fixture Crop-placed at scale 1 into the page, 4 squares across)
  bounds_of <dump.xml> <resource-id-prefix>        "l t r b" of every node whose id starts with the prefix
  start_gap <dump.xml>                             a y between Start's grid and its bottom row, clear of tiles
"""
import re
import sys


def nodes(path):
    xml = open(path, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r"<node[^>]*>", xml):
        s = m.group(0)
        t = re.search(r'text="([^"]*)"', s)
        b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        rid = re.search(r'resource-id="([^"]*)"', s)
        cd = re.search(r'content-desc="([^"]*)"', s)
        yield (t.group(1) if t else ""), (cd.group(1) if cd else ""), (rid.group(1) if rid else ""), tuple(map(int, b.groups())) if b else None


def main():
    cmd, path = sys.argv[1], sys.argv[2]
    if cmd == "clear_rows":
        x0, x1 = int(sys.argv[3]), int(sys.argv[4])
        texts = [b for t, cd, rid, b in nodes(path) if (t or cd) and b]
        for y in map(int, sys.argv[5:]):
            hit = any(not (b[2] < x0 or b[0] > x1 or b[3] < y - 4 or b[1] > y + 4) for b in texts)
            if not hit:
                print(y)
                return
    elif cmd in ("checker_rows", "bounds_of", "start_gap"):
        extra(cmd, path, sys.argv[3:])
    elif cmd == "text_nodes":
        for t, cd, rid, b in nodes(path):
            if t or cd:
                print("%s|%s" % (t or cd, " ".join(map(str, b)) if b else ""))


def page(path, rid):
    for t, cd, r, b in nodes(path):
        if r == rid and b:
            return b
    return None


def extra(cmd, path, args):
    if cmd == "checker_rows":
        fw, fh = int(args[0]), int(args[1])
        l, t, r, b = page(path, "app_list")
        w, h = r - l, b - t
        scale = max(w / fw, h / fh)
        sq = (fw // 4) * scale
        off = (fh * scale - h) / 2.0
        ys = []
        k = 0
        while True:
            y = t + (k * sq + sq / 2.0) - off
            if y > b - 10:
                break
            if y > t + 10:
                ys.append(int(round(y)))
            k += 1
        print(" ".join(map(str, ys)))
    elif cmd == "bounds_of":
        for t, cd, r, b in nodes(path):
            if r.startswith(args[0]) and b:
                print(" ".join(map(str, b)))
    elif cmd == "start_gap":
        grid = [b for t, cd, r, b in nodes(path) if r.startswith("tile:") and not r.startswith("tile:dock:") and b]
        dock = [b for t, cd, r, b in nodes(path) if r.startswith("tile:dock:") and b]
        top = max(bb[3] for bb in grid)
        bottom = min(bb[1] for bb in dock) if dock else top + 200
        print(int((top + bottom) / 2), top, bottom)


if __name__ == "__main__":
    main()
