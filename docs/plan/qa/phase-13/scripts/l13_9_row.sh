#!/usr/bin/env bash
# L13-9 regression row (INDEX ledger): the Clock app's alarm editor flyouts (Repeats, Sound, Snooze time) closed on Back
# only by writing state, and stayed hit-testable until they left the composition a frame later, so a touch in that frame
# was lost to the flyout's stale full-screen scrim (L13-3's shape, phase 15's part). Each trial opens the Repeats flyout
# (A), then sends Back and a tap on the Snooze time field (B) together from one device shell, B offset by 0-20 ms, and
# reads whether B opened the Snooze flyout. After each trial the state is read and restored. Modelled on l13_6_probe.sh.
#   l13_9_row.sh [n]    (n trials, default 30)
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
N="${1:-30}"
CLOCK_ACT="app.tileshell/.clock.ClockActivity"

where() { # dump -> snooze | days | editor | other
  python3 - "$1" <<'PY'
import sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
if 'resource-id="alarm_snooze_flyout"' in s: print("snooze")
elif 'resource-id="alarm_days_flyout"' in s: print("days")
elif 'resource-id="alarm_editor_field:snooze"' in s: print("editor")
else: print("other")
PY
}
editor() { # open a new alarm's editor; leaves $ROW_DIR/editor.xml
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n "$CLOCK_ACT" --es page alarm > /dev/null 2>&1; sleep 1.5
  dump_ui "$ROW_DIR/list.xml"; tap_node "$ROW_DIR/list.xml" 'clock_bar:add'; sleep 1.5
  dump_ui "$ROW_DIR/editor.xml"
}
centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }

row_begin L13_9 "a tap right after Back closes an alarm editor flyout reaches the editor (n=$N)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
editor
read -r AX AY <<< "$(centre "$ROW_DIR/editor.xml" 'alarm_editor_field:repeats')"
read -r BX BY <<< "$(centre "$ROW_DIR/editor.xml" 'alarm_editor_field:snooze')"
assert_ne "the editor is open (Repeats field found)" "" "${AX:-}"
assert_ne "the Snooze time field found" "" "${BX:-}"
note "A = Repeats ($AX,$AY), B = Snooze time ($BX,$BY), apk=$(installed_apk_id)"
mkdir -p "$ROW_DIR/trials"
: > "$ROW_DIR/slice.txt"; : > "$ROW_DIR/trials.txt"
for i in $(seq 1 "$N"); do
  off=$(( (i % 5) * 5 ))
  MARK="$(ring_mark)"
  adb shell input tap $AX $AY
  sleep 0.8
  adb shell "input keyevent 4 & sleep 0.0$(printf %02d $off); input tap $BX $BY; wait"
  sleep 1.2
  dump_ui "$ROW_DIR/trials/t$i.xml"
  st="$(where "$ROW_DIR/trials/t$i.xml")"
  echo "t$i offset=${off}ms after: $st" >> "$ROW_DIR/trials.txt"
  ring_since "$MARK" >> "$ROW_DIR/slice.txt"
  case "$st" in
    snooze|days) adb shell input keyevent 4; sleep 0.8 ;;
    editor) ;;
    *) editor ;;
  esac
done
OK="$(grep -c 'after: snooze' "$ROW_DIR/trials.txt")"
note "trials $N: B opened the Snooze flyout $OK"
assert_eq "every tap sent with the Back opened the Snooze flyout" "$N" "$OK"
adb shell am force-stop $PKG; sleep 1
show_start 3
row_end
