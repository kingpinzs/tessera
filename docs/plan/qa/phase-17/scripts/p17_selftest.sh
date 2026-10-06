#!/usr/bin/env bash
# Phase 17 harness self-test (build task 16): the helpers of p17.sh do what the rows will rely on — media_up pushes only
# the named fixtures with one minute between them and media_down brings the counts back to the census; a helper that
# cannot fail proves nothing, so each has a control. It installs nothing and leaves the device as it found it.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p17.sh"
take_device_lock
row_begin P17SELF "harness self-test: media_up / media_down, px, absent_in"
assert_eq "wake" "Awake" "$(wake_device)"
[ -f "$GEN/qa-steps.mp4" ] || bash "$P17/scripts/make_videos.sh" "$GEN" >/dev/null
[ -f "$GEN/qa-line.png" ] || python3 "$P01S/make_photos.py" "$GEN" editor >/dev/null

media_up qa-photo-0.png qa-photo-1.png qa-photo-2.png qa-photo-3.png qa-photo-4.png qa-photo-5.png qa-steps.mp4
assert_eq "six images more than the census" "$((CENSUS_IMAGES + 6))" "$(media_count images)"
assert_eq "one video more than the census" "$((CENSUS_VIDEO + 1))" "$(media_count video)"
ROWS="$(q "content query --uri content://media/external/images/media --projection _display_name:relative_path:date_modified")"
assert_contains "qa-photo-0 in DCIM/Camera" "_display_name=qa-photo-0.png, relative_path=DCIM/Camera/" "$ROWS"
assert_contains "qa-photo-5 in Pictures/QA-Album" "_display_name=qa-photo-5.png, relative_path=Pictures/QA-Album/" "$ROWS"
# The emulator already holds phase 01's qa-photo-0..5 in Pictures/ (the census), so a row is named by file AND folder.
m() { echo "$ROWS" | grep -F "_display_name=$1, relative_path=$2," | sed -n 's/.*date_modified=\([0-9]*\).*/\1/p' | head -1; }
assert_eq "qa-photo-1 is one minute older than qa-photo-0" "60" "$(( $(m qa-photo-0.png DCIM/Camera/) - $(m qa-photo-1.png DCIM/Camera/) ))"
assert_eq "qa-photo-5 is five minutes older than qa-photo-0" "300" "$(( $(m qa-photo-0.png DCIM/Camera/) - $(m qa-photo-5.png Pictures/QA-Album/) ))"
assert_ne "media_id finds the pushed video" "" "$(media_id video qa-steps.mp4)"
media_down
assert_absent "nothing is left in the two fixture folders" "qa-" "$(q "content query --uri content://media/external/images/media --projection _display_name:relative_path" | grep -E 'relative_path=(DCIM/Camera|Pictures/QA-Album)/')$(q "content query --uri content://media/external/video/media --projection _display_name:relative_path" | grep -F 'relative_path=Movies/')"

# px reads a known pixel of a generated PNG (qa-redeye: red at its centre, grey outside the disc).
assert_eq "px: the disc's centre" "255,0,0" "$(px "$GEN/qa-redeye.png" 320 240)"
assert_eq "px: 30 px outside the disc" "128,128,128" "$(px "$GEN/qa-redeye.png" 370 240)"
assert_eq "px: the line's centre is black" "0,0,0" "$(px "$GEN/qa-line.png" 320 240)"
assert_eq "px: the line rises to the right" "0,0,0" "$(px "$GEN/qa-line.png" 520 205)"
assert_eq "px: off the line is white" "255,255,255" "$(px "$GEN/qa-line.png" 520 240)"

# absent_in on a live slice passes; on an empty slice it must FAIL — counted here by hand so the row itself stays green.
MARK="$(ring_mark)"; adb shell am start -n app.tileshell/.SettingsActivity >/dev/null 2>&1; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 1
absent_in "absent_in on a live slice" "no-such-needle-xyz" "$(ring_since 0 launcher)"
BEFORE=$FAIL; absent_in "(control) absent_in on an empty slice" "x" "" >/dev/null 2>&1
if [ "$FAIL" -eq $((BEFORE + 1)) ]; then FAIL=$BEFORE; _verdict PASS "absent_in fails on an empty slice (control)" "it counted one FAIL, taken back"; else _verdict FAIL "absent_in fails on an empty slice (control)" "it did not fail"; fi
ensure_start
row_end
