#!/usr/bin/env python3
"""shade_xy.py <dump.xml> expand|cancel — the tap point of the Copying notification's expander, or of its Cancel action."""
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
def nodes(pred):
    for m in re.finditer(r"<node[^>]*>", x):
        s = m.group(0)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if b and pred(s):
            l, t, r, bt = map(int, b.groups())
            yield (l + r) // 2, (t + bt) // 2
if sys.argv[2] == "cancel":
    for p in nodes(lambda s: re.search(r'text="Cancel"', s, re.I) or 'content-desc="Cancel"' in s): print(*p); break
else:
    title = next(nodes(lambda s: 'text="Copying files' in s or 'text="Moving files' in s or 'text="Extracting' in s), None)
    if title:
        best = min(nodes(lambda s: 'expand_button' in s), key=lambda p: abs(p[1] - title[1]), default=None)
        if best: print(*best)
