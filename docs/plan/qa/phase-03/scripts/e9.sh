#!/usr/bin/env bash
# E9 — "Lock screen options", and the locked session's look.
#
#   "Cortana's Settings page shows 'Lock screen options' with 'Open Cortana when I press and hold the
#    side key – even when my device is locked' and the toggle On on a fresh install (dump text); with a
#    PIN set, then KEYCODE_SLEEP, KEYCODE_WAKEUP and dumpsys window showing the keyguard,
#    KEYCODE_ASSIST opens Cortana above the keyguard while the toggle is On and nothing while it is
#    Off; if SystemUI declines assist over the keyguard on the AOSP image, this row uses
#    `cmd voiceinteraction show` instead; the locked session opens listening and its dump has the
#    greeting 'What's on your mind?' and no menu node (R6 §3.5.5); afterwards locksettings clear"
#    (review T-B1 / R2D-11)
#
# The row restores the PIN state it found (PLAN RV12), including on an early exit.
. "$(dirname "$0")/lib.sh"

row_begin E9 "lock screen options, and the locked session"

PIN=1234
pin_set=no
restore() {
  if [ "$pin_set" = yes ]; then
    adb shell locksettings clear --old $PIN >/dev/null 2>&1
    note "restored: the PIN was cleared"
  fi
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
}
trap restore EXIT

# ---- the setting, with its W10M wording (R6 3.5.1, H8) -----------------------------------------
ensure_start
cortana_assist
sleep 5
dump_ui "$ROW_DIR/e9_home.xml"
# Cortana's Settings is a destination of the ≡ pane.
tap_node "$ROW_DIR/e9_home.xml" cortana_menu_button
sleep 2
dump_ui "$ROW_DIR/e9_pane.xml"
screencap "$ROW_DIR/e9_pane.png"
if [ "$(has_node "$ROW_DIR/e9_pane.xml" cortana_pane)" = yes ]; then
  _verdict PASS "the menu pane opens" "cortana_pane present"
  tap_node "$ROW_DIR/e9_pane.xml" cortana_pane_item_settings
  sleep 3
else
  _verdict FAIL "the menu pane opens" "no cortana_pane after tapping the menu button"
fi
dump_ui "$ROW_DIR/e9_settings.xml"
screencap "$ROW_DIR/e9_settings.png"

assert_eq "Cortana's Settings page is on screen (H27, H28)" "yes" \
  "$(has_node "$ROW_DIR/e9_settings.xml" cortana_settings)"
assert_eq "it has a Lock screen options section" "yes" \
  "$(has_node "$ROW_DIR/e9_settings.xml" "cortana_settings_section:lock_screen_options")"
wording="$(node_text "$ROW_DIR/e9_settings.xml" "cortana_settings_lock_screen:label")"
note "toggle wording: $wording"
assert_contains "R6 3.5.1's W10M wording, side key adapted (H8)" \
  "press and hold the side key" "$wording"
assert_contains "and its 'even when my device is locked' half" "even when my device is locked" "$wording"
assert_eq "On by default on a fresh install (R6 3.5.3 candidate, H8)" "On" \
  "$(node_text "$ROW_DIR/e9_settings.xml" "cortana_settings_lock_screen:state")"

cortana_close
ensure_start

# ---- the locked session, with the toggle On ------------------------------------------------------
adb shell locksettings set-pin $PIN >/dev/null 2>&1 && pin_set=yes
assert_eq "a PIN is set" "yes" "$pin_set"
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
sleep 3
adb shell dumpsys window > "$ROW_DIR/e9_keyguard.txt" 2>/dev/null
assert_contains "the keyguard is showing" "isKeyguardShowing=true" "$(cat "$ROW_DIR/e9_keyguard.txt")"

cortana_assist
sleep 5
dump_ui "$ROW_DIR/e9_locked.xml"
screencap "$ROW_DIR/e9_locked.png"
opened="$(has_node "$ROW_DIR/e9_locked.xml" cortana_session)"
if [ "$opened" != yes ]; then
  # T-B1's fallback: if SystemUI declines assist over the keyguard on this image, show it directly.
  note "KEYCODE_ASSIST did not open over the keyguard; using cmd voiceinteraction show (T-B1)"
  adb shell cmd voiceinteraction show >/dev/null 2>&1
  sleep 5
  dump_ui "$ROW_DIR/e9_locked.xml"
  screencap "$ROW_DIR/e9_locked.png"
  opened="$(has_node "$ROW_DIR/e9_locked.xml" cortana_session)"
fi
assert_eq "Cortana opens above the keyguard with the toggle On" "yes" "$opened"
assert_eq "R6 3.5.5: it opens LISTENING" "yes" "$(has_node "$ROW_DIR/e9_locked.xml" cortana_listening_box)"
assert_eq "R6 3.5.5: the non-personalised greeting" "What's on your mind?" \
  "$(node_text "$ROW_DIR/e9_locked.xml" cortana_greeting)"
assert_eq "R6 3.5.5: no menu node at all (H11)" "no" \
  "$(has_node "$ROW_DIR/e9_locked.xml" cortana_menu_button)"
assert_contains "and the session recorded that it opened locked" "locked=true" "$(diag cortana | tail -6)"
adb shell dumpsys window > "$ROW_DIR/e9_locked_window.txt" 2>/dev/null
assert_contains "the keyguard is still up behind it" "isKeyguardShowing=true" "$(cat "$ROW_DIR/e9_locked_window.txt")"

cortana_close

# ---- and nothing opens with the toggle Off -------------------------------------------------------
# The toggle is flipped through the shell's own preference store rather than the page, because the page
# is behind the keyguard at this point; the row is about what the SETTING does, not how it is reached.
adb shell input keyevent KEYCODE_MENU >/dev/null 2>&1
adb shell run-as app.tileshell sh -c \
  'f=/data/data/app.tileshell/shared_prefs/cortana.xml; [ -f "$f" ] && sed -i "s/name=\"lock_screen\" value=\"true\"/name=\"lock_screen\" value=\"false\"/" "$f"' >/dev/null 2>&1
adb shell am force-stop app.tileshell
sleep 3
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
sleep 3
cortana_assist
sleep 5
dump_ui "$ROW_DIR/e9_locked_off.xml"
screencap "$ROW_DIR/e9_locked_off.png"
assert_eq "with the toggle Off, nothing opens over the keyguard" "no" \
  "$(has_node "$ROW_DIR/e9_locked_off.xml" cortana_session)"

# Put the toggle back the way a fresh install has it.
adb shell run-as app.tileshell sh -c \
  'f=/data/data/app.tileshell/shared_prefs/cortana.xml; [ -f "$f" ] && sed -i "s/name=\"lock_screen\" value=\"false\"/name=\"lock_screen\" value=\"true\"/" "$f"' >/dev/null 2>&1
adb shell am force-stop app.tileshell

row_end
