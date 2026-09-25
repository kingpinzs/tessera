#!/usr/bin/env python3
"""Phase 15's baseline layout (build task 8, C-3, T15-35).

Derived from phase 02's verified baseline (qa/phase-02/baseline_layout.json) — never phase 11's, whose fixture tiles
only phase 11's rows install (C-28) — with its addedOnce, manualSizes and slots kept, and FOUR tiles appended at named
cells under TileModel's key form app:<component>:0: the three phase 15 apps and Auxio (E0's second music app). Their
sizes go in manualSizes so no auto-sizer moves them. The file it came from is kept beside it as
baseline_layout-pre-15.json. This phase adds no addedOnce marker.

Run from anywhere: python3 docs/plan/qa/phase-15/scripts/make_baseline.py
"""
import json, os, shutil

HERE = os.path.dirname(os.path.abspath(__file__))
QA = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(QA, "phase-02", "baseline_layout.json")
OUT = os.path.join(QA, "phase-15", "baseline_layout.json")
PRE = os.path.join(QA, "phase-15", "baseline_layout-pre-15.json")

ADD = [
    # key, size, why
    ("app:app.tileshell/app.tileshell.clock.ClockActivity:0", "WIDE", "Alarms & Clock: wide shows the next-alarm face (r11/clock.md §9)"),
    ("app:app.tileshell/app.tileshell.calculator.CalculatorActivity:0", "SMALL", "Calculator: static glyph tile"),
    ("app:app.tileshell/app.tileshell.recorder.RecorderActivity:0", "SMALL", "Voice Recorder: static glyph tile"),
    ("app:org.oxycblt.auxio/org.oxycblt.auxio.MainActivity:0", "MEDIUM", "Auxio: E0's second music app (phase 10 E11)"),
]

def main():
    with open(SRC) as f:
        base = json.load(f)
    shutil.copyfile(SRC, PRE)
    keys = {e["key"] for e in base["order"]}
    for key, size, _ in ADD:
        assert key not in keys, key
        base["order"].append({"key": key, "size": size})
        if key not in base["manualSizes"]:
            base["manualSizes"].append(key)
    with open(OUT, "w") as f:
        json.dump(base, f, indent=2)
        f.write("\n")
    print(f"wrote {OUT}: {len(base['order'])} tiles ({len(ADD)} added), pre-15 copy at {PRE}")

if __name__ == "__main__":
    main()
