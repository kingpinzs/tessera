#!/usr/bin/env python3
"""Phase 15 E0 / E1: what each Start tile in a uiautomator dump carries, read from the tile's own nodes.

Usage: tiles.py <dump.xml> [tile-id ...]
Prints one line per `tile:<id>` node (only the ids named, when any are):
  <id> <TAB> x1,y1,x2,y2 <TAB> w=<px> h=<px> <TAB> controls=<yes|no> <TAB> badge=<yes|no> <TAB> texts=<t1|t2|...>
`controls` is a `tile_controls:<id>` node anywhere in the dump (the now-playing transport strip), `badge` a
`badge:<id>` node (the notification count), `texts` every non-empty text / content-desc inside the tile's subtree.
"""
import re, sys, xml.etree.ElementTree as ET


def main():
    root = ET.parse(sys.argv[1]).getroot()
    want = set(sys.argv[2:])
    ids = {n.get("resource-id") or "" for n in root.iter("node")}
    for n in root.iter("node"):
        rid = n.get("resource-id") or ""
        if not rid.startswith("tile:"):
            continue
        tid = rid[len("tile:"):]
        if want and tid not in want:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds") or ""))
        texts = []
        for d in n.iter("node"):
            for attr in ("text", "content-desc"):
                v = (d.get(attr) or "").strip()
                if v and v not in texts:
                    texts.append(v)
        print("\t".join([
            tid,
            f"{x1},{y1},{x2},{y2}",
            f"w={x2 - x1} h={y2 - y1}",
            "controls=" + ("yes" if f"tile_controls:{tid}" in ids else "no"),
            "badge=" + ("yes" if f"badge:{tid}" in ids else "no"),
            "texts=" + "|".join(texts),
        ]))


if __name__ == "__main__":
    main()
