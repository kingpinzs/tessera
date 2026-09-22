#!/usr/bin/env python3
"""E11: are Android's own status and nav bar windows visible?

    barvis.py <dumpsys-window.txt>

Exits 0 only when BOTH bars are present in the dump and BOTH report isReadyForDisplay()=false and
isVisible=false. A bar that is missing from the dump entirely is NOT a pass: that would make the check
vacuous, which is exactly the kind of fail-open assertion phase 02's gate was returned for.

The AVD calls its navigation bar window "Taskbar"; a phone calls it "NavigationBar". Either name
counts, and the one that was found is printed, so the log says which window was actually measured.
"""
import re
import sys

text = open(sys.argv[1], encoding="utf-8", errors="replace").read()

BLOCK = r"Window #\d+ Window\{[0-9a-f]+ u\d+ %s\}:(.*?)(?=\n\s*Window #\d+ Window\{|\Z)"


def read(names):
    for name in names:
        m = re.search(BLOCK % re.escape(name), text, re.S)
        if not m:
            continue
        block = m.group(1)
        ready = re.search(r"isReadyForDisplay\(\)=(\w+)", block)
        visible = re.search(r"\bisVisible=(\w+)", block)
        if not ready or not visible:
            return name, False, "the window is in the dump but reports neither field"
        ok = ready.group(1) == "false" and visible.group(1) == "false"
        return name, ok, f"isReadyForDisplay()={ready.group(1)} isVisible={visible.group(1)}"
    return "/".join(names), False, "no such window in the dump"


status = read(["StatusBar"])
nav = read(["NavigationBar", "Taskbar"])

print("; ".join(f"{name}: {why}" for name, _, why in (status, nav)))
ok = status[1] and nav[1]
if not ok:
    for name, good, why in (status, nav):
        if not good:
            print(f"  {name} is not proven hidden: {why}")
sys.exit(0 if ok else 1)
