#!/usr/bin/env python3
"""e10_timing.py <E10 dir>: live tile events from the timing screenrecords (E10, R3 A7 / A8, RV11).

For each tile in timing_start.xml, each real frame's mean absolute grey-level change inside the tile (inset 3 px) is
computed at half resolution; a run of changed frames (gaps under 0.1 s) is one animation event. Event start = pts of
its first changed frame; duration = last changed pts - first changed pts + one 60-fps frame. Intervals are taken
within a segment only. Events are matched to the shell's tile_anim diagnostics for the kind (FLIP / CROSSFADE / PEEK).
"""
import math, re, statistics, subprocess, sys, itertools, collections
import numpy as np

D = sys.argv[1]
FRAME = 1 / 60
SCALE = 2
xml = open(f"{D}/timing_start.xml").read()
tiles = {}
for m in re.finditer(r'resource-id="tile:([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    x1, y1, x2, y2 = (int(v) // SCALE for v in m.groups()[1:])
    tiles[m.group(1)] = (x1 + 3, y1 + 3, x2 - 3, y2 - 3)

kinds = {}
for l in open(f"{D}/timing_tile_anim_diag.txt"):
    m = re.search(r"tile=(\S+) kind=(\S+)", l)
    if m: kinds[m.group(1)] = m.group(2)

def segment(mp4):
    W, H = 1080 // SCALE, 2340 // SCALE
    # showinfo prints the pts of every frame actually written, so times stay aligned with the raw frames even where
    # the VFR stream repeats a timestamp
    info = open(f"{mp4}.showinfo.txt", "w")
    p = subprocess.Popen(["ffmpeg", "-v", "info", "-i", mp4, "-vsync", "0", "-vf", f"showinfo,scale={W}:{H},format=gray", "-f", "rawvideo", "-"], stdout=subprocess.PIPE, stderr=info)
    prev = None; diffs = collections.defaultdict(list); k = 0
    while True:
        buf = p.stdout.read(W * H)
        if len(buf) < W * H: break
        fr = np.frombuffer(buf, np.uint8).reshape(H, W).astype(np.int16)
        if prev is not None:
            for tid, (x1, y1, x2, y2) in tiles.items():
                diffs[tid].append(float(np.abs(fr[y1:y2, x1:x2] - prev[y1:y2, x1:x2]).mean()))
        prev = fr; k += 1
    p.wait(); info.close()
    pts = [float(m.group(1)) for m in re.finditer(r"pts_time:([0-9.]+)", open(f"{mp4}.showinfo.txt").read())]
    n = min(k, len(pts))
    events = {}
    for tid in tiles:
        d = diffs[tid][: n - 1]; ev = []
        # skip the encoder's settling: 0.5 s after the first frame it writes past the opening frame (a whole-frame change there)
        i = next((m for m in range(len(d)) if pts[m + 1] - pts[1] >= 0.5), len(d))
        while i < len(d):
            if d[i] > 0.5:
                j = i
                while True:
                    nxt = next((m for m in range(j + 1, len(d)) if d[m] > 0.5), None)
                    if nxt is None or pts[nxt + 1] - pts[j + 1] > 0.1: break
                    j = nxt
                ev.append((pts[i + 1], pts[j + 1] - pts[i + 1] + FRAME, j - i + 1))
                i = j + 1
            else:
                i += 1
        events[tid] = ev
    return events, pts[n - 1] - pts[0], pts

SEGMENTS = sys.argv[2].split(",") if len(sys.argv) > 2 else ["1", "2", "3"]
segs = [segment(f"{D}/e10_timing_{s}.mp4") for s in SEGMENTS]
out = []
BANDS = {"FLIP": (4.96, 0.22), "PEEK": (4.96, 0.22), "CROSSFADE": (4.4, 0.4)}
DUR = {"FLIP": (108, 17), "CROSSFADE": (367, 17)}
live = []
for tid in tiles:
    evs = [e for s in segs for e in s[0][tid]]
    kind = kinds.get(tid, "none")
    if kind == "none":
        # not a live tile in the shell's diagnostics: any change here is a defect to report, not a timer to measure
        out.append(f"{tid}: static tile, {len(evs)} changes {[round(e[0], 3) for e in evs][:5]}"); continue
    live.append(tid)
    ivs = [b[0] - a[0] for s in segs for a, b in zip(s[0][tid], s[0][tid][1:])]
    if len(ivs) < 2:
        out.append(f"{tid} [{kind}]: {len(evs)} events, too few intervals"); continue
    mean = statistics.mean(ivs)
    line = f"{tid} [{kind}]: {len(evs)} events, {len(ivs)} intervals, mean {mean:.3f} s, sd {statistics.pstdev(ivs):.3f}, min {min(ivs):.3f}, max {max(ivs):.3f}"
    if kind in BANDS:
        c, tol = BANDS[kind]; lo, hi = c - tol - FRAME, c + tol + FRAME
        line += f"; band {lo:.3f}-{hi:.3f}: {'PASS' if lo <= mean <= hi and len(ivs) >= 20 else 'FAIL'}"
    durs = [e[1] * 1000 for e in evs]
    line += f"; duration median {statistics.median(durs):.0f} ms (min {min(durs):.0f}, max {max(durs):.0f})"
    if kind in DUR:
        c, tol = DUR[kind]; lim = tol + FRAME * 1000
        ok = sum(1 for x in durs if abs(x - c) <= lim)
        line += f", within {c}+/-{lim:.0f} ms: {ok}/{len(durs)}"
    out.append(line)

seqs = {tid: tuple(round((b[0] - a[0]) * 60) for s in segs for a, b in zip(s[0][tid], s[0][tid][1:])) for tid in live}
same = [(a, b) for a, b in itertools.combinations(live, 2) if seqs[a] == seqs[b]]
out.append(f"identical interval sequences (in frames) between live tiles: {len(same)} {same}")

coinc = 0
for s in segs:
    for a, b in itertools.combinations(live, 2):
        sa = {round(e[0], 3) for e in s[0][a]}; coinc += sum(1 for e in s[0][b] if round(e[0], 3) in sa)
Dlen = sum(s[1] for s in segs)
allivs = [b[0] - a[0] for tid in live for s in segs for a, b in zip(s[0][tid], s[0][tid][1:])]
T = statistics.mean(allivs); pairs = len(live) * (len(live) - 1) // 2
lam = pairs * Dlen / (60 * T * T); limit = lam + 3 * math.sqrt(lam) + 3
out.append(f"same-frame event starts summed over {pairs} tile pairs: {coinc}; capture {Dlen:.1f} s, mean period {T:.3f} s, lambda {lam:.2f}, limit {limit:.2f}: {'PASS' if coinc <= limit else 'FAIL'}")
fps = []
for s in segs:
    p = s[2]; iv = np.diff(p) * 1000
    fps.append(f"median frame interval {np.median(iv):.1f} ms over {len(p)} frames")
out.append("capture: " + "; ".join(fps))
print("\n".join(out))
