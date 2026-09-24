#!/usr/bin/env bash
# E21 — Permission states: the Alarms & Clock parts (below) and the Voice Recorder parts (the last section: RECORD_AUDIO
# revoked, then READ_MEDIA_AUDIO revoked hiding other apps' recordings, T15-19, T15-57).
# Full-screen intent denied: `appops set app.tileshell USE_FULL_SCREEN_INTENT deny` → E4's alarm (PIN, asleep, the
#   clock jumped) still sounds and notifies, no ring surface appears over the keyguard (the keyguard on top, no
#   activity of app.tileshell in focus), the ring slice holds `surface: lockscreen-notification <id>` and
#   `full-screen intent: deny`; the Setup checklist's "Full-screen alarms" row is red (state missing) and its tap
#   resumes com.android.settings; restore allow → the row is granted.
# Overlay (Q-E A): `appops set app.tileshell SYSTEM_ALERT_WINDOW deny` → `checklist:overlay:missing`, its tap resumes
#   com.android.settings (E4b proves the heads-up fallback); restore allow.
# `appops set app.tileshell SCHEDULE_EXACT_ALARM deny` while USE_EXACT_ALARM is held: the op's reading is recorded (it
#   does not govern a USE_EXACT_ALARM holder) and an alarm still arms with setAlarmClock — asserted through the Next
#   alarm clock line; restore `default`.
# Restore: the alarms deleted through the app, RV12's clock restore, force-stop + Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"; . "$(dirname "$0")/rec.sh"

row_begin E21 "permission states: full-screen intent, overlay, SCHEDULE_EXACT_ALARM; the recorder's microphone and audio access"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

# The plain start (run 3's `--activity-new-task --activity-clear-task` form throws in ActivityManagerService on this
# image and starts nothing — the probe of 11:07). The page is verified by its `checklist:` ids; when an old Settings
# instance comes back on another page (run 2), the shell is force-stopped once and the page opened again.
open_checklist() {
  local d="$ROW_DIR/.checklist_open.xml" i
  for i in 1 2 3; do
    adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page CHECKLIST >/dev/null 2>&1; sleep 2.5
    dump_ui "$d" >/dev/null 2>&1
    grep -q 'resource-id="checklist:' "$d" && return 0
    note "open_checklist: no checklist rows in the dump (try $i); force-stopping the shell and opening again"
    adb shell am force-stop app.tileshell; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 3
  done
  return 1
}
# The checklist row's glyph colour (TwoLineItem's left glyph): red (232,17,35) for MISSING, green (16,137,62) for GRANTED.
row_glyph_rgb() { # dump.xml tag png
  local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { echo ""; return; }
  # shellcheck disable=SC2086
  set -- $b "$3"
  python3 "$HERE/pixcmp.py" mean "$5" $(( $1 + 20 )) $(( ($2 + $4) / 2 - 18 )) $(( $1 + 70 )) $(( ($2 + $4) / 2 + 18 ))
}
glyph_is() { # rgb-triplet "r,g,b" tolerance -> yes/no
  python3 -c '
import sys
a = [int(x) for x in sys.argv[1].split(",")]; b = [int(x) for x in sys.argv[2].split(",")]; t = int(sys.argv[3])
print("yes" if all(abs(x - y) <= t for x, y in zip(a, b)) else "no (%s)" % sys.argv[1])' "$1" "$2" "$3"
}

