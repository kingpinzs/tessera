#!/usr/bin/env python3
"""picture_oracle.py — phase 12 build task 4 (ii), r3 V7: the tile-over-picture oracle.

Proves that a Start tile's pixels are the preset's accent composited at a LITERAL alpha over the shipped
picture, using an independent model of where the picture lands on the screen. Nothing here imports
from the app; every number is re-derived from the code and cited.

    picture_oracle.py --picture preset_hal.webp --screenshot start.png --dump start.xml \\
        --tile slot:PEOPLE --accent E81123 --alpha 0.72 [--viewport 0,0,1080,2196] [--scroll 0] [--out r.json]
    picture_oracle.py --expect-opaque ...   (Midnight: alpha 1, T = accent ± 2; the picture still validates the gutters)
    picture_oracle.py --no-picture ...      (Default: no picture; gutter pixels = (0,0,0) ± 2, T = accent ± 2)

Exit 0 PASS, 1 FAIL, 2 UNUSABLE. The last stdout line is `RESULT PASS|FAIL|UNUSABLE <summary>`.

The geometry modelled (all at rest, the AVD tileshell_fhd 1080 x 2340 @ 450 dpi):
  * 1 epx = 1080 / 360 = 3 px                       ui/tokens/Scale.kt:21 (CANVAS_EPX = 360f), :37 (pxPerEpx)
  * drawn status bar 28 epx = 84 px, drawn OVER the page (a Row with no background, aligned TopCenter in a Box
    that also holds the Column)                       bars/SystemBars.kt:79, :137; StartActivity.kt:270-271
  * drawn nav bar 48 epx = 144 px, black, below the page in the Column
                                                      bars/SystemBars.kt:81, :199; StartActivity.kt:256-269
  * the pager page = the Column's weight(1f) Box = x 0..1080, y 0..2196 (`start_page` in the dump)
  * the picture: Image(ContentScale.Crop, fillMaxSize) inside graphicsLayer { translationY = -scroll*0.25;
    scaleX = scaleY = 1.3; alpha = gridAlpha (1 at rest) }  start/StartPage.kt:563-571
    Crop = uniform scale max(vw/pw, vh/ph), centred (Alignment.Center); the layer scale is about the layer's
    centre (TransformOrigin.Center default).
  * the picture is decoded at full size: BackgroundDecoder keeps inSampleSize = 1 while height <= 2 * 2400
    (4056 <= 4800)                                    start/BackgroundDecoder.kt:19, :42-43
  * the shipped pictures are 1872 x 4056 (app/src/main/res/drawable-nodpi/preset_*.webp), so the Crop scale is
    1080/1872 = 0.576923 (scaled size exactly 1080 x 2340, offset y = (2196 - 2340) / 2 = -72), and the total
    picture->screen scale is 0.576923 * 1.3 = 0.75 (one screen px = 1.3333 picture px).
  * a tile's plate is `accent.copy(alpha = accent.alpha * tileAlpha)` (start/TileView.kt:347) with
    tileAlpha = 1 - 0.8 * transparency (start/StartPage.kt:429) — this script NEVER computes that; the row passes
    the preset table's literal alpha.
  * the label sits bottom-left (padding start 8 epx, bottom 5 epx, caption 12 epx: TileView.kt:420,
    ui/tokens/StartGrid.kt:35), the icon is a centred square of 0.42 x the tile width for MEDIUM
    (TileView.kt:398-399) — both are excluded from the tile patches.

Screen -> picture mapping (viewport-local pixel centres; vx0,vy0 = viewport origin; S = 1.3; ty = -scroll*0.25):
    cx = sx + 0.5 - vx0 ; cy = sy + 0.5 - vy0
    lx = pw_/2 + (cx - pw_/2) / S ; ly = ph_/2 + (cy - ty - ph_/2) / S      (pw_, ph_ = viewport size)
    u = (lx - offx) * pw / dstw ; v = (ly - offy) * ph / dsth               (dst = Crop-scaled size, off = centring)
then a bilinear sample at (u, v) with texel centres at (i + 0.5): the device draws the bitmap through one matrix
(Crop x layer scale) with Compose's default FilterQuality.Low = bilinear, no mipmaps.

Method:
  1. VALIDATE the mapping on gutter patches: 5x5 patches inside the viewport, below the status bar, clear (3 px)
     of every dump node that is not a full-page container, spread over a 4 x 6 cell grid. Each must match the
     oracle within +-6 per channel mean. < 4 usable patches (or poor spread) = UNUSABLE; any patch failing = FAIL.
  2. Inside the named tile: 9x9 patches clear of the edges (3 px), the label band (bottom 70 px) and the icon
     square (0.42 w + 12 px), rejected when the screenshot has structure the model does not predict (std above
     (1-alpha) x the oracle's std + 3, or any pixel > 24 from the patch mean), or when the accent and B do not
     separate (max channel |accent - B| < 16, or (1-alpha) x that < 8). At least 3 accepted patches or UNUSABLE.
  3. At each accepted patch: T = screenshot mean, B = oracle mean, assert T = alpha*accent + (1-alpha)*B +- 4 per
     channel, and the negative control T != accent +- 4 (a missing picture makes alpha 1 and fails this).
  Plus a GEOMETRY SENSITIVITY report (informational): for three deliberately wrong models (picture shifted 30 px
  in y, in x, layer scale 1.0) it counts the gutter patches that would have shown the error and the tile patches
  whose B it would move. The shipped pictures are dark over much of the screen (preset_hal is black outside the
  eye), so a validation can pass on flat patches that constrain nothing; a WARNING names any error that would
  move a tile patch's B yet no gutter patch could see. Read it before trusting a PASS on a smooth picture.
"""
import argparse
import json
import math
import re
import sys

