#!/usr/bin/env python3
"""
E7 (geometry half): the settled edit-mode values of phase 02's Decisions.

usage: e7_geometry.py <dir> <held tile id> [--theme dark|light]

Reads from <dir>: normal.xml / normal.png (Start at rest) and edit.xml / edit.png (edit mode settled).

Where each number comes from, and why:
  - SIZES AND POSITIONS from the two dumps. A dump taken while a tile is held carries the graphicsLayer
    transform (unheld tiles report 0.835 of their resting size with moved centres; the held tile keeps its
    size), so it is exact and immune to what a pixel walk does when a tile's own artwork matches the page
    colour — which is what broke the light theme, where an icon's white pixels sit within tolerance of the
    dimmed white page.
  - COLOURS from the screencap, sampled inside the rectangles the dump gives. That is the part a dump cannot
    answer: the dim factor is a property of the pixels.
  - THE DISCS from both: bounds from the dump, and the pixels confirm a disc is actually painted there in the
    theme's foreground (a node with no paint would otherwise pass).

Values, per the Decisions: other tiles 0.835 ± 0.01 (§1.1.2) · centres contract to 0.90 ± 0.01 about a point
at the screen's horizontal centre, 46-49 % of its height (§1.1.3) · the held tile 1.00 ± 0.01 (§1.1.7) ·
dark: tile pixels × 0.53 ± 0.03, wallpaper × 0.25-0.30 (§1.1.5) · light: c' ≈ 0.63c + 62 (§1.1.6) · two discs
31 ± 1.5 epx centred on the held tile's right-hand corners (§1.2.1-§1.2.3), disc = theme foreground (§1.2.4).
"""
import sys

import numpy as np

import qa

d = sys.argv[1]
held_id = sys.argv[2]
theme = "dark"
if "--theme" in sys.argv:
    theme = sys.argv[sys.argv.index("--theme") + 1]
disc_colour = (255, 255, 255) if theme == "dark" else (0, 0, 0)

rest_bounds = qa.dump_tiles(f"{d}/normal.xml")
edit_bounds = qa.dump_tiles(f"{d}/edit.xml")
normal = qa.load(f"{d}/normal.png")
edit = qa.load(f"{d}/edit.png")
bg_normal = qa.detect_background(normal)
bg_edit = qa.detect_background(edit)
page_h = float(edit.shape[0])
results = []

if held_id not in rest_bounds:
    sys.exit(f"held tile {held_id} is not in {d}/normal.xml")
if not any(k.startswith("edit_disc") for k in qa.dump_ids(f"{d}/edit.xml")):
    sys.exit(f"{d}/edit.xml was not taken in edit mode (no discs in it)")

grid = {t: b for t, b in rest_bounds.items() if not t.startswith("dock:") and t in edit_bounds}
print(f"{len(grid)} grid tiles in both dumps; page background: resting {bg_normal}, in edit mode {bg_edit}")

# --- §1.1.7 the held tile stays at 1.00, §1.1.2 the others shrink to 0.835 -----------------------------------
# The held tile is the one node the dump gets wrong: it carries TWO transforms (the grid's contraction and its
# own counter-scale), and the dump reports its left edge about 9 px inside where the tile is actually painted
# (checked pixel by pixel: painted 45..388, dump 54..388). Every singly-transformed node — the other tiles,
# the discs — matches the pixels exactly. So the held tile is measured from the pixels.
rw, rh = qa.rect_size(rest_bounds[held_id])
held_rect = qa.probe_rect_edges(edit, *qa.rect_centre(edit_bounds[held_id]), bg_edit)
if held_rect is None:
    sys.exit("the held tile could not be found in the edit capture")
hw, hh = qa.rect_size(held_rect)
print(f"  held tile: dump says {edit_bounds[held_id]}, pixels say {held_rect}")
results.append(("held tile width", qa.check("held tile scale (width)", hw / rw, 1.00, 0.01)))
results.append(("held tile height", qa.check("held tile scale (height)", hh / rh, 1.00, 0.01)))

others = {t: (rest_bounds[t], edit_bounds[t]) for t in grid if t != held_id}
scales = []
for rest, shown in others.values():
    a, b = qa.rect_size(rest), qa.rect_size(shown)
    scales += [b[0] / a[0], b[1] / a[1]]
scale = float(np.median(scales))
print(f"  other-tile scale: n={len(scales)} median={scale:.4f} spread={min(scales):.4f}..{max(scales):.4f}")
results.append(("other tile scale", qa.check("other tiles' scale about their own centres", scale, 0.835, 0.01)))

# --- §1.1.3 the centres contract to 0.90 about a fixed point --------------------------------------------------
ratios = []
ids = sorted(others)
for i in range(len(ids)):
    for j in range(i + 1, len(ids)):
        a, b = others[ids[i]], others[ids[j]]
        for axis in (0, 1):
            rest_d = qa.rect_centre(a[0])[axis] - qa.rect_centre(b[0])[axis]
            shown_d = qa.rect_centre(a[1])[axis] - qa.rect_centre(b[1])[axis]
            if abs(rest_d) > 80:
                ratios.append(shown_d / rest_d)
pitch = float(np.median(ratios))
print(f"  centre contraction: n={len(ratios)} median={pitch:.4f} spread={min(ratios):.4f}..{max(ratios):.4f}")
results.append(("centre contraction", qa.check("tile centres contract to", pitch, 0.90, 0.01)))

