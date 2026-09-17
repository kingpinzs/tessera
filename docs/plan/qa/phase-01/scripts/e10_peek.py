#!/usr/bin/env python3
"""e10_peek.py <mp4> <x1> <y1> <x2> <y2>: peek slide travel per frame in the tile, from the test image's blue (0,120,215) rows.
Down slide (photo coming in from the top): travel = blue rows from the top / tile height. Up slide (photo leaving upward): 1 - that."""
import subprocess, sys, numpy as np
mp4 = sys.argv[1]; x1, y1, x2, y2 = map(int, sys.argv[2:6])
x1, y1 = x1 + x1 % 2, y1 + y1 % 2; w, h = (x2 - x1) // 2 * 2, (y2 - y1) // 2 * 2  # yuv420 crops snap to even sizes
pts = [float(l.strip().strip(",")) for l in subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "frame=best_effort_timestamp_time", "-of", "csv=p=0", mp4], capture_output=True, text=True).stdout.split() if l.strip()]
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", mp4, "-vsync", "0", "-vf", f"crop={w}:{h}:{x1}:{y1}", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True).stdout
n = len(raw) // (w * h * 3); f = np.frombuffer(raw, np.uint8)[: n * w * h * 3].reshape(n, h, w, 3).astype(int)
band = f[:, :, int(w * 0.86):w - 6, :]  # right band: clear of the centred circle and of the left-aligned label
blue = (band[..., 2] > 150) & (band[..., 0] < 90) & (band[..., 1] > 70) & (band[..., 1] < 170)
rowblue = blue.mean(axis=2) > 0.8
inner = rowblue[:, 2:h - 2]  # the crop's outer rows can hold the gutter
frac = np.round(inner.sum(axis=1) / inner.shape[1], 3)  # share of the tile that shows the photo (it is one contiguous block while sliding)
events, i = [], 1
while i < n:
    if frac[i] != frac[i - 1]:
        j = i
        # the slow tail can repeat a row count for a frame or two: the slide ends when nothing moves for 0.1 s
        while True:
            k = next((m for m in range(j + 1, n) if frac[m] != frac[m - 1]), None)
            if k is None or pts[k] - pts[j] > 0.1: break
            j = k
        events.append((i - 1, j)); i = j + 1
    else: i += 1
print(f"frames {n}, tile {w}x{h} px")
for a, b in events:
    start_f, end_f = frac[a], frac[b]
    if abs(end_f - start_f) < 0.5 or b - a < 3: continue
    down = end_f > start_f
    trav = [(pts[k], (frac[k] if down else 1 - frac[k])) for k in range(a, b + 1)]
    t0 = trav[1][0] - min(trav[1][0] - trav[0][0], 1 / 60)  # movement began within one frame before the first moved frame
    def cross(v):
        for (ta, pa), (tb, pb) in zip(trav, trav[1:]):
            if pa < v <= pb: return (ta + (tb - ta) * (v - pa) / (pb - pa) - t0) * 1000
    done = next(((t - t0) * 1000 for t, p in trav if p >= 0.9999), None)  # the last row gone
    iv = np.diff([t for t, _ in trav[1:]]) * 1000
    ms = lambda v: "n/a" if v is None else f"{v:.0f} ms"
    print(f"{'down' if down else 'up  '} slide at {trav[1][0]:.3f}s: 50% {ms(cross(0.5))}, 90% {ms(cross(0.9))}, gone {ms(done)}; frames {len(trav)-1}, median frame interval {np.median(iv):.1f} ms (max {iv.max():.1f}), travel {trav[0][1]:.3f} -> {trav[-1][1]:.3f}")
