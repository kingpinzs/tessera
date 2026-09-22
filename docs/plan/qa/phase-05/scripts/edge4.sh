#!/usr/bin/env bash
# EDGE4 — liveness across a reboot (N-01: "reboot, 24 h idle, ... service disabled" for the IME).
#
# After a real reboot the keyboard is still the enabled and selected input method, its process starts on
# the first field that asks for it, it types, and the checklist says both rows are on. The 24-hour idle
# and One UI's optimisers are phone questions; "service disabled" is E1's bracket and EDGE2's force-stop.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin EDGE4 "after a reboot the keyboard is still selected and types"
kb_begin
before_pid="$(adb shell pidof app.tileshell:ime | tr -d '\r')"
note "before the reboot: selected $(adb shell settings get secure default_input_method | tr -d '\r'), :ime pid ${before_pid:-none}"
adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
sleep 20
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard >/dev/null 2>&1; sleep 2
assert_eq "after the reboot: still the selected keyboard" "$IME_ID" "$(adb shell settings get secure default_input_method | tr -d '\r')"
assert_contains "after the reboot: still enabled" "$IME_ID" "$(adb shell ime list -s | tr -d '\r')"
open_field field_text
D="$ROW_DIR/edge4.xml"; kb_dump "$D"
assert_eq "the keyboard comes up on the first field after the reboot" "yes" "$(has_node "$D" kb_key_q)"
after_pid="$(adb shell pidof app.tileshell:ime | tr -d '\r')"
assert_ne "a fresh :ime process" "${before_pid:-none}" "${after_pid:-none}"
tap_word "$D" "up"; sleep 0.8
assert_eq "and it types" "[up]" "$(read_mirror text)"
adb shell am start -W -f 0x10008000 -n app.tileshell/.settings.SettingsActivity --es page CHECKLIST >/dev/null; sleep 2
C="$ROW_DIR/.edge4_cl.xml"; scroll_to_node "$C" "checklist:keyboard_selected:granted" 6 >/dev/null 2>&1
assert_eq "the checklist: keyboard enabled" "yes" "$(has_node "$C" checklist:keyboard_enabled:granted)"
assert_eq "the checklist: keyboard selected" "yes" "$(has_node "$C" checklist:keyboard_selected:granted)"
adb shell input keyevent KEYCODE_HOME
kb_end
row_end
