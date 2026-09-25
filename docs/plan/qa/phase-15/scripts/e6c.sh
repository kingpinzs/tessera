#!/usr/bin/env bash
# E6c — Alarm after a reboot before the first unlock (phase 15 T15-22, direct boot). A PIN set; an alarm 5 min ahead
# (AlarmClock API); `adb reboot`, the boot poll, and NO unlock: the keyguard shows and user 0 is still locked
# (`dumpsys user` RUNNING_LOCKED; `getprop sys.user.0.ce_available` recorded) → dumpsys alarm's Next alarm clock
# holds the alarm for user 0 (re-armed at LOCKED_BOOT_COMPLETED from device-protected storage); the clock jumped to
# 10 s before it → an ALARM player of the shell starts and the ring activity is on top with the keyguard showing;
# Dismiss; then the PIN unlocks the phone and the launcher ring holds `rearm (locked boot): 1 alarms, 0 timers` and
# `fired <id>` from the locked boot, and Start comes up (start_page). Restore: RV12's clock restore, the alarm deleted
# through the app, force-stop + Home, the PIN cleared, wake_device.
#
# The ring cannot be read before the unlock (the listener service is not bound while the user is locked), so the
# locked half reads the device: dumpsys audio / window / alarm. The gesture driver's fixture is not direct-boot aware
# either, so the locked toast is dumped with plain `uiautomator dump` (it is the focused window).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E6c "alarm rings after a reboot before the first unlock (direct boot)"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

set_pin
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 300000 )))"
ID="$(api_alarm "$AH" "$AM_" "Boot")"
assert_ne "the alarm was created" "" "$ID"
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"
assert_ne "… and armed" 0 "$AT"
ring_save launcher; cp "$ROW_DIR/ring-launcher.txt" "$ROW_DIR/ring-launcher-prekill.txt"

# ---- reboot, no unlock ----------------------------------------------------------------------------------------------
BOOT="$(reboot_and_wait)"; note "booted at $BOOT"
sleep 5
assert_eq "after the boot the keyguard is showing" "true" "$(keyguard_showing)"
USER0="$(adb shell dumpsys user | tr -d '\r' | sed -n '/UserInfo{0:/,/UserInfo{[1-9]/p' | grep -m1 -E 'State:|RUNNING_LOCKED|RUNNING_UNLOCKED')"
note "dumpsys user, user 0: $USER0"
assert_contains "user 0 is still locked (RUNNING_LOCKED)" "RUNNING_LOCKED" "$USER0"
record "getprop sys.user.0.ce_available before the unlock" "[$(adb shell getprop sys.user.0.ce_available | tr -d '\r')]"
assert_eq "dumpsys alarm's Next alarm clock holds the alarm for user 0 (re-armed at the locked boot)" "$AT" "$(next_alarm_clock_ms)"
assert_eq "… as an RTC_WAKEUP entry of the shell" "$AT" "$(alarm_trigger_ms | paste -sd,)"

# ---- jump to 10 s before → rings over the keyguard --------------------------------------------------------------------
[ "$AT" -gt 0 ] || AT="$(device_ms)"
jump_clock $(( AT - 10000 )) >/dev/null
STARTED=0
for _ in $(seq 1 60); do STARTED="$(alarm_player_started)"; [ "$STARTED" != 0 ] && break; sleep 0.5; done
assert_ne "an ALARM player of the shell started within 30 s (still locked)" 0 "$STARTED"
sleep 2
assert_contains "the ring activity is on top" "clock.RingActivity" "$(current_focus)"
assert_eq "… with the keyguard showing" "true" "$(keyguard_showing)"
record "keyguard occluded by the ring activity" "$(keyguard_occluded)"
# Before the first unlock /sdcard (emulated storage) is not mounted, so lib.sh's dump_ui — `uiautomator dump
# /sdcard/qa.xml` — fails (run 1: "(dump failed)"); the dump goes to /data/local/tmp here.
dump_locked() { # out.xml
  adb shell uiautomator dump /data/local/tmp/qa_locked.xml >/dev/null 2>&1
  adb shell cat /data/local/tmp/qa_locked.xml > "$1" 2>/dev/null
  grep -q '<node' "$1"
}
dump_locked "$ROW_DIR/ring_locked.xml" || note "the locked dump failed: $(head -c 120 "$ROW_DIR/ring_locked.xml")"
screencap "$ROW_DIR/ring_locked.png"
assert_eq "the locked toast is up (ring_dismiss in the dump)" yes "$(has_node "$ROW_DIR/ring_locked.xml" ring_dismiss)"
tap_node "$ROW_DIR/ring_locked.xml" ring_dismiss; sleep 3
assert_eq "dismiss: the player stopped" 0 "$(alarm_player_started)"
assert_absent "dismiss: the ring activity left" "clock.RingActivity" "$(current_focus)"

# ---- unlock with the PIN, read the ring, Start comes up ----------------------------------------------------------------
unlock_with_pin
assert_eq "the PIN unlocked the phone" "false" "$(keyguard_showing)"
sleep 4
RING="$(diag)"
printf '%s\n' "$RING" > "$ROW_DIR/ring-launcher-after-unlock.txt"
assert_contains "the ring holds rearm (locked boot): 1 alarms, 0 timers" "[alarms] rearm (locked boot): 1 alarms, 0 timers" "$RING"
assert_contains "… and fired $ID from the locked boot" "[alarms] fired $ID kind=alarm" "$RING"
assert_contains "… and ring ended $ID: dismiss" "[alarms] ring ended $ID: dismiss" "$RING"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/start_after_unlock.xml"; screencap "$ROW_DIR/start_after_unlock.png"
assert_eq "Start comes up normally after the unlock (start_page)" yes "$(has_node "$ROW_DIR/start_after_unlock.xml" start_page)"

# ---- restore -------------------------------------------------------------------------------------------------------------
ring_save launcher
clock_restore
clear_pin
assert_eq "restore: wake_device printed Awake (C-25)" "Awake" "$(wake_device)"
app_delete_alarm "$ID"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_clock_empty "restore"
row_end
