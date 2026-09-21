#!/usr/bin/env python3
"""
E7 (motion half): the edit-mode entry and exit timings, from screenrecord frames (PLAN RV11).

usage: e7_motion.py entry <mp4>
       e7_motion.py exit  <mp4> <tap x> <tap y>

Per frame, two whole-screen signals are taken at half resolution:
  area   = how many pixels are tile (not page background)  -> the contraction, R6 §1.1.2-§1.1.3
  level  = the mean brightness of those pixels             -> the dimming,     R6 §1.1.5
Both are normalised between their resting and settled values, so each reads 0 -> 1 across the transition.

entry (R6 §1.1.8 / §1.1.9): the scale settles in 417 ± 50 ms with 50 % done by ≈67 ms; the dimming settles in
550 ± 50 ms with 50 % by ≈83 ms and 90 % by ≈350 ms.

exit (R6 §1.5.2 / §1.5.3): with "show taps" on, the touch indicator's last frame IS the touch-up, so the
150 ± 17 ms before the exit starts is measured rather than inferred; scale and pitch then return in 183-217 ms
and the undim is 90 % done by ≈100 ms, settled by ≈300 ms.
"""
import subprocess
import sys

import numpy as np

import qa

SCALE = 2
W, H = 1080 // SCALE, 2340 // SCALE
FRAME_MS = 1000 / 60


def frames(mp4):
    """(pts_seconds, frame) for every frame ffmpeg actually decodes, at half resolution, RGB."""
    info_path = mp4 + ".showinfo.txt"
    with open(info_path, "w") as info:
        p = subprocess.Popen(
            ["ffmpeg", "-v", "info", "-i", mp4, "-vsync", "0", "-vf", f"showinfo,scale={W}:{H},format=rgb24",
             "-f", "rawvideo", "-"],
            stdout=subprocess.PIPE, stderr=info)
        out = []
        while True:
            buf = p.stdout.read(W * H * 3)
            if len(buf) < W * H * 3:
                break
            out.append(np.frombuffer(buf, np.uint8).reshape(H, W, 3).astype(np.int16))
        p.wait()
    import re
    pts = [float(m.group(1)) for m in re.finditer(r"pts_time:([0-9.]+)", open(info_path).read())]
    n = min(len(out), len(pts))
    return list(zip(pts[:n], out[:n]))


def signals(fs):
    area, level = [], []
    for _, fr in fs:
        m = fr.max(axis=2)
        mask = m > 20
        area.append(int(mask.sum()))
        level.append(float(m[mask].mean()) if mask.any() else 0.0)
    return np.array(area, dtype=float), np.array(level, dtype=float)


def normalise(v, rest, settled):
    if abs(settled - rest) < 1e-6:
        return np.zeros_like(v)
    return (v - rest) / (settled - rest)


def crossing(times, prog, level):
    """First time the progress reaches [level], linearly interpolated, relative to times[0]."""
    for i in range(1, len(prog)):
        if prog[i] >= level:
            t0, t1 = times[i - 1], times[i]
            p0, p1 = prog[i - 1], prog[i]
            if p1 == p0:
                return (t1 - times[0]) * 1000
            return (t0 + (t1 - t0) * (level - p0) / (p1 - p0) - times[0]) * 1000
    return None


def settle_time(times, prog, tol=0.02):
    """When the progress last leaves the ±tol band around 1.0, i.e. when it has settled."""
    last = 0
    for i, p in enumerate(prog):
        if abs(p - 1.0) > tol:
            last = i
    return (times[min(last + 1, len(times) - 1)] - times[0]) * 1000


mode = sys.argv[1]
mp4 = sys.argv[2]
fs = frames(mp4)
if len(fs) < 10:
    sys.exit(f"only {len(fs)} frames decoded from {mp4}")
times = np.array([t for t, _ in fs])
area, level = signals(fs)
results = []

