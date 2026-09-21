#!/usr/bin/env python3
"""Shared phase 02 QA measurement helpers.

The edit-mode contraction is a graphicsLayer transform, and a uiautomator dump reports a Compose node's
LAYOUT bounds, which the transform does not touch (probed 2026-09-21: every tile's bounds are identical in and
out of edit mode). So every edit-mode geometry value is measured from screencap PIXELS; dumps are used for the
untransformed baseline and for what tile is where.
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
    The edit-mode disc centred on a tile corner: inside a search box around (cx, cy), the pixels within 30 of
    [disc_colour] are the disc. Returns (centre_x, centre_y, diameter_px, pixel_count) or None.
    """
    h, w, _ = img.shape
    x1, y1 = max(0, int(cx - search)), max(0, int(cy - search))
    x2, y2 = min(w, int(cx + search)), min(h, int(cy + search))
    box = img[y1:y2, x1:x2]
    mask = (np.abs(box - np.asarray(disc_colour, dtype=np.int16)).max(axis=2) <= 30)
    if mask.sum() < 40:
        return None
    ys, xs = np.nonzero(mask)
    # The disc is the filled circle: its diameter is the widest run of mask pixels in any row.
    widths = [int(mask[r].sum()) for r in range(mask.shape[0]) if mask[r].any()]
    heights = [int(mask[:, c].sum()) for c in range(mask.shape[1]) if mask[:, c].any()]
    return (x1 + xs.mean(), y1 + ys.mean(), (max(widths) + max(heights)) / 2.0, int(mask.sum()))


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
