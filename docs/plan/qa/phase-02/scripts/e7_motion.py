#!/usr/bin/env python3
"""
E7 (motion half): the edit-mode entry and exit timings, from screenrecord frames (PLAN RV11).

usage: e7_motion.py entry <dir> <static tile id>
       e7_motion.py exit  <dir> <static tile id> [<tap x> <tap y>]

Two signals per frame, both taken from a STATIC tile (one with no live faces), so a tile flipping on its own
timer cannot be read as the entry:

  scale  = the x of the rightmost tile pixel on a scanline through that tile — the contraction moves it, a flip
           (a vertical squash) and a peek (a vertical slide) do not;
  dim    = the brightness of a small box that stays inside that tile throughout the move.

entry (R6 §1.1.8 / §1.1.9): the scale settles in 417 ± 50 ms with 50 % done by ≈67 ms; the dimming settles in
550 ± 50 ms with 50 % by ≈83 ms and 90 % by ≈350 ms.

exit (R6 §1.5.2 / §1.5.3): scale and pitch return in 183-217 ms, the undim is 90 % done by ≈100 ms and settled
by ≈300 ms. The 150 ± 17 ms between the touch-up and the first changed frame needs the touch-up's own frame:
pass the tap point and, if Android's "show taps" indicator is in the recording, it is measured; when the
indicator is not in the frames the latency is reported as UNMEASURED here and belongs to phone row P2, which
the phase doc already assigns for tolerances of 17 ms or less.
"""
import re
import subprocess
import sys

import numpy as np

import qa

SCALE = 2
W, H = 1080 // SCALE, 2340 // SCALE
FRAME_MS = 1000 / 60


def scanline_frames(mp4, scan_y, band=8):
    """
    Every frame's rows around [scan_y], at FULL horizontal resolution: (pts, band) pairs.

    Full resolution matters. Decoded at half scale the tile edge moves in 2-px steps, 3.6 % of the whole swing,
    so the tail of an ease-out is invisible and the settle reads ~100 ms early. Cropping to a few rows keeps
    that precision affordable.
    """
    info_path = mp4 + ".showinfo.txt"
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
            if p1 == p0:
                return (t1 - times[0]) * 1000
            return (t0 + (t1 - t0) * (level - p0) / (p1 - p0) - times[0]) * 1000
    return None


def settle_time(times, prog, tol=0.02):
    # "settled" = the last frame whose value is still further than [tol] of the whole swing from the end. On a
    # ~53 px edge swing 2 % is one pixel, which is all a recording can resolve.
    last = 0
    for i, p in enumerate(prog):
        if abs(p - 1.0) > tol:
            last = i
    return (times[min(last + 1, len(times) - 1)] - times[0]) * 1000


mode, d, static_id = sys.argv[1], sys.argv[2], sys.argv[3]
mp4 = f"{d}/e7_{mode}.mp4"
bounds = qa.dump_tiles(f"{d}/normal.xml")
if static_id not in bounds:
    sys.exit(f"{static_id} is not in {d}/normal.xml")
x1, y1, x2, y2 = bounds[static_id]
cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
fx, fy = qa.PANEL_W * 0.5, 2340 * 0.475
# The scanline and the sample box both sit where the tile is at rest AND after the contraction.
scan_y_px = int(round((cy + (fy + (cy - fy) * 0.9)) / 2))

fs, band_top = scanline_frames(mp4, scan_y_px)
if len(fs) < 10:
    sys.exit(f"only {len(fs)} frames decoded from {mp4}")
times = np.array([t for t, _ in fs])
row_in_band = min(max(0, scan_y_px - band_top), fs[0][1].shape[0] - 1)
edge, level = [], []
for _, fr in fs:
    row = fr[row_in_band]
    bright = row.max(axis=1).astype(float)
    nz = np.nonzero(bright > 20)[0]
    hard = int(nz.max()) if len(nz) else 0
    # Sub-pixel edge: a hard threshold on an antialiased edge stops moving two or three pixels before the tile
    # does, which cuts an ease-out's tail off. The covered fraction of the pixels around the edge gives the
    # edge's position to a tenth of a pixel, so the tail is visible.
    lo = max(0, hard - 6)
    plate = float(np.median(bright[max(0, hard - 30):max(1, hard - 8)])) or 1.0
    coverage = float(np.clip(bright[lo:hard + 6] / plate, 0.0, 1.0).sum())
    right = lo + coverage
    edge.append(right)
    # The dim is read off the tile's own rectangle in THIS frame — the tile is moving, so a fixed sample box
    # would mix the contraction into the dimming.
    left = hard
    while left > 0 and bright[left - 1] > 20:
        left -= 1
    inset = int((hard - left) * 0.25)
    a, b = left + inset, hard - inset
    level.append(float(bright[a:b + 1].mean()) if b > a else 0.0)
