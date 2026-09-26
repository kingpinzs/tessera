#!/usr/bin/env python3
"""L13-3: per-trial timeline from the diagnostic build's [l13] ring lines (diag-instrumentation.diff).

  diag_parse.py <trials dir> [...]

For each trial tN.ring: the Back that closed the band (uptime), the next down (uptime of the event) and who received
it (the band's scrim, or the row), when the app handled it, and when the band left composition. All times are SystemClock.uptimeMillis().
"""
import glob
import os
import re
import sys


def parse(path):
    back = left = None
    down = None  # (who, eventUptime)
    for line in open(path):
        m = re.search(r"\[l13\] (.*)", line)
        if not m:
            continue
        msg = m.group(1)
        if msg.startswith("back closes band"):
            back = int(re.search(r"now=(\d+)", msg).group(1))
            left = down = None
        elif back is not None and msg.startswith("band left composition") and left is None:
            left = int(re.search(r"now=(\d+)", msg).group(1))
        elif back is not None and down is None and (msg.startswith("scrim down") or msg.startswith("row down")):
            down = ("scrim" if msg.startswith("scrim") else "row", int(re.search(r"eventUptime=(\d+)", msg).group(1)),
                    int(re.search(r"now=(\d+)", msg).group(1)))
    return back, left, down


def main():
    rows = []
    for d in sys.argv[1:]:
        for p in sorted(glob.glob(os.path.join(d, "t*.ring")), key=lambda s: int(re.search(r"t(\d+)\.ring", s).group(1))):
            back, left, down = parse(p)
            if back is None or down is None:
                continue
            rows.append((os.path.relpath(p), down[0], down[1] - back, (left - back) if left else None,
                         (down[2] - left) if left else None, down[2] - down[1]))
    print(f"{'trial':56s} {'down went to':12s} {'back->down event':>16s} {'back->band gone':>16s} {'band gone->down handled':>24s} {'event->handled':>15s}")
    for r in rows:
        print(f"{r[0]:56s} {r[1]:12s} {str(r[2]) + 'ms':>16s} {str(r[3]) + 'ms':>16s} {str(r[4]) + 'ms':>24s} {str(r[5]) + 'ms':>15s}")
    scrim = [r for r in rows if r[1] == "scrim"]
    row = [r for r in rows if r[1] == "row"]
    print(f"\n{len(rows)} trials: the down went to the row {len(row)} times, to the band's scrim {len(scrim)} times")
    if scrim:
        print("every scrim down was handled before the band left composition:", all(r[4] is not None and r[4] < 0 for r in scrim))
    if row:
        print("every row down was handled after the band left composition:", all(r[4] is not None and r[4] >= 0 for r in row))
    lat = sorted(r[3] for r in rows if r[3] is not None)
    if lat:
        print(f"Back -> band left composition: min {lat[0]} ms, median {lat[len(lat) // 2]} ms, max {lat[-1]} ms (n={len(lat)})")


if __name__ == "__main__":
    main()
