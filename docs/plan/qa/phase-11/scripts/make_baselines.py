#!/usr/bin/env python3
"""make_baselines.py: phase 11's six baselines, derived from phase 02's (T11-3, C-3, T11-18, T11-35).

Every file keeps phase 02's slots, dock and addedOnce markers (this phase adds none), and marks EVERY tile's size
hand-set (manualSizes) so the use-based auto-sizer cannot reshape it. The fixture tile (tileclient-a, MEDIUM) sits
in the MIDDLE column of the second row (first fit after PEOPLE, BROWSER, MAIL on row 1 and PHOTOS on row 2), clear
of both screen edges, the status bar and the bottom row; E1 asserts that cell before anything else.
"""
import copy, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.dirname(HERE)
A = "app:app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity:0"
B = "app:app.tileshell.testclient.b/app.tileshell.testclient.VerbActivity:0"
base = json.load(open(os.path.join(OUT, "baseline_layout-pre-11.json")))

def t(key, size): return {"key": key, "size": size}

def finish(d):
    keys = {o["key"] for o in d["order"]} | set(d["dock"])
    for f in d["folders"]:
        keys |= {m["key"] for m in f["members"]}
    d["manualSizes"] = sorted(keys)
    return d

folder_qa = {"id": "qa", "name": "QA", "members": [t("slot:STORE", "SMALL"), t("slot:MAPS", "SMALL")]}
main = copy.deepcopy(base)
main["order"] = [
    t("slot:PEOPLE", "MEDIUM"), t("slot:BROWSER", "MEDIUM"), t("slot:MAIL", "MEDIUM"),
    t("slot:PHOTOS", "MEDIUM"), t(A, "MEDIUM"), t(B, "SMALL"),
    t("slot:CALENDAR", "WIDE"), t("shell:weather", "WIDE"), t("folder:qa", "MEDIUM"),
    t("slot:MUSIC", "SMALL"), t("shell:settings", "SMALL"), t("shell:cortana", "MEDIUM"),
]
main["folders"] = [folder_qa]
variants = {"baseline_layout.json": finish(main)}

# The fixture in the bottom tile row (E7 line arrangement, E12).
v = copy.deepcopy(main); v["order"] = [o for o in v["order"] if o["key"] != A]; v["dock"] = v["dock"] + [A]
variants["baseline_layout-bottomrow.json"] = finish(v)

# The fixture WIDE in the 2-column grid's first row (show more tiles is turned off by the row, a pref): its top
# pair cannot clear the status bar without landing on it, so the burst takes a line (build finding: a full-width
# WIDE tile further down keeps a clamped corner, never on the tile — INDEX Change Log).
v = copy.deepcopy(main); v["order"] = [t(A, "WIDE")] + [o for o in v["order"] if o["key"] != A]
variants["baseline_layout-wide.json"] = finish(v)

# The fixture inside folder:qa (E7's band case; E5's folder-name strip).
v = copy.deepcopy(main); v["order"] = [o for o in v["order"] if o["key"] != A]
v["folders"] = [{"id": "qa", "name": "QA", "members": folder_qa["members"] + [t(A, "MEDIUM")]}]
variants["baseline_layout-band.json"] = finish(v)

# Tall: every tile AFTER the fixture set made WIDE, so the fixture keeps its cell and Start scrolls (T11-18).
v = copy.deepcopy(main)
after = False
for o in v["order"]:
    if after: o["size"] = "WIDE"
    if o["key"] == B: after = True
variants["baseline_layout-tall.json"] = finish(v)

# The fixture first, at the grid's top-left (T11-35).
v = copy.deepcopy(main); v["order"] = [t(A, "MEDIUM")] + [o for o in v["order"] if o["key"] != A]
variants["baseline_layout-topleft.json"] = finish(v)

for name, d in variants.items():
    json.dump(d, open(os.path.join(OUT, name), "w"))
    print(name, [o["key"].split("/")[0].replace("app:app.tileshell.testclient.", "fixture-") + "/" + o["size"][0] for o in d["order"]], "dock", len(d["dock"]))
