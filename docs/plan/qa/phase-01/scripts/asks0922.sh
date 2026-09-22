#!/usr/bin/env bash
# Jeremy's 2026-09-22 asks, on the device:
#   "Tile flips seem a bit abrupt"                          -> Motion.flipScale, unit-tested; feel is his call
#   "they shouldn't do it one after another each should be
#    on their own timer"                                    -> spread start phases, asserted here
#   "Contacts should be called people"                      -> the slot's name is the tile's name
#   "there should be games and office folders"              -> seeded from each app's declared category
#
# The folders cannot be seeded ON THIS AVD and that is the correct behaviour, not a gap in the row: the
# rules refuse to guess, and not one of the F-Droid fixtures declares android:appCategory. What is
# asserted here is the safety half — the scan runs and makes NO folder rather than an empty or invented
# one. A populated folder needs a phone with Play-installed apps, which is a NEEDS-HUMAN row.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

show_start() { adb shell am force-stop $PKG; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 6; }

label_of() { # label_of <xml> <tile id>
  python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'resource-id="' + re.escape(sys.argv[2]) + r'"[^>]*content-desc="([^"]*)"', s)
print(m.group(1) if m else "")
PY
}

row_begin ASKS0922 "the slot's own name, spread flip timers, and the category folders"

show_start
dump "$ROW_DIR/start.xml"
adb exec-out screencap -p > "$ROW_DIR/start.png"

# ---- "Contacts should be called people" ------------------------------------------------------------
# The slot IS the W10M tile, so it keeps its own name whichever app Android resolves it to. Before this,
# the resolved app's label won and the tiles read Contacts / WebView Browser Tester / OsmAnd~ / Messages.
assert_eq "the People tile is called People" "People" "$(label_of "$ROW_DIR/start.xml" tile:slot:PEOPLE)"
assert_eq "the Browser tile is called Browser" "Browser" "$(label_of "$ROW_DIR/start.xml" tile:slot:BROWSER)"
assert_eq "the Maps tile is called Maps" "Maps" "$(label_of "$ROW_DIR/start.xml" tile:slot:MAPS)"
assert_eq "the Messaging tile is called Messaging" "Messaging" "$(label_of "$ROW_DIR/start.xml" tile:dock:slot:MESSAGING)"
# A tile pinned to a specific app still carries that app's name; only slots changed.
assert_eq "the Weather tile is untouched" "Weather" "$(label_of "$ROW_DIR/start.xml" tile:shell:weather)"

# ---- "each should be on their own timer" -----------------------------------------------------------
# Watched for a full band and a bit, then the FIRST face change of each tile is compared. Before this the
# phases were drawn at random out of one band and two tiles landed 400 ms apart on Jeremy's phone; they
# are now spread across it, so the closest pair is a whole slice apart.
command sleep 22
diag tile_anim > "$ROW_DIR/anim.txt"
note "face changes seen: $(grep -c 'tile=' "$ROW_DIR/anim.txt")"
GAP="$(python3 "$HERE/first_gap.py" < "$ROW_DIR/anim.txt")"
note "closest first-flip gap between two tiles: [${GAP:-none}] ms"
assert_ne "at least two tiles cycled, so there is something to compare" "" "$GAP"
# Every cycling tile takes its own slot on one comb of TileTiming.COMB_MS (4960 ms), so the closest two
# can ever be is one slice of it. With the tiles this Start carries that slice is ~380 ms; 300 ms is the
# floor the row insists on, and the first run of this row measured 244 ms before the comb existed.
[ -n "$GAP" ] && assert_eq "no two tiles take their turn together" "ok" \
  "$([ "$GAP" -ge 300 ] && echo ok || echo "${GAP} ms apart")"

