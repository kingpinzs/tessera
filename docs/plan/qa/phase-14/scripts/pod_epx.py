#!/usr/bin/env python3
"""E15 (RV10): every pod node's bounds in epx (px / (portrait width / 360)) against a base dump.

  pod_epx.py <base.xml> <base-width> <dump.xml>:<width> ...   exit 0 when every dump passes
A pod node is any resource-id starting "pod" (pod_bay, pod_bay_title, pod:<id>, pod_header:<id>, pod_row:<id>:<n>,
pod_empty:<id>, ...), phase 01 E3's method (epx_compare.py) on the pod bay's nodes.

The rule (Jeremy's ruling Q-E15 (a), 2026-09-30, INDEX Change Log): left, top and bottom of every node within 1 epx; the
right edge within 1 epx too, except on a node that carries text, where it is the text's own width — the font engine's,
at that pixel size — and has 1.5 epx (run 1: boxes within 0.33 epx, text right edges up to 1.42 under wm size).
"""
import re, sys

EDGE = 1.0
TEXT_RIGHT = 1.5

def load(fn, width):
    s = open(fn, encoding="utf-8", errors="replace").read()
    k = width / 360.0
    out = {}
    for m in re.finditer(r"<node [^>]*>", s):
        node = m.group(0)
        rid = re.search(r'resource-id="(pod[^"]*)"', node)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if not rid or not b:
            continue
        text = re.search(r'text="([^"]*)"', node)
        out[rid.group(1)] = (tuple(int(v) / k for v in b.groups()), bool(text and text.group(1)))
    return out

base = load(sys.argv[1], int(sys.argv[2]))
ok = bool(base)
print(f"base {sys.argv[1]}: {len(base)} pod nodes, {sum(1 for _, t in base.values() if t)} with text")
for spec in sys.argv[3:]:
    fn, w = spec.rsplit(":", 1)
    t = load(fn, int(w))
    missing = sorted(set(base) - set(t))
    extra = sorted(set(t) - set(base))
    worst_box, worst_box_k, worst_text, worst_text_k, bad = 0.0, None, 0.0, None, []
    for k in base:
        if k not in t:
            continue
        (b, is_text), (c, _) = base[k], t[k]
        d = [abs(x - y) for x, y in zip(b, c)]
        box = max(d[0], d[1], d[3]) if is_text else max(d)
        if box > worst_box:
            worst_box, worst_box_k = box, k
        if is_text and d[2] > worst_text:
            worst_text, worst_text_k = d[2], k
        if box > EDGE or (is_text and d[2] > TEXT_RIGHT):
            bad.append(k)
    verdict = "PASS" if (not missing and not bad and len(t) >= len(base)) else "FAIL"
    ok &= verdict == "PASS"
    print(f"{fn}: {len(t)}/{len(base)} pod nodes, missing={missing}, extra={extra}, "
          f"boxes max |d| = {round(worst_box, 3)} epx ({worst_box_k}) <= {EDGE}, "
          f"text right edges max |d| = {round(worst_text, 3)} epx ({worst_text_k}) <= {TEXT_RIGHT}, over={bad} -> {verdict}")
sys.exit(0 if ok else 1)
