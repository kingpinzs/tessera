#!/usr/bin/env bash
# E4b — Alarm fires while the phone is in use (phase 15 T15-14, T15-23, T15-32). Unlocked and awake with DeskClock
# in front; an alarm 2 min ahead (AlarmClock API), the clock jumped to 10 s before it → within 30 s an ALARM
# player of the shell is started while DeskClock is still the resumed activity; the shell has a window of type
# APPLICATION_OVERLAY; the ring slice holds `notification <id>: quiet`, `surface: toast-overlay <id>` and
# `[motion] ring_toast … settle=217 ± 17 maxGapMs ≤ 33.4`; the quiet-channel notification carries no full-screen
# intent; the gesture-driver dump holds the toast with Snooze for / Snooze / Dismiss and no SystemUI node with
# the alarm's name; a tap at (W/2, 1500) leaves the toast, the player and DeskClock unchanged; Snooze → the overlay
# is gone, DeskClock resumed, the alarm 10 min ahead; jump again → the toast; KEYCODE_SLEEP mid-ring → the overlay
# is gone, `notification <id>: fullscreen`, `surface: toast-locked <id>`, the ring activity in front, the player
# still started; wake_device; Dismiss → gone, `ring ended <id>: dismiss`. Then SYSTEM_ALERT_WINDOW denied: no
# overlay, `notification: fullscreen`, `surface: heads-up`, the notification's Snooze / Dismiss, a tap on the
# heads-up's Dismiss stops it. Timer kind: a 30-s timer waited for (elapsed clock), its toast has ring_dismiss and
# no ring_snooze. Restore: RV12's clock restore, the alarm and timer deleted through the app, force-stop + Home,
# DeskClock force-stopped and its grant revoked, Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

DESK=com.android.deskclock
row_begin E4b "alarm fires while in use: overlay toast, touches consumed, snooze, sleep mid-ring, heads-up fallback, timer toast"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

desk_front() { adb shell am start -W -n $DESK/.DeskClock >/dev/null 2>&1; sleep 2; }

# ---- setup: DeskClock in front ------------------------------------------------------------------------------------
adb shell pm grant $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
desk_front
assert_contains "DeskClock is the resumed activity" "$DESK" "$(resumed)"

# ---- the alarm 2 min ahead; jump to 10 s before ---------------------------------------------------------------------
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$AH" "$AM_" "Desk")"
assert_ne "the alarm was created" "" "$ID"
desk_front
AT="$(alarm_trigger_ms | paste -sd,)"
assert_ne "… and armed" "" "$AT"
jump_clock $(( AT - 10000 )) >/dev/null
MARK="$(ring_mark)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 30)"
assert_ne "the alarm fired within 30 s" "" "$FIRED"
sleep 3
assert_ne "an ALARM player of the shell is started" 0 "$(alarm_player_started)"
assert_contains "DeskClock is still the resumed activity" "$DESK" "$(resumed)"
OVL="$(overlay_window)"; note "overlay window: $OVL"
assert_contains "the shell has a window of type APPLICATION_OVERLAY (TesseraRing)" "type=APPLICATION_OVERLAY" "$OVL"
SLICE="$(ring_since "$MARK")"
assert_contains "notification $ID: quiet" "[alarms] notification $ID: quiet" "$SLICE"
assert_contains "surface: toast-overlay $ID" "[alarms] surface: toast-overlay $ID" "$SLICE"
MOTION="$(printf '%s\n' "$SLICE" | grep -F '[motion] ring_toast' | tail -1)"; note "motion: ${MOTION#*] }"
assert_within "[motion] ring_toast settle = 217 ± 17 ms" 217 "$(field_of "$MOTION" settle)" 17
assert_eq "[motion] ring_toast maxGapMs <= 33.4" yes "$(python3 -c 'import sys; v=sys.argv[1]; print("yes" if v and float(v) <= 33.4 else "no (%s)" % v)' "$(field_of "$MOTION" maxGapMs)")"
adb shell dumpsys notification --noredact | tr -d '\r' > "$ROW_DIR/notification_overlay.txt"
QN="$(notification_on_channel clock_ringing_quiet)"; printf '%s\n' "$QN" > "$ROW_DIR/notification_quiet_block.txt"
assert_ne "the ring notification is on the quiet channel" "" "$QN"
assert_eq "… with NO full-screen intent" "" "$(printf '%s\n' "$QN" | grep -i 'fullScreenIntent=PendingIntent')"
assert_eq "… and none on the alarm channel" "" "$(notification_on_channel clock_ringing)"
gdump "$ROW_DIR/overlay.xml"; screencap "$ROW_DIR/overlay.png"
assert_eq "the overlay dump holds ring_surface" yes "$(has_node "$ROW_DIR/overlay.xml" ring_surface)"
assert_eq "… ring_snooze_for" yes "$(has_node "$ROW_DIR/overlay.xml" ring_snooze_for)"
assert_eq "… ring_snooze" yes "$(has_node "$ROW_DIR/overlay.xml" ring_snooze)"
assert_eq "… ring_dismiss" yes "$(has_node "$ROW_DIR/overlay.xml" ring_dismiss)"
assert_eq "no SystemUI node carries the alarm's name (no heads-up over the toast)" "" "$(pkg_nodes "$ROW_DIR/overlay.xml" com.android.systemui | grep -F '|Desk')"
B="$(bounds "$ROW_DIR/overlay.xml" ring_surface)"; note "overlay ring_surface bounds: $B"
record "the overlay toast's top edge (px) — the recorded seam against 8.1 (T15-32)" "$(echo "$B" | cut -d' ' -f2)"

