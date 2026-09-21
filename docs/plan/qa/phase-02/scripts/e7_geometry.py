#!/usr/bin/env python3
"""
E7 (geometry half): the settled edit-mode values of phase 02's Decisions, measured from screencap pixels.

usage: e7_geometry.py <dir> <held tile id> [--theme dark|light]

Reads from <dir>: normal.xml + normal.png (Start at rest) and edit.png (edit mode settled, same layout, same
scroll). Measures, per R6 §1.1 as the Decisions quote it:
  - every other tile's scale about its own centre        0.835 ± 0.01   (§1.1.2, HIGH)
  - the contraction of the tile centres                  0.90  ± 0.01   (§1.1.3, HIGH)
  - the fixed point's height as a fraction of the screen 0.46 .. 0.49   (§1.1.3, and the agent's fraction pick)
  - the held tile                                        1.00  ± 0.01   (§1.1.7, HIGH)
  - the dimming of the other tiles                       dark: ×0.53 ± 0.03 (§1.1.5) / light: c' = 0.63c + 62 (§1.1.6)
  - the two discs                                        31 ± 1.5 epx on the held tile's right-hand corners (§1.2.1-§1.2.3)
"""
import sys

import numpy as np

import qa

d = sys.argv[1]
held_id = sys.argv[2]
theme = "dark"
if "--theme" in sys.argv:
    theme = sys.argv[sys.argv.index("--theme") + 1]
background = (0, 0, 0) if theme == "dark" else (255, 255, 255)
disc_colour = (255, 255, 255) if theme == "dark" else (0, 0, 0)

normal_bounds = qa.dump_tiles(f"{d}/normal.xml")
normal = qa.load(f"{d}/normal.png")
edit = qa.load(f"{d}/edit.png")
if held_id not in normal_bounds:
    sys.exit(f"held tile {held_id} is not in {d}/normal.xml")

page_h = float(edit.shape[0])
results = []

# The grid tiles, excluding the bottom row (it does not contract: it is not in the scrolling grid).
grid = {t: b for t, b in normal_bounds.items() if not t.startswith("dock:")}

# Every tile's rectangle at rest and in edit mode. The edit-mode probe starts from the tile's resting centre
# pulled 10 % toward the fixed point, which lands inside the contracted tile for every tile on the screen.
fx, fy = qa.PANEL_W * 0.5, page_h * 0.475
measured = {}
for tid, b in grid.items():
    cx, cy = qa.rect_centre(b)
    rest = qa.probe_rect(normal, cx, cy, background)
    guess = (fx + (cx - fx) * 0.9, fy + (cy - fy) * 0.9)
    shown = qa.probe_rect(edit, guess[0], guess[1], background)
    if rest and shown:
        measured[tid] = (rest, shown)
print(f"measured {len(measured)} of {len(grid)} grid tiles from pixels\n")

held = measured.get(held_id)
if held is None:
    sys.exit(f"could not probe the held tile {held_id} in both captures")

# --- §1.1.7 the held tile stays at 1.00, and §1.1.2 the others shrink to 0.835 -------------------------------
hw, hh = qa.rect_size(held[1])
rw, rh = qa.rect_size(held[0])
results.append(("held tile scale", qa.check("held tile scale (width)", hw / rw, 1.00, 0.01)))
results.append(("held tile scale h", qa.check("held tile scale (height)", hh / rh, 1.00, 0.01)))

others = {t: v for t, v in measured.items() if t != held_id}
scales = []
for tid, (rest, shown) in others.items():
    rwi, rhi = qa.rect_size(rest)
    swi, shi = qa.rect_size(shown)
    scales += [swi / rwi, shi / rhi]
scale = float(np.median(scales))
print(f"  other-tile scale samples: n={len(scales)} median={scale:.4f} spread={min(scales):.4f}..{max(scales):.4f}")
results.append(("other tile scale", qa.check("other tiles' scale about their own centres", scale, 0.835, 0.01)))

# --- §1.1.3 the centres contract to 0.90, about a fixed point 46-49 % down the screen -------------------------
# The ratio is taken between PAIRS of tiles, which cancels the fixed point out entirely.
ratios = []
ids = sorted(others)
for i in range(len(ids)):
    for j in range(i + 1, len(ids)):
        a, b = others[ids[i]], others[ids[j]]
        for axis in (0, 1):
            rest_d = qa.rect_centre(a[0])[axis] - qa.rect_centre(b[0])[axis]
            shown_d = qa.rect_centre(a[1])[axis] - qa.rect_centre(b[1])[axis]
            if abs(rest_d) > 80:  # only well-separated pairs, so pixel noise cannot swamp the ratio
                ratios.append(shown_d / rest_d)