if mode == "entry":
    # The transition is the run between the resting head and the settled tail of the recording.
    rest_a, rest_l = float(np.median(area[:5])), float(np.median(level[:5]))
    end_a, end_l = float(np.median(area[-5:])), float(np.median(level[-5:]))
    start = next((i for i in range(len(area)) if abs(area[i] - rest_a) > max(200, 0.002 * rest_a)
                  or abs(level[i] - rest_l) > 1.0), None)
    if start is None:
        sys.exit("no change found in the recording: did edit mode engage?")
    t = times[start:]
    pa = normalise(area[start:], rest_a, end_a)
    pl = normalise(level[start:], rest_l, end_l)
    print(f"entry: {len(fs)} frames, transition starts at frame {start} (pts {times[start]:.3f}s)")
    print(f"  scale   50 % at {crossing(t, pa, 0.5):.0f} ms, settled at {settle_time(t, pa):.0f} ms")
    print(f"  dimming 50 % at {crossing(t, pl, 0.5):.0f} ms, 90 % at {crossing(t, pl, 0.9):.0f} ms, "
          f"settled at {settle_time(t, pl):.0f} ms")
    results.append(("scale settle", qa.check("scale settles in", settle_time(t, pa), 417, 50, " ms")))
    results.append(("scale half", qa.check("scale 50 % at", crossing(t, pa, 0.5), 67, 33, " ms")))
    results.append(("dim settle", qa.check("dimming settles in", settle_time(t, pl), 550, 50, " ms")))
    results.append(("dim half", qa.check("dimming 50 % at", crossing(t, pl, 0.5), 83, 33, " ms")))
    results.append(("dim 90", qa.check("dimming 90 % at", crossing(t, pl, 0.9), 350, 60, " ms")))

elif mode == "exit":
    tap_x, tap_y = int(sys.argv[3]) // SCALE, int(sys.argv[4]) // SCALE
    # The "show taps" indicator: bright pixels around the tap point, on the page background.
    box = [fr[max(0, tap_y - 40):tap_y + 40, max(0, tap_x - 40):tap_x + 40] for _, fr in fs]
    bright = np.array([int((b.max(axis=2) > 180).sum()) for b in box])
    touching = bright > 30
    if not touching.any():
        sys.exit("no touch indicator found: is `settings put system show_touches 1` set, and is the tap point "
                 "on the page background?")
    up = int(np.nonzero(touching)[0][-1])  # the last frame the indicator is drawn = the touch-up
    rest_a, rest_l = float(np.median(area[:5])), float(np.median(level[:5]))
    end_a, end_l = float(np.median(area[-5:])), float(np.median(level[-5:]))
    start = next((i for i in range(up + 1, len(area))
                  if abs(area[i] - rest_a) > max(200, 0.002 * rest_a) or abs(level[i] - rest_l) > 1.0), None)
    if start is None:
        sys.exit("the exit never changed the screen after the touch-up")
    latency = (times[start] - times[up]) * 1000
    t = times[start:]
    pa = normalise(area[start:], rest_a, end_a)
    pl = normalise(level[start:], rest_l, end_l)
    print(f"exit: touch-up at frame {up} (pts {times[up]:.3f}s), first changed frame {start} (pts {times[start]:.3f}s)")
    print(f"  latency {latency:.0f} ms; scale settles at {settle_time(t, pa):.0f} ms; "
          f"undim 90 % at {crossing(t, pl, 0.9):.0f} ms, settled at {settle_time(t, pl):.0f} ms")
    # One frame of quantisation sits on each side of the latency (the indicator's last frame and the first
    # changed frame are both ±1 frame at 60 fps), so the tolerance carries that.
    results.append(("exit latency", qa.check("exit starts after touch-up", latency, 150, 17 + FRAME_MS, " ms")))
    results.append(("exit scale", qa.check_range("scale and pitch return in", settle_time(t, pa), 183 - FRAME_MS, 217 + FRAME_MS, " ms")))
    results.append(("undim 90", qa.check("undim 90 % at", crossing(t, pl, 0.9), 100, 40, " ms")))
    results.append(("undim settle", qa.check("undim settles at", settle_time(t, pl), 300, 60, " ms")))
else:
    sys.exit("mode must be entry or exit")

qa.report(results)
