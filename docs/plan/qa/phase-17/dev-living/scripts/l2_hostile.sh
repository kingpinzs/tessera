#!/usr/bin/env bash
# Development proof, build task 6d in Photos: stills whose Motion Photo XMP another writer got wrong (or wrote to
# mislead). Three small files made on the host (make_living_fixtures.py) are pushed to Pictures/living-qa: one whose
# directory is right and whose clip starts like an MP4 but is not one — a Living Image to the reader, so it has the
# glyph, and a hold must end as `stop (error <why>)` with the still left showing and the launcher alive; one whose
# stated length is longer than the file and one whose clip is not an MP4 — plain stills: no glyph, and a hold logs
# nothing. The files are removed at the end (the census back). Not the gate.
. "$(dirname "$0")/head.sh"
keep_earlier L2
row_begin L2 "Living Images in Photos: a clip that does not parse, lengths that lie"
assert_eq "wake" "Awake" "$(wake_device)"
T0="$(adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
COUNT0="$(count_rows $IMAGES)"; VCOUNT0="$(count_rows $VIDEOS)"
record "census before" "images=$COUNT0 videos=$VCOUNT0"
record "fixtures (name size clip)" "$(python3 "$LIVING_HERE/make_living_fixtures.py" "$ROW_DIR/fix" | tr '\n' '|')"
DIR=/sdcard/Pictures/living-qa
adb shell mkdir -p "$DIR"
for f in living-broken.jpg living-lying.jpg living-notmp4.jpg; do adb push "$ROW_DIR/fix/$f" "$DIR/$f" >/dev/null; done
scan; sleep 2
BROKEN="$(img_id Pictures/living-qa/ living-broken.jpg)"; LYING="$(img_id Pictures/living-qa/ living-lying.jpg)"; NOTMP4="$(img_id Pictures/living-qa/ living-notmp4.jpg)"
record "ids broken / lying / notmp4" "$BROKEN / $LYING / $NOTMP4"
assert_eq "three new image rows" "$((COUNT0 + 3))" "$(count_rows $IMAGES)"
adb shell am force-stop app.tileshell; ensure_start; sleep 2
PID0="$(launcher_pid)"; record "launcher pid at the start" "$PID0"

adb shell am start -n app.tileshell/.photos.PhotosActivity >/dev/null; sleep 4
C="$ROW_DIR/collection.xml"; dump_ui "$C"
assert_eq "the three are tiles of the collection" "yes yes yes" "$(has_node "$C" "photos_item:$BROKEN") $(has_node "$C" "photos_item:$LYING") $(has_node "$C" "photos_item:$NOTMP4")"
assert_eq "glyphs: the one the reader takes, and neither of the two it refuses" "$BROKEN" "$(ids_of "$C" photos_living:)"

# The clip that is not an MP4 after its first box: the hold's error form, the still left showing.
tap_node "$C" "photos_item:$BROKEN"; sleep 3
V="$ROW_DIR/viewer.xml"; dump_ui "$V"
assert_eq "its viewer carries the glyph" "yes" "$(has_node "$V" viewer_living)"
IB="$(bounds "$V" viewer_image)"; screencap "$ROW_DIR/still.png"
HMARK="$(ring_mark)"; hold_start 540 1170 2500; sleep 1.6; screencap "$ROW_DIR/mid.png"; wait "$HOLD_PID"; sleep 1.5
SLICE="$(living_lines "$HMARK")"; record "the hold's lines" "$(echo "$SLICE" | tr '\n' '|')"
assert_contains "the play line with the stated clip's bytes" "living $BROKEN: play 4096 bytes" "$SLICE"
assert_contains "the stop line in its error form" "living $BROKEN: stop (error " "$SLICE"
assert_eq "one play and one stop" "play stop" "$(echo "$SLICE" | sed -E 's/living [^:]*: ([a-z]+).*/\1/' | xargs)"
MID="$(frame_diff "$ROW_DIR/still.png" "$ROW_DIR/mid.png" $IB)"; record "mid-hold vs the still (% of the photo's pixels)" "$MID"
if [ "$(python3 -c "print('yes' if $MID < 0.5 else 'no')")" = yes ]; then _verdict PASS "the still stays through the hold" "$MID %"; else _verdict FAIL "the still stays through the hold" "$MID %"; fi
dump_ui "$ROW_DIR/after.xml"
assert_eq "the picture is still shown, with no error state and no clip surface" "yes no no" "$(has_node "$ROW_DIR/after.xml" viewer_image) $(has_node "$ROW_DIR/after.xml" viewer_error) $(has_node "$ROW_DIR/after.xml" viewer_living_clip)"
# A second hold works the same (the first error left no player behind).
HMARK="$(ring_mark)"; adb shell input swipe 540 1170 540 1170 1500; sleep 1.5
assert_eq "a second hold: one play and one stop again" "play stop" "$(living_lines "$HMARK" | sed -E 's/living [^:]*: ([a-z]+).*/\1/' | xargs)"
adb shell input keyevent KEYCODE_BACK; sleep 2

# The two the reader refuses: plain stills.
for pair in "lying:$LYING" "notmp4:$NOTMP4"; do
  n="${pair%%:*}"; id="${pair#*:}"
  dump_ui "$C"; tap_node "$C" "photos_item:$id"; sleep 3
  dump_ui "$ROW_DIR/$n.xml"
  assert_eq "$n: shown, with no glyph" "yes no" "$(has_node "$ROW_DIR/$n.xml" viewer_image) $(has_node "$ROW_DIR/$n.xml" viewer_living)"
  HMARK="$(ring_mark)"; adb shell input swipe 540 1170 540 1170 2000; sleep 1.5
  assert_eq "$n: a hold logs nothing" "" "$(living_lines "$HMARK")"
  adb shell input keyevent KEYCODE_BACK; sleep 1
  # The plain hold toggled the chrome (a slow tap, as before): Back may have been taken by nothing; make sure of the page.
  dump_ui "$ROW_DIR/$n-back.xml"; [ "$(has_node "$ROW_DIR/$n-back.xml" viewer_image)" = yes ] && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
done
adb shell input keyevent KEYCODE_HOME; sleep 1

assert_eq "the launcher's pid is unchanged" "$PID0" "$(launcher_pid)"
assert_eq "no AndroidRuntime line names the shell" "" "$(adb logcat -d -v brief -s AndroidRuntime -T "$T0" 2>/dev/null | grep -F 'app.tileshell' | head -3)"
ring_save launcher
for f in living-broken.jpg living-lying.jpg living-notmp4.jpg; do adb shell rm -f "$DIR/$f"; done
adb shell rmdir "$DIR"
scan; sleep 2
assert_eq "images back at the census" "$COUNT0" "$(count_rows $IMAGES)"
assert_eq "videos back at the census" "$VCOUNT0" "$(count_rows $VIDEOS)"
assert_eq "the folder is gone" "" "$(adb shell "ls -d $DIR 2>/dev/null" | tr -d '\r')"
ensure_start
row_end
