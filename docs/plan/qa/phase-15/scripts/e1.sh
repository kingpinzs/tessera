#!/usr/bin/env bash
# E1 — app identity and list (T15-47, X8, X14): the three apps in the app list under their letters by their label's own
# tag, no "New" caption, V available in the jump grid, each hold menu Pin to Start and NOT Uninstall; phase 01 E12's walk
# re-run on this build; each app's window carries resource-ids; the C-3 layout baseline; and the Music negative (the three
# pinned tiles keep their own faces while the shell's Music plays — only the MUSIC tile carries the now-playing face).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"
. "$QROOT/phase-02/scripts/layout.sh"
MUSIC_FIXDIR="$QROOT/phase-01/MUSIC6-fixtures"
. "$QROOT/phase-01/scripts/music_lib.sh"
row_begin E1 "the three apps in the app list, their windows, the layout baseline and the Music negative"

TILES="$(dirname "$0")/tiles.py"
MUSIC_ID="slot:MUSIC"
CLOCK_ID="app:app.tileshell/app.tileshell.clock.ClockActivity:0"
CALC_ID="app:app.tileshell/app.tileshell.calculator.CalculatorActivity:0"
REC_ID="app:app.tileshell/app.tileshell.recorder.RecorderActivity:0"
declare -A NAME=( [clock]="app.tileshell/.clock.ClockActivity" [calc]="app.tileshell/.calculator.CalculatorActivity" [rec]="app.tileshell/.recorder.RecorderActivity" )
declare -A LABEL=( [clock]="Alarms & Clock" [calc]="Calculator" [rec]="Voice Recorder" )
declare -A LETTER=( [clock]=A [calc]=C [rec]=V )
tile_field() { # dump.xml id field
  python3 "$TILES" "$1" "$2" | awk -F'\t' -v f="$3" '
    { m["bounds"] = $2; m["size"] = $3; m["controls"] = $4; m["badge"] = $5; m["texts"] = $6 }
    END { print m[f] }'
}
open_applist() {
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input swipe 900 1200 150 1200 250; sleep 2
}
# The list keeps its scroll position (after the walk it reopens at the bottom), so a row is reached through the jump
# grid: any header on screen opens the grid, and the letter's cell jumps to its group.
goto_letter() { # letter
  local h i
  open_applist
  for i in 1 2 3 4 5; do
    dump_ui "$ROW_DIR/.nav.xml"
    h="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/.nav.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
    [ -n "$h" ] && break
    adb shell input swipe 540 800 540 1700 300; sleep 1
  done
  tap_node "$ROW_DIR/.nav.xml" "$h"; sleep 1.5
  dump_ui "$ROW_DIR/.grid.xml"
  tap_node "$ROW_DIR/.grid.xml" "jump_cell:$1"; sleep 1.5
}

# ---------------------------------------------------------------- C-3 baseline
MARK="$(ring_mark)"
layout_restore "$P15/baseline_layout.json"
assert_eq "baseline layout restored" 0 $?
sleep 2
ring_since "$MARK" > "$ROW_DIR/ring_restore.txt"
assert_eq "no assignSlotOnce ... -> assigned after the restore" 0 "$(grep -c 'assignSlotOnce.* -> assigned' "$ROW_DIR/ring_restore.txt")"
assert_contains "positive control: the Music slot's one-time assignment reports already run" "assignSlotOnce slot:music:v1 MUSIC -> " "$(grep -F 'already run' "$ROW_DIR/ring_restore.txt")"
layout_json | tr -d '\r' > "$ROW_DIR/layout_after_restore.json"
assert_eq "the restored addedOnce equals the file's" \
  "$(python3 -c 'import json,sys; print(sorted(json.load(open(sys.argv[1]))["addedOnce"]))' "$P15/baseline_layout.json")" \
  "$(python3 -c 'import json,sys; print(sorted(json.load(open(sys.argv[1]))["addedOnce"]))' "$ROW_DIR/layout_after_restore.json")"

# ---------------------------------------------------------------- the app list: walk, letters, captions
open_applist
: > "$ROW_DIR/walk_order.txt"
prev=""
for i in $(seq 1 30); do
  dump_ui "$ROW_DIR/walk.xml"
  # Headers, rows and names in on-screen order (by top edge), appended when not yet seen.
  python3 - "$ROW_DIR/walk.xml" "$ROW_DIR/walk_order.txt" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); seen = open(sys.argv[2]).read().split("\n")
items = []
for m in re.finditer(r'resource-id="(applist_(?:header|name|new):[^"]*)"[^>]*bounds="\[\d+,(\d+)\]', s):
    items.append((int(m.group(2)), m.group(1)))
with open(sys.argv[2], "a") as o:
    for _, rid in sorted(items):
        if rid not in seen:
            o.write(rid + "\n"); seen.append(rid)