from PIL import Image

try:
    import numpy as np
except ImportError:
    np = None   # the pure-Pillow path below is slower but gives the same numbers

# ---------------------------------------------------------------- constants derived from the code (cited above)
SCREEN_W, SCREEN_H = 1080, 2340
PX_PER_EPX = SCREEN_W / 360.0            # Scale.kt:21,37
STATUS_EPX = 28                          # SystemBars.kt:79
NAV_EPX = 48                             # SystemBars.kt:81
STATUS_PX = int(round(STATUS_EPX * PX_PER_EPX))   # 84
DEFAULT_VIEWPORT = (0, 0, SCREEN_W, SCREEN_H - int(round(NAV_EPX * PX_PER_EPX)))  # (0, 0, 1080, 2196)
LAYER_SCALE = 1.3                        # StartPage.kt:566
PARALLAX = 0.25                          # StartPage.kt:565
LABEL_BAND_PX = 70                       # caption 12 epx line (~16 epx) + 5 epx bottom padding, with slack
ICON_FRACTION = 0.42                     # TileView.kt:398 (MEDIUM: width * 0.42; WIDE: height * 0.42)
ICON_MARGIN_PX = 12
EDGE_MARGIN_PX = 3


def log(msg=""):
    print(msg)


# ---------------------------------------------------------------- the dump
NODE_RE = re.compile(r'<node\b([^>]*)/?>')
ATTR_RE = re.compile(r'(\w[\w:-]*)="([^"]*)"')
BOUNDS_RE = re.compile(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]')


def parse_dump(path):
    """Every node with bounds: [(resource_id, text, (x1, y1, x2, y2))]."""
    s = open(path, encoding="utf-8", errors="replace").read()
    nodes = []
    for m in NODE_RE.finditer(s):
        attrs = dict(ATTR_RE.findall(m.group(1)))
        b = BOUNDS_RE.search(attrs.get("bounds", ""))
        if not b:
            continue
        nodes.append((attrs.get("resource-id", ""), attrs.get("text", ""), tuple(int(v) for v in b.groups())))
    return nodes


def find_tile(nodes, name):
    wanted = {name, "tile:" + name}
    for rid, _, b in nodes:
        if rid in wanted:
            return rid, b
    return None, None


def find_viewport(nodes):
    for rid, _, b in nodes:
        if rid == "start_page":
            return b
    return None


def occupancy(nodes, viewport, margin):
    """Rectangles (expanded by margin) that a gutter patch must avoid: every node with an id or text, except
    full-page containers (>= 90 % of the viewport area)."""
    vx0, vy0, vx1, vy1 = viewport
    varea = float((vx1 - vx0) * (vy1 - vy0))
    rects = []
    for rid, text, (x1, y1, x2, y2) in nodes:
        if not rid and not text:
            continue
        area = float(max(0, x2 - x1) * max(0, y2 - y1))
        if area >= 0.9 * varea:
            continue
        rects.append((x1 - margin, y1 - margin, x2 + margin, y2 + margin, rid or ("text:" + text[:20])))
    return rects


def rect_free(x1, y1, x2, y2, rects):
    for rx1, ry1, rx2, ry2, _ in rects:
        if x1 < rx2 and x2 > rx1 and y1 < ry2 and y2 > ry1:
            return False
    return True


