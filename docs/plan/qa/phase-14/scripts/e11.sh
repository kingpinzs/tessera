#!/usr/bin/env bash
# Phase 14 E11 — screen-off, lock and process death with the pod bay open: screen off and on keeps the pod bay (H9), so
# does a PIN-locked sleep / wake / unlock; a force-stop then Home is Start (the page is not persisted). The lock-screen
# half wakes with KEYCODE_WAKEUP alone (C-25's exception: wake_device dismisses the keyguard).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E11 "screen-off, PIN lock and process death with the pod bay open"

PIN=1234
pin_set=no
restore() {
  [ "$pin_set" = yes ] && adb shell locksettings clear --old $PIN >/dev/null 2>&1
  adb shell locksettings set-disabled true >/dev/null 2>&1
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  adb shell wm dismiss-keyguard >/dev/null 2>&1
  echo "      restored: PIN cleared, lock screen disabled, awake ($(adb shell locksettings get-disabled | tr -d '\r'))" >> "$LOG"
}
trap restore EXIT
awake() { adb shell dumpsys power | grep -m1 'mWakefulness=' | tr -d '\r ' ; }
keyguard() { adb shell dumpsys window | grep -m1 -oE 'isKeyguardShowing=(true|false)'; }

# (1) Screen off and on, no lock.
open_pod_bay "$ROW_DIR/01-podbay.xml"
assert_eq "the pod bay is open" "yes" "$(has_node "$ROW_DIR/01-podbay.xml" pod_bay)"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_SLEEP
sleep 2
assert_eq "asleep" "mWakefulness=Asleep" "$(awake)"
adb shell input keyevent KEYCODE_WAKEUP
sleep 2
adb shell wm dismiss-keyguard
sleep 2
assert_eq "C-25: awake before the next read" "mWakefulness=Awake" "$(awake)"
dump_ui "$ROW_DIR/02-after-wake.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/02-slice.txt"
assert_eq "screen off and on: still the pod bay" "yes" "$(has_node "$ROW_DIR/02-after-wake.xml" pod_bay)"
assert_eq "screen off and on: no start_page" "no" "$(has_node "$ROW_DIR/02-after-wake.xml" start_page)"
absent_in "screen off and on: the pod bay did not close" "[podbay] closed" "$s"

# (2) With a PIN: sleep, wake (keyguard kept), unlock.
adb shell locksettings set-disabled false >/dev/null 2>&1
adb shell locksettings set-pin $PIN >/dev/null 2>&1 && pin_set=yes
assert_eq "a PIN is set" "yes" "$pin_set"
assert_eq "the lock screen is enabled" "false" "$(adb shell locksettings get-disabled | tr -d '\r')"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
sleep 3
assert_eq "woken (KEYCODE_WAKEUP alone, keyguard kept)" "mWakefulness=Awake" "$(awake)"
assert_eq "the keyguard is showing" "isKeyguardShowing=true" "$(keyguard)"
screencap "$ROW_DIR/03-keyguard.png"
adb shell wm dismiss-keyguard   # to the bouncer
sleep 2
adb shell input text $PIN
adb shell input keyevent KEYCODE_ENTER
sleep 4
assert_eq "unlocked" "isKeyguardShowing=false" "$(keyguard)"
dump_ui "$ROW_DIR/04-after-unlock.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/04-slice.txt"
assert_eq "after the PIN unlock: still the pod bay" "yes" "$(has_node "$ROW_DIR/04-after-unlock.xml" pod_bay)"
absent_in "after the PIN unlock: the pod bay did not close" "[podbay] closed" "$s"
screencap "$ROW_DIR/04-after-unlock.png"
restore
pin_set=no

# (3) Process death: force-stop, Home -> Start (the page is not persisted).
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 1
ring_save
# The MARK is before the force-stop: Android restarts the home app the instant it is stopped (layout.sh's note), so the
# new process's first lines land before any MARK taken after it.
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 5
dump_ui "$ROW_DIR/05-after-death.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/05-slice.txt"
assert_eq "after force-stop and Home: Start alone" "yes" "$(start_alone "$ROW_DIR/05-after-death.xml")"
assert_eq "the new process's first page line is START" "START" "$(pages_in "$s" | cut -d' ' -f1)"

row_end
