#!/usr/bin/env bash
# E11 Regression, the specific sub-steps this phase's changes touch (phase 13 Acceptance E11; the 2026-09-25 QA ruling,
# T13-23, T13-3), each passing with its own numbers on this build (the Q1 A promise):
#   (1) phase 03 E15 — its driver qa/phase-03/scripts/e15.sh is a dependency of this row (INDEX row 03 lists E15 NOT
#       RUN) and runs as its own row just before this one; here its result is asserted: this build, no FAIL.
#   (2) phase 02 E2's hold-menu display and Pin action (the band is acrylic now, and L13-2 made it modal).
#   (3) phase 10 MUSIC8's menu open, selection and dismissal.
#   (4) phase 01 E12's swipe / search / jump-grid sub-steps and E19's app-list bar checks.
#   (5) no new exported component (qa/phase-03/scripts/exported.py against qa/phase-03/exported-allowlist.txt).
# Affected motion on the shell's clock is E8's. Whole gates stay Jeremy's end-of-project run.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
P01="$(cd "$P13/../../phase-01/scripts" && pwd)"
P02="$(cd "$P13/../../phase-02/scripts" && pwd)"
P03="$(cd "$P13/../../phase-03/scripts" && pwd)"

row_begin E11 "regression: 03 E15, 02 E2 hold menu + Pin, 10 MUSIC8 menu, 01 E12 swipe/search/jump grid + E19 bars, exports"
PXE=3   # px per epx at 1080 wide
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
show_start 6
BUILT="$(sha256sum "$APK" | cut -c1-16)"

# ================================================================ (1) phase 03 E15, run just before as its own row
# E15 is phase 03's row and phase 03's gate is deferred to the end-of-build pass (INDEX row 03): its pre-phase-13 run
# (qa/phase-03/E15-run2-a437acd5, 2026-09-24, apk a437acd5, 20/7) already fails 7 clauses in phase 03's own part. What
# E11 proves is the Q1 A promise — phase 13 changes none of E15's numbers — so the two runs are compared clause by
# clause: no clause that passed before fails now; a clause failing in both reads the same value within its own
# tolerance; a clause with no earlier verdict (the earlier run made no reminder row, so its menu steps never ran) must
# pass on its own numbers.
E15="$QA/../phase-03/E15/E15.txt"
E15B="$QA/../phase-03/E15-run2-a437acd5/E15.txt"
assert_eq "(1) phase 03 E15 ran" "yes" "$([ -s "$E15" ] && echo yes || echo no)"
assert_eq "(1) on this build" "$BUILT" "$(grep -m1 '^apk built' "$E15" 2>/dev/null | awk '{print $3}')"
note "(1) this build: $(tail -1 "$E15" 2>/dev/null); before phase 13: $(tail -1 "$E15B" 2>/dev/null)"
python3 - "$E15B" "$E15" > "$ROW_DIR/1-e15-compare.txt" <<'PY'
import re, sys
def parse(p):
    out = {}
    for line in open(p):
        m = re.match(r'(PASS|FAIL)  (.+?) {2,}(.*)$', line.rstrip('\n'))
        if m:
            out[m.group(2).strip()] = (m.group(1), m.group(3))
    return out
b, a = parse(sys.argv[1]), parse(sys.argv[2])
def num(d):
    m = re.match(r'\s*(-?[0-9.]+) vs (-?[0-9.]+) \+/- ([0-9.]+)', d)
    return (float(m.group(1)), float(m.group(3))) if m else None