# ---------------------------------------------------------------- the picture model
class Oracle:
    """Screen pixel -> picture pixel through the Crop + centred layer scale + parallax chain, bilinear."""

    def __init__(self, picture_path, viewport, layer_scale=LAYER_SCALE, scroll=0.0, parallax=PARALLAX,
                 tx=0.0, extra_ty=0.0, source=None):
        im = source if source is not None else Image.open(picture_path).convert("RGB")
        self.image = im
        self.pw, self.ph = im.size
        self.viewport = viewport
        vx0, vy0, vx1, vy1 = viewport
        self.vw, self.vh = vx1 - vx0, vy1 - vy0
        self.S = float(layer_scale)
        self.tx = float(tx)                                   # only the sensitivity probes translate in x
        self.ty = -float(scroll) * parallax + float(extra_ty)
        # ContentScale.Crop: uniform scale to cover; the painter draws into the rounded dst size, centred.
        s0 = max(self.vw / self.pw, self.vh / self.ph)
        self.dstw = int(round(self.pw * s0))
        self.dsth = int(round(self.ph * s0))
        self.offx = int(round((self.vw - self.dstw) * 0.5))
        self.offy = int(round((self.vh - self.dsth) * 0.5))
        self.crop_scale = s0
        if np is not None:
            self.arr = np.asarray(im, dtype=np.float64)
        else:
            self.arr = None
            self.px = im.load()

    def describe(self):
        return {
            "picture_size": [self.pw, self.ph], "viewport": list(self.viewport),
            "crop_scale": self.crop_scale, "crop_dst": [self.dstw, self.dsth], "crop_offset": [self.offx, self.offy],
            "layer_scale": self.S, "translation_y": self.ty, "total_scale": self.crop_scale * self.S,
        }

    def to_source(self, sx, sy):
        """Screen pixel (sx, sy) integer coords -> continuous picture coords (u, v) of its centre."""
        vx0, vy0, _, _ = self.viewport
        cx = sx + 0.5 - vx0
        cy = sy + 0.5 - vy0
        px_, py_ = self.vw / 2.0, self.vh / 2.0
        lx = px_ + (cx - self.tx - px_) / self.S
        ly = py_ + (cy - self.ty - py_) / self.S
        u = (lx - self.offx) * (self.pw / self.dstw)
        v = (ly - self.offy) * (self.ph / self.dsth)
        return u, v

    def _bilinear_one(self, u, v):
        x = u - 0.5
        y = v - 0.5
        x0 = math.floor(x)
        y0 = math.floor(y)
        fx = x - x0
        fy = y - y0
        xa = min(max(x0, 0), self.pw - 1)
        xb = min(max(x0 + 1, 0), self.pw - 1)
        ya = min(max(y0, 0), self.ph - 1)
        yb = min(max(y0 + 1, 0), self.ph - 1)
        p00 = self.px[xa, ya]
        p10 = self.px[xb, ya]
        p01 = self.px[xa, yb]
        p11 = self.px[xb, yb]
        return [(1 - fx) * (1 - fy) * p00[c] + fx * (1 - fy) * p10[c] + (1 - fx) * fy * p01[c] + fx * fy * p11[c]
                for c in range(3)]

    def patch(self, x1, y1, x2, y2):
        """The oracle's pixels for screen rect [x1,x2) x [y1,y2) as an (h, w, 3) array (numpy) or nested lists."""
        if np is not None:
            ys, xs = np.mgrid[y1:y2, x1:x2]
            u, v = self.to_source(xs.astype(np.float64), ys.astype(np.float64))
            return self._bilinear_np(u, v)
        rows = []
        for sy in range(y1, y2):
            row = []
            for sx in range(x1, x2):
                u, v = self.to_source(sx, sy)
                row.append(self._bilinear_one(u, v))
            rows.append(row)
        return rows

    def _bilinear_np(self, u, v):
        x = u - 0.5
        y = v - 0.5
        x0 = np.floor(x).astype(np.int64)
        y0 = np.floor(y).astype(np.int64)
        fx = (x - x0)[..., None]
        fy = (y - y0)[..., None]
        xa = np.clip(x0, 0, self.pw - 1)
        xb = np.clip(x0 + 1, 0, self.pw - 1)
        ya = np.clip(y0, 0, self.ph - 1)
        yb = np.clip(y0 + 1, 0, self.ph - 1)
        a = self.arr
        return ((1 - fx) * (1 - fy) * a[ya, xa] + fx * (1 - fy) * a[ya, xb]
                + (1 - fx) * fy * a[yb, xa] + fx * fy * a[yb, xb])

    def perturbed(self, **kw):
        """A copy of this model with a deliberately wrong geometry (for the sensitivity report)."""
        args = dict(layer_scale=self.S, tx=self.tx, extra_ty=0.0)
        args.update(kw)
        o = Oracle(None, self.viewport, args["layer_scale"], 0.0, PARALLAX, args["tx"], self.ty + args["extra_ty"],
                   source=self.image)
        return o


