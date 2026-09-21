#!/usr/bin/env python3
"""
E7 (hold bracket): 740 ms on a tile must NOT enter edit mode and 830 ms must, which brackets R6 §1.1.1's
783 ± 33 ms hold from both sides.

usage: e7_bracket.py <dir> <held tile id>
Reads normal.xml / normal.png (Start at rest) and hold_740.png / hold_830.png from <dir>.

Edit mode is read off the pixels, not off a dump: the transform that proves it is a graphicsLayer, which a
uiautomator dump does not see. A tile OTHER than the held one is both dimmed and moved in edit mode, so its
resting centre reads page background once the grid has contracted away from it — and its plate is darker.
"""
import sys

import numpy as np

import qa

d = sys.argv[1]
held_id = sys.argv[2]
bounds = qa.dump_tiles(f"{d}/normal.xml")
normal = qa.load(f"{d}/normal.png")

# The probe tile: the grid tile whose centre is furthest from the fixed point, so the contraction moves it most.
fx, fy = qa.PANEL_W * 0.5, normal.shape[0] * 0.475
candidates = {t: b for t, b in bounds.items() if t != held_id and not t.startswith("dock:")}
if not candidates:
    sys.exit("no other grid tile to probe")
probe_id = max(candidates, key=lambda t: (qa.rect_centre(candidates[t])[0] - fx) ** 2 + (qa.rect_centre(candidates[t])[1] - fy) ** 2)
pc = qa.rect_centre(bounds[probe_id])
rest_rect = qa.probe_rect(normal, pc[0], pc[1])
rest_colour = qa.plate_colour(normal, rest_rect) if rest_rect else None
print(f"probe tile {probe_id} at {pc}, resting plate {rest_colour}")


def in_edit_mode(path):
    """True when the grid has contracted and dimmed: the probe tile's plate is darker than at rest."""
    img = qa.load(path)
    rect = qa.probe_rect(img, pc[0], pc[1])
    if rect is None:
        return True, "probe point is page background: the grid contracted away from it"
    colour = qa.plate_colour(img, rect)
    if colour is None or rest_colour is None:
        return False, "no plate colour"
    ratio = float(np.median([colour[c] / rest_colour[c] for c in range(3) if rest_colour[c] >= 40] or [1.0]))
    moved = abs(rect[0] - rest_rect[0]) > 4 or abs(rect[1] - rest_rect[1]) > 4
    return (ratio < 0.8 or moved), f"plate ratio {ratio:.3f}, rect {rect} vs resting {rest_rect}"


results = []
edit740, why740 = in_edit_mode(f"{d}/hold_740.png")
print(f"{'FAIL' if edit740 else 'PASS'}  740 ms hold does not enter edit mode ({why740})")
results.append(("740 ms no edit mode", not edit740))
edit830, why830 = in_edit_mode(f"{d}/hold_830.png")
print(f"{'PASS' if edit830 else 'FAIL'}  830 ms hold enters edit mode ({why830})")
results.append(("830 ms enters edit mode", edit830))

qa.report(results)
