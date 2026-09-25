#!/usr/bin/env bash
# Phase 13's own driver helpers, sourced after lib.sh. The shared floor (rows, asserts, rings, dumps, battery saver)
# is lib.sh's; this file holds only what phase 13's rows share with each other.

# This session drives emulator-5554 only (other emulators belong to paused phase 15 work).
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"

P13="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The fixture route is phase 01 item4.sh's frame-photo route: a picture in /sdcard/Pictures, media-scanned, set by
# its MediaStore content URI (the shell holds READ_MEDIA_IMAGES). A file pushed under /sdcard/Android/data/<pkg>
# lands in a root-owned directory the app cannot read (EACCES, observed 2026-09-25), so that path is not used.
CHECKER_PATH="/sdcard/Pictures/qa13-checker.png"
CHECKER_URI=""

# One key of shared_prefs/start_theme.xml, through phase 01's route (qa/phase-01/scripts/item4.sh): the shell
# rewrites its prefs as it exits, so stop it, write, and stop it again so the next start is the first to read it.
set_pref() { # <key> <boolean|string> <value|--remove>
  local dir="${ROW_DIR:-$P13}"
  adb shell am force-stop $PKG; sleep 1
  adb shell run-as $PKG cat shared_prefs/start_theme.xml > "$dir/prefs-in.xml" 2>/dev/null
  [ -s "$dir/prefs-in.xml" ] || printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map />\n' > "$dir/prefs-in.xml"
  python3 "$QA/../phase-01/scripts/prefs_edit.py" "$dir/prefs-in.xml" "$1" "$2" "$3" > "$dir/prefs-out.xml"
  adb shell "run-as $PKG sh -c 'cat > shared_prefs/start_theme.xml'" < "$dir/prefs-out.xml"
  adb shell am force-stop $PKG; sleep 2
}

# A picture pushed to /sdcard/Pictures and scanned; prints its content://media URI.
push_picture() { # local.png device-path
  adb push "$1" "$2" >/dev/null
  adb shell content call --uri content://media --method scan_file --arg "$2" >/dev/null 2>&1
  sleep 1
  local id
  id="$(adb shell content query --uri content://media/external/images/media --projection _id:_data 2>/dev/null \
    | tr -d '\r' | grep -F "_data=${2/#\/sdcard\//\/storage\/emulated\/0\/}" | grep -o '_id=[0-9]*' | tail -1 | sed 's/_id=//')"
  [ -n "$id" ] && echo "content://media/external/images/media/$id"
}

# The checkerboard fixture (T13-12) pushed and set as the Start background; restore with clear_background.
set_checker() { # [W H]
  local png="${ROW_DIR:-$P13}/checker.png"
  python3 "$P13/make_fixtures.py" checker "$png" "${1:-1080}" "${2:-2340}" >/dev/null
  CHECKER_URI="$(push_picture "$png" "$CHECKER_PATH")"
  [ -n "$CHECKER_URI" ] || { echo "set_checker: the checker was not scanned into MediaStore" >&2; return 1; }
  set_pref background string "$CHECKER_URI"
}

# Removes the background key and the pushed picture (the fixture's restore).
clear_background() {
  set_pref background string --remove
  adb shell rm -f "$CHECKER_PATH"
  adb shell content call --uri content://media --method scan_file --arg "$CHECKER_PATH" >/dev/null 2>&1
}

show_start() { adb shell input keyevent KEYCODE_HOME; sleep "${1:-6}"; }

# Start → the app list with the pivot swipe (the app list is Start's pager page 1).
# The display's current size as "W H" (a wm size override wins).
screen_size() {
  adb shell wm size | tr -d '\r' | awk -F': ' '{print $2}' | tail -1 | tr 'x' ' '
}
to_app_list() {
  local w h
  read -r w h <<< "$(screen_size)"
  adb shell input swipe $(( w * 5 / 6 )) $(( h / 2 )) $(( w / 7 )) $(( h / 2 )) 250
  sleep "${1:-2}"
}
to_start() {
  local w h
  read -r w h <<< "$(screen_size)"
  adb shell input swipe $(( w / 7 )) $(( h / 2 )) $(( w * 5 / 6 )) $(( h / 2 )) 250
  sleep "${1:-2}"
}

# The Transparency effects toggle, through the product path: Settings > Start + theme, tag
# theme_transparency_effects. open_transparency brings the row on screen; tap_transparency taps it (take the MARK
# just before it); leave_settings goes Back to wherever Settings was opened from.
SETTINGS_DUMP=""
open_transparency() {
  SETTINGS_DUMP="${ROW_DIR:-$P13}/settings-theme.xml"
  adb shell am start -n $PKG/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1
  sleep 2
  scroll_to_node "$SETTINGS_DUMP" theme_transparency_effects 6
}
tap_transparency() { tap_node "$SETTINGS_DUMP" theme_transparency_effects; sleep 1; }
# Settings opened on a page keeps its home page under it, so Back is pressed until Settings is gone.
leave_settings() {
  local i
  for i in 1 2 3; do
    adb shell input keyevent KEYCODE_BACK
    sleep 1.5
    adb shell dumpsys window | grep -m1 mCurrentFocus | grep -q SettingsActivity || return 0
  done
  return 1
}

transparency_state() { # prints true / false, read from the prefs file
  adb shell run-as $PKG cat shared_prefs/start_theme.xml 2>/dev/null \
    | grep -o 'name="transparency_effects" value="[a-z]*"' | sed 's/.*value="//; s/"//'
}

# The wall= stamp of the first ring line in stdin matching a fixed string.
wall_of() { # needle  (ring lines on stdin)
  grep -F "$1" | grep -oE 'wall=[0-9]+' | head -1 | cut -d= -f2
}

# The checker's square-centre rows on the app list (the page is 1080 x 2196 at y 0; the picture is Crop-placed at
# scale 1, so its 270-px squares start 72 px above the page top: centres at y = 270k + 63).
CHECKER_ROWS="603 873 1143 1413 1683 1953 333"

# A text-free strip on the app list's right half, found from the dump; prints its y (a square-centre row).
applist_strip() { # dump.xml
  python3 "$P13/dumpq.py" clear_rows "$1" 675 945 $CHECKER_ROWS
}

# Edge spread across the checker's x = 810 boundary on that strip: "width plateau_a plateau_b".
applist_edge() { # capture.png y
  python3 "$P13/edge.py" edge "$1" $(( $2 - 4 )) $(( $2 + 4 )) 675 945
}

# The same, in a wm-size pass: squares are W/4 wide, so the boundary between the 3rd and 4th columns is at 3W/4.
applist_edge_w() { # capture.png y W
  local s=$(( $3 / 4 ))
  python3 "$P13/edge.py" edge "$1" $(( $2 - 4 )) $(( $2 + 4 )) $(( 2 * s + s / 2 )) $(( 3 * s + s / 2 ))
}
