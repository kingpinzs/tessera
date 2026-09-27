#!/usr/bin/env python3
"""L13-3 window proof: from DIAG3 ring slices, every Back that closed the band opens a window that ends when the band
leaves composition. A row down dispatched inside a window is a down that, before the fix, landed on the stale scrim.
Prints each window and a summary. Usage: diag_window.py slice.txt [slice.txt ...]"""
import re, sys

NOW = re.compile(r'now=(\d+)')
windows = []
for path in sys.argv[1:]:
    cur = None
    pending = None  # an in-window down waiting for its outcome
    for line in open(path, encoding='utf-8', errors='replace'):
        if '[l13]' not in line:
            continue
        m = NOW.search(line)
        now = int(m.group(1)) if m else None
        if 'back closes band' in line:
            cur = {'file': path, 't0': now, 'unplaced': None, 'left': None, 'row': [], 'scrim': []}
            windows.append(cur)
        elif 'layer unplaced' in line and cur and cur['left'] is None and cur['unplaced'] is None:
            cur['unplaced'] = now
        elif 'band left composition' in line and cur and cur['left'] is None:
            cur['left'] = now
        elif 'row down' in line:
            if cur and cur['left'] is None:
                pending = {'now': now, 'outcome': None}
                cur['row'].append(pending)
            else:
                pending = None
        elif 'row outcome' in line and pending is not None:
            pending['outcome'] = line.split('row outcome ')[1].split()[0]
            pending = None
        elif 'scrim down' in line and cur and cur['left'] is None:
            cur['scrim'].append(now)

inw = [d for w in windows for d in w['row']]
held = [d for d in inw if d['outcome'] == 'hold']
scrim = sum(len(w['scrim']) for w in windows)
for w in windows:
    rel = lambda t: '-' if t is None else f"+{t - w['t0']}ms"
    rows = ', '.join(f"down {rel(d['now'])} -> {d['outcome']}" for d in w['row']) or 'no down in window'
    print(f"{w['file'].split('/')[-2]} back@{w['t0']}: unplaced {rel(w['unplaced'])}, band left {rel(w['left'])}; "
          f"{rows}; scrim downs {len(w['scrim'])}")
print(f"SUMMARY windows={len(windows)} in-window row downs={len(inw)} held={len(held)} "
      f"other={[d['outcome'] for d in inw if d['outcome'] != 'hold']} stale-scrim downs in windows={scrim}")
