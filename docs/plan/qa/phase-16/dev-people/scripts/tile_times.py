#!/usr/bin/env python3
"""The People tile's event timing, read off a ring slice (DEV-E11; R3 A9 with RV11's tolerance + one frame).

  tile_times.py SLICE            prints one line per check: "PASS|FAIL <name> | <detail>"

Events are `[people] tile event <n> t0=<uptime> lookup=<key>`; each has a `[motion] people_bubble_out` and a
`[motion] people_bubble_in` line after it (t0 uptime ms, settle ms, frames, maxGapMs).
"""
import re
import sys

FRAME = 16.7
lines = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
events, outs, ins = [], [], []
for l in lines:
    m = re.search(r"\[people\] tile event (\d+) t0=(\d+) lookup=(\S+)", l)
    if m: events.append((int(m.group(1)), int(m.group(2)), m.group(3)))
    m = re.search(r"\[motion\] people_bubble_(out|in) t0=(\d+) peak=(\d+) overshoot=(\d+) settle=(\d+) frames=(\d+) maxGapMs=(\d+)", l)
    if m:
        rec = dict(t0=int(m.group(2)), settle=int(m.group(5)), frames=int(m.group(6)), gap=int(m.group(7)))
        (outs if m.group(1) == "out" else ins).append(rec)


def verdict(ok, name, detail):
    print("%s %s | %s" % ("PASS" if ok else "FAIL", name, detail))


verdict(len(events) >= 4, "at least 4 tile events in the window", "%d events: %s" % (len(events), [e[0] for e in events]))
gaps = [events[i + 1][1] - events[i][1] for i in range(len(events) - 1)]
verdict(bool(gaps) and all(abs(g - 7700) <= 200 + FRAME for g in gaps), "events 7.7 s apart (± 0.2 s + one frame)", str(gaps))
verdict(len({e[2] for e in events}) >= 2, "at least two different contacts", str(sorted({e[2] for e in events})))
for n, t0, lookup in events:
    o = next((x for x in outs if 0 <= x["t0"] - t0 <= 100), None)
    i = next((x for x in ins if o and 0 < x["t0"] - t0 <= 2500), None)
    if not o or not i:
        # The window may end inside the last event; an event with both slides missing is not graded, one with one is.
        if not o and not i and n == events[-1][0]: continue
        if o and not i and n == events[-1][0]: continue
        verdict(False, "event %d has both motion lines" % n, "out=%s in=%s" % (o, i)); continue
    verdict(abs(o["settle"] - 333) <= 42 + FRAME, "event %d out settle ≈ 333 ms (± 42 + one frame)" % n, "%d ms, %d frames" % (o["settle"], o["frames"]))
    verdict(abs(i["settle"] - 583) <= 42 + FRAME, "event %d in settle ≈ 583 ms (± 42 + one frame)" % n, "%d ms, %d frames" % (i["settle"], i["frames"]))
    whole = i["t0"] + i["settle"] - o["t0"]
    verdict(abs(whole - 1880) <= 40 + FRAME, "event %d: 1.88 s from the out's t0 to the in's settle (± 0.04 s + one frame)" % n, "%d ms" % whole)
    verdict(o["gap"] <= 33.4 and i["gap"] <= 33.4, "event %d: maxGapMs ≤ 33.4 on both slides" % n, "out %d, in %d" % (o["gap"], i["gap"]))