# ---- full-screen intent denied ---------------------------------------------------------------------------------------------
# On this image USE_FULL_SCREEN_INTENT is a UID-mode op: NotificationManager.canUseFullScreenIntent reads the uid
# mode, and the doc's package-level `appops set app.tileshell … deny` leaves "Uid mode: allow" in place (run 1: the
# alarm still rang with the locked toast). The deny is set with --uid (both readings recorded); the difference is
# reported.
adb shell appops set app.tileshell USE_FULL_SCREEN_INTENT deny
record "appops after the doc's package-level deny (both modes)" "$(adb shell appops get app.tileshell USE_FULL_SCREEN_INTENT | tr -d '\r' | paste -sd'|')"
adb shell appops set --uid app.tileshell USE_FULL_SCREEN_INTENT deny
record "appops after the uid-level deny (both modes)" "$(adb shell appops get app.tileshell USE_FULL_SCREEN_INTENT | tr -d '\r' | paste -sd'|')"
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$AH" "$AM_" "Nofsi")"
assert_ne "the alarm was created" "" "$ID"
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"; [ "$AT" -gt 0 ] || AT="$(device_ms)"
adb shell input keyevent KEYCODE_HOME; sleep 1
set_pin
assert_eq "the keyguard shows after sleep + wake" "true" "$(lock_and_wake)"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
jump_clock $(( AT - 10000 )) >/dev/null
MARK="$(ring_mark)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 30)"
assert_ne "FSI denied: the alarm fired" "" "$FIRED"
sleep 4
assert_ne "FSI denied: it still sounds (an ALARM player of the shell is started)" 0 "$(alarm_player_started)"
RN="$(notification_on_channel clock_ringing)"; printf '%s\n' "$RN" > "$ROW_DIR/notification_fsi_denied.txt"
assert_ne "FSI denied: it still notifies (the alarm-channel notification is posted)" "" "$RN"
assert_eq "FSI denied: the keyguard is on top" "true" "$(keyguard_showing)"
FOCUS="$(current_focus)"; note "focus: $FOCUS"
assert_absent "FSI denied: no activity of app.tileshell over the keyguard" "app.tileshell" "$FOCUS"
assert_eq "FSI denied: the keyguard is not occluded" "false" "$(keyguard_occluded)"
SLICE="$(ring_since "$MARK")"
assert_contains "FSI denied: surface: lockscreen-notification $ID" "[alarms] surface: lockscreen-notification $ID" "$SLICE"
assert_contains "FSI denied: full-screen intent: deny" "[alarms] full-screen intent: deny" "$SLICE"
adb shell input keyevent KEYCODE_WAKEUP; sleep 2
dump_ui "$ROW_DIR/lockscreen_notification.xml"; screencap "$ROW_DIR/lockscreen_notification.png"
record "the lock screen's SystemUI nodes naming the alarm" "$(pkg_nodes "$ROW_DIR/lockscreen_notification.xml" com.android.systemui | grep -i 'nofsi\|alarm' | head -3 | paste -sd'|')"
# End the ring from the lock screen's notification when its Dismiss shows; else the restore's force-stop ends it.
MARK="$(ring_mark)"
if [ -n "$(bounds_by_text "$ROW_DIR/lockscreen_notification.xml" com.android.systemui Dismiss)" ]; then
  tap_by_text "$ROW_DIR/lockscreen_notification.xml" com.android.systemui Dismiss; sleep 3
  assert_contains "the lock-screen notification's Dismiss ends the ring" "[alarms] ring ended $ID: dismiss" "$(ring_since "$MARK")"
else
  note "no Dismiss action visible on the lock screen; the ring is ended by the restore's force-stop"