PY
  last="$(grep -o 'resource-id="applist_name:[^"]*"' "$ROW_DIR/walk.xml" | tail -1)"
  [ "$last" = "$prev" ] && break; prev="$last"
  adb shell input swipe 540 1900 540 700 1500; sleep 1.2   # slow: no fling past a row (see e12_part1.sh)
done
rm -f "$ROW_DIR/walk.xml"
for k in clock calc rec; do
  group="$(python3 - "$ROW_DIR/walk_order.txt" "applist_name:${NAME[$k]}" <<'PY'
import sys
order = open(sys.argv[1]).read().split("\n"); letter = "?"
for rid in order:
    if rid.startswith("applist_header:"): letter = rid.split(":", 1)[1]
    if rid == sys.argv[2]: print(letter); break
PY
)"
  assert_eq "${LABEL[$k]} sits under ${LETTER[$k]}" "${LETTER[$k]}" "$group"
done
assert_eq "no New caption on any in-APK row" 0 "$(grep -c '^applist_new:app.tileshell$' "$ROW_DIR/walk_order.txt")"

# Each row's label text, and its hold menu (at the label's own bounds): Pin to Start, not Uninstall.
for k in clock calc rec; do
  goto_letter "${LETTER[$k]}"
  scroll_to_node "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}" 3
  assert_eq "${LABEL[$k]} row found by applist_name" "${LABEL[$k]}" "$(node_text "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}" | python3 -c 'import html,sys; print(html.unescape(sys.stdin.read().strip()))')"
  # The jump scrolls smoothly, and a touch while the list still moves only stops it: hold once the row has not moved
  # between two dumps (E1 run 3's Calculator hold opened no menu).
  prevb=""
  for i in 1 2 3 4 5 6; do
    b="$(bounds "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}")"
    [ -n "$b" ] && [ "$b" = "$prevb" ] && break
    prevb="$b"; sleep 0.7; dump_ui "$ROW_DIR/row_$k.xml"
  done
  read -r x1 y1 x2 y2 <<< "$b"
  adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 1000; sleep 1.5
  dump_ui "$ROW_DIR/menu_$k.xml"
  assert_eq "${LABEL[$k]} hold menu offers Pin to Start" yes "$(has_node "$ROW_DIR/menu_$k.xml" applist_menu_pin)"
  assert_eq "${LABEL[$k]} hold menu does NOT offer Uninstall" no "$(has_node "$ROW_DIR/menu_$k.xml" applist_menu_uninstall)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
done

# The jump grid: V is available (a tap on it jumps; a dimmed cell takes no tap and logs nothing).
open_applist
dump_ui "$ROW_DIR/applist_top.xml"
H="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/applist_top.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
tap_node "$ROW_DIR/applist_top.xml" "$H"; sleep 2
dump_ui "$ROW_DIR/jump_grid.xml"
adb exec-out screencap -p > "$ROW_DIR/jump_grid.png"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/jump_grid.xml" jump_cell:V; sleep 2
assert_contains "the jump grid's V cell is available (a tap jumps to V)" "jump to V" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/after_jump_V.xml"
assert_eq "after the jump, Voice Recorder's row is on screen" yes "$(has_node "$ROW_DIR/after_jump_V.xml" "applist_name:${NAME[rec]}")"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---------------------------------------------------------------- phase 01 E12's walk, re-run on this build
# It resets the caption store (its method line); the store is saved first and put back after (RV12).
adb shell "run-as app.tileshell cat shared_prefs/applist_new.xml" < /dev/null > "$ROW_DIR/applist_new.before.xml" 2>/dev/null
mkdir -p "$ROW_DIR/e12"
bash "$QROOT/phase-01/scripts/e12_part1.sh" "$ROW_DIR/e12" > "$ROW_DIR/e12/run.out" 2>&1
echo $? > "$ROW_DIR/e12/rc"
assert_eq "phase 01 E12 part 1 ran to the end" 0 "$(cat "$ROW_DIR/e12/rc")"
E12="$ROW_DIR/e12/E12.txt"
assert_contains "E12: the list is up with its search box" "app_list=1 applist_search=1" "$(cat "$E12")"
assert_contains "E12: no New captions right after the baseline" "New captions: 0" "$(cat "$E12")"
assert_eq "E12: every fixture row is present" 17 "$(grep '^fixture rows present:' "$E12" | wc -w | awk '{print $1 - 3}')"
# The search matches LABELS, and no Fossify app's label says "Fossify": phase 01's own E12 recorded an empty result for
# this search (qa/phase-01/E12/E12.txt). The re-run must give what phase 01 recorded.
assert_eq "E12: the search for fossify gives what phase 01's E12 recorded" \
  "$(grep -h "^results for 'fossify'" "$QROOT/phase-01/E12/E12.txt" | head -1 | sed 's/ *$//')" \
  "$(grep "^results for 'fossify'" "$E12" | head -1 | sed 's/ *$//')"
