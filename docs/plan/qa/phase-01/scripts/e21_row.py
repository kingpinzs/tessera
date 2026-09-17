#!/usr/bin/env python3
"""E21: bottom tile row geometry from a uiautomator dump (1080-px-wide panel, 360-epx canvas)."""
import re, sys
s = open(sys.argv[1]).read(); W = 1080; k = W / 360
def nodes(prefix):
    return {m.group(1): tuple(map(int, m.groups()[1:])) for m in re.finditer(r'resource-id="(' + re.escape(prefix) + r'[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)}
dock = nodes('tile:dock:'); grid = nodes('tile:slot:'); grid.update(nodes('tile:shell:'))
nav = nodes('w10m_nav_bar')
left, right, gutter = W * 13 / 1440, W * 21 / 1440, W * 17.5 / 1440
medium = (W - left - right - gutter * 2) / 3; small = (medium + gutter) / 2 - gutter
want = ['tile:dock:slot:PHONE', 'tile:dock:slot:MESSAGING', 'tile:dock:slot:CAMERA']
ok = sorted(dock) == sorted(want)
print('row tiles:', sorted(dock), 'expected', want, '->', 'PASS' if ok else 'FAIL')
if not dock:
    sys.exit(1)
xs = sorted(dock.values())
widths = [b[2] - b[0] for b in xs]; heights = [b[3] - b[1] for b in xs]
exp_w = (W - left - right - gutter * 2) / 3
exp_h = small * 1.5
wok = all(abs(w - exp_w) / k <= 1 for w in widths); hok = all(abs(h - exp_h) / k <= 1 for h in heights)
print('widths epx', [round(w / k, 2) for w in widths], 'expected', round(exp_w / k, 2), '±1 ->', 'PASS' if wok else 'FAIL')
print('heights epx', [round(h / k, 2) for h in heights], 'expected 1.5 small tiles', round(exp_h / k, 2), '±1 ->', 'PASS' if hok else 'FAIL')
span_ok = abs(xs[0][0] - left) / k <= 1 and abs((W - right) - xs[-1][2]) / k <= 1
print('row spans margin to margin: left', round(xs[0][0] / k, 2), 'right edge', round(xs[-1][2] / k, 2), 'expected', round(left / k, 2), round((W - right) / k, 2), '->', 'PASS' if span_ok else 'FAIL')
navtop = list(nav.values())[0][1] if nav else None
gap = (navtop - xs[0][3]) if navtop else None
gok = gap is not None and abs(gap - gutter) / k <= 1
print('gap row bottom -> nav bar top epx', None if gap is None else round(gap / k, 2), 'expected one gutter', round(gutter / k, 2), '->', 'PASS' if gok else 'FAIL')
dup = [g for g in grid if g.split(':')[-1] in ('PHONE', 'MESSAGING', 'CAMERA')]
print('Phone/Messaging/Camera also in grid:', dup, '->', 'PASS' if not dup else 'FAIL')
sys.exit(0 if ok and wok and hok and span_ok and gok and not dup else 1)
