#!/usr/bin/env bash
# ITEM 4 (INDEX Change Log 2026-09-21): "the photo one should have a slide show option on the tile and
# be able to make it a bit bigger when its playing the slide show and also set a main photo kind of
# like a picture frame and it does not flip when that is set".
#
# PhotoRules.plan is pure and already unit-tested, so this row is about the WIRING: that the settings
# reach the feed, that the tile really is drawn bigger, that it really advances on the fixed cadence,
# and that a picture frame really does not flip. The last one is the rule Jeremy stated without
# qualification, so it is asserted as an absence over a window rather than from a single dump.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

TILE=tile:slot:PHOTOS

set_pref() { # <key> <boolean|string> <value|--remove>; the shell rewrites prefs as it exits, so stop it
  adb shell am force-stop $PKG; sleep 1
  adb shell run-as $PKG cat shared_prefs/start_theme.xml > "$ROW_DIR/prefs-in.xml" 2>/dev/null
  [ -s "$ROW_DIR/prefs-in.xml" ] || printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map />\n' > "$ROW_DIR/prefs-in.xml"
  python3 "$HERE/prefs_edit.py" "$ROW_DIR/prefs-in.xml" "$1" "$2" "$3" > "$ROW_DIR/prefs-out.xml"
  adb shell "run-as $PKG sh -c 'cat > shared_prefs/start_theme.xml'" < "$ROW_DIR/prefs-out.xml"
  # The notification listener is restarted by the system within a second of a force-stop and reads the
  # settings as it comes up, so a write that lands after that restart is not seen until the NEXT start.
  # The first run of this row measured exactly that — every state was one setting behind, which read as
  # "the slideshow does not grow the tile" and "a picture frame does", both of which are false. Stopping
  # the package again AFTER the write makes the next start the first process to read it.
  adb shell am force-stop $PKG; sleep 2
}

show_start() { adb shell input keyevent KEYCODE_HOME; sleep 7; }

tile_area() { # tile_area <xml> -> "W H", empty when the tile is not on screen
  python3 - "$1" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'resource-id="tile:slot:PHOTOS"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
print(f"{int(m.group(3))-int(m.group(1))} {int(m.group(4))-int(m.group(2))}" if m else "")
PY
}

row_begin ITEM4 "Photos tile: a slideshow grows and advances, a picture frame does neither"

# ---- photos in the gallery -----------------------------------------------------------------------
python3 "$HERE/make_photos.py" "$ROW_DIR" >/dev/null
for i in 0 1 2 3 4 5; do
  adb push "$ROW_DIR/qa-photo-$i.png" "/sdcard/Pictures/qa-photo-$i.png" >/dev/null 2>&1
  adb shell content call --uri content://media --method scan_file --arg "/sdcard/Pictures/qa-photo-$i.png" >/dev/null 2>&1
done
sleep 3
IDS=$(adb shell content query --uri content://media/external/images/media --projection _id 2>/dev/null | grep -o '_id=[0-9]*' | sed 's/_id=//')
COUNT=$(echo "$IDS" | grep -c '[0-9]')
note "images in MediaStore: $COUNT"
assert_eq "the gallery has at least two photos" "ok" "$([ "${COUNT:-0}" -ge 2 ] && echo ok || echo "${COUNT:-0}")"

# ---- baseline: no slideshow, no frame --------------------------------------------------------------
set_pref photos_slideshow boolean false
set_pref photo_frame string --remove
show_start
dump "$ROW_DIR/plain.xml"
adb exec-out screencap -p > "$ROW_DIR/plain.png"
PLAIN="$(tile_area "$ROW_DIR/plain.xml")"
note "plain tile: $PLAIN"
assert_ne "the Photos tile is on Start" "" "$PLAIN"

# ---- the slideshow: bigger, and advancing on its own fixed cadence ---------------------------------
set_pref photos_slideshow boolean true
show_start
dump "$ROW_DIR/slideshow.xml"
adb exec-out screencap -p > "$ROW_DIR/slideshow.png"
SHOW="$(tile_area "$ROW_DIR/slideshow.xml")"
note "slideshow tile: $SHOW"
PLAIN_A=$(echo "$PLAIN" | awk '{print $1*$2}')
SHOW_A=$(echo "$SHOW" | awk '{print $1*$2}')
note "areas: plain=$PLAIN_A slideshow=$SHOW_A"
assert_eq "the tile is drawn bigger while the slideshow runs" "ok" \
  "$([ -n "$SHOW_A" ] && [ -n "$PLAIN_A" ] && [ "$SHOW_A" -gt "$PLAIN_A" ] && echo ok || echo "plain=$PLAIN_A show=$SHOW_A")"

command sleep 14
PERIOD="$(diag tile_anim | python3 "$HERE/anim_period.py" slot:PHOTOS)"
note "median face-change period: [${PERIOD:-none}] ms"
assert_ne "the slideshow advances at all" "" "$PERIOD"
[ -n "$PERIOD" ] && assert_within "the slideshow advances on the fixed 3 s cadence" 3000 "$PERIOD" 900

# ---- the picture frame: does NOT flip, and does not grow -------------------------------------------
FRAME_ID=$(echo "$IDS" | tail -1)
set_pref photo_frame string "content://media/external/images/media/$FRAME_ID"
note "picture frame uri: content://media/external/images/media/$FRAME_ID"
show_start
dump "$ROW_DIR/frame.xml"
adb exec-out screencap -p > "$ROW_DIR/frame.png"
FRAME="$(tile_area "$ROW_DIR/frame.xml")"
FRAME_A=$(echo "$FRAME" | awk '{print $1*$2}')
note "frame tile: $FRAME (area $FRAME_A)"
assert_eq "a picture frame does not grow" "$PLAIN_A" "$FRAME_A"

# The rule stated without qualification: a frame does not flip. Watched over 20 s, which is more than
# six slideshow cadences and about four of R3 A8's own random band, so a tile that flipped at all
# would be seen doing it.
adb shell run-as $PKG true 2>/dev/null
BEFORE="$(diag tile_anim | grep -c 'tile=slot:PHOTOS')"
command sleep 20
AFTER="$(diag tile_anim | grep -c 'tile=slot:PHOTOS')"
note "face changes seen with the frame set: before=$BEFORE after=$AFTER"
assert_eq "a picture frame does not flip" "$BEFORE" "$AFTER"

# ---- put the tile back the way it was found --------------------------------------------------------
set_pref photo_frame string --remove
set_pref photos_slideshow boolean false
row_end
