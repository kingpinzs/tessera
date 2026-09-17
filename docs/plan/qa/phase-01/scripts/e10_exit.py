#!/usr/bin/env python3
"""e10_exit.py <start dump.xml> <mp4> <tapped tile id> <tile_anim diagnostics>: Start exit and entrance timings (R3 A11).

Measured on tiles that do not animate on their own (live tiles in the diagnostics are left out of the bands), with
"tile colour" per pixel = the chroma max(R,G,B) - min(R,G,B), so an app's white or grey screen reads as dark whatever the accent is. Frame times come
from ffmpeg showinfo, aligned with the frames actually decoded.
- bands = distinct top edges of those static tiles (the bottom tile row is its own band);
- exit start = the frame before the first frame where a static band falls below 95 % of its resting value, or the
  gutter scale leaves 1.00; each band's fade start (< 95 %) and end (< 3 %); black = every band < 3 %
  (R3 A11: whole exit 217-267 ms, row stagger about 30 ms, each row's fade 130-170 ms, tapped tile last);
- entrance = the first frame after black (and at least 0.5 s after it) where the static bands' energy rises above
  1 %; alpha per frame = energy / resting; complete at 98 % (R3 A11: 13 frames = 217 ms);
- scale per frame from the dark gutter between the Weather and Store tiles, tracked about the page centre
  (R3 A11 exit 1.00 -> 1.56, entrance 0.78 -> 0.98 in 150 ms).
"""
import re, subprocess, sys
import numpy as np

