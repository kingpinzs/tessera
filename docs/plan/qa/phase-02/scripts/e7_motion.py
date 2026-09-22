#!/usr/bin/env python3
"""
E7 (motion half): the edit-mode entry and exit timings, from screenrecord frames (PLAN RV11).

usage: e7_motion.py entry <dir>            # every usable tile, median reported
       e7_motion.py exit  <dir>

Gate findings this answers:
  - the old version searched the WHOLE scanline for the rightmost bright pixel, so it silently measured
    whichever tile happened to be rightmost rather than the one it named;
  - it measured ONE hand-picked tile, and a different legitimate choice turned a check into a FAIL, so the
    row's verdict depended on an operator decision that nothing recorded.

Now: every grid tile that yields a clean edge (its right edge is followed by background, and that edge
actually travels) is measured independently, each inside its OWN x window, and the row is judged on the
MEDIAN across tiles with the spread printed. A single flipping live tile can no longer decide the row.

Signals per tile, per frame:
  scale = the tile's right edge, to sub-pixel precision from the intensity ramp (a hard threshold on an
          antialiased edge saturates ~3 px early and hides an ease-out's tail);
  dim   = the mean brightness across that tile's own span in the same frame.

entry (R6 §1.1.8 / §1.1.9): scale settles in 417 ± 50 ms, 50 % by ≈67 ms; dimming settles in 550 ± 50 ms,
50 % by ≈83 ms, 90 % by ≈350 ms.
exit (R6 §1.5.3): scale and pitch return in 183-217 ms; undim 90 % by ≈100 ms, settled ≈300 ms.
The 150 ± 17 ms of R6 §1.5.2 is measured by the shell itself against the input event's clock and read out of
the ring buffer (e7_latency.py) — this AVD draws no touch indicator, so no frame marks the touch-up.

SETTLE is defined here as: the first time the signal enters the ±2 % band around its settled value AND stays.
2 % of a typical ~50 px swing is one pixel, the finest a recording resolves. The band is a documented
parameter of the measurement, not a free knob: it is printed in the output with every result.
"""
import os
import re
import subprocess
import sys

import numpy as np

import qa

FRAME_MS = 1000 / 60
SETTLE_BAND = 0.02