assert_contains "E12: the jump to O lands on O" "first header on screen: resource-id=\"applist_header:O\"" "$(cat "$E12")"
if [ -s "$ROW_DIR/applist_new.before.xml" ]; then
  adb shell am force-stop app.tileshell
  adb push "$ROW_DIR/applist_new.before.xml" /data/local/tmp/applist_new.xml >/dev/null
  adb shell 'run-as app.tileshell sh -c "cat /data/local/tmp/applist_new.xml > shared_prefs/applist_new.xml"'
  adb shell input keyevent KEYCODE_HOME; sleep 3
fi

# ---------------------------------------------------------------- each app's window carries resource-ids
for pair in "clock:clock_pivot:alarm" "calc:calc_display" "rec:rec_button"; do
  k="${pair%%:*}"; tag="${pair#*:}"
  adb shell am start -W -n "${NAME[$k]}" > /dev/null 2>&1; sleep 3
  if [ "$k" = clock ]; then gdump "$ROW_DIR/window_$k.xml"; else dump_ui "$ROW_DIR/window_$k.xml"; fi
  assert_eq "${LABEL[$k]}'s window dump carries resource-id $tag" yes "$(has_node "$ROW_DIR/window_$k.xml" "$tag")"
  adb shell input keyevent KEYCODE_HOME; sleep 1
done

# ---------------------------------------------------------------- the Music negative
VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
HAD_FIX="$(adb shell ls /sdcard/Music/tessera-qa 2>/dev/null | grep -c mp3)"
music_mute
music_fixtures
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
# The Clock tile shows a LIVE face only with an alarm set (ClockTiles publishes nothing otherwise, and the tile shows its
# bell); one is seeded so "keeps its own face" is a face Music could have replaced.
assert_clock_empty "before the seeded alarm"
e1alarm="$(api_alarm 7 20 E1alarm)"
assert_ne "an alarm is seeded for the Clock tile's face" "" "$e1alarm"
adb shell input keyevent KEYCODE_HOME; sleep 3
gdump "$ROW_DIR/start_idle.xml"
assert_eq "idle: the Clock tile shows its live face (headline)" yes "$(has_node "$ROW_DIR/start_idle.xml" "tile_clock_headline:$CLOCK_ID")"
bloom="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"title='Bloom'\"" | tr -d '\r' | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 4
dump_ui "$ROW_DIR/music_open.xml"
tap_node "$ROW_DIR/music_open.xml" "music_pivot_header:songs"; sleep 3
dump_ui "$ROW_DIR/music_songs.xml"
tap_node "$ROW_DIR/music_songs.xml" "music_song:$bloom"; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3
gdump "$ROW_DIR/start_playing.xml"
adb exec-out screencap -p > "$ROW_DIR/start_playing.png"
python3 "$TILES" "$ROW_DIR/start_idle.xml" > "$ROW_DIR/start_idle.tiles.txt"
python3 "$TILES" "$ROW_DIR/start_playing.xml" > "$ROW_DIR/start_playing.tiles.txt"
assert_eq "only the MUSIC tile carries the now-playing face" "controls=yes" "$(tile_field "$ROW_DIR/start_playing.xml" "$MUSIC_ID" controls)"
for id in "$CLOCK_ID" "$CALC_ID" "$REC_ID"; do
  assert_eq "$id has not grown" "$(tile_field "$ROW_DIR/start_idle.xml" "$id" size)" "$(tile_field "$ROW_DIR/start_playing.xml" "$id" size)"
  assert_eq "$id carries no transport" "controls=no" "$(tile_field "$ROW_DIR/start_playing.xml" "$id" controls)"
  assert_absent "$id shows no now-playing text" "Bloom" "$(tile_field "$ROW_DIR/start_playing.xml" "$id" texts)"
done
assert_eq "the Clock tile keeps its own face" yes "$(has_node "$ROW_DIR/start_playing.xml" "tile_clock_headline:$CLOCK_ID")"
for id in "$CALC_ID" "$REC_ID"; do
  assert_eq "$id keeps its own face (its texts)" "$(tile_field "$ROW_DIR/start_idle.xml" "$id" texts)" "$(tile_field "$ROW_DIR/start_playing.xml" "$id" texts)"
done

# ---------------------------------------------------------------- restore
adb shell cmd media_session dispatch pause >/dev/null 2>&1
app_delete_all
assert_clock_empty "restore"
if [ "$HAD_FIX" = 0 ]; then
  adb shell rm -rf /sdcard/Music/tessera-qa
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
fi
[ -n "$VOL0" ] && adb shell cmd media_session volume --stream 3 --set "$VOL0" >/dev/null 2>&1
ring_save
layout_restore "$P15/baseline_layout.json"
row_end