edge = np.array(edge)
level = np.array(level)

rest_e, rest_l = float(np.median(edge[:5])), float(np.median(level[:5]))
end_e, end_l = float(np.median(edge[-5:])), float(np.median(level[-5:]))
if abs(end_e - rest_e) < 4 or abs(end_l - rest_l) < 4:
    sys.exit(f"the recording does not contain the transition (edge {rest_e}->{end_e}, level {rest_l:.1f}->{end_l:.1f})")
start = next((i for i in range(len(edge)) if abs(edge[i] - rest_e) >= 0.5 or abs(level[i] - rest_l) > 2), None)
if start is None:
    sys.exit("no change found in the recording")
t = times[start:]
pe = (edge[start:] - rest_e) / (end_e - rest_e)
pl = (level[start:] - rest_l) / (end_l - rest_l)
results = []
print(f"{mode}: {len(fs)} frames, scanline y={scan_y_px}px, transition starts at frame {start} (pts {times[start]:.3f}s)")
print(f"  edge {rest_e * SCALE:.0f} -> {end_e * SCALE:.0f} px, level {rest_l:.1f} -> {end_l:.1f}")

if mode == "entry":
    print(f"  scale   50 % at {crossing(t, pe, 0.5):.0f} ms, settled at {settle_time(t, pe):.0f} ms")
    print(f"  dimming 50 % at {crossing(t, pl, 0.5):.0f} ms, 90 % at {crossing(t, pl, 0.9):.0f} ms, settled at {settle_time(t, pl):.0f} ms")
    results.append(("scale settle", qa.check("scale settles in", settle_time(t, pe), 417, 50, " ms")))
    results.append(("scale half", qa.check("scale 50 % at", crossing(t, pe, 0.5), 67, 33, " ms")))
    results.append(("dim settle", qa.check("dimming settles in", settle_time(t, pl), 550, 50, " ms")))
    results.append(("dim half", qa.check("dimming 50 % at", crossing(t, pl, 0.5), 83, 33, " ms")))
    results.append(("dim 90", qa.check("dimming 90 % at", crossing(t, pl, 0.9), 350, 60, " ms")))
else:
    print(f"  scale settled at {settle_time(t, pe):.0f} ms; undim 90 % at {crossing(t, pl, 0.9):.0f} ms, settled at {settle_time(t, pl):.0f} ms")
    results.append(("exit scale", qa.check_range("scale and pitch return in", settle_time(t, pe), 183 - FRAME_MS, 217 + FRAME_MS, " ms")))
    results.append(("undim 90", qa.check("undim 90 % at", crossing(t, pl, 0.9), 100, 40, " ms")))
    results.append(("undim settle", qa.check("undim settles at", settle_time(t, pl), 300, 60, " ms")))
    if len(sys.argv) >= 6:
        # The touch-up's own frame: Android's "show taps" indicator, looked for in a band at the TAP's row (the
        # signal band above is at the tile's row and never contains it).
        tap_x, tap_y = int(sys.argv[4]), int(sys.argv[5])
        tap_fs, tap_top = scanline_frames(mp4, tap_y, band=16)
        bright = np.array([int((fr[:, max(0, tap_x - 50):tap_x + 50].max(axis=2) > 170).sum()) for _, fr in tap_fs])
        touching = bright > 25
        if touching.any():
            up = int(np.nonzero(touching)[0][-1])
            up_t = tap_fs[up][0]
            latency = (times[start] - up_t) * 1000
            print(f"  touch indicator last seen at pts {up_t:.3f}s -> latency {latency:.0f} ms")
            results.append(("exit latency", qa.check("exit starts after touch-up", latency, 150, 17 + FRAME_MS, " ms")))
        else:
            print("  exit latency: UNMEASURED — Android's touch indicator is not in these frames "
                  f"(max {bright.max()} bright pixels at the tap point); the 150 ± 17 ms row is phone row P2")

qa.report(results)
