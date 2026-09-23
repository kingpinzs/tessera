#!/usr/bin/env bash
# J3 — an open folder at the end of Start sits ABOVE the fixed rows and is scrolled into view (Jeremy,
# 2026-09-22, on the phone: "When the folder is open it goes underneath the bottom line instead of over
# and does not scroll when its to far down").
#
# Phase 01's bottom-row decision (2026-09-17): "the Start grid scrolls above it and its content ends above
# the row so no tile hides behind it". The fixed stack at the bottom of the page is the bottom tile row
# and, since 2026-09-22, the last-opened app's tile above it. This row seeds a Start taller than the
# screen (every tile wide) with a WIDE folder of four small tiles LAST (a medium one packs beside a wide
# tile at the top), scrolls to the end, and checks:
#   1. the folder tile clears the fixed stack at the end of the scroll;
#   2. opened, the band's bottom rule and its last member clear the fixed stack too;
#   3. the band is on screen, not scrolled off the top.
# The layout is saved first and restored at the end (RV12). Built on phase 03's audited floor (lib.sh).
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/layout.sh"

row_begin J3 "an open folder at the end of Start: above the fixed rows, scrolled into view"
D="$ROW_DIR/j3.xml"
saved="$ROW_DIR/.layout_before.json"; layout_save "$saved"
# Built from the phone's OWN current layout, so the shell has nothing to add back on start (addedOnce is
# kept), and every size is marked hand-set so the auto-sizer leaves the tall layout alone (run 2 seeded
# from the baseline file: the shell re-added Tess and re-sized the tiles, and Start was not tall).
python3 - "$saved" "$ROW_DIR/tall.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
members = ["slot:STORE", "slot:MAPS", "slot:MUSIC", "shell:settings"]
d["order"] = [dict(o, size="WIDE") for o in d["order"] if o["key"] not in members]
d["order"].append({"key": "folder:j3", "size": "WIDE"})  # WIDE: a medium one packs beside a wide tile at the TOP (run 4)
d["folders"] = [f for f in d.get("folders", []) if f["id"] != "j3"] + \
    [{"id": "j3", "name": "J3", "members": [{"key": k, "size": "SMALL"} for k in members]}]
d["manualSizes"] = sorted({o["key"] for o in d["order"]})
json.dump(d, open(sys.argv[2], "w"))
PY
# Seed and PROVE it took. Android restarts the home app the instant it is force-stopped, so a shell can
# come back with the old layout in memory before the new file lands (run 1 dumped the old Start). Kill it
# again after the write, then require the seeded folder on screen.
seed() { # file
  local i
  for i in 1 2 3; do
    adb shell am force-stop $PKG
    adb push "$1" /data/local/tmp/j3_layout.json >/dev/null
    adb shell 'run-as app.tileshell sh -c "cat /data/local/tmp/j3_layout.json > files/start_layout.json"'
    adb shell am force-stop $PKG; sleep 1
    adb shell input keyevent KEYCODE_HOME; sleep 4
    dump_ui "$ROW_DIR/.seed.xml"
    [ "$(has_node "$ROW_DIR/.seed.xml" tile:shell:weather)" = yes ] && \
      python3 - "$1" <<'PY2' && return 0
import json, subprocess, sys
want = json.load(open(sys.argv[1]))
got = json.loads(subprocess.run(["adb", "shell", "run-as", "app.tileshell", "cat", "files/start_layout.json"], capture_output=True, text=True).stdout)
sys.exit(0 if [o["key"] for o in got["order"]] == [o["key"] for o in want["order"]] and got.get("folders") == want.get("folders") else 1)
PY2
    note "seed attempt $i: the shell did not come up on the seeded layout"
  done
  return 1
}
seed "$ROW_DIR/tall.json" && note "seeded: every tile wide, folder j3 last (verified on disk after the shell restarted)" || log "could not seed the tall layout"
for _ in 1 2 3 4 5; do adb shell input swipe 540 1700 540 500 250; sleep 0.6; done
sleep 1.5
dump_ui "$D"; screencap "$ROW_DIR/j3_end.png"

geom() { # dump -> "fixed_top folder_bottom band_top band_bottom last_member_bottom recent"
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
def b(n):
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds")))
    return x1, y1, x2, y2
tops, recent = [], "no"
for n in root.iter("node"):
    rid = n.get("resource-id", "")
    if rid.startswith("tile:dock:"):
        tops.append(b(n)[1])
    if rid == "recent_app_row":
        for c in n.iter("node"):
            if c.get("resource-id", "").startswith("tile:"):
                tops.append(b(c)[1]); recent = c.get("resource-id")
def one(rid):
    for n in root.iter("node"):
        if n.get("resource-id") == rid: return b(n)
    return None
f = one("tile:folder:j3"); bt = one("folder_band_top:j3"); bb = one("folder_band_bottom:j3")
mem = [b(n)[3] for n in root.iter("node") if n.get("resource-id", "").startswith("tile:member:")]
print(min(tops) if tops else "none", f[3] if f else "none", bt[1] if bt else "none", bb[3] if bb else "none",
      max(mem) if mem else "none", recent)
PY
}
read -r FIXED FOLDER _ _ _ RECENT <<< "$(geom "$D")"
note "end of scroll: top of the fixed stack $FIXED px (last-opened tile: $RECENT), folder tile bottom $FOLDER px"
assert_eq "1. at the end of the scroll the folder tile clears the fixed rows (bottom <= $FIXED)" "yes" \
  "$(python3 -c "print('yes' if '$FOLDER'!='none' and '$FIXED'!='none' and int('$FOLDER') <= int('$FIXED') else 'no')")"

tap_node "$D" tile:folder:j3; sleep 2.5
dump_ui "$D"; screencap "$ROW_DIR/j3_open.png"
read -r FIXED _ BTOP BBOT MEM RECENT <<< "$(geom "$D")"
note "folder open: fixed stack top $FIXED px, band top $BTOP px, band bottom rule $BBOT px, last member bottom $MEM px"
assert_eq "2a. the open band's bottom rule clears the fixed rows" "yes" \
  "$(python3 -c "print('yes' if '$BBOT'!='none' and int('$BBOT') <= int('$FIXED') else 'no')")"
assert_eq "2b. the band's last member tile clears the fixed rows" "yes" \
  "$(python3 -c "print('yes' if '$MEM'!='none' and int('$MEM') <= int('$FIXED') else 'no')")"
assert_eq "3. the band's top is on screen" "yes" \
  "$(python3 -c "print('yes' if '$BTOP'!='none' and int('$BTOP') >= 0 else 'no')")"

seed "$saved" && note "restored: the layout from before the row (verified on disk after the shell restarted)"
row_end
