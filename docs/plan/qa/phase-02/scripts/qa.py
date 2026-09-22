#!/usr/bin/env python3
"""Shared phase 02 QA measurement helpers.

E7 measures the settled geometry from screencap PIXELS, because that is what proves the numbers the phase doc
lists — a tile's drawn size and the dim factor in real pixels. (A dump taken while a tile is held DOES carry
the graphicsLayer transform; an earlier note here claimed otherwise and was wrong. Dumps are used for layout
facts and for addressing the discs; pixels for what the screen actually shows.)
"""
import re
import sys

import numpy as np
from PIL import Image

PANEL_W = 1080          # AVD panel width in px
CANVAS_EPX = 360.0      # the shell's canvas (phase 01 Scale)
PX_PER_EPX = PANEL_W / CANVAS_EPX


def epx(px):
    """px -> epx on this panel."""
    return px / PX_PER_EPX


def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int16)


def dump_tiles(path):
    """{tile id: (x1, y1, x2, y2)} from a uiautomator dump (layout bounds, no transform)."""
    s = open(path).read()
    return {
        m.group(1): tuple(int(v) for v in m.groups()[1:])
        for m in re.finditer(r'resource-id="tile:([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
    }


def detect_background(img):
    """
    The page background as it appears IN THIS CAPTURE. It cannot be assumed: in the light theme edit mode
    dims the background too (white 255 becomes 223), and comparing against pure white then reads the whole
    screen as one enormous tile. Taken as the modal colour among unsaturated pixels, which is the page in
    both themes, dimmed or not — the accent is saturated and never wins.
    """
    flat = img.reshape(-1, 3)
    spread = flat.max(axis=1) - flat.min(axis=1)
    grey = flat[spread < 12]
    if len(grey) == 0:
        return (0, 0, 0)
    packed = (grey[:, 0].astype(np.int32) << 16) | (grey[:, 1].astype(np.int32) << 8) | grey[:, 2].astype(np.int32)
    values, counts = np.unique(packed, return_counts=True)
    top = int(values[counts.argmax()])
    return ((top >> 16) & 255, (top >> 8) & 255, top & 255)


def dump_ids(path):
    """Every resource-id in a dump."""
    return set(re.findall(r'resource-id="([^"]+)"', open(path).read()))


def dump_node(path, rid):
    """One node's (x1, y1, x2, y2) by resource-id, or None."""
    m = re.search(r'resource-id="' + re.escape(rid) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
                  open(path).read())
    return tuple(int(v) for v in m.groups()) if m else None


def is_background(px, background):
    """A pixel belongs to the page background (black in the dark theme, white in the light one)."""
    return int(np.abs(np.asarray(px, dtype=np.int16) - np.asarray(background, dtype=np.int16)).max()) <= 24


def probe_rect(img, cx, cy, background=(0, 0, 0)):
    """
    The tile rectangle containing (cx, cy): walk out from that point until the page background.
    Returns (x1, y1, x2, y2) with x2/y2 exclusive, or None when the point is already background.
    """
    cx, cy = int(round(cx)), int(round(cy))
    h, w, _ = img.shape
    if not (0 <= cx < w and 0 <= cy < h) or is_background(img[cy, cx], background):
        return None
    x1 = cx
    while x1 > 0 and not is_background(img[cy, x1 - 1], background):
        x1 -= 1
    x2 = cx
    while x2 + 1 < w and not is_background(img[cy, x2 + 1], background):
        x2 += 1
    # Scan the vertical extent down the tile's own middle, so a label or a live face cannot cut it short.
    mx = (x1 + x2) // 2
    y1 = cy
    while y1 > 0 and not is_background(img[y1 - 1, mx], background):
        y1 -= 1
    y2 = cy
    while y2 + 1 < h and not is_background(img[y2 + 1, mx], background):
        y2 += 1
    return (x1, y1, x2 + 1, y2 + 1)


def probe_rect_edges(img, cx, cy, background):
    """
    A tile's rectangle measured along its EDGES rather than its middle: the horizontal run through the centre
    row, then the vertical run a few pixels inside the left edge. A tile's own artwork (an icon, a label) can
    match the page colour — an app icon's white against the light theme's dimmed white page — and a scan down
    the middle then stops inside the tile. Nothing is drawn in the margin.
    """
    horizontal = probe_rect(img, cx, cy, background)
    if horizontal is None:
        return None
    x1, _, x2, _ = horizontal
    col = min(x1 + 4, x2 - 1)
    h = img.shape[0]
    y1 = int(cy)
    while y1 > 0 and not is_background(img[y1 - 1, col], background):
        y1 -= 1
    y2 = int(cy)
    while y2 + 1 < h and not is_background(img[y2 + 1, col], background):
        y2 += 1
    return (x1, y1, x2, y2 + 1)


def rect_size(r):
    return (r[2] - r[0], r[3] - r[1])


def rect_centre(r):
    return ((r[0] + r[2]) / 2.0, (r[1] + r[3]) / 2.0)


def plate_colour(img, rect, inset=0.30):
    """
    The tile's accent plate colour: the MODE of the pixels in an inset box, so a glyph, a label or a live face
    cannot drag the value. Returned as a float RGB triple.
    """
    x1, y1, x2, y2 = rect
    w, h = x2 - x1, y2 - y1
    box = img[int(y1 + h * inset):int(y2 - h * inset), int(x1 + w * inset):int(x2 - w * inset)]
    flat = box.reshape(-1, 3)
    if len(flat) == 0:
        return None
    packed = (flat[:, 0].astype(np.int32) << 16) | (flat[:, 1].astype(np.int32) << 8) | flat[:, 2].astype(np.int32)
    values, counts = np.unique(packed, return_counts=True)
    top = int(values[counts.argmax()])
    return np.array([(top >> 16) & 255, (top >> 8) & 255, top & 255], dtype=float)


def find_disc(img, cx, cy, disc_colour, search=60):
    """
    The edit-mode disc centred on a tile corner. Only the CONNECTED run of disc-coloured pixels that touches the
    expected corner counts: a white glyph inside the tile (the Contacts icon, a label) sits within the search box
    and would otherwise inflate the bounding box.
    Returns (centre_x, centre_y, diameter_px, pixel_count) or None.
    """
    from collections import deque
    h, w, _ = img.shape
    x1, y1 = max(0, int(cx - search)), max(0, int(cy - search))
    x2, y2 = min(w, int(cx + search)), min(h, int(cy + search))
    box = img[y1:y2, x1:x2]
    mask = (np.abs(box - np.asarray(disc_colour, dtype=np.int16)).max(axis=2) <= 30)
    if mask.sum() < 40:
        return None
    # A seed inside the disc: the disc-coloured pixel nearest the expected corner.
    ys, xs = np.nonzero(mask)
    d2 = (xs - (cx - x1)) ** 2 + (ys - (cy - y1)) ** 2
    seed = (int(ys[d2.argmin()]), int(xs[d2.argmin()]))
    seen = np.zeros_like(mask)
    q = deque([seed])
    seen[seed] = True
    count = 0
    left = right = seed[1]
    top = bottom = seed[0]
    while q:
        r, c = q.popleft()
        count += 1
        left, right = min(left, c), max(right, c)
        top, bottom = min(top, r), max(bottom, r)
        for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nr, nc = r + dr, c + dc
            if 0 <= nr < mask.shape[0] and 0 <= nc < mask.shape[1] and mask[nr, nc] and not seen[nr, nc]:
                seen[nr, nc] = True
                q.append((nr, nc))
    diameter = ((right - left + 1) + (bottom - top + 1)) / 2.0
    return (x1 + (left + right) / 2.0, y1 + (top + bottom) / 2.0, diameter, count)


def check(label, value, expected, tolerance, unit=""):
    """Print one PASS/FAIL row against a measured tolerance and return the boolean."""
    ok = abs(value - expected) <= tolerance
    print(f"{'PASS' if ok else 'FAIL'}  {label}: {value:.3f}{unit} (expected {expected}{unit} ± {tolerance}{unit})")
    return ok


def check_range(label, value, low, high, unit=""):
    ok = low <= value <= high
    print(f"{'PASS' if ok else 'FAIL'}  {label}: {value:.3f}{unit} (expected {low}{unit}..{high}{unit})")
    return ok


def report(results):
    """Exit non-zero when any row failed, so a shell driver can gate on the exit code (Hard Rule 14)."""
    failed = [name for name, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    if failed:
        print("FAILED: " + ", ".join(failed))
    sys.exit(1 if failed else 0)