fi
ring_save launcher
clock_restore
clear_pin
assert_eq "wake_device printed Awake (C-25)" "Awake" "$(wake_device)"
# The checklist row while still denied.
open_checklist
scroll_to_node "$ROW_DIR/checklist_fsi_denied.xml" 'checklist:full_screen_alarms:missing' 8
screencap "$ROW_DIR/checklist_fsi_denied.png"
assert_eq "the Full-screen alarms row reads missing" yes "$(has_node "$ROW_DIR/checklist_fsi_denied.xml" 'checklist:full_screen_alarms:missing')"
RGB="$(row_glyph_rgb "$ROW_DIR/checklist_fsi_denied.xml" 'checklist:full_screen_alarms:missing' "$ROW_DIR/checklist_fsi_denied.png")"
record "the missing row's glyph colour (red is 232,17,35)" "$RGB"
tap_node "$ROW_DIR/checklist_fsi_denied.xml" 'checklist:full_screen_alarms:missing'; sleep 2.5
assert_contains "its tap resumes com.android.settings" "com.android.settings" "$(resumed)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell appops set --uid app.tileshell USE_FULL_SCREEN_INTENT allow; adb shell appops set app.tileshell USE_FULL_SCREEN_INTENT allow
open_checklist
scroll_to_node "$ROW_DIR/checklist_fsi_allowed.xml" 'checklist:full_screen_alarms:granted' 8
assert_eq "restore allow: the row reads granted" yes "$(has_node "$ROW_DIR/checklist_fsi_allowed.xml" 'checklist:full_screen_alarms:granted')"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- overlay denied ------------------------------------------------------------------------------------------------------------
adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW deny
open_checklist
scroll_to_node "$ROW_DIR/checklist_overlay_denied.xml" 'checklist:overlay:missing' 8
screencap "$ROW_DIR/checklist_overlay_denied.png"
assert_eq "overlay denied: checklist:overlay:missing" yes "$(has_node "$ROW_DIR/checklist_overlay_denied.xml" 'checklist:overlay:missing')"
tap_node "$ROW_DIR/checklist_overlay_denied.xml" 'checklist:overlay:missing'; sleep 2.5
assert_contains "its tap resumes com.android.settings" "com.android.settings" "$(resumed)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW allow
open_checklist
scroll_to_node "$ROW_DIR/checklist_overlay_allowed.xml" 'checklist:overlay:granted' 8
assert_eq "restore allow: checklist:overlay:granted" yes "$(has_node "$ROW_DIR/checklist_overlay_allowed.xml" 'checklist:overlay:granted')"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- SCHEDULE_EXACT_ALARM denied while USE_EXACT_ALARM is held -------------------------------------------------------------------
adb shell appops set app.tileshell SCHEDULE_EXACT_ALARM deny
record "appops SCHEDULE_EXACT_ALARM after deny" "$(adb shell appops get app.tileshell SCHEDULE_EXACT_ALARM | tr -d '\r' | head -1)"
record "USE_EXACT_ALARM grant" "$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -m1 'android.permission.USE_EXACT_ALARM: granted' | sed 's/^ *//')"
MARK="$(ring_mark)"
ID2="$(api_alarm 6 30 "Exact")"
assert_ne "the alarm was created" "" "$ID2"
EXP="$(next_wall_ms 6 30)"
assert_eq "it still arms with setAlarmClock: the Next alarm clock line holds it" "$EXP" "$(next_alarm_clock_ms)"
assert_contains "… and the rearm line says exact=true" "exact=true" "$(ring_since "$MARK" | grep -F '[alarms] rearm (store change)' | tail -1)"
adb shell appops set app.tileshell SCHEDULE_EXACT_ALARM default
record "appops SCHEDULE_EXACT_ALARM after restore" "$(adb shell appops get app.tileshell SCHEDULE_EXACT_ALARM | tr -d '\r' | head -1)"

