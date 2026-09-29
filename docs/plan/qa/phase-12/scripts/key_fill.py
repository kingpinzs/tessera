#!/usr/bin/env python3
"""A letter key's fill, measured off a screencap (E11's keyboard check).

    key_fill.py <screencap.png> <kb_dump.xml> <key resource-id> <expected r,g,b> <tolerance>

Finds the key's bounds in the instrumentation dump (the IME window's nodes; a plain uiautomator dump has none), samples
two glyph-free patches inside it — 6 x 6 px, 5 px in from the bottom-left and bottom-right corners, away from the centred
label — and compares each patch's mean with the expected fill. Prints the samples and `RESULT PASS|FAIL|UNUSABLE ...`;
exit 0 / 1 / 2.
"""
import re
import sys
import xml.etree.ElementTree as ET

from PIL import Image


def main():
    png, dump, key, exp, tol = sys.argv[1:6]
    er, eg, eb = (int(v) for v in exp.split(","))
    tol = float(tol)
    try:
        root = ET.parse(dump).getroot()
    except Exception as e:
        print(f"RESULT UNUSABLE dump unreadable: {e}")
        return 2
    node = next((n for n in root.iter("node") if n.get("resource-id") == key), None)
    if node is None:
        print(f"RESULT UNUSABLE no node {key}")
        return 2
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds") or "0 0 0 0"))
    img = Image.open(png).convert("RGB")
    ok = True
    for name, (px, py) in {"bottom-left": (x1 + 5, y2 - 11), "bottom-right": (x2 - 11, y2 - 11)}.items():
        pix = [tuple(img.getpixel((px + dx, py + dy))) for dx in range(6) for dy in range(6)]  # type: ignore[arg-type]
        m = tuple(sum(p[i] for p in pix) / len(pix) for i in range(3))
        good = all(abs(m[i] - (er, eg, eb)[i]) <= tol for i in range(3))
        ok &= good
        print(f"{key} {name} patch at ({px},{py}): mean=({m[0]:.1f},{m[1]:.1f},{m[2]:.1f}) expected=({er},{eg},{eb}) +-{tol} {'ok' if good else 'OFF'}")
    print(f"RESULT {'PASS' if ok else 'FAIL'} {key} bounds=[{x1},{y1}][{x2},{y2}]")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