# ---- a touch outside the toast is consumed ---------------------------------------------------------------------------
DESK_BEFORE="$(pkg_nodes "$ROW_DIR/overlay.xml" $DESK)"
adb shell input tap 540 1500; sleep 1.5
gdump "$ROW_DIR/after_tap.xml"
assert_eq "tap at (540,1500): the toast is still up" yes "$(has_node "$ROW_DIR/after_tap.xml" ring_surface)"
assert_ne "tap at (540,1500): the player is still started" 0 "$(alarm_player_started)"
assert_contains "tap at (540,1500): DeskClock still resumed" "$DESK" "$(resumed)"
assert_eq "tap at (540,1500): DeskClock's nodes unchanged" "$DESK_BEFORE" "$(pkg_nodes "$ROW_DIR/after_tap.xml" $DESK)"

# ---- Snooze → overlay gone, DeskClock resumed, 10 min ahead ----------------------------------------------------------
MARK="$(ring_mark)"; NOW="$(device_ms)"
gtap "$ROW_DIR/after_tap.xml" ring_snooze; sleep 3
assert_contains "snooze: ring ended $ID: snooze" "[alarms] ring ended $ID: snooze" "$(ring_since "$MARK")"
assert_eq "snooze: the overlay window is gone" "" "$(overlay_window)"
assert_contains "snooze: DeskClock still resumed" "$DESK" "$(resumed)"
SNZ="$(alarm_field "$ID" snoozedUntilMs)"
assert_within "snooze: dumpsys alarm shows the alarm 10 min ahead" $(( NOW + 600000 )) "$(alarm_trigger_ms | head -1)" 5000

# ---- jump again → the toast; KEYCODE_SLEEP mid-ring → the locked form ---------------------------------------------------
jump_clock $(( SNZ - 10000 )) >/dev/null
MARK="$(ring_mark)"
assert_ne "ring 2: fired" "" "$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 30)"
sleep 3
assert_contains "ring 2: surface: toast-overlay" "[alarms] surface: toast-overlay $ID" "$(ring_since "$MARK")"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_SLEEP; sleep 4
SLICE="$(ring_since "$MARK")"
assert_eq "sleep mid-ring: the overlay window is gone" "" "$(overlay_window)"
assert_contains "sleep mid-ring: notification $ID: fullscreen" "[alarms] notification $ID: fullscreen" "$SLICE"
assert_contains "sleep mid-ring: surface: toast-locked $ID" "[alarms] surface: toast-locked $ID" "$SLICE"
assert_contains "sleep mid-ring: the ring activity has the focus" "clock.RingActivity" "$(current_focus)"
record "sleep mid-ring: isKeyguardShowing (no PIN in this row; the keyguard is disabled by provision.sh)" "$(keyguard_showing)"
assert_ne "sleep mid-ring: the player is still started" 0 "$(alarm_player_started)"
assert_eq "wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
sleep 2
gdump "$ROW_DIR/after_wake.xml"; screencap "$ROW_DIR/after_wake.png"
MARK="$(ring_mark)"
gtap "$ROW_DIR/after_wake.xml" ring_dismiss; sleep 3
assert_contains "dismiss: ring ended $ID: dismiss" "[alarms] ring ended $ID: dismiss" "$(ring_since "$MARK")"
assert_eq "dismiss: no ring_surface left" no "$(gdump "$ROW_DIR/after_dismiss.xml" >/dev/null; has_node "$ROW_DIR/after_dismiss.xml" ring_surface)"
assert_eq "dismiss: the player stopped" 0 "$(alarm_player_started)"

