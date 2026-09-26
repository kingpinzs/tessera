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

# ================================================================ corroboration: a screenrecord of each, phase 05's rule
# C-5: a screenrecord corroborates each motion under phase 05's frame-spacing rule, and phase 05's rule is that a capture
# whose source frames lie more than 18.2 ms apart during the motion is REJECTED AND RETAKEN (qa/phase-05/README.md). Each
# motion is therefore retaken, up to 10 times, until one capture passes: its motion window (motion_frames.py, read inside
# the surface's own bounds so the show_touches dot and live tiles elsewhere cannot open or widen it) has every source
# frame <= 18.2 ms apart, and it spans the motion the shell logged for that same attempt (the pane and the reminder
# menu: window = settle - 1 frame +- 2 frames; the pivot: at least its settle, because the finger's drag precedes the
# release its t0 marks). The band is a jump (its first frame at full height): corroborated when it appears within two
# source frames. The first run recorded one capture per motion and accepted nothing (gate review B, B2).
adb shell settings put system show_touches 1
roi_half() { # dump.xml resource-id -> "x0,y0,x1,y1" in the 540 x 1170 capture
  local b; b="$(bounds "$1" "$2")"
  [ -n "$b" ] || return 0
  set -- $b; echo "$(( $1 / 2 )),$(( $2 / 2 )),$(( ($3 + 1) / 2 )),$(( ($4 + 1) / 2 ))"
}
corroborate() { # name roi mode(open|pivot|jump) act reset
  local n="$1" roi="$2" mode="$3" act="$4" reset="$5" a ok="" out line settle win gap wf mark pid
  for a in $(seq 1 10); do
    adb shell rm -f /sdcard/Download/e8.mp4
    adb shell screenrecord --size 540x1170 --bit-rate 6000000 --time-limit 4 /sdcard/Download/e8.mp4 & pid=$!
    sleep 0.8
    mark="$(ring_mark)"
    "$act"
    wait $pid
    adb pull /sdcard/Download/e8.mp4 "$ROW_DIR/rec-$n-$a.mp4" >/dev/null 2>&1
    line="$(motion_line "$mark" "$n")"; settle="$(field "$line" settle)"
    out="$(python3 "$P13/motion_frames.py" "$ROW_DIR/rec-$n-$a.mp4" "${TMPDIR:-/tmp}/qa13-e8-frames-$n" 0.2 "$roi" 2>/dev/null | tail -1)"
    note "$n attempt $a (roi $roi): $out; [motion] settle=${settle:-none}"
    win="$(echo "$out" | grep -oE 'window_ms=[0-9.]+' | cut -d= -f2)"
    gap="$(echo "$out" | grep -oE 'max_gap_ms=[0-9.]+' | cut -d= -f2)"
    wf="$(echo "$out" | grep -oE 'window_frames=[0-9]+' | cut -d= -f2)"
    "$reset"
    case "$mode" in
      open)  python3 -c "import sys; w,g,s=map(float,sys.argv[1:]); sys.exit(0 if g <= 18.2 and abs(w-(s-16.7)) <= 33.4 else 1)" "${win:-0}" "${gap:-99}" "${settle:-0}" && ok="$a" ;;
      pivot) python3 -c "import sys; w,g,s=map(float,sys.argv[1:]); sys.exit(0 if g <= 18.2 and w >= s-33.4 else 1)" "${win:-0}" "${gap:-99}" "${settle:-9999}" && ok="$a" ;;
      jump)  [ -n "$wf" ] && [ "$wf" -ge 1 ] && [ "$wf" -le 2 ] && ok="$a" ;;
    esac
    [ -n "$ok" ] && break
  done
  assert_ne "$n: a screenrecord corroborates it under phase 05's rule (retaken up to 10)" "" "$ok"
  record "$n: the accepted capture" "${ok:+attempt $ok: $out; settle=$settle}"
}
act_pane() { dump_ui "$ROW_DIR/.h.xml"; tap_node "$ROW_DIR/.h.xml" cortana_menu_button; sleep 1.5; }
reset_pane() { adb shell input keyevent KEYCODE_BACK; sleep 1; }
act_menu() { adb shell input swipe $RX $RY $RX $RY 1000; sleep 1.2; }
reset_menu() { adb shell input tap 1000 1500; sleep 1.2; }
act_pivot() { to_app_list 1.5; }
reset_pivot() { to_start 2; }
act_band() { adb shell input swipe $AX $AY $AX $AY 1000; sleep 1; }
reset_band() { adb shell input keyevent KEYCODE_BACK; sleep 1; }
# the regions, from dumps of each surface open
ensure_start; cortana_assist; sleep 3
act_pane; dump_ui "$ROW_DIR/roi-pane.xml"; reset_pane
corroborate cortana_pane "$(roi_half "$ROW_DIR/roi-pane.xml" cortana_pane)" open act_pane reset_pane
cortana_close
open_reminders
act_menu; dump_ui "$ROW_DIR/roi-menu.xml"; reset_menu
corroborate reminder_menu "$(roi_half "$ROW_DIR/roi-menu.xml" acrylic:reminder_menu)" open act_menu reset_menu
cortana_close
show_start 5
corroborate pivot "0,150,540,1000" pivot act_pivot reset_pivot
to_app_list 2
act_band; dump_ui "$ROW_DIR/roi-band.xml"; reset_band
corroborate applist_menu "$(roi_half "$ROW_DIR/roi-band.xml" applist_menu)" jump act_band reset_band
to_start 2
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