if abs(1 - pitch) > 1e-6:
    fixed_xs, fixed_ys = [], []
    for rest, shown in others.values():
        rc, sc = qa.rect_centre(rest), qa.rect_centre(shown)
        fixed_xs.append((sc[0] - pitch * rc[0]) / (1 - pitch))
        fixed_ys.append((sc[1] - pitch * rc[1]) / (1 - pitch))
    fixed_x, fixed_y = float(np.median(fixed_xs)), float(np.median(fixed_ys))
    print(f"  fixed point: x={fixed_x:.1f}px ({fixed_x / qa.PANEL_W:.3f} of the width), y={fixed_y:.1f}px")
    results.append(("fixed point x", qa.check("fixed point x as a fraction of the width", fixed_x / qa.PANEL_W, 0.5, 0.02)))
    results.append(("fixed point y", qa.check_range("fixed point y as a fraction of the screen height", fixed_y / page_h, 0.46, 0.49)))
else:
    print("FAIL  the grid did not contract at all: this capture is not edit mode")
    results.append(("fixed point", False))

# --- §1.1.5 / §1.1.6 the dimming ------------------------------------------------------------------------------
# Only tiles whose resting plate is the ACCENT: a live tile's face (a photo, the weather) is a different
# colour in each capture, and pairing those two corrupts the fit (it read 0.459c + 72 instead of 0.63c + 62).
samples = []
for tid, (rest, shown) in others.items():
    a = qa.plate_colour(normal, rest)
    b = qa.plate_colour(edit, shown)
    if a is not None and b is not None and a.max() >= 30:
        samples.append((tid, a, b))
if samples:
    from collections import Counter
    accent = Counter(tuple(int(c) for c in a) for _, a, _ in samples).most_common(1)[0][0]
    dropped = [t for t, a, _ in samples if tuple(int(c) for c in a) != accent]
    samples = [(t, a, b) for t, a, b in samples if tuple(int(c) for c in a) == accent]
    print(f"  dim samples: {len(samples)} accent tiles {accent}" + (f", dropped {dropped} (live faces)" if dropped else ""))
if theme == "dark":
    factors = [b[c] / a[c] for _, a, b in samples for c in range(3) if a[c] >= 40]
    factor = float(np.median(factors))
    print(f"  dark tile dim: n={len(factors)} median factor={factor:.3f}")
    results.append(("tile dim", qa.check("other tiles' pixels ×", factor, 0.53, 0.03)))
    print("  dark wallpaper dim: the page is black here (no wallpaper set), and black × any factor is black, "
          "so §1.1.5's 0.25-0.30 is read in the light theme's page instead")
else:
    xs = np.array([a[c] for _, a, _ in samples for c in range(3)])
    ys = np.array([b[c] for _, _, b in samples for c in range(3)])
    slope, intercept = np.polyfit(xs, ys, 1)
    print(f"  light tile dim: n={len(xs)} fit c' = {slope:.3f}c + {intercept:.1f}")
    results.append(("light dim slope", qa.check("light-theme dim slope", float(slope), 0.63, 0.05)))
    results.append(("light dim offset", qa.check("light-theme dim offset", float(intercept), 62, 8)))
    print(f"  light page: {bg_normal} -> {bg_edit}; the same formula predicts {0.63 * bg_normal[0] + 62:.0f}")
    results.append(("page dim", qa.check("the dimmed page", float(bg_edit[0]), 0.63 * bg_normal[0] + 62, 6)))

# --- §1.2.1-§1.2.4 the two discs -------------------------------------------------------------------------------
hx1, hy1, hx2, hy2 = edit_bounds[held_id]
for kind, (cx, cy) in (("unpin", (hx2, hy1)), ("resize", (hx2, hy2))):
    rect = qa.dump_node(f"{d}/edit.xml", f"edit_disc:{kind}")
    if rect is None:
        print(f"FAIL  {kind} disc: no node in the dump")
        results.append((f"{kind} disc", False))
        continue
    w, h = qa.rect_size(rect)
    dcx, dcy = qa.rect_centre(rect)
    print(f"  {kind} disc: node {rect} = {qa.epx(w):.2f} × {qa.epx(h):.2f} epx, centre ({dcx:.1f}, {dcy:.1f}); "
          f"the held tile's corner is ({cx}, {cy})")
    results.append((f"{kind} diameter", qa.check(f"{kind} disc diameter", qa.epx((w + h) / 2), 31, 1.5, " epx")))
    results.append((f"{kind} centre x", qa.check(f"{kind} disc centre x off the corner", qa.epx(dcx - cx), 0, 1.5, " epx")))
    results.append((f"{kind} centre y", qa.check(f"{kind} disc centre y off the corner", qa.epx(dcy - cy), 0, 1.5, " epx")))
    found = qa.find_disc(edit, dcx, dcy, disc_colour, search=int(w))
    if found is None:
        print(f"FAIL  {kind} disc: the node is there but nothing is painted in {disc_colour}")
        results.append((f"{kind} painted", False))
    else:
        _, _, diameter, count = found
        print(f"    painted: {count} px of the theme's foreground, {qa.epx(diameter):.2f} epx across")
        results.append((f"{kind} painted", qa.check(f"{kind} disc painted in the theme's foreground",
                                                    qa.epx(diameter), 31, 3, " epx")))

qa.report(results)