for k in sorted(set(a) | set(b)):
    va, vb = a.get(k), b.get(k)
    if va is None:
        print("GONE|%s|%s" % (k, vb[0])); continue
    if vb is None:
        print("NEW|%s|%s|%s" % (k, va[0], va[1])); continue
    if vb[0] == "PASS" and va[0] == "FAIL":
        print("REGRESSED|%s|%s -> %s" % (k, vb[1], va[1])); continue
    if vb[0] == "FAIL" and va[0] == "FAIL":
        na, nb = num(va[1]), num(vb[1])
        same = (na and nb and abs(na[0] - nb[0]) <= max(na[1], 1.0)) or (not na and va[1] == vb[1])
        print("%s|%s|%s -> %s" % ("FAIL-BOTH-SAME" if same else "FAIL-BOTH-MOVED", k, vb[1], va[1])); continue
    print("%s|%s|%s" % ("PASS-BOTH" if vb[0] == "PASS" else "FIXED", k, va[1]))
PY
cat "$ROW_DIR/1-e15-compare.txt" >> "$LOG"
assert_eq "(1) no E15 clause that passed before phase 13 fails now" "" "$(grep '^REGRESSED|' "$ROW_DIR/1-e15-compare.txt" | cut -d'|' -f2 | tr '\n' ';')"
assert_eq "(1) every clause failing before and now reads the same value (phase 03's own, unchanged)" "" "$(grep '^FAIL-BOTH-MOVED|' "$ROW_DIR/1-e15-compare.txt" | cut -d'|' -f2 | tr '\n' ';')"
assert_eq "(1) every clause with no earlier verdict passes on its own numbers" "" "$(grep '^NEW|' "$ROW_DIR/1-e15-compare.txt" | grep '|FAIL|' | cut -d'|' -f2 | tr '\n' ';')"
record "(1) E15 clauses failing before and after phase 13 (phase 03's gate)" "$(grep -c '^FAIL-BOTH-SAME|' "$ROW_DIR/1-e15-compare.txt")"