def sensitivity_report(oracle, gutters, chosen, alpha, gutter_tol, tol, patch_g, patch_t):
    """How many validated gutter patches / chosen tile patches would notice each named wrong geometry. A
    perturbation that moves a tile patch's B (by more than tol after the (1-alpha) weighting) but no gutter
    patch cannot have been ruled out by the validation — reported as a WARNING, not a verdict."""
    probes = [("shift_y+30px", dict(extra_ty=30.0)), ("shift_x+30px", dict(tx=30.0)), ("layer_scale_1.0", dict(layer_scale=1.0))]
    report = {}
    log("GEOMETRY SENSITIVITY (which patches would notice a wrong model; informational)")
    for name, kw in probes:
        alt = oracle.perturbed(**kw)
        g_sens = 0
        for r in gutters:
            b2, _, _ = patch_stats(alt.patch(r["x"], r["y"], r["x"] + patch_g, r["y"] + patch_g))
            if max(abs(b2[c] - r["oracle"][c]) for c in range(3)) > gutter_tol:
                g_sens += 1
        t_sens = 0
        for r in chosen:
            b2, _, _ = patch_stats(alt.patch(r["x"], r["y"], r["x"] + patch_t, r["y"] + patch_t))
            if (1 - alpha) * max(abs(b2[c] - r["B"][c]) for c in range(3)) > tol:
                t_sens += 1
        warn = t_sens > 0 and g_sens == 0
        report[name] = {"gutter_sensitive": g_sens, "gutter_total": len(gutters), "tile_sensitive": t_sens,
                        "tile_total": len(chosen), "warning": warn}
        log("  %-16s gutter patches that would show it: %2d/%d   tile patches whose B it would move: %2d/%d%s" % (
            name, g_sens, len(gutters), t_sens, len(chosen),
            "   WARNING: unproven where it matters" if warn else ("   (the picture is flat there: B does not depend on it)"
                                                                 if t_sens == 0 else "")))
    return report


# ---------------------------------------------------------------- small stats helpers (numpy or not)
def patch_stats(pixels):
    """(mean[3], std[3], max_abs_dev_from_mean) of an (h, w, 3) patch."""
    if np is not None:
        arr = np.asarray(pixels, dtype=np.float64).reshape(-1, 3)
        mean = arr.mean(axis=0)
        std = arr.std(axis=0)
        dev = np.abs(arr - mean).max()
        return mean.tolist(), std.tolist(), float(dev)
    flat = [p for row in pixels for p in row]
    n = float(len(flat))
    mean = [sum(p[c] for p in flat) / n for c in range(3)]
    std = [math.sqrt(sum((p[c] - mean[c]) ** 2 for p in flat) / n) for c in range(3)]
    dev = max(abs(p[c] - mean[c]) for p in flat for c in range(3))
    return mean, std, dev


def shot_patch(shot, x1, y1, x2, y2):
    if np is not None:
        return shot[y1:y2, x1:x2, :]
    return [[list(shot.getpixel((x, y))[:3]) for x in range(x1, x2)] for y in range(y1, y2)]


def load_screenshot(path):
    im = Image.open(path).convert("RGB")
    if np is not None:
        return np.asarray(im, dtype=np.float64), im.size
    return im, im.size


def fmt(v):
    return "(" + ",".join("%6.1f" % c for c in v) + ")"


def hexrgb(s):
    s = s.strip().lstrip("#")
    if len(s) == 8:
        s = s[2:]
    if len(s) != 6:
        raise SystemExit("--accent wants RRGGBB hex, got %r" % s)
    return [int(s[i:i + 2], 16) for i in (0, 2, 4)]


# ---------------------------------------------------------------- gutter validation
def free_positions(viewport, rects, patch, margin):
    """Top-left corners (x, y) of every patch x patch window inside the viewport, below the status bar, that
    touches none of the (already margin-expanded) rects. numpy: every position; else an 8-px grid."""
    vx0, vy0, vx1, vy1 = viewport
    ymin = max(vy0 + STATUS_PX + margin, vy0)
    xmin = vx0 + margin
    xmax = vx1 - patch - margin   # inclusive last x
    ymax = vy1 - patch - margin
    if np is not None:
        h, w = vy1 - vy0, vx1 - vx0
        blocked = np.zeros((h, w), dtype=np.int32)
        for rx1, ry1, rx2, ry2, _ in rects:
            x1, y1 = max(rx1 - vx0, 0), max(ry1 - vy0, 0)
            x2, y2 = min(rx2 - vx0, w), min(ry2 - vy0, h)
            if x2 > x1 and y2 > y1:
                blocked[y1:y2, x1:x2] = 1
        integral = np.zeros((h + 1, w + 1), dtype=np.int64)
        integral[1:, 1:] = blocked.cumsum(0).cumsum(1)
        ys = np.arange(ymin - vy0, ymax - vy0 + 1)
        xs = np.arange(xmin - vx0, xmax - vx0 + 1)
        if len(ys) == 0 or len(xs) == 0:
            return []
        Y, X = np.meshgrid(ys, xs, indexing="ij")
        s = (integral[Y + patch, X + patch] - integral[Y, X + patch] - integral[Y + patch, X] + integral[Y, X])
        fy, fx = np.nonzero(s == 0)
        return [(int(x) + vx0, int(y) + vy0) for y, x in zip(ys[fy], xs[fx])]
    out = []
    for y in range(ymin, ymax + 1, 8):
        for x in range(xmin, xmax + 1, 8):
            if rect_free(x, y, x + patch, y + patch, rects):
                out.append((x, y))
    return out


