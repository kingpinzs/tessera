#!/usr/bin/env bash
# E23 — Bars, the ring-surface clauses only (phase 15 Q-E A, T15-14, T15-31, T15-32; the three apps' page clauses
# are another driver's). Over the keyguard (E4's setup: PIN, asleep, the clock jumped) the toast covers the top
# 248 epx INCLUDING the status bar (r11/clock.md 8.1): `ring_surface` from y 0, 248 epx ± 3 % tall, and the status
# bar's own strip reads the toast's fill (57,57,57); a tap at three points below it (y = 400, 500, 600 epx) leaves
# the alarm ringing — the ALARM player still started and `ring_surface` still in the dump. In use (E4b's setup, over
# DeskClock) the overlay toast's top edge sits at the system status bar's bottom (the recorded seam, T15-32) and
# the same three taps leave it ringing. Restore as E4 / E4b.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

DESK=com.android.deskclock
row_begin E23 "the ring surface's bars: locked (covers the status bar) and in use (below it); taps below leave it ringing"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

three_taps() { # label dump-prefix
  local y
  for y in 400 500 600; do
    adb shell input tap 540 $(( y * PX )); sleep 1.2
    gdump "$ROW_DIR/$2_tap$y.xml"
    assert_ne "$1: after a tap at y = $y epx the ALARM player is still started" 0 "$(alarm_player_started)"
    assert_eq "$1: … and ring_surface is still up" yes "$(has_node "$ROW_DIR/$2_tap$y.xml" ring_surface)"
  done
}

# ---- over the keyguard ---------------------------------------------------------------------------------------------------
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$AH" "$AM_" "Bars")"
assert_ne "the alarm was created" "" "$ID"
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"; [ "$AT" -gt 0 ] || AT="$(device_ms)"
adb shell input keyevent KEYCODE_HOME; sleep 1
set_pin
assert_eq "the keyguard shows after sleep + wake" "true" "$(lock_and_wake)"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
jump_clock $(( AT - 10000 )) >/dev/null
MARK="$(ring_mark)"
assert_ne "locked: the alarm fired" "" "$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 30)"
sleep 3
assert_contains "locked: surface: toast-locked $ID" "[alarms] surface: toast-locked $ID" "$(ring_since "$MARK")"
gdump "$ROW_DIR/locked.xml"; screencap "$ROW_DIR/locked.png"
B="$(bounds "$ROW_DIR/locked.xml" ring_surface)"; note "locked ring_surface: $B"
assert_eq "locked: the toast starts at y 0 (covers the status bar)" "0" "$(echo "$B" | cut -d' ' -f2)"
assert_within "locked: the toast is 248 epx tall ± 3 %" $(( 248 * PX )) "$(echo "$B" | cut -d' ' -f4)" $(( 248 * PX * 3 / 100 ))
SB="$(python3 "$HERE/pixcmp.py" mean "$ROW_DIR/locked.png" 700 4 1000 24)"
assert_eq "locked: the status-bar strip (y 4–24, x 700–1000) reads the toast fill (57,57,57) ± 4" yes "$(python3 -c '
import sys; a=[int(x) for x in sys.argv[1].split(",")]; print("yes" if all(abs(v-57)<=4 for v in a) else "no (%s)" % sys.argv[1])' "$SB")"
record "locked: the gesture driver's window list" "$(gwindows "$ROW_DIR/locked.xml")"
three_taps "locked" locked
gdump "$ROW_DIR/locked_end.xml"
MARK="$(ring_mark)"
gtap "$ROW_DIR/locked_end.xml" ring_dismiss; sleep 3
assert_contains "locked: Dismiss ends it" "[alarms] ring ended $ID: dismiss" "$(ring_since "$MARK")"
ring_save launcher
clock_restore
clear_pin
assert_eq "wake_device printed Awake (C-25)" "Awake" "$(wake_device)"

# ---- in use, over DeskClock ------------------------------------------------------------------------------------------------
adb shell pm grant $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -W -n $DESK/.DeskClock >/dev/null 2>&1; sleep 2
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 120000 )))"
ID2="$(api_alarm "$AH" "$AM_" "Bars2")"
assert_ne "the in-use alarm was created" "" "$ID2"
adb shell am start -W -n $DESK/.DeskClock >/dev/null 2>&1; sleep 1
AT2="$(alarm_trigger_ms | head -1)"; AT2="${AT2:-0}"; [ "$AT2" -gt 0 ] || AT2="$(device_ms)"
jump_clock $(( AT2 - 10000 )) >/dev/null
MARK="$(ring_mark)"
assert_ne "in use: the alarm fired" "" "$(wait_ring "$MARK" "[alarms] fired $ID2 kind=alarm" 30)"
sleep 3
assert_contains "in use: surface: toast-overlay $ID2" "[alarms] surface: toast-overlay $ID2" "$(ring_since "$MARK")"
gdump "$ROW_DIR/overlay.xml"; screencap "$ROW_DIR/overlay.png"
B2="$(bounds "$ROW_DIR/overlay.xml" ring_surface)"; note "overlay ring_surface: $B2"
SBH="$(adb shell dumpsys window windows | tr -d '\r' | grep -A3 'Window #.*StatusBar}' | grep -oE '\(fillx[0-9]+\)' | head -1 | grep -oE '[0-9]+')"
note "the system status bar window is $SBH px tall"
assert_eq "in use: the toast's top edge sits at the system status bar's bottom (the recorded seam)" "$SBH" "$(echo "$B2" | cut -d' ' -f2)"
record "the recorded seam (px): the overlay toast's top edge" "$(echo "$B2" | cut -d' ' -f2)"
assert_within "in use: the toast is 248 epx tall ± 3 %" $(( 248 * PX )) "$(( $(echo "$B2" | cut -d' ' -f4) - $(echo "$B2" | cut -d' ' -f2) ))" $(( 248 * PX * 3 / 100 ))
three_taps "in use" overlay
gdump "$ROW_DIR/overlay_end.xml"
MARK="$(ring_mark)"
gtap "$ROW_DIR/overlay_end.xml" ring_dismiss; sleep 3
assert_contains "in use: Dismiss ends it" "[alarms] ring ended $ID2: dismiss" "$(ring_since "$MARK")"

# ---- restore ---------------------------------------------------------------------------------------------------------------
ring_save launcher
clock_restore
app_delete_alarm "$ID"; app_delete_alarm "$ID2"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am force-stop $DESK; adb shell pm revoke $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 2
assert_clock_empty "restore"
row_end
