#!/usr/bin/env bash
# L13-7 regression row (INDEX ledger; Jeremy's report 2026-09-26: "I click from home screen then press the back
# button" and Start does not show). The Tess tile plays Start's exit and then opens Tess's session, a window over
# Start that never pauses it, so onResume never played the entrance and Start stayed on the exit's last frame: black,
# no tiles (qa/phase-13/L13-7-tess-tile-repro-ad784939/). Each case taps the Tess tile, leaves Tess one way, and
# requires every tile back plus one entrance. The control launches Settings from its tile: one entrance, not two.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$QA/../phase-02/scripts/layout.sh"

focus() { adb shell dumpsys window | grep -m1 mCurrentFocus | sed 's/.*Window{[0-9a-f]* u0 //; s/}//' | tr -d '\r'; }
tiles() { grep -o 'resource-id="tile:[^"]*"' "$1" | wc -l | tr -d ' '; }
tap_tag() { set -- $(bounds "$1" "$2"); adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }

row_begin L13_7 "leaving Tess opened from her tile brings Start back (system Back, Tess's Back, Tess's Windows key; control)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
layout_restore "$(dirname "$0")/l13_7_layout.json" > "$ROW_DIR/layout-restore.txt" 2>&1
sleep 2
dump_ui "$ROW_DIR/0-start.xml"
N0="$(tiles "$ROW_DIR/0-start.xml")"
assert_eq "the Tess tile is on Start" "yes" "$(has_node "$ROW_DIR/0-start.xml" tile:shell:cortana)"
note "tiles on Start before: $N0"

one() { # tag how(back|drawn_back|drawn_windows)
  local tag="$1" how="$2" mark
  adb shell input keyevent KEYCODE_HOME; sleep 2
  dump_ui "$ROW_DIR/$tag-0.xml"
  mark="$(ring_mark)"
  tap_tag "$ROW_DIR/$tag-0.xml" tile:shell:cortana; sleep 3
  assert_eq "$tag: Tess opened over Start" "VoiceInteractionSession" "$(focus)"
  case "$how" in
    back) adb shell input keyevent KEYCODE_BACK ;;
    drawn_back) dump_ui "$ROW_DIR/$tag-tess.xml"; tap_tag "$ROW_DIR/$tag-tess.xml" nav_back ;;
    drawn_windows) dump_ui "$ROW_DIR/$tag-tess.xml"; tap_tag "$ROW_DIR/$tag-tess.xml" nav_windows ;;
  esac
  sleep 3
  dump_ui "$ROW_DIR/$tag-1.xml"; screencap "$ROW_DIR/$tag-1.png"
  ring_since "$mark" > "$ROW_DIR/$tag-slice.txt"
  assert_eq "$tag: Start has focus again" "app.tileshell/app.tileshell.StartActivity" "$(focus)"
  assert_eq "$tag: every tile is back" "$N0" "$(tiles "$ROW_DIR/$tag-1.xml")"
  assert_eq "$tag: Start played its entrance once" "1" "$(grep -cE '\[motion\] start entrance( \(focus back\))?$' "$ROW_DIR/$tag-slice.txt")"
}
one back back
one drawn-back drawn_back
one drawn-windows drawn_windows

# Control: a tile that launches an activity (Settings) pauses Start; it comes back in onResume, and the focus that
# returns after it must not play a second entrance.
adb shell input keyevent KEYCODE_HOME; sleep 2
dump_ui "$ROW_DIR/ctl-0.xml"
MARK="$(ring_mark)"
tap_tag "$ROW_DIR/ctl-0.xml" tile:shell:settings; sleep 3
assert_contains "control: Settings opened" "Settings" "$(focus)"
adb shell input keyevent KEYCODE_BACK; sleep 3
dump_ui "$ROW_DIR/ctl-1.xml"
ring_since "$MARK" > "$ROW_DIR/ctl-slice.txt"
assert_eq "control: every tile is back" "$N0" "$(tiles "$ROW_DIR/ctl-1.xml")"
assert_eq "control: Start played its entrance once" "1" "$(grep -cE '\[motion\] start entrance( \(focus back\))?$' "$ROW_DIR/ctl-slice.txt")"

MARK="$(ring_mark)"
layout_restore "$QA/../phase-02/baseline_layout.json" > "$ROW_DIR/layout-baseline.txt" 2>&1
assert_absent "restore: phase 02's baseline, no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"
show_start 3
row_end