# ---- overlay denied: the heads-up fallback ------------------------------------------------------------------------------
adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW deny
desk_front
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 120000 )))"
ID2="$(api_alarm "$AH" "$AM_" "Headsup")"
assert_ne "heads-up: the second alarm was created" "" "$ID2"
desk_front
AT2="$(alarm_trigger_ms | paste -sd,)"
jump_clock $(( AT2 - 10000 )) >/dev/null
MARK="$(ring_mark)"
assert_ne "heads-up: fired" "" "$(wait_ring "$MARK" "[alarms] fired $ID2 kind=alarm" 30)"
sleep 3
SLICE="$(ring_since "$MARK")"
assert_eq "heads-up: no overlay window of the shell" "" "$(overlay_window)"
assert_contains "heads-up: notification $ID2: fullscreen" "[alarms] notification $ID2: fullscreen" "$SLICE"
assert_contains "heads-up: surface: heads-up $ID2" "[alarms] surface: heads-up $ID2" "$SLICE"
HN="$(notification_on_channel clock_ringing)"; printf '%s\n' "$HN" > "$ROW_DIR/notification_headsup_block.txt"
assert_contains "heads-up: the alarm notification has a Snooze action" "Snooze" "$HN"
assert_contains "heads-up: … and a Dismiss action" "Dismiss" "$HN"
gdump "$ROW_DIR/headsup.xml"; screencap "$ROW_DIR/headsup.png"
assert_eq "heads-up: no shell toast (no ring_surface)" no "$(has_node "$ROW_DIR/headsup.xml" ring_surface)"
HB="$(bounds_by_text "$ROW_DIR/headsup.xml" com.android.systemui Dismiss)"; note "SystemUI Dismiss bounds: $HB"
assert_ne "heads-up: SystemUI shows a Dismiss action" "" "$HB"
MARK="$(ring_mark)"
tap_by_text "$ROW_DIR/headsup.xml" com.android.systemui Dismiss; sleep 3
assert_contains "heads-up Dismiss: ring ended $ID2: dismiss" "[alarms] ring ended $ID2: dismiss" "$(ring_since "$MARK")"
assert_eq "heads-up Dismiss: the player stopped" 0 "$(alarm_player_started)"
adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW allow
assert_contains "overlay grant restored" "allow" "$(adb shell appops get app.tileshell SYSTEM_ALERT_WINDOW | tr -d '\r')"

# ---- timer kind: a 30-s timer on the elapsed clock ----------------------------------------------------------------------
desk_front
MARK="$(ring_mark)"
TID="$(api_timer 30 "Short")"
assert_ne "a 30-s timer was created (running)" "" "$TID"
desk_front
FIRED="$(wait_ring "$MARK" "[alarms] fired $TID kind=timer" 45)"
assert_ne "the timer fired (waited on the elapsed clock)" "" "$FIRED"
sleep 3
assert_within "timer: late <= 1000 ms" 0 "$(field_of "$FIRED" late)" 1000
assert_ne "timer: an ALARM player of the shell is started" 0 "$(alarm_player_started)"
assert_contains "timer: surface: toast-overlay $TID" "[alarms] surface: toast-overlay $TID" "$(ring_since "$MARK")"
assert_contains "timer: notification $TID: quiet" "[alarms] notification $TID: quiet" "$(ring_since "$MARK")"
gdump "$ROW_DIR/timer_toast.xml"; screencap "$ROW_DIR/timer_toast.png"
assert_eq "timer toast: ring_title reads Timer finished" "Timer finished" "$(node_text "$ROW_DIR/timer_toast.xml" ring_title)"
assert_eq "timer toast: ring_dismiss" yes "$(has_node "$ROW_DIR/timer_toast.xml" ring_dismiss)"
assert_eq "timer toast: NO ring_snooze" no "$(has_node "$ROW_DIR/timer_toast.xml" ring_snooze)"
assert_eq "timer toast: NO ring_snooze_for" no "$(has_node "$ROW_DIR/timer_toast.xml" ring_snooze_for)"
TB="$(bounds "$ROW_DIR/timer_toast.xml" ring_surface)"; note "timer ring_surface bounds: $TB"
MARK="$(ring_mark)"
gtap "$ROW_DIR/timer_toast.xml" ring_dismiss; sleep 3
assert_contains "timer dismiss: ring ended $TID: dismiss" "[alarms] ring ended $TID: dismiss" "$(ring_since "$MARK")"
assert_eq "timer dismiss: the player stopped" 0 "$(alarm_player_started)"

# ---- restore ---------------------------------------------------------------------------------------------------------------
ring_save launcher
clock_restore
app_delete_alarm "$ID"; app_delete_alarm "$ID2"; app_delete_timer "$TID"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am force-stop $DESK
adb shell pm revoke $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 2
assert_clock_empty "restore"
row_end