def scanline_frames(mp4, scan_y, band=8):
    """Every frame's rows around [scan_y] at FULL horizontal resolution: ([(pts, band)], band_top)."""
    info_path = f"{mp4}.showinfo.{scan_y}.txt"
    top = max(0, scan_y - band // 2)
    with open(info_path, "w") as info:
        p = subprocess.Popen(
            ["ffmpeg", "-v", "info", "-i", mp4, "-vsync", "0",
             "-vf", f"showinfo,crop=1080:{band}:0:{top},format=rgb24", "-f", "rawvideo", "-"],
            stdout=subprocess.PIPE, stderr=info)
        out = []
        while p.stdout is not None:
            buf = p.stdout.read(1080 * band * 3)
            if len(buf) < 1080 * band * 3:
                break
            out.append(np.frombuffer(buf, np.uint8).reshape(band, 1080, 3).astype(np.int16))
        p.wait()
    pts = [float(m.group(1)) for m in re.finditer(r"pts_time:([0-9.]+)", open(info_path).read())]
    n = min(len(out), len(pts))
    return list(zip(pts[:n], out[:n])), top


def crossing(times, prog, level):
    for i in range(1, len(prog)):
        if prog[i] >= level:
            t0, t1, p0, p1 = times[i - 1], times[i], prog[i - 1], prog[i]
            return ((t1 if p1 == p0 else t0 + (t1 - t0) * (level - p0) / (p1 - p0)) - times[0]) * 1000
    return None


def settle_time(times, prog, tol=SETTLE_BAND):
    """The first time the signal is inside ±tol of its settled value and never leaves again."""
    last_outside = -1
    for i, p in enumerate(prog):
        if abs(p - 1.0) > tol:
            last_outside = i
    return (times[min(last_outside + 1, len(times) - 1)] - times[0]) * 1000


def measure(mp4, rect, fx, fy):
    """Timings for one tile, or None when its edge is not clean enough to measure."""
    x1, y1, x2, y2 = rect
    cy = (y1 + y2) / 2
    scan_y = int(round((cy + (fy + (cy - fy) * 0.9)) / 2))
    lo = max(0, int(min(x1, fx + (x1 - fx) * 0.9)) - 10)
    hi = min(1080, int(max(x2, fx + (x2 - fx) * 0.9)) + 10)
    fs, band_top = scanline_frames(mp4, scan_y)
    if len(fs) < 10:
        return None
    times = np.array([t for t, _ in fs])
    row_i = min(max(0, scan_y - band_top), fs[0][1].shape[0] - 1)
    edge, level = [], []
    for _, fr in fs:
        bright = fr[row_i].max(axis=1).astype(float)
        window = bright[lo:hi]
        nz = np.nonzero(window > 20)[0]
        if len(nz) == 0 or nz.max() >= (hi - lo) - 2:
            return None   # no edge inside the window, or the tile runs past it: not this tile's edge
        hard = int(nz.max()) + lo
        plate = float(np.median(bright[max(0, hard - 30):max(1, hard - 8)])) or 1.0
        edge.append(max(0, hard - 6) + float(np.clip(bright[max(0, hard - 6):hard + 6] / plate, 0.0, 1.0).sum()))
        left = hard
        while left > lo and bright[left - 1] > 20:
            left -= 1
        inset = int((hard - left) * 0.25)
        a, b = left + inset, hard - inset
        level.append(float(bright[a:b + 1].mean()) if b > a else 0.0)
    edge, level = np.array(edge), np.array(level)
    rest_e, rest_l = float(np.median(edge[:5])), float(np.median(level[:5]))
    end_e, end_l = float(np.median(edge[-5:])), float(np.median(level[-5:]))
    if abs(end_e - rest_e) < 8 or abs(end_l - rest_l) < 8:
        return None   # this tile barely moved or barely dimmed: it cannot time the transition
    start = next((i for i in range(len(edge)) if abs(edge[i] - rest_e) >= 0.5 or abs(level[i] - rest_l) > 2), None)
    if start is None:
        return None
    t = times[start:]
    pe = (edge[start:] - rest_e) / (end_e - rest_e)
    pl = (level[start:] - rest_l) / (end_l - rest_l)
    return {
        "swing_px": abs(end_e - rest_e),
        "scale_half": crossing(t, pe, 0.5), "scale_settle": settle_time(t, pe),
        "dim_half": crossing(t, pl, 0.5), "dim_90": crossing(t, pl, 0.9), "dim_settle": settle_time(t, pl),
    }


mode, d = sys.argv[1], sys.argv[2]
runs = int(sys.argv[sys.argv.index("--runs") + 1]) if "--runs" in sys.argv else 1
mp4s = [f"{d}/e7_{mode}.mp4"] + [f"{d}/e7_{mode}_{i}.mp4" for i in range(2, runs + 1)]
mp4s = [m for m in mp4s if os.path.exists(m)]
bounds = {t: b for t, b in qa.dump_tiles(f"{d}/normal.xml").items() if not t.startswith("dock:")}
fx, fy = qa.PANEL_W * 0.5, 2340 * 0.475

per_tile = {}
for run, mp4 in enumerate(mp4s, start=1):
    for tid, rect in sorted(bounds.items()):
        m = measure(mp4, rect, fx, fy)
        if m:
            per_tile[f"{tid} (run {run})"] = m
if not per_tile:
    sys.exit(f"no tile in {d}/normal.xml gave a measurable edge in {mp4s}")

print(f"{mode}: {len(mp4s)} recording(s), {len(per_tile)} tile-measurements, settle band ±{SETTLE_BAND:.0%}")
for tid, m in per_tile.items():
    print(f"  {tid:32s} swing {m['swing_px']:5.1f} px  scale 50 % {m['scale_half']:6.1f}  settle {m['scale_settle']:6.1f}"
          f"   dim 50 % {m['dim_half']:6.1f}  90 % {m['dim_90']:6.1f}  settle {m['dim_settle']:6.1f}")


def med(key):
    vals = [m[key] for m in per_tile.values() if m[key] is not None]
    return float(np.median(vals)), min(vals), max(vals)


results = []
if mode == "entry":
    for key, label, want, tol in (
        ("scale_settle", "scale settles in", 417, 50),
        ("scale_half", "scale 50 % at", 67, 33),
        ("dim_settle", "dimming settles in", 550, 50),
        ("dim_half", "dimming 50 % at", 83, 33),
        ("dim_90", "dimming 90 % at", 350, 60),
    ):
        m, lo_v, hi_v = med(key)
        print(f"  median {label}: {m:.1f} ms (tiles span {lo_v:.1f}..{hi_v:.1f})")
        results.append((label, qa.check(f"{label} (median of {len(per_tile)} tiles)", m, want, tol, " ms")))
else:
    m, lo_v, hi_v = med("scale_settle")
    print(f"  median scale return: {m:.1f} ms (tiles span {lo_v:.1f}..{hi_v:.1f})")
    results.append(("exit scale", qa.check_range("scale and pitch return in", m, 183 - FRAME_MS, 217 + FRAME_MS, " ms")))
    for key, label, want, tol in (("dim_90", "undim 90 % at", 100, 40), ("dim_settle", "undim settles at", 300, 60)):
        m, lo_v, hi_v = med(key)
        print(f"  median {label}: {m:.1f} ms (tiles span {lo_v:.1f}..{hi_v:.1f})")
        results.append((label, qa.check(f"{label} (median of {len(per_tile)} tiles)", m, want, tol, " ms")))

qa.report(results)
