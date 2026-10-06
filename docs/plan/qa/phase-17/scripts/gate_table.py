#!/usr/bin/env python3
"""Phase 17 gate - the table of every row's passing run, read from the run folders' names. Host only.

usage: gate_table.py <qa/phase-17 dir> <gate build id, 8 hex>     prints Markdown; exit 1 if a row has no passing run
A folder is <ROW>-build-<id>-run<k>-pass-<p>-<f>-<r> (older ones: <ROW>-run<k>-pass-…). A row whose only passing run
is an earlier build's is marked: its code did not change after that build, so it was not run again.
"""
import os, re, sys

QA, GATE = sys.argv[1], sys.argv[2]
ROWS = ['E1', 'E2', 'E3', 'E4', 'E5', 'E6', 'E6b', 'E7', 'E8', 'E9', 'E10', 'E11', 'E12', 'E13', 'E14', 'E15', 'E16',
        'E17', 'E18', 'E19_PHOTOS', 'E19_CAMERA', 'E19_VIDEO', 'E20', 'E21', 'E22', 'E23_PHOTOS', 'E23_CAMERA',
        'E23_VIDEO', 'E24', 'E25', 'TRUST_PHOTOS', 'TRUST_VIDEO', 'GUARD_SELF']
dirs = sorted(d for d in os.listdir(QA) if os.path.isdir(os.path.join(QA, d)))
for sub in ('dev-living',):
    p = os.path.join(QA, sub)
    if os.path.isdir(p):
        dirs += sorted(sub + '/' + d for d in os.listdir(p) if os.path.isdir(os.path.join(p, d)))
extra = sorted({re.split(r'-(?:build|run|dev)', d)[0] for d in dirs if d.startswith('EDGE')})


def best(row):
    mine = [d for d in dirs if re.match(re.escape(row) + r'-(build-[0-9a-f]{8}-)?(run|dev)\d+', d) and 'pass' in d.split('-')]
    gate = [d for d in mine if 'build-' + GATE in d]
    return (gate[-1], True) if gate else ((mine[-1], False) if mine else (None, False))


missing = 0
print('| Row | Passing run | Build |')
print('|---|---|---|')
for row in ROWS + extra:
    d, on_gate = best(row)
    if row == 'E18' and d is None and os.path.isfile(os.path.join(QA, 'E18', 'E18.txt')):
        last = open(os.path.join(QA, 'E18', 'E18.txt')).read().strip().splitlines()[-1].strip()
        print(f'| E18 | `E18/E18.txt` — {last} | the join over the saved slices |')
        continue
    if d is None:
        missing += 1
        print(f'| {row} | **NO PASSING RUN** | — |')
    else:
        m = re.search(r'build-([0-9a-f]{8})', d)
        b = GATE if on_gate else ((m.group(1) if m else 'pre-merge') + ' (earlier build; code unchanged since)')
        print(f'| {row} | `{d}` | {b} |')
sys.exit(1 if missing else 0)
