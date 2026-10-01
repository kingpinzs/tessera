#!/usr/bin/env bash
# E13 follow-up probe: does the app list's search filter on text typed through LatinIME (composing, underlined) and
# through the shell's own keyboard? One dump each, 5 s after typing.
export ANDROID_SERIAL=emulator-5554
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/../../scripts/lib.sh"; . "$HERE/../../scripts/p14.sh"
ROW_DIR="$HERE"; take_device_lock
one() { # label
  adb shell input keyevent KEYCODE_HOME; sleep 2; ensure_start; swipe_left 2
  dump_ui "$HERE/$1-list.xml"; tap_node "$HERE/$1-list.xml" applist_search; sleep 1.5
  adb shell input text fossify; sleep 5
  dump_ui "$HERE/$1-search.xml"; screencap "$HERE/$1-search.png"
  echo "$1: ime=$(adb shell settings get secure default_input_method | tr -d '\r') rows=$(grep -oE 'resource-id="applist_row:[^"]*"' "$HERE/$1-search.xml" | wc -l) field=[$(node_text "$HERE/$1-search.xml" applist_search)]"
  adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
}
one latin
adb shell ime enable app.tileshell/.ime.KeyboardService >/dev/null; adb shell ime set app.tileshell/.ime.KeyboardService >/dev/null; sleep 2
one shell
ensure_start