# ---- "there should be games and office folders" ----------------------------------------------------
# Seeded from what each app declares about itself. On this AVD three apps declare PRODUCTIVITY and none
# declares GAME, so the honest expectation is one folder and not two: Office is created and Games
# declines. Both halves matter — a rule that invents a Games folder out of nothing would be the failure.
#
# The slate is cleaned first (seeded folders removed, markers dropped) so the ADD is WATCHED rather than
# inferred: the scan had already run when the APK was installed, and a run that only sees the second
# start cannot tell correct code from code that never ran.
adb shell am force-stop $PKG; sleep 1
adb shell run-as $PKG cat files/start_layout.json > "$ROW_DIR/layout-raw.json"
python3 "$HERE/clean_seeded.py" "$ROW_DIR/layout-raw.json" > "$ROW_DIR/layout-clean.json"
adb shell "run-as $PKG sh -c 'cat > files/start_layout.json'" < "$ROW_DIR/layout-clean.json"
python3 "$HERE/folders.py" "$ROW_DIR/layout-clean.json" > "$ROW_DIR/folders-before.txt"
BEFORE=$(wc -l < "$ROW_DIR/folders-before.txt" | tr -d ' ')
note "folders before the ADD: $BEFORE [$(tr '\n' ' ' < "$ROW_DIR/folders-before.txt")]"

show_start
diag layout > "$ROW_DIR/layout.txt"
assert_contains "the category scan ran" "category folders: scanned" "$(cat "$ROW_DIR/layout.txt")"
note "$(grep -o 'category folders: scanned [0-9]* apps' "$ROW_DIR/layout.txt" | head -1)"
assert_contains "no game is installed, so no Games folder is invented" \
  'addFolderOnce folder:games:v1 "Games" 0 members -> already run or refused' "$(cat "$ROW_DIR/layout.txt")"
assert_contains "the Office folder is created from the apps that declare productivity" \
  '-> created' "$(grep folder:office:v1 "$ROW_DIR/layout.txt")"

adb shell run-as $PKG cat files/start_layout.json > "$ROW_DIR/layout-after.json"
python3 "$HERE/folders.py" "$ROW_DIR/layout-after.json" > "$ROW_DIR/folders-after.txt"
AFTER=$(wc -l < "$ROW_DIR/folders-after.txt" | tr -d ' ')
note "folders after the ADD: $AFTER [$(tr '\n' ' ' < "$ROW_DIR/folders-after.txt")]"
assert_eq "exactly one folder appeared" "$((BEFORE + 1))" "$AFTER"
OFFICE="$(grep '|Office|' "$ROW_DIR/folders-after.txt" | head -1)"
assert_ne "a folder named Office is on Start" "" "$OFFICE"
MEMBERS="${OFFICE##*|}"
note "Office holds $MEMBERS apps"
assert_eq "it holds at least the two a folder needs" "ok" \
  "$([ -n "$MEMBERS" ] && [ "$MEMBERS" -ge 2 ] && echo ok || echo "${MEMBERS:-none}")"
assert_absent "no Games folder was created" "|Games|" "$(cat "$ROW_DIR/folders-after.txt")"
# A seeded folder collects loose apps and never raids one the user made (found here 2026-09-22, fixed in
# LayoutOps.folderOf). Asserted only when there IS a folder to protect: with none on Start, comparing
# against an empty file is a pass that means nothing, which is how the first run of this row "proved"
# there was no Office folder while one was being created.
if [ "$BEFORE" -gt 0 ]; then
  assert_contains "the folder that was already there is untouched" \
    "$(head -1 "$ROW_DIR/folders-before.txt")" "$(cat "$ROW_DIR/folders-after.txt")"
else
  note "no folder existed before the ADD here, so there was nothing to raid; the rule itself is covered by CategoryFoldersTest"
fi

# It does not scan again: the markers are recorded even when an ADD declines, so the cost is paid once.
show_start
diag layout > "$ROW_DIR/layout2.txt"
# The ring is per process, so this capture holds the SECOND start only. The cortana line proves the
# capture is not simply empty — without it, "no scan line" would prove nothing at all.
assert_contains "the second start was captured" "addOnce phase03:cortana" "$(cat "$ROW_DIR/layout2.txt")"
assert_eq "and it did not scan again" "0" \
  "$(grep -c 'category folders: scanned' "$ROW_DIR/layout2.txt" | tr -d ' ')"

row_end
