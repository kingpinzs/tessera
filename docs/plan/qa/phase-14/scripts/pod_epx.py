#!/usr/bin/env python3
"""E15 (RV10): every pod node's bounds in epx (px / (portrait width / 360)) against a base dump, +-1 epx.

  pod_epx.py <base.xml> <base-width> <dump.xml>:<width> ...   exit 0 when every dump passes
A pod node is any resource-id starting "pod" (pod_bay, pod_bay_title, pod:<id>, pod_header:<id>, pod_row:<id>:<n>,
pod_empty:<id>, ...), phase 01 E3's method (epx_compare.py) on the pod bay's nodes.
"""
import re, sys

def load(fn, width):
    s = open(fn, encoding="utf-8", errors="replace").read()
    k = width / 360.0
    out = {}
    for m in re.finditer(r'resource-id="(pod[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
        out[m.group(1)] = tuple(int(v) / k for v in m.groups()[1:])
    return out

base = load(sys.argv[1], int(sys.argv[2]))
ok = bool(base)
print(f"base {sys.argv[1]}: {len(base)} pod nodes")
for spec in sys.argv[3:]:
    fn, w = spec.rsplit(":", 1)
    t = load(fn, int(w))
    missing = sorted(set(base) - set(t))
    extra = sorted(set(t) - set(base))
    diffs = {k: max(abs(a - b) for a, b in zip(base[k], t[k])) for k in base if k in t}
    worst_k = max(diffs, key=diffs.get) if diffs else None
    worst = diffs[worst_k] if worst_k else None
    verdict = "PASS" if (not missing and worst is not None and worst <= 1.0) else "FAIL"
    ok &= verdict == "PASS"
    print(f"{fn}: {len(t)}/{len(base)} pod nodes, missing={missing}, extra={extra}, "
          f"max |d| = {None if worst is None else round(worst, 3)} epx ({worst_k}) -> {verdict}")
sys.exit(0 if ok else 1)