def gutter_candidates(viewport, rects, patch, margin, cols=4, rows=6):
    """One patch per cell of a cols x rows grid over the viewport: the free position nearest the cell's centre."""
    vx0, vy0, vx1, vy1 = viewport
    cw = (vx1 - vx0) / float(cols)
    ch = (vy1 - vy0) / float(rows)
    per_cell = {}
    for x, y in free_positions(viewport, rects, patch, margin):
        cell = (int((x + patch / 2.0 - vx0) // cw), int((y + patch / 2.0 - vy0) // ch))
        cx, cy = vx0 + (cell[0] + 0.5) * cw, vy0 + (cell[1] + 0.5) * ch
        d = (x + patch / 2.0 - cx) ** 2 + (y + patch / 2.0 - cy) ** 2
        best = per_cell.get(cell)
        if best is None or d < best[0]:
            per_cell[cell] = (d, x, y)
    return [(cell, x, y) for cell, (_, x, y) in sorted(per_cell.items())]


def validate_gutters(shot, oracle, viewport, rects, patch, margin, tol, no_picture=False, black_tol=2.0):
    cands = gutter_candidates(viewport, rects, patch, margin)
    results = []
    log("GUTTER VALIDATION (%dx%d patches, tol +-%.1f per channel mean%s)" % (
        patch, patch, black_tol if no_picture else tol, ", expecting black (no picture)" if no_picture else ""))
    log("  %-8s %-14s %-24s %-24s %-24s %s" % ("cell", "xy", "screenshot", "oracle", "diff", "verdict"))
    for cell, x, y in cands:
        t_mean, t_std, _ = patch_stats(shot_patch(shot, x, y, x + patch, y + patch))
        if no_picture:
            b_mean = [0.0, 0.0, 0.0]
        else:
            b_mean, _, _ = patch_stats(oracle.patch(x, y, x + patch, y + patch))
        diff = [t_mean[c] - b_mean[c] for c in range(3)]
        ok = max(abs(d) for d in diff) <= (black_tol if no_picture else tol)
        results.append({"cell": list(cell), "x": x, "y": y, "screenshot": t_mean, "oracle": b_mean, "diff": diff,
                        "screenshot_std": t_std, "pass": ok})
        log("  %-8s %-14s %-24s %-24s %-24s %s" % ("%d,%d" % cell, "%d,%d" % (x, y), fmt(t_mean), fmt(b_mean),
                                                    fmt(diff), "ok" if ok else "MISMATCH"))
    cells = {tuple(r["cell"]) for r in results}
    spread_ok = (len(results) >= 4 and len(cells) >= 4 and len({c[0] for c in cells}) >= 2
                 and len({c[1] for c in cells}) >= 2)
    failed = [r for r in results if not r["pass"]]
    if results:
        worst = max(max(abs(d) for d in r["diff"]) for r in results)
        log("  %d patches over %d cells; worst per-channel mean diff %.2f; %d mismatch" % (
            len(results), len(cells), worst, len(failed)))
    else:
        log("  no usable gutter patch at all")
    return results, spread_ok, failed


# ---------------------------------------------------------------- tile patches
def tile_candidates(bounds, patch, step=12):
    x1, y1, x2, y2 = bounds
    w, h = x2 - x1, y2 - y1
    icon = (w if w <= h else h) * ICON_FRACTION + 2 * ICON_MARGIN_PX    # MEDIUM: width; WIDE: height (the shorter)
    icx, icy = x1 + w / 2.0, y1 + h / 2.0
    ix1, iy1, ix2, iy2 = icx - icon / 2, icy - icon / 2, icx + icon / 2, icy + icon / 2
    out = []
    for y in range(y1 + EDGE_MARGIN_PX, y2 - LABEL_BAND_PX - patch, step):
        for x in range(x1 + EDGE_MARGIN_PX, x2 - EDGE_MARGIN_PX - patch, step):
            px1, py1, px2, py2 = x, y, x + patch, y + patch
            if px1 < ix2 and px2 > ix1 and py1 < iy2 and py2 > iy1:
                continue
            out.append((x, y))
    return out, (ix1, iy1, ix2, iy2)


def select_tile_patches(shot, oracle, bounds, patch, accent, alpha, mode, max_patches, min_sep=16.0, min_eff=8.0,
                        spacing=36):
    cands, icon_rect = tile_candidates(bounds, patch)
    log("TILE PATCH SELECTION in %s (%dx%d patches; edges %d px, label band %d px, icon square %s excluded)" % (
        list(bounds), patch, patch, EDGE_MARGIN_PX, LABEL_BAND_PX, "[%d,%d]-[%d,%d]" % tuple(int(v) for v in icon_rect)))
    accepted, rejected = [], []
    for x, y in cands:
        t_mean, t_std, t_dev = patch_stats(shot_patch(shot, x, y, x + patch, y + patch))
        rec = {"x": x, "y": y, "T": t_mean, "T_std": t_std, "T_maxdev": t_dev}
        if mode == "picture":
            b_mean, b_std, b_dev = patch_stats(oracle.patch(x, y, x + patch, y + patch))
            rec["B"] = b_mean
            rec["B_std"] = b_std
            predicted_std = (1 - alpha) * (sum(b_std) / 3.0)
            predicted_dev = (1 - alpha) * b_dev
            actual_std = sum(t_std) / 3.0
            sep = max(abs(accent[c] - b_mean[c]) for c in range(3))
            rec["separation"] = sep
            if actual_std > predicted_std + 3.0 or t_dev > predicted_dev + 12.0:
                rec["reject"] = "structure: std %.2f vs predicted %.2f, max dev %.1f vs predicted %.1f" % (
                    actual_std, predicted_std, t_dev, predicted_dev)
            elif sep < min_sep or (1 - alpha) * sep < min_eff:
                rec["reject"] = "non-discriminating: max|accent-B| %.1f, (1-alpha)*sep %.1f" % (sep, (1 - alpha) * sep)
        else:
            actual_std = sum(t_std) / 3.0
            rec["separation"] = 0.0
            if actual_std > 3.0 or t_dev > 24.0:
                rec["reject"] = "structure: std %.2f, max dev %.1f (opaque plate expected flat)" % (actual_std, t_dev)
        if "reject" in rec:
            rejected.append(rec)
        else:
            accepted.append(rec)
    # Spread over the tile: the best-separated, flattest patch in each cell of a 4x4 grid first, then the rest by
    # separation, never two within `spacing` px of each other.
    x1, y1, x2, y2 = bounds
    cw, ch = (x2 - x1) / 4.0, (y2 - y1) / 4.0
    per_cell = {}
    for r in accepted:
        cell = (int((r["x"] + patch / 2.0 - x1) // cw), int((r["y"] + patch / 2.0 - y1) // ch))
        key = (-r["separation"], sum(r["T_std"]))
        if cell not in per_cell or key < per_cell[cell][0]:
            per_cell[cell] = (key, r)
    ordered = [r for _, r in sorted(per_cell.values(), key=lambda kr: kr[0])]
    ordered += [r for r in sorted(accepted, key=lambda r: (-r["separation"], r["y"], r["x"])) if r not in ordered]
    chosen = []
    for r in ordered:
        if all(abs(r["x"] - c["x"]) >= spacing or abs(r["y"] - c["y"]) >= spacing for c in chosen):
            chosen.append(r)
        if len(chosen) >= max_patches:
            break
    chosen.sort(key=lambda r: (r["y"], r["x"]))
    reasons = {}
    for r in rejected:
        key = r["reject"].split(":")[0]
        reasons[key] = reasons.get(key, 0) + 1
    log("  %d candidates: %d accepted (%d chosen), rejected %s" % (
        len(cands), len(accepted), len(chosen), ", ".join("%s x%d" % kv for kv in sorted(reasons.items())) or "none"))
    for r in rejected[:12]:
        log("    rejected %4d,%-4d %s" % (r["x"], r["y"], r["reject"]))
    if len(rejected) > 12:
        log("    ... %d more rejections (all in the JSON)" % (len(rejected) - 12))
    return chosen, rejected


# ---------------------------------------------------------------- main
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--picture", help="the shipped WebP (app/src/main/res/drawable-nodpi/preset_<name>.webp)")
    ap.add_argument("--screenshot", required=True, help="adb exec-out screencap -p of Start at rest")
    ap.add_argument("--dump", required=True, help="the uiautomator dump taken with the screenshot")
    ap.add_argument("--tile", default="slot:PEOPLE", help="the tile node, with or without the tile: prefix")
    ap.add_argument("--accent", required=True, help="the preset's accent, RRGGBB hex (the table's literal)")
    ap.add_argument("--alpha", type=float, help="the tile alpha LITERAL from the preset table (1 - 0.8 * transparency)")
    ap.add_argument("--viewport", help="x0,y0,x1,y1 of the pager page; default the dump's start_page, else 0,0,1080,2196")
    ap.add_argument("--scroll", type=float, default=0.0, help="Start's scroll in px (parallax translationY = -scroll*0.25)")
    ap.add_argument("--layer-scale", type=float, default=LAYER_SCALE, help="the graphicsLayer scale (1.3 as built)")
    ap.add_argument("--expect-opaque", action="store_true", help="Midnight: alpha 1, assert T = accent +- 2")
    ap.add_argument("--no-picture", action="store_true", help="Default: no picture; gutters (0,0,0) +- 2, T = accent +- 2")
    ap.add_argument("--tol", type=float, default=4.0, help="composite tolerance per channel (default 4)")
    ap.add_argument("--gutter-tol", type=float, default=6.0, help="gutter validation tolerance per channel mean (default 6)")
    ap.add_argument("--gutter-patch", type=int, default=5)
    ap.add_argument("--tile-patch", type=int, default=9)
    ap.add_argument("--max-patches", type=int, default=12)
    ap.add_argument("--min-patches", type=int, default=3)
    ap.add_argument("--out", help="write the JSON result here")
    a = ap.parse_args(argv)

    mode = "no_picture" if a.no_picture else ("opaque" if a.expect_opaque else "picture")
    if mode == "picture" and (a.picture is None or a.alpha is None):
        ap.error("--picture and --alpha are required unless --expect-opaque / --no-picture")
    if mode == "opaque" and a.alpha is None:
        a.alpha = 1.0
    if mode == "no_picture":
        a.alpha = 1.0
    accent = hexrgb(a.accent)
    result = {"mode": mode, "args": vars(a), "accent": accent, "numpy": np is not None}

    def finish(verdict, summary, code):
        result["verdict"] = verdict
        result["summary"] = summary
        result["exit"] = code
        if a.out:
            with open(a.out, "w") as f:
                json.dump(result, f, indent=1)
        log("RESULT %s %s" % (verdict, summary))
        return code

    nodes = parse_dump(a.dump)
    if a.viewport:
        viewport = tuple(int(v) for v in a.viewport.split(","))
        vp_source = "--viewport"
    else:
        viewport = find_viewport(nodes)
        vp_source = "dump start_page"
        if viewport is None:
            viewport = DEFAULT_VIEWPORT
            vp_source = "default (no start_page in the dump)"
    shot, size = load_screenshot(a.screenshot)
    log("picture_oracle: mode=%s screenshot=%s %dx%d dump=%s nodes=%d viewport=%s (%s) numpy=%s" % (
        mode, a.screenshot, size[0], size[1], a.dump, len(nodes), list(viewport), vp_source, np is not None))
    log("  accent=%s alpha=%.3f (LITERAL from the caller, never computed here) scroll=%.1f layer_scale=%.2f" % (
        a.accent, a.alpha, a.scroll, a.layer_scale))
    if size != (SCREEN_W, SCREEN_H):
        log("  note: screenshot is not %dx%d; the geometry defaults assume the tileshell_fhd AVD" % (SCREEN_W, SCREEN_H))
    result["viewport"] = list(viewport)

    tile_id, bounds = find_tile(nodes, a.tile)
    if bounds is None:
        return finish("UNUSABLE", "tile %s not in the dump" % a.tile, 2)
    log("  tile %s bounds %s (%d x %d px)" % (tile_id, list(bounds), bounds[2] - bounds[0], bounds[3] - bounds[1]))
    result["tile"] = {"id": tile_id, "bounds": list(bounds)}

    oracle = None
    if a.picture:
        oracle = Oracle(a.picture, viewport, a.layer_scale, a.scroll)
        result["geometry"] = oracle.describe()
        g = oracle.describe()
        log("  picture %s %dx%d: crop scale %.6f -> dst %dx%d at offset %s; layer scale %.2f about the viewport centre; "
            "translationY %.2f; total scale %.4f" % (a.picture, g["picture_size"][0], g["picture_size"][1], g["crop_scale"],
                                                     g["crop_dst"][0], g["crop_dst"][1], g["crop_offset"], g["layer_scale"],
                                                     g["translation_y"], g["total_scale"]))

    rects = occupancy(nodes, viewport, EDGE_MARGIN_PX)
    result["occupancy_rects"] = len(rects)

    # ---- 1. gutter validation
    if oracle is not None or mode == "no_picture":
        gutters, spread_ok, failed = validate_gutters(shot, oracle, viewport, rects, a.gutter_patch, EDGE_MARGIN_PX,
                                                      a.gutter_tol, no_picture=(mode == "no_picture"))
        result["gutter"] = {"patches": gutters, "spread_ok": spread_ok, "failed": len(failed)}
        if not spread_ok:
            return finish("UNUSABLE", "only %d gutter patches / poor spread (need >= 4 over >= 4 cells, 2 rows, 2 cols)"
                          % len(gutters), 2)
        if failed:
            what = "gutter is not black" if mode == "no_picture" else "picture mapping does not match the screenshot"
            return finish("FAIL", "%s at %d of %d gutter patches (worst diff %.1f)" % (
                what, len(failed), len(gutters), max(max(abs(d) for d in r["diff"]) for r in failed)), 1)
        log("  gutter validation OK: the %s" % ("gutters are black (no picture)" if mode == "no_picture"
                                                 else "oracle's geometry reproduces the screenshot's picture"))
    else:
        log("GUTTER VALIDATION skipped: --expect-opaque without --picture (pass --picture to validate the picture too)")
        result["gutter"] = None

    # ---- 2. tile patches
    chosen, rejected = select_tile_patches(shot, oracle, bounds, a.tile_patch, accent, a.alpha, mode, a.max_patches)
    result["tile_patches"] = {"chosen": chosen, "rejected": rejected}
    if len(chosen) < a.min_patches:
        return finish("UNUSABLE", "%d usable tile patches (< %d): no glyph-free, discriminating patch in %s" % (
            len(chosen), a.min_patches, tile_id), 2)
    if mode == "picture":
        result["sensitivity"] = sensitivity_report(oracle, result["gutter"]["patches"], chosen, a.alpha, a.gutter_tol,
                                                   a.tol, a.gutter_patch, a.tile_patch)

    # ---- 3. the assertion at each patch
    tol = a.tol if mode == "picture" else 2.0
    log("TILE PATCH ASSERTIONS (%s)" % ({"picture": "T = alpha*accent + (1-alpha)*B +- %.0f, and T != accent +- %.0f" % (tol, a.tol),
                                         "opaque": "T = accent +- 2 (opaque plate)",
                                         "no_picture": "T = accent +- 2 (no picture)"}[mode]))
    log("  %-10s %-24s %-24s %-24s %-24s %s" % ("xy", "T", "B", "expected", "diff", "verdict"))
    fails, control_fails = 0, 0
    for r in chosen:
        T = r["T"]
        if mode == "picture":
            B = r["B"]
            expected = [a.alpha * accent[c] + (1 - a.alpha) * B[c] for c in range(3)]
        else:
            B = None
            expected = [float(v) for v in accent]
        diff = [T[c] - expected[c] for c in range(3)]
        ok = max(abs(d) for d in diff) <= tol
        r["expected"] = expected
        r["diff"] = diff
        r["pass"] = ok
        verdict = "ok" if ok else "MISMATCH"
        if mode == "picture":
            ctrl = max(abs(T[c] - accent[c]) for c in range(3)) > a.tol
            r["control_not_accent"] = ctrl
            if not ctrl:
                control_fails += 1
                verdict += " CONTROL:T==accent"
        if not ok:
            fails += 1
        log("  %-10s %-24s %-24s %-24s %-24s %s" % ("%d,%d" % (r["x"], r["y"]), fmt(T), fmt(B) if B else "-", fmt(expected),
                                                     fmt(diff), verdict))
    n = len(chosen)
    if fails or control_fails:
        parts = []
        if fails:
            parts.append("%d of %d patches off the composite by > %.0f" % (fails, n, tol))
        if control_fails:
            parts.append("%d of %d patches equal the accent +- %.0f (picture missing?)" % (control_fails, n, a.tol))
        return finish("FAIL", "; ".join(parts), 1)
    if mode == "picture":
        return finish("PASS", "%d patches: T = %.2f*accent + %.2f*B +- %.0f and T != accent; gutters %d/%d within +-%.0f" % (
            n, a.alpha, 1 - a.alpha, tol, len(result["gutter"]["patches"]), len(result["gutter"]["patches"]), a.gutter_tol), 0)
    if mode == "opaque":
        return finish("PASS", "%d patches: T = accent +- 2%s" % (
            n, "; gutters validated against the picture" if result.get("gutter") else ""), 0)
    return finish("PASS", "%d patches: T = accent +- 2; %d gutter patches black" % (n, len(result["gutter"]["patches"])), 0)


if __name__ == "__main__":
    sys.exit(main())