xml, mp4, tapped, diag = sys.argv[1:5]
s = open(xml).read()
tiles = {m.group(1): tuple(int(v) for v in m.groups()[1:]) for m in re.finditer(r'resource-id="tile:([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)}
live = {m.group(1) for m in re.finditer(r"tile=(\S+) kind=", open(diag).read())}
page = re.search(r'resource-id="start_page"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
py1, py2 = int(page.group(2)), int(page.group(4)); cy = (py1 + py2) / 2; cx = 540
static = {t: b for t, b in tiles.items() if t not in live}
rows = {}
for tid, b in static.items():
    rows.setdefault("row" if tid.startswith("dock:") else b[1], []).append(b)
bands = sorted(k for k in rows if k != "row") + (["row"] if "row" in rows else [])
wt, st = tiles["shell:weather"], tiles["slot:STORE"]
x0 = (wt[2] + st[0]) / 2; yw = (wt[1] + wt[3]) / 2

W, H = 1080, 2340
info_path = f"{mp4}.showinfo.txt"
with open(info_path, "w") as info:
    p = subprocess.Popen(["ffmpeg", "-v", "info", "-i", mp4, "-vsync", "0", "-vf", "showinfo", "-pix_fmt", "rgb24", "-f", "rawvideo", "-"], stdout=subprocess.PIPE, stderr=info)
    energy, tap, scale = [], [], []
    est = 1.0
    while True:
        buf = p.stdout.read(W * H * 3)
        if len(buf) < W * H * 3: break
        fr = np.frombuffer(buf, np.uint8).reshape(H, W, 3).astype(np.int16)
        # Chroma (max channel - min channel): high on a coloured tile whatever the accent is, near zero on the white
        # or grey screens of an app underneath, so an app window still reads as "dark" here.
        acc = fr.max(axis=2) - fr.min(axis=2)
        yb = int(cy + est * (yw - cy)); xp = cx + est * (x0 - cx)
        prof = acc[max(yb - 30, 0):min(yb + 30, H), :].mean(axis=0)
        found = None
        if prof.max() <= 15:
            est = 0.8  # page dark (after the exit): the next lit frames are the entrance, which starts small
        else:
            dark = prof < 0.35 * prof.max(); runs = []; i = 0
            while i < W:
                if dark[i]:
                    j = i
                    while j + 1 < W and dark[j + 1]: j += 1
                    runs.append((i + j) / 2); i = j + 1
                else: i += 1
            near = [r for r in runs if abs(r - xp) < 60]
            if near:
                est = (min(near, key=lambda r: abs(r - xp)) - cx) / (x0 - cx); found = est
        scale.append(found)
        # fade inside each tile's current position: its rectangle scaled by this frame's scale about the page centre
        def mean_at(b):
            k = found if found is not None else est
            X1, X2 = cx + k * (b[0] - cx) + 6, cx + k * (b[2] - cx) - 6
            Y1, Y2 = cy + k * (b[1] - cy) + 6, cy + k * (b[3] - cy) - 6
            x1, x2, y1, y2 = max(int(X1), 0), min(int(X2), W), max(int(Y1), 0), min(int(Y2), int(py2))
            if (x2 - x1) * (y2 - y1) < 0.3 * (X2 - X1) * (Y2 - Y1): return np.nan  # mostly off the page
            return acc[y1:y2, x1:x2].mean()
        energy.append([np.nanmean([mean_at(b) for b in rows[k]]) if not all(np.isnan(mean_at(b)) for b in rows[k]) else np.nan for k in bands])
        tap.append(mean_at(tiles[tapped]))
    p.wait()
pts = [float(m.group(1)) for m in re.finditer(r"pts_time:([0-9.]+)", open(info_path).read())]
n = min(len(energy), len(pts)); E = np.array(energy[:n]); rest = np.median(E[:5], axis=0)
out = [f"static bands (top edge px): {bands}; resting tile colour {np.round(rest, 1).tolist()}; live tiles left out: {sorted(live)}"]
first = next(i for i in range(1, n) if np.any(np.nan_to_num(E[i], nan=0) < 0.95 * rest) or (scale[i] is not None and abs(scale[i] - 1) > 0.02))
t0 = pts[first - 1] if pts[first] - pts[first - 1] <= 0.02 else pts[first] - 1 / 60
ms = lambda i: (pts[i] - t0) * 1000
out.append(f"exit: first changed frame {first} at {pts[first]:.3f} s; start taken as {t0:.3f} s")
for k, name in enumerate(bands):
    fs = next((i for i in range(first, n) if not np.isnan(E[i, k]) and E[i, k] < 0.95 * rest[k]), None)
    fe = next((i for i in range(first, n) if np.isnan(E[i, k]) or E[i, k] < 0.03 * rest[k]), None)
    gone = ' (left the page)' if fe is not None and np.isnan(E[fe, k]) else ''
    out.append(f"  band {name}: fade starts {ms(fs):.0f} ms, below 3 % at {ms(fe):.0f} ms{gone}, fade {ms(fe) - ms(fs):.0f} ms" if fs is not None and fe is not None else f"  band {name}: not measured")
black = next(i for i in range(first, n) if np.all(np.nan_to_num(E[i], nan=0) < 0.03 * rest))
tb = next(i for i in range(first, n) if np.isnan(tap[i]) or tap[i] < 0.03 * tap[0])
out.append(f"  every static band below 3 % at {ms(black):.0f} ms (R3 A11 whole exit 217-267 ms); tapped tile {tapped} below 3 % at {ms(tb):.0f} ms")
out.append("  scale per frame: " + " ".join(f"{ms(i):.0f}:{scale[i]:.2f}" for i in range(first - 1, black + 1) if scale[i] is not None))
iv = np.diff(pts[first - 1:black + 1]) * 1000
out.append(f"  exit frame intervals: median {np.median(iv):.1f} ms, max {iv.max():.1f} ms")
es = next((i for i in range(black + 1, n) if pts[i] - pts[black] > 0.5 and np.nanmean(E[i] / rest) > 0.01), None)
if es:
    e0 = pts[es - 1] if pts[es] - pts[es - 1] <= 0.02 else pts[es] - 1 / 60
    em = lambda i: (pts[i] - e0) * 1000
    done = next(i for i in range(es, n) if np.nanmean(E[i] / rest) >= 0.98)
    out.append(f"entrance: first frame {es} at {pts[es]:.3f} s; alpha reaches 98 % at {em(done):.0f} ms (R3 A11 fade complete in 13 frames = 217 ms)")
    out.append("  alpha per frame: " + " ".join(f"{em(i):.0f}:{np.nanmean(E[i] / rest):.2f}" for i in range(es, done + 1)))
    out.append("  scale per frame: " + " ".join(f"{em(i):.0f}:{scale[i]:.2f}" for i in range(es, done + 1) if scale[i] is not None))
    iv = np.diff(pts[es - 1:done + 1]) * 1000
    out.append(f"  entrance frame intervals: median {np.median(iv):.1f} ms, max {iv.max():.1f} ms")
print("\n".join(out))
