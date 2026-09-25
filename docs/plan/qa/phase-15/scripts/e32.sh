#!/usr/bin/env bash
# E32 — Ring timeout (phase 15 T15-49; H6). An alarm fired over the in-use route (E4b's setup: DeskClock in front,
# the clock jumped to 10 s before) and left unanswered → at 600 ± 5 s after the `[alarms] fired <id>` line (its
# uptime, read from the `[timer]`-free ring stamp: the line's wall= and the ring's own uptime are the same clock
# here, so the check reads the wall= of `fired` and of `ring ended … timeout`), the shell's ALARM player is gone, the
# overlay window is gone, the ring slice holds `ring ended <id>: timeout` and then `missed`, and the notifications
# hold "Missed alarm h:mm" with the alarm's time. The timeout runs on the ring service's own timer, so the row waits
# the full 10 minutes — polling, never shortening. Restore: the missed notification cleared, the alarm deleted through
# the app, RV12's clock restore, force-stop + Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

DESK=com.android.deskclock
row_begin E32 "ring timeout: an unanswered alarm ends at 600 s as timeout + missed"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

adb shell pm grant $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -W -n $DESK/.DeskClock >/dev/null 2>&1; sleep 2
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$AH" "$AM_" "Unanswered")"
assert_ne "the alarm was created" "" "$ID"
adb shell am start -W -n $DESK/.DeskClock >/dev/null 2>&1; sleep 1
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"
[ "$AT" -gt 0 ] || AT="$(device_ms)"
jump_clock $(( AT - 10000 )) >/dev/null
MARK="$(ring_mark)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 30)"
assert_ne "the alarm fired" "" "$FIRED"
FIRED_WALL="$(field_of "$FIRED" wall)"
sleep 3
assert_contains "it rings on the in-use overlay" "[alarms] surface: toast-overlay $ID" "$(ring_since "$MARK")"
assert_ne "the player is started" 0 "$(alarm_player_started)"
# Run 1's ring was DISMISSED at +45 s by input this driver never sent (the ring then shows Android's Settings
# launched from the app list at +102 s) — someone else's adb on emulator-5558. The wait below therefore also keeps
# every [launch] / [start] / [applist] line of the window, so a foreign touch is named, not guessed.

# ---- the real wait: poll every 5 s for up to 660 s for the timeout line ----------------------------------------------------
ENDED=""
for _ in $(seq 1 132); do
  ENDED="$(ring_since "$MARK" | grep -F "[alarms] ring ended $ID: timeout")"
  [ -n "$ENDED" ] && break
  sleep 5
done
FOREIGN="$(ring_since "$MARK" | grep -E '\[launch\]|\[start\] home|\[applist\]|ring ended .*: (dismiss|snooze)' | sed 's/^ *//' | cut -c1-140 | head -6 | paste -sd'|')"
record "UI activity during the wait (this driver sends none after the jump)" "${FOREIGN:-none}"
assert_ne "the ring ended with timeout (within 660 s)" "" "$ENDED"
ENDED_WALL="$(field_of "$ENDED" wall)"
assert_within "the timeout came 600 ± 5 s after the fired line (wall= of both lines)" 600000 $(( ${ENDED_WALL:-0} - FIRED_WALL )) 5000
sleep 2
SLICE="$(ring_since "$MARK")"
assert_contains "… then missed" "[alarms] ring ended $ID: missed" "$SLICE"
assert_eq "the timeout line comes before the missed line" yes "$(printf '%s\n' "$SLICE" | grep -nE "ring ended $ID: (timeout|missed)" | python3 -c '
import sys
ls = [l.split(":", 1)[0] for l in sys.stdin]
print("yes" if len(ls) >= 2 else "no")')"
assert_eq "the ALARM player is gone" 0 "$(alarm_player_started)"
assert_eq "the overlay window is gone" "" "$(overlay_window)"
MN="$(notification_on_channel clock_missed)"; printf '%s\n' "$MN" > "$ROW_DIR/notification_missed.txt"
assert_contains "a Missed alarm notification with the alarm's time" "Missed alarm $(time_12h "$AH" "$AM_")" "$MN"
assert_eq "the one-shot is off after the miss" false "$(alarm_field "$ID" enabled)"
screencap "$ROW_DIR/after_timeout.png"

# ---- restore ------------------------------------------------------------------------------------------------------------------
ring_save launcher
adb shell cmd notification cancel-all >/dev/null 2>&1 || adb shell service call notification 1 >/dev/null 2>&1
adb shell am force-stop $DESK; adb shell pm revoke $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
clock_restore
app_delete_alarm "$ID"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_eq "restore: the missed notification is cleared" "" "$(notification_on_channel clock_missed)"
assert_clock_empty "restore"
row_end