# ================================================================ (2) phase 02 E2: the hold menu and Pin to Start
. "$P02/layout.sh"
MARK="$(ring_mark)"
layout_restore "$QA/../phase-02/baseline_layout.json"
assert_absent "(2) phase 02's baseline: no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"
FIX=app.tileshell.testclient.a
assert_eq "(2) the fixture $FIX is not on Start in the baseline" "0" "$(layout_json | grep -c "$FIX")"
show_start 5
to_app_list 3
dump_ui "$ROW_DIR/2-applist.xml"
[ "$(has_node "$ROW_DIR/2-applist.xml" "applist_row:$FIX")" = yes ] || scroll_to_node "$ROW_DIR/2-applist.xml" "applist_row:$FIX" 12
assert_eq "(2) the fixture's row is on the app list" "yes" "$(has_node "$ROW_DIR/2-applist.xml" "applist_row:$FIX")"
set -- $(bounds "$ROW_DIR/2-applist.xml" "applist_row:$FIX"); HX=$(( ($1 + $3) / 2 )); HY=$(( ($2 + $4) / 2 ))
adb shell input swipe $HX $HY $HX $HY 1000
sleep 1.5
dump_ui "$ROW_DIR/2-band.xml"
screencap "$ROW_DIR/2-band.png"
assert_eq "(2) the hold opens the band (applist_menu)" "yes" "$(has_node "$ROW_DIR/2-band.xml" applist_menu)"
assert_eq "(2) with Pin to Start" "yes" "$(has_node "$ROW_DIR/2-band.xml" applist_menu_pin)"
set -- $(bounds "$ROW_DIR/2-band.xml" applist_menu_pin)
assert_contains "(2) labelled 'pin to Start', inside the item" "pin to start" "$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/2-band.xml" | awk -F'|' -v t=$2 -v b=$4 '{split($2,q," "); if (q[2] >= t && q[4] <= b) print tolower($1)}')"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/2-band.xml" applist_menu_pin
sleep 2
ring_since "$MARK" > "$ROW_DIR/2-slice-pin.txt"
assert_contains "(2) Pin runs: the pin line says pinned" "-> pinned" "$(grep -F "pin to Start $FIX" "$ROW_DIR/2-slice-pin.txt")"
dump_ui "$ROW_DIR/2-after-pin.xml"
assert_eq "(2) the band closes after Pin" "no" "$(has_node "$ROW_DIR/2-after-pin.xml" applist_menu)"
assert_eq "(2) the layout gains the fixture's tile" "1" "$(layout_json | grep -o "$FIX" | wc -l | tr -d ' ')"
show_start 5
dump_ui "$ROW_DIR/2-start.xml"
assert_contains "(2) the tile is on Start" "$FIX" "$(grep -o 'resource-id="tile:[^"]*"' "$ROW_DIR/2-start.xml" | tr '\n' ' ')"
MARK="$(ring_mark)"
layout_restore "$QA/../phase-02/baseline_layout.json"
assert_absent "(2) restore: phase 02's baseline, no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"
assert_eq "(2) restore: the fixture's tile is gone" "0" "$(layout_json | grep -c "$FIX")"

# ================================================================ (3) phase 10 MUSIC8: menu open, selection, dismissal
source "$P01/ui.sh"
source "$P01/music_lib.sh"
music_mute
music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell am force-stop $PKG; sleep 1
adb shell run-as $PKG cp files/music_playlists.json files/music_playlists.json.qa13 2>/dev/null
adb shell run-as $PKG rm -f files/music_playlists.json
music_open
goto_pivot playlists "$ROW_DIR/3-empty.xml"
tap_node "$ROW_DIR/3-empty.xml" music_new_playlist; sleep 2
adb shell input keyevent KEYCODE_MOVE_END; for _ in $(seq 1 14); do adb shell input keyevent 67; done
adb shell input text "QA13%sE11"; sleep 1; adb shell input keyevent 66; sleep 2
dump_ui "$ROW_DIR/3-made.xml"
# The id comes from the product's own store, not the dump: run 2's dump caught Start in front for a moment after Done
# while the playlist had been written (E11-run2-music-dump-db078d6a/).
PLID="$(adb shell run-as $PKG cat files/music_playlists.json 2>/dev/null | python3 -c "
import json, sys
d = json.load(sys.stdin)
print(next((p['id'] for p in d['playlists'] if p['name'] == 'QA13 E11'), ''))" 2>/dev/null)"
PL="music_playlist:$PLID"
assert_ne "(3) a playlist to add to (in the store)" "" "$PLID"
goto_pivot songs "$ROW_DIR/3-songs.xml"
SONG="$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/3-songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/3-songs.xml" "$SONG"); SX=$(( (${1:-0} + ${3:-0}) / 2 )); SY=$(( (${2:-0} + ${4:-0}) / 2 ))
assert_ne "(3) a song row to hold" "" "$SONG"
# open
adb shell input swipe $SX $SY $SX $SY 1200; sleep 2
dump_ui "$ROW_DIR/3-menu.xml"
screencap "$ROW_DIR/3-menu.png"
assert_eq "(3) open: holding a song opens music_menu" "yes" "$(has_node "$ROW_DIR/3-menu.xml" music_menu)"
assert_eq "(3) open: offering the playlist it could join" "yes" "$(has_node "$ROW_DIR/3-menu.xml" "music_menu_add:$PLID")"
assert_eq "(3) open: and a new one" "yes" "$(has_node "$ROW_DIR/3-menu.xml" music_menu_new)"
# selection
MARK="$(ring_mark)"
tap_node "$ROW_DIR/3-menu.xml" "music_menu_add:$PLID"; sleep 2
dump_ui "$ROW_DIR/3-picked.xml"
assert_eq "(3) selection: picking closes the menu" "no" "$(has_node "$ROW_DIR/3-picked.xml" music_menu)"
assert_contains "(3) selection: the store adds the track (its line after the MARK)" "playlists add" "$(ring_since "$MARK" | grep -F '[music]')"
# dismissal: a tap off the band closes it and runs nothing; Back closes it too
adb shell input swipe $SX $SY $SX $SY 1200; sleep 2
dump_ui "$ROW_DIR/3-menu2.xml"
set -- $(bounds "$ROW_DIR/3-menu2.xml" music_menu); MT=${2:-0}; MBOT=${4:-0}
assert_eq "(3) dismissal: the menu is open to dismiss" "yes" "$(has_node "$ROW_DIR/3-menu2.xml" music_menu)"
OFFY=$(( MT > 400 ? MT - 200 : MBOT + 200 ))
adb shell input tap 540 $OFFY; sleep 1.5
dump_ui "$ROW_DIR/3-dismissed.xml"
assert_eq "(3) dismissal: a tap off the band closes it" "no" "$(has_node "$ROW_DIR/3-dismissed.xml" music_menu)"
adb shell input swipe $SX $SY $SX $SY 1200; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$ROW_DIR/3-back.xml"
assert_eq "(3) dismissal: Back closes it, Music stays" "no yes" "$(has_node "$ROW_DIR/3-back.xml" music_menu) $(has_node "$ROW_DIR/3-back.xml" music_root)"
goto_pivot playlists "$ROW_DIR/3-count.xml"
assert_eq "(3) dismissal ran nothing: the playlist holds the one song" "1 song" "$(node_text "$ROW_DIR/3-count.xml" "music_sub:$PL")"
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'if [ -f files/music_playlists.json.qa13 ]; then mv files/music_playlists.json.qa13 files/music_playlists.json; else rm -f files/music_playlists.json; fi'"

