#!/usr/bin/env bash
# Build-start check (phase 13 Decisions, "Build-start checks", T13-12): HWUI's radius -> sigma formula on API 36, read
# on a blurred ISOLATED step. The half-black / half-white split picture (make_fixtures.py split, the step at x = 540)
# is the Start background; on the app list, the static route blurs it with r = 30 epx = 90 px. The step's 10-90 %
# width, read between plateaus 5 sigma either side (x 270 .. 810), is expected at 2.563 * sigma with
# sigma = 0.57735 * r + 0.5 = 52.46 px -> 134.5 px. The checker's value (130.2 px, E2) is the 4-across pattern's, whose
# neighbouring edges lie 2.6 sigma away; the isolated step has none. Recorded in the doc's Decisions via the Change Log.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin BS_STEP "build-start: HWUI's sigma on an isolated half / half step (static route, r = 90 px)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
python3 "$P13/make_fixtures.py" split "$ROW_DIR/split.png" 1080 2340 >/dev/null
URI="$(push_picture "$ROW_DIR/split.png" /sdcard/Pictures/qa13-step.png)"
assert_ne "the split picture is in MediaStore" "" "$URI"
# The MARK goes before set_pref: the shell is Home, so Android restarts it the instant set_pref force-stops it, and
# that new process builds the layer before any later MARK (the first run's FAIL, kept in BS_STEP-run1-mark-late/).
MARK="$(ring_mark)"
set_pref background string "$URI"
show_start 7
to_app_list 3
ring_since "$MARK" > "$ROW_DIR/slice.txt"
assert_contains "the static layer is built for the split picture" "[fluent] static backdrop rebuilt for $URI in " "$(cat "$ROW_DIR/slice.txt")"
dump_ui "$ROW_DIR/applist.xml"
screencap "$ROW_DIR/applist.png"
# shellcheck disable=SC2046
Y="$(python3 "$P13/dumpq.py" clear_rows "$ROW_DIR/applist.xml" 270 810 $(seq 400 10 2000))"
assert_ne "a text-free strip across x 270..810" "" "$Y"
read -r W A B <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/applist.png" $(( Y - 4 )) $(( Y + 4 )) 270 810)"
EXP="$(python3 "$P13/edge.py" expected_width 90)"
note "strip y=$Y x 270..810: width=$W plateaus=$A/$B expected=$EXP"
record "isolated step: 10-90 % width (px)" "$W"
record "isolated step: implied sigma = width / 2.563 (px)" "$(python3 -c "print(round($W / 2.563, 2))")"
assert_within "the isolated step's width = 2.563 sigma(r = 90 px) +- 20 %" "$EXP" "$W" "$(python3 -c "print(round($EXP * 0.2, 1))")"
assert_within "plateau (black half) = 0.2 x 0" 0 "$A" 3
assert_within "plateau (white half) = 0.2 x 255" 51 "$B" 3
to_start 2
clear_background
adb shell rm -f /sdcard/Pictures/qa13-step.png
adb shell content call --uri content://media --method scan_file --arg /sdcard/Pictures/qa13-step.png >/dev/null 2>&1
show_start 3
row_end