# ---- Voice Recorder: RECORD_AUDIO revoked (a revoke kills the app's processes: the page is reopened after it, T15-57)
audio_route "E21 recorder"
purge_own_takes
adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO; sleep 2
rec_open record
dump_ui "$ROW_DIR/rec_mic_denied.xml"; screencap "$ROW_DIR/rec_mic_denied.png"
assert_eq "RECORD_AUDIO revoked: the page says it cannot record (rec_mic_notice)" yes "$(has_node "$ROW_DIR/rec_mic_denied.xml" rec_mic_notice)"
assert_eq "… and offers the grant in place (rec_mic_grant)" yes "$(has_node "$ROW_DIR/rec_mic_denied.xml" rec_mic_grant)"
n0="$(own_count)"; MARK="$(ring_mark)"
gtap "$ROW_DIR/rec_mic_denied.xml" rec_button; sleep 3
assert_eq "rec_button starts nothing (no new take)" "$n0" "$(own_count)"
assert_absent "… and no take started in the :recorder ring" "[recorder] start " "$(rec_ring "$MARK")"
# The microphone row is Tess's checklist's (cortana/CortanaChecklist.kt:42), on Tess's Settings page: the ≡ pane's Settings
# (phase 03 E9's route), not the Setup checklist.
adb shell input keyevent KEYCODE_HOME; sleep 1; cortana_assist; sleep 5
dump_ui "$ROW_DIR/tess_home.xml"; tap_node "$ROW_DIR/tess_home.xml" cortana_menu_button; sleep 2
dump_ui "$ROW_DIR/tess_pane.xml"; tap_node "$ROW_DIR/tess_pane.xml" cortana_pane_item_settings; sleep 3
dump_ui "$ROW_DIR/tess_checklist_mic_denied.xml"; screencap "$ROW_DIR/tess_checklist_mic_denied.png"
assert_eq "Tess's checklist microphone row is red (missing)" yes "$(has_node "$ROW_DIR/tess_checklist_mic_denied.xml" cortana_check:microphone:missing)"
cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO; sleep 2
rec_open record
dump_ui "$ROW_DIR/rec_mic_granted.xml"
assert_eq "granted again: no microphone notice" no "$(has_node "$ROW_DIR/rec_mic_granted.xml" rec_mic_notice)"
# ---- Voice Recorder: other apps' recordings hidden without READ_MEDIA_AUDIO (T15-19)
TAKE="$(make_take 3)"
assert_ne "its own take is made first" "" "$TAKE"
push_fixture_recordings
OTHER="$(id_by_name other.m4a)"; note "other.m4a: ${OTHER:-?}"
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_AUDIO; sleep 2
MARK="$(ring_mark)"
rec_open list; sleep 1
dump_ui "$ROW_DIR/rec_list_hidden.xml"; screencap "$ROW_DIR/rec_list_hidden.png"
assert_eq "READ_MEDIA_AUDIO revoked: the list shows its own take" yes "$(has_node "$ROW_DIR/rec_list_hidden.xml" "rec_row:$TAKE")"
assert_eq "… and NOT other.m4a" no "$(has_node "$ROW_DIR/rec_list_hidden.xml" "rec_row:$OTHER")"
assert_eq "… with the notice naming the Music grant (rec_list_notice)" yes "$(has_node "$ROW_DIR/rec_list_hidden.xml" rec_list_notice)"
note "rec_list_notice text: $(node_text "$ROW_DIR/rec_list_hidden.xml" rec_list_notice)"
assert_contains "the ring says other apps' recordings are hidden" "(other apps: hidden, READ_MEDIA_AUDIO denied)" "$(ring_since "$MARK" | grep -F '[recorder] list:')"
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO; sleep 2
MARK="$(ring_mark)"
rec_open list; sleep 1
dump_ui "$ROW_DIR/rec_list_back.xml"
assert_eq "granted again: other.m4a is back" yes "$(has_node "$ROW_DIR/rec_list_back.xml" "rec_row:$OTHER")"
assert_eq "… and the notice is gone" no "$(has_node "$ROW_DIR/rec_list_back.xml" rec_list_notice)"
assert_contains "… and the list line counts it again" "(1 by other apps)" "$(ring_since "$MARK" | grep -F '[recorder] list:')"
app_delete_take "$TAKE" || note "restore: the take $TAKE could not be deleted in the app"
purge_own_takes
remove_fixture_recordings
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- restore -----------------------------------------------------------------------------------------------------------------------
app_delete_alarm "$ID"; app_delete_alarm "$ID2"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_contains "restore: USE_FULL_SCREEN_INTENT allow" "allow" "$(adb shell appops get app.tileshell USE_FULL_SCREEN_INTENT | tr -d '\r' | head -1)"
assert_contains "restore: SYSTEM_ALERT_WINDOW allow" "allow" "$(adb shell appops get app.tileshell SYSTEM_ALERT_WINDOW | tr -d '\r' | head -1)"
assert_clock_empty "restore"
assert_contains "restore: RECORD_AUDIO granted" "RECORD_AUDIO: granted=true" "$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep 'android.permission.RECORD_AUDIO: granted')"
assert_contains "restore: READ_MEDIA_AUDIO granted" "READ_MEDIA_AUDIO: granted=true" "$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep 'android.permission.READ_MEDIA_AUDIO: granted')"
row_end