# ================================================================ (4) phase 01 E12 swipe / search / jump grid, E19 bars
show_start 6
to_app_list 3
dump_ui "$ROW_DIR/4-applist.xml"
screencap "$ROW_DIR/4-applist.png"
assert_eq "(4) E12 swipe left shows the app list" "yes" "$(has_node "$ROW_DIR/4-applist.xml" app_list)"
assert_eq "(4) E12 with its search box" "yes" "$(has_node "$ROW_DIR/4-applist.xml" applist_search)"
# search: the query is the first letters of a row's own label, so the expected set is computable from the list itself
# The label is the text node inside a row's own bounds (a row node carries no text; the first run took the page's
# second text node, an empty one at x 996, and typed nothing — kept in E11-run1-empty-query-db078d6a/).
ROWB="$(bounds "$ROW_DIR/4-applist.xml" "$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/4-applist.xml" | sed -n 5p | sed 's/resource-id="//; s/"$//')")"
LBL="$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/4-applist.xml" | python3 -c "
import sys
l, t, r, b = map(int, '$ROWB'.split())
for line in sys.stdin:
    text, _, box = line.rstrip('\n').rpartition('|')
    if not text or not box: continue
    x1, y1, x2, y2 = map(int, box.split())
    if x1 >= l and y1 >= t and x2 <= r and y2 <= b: print(text); break")"
QRY="$(echo "$LBL" | tr -cd 'A-Za-z' | cut -c1-4)"
note "(4) search query '$QRY' (from the row label '$LBL')"
assert_ne "(4) a query to type" "" "$QRY"
assert_eq "(4) a query of 4 letters" "4" "${#QRY}"
tap_node "$ROW_DIR/4-applist.xml" applist_search; sleep 1
adb shell input text "$QRY"; sleep 2
dump_ui "$ROW_DIR/4-search.xml"
screencap "$ROW_DIR/4-search.png"
python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/4-search.xml" > "$ROW_DIR/4-search-texts.txt"
N="$(grep -o 'resource-id="applist_row:' "$ROW_DIR/4-search.xml" | wc -l | tr -d ' ')"   # by match: a dump is one line
note "(4) search results: $N rows"
assert_eq "(4) E12 search filters to at least the row it came from" "yes" "$([ "$N" -ge 1 ] && echo yes || echo no)"
assert_contains "(4) E12 the source row is among the results" "$LBL" "$(cut -d'|' -f1 "$ROW_DIR/4-search-texts.txt")"
MISS="$(python3 - "$ROW_DIR/4-search.xml" "$QRY" <<'PY'
import re, sys
xml = open(sys.argv[1]).read(); q = sys.argv[2].lower(); bad = []
for m in re.finditer(r'<node[^>]*resource-id="applist_row:([^"]*)"[^>]*>(.*?)</node>', xml, re.S):
    texts = [t for t in re.findall(r'text="([^"]*)"', m.group(0)) if t]
    if not any(q in t.lower() for t in texts): bad.append(m.group(1))
