#!/usr/bin/env python3
"""e7_geom.py DUMP TILE_ID REST_FILE — phase 11 E7's geometry facts for one held burst, one "key value" per line.

REST_FILE holds `i l t r b` rows (q.sh rest_lines). Reported:
  tile            the held tile's drawn rect (dump)
  sat_i / lab_i   each satellite's and label's rect (dump)
  size_i          satellite width,height
  rest_vs_dump    max |rest= − dump| over every satellite edge (px)
  on_tile         how many satellite or label rects intersect the held tile (must be 0)
  arrangement     corner (each satellite diagonally outside its corner) | line_above | line_below | other
  standoff_i      corner only: the inner corner's distance from the tile corner on x,y (px)
  label_side_i    above | below (label relative to its satellite)
  line_gap        line only: gap between the tile edge and the row (px)
  row_gaps        line only: gaps between neighbours (px)
  extent          min_left,min_top,max_right,max_bottom over satellites and labels
"""
import re
import sys


def rect(s, rid):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(rid), s)
    return tuple(map(int, m.groups())) if m else None


def inter(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def main():
    s = open(sys.argv[1]).read()
    t = rect(s, sys.argv[2])
    rest = {}
    for line in open(sys.argv[3]):
        p = line.split()
        if len(p) == 5:
            rest[int(p[0])] = tuple(map(int, p[1:]))
    print("tile", *(t or ("none",)))
    sats, labs = {}, {}
    for i in range(4):
        q, l = rect(s, "quick_sat:%d" % i), rect(s, "quick_sat_label:%d" % i)
        if q:
            sats[i], labs[i] = q, l
            print("sat_%d" % i, *q)
            print("size_%d" % i, q[2] - q[0], q[3] - q[1])
            if l:
                print("label_side_%d" % i, "above" if l[3] <= q[1] + 1 else "below" if l[1] >= q[3] - 1 else "other")
    if not t or not sats:
        print("arrangement none")
        return
    diffs = [abs(a - b) for i in sats if i in rest for a, b in zip(sats[i], rest[i])]
    print("rest_vs_dump", max(diffs) if diffs and len([i for i in sats if i in rest]) == len(sats) else "missing")
    print("on_tile", sum(1 for i in sats if inter(sats[i], t)) + sum(1 for i in labs if labs[i] and inter(labs[i], t)))
    # Corner: every satellite on its own side of the tile horizontally, the top pair above the bottom pair. A corner
    # satellite may have been clamped inward (Decisions "Clamping") — then it is not diagonal, and says so.
    sides = all(((q[2] <= t[0]) if i % 2 == 0 else (q[0] >= t[2])) for i, q in sats.items())
    order = all(sats[i][1] < sats[j][1] for i in (0, 1) for j in (2, 3) if i in sats and j in sats)
    corner = sides and order
    diagonal = all(((q[3] <= t[1]) if i < 2 else (q[1] >= t[3])) for i, q in sats.items())
    tops = {q[1] for q in sats.values()}
    if corner and len(sats) > 1:
        print("arrangement corner")
        print("diagonal", "yes" if diagonal else "clamped")
        for i, q in sats.items():
            dx = (t[0] - q[2]) if i % 2 == 0 else (q[0] - t[2])
            dy = (t[1] - q[3]) if i < 2 else (q[1] - t[3])
            print("standoff_%d" % i, dx, dy)
    elif max(tops) - min(tops) <= 1:
        above = all(q[3] <= t[1] for q in sats.values())
        print("arrangement", "line_above" if above else "line_below" if all(q[1] >= t[3] for q in sats.values()) else "other")
        print("line_gap", (t[1] - max(q[3] for q in sats.values())) if above else (min(q[1] for q in sats.values()) - t[3]))
        xs = sorted(sats.values())
        print("row_gaps", *[b[0] - a[2] for a, b in zip(xs, xs[1:])])
    else:
        print("arrangement other")
    allr = list(sats.values()) + [l for l in labs.values() if l]
    print("extent", min(r[0] for r in allr), min(r[1] for r in allr), max(r[2] for r in allr), max(r[3] for r in allr))


if __name__ == "__main__":
    main()