pitch = float(np.median(ratios))
print(f"  centre-contraction samples: n={len(ratios)} median={pitch:.4f} spread={min(ratios):.4f}..{max(ratios):.4f}")
results.append(("centre contraction", qa.check("tile centres contract to", pitch, 0.90, 0.01)))

# fixed point: shown = fixed + (rest - fixed) * k  =>  fixed = (shown - k*rest) / (1 - k)
fixed_xs, fixed_ys = [], []
for tid, (rest, shown) in measured.items():
    rc, sc = qa.rect_centre(rest), qa.rect_centre(shown)
    fixed_xs.append((sc[0] - pitch * rc[0]) / (1 - pitch))
    fixed_ys.append((sc[1] - pitch * rc[1]) / (1 - pitch))
fixed_x, fixed_y = float(np.median(fixed_xs)), float(np.median(fixed_ys))
print(f"  fixed point: x={fixed_x:.1f}px ({fixed_x / qa.PANEL_W:.3f} of the width), y={fixed_y:.1f}px")
results.append(("fixed point x", qa.check("fixed point x as a fraction of the width", fixed_x / qa.PANEL_W, 0.5, 0.02)))
# page_h here IS the screencap, i.e. the whole panel, which is what R6 §1.1.3 measures against.
results.append(("fixed point y", qa.check_range("fixed point y as a fraction of the screen height", fixed_y / page_h, 0.46, 0.49)))

# --- §1.1.5 / §1.1.6 the dimming ------------------------------------------------------------------------------
dim_samples = []
for tid, (rest, shown) in others.items():
    a = qa.plate_colour(normal, rest)
    b = qa.plate_colour(edit, shown)
    if a is None or b is None or a.max() < 30:
        continue
    dim_samples.append((tid, a, b))
if dim_samples:
    if theme == "dark":
        factors = []
        for tid, a, b in dim_samples:
            for ch in range(3):
                if a[ch] >= 40:  # a channel that is ~0 at rest says nothing about a multiplier
                    factors.append(b[ch] / a[ch])
        factor = float(np.median(factors))
        print(f"  dark-theme tile dim: n={len(factors)} median factor={factor:.3f}")
        results.append(("tile dim (dark)", qa.check("other tiles' pixels × ", factor, 0.53, 0.03)))
    else:
        # c' = 0.63c + 62: fit both terms across every channel sample
        xs = np.array([a[ch] for _, a, _ in dim_samples for ch in range(3)])
        ys = np.array([b[ch] for _, _, b in dim_samples for ch in range(3)])
        slope, intercept = np.polyfit(xs, ys, 1)
        print(f"  light-theme tile dim: n={len(xs)} fit c' = {slope:.3f}c + {intercept:.1f}")
        results.append(("tile dim slope (light)", qa.check("light-theme dim slope", float(slope), 0.63, 0.05)))
        results.append(("tile dim offset (light)", qa.check("light-theme dim offset", float(intercept), 62, 8)))

# --- §1.2.1-§1.2.3 the two discs ------------------------------------------------------------------------------
hx1, hy1, hx2, hy2 = held[1]
for name, (cx, cy) in (("unpin (top-right corner)", (hx2, hy1)), ("resize (bottom-right corner)", (hx2, hy2))):
    found = qa.find_disc(edit, cx, cy, disc_colour)
    if not found:
        print(f"FAIL  {name} disc: not found at ({cx:.0f}, {cy:.0f})")
        results.append((f"{name} disc found", False))
        continue
    dx, dy, diameter, count = found
    print(f"  {name}: centre=({dx:.1f}, {dy:.1f}) corner=({cx:.1f}, {cy:.1f}) diameter={qa.epx(diameter):.2f} epx")
    results.append((f"{name} diameter", qa.check(f"{name} diameter", qa.epx(diameter), 31, 1.5, " epx")))
    # "centred on the corner": the disc's centre sits on the tile's corner, within a pixel or so of epx
    results.append((f"{name} centre x", qa.check(f"{name} centre x offset from the corner", qa.epx(dx - cx), 0, 1.5, " epx")))
    results.append((f"{name} centre y", qa.check(f"{name} centre y offset from the corner", qa.epx(dy - cy), 0, 1.5, " epx")))

qa.report(results)
