#!/usr/bin/env bash
# E8 Measured motion holds with acrylic on, on the shell's own [motion] clock (phase 13 Acceptance E8, C-5, C-20, C-31,
# T13-7, T13-27): the pane's slide settles 250 +- 17 ms after t0 (R7 §3.1.10); the reminder menu's grow settles 233 ms +
# one frame after its half-height first frame (R7 §3.6.4; t0 is that first frame's composition, so settle = 249.7 +- 17);
# the pivot settles 250 ms +- one frame after the release (X13); the app-list band's first frame is <= 33.4 ms after the
# hold's 783-ms uptime (a jump, no motion added); every line's maxGapMs <= 33.4 ms. Each line is read from the ring slice
# after a MARK taken just before its open / swipe. One warm-up per motion is recorded, not asserted (the first open after a
# start janks on the AVD's debug build — phase 11 E6's finding); three runs each are asserted, with nothing recording.
# Then one 60-fps screenrecord of each (show_touches 1, restored to 0) corroborates under phase 05's frame-spacing rule and
# is recorded, never the clock.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"

row_begin E8 "measured motion with acrylic on: pane 250, reminder menu 233 + 1 frame, pivot 250, app-list band <= 2 vsync"
FRAME=16.7

field() { echo "$1" | grep -oE "$2=[0-9.]+" | head -1 | cut -d= -f2; }
motion_line() { # mark name
  ring_since "$1" | grep -F "[motion] $2 " | tail -1
}
# check <name> <line> <expected settle> <tolerance> [<le|within>]
check() {
  local n="$1" line="$2" want="$3" tol="$4" mode="${5:-within}" s g
  s="$(field "$line" settle)"; g="$(field "$line" maxGapMs)"
  note "$n: ${line#*\[motion\] }"
  if [ -z "$line" ]; then _verdict FAIL "$n: a [motion] line after the MARK" "none"; return; fi
  if [ "$mode" = le ]; then
    assert_eq "$n: settle <= $want ms (got $s)" yes "$(python3 -c "print('yes' if $s <= $want else 'no')")"
  else
    assert_within "$n: settle = $want +- $tol ms" "$want" "$s" "$tol"
  fi
  assert_eq "$n: maxGapMs <= 33.4 (got $g)" yes "$(python3 -c "print('yes' if $g <= 33.4 else 'no')")"
}

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
show_start 6
MOTIONS="$ROW_DIR/motion-lines.txt"; : > "$MOTIONS"

# ================================================================ the pane's slide
ensure_start
cortana_assist
sleep 3
for i in 0 1 2 3; do
  dump_ui "$ROW_DIR/.home.xml"
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/.home.xml" cortana_menu_button
  sleep 1.5
  L="$(motion_line "$MARK" cortana_pane)"; echo "pane[$i] $L" >> "$MOTIONS"
  if [ $i -eq 0 ]; then record "pane warm-up" "${L#*\[motion\] }"; else check "pane[$i]" "$L" 250 17; fi
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
cortana_close

# ================================================================ the reminder menu's grow
make_typed_reminder "remind me to check the QA13 motion list tomorrow at 9 am"
open_reminders
ID="$(reminder_id "QA13 motion")"
assert_ne "a reminder row to hold" "" "$ID"
set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$ID"); RX=$(( ($1 + $3) / 2 )); RY=$(( ($2 + $4) / 2 ))
for i in 0 1 2 3; do
  MARK="$(ring_mark)"
  adb shell input swipe $RX $RY $RX $RY 1000
  sleep 1.5
  L="$(motion_line "$MARK" reminder_menu)"; echo "reminder_menu[$i] $L" >> "$MOTIONS"
  if [ $i -eq 0 ]; then record "reminder menu warm-up" "${L#*\[motion\] }"; else check "reminder_menu[$i]" "$L" 249.7 17; fi
  adb shell input tap 1000 1500; sleep 1.2
done

# ================================================================ the pivot's settle (Start <-> app list)
cortana_close
show_start 5
for i in 0 1 2 3; do
  MARK="$(ring_mark)"
  if [ $((i % 2)) -eq 0 ]; then to_app_list 2; else to_start 2; fi
  L="$(motion_line "$MARK" pivot)"; echo "pivot[$i] $L" >> "$MOTIONS"
  if [ $i -eq 0 ]; then record "pivot warm-up" "${L#*\[motion\] }"; else check "pivot[$i]" "$L" 250 $FRAME; fi
done
to_start 2

# ================================================================ the app-list band's first frame (a jump)
to_app_list 3
dump_ui "$ROW_DIR/applist.xml"
ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/applist.xml" "$ROWID"); AX=$(( ($1 + $3) / 2 )); AY=$(( ($2 + $4) / 2 ))
for i in 0 1 2 3; do
  MARK="$(ring_mark)"
  adb shell input swipe $AX $AY $AX $AY 1000
  sleep 1.2
  L="$(motion_line "$MARK" applist_menu)"; echo "applist_menu[$i] $L" >> "$MOTIONS"
  if [ $i -eq 0 ]; then record "app-list band warm-up" "${L#*\[motion\] }"; else check "applist_menu[$i]" "$L" 33.4 0 le; fi
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
to_start 2

# ================================================================ corroboration: one screenrecord of each (recorded)
adb shell settings put system show_touches 1
rec() { # name, then the action as the remaining words
  local n="$1"; shift
  adb shell rm -f /sdcard/Download/e8.mp4
  adb shell screenrecord --size 540x1170 --bit-rate 6000000 --time-limit 4 /sdcard/Download/e8.mp4 & local pid=$!
  sleep 0.8
  "$@"
  wait $pid
  adb pull /sdcard/Download/e8.mp4 "$ROW_DIR/rec-$n.mp4" >/dev/null 2>&1
  record "screenrecord $n (phase 05's rule: max_gap_ms <= 18.2 during the motion)" "$(python3 "$P13/motion_frames.py" "$ROW_DIR/rec-$n.mp4" "${TMPDIR:-/tmp}/qa13-e8-frames-$n" 2>&1 | tail -1)"
}
act_pane() { dump_ui "$ROW_DIR/.h.xml"; tap_node "$ROW_DIR/.h.xml" cortana_menu_button; sleep 1.5; }
act_menu() { adb shell input swipe $RX $RY $RX $RY 1000; sleep 1.2; }
act_pivot() { to_app_list 1.5; }
act_band() { adb shell input swipe $AX $AY $AX $AY 1000; sleep 1; }
ensure_start; cortana_assist; sleep 3
rec pane act_pane
adb shell input keyevent KEYCODE_BACK; sleep 1; cortana_close
open_reminders
rec reminder_menu act_menu
adb shell input tap 1000 1500; sleep 1.2; cortana_close
show_start 5
rec pivot act_pivot
rec applist_menu act_band
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell settings put system show_touches 0
assert_eq "show_touches restored to 0" "0" "$(adb shell settings get system show_touches | tr -d '\r')"

# ---- restore
open_reminders
set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$ID")
delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
open_reminders
assert_eq "restore: no QA13 reminder left" "" "$(reminder_id "QA13")"
cortana_close
show_start 3
row_end
