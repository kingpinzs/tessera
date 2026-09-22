#!/usr/bin/env bash
# E8 — the Search key on the drawn Start bar, and the assist key.
#
#   "adb shell input tap on the Search key of the drawn Start bar opens Cortana on its home page, not
#    listening (dump shows the session UI); a press-and-hold on the Search key (input swipe x y x y
#    1000) opens Cortana already listening (dump shows the 'Listening...' query box); input keyevent
#    KEYCODE_ASSIST opens Cortana (PhoneWindowManager's assist path to the assistant role holder; the
#    injected long-press Home does nothing on the AOSP image, whose config_longPressOnHomeBehavior
#    is 0)" (review T-B1)
#
# The Search key lives in the slot phase 01 deliberately left EMPTY, so the row also proves the ADD.
. "$(dirname "$0")/lib.sh"

row_begin E8 "the Search key: tap, hold, and the assist key"

# ---- the key exists, in phase 01's empty slot, on Start's own bar -------------------------------
ensure_start
sleep 2
dump_ui "$ROW_DIR/e8_start.xml"
screencap "$ROW_DIR/e8_start.png"
assert_eq "Start is showing" "yes" "$(has_node "$ROW_DIR/e8_start.xml" w10m_nav_bar)"
assert_eq "the Search key is in the bar" "yes" "$(has_node "$ROW_DIR/e8_start.xml" nav_search)"
assert_eq "and phase 01's empty slot is gone" "no" "$(has_node "$ROW_DIR/e8_start.xml" nav_search_slot)"

# X17: three equal slots. The Search key is the right third.
px_per_epx="$(python3 -c "print(1080/360)")"
search="$(bounds "$ROW_DIR/e8_start.xml" nav_search)"
back="$(bounds "$ROW_DIR/e8_start.xml" nav_back)"
if [ -n "$search" ] && [ -n "$back" ]; then
  set -- $search; s_l=$1 s_r=$3
  set -- $back; b_l=$1 b_r=$3
  assert_within "X17: the Search slot is a third of the bar" 120 \
    "$(python3 -c "print(($s_r - $s_l) / $px_per_epx)")" 2.0
  assert_within "and it is the RIGHT third" 240 "$(python3 -c "print($s_l / $px_per_epx)")" 2.0
  assert_within "with Back in the left third" 0 "$(python3 -c "print($b_l / $px_per_epx)")" 2.0
else
  _verdict FAIL "the nav slots are measurable" "search=[$search] back=[$back]"
fi

# ---- a tap opens the home page, NOT listening (R6 4.2.1) ---------------------------------------
tap_node "$ROW_DIR/e8_start.xml" nav_search
sleep 5
dump_ui "$ROW_DIR/e8_tap.xml"
screencap "$ROW_DIR/e8_tap.png"
assert_eq "a tap opens Cortana" "yes" "$(has_node "$ROW_DIR/e8_tap.xml" cortana_session)"
assert_eq "on its home page" "yes" "$(has_node "$ROW_DIR/e8_tap.xml" cortana_greeting)"
assert_eq "NOT listening (R6 4.2.1)" "no" "$(has_node "$ROW_DIR/e8_tap.xml" cortana_listening_box)"
assert_contains "and the session recorded the mode it opened in" "mode=HOME" "$(diag cortana | tail -5)"
cortana_close
ensure_start

# ---- a press-and-hold opens Cortana already listening (R6 4.2.3, hold time H20) -----------------
b="$(bounds "$ROW_DIR/e8_start.xml" nav_search)"
if [ -n "$b" ]; then
  set -- $b
  x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
  # The hold is Android's own long-press timeout; a 1000 ms swipe with no travel is a long press.
  adb shell input swipe $x $y $x $y 1000
  sleep 6
  dump_ui "$ROW_DIR/e8_hold.xml"
  screencap "$ROW_DIR/e8_hold.png"
  assert_eq "a press-and-hold opens Cortana" "yes" "$(has_node "$ROW_DIR/e8_hold.xml" cortana_session)"
  assert_eq "already listening (R6 4.2.3)" "yes" "$(has_node "$ROW_DIR/e8_hold.xml" cortana_listening_box)"
  assert_eq "showing the Listening placeholder" "Listening..." \
    "$(node_text "$ROW_DIR/e8_hold.xml" cortana_listening_placeholder)"
  assert_contains "and the session recorded the listening mode" "mode=LISTENING" "$(diag cortana | tail -6)"
else
  _verdict FAIL "the Search key is tappable" "no bounds for nav_search"
fi
cortana_close
ensure_start

# ---- KEYCODE_ASSIST reaches the role holder ----------------------------------------------------
cortana_assist
sleep 5
dump_ui "$ROW_DIR/e8_assist.xml"
screencap "$ROW_DIR/e8_assist.png"
assert_eq "KEYCODE_ASSIST opens Cortana" "yes" "$(has_node "$ROW_DIR/e8_assist.xml" cortana_session)"
cortana_close
ensure_start

# T-B1: the injected long-press Home does nothing on this AOSP image, whose config_longPressOnHomeBehavior
# is 0. The row records that rather than treating it as a failure; P3 covers the real gesture on the phone.
before="$(diag cortana | wc -l)"
adb shell input keyevent --longpress KEYCODE_HOME
sleep 4
dump_ui "$ROW_DIR/e8_longhome.xml"
opened="$(has_node "$ROW_DIR/e8_longhome.xml" cortana_session)"
note "long-press Home opened a session: $opened (T-B1 expects no on the AOSP image; P3 covers the phone)"
assert_eq "long-press Home behaves as T-B1 recorded for this image" "no" "$opened"

row_end
