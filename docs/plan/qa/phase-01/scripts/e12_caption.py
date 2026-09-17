#!/usr/bin/env python3
"""e12_caption.py <dump.xml> <screencap.png> <new pkg>: measures the "New" caption placement in epx (px / (width/360))."""
import re, sys, colorsys
from PIL import Image
xml, png, pkg = sys.argv[1:4]
s = open(xml).read(); img = Image.open(png).convert("RGB"); W, H = img.size; k = W / 360
def rows():
    out = []
    for m in re.finditer(r'resource-id="applist_row:([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
        out.append((m.group(1),) + tuple(map(int, m.groups()[1:])))
    return out
def label(p):
    m = re.search(r'resource-id="applist_row:' + re.escape(p) + r'".*?text="([^"]+)"', s, re.S); return m.group(1) if m else "?"
def first_glyph(x1, y1, x2, y2, kind):
    cols = {}
    for x in range(x1, x2):
        for y in range(y1, y2):
            r, g, b = img.getpixel((x, y)); h, l, sat = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            hit = (l > 0.5 and sat < 0.15) if kind == "white" else (sat > 0.45 and l > 0.25)
            if hit: cols.setdefault(x, []).append(y)
    if not cols: return None
    xs = sorted(cols); run = [xs[0]]
    for x in xs[1:]:
        if x == run[-1] + 1: run.append(x)
        else: break
    ys = [y for x in run for y in cols[x]]
    return dict(left=run[0], right=run[-1], top=min(ys), bottom=max(ys) + 1)  # bottom = baseline: the ink ends one px above it
R = rows(); out = []
TEXT_X = int(50 * k)
cap = [r for r in R if r[0] == pkg][0]
p, x1, y1, x2, y2 = cap
n = first_glyph(TEXT_X, y1, x2, y2, "accent"); name = first_glyph(TEXT_X, y1, x2, y2, "white")
out.append(f"captioned row {p} '{label(p)}': bounds y {y1}-{y2} ({(y2-y1)/k:.2f} epx tall)")
out.append(f"  'New' N glyph: left x {n['left']/k:.2f} epx, cap height {(n['bottom']-n['top'])/k:.2f} epx, baseline {(n['bottom']-y1)/k:.2f} epx below row top")
out.append(f"  name first glyph: left x {name['left']/k:.2f} epx, cap {(name['bottom']-name['top'])/k:.2f} epx, baseline {(name['bottom']-y1)/k:.2f} epx below row top")
out.append(f"  name text centre vs icon centre (row top + 22 epx; R6 5.1.5: -7 +/- 1.4): {((name['bottom']+name['top'])/2 - y1)/k - 22:+.2f} epx")
out.append(f"  caption baseline - name baseline: {(n['bottom']-name['bottom'])/k:.2f} epx")
refs = []
for q, a1, b1, a2, b2 in R:
    if q == pkg: continue
    lab = label(q)
    if not lab or lab[0] not in "BDEFHIKLMNOPRTUVWXZ": continue  # capitals with a flat baseline and no descender
    g = first_glyph(TEXT_X, b1, a2, b2, "white")
    if g: refs.append((q, lab, b1, b2, g))
for q, lab, b1, b2, g in refs[:4]:
    out.append(f"reference row {q} '{lab}': {(b2-b1)/k:.2f} epx tall, name baseline {(g['bottom']-b1)/k:.2f} epx below row top, text centre vs icon centre {((g['bottom']+g['top'])/2 - b1)/k - 22:+.2f} epx (R6: +2.75 +/- 1.4), name left {g['left']/k:.2f} epx")
if refs:
    q, lab, b1, b2, g = refs[0]
    out.append(f"name moved up in the captioned row: {((g['bottom']-b1)-(name['bottom']-y1))/k:.2f} epx vs '{lab}' (R6 5.1.5: -7 - 2.75 = 9.75 +/- 1.4)")
tops = sorted(r[2] for r in R)
pitches = [(b - a) / k for a, b in zip(tops, tops[1:])]
out.append(f"row top-to-top pitches on screen (epx): {', '.join(f'{p:.2f}' for p in pitches)}")
print("\n".join(out))
