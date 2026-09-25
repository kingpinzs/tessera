#!/usr/bin/env bash
# E5 — Doze and offline (phase 15). An alarm 2 min ahead (AlarmClock API); airplane mode on; phase 03 E2's Doze
# procedure (deviceidle enable deep, battery unplug, KEYCODE_SLEEP, deviceidle force-idle); the clock jumped to 5 s
# before → it rings: `[alarms] fired <id>` with late ≤ 1000 ms, a started ALARM player of the shell, and the
# display on. No PIN is set (T15-57), so E4's keyguard clauses do not apply. Dismiss (T15-45). Restore per RV12:
# deviceidle unforce + disable deep, battery reset, airplane mode off, the clock; the alarm deleted through the
# app; force-stop + Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E5 "doze and offline: the alarm still rings on time"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

# Doze never engages while a wake-from-idle alarm (setAlarmClock is one) is due inside the controller's
# min_time_to_alarm (30 min on this image): stepIdleStateLocked backs out to ACTIVE, and force-idle reports "Unable
# to go deep idle; stopped at INACTIVE" (runs 1–2 with the doc's "2 min ahead"). So the alarm is set past that
# window and the clock is jumped to 5 s before it, as the doc's jump already does; the difference is reported.
MTA="$(adb shell dumpsys deviceidle | tr -d '\r' | grep -oE 'min_time_to_alarm=\+?[0-9hms]+' | head -1)"
MTA_MS="$(python3 -c '
import re, sys
s = sys.argv[1].split("=")[-1]; ms = 0
for v, u in re.findall(r"(\d+)([hms])", s): ms += int(v) * {"h": 3600000, "m": 60000, "s": 1000}[u]
print(ms or 1800000)' "$MTA")"
record "deviceidle min_time_to_alarm (the alarm is set 5 min past it, not the doc's 2 min ahead)" "$MTA"
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + MTA_MS + 300000 )))"
ID="$(api_alarm "$AH" "$AM_" "Doze")"
assert_ne "the alarm was created" "" "$ID"
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"
assert_ne "… and armed" 0 "$AT"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- offline, then deep Doze --------------------------------------------------------------------------------------
adb shell cmd connectivity airplane-mode enable; sleep 2
assert_eq "airplane mode is on" 1 "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
adb shell dumpsys deviceidle enable deep >/dev/null
adb shell dumpsys battery unplug
adb shell input keyevent KEYCODE_SLEEP; sleep 3
# force-idle refuses ("Unable to go deep idle; stopped at INACTIVE", run 1) until the controller has seen the screen
# go off and the battery unplug; it is retried until the state reads IDLE (up to 6 tries, 2 s apart).
FORCE=""
for _ in 1 2 3 4 5 6; do
  FORCE="$(adb shell dumpsys deviceidle force-idle | tr -d '\r')"
  [ "$(adb shell dumpsys deviceidle get deep | tr -d '\r')" = IDLE ] && break
  sleep 2
done
note "force-idle: $FORCE; $(adb shell dumpsys deviceidle | tr -d '\r' | grep -oE 'mScreenOn=[a-z]+|mCharging=[a-z]+' | paste -sd' ')"
assert_contains "the device is forced into deep idle" "deep idle" "$FORCE"
assert_eq "deviceidle get deep reads IDLE" "IDLE" "$(adb shell dumpsys deviceidle get deep | tr -d '\r')"
assert_ne "the screen is off" "Awake" "$(wakefulness)"

# ---- jump to 5 s before → rings -------------------------------------------------------------------------------------
[ "$AT" -gt 0 ] || AT="$(device_ms)"
jump_clock $(( AT - 5000 )) >/dev/null
MARK="$(ring_mark)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 30)"
assert_ne "the alarm fired within 30 s of the jump" "" "$FIRED"
assert_within "late <= 1000 ms" 0 "$(field_of "$FIRED" late)" 1000
sleep 3
assert_ne "an ALARM player of the shell is started" 0 "$(alarm_player_started)"
assert_eq "the display is on (Awake)" "Awake" "$(wakefulness)"
record "deviceidle state at the ring" "$(adb shell dumpsys deviceidle get deep | tr -d '\r')"
record "surface line" "$(ring_since "$MARK" | grep -o '\[alarms\] surface: [a-z-]* [a-z0-9]*' | head -1)"
gdump "$ROW_DIR/ring.xml"; screencap "$ROW_DIR/ring.png"
assert_eq "the toast is up (ring_dismiss)" yes "$(has_node "$ROW_DIR/ring.xml" ring_dismiss)"
MARK="$(ring_mark)"
gtap "$ROW_DIR/ring.xml" ring_dismiss; sleep 3
assert_contains "dismiss: ring ended $ID: dismiss" "[alarms] ring ended $ID: dismiss" "$(ring_since "$MARK")"
assert_eq "dismiss: the player stopped" 0 "$(alarm_player_started)"

# ---- restore ----------------------------------------------------------------------------------------------------------
ring_save launcher
adb shell dumpsys deviceidle unforce >/dev/null
adb shell dumpsys deviceidle disable deep >/dev/null
adb shell dumpsys battery reset
adb shell cmd connectivity airplane-mode disable; sleep 2
assert_eq "restore: airplane mode off" 0 "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
clock_restore
assert_eq "restore: wake_device printed Awake (C-25)" "Awake" "$(wake_device)"
app_delete_alarm "$ID"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_clock_empty "restore"
row_end
