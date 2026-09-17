#!/usr/bin/env python3
"""E3: compare every tagged tile's bounds in epx (px / (portrait width / 360)) against a base dump."""
import re, sys
def load(fn, width):
    s = open(fn).read(); k = width / 360.0; out = {}
    for m in re.finditer(r'resource-id="((?:tile|dock):[^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
        out[m.group(1)] = tuple(int(v) / k for v in m.groups()[1:])
    return out
base = load(sys.argv[1], int(sys.argv[2]))
ok = True
for spec in sys.argv[3:]:
    fn, w = spec.split(':'); t = load(fn, int(w))
    missing = sorted(set(base) - set(t))
    worst = max((max(abs(a - b) for a, b in zip(base[k], t[k])) for k in base if k in t), default=None)
    verdict = 'PASS' if (not missing and worst is not None and worst <= 1.0) else 'FAIL'
    ok &= verdict == 'PASS'
    print(f"{fn}: {len(t)}/{len(base)} tiles, missing={missing}, max |d| = {None if worst is None else round(worst, 3)} epx -> {verdict}")
sys.exit(0 if ok else 1)