print(" ".join(bad))
PY
)"
assert_eq "(4) E12 every result's label holds the query" "" "$MISS"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
show_start 4
to_app_list 3
dump_ui "$ROW_DIR/4-top.xml"
HDR="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/4-top.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
tap_node "$ROW_DIR/4-top.xml" "$HDR"; sleep 2
dump_ui "$ROW_DIR/4-grid.xml"
screencap "$ROW_DIR/4-grid.png"
assert_eq "(4) E12 a letter header opens the jump grid" "yes" "$(has_node "$ROW_DIR/4-grid.xml" jump_grid)"
LET="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/2-applist.xml" "$ROW_DIR/4-applist.xml" | sed 's/.*applist_header://; s/"$//' | grep -v "^${HDR#applist_header:}$" | tail -1)"
[ "$(has_node "$ROW_DIR/4-grid.xml" "jump_cell:$LET")" = yes ] || LET=O
note "(4) jumping to $LET"
tap_node "$ROW_DIR/4-grid.xml" "jump_cell:$LET"; sleep 2
dump_ui "$ROW_DIR/4-jumped.xml"
screencap "$ROW_DIR/4-jumped.png"
assert_eq "(4) E12 a letter closes the grid" "no" "$(has_node "$ROW_DIR/4-jumped.xml" jump_grid)"
FIRST="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/4-jumped.xml" | head -1 | sed 's/.*applist_header://; s/"$//')"
assert_eq "(4) E12 and jumps: its header is the first on screen" "$LET" "$FIRST"
# E19 on the app list: the system bars hidden, the drawn bars present at their heights
WIN="$(adb shell dumpsys window | grep -E 'InsetsSource .*type=(statusBars|navigationBars)' | head -2 | tr -s ' ')"
echo "$WIN" > "$ROW_DIR/4-insets.txt"
assert_eq "(4) E19 app list: statusBars not visible" "visible=false" "$(echo "$WIN" | grep 'type=statusBars' | grep -o 'visible=[a-z]*' | head -1)"
assert_eq "(4) E19 app list: navigationBars not visible" "visible=false" "$(echo "$WIN" | grep 'type=navigationBars' | grep -o 'visible=[a-z]*' | head -1)"
SB="$(bounds "$ROW_DIR/4-jumped.xml" w10m_status_bar)"; NB="$(bounds "$ROW_DIR/4-jumped.xml" w10m_nav_bar)"
assert_ne "(4) E19 the drawn status bar is there" "" "$SB"
assert_ne "(4) E19 the drawn nav bar is there" "" "$NB"
set -- $SB; assert_within "(4) E19 drawn status bar 28 epx tall" 28 "$(python3 -c "print(($4 - $2) / $PXE)")" 1.1
set -- $NB; assert_within "(4) E19 drawn nav bar 48 epx tall (X6)" 48 "$(python3 -c "print(($4 - $2) / $PXE)")" 1.1
assert_eq "(4) E19 nav bar at the screen bottom" "2340" "$4"
show_start 3

# ================================================================ (5) no new exported component
python3 "$P03/exported.py" "$APK" "$QA/../phase-03/exported-allowlist.txt" > "$ROW_DIR/5-exported.txt" 2>&1
echo $? > "$ROW_DIR/5-exported.rc"
assert_eq "(5) exported.py: the APK's exports equal the allow-list (rc 0)" "0" "$(cat "$ROW_DIR/5-exported.rc")"
note "(5) $(tail -2 "$ROW_DIR/5-exported.txt" | tr '\n' ' ')"
row_end
